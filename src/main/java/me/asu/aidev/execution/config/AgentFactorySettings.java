package me.asu.aidev.execution.config;

import java.util.*;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.authority.*;
import me.asu.aidev.work.domain.TaskType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operator-owned factory defaults. No model credentials or task authority are stored here. */
@Service
public class AgentFactorySettings {
    // Resolve connection validation only when saving; OAuth reads these settings during operation.
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.beans.factory.ObjectProvider<me.asu.aidev.modelauth.ModelConnections> modelConnections;
    public record Settings(String runtime,String model,String effort,boolean automaticWorkers,String python,String executable,String guardHome,String credentialRef,Long credentialVersion,String apiProvider,String apiProtocol) {
        public Settings(String runtime,String model,String effort,boolean automaticWorkers,String python,String executable,String guardHome,String credentialRef,Long credentialVersion) {
            this(runtime,model,effort,automaticWorkers,python,executable,guardHome,credentialRef,credentialVersion,"openai","responses");
        }
        public Settings(String runtime,String model,String effort,boolean automaticWorkers,String python,String executable,String guardHome) {
            this(runtime,model,effort,automaticWorkers,python,executable,guardHome,null,null);
        }
        public Settings {
            apiProvider=apiProvider==null?"openai":apiProvider;
            apiProtocol=apiProtocol==null?"responses":apiProtocol;
            if(!Set.of("openai","deepseek").contains(apiProvider)||!Set.of("responses","chat-completions").contains(apiProtocol))throw new IllegalArgumentException("请选择支持的模型服务商与 API 协议。");
            if(!"openai-agents".equals(runtime)&&(!apiProvider.equals("openai")||!apiProtocol.equals("responses")))throw new IllegalArgumentException("Codex 不使用 API 服务商与协议设置。");
            if(credentialRef!=null&&!credentialRef.isBlank()&&(!apiProvider.equals("openai")||!apiProtocol.equals("responses")))throw new IllegalArgumentException("ChatGPT OAuth 仅用于 OpenAI Responses，不能用于其他服务商或协议。");
            if(credentialRef!=null&&credentialRef.isBlank())credentialRef=null;
            if(credentialRef==null)credentialVersion=null;
            else if(!credentialRef.matches("MC-[0-9a-f-]{36}")||credentialVersion==null||credentialVersion<1)throw new IllegalArgumentException("MODEL_CONNECTION_REQUIRED");
            if(runtime==null||!Set.of("codex-app-server","openai-agents").contains(runtime))throw new IllegalArgumentException("请选择支持的模型运行时。");
            if(!me.asu.aidev.modelauth.ModelAuthContract.modelName(model))throw new IllegalArgumentException("请填写模型名称。");
            var efforts=runtime.equals("codex-app-server")?Set.of("none","minimal","low","medium","high","xhigh")
                :apiProvider.equals("deepseek")?Set.of("default","none","low","high","max"):Set.of("default","none","minimal","low","medium","high","xhigh","max");
            if(effort==null||!efforts.contains(effort))throw new IllegalArgumentException("此服务商与协议不支持所选推理强度。");
            for(String value:List.of(Objects.toString(python,""),Objects.toString(executable,""),Objects.toString(guardHome,"")))
                if(value.length()>2048||value.indexOf('\n')>=0||value.indexOf('\r')>=0||value.indexOf('\0')>=0)throw new IllegalArgumentException("启动路径无效。");
            python=python==null||python.isBlank()?"python":python.strip();executable=Objects.toString(executable,"").strip();guardHome=Objects.toString(guardHome,"").strip();
        }
        public String workerRuntime() {return runtime+":"+(runtime.equals("openai-agents")&&(apiProvider.equals("deepseek")||apiProtocol.equals("chat-completions"))?apiProvider+":"+apiProtocol+":":"");}
    }
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final AuthorityGrantService grants;private final AiProfileCatalog catalogue;
    public AgentFactorySettings(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,AiProfileCatalog catalogue) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.catalogue=catalogue;
    }
    private Map<String,Object> latest() {
        var rows=repo.jdbc().queryForList("SELECT id,detail FROM authority_audit WHERE target_key='AGENT_FACTORY' AND event='AGENT_FACTORY_SETTINGS' ORDER BY created_ms DESC,id DESC LIMIT 1");
        return rows.isEmpty()?Map.of():rows.getFirst();
    }
    public record Snapshot(Settings settings,String version) {}
    public record RuntimeProfile(String id,long version,boolean enabled,Settings settings) {}
    public record RuntimeProfiles(String revision,String defaultId,String managerDefaultId,List<RuntimeProfile> items) {}
    public record ProfileChange(String expectedRevision,String id,Settings settings,boolean enabled,boolean taskDefault,boolean managerDefault) {}
    public RuntimeProfiles runtimeProfiles() {
        var rows=repo.jdbc().queryForList("""
                SELECT detail FROM authority_audit WHERE target_key='AGENT_FACTORY' AND event='AGENT_FACTORY_PROFILES'
                 ORDER BY created_ms DESC,id DESC LIMIT 1
                """);
        if(!rows.isEmpty())return repo.decode(String.valueOf(rows.getFirst().get("detail")),RuntimeProfiles.class);
        var row=latest();
        if(row.isEmpty())return new RuntimeProfiles("initial",null,null,List.of());
        var original=repo.decode(String.valueOf(row.get("detail")),Settings.class);
        return new RuntimeProfiles("factory-"+row.get("id"),"factory-default","factory-default",
                List.of(new RuntimeProfile("factory-default",1,true,original)));
    }
    public Snapshot snapshot() {
        var state=runtimeProfiles();
        return state.items().stream().filter(p->p.id().equals(state.defaultId())).findFirst()
                .map(p->new Snapshot(p.settings(),p.id()+"-"+p.version()))
                .orElse(new Snapshot(null,catalogue.version()));
    }
    public Settings current() {return snapshot().settings();}
    public List<AiProfileCatalog.NamedProfile> profiles() {
        var saved=current();
        return saved==null?catalogue.approved(TaskType.EXTERNAL_WORK):List.of(new AiProfileCatalog.NamedProfile("factory-default",
                new AiProfileCatalog.Profile(saved.apiProvider(),saved.runtime(),saved.model(),saved.effort(),false,1)));
    }
    public String version() {return snapshot().version();}
    public Map<String,Object> state() {
        grants.identityHuman(boundary.human());
        var result=new LinkedHashMap<String,Object>();result.put("settings",current());result.put("models",profiles());result.put("runtimeProfiles",runtimeProfiles());
        return result;
    }
    @Transactional public Map<String,Object> save(Settings input) {
        grants.human(boundary.human(),"A17_CONFIGURATION");boundary.lock();
        var state=runtimeProfiles();
        update(new ProfileChange(state.revision(),state.defaultId()==null?"factory-default":state.defaultId(),input,true,true,false),state);
        return state();
    }
    @Transactional public Map<String,Object> saveProfile(ProfileChange input) {
        grants.human(boundary.human(),"A17_CONFIGURATION");boundary.lock();
        update(input,runtimeProfiles());
        return state();
    }
    private void update(ProfileChange input,RuntimeProfiles prior) {
        if(input==null||input.settings()==null||input.id()==null||!input.id().matches("[a-z][a-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("请检查运行配置名称和设置。");
        if(!prior.revision().equals(input.expectedRevision()))throw new IllegalStateException("工厂配置已变化，请重新读取后编辑。");
        var config=input.settings();
        if(input.enabled()&&config.credentialRef()!=null)
            modelConnections.getObject().selection(new me.asu.aidev.modelauth.ModelAuthContract.Selection(config.credentialRef(),config.credentialVersion()),config.runtime());
        var items=new ArrayList<>(prior.items());
        var existing=items.stream().filter(p->p.id().equals(input.id())).findFirst().orElse(null);
        if(existing==null&&items.size()>=32)throw new IllegalStateException("工厂最多可登记 32 个运行配置。");
        String defaultId=input.taskDefault()||prior.defaultId()==null?input.id():prior.defaultId();
        String managerId=input.managerDefault()||prior.managerDefaultId()==null?input.id():prior.managerDefaultId();
        if(!input.enabled()&&(input.id().equals(defaultId)||input.id().equals(managerId)))
            throw new IllegalArgumentException("请先为任务和经理设置其他启用的默认配置。");
        if(existing!=null)items.remove(existing);
        long version=existing==null?1:existing.settings().equals(config)?existing.version():Math.incrementExact(existing.version());
        items.add(new RuntimeProfile(input.id(),version,input.enabled(),config));
        items.sort(Comparator.comparing(RuntimeProfile::id));
        var state=new RuntimeProfiles(UUID.randomUUID().toString(),defaultId,managerId,List.copyOf(items));
        // The authority lock serializes revisions. Preserve their read order even within one clock tick.
        long last=repo.jdbc().queryForObject("""
                SELECT COALESCE(MAX(created_ms),0) FROM authority_audit
                 WHERE target_key='AGENT_FACTORY' AND event='AGENT_FACTORY_PROFILES'
                """,Long.class);
        repo.jdbc().update("INSERT INTO authority_audit VALUES(?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),
                "AGENT_FACTORY",null,null,boundary.human(),"AGENT_FACTORY_PROFILES",repo.encode(state),
                Math.max(System.currentTimeMillis(),Math.incrementExact(last)));
    }
}
