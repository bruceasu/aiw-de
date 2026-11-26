package me.asu.aidev.execution.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityGrantService;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Runs bounded, model-only manager requests. */
@Service
public class ManagerProfileSelection {
    public record Request(String task,List<String> candidates) {}
    public record Result(String requestId,String profileId,long version,String reason) {}
    public record SkillDraft(String title,String content,String language) {}
    public record Assessment(String requestId,String kind,String advice,String content) {}
    private record Output(String requestId,String profileId,String reason) {}
    private static class InferenceFailure extends IllegalStateException {
        InferenceFailure(String reason) {super(reason);}
    }
    private final AgentFactorySettings factory;
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final AuthorityGrantService grants;
    private final Path helper;
    private final Semaphore running=new Semaphore(1);
    private final LinkedHashSet<ProcessHandle> remainingProcesses=new LinkedHashSet<>();

    public ManagerProfileSelection(AgentFactorySettings factory,AuthorityRepository repo,AuthorityBoundary boundary,
            AuthorityGrantService grants,@Value("${aidev.agent-factory.worker-root:workers/process-python}") String root) {
        this.factory=factory;this.repo=repo;this.boundary=boundary;this.grants=grants;
        this.helper=Path.of(root).toAbsolutePath().normalize().resolve("manager_model.py");
    }

    public Result choose(Request input) {
        String actor=boundary.human();grants.identityHuman(actor);
        return choose(input,actor,"AGENT_FACTORY");
    }

    public Result chooseForWorkflow(String workflowId,Request input) {
        return choose(input,"control-plane:manager","WORKFLOW:"+workflowId);
    }

    private Result choose(Request input,String actor,String target) {
        if(input==null||input.task()==null||input.task().isBlank()||input.task().length()>4000
                ||input.candidates()==null||input.candidates().isEmpty()||input.candidates().size()>16)
            throw new IllegalArgumentException("Select up to sixteen enabled profiles and describe the task.");
        var profiles=factory.runtimeProfiles();
        var candidates=profiles.items().stream().filter(p->p.enabled()&&input.candidates().contains(p.id())).toList();
        if(candidates.size()!=input.candidates().size())throw new IllegalArgumentException("A candidate profile is unavailable.");
        String id=UUID.randomUUID().toString();
        if(candidates.size()==1) {
            var candidate=candidates.getFirst();
            repo.audit(target,null,id,actor,"MANAGER_SELECTION_RECORDED",candidate.id());
            return new Result(id,candidate.id(),candidate.version(),"Only one enabled candidate matches.");
        }
        var manager=managerProfile(profiles);
        var settings=manager.settings();
        byte[] request=repo.encode(Map.of("requestId",id,"task",input.task(),"candidates",candidates.stream().map(p->Map.of(
                "id",p.id(),"runtime",p.settings().runtime(),"model",p.settings().model(),"effort",p.settings().effort(),
                "provider",p.settings().apiProvider(),"protocol",p.settings().apiProtocol())).toList(),
                "settings",modelSettings(settings))).getBytes(StandardCharsets.UTF_8);
        if(request.length>32768)throw new IllegalArgumentException("Manager input is too large.");
        if(!running.tryAcquire())throw new IllegalStateException("The manager is already evaluating another request.");
        try {
            repo.audit(target,null,id,actor,"MANAGER_SELECTION_REQUESTED",manager.id());
            Output output=invoke(settings.python(),request,Output.class);
            if(!id.equals(output.requestId())||output.reason()==null||output.reason().isBlank()||output.reason().length()>512)
                throw new IllegalStateException("Invalid manager response.");
            var selected=candidates.stream().filter(p->p.id().equals(output.profileId())).findFirst()
                    .orElseThrow(()->new IllegalStateException("Manager selected an unavailable profile."));
            if(!profiles.revision().equals(factory.runtimeProfiles().revision()))
                throw new IllegalStateException("Profiles changed during manager inference. Choose again.");
            repo.audit(target,null,id,actor,"MANAGER_SELECTION_RECORDED",selected.id());
            return new Result(id,selected.id(),selected.version(),output.reason().strip());
        } catch(RuntimeException failure) {
            repo.audit(target,null,id,actor,"MANAGER_SELECTION_FAILED",failure instanceof InferenceFailure?failure.getMessage():
                    "Inference failed or the candidate snapshot changed.");
            throw failure;
        } finally {
            running.release();
        }
    }

    public Assessment assessWish(String actor,String wishId,String title,String body,String missing,String answers,String language) {
        if(body==null||body.isBlank())throw new IllegalArgumentException("Describe your wish before asking the manager.");
        return assess(actor,"GOAL:"+wishId,"wish",language,Map.of("title",title,"body",body,"openQuestions",missing,"clarificationAnswers",answers));
    }

    public Assessment reviewSkill(String actor,SkillDraft draft) {
        if(draft==null||draft.title()==null||draft.title().isBlank()||draft.title().length()>160
                ||draft.content()==null||draft.content().isBlank())
            throw new IllegalArgumentException("Enter the Skill name and draft before asking the manager.");
        if(draft.content().length()>32768)
            throw new IllegalArgumentException("Manager input is too large. Shorten the draft and try again.");
        return assess(actor,"PLATFORM_SKILLS","skill",draft.language(),Map.of("title",draft.title(),"content",draft.content()));
    }

    private Assessment assess(String actor,String target,String mode,String language,Map<String,String> draft) {
        var manager=managerProfile(factory.runtimeProfiles());
        var settings=manager.settings();
        String id=UUID.randomUUID().toString();
        byte[] request=repo.encode(Map.of("requestId",id,"mode",mode,"draft",draft,
                "language","zh".equals(language)?"Chinese":"English","settings",modelSettings(settings)))
                .getBytes(StandardCharsets.UTF_8);
        if(request.length>32768)throw new IllegalArgumentException("Manager input is too large. Shorten the draft and try again.");
        if(!running.tryAcquire())throw new IllegalStateException("The manager is already evaluating another request.");
        try {
            repo.audit(target,null,id,actor,"MANAGER_ADVICE_REQUESTED",mode+" "+manager.id());
            var output=invoke(settings.python(),request,Assessment.class);
            boolean validDraft=mode.equals("skill")
                    ? "SKILL".equals(output.kind())&&output.content()!=null&&!output.content().isBlank()
                    : output.content()==null&&output.kind()!=null
                        &&Set.of("WORKFLOW","SKILL","TASK","MIXED","NEEDS_CLARIFICATION").contains(output.kind());
            if(!id.equals(output.requestId())||output.advice()==null||output.advice().isBlank()||output.advice().length()>4000
                    ||!validDraft)
                throw new IllegalStateException("Invalid manager response.");
            repo.audit(target,null,id,actor,"MANAGER_ADVICE_RECORDED",mode+" "+output.kind());
            return output;
        } catch(RuntimeException failure) {
            repo.audit(target,null,id,actor,"MANAGER_ADVICE_FAILED",failure instanceof InferenceFailure?failure.getMessage():"Invalid or unavailable manager response.");
            throw failure;
        } finally {
            running.release();
        }
    }

    private AgentFactorySettings.RuntimeProfile managerProfile(AgentFactorySettings.RuntimeProfiles profiles) {
        var manager=profiles.items().stream().filter(p->p.enabled()&&p.id().equals(profiles.managerDefaultId())).findFirst()
                .orElseThrow(()->new IllegalStateException("Configure an enabled manager profile."));
        if(manager.settings().credentialRef()!=null)
            throw new IllegalStateException("Select a manager profile that uses local Codex or a local API key.");
        return manager;
    }

    private Map<String,String> modelSettings(AgentFactorySettings.Settings settings) {
        return Map.of("provider",settings.runtime(),"model",settings.model(),"effort",settings.effort(),
                "executable",settings.executable(),"apiProvider",settings.apiProvider(),"apiProtocol",settings.apiProtocol());
    }

    private <T> T invoke(String python,byte[] request,Class<T> responseType) {
        stopModelProcesses();
        Path directory=null;
        try {
            directory=Files.createTempDirectory("aiw-manager-");
            var builder=new ProcessBuilder(python,helper.toString()).directory(directory.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            var inherited=new LinkedHashMap<>(builder.environment());builder.environment().clear();
            for(String name:List.of("PATH","SystemRoot","WINDIR","TEMP","TMP","HOME","USERPROFILE","LOCALAPPDATA","APPDATA",
                    "PATHEXT","CODEX_HOME","OPENAI_API_KEY","DEEPSEEK_API_KEY","OPENAI_BASE_URL","SSL_CERT_FILE","SSL_CERT_DIR")) {
                var value=inherited.get(name);if(value!=null)builder.environment().put(name,value);
            }
            builder.environment().put("PYTHONUTF8","1");
            Process process=builder.start();
            remainingProcesses.add(process.toHandle());
            var read=new FutureTask<>(()->{
                try(var stdin=process.getOutputStream()){stdin.write(request);}
                return process.getInputStream().readNBytes(32769);
            });
            Thread.ofVirtual().start(read);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(125);
            byte[] result;
            while(true) {
                observeModelProcesses();
                long remaining=deadline-System.nanoTime();
                if(remaining<=0)throw new TimeoutException();
                try {
                    result=read.get(Math.min(remaining,TimeUnit.MILLISECONDS.toNanos(100)),TimeUnit.NANOSECONDS);
                    break;
                } catch(TimeoutException waiting) {
                    if(System.nanoTime()>=deadline)throw waiting;
                }
            }
            if(result.length>32768||!process.waitFor(2,TimeUnit.SECONDS)||process.exitValue()!=0)
                throw new IllegalStateException("The manager did not return a usable recommendation.");
            var output=repo.decode(new String(result,StandardCharsets.UTF_8),responseType);
            if(output==null)throw new IllegalStateException("The manager returned an empty recommendation.");
            return output;
        } catch(TimeoutException failure) {
            throw new InferenceFailure("Manager inference timed out.");
        } catch(IOException|ExecutionException failure) {
            throw new InferenceFailure("The manager model process was unavailable.");
        } catch(InterruptedException failure) {
            Thread.currentThread().interrupt();throw new IllegalStateException("Manager inference was interrupted.");
        } finally {
            try {stopModelProcesses();}
            finally {if(directory!=null)try {Files.deleteIfExists(directory);} catch(IOException ignored) { }}
        }
    }

    private void observeModelProcesses() {
        for(var handle:List.copyOf(remainingProcesses)) {
            if(handle.isAlive())handle.descendants().forEach(remainingProcesses::add);
        }
    }

    private void stopModelProcesses() {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        boolean interrupted=Thread.interrupted();
        try {
            while(!remainingProcesses.isEmpty()) {
                observeModelProcesses();
                remainingProcesses.removeIf(handle->!handle.isAlive());
                if(remainingProcesses.isEmpty())return;
                if(System.nanoTime()>=deadline)
                    throw new InferenceFailure("Manager process tree exit is unconfirmed. Wait for the prior model process to stop.");
                for(var handle:List.copyOf(remainingProcesses).reversed())handle.destroyForcibly();
                try {Thread.sleep(25);}
                catch(InterruptedException stop) {interrupted=true;}
            }
        } finally {
            if(interrupted)Thread.currentThread().interrupt();
        }
    }
}
