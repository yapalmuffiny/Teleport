package ca.zerodev;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static ca.zerodev.Messages.ph;

/**
 * Handles every teleport action through a single {@code /tp} command, dispatching on its arguments.
 */
public final class TpCommand implements TabExecutor {

    private static final Set<String> SELF_TOKENS = Set.of("me", "@s", "self", "here");
    private static final Set<String> PLAYER_ARG_SUBCOMMANDS =
            Set.of("accept", "deny", "decline", "request", "summon");
    private static final int LOOK_RANGE = 32;

    private final Teleport plugin;
    private final Messages msg;

    public TpCommand(Teleport plugin) {
        this.plugin = plugin;
        this.msg = plugin.messages();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("teleport.use")) {
            msg.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            msg.send(sender, "usage");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> { handleReload(sender); return true; }
            case "help" -> { msg.send(sender, "usage"); return true; }
            case "random", "rtp" -> { handleRtp(sender, args); return true; }
            case "look" -> { handleLook(sender, args); return true; }
            case "request" -> { handleSendRequest(sender, args, TeleportRequest.Type.GOTO); return true; }
            case "summon" -> { handleSendRequest(sender, args, TeleportRequest.Type.SUMMON); return true; }
            case "accept" -> { handleResponse(sender, args, true); return true; }
            case "deny", "decline" -> { handleResponse(sender, args, false); return true; }
            default -> { }
        }

        if (Coords.isTriplet(args, 0) && args.length == 3) {
            handleSelfToCoords(sender, args);
            return true;
        }

        if (args[0].startsWith("@")) {
            handleEntitySelector(sender, args);
            return true;
        }

        Player source = Bukkit.getPlayerExact(args[0]);
        if (source == null) {
            msg.send(sender, "unknown-player", ph("player", args[0]));
            return true;
        }

        if (args.length == 1) {
            handleSelfToPlayer(sender, source);
            return true;
        }

        if (SELF_TOKENS.contains(args[1].toLowerCase())) {
            handleSummon(sender, source);
            return true;
        }

        if (Coords.isTriplet(args, 1)) {
            handleForceToCoords(sender, source, args);
            return true;
        }

        Player dest = Bukkit.getPlayerExact(args[1]);
        if (dest == null) {
            msg.send(sender, "unknown-player", ph("player", args[1]));
            return true;
        }
        handleForceToPlayer(sender, source, dest);
        return true;
    }

    private void handleSelfToCoords(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!plugin.getConfig().getBoolean("features.coordinates", true)) {
            msg.send(player, "feature-disabled");
            return;
        }
        if (!player.hasPermission("teleport.coordinates") && !player.hasPermission("teleport.force")) {
            msg.send(player, "no-permission");
            return;
        }
        Location loc = Coords.parse(player.getLocation(), player.getWorld(), args, 0);
        player.teleportAsync(loc);
        msg.send(player, "teleported-to-coords",
                ph("x", formatCoord(loc.getX())), ph("y", formatCoord(loc.getY())), ph("z", formatCoord(loc.getZ())));
    }

    private void handleSelfToPlayer(CommandSender sender, Player target) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (player.equals(target)) {
            msg.send(player, "self-target");
            return;
        }
        if (player.hasPermission("teleport.force")) {
            player.teleportAsync(target.getLocation());
            msg.send(player, "teleported-to-player", ph("player", target.getName()));
            return;
        }
        if (requestDisabled(TeleportRequest.Type.GOTO, player)) {
            msg.send(player, "feature-disabled");
            return;
        }
        msg.send(player, "cannot-force-goto");
        msg.sendButton(player, "cannot-force-goto-hint", target.getName());
    }

    private void handleSummon(CommandSender sender, Player target) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (player.equals(target)) {
            msg.send(player, "self-target");
            return;
        }
        if (player.hasPermission("teleport.force")) {
            target.teleportAsync(player.getLocation());
            msg.send(player, "summoned-player", ph("player", target.getName()));
            msg.send(target, "you-were-teleported", ph("player", player.getName()));
            return;
        }
        if (requestDisabled(TeleportRequest.Type.SUMMON, player)) {
            msg.send(player, "feature-disabled");
            return;
        }
        msg.send(player, "cannot-force-summon");
        msg.sendButton(player, "cannot-force-summon-hint", target.getName());
    }

    private void handleForceToCoords(CommandSender sender, Player target, String[] args) {
        if (lacksForce(sender)) return;
        Location loc = Coords.parse(target.getLocation(), target.getWorld(), args, 1);
        target.teleportAsync(loc);
        msg.send(sender, "teleported-player-to-coords", ph("player", target.getName()),
                ph("x", formatCoord(loc.getX())), ph("y", formatCoord(loc.getY())), ph("z", formatCoord(loc.getZ())));
        msg.send(target, "you-were-teleported", ph("player", sender.getName()));
    }

    private void handleForceToPlayer(CommandSender sender, Player target, Player dest) {
        if (lacksForce(sender)) return;
        target.teleportAsync(dest.getLocation());
        msg.send(sender, "teleported-player-to-player",
                ph("player", target.getName()), ph("target", dest.getName()));
        if (!target.equals(sender)) {
            msg.send(target, "you-were-teleported", ph("player", sender.getName()));
        }
    }

    private void handleEntitySelector(CommandSender sender, String[] args) {
        if (entitiesDisabled(sender)) return;
        List<Entity> entities = select(sender, args[0]);
        if (entities == null) return;
        if (entities.isEmpty()) {
            msg.send(sender, "entities-none", ph("selector", args[0]));
            return;
        }
        String what = entities.size() == 1 ? "1 entity" : entities.size() + " entities";
        teleportEntities(sender, entities, what, args);
    }

    private void handleLook(CommandSender sender, String[] args) {
        if (entitiesDisabled(sender)) return;
        Player player = requirePlayer(sender);
        if (player == null) return;
        Entity target = player.getTargetEntity(LOOK_RANGE);
        if (target == null) {
            msg.send(player, "look-none");
            return;
        }
        teleportEntities(sender, List.of(target), "the " + target.getName(), args);
    }

    private void teleportEntities(CommandSender sender, List<Entity> entities, String what, String[] args) {
        if (args.length == 1 || SELF_TOKENS.contains(args[1].toLowerCase())) {
            Player player = requirePlayer(sender);
            if (player == null) return;
            teleportAll(entities, player.getLocation());
            msg.send(sender, "entities-teleported", ph("what", what), ph("where", "you"));
            return;
        }

        if (Coords.isTriplet(args, 1)) {
            Location base = sender instanceof Player p ? p.getLocation() : null;
            World world = sender instanceof Player p ? p.getWorld() : entities.getFirst().getWorld();
            Location dest = Coords.parse(base, world, args, 1);
            teleportAll(entities, dest);
            msg.send(sender, "entities-teleported", ph("what", what),
                    ph("where", formatCoord(dest.getX()) + ", " + formatCoord(dest.getY()) + ", " + formatCoord(dest.getZ())));
            return;
        }

        Location to;
        String where;
        if (args[1].startsWith("@")) {
            List<Entity> destEntities = select(sender, args[1]);
            if (destEntities == null) return;
            if (destEntities.isEmpty()) {
                msg.send(sender, "entities-none", ph("selector", args[1]));
                return;
            }
            Entity anchor = destEntities.getFirst();
            to = anchor.getLocation();
            where = anchor.getName();
        } else {
            Player destPlayer = Bukkit.getPlayerExact(args[1]);
            if (destPlayer == null) {
                msg.send(sender, "unknown-player", ph("player", args[1]));
                return;
            }
            to = destPlayer.getLocation();
            where = destPlayer.getName();
        }
        teleportAll(entities, to);
        msg.send(sender, "entities-teleported", ph("what", what), ph("where", where));
    }

    private void handleSendRequest(CommandSender sender, String[] args, TeleportRequest.Type type) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            msg.send(player, "usage");
            return;
        }
        if (requestDisabled(type, player)) {
            msg.send(player, "feature-disabled");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg.send(player, "unknown-player", ph("player", args[1]));
            return;
        }
        if (target.equals(player)) {
            msg.send(player, "self-target");
            return;
        }
        if (plugin.requests().hasFrom(target.getUniqueId(), player.getUniqueId())) {
            msg.send(player, "request-pending-already", ph("player", target.getName()));
            return;
        }

        long expire = plugin.getConfig().getLong("requests.expire-seconds", 60);
        TeleportRequest req = new TeleportRequest(player.getUniqueId(), player.getName(),
                target.getUniqueId(), type, System.currentTimeMillis() + expire * 1000L);
        plugin.requests().add(req);

        msg.send(player, "request-sent", ph("player", target.getName()), ph("time", expire));
        String received = type == TeleportRequest.Type.GOTO ? "request-received-goto" : "request-received-summon";
        msg.send(target, received, ph("player", player.getName()));
        msg.sendButton(target, "request-buttons", player.getName());
    }

    private void handleResponse(CommandSender sender, String[] args, boolean accept) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        TeleportRequest req;
        if (args.length >= 2) {
            Player from = Bukkit.getPlayerExact(args[1]);
            if (from == null) {
                msg.send(player, "request-none-from", ph("player", args[1]));
                return;
            }
            req = plugin.requests().take(player.getUniqueId(), from.getUniqueId());
            if (req == null) {
                msg.send(player, "request-none-from", ph("player", from.getName()));
                return;
            }
        } else {
            req = plugin.requests().takeSingle(player.getUniqueId());
            if (req == null) {
                msg.send(player, "request-none");
                return;
            }
        }

        Player from = Bukkit.getPlayer(req.sender());
        if (!accept) {
            msg.send(player, "request-denied-target", ph("player", req.senderName()));
            if (from != null) msg.send(from, "request-denied-sender", ph("player", player.getName()));
            return;
        }

        if (from == null) {
            msg.send(player, "request-none-from", ph("player", req.senderName()));
            return;
        }
        if (req.type() == TeleportRequest.Type.GOTO) {
            from.teleportAsync(player.getLocation());
        } else {
            player.teleportAsync(from.getLocation());
        }
        msg.send(player, "request-accepted-target", ph("player", from.getName()));
        msg.send(from, "request-accepted-sender", ph("player", player.getName()));
    }

    private void handleRtp(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!plugin.getConfig().getBoolean("rtp.enabled", true)) {
            msg.send(player, "rtp-disabled");
            return;
        }
        if (!player.hasPermission("teleport.rtp")) {
            msg.send(player, "no-permission");
            return;
        }

        World world;
        if (args.length >= 2) {
            if (!player.hasPermission("teleport.rtp.dimensions")) {
                msg.send(player, "no-permission");
                return;
            }
            world = Bukkit.getWorld(args[1]);
            if (world == null) {
                msg.send(player, "rtp-unknown-dimension", ph("dimension", args[1]));
                return;
            }
        } else {
            world = player.getWorld();
        }

        if (!plugin.getConfig().getBoolean("rtp.dimensions." + world.getName(), false)) {
            msg.send(player, "rtp-dimension-disabled", ph("dimension", world.getName()));
            return;
        }

        long cooldown = plugin.rtp().cooldownRemaining(player);
        if (cooldown > 0) {
            msg.send(player, "rtp-cooldown", ph("time", cooldown));
            return;
        }

        msg.send(player, "rtp-searching");
        Location loc = plugin.rtp().findSafe(world);
        if (loc == null) {
            msg.send(player, "rtp-failed");
            return;
        }
        plugin.rtp().markUsed(player);
        Location spawn = world.getSpawnLocation();
        int distance = (int) Math.hypot(loc.getX() - spawn.getX(), loc.getZ() - spawn.getZ());
        player.teleportAsync(loc);
        msg.send(player, "rtp-success", ph("distance", distance), ph("world", world.getName()));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("teleport.reload")) {
            msg.send(sender, "no-permission");
            return;
        }
        plugin.reload();
        msg.send(sender, "reloaded");
    }

    private boolean requestDisabled(TeleportRequest.Type type, Player player) {
        if (type == TeleportRequest.Type.GOTO) {
            return !plugin.getConfig().getBoolean("features.requests", true)
                    || !player.hasPermission("teleport.request");
        }
        return !plugin.getConfig().getBoolean("features.summon-requests", true)
                || !player.hasPermission("teleport.summon");
    }

    private boolean entitiesDisabled(CommandSender sender) {
        if (!plugin.getConfig().getBoolean("features.entities", true)) {
            msg.send(sender, "feature-disabled");
            return true;
        }
        if (!sender.hasPermission("teleport.entities")) {
            msg.send(sender, "no-permission");
            return true;
        }
        return false;
    }

    private List<Entity> select(CommandSender sender, String selector) {
        try {
            return Bukkit.selectEntities(sender, selector);
        } catch (IllegalArgumentException e) {
            msg.send(sender, "entities-bad-selector", ph("selector", selector));
            return null;
        }
    }

    private void teleportAll(List<Entity> entities, Location dest) {
        for (Entity entity : entities) {
            entity.teleportAsync(dest);
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        msg.send(sender, "player-only");
        return null;
    }

    private boolean lacksForce(CommandSender sender) {
        if (sender.hasPermission("teleport.force")) return false;
        msg.send(sender, "no-permission");
        return true;
    }

    private static String formatCoord(double v) {
        return String.valueOf(Math.round(v * 10.0) / 10.0);
    }

    private static List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String label, @NotNull String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("random");
            out.addAll(onlinePlayerNames());
            if (sender.hasPermission("teleport.entities")) {
                out.addAll(List.of("look", "@a", "@e", "@p", "@r", "@s"));
            }
            if (sender.hasPermission("teleport.reload")) out.add("reload");
        } else if (args.length == 2) {
            String first = args[0].toLowerCase();
            if (first.equals("random") || first.equals("rtp")) {
                out.addAll(enabledDimensions());
            } else if (PLAYER_ARG_SUBCOMMANDS.contains(first)) {
                out.addAll(onlinePlayerNames());
            } else {
                out.add("me");
                out.addAll(onlinePlayerNames());
            }
        }
        String prefix = args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        return out;
    }

    private List<String> enabledDimensions() {
        List<String> dims = new ArrayList<>();
        var section = plugin.getConfig().getConfigurationSection("rtp.dimensions");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (section.getBoolean(key)) dims.add(key);
            }
        }
        return dims;
    }
}
