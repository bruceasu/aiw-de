package me.asu.aidev.execution.guard;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.execution.guard.GuardContract.*;
import static me.asu.aidev.execution.guard.ExecutionLedger.number;

/** Durable consent facts. Neither opening nor deciding starts an operation or resumes a task. */
@Service
public class ToolApprovals {
    public static final String PROTOCOL="aiw.tool.approval.v1";
    public enum Action { REPLACE_FILE, DELETE_FILE, EXECUTE_COMMAND }
    public record Binding(String protocol,String approvalId,Begin operation,Action action,
                          String workspaceDigest,String targetVersion,long expiresAt,boolean gitCheckpoint,Command command,FrozenRef checkpoint) {
        public Binding {
            if(!PROTOCOL.equals(protocol))throw new IllegalArgumentException("Unsupported approval protocol");
            text(approvalId,128);Objects.requireNonNull(operation);Objects.requireNonNull(action);
            hash(workspaceDigest);hash(targetVersion);
            boolean file=action==Action.REPLACE_FILE||action==Action.DELETE_FILE;
            boolean commandAction=action==Action.EXECUTE_COMMAND;
            if(file && (operation.tool()!=Tool.WRITE || operation.resource().isBlank() || operation.resource().equals("."))
                    || commandAction && (operation.tool()!=Tool.COMMAND || command==null || !command.id().equals(operation.resource()) || targetVersion.isBlank())
                    || !commandAction && command!=null || expiresAt<1 || checkpoint==null
                    || !"tool-approval".equals(checkpoint.kind()) || !approvalId.equals(checkpoint.id())
                    || gitCheckpoint && !checkpoint.revision().matches("[0-9a-f]{40}|[0-9a-f]{64}"))
                throw new IllegalArgumentException("Exact fixed file or command operation required");
        }
    }
    public record Decision(String approvalId,long expectedRevision,String bindingDigest,boolean approve) {
        public Decision {text(approvalId,128);hash(bindingDigest);if(expectedRevision<1)throw new IllegalArgumentException("Exact approval revision required");}
    }
    public record Discard(String approvalId,String bindingDigest,String reason) {
        public Discard {text(approvalId,128);hash(bindingDigest);if(!"TARGET_VERSION_CHANGED".equals(reason))throw new IllegalArgumentException("Supported invalidation reason required");}
    }
    public record View(String id,String taskId,String ownerId,Binding binding,String bindingDigest,String state,long revision,boolean quiescent,String successorTaskId) {}
    public record Consume(String approvalId,String bindingDigest,Begin operation,Action action,String workspaceDigest,String targetVersion) {
        public Consume {
            text(approvalId,128);hash(bindingDigest);Objects.requireNonNull(operation);Objects.requireNonNull(action);
            hash(workspaceDigest);hash(targetVersion);
        }
    }
    public record ApprovedOperation(String operationId,Tool tool,String repositoryRef,String resource,String parameterDigest,long expectedGrantRevision) {}
    public record Approved(String approvalId,String sourceTaskId,String sourceAttemptId,String bindingDigest,ApprovedOperation operation,
                           Action action,String workspaceDigest,String targetVersion,FrozenRef checkpoint,boolean gitCheckpoint,
                           String operationState,boolean execute,String resultDigest) {}
    public static String commandDigest(Command command) {
        var values=new ArrayList<String>();values.add("aiw.guard.command.v1");values.add(command.id());values.add(Integer.toString(command.argv().size()));
        values.addAll(command.argv());values.add(command.cwd());values.add(command.permissionProfile());values.add(Long.toString(command.timeoutMs()));
        values.add(Integer.toString(command.outputBytes()));values.add(Long.toString(command.memoryBytes()));values.add(Integer.toString(command.processLimit()));
        var canonical=new StringBuilder();for(String value:values)canonical.append(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).append(':').append(value).append('\n');
        return AuthorityRepository.hash(canonical.toString());
    }
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final ExecutionGuard guard;private final ExecutionYieldService yields;private final WorkTaskRepository tasks;
    public ToolApprovals(AuthorityRepository repo,AuthorityBoundary boundary,ExecutionGuard guard,ExecutionYieldService yields,WorkTaskRepository tasks) {
        this.repo=repo;this.boundary=boundary;this.guard=guard;this.yields=yields;this.tasks=tasks;
    }
    @Transactional public View open(String task,Binding binding) {
        boundary.lock();guard.requireApprovalScope(task,binding.operation());
        String encoded=repo.encode(binding),digest=AuthorityRepository.hash(encoded);
        String requestDigest=AuthorityRepository.hash(task+"\n"+encoded);
        var prior=repo.jdbc().queryForList("SELECT * FROM execution_tool_approval WHERE id=? OR operation_id=?",binding.approvalId(),binding.operation().operationId());
        if(!prior.isEmpty()) {
            if(prior.size()!=1 || !requestDigest.equals(prior.getFirst().get("request_digest")))throw new IllegalStateException("APPROVAL_ID_CONFLICT");
            return view(refresh(prior.getFirst()));
        }
        var c=guard.contextRow(task);var policy=guard.policyRow(String.valueOf(c.get("plan_id")));
        if(binding.action()==Action.EXECUTE_COMMAND) {
            var frozen=repo.decode(String.valueOf(c.get("policy_json")),Policy.class);var current=guard.policy(policy);
            if(!Objects.equals(binding.command(),command(frozen,binding.operation().resource()))
                    || !Objects.equals(binding.command(),command(current,binding.operation().resource()))
                    || !commandDigest(binding.command()).equals(binding.targetVersion()))
                throw new SecurityException("Exact original and current command policy required");
            if(containsCredentialArgument(binding.command()))
                throw new IllegalArgumentException("Commands containing credential-like arguments cannot be approved; use a protected credential source");
        }
        long now=System.currentTimeMillis();
        if(binding.expiresAt()<=now || binding.expiresAt()>number(policy,"expires_ms") || binding.expiresAt()-now>86_400_000)
            throw new IllegalArgumentException("Approval expiry must fit policy and one day");
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_operation WHERE operation_id=?",Long.class,binding.operation().operationId())!=0)
            throw new IllegalStateException("Operation already has an outcome or start");
        yields.prepare(task,new Yield(binding.operation().lease(),binding.approvalId(),binding.checkpoint()));
        repo.jdbc().update("""
            INSERT INTO execution_tool_approval(id,task_id,plan_id,owner_id,attempt_id,operation_id,binding_json,binding_digest,
            request_digest,policy_digest,generation,state,expires_ms,created_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?)
            """,binding.approvalId(),task,c.get("plan_id"),policy.get("owner_id"),binding.operation().lease().attemptId(),
            binding.operation().operationId(),encoded,digest,requestDigest,policy.get("digest"),policy.get("generation"),binding.expiresAt(),now);
        repo.audit("PLAN:"+c.get("plan_id"),null,binding.approvalId(),"worker:"+binding.operation().lease().workerId(),"TOOL_APPROVAL_REQUESTED",digest);
        return view(row(binding.approvalId()));
    }
    @Transactional public List<View> list(String plan) {
        boundary.lock();guard.owner(String.valueOf(guard.policyRow(plan).get("owner_id")));
        return repo.jdbc().queryForList("SELECT * FROM execution_tool_approval WHERE plan_id=? ORDER BY created_ms,id",plan)
                .stream().map(this::refresh).map(this::view).toList();
    }
    @Transactional public View decide(Decision input) {
        boundary.lock();var row=row(input.approvalId());guard.owner(String.valueOf(row.get("owner_id")));
        String encoded=repo.encode(input);
        if(row.get("decision_json")!=null) {
            if(!encoded.equals(row.get("decision_json")))throw new IllegalStateException("APPROVAL_DECISION_CONFLICT");
            return view(refresh(row));
        }
        row=refresh(row);
        if(!"PENDING".equals(row.get("state")) || number(row,"revision")!=input.expectedRevision()
                || !input.bindingDigest().equals(row.get("binding_digest")))throw new IllegalStateException("APPROVAL_VERSION_OR_STATE_CHANGED");
        if(!"ACKNOWLEDGED".equals(repo.jdbc().queryForObject("SELECT state FROM execution_yield WHERE task_id=?",String.class,row.get("task_id"))))
            throw new IllegalStateException("WORKER_HAS_NOT_CONFIRMED_QUIESCENCE");
        String state=input.approve()?"APPROVED":"REJECTED";
        repo.jdbc().update("UPDATE execution_tool_approval SET state=?,revision=revision+1,decision_json=?,decided_by=?,decided_ms=? WHERE id=? AND state='PENDING'",
                state,encoded,boundary.human(),System.currentTimeMillis(),input.approvalId());
        if(input.approve()) {
            String successor=continueApproved(row);
            repo.jdbc().update("UPDATE execution_tool_approval SET successor_task_id=? WHERE id=? AND state='APPROVED' AND successor_task_id IS NULL",successor,input.approvalId());
        }
        repo.audit("PLAN:"+row.get("plan_id"),null,input.approvalId(),boundary.human(),"TOOL_APPROVAL_"+state,input.bindingDigest());
        return view(row(input.approvalId()));
    }
    private Map<String,Object> row(String id) {return repo.jdbc().queryForMap("SELECT * FROM execution_tool_approval WHERE id=?",id);}
    /** Consent consumption and STARTED share this transaction and the global authority lock. */
    @Transactional public ExecutionGuard.Receipt consume(String task,Consume input) {
        boundary.lock();var row=row(input.approvalId());String encoded=repo.encode(input);
        if("CONSUMED".equals(row.get("state"))) {
            if(!task.equals(row.get("consumed_task_id")) || !encoded.equals(row.get("consumption_json")))
                throw new IllegalStateException("APPROVAL_CONSUMPTION_CONFLICT");
            // Original identity checks still apply, but a lost Lease does not manufacture a second start.
            return guard.query(task,input.operation().lease(),input.operation().operationId());
        }
        row=refresh(row);
        if(!"APPROVED".equals(row.get("state")) || !input.bindingDigest().equals(row.get("binding_digest")))
            throw new IllegalStateException("EXACT_APPROVED_OPERATION_REQUIRED");
        var binding=repo.decode(String.valueOf(row.get("binding_json")),Binding.class);
        var source=binding.operation();var operation=input.operation();
        var fixed=new Begin(operation.protocol(),operation.platformInstanceId(),source.lease(),operation.operationId(),operation.tool(),
                operation.repositoryRef(),operation.resource(),operation.parameterDigest(),operation.expectedGrantRevision());
        if(!fixed.equals(source) || binding.action()!=input.action() || !binding.workspaceDigest().equals(input.workspaceDigest())
                || !binding.targetVersion().equals(input.targetVersion()))throw new SecurityException("Approved operation or file version changed");
        String original=String.valueOf(row.get("task_id"));
        if(task.equals(original)) {
            if(!operation.lease().equals(source.lease()))throw new SecurityException("Exact original attempt required");
        } else {
            var next=guard.contextRow(task);var prior=guard.contextRow(original);
            if(!original.equals(next.get("resumes_task_id")) || !task.equals(prior.get("successor_task_id"))
                    || !Objects.equals(next.get("config_digest"),prior.get("config_digest"))
                    || number(next,"generation")!=number(row,"generation")
                    || repo.jdbc().queryForObject("SELECT COUNT(*) FROM work_task WHERE id=? AND state='CONTINUED'",Long.class,original)!=1)
                throw new SecurityException("Exact platform-created continuation required");
        }
        var context=guard.contextRow(task);var policy=guard.policyRow(String.valueOf(context.get("plan_id")));
        if(!row.get("plan_id").equals(context.get("plan_id")) || !row.get("owner_id").equals(policy.get("owner_id")))
            throw new SecurityException("Approval Owner or Plan changed");
        requireExactCommand(binding,context,policy);
        guard.requireApprovalScope(task,operation);
        if(repo.jdbc().update("""
                UPDATE execution_tool_approval SET state='CONSUMED',revision=revision+1,consumption_json=?,start_digest=?,consumed_task_id=?,consumed_ms=?
                WHERE id=? AND state='APPROVED'
                """,encoded,AuthorityRepository.hash(repo.encode(operation)),task,System.currentTimeMillis(),input.approvalId())!=1)
            throw new IllegalStateException("APPROVAL_ALREADY_CONSUMED");
        var receipt=guard.begin(task,operation);
        if(!receipt.execute())throw new IllegalStateException("APPROVED_OPERATION_NOT_STARTED");
        repo.audit("PLAN:"+row.get("plan_id"),null,input.approvalId(),"worker:"+operation.lease().workerId(),"TOOL_APPROVAL_CONSUMED",input.bindingDigest());
        return receipt;
    }
    /** Returns the one fixed approved action for this exact continuation, without exposing its source Lease. */
    @Transactional public Approved approved(String task,Lease lease) {
        boundary.lock();guard.verifyLease(task,lease,false,true);
        var rows=repo.jdbc().queryForList("SELECT * FROM execution_tool_approval WHERE successor_task_id=? ORDER BY decided_ms,id",task);
        if(rows.isEmpty())return null;
        if(rows.size()!=1)throw new IllegalStateException("Multiple approved operations bound to one continuation");
        var row=refresh(rows.getFirst());String state=String.valueOf(row.get("state"));
        if(!Set.of("APPROVED","CONSUMED","EXPIRED","INVALIDATED").contains(state))throw new IllegalStateException("Approval has no executable continuation");
        var binding=repo.decode(String.valueOf(row.get("binding_json")),Binding.class);String source=String.valueOf(row.get("task_id"));
        var next=guard.contextRow(task);var prior=guard.contextRow(source);
        if(!source.equals(next.get("resumes_task_id")) || !task.equals(prior.get("successor_task_id"))
                || !Objects.equals(next.get("config_digest"),prior.get("config_digest"))
                || number(next,"generation")!=number(row,"generation") || !Objects.equals(row.get("plan_id"),next.get("plan_id")))
            throw new SecurityException("Exact platform-created approval continuation required");
        var checkpoint=repo.decode(String.valueOf(repo.jdbc().queryForObject("SELECT checkpoint_json FROM execution_yield WHERE task_id=?",String.class,source)),FrozenRef.class);
        if(!binding.checkpoint().equals(checkpoint))throw new SecurityException("Approval checkpoint changed");
        var handoffs=repo.jdbc().queryForList("SELECT sha256 FROM execution_handoff WHERE task_id=? AND attempt_id=?",String.class,source,binding.operation().lease().attemptId());
        if(handoffs.size()!=1 || !checkpoint.sha256().equals(handoffs.getFirst()))throw new IllegalStateException("Approved handoff changed");
        var ref=new FrozenRef("tool-approval",String.valueOf(row.get("id")),checkpoint.revision(),String.valueOf(row.get("binding_digest")));
        var context=guard.context(task);
        if(!context.snapshot().references().contains(ref))throw new SecurityException("Approval reference is not frozen into this continuation");
        var fixed=binding.operation();var current=new Begin(fixed.protocol(),fixed.platformInstanceId(),lease,fixed.operationId(),fixed.tool(),
                fixed.repositoryRef(),fixed.resource(),fixed.parameterDigest(),fixed.expectedGrantRevision());
        boolean execute="APPROVED".equals(state);String resultDigest=null;String operationState=state;
        if("APPROVED".equals(state))requireExactCommand(binding,next,guard.policyRow(String.valueOf(next.get("plan_id"))));
        if(execute) guard.requireApprovalScope(task,current);
        if("CONSUMED".equals(state)) {
            guard.requireApprovalScope(task,current);
            if(!task.equals(row.get("consumed_task_id")))throw new SecurityException("Approval was consumed by a different task");
            var operations=repo.jdbc().queryForList("SELECT state,result_digest FROM execution_operation WHERE operation_id=? AND task_id=?",fixed.operationId(),task);
            if(operations.size()!=1 || !Set.of("SUCCEEDED","FAILED").contains(operations.getFirst().get("state")))
                throw new IllegalStateException("Previously approved operation requires reconciliation; it will not be replayed");
            operationState=String.valueOf(operations.getFirst().get("state"));
            resultDigest=(String)operations.getFirst().get("result_digest");
        }
        return new Approved(String.valueOf(row.get("id")),source,String.valueOf(row.get("attempt_id")),String.valueOf(row.get("binding_digest")),
                new ApprovedOperation(fixed.operationId(),fixed.tool(),fixed.repositoryRef(),fixed.resource(),fixed.parameterDigest(),fixed.expectedGrantRevision()),
                binding.action(),binding.workspaceDigest(),binding.targetVersion(),checkpoint,binding.gitCheckpoint(),operationState,execute,resultDigest);
    }
    /** A stale file version only removes consent; it can never start or finish an operation. */
    @Transactional public void discard(String task,Lease lease,Discard input) {
        boundary.lock();guard.verifyLease(task,lease,true,true);var row=row(input.approvalId());
        if(!task.equals(row.get("successor_task_id")) || !input.bindingDigest().equals(row.get("binding_digest"))
                || !"APPROVED".equals(row.get("state"))
                || repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_operation WHERE operation_id=?",Long.class,row.get("operation_id"))!=0)
            throw new IllegalStateException("Approved operation is no longer discardable");
        var source=String.valueOf(row.get("task_id"));var next=guard.contextRow(task);var prior=guard.contextRow(source);
        if(!source.equals(next.get("resumes_task_id")) || !task.equals(prior.get("successor_task_id")))
            throw new SecurityException("Exact approved continuation required");
        if(repo.jdbc().update("UPDATE execution_tool_approval SET state='INVALIDATED',revision=revision+1 WHERE id=? AND state='APPROVED'",input.approvalId())!=1)
            throw new IllegalStateException("Approved operation changed during invalidation");
        repo.audit("PLAN:"+row.get("plan_id"),null,input.approvalId(),"worker:"+lease.workerId(),"TOOL_APPROVAL_INVALIDATED",input.reason());
    }
    private Map<String,Object> refresh(Map<String,Object> row) {
        if(!Set.of("PENDING","APPROVED").contains(row.get("state")))return row;
        var policy=guard.policyRow(String.valueOf(row.get("plan_id")));String state=null;
        if(number(row,"expires_ms")<=System.currentTimeMillis())state="EXPIRED";
        else if(AuthorityRepository.flag(policy.get("revoked")) || !row.get("owner_id").equals(policy.get("owner_id"))
                || !row.get("policy_digest").equals(policy.get("digest")) || number(row,"generation")!=number(policy,"generation"))state="INVALIDATED";
        if(state==null)return row;
        repo.jdbc().update("UPDATE execution_tool_approval SET state=?,revision=revision+1 WHERE id=? AND state IN ('PENDING','APPROVED')",state,row.get("id"));
        return row(String.valueOf(row.get("id")));
    }
    private View view(Map<String,Object> row) {
        var binding=repo.decode(String.valueOf(row.get("binding_json")),Binding.class);
        // Lease tokens belong to the Worker. Browser responses contain a redacted transport copy.
        var operation=binding.operation();var lease=operation.lease();
        var redacted=new Begin(operation.protocol(),operation.platformInstanceId(),new Lease(lease.workerId(),lease.attemptId(),"REDACTED"),
                operation.operationId(),operation.tool(),operation.repositoryRef(),operation.resource(),operation.parameterDigest(),operation.expectedGrantRevision());
        binding=new Binding(binding.protocol(),binding.approvalId(),redacted,binding.action(),binding.workspaceDigest(),binding.targetVersion(),binding.expiresAt(),binding.gitCheckpoint(),
                redactCommand(binding.command()),binding.checkpoint());
        return new View(String.valueOf(row.get("id")),String.valueOf(row.get("task_id")),String.valueOf(row.get("owner_id")),binding,
                String.valueOf(row.get("binding_digest")),String.valueOf(row.get("state")),number(row,"revision"),
                "ACKNOWLEDGED".equals(repo.jdbc().queryForObject("SELECT state FROM execution_yield WHERE task_id=?",String.class,row.get("task_id"))),
                (String)row.get("successor_task_id"));
    }

    private String continueApproved(Map<String,Object> approval) {
        String source=String.valueOf(approval.get("task_id"));var old=tasks.find(new TaskId(source)).orElseThrow();
        if(old.state()!=TaskState.WAITING_INPUT)throw new IllegalStateException("Approved Worker must be quiescent before continuation");
        if(guard.contextRow(source).get("successor_task_id")!=null)throw new IllegalStateException("Waiting task already has a continuation");
        var binding=repo.decode(String.valueOf(approval.get("binding_json")),Binding.class);var cp=binding.checkpoint();
        if(!Objects.equals(cp,repo.decode(String.valueOf(repo.jdbc().queryForObject("SELECT checkpoint_json FROM execution_yield WHERE task_id=?",String.class,source)),FrozenRef.class)))
            throw new IllegalStateException("Approval yield checkpoint changed");
        var handoff=repo.jdbc().queryForList("SELECT sha256 FROM execution_handoff WHERE attempt_id=? AND task_id=?",String.class,binding.operation().lease().attemptId(),source);
        if(handoff.size()!=1||!cp.sha256().equals(handoff.getFirst()))throw new IllegalStateException("Approved handoff checkpoint is unavailable");
        String root=source.replaceFirst("-resume-[1-9][0-9]*$","");int index=1;String next;
        do {if(index>999_999_999)throw new IllegalStateException("Continuation identifier limit");next=root+"-resume-"+index++;}while(tasks.find(new TaskId(next)).isPresent());
        if(!Objects.equals(old.repositoryRef(),binding.operation().repositoryRef()))throw new IllegalStateException("Approval repository changed");
        String revision=old.baseRevision();
        String applied=switch(binding.action()) {case DELETE_FILE->"delete the single file";case REPLACE_FILE->"replace the whole file";case EXECUTE_COMMAND->"run command "+binding.command().id();};
        var now=java.time.Instant.now();var task=new WorkTask(new TaskId(next),old.type(),TaskState.READY,old.subjectType(),old.subjectId(),
                old.requiredCapabilities(),old.skillRef(),old.instruction()+"\n\nThe Worker will apply the approved operation ("+applied+") for "+binding.operation().resource()+" before continuing. Continue from the updated workspace and do not repeat that operation.",
                old.instructionRevision()+1,old.repositoryRef(),revision,old.priority(),0,now,now);
        var ref=new FrozenRef("tool-approval",String.valueOf(approval.get("id")),cp.revision(),AuthorityRepository.hash(repo.encode(binding)));
        return yields.continueTask(source,old.version(),task,String.valueOf(approval.get("id")),List.of(ref)).value();
    }
    private Command command(Policy policy,String id) {return policy.commands().stream().filter(command->command.id().equals(id)).findFirst().orElse(null);}
    private void requireExactCommand(Binding binding,Map<String,Object> context,Map<String,Object> policyRow) {
        if(binding.action()!=Action.EXECUTE_COMMAND)return;
        var original=repo.decode(String.valueOf(context.get("policy_json")),Policy.class);
        var current=guard.policy(policyRow);String id=binding.operation().resource();
        if(!Objects.equals(binding.command(),command(original,id)) || !Objects.equals(binding.command(),command(current,id))
                || !commandDigest(binding.command()).equals(binding.targetVersion()))
            throw new SecurityException("Approved command no longer matches the original and current policy");
        if(containsCredentialArgument(binding.command()))throw new SecurityException("Credential-like command arguments are not approvable");
    }
    private boolean containsCredentialArgument(Command command) {
        if(command==null)return false;
        for(int i=0;i<command.argv().size();i++) {
            String arg=command.argv().get(i);
            if(sensitiveCommandValue(arg))return true;
            if(sensitiveCommandKey(arg) && arg.indexOf('=')<0 && arg.indexOf(':')<0 && i+1<command.argv().size())return true;
        }
        return false;
    }
    private boolean sensitiveCommandKey(String raw) {
        if(raw.matches("(?i)(?:-u|--user|--username)"))return true;
        String key=raw.trim().replaceFirst("(?i)^-(?:d)(?=[a-z])", "").replaceFirst("^-{1,2}", "");
        int equal=key.indexOf('=');int colon=key.indexOf(':');int split=equal<0?colon:colon<0?equal:Math.min(equal,colon);
        if(split>=0)key=key.substring(0,split);
        String compact=key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return compact.matches(".*(?:password|passwd|passphrase|secret|token|credential|authorization|auth|apikey|accesskey|privatekey|clientsecret|bearer|cookie|sessionkey|signingkey).*");
    }
    private boolean sensitiveCommandValue(String arg) {
        if(arg.matches("(?i)https?://[^/@\\s:]+:[^/@\\s]+@.*"))return true;
        if(java.util.regex.Pattern.compile("(?i)(?:authorization|proxy-authorization|cookie|set-cookie|x-[a-z0-9-]*api[-_]?key)\\s*[:=]\\s*(?:(?:bearer|basic)\\s+)?\\S+").matcher(arg).find())return true;
        if(java.util.regex.Pattern.compile("(?i)\\b(?:bearer|basic)\\s+[A-Za-z0-9._~+/-]+=*").matcher(arg).find())return true;
        if(java.util.regex.Pattern.compile("(?i)(?:sk-(?:proj-)?|gh[pousr]_|github_pat_|xox[baprs]-|AKIA)[A-Za-z0-9_-]{12,}|eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}").matcher(arg).find())return true;
        int equal=arg.indexOf('=');int colon=arg.indexOf(':');int split=equal<0?colon:colon<0?equal:Math.min(equal,colon);
        return split>0 && sensitiveCommandKey(arg.substring(0,split));
    }
    private Command redactCommand(Command command) {
        if(command==null)return null;
        var argv=new ArrayList<String>();boolean redactNext=false;
        for(String arg:command.argv()) {
            if(redactNext){argv.add("[REDACTED]");redactNext=false;continue;}
            int equal=arg.indexOf('=');int colon=arg.indexOf(':');int split=equal<0?colon:colon<0?equal:Math.min(equal,colon);
            if(split>0 && sensitiveCommandKey(arg.substring(0,split))){argv.add(arg.substring(0,split+1)+"[REDACTED]");continue;}
            if(sensitiveCommandValue(arg)){
                String safe=arg.replaceAll("(?i)((?:authorization|proxy-authorization|cookie|set-cookie|x-[a-z0-9-]*api[-_]?key)\\s*[:=]\\s*(?:(?:bearer|basic)\\s+)?).+", "$1[REDACTED]");
                safe=safe.replaceAll("(?i)(\\b(?:bearer|basic)\\s+)[A-Za-z0-9._~+/-]+=*", "$1[REDACTED]");
                safe=safe.replaceAll("(?i)(?:sk-(?:proj-)?|gh[pousr]_|github_pat_|xox[baprs]-|AKIA)[A-Za-z0-9_-]{12,}|eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}", "[REDACTED]");
                argv.add(safe.replaceAll("(?i)(https?://)[^/@\\s:]+:[^/@\\s]+@","$1[REDACTED]@"));
                continue;
            }
            if(sensitiveCommandKey(arg) && arg.indexOf('=')<0 && arg.indexOf(':')<0){argv.add(arg);redactNext=true;continue;}
            String redacted=arg.replaceAll("(?i)((?:authorization|proxy-authorization|cookie|set-cookie|x-[a-z0-9-]*api[-_]?key)\\s*[:=]\\s*(?:(?:bearer|basic)\\s+)?).+", "$1[REDACTED]");
            redacted=redacted.replaceAll("(?i)(\\b(?:bearer|basic)\\s+)[A-Za-z0-9._~+/-]+=*", "$1[REDACTED]");
            redacted=redacted.replaceAll("(?i)(?:sk-(?:proj-)?|gh[pousr]_|github_pat_|xox[baprs]-|AKIA)[A-Za-z0-9_-]{12,}|eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}", "[REDACTED]");
            argv.add(redacted.replaceAll("(?i)(https?://)[^/@\\s:]+:[^/@\\s]+@","$1[REDACTED]@"));
        }
        return new Command(command.id(),argv,command.cwd(),command.permissionProfile(),command.timeoutMs(),command.outputBytes(),command.memoryBytes(),command.processLimit());
    }
}
