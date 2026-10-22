package me.asu.aidev.approval.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import me.asu.aidev.approval.application.ApprovalService;
import me.asu.aidev.approval.application.DecideApprovalCommand;
import me.asu.aidev.approval.domain.ApprovalChannel;
import me.asu.aidev.approval.domain.ApprovalDecision;
import me.asu.aidev.approval.domain.ApprovalRequest;
import me.asu.aidev.approval.domain.ApprovalRequestId;
import me.asu.aidev.approval.port.ApprovalNotificationPort;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Telegram approval transport using Bot API long polling and inline buttons.
 * Telegram expresses a human decision; ApprovalService remains the authority.
 */
@Component
@ConditionalOnProperty(name = "aidev.approval.telegram.enabled", havingValue = "true")
public class TelegramApprovalAdapter implements ApprovalNotificationPort, SmartLifecycle {
    @org.springframework.beans.factory.annotation.Autowired
    private ObjectProvider<QuestionTelegramTransport> questionTransport;
    public void handleRecordedCallback(String raw) {
        try {handleCallback(new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw));}
        catch(java.io.IOException error) {throw new IllegalArgumentException("Invalid recorded approval callback");}
    }
    private static final Logger LOG = Logger.getLogger(TelegramApprovalAdapter.class.getName());
    private static final String APPROVE_PREFIX = "aiw:a:";
    private static final String REJECT_PREFIX = "aiw:r:";

    private final ObjectProvider<ApprovalService> approvalService;
    private final TelegramBotApiClient telegram;
    private final String defaultChatId;
    private final Set<Long> allowedUserIds;
    private final int pollTimeoutSeconds;
    private final long backoffMillis;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread pollingThread;
    private volatile long offset;

    public TelegramApprovalAdapter(
            ObjectProvider<ApprovalService> approvalService,
            @Value("${aidev.approval.telegram.token:}") String token,
            @Value("${aidev.approval.telegram.api-base:https://api.telegram.org}") String apiBase,
            @Value("${aidev.approval.telegram.default-chat-id:}") String defaultChatId,
            @Value("${aidev.approval.telegram.allowed-user-ids:}") String allowedUserIds,
            @Value("${aidev.approval.telegram.poll-timeout-seconds:30}") int pollTimeoutSeconds,
            @Value("${aidev.approval.telegram.backoff-ms:1000}") long backoffMillis) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Telegram approval is enabled but AIW_TELEGRAM_BOT_TOKEN is empty");
        }
        this.allowedUserIds = parseUserIds(allowedUserIds);
        if (this.allowedUserIds.isEmpty()) {
            throw new IllegalArgumentException("Telegram approval requires AIW_TELEGRAM_ALLOWED_USER_IDS");
        }
        this.approvalService = approvalService;
        this.telegram = new TelegramBotApiClient(apiBase, token.strip());
        this.defaultChatId = normalize(defaultChatId);
        this.pollTimeoutSeconds = Math.max(0, pollTimeoutSeconds);
        this.backoffMillis = Math.max(50, backoffMillis);
    }

    @Override
    public boolean supports(ApprovalRequest request) {
        return request.channel() == ApprovalChannel.TELEGRAM;
    }

    @Override
    public void approvalRequested(ApprovalRequest request) {
        String chatId = resolveChatId(request.channelRef())
                .orElseThrow(() -> new IllegalStateException(
                        "Telegram approval requires channelRef=chat:<id> or AIW_TELEGRAM_CHAT_ID"));
        String text = "AIW approval required\n"
                + "Approval: " + request.id().value() + "\n"
                + "Subject: " + request.subjectType() + "/" + request.subjectId() + "\n"
                + "Source task: " + request.sourceTaskId().value() + "\n"
                + "Result revision: " + request.resultRevision() + "\n"
                + "Requested by: " + request.requestedBy();
        try {
            telegram.sendApprovalMessage(chatId, text, request.id().value());
        } catch (IOException e) {
            throw new IllegalStateException("failed to send Telegram approval message", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telegram approval notification interrupted", e);
        }
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        pollingThread = Thread.ofVirtual().name("aiw-telegram-approval-poller").start(this::pollLoop);
    }

    @Override
    public void stop() {
        running.set(false);
        Thread t = pollingThread;
        if (t != null) t.interrupt();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                var questions=questionTransport.getIfAvailable();
                JsonNode response = telegram.getUpdates(questions==null?offset:questions.offset(), pollTimeoutSeconds);
                for (JsonNode update : response.path("result")) {
                    if(questions!=null) {questions.accept(update);continue;}
                    long updateId = update.path("update_id").asLong(-1);
                    if (updateId >= 0) offset = Math.max(offset, updateId + 1);
                    JsonNode callback = update.path("callback_query");
                    if (!callback.isMissingNode() && !callback.isNull()) {
                        handleCallback(callback);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException | IOException e) {
                if (running.get()) LOG.log(Level.WARNING, "Telegram approval polling failed", e);
                sleepBackoff();
            }
        }
        running.set(false);
    }

    private void handleCallback(JsonNode callback) {
        String callbackId = callback.path("id").asText("");
        long userId = callback.path("from").path("id").asLong(Long.MIN_VALUE);
        String data = callback.path("data").asText("");
        JsonNode message = callback.path("message");
        String chatId = message.path("chat").path("id").isNumber()
                ? message.path("chat").path("id").asText()
                : null;
        long messageId = message.path("message_id").asLong(-1);

        if (!allowedUserIds.contains(userId)) {
            answerQuietly(callbackId, "You are not authorized to approve AIW tasks.", true);
            return;
        }

        ParsedDecision parsed = parseDecision(data).orElse(null);
        if (parsed == null) {
            answerQuietly(callbackId, "Invalid or expired AIW approval action.", true);
            return;
        }

        ApprovalService service = approvalService.getObject();
        ApprovalRequest request = service.find(parsed.approvalRequestId()).orElse(null);
        if (request == null || request.channel() != ApprovalChannel.TELEGRAM) {
            answerQuietly(callbackId, "Approval request not found.", true);
            return;
        }
        if (!chatMatches(request, chatId)) {
            answerQuietly(callbackId, "This approval belongs to another Telegram chat.", true);
            return;
        }

        String actor = "telegram:user:" + userId;
        String note = parsed.decision() == ApprovalDecision.APPROVE
                ? "Approved from Telegram inline button"
                : "Rejected from Telegram inline button";
        try {
            ApprovalRequest decided = service.decide(new DecideApprovalCommand(
                    parsed.approvalRequestId(), parsed.decision(), actor, note));
            String confirmation = decided.status().name().equals("APPROVED") ? "Approved." : "Rejected.";
            answerQuietly(callbackId, confirmation, false);
            if (chatId != null && messageId >= 0) clearKeyboardQuietly(chatId, messageId);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Telegram approval decision failed", e);
            answerQuietly(callbackId, "Decision was not accepted: " + safeMessage(e), true);
        }
    }

    private boolean chatMatches(ApprovalRequest request, String actualChatId) {
        Optional<String> expected = resolveChatId(request.channelRef());
        if (expected.isEmpty() || actualChatId == null) return true;
        String value = expected.get();
        if (value.startsWith("@")) return true;
        return value.equals(actualChatId);
    }

    private void answerQuietly(String callbackId, String text, boolean alert) {
        if (callbackId == null || callbackId.isBlank()) return;
        try {
            telegram.answerCallbackQuery(callbackId, text, alert);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "failed to answer Telegram callback query", e);
        }
    }

    private void clearKeyboardQuietly(String chatId, long messageId) {
        try {
            telegram.clearInlineKeyboard(chatId, messageId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "failed to clear Telegram approval buttons", e);
        }
    }

    private Optional<String> resolveChatId(String channelRef) {
        String ref = normalize(channelRef);
        if (ref != null) {
            if (ref.startsWith("chat:")) return Optional.of(ref.substring("chat:".length()).strip());
            return Optional.of(ref);
        }
        return Optional.ofNullable(defaultChatId);
    }

    private static Optional<ParsedDecision> parseDecision(String data) {
        if (data.startsWith(APPROVE_PREFIX)) {
            return id(data.substring(APPROVE_PREFIX.length()), ApprovalDecision.APPROVE);
        }
        if (data.startsWith(REJECT_PREFIX)) {
            return id(data.substring(REJECT_PREFIX.length()), ApprovalDecision.REJECT);
        }
        return Optional.empty();
    }

    private static Optional<ParsedDecision> id(String raw, ApprovalDecision decision) {
        String id = raw == null ? "" : raw.strip();
        if (!id.startsWith("APR-") || id.length() > 50) return Optional.empty();
        try {
            return Optional.of(new ParsedDecision(new ApprovalRequestId(id), decision));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Set<Long> parseUserIds(String raw) {
        Set<Long> out = new HashSet<>();
        if (raw == null || raw.isBlank()) return out;
        Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .forEach(s -> out.add(Long.parseLong(s)));
        return Set.copyOf(out);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String v = value.strip();
        return v.isEmpty() ? null : v;
    }

    private static String safeMessage(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private record ParsedDecision(ApprovalRequestId approvalRequestId, ApprovalDecision decision) {}
}
