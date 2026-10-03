package de.fmc.lobby.hotbar;

import de.fmc.lobby.FMCLobby;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Rechtsklick auf Teleporter / Partikel-Item öffnet das jeweilige Menü.
 */
public class HotbarListener implements Listener {

    private final FMCLobby plugin;

    public HotbarListener(FMCLobby plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String id = HotbarManager.idOf(event.getItem());
        if (id == null) {
            return;
        }
        switch (id) {
            case HotbarManager.TELEPORTER -> {
                event.setCancelled(true);
                plugin.getTeleporter().open(event.getPlayer());
            }
            case HotbarManager.PARTICLES -> {
                event.setCancelled(true);
                plugin.getParticles().openMenu(event.getPlayer(), 1);
            }
            default -> {
                // Enterhaken und Baublöcke werden in eigenen Listenern behandelt
            }
        }
    }
}
