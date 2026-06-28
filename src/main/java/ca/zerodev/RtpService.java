package ca.zerodev;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Finds safe random-teleport locations and tracks per-player cooldowns.
 */
public final class RtpService {

    private static final Set<Material> UNSAFE = Set.of(
            Material.LAVA, Material.WATER, Material.FIRE, Material.MAGMA_BLOCK,
            Material.CACTUS, Material.POWDER_SNOW, Material.WITHER_ROSE, Material.SWEET_BERRY_BUSH
    );

    private static final int NETHER_SCAN_TOP = 110;
    private static final int NETHER_SCAN_BOTTOM = 8;

    private final Teleport plugin;
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();

    public RtpService(Teleport plugin) {
        this.plugin = plugin;
    }

    /** Seconds the player must still wait before teleporting again, or {@code 0} if they may go now. */
    public long cooldownRemaining(Player player) {
        if (player.hasPermission("teleport.rtp.cooldown.bypass")) return 0;
        long until = cooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        long remaining = until - System.currentTimeMillis();
        return remaining > 0 ? (remaining + 999) / 1000 : 0;
    }

    public void markUsed(Player player) {
        long seconds = plugin.getConfig().getLong("rtp.cooldown-seconds", 30);
        cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    /** Returns a safe location to drop into within the configured radius, or {@code null} if none was found. */
    public Location findSafe(World world) {
        int min = plugin.getConfig().getInt("rtp.min-radius", 500);
        int max = plugin.getConfig().getInt("rtp.max-radius", 5000);
        int attempts = plugin.getConfig().getInt("rtp.max-attempts", 50);
        if (max < min) max = min;

        Location center = world.getSpawnLocation();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < attempts; i++) {
            double angle = rnd.nextDouble(Math.PI * 2);
            double dist = min + rnd.nextDouble(Math.max(1, max - min));
            int x = (int) Math.round(center.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * dist);
            Location loc = safeColumn(world, x, z);
            if (loc != null) return loc;
        }
        return null;
    }

    private Location safeColumn(World world, int x, int z) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            for (int y = NETHER_SCAN_TOP; y >= NETHER_SCAN_BOTTOM; y--) {
                Block ground = world.getBlockAt(x, y, z);
                if (isSafeSpot(ground)) return centered(ground);
            }
            return null;
        }
        int y = world.getHighestBlockYAt(x, z);
        Block ground = world.getBlockAt(x, y, z);
        return isSafeSpot(ground) ? centered(ground) : null;
    }

    private boolean isSafeSpot(Block ground) {
        if (!ground.getType().isSolid() || UNSAFE.contains(ground.getType())) return false;
        Block feet = ground.getRelative(0, 1, 0);
        Block head = ground.getRelative(0, 2, 0);
        return feet.isPassable() && head.isPassable()
                && !UNSAFE.contains(feet.getType()) && !UNSAFE.contains(head.getType());
    }

    private Location centered(Block ground) {
        return new Location(ground.getWorld(),
                ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
    }
}
