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
 * Ist der Server offline (oder warten schon Spieler), führt der Klick in die Warteschlange.
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
        QueueManager queue = plugin.getQueue();
        for (ServerEntry entry : service.getEntries()) {
            ServerStatus status = service.getStatus(entry);
            String statusText = status.online() ? service.getStatusOnline() : service.getStatusOffline();
            String online = NumberFormatter.format(status.players());
            String max = NumberFormatter.format(status.max());
            String waiting = NumberFormatter.format(queue.queueSize(entry));
            String action;
            if (queue.isQueued(viewer, entry)) {
                action = service.getActionQueueLeave();
            } else if (queue.shouldQueue(entry)) {
                action = service.getActionQueueJoin();
            } else {
                action = service.getActionConnect();
            }

            List<String> lore = new ArrayList<>(entry.lore().size());
            for (String line : entry.lore()) {
                lore.add(line.replace("%status%", statusText)
                        .replace("%action%", action)
                        .replace("%online%", online)
                        .replace("%max%", max)
                        .replace("%queue%", waiting)
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
        QueueManager queue = plugin.getQueue();

        // Erneuter Klick verlässt die Warteschlange
        if (queue.isQueued(viewer, entry)) {
            queue.leave(viewer);
            Sounds.toggleOff(viewer);
            cm.sendOrDefault(viewer, "teleporter.queue-left",
                    "%prefix%&7Du hast die Warteschlange für &a%server% &cverlassen&7.",
                    "%server%", entry.plainName());
            render();
            return;
        }
        if (queue.shouldQueue(entry)) {
            int position = queue.join(viewer, entry);
            Sounds.toggleOn(viewer);
            cm.sendOrDefault(viewer, "teleporter.queue-joined",
                    "%prefix%&7Du bist in der Warteschlange für &a%server% &8(&7Platz &e%position%&8)&7. "
                            + "Sobald der Server online ist, wirst du automatisch verbunden.",
                    "%server%", entry.plainName(), "%position%", NumberFormatter.format(position));
            render();
            return;
        }
        // Warteschlange deaktiviert
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
