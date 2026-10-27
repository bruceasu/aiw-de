package me.asu.aidev.execution.config;

import java.nio.file.*;
import java.util.*;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.autonomous.application.WorkspaceRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;

/** Starts only the bundled worker. External workers never enter this process registry. */
@Service
public class ManagedWorkerProvisioner {
    public record Result(boolean starting,String message) {}
    private static final class Slot {Process process;Path status;String worker;long since=System.nanoTime();String failure;String settingsVersion;String role;String repository;boolean stopping;}
    @org.springframework.beans.factory.annotation.Autowired private FactoryWorkerRegistry registrations;
    private boolean closed;
    private final Map<String,Slot> slots=new HashMap<>();
    private final AgentFactorySettings settings;private final WorkspaceRegistry workspaces;
    @org.springframework.beans.factory.annotation.Value("${aidev.worker-protocol.token:}") private String token;
    @org.springframework.beans.factory.annotation.Value("${server.port:8080}") private int port;
    @org.springframework.beans.factory.annotation.Value("${server.servlet.context-path:}") private String contextPath;
    @org.springframework.beans.factory.annotation.Value("${aidev.agent-factory.worker-root:workers/process-python}") private String workerRoot;
    @org.springframework.beans.factory.annotation.Value("${aidev.agent-factory.max-workers:4}") private int maximum;
    public ManagedWorkerProvisioner(AgentFactorySettings settings,WorkspaceRegistry workspaces) {this.settings=settings;this.workspaces=workspaces;}
    public synchronized Result request(String repository,String runtime,boolean commandRequired) {
        return request(repository,runtime,commandRequired,"coder");
    }
    public synchronized Result requestDiscussion(String backend) {
        var config=settings.current();
        if(config==null || !me.asu.aidev.goal.application.GoalTaskPolicy.runtime(backend,config.runtime()+":"))
            return new Result(false,"The selected discussion backend does not match the Agent factory settings.");
        return request(null,config.runtime(),false,"goal-discussion");
    }
    public synchronized Result analysisAvailability(String runtime) {
        var config=settings.current();
        if(closed || config==null || !config.automaticWorkers() || !config.runtime().equals(runtime))
            return new Result(false,"No matching automatic analysis Worker is configured in Agent factory.");
        if(token==null || token.isBlank() || !Files.isRegularFile(Path.of(workerRoot).toAbsolutePath().resolve("managed_worker.py")))
            return new Result(false,"The local analysis Worker installation or connection credentials are unavailable.");
        return new Result(true,"An analysis Worker will start automatically when requested.");
    }
    public synchronized Result requestAnalysis(String runtime) {
        return request(null,runtime,false,"planner");
    }
    public synchronized Result requestNative(String repository,String runtime,String role) {
        if(!Set.of("requirements","architect","planner","coder","stage-reviewer").contains(role))
            throw new IllegalArgumentException("Unsupported workflow role");
        return request(repository,runtime,false,role,false);
    }
    private Result request(String repository,String runtime,boolean commandRequired,String role) {
        return request(repository,runtime,commandRequired,role,"coder".equals(role));
    }
    private Result request(String repository,String runtime,boolean commandRequired,String role,boolean guarded) {
        if(closed)return new Result(false,"平台正在停止，不能启动新的 Worker。");
        var config=settings.current();
        if(config==null||!config.automaticWorkers())return new Result(false,"本机 Worker 自动启动尚未启用。请在 Agent 工厂设置中启用；外部 Worker 可继续自行接入。");
        if(!config.runtime().equals(runtime))return new Result(false,"当前工厂默认运行时已变化，请重新选择模型后准备任务。");
        if(token==null||token.isBlank())return new Result(false,"平台缺少本机 Worker 连接凭证，请检查平台启动配置。");
        if(commandRequired)return new Result(false,"此方法要求命令执行适配器，请接入声明该能力的 Worker；自动 Worker 不隐式授予任意命令权限。");
        Path root=Path.of(workerRoot).toAbsolutePath().normalize();
        if(!Files.isRegularFile(root.resolve("managed_worker.py")))return new Result(false,"未找到随平台安装的 Worker 程序，请检查平台安装目录。");
        String key=compatibilityKey("coder".equals(role) && !guarded?"coder:native":role,repository,config);
        var existing=slots.get(key);
        if(existing!=null) {
            readStatus(existing);
            if(existing.failure!=null && Objects.equals(existing.settingsVersion,settings.version()))return new Result(false,existing.failure);
            if(existing.process!=null&&!existing.process.isAlive()) {
                if(registrations.busy(existing.worker)) return new Result(false,"The previous Worker still has an active lease; wait for recovery before restarting it.");
                slots.remove(key);
            } else if(existing.failure!=null && existing.process==null) {
                slots.remove(key);
            } else {
                if(existing.process!=null && existing.process.isAlive() && registrations.online(existing.worker)) return new Result(true,"A compatible local Worker is already running; its registration is being checked.");
                if(System.nanoTime()-existing.since>120_000_000_000L)return new Result(false,"本机 Worker 尚未完成兼容能力登记。请核对运行环境；工厂不会重复启动同一 Worker。");
                return new Result(true,"正在启动并等待本机 Worker 完成能力登记。");
            }
        }
        long running=slots.values().stream().filter(slot->slot.process==null?slot.failure==null:slot.process.isAlive()).count();
        if(running>=Math.max(1,maximum)) {
            if(slots.values().stream().anyMatch(s -> s.stopping) || releaseIdle())
                return new Result(true,"An unused Worker is draining to make room for this role; preparation will continue.");
            return new Result(false,"平台管理的本机 Worker 已达到数量限制，请等待现有任务完成或接入外部 Worker。");
        }
        String directory=repository==null?"":workspaces.repositoryPath(repository).toAbsolutePath().toString();
        var slot=new Slot();slot.worker="factory-"+key.substring(0,24);slot.settingsVersion=settings.version();slot.role=role;slot.repository=repository;
        if(registrations.busy(slot.worker)) return new Result(false,"The previous Worker still has an active lease; wait for recovery before restarting it.");
        registrations.remember(slot.worker,key);slots.put(key,slot);
        Runnable start=()->start(slot,config,key,repository,directory,root,role,guarded);
        if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {Thread.ofVirtual().name("factory-worker-start").start(start);}
            @Override public void afterCompletion(int status) {if(status!=STATUS_COMMITTED)synchronized(ManagedWorkerProvisioner.this){slots.remove(key,slot);}}
        });
        else Thread.ofVirtual().name("factory-worker-start").start(start);
        return new Result(true,"正在启动并等待本机 Worker 完成能力登记。");
    }
    static String compatibilityKey(String role,String repository,AgentFactorySettings.Settings config) {
        return AuthorityRepository.hash(String.join("\n",role,Objects.toString(repository,""),config.runtime(),config.model(),
                config.effort(),config.python(),config.executable(),config.guardHome()));
    }
    private boolean releaseIdle() {
        for(var entry:new ArrayList<>(slots.entrySet())) {
            var slot=entry.getValue();
            if(slot.process==null || !slot.process.isAlive() || slot.failure!=null || slot.stopping) continue;
            String skill=switch(slot.role) {
                case "goal-discussion" -> "goal-discussion";
                case "requirements" -> "software-requirements-analyst";
                case "architect" -> "solution-architect";
                case "planner" -> "implementation-planner";
                case "stage-reviewer" -> "workflow-stage-reviewer";
                case "coder" -> "coding-work-package-executor";
                default -> null;
            };
            if(skill==null || !registrations.drainIdle(slot.worker,skill,slot.repository)) continue;
            slot.stopping=true;
            Runnable stop=() -> stopIdle(entry.getKey(),slot);
            if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {Thread.ofVirtual().start(stop);}
                @Override public void afterCompletion(int status) {if(status!=STATUS_COMMITTED)synchronized(ManagedWorkerProvisioner.this){slot.stopping=false;}}
            });
            else Thread.ofVirtual().start(stop);
            return true;
        }
        return false;
    }
    private synchronized void stopIdle(String key,Slot slot) {
        try {
            slot.process.descendants().toList().reversed().forEach(ProcessHandle::destroyForcibly);
            slot.process.destroy();
            if(!slot.process.waitFor(1,java.util.concurrent.TimeUnit.SECONDS)) slot.process.destroyForcibly().waitFor(1,java.util.concurrent.TimeUnit.SECONDS);
            if(slot.process.isAlive()) {slot.failure="Idle Worker shutdown did not complete; inspect the process before retrying";slot.stopping=false;return;}
            registrations.stopped(slot.worker);slots.remove(key,slot);
        } catch(InterruptedException error) {Thread.currentThread().interrupt();slot.stopping=false;}
    }
    private synchronized void start(Slot slot,AgentFactorySettings.Settings config,String key,String repository,String directory,Path root,String role,boolean guarded) {
        if(closed){slot.failure="平台正在停止。";return;}
        try {
            var builder=new ProcessBuilder(config.python(),root.resolve("managed_worker.py").toString()).directory(root.toFile());
            var env=builder.environment();env.keySet().removeIf(name->name.startsWith("AIW_PERSONAL_"));
            env.put("AIW_WORKER_TOKEN",token);env.put("AIW_CONTROL_PLANE_URL","http://127.0.0.1:"+port+contextPath);
            env.put("AIW_AGENT_RUNTIME",config.runtime());
            env.remove("AIW_REPOSITORY_REF");env.remove("AIW_REPOSITORY_ROOT");
            if(repository!=null){env.put("AIW_REPOSITORY_REF",repository);env.put("AIW_REPOSITORY_ROOT",directory);}
            slot.worker="factory-"+key.substring(0,24);
            Path statuses=Path.of(System.getProperty("user.home"),".aiw","agent-factory","status");Files.createDirectories(statuses);
            slot.status=statuses.resolve(slot.worker+".json");
            if(Files.isSymbolicLink(slot.status))throw new java.io.IOException("Unsafe status path");
            Files.deleteIfExists(slot.status);
            env.put("AIW_FACTORY_STATUS_FILE",slot.status.toString());env.put("AIW_WORKER_ID",slot.worker);env.put("AIW_PROCESS_ROLE",role);env.put("AIW_WORKER_MAX_CONCURRENCY","1");
            env.remove("AIW_WORKER_CAPABILITIES");
            env.put("AIW_DYNAMIC_CONFIG",Boolean.toString(guarded));env.put("AIW_EXECUTION_GUARD",Boolean.toString(guarded));env.put("AIW_GUARD_COMMAND_ENABLED","false");
            if(!guarded){env.put("AIW_CODEX_SANDBOX","coder".equals(role)?"workspace-write":"read-only");env.put("AIW_AGENT_ALLOW_SHELL","false");}
            if(!config.executable().isEmpty())env.put("AIW_CODEX_PATH",config.executable());
            if(!config.guardHome().isEmpty())env.put("AIW_CODEX_GUARD_HOME",config.guardHome());
            env.put(config.runtime().equals("codex-app-server")?"AIW_CODEX_MODEL":"AIW_OPENAI_MODEL",config.model());
            env.put(config.runtime().equals("codex-app-server")?"AIW_CODEX_REASONING_EFFORT":"AIW_OPENAI_REASONING_EFFORT",config.effort());
            // Never expose subprocess output or inherited credentials in console responses.
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
            slot.process=builder.start();
        } catch(java.io.IOException failure) {slot.failure="无法启动 Python Worker，请检查 Agent 工厂设置中的 Python 路径及本机安装。";}
    }
    private void readStatus(Slot slot) {
        if(slot.failure!=null||slot.status==null)return;
        try {
            if(!Files.isRegularFile(slot.status,LinkOption.NOFOLLOW_LINKS)||Files.size(slot.status)>8192)return;
            var value=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(slot.status));
            if(!Objects.equals(slot.worker,value.path("workerId").asText())||!value.path("state").asText().equals("FAILED"))return;
            String code=value.path("code").asText();
            if(!code.matches("[A-Z][A-Z0-9_]{2,100}"))code="WORKER_INITIALIZATION_FAILED";
            String reason=switch(code) {
                case "PYTHON_DEPENDENCY_MISSING" -> "Python 缺少 Worker 运行依赖，请使用已安装依赖的 Python 环境。";
                case "EXECUTABLE_NOT_FOUND" -> "运行环境找不到必要程序，请核对 Python、Git 和 Codex 的安装路径。";
                case "DEDICATED_GUARDED_CODEX_HOME_REQUIRED","GUARDED_HOME_MUST_BE_DEDICATED" -> "Codex 专业 Worker 的受控登录目录尚未就绪，请在工厂高级设置中选择已有的受控执行环境。";
                default -> "Worker 初始化检查未通过，请核对工厂运行环境。";
            };
            slot.failure=reason+"（"+code+"）";
        } catch(java.io.IOException ignored) { /* An incomplete status does not prove startup failure. */ }
    }
    public synchronized List<Map<String,String>> state() {
        return slots.values().stream().map(slot->{readStatus(slot);return Map.of("workerId",Objects.toString(slot.worker,"pending"),
                "state",slot.failure!=null?"FAILED":slot.process==null?"STARTING":slot.process.isAlive()?"RUNNING":"STOPPED",
                "message",Objects.toString(slot.failure,""));}).toList();
    }
    @jakarta.annotation.PreDestroy public synchronized void close() {
        closed=true;
        for(var slot:slots.values())if(slot.process!=null&&slot.process.isAlive()) {
            var children=slot.process.descendants().toList();
            children.reversed().forEach(ProcessHandle::destroy);slot.process.destroy();
        }
    }
}
