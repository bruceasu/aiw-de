package me.asu.aidev.goal.application;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import me.asu.aidev.goal.domain.GoalData.*;
import me.asu.aidev.goal.jdbc.GoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalAttachments {
    private final GoalRepository repo;
    public GoalAttachments(GoalRepository repo) { this.repo = repo; }
    public record Conversion(String status, String text, String detail) {}
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    @Transactional
    public List<Attachment> upload(String goal, String actor, long revision, String filename, byte[] bytes) {
        Draft draft = active(goal, actor);
        if (draft.revision() != revision) throw new IllegalStateException("Goal changed; reload");
        if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024) throw new IllegalArgumentException("Attachment must contain 1 byte to 10 MiB");
        if (filename == null || filename.length() > 256 || filename.contains("\r") || filename.contains("\n"))
            throw new IllegalArgumentException("Invalid filename");
        repo.lock(draft);
        if (repo.attachments(goal, actor).size() >= 20) throw new IllegalStateException("At most 20 attachments per goal");
        repo.jdbc().update("INSERT INTO goal_attachment VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID().toString(), goal,
            filename, Base64.getEncoder().encodeToString(bytes), hash(bytes), "SAVED", "", "Select Convert to extract text locally");
        repo.save(draft, draft.title(), draft.content(), false, false);
        return repo.attachments(goal, actor);
    }
    public Draft active(String id, String actor) {
        Draft draft = repo.get(id, actor);
        if (draft.deleted()) throw new IllegalStateException("Restore the goal before using it");
        return draft;
    }
    public byte[] bytes(String goal, String attachment, String actor) {
        active(goal, actor);
        return repo.jdbc().query("SELECT content_base64 FROM goal_attachment WHERE id=? AND goal_id=?",
            (rs,n) -> Base64.getDecoder().decode(rs.getString(1)), attachment, goal).stream().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
    }
    public Conversion convert(String goal, String id, String actor) {
        Attachment file = repo.attachments(goal, actor).stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
        byte[] bytes = bytes(goal, id, actor);
        String suffix = file.filename().contains(".") ? file.filename().substring(file.filename().lastIndexOf('.')).toLowerCase(Locale.ROOT) : ".txt";
        if (!suffix.matches("\\.[a-z0-9]{1,10}")) suffix = ".txt";
        Path temporary = null;
        Process process = null;
        var reader = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "goal-converter-output"); t.setDaemon(true); return t; });
        try {
            temporary = Files.createTempFile("aiw-goal-", suffix);
            Files.write(temporary, bytes);
            process = new ProcessBuilder("aiw", "file-convert", "-i", temporary.toString(), "-o", "-", "--format", "md")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            Process running = process;
            var output = reader.submit(() -> running.getInputStream().readNBytes(256 * 1024 + 1));
            if (!process.waitFor(30, TimeUnit.SECONDS)) return new Conversion("FAILED", "", "Conversion exceeded 30 seconds; no dependency installation was requested");
            byte[] result = output.get(2, TimeUnit.SECONDS);
            if (process.exitValue() != 0) return new Conversion("FAILED", "", "Converter failed or the format/backend is unavailable (exit " + process.exitValue() + ")");
            if (result.length > 256 * 1024) return new Conversion("FAILED", "", "Converted text exceeds 256 KiB; provide a smaller file");
            String text = new String(result, StandardCharsets.UTF_8).strip();
            boolean visual = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".tif", ".tiff").contains(suffix);
            if (visual) return new Conversion("NEEDS_VISUAL_REVIEW", text, "Text conversion does not establish image understanding. Supply readable text; no vision service is configured by this feature");
            if (text.isBlank()) return new Conversion("EMPTY", "", "No readable text extracted; scanned pages may need OCR or user supplied text");
            return new Conversion("TEXT_EXTRACTED", text, "Text extracted; check tables, images and scanned pages before sharing");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Conversion("FAILED", "", "Conversion interrupted");
        } catch (Exception e) {
            return new Conversion("FAILED", "", "Local converter unavailable or output could not be read; install/configure it separately");
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            reader.shutdownNow();
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) { /* Random local converter input retained on OS cleanup failure. */ }
        }
    }
    @Transactional
    public Draft finish(String goal, String attachment, String actor, Conversion converted) {
        Draft draft = active(goal, actor);
        repo.lock(draft);
        if (repo.jdbc().update("UPDATE goal_attachment SET status=?,extracted=?,detail=? WHERE id=? AND goal_id=?",
            converted.status(), converted.text(), converted.detail(), attachment, goal) != 1) throw new IllegalArgumentException("Attachment not found");
        return repo.save(draft, draft.title(), draft.content(), false, false);
    }
}
