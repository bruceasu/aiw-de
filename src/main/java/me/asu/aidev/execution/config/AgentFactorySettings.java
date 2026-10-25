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
    public record Settings(String runtime,String model,String effort,boolean automaticWorkers,String python,String executable,String guardHome) {
        public Settings {
            if(runtime==null||!Set.of("codex-app-server","openai-agents").contains(runtime))throw new IllegalArgumentException("请选择支持的模型运行时。");
            if(model==null||!model.matches("[A-Za-z0-9._-]{1,120}"))throw new IllegalArgumentException("请填写模型名称。");
            if(effort==null||!(runtime.equals("codex-app-server")?Set.of("none","minimal","low","medium","high","xhigh"):Set.of("none","minimal","low","medium","high")).contains(effort))throw new IllegalArgumentException("此运行时不支持所选推理强度。");
            for(String value:List.of(Objects.toString(python,""),Objects.toString(executable,""),Objects.toString(guardHome,"")))
                if(value.length()>2048||value.indexOf('\n')>=0||value.indexOf('\r')>=0||value.indexOf('\0')>=0)throw new IllegalArgumentException("启动路径无效。");
            python=python==null||python.isBlank()?"python":python.strip();executable=Objects.toString(executable,"").strip();guardHome=Objects.toString(guardHome,"").strip();
        }
    }
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final AuthorityGrantService grants;private final AiProfileCatalog catalogue;
    public AgentFactorySettings(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,AiProfileCatalog catalogue) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.catalogue=catalogue;
    }
    private Map<String,Object> latest() {
        var rows=repo.jdbc().queryForList("SELECT id,detail FROM authority_audit WHERE target_key='AGENT_FACTORY' AND event='AGENT_FACTORY_SETTINGS' ORDER BY created_ms DESC,id DESC LIMIT 1");
        return rows.isEmpty()?Map.of():rows.getFirst();
    }
    public Settings current() {var row=latest();return row.isEmpty()?null:repo.decode(String.valueOf(row.get("detail")),Settings.class);}
    public List<AiProfileCatalog.NamedProfile> profiles() {
        var saved=current();
        return saved==null?catalogue.approved(TaskType.EXTERNAL_WORK):List.of(new AiProfileCatalog.NamedProfile("factory-default",
                new AiProfileCatalog.Profile("openai",saved.runtime(),saved.model(),saved.effort(),false,1)));
    }
    public String version() {var row=latest();return row.isEmpty()?catalogue.version():"factory-"+row.get("id");}
    public Map<String,Object> state() {
        grants.identityHuman(boundary.human());
        var result=new LinkedHashMap<String,Object>();result.put("settings",current());result.put("models",profiles());
        return result;
    }
    @Transactional public Map<String,Object> save(Settings input) {
        grants.human(boundary.human(),"A17_CONFIGURATION");boundary.lock();
        if(input==null)throw new IllegalArgumentException("请核对工厂设置。");
        repo.audit("AGENT_FACTORY",null,null,boundary.human(),"AGENT_FACTORY_SETTINGS",repo.encode(input));
        return state();
    }
}
