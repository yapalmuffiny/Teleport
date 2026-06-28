package ca.zerodev;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks pending teleport requests, keyed by target then sender, and expires them on a repeating task.
 */
public final class RequestManager {

    private final Teleport plugin;
    private final Map<UUID, Map<UUID, TeleportRequest>> byTarget = new ConcurrentHashMap<>();

    public RequestManager(Teleport plugin) {
        this.plugin = plugin;
    }

    /** Begins the repeating sweep that removes (and notifies) expired requests. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 20L, 20L);
    }

    public boolean hasFrom(UUID target, UUID sender) {
        Map<UUID, TeleportRequest> m = byTarget.get(target);
        return m != null && m.containsKey(sender);
    }

    public void add(TeleportRequest request) {
        byTarget.computeIfAbsent(request.target(), _ -> new ConcurrentHashMap<>())
                .put(request.sender(), request);
    }

    /** Removes and returns the request from {@code sender} to {@code target}, or {@code null} if none is pending. */
    public TeleportRequest take(UUID target, UUID sender) {
        Map<UUID, TeleportRequest> m = byTarget.get(target);
        if (m == null) return null;
        TeleportRequest req = m.remove(sender);
        if (m.isEmpty()) byTarget.remove(target);
        return (req == null || req.isExpired()) ? null : req;
    }

    /** Removes and returns the sole pending request for a target, or {@code null} if there are zero or several. */
    public TeleportRequest takeSingle(UUID target) {
        Map<UUID, TeleportRequest> m = byTarget.get(target);
        if (m == null || m.size() != 1) return null;
        UUID only = m.keySet().iterator().next();
        return take(target, only);
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        byTarget.values().forEach(m -> m.values().removeIf(req -> {
            if (req.expiresAt() > now) return false;
            Player sender = Bukkit.getPlayer(req.sender());
            if (sender != null) {
                Player target = Bukkit.getPlayer(req.target());
                String targetName = target != null ? target.getName() : "player";
                plugin.messages().send(sender, "request-expired-sender", Messages.ph("player", targetName));
            }
            return true;
        }));
        byTarget.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
