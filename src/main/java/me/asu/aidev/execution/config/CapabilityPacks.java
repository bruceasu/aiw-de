package me.asu.aidev.execution.config;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.GuardContract.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.execution.config.ConfigurationContract.*;
import static me.asu.aidev.execution.guard.GuardContract.text;

/** Reusable declarative Skill groups, stored through the original private immutable asset boundary. */
@Service
public class CapabilityPacks {
    public static final String PROTOCOL="aiw.capability.pack.v1";
    public record Definition(String protocol,String name,String repositoryRef,String description,List<AssetRef> skills,
                             String work,List<Tool> toolRequirements) {
        public Definition {
            if(!PROTOCOL.equals(protocol))throw new IllegalArgumentException("Unknown capability pack protocol");
            text(name,120);text(repositoryRef,120);text(description,500);text(work,2000);
            if(skills==null||toolRequirements==null)throw new IllegalArgumentException("Explicit Skill and tool lists required");
            skills=List.copyOf(skills);toolRequirements=toolRequirements.stream().sorted().toList();
            if(skills.isEmpty()||skills.size()>6||skills.stream().map(AssetRef::ref).distinct().count()!=skills.size()
                    ||skills.stream().anyMatch(s->s.ref().equals("engineering-artifact-protocol")||s.ref().matches("role-[0-9a-f]{64}"))
                    ||toolRequirements.isEmpty()||toolRequirements.size()>7||new HashSet<>(toolRequirements).size()!=toolRequirements.size())
                throw new IllegalArgumentException("A bounded non-nested Skill group is required");
        }
    }
    public record Save(String requestId,Definition definition) {}
    public record Lookup(String requestId,String repositoryRef) {}
    public record Choice(AssetRef reference,Definition definition) {}
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final ExecutionConfigurations configs;
    public CapabilityPacks(AuthorityRepository repo,AuthorityBoundary boundary,ExecutionConfigurations configs) {this.repo=repo;this.boundary=boundary;this.configs=configs;}
    private String ref(String owner,String request) {text(request,80);return "cap-pack-"+AuthorityRepository.hash(owner+":"+request).substring(0,20);}
    private Choice choice(Asset asset) {
        var definition=repo.decode(asset.content(),Definition.class);
        if(!asset.repositoryRef().equals(definition.repositoryRef()))throw new IllegalStateException("Pack repository does not match its private asset");
        return new Choice(asset.reference(),definition);
    }
    @Transactional public Choice save(Save input) {
        boundary.lock();String owner=configs.currentOwner();var definition=Objects.requireNonNull(input.definition());
        for(var skill:definition.skills())configs.asset("skill",skill,owner,definition.repositoryRef());
        String content=repo.encode(definition);
        var asset=new Asset("knowledge",ref(owner,input.requestId()),"1",AuthorityRepository.hash(content),owner,definition.repositoryRef(),content);
        return choice(configs.registerAsset(asset));
    }
    public Object lookup(Lookup input) {
        String owner=configs.currentOwner();text(input.repositoryRef(),120);
        var rows=repo.jdbc().queryForList("SELECT asset_json FROM execution_asset WHERE kind='knowledge' AND ref=? AND version='1' AND owner_id=? AND repository_ref=?",String.class,ref(owner,input.requestId()),owner,input.repositoryRef());
        return rows.isEmpty()?Map.of("found",false):Map.of("found",true,"pack",choice(repo.decode(rows.getFirst(),Asset.class)));
    }
    public Object list() {
        String owner=configs.currentOwner();var packs=new ArrayList<Choice>();
        var rows=repo.jdbc().queryForList("SELECT asset_json FROM execution_asset WHERE kind='knowledge' AND ref LIKE 'cap-pack-%' AND version='1' AND owner_id=? ORDER BY ref LIMIT 500",String.class,owner);
        for(String raw:rows) {
            var asset=repo.decode(raw,Asset.class);
            try {packs.add(choice(asset));} catch(IllegalArgumentException invalid) { /* Existing ordinary knowledge may share this prefix. */ }
        }
        return Map.of("protocol",PROTOCOL,"ownerId",owner,"packs",List.copyOf(packs),"limit",500);
    }
}
