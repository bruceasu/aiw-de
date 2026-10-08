"""Disposable FD-002 black-box fixtures; schema comes from public migrations."""
from pathlib import Path
import json
import sqlite3
import sys

T = 1759363200000  # 2025-10-02T00:00:00Z; deterministic UTC boundaries.
PREFIX = "fd002-tester-"
LONG = "Original requirement <script>window.__injected=1</script> & 中文 " + "Long requirement paragraph. " * 250


def database(path):
    path = Path(path).resolve()
    if path.name != "tester.db" or not path.parent.name.startswith(PREFIX):
        raise ValueError("a dedicated FD-002 disposable database is required")
    db = sqlite3.connect(path, timeout=5)
    db.execute("PRAGMA foreign_keys=ON")
    return db


def put(db, table, **fields):
    # Table/field names are fixed in this test module; values are parameterized.
    db.execute(f'INSERT INTO {table} ({",".join(fields)}) VALUES ({",".join("?" for _ in fields)})', tuple(fields.values()))


def task(db, run, name, kind="IMPLEMENTATION", state="COMPLETED", at=T, instruction=None, subject="AUTONOMOUS_REQUIREMENT"):
    put(db, "work_task", id=name, task_type=kind, state=state, subject_type=subject, subject_id=run,
        required_capabilities="local-test", priority=10, created_at_epoch_ms=at, updated_at_epoch_ms=at+100,
        instruction=instruction or ("Fixture instruction for " + name), repository_ref="fixture", base_revision="1"*40)
    return name


def run(db, name, requirement=None, status="RUNNING", control="ACTIVE", **extra):
    req = requirement or name+"-req"
    if not db.execute("SELECT 1 FROM work_task WHERE id=?", (req,)).fetchone():
        task(db, name, req, "REQUIREMENT_ANALYSIS", at=T-10000, instruction="Original requirement for "+name)
    put(db, "autonomous_run", id=name, requirement_task_id=req, status=status, control_state=control,
        started_at_epoch_ms=T-10000, status_updated_at_epoch_ms=T, **extra)
    return req


def edge(db, dependent, prerequisite):
    put(db, "work_task_dependency", task_id=dependent, depends_on_task_id=prerequisite)


def attempt(db, task_id, number, state="FAILED", reason=None, at=T+1000, summary=None):
    aid = task_id+"-a"+str(number)
    put(db, "execution_attempt", id=aid, task_id=task_id, attempt_no=number, worker_id="fd2-worker-historical",
        lease_token=aid+"-token", state=state, created_at_epoch_ms=at, started_at_epoch_ms=at+10,
        finished_at_epoch_ms=at+20 if state not in ("CLAIMED", "RUNNING") else None,
        termination_reason=reason, failure_category="UNKNOWN" if state=="FAILED" else None,
        completion_summary=summary, publication_ref="https://example.invalid/not-to-be-read" if summary and state=="COMPLETED" else None)
    return aid


def event(db, rid, seq, kind, at, actor="operator:fixture", related=None, detail=None):
    put(db, "autonomous_run_event", run_id=rid, sequence_no=seq, event_type=kind, occurred_at_epoch_ms=at,
        actor=actor, related_task_id=related, detail=detail)
    db.execute("UPDATE autonomous_run SET event_sequence=? WHERE id=?", (seq, rid))


def seed(path):
    manifest = {"time": T, "long_requirement": LONG, "runs": {}}
    with database(path) as db:
        put(db, "worker", id="fd2-worker-historical", runtime_type="fixture", capabilities="local-test",
            max_concurrency=1, active_leases=0, status="OFFLINE", registered_at_epoch_ms=T,
            last_heartbeat_at_epoch_ms=T, updated_at_epoch_ms=T)
        req = run(db, "fd2-single")
        db.execute("UPDATE work_task SET instruction=? WHERE id=?", (LONG, req))
        single = [req, task(db, "fd2-single", "fd2-s-impl", at=T+1), task(db, "fd2-single", "fd2-s-review", "CODE_REVIEW", at=T+2), task(db, "fd2-single", "fd2-s-verify", "INTEGRATION_VERIFICATION", at=T+3)]
        for pre, dep in zip(single, single[1:]): edge(db, dep, pre)
        manifest["runs"]["fd2-single"] = {"tasks": single, "edges": list(zip(single, single[1:]))}
        req = run(db, "fd2-parallel")
        names = [req, "fd2-p-a", "fd2-p-b", "fd2-p-integrate", "fd2-p-review", "fd2-p-verify"]
        for i, (name, kind) in enumerate(zip(names[1:], ["IMPLEMENTATION", "IMPLEMENTATION", "INTEGRATION", "CODE_REVIEW", "INTEGRATION_VERIFICATION"])):
            task(db, "fd2-parallel", name, kind, at=T+i+1)
        pairs = [(req,names[1]), (req,names[2]), (names[1],names[3]), (names[2],names[3]), (names[3],names[4]), (names[3],names[5])]
        for pre, dep in pairs: edge(db, dep, pre)
        manifest["runs"]["fd2-parallel"] = {"tasks": names, "edges": pairs}
        run(db, "fd2-disconnected")
        task(db, "fd2-disconnected", "fd2-d-impl")
        task(db, "fd2-disconnected", "fd2-d-review", "CODE_REVIEW", at=T+1)
        run(db, "fd2-cycle")
        a=task(db, "fd2-cycle", "fd2-c-a"); b=task(db, "fd2-cycle", "fd2-c-b", at=T+1)
        edge(db,a,b); edge(db,b,a)
        req=run(db, "fd2-boundary")
        tasks=[req]+[task(db, "fd2-boundary", f"fd2-b-{i:03}", at=T+i) for i in range(200)]
        edge(db, tasks[1], req)  # Requirement is outside newest 200 tasks.
        foreign=run(db, "fd2-foreign")
        other=task(db, "other-subject", "fd2-other-subject", subject="TASK")
        edge(db, tasks[2], foreign); edge(db, tasks[3], other)
        # The excluded oldest task owns a real edge: full Run total=4,
        # displayed-task candidates=3. This edge must not create a fake node.
        edge(db, req, foreign)
        manifest["runs"]["fd2-boundary"]={"tasks": tasks, "outside":req,"cross_run":foreign,"cross_subject":other,
            "run_edges":4,"candidate_edges":3,"returned_edges":3}
        req=run(db, "fd2-dense")
        dense=[req]+[task(db, "fd2-dense", f"fd2-dense-{i:03}", at=T+i) for i in range(199)]
        pairs=[]
        for dep in range(1,200):
            for pre in range(dep):
                if len(pairs)==1201: break
                pairs.append((dense[pre],dense[dep])); edge(db,dense[dep],dense[pre])
            if len(pairs)==1201: break
        manifest["runs"]["fd2-dense"]={"tasks":dense,"edges":pairs}
        run(db,"fd2-attempt-window")
        aidtask=task(db,"fd2-attempt-window","fd2-aw-task")
        for i in range(1,302): attempt(db,aidtask,i,"COMPLETED",at=T+i)
        # Newer unrelated attempts must not displace this Run's newest 300.
        for i in range(1,306): attempt(db,foreign,i,"COMPLETED",at=T+10000+i)
        run(db,"fd2-history",status="FAILED",last_failed_task_id=None,last_failure_reason="stored run failure",failure_count=2,recovery_count=1)
        failed=task(db,"fd2-history","fd2-h-task",state="FAILED")
        for n, state, reason in [(1,"EXPIRED",None),(2,"EXPIRED","OPERATOR_PAUSED"),(3,"EXPIRED","OPERATOR_CANCELLED"),(4,"FAILED",None),(5,"COMPLETED",None)]:
            attempt(db,failed,n,state,reason,at=T+100*n,summary="REPORT SUMMARY ONLY" if n==4 else "COMPLETED REPORT SUMMARY" if n==5 else None)
        event(db,"fd2-history",1,"FAILED",T-86400000,related=failed,detail="FAILED OUTSIDE TIMELINE actor and timestamp")
        event(db,"fd2-history",2,"RECOVERED",T-86399000,actor="human:recovery",related=failed,detail="Manual recovery exact event time")
        for i in range(3,24): event(db,"fd2-history",i,"RECOVERED",T-86399000+i,actor="human:history",related=failed,detail="RECOVERY HISTORY "+str(i))
        for i in range(24,345): event(db,"fd2-history",i,"MAX_PARALLELISM_CHANGED",T-1000+i,detail="WINDOW EVENT "+str(i))
        manifest["runs"]["fd2-history"]={"failure_time":T-86400000,"recovery_time":T-86399000,"recovery_total":23,"latest_failure":"FAILED OUTSIDE TIMELINE actor and timestamp","task":failed}
        run(db,"fd2-timeline")
        for seq, at, detail in [(1,T-1000,"BEFORE MIDNIGHT"),(2,T+1000,"AFTER MIDNIGHT"),(3,T+1000,"SAME TIME SECOND"),(4,T+2000,"Long event full detail "+"detail words "*100)]:
            event(db,"fd2-timeline",seq,"PAUSED",at,actor=None if seq==3 else "human:timeline",detail=detail)
        event(db,"fd2-timeline",5,"RESUMED",T+3000,detail=None,actor=None)
        run(db,"fd2-empty")
        # Bad relationship has existing FK target belonging to a different Run.
        foreign_original=task(db,"fd2-foreign","fd2-bad-original","REQUIREMENT_ANALYSIS",instruction="FOREIGN REQUIREMENT MUST NOT BE USED")
        run(db,"fd2-bad-link", requirement=foreign_original)
        # A truly missing requirement/empty task graph requires corrupt-row fixture.
        # SQLite FK checks are disabled only for this disposable connection.
        db.commit()
        db.execute("PRAGMA foreign_keys=OFF")
        run(db,"fd2-missing-link")
        db.execute("UPDATE autonomous_run SET requirement_task_id='fd2-absent-req' WHERE id='fd2-missing-link'")
        db.execute("DELETE FROM work_task WHERE subject_id='fd2-missing-link'")
        db.commit(); db.execute("PRAGMA foreign_keys=ON")
        longrun="fd2-long-"+"R"*110
        req=run(db,longrun); longtask="fd2-longtask-"+"T"*110
        task(db,longrun,longtask,"INTEGRATION_VERIFICATION",state="READY",instruction=LONG)
        edge(db,longtask,req)
        manifest["long_run"]=longrun; manifest["long_task"]=longtask
        for rid,status,control in [("fd2-action","RUNNING","ACTIVE"),("fd2-paused","RUNNING","PAUSED"),("fd2-failed","FAILED","ACTIVE"),("fd2-cancelled","RUNNING","CANCELLED")]:
            run(db,rid,status=status,control=control)
            task(db,rid,rid+"-work",state="FAILED" if status=="FAILED" else "READY")
        # Per-role DBs start from identical input; action assertions mutate only them.
    return manifest


def mutate(path, mode):
    with database(path) as db:
        if mode=="repair":
            task(db,"fd2-parallel","fd2-p-repair","REPAIR",state="READY",at=T+30000)
            edge(db,"fd2-p-repair","fd2-p-review")
        elif mode=="tick":
            event(db,"fd2-timeline",6,"PAUSED",T+6000,detail="NEW AFTER REFRESH")
        else: raise ValueError("only repair/tick mutations are accepted")


if __name__=="__main__":
    if len(sys.argv)!=3: raise ValueError("fixture mutation takes disposable DB and repair/tick")
    mutate(sys.argv[1],sys.argv[2])
    print(json.dumps({"mutation":sys.argv[2],"database":"disposable tester.db"}))
