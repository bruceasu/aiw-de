package me.asu.aidev.worker.application;

import java.util.*;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.port.TaskClaimPolicy;
import me.asu.aidev.execution.skills.SkillFiles;
import me.asu.aidev.work.domain.WorkTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable personal members; assignments freeze context without owning a process or granting tools. */
@Service
public class TeamMembers implements TaskClaimPolicy {
    private final AuthorityRepository db;
    private final AuthorityBoundary boundary;
    private final SkillFiles skills;
    public TeamMembers(AuthorityRepository db,AuthorityBoundary boundary,SkillFiles skills) {this.db=db;this.boundary=boundary;this.skills=skills;}
    public record Profile(String id,long revision,String name,String purpose,List<String> skills,String notes,String state) {}
    public record Save(String id,long revision,String name,String purpose,List<String> skills,String notes) {}
    private static String text(String value,int limit,boolean optional) {
        if(value==null)value="";value=value.strip();
        if((!optional&&value.isEmpty())||value.length()>limit||value.indexOf(0)>=0)throw new IllegalArgumentException("Member text is empty or too long");
        return value;
    }
    private static void id(String value) {if(value==null||!value.matches("member-[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid member ID");}
    private Profile profile(Map<String,Object> row) {
        @SuppressWarnings("unchecked") List<String> names=db.decode((String)row.get("skills_json"),List.class);
        return new Profile((String)row.get("id"),((Number)row.get("revision")).longValue(),(String)row.get("name"),
                (String)row.get("purpose"),List.copyOf(names),(String)row.get("notes"),(String)row.get("state"));
    }
    public Profile get(String actor,String member) {
        id(member);return db.jdbc().queryForList("SELECT * FROM worker_team_member WHERE id=? AND actor=?",member,actor)
                .stream().map(this::profile).findFirst().orElseThrow();
    }
    public Object history(String actor,String member) {
        get(actor,member);
        var rows=db.jdbc().queryForList("""
            SELECT DISTINCT r.id,r.state,r.run_json,r.updated_ms FROM worker_team_assignment a
              JOIN workflow_execution r ON r.id=a.run_id WHERE a.member_id=? AND r.actor=?
             ORDER BY r.updated_ms DESC,r.id LIMIT 100
            """,member,actor);
        for(var row:rows){var run=db.decode((String)row.remove("run_json"),Map.class);if(run.get("spec") instanceof Map<?,?> spec)row.put("title",spec.get("title"));}
        return rows;
    }
    public Object list(String actor,boolean archived) {
        var members=db.jdbc().queryForList("SELECT * FROM worker_team_member WHERE actor=? AND state"+(archived?"='ARCHIVED'":"<>'ARCHIVED'")+" ORDER BY updated_ms DESC,id LIMIT 100",actor)
                .stream().map(this::profile).map(p->Map.of("id",p.id(),"revision",p.revision(),"name",p.name(),"purpose",p.purpose(),"skills",p.skills(),"state",p.state())).toList();
        var work=archived?List.<Map<String,Object>>of():db.jdbc().queryForList("""
            SELECT a.member_id,t.run_id,t.task_id,w.state,w.instruction,w.current_worker_id,wr.last_heartbeat_at_epoch_ms
              FROM worker_team_task t
              JOIN worker_team_assignment a ON a.run_id=t.run_id AND a.node_key=t.node_key
              JOIN worker_team_member m ON m.id=a.member_id JOIN work_task w ON w.id=t.task_id
              LEFT JOIN worker wr ON wr.id=w.current_worker_id
             WHERE m.actor=? AND w.state IN ('READY','BLOCKED','CLAIMED','RUNNING','YIELDING','WAITING_INPUT')
             ORDER BY w.created_at_epoch_ms DESC,t.task_id LIMIT 200
            """,actor);
        for(var item:work){String instruction=(String)item.remove("instruction");item.put("title",instruction==null?item.get("task_id"):instruction.substring(0,Math.min(120,instruction.length())));}
        var runs=archived?List.<Map<String,Object>>of():db.jdbc().queryForList("""
            SELECT DISTINCT a.member_id,r.id AS run_id,r.state,r.updated_ms FROM worker_team_assignment a
              JOIN workflow_execution r ON r.id=a.run_id JOIN worker_team_member m ON m.id=a.member_id
             WHERE m.actor=? AND r.state NOT IN ('COMPLETED','STOPPED') ORDER BY r.updated_ms DESC,r.id LIMIT 200
            """,actor);
        return Map.of("members",members,"work",work,"runs",runs,"limit",100,"workLimit",200,"skills",skills.available(),"observedAt",System.currentTimeMillis());
    }
    @Transactional public Profile save(String actor,Save input) {
        if(input==null||input.revision()<0)throw new IllegalArgumentException("Member input and revision required");id(input.id());boundary.lock();
        String name=text(input.name(),80,false),purpose=text(input.purpose(),4000,false),notes=text(input.notes(),8000,true);
        var selected=input.skills()==null?List.<String>of():input.skills();
        if(selected.size()>8||new HashSet<>(selected).size()!=selected.size()||!skills.available().containsAll(selected))throw new IllegalArgumentException("Select up to eight registered platform Skills");
        long now=System.currentTimeMillis();String encoded=db.encode(selected);
        if(input.revision()==0) {
            var previous=db.jdbc().queryForList("SELECT * FROM worker_team_member WHERE id=?",input.id());
            if(!previous.isEmpty()) {
                var original=get(actor,input.id());
                if(original.revision()==1&&original.name().equals(name)&&original.purpose().equals(purpose)&&original.skills().equals(selected)&&original.notes().equals(notes))return original;
                throw new IllegalStateException("Member already exists; refresh before changing it");
            }
            if(db.jdbc().queryForObject("SELECT COUNT(*) FROM worker_team_member WHERE actor=? AND state<>'ARCHIVED'",Long.class,actor)>=100)throw new IllegalStateException("At most 100 current members per account");
            db.jdbc().update("INSERT INTO worker_team_member VALUES(?,?,?,?,?,?,1,'ACTIVE',?,?)",input.id(),actor,name,purpose,encoded,notes,now,now);
        } else if(db.jdbc().update("UPDATE worker_team_member SET name=?,purpose=?,skills_json=?,notes=?,revision=revision+1,updated_ms=? WHERE id=? AND actor=? AND revision=? AND state<>'ARCHIVED'",name,purpose,encoded,notes,now,input.id(),actor,input.revision())!=1)
            throw new IllegalStateException("Member changed or is archived; refresh before saving");
        return get(actor,input.id());
    }
    @Transactional public Profile state(String actor,String member,long revision,String action) {
        boundary.lock();var current=get(actor,member);
        if(action==null)throw new IllegalArgumentException("Member action required");
        if(current.revision()!=revision)throw new IllegalStateException("Member changed; refresh before applying this action");
        String next=switch(action){case "stop"->"DRAINING";case "resume"->"ACTIVE";case "archive"->"ARCHIVED";default->throw new IllegalArgumentException("Unknown member action");};
        if(current.state().equals("ARCHIVED"))throw new IllegalStateException("Archived members are read-only");
        if(next.equals("ARCHIVED")&&(db.jdbc().queryForObject("""
            SELECT COUNT(*) FROM worker_team_assignment a JOIN workflow_execution r ON r.id=a.run_id
             WHERE a.member_id=? AND r.state NOT IN ('COMPLETED','STOPPED')
            """,Long.class,member)>0||db.jdbc().queryForObject("""
            SELECT COUNT(*) FROM worker_team_task t JOIN worker_team_assignment a ON a.run_id=t.run_id AND a.node_key=t.node_key
              JOIN work_task w ON w.id=t.task_id WHERE a.member_id=? AND w.state IN ('READY','BLOCKED','CLAIMED','RUNNING','YIELDING','WAITING_INPUT')
            """,Long.class,member)>0))throw new IllegalStateException("Finish or stop assigned workflows before dissolving this member");
        if(db.jdbc().update("UPDATE worker_team_member SET state=?,revision=revision+1,updated_ms=? WHERE id=? AND actor=? AND revision=?",next,System.currentTimeMillis(),member,actor,revision)!=1)
            throw new IllegalStateException("Member changed; refresh");
        return get(actor,member);
    }
    public List<String> requirements(List<String> selected) {
        if(selected==null)return List.of();
        if(selected.size()>8||new HashSet<>(selected).size()!=selected.size()||!skills.available().containsAll(selected))
            throw new IllegalArgumentException("Select up to eight registered platform Skills");
        return List.copyOf(selected);
    }
    private boolean supports(Profile member,List<String> required) {
        var available=new HashSet<>(member.skills());available.add("general-work");return available.containsAll(required);
    }
    public Profile forRun(String actor,String member,List<String> required) {
        var result=get(actor,member);
        if(!result.state().equals("ACTIVE"))throw new IllegalStateException("Selected member is not accepting new work");
        if(!supports(result,required))throw new IllegalArgumentException("Selected member lacks required Skills");
        return result;
    }
    public record Selection(Profile member,String reason) {}
    /** Caller holds authority_lock until the selected snapshot and task are committed. */
    public Selection choose(String actor,List<String> required) {
        var candidates=db.jdbc().queryForList("SELECT * FROM worker_team_member WHERE actor=? AND state<>'ARCHIVED' ORDER BY id LIMIT 100",actor)
                .stream().map(this::profile).toList();
        if(candidates.isEmpty())return new Selection(null,"No team members exist. Create one in Agent factory.");
        var matching=candidates.stream().filter(m->supports(m,required)).toList();
        if(matching.isEmpty())return new Selection(null,"No member has all required Skills. Update the requirements or member settings.");
        var load=db.jdbc().queryForList("""
            SELECT a.member_id,
              SUM(CASE WHEN w.state IN ('READY','BLOCKED','CLAIMED','RUNNING','YIELDING','WAITING_INPUT') THEN 1 ELSE 0 END) AS pending,
              MAX(w.created_at_epoch_ms) AS last_work
            FROM worker_team_assignment a JOIN worker_team_member m ON m.id=a.member_id
              LEFT JOIN worker_team_task t ON t.run_id=a.run_id AND t.node_key=a.node_key
              LEFT JOIN work_task w ON w.id=t.task_id WHERE m.actor=? GROUP BY a.member_id
            """,actor);
        var pending=new HashMap<String,Long>();var last=new HashMap<String,Long>();
        for(var row:load){String id=(String)row.get("member_id");pending.put(id,((Number)row.get("pending")).longValue());
            if(row.get("last_work") instanceof Number time)last.put(id,time.longValue());}
        var chosen=matching.stream().filter(m->m.state().equals("ACTIVE"))
                .min(Comparator.comparingLong((Profile m)->pending.getOrDefault(m.id(),0L))
                        .thenComparingLong(m->last.getOrDefault(m.id(),0L)).thenComparing(Profile::id)).orElse(null);
        return new Selection(chosen,chosen==null?"Matching members have stopped accepting work. Resume a matching member.":null);
    }
    public void attachSkills(String task,List<String> required) {if(!required.isEmpty())skills.member(task,required);}
    public void freeze(String run,String node,Profile member) {
        db.jdbc().update("INSERT INTO worker_team_assignment VALUES(?,?,?,?)",run,node,member.id(),db.encode(member));
    }
    public Profile assigned(String run,String node) {
        return db.jdbc().queryForList("SELECT profile_json FROM worker_team_assignment WHERE run_id=? AND node_key=?",String.class,run,node)
                .stream().map(json->db.decode(json,Profile.class)).findFirst().orElse(null);
    }
    public Map<String,Object> identity(String task) {
        if(task==null)return Map.of();
        var rows=db.jdbc().queryForList("""
            SELECT a.profile_json,m.state FROM worker_team_task t
              JOIN worker_team_assignment a ON a.run_id=t.run_id AND a.node_key=t.node_key
              JOIN worker_team_member m ON m.id=a.member_id WHERE t.task_id=?
            """,task);
        if(rows.isEmpty())return Map.of();var row=rows.getFirst();var member=db.decode((String)row.get("profile_json"),Profile.class);
        return Map.of("id",member.id(),"name",member.name(),"revision",member.revision(),"state",row.get("state"));
    }
    public String context(Profile member) {
        return "\nTeam member: "+member.name()+"\nMember responsibilities:\n"+member.purpose()+"\nHuman-maintained experience notes:\n"+member.notes()
                +"\nTreat experience notes as fallible context. Ask if important facts conflict. These instructions and Skills do not grant tools, file access, or permissions. Follow the task's execution policy.\n";
    }
    public void bind(String run,String node,String task,Profile member) {
        db.jdbc().update("INSERT INTO worker_team_task VALUES(?,?,?)",task,run,node);skills.member(task,member.skills());
    }
    public void inherit(String source,String successor) {
        var rows=db.jdbc().queryForList("SELECT run_id,node_key FROM worker_team_task WHERE task_id=?",source);
        if(rows.isEmpty())return;var original=rows.getFirst();
        db.jdbc().update("INSERT INTO worker_team_task VALUES(?,?,?) ON CONFLICT(task_id) DO NOTHING",successor,original.get("run_id"),original.get("node_key"));
        var actual=db.jdbc().queryForMap("SELECT run_id,node_key FROM worker_team_task WHERE task_id=?",successor);
        if(!original.equals(actual))throw new IllegalStateException("Successor has a different member assignment");
    }
    @Override public boolean canClaim(WorkTask task) {
        var members=db.jdbc().queryForList("""
            SELECT a.member_id FROM worker_team_task t JOIN worker_team_assignment a ON a.run_id=t.run_id AND a.node_key=t.node_key WHERE t.task_id=?
            """,String.class,task.id().value());
        if(members.isEmpty())return true;
        boundary.lock();
        return db.jdbc().queryForObject("SELECT COUNT(*) FROM worker_team_member WHERE id=? AND state='ACTIVE'",Long.class,members.getFirst())==1;
    }
}
