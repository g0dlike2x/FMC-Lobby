package de.fmc.lobby.particle;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.config.ConfigManager;
import de.fmc.lobby.gui.GuiItems;
import de.fmc.lobby.gui.LobbyMenu;
import de.fmc.lobby.util.Sounds;
import de.fmc.lobby.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.Map;

/**
 * Partikel-GUI mit optionalen Seiten. Alle Item-Zustände sind vorgebaut, hier wird nur geklont.
 */
public class ParticleMenu extends LobbyMenu {

    private final FMCLobby plugin;
    private final ParticleManager manager;
    private int page;

    public ParticleMenu(FMCLobby plugin, ParticleManager manager, Player viewer, int page) {
        super(viewer);
        this.plugin = plugin;
        this.manager = manager;
        this.page = page;
        this.inventory = Bukkit.createInventory(this, manager.getRows() * 9, Text.component(manager.getTitle()));
    }

    @Override
    public void render() {
        inventory.clear();
        ParticleEffect selected = manager.getSelected(viewer);

        for (ParticleEffect effect : manager.getPage(page).values()) {
            if (effect == selected) {
                inventory.setItem(effect.slot(), effect.itemSelected().clone());
            } else if (effect.isLockedFor(viewer)) {
                inventory.setItem(effect.slot(), effect.itemLocked().clone());
            } else {
                inventory.setItem(effect.slot(), effect.itemAvailable().clone());
            }
        }

        int size = inventory.getSize();
        if (inBounds(manager.getRemoveSlot())) {
            inventory.setItem(manager.getRemoveSlot(), manager.buildRemoveItem(viewer));
        }
        if (inBounds(manager.getBackSlot())) {
            inventory.setItem(manager.getBackSlot(), plugin.getGuiItems().back());
        }
        int pages = manager.getPageCount();
        if (pages > 1) {
            GuiItems gui = plugin.getGuiItems();
            inventory.setItem(size - 9, gui.previous(page, pages));
            inventory.setItem(size - 1, gui.next(page, pages));
        }
    }

    private boolean inBounds(int slot) {
        return slot >= 0 && slot < inventory.getSize();
    }

    @Override
    public void handleClick(int slot, ClickType click) {
        ConfigManager cm = plugin.getConfigManager();
        int size = inventory.getSize();
        int pages = manager.getPageCount();

        // Seitenwechsel
        if (pages > 1 && (slot == size - 9 || slot == size - 1)) {
            int target = slot == size - 9 ? page - 1 : page + 1;
            if (target < 1 || target > pages) {
                Sounds.deny(viewer);
                return;
            }
            page = target;
            Sounds.click(viewer);
            render();
            return;
        }

        // Zurück
        if (slot == manager.getBackSlot()) {
            Sounds.click(viewer);
            plugin.getTeleporter().open(viewer);
            return;
        }

        // Entfernen
        if (slot == manager.getRemoveSlot()) {
            if (manager.getSelected(viewer) == null) {
                Sounds.deny(viewer);
                cm.send(viewer, "particles.none");
                return;
            }
            manager.deselect(viewer);
            Sounds.toggleOff(viewer);
            cm.send(viewer, "particles.removed");
            render();
            return;
        }

        Map<Integer, ParticleEffect> entries = manager.getPage(page);
        ParticleEffect effect = entries.get(slot);
        if (effect == null) {
            return;
        }
        if (effect.isLockedFor(viewer)) {
            Sounds.deny(viewer);
            cm.send(viewer, "particles.locked");
            return;
        }
        if (manager.getSelected(viewer) == effect) {
            manager.deselect(viewer);
            Sounds.toggleOff(viewer);
            cm.send(viewer, "particles.removed");
        } else {
            manager.select(viewer, effect);
            Sounds.toggleOn(viewer);
            cm.send(viewer, "particles.selected", "%particle%", effect.plainName());
        }
        render();
    }
}
