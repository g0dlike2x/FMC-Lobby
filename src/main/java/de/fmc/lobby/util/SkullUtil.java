package de.fmc.lobby.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Köpfe mit Base64-Textur. Es findet keine Mojang-Abfrage statt: Der Client lädt die Textur selbst.
 */
public final class SkullUtil {

    private SkullUtil() {
    }

    /**
     * @param base64 "textures"-Wert (eyJ0ZXh0dXJlcyI6…)
     * @return Kopf oder null, wenn keine Textur angegeben ist
     */
    public static ItemStack head(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }
        // Feste UUID aus der Textur, damit gleiche Köpfe stapelbar/gleich bleiben
        UUID id = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createProfile(id, null);
        profile.setProperty(new ProfileProperty("textures", base64.trim()));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }
}
