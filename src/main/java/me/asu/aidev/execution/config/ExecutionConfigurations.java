package me.asu.aidev.execution.config;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.worker.domain.*;
import me.asu.aidev.worker.port.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.execution.config.ConfigurationContract.*;

/** Immutable method/assets, separate from task facts and effective permissions. */
@Service
public class ExecutionConfigurations {
    private me.asu.aidev.execution.skills.SkillFiles skillFiles;
    @org.springframework.beans.factory.annotation.Autowired public void setSkillFiles(me.asu.aidev.execution.skills.SkillFiles value) { skillFiles=value; }
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final ExecutionGuard guard;
    private final WorkerRepository workers;private final Catalogue catalogue;private final ComposedRoles roles;
    public ExecutionConfigurations(AuthorityRepository repo,AuthorityBoundary boundary,ExecutionGuard guard,WorkerRepository workers) {
        this.repo=repo;this.boundary=boundary;this.guard=guard;this.workers=workers;
        try(var stream=getClass().getResourceAsStream("/execution/software-catalog.json")) {
            if(stream==null) throw new IllegalStateException("Bundled software catalogue missing");
            catalogue=repo.decode(new String(stream.readNBytes(1_048_577),java.nio.charset.StandardCharsets.UTF_8),Catalogue.class);
            if(!PROTOCOL.equals(catalogue.protocol())) throw new IllegalStateException("Bundled catalogue protocol mismatch");
        } catch(java.io.IOException error) { throw new IllegalStateException("Cannot read software catalogue",error); }
        roles=new ComposedRoles(repo,catalogue);
    }
    public List<Category> categories() { return List.of(
        new Category("executive-assistant","秘书助理","秘書室","Executive Assistant",true),
        new Category("requirements-review","需求评审","要件審査室","Requirements Review",true),
        new Category("planner","计划编排","計画管理室","Planner",true),
        new Category("agent-factory","Agent 工厂","エージェント管理部","Agent Factory",false),
        new Category("finance-analytics","财务与统计","経理・管理会計部","Finance & Analytics",false),
        new Category("knowledge-management","知识管理","ナレッジ管理部","Knowledge Management",false),
        new Category("market-surveillance","市场与交易监测","市場分析・取引監視部","Market & Trade Surveillance",false),
        new Category("security-operations","信息安全","情報セキュリティ部","Security Operations",false),
        new Category("agent-audit","行为审计","内部監査部","Agent Audit",false),
        new Category("product-engineering","产品与工程","プロダクト・技術部","Product & Engineering",true)); }
    public Catalogue catalogue() { return catalogue; }
    public String currentOwner() {
        if(boundary.human()==null || boundary.delegated()) throw new SecurityException("Authenticated human required");
        var rows=repo.jdbc().queryForList("SELECT id FROM authority_owner WHERE actor=?",String.class,boundary.human());
        if(rows.size()!=1) throw new SecurityException("Bind exactly one current owner first");return rows.getFirst();
    }
    @Transactional public Asset registerAsset(Asset input) {
        boundary.lock();GuardContract.text(input.ownerId(),128);guard.owner(input.ownerId());
        if(input.repositoryRef()==null || input.repositoryRef().isBlank()) throw new IllegalArgumentException("Explicit asset repository scope required");
        String json=repo.encode(input);
        var old=repo.jdbc().queryForList("SELECT asset_json FROM execution_asset WHERE kind=? AND ref=? AND version=? AND owner_id=? AND repository_ref=?",String.class,input.kind(),input.ref(),input.version(),input.ownerId(),input.repositoryRef());
        if(!old.isEmpty()) { if(!old.getFirst().equals(json)) throw new IllegalStateException("Immutable asset version conflict");return input; }
        repo.jdbc().update("INSERT INTO execution_asset VALUES(?,?,?,?,?,?,?)",input.kind(),input.ref(),input.version(),input.digest(),input.ownerId(),input.repositoryRef(),json);
        repo.audit("CONFIG:"+input.ownerId(),null,null,boundary.human(),"DECLARATIVE_ASSET_REGISTERED",input.kind()+":"+input.ref()+":"+input.version()+":"+input.digest());
        return input;
    }
    Asset asset(String kind,AssetRef ref,String owner,String repository) {
        if(kind.equals("skill")) {
            var bundled=catalogue.assets().stream().filter(a->a.kind().equals(kind) && a.reference().equals(ref)).findFirst();
            if(bundled.isPresent()) return bundled.get();
        }
        var rows=repo.jdbc().queryForList("SELECT asset_json FROM execution_asset WHERE kind=? AND ref=? AND version=? AND owner_id=? AND repository_ref=? AND digest=?",String.class,kind,ref.ref(),ref.version(),owner,repository,ref.digest());
        if(rows.size()!=1) throw new IllegalArgumentException("Registered asset/version/access scope not available");
        return repo.decode(rows.getFirst(),Asset.class);
    }
    @Transactional public PreparedConfigRef save(Candidate input) { boundary.lock();guard.owner(input.ownerId());return saveAuthorized(input); }
    PreparedConfigRef saveAuthorized(Candidate input) {
        var content=content(input);String json=repo.encode(content),digest=AuthorityRepository.hash(json);
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>524288) throw new IllegalArgumentException("Configuration exceeds context budget");
        var old=repo.jdbc().queryForList("SELECT content_json,digest FROM execution_configuration WHERE owner_id=? AND id=? AND revision=?",input.ownerId(),input.id(),input.revision());
        if(!old.isEmpty()) {
            String original=String.valueOf(old.getFirst().get("content_json")),originalDigest=String.valueOf(old.getFirst().get("digest"));
            if(!AuthorityRepository.hash(original).equals(originalDigest) || !repo.decode(original,Content.class).equals(content)) throw new IllegalStateException("Immutable configuration revision conflict");
            return ref(input,originalDigest);
        }
        Long previous=repo.jdbc().queryForObject("SELECT COALESCE(MAX(revision),0) FROM execution_configuration WHERE owner_id=? AND id=?",Long.class,input.ownerId(),input.id());
        if(input.revision()!=previous+1) throw new IllegalStateException("Exact next configuration revision required");
        repo.jdbc().update("INSERT INTO execution_configuration VALUES(?,?,?,?,?,?)",input.ownerId(),input.id(),input.revision(),digest,input.repositoryRef(),json);
        repo.audit("CONFIG:"+input.ownerId(),null,null,Objects.toString(boundary.human(),"platform"),"CONFIGURATION_VERSION_SAVED",input.id()+":"+input.revision()+":"+digest);
        return ref(input,digest);
    }
    private Content content(Candidate input) { return content(input,null); }
    private Content content(Candidate input,Asset previewRole) {
        if(categories().stream().noneMatch(c->c.id().equals(input.category()) && c.softwareWorker())) throw new IllegalArgumentException("Responsibility category has no first-phase software method");
        boolean composed=ComposedRoles.CAPABILITY.equals(input.method().roleCapability());
        if(!composed && catalogue.methods().stream().noneMatch(m->m.method().roleCapability().equals(input.method().roleCapability()))) throw new IllegalArgumentException("Unimplemented role capability");
        var expected=new HashSet<>(input.method().protocolSkills());expected.add(input.method().primarySkill());
        if(!expected.equals(new HashSet<>(input.skills().stream().map(AssetRef::ref).toList())) || !expected.contains("engineering-artifact-protocol"))
            throw new IllegalArgumentException("Exact registered primary/protocol Skills required");
        var assets=new ArrayList<Asset>();
        input.skills().forEach(r->assets.add(previewRole!=null && previewRole.reference().equals(r)?previewRole:asset("skill",r,input.ownerId(),input.repositoryRef())));
        input.knowledge().forEach(r->assets.add(asset("knowledge",r,input.ownerId(),input.repositoryRef())));
        if(composed) roles.verify(input,assets);
        return new Content(PROTOCOL,input,List.copyOf(assets));
    }
    @Transactional public ComposedRoles.Preview proposeRole(ComposedRoles.Definition definition) {
        boundary.lock();var compiled=roles.compile(definition,currentOwner());
        content(compiled.candidate(),compiled.asset());
        var reused=existing(compiled.candidate());
        return new ComposedRoles.Preview(ComposedRoles.PROTOCOL,AuthorityRepository.hash(repo.encode(definition)),reused,reused==null?compiled.candidate():null);
    }
    @Transactional public PreparedConfigRef saveRole(ComposedRoles.Definition definition) {
        boundary.lock();var compiled=roles.compile(definition,currentOwner());
        content(compiled.candidate(),compiled.asset());
        registerAsset(compiled.asset());
        return saveAuthorized(compiled.candidate());
    }
    public ComposedRoles.Definition role(PreparedConfigRef reference) {
        var content=repo.decode(get(currentOwner(),reference).contentJson(),Content.class);
        if(!ComposedRoles.CAPABILITY.equals(content.candidate().method().roleCapability())) throw new ComposedRoles.Invalid("NOT_COMPOSED_ROLE");
        return roles.verify(content.candidate(),content.assets());
    }
    public List<ComposedRoles.Choice> roleChoices(String owner) {
        var choices=new ArrayList<ComposedRoles.Choice>();
        for(var row:repo.jdbc().queryForList("SELECT content_json,digest FROM execution_configuration WHERE owner_id=? ORDER BY id,revision",owner)) {
            var content=repo.decode((String)row.get("content_json"),Content.class);var c=content.candidate();
            if(ComposedRoles.CAPABILITY.equals(c.method().roleCapability())) {
                if(!AuthorityRepository.hash((String)row.get("content_json")).equals(row.get("digest"))) throw new IllegalStateException("Stored configuration corrupted");
                var definition=roles.verify(c,content.assets());
                choices.add(new ComposedRoles.Choice(definition.name(),c.method().name(),c.repositoryRef(),ref(c,(String)row.get("digest"))));
            }
        }
        return List.copyOf(choices);
    }
    public ExecutionObservations.Snapshot observations() {
        String owner=currentOwner();
        return new ExecutionObservations(repo).read(owner,binding->{
            var content=repo.decode(get(owner,binding.ref()).contentJson(),Content.class);var candidate=content.candidate();
            return ComposedRoles.CAPABILITY.equals(candidate.method().roleCapability())
                    ?roles.verify(candidate,content.assets()).name():candidate.method().name();
        });
    }
    PreparedConfigRef existing(Candidate input) {
        for(var row:repo.jdbc().queryForList("SELECT content_json,digest FROM execution_configuration WHERE owner_id=? AND repository_ref=? ORDER BY id,revision",input.ownerId(),input.repositoryRef())) {
            var c=repo.decode(String.valueOf(row.get("content_json")),Content.class).candidate();
            if(c.category().equals(input.category()) && c.method().equals(input.method()) && c.skills().equals(input.skills()) && c.knowledge().equals(input.knowledge())
                    && c.toolRequirements().equals(input.toolRequirements()) && c.outputContract().equals(input.outputContract())) return ref(c,String.valueOf(row.get("digest")));
        }
        return null;
    }
    @Transactional public Proposal propose(Assembly assembly) {
        boundary.lock();String owner=currentOwner();var base=defaultCandidate(assembly.defaultRole(),owner,assembly.repositoryRef());
        var skills=new ArrayList<>(base.skills());skills.addAll(assembly.additionalSkills());
        var protocols=new ArrayList<>(base.method().protocolSkills());assembly.additionalSkills().forEach(a->protocols.add(a.ref()));
        var old=base.method();
        var method=new Method(old.name(),old.workerIdPrefix(),old.roleCapability(),old.allowedTaskTypes(),protocols,old.primarySkill(),
                assembly.methodInstruction()==null?old.instruction():old.instruction()+"\nAdditional method guidance:\n"+assembly.methodInstruction(),old.readOnly(),old.resultKind());
        String strategy=assembly.methodInstruction()!=null?"DECLARATIVE_CANDIDATE":assembly.additionalSkills().isEmpty() && assembly.knowledge().isEmpty()?"EXACT_REUSE":"SKILL_COMPOSITION";
        var candidate=new Candidate(assembly.id(),assembly.revision(),owner,assembly.repositoryRef(),assembly.category(),method,skills,assembly.knowledge(),assembly.toolRequirements(),base.outputContract(),"factory:"+strategy.toLowerCase(Locale.ROOT));
        content(candidate);var reused=existing(candidate);
        return new Proposal(reused!=null?"EXACT_REUSE":strategy,reused,reused==null?candidate:null);
    }
    private PreparedConfigRef ref(Candidate c,String digest) { return new PreparedConfigRef(c.id(),c.revision(),digest,PROTOCOL,c.method().resultKind(),c.skills(),c.knowledge(),c.toolRequirements()); }
    public Payload get(String owner,PreparedConfigRef expected) {
        var rows=repo.jdbc().queryForList("SELECT content_json,digest FROM execution_configuration WHERE owner_id=? AND id=? AND revision=?",owner,expected.id(),expected.revision());
        if(rows.size()!=1) throw new IllegalArgumentException("Configuration not registered");var row=rows.getFirst();String json=String.valueOf(row.get("content_json"));
        if(!AuthorityRepository.hash(json).equals(row.get("digest"))) throw new IllegalStateException("Stored configuration corrupted");
        var c=repo.decode(json,Content.class);
        if(!ref(c.candidate(),String.valueOf(row.get("digest"))).equals(expected)) throw new IllegalStateException("Prepared reference mismatch");
        return new Payload(expected,json,null);
    }
    public Candidate defaultCandidate(String role,String owner,String repository) {
        var m=catalogue.methods().stream().filter(method->method.role().equals(role)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown default software method"));
        var ids=new LinkedHashSet<>(m.method().protocolSkills());ids.add(m.method().primarySkill());
        var refs=ids.stream().map(name->catalogue.assets().stream().filter(a->a.ref().equals(name)).findFirst().orElseThrow().reference()).toList();
        var tools=new HashSet<>(Set.of(GuardContract.Tool.MODEL,GuardContract.Tool.READ,GuardContract.Tool.SEARCH,GuardContract.Tool.GIT,GuardContract.Tool.QUESTION));
        if(!m.method().readOnly()) tools.add(GuardContract.Tool.WRITE);
        if(Set.of("verifier","integration-verifier").contains(role)) tools.add(GuardContract.Tool.COMMAND);
        return new Candidate("default-"+role+"-"+AuthorityRepository.hash(repository).substring(0,12),1,owner,repository,m.category(),m.method(),refs,List.of(),tools,
                "Use the registered engineering-artifact-protocol and return "+m.method().resultKind()+". List checks actually run and checks not run.","bundled-python-profile:"+role);
    }
    public boolean managed(String task) { return repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_task_configuration WHERE task_id=?",Long.class,task)>0; }
    public Binding binding(String task) { return repo.decode(repo.jdbc().queryForObject("SELECT binding_json FROM execution_task_configuration WHERE task_id=?",String.class,task),Binding.class); }
    public void bindTask(String task,Binding binding) {
        repo.jdbc().update("INSERT INTO execution_task_configuration VALUES(?,?)",task,repo.encode(binding));
        skillFiles.configured(task,repo.decode(get(binding.configOwner(),binding.ref()).contentJson(),Content.class));
    }
    public void continueBinding(String source,String successor) {
        skillFiles.inherit(source,successor);
        if(managed(source)) repo.jdbc().update("INSERT INTO execution_task_configuration VALUES(?,?)",successor,repo.encode(binding(source)));
    }
    @Transactional public void registerWorker(WorkerRegistration input) {
        boundary.lock();var worker=workers.find(new WorkerId(input.workerId())).orElseThrow();var capabilities=input.capabilities();
        if(!worker.capabilities().containsAll(Set.of(CAPABILITY,GuardContract.CAPABILITY)) || !worker.runtimeType().startsWith(capabilities.runtime()+":")) throw new SecurityException("Verified configuration adapter capability required");
        String json=repo.encode(capabilities);
        repo.jdbc().update("INSERT INTO execution_worker_configuration VALUES(?,?,?) ON CONFLICT(worker_id) DO UPDATE SET capabilities_json=excluded.capabilities_json,capabilities_digest=excluded.capabilities_digest",input.workerId(),json,AuthorityRepository.hash(json));
    }
    public void invalidateWorker(String worker) { repo.jdbc().update("DELETE FROM execution_worker_configuration WHERE worker_id=?",worker); }
    public boolean compatible(Worker worker,Binding binding) {
        if(!worker.capabilities().containsAll(Set.of(CAPABILITY,GuardContract.CAPABILITY,me.asu.aidev.execution.skills.SkillFileContract.CAPABILITY)) || !worker.runtimeType().startsWith(binding.runtime()+":")) return false;
        var rows=repo.jdbc().queryForList("SELECT capabilities_json,capabilities_digest FROM execution_worker_configuration WHERE worker_id=?",worker.id().value());
        if(rows.size()!=1) return false;var row=rows.getFirst();String json=String.valueOf(row.get("capabilities_json"));
        if(!AuthorityRepository.hash(json).equals(row.get("capabilities_digest"))) return false;
        var metadata=repo.decode(json,WorkerCapabilities.class);
        return metadata.runtime().equals(binding.runtime()) && (!binding.ref().toolRequirements().contains(GuardContract.Tool.COMMAND) || worker.capabilities().contains("guarded-command-linux-v1"))
                && (!binding.ref().toolRequirements().contains(GuardContract.Tool.QUESTION) || worker.capabilities().contains(me.asu.aidev.questions.QuestionContract.CAPABILITY)
                    && metadata.protocolVersions().contains(me.asu.aidev.questions.QuestionContract.PROTOCOL));
    }
    public boolean canClaim(String task,Worker worker) {
        var binding=binding(task);if(!compatible(worker,binding)) return false;
        var payload=get(binding.configOwner(),binding.ref());var content=repo.decode(payload.contentJson(),Content.class);var context=guard.context(task);
        var expected=new ArrayList<GuardContract.FrozenRef>();binding.ref().skills().forEach(r->expected.add(r.frozen("skill")));binding.ref().knowledge().forEach(r->expected.add(r.frozen("knowledge")));
        expected.add(new GuardContract.FrozenRef("configuration",binding.ref().id(),Long.toString(binding.ref().revision()),binding.ref().digest()));
        return content.candidate().repositoryRef().equals(context.snapshot().task().repositoryRef()) && context.configurationDigest().equals(binding.ref().digest())
                && context.snapshot().references().stream().filter(r->OWNED_REFERENCE_KINDS.contains(r.kind())).toList().size()==expected.size()
                && new HashSet<>(context.snapshot().references().stream().filter(r->OWNED_REFERENCE_KINDS.contains(r.kind())).toList()).equals(new HashSet<>(expected));
    }
    public void claimed(String task,String attempt,String worker) {
        if(!managed(task)) return;
        if(!canClaim(task,workers.find(new WorkerId(worker)).orElseThrow())) throw new IllegalStateException("Prepared worker capabilities changed");
        repo.jdbc().update("INSERT INTO execution_attempt_configuration SELECT ?,?,worker_id,capabilities_json,capabilities_digest FROM execution_worker_configuration WHERE worker_id=?",attempt,task,worker);
    }
    public Payload payload(String task,String attempt) {
        if(!managed(task)) return null;var b=binding(task);var result=get(b.configOwner(),b.ref());
        var row=repo.jdbc().queryForMap("SELECT capabilities_json,capabilities_digest FROM execution_attempt_configuration WHERE attempt_id=? AND task_id=?",attempt,task);
        String json=String.valueOf(row.get("capabilities_json"));if(!AuthorityRepository.hash(json).equals(row.get("capabilities_digest"))) throw new IllegalStateException("Capability snapshot corrupted");
        return new Payload(result.configRef(),result.contentJson(),repo.decode(json,WorkerCapabilities.class));
    }
    public me.asu.aidev.execution.domain.AiSelection select(String task,String worker,String profile,String version,String reason,String attempt) {
        var payload=payload(task,attempt);var b=binding(task);var registered=repo.jdbc().queryForObject("SELECT capabilities_digest FROM execution_worker_configuration WHERE worker_id=?",String.class,worker);
        if(!registered.equals(AuthorityRepository.hash(repo.encode(payload.workerCapabilities())))) throw new IllegalStateException("Worker resources changed after claim");
        var selected=b.guidance().allowedProfiles().getFirst();
        if(!selected.id().equals(profile) || !b.guidance().version().equals(version)) throw new IllegalArgumentException("Exact prepared AI profile required");
        var p=selected.profile();return new me.asu.aidev.execution.domain.AiSelection(profile,version,p.provider(),p.runtime(),p.model(),p.effort(),p.fast(),"Prepared immutable configuration");
    }
}
