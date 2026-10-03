package de.fmc.lobby.util;

import com.google.common.collect.ImmutableMultimap;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Baut Items im CityBuild-Stil: Name ohne Kursiv, Attribute und Verzauberungen versteckt,
 * Leuchten nur über setEnchantmentGlintOverride.
 */
public final class ItemBuilder {

    private ItemBuilder() {
    }

    /**
     * @param name bereits übersetzter §-Name
     * @param lore bereits übersetzte §-Zeilen (darf null sein)
     */
    public static ItemStack build(Material material, int amount, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Text.item(name));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(toLore(lore));
        }
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        finish(meta);
        item.setItemMeta(meta);
        return item;
    }

    /** Setzt die Standard-Flags und leert die Attribut-Modifier. */
    public static void finish(ItemMeta meta) {
        meta.setAttributeModifiers(ImmutableMultimap.of());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
    }

    /** Ersetzt die Lore eines (geklonten) Items. */
    public static void setLore(ItemStack item, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.lore(toLore(lore));
        item.setItemMeta(meta);
    }

    /** §-Zeilen → Lore-Components ohne Kursiv. */
    public static List<Component> toLore(List<String> lore) {
        List<Component> lines = new ArrayList<>(lore.size());
        for (String line : lore) {
            lines.add(Text.item(line));
        }
        return lines;
    }
}
