package de.fmc.lobby.gui;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.config.ConfigManager;
import de.fmc.lobby.util.ItemBuilder;
import de.fmc.lobby.util.SkullUtil;
import de.fmc.lobby.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Gemeinsame GUI-Bausteine (Zurück-Knopf, Pfeil-Köpfe). Werden beim Start / Reload einmal gebaut.
 */
public class GuiItems {

    private final FMCLobby plugin;

    private ItemStack back;
    private ItemStack leftGreen;
    private ItemStack rightGreen;
    private ItemStack leftRed;
    private ItemStack rightRed;
    private String pageLore;

    public GuiItems(FMCLobby plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        ConfigManager cm = plugin.getConfigManager();
        YamlConfiguration config = cm.getConfig();

        back = ItemBuilder.build(Material.ENDER_EYE, 1,
                cm.getMessageOrDefault("gui.back", "§c<- Zurück zum Menü"), null, false);

        String previous = cm.getMessageOrDefault("gui.previous-page", "§a<- Vorherige Seite");
        String next = cm.getMessageOrDefault("gui.next-page", "§aNächste Seite ->");
        pageLore = cm.getMessageOrDefault("gui.page-lore", "§7Seite§8: §e%page%§8/§e%pages%");

        leftGreen = head(config.getString("gui.heads.arrow-left-green"), previous);
        rightGreen = head(config.getString("gui.heads.arrow-right-green"), next);
        leftRed = head(config.getString("gui.heads.arrow-left-red"), previous);
        rightRed = head(config.getString("gui.heads.arrow-right-red"), next);
    }

    private ItemStack head(String texture, String name) {
        ItemStack item = SkullUtil.head(texture);
        if (item == null) {
            // Ersatz, falls keine Textur hinterlegt ist
            item = new ItemStack(Material.ARROW);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.item(name));
            ItemBuilder.finish(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack back() {
        return back.clone();
    }

    /** Pfeil links: grün, wenn es eine vorherige Seite gibt, sonst rot. */
    public ItemStack previous(int page, int pages) {
        return withPage(page > 1 ? leftGreen : leftRed, page, pages);
    }

    /** Pfeil rechts: grün, wenn es eine nächste Seite gibt, sonst rot. */
    public ItemStack next(int page, int pages) {
        return withPage(page < pages ? rightGreen : rightRed, page, pages);
    }

    private ItemStack withPage(ItemStack base, int page, int pages) {
        ItemStack item = base.clone();
        ItemBuilder.setLore(item, List.of("", pageLore
                .replace("%page%", Integer.toString(page))
                .replace("%pages%", Integer.toString(pages))));
        return item;
    }
}
