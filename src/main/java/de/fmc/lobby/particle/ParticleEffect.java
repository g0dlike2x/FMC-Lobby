package de.fmc.lobby.particle;

import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

/**
 * Ein Partikel-Eintrag aus der config.yml. Beide Item-Zustände sind vorgebaut.
 * Alle Partikel sind für jeden Spieler frei.
 *
 * @param data Zusatzdaten für den Partikel (DustOptions, Color, BlockData …) oder null
 */
public record ParticleEffect(String id, int page, int slot, String plainName,
                             Particle particle, Object data, int amount, double speed,
                             ParticleShape shape,
                             ItemStack itemAvailable, ItemStack itemSelected) {
}
