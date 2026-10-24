package me.asu.aidev.execution.config;

import java.nio.charset.StandardCharsets;
import java.util.*;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.guard.GuardContract.Tool;
import me.asu.aidev.execution.skills.SkillFileContract;
import static me.asu.aidev.execution.config.ConfigurationContract.*;
import static me.asu.aidev.execution.guard.GuardContract.text;

/** A role is declared data compiled to the existing immutable execution contract. */
public final class ComposedRoles {
    public static final String PROTOCOL="aiw.role.composition.v1", CAPABILITY="composed-role-v1";
    public static final String ENGINEERING="engineering-artifact-protocol";
    public static final class Invalid extends IllegalArgumentException {
        public Invalid(String reason) { super(reason); }
    }
    public record Step(String key,String title,List<AssetRef> skills,String instruction,String whenCondition,String completion) {
        public Step {
            ConfigurationContract.id(key);text(title,120);text(instruction,2000);text(completion,1000);
            whenCondition=whenCondition==null?"":whenCondition;
            if(whenCondition.length()>500 || whenCondition.indexOf('\0')>=0) throw new Invalid("STEP_CONDITION_INVALID");
            skills=List.copyOf(skills);
            if(skills.isEmpty() || skills.size()>6 || skills.stream().map(AssetRef::ref).distinct().count()!=skills.size())
                throw new Invalid("STEP_SKILLS_INVALID");
        }
    }
    public record Definition(String protocol,String id,long revision,String repositoryRef,String category,String name,
                             String goal,String delivery,String resultKind,AssetRef protocolSkill,List<Step> steps,
                             List<AssetRef> knowledge,Set<Tool> toolRequirements) {
        public Definition {
            if(!PROTOCOL.equals(protocol)) throw new Invalid("ROLE_PROTOCOL_INVALID");
            ConfigurationContract.id(id);ConfigurationContract.id(category);text(repositoryRef,120);
            text(name,120);text(goal,4000);text(delivery,2000);name=name.strip();
            if(name.contains("\n") || name.contains("\r")) throw new Invalid("ROLE_NAME_INVALID");
            if(revision<1 || !Set.of("REPORT","VERDICT","WORKSPACE").contains(resultKind)) throw new Invalid("ROLE_OUTPUT_INVALID");
            if(protocolSkill==null || !ENGINEERING.equals(protocolSkill.ref())) throw new Invalid("ROLE_PROTOCOL_INVALID");
            steps=List.copyOf(steps);knowledge=List.copyOf(knowledge);
            if(steps.isEmpty() || steps.size()>8 || steps.stream().map(Step::key).distinct().count()!=steps.size()
                    || knowledge.size()>8 || knowledge.stream().map(AssetRef::ref).distinct().count()!=knowledge.size())
                throw new Invalid("ROLE_LIMIT_EXCEEDED");
            toolRequirements=Collections.unmodifiableSet(EnumSet.copyOf(toolRequirements));
            if(!toolRequirements.containsAll(Set.of(Tool.MODEL,Tool.GIT))
                    || toolRequirements.contains(Tool.WRITE)!=resultKind.equals("WORKSPACE")) throw new Invalid("ROLE_TOOLS_INVALID");
        }
    }
    public record Compiled(Definition definition,Asset asset,Candidate candidate) {}
    public record Preview(String protocol,String definitionDigest,PreparedConfigRef reused,Candidate candidate) {}
    public record Choice(String name,String methodName,String repositoryRef,PreparedConfigRef reference) {}
    private final AuthorityRepository repo;
    private final Catalogue catalogue;
    public ComposedRoles(AuthorityRepository repo,Catalogue catalogue) { this.repo=repo;this.catalogue=catalogue; }

    public Compiled compile(Definition d,String owner) {
        String json=repo.encode(d);
        if(json.getBytes(StandardCharsets.UTF_8).length>24576) throw new Invalid("ROLE_LIMIT_EXCEEDED");
        var selected=new LinkedHashMap<String,AssetRef>();
        for(var step:d.steps()) for(var skill:step.skills()) {
            if(skill.ref().equals(ENGINEERING) || skill.ref().matches("role-[0-9a-f]{64}")) throw new Invalid("STEP_SKILLS_INVALID");
            var prior=selected.putIfAbsent(skill.ref(),skill);
            if(prior!=null && !prior.equals(skill)) throw new Invalid("SKILL_VERSION_CONFLICT");
            // Legacy role entry points keep their output and input restrictions.
            if(catalogue.methods().stream().filter(m->m.method().primarySkill().equals(skill.ref()))
                    .anyMatch(m->!m.method().resultKind().equals(d.resultKind()) || m.method().readOnly()==d.resultKind().equals("WORKSPACE")))
                throw new Invalid("INCOMPATIBLE_OUTPUT");
        }
        if(selected.size()>6) throw new Invalid("ROLE_LIMIT_EXCEEDED");
        String ref="role-"+AuthorityRepository.hash(owner+"\n"+d.repositoryRef()+"\n"+d.id());
        String entry=entry(d,ref);
        var files=new SkillFileContract.Document(SkillFileContract.PROTOCOL,List.of(
                SkillFileContract.file("SKILL.md",entry.getBytes(StandardCharsets.UTF_8)),
                SkillFileContract.file("role.json",json.getBytes(StandardCharsets.UTF_8))));
        String content=repo.encode(files);
        var asset=new Asset("skill",ref,"r"+d.revision(),AuthorityRepository.hash(content),owner,d.repositoryRef(),content);
        var references=new ArrayList<AssetRef>();references.add(d.protocolSkill());references.addAll(selected.values());
        var protocols=references.stream().map(AssetRef::ref).toList();references.add(asset.reference());
        var method=new Method(ref+"-r"+d.revision(),"composed",CAPABILITY,List.of("EXTERNAL_WORK"),protocols,ref,
                "Follow the fixed role entry and its ordered Skill steps. Preserve the assigned task, tools and output contract.",
                !d.resultKind().equals("WORKSPACE"),d.resultKind());
        var candidate=new Candidate(d.id(),d.revision(),owner,d.repositoryRef(),d.category(),method,references,d.knowledge(),d.toolRequirements(),
                "Use the registered engineering-artifact-protocol and return "+d.resultKind()+". Keep its outer schema unchanged.\nRole delivery requirements:\n"+d.delivery(),
                "factory:role-composition");
        return new Compiled(d,asset,candidate);
    }

    public Definition verify(Candidate candidate,List<Asset> assets) {
        var asset=assets.stream().filter(a->a.kind().equals("skill") && a.ref().equals(candidate.method().primarySkill())).findFirst()
                .orElseThrow(()->new Invalid("ROLE_ASSET_MISMATCH"));
        var document=repo.decode(asset.content(),SkillFileContract.Document.class);
        var file=document.files().stream().filter(f->f.path().equals("role.json")).findFirst()
                .orElseThrow(()->new Invalid("ROLE_ASSET_MISMATCH"));
        var definition=repo.decode(new String(Base64.getDecoder().decode(file.base64()),StandardCharsets.UTF_8),Definition.class);
        var compiled=compile(definition,candidate.ownerId());
        if(!compiled.candidate().equals(candidate) || !compiled.asset().equals(asset)) throw new Invalid("ROLE_ASSET_MISMATCH");
        return definition;
    }

    private String entry(Definition d,String ref) {
        var out=new StringBuilder("---\nname: "+ref+"\ndescription: Follow the fixed composed role and its ordered Skill methods for the assigned task.\n---\n\n# "+d.name()+"\n\n");
        out.append("Use this role for the assigned task only. This is one Worker task, not a new process or a platform workflow.\n\n## Goal\n\n")
                .append(d.goal()).append("\n\n## Delivery\n\n").append(d.delivery())
                .append("\n\nReturn ").append(d.resultKind()).append(" using the existing output contract.\n\n## Ordered work\n\n");
        int number=0;
        for(var step:d.steps()) {
            out.append("### ").append(++number).append(". ").append(step.title()).append("\n\n")
                    .append("Skill references: ").append(String.join(", ",step.skills().stream().map(a->a.ref()+"@"+a.version()+" ("+a.digest()+")").toList())).append(".\n")
                    .append("When: ").append(step.whenCondition().isBlank()?"Always within this task":step.whenCondition()).append("\n")
                    .append("Work: ").append(step.instruction()).append("\n")
                    .append("Completion: ").append(step.completion()).append("\n\n");
        }
        return out.append("## Execution and evidence\n\n")
                .append("Follow the steps in order and use the declared Skill entry points and indexed resources. Conditions are instructions for your reasoning, not executable expressions. ")
                .append("Mark a step not applicable only with a reason. If a required input, tool, permission or prior result is missing, report the affected step as blocked through the existing question/gap path. ")
                .append("Continue only independent work already in scope; do not invent completion.\n\n")
                .append("A legacy professional Skill keeps its input, source, task, read-only and output restrictions. This role does not waive them. ")
                .append("Shared methods supplement this role. If natural-language instructions conflict, report the conflict and stop dependent work rather than claiming success or switching roles.\n\n")
                .append("Keep the fixed task facts, repository scope, tool authority and model configuration. Selecting a Skill grants no tools or external access. ")
                .append("Do not create tasks, dispatch other Workers or change platform status. Cross-Worker orchestration belongs to the platform.\n\n")
                .append("In existing result description fields, record each step as completed, not applicable or blocked, with actual artifacts and checks. ")
                .append("These notes are not platform step states or proof of enforced Skill calls. Preserve failures, unknown operations and remaining budgets. ")
                .append("Do not claim independent review of your own work or mark an unrun check passed. Finish only when the assigned delivery is supported, otherwise report the precise gap.\n").toString();
    }
}
