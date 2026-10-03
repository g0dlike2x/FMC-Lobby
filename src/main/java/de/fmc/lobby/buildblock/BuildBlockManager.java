package de.fmc.lobby.buildblock;

import de.fmc.lobby.FMCLobby;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Baublöcke: ein einziger globaler Task arbeitet eine FIFO-Queue (Block, Ablauf-Tick) ab.
 * Da alle Blöcke dieselbe Lebensdauer haben, ist die Queue automatisch nach Ablaufzeit sortiert.
 */
public class BuildBlockManager {

    /** Sichtweite der Bröckel-Animation */
    private static final double CRUMBLE_RANGE = 24;
    /** Bröckel-Animation nur alle X Ticks aktualisieren */
    private static final int CRUMBLE_STEP = 5;

    private final FMCLobby plugin;
    private final ArrayDeque<PlacedBlock> queue = new ArrayDeque<>();
    private final Map<Block, PlacedBlock> placed = new HashMap<>();
    private final Map<UUID, Integer> counts = new HashMap<>();
    /** Spieler, deren Stack im nächsten Tick wieder auf 64 gesetzt wird */
    private final List<Player> pendingRefill = new ArrayList<>();

    private BukkitTask task;
    private int lifetimeTicks = 60;
    private int crumbleTicks = 20;
    private int sourceIdCounter;

    public BuildBlockManager(FMCLobby plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration config = plugin.getConfigManager().getConfig();
        lifetimeTicks = Math.max(5, config.getInt("build-blocks.lifetime-ticks", 60));
        crumbleTicks = Math.max(0, Math.min(lifetimeTicks, config.getInt("build-blocks.crumble-ticks", 20)));
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    /** Neuer Block in die Queue. */
    public void add(Player owner, Block block, Material material) {
        PlacedBlock pb = new PlacedBlock(block, material, owner.getUniqueId(),
                Bukkit.getCurrentTick() + lifetimeTicks, nextSourceId());
        queue.addLast(pb);
        placed.put(block, pb);
        counts.merge(owner.getUniqueId(), 1, Integer::sum);
    }

    /** Aktuelle Anzahl gleichzeitig platzierter Blöcke eines Spielers. */
    public int count(UUID owner) {
        return counts.getOrDefault(owner, 0);
    }

    /** Baublock an dieser Stelle oder null. */
    public PlacedBlock get(Block block) {
        return placed.get(block);
    }

    /** Eigener Block wurde abgebaut: aus der Verwaltung nehmen (Queue-Eintrag wird übersprungen). */
    public void forget(PlacedBlock pb) {
        if (pb.removed) {
            return;
        }
        pb.removed = true;
        placed.remove(pb.block);
        decrement(pb.owner);
    }

    public void queueRefill(Player player) {
        pendingRefill.add(player);
    }

    private int nextSourceId() {
        // Negative IDs kollidieren nicht mit echten Entities
        sourceIdCounter = (sourceIdCounter + 1) & 0x3FFFFFFF;
        return -1 - sourceIdCounter;
    }

    private void decrement(UUID owner) {
        counts.computeIfPresent(owner, (k, v) -> v <= 1 ? null : v - 1);
    }

    private void tick() {
        if (!pendingRefill.isEmpty()) {
            refill();
        }
        if (queue.isEmpty()) {
            return;
        }
        int now = Bukkit.getCurrentTick();

        // Abgelaufene Blöcke entfernen
        while (!queue.isEmpty() && queue.peekFirst().expireTick <= now) {
            PlacedBlock pb = queue.pollFirst();
            if (pb.removed) {
                continue;
            }
            removeBlock(pb);
        }

        // Bröckel-Animation (nur Blöcke im letzten Zeitfenster, gestaffelt)
        if (crumbleTicks > 0 && now % CRUMBLE_STEP == 0) {
            for (PlacedBlock pb : queue) {
                int remaining = pb.expireTick - now;
                if (remaining > crumbleTicks) {
                    break; // Queue ist sortiert, alle weiteren laufen später ab
                }
                if (pb.removed) {
                    continue;
                }
                float progress = 1.0f - (float) remaining / crumbleTicks;
                sendDamage(pb, Math.max(0.1f, Math.min(0.9f, progress)));
            }
        }
    }

    private void refill() {
        int slot = plugin.getHotbar().buildBlockSlot();
        for (Player player : pendingRefill) {
            if (!player.isOnline() || slot < 0 || plugin.getBuildMode().isBuilder(player)) {
                continue;
            }
            ItemStack current = player.getInventory().getItem(slot);
            ItemStack full = plugin.getHotbar().buildBlockItem();
            if (full != null && (current == null || current.getAmount() < full.getAmount() || !current.isSimilar(full))) {
                player.getInventory().setItem(slot, full);
            }
        }
        pendingRefill.clear();
    }

    private void removeBlock(PlacedBlock pb) {
        pb.removed = true;
        placed.remove(pb.block);
        decrement(pb.owner);
        if (pb.block.getType() == pb.material) {
            pb.block.setType(Material.AIR, false);
        }
        if (crumbleTicks > 0) {
            sendDamage(pb, 0f); // Riss beim Client zurücksetzen
        }
    }

    private void sendDamage(PlacedBlock pb, float progress) {
        Location location = pb.block.getLocation();
        for (Player viewer : location.getNearbyPlayers(CRUMBLE_RANGE)) {
            viewer.sendBlockDamage(location, progress, pb.sourceId);
        }
    }

    /** onDisable: alle offenen Blöcke sofort entfernen. */
    public void removeAll() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        Iterator<PlacedBlock> it = queue.iterator();
        while (it.hasNext()) {
            PlacedBlock pb = it.next();
            if (!pb.removed && pb.block.getType() == pb.material) {
                pb.block.setType(Material.AIR, false);
            }
        }
        queue.clear();
        placed.clear();
        counts.clear();
        pendingRefill.clear();
    }

    /** Ein platzierter Baublock. */
    public static final class PlacedBlock {
        final Block block;
        final Material material;
        final UUID owner;
        final int expireTick;
        final int sourceId;
        boolean removed;

        PlacedBlock(Block block, Material material, UUID owner, int expireTick, int sourceId) {
            this.block = block;
            this.material = material;
            this.owner = owner;
            this.expireTick = expireTick;
            this.sourceId = sourceId;
        }

        public UUID owner() {
            return owner;
        }
    }
}
