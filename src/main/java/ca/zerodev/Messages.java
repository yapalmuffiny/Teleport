package ca.zerodev;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.intellij.lang.annotations.Subst;

import java.util.Set;

/**
 * Loads message strings from {@code config.yml} and renders them with MiniMessage.
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Teleport plugin;
    private String prefix = "";
    private Set<String> actionBarKeys = Set.of();

    public Messages(Teleport plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        prefix = plugin.getConfig().getString("messages.prefix", "");
        actionBarKeys = Set.copyOf(plugin.getConfig().getStringList("messages.action-bar"));
    }

    private String raw(String key) {
        return plugin.getConfig().getString("messages." + key, "<red>Missing message: " + key);
    }

    public Component component(String key, TagResolver... resolvers) {
        return MM.deserialize(prefix + raw(key), resolvers);
    }

    /** Routes to the action bar if the key is configured for it (and the target is a player), otherwise to chat. */
    public void send(CommandSender to, String key, TagResolver... resolvers) {
        Component component = component(key, resolvers);
        if (to instanceof Player player && actionBarKeys.contains(key)) {
            player.sendActionBar(component);
        } else {
            to.sendMessage(component);
        }
    }

    /**
     * Sends a prefixed message with the player name substituted directly into the text, so the name
     * also resolves inside click commands (where MiniMessage placeholders are not expanded). The
     * prefix is applied to each line of a multi-line ({@code <newline>}) message.
     */
    public void sendButton(CommandSender to, String key, String playerName) {
        String body = raw(key).replace("<player>", playerName);
        body = prefix + body.replace("<newline>", "<newline>" + prefix);
        to.sendMessage(MM.deserialize(body));
    }

    /** Creates a placeholder whose value is inserted literally, never parsed as MiniMessage. */
    public static TagResolver ph(@Subst("name") String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }
}
