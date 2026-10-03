package de.fmc.lobby.doublejump;

import com.destroystokyo.paper.ParticleBuilder;
import de.fmc.lobby.FMCLobby;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Doppelsprung: Im Survival darf der Spieler "fliegen". Der Flug-Versuch (Leertaste in der Luft)
 * wird abgefangen und in einen Boost umgewandelt. Erst nach der Landung (und dem Cooldown)
 * wird das Fliegen wieder erlaubt. Ein globaler Task prüft nur Spieler, die gerade gesprungen sind.
 */
public class DoubleJumpListener implements Listener {

    private final FMCLobby plugin;
    /** Spieler, die gesprungen sind und auf die Landung warten (nur Main-Thread) */
    private final Set<UUID> airborne = new HashSet<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    private BukkitTask task;
    private boolean enabled;
    private double forward;
    private double height;
    private long cooldownMs;
    private String sound;
    private float pitch;
    private Particle particle;
    private int particleAmount;

    public DoubleJumpListener(FMCLobby plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration config = plugin.getConfigManager().getConfig();
        enabled = config.getBoolean("double-jump.enabled", true);
        // Werte begrenzen, damit der Anti-Cheat nicht anschlägt
        forward = clamp(config.getDouble("double-jump.forward", 1.1), 0.0, 2.0);
        height = clamp(config.getDouble("double-jump.height", 0.85), 0.2, 1.2);
        cooldownMs = Math.max(0, config.getLong("double-jump.cooldown-ms", 800));
        sound = config.getString("double-jump.sound", "entity.breeze.jump");
        pitch = (float) clamp(config.getDouble("double-jump.pitch", 1.2), 0.5, 2.0);
        particle = parseParticle(config.getString("double-jump.particle", "cloud"));
        particleAmount = Math.max(0, Math.min(30, config.getInt("double-jump.particle-amount", 12)));

        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (enabled) {
                enable(player);
            } else {
                disable(player);
            }
        }
        if (!enabled) {
            airborne.clear();
        }
    }

    /** Nur Partikel ohne Zusatzdaten (cloud, poof, end_rod …), sonst Fallback cloud. */
    private Particle parseParticle(String name) {
        try {
            NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
            Particle p = key == null ? null : Registry.PARTICLE_TYPE.get(key);
            if (p != null && p.getDataType() == Void.class) {
                return p;
            }
        } catch (IllegalArgumentException ignored) {
            // Fallback unten
        }
        plugin.getLogger().warning("double-jump.particle '" + name + "' ist ungültig oder braucht Zusatzdaten, nutze cloud.");
        return Particle.CLOUD;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            disable(player);
        }
        airborne.clear();
        cooldownUntil.clear();
    }

    /** Doppelsprung für einen Spieler freischalten (nach Join, Gamemode-Wechsel, Respawn). */
    public void enable(Player player) {
        if (enabled && eligible(player)) {
            player.setAllowFlight(true);
        }
    }

    private void disable(Player player) {
        if (eligible(player)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    /** Creative/Spectator und Baumodus fliegen normal. */
    private boolean eligible(Player player) {
        GameMode mode = player.getGameMode();
        return (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && !plugin.getBuildMode().isBuilder(player);
    }

    public void remove(UUID uuid) {
        airborne.remove(uuid);
        cooldownUntil.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!enabled || !event.isFlying() || !eligible(player)) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);

        UUID uuid = player.getUniqueId();
        airborne.add(uuid);
        cooldownUntil.put(uuid, System.currentTimeMillis() + cooldownMs);

        Location location = player.getLocation();
        Vector velocity = location.getDirection().setY(0);
        if (velocity.lengthSquared() > 1.0E-4) {
            velocity.normalize().multiply(forward);
        } else {
            velocity.zero(); // Blick senkrecht nach oben/unten
        }
        velocity.setY(height);
        player.setFallDistance(0);
        player.setVelocity(velocity);

        player.getWorld().playSound(location, sound, 0.6f, pitch);
        if (particleAmount > 0) {
            new ParticleBuilder(particle)
                    .location(location)
                    .count(particleAmount)
                    .offset(0.3, 0.05, 0.3)
                    .extra(0.02)
                    .receivers(24)
                    .source(player)
                    .spawn();
        }
    }

    /** Alle 2 Ticks: gelandete Spieler dürfen wieder springen. */
    @SuppressWarnings("deprecation") // Player#isOnGround kommt vom Client, reicht für die Lobby
    private void tick() {
        if (airborne.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<UUID> it = airborne.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !eligible(player)) {
                it.remove();
                continue;
            }
            boolean landed = player.isOnGround() || player.isInWater() || player.isClimbing();
            Long until = cooldownUntil.get(uuid);
            if (landed && (until == null || until <= now)) {
                player.setAllowFlight(true);
                it.remove();
            }
        }
    }
}
