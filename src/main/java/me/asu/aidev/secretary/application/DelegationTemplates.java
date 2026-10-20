package me.asu.aidev.secretary.application;

import java.util.*;
import me.asu.aidev.secretary.domain.DelegationRule;
import me.asu.aidev.secretary.jdbc.DelegationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DelegationTemplates {
    private final JdbcTemplate jdbc;
    private final DelegationBoundary boundary;
    private final DelegationRepository repo;
    private final DelegationGrantService grants;
    private final DelegationTargets targets;
    public DelegationTemplates(JdbcTemplate jdbc,DelegationBoundary boundary,DelegationRepository repo,DelegationGrantService grants,DelegationTargets targets) {
        this.jdbc=jdbc;this.boundary=boundary;this.repo=repo;this.grants=grants;this.targets=targets;
    }
    public record Template(String id,long revision,String originalText,DelegationRule rule,String sourceGrant,Long sourceRevision) {}
    public List<Template> list(String actor) {
        return jdbc.query("SELECT * FROM delegation_template WHERE owner=? ORDER BY created_ms DESC,id",(rs,n)->new Template(
                rs.getString("id"),rs.getLong("revision"),rs.getString("original_text"),repo.decode(rs.getString("rule_json"),DelegationRule.class),
                rs.getString("source_grant"),rs.getObject("source_revision")==null?null:rs.getLong("source_revision")),actor);
    }
    @Transactional public Template create(String grantId,long revision,String actor) {
        boundary.lock();var grant=grants.owned(grantId,actor);
        if(grant.revision()!=revision) throw new IllegalStateException("Source rule changed; reload");
        Template template=new Template("DT-"+UUID.randomUUID(),1,grant.originalText(),grant.rule(),grant.id(),grant.revision());
        jdbc.update("INSERT INTO delegation_template VALUES(?,?,?,?,?,?,?,?)",template.id(),actor,1,template.originalText(),repo.encode(template.rule()),grantId,revision,System.currentTimeMillis());
        history(template,actor);return template;
    }
    private void history(Template value,String actor) {
        jdbc.update("INSERT INTO delegation_template_history VALUES(?,?,?,?,?)",value.id(),value.revision(),actor,repo.encode(value),System.currentTimeMillis());
        repo.audit(null,null,null,actor,"TEMPLATE_SAVED",value.id()+":"+value.revision()+"; no identity, enablement or consumption copied");
    }
    @Transactional public Template edit(String id,long revision,String original,DelegationRule rule,String actor) {
        boundary.lock();Template old=list(actor).stream().filter(t->t.id().equals(id)).findFirst().orElseThrow(()->new SecurityException("Template is not owned by this operator"));
        if(old.revision()!=revision) throw new IllegalStateException("Template changed; reload");
        if(original==null || original.isBlank() || original.length()>16000) throw new IllegalArgumentException("Original text required");
        targets.validate(rule);
        Template value=new Template(id,revision+1,original,rule,old.sourceGrant(),old.sourceRevision());
        jdbc.update("UPDATE delegation_template SET revision=?,original_text=?,rule_json=? WHERE id=? AND owner=? AND revision=?",
                value.revision(),original,repo.encode(rule),id,actor,revision);
        history(value,actor);return value;
    }
    @Transactional public void delete(String id,long revision,String actor) {
        boundary.lock();
        if(jdbc.update("DELETE FROM delegation_template WHERE id=? AND revision=? AND owner=?",id,revision,actor)!=1)
            throw new IllegalStateException("Template is missing, changed or not owned by this operator");
        repo.audit(null,null,null,actor,"TEMPLATE_DELETED",id+":"+revision+"; historical provenance and active grants preserved");
    }
}
