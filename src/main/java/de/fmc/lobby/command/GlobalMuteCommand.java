package de.fmc.lobby.command;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.chat.ChatListener;
import de.fmc.lobby.config.ConfigManager;
import de.fmc.lobby.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * /globalmute – schaltet den Chat für alle ohne lobby.globalmute.bypass ab bzw. wieder an.
 */
public class GlobalMuteCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "lobby.globalmute";

    private final FMCLobby plugin;
    private final ChatListener chat;

    public GlobalMuteCommand(FMCLobby plugin, ChatListener chat) {
        this.plugin = plugin;
        this.chat = chat;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        ConfigManager cm = plugin.getConfigManager();
        if (!sender.hasPermission(PERM)) {
            cm.send(sender, "general.no-permission");
            return true;
        }
        boolean mute = !chat.isMuted();
        chat.setMuted(mute);
        String message = mute
                ? cm.getMessageOrDefault("chat.globalmute-enabled",
                "%prefix%&7Der Chat wurde von &c%player% &cdeaktiviert&7.")
                : cm.getMessageOrDefault("chat.globalmute-disabled",
                "%prefix%&7Der Chat wurde von &a%player% &awieder aktiviert&7.");
        Bukkit.broadcast(Text.component(ConfigManager.replace(message, "%player%", sender.getName())));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String @NotNull [] args) {
        return List.of();
    }
}
