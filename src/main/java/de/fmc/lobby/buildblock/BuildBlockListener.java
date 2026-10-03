package de.fmc.lobby.buildblock;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.hotbar.HotbarManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Platzieren nur mit dem Baublock-Item, nur auf Luft, nicht nahe Spawn/NPCs und im erlaubten Höhenbereich.
 * Abbauen nur für eigene Baublöcke (oder im Baumodus).
 */
public class BuildBlockListener implements Listener {

    private final FMCLobby plugin;
    private final BuildBlockManager manager;

    private int limit;
    private double radiusSquared;
    private double radius;
    private int minY;
    private int maxY;
    private final List<Location> protectedPoints = new ArrayList<>();

    public BuildBlockListener(FMCLobby plugin, BuildBlockManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void reload() {
        YamlConfiguration config = plugin.getConfigManager().getConfig();
        limit = Math.max(1, config.getInt("build-blocks.limit-per-player", 30));
        radius = Math.max(0, config.getDouble("build-blocks.no-build-radius", 8));
        radiusSquared = radius * radius;
        minY = config.getInt("build-blocks.min-y", Integer.MIN_VALUE);
        maxY = config.getInt("build-blocks.max-y", Integer.MAX_VALUE);

        protectedPoints.clear();
        for (String raw : config.getStringList("build-blocks.protected-points")) {
            String[] parts = raw.split(";");
            if (parts.length != 4) {
                plugin.getLogger().warning("build-blocks.protected-points: ungültiger Eintrag '" + raw + "' (welt;x;y;z)");
                continue;
            }
            World world = Bukkit.getWorld(parts[0].trim());
            if (world == null) {
                plugin.getLogger().warning("build-blocks.protected-points: Welt '" + parts[0] + "' nicht gefunden.");
                continue;
            }
            try {
                protectedPoints.add(new Location(world, Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim())));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("build-blocks.protected-points: ungültige Zahl in '" + raw + "'");
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (plugin.getBuildMode().isBuilder(player)) {
            return;
        }
        ItemStack item = event.getItemInHand();
        if (!HotbarManager.BUILD_BLOCK.equals(HotbarManager.idOf(item))) {
            event.setCancelled(true);
            return;
        }
        // Nur auf Luft (kein Ersetzen von Gras, Wasser, Schnee …)
        if (!event.getBlockReplacedState().getType().isAir()) {
            event.setCancelled(true);
            return;
        }
        Block block = event.getBlockPlaced();
        if (block.getY() < minY || block.getY() > maxY) {
            event.setCancelled(true);
            plugin.getConfigManager().actionBar(player, "build-blocks.height");
            return;
        }
        if (isProtected(block)) {
            event.setCancelled(true);
            plugin.getConfigManager().actionBar(player, "build-blocks.too-close");
            return;
        }
        if (manager.count(player.getUniqueId()) >= limit) {
            event.setCancelled(true);
            plugin.getConfigManager().actionBar(player, "build-blocks.limit", "%limit%", Integer.toString(limit));
            return;
        }

        manager.add(player, block, block.getType());

        // Stack sofort wieder auffüllen (+ Sicherheitsnetz im nächsten Tick)
        ItemStack full = plugin.getHotbar().buildBlockItem();
        if (full != null) {
            player.getInventory().setItem(event.getHand(), full);
        }
        manager.queueRefill(player);
    }

    private boolean isProtected(Block block) {
        if (radiusSquared <= 0) {
            return false;
        }
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        Location spawn = plugin.getConfigManager().getSpawn();
        if (spawn.getWorld() == center.getWorld() && spawn.distanceSquared(center) <= radiusSquared) {
            return true;
        }
        for (Location point : protectedPoints) {
            if (point.getWorld() == center.getWorld() && point.distanceSquared(center) <= radiusSquared) {
                return true;
            }
        }
        // NPCs (Citizens & Co. setzen das Metadata-Flag "NPC")
        return !center.getWorld().getNearbyEntities(center, radius, radius, radius,
                entity -> entity.hasMetadata("NPC")).isEmpty();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.getBuildMode().isBuilder(player)) {
            return;
        }
        BuildBlockManager.PlacedBlock pb = manager.get(event.getBlock());
        if (pb == null || !pb.owner().equals(player.getUniqueId())) {
            // Lobby-Blöcke und fremde Baublöcke sind geschützt
            event.setCancelled(true);
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        manager.forget(pb);
    }

    /** Eigene Baublöcke brechen sofort. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        if (plugin.getBuildMode().isBuilder(event.getPlayer())) {
            return;
        }
        BuildBlockManager.PlacedBlock pb = manager.get(event.getBlock());
        if (pb != null && pb.owner().equals(event.getPlayer().getUniqueId())) {
            event.setInstaBreak(true);
        }
    }
}
