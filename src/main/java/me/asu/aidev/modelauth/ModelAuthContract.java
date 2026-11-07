package me.asu.aidev.modelauth;

import java.util.Set;
import me.asu.aidev.execution.guard.GuardContract.Lease;

/** No secret-bearing value is an ordinary connection projection. */
public final class ModelAuthContract {
    private ModelAuthContract() {}
    public enum Kind { CHATGPT_RESPONSES, CODEX_APP_SERVER }
    public record Connection(String id,Kind kind,String label,String state,long version,long generation,long expiresAt) {}
    public record Selection(String id,long version) {}
    public record ResetLocalStorage(String expectedRevision,boolean confirmClear) {}
    public record Disconnection(Connection connection,boolean remoteRevocationConfirmed) {}
    public record Login(String connectionId,Long expectedVersion,String label) {}
    public record Import(String connectionId,Long expectedVersion,String label,String content) {
        @Override public String toString() {return "Import[redacted]";}
    }
    public record Access(Lease lease,String connectionId,long connectionVersion,String model,Long rejectedGeneration) {}
    public record TokenSet(String accessToken,String refreshToken,String idToken,String scope) {
        @Override public String toString() {return "TokenSet[redacted]";}
    }
    public record Issued(TokenSet tokens,String clientId,String subject,String accountId,String planType,
                         long expiresAt,long earliestRefreshAt,boolean inferenceAllowed) {
        @Override public String toString() {return "Issued[redacted]";}
    }
    /** This value may leave Plane only on the authenticated, exact-lease endpoint. */
    public record AccessToken(String accessToken,long expiresAt,long generation,Kind kind,String accountId,String planType) {
        @Override public String toString() {return "AccessToken[redacted]";}
    }
    public static final Set<String> READY=Set.of("READY","IDENTITY_ONLY");
}
