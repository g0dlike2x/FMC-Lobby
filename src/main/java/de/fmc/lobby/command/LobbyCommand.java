package de.fmc.lobby.command;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.config.ConfigManager;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * /lobby reload | setspawn | build
 */
public class LobbyCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_ADMIN = "lobby.admin";
    private static final String PERM_BUILD = "lobby.build";

    private final FMCLobby plugin;

    public LobbyCommand(FMCLobby plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        ConfigManager cm = plugin.getConfigManager();
        if (args.length == 0) {
            cm.send(sender, "general.usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!sender.hasPermission(PERM_ADMIN)) {
                    cm.send(sender, "general.no-permission");
                    return true;
                }
                long start = System.currentTimeMillis();
                try {
                    plugin.reloadAll();
                    // Nach dem Reload die neuen Texte verwenden
                    plugin.getConfigManager().send(sender, "command.reload-success",
                            "%time%", Long.toString(System.currentTimeMillis() - start));
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "Fehler beim Neuladen", e);
                    plugin.getConfigManager().send(sender, "command.reload-failed");
                }
            }
            case "setspawn" -> {
                if (!sender.hasPermission(PERM_ADMIN)) {
                    cm.send(sender, "general.no-permission");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    cm.send(sender, "general.player-only");
                    return true;
                }
                cm.setSpawn(player.getLocation());
                cm.send(player, "command.setspawn-success");
            }
            case "build" -> {
                if (!sender.hasPermission(PERM_BUILD)) {
                    cm.send(sender, "general.no-permission");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    cm.send(sender, "general.player-only");
                    return true;
                }
                if (plugin.getBuildMode().toggle(player)) {
                    player.getInventory().clear();
                    player.setGameMode(GameMode.CREATIVE);
                    cm.send(player, "command.build-enabled");
                } else {
                    plugin.preparePlayer(player, false);
                    cm.send(player, "command.build-disabled");
                }
            }
            default -> cm.send(sender, "general.usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String @NotNull [] args) {
        List<String> result = new ArrayList<>();
        if (args.length != 1) {
            return result;
        }
        String input = args[0].toLowerCase(Locale.ROOT);
        if (sender.hasPermission(PERM_ADMIN)) {
            addIfMatches(result, "reload", input);
            addIfMatches(result, "setspawn", input);
        }
        if (sender.hasPermission(PERM_BUILD)) {
            addIfMatches(result, "build", input);
        }
        return result;
    }

    private static void addIfMatches(List<String> list, String option, String input) {
        if (option.startsWith(input)) {
            list.add(option);
        }
    }
}
