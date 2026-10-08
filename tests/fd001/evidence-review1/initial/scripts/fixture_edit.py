"""Only adds one READY task to the explicitly identified disposable fixture DB."""
from pathlib import Path
import sqlite3, sys, time
path=Path(sys.argv[1]).resolve()
if path.name!='tester.db' or not path.parent.name.startswith('fd001-tester-'):
    raise ValueError('dedicated temporary fixture required')
now=int(time.time()*1000)
connection=sqlite3.connect(path)
try:
    connection.execute('PRAGMA foreign_keys=ON')
    connection.execute("INSERT INTO work_task(id,task_type,state,subject_type,subject_id,required_capabilities,priority,created_at_epoch_ms,updated_at_epoch_ms,instruction,repository_ref,base_revision) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",('tester-count-change','IMPLEMENTATION','READY','AUTONOMOUS_REQUIREMENT','tester-run-08','nonexistent',10,now,now,'INDEPENDENT LOCAL COUNT CHANGE','fixture','1'*40))
    connection.commit()
finally:connection.close()
