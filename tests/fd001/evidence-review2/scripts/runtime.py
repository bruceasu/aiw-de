"""Independent FD-001 black-box launcher. Loopback and disposable DB only."""
import json, os, re, socket, sqlite3, subprocess, sys, tempfile, time, urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODE = sys.argv[1] if len(sys.argv)==2 and sys.argv[1] in ('diagnose','review-fixes') else 'matrix'
if sys.argv[1:] and MODE == 'matrix': raise ValueError('only diagnose/review-fixes modes are accepted')
OUT = ROOT / 'tests/fd001/evidence-review2' if MODE == 'review-fixes' else ROOT / 'tests/fd001/evidence-r7' / 'diagnose-v1' if MODE == 'diagnose' else ROOT / 'tests/fd001/evidence-r7'

def seed(path):
    assert path.name == 'tester.db' and path.parent.name.startswith('fd001-tester-')
    now = int(time.time()*1000)
    with sqlite3.connect(path) as db:
        db.execute('PRAGMA foreign_keys=ON')
        def put(table, **fields):
            db.execute(f'INSERT INTO {table} ({",".join(fields)}) VALUES ({",".join("?" for _ in fields)})', tuple(fields.values()))
        for wid, status, leases in [('tester-idle','ACTIVE',0),('tester-busy','ACTIVE',1),('tester-drain','DRAINING',1),('tester-offline','OFFLINE',0)]:
            put('worker', id=wid, runtime_type='tester-local',capabilities='java,maven',max_concurrency=1,active_leases=leases,status=status,registered_at_epoch_ms=now,last_heartbeat_at_epoch_ms=now,updated_at_epoch_ms=now)
        for i in range(16):
            run=f'tester-run-{i:02}'; task=run+'-req'
            status={1:'FAILED',2:'WAITING_APPROVAL',3:'ENGINEERING_PASSED',4:'SUCCEEDED',5:'REJECTED'}.get(i,'RUNNING')
            put('work_task',id=task,task_type='REQUIREMENT_ANALYSIS',state='COMPLETED',subject_type='AUTONOMOUS_REQUIREMENT',subject_id=run,required_capabilities='java,maven',priority=10,created_at_epoch_ms=now-9000-i,updated_at_epoch_ms=now-8000-i,instruction='INDEPENDENT LOCAL FIXTURE '+('Long readable requirement ' * 30)+'<script>unsafe()</script>',repository_ref='fixture',base_revision='1'*40)
            put('autonomous_run',id=run,requirement_task_id=task,status=status,control_state='PAUSED' if i==6 else 'ACTIVE',started_at_epoch_ms=now-9000-i,status_updated_at_epoch_ms=now-i,completed_at_epoch_ms=now-1000 if i in (4,5) else None,max_parallelism=1 if i==0 else 0,active_slots=1 if i in (0,7) else 0)
            if i not in (4,5):
                put('work_task',id=run+'-ready',task_type='IMPLEMENTATION',state='READY',subject_type='AUTONOMOUS_REQUIREMENT',subject_id=run,required_capabilities='nonexistent' if i>=8 else 'java,maven',priority=10,created_at_epoch_ms=now-5000-i,updated_at_epoch_ms=now-5000-i,instruction='INDEPENDENT LOCAL FIXTURE',repository_ref='fixture',base_revision='1'*40)
            put('approval_request',id='tester-pending-'+str(i),source_task_id=task,subject_type='AUTONOMOUS_REQUIREMENT',subject_id=run,result_revision='2'*40,engineering_gate_closed_at_epoch_ms=now-7000,status='PENDING',requested_by='tester',channel='WEB',requested_at_epoch_ms=now-6000-i)
        for i in range(110):
            task='tester-decided-source-'+str(i)
            put('work_task',id=task,task_type='IMPLEMENTATION',state='COMPLETED',subject_type='AUTONOMOUS_REQUIREMENT',subject_id='tester-run-00',required_capabilities='java,maven',priority=10,created_at_epoch_ms=now-3000,updated_at_epoch_ms=now-2000,instruction='INDEPENDENT LOCAL FIXTURE',repository_ref='fixture',base_revision='1'*40)
            put('approval_request',id='tester-decided-'+str(i),source_task_id=task,subject_type='AUTONOMOUS_REQUIREMENT',subject_id='tester-run-00',result_revision='2'*40,engineering_gate_closed_at_epoch_ms=now-2000,status='APPROVED',requested_by='tester',channel='WEB',requested_at_epoch_ms=now-i,decided_at_epoch_ms=now-i,decided_by='tester',decision_note='same-time-order-'+str(i))
        for i,wid in [(0,'tester-busy'),(7,'tester-drain')]:
            task=f'tester-run-{i:02}-executing'
            put('work_task',id=task,task_type='IMPLEMENTATION',state='RUNNING',subject_type='AUTONOMOUS_REQUIREMENT',subject_id=f'tester-run-{i:02}',required_capabilities='java,maven',priority=10,attempt_no=1,current_worker_id=wid,lease_token='tester-token-'+str(i),lease_acquired_at_epoch_ms=now,lease_expires_at_epoch_ms=now+3600000,lease_heartbeat_at_epoch_ms=now,created_at_epoch_ms=now,updated_at_epoch_ms=now,instruction='LOCAL',repository_ref='fixture',base_revision='1'*40)
            put('execution_attempt',id='tester-attempt-'+str(i),task_id=task,attempt_no=1,worker_id=wid,lease_token='tester-token-'+str(i),state='RUNNING',created_at_epoch_ms=now,started_at_epoch_ms=now)
    db.close()

OUT.mkdir(parents=True,exist_ok=True)
props=ET.parse(ROOT/'target/failsafe-reports/TEST-me.asu.aidev.webconsole.R9S1WebConsoleIT.xml').getroot().find('properties')
cp=next(p.attrib['value'] for p in props if p.attrib['name']=='surefire.test.class.path')
cp=str(ROOT/'src/main/resources')+';'+';'.join(p for p in cp.split(';') if not p.endswith('test-classes'))
results=[]
for role in (['ADMIN'] if MODE in ('diagnose','review-fixes') else ['ADMIN','VIEWER','OPERATOR','APPROVER']):
    with tempfile.TemporaryDirectory(prefix='fd001-tester-', ignore_cleanup_errors=True) as temp:
        db=Path(temp)/'tester.db'
        with socket.socket() as sock:
            sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
        url=f'http://127.0.0.1:{port}'
        memory=['-Xms64m','-Xmx512m','-XX:ReservedCodeCacheSize=96m',f'-XX:ErrorFile={OUT}/hs_err_pid%p.log',f'-XX:ReplayDataFile={OUT}/replay_pid%p.log'] if MODE=='review-fixes' else []
        args=['C:/green/jdk-21.0.8/bin/java.exe']+memory+['-cp',cp,'me.asu.aidev.AiDevManagerApplication','--spring.profiles.active=sqlite',f'--spring.datasource.url=jdbc:sqlite:{db}','--server.address=127.0.0.1',f'--server.port={port}','--aidev.lease-recovery.enabled=false','--aidev.approval.telegram.enabled=false','--aidev.web-console.auth.enabled=true','--aidev.web-console.auth.username=tester','--aidev.web-console.auth.password=local-fixture-secret',f'--aidev.web-console.auth.role={role}']
        with (OUT/f'{role}-app.log').open('w',encoding='utf8') as log:
            app=subprocess.Popen(args,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
            try:
                for _ in range(120):
                    if app.poll() is not None: raise RuntimeError('temporary application exited')
                    try:
                        urllib.request.urlopen(url+'/ui/login',timeout=2).close();break
                    except OSError: time.sleep(.25)
                else: raise RuntimeError('startup timeout')
                if MODE == 'diagnose':
                    with urllib.request.urlopen(url+'/ui/login',timeout=3) as response:
                        snippet=response.read(4096).decode('utf8','replace')
                        title=re.search(r'<title>(.*?)</title>',snippet,re.S)
                        (OUT/'http-login.json').write_text(json.dumps(dict(status=response.status,content_type=response.headers.get('Content-Type'),url=response.url,safe_excerpt=title.group(1) if title else 'No title in first 4096 bytes'),ensure_ascii=False,indent=2),encoding='utf8')
                else:
                    seed(db)
                    if MODE=='review-fixes':
                        with sqlite3.connect(db) as connection:
                            connection.execute("UPDATE work_task SET state='COMPLETED' WHERE state='READY' AND id NOT IN ('tester-run-00-ready','tester-run-06-ready')")
                            connection.execute("UPDATE work_task SET required_capabilities='nonexistent' WHERE id='tester-run-06-ready'")
                        connection.close()
                helper='diagnose.mjs' if MODE == 'diagnose' else 'review-fixes.mjs' if MODE=='review-fixes' else 'browser.mjs'
                child=subprocess.run(['C:/green/nodejs/node.exe',str(ROOT/'tests/fd001'/helper),url,str(OUT),role]+([str(db)] if MODE=='review-fixes' else []),cwd=ROOT,capture_output=True,text=True,encoding='utf8',creationflags=subprocess.CREATE_NO_WINDOW,timeout=65 if MODE=='review-fixes' else 50 if MODE=='diagnose' else 140)
                (OUT/f'{role}-browser.log').write_text(child.stdout+child.stderr,encoding='utf8')
                if (OUT/f'{role}-results.json').exists(): results+=json.loads((OUT/f'{role}-results.json').read_text(encoding='utf8'))
                if child.returncode: results.append(dict(id=f'{role}-runner',behavior='Browser runner completes',passed=False,detail=child.stderr))
            finally:
                app.terminate()
                try:app.wait(timeout=15)
                except subprocess.TimeoutExpired:app.kill();app.wait()
(OUT/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(dict(executed=len(results),passed=sum(r['passed'] for r in results),failed=sum(not r['passed'] for r in results)),ensure_ascii=False))
raise SystemExit(1 if any(not r['passed'] for r in results) else 0)
