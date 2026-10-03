package de.fmc.lobby.teleporter;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.config.ConfigManager;
import de.fmc.lobby.gui.LobbyMenu;
import de.fmc.lobby.util.ItemBuilder;
import de.fmc.lobby.util.NumberFormatter;
import de.fmc.lobby.util.Sounds;
import de.fmc.lobby.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Teleporter-GUI. Liest ausschließlich den Status-Cache.
 */
public class TeleporterMenu extends LobbyMenu {

    private final FMCLobby plugin;
    private final TeleporterService service;

    public TeleporterMenu(FMCLobby plugin, TeleporterService service, Player viewer) {
        super(viewer);
        this.plugin = plugin;
        this.service = service;
        this.inventory = Bukkit.createInventory(this, service.getRows() * 9, Text.component(service.getTitle()));
    }

    @Override
    public void render() {
        for (ServerEntry entry : service.getEntries()) {
            ServerStatus status = service.getStatus(entry);
            String statusText = status.online() ? service.getStatusOnline() : service.getStatusOffline();
            String online = NumberFormatter.format(status.players());
            String max = NumberFormatter.format(status.max());

            List<String> lore = new ArrayList<>(entry.lore().size());
            for (String line : entry.lore()) {
                lore.add(line.replace("%status%", statusText)
                        .replace("%online%", online)
                        .replace("%max%", max)
                        .replace("%server%", entry.plainName()));
            }
            ItemStack item = entry.baseItem().clone();
            ItemBuilder.setLore(item, lore);
            inventory.setItem(entry.slot(), item);
        }
    }

    @Override
    public void handleClick(int slot, ClickType click) {
        ServerEntry entry = service.getBySlot(slot);
        if (entry == null) {
            return;
        }
        ConfigManager cm = plugin.getConfigManager();
        if (!service.tryClick(viewer)) {
            cm.actionBar(viewer, "teleporter.cooldown");
            return;
        }
        if (!service.getStatus(entry).online()) {
            Sounds.deny(viewer);
            cm.send(viewer, "teleporter.offline", "%server%", entry.plainName());
            return;
        }
        Sounds.click(viewer);
        cm.send(viewer, "teleporter.connecting", "%server%", entry.plainName());
        viewer.closeInventory();
        plugin.getMessenger().connect(viewer, entry.proxyName());
    }
}
