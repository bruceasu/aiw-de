package me.asu.aidev.execution.skills;

import java.nio.charset.StandardCharsets;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.config.ConfigurationContract;
import me.asu.aidev.work.domain.*;
import org.springframework.stereotype.Service;
import static me.asu.aidev.execution.skills.SkillFileContract.*;

/** One platform source; task bindings store the complete immutable bytes, never a latest pointer. */
@Service
public class SkillFiles {
    public record Index(Map<String,List<String>> files,Map<String,List<String>> profiles) {}
    public record Frozen(List<Bundle> packages) { public Frozen { packages=List.copyOf(packages); } }
    private final AuthorityRepository repo;
    private final Map<String,Bundle> builtin=new HashMap<>();
    private final Index index;
    public SkillFiles(AuthorityRepository repo) {
        this.repo=repo;index=repo.decode(resource("/worker-skills/index.json"),Index.class);
        var catalogue=repo.decode(resource("/execution/software-catalog.json"),ConfigurationContract.Catalogue.class);
        index.files().forEach((name,paths)->{
            var asset=catalogue.assets().stream().filter(a->a.kind().equals("skill") && a.ref().equals(name)).findFirst().orElseThrow();
            var files=paths.stream().map(p->{SkillFileContract.path(p);return file(p,resourceBytes("/worker-skills/"+name+"/"+p));}).toList();
            builtin.put(name,bundle(name,"platform-builtin","","",asset.version(),asset.digest(),files));
        });
    }
    private byte[] resourceBytes(String name) {
        try(var s=getClass().getResourceAsStream(name)) {
            if(s==null) throw new IllegalStateException("Platform Skill resource missing: "+name);
            byte[] bytes=s.readNBytes(4_194_305);if(bytes.length>4_194_304) throw new IllegalStateException("Skill resource too large");return bytes;
        } catch(java.io.IOException e) { throw new IllegalStateException("Cannot read platform Skill",e); }
    }
    private String resource(String name) { return new String(resourceBytes(name),StandardCharsets.UTF_8); }
    public void created(WorkTask task) {
        if(task.type()!=TaskType.EXTERNAL_WORK) return;
        var names=index.profiles().get(task.skillRef());
        if(names==null) {
            // Configured tasks are completed by bindTask in this same preparation transaction.
            if(!task.requiredCapabilities().contains(ConfigurationContract.CAPABILITY)) throw new IllegalArgumentException("Unregistered platform Skill profile");
            return;
        }
        store(task.id().value(),new Frozen(names.stream().map(builtin::get).toList()),false);
    }
    public void configured(String task,ConfigurationContract.Content content) {
        var c=content.candidate();var packages=new ArrayList<Bundle>();
        for(var a:content.assets()) if(a.kind().equals("skill")) {
            var b=builtin.get(a.ref());
            if(b!=null && b.assetVersion().equals(a.version()) && b.assetDigest().equals(a.digest())) packages.add(b);
            else {
                if(!Objects.equals(a.ownerId(),c.ownerId()) || !Objects.equals(a.repositoryRef(),c.repositoryRef())) throw new SecurityException("Skill scope differs from configuration");
                List<File> files=List.of(file("SKILL.md",a.content().getBytes(StandardCharsets.UTF_8)));
                if(a.content().stripLeading().startsWith("{")) {
                    try { var document=repo.decode(a.content(),Document.class);files=document.files(); }
                    catch(IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid explicit Skill file-set document",invalid); }
                }
                packages.add(bundle(a.ref(),"platform-asset",c.ownerId(),c.repositoryRef(),a.version(),a.digest(),files));
            }
        }
        store(task,new Frozen(packages),true);
    }
    private void store(String task,Frozen frozen,boolean replaceDefault) {
        if(frozen.packages().isEmpty() || frozen.packages().size()>16 || frozen.packages().stream().map(Bundle::ref).distinct().count()!=frozen.packages().size()
                || frozen.packages().stream().flatMap(b->b.files().stream()).mapToLong(File::size).sum()>8_388_608) throw new IllegalArgumentException("Task Skill budget invalid");
        String json=repo.encode(frozen),digest=AuthorityRepository.hash(json);
        var old=repo.jdbc().queryForList("SELECT binding_json,digest,configured FROM execution_task_skills WHERE task_id=?",task);
        if(!old.isEmpty()) {
            var row=old.getFirst();if(digest.equals(row.get("digest"))) return;
            if(!replaceDefault || ((Number)row.get("configured")).intValue()!=0
                    || repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_attempt WHERE task_id=?",Long.class,task)>0)
                throw new IllegalStateException("Immutable task Skill binding conflict");
            repo.jdbc().update("UPDATE execution_task_skills SET binding_json=?,digest=?,configured=1 WHERE task_id=?",json,digest,task);return;
        }
        repo.jdbc().update("INSERT INTO execution_task_skills(task_id,binding_json,digest,configured) VALUES(?,?,?,?)",task,json,digest,replaceDefault?1:0);
    }
    public boolean managed(String task) { return repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_task_skills WHERE task_id=?",Long.class,task)>0; }
    public Payload payload(String task,String attempt) {
        if(!managed(task)) return null;var row=repo.jdbc().queryForMap("SELECT binding_json,digest FROM execution_task_skills WHERE task_id=?",task);
        String json=String.valueOf(row.get("binding_json"));if(!AuthorityRepository.hash(json).equals(row.get("digest"))) throw new IllegalStateException("Task Skill binding corrupted");
        var frozen=repo.decode(json,Frozen.class);return new Payload(PROTOCOL,task,attempt,frozen.packages().stream().map(Bundle::reference).toList(),frozen.packages());
    }
    public void inherit(String source,String successor) {
        if(!managed(source)) return;
        var row=repo.jdbc().queryForMap("SELECT binding_json,digest,configured FROM execution_task_skills WHERE task_id=?",source);
        if(!AuthorityRepository.hash(String.valueOf(row.get("binding_json"))).equals(row.get("digest"))) throw new IllegalStateException("Original Skill binding corrupted");
        var frozen=repo.decode(String.valueOf(row.get("binding_json")),Frozen.class);
        String repository=repo.jdbc().queryForObject("SELECT repository_ref FROM work_task WHERE id=?",String.class,successor);
        if(frozen.packages().stream().anyMatch(b->b.source().equals("platform-asset") && !Objects.equals(repository,b.repositoryRef())))
            throw new SecurityException("Replacement cannot transfer repository-scoped Skill assets");
        if(managed(successor)) {
            var old=repo.jdbc().queryForMap("SELECT digest FROM execution_task_skills WHERE task_id=?",successor);
            if(!Objects.equals(old.get("digest"),row.get("digest"))) throw new IllegalStateException("Successor already has different fixed Skills");return;
        }
        repo.jdbc().update("INSERT INTO execution_task_skills(task_id,binding_json,digest,configured) VALUES(?,?,?,1)",successor,row.get("binding_json"),row.get("digest"));
    }
}
