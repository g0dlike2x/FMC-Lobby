package de.fmc.lobby.gui;

import de.fmc.lobby.util.Sounds;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Basis aller Lobby-Menüs. Erkennung ausschließlich über den InventoryHolder, nie über den Titel.
 */
public abstract class LobbyMenu implements InventoryHolder {

    protected final Player viewer;
    protected Inventory inventory;

    protected LobbyMenu(Player viewer) {
        this.viewer = viewer;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player getViewer() {
        return viewer;
    }

    /** Öffnet das Menü mit Sound. */
    public void open() {
        render();
        viewer.openInventory(inventory);
        Sounds.open(viewer);
    }

    /** Setzt alle Items (wird auch für Live-Updates aufgerufen). */
    public abstract void render();

    /** Klick in das obere Inventar. Das Event ist zu diesem Zeitpunkt bereits abgebrochen. */
    public abstract void handleClick(int slot, ClickType click);
}
