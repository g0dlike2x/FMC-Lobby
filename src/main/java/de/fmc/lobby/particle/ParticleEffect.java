package de.fmc.lobby.particle;

import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Ein Partikel-Eintrag aus der config.yml. Alle drei Item-Zustände sind vorgebaut.
 *
 * @param data       Zusatzdaten für den Partikel (DustOptions, Color, BlockData …) oder null
 * @param permission Permission oder null (= für alle frei)
 */
public record ParticleEffect(String id, int page, int slot, String plainName,
                             Particle particle, Object data, int amount, double speed,
                             String permission, ParticleShape shape,
                             ItemStack itemAvailable, ItemStack itemSelected, ItemStack itemLocked) {

    public boolean isLockedFor(Player player) {
        return permission != null && !player.hasPermission(permission);
    }
}
