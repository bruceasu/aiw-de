package me.asu.aidev.knowledge.protocol.http;

import me.asu.aidev.knowledge.application.KnowledgeService;
import me.asu.aidev.knowledge.domain.KnowledgeDocument;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/knowledge/documents")
public class KnowledgeController {
    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) { this.knowledge = knowledge; }

    @GetMapping
    public List<KnowledgeDocument> search(
            @RequestParam(required = false) String repositoryRef,
            @RequestParam(required = false) String runId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit) {
        return knowledge.search(repositoryRef, runId, q, limit);
    }

    @GetMapping("/{id}")
    public KnowledgeDocument find(@PathVariable String id) {
        return knowledge.find(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge document not found: " + id));
    }
}
