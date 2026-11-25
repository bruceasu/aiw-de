package me.asu.aidev.execution.tools;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.NoSuchElementException;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class SharedToolConsole {
    private final SharedToolCatalog tools;
    private final SharedToolAccess access;
    private final WebConsoleSecurityService security;

    public SharedToolConsole(SharedToolCatalog tools,SharedToolAccess access,WebConsoleSecurityService security) {
        this.tools=tools;this.access=access;this.security=security;
    }

    @GetMapping("/ui/tools")
    public String page(HttpSession session) {
        security.requireOperator(session);
        return "ui/shared-tools";
    }

    @GetMapping("/api/r1/tools") @ResponseBody
    public SharedToolCatalog.Page search(HttpSession session,@RequestParam(required=false) String q,
            @RequestParam(required=false) SharedToolCatalog.Kind kind,
            @RequestParam(defaultValue="ALL") SharedToolCatalog.State state,
            @RequestParam(required=false) String after,@RequestParam(defaultValue="25") int limit) {
        security.requireOperator(session);
        return tools.search(q,kind,state,after,limit);
    }

    @GetMapping("/api/r1/tools/{id}") @ResponseBody
    public SharedToolCatalog.Entry get(HttpSession session,@PathVariable String id) {
        security.requireAdmin(session);
        return tools.get(id);
    }

    @GetMapping("/api/r1/tools/executable") @ResponseBody
    public java.util.List<SharedToolCatalog.Item> executable(HttpSession session) {
        security.requireAdmin(session);
        return access.executable();
    }

    @PostMapping("/api/r1/tools") @ResponseBody
    public SharedToolCatalog.Entry save(HttpSession session,@RequestBody SharedToolCatalog.Edit edit) {
        security.requireAdmin(session);
        return tools.save(security.actor(session),edit);
    }

    @PostMapping("/api/r1/tools/tasks/{taskId}/revoke") @ResponseBody
    public Map<String,Boolean> revoke(HttpSession session,@PathVariable String taskId,@RequestBody SharedToolAccess.Ref ref) {
        security.requireAdmin(session);
        access.revoke(security.actor(session),taskId,ref);
        return Map.of("revoked",true);
    }

    @GetMapping("/api/r1/tools/tasks/{taskId}/operations") @ResponseBody
    public java.util.List<SharedToolAccess.Receipt> operations(HttpSession session,@PathVariable String taskId) {
        security.requireOperator(session);
        return access.receipts(security.actor(session),taskId);
    }

    @PostMapping("/api/r1/tools/tasks/{taskId}/reconcile") @ResponseBody
    public Map<String,Boolean> reconcile(HttpSession session,@PathVariable String taskId,@RequestBody SharedToolAccess.Reconciliation input) {
        security.requireAdmin(session);
        access.reconcile(security.actor(session),taskId,input);
        return Map.of("reconciled",true);
    }

    @ExceptionHandler(SecurityException.class) @ResponseBody
    public ResponseEntity<?> denied() {
        return ResponseEntity.status(403).body(Map.of("error","当前角色无权访问此工具目录操作。"));
    }

    @ExceptionHandler(NoSuchElementException.class) @ResponseBody
    public ResponseEntity<?> missing() {
        return ResponseEntity.status(404).body(Map.of("error","工具不存在。"));
    }

    @ExceptionHandler(IllegalArgumentException.class) @ResponseBody
    public ResponseEntity<?> invalid(IllegalArgumentException error) {
        return ResponseEntity.unprocessableContent().body(Map.of("error",error.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class) @ResponseBody
    public ResponseEntity<?> changed(IllegalStateException error) {
        return ResponseEntity.status(409).body(Map.of("error",error.getMessage()));
    }
}
