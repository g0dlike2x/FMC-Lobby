package de.fmc.lobby.command;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.config.ConfigManager;
import de.fmc.lobby.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * /discord und /buy: anklickbare Link-Nachricht. Texte aus messages.yml (links.&lt;key&gt;),
 * URL aus config.yml (links.&lt;key&gt;).
 */
public class LinkCommand implements CommandExecutor, TabCompleter {

    private final FMCLobby plugin;
    private final String key;
    private final String fallbackUrl;
    private final List<String> fallbackLines;

    public LinkCommand(FMCLobby plugin, String key, String fallbackUrl, List<String> fallbackLines) {
        this.plugin = plugin;
        this.key = key;
        this.fallbackUrl = fallbackUrl;
        this.fallbackLines = fallbackLines;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        ConfigManager cm = plugin.getConfigManager();
        String url = cm.getConfig().getString("links." + key, fallbackUrl);
        Component hover = Text.component(cm.getMessageOrDefault("links.hover", "§7Klicke, um den Link zu öffnen"));
        for (String line : cm.getMessageList("links." + key, fallbackLines)) {
            Component message = Text.component(line);
            if (url != null && !url.isBlank()) {
                message = message.clickEvent(ClickEvent.openUrl(url)).hoverEvent(HoverEvent.showText(hover));
            }
            sender.sendMessage(message);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String @NotNull [] args) {
        return List.of();
    }
}
