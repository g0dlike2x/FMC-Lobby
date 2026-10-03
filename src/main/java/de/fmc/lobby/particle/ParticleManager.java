package de.fmc.lobby.particle;

import com.destroystokyo.paper.ParticleBuilder;
import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.config.ConfigManager;
import de.fmc.lobby.util.ItemBuilder;
import de.fmc.lobby.util.Keys;
import de.fmc.lobby.util.Players;
import de.fmc.lobby.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Partikel: Einträge aus der Config, Auswahl im PersistentDataContainer des Spielers,
 * ein einziger globaler Zeichen-Task für alle Spieler mit Auswahl.
 */
public class ParticleManager {

    private final FMCLobby plugin;

    private final Map<String, ParticleEffect> effects = new LinkedHashMap<>();
    /** Seite → (Slot → Effekt) */
    private final Map<Integer, Map<Integer, ParticleEffect>> pages = new HashMap<>();
    /** Spieler mit aktiver Auswahl (nur Main-Thread) */
    private final Map<UUID, Active> active = new HashMap<>();

    private BukkitTask task;
    private long step;
    private double viewDistance = 24;
    private int pageCount = 1;

    // Menü
    private String title;
    private int rows;
    private int removeSlot;
    private int backSlot;
    private ItemStack removeBase;
    private List<String> removeLore;
    private String noneName;

    public ParticleManager(FMCLobby plugin) {
        this.plugin = plugin;
    }

    // ───────────────────────────── Laden ─────────────────────────────

    public void reload() {
        ConfigManager cm = plugin.getConfigManager();
        YamlConfiguration config = cm.getConfig();

        viewDistance = Math.max(4, config.getDouble("particles.view-distance", 24));
        title = Text.color(config.getString("particles.menu.title", "&8&nLobby&8 | Partikel"));
        rows = Math.max(2, Math.min(6, config.getInt("particles.menu.rows", 4)));
        removeSlot = config.getInt("particles.menu.remove-slot", rows * 9 - 5);
        backSlot = config.getInt("particles.menu.back-slot", -1);

        removeBase = ItemBuilder.build(Material.BARRIER, 1,
                cm.getMessageOrDefault("particles.remove-name", "§c§lPartikel entfernen"), null, false);
        removeLore = cm.getMessageList("particles.remove-lore",
                List.of("", "§7Aktuell§8: §e%current%", "", "§bKlicke hier, um deinen Partikel zu entfernen."));
        noneName = cm.getMessageOrDefault("particles.none-name", "Keiner");

        effects.clear();
        pages.clear();
        pageCount = 1;
        ConfigurationSection section = config.getConfigurationSection("particles.effects");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                ParticleEffect effect = parse(id, s);
                if (effect == null) {
                    continue;
                }
                Map<Integer, ParticleEffect> page = pages.computeIfAbsent(effect.page(), p -> new HashMap<>());
                if (page.containsKey(effect.slot())) {
                    plugin.getLogger().warning("Partikel '" + id + "': Slot " + effect.slot()
                            + " auf Seite " + effect.page() + " ist doppelt belegt.");
                    continue;
                }
                page.put(effect.slot(), effect);
                effects.put(id, effect);
                pageCount = Math.max(pageCount, effect.page());
            }
        }
        // Bei mehreren Seiten sind die Ecken unten links/rechts für die Pfeile reserviert
        if (pageCount > 1) {
            int left = rows * 9 - 9;
            int right = rows * 9 - 1;
            for (Map<Integer, ParticleEffect> page : pages.values()) {
                for (int reserved : new int[]{left, right}) {
                    ParticleEffect removed = page.remove(reserved);
                    if (removed != null) {
                        effects.remove(removed.id());
                        plugin.getLogger().warning("Partikel '" + removed.id() + "': Slot " + reserved
                                + " ist für die Seiten-Pfeile reserviert.");
                    }
                }
            }
        }

        // Aktive Auswahl auf neue Einträge umstellen
        List<UUID> invalid = new ArrayList<>();
        for (Map.Entry<UUID, Active> entry : active.entrySet()) {
            ParticleEffect updated = effects.get(entry.getValue().effect().id());
            if (updated == null) {
                invalid.add(entry.getKey());
            } else {
                entry.setValue(new Active(entry.getValue().player(), updated));
            }
        }
        invalid.forEach(active::remove);

        if (task != null) {
            task.cancel();
        }
        long interval = Math.max(1, config.getLong("particles.interval-ticks", 3));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    private ParticleEffect parse(String id, ConfigurationSection s) {
        String particleName = s.getString("particle", "").trim().toLowerCase(Locale.ROOT);
        Particle particle = null;
        if (!particleName.isEmpty()) {
            try {
                NamespacedKey key = NamespacedKey.fromString(particleName);
                particle = key == null ? null : Registry.PARTICLE_TYPE.get(key);
            } catch (IllegalArgumentException ignored) {
                // ungültiger Name, Warnung folgt
            }
        }
        if (particle == null) {
            plugin.getLogger().warning("Partikel '" + id + "': unbekannter Partikel '" + particleName + "'.");
            return null;
        }
        Object data;
        try {
            data = buildData(particle, s);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Partikel '" + id + "': " + e.getMessage());
            return null;
        }

        ParticleShape shape;
        try {
            shape = ParticleShape.valueOf(s.getString("shape", "RING").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Partikel '" + id + "': unbekannte Form, nutze RING.");
            shape = ParticleShape.RING;
        }

        int page = Math.max(1, s.getInt("page", 1));
        int slot = s.getInt("slot", -1);
        if (slot < 0 || slot >= rows * 9 || slot == removeSlot || slot == backSlot) {
            plugin.getLogger().warning("Partikel '" + id + "': Slot " + slot
                    + " ist ungültig oder für Knöpfe reserviert.");
            return null;
        }

        Material material = Material.matchMaterial(s.getString("material", "BLAZE_POWDER"));
        if (material == null || !material.isItem()) {
            material = Material.BLAZE_POWDER;
        }
        String plain = Text.color(s.getString("name", id));

        ConfigManager cm = plugin.getConfigManager();
        String shapeName = cm.getMessageOrDefault("particles.shapes." + shape.name(), shape.name());
        List<String> loreTemplate = cm.getMessageList("particles.lore",
                List.of("", "§7Form§8: §e%shape%", "§7Status§8: %state%", "", "%action%"));

        ItemStack available = ItemBuilder.build(material, 1, "§6§l" + plain,
                lore(loreTemplate, shapeName,
                        cm.getMessageOrDefault("particles.state-available", "§e✦ Verfügbar"),
                        cm.getMessageOrDefault("particles.action-select", "§bKlicke hier, um den Partikel auszuwählen.")),
                false);
        ItemStack selected = ItemBuilder.build(material, 1, "§a§l" + plain,
                lore(loreTemplate, shapeName,
                        cm.getMessageOrDefault("particles.state-selected", "§a✔ Ausgewählt"),
                        cm.getMessageOrDefault("particles.action-deselect", "§bKlicke hier, um den Partikel abzulegen.")),
                true);

        return new ParticleEffect(id, page, slot, plain, particle, data,
                Math.max(1, Math.min(10, s.getInt("amount", 1))), s.getDouble("speed", 0.0),
                shape, available, selected);
    }

    private static List<String> lore(List<String> template, String shape, String state, String action) {
        List<String> lore = new ArrayList<>(template.size());
        for (String line : template) {
            lore.add(line.replace("%shape%", shape).replace("%state%", state).replace("%action%", action));
        }
        return lore;
    }

    /** Zusatzdaten passend zum Datentyp des Partikels. */
    private static Object buildData(Particle particle, ConfigurationSection s) {
        Class<?> type = particle.getDataType();
        if (type == Void.class) {
            return null;
        }
        if (type == Particle.DustOptions.class) {
            return new Particle.DustOptions(color(s.getString("color", "#A235FF")), (float) s.getDouble("size", 1.0));
        }
        if (type == Particle.DustTransition.class) {
            return new Particle.DustTransition(color(s.getString("color", "#A235FF")),
                    color(s.getString("color-to", "#FFFFFF")), (float) s.getDouble("size", 1.0));
        }
        if (type == Color.class) {
            return color(s.getString("color", "#A235FF"));
        }
        if (type == BlockData.class) {
            Material block = Material.matchMaterial(s.getString("block", "PURPLE_CONCRETE"));
            if (block == null || !block.isBlock()) {
                throw new IllegalArgumentException("'block' muss ein Block-Material sein.");
            }
            return block.createBlockData();
        }
        if (type == ItemStack.class) {
            Material item = Material.matchMaterial(s.getString("item", "AMETHYST_SHARD"));
            if (item == null || !item.isItem()) {
                throw new IllegalArgumentException("'item' muss ein Item-Material sein.");
            }
            return new ItemStack(item);
        }
        if (type == Float.class) {
            return (float) s.getDouble("value", 0.0);
        }
        if (type == Integer.class) {
            return s.getInt("value", 0);
        }
        throw new IllegalArgumentException("Partikel-Datentyp " + type.getSimpleName() + " wird nicht unterstützt.");
    }

    private static Color color(String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Color.fromRGB(Integer.parseInt(clean, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ungültige Farbe '" + hex + "'.");
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        active.clear();
    }

    // ───────────────────────────── Spieler ─────────────────────────────

    /** Beim Join: Auswahl aus dem PDC lesen (kein Datei-IO, PDC liegt im Speicher). */
    public void load(Player player) {
        String id = player.getPersistentDataContainer().get(Keys.PARTICLE, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        ParticleEffect effect = effects.get(id);
        if (effect == null) {
            return;
        }
        active.put(player.getUniqueId(), new Active(player, effect));
    }

    public void unload(UUID uuid) {
        active.remove(uuid);
    }

    public ParticleEffect getSelected(Player player) {
        Active a = active.get(player.getUniqueId());
        return a == null ? null : a.effect();
    }

    public void select(Player player, ParticleEffect effect) {
        player.getPersistentDataContainer().set(Keys.PARTICLE, PersistentDataType.STRING, effect.id());
        active.put(player.getUniqueId(), new Active(player, effect));
    }

    public void deselect(Player player) {
        player.getPersistentDataContainer().remove(Keys.PARTICLE);
        active.remove(player.getUniqueId());
    }

    // ───────────────────────────── Zeichnen ─────────────────────────────

    private void tick() {
        if (active.isEmpty()) {
            return;
        }
        step++;
        for (Active a : active.values()) {
            Player player = a.player();
            if (player.getGameMode() == GameMode.SPECTATOR || player.isDead() || Players.isVanished(player)) {
                continue;
            }
            ParticleEffect effect = a.effect();
            Location location = player.getLocation();
            Collection<Player> receivers = location.getNearbyPlayers(viewDistance);
            if (receivers.isEmpty()) {
                continue;
            }
            ParticleBuilder builder = new ParticleBuilder(effect.particle())
                    .count(1)
                    .offset(0, 0, 0)
                    .extra(effect.speed())
                    .receivers(receivers)
                    // Spieler, die den Träger nicht sehen dürfen (hidePlayer), bekommen nichts
                    .source(player);
            if (effect.data() != null) {
                builder.data(effect.data());
            }
            effect.shape().draw(builder, location.getWorld(), location.getX(), location.getY(), location.getZ(),
                    step, effect.amount());
        }
    }

    // ───────────────────────────── Menü ─────────────────────────────

    public void openMenu(Player player, int page) {
        new ParticleMenu(plugin, this, player, Math.max(1, Math.min(pageCount, page))).open();
    }

    Map<Integer, ParticleEffect> getPage(int page) {
        return pages.getOrDefault(page, Map.of());
    }

    int getPageCount() {
        return pageCount;
    }

    String getTitle() {
        return title;
    }

    int getRows() {
        return rows;
    }

    int getRemoveSlot() {
        return removeSlot;
    }

    int getBackSlot() {
        return backSlot;
    }

    /** Entfernen-Knopf mit aktuellem Partikel in der Lore */
    ItemStack buildRemoveItem(Player player) {
        ParticleEffect current = getSelected(player);
        String name = current == null ? noneName : current.plainName();
        List<String> lore = new ArrayList<>(removeLore.size());
        for (String line : removeLore) {
            lore.add(line.replace("%current%", name));
        }
        ItemStack item = removeBase.clone();
        ItemBuilder.setLore(item, lore);
        return item;
    }

    private record Active(Player player, ParticleEffect effect) {
    }
}
