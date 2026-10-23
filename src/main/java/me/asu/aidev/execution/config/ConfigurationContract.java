package me.asu.aidev.execution.config;

import java.util.*;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.execution.guard.GuardContract;
import static me.asu.aidev.execution.guard.GuardContract.*;

/** Declarative content only. No generated executable tools, credentials or grants. */
public final class ConfigurationContract {
    public static final String PROTOCOL="aiw.execution.config.v1", CAPABILITY="execution-config-v1";
    public static final Set<String> OWNED_REFERENCE_KINDS=Set.of("configuration","skill","knowledge");
    public static Set<String> requiredCapabilities(String repository) { return Set.of(CAPABILITY,GuardContract.CAPABILITY,me.asu.aidev.execution.skills.SkillFileContract.CAPABILITY,"repo:"+repository); }
    private ConfigurationContract() {}
    public enum State { NEEDS_CONFIGURATION, PREPARING, READY, BLOCKED }
    public enum Error { INVALID_CONFIGURATION, UNSUPPORTED_RUNTIME, PREPARATION_FAILED, NO_COMPATIBLE_WORKER, AUTHORIZATION_REQUIRED, BUDGET_CONFIGURATION_REQUIRED, SOURCE_CHANGED }
    public record Category(String id,String chinese,String japanese,String english,boolean softwareWorker) {}
    public record AssetRef(String ref,String version,String digest) {
        public AssetRef { id(ref);text(version,64);hash(digest); }
        public FrozenRef frozen(String kind) { return new FrozenRef(kind,ref,version,digest); }
    }
    public record Asset(String kind,String ref,String version,String digest,String ownerId,String repositoryRef,String content) {
        public Asset { if(!Set.of("skill","knowledge").contains(kind)) throw new IllegalArgumentException("Unknown asset kind");
            id(ref);text(version,64);hash(digest);text(content,131072);
            if(!me.asu.aidev.authority.AuthorityRepository.hash(content).equals(digest)) throw new IllegalArgumentException("Asset digest mismatch");
            if(kind.equals("knowledge")) { text(ownerId,128);text(repositoryRef,120); }
        }
        public AssetRef reference() { return new AssetRef(ref,version,digest); }
    }
    public record Method(String name,String workerIdPrefix,String roleCapability,List<String> allowedTaskTypes,
                         List<String> protocolSkills,String primarySkill,String instruction,boolean readOnly,String resultKind) {
        public Method { id(name);id(workerIdPrefix);id(roleCapability);id(primarySkill);text(instruction,8192);
            allowedTaskTypes=List.copyOf(allowedTaskTypes);protocolSkills=List.copyOf(protocolSkills);
            if(!allowedTaskTypes.equals(List.of("EXTERNAL_WORK")) || !Set.of("REPORT","VERDICT","WORKSPACE").contains(resultKind)
                    || readOnly==resultKind.equals("WORKSPACE") || protocolSkills.isEmpty() || protocolSkills.size()>8 || new HashSet<>(protocolSkills).size()!=protocolSkills.size())
                throw new IllegalArgumentException("Invalid software method/output contract");
            protocolSkills.forEach(ConfigurationContract::id);
        }
    }
    public record DefaultMethod(String role,String category,Method method) {}
    public record Catalogue(String protocol,List<DefaultMethod> methods,List<Asset> assets) {}
    public record Candidate(String id,long revision,String ownerId,String repositoryRef,String category,Method method,
                            List<AssetRef> skills,List<AssetRef> knowledge,Set<Tool> toolRequirements,String outputContract,String source) {
        public Candidate { ConfigurationContract.id(id);text(ownerId,128);text(repositoryRef,120);ConfigurationContract.id(category);
            Objects.requireNonNull(method);skills=List.copyOf(skills);knowledge=List.copyOf(knowledge);toolRequirements=Set.copyOf(toolRequirements);
            if(revision<1 || skills.isEmpty() || skills.size()>16 || knowledge.size()>8 || !toolRequirements.containsAll(Set.of(Tool.MODEL,Tool.GIT))
                    || method.readOnly() && toolRequirements.contains(Tool.WRITE) || !method.readOnly() && !toolRequirements.contains(Tool.WRITE))
                throw new IllegalArgumentException("Invalid declarative requirements");
            if(skills.stream().map(AssetRef::ref).distinct().count()!=skills.size() || knowledge.stream().map(AssetRef::ref).distinct().count()!=knowledge.size())
                throw new IllegalArgumentException("Duplicate immutable reference");
            text(outputContract,8192);text(source,512);
        }
    }
    public record PreparedConfigRef(String id,long revision,String digest,String protocol,String resultKind,
                                    List<AssetRef> skills,List<AssetRef> knowledge,Set<Tool> toolRequirements) {}
    public record Content(String protocol,Candidate candidate,List<Asset> assets) {}
    public record Payload(PreparedConfigRef configRef,String contentJson,WorkerCapabilities workerCapabilities) {}
    public record WorkerCapabilities(List<String> protocolVersions,String runtime,Map<String,String> adapterVersions,String platform,String resourcePolicyDigest) {
        public WorkerCapabilities { protocolVersions=List.copyOf(protocolVersions);adapterVersions=Map.copyOf(adapterVersions);
            if(!protocolVersions.containsAll(List.of(PROTOCOL,GuardContract.PROTOCOL)) || !Set.of("openai-agents","codex-app-server").contains(runtime)
                    || adapterVersions.isEmpty() || adapterVersions.size()>16) throw new IllegalArgumentException("Unsupported runtime capability");
            text(platform,80);hash(resourcePolicyDigest);adapterVersions.forEach((k,v)->{text(k,80);text(v,256);});
            if(runtime.equals("codex-app-server") && !Set.of("linux","darwin").contains(platform)) throw new IllegalArgumentException("Codex requires Unix/WSL");
        }
    }
    public record WorkerRegistration(String workerId,WorkerCapabilities capabilities) { public WorkerRegistration {text(workerId,128);Objects.requireNonNull(capabilities);} }
    public record Intent(String intentId,long intentVersion,long expectedGeneration,String planId,long policyRevision,String repositoryRef,
                         String baseRevision,String instruction,List<String> dependencies,String runtime,String aiProfileId,
                         String defaultRole,PreparedConfigRef configRef,Candidate candidate) {
        public Intent { id(intentId);text(planId,128);text(repositoryRef,120);text(instruction,16384);id(aiProfileId);
            if(intentVersion<1 || expectedGeneration<0 || policyRevision<1 || baseRevision==null || !baseRevision.matches("[0-9a-f]{40}|[0-9a-f]{64}"))
                throw new IllegalArgumentException("Fixed intent version and repository base required");
            dependencies=List.copyOf(dependencies);if(dependencies.size()>128 || new HashSet<>(dependencies).size()!=dependencies.size()) throw new IllegalArgumentException("Invalid dependencies");
            dependencies.forEach(v->text(v,128));
            if(!Set.of("openai-agents","codex-app-server").contains(runtime)) throw new IllegalArgumentException("Unsupported runtime");
            if((defaultRole==null?0:1)+(configRef==null?0:1)+(candidate==null?0:1)!=1) throw new IllegalArgumentException("Choose one configuration source");
        }
    }
    public record Preparation(String intentId,long intentVersion,long generation,State state,PreparedConfigRef configRef,Error errorCode,
                              String taskId,List<String> missing,List<String> recoveryActions) {}
    public record Assembly(String id,long revision,String repositoryRef,String category,String defaultRole,List<AssetRef> additionalSkills,
                           List<AssetRef> knowledge,Set<Tool> toolRequirements,String methodInstruction) {
        public Assembly { ConfigurationContract.id(id);text(repositoryRef,120);ConfigurationContract.id(category);ConfigurationContract.id(defaultRole);
            additionalSkills=List.copyOf(additionalSkills);knowledge=List.copyOf(knowledge);toolRequirements=Set.copyOf(toolRequirements);
            if(revision<1 || additionalSkills.size()>6 || knowledge.size()>8) throw new IllegalArgumentException("Bounded declaration required");
            if(methodInstruction!=null) text(methodInstruction,4096);
        }
    }
    public record Proposal(String strategy,PreparedConfigRef reused,Candidate candidate) {}
    public record Binding(String configOwner,PreparedConfigRef ref,String runtime,AiProfileCatalog.Guidance guidance) {}
    public static void id(String value) { if(value==null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,95}")) throw new IllegalArgumentException("Registered identifier required"); }
}
