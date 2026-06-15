package id.patahgaming.dndplugin;

import id.patahgaming.dndplugin.ai.DeepSeekClient;
import id.patahgaming.dndplugin.command.AtkCommand;
import id.patahgaming.dndplugin.command.RollCommand;
import id.patahgaming.dndplugin.command.TalkCommand;
import id.patahgaming.dndplugin.data.NpcDataStore;
import id.patahgaming.dndplugin.data.PlayerDataStore;
import id.patahgaming.dndplugin.data.PoiStore;
import id.patahgaming.dndplugin.data.StoryTreeStore;
import id.patahgaming.dndplugin.engine.PoiTickTask;
import id.patahgaming.dndplugin.listener.NpcInteractListener;
import id.patahgaming.dndplugin.listener.NpcSpawnListener;
import id.patahgaming.dndplugin.listener.PlayerJoinListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class DndPlugin extends JavaPlugin {

    private static DndPlugin instance;

    private PlayerDataStore playerDataStore;
    private NpcDataStore npcDataStore;
    private StoryTreeStore storyTreeStore;
    private PoiStore poiStore;
    private DeepSeekClient deepSeekClient;
    private PoiTickTask poiTickTask;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultResources();

        // Data stores
        this.playerDataStore = new PlayerDataStore(this);
        this.npcDataStore = new NpcDataStore(this);
        this.storyTreeStore = new StoryTreeStore(this);
        this.poiStore = new PoiStore(this);

        // AI client (DeepSeek API key from config.yml)
        this.deepSeekClient = new DeepSeekClient(this);

        // Listeners
        getServer().getPluginManager().registerEvents(new NpcSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new NpcInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        // Tick loop: cek POI bbox setiap 2 detik (diambil dari config)
        int intervalSeconds = getConfig().getInt("engine.poi-tick-interval-seconds", 2);
        long intervalTicks = Math.max(20L, intervalSeconds * 20L);
        this.poiTickTask = new PoiTickTask(this);
        this.poiTickTask.runTaskTimer(this, intervalTicks, intervalTicks);

        // Commands
        RollCommand rollCommand = new RollCommand(this);
        getCommand("roll").setExecutor(rollCommand);
        getCommand("roll").setTabCompleter(rollCommand);
        getCommand("atk").setExecutor(new AtkCommand(this));
        getCommand("talk").setExecutor(new TalkCommand(this));

        getLogger().info("DnD AI System enabled.");
    }

    @Override
    public void onDisable() {
        // Cancel tick task agar tidak terus berjalan setelah plugin disabled
        if (poiTickTask != null) {
            poiTickTask.cancel();
            poiTickTask = null;
        }

        if (playerDataStore != null) {
            playerDataStore.saveAll();
        }
        if (npcDataStore != null) {
            npcDataStore.saveAll();
        }
        getLogger().info("DnD AI System disabled.");
    }

    private void saveDefaultResources() {
        // Salin config.yml default jika belum ada
        saveDefaultConfig();

        // Salin folder data/ (story, npcs, poi) jika belum ada
        String[] defaultDataFiles = {
                "data/story/forest_entrance.yml",
                "data/npcs/elder_marek.yml",
                "data/poi/forest_cave.yml"
        };
        for (String path : defaultDataFiles) {
            java.io.File target = new java.io.File(getDataFolder(), path);
            if (!target.exists()) {
                try {
                    saveResource(path, false);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Resource tidak ditemukan di JAR: " + path + " — skip");
                }
            }
        }
    }

    public static DndPlugin getInstance() {
        return instance;
    }

    public PlayerDataStore getPlayerDataStore() {
        return playerDataStore;
    }

    public NpcDataStore getNpcDataStore() {
        return npcDataStore;
    }

    public StoryTreeStore getStoryTreeStore() {
        return storyTreeStore;
    }

    public PoiStore getPoiStore() {
        return poiStore;
    }

    public DeepSeekClient getDeepSeekClient() {
        return deepSeekClient;
    }

    public PoiTickTask getPoiTickTask() {
        return poiTickTask;
    }
}