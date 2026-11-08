package me.asu.aidev.goal.application;

import java.util.*;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.execution.domain.AiSelection;
import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.goal.jdbc.GoalRepository;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.worker.domain.Worker;
import org.springframework.stereotype.Component;

@Component
public class GoalTaskPolicy implements WorkerTaskClaimPolicy {
    public record Frozen(String backend, String version, List<AiProfileCatalog.NamedProfile> profiles,String apiProtocol,List<GoalImages.Image> images,boolean oauth) {
        public Frozen(String backend,String version,List<AiProfileCatalog.NamedProfile> profiles) {this(backend,version,profiles,null,List.of(),false);}
        public Frozen(String backend,String version,List<AiProfileCatalog.NamedProfile> profiles,String apiProtocol) {this(backend,version,profiles,apiProtocol,List.of(),false);}
        public Frozen {
            images=images==null?List.of():List.copyOf(images);
            if(apiProtocol==null&&version!=null&&version.startsWith("factory-")&&profiles.stream().anyMatch(p->p.profile().runtime().equals("openai-agents")))apiProtocol="responses";
        }
    }
    private final GoalRepository repo;
    private final AiProfileCatalog profiles;
    @org.springframework.beans.factory.annotation.Autowired
    private me.asu.aidev.execution.config.AgentFactorySettings factorySettings;
    public GoalTaskPolicy(GoalRepository repo, AiProfileCatalog profiles) { this.repo = repo; this.profiles = profiles; }
    public boolean managed(WorkTask task) { return "goal-discussion".equals(task.subjectType()); }
    public static boolean runtime(String backend, String runtime) {
        if ("deepseek".equals(backend)) return runtime.startsWith("openai-agents:deepseek:");
        if ("openai".equals(backend)) return !runtime.startsWith("openai-agents:deepseek:") && List.of("openai-agents:", "openai-api:").stream().anyMatch(runtime::startsWith);
        return "codex".equals(backend) && List.of("codex-cli:", "codex-app-server:").stream().anyMatch(runtime::startsWith);
    }
    public Frozen freeze(String backend) {
        var snapshot=factorySettings==null?null:factorySettings.snapshot();
        var settings=snapshot==null?null:snapshot.settings();
        boolean factory=settings!=null && runtime(backend,settings.workerRuntime());
        var selected = (factory?List.of(new AiProfileCatalog.NamedProfile("factory-default",new AiProfileCatalog.Profile(
            settings.apiProvider(),settings.runtime(),settings.model(),settings.effort(),false,1))):profiles.approved(TaskType.EXTERNAL_WORK)).stream()
            .filter(p -> "deepseek".equals(backend)?p.profile().provider().equals("deepseek")&&p.profile().runtime().equals("openai-agents")
                :p.profile().provider().equals("openai")&&runtime(backend,p.profile().runtime()+":"))
            .filter(p -> me.asu.aidev.workflow.application.RecommendationProfiles.compatible(p.profile())).toList();
        if ((factory || profiles.hasPolicy(TaskType.EXTERNAL_WORK)) && selected.isEmpty())
            throw new IllegalStateException("No approved AI profile matches the selected backend");
        if ("deepseek".equals(backend)&&selected.isEmpty())throw new IllegalStateException("请先在 Agent 工厂保存 DeepSeek 配置，或配置明确的 DeepSeek AI Profile 与外部 Worker。");
        return new Frozen(backend, factory?snapshot.version():profiles.version(), selected,factory&&settings.runtime().equals("openai-agents")?settings.apiProtocol():null,
            List.of(),factory&&settings.credentialRef()!=null);
    }
    public Frozen frozen(WorkTask task) {
        return repo.jdbc().query("SELECT frozen_json FROM goal_request WHERE id=?", (rs,n) -> repo.decode(rs.getString(1), Frozen.class),
            task.id().value()).stream().findFirst().orElseThrow(() -> new IllegalStateException("Frozen goal request missing"));
    }
    public Frozen images(Frozen frozen,List<GoalImages.Image> images) {
        if(images.isEmpty())return frozen;
        if(!Set.of("openai","deepseek").contains(frozen.backend()))throw new IllegalArgumentException("图片讨论需要 API Key SDK Worker；当前 Codex 通道未接入图片。");
        if(frozen.oauth())
            throw new IllegalArgumentException("当前 ChatGPT OAuth 讨论通道未接入图片，请选择 API Key SDK 连接。");
        var selected=frozen.profiles().stream().filter(p->p.profile().runtime().equals("openai-agents")
            && !(p.profile().provider().equals("deepseek")&&p.profile().model().equals("deepseek-v4-pro"))).toList();
        if(selected.isEmpty())throw new IllegalArgumentException("请先明确配置可接收图片的 SDK 模型；DeepSeek V4 Pro 文档未声明图片处理能力。");
        return new Frozen(frozen.backend(),frozen.version(),selected,frozen.apiProtocol(),images,false);
    }
    public boolean matches(Frozen f, String runtime) {
        return runtime(f.backend(), runtime)
            && (f.backend().equals("codex")||me.asu.aidev.modelauth.ModelCapabilities.protocolMatches(runtime,f.backend(),f.apiProtocol()))
            && (f.profiles().isEmpty() || f.profiles().stream().anyMatch(p -> runtime.startsWith(p.profile().runtime() + ":")));
    }
    @Override public boolean canClaim(WorkTask task) { return true; }
    @Override public boolean canClaim(WorkTask task, Worker worker) {
        if(!managed(task))return true;
        var frozen=frozen(task);
        return matches(frozen,worker.runtimeType())&&(frozen.images().isEmpty()||worker.capabilities().contains(GoalImages.CAPABILITY));
    }
    public AiProfileCatalog.Guidance guidance(WorkTask task, String runtime) {
        Frozen f = frozen(task);
        if (!matches(f, runtime)) throw new IllegalArgumentException("Worker does not match frozen goal backend");
        var allowed = f.profiles().stream().filter(p -> runtime.startsWith(p.profile().runtime() + ":")).toList();
        return allowed.isEmpty() ? null : new AiProfileCatalog.Guidance(f.version(), allowed, null, allowed.getFirst().id());
    }
    public AiSelection select(WorkTask task, String runtime, String id, String version, String reason) {
        Frozen f = frozen(task);
        if (!matches(f, runtime)) throw new IllegalArgumentException("Worker does not match frozen goal backend");
        if (f.profiles().isEmpty()) {
            if (id != null || version != null || reason != null) throw new IllegalArgumentException("Goal uses Worker defaults");
            return null;
        }
        if (!Objects.equals(f.version(), version) || reason == null || reason.isBlank() || reason.length() > 512)
            throw new IllegalArgumentException("Frozen profile version and selection reason are required");
        var chosen = f.profiles().stream().filter(p -> p.id().equals(id) && runtime.startsWith(p.profile().runtime() + ":")).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Profile is outside the frozen goal backend"));
        var p = chosen.profile();
        return new AiSelection(chosen.id(), f.version(), p.provider(), p.runtime(), p.model(), p.effort(), p.fast(), reason.strip());
    }
}
