"""One authorized command; no build, downloads, external URL or production DB."""
import json
import hashlib
import os
from pathlib import Path
import shutil
import socket
import sqlite3
import subprocess
import sys
import tempfile
import time
import urllib.request
import xml.etree.ElementTree as ET
from contextlib import closing
from fixture import seed

ROOT=Path(__file__).resolve().parents[2]
HERE=ROOT/"tests/fd002"
JAVA=Path("C:/green/jdk-21.0.8/bin/java.exe")
NODE=Path("C:/green/nodejs/node.exe")
CHROME=Path("C:/Program Files/Google/Chrome/Application/chrome.exe")
SESSION="fd002-tester-20261005-15d090a2"
EVENT="FD-002-000012-implementation-ready"
DIGEST="8a272107c44241a585978e51becdf1d769f5d17736f122b276fea088bb6178b9"
COMMAND="C:/green/python312/python.exe tests/fd002/runtime.py"


def own_temp(path):
    path=Path(path).resolve()
    if path.parent!=Path(tempfile.gettempdir()).resolve() or not path.name.startswith("fd002-tester-"):
        raise ValueError("unsafe temporary cleanup target")
    return path


def stop_owned(process):
    if process.poll() is not None: return
    process.terminate()
    try: process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill(); process.wait(timeout=10)


def classpath():
    xml=ROOT/"target/failsafe-reports/TEST-me.asu.aidev.webconsole.R9S1WebConsoleIT.xml"
    props=ET.parse(xml).getroot().find("properties")
    old=next(p.attrib["value"] for p in props if p.attrib["name"]=="surefire.test.class.path")
    dependencies=[p for p in old.split(";") if not p.replace("\\","/").rstrip("/").endswith(("/target/test-classes","/target/classes"))]
    paths=[str(ROOT/"src/main/resources"),str(ROOT/"target/classes")]+dependencies
    missing=[p for p in paths if not Path(p).exists()]
    if missing: raise RuntimeError("local classpath unavailable: "+repr(missing))
    return ";".join(dict.fromkeys(paths))


def run_once():
    if sys.argv[1:]: raise ValueError("this reviewed command accepts no modes or extra arguments")
    inventory=json.loads((HERE/"scenarios.json").read_text(encoding="utf-8"))
    event_path=ROOT/".ai/fd/FD-002/events/000012-implementation-ready.json"
    receipt=json.loads(event_path.read_text(encoding="utf-8"))
    latest=max(event_path.parent.glob("*.json"),key=lambda p:p.name)
    if (latest.name!=event_path.name or receipt.get("event_id")!=EVENT or receipt.get("dispatch_state")!="dispatched"
            or receipt.get("fd_revision")!=12 or receipt.get("fd_sha256")!=DIGEST or receipt.get("session_ref")!=SESSION):
        raise RuntimeError("latest reviewed Tester event/revision/digest/owner no longer matches")
    fd=(ROOT/"docs/features/FD-002_RUN_MISSION_DETAIL_AND_TIMELINE.md").read_text(encoding="utf-8").replace("\r\n","\n").replace("\r","\n")
    if hashlib.sha256(fd.encode("utf-8")).hexdigest()!=DIGEST:
        raise RuntimeError("current normalized FD content differs from reviewed receipt")
    authorization=ROOT/"docs/features/reports/FD-002-test-authorization-r12-v2.json"
    auth=json.loads(authorization.read_text(encoding="utf-8")); approval=auth.get("data",{})
    if (auth.get("fd_id")!="FD-002" or auth.get("source_event")!=EVENT or approval.get("decision")!="approved"
        or approval.get("implementation_event")!=EVENT or approval.get("fd_revision")!=12 or approval.get("fd_digest")!=DIGEST
        or approval.get("tester_session")!=SESSION or approval.get("command")!=COMMAND
        or Path(approval.get("working_directory","")).resolve()!=ROOT or Path.cwd().resolve()!=ROOT):
        raise RuntimeError("exact versioned authorization is missing, pending, denied or mismatched")
    out=HERE/("evidence-r12-"+time.strftime("%Y%m%d-%H%M%S")+"-"+str(os.getpid()))
    out.mkdir(parents=True,exist_ok=False)
    for name in ("runtime.py","fixture.py","browser.mjs","scenarios.json"):
        shutil.copy2(HERE/name,out/name)
    metadata={"tester_session":SESSION,"source_event":EVENT,"fd_revision":12,"fd_digest":DIGEST,
              "command":COMMAND,"cwd":str(ROOT),"authorization":str(authorization),
              "branch_coverage":"unavailable","branch_reason":"no bytecode coverage instrumentation in this offline black-box command"}
    (out/"execution.json").write_text(json.dumps(metadata,indent=2),encoding="utf-8")
    results=[]; issues=[]
    cp=classpath()
    clean_env={k:v for k,v in os.environ.items() if not k.startswith(("AIW_","SPRING_","SERVER_")) and k not in ("JAVA_TOOL_OPTIONS","_JAVA_OPTIONS","JDK_JAVA_OPTIONS")}
    # Disable proxies for every loopback readiness probe.
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for role in ("ADMIN","VIEWER","OPERATOR","APPROVER"):
        temp=own_temp(tempfile.mkdtemp(prefix="fd002-tester-")); db=temp/"tester.db"; app=None; browser=None
        role_out=out/role; role_out.mkdir()
        try:
            with socket.socket() as sock:
                sock.bind(("127.0.0.1",0)); port=sock.getsockname()[1]
            url=f"http://127.0.0.1:{port}"
            args=[str(JAVA),"-Xms64m","-Xmx512m","-XX:ReservedCodeCacheSize=96m",
                  "-XX:ActiveProcessorCount=2","-XX:TieredStopAtLevel=1","-XX:+UseSerialGC",
                  f"-XX:ErrorFile={role_out}/hs_err_pid%p.log",f"-XX:ReplayDataFile={role_out}/replay_pid%p.log",
                  "-cp",cp,"me.asu.aidev.AiDevManagerApplication","--spring.profiles.active=sqlite",
                  "--spring.flyway.enabled=true",f"--spring.datasource.url=jdbc:sqlite:{db}","--server.address=127.0.0.1",f"--server.port={port}",
                  "--aidev.lease-recovery.enabled=false","--aidev.approval.telegram.enabled=false","--aidev.approval.policy.mode=MANUAL",
                  "--aidev.web-console.auth.enabled=true","--aidev.web-console.auth.username=tester","--aidev.web-console.auth.password=local-fixture-secret",f"--aidev.web-console.auth.role={role}"]
            with (role_out/"app.log").open("w",encoding="utf-8") as log:
                app=subprocess.Popen(args,cwd=ROOT,env=clean_env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
                ready_deadline=time.monotonic()+50
                while time.monotonic()<ready_deadline:
                    if app.poll() is not None: raise RuntimeError("temporary application exited before readiness")
                    try:
                        with opener.open(url+"/ui/login",timeout=min(2,max(.05,ready_deadline-time.monotonic()))) as response:
                            page=response.read().decode("utf-8","replace")
                            if response.status==200 and 'name="username"' in page and 'name="password"' in page: break
                    except OSError: pass
                    time.sleep(min(.25,max(0,ready_deadline-time.monotonic())))
                else: raise RuntimeError("login page readiness timeout")
                with closing(sqlite3.connect(db)) as con:
                    version=con.execute("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1").fetchone()[0]
                    cols={r[1] for r in con.execute("PRAGMA table_info(execution_attempt)")}
                    if int(version)<16 or "failure_category" not in cols: raise RuntimeError("complete V1–V16 migration unavailable")
                manifest=seed(db)
                (role_out/"fixture.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
                profile=temp/"chrome-profile"
                with (role_out/"chrome.log").open("w",encoding="utf-8") as chrome_log:
                    browser=subprocess.Popen([str(CHROME),"--headless=new","--disable-gpu","--remote-debugging-port=0",f"--user-data-dir={profile}",
                       "--no-first-run","--no-default-browser-check","--disable-background-networking","--disable-component-update",
                       "--disable-sync","--disable-extensions","--disable-domain-reliability","--disable-breakpad","--disable-client-side-phishing-detection",
                       "--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1","about:blank"],cwd=ROOT,env=clean_env,
                       stdout=chrome_log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
                    for _ in range(100):
                        if browser.poll() is not None: raise RuntimeError("owned Chrome exited during startup")
                        active=profile/"DevToolsActivePort"
                        if active.exists():
                            debug_port=int(active.read_text(encoding="utf-8").splitlines()[0]); break
                        time.sleep(.1)
                    else: raise RuntimeError("Chrome debugging readiness timeout")
                    (role_out/"owned-processes.json").write_text(json.dumps({"application_pid":app.pid,"chrome_pid":browser.pid,"database":str(db),"profile":str(profile)}),encoding="utf-8")
                    child=subprocess.run([str(NODE),"--max-semi-space-size=1","--max-old-space-size=128",str(HERE/"browser.mjs"),url,str(role_out),role,str(db),str(debug_port)],cwd=ROOT,env=clean_env,
                                         capture_output=True,text=True,encoding="utf-8",creationflags=subprocess.CREATE_NO_WINDOW,timeout=230 if role=="ADMIN" else 75)
                    (role_out/"browser.log").write_text(child.stdout+child.stderr,encoding="utf-8")
                report=role_out/"results.json"
                if report.exists(): results.extend(json.loads(report.read_text(encoding="utf-8")))
                if child.returncode: issues.append({"role":role,"error":"browser command exit "+str(child.returncode)})
        except Exception as exc:
            issues.append({"role":role,"error":repr(exc)})
            (role_out/"infrastructure-error.txt").write_text(repr(exc),encoding="utf-8")
            if isinstance(exc,subprocess.TimeoutExpired):
                captured=(exc.stdout or b"")+ (exc.stderr or b"")
                if isinstance(captured,bytes): captured=captured.decode("utf-8","replace")
                (role_out/"browser.log").write_text(captured,encoding="utf-8")
            partial=role_out/"results.json"
            if partial.exists(): results.extend(json.loads(partial.read_text(encoding="utf-8")))
        finally:
            retained=False
            try:
                if browser is not None and browser.poll() is None:
                    # Only the exact process tree spawned by this role; never image-name kill.
                    killed=subprocess.run([str(Path(os.environ.get("SystemRoot","C:/Windows"))/"System32/taskkill.exe"),"/PID",str(browser.pid),"/T","/F"],capture_output=True,text=True,creationflags=subprocess.CREATE_NO_WINDOW,timeout=10)
                    (role_out/"chrome-cleanup.log").write_text(killed.stdout+killed.stderr,encoding="utf-8")
                    try: browser.wait(timeout=10)
                    except subprocess.TimeoutExpired: stop_owned(browser)
                    if killed.returncode and browser.poll() is None: raise RuntimeError("owned Chrome tree did not terminate")
            except Exception as exc:
                retained=True
                issues.append({"role":role,"chrome_cleanup_error":repr(exc),"retained_temp_path":str(temp)})
            finally:
                try:
                    if app is not None: stop_owned(app)
                except Exception as exc:
                    retained=True
                    issues.append({"role":role,"app_cleanup_error":repr(exc),"retained_temp_path":str(temp)})
            alive=[p.pid for p in (browser,app) if p is not None and p.poll() is None]
            try:
                for name in ("tester.db","tester.db-wal","tester.db-shm"):
                    if (temp/name).exists(): shutil.copy2(temp/name,role_out/name)
            except Exception as exc:
                retained=True
                issues.append({"role":role,"database_preservation_error":repr(exc),"retained_temp_path":str(temp)})
            if alive: issues.append({"role":role,"live_owned_pids":alive,"retained_temp_path":str(temp)})
            elif not retained:
                try: shutil.rmtree(own_temp(temp))
                except OSError as exc: issues.append({"role":role,"cleanup_error":repr(exc),"path":str(temp)})
    by_id={r["id"]:r for r in results}
    scenarios=[]
    for item in inventory:
        result=by_id.get(item["id"])
        scenarios.append({**item,"status":"passed" if result and result["passed"] else "blocked" if result else "uncovered",
                          "test_cases":[item["id"]] if result and result["passed"] else [],"evidence":result})
    passed=sum(s["status"]=="passed" for s in scenarios)
    summary={**metadata,"applicable_scenarios":len(scenarios),"covered_scenarios":passed,"executed_behavior_tests":len(by_id),
             "passed_behavior_tests":sum(r["passed"] for r in by_id.values()),"failed_behavior_tests":sum(not r["passed"] for r in by_id.values()),
             "unrun_behavior_tests":len(scenarios)-len(by_id),"requirements_coverage":f"{passed}/{len(scenarios)} ({passed/len(scenarios):.2%})",
             "infrastructure_issues":issues,"scenarios":scenarios}
    (out/"results.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding="utf-8")
    print(json.dumps({"evidence":str(out),"executed":len(by_id),"passed":passed,"applicable":len(scenarios),"issues":issues},ensure_ascii=False))
    return 1 if issues or any(not r["passed"] for r in by_id.values()) else 0


if __name__=="__main__":
    raise SystemExit(run_once())
