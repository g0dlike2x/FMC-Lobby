package de.fmc.lobby.protection;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import de.fmc.lobby.FMCLobby;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * Lobby-Schutz: alles deaktiviert. Spieler im Baumodus (/lobby build) sind ausgenommen,
 * soweit es ums Bauen und Interagieren geht. Schaden und Hunger sind immer aus.
 * Platzieren/Abbauen regelt der {@link de.fmc.lobby.buildblock.BuildBlockListener}.
 */
public class ProtectionListener implements Listener {

    private final FMCLobby plugin;
    private double voidY;

    public ProtectionListener(FMCLobby plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        voidY = plugin.getConfigManager().getConfig().getDouble("world.void-y", 0);
    }

    private boolean builder(Entity entity) {
        return entity instanceof Player player && plugin.getBuildMode().isBuilder(player);
    }

    // ───────────────────────────── Schaden & Hunger ─────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                toSpawn(player);
            }
            return;
        }
        // Andere Entities (NPCs, Rüstungsständer, Item-Frames …) nur im Baumodus beschädigen
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            if (builder(damager)) {
                return;
            }
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFood(FoodLevelChangeEvent event) {
        event.setCancelled(true);
        if (event.getEntity() instanceof Player player && player.getFoodLevel() < 20) {
            player.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onItemDamage(PlayerItemDamageEvent event) {
        event.setCancelled(true);
    }

    // ───────────────────────────── Void & Tod ─────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo().getY() < voidY) {
            toSpawn(event.getPlayer());
        }
    }

    private void toSpawn(Player player) {
        player.setFallDistance(0);
        player.teleport(plugin.getConfigManager().getSpawn());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        event.deathMessage(null);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(plugin.getConfigManager().getSpawn());
    }

    @EventHandler
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getBuildMode().isBuilder(player)) {
            plugin.getHotbar().give(player);
        }
    }

    // ───────────────────────────── Items & Inventar ─────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        if (!builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPickup(EntityPickupItemEvent event) {
        if (!builder(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Inventar-Klicks inkl. Shift-Klick und Zahlentasten. Menü-Klicks verarbeitet der MenuListener. */
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!builder(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!builder(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ───────────────────────────── Interaktion ─────────────────────────────

    /**
     * Container, Türen, Knöpfe, Hebel usw. werden blockiert, das Item in der Hand
     * (Baublock, Enterhaken) bleibt nutzbar. Druckplatten, Stolperdraht und Ackerboden (PHYSICAL) sind aus.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (builder(event.getPlayer())) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            event.setCancelled(true);
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK)) {
            return;
        }
        // Wichtig: DENY auf den Block verhindert in Paper auch die Item-Nutzung (Platzieren, Angel).
        // Deshalb nur interaktive Blöcke (Truhen, Türen, Knöpfe, Hebel …) sperren.
        if (!clicked.getType().isInteractable()) {
            return;
        }
        // Schleichen mit Item in der Hand: Vanilla nutzt den Block nicht, das Item (Baublock) soll gehen
        if (action == Action.RIGHT_CLICK_BLOCK && event.getPlayer().isSneaking() && event.hasItem()) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        // NPCs (Citizens & Co.) bleiben anklickbar
        if (!builder(event.getPlayer()) && !event.getRightClicked().hasMetadata("NPC")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!builder(event.getPlayer()) && !event.getRightClicked().hasMetadata("NPC")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent byEntity && builder(byEntity.getRemover())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        if (!builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!builder(event.getAttacker())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!builder(event.getAttacker())) {
            event.setCancelled(true);
        }
    }

    // ───────────────────────────── Welt ─────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().clear();
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() == null || !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBurn(BlockBurnEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSpread(BlockSpreadEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onGrow(BlockGrowEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onForm(BlockFormEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFade(BlockFadeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFertilize(BlockFertilizeEvent event) {
        if (event.getPlayer() == null || !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onStructureGrow(StructureGrowEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onMoisture(MoistureChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof FallingBlock || builder(entity)) {
            return;
        }
        event.setCancelled(true);
    }

    /** Mobs auf Druckplatten / Ackerboden */
    @EventHandler(priority = EventPriority.LOW)
    public void onEntityInteract(EntityInteractEvent event) {
        event.setCancelled(true);
    }

    /** Nur Plugin-, Befehls- und Spawn-Ei-Spawns (Baumodus) sind erlaubt, z. B. für NPCs. */
    @EventHandler(priority = EventPriority.LOW)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        switch (event.getSpawnReason()) {
            case CUSTOM, COMMAND, SPAWNER_EGG, DEFAULT -> {
            }
            default -> event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPortal(PlayerPortalEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityPortal(EntityPortalEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onThunder(ThunderChangeEvent event) {
        if (event.toThunderState()) {
            event.setCancelled(true);
        }
    }
}
