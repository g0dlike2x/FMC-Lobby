package de.fmc.lobby.teleporter;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.network.ServerPinger;
import de.fmc.lobby.util.ItemBuilder;
import de.fmc.lobby.util.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Teleporter: Einträge aus der Config, asynchroner Status-Ping mit Cache, Live-Update offener GUIs.
 * Netzwerk-IO läuft nie auf dem Main-Thread; das GUI liest nur den Cache.
 */
public class TeleporterService {

    private final FMCLobby plugin;

    /** Unveränderliche Liste, wird beim Reload ersetzt (async lesbar) */
    private volatile List<ServerEntry> entries = List.of();
    /** Slot → Eintrag (nur Main-Thread) */
    private Map<Integer, ServerEntry> bySlot = Map.of();

    /** Status-Cache pro Eintrag-ID (async schreiben, sync lesen) */
    private final Map<String, ServerStatus> statusCache = new ConcurrentHashMap<>();
    /** Verhindert überlappende Pings auf denselben Server */
    private final Map<String, AtomicBoolean> inFlight = new ConcurrentHashMap<>();
    /** Bündelt mehrere Status-Änderungen zu einem GUI-Refresh */
    private final AtomicBoolean refreshQueued = new AtomicBoolean(false);
    /** Klick-Cooldown (nur Main-Thread) */
    private final Map<UUID, Long> clickCooldown = new HashMap<>();

    private BukkitTask pingTask;
    private volatile int timeoutMs = 1000;
    private long cooldownMs = 2000;
    private String title = "§8§nLobby§8 | Teleporter";
    private int rows = 3;
    private String statusOnline;
    private String statusOffline;

    public TeleporterService(FMCLobby plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration config = plugin.getConfigManager().getConfig();
        title = Text.color(config.getString("teleporter.title", "&8&nLobby&8 | Teleporter"));
        rows = Math.max(1, Math.min(6, config.getInt("teleporter.rows", 3)));
        timeoutMs = Math.max(200, config.getInt("teleporter.ping-timeout-ms", 1000));
        cooldownMs = Math.max(0, config.getLong("teleporter.click-cooldown-ms", 2000));
        statusOnline = plugin.getConfigManager().getMessageOrDefault("teleporter.status-online", "§a✔ Online");
        statusOffline = plugin.getConfigManager().getMessageOrDefault("teleporter.status-offline", "§c✖ Neustart läuft");

        List<ServerEntry> list = new ArrayList<>();
        Map<Integer, ServerEntry> slots = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("teleporter.servers");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                ServerEntry entry = parse(id, s);
                if (entry == null) {
                    continue;
                }
                if (slots.containsKey(entry.slot())) {
                    plugin.getLogger().warning("Teleporter: Slot " + entry.slot() + " ist doppelt belegt (" + id + ").");
                    continue;
                }
                list.add(entry);
                slots.put(entry.slot(), entry);
            }
        }
        entries = Collections.unmodifiableList(list);
        bySlot = slots;

        // Alte Cache-Einträge entfernen
        statusCache.keySet().removeIf(key -> list.stream().noneMatch(e -> e.id().equals(key)));
        inFlight.keySet().removeIf(key -> list.stream().noneMatch(e -> e.id().equals(key)));

        restartTask(Math.max(1, config.getLong("teleporter.status-interval-seconds", 3)));
    }

    private ServerEntry parse(String id, ConfigurationSection s) {
        int slot = s.getInt("slot", -1);
        if (slot < 0 || slot >= rows * 9) {
            plugin.getLogger().warning("Teleporter-Eintrag '" + id + "': Slot " + slot + " liegt außerhalb des GUIs.");
            return null;
        }
        String proxyName = s.getString("proxy-name");
        if (proxyName == null || proxyName.isBlank()) {
            plugin.getLogger().warning("Teleporter-Eintrag '" + id + "': proxy-name fehlt.");
            return null;
        }
        Material material = Material.matchMaterial(s.getString("material", "GRASS_BLOCK"));
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("Teleporter-Eintrag '" + id + "': unbekanntes Material, nutze GRASS_BLOCK.");
            material = Material.GRASS_BLOCK;
        }
        String name = Text.color(s.getString("name", "&a&l" + proxyName));
        ItemStack base = ItemBuilder.build(material, 1, name, null, s.getBoolean("glow", false));
        String plain = PlainTextComponentSerializer.plainText().serialize(Text.component(name));
        return new ServerEntry(id, slot, proxyName,
                s.getString("host", "127.0.0.1"), s.getInt("port", 25565),
                base, List.copyOf(Text.color(s.getStringList("lore"))), plain);
    }

    // ───────────────────────────── Status-Ping ─────────────────────────────

    private void restartTask(long intervalSeconds) {
        if (pingTask != null) {
            pingTask.cancel();
        }
        pingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pingAll, 0L, intervalSeconds * 20L);
    }

    public void shutdown() {
        if (pingTask != null) {
            pingTask.cancel();
            pingTask = null;
        }
    }

    /** Läuft asynchron: jeder Server wird parallel angepingt, damit ein Timeout die anderen nicht bremst. */
    private void pingAll() {
        for (ServerEntry entry : entries) {
            AtomicBoolean flag = inFlight.computeIfAbsent(entry.id(), k -> new AtomicBoolean(false));
            if (!flag.compareAndSet(false, true)) {
                continue;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    ServerStatus status = ServerPinger.ping(entry.host(), entry.port(), timeoutMs);
                    ServerStatus old = statusCache.put(entry.id(), status);
                    if (!status.equals(old)) {
                        queueRefresh();
                    }
                } finally {
                    flag.set(false);
                }
            });
        }
    }

    /** Zurück auf den Main-Thread, aber nur für das GUI-Update und gebündelt. */
    private void queueRefresh() {
        if (!plugin.isEnabled() || !refreshQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                refreshQueued.set(false);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder(false);
                    if (holder instanceof TeleporterMenu menu) {
                        menu.render();
                    }
                }
            });
        } catch (IllegalPluginAccessException e) {
            // Plugin wird gerade deaktiviert
            refreshQueued.set(false);
        }
    }

    // ───────────────────────────── Zugriff für das GUI ─────────────────────────────

    public void open(Player player) {
        new TeleporterMenu(plugin, this, player).open();
    }

    public List<ServerEntry> getEntries() {
        return entries;
    }

    public ServerEntry getBySlot(int slot) {
        return bySlot.get(slot);
    }

    public ServerStatus getStatus(ServerEntry entry) {
        return statusCache.getOrDefault(entry.id(), ServerStatus.OFFLINE);
    }

    public String getTitle() {
        return title;
    }

    public int getRows() {
        return rows;
    }

    public String getStatusOnline() {
        return statusOnline;
    }

    public String getStatusOffline() {
        return statusOffline;
    }

    /** true, wenn der Klick erlaubt ist (und setzt den Cooldown). */
    public boolean tryClick(Player player) {
        long now = System.currentTimeMillis();
        Long until = clickCooldown.get(player.getUniqueId());
        if (until != null && until > now) {
            return false;
        }
        clickCooldown.put(player.getUniqueId(), now + cooldownMs);
        return true;
    }

    public void remove(UUID uuid) {
        clickCooldown.remove(uuid);
    }
}
