package ca.zerodev;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Plugin entry point: loads configuration, wires up the services, and registers the {@code /tp} command.
 */
public final class Teleport extends JavaPlugin {

    private Messages messages;
    private RequestManager requests;
    private RtpService rtp;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergeDefaults();
        messages = new Messages(this);
        requests = new RequestManager(this);
        rtp = new RtpService(this);
        requests.start();

        PluginCommand tp = getCommand("tp");
        if (tp != null) {
            TpCommand handler = new TpCommand(this);
            tp.setExecutor(handler);
            tp.setTabCompleter(handler);
        } else {
            getLogger().severe("Could not register /tp - is it declared in plugin.yml?");
        }
    }

    public void reload() {
        reloadConfig();
        mergeDefaults();
        messages.reload();
    }

    private void mergeDefaults() {
        InputStream resource = getResource("config.yml");
        if (resource == null) return;
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(resource, StandardCharsets.UTF_8));
        getConfig().setDefaults(defaults);
    }

    public Messages messages() {
        return messages;
    }

    public RequestManager requests() {
        return requests;
    }

    public RtpService rtp() {
        return rtp;
    }
}
