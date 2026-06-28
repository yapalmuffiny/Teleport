package ca.zerodev;

import java.util.UUID;

/**
 * A pending teleport request between two players.
 */
public record TeleportRequest(UUID sender, String senderName, UUID target, Type type, long expiresAt) {

    /** Direction of a request: {@link #GOTO} sends the sender to the target; {@link #SUMMON} brings the target to the sender. */
    public enum Type {
        GOTO,
        SUMMON
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }
}
