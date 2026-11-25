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
    public record MemberRevision(int version,String content) {}
    public static final String CATALOGUE_CAPABILITY="platform-skill-catalog-v1";
    public record CatalogueVersion(String ref,int version,boolean enabled,String title,String content,String digest,long createdMs) {}
    public record CatalogueEdit(String ref,int expectedVersion,boolean enabled,String title,String content) {}
    @org.springframework.beans.factory.annotation.Autowired private AuthorityBoundary boundary;
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
    public List<String> available() { return builtin.keySet().stream().sorted().toList(); }
    private static final String PLATFORM_REF="custom-[a-z][a-z0-9-]{0,63}";
    public static boolean platformReference(String ref) {return ref!=null&&ref.matches(PLATFORM_REF+"(?:@[1-9][0-9]{0,9})?");}
    public static String referenceId(String selection) {return selection.split("@",2)[0];}
    public List<CatalogueVersion> catalogue(String actor) {
        return repo.jdbc().query("""
                SELECT skill_ref,version,enabled,title,content,digest,created_ms FROM platform_skill_version s
                 WHERE actor=? AND version=(SELECT MAX(v.version) FROM platform_skill_version v
                                           WHERE v.actor=s.actor AND v.skill_ref=s.skill_ref)
                 ORDER BY skill_ref
                """,SkillFiles::readVersion,actor);
    }
    public List<String> available(String actor) {
        var names=new LinkedHashSet<>(available());
        repo.jdbc().query("""
                SELECT s.skill_ref,s.version FROM platform_skill_version s
                 WHERE s.actor=? AND s.enabled AND (
                    SELECT latest.enabled FROM platform_skill_version latest
                    WHERE latest.actor=s.actor AND latest.skill_ref=s.skill_ref ORDER BY latest.version DESC LIMIT 1
                 ) ORDER BY s.skill_ref,s.version DESC
                """,rs->{
            String ref=rs.getString(1);
            names.add(ref);names.add(ref+"@"+rs.getInt(2));
        },actor);
        return List.copyOf(names);
    }
    public List<CatalogueVersion> versions(String actor,String ref) {
        if(ref==null||!ref.matches(PLATFORM_REF))throw new IllegalArgumentException("Invalid platform Skill reference");
        return repo.jdbc().query("""
                SELECT skill_ref,version,enabled,title,content,digest,created_ms
                 FROM platform_skill_version WHERE actor=? AND skill_ref=? ORDER BY version DESC
                """,SkillFiles::readVersion,actor,ref);
    }
    private static CatalogueVersion readVersion(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        return new CatalogueVersion(rs.getString(1),rs.getInt(2),rs.getBoolean(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getLong(7));
    }
    @org.springframework.transaction.annotation.Transactional
    public CatalogueVersion saveCatalogue(String actor,CatalogueEdit edit) {
        if(edit==null||edit.ref()==null||!edit.ref().matches(PLATFORM_REF)||edit.title()==null||edit.title().isBlank()||edit.title().length()>160
                ||edit.content()==null||edit.content().isBlank()||edit.content().indexOf(0)>=0
                ||edit.content().getBytes(StandardCharsets.UTF_8).length>131072)throw new IllegalArgumentException("Invalid platform Skill");
        boundary.lock();
        int prior=repo.jdbc().queryForObject("SELECT COALESCE(MAX(version),0) FROM platform_skill_version WHERE actor=? AND skill_ref=?",
                Integer.class,actor,edit.ref());
        if(edit.expectedVersion()!=prior)throw new IllegalStateException("The Skill changed; reload before editing.");
        int next=Math.incrementExact(prior);String digest=hash(edit.content().getBytes(StandardCharsets.UTF_8));long now=System.currentTimeMillis();
        repo.jdbc().update("INSERT INTO platform_skill_version VALUES(?,?,?,?,?,?,?,?)",actor,edit.ref(),
                next,edit.enabled(),edit.title().strip(),edit.content(),digest,now);
        return new CatalogueVersion(edit.ref(),next,edit.enabled(),edit.title().strip(),edit.content(),digest,now);
    }
    private Bundle fromCatalogue(String actor,String name) {
        String ref=referenceId(name);
        long version=ref.equals(name)?0:Long.parseLong(name.substring(ref.length()+1));
        var selected=repo.jdbc().query("""
                SELECT s.skill_ref,s.version,s.enabled,s.title,s.content,s.digest,s.created_ms
                 FROM platform_skill_version s WHERE s.actor=? AND s.skill_ref=? AND (?=0 OR s.version=?)
                   AND (SELECT latest.enabled FROM platform_skill_version latest
                        WHERE latest.actor=s.actor AND latest.skill_ref=s.skill_ref ORDER BY latest.version DESC LIMIT 1)
                 ORDER BY s.version DESC LIMIT 1
                """,SkillFiles::readVersion,actor,ref,version,version).stream().findFirst().filter(CatalogueVersion::enabled)
                .orElseThrow(()->new IllegalArgumentException("Platform Skill unavailable: "+name));
        byte[] raw=selected.content().getBytes(StandardCharsets.UTF_8);
        if(!hash(raw).equals(selected.digest()))throw new IllegalStateException("Platform Skill content corrupted");
        return bundle(ref,"platform-skill",actor,"",Integer.toString(selected.version()),selected.digest(),List.of(file("SKILL.md",raw)));
    }
    public String defaultText(String ref) {
        var value=builtin.get(ref);if(value==null)throw new IllegalArgumentException("Unknown platform Skill");
        return new String(Base64.getDecoder().decode(value.files().stream().filter(f->f.path().equals("SKILL.md")).findFirst().orElseThrow().base64()),StandardCharsets.UTF_8);
    }
    public void member(String task,List<String> selected) {
        var original=payload(task,null);var packages=new LinkedHashMap<String,Bundle>();
        if(original!=null)original.packages().forEach(b->packages.put(b.ref(),b));
        for(String name:selected){var value=builtin.get(name);if(value==null)throw new IllegalArgumentException("Unknown platform Skill");packages.put(name,value);}
        store(task,new Frozen(List.copyOf(packages.values())),true);
    }
    public void member(String task,List<String> selected,String actor,String memberId,Map<String,MemberRevision> revisions) {
        var original=payload(task,null);var packages=new LinkedHashMap<String,Bundle>();
        if(original!=null)original.packages().forEach(b->packages.put(b.ref(),b));
        if(selected.isEmpty()&&packages.isEmpty())return;
        for(String name:selected) {
            if(platformReference(name)) {
                var selectedVersion=fromCatalogue(actor,name);
                var existing=packages.putIfAbsent(selectedVersion.ref(),selectedVersion);
                if(existing!=null&&!existing.assetVersion().equals(selectedVersion.assetVersion()))
                    throw new IllegalArgumentException("Choose one version of platform Skill "+selectedVersion.ref());
                continue;
            }
            var value=builtin.get(name);if(value==null)throw new IllegalArgumentException("Unknown platform Skill");
            var revision=revisions.get(name);
            if(revision==null) {packages.put(name,value);continue;}
            var files=new ArrayList<>(value.files().stream().filter(f->!f.path().equals("SKILL.md")).toList());
            files.add(file("SKILL.md",revision.content().getBytes(StandardCharsets.UTF_8)));files.sort(Comparator.comparing(File::path));
            String digest=hash(revision.content().getBytes(StandardCharsets.UTF_8));
            packages.put(name,bundle(name,"member-revision",actor,memberId,Integer.toString(revision.version()),digest,files));
        }
        store(task,new Frozen(List.copyOf(packages.values())),true);
    }
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
