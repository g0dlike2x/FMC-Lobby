package de.fmc.lobby;

import de.fmc.lobby.buildblock.BuildBlockListener;
import de.fmc.lobby.buildblock.BuildBlockManager;
import de.fmc.lobby.command.LobbyCommand;
import de.fmc.lobby.config.ConfigManager;
import de.fmc.lobby.display.ScoreboardManager;
import de.fmc.lobby.display.TablistManager;
import de.fmc.lobby.grapple.GrappleListener;
import de.fmc.lobby.gui.GuiItems;
import de.fmc.lobby.gui.LobbyMenu;
import de.fmc.lobby.gui.MenuListener;
import de.fmc.lobby.hook.LuckPermsHook;
import de.fmc.lobby.hook.PlaceholderHook;
import de.fmc.lobby.hotbar.HotbarListener;
import de.fmc.lobby.hotbar.HotbarManager;
import de.fmc.lobby.listener.JoinQuitListener;
import de.fmc.lobby.network.BungeeMessenger;
import de.fmc.lobby.particle.ParticleManager;
import de.fmc.lobby.protection.BuildModeManager;
import de.fmc.lobby.protection.ProtectionListener;
import de.fmc.lobby.teleporter.TeleporterService;
import de.fmc.lobby.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * FMC-Lobby – Lobby-Server von FARMMC.DE.
 * Fängt die Spieler beim CityBuild-Neustart auf und schickt sie per Teleporter zurück.
 */
public final class FMCLobby extends JavaPlugin {

    private ConfigManager configManager;
    private BungeeMessenger messenger;
    private BuildModeManager buildMode;
    private GuiItems guiItems;
    private HotbarManager hotbar;
    private TeleporterService teleporter;
    private ParticleManager particles;
    private GrappleListener grapple;
    private BuildBlockManager buildBlocks;
    private BuildBlockListener buildBlockListener;
    private ProtectionListener protection;
    private ScoreboardManager scoreboard;
    private TablistManager tablist;

    @Override
    public void onEnable() {
        Keys.init(this);
        configManager = new ConfigManager(this);
        configManager.load();

        messenger = new BungeeMessenger(this);
        messenger.register();

        buildMode = new BuildModeManager();
        guiItems = new GuiItems(this);
        hotbar = new HotbarManager(this);
        teleporter = new TeleporterService(this);
        particles = new ParticleManager(this);
        grapple = new GrappleListener(this);
        buildBlocks = new BuildBlockManager(this);
        buildBlockListener = new BuildBlockListener(this, buildBlocks);
        protection = new ProtectionListener(this);
        scoreboard = new ScoreboardManager(this);
        tablist = new TablistManager(this);

        reloadComponents();
        applyWorldSettings();

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new HotbarListener(this), this);
        pm.registerEvents(new JoinQuitListener(this), this);
        pm.registerEvents(protection, this);
        pm.registerEvents(buildBlockListener, this);
        pm.registerEvents(grapple, this);

        PluginCommand command = getCommand("lobby");
        if (command != null) {
            LobbyCommand executor = new LobbyCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        // Falls per PlugMan neu geladen: bereits verbundene Spieler direkt einrichten
        for (Player player : Bukkit.getOnlinePlayers()) {
            preparePlayer(player, false);
        }

        getLogger().info("FMC-Lobby " + getPluginMeta().getVersion() + " aktiviert"
                + (PlaceholderHook.isAvailable() ? " (PlaceholderAPI)" : "")
                + (LuckPermsHook.isAvailable() ? " (LuckPerms)" : "") + ".");
    }

    @Override
    public void onDisable() {
        closeMenus();
        if (buildBlocks != null) {
            // Alle offenen Baublöcke sofort entfernen
            buildBlocks.removeAll();
        }
        if (teleporter != null) {
            teleporter.shutdown();
        }
        if (particles != null) {
            particles.shutdown();
        }
        if (scoreboard != null) {
            scoreboard.shutdown();
        }
        if (tablist != null) {
            tablist.shutdown();
        }
        if (messenger != null) {
            messenger.unregister();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }

    /** /lobby reload: Dateien neu laden und alle Caches neu bauen. */
    public void reloadAll() {
        closeMenus();
        configManager.load();
        reloadComponents();
        applyWorldSettings();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!buildMode.isBuilder(player)) {
                hotbar.give(player);
            }
        }
    }

    private void reloadComponents() {
        PlaceholderHook.refresh();
        LuckPermsHook.refresh();
        messenger.reload();
        guiItems.reload();
        hotbar.reload();
        teleporter.reload();
        particles.reload();
        grapple.reload();
        buildBlocks.reload();
        buildBlockListener.reload();
        protection.reload();
        scoreboard.reload();
        tablist.reload();
    }

    /** Gamerules setzen und Autosave abschalten. */
    private void applyWorldSettings() {
        boolean disableAutosave = configManager.getConfig().getBoolean("world.disable-autosave", true);
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            world.setGameRule(GameRules.SPAWN_MOBS, false);
            world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
            // Ersatz für doFireTick=false seit 1.21.11
            world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
            world.setStorm(false);
            world.setThundering(false);
            world.setAutoSave(!disableAutosave);
        }
    }

    private void closeMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof LobbyMenu) {
                player.closeInventory();
            }
        }
    }

    /**
     * Spieler in den Lobby-Zustand versetzen: Survival (für Baublöcke), volle Leben und Sättigung,
     * Inventar leeren, Hotbar setzen, optional zum Spawn.
     */
    public void preparePlayer(Player player, boolean teleport) {
        player.setGameMode(GameMode.SURVIVAL);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        player.setFireTicks(0);
        player.setFallDistance(0);
        hotbar.give(player);
        if (teleport) {
            player.teleport(configManager.getSpawn());
        }
        particles.load(player);
    }

    // ───────────────────────────── Getter ─────────────────────────────

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public BungeeMessenger getMessenger() {
        return messenger;
    }

    public BuildModeManager getBuildMode() {
        return buildMode;
    }

    public GuiItems getGuiItems() {
        return guiItems;
    }

    public HotbarManager getHotbar() {
        return hotbar;
    }

    public TeleporterService getTeleporter() {
        return teleporter;
    }

    public ParticleManager getParticles() {
        return particles;
    }

    public GrappleListener getGrapple() {
        return grapple;
    }

    public ScoreboardManager getScoreboard() {
        return scoreboard;
    }

    public TablistManager getTablist() {
        return tablist;
    }
}
