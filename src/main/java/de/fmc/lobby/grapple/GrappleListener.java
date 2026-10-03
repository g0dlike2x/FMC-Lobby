package de.fmc.lobby.grapple;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.hotbar.HotbarManager;
import de.fmc.lobby.util.Keys;
import de.fmc.lobby.util.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enterhaken: Steckt der Haken in / an einem Block, wird der Spieler beim Einholen hingezogen.
 * Velocity = (Haken − Spieler) × strength + Y-Boost, begrenzt auf max-velocity / max-y-velocity.
 */
public class GrappleListener implements Listener {

    /** Prüf-Abstände um den Haken herum für "liegt neben einem festen Block" */
    private static final double[][] OFFSETS = {
            {0, 0, 0}, {0.4, 0, 0}, {-0.4, 0, 0}, {0, 0.4, 0}, {0, -0.4, 0}, {0, 0, 0.4}, {0, 0, -0.4}
    };

    private final FMCLobby plugin;
    /** Cooldown-Ende pro Spieler (nur Main-Thread) */
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private long cooldownMs;
    private double strength;
    private double yBoost;
    private double maxVelocity;
    private double maxYVelocity;
    private double castMultiplier;
    private boolean stickToBlocks;
    private String soundPull;
    private String soundWind;

    public GrappleListener(FMCLobby plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration config = plugin.getConfigManager().getConfig();
        cooldownMs = Math.max(0, config.getLong("grapple.cooldown-ms", 1500));
        // Werte begrenzen, damit der Anti-Cheat nicht anschlägt
        strength = clamp(config.getDouble("grapple.strength", 0.3), 0.05, 1.0);
        yBoost = clamp(config.getDouble("grapple.y-boost", 0.35), 0.0, 1.0);
        maxVelocity = clamp(config.getDouble("grapple.max-velocity", 2.0), 0.5, 3.0);
        maxYVelocity = clamp(config.getDouble("grapple.max-y-velocity", 1.6), 0.3, 2.5);
        castMultiplier = clamp(config.getDouble("grapple.cast-multiplier", 1.5), 0.5, 3.0);
        stickToBlocks = config.getBoolean("grapple.stick-to-blocks", true);
        soundPull = config.getString("grapple.sounds.pull", "entity.fishing_bobber.retrieve");
        soundWind = config.getString("grapple.sounds.wind", "entity.wind_charge.wind_burst");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public void remove(UUID uuid) {
        cooldowns.remove(uuid);
    }

    private static boolean holdsGrapple(Player player) {
        return HotbarManager.GRAPPLE.equals(HotbarManager.idOf(player.getInventory().getItemInMainHand()))
                || HotbarManager.GRAPPLE.equals(HotbarManager.idOf(player.getInventory().getItemInOffHand()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        FishHook hook = event.getHook();

        switch (event.getState()) {
            case FISHING -> {
                if (!holdsGrapple(player)) {
                    return;
                }
                long remaining = remaining(player);
                if (remaining > 0) {
                    event.setCancelled(true);
                    sendCooldown(player, remaining);
                    return;
                }
                hook.getPersistentDataContainer().set(Keys.GRAPPLE_HOOK, PersistentDataType.BYTE, (byte) 1);
                hook.setVelocity(hook.getVelocity().multiply(castMultiplier));
            }
            case CAUGHT_ENTITY, CAUGHT_FISH -> {
                // In der Lobby weder Spieler heranziehen noch Fische/Loot angeln
                event.setCancelled(true);
                hook.remove();
            }
            case IN_GROUND, REEL_IN -> {
                if (!hook.getPersistentDataContainer().has(Keys.GRAPPLE_HOOK)) {
                    return;
                }
                boolean anchored = event.getState() == PlayerFishEvent.State.IN_GROUND
                        || hook.getPersistentDataContainer().has(Keys.GRAPPLE_ANCHORED)
                        || nearSolid(hook.getLocation());
                if (!anchored) {
                    return;
                }
                long remaining = remaining(player);
                if (remaining > 0) {
                    sendCooldown(player, remaining);
                    return;
                }
                pull(player, hook.getLocation());
            }
            default -> {
            }
        }
    }

    /** Haken bleibt an Wand und Decke hängen, statt herunterzufallen. */
    @EventHandler(ignoreCancelled = true)
    public void onHookHit(ProjectileHitEvent event) {
        if (!stickToBlocks || event.getHitBlock() == null || !(event.getEntity() instanceof FishHook hook)) {
            return;
        }
        if (!hook.getPersistentDataContainer().has(Keys.GRAPPLE_HOOK)) {
            return;
        }
        hook.getPersistentDataContainer().set(Keys.GRAPPLE_ANCHORED, PersistentDataType.BYTE, (byte) 1);
        hook.setGravity(false);
        // Vanilla setzt die Bewegung noch bis zur Wand, einen Tick später einfrieren
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (hook.isValid()) {
                hook.setVelocity(new Vector());
            }
        });
    }

    private void pull(Player player, Location target) {
        Location from = player.getLocation();
        Vector velocity = target.toVector().subtract(from.toVector()).multiply(strength);
        velocity.setY(velocity.getY() + yBoost);
        double length = velocity.length();
        if (length > maxVelocity) {
            velocity.multiply(maxVelocity / length);
        }
        if (velocity.getY() > maxYVelocity) {
            velocity.setY(maxYVelocity);
        }
        if (!Double.isFinite(velocity.getX()) || !Double.isFinite(velocity.getY()) || !Double.isFinite(velocity.getZ())) {
            return;
        }
        player.setFallDistance(0);
        player.setVelocity(velocity);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownMs);
        player.playSound(from, soundPull, 0.8f, 1.0f);
        player.playSound(from, soundWind, 0.5f, 1.2f);
    }

    private long remaining(Player player) {
        Long until = cooldowns.get(player.getUniqueId());
        if (until == null) {
            return 0;
        }
        return Math.max(0, until - System.currentTimeMillis());
    }

    private void sendCooldown(Player player, long remainingMs) {
        plugin.getConfigManager().actionBar(player, "grapple.cooldown",
                "%time%", NumberFormatter.decimal(remainingMs / 1000.0, 1));
    }

    private static boolean nearSolid(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        for (double[] o : OFFSETS) {
            if (world.getBlockAt(
                    (int) Math.floor(location.getX() + o[0]),
                    (int) Math.floor(location.getY() + o[1]),
                    (int) Math.floor(location.getZ() + o[2])).getType().isSolid()) {
                return true;
            }
        }
        return false;
    }
}
