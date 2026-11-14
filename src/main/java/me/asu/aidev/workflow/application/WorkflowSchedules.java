package me.asu.aidev.workflow.application;

import java.time.*;
import java.util.*;
import jakarta.servlet.http.HttpSession;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.run.NativePlanOwners;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

/** Persisted arrangements launch ordinary text workflows. OS services only keep Plane running. */
@RestController
@RequestMapping("/api/r1/workflows/schedules")
public class WorkflowSchedules {
    public record Save(String requestId,WorkflowRuns.Spec spec,String repeat,String zone,long firstAt) {}
    public record Control(long version,String action,Long firstAt) {}
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final AuthorityGrantService grants;
    private final NativePlanOwners owners;
    private final WebConsoleSecurityService security;
    private final WorkflowRuns workflows;
    private final TransactionTemplate transactions;
    public WorkflowSchedules(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,
            NativePlanOwners owners,WebConsoleSecurityService security,WorkflowRuns workflows,PlatformTransactionManager manager) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.owners=owners;this.security=security;this.workflows=workflows;
        transactions=new TransactionTemplate(manager);
    }
    private String actor(HttpSession session) {
        security.requireOperator(session);String actor=boundary.human();
        if(actor==null||boundary.delegated())throw new SecurityException("Current human session required");
        return actor;
    }
    private static long number(Map<String,Object> row,String key) {return ((Number)row.get(key)).longValue();}
    private Map<String,Object> row(String id) {return repo.jdbc().queryForMap("SELECT * FROM workflow_schedule WHERE id=?",id);}
    private Map<String,Object> view(Map<String,Object> row) {
        var result=new LinkedHashMap<>(row);var spec=repo.decode((String)result.remove("spec_json"),WorkflowRuns.Spec.class);
        result.put("title",spec.title());result.remove("request_digest");result.remove("owner_id");result.remove("actor");
        result.put("last_state",row.get("last_execution")==null?null:executionState((String)row.get("last_execution")));
        return result;
    }
    @GetMapping public Object list(HttpSession session) {
        return repo.jdbc().queryForList("SELECT * FROM workflow_schedule WHERE actor=? ORDER BY updated_ms DESC LIMIT 100",actor(session)).stream().map(this::view).toList();
    }
    @GetMapping("/{id}") public Object content(@PathVariable String id,HttpSession session) {
        String actor=actor(session);var row=row(id);
        if(!actor.equals(row.get("actor")))throw new SecurityException("Schedule belongs to another owner");
        return Map.of("spec",repo.decode((String)row.get("spec_json"),WorkflowRuns.Spec.class));
    }
    @PostMapping @Transactional public Object save(@RequestBody Save input,HttpSession session) {
        String actor=actor(session);grants.human(actor,"A05");boundary.lock();
        if(input==null||input.spec()==null||input.repeat()==null||input.zone()==null)throw new IllegalArgumentException("Workflow, repeat and time zone required");
        ActionPermissionContract.uuid(input.requestId());
        if(!Set.of("ONCE","DAILY","WEEKLY").contains(input.repeat()))throw new IllegalArgumentException("Choose once, daily or weekly");
        if(input.spec().nodes().stream().anyMatch(WorkflowRuns.Node::workspaceTools)||!input.spec().commands().isEmpty())
            throw new IllegalArgumentException("Schedules currently support text workflows only");
        if(input.spec().nodes().stream().noneMatch(n->n.kind()==WorkflowRuns.Kind.TASK||n.kind()==WorkflowRuns.Kind.DECISION))throw new IllegalArgumentException("A working node is required");
        var zone=ZoneId.of(input.zone());String digest=AuthorityRepository.hash(repo.encode(input));
        var existing=repo.jdbc().queryForList("SELECT * FROM workflow_schedule WHERE id=?",input.requestId());
        if(!existing.isEmpty()) {
            if(!actor.equals(existing.getFirst().get("actor"))||!digest.equals(existing.getFirst().get("request_digest")))throw new IllegalStateException("Schedule request changed; inspect the original arrangement");
            return view(existing.getFirst());
        }
        if(input.firstAt()<=System.currentTimeMillis())throw new IllegalArgumentException("Choose a future first time");
        var first=Instant.ofEpochMilli(input.firstAt()).atZone(zone);
        repo.jdbc().update("""
            INSERT INTO workflow_schedule(id,actor,owner_id,request_digest,spec_json,repeat_kind,zone_id,local_time,weekday,next_ms,state,message,version,updated_ms)
            VALUES(?,?,?,?,?,?,?,?,?,?,'ACTIVE','Arrangement saved; the current content is frozen',0,?)
            """,input.requestId(),actor,owners.currentOwner(),digest,repo.encode(input.spec()),input.repeat(),zone.getId(),first.toLocalTime().withNano(0).toString(),first.getDayOfWeek().getValue(),input.firstAt(),System.currentTimeMillis());
        return view(row(input.requestId()));
    }
    /** Next local calendar occurrence; a DST overlap produces one occurrence, not two. */
    static long next(String repeat,String zone,String localTime,int weekday,long after) {
        ZoneId id=ZoneId.of(zone);var time=LocalTime.parse(localTime);var current=Instant.ofEpochMilli(after).atZone(id);
        LocalDate date=current.toLocalDate();
        if(repeat.equals("WEEKLY"))date=date.plusDays(Math.floorMod(weekday-date.getDayOfWeek().getValue(),7));
        else if(!repeat.equals("DAILY"))throw new IllegalArgumentException("A one-time arrangement has no next occurrence");
        var candidate=date.atTime(time).atZone(id).withEarlierOffsetAtOverlap();
        if(!candidate.toInstant().isAfter(current.toInstant()))candidate=date.plusDays(repeat.equals("WEEKLY")?7:1).atTime(time).atZone(id).withEarlierOffsetAtOverlap();
        return candidate.toInstant().toEpochMilli();
    }
    private void changed(Map<String,Object> row,String state,long due,String message,boolean checked) {
        if(repo.jdbc().update("UPDATE workflow_schedule SET state=?,next_ms=?,message=?,failure_checked=?,version=version+1,updated_ms=? WHERE id=? AND version=?",
            state,due,message,checked,System.currentTimeMillis(),row.get("id"),row.get("version"))!=1)throw new IllegalStateException("Schedule changed; reload");
    }
    @PostMapping("/{id}/control") @Transactional public Object control(@PathVariable String id,@RequestBody Control input,HttpSession session) {
        String actor=actor(session);boundary.lock();var row=row(id);
        if(!actor.equals(row.get("actor")))throw new SecurityException("Schedule belongs to another owner");
        if(number(row,"version")!=input.version())throw new IllegalStateException("Schedule changed; reload");
        long now=System.currentTimeMillis(),due=number(row,"next_ms");
        String previous=(String)row.get("last_execution");
        boolean checked=previous!=null&&"FAILED".equals(executionState(previous))&&!unfinished(previous);
        switch(input.action()) {
            case "pause" -> changed(row,"PAUSED",due,"Automatic starts paused; current execution continues",AuthorityRepository.flag(row.get("failure_checked")));
            case "stop" -> changed(row,"STOPPED",due,"Arrangement disabled; current execution is unchanged",false);
            case "resume" -> changed(row,due>now?"ACTIVE":"NEEDS_CONFIRMATION",due,due>now?"User checked the prior result and resumed automatic starts":"Choose run once, skip or reschedule; missed work was not started",checked);
            case "skip" -> changed(row,row.get("repeat_kind").equals("ONCE")?"STOPPED":"ACTIVE",row.get("repeat_kind").equals("ONCE")?due:next((String)row.get("repeat_kind"),(String)row.get("zone_id"),(String)row.get("local_time"),((Number)row.get("weekday")).intValue(),now),"User skipped the missed occurrence",checked);
            case "reschedule" -> {
                if(input.firstAt()==null||input.firstAt()<=now)throw new IllegalArgumentException("Choose a future first time");
                var first=Instant.ofEpochMilli(input.firstAt()).atZone(ZoneId.of((String)row.get("zone_id")));
                repo.jdbc().update("UPDATE workflow_schedule SET local_time=?,weekday=? WHERE id=?",first.toLocalTime().withNano(0).toString(),first.getDayOfWeek().getValue(),id);
                changed(row,"ACTIVE",input.firstAt(),"User rescheduled after checking the prior result",checked);
            }
            case "run-now" -> {
                changed(row,"ACTIVE",now,"User requested one execution",true);fire(id,now,true);
            }
            default -> throw new IllegalArgumentException("Unknown schedule operation");
        }
        return view(row(id));
    }
    private String executionState(String execution) {
        return repo.jdbc().queryForList("SELECT state FROM workflow_execution WHERE id=?",String.class,execution).stream().findFirst().orElse("UNKNOWN");
    }
    private boolean unfinished(String execution) {
        return repo.jdbc().queryForObject("SELECT COUNT(*) FROM work_task WHERE subject_id=? AND lease_token IS NOT NULL",Long.class,execution)>0
            || !Set.of("COMPLETED","STOPPED","FAILED").contains(executionState(execution));
    }
    private void fire(String id,long expectedDue,boolean manual) {
        boundary.lock();var row=row(id);long now=System.currentTimeMillis(),due=number(row,"next_ms");
        if(!row.get("state").equals("ACTIVE")||due!=expectedDue||due>now)return;
        if(!manual&&now/60000>due/60000) {
            changed(row,"NEEDS_CONFIRMATION",due,"Missed time; choose run once, skip or reschedule",false);return;
        }
        String previous=(String)row.get("last_execution");
        if(previous!=null&&(unfinished(previous)||!manual&&!AuthorityRepository.flag(row.get("failure_checked"))&&"FAILED".equals(executionState(previous)))) {
            if(manual)throw new IllegalStateException("Check the original execution and its stop acknowledgement first");
            changed(row,"NEEDS_CONFIRMATION",due,"Check the prior execution before starting another occurrence",false);return;
        }
        var run=workflows.startScheduled(id,due);
        long following=row.get("repeat_kind").equals("ONCE")?due:next((String)row.get("repeat_kind"),(String)row.get("zone_id"),(String)row.get("local_time"),((Number)row.get("weekday")).intValue(),now);
        changed(row,row.get("repeat_kind").equals("ONCE")?"TRIGGERED":"ACTIVE",following,"Execution started; inspect its actual result",false);
        repo.jdbc().update("UPDATE workflow_schedule SET last_execution=? WHERE id=?",run.id,id);
    }
    @Scheduled(fixedDelayString="${aidev.workflow.schedule-poll-ms:5000}") public void tick() {
        var ready=repo.jdbc().queryForList("SELECT id,next_ms FROM workflow_schedule WHERE state='ACTIVE' AND next_ms<=? ORDER BY next_ms LIMIT 20",System.currentTimeMillis());
        for(var row:ready) {
            String id=(String)row.get("id");long due=number(row,"next_ms");
            try {transactions.executeWithoutResult(status->fire(id,due,false));}
            catch(RuntimeException error) {
                String reason=error.getMessage()==null?"Schedule could not start; inspect the saved work":error.getMessage();
                transactions.executeWithoutResult(status->repo.jdbc().update("UPDATE workflow_schedule SET state='NEEDS_CONFIRMATION',message=?,version=version+1,updated_ms=? WHERE id=? AND state='ACTIVE' AND next_ms=?",reason.substring(0,Math.min(900,reason.length())),System.currentTimeMillis(),id,due));
            }
        }
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() {return ResponseEntity.status(403).body(Map.of("error","Current schedule Owner required"));}
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class,DateTimeException.class}) public ResponseEntity<?> invalid(RuntimeException error) {return ResponseEntity.unprocessableContent().body(Map.of("error",String.valueOf(error.getMessage())));}
}
