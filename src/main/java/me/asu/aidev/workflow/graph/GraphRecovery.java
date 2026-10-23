package me.asu.aidev.workflow.graph;

import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Recovery consumes DB facts only. It never retries a blocked preparation or unknown effect. */
@Component
public class GraphRecovery {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(GraphRecovery.class);
    private final AuthorityRepository repo;private final GraphRunner runner;private final GraphRecommendations recommendations;
    public GraphRecovery(AuthorityRepository repo,GraphRunner runner,GraphRecommendations recommendations){this.repo=repo;this.runner=runner;this.recommendations=recommendations;}
    @Scheduled(fixedDelayString="${aidev.workflow-graphs.recovery-ms:5000}") public void recover(){
        for(String plan:repo.jdbc().queryForList("SELECT id FROM workflow_graph WHERE state IN ('ACTIVE','CANCELLING')",String.class))try{runner.tick(plan);}catch(RuntimeException e){LOG.warn("Graph {} DB recovery blocked ({})",plan,e.getClass().getSimpleName());}
        for(String request:repo.jdbc().queryForList("SELECT request_id FROM workflow_graph_recommendation WHERE state='WAITING_RESULT'",String.class))try{recommendations.capture(request);}catch(RuntimeException e){LOG.warn("Graph recommendation {} DB recovery blocked ({})",request,e.getClass().getSimpleName());}
    }
}
