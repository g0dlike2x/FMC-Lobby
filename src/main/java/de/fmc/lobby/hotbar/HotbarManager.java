package de.fmc.lobby.hotbar;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.util.ItemBuilder;
import de.fmc.lobby.util.Keys;
import de.fmc.lobby.util.NumberFormatter;
import de.fmc.lobby.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Baut alle Hotbar-Items einmal beim Start / Reload. Beim Join werden nur Klone verteilt.
 */
public class HotbarManager {

    public static final String TELEPORTER = "teleporter";
    public static final String PARTICLES = "particles";
    public static final String GRAPPLE = "grapple";
    public static final String BUILD_BLOCK = "buildblock";

    private final FMCLobby plugin;
    private final List<HotbarItem> items = new ArrayList<>();
    private int heldSlot = 4;
    private HotbarItem buildBlock;

    public HotbarManager(FMCLobby plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration config = plugin.getConfigManager().getConfig();
        items.clear();
        buildBlock = null;
        heldSlot = clampSlot(config.getInt("hotbar.held-slot", 4));

        String cooldown = NumberFormatter.decimal(config.getLong("grapple.cooldown-ms", 1500) / 1000.0, 1);
        String lifetime = NumberFormatter.decimal(config.getLong("build-blocks.lifetime-ticks", 60) / 20.0, 1);
        String limit = NumberFormatter.format(config.getInt("build-blocks.limit-per-player", 30));

        add(config, "teleporter", TELEPORTER, Material.COMPASS, 1);
        add(config, "particles", PARTICLES, Material.BLAZE_POWDER, 1);
        HotbarItem grapple = add(config, "grapple", GRAPPLE, Material.FISHING_ROD, 1,
                "%cooldown%", cooldown);
        if (grapple != null) {
            ItemMeta meta = grapple.item().getItemMeta();
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            grapple.item().setItemMeta(meta);
        }
        buildBlock = add(config, "build-blocks", BUILD_BLOCK, Material.PURPLE_CONCRETE, 64,
                "%lifetime%", lifetime, "%limit%", limit);
        if (buildBlock != null && !buildBlock.item().getType().isBlock()) {
            plugin.getLogger().warning("hotbar.build-blocks.material ist kein Block – Baublöcke deaktiviert.");
            items.remove(buildBlock);
            buildBlock = null;
        }
    }

    private HotbarItem add(YamlConfiguration config, String path, String id, Material fallback, int amount,
                           String... replacements) {
        ConfigurationSection s = config.getConfigurationSection("hotbar." + path);
        if (s == null || !s.getBoolean("enabled", true)) {
            return null;
        }
        Material material = Material.matchMaterial(s.getString("material", fallback.name()));
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("hotbar." + path + ".material ist ungültig, nutze " + fallback.name());
            material = fallback;
        }
        if (id.equals(GRAPPLE) && material != Material.FISHING_ROD) {
            plugin.getLogger().warning("hotbar.grapple.material muss FISHING_ROD sein.");
            material = Material.FISHING_ROD;
        }
        List<String> lore = new ArrayList<>();
        for (String line : Text.color(s.getStringList("lore"))) {
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                line = line.replace(replacements[i], replacements[i + 1]);
            }
            lore.add(line);
        }
        ItemStack item = ItemBuilder.build(material, Math.min(amount, material.getMaxStackSize()),
                Text.color(s.getString("name", "&6&l" + id)), lore, s.getBoolean("glow", false));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, id);
        item.setItemMeta(meta);

        HotbarItem hotbarItem = new HotbarItem(id, clampSlot(s.getInt("slot", 0)), item);
        for (HotbarItem other : items) {
            if (other.slot() == hotbarItem.slot()) {
                plugin.getLogger().warning("Hotbar-Slot " + hotbarItem.slot() + " ist doppelt belegt (" + id + ").");
            }
        }
        items.add(hotbarItem);
        return hotbarItem;
    }

    private static int clampSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    /** Inventar leeren und Hotbar setzen (nur Klone). */
    public void give(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        for (HotbarItem item : items) {
            inventory.setItem(item.slot(), item.item().clone());
        }
        inventory.setHeldItemSlot(heldSlot);
    }

    /** Lobby-Kennung eines Items oder null. Liest den PDC ohne Meta-Kopie. */
    public static String idOf(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        return item.getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
    }

    /** Neuer voller Stack Baublöcke oder null, falls deaktiviert. */
    public ItemStack buildBlockItem() {
        return buildBlock == null ? null : buildBlock.item().clone();
    }

    public Material buildBlockMaterial() {
        return buildBlock == null ? null : buildBlock.item().getType();
    }

    public int buildBlockSlot() {
        return buildBlock == null ? -1 : buildBlock.slot();
    }

    public record HotbarItem(String id, int slot, ItemStack item) {
    }
}
