package me.asu.aidev.workflow.application;

import java.util.List;
import org.springframework.stereotype.Service;

/** UI projection: code delivery is performed and reported by Workers. */
@Service
public class WorkflowGitDeliveryService {
    public record Item(String repositoryRef,String taskId,String baseRevision,String resultRevision,List<String> files,String state,String message) {}
    public record View(String state,List<Item> items) {}
    public View view(String id) { return new View("NOT_REQUIRED",List.of()); }
    public boolean pending(String id) { return false; }
}
