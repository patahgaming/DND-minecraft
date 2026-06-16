package id.patahgaming.dndplugin;

import id.patahgaming.dndplugin.ai.DeepSeekClient;
import id.patahgaming.dndplugin.ai.NarratorService;
import id.patahgaming.dndplugin.ai.NpcDialogueService;
import id.patahgaming.dndplugin.command.AtkCommand;
import id.patahgaming.dndplugin.command.BattleCommand;
import id.patahgaming.dndplugin.command.CastCommand;
import id.patahgaming.dndplugin.command.CharacterCommand;
import id.patahgaming.dndplugin.command.DndCommand;
import id.patahgaming.dndplugin.command.NewNpcCommand;
import id.patahgaming.dndplugin.command.RollCommand;
import id.patahgaming.dndplugin.command.SpawnNpcCommand;
import id.patahgaming.dndplugin.command.TalkCommand;
import id.patahgaming.dndplugin.data.NpcDataStore;
import id.patahgaming.dndplugin.data.PlayerDataStore;
import id.patahgaming.dndplugin.data.PoiStore;
import id.patahgaming.dndplugin.data.SrdRulesStore;
import id.patahgaming.dndplugin.data.StoryTreeStore;
import id.patahgaming.dndplugin.engine.PoiTickTask;
import id.patahgaming.dndplugin.engine.combat.TurnManager;
import id.patahgaming.dndplugin.listener.CombatListener;
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
    private NarratorService narratorService;
    private NpcDialogueService npcDialogueService;
    private TurnManager turnManager;
    private SrdRulesStore srdRulesStore;
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

        // AI services (DeepSeek API key from config.yml)
        this.deepSeekClient = new DeepSeekClient(this);
        this.narratorService = new NarratorService(this);
        this.npcDialogueService = new NpcDialogueService(this);
        this.turnManager = new TurnManager(this);

        // SRD rules (load async biar startup cepet)
        this.srdRulesStore = new SrdRulesStore(this);
        getServer().getScheduler().runTaskAsynchronously(this, () -> srdRulesStore.load());

        // Listeners
        getServer().getPluginManager().registerEvents(new NpcSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new NpcInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);

        // Tick loop: cek POI bbox setiap 2 detik (diambil dari config)
        int intervalSeconds = getConfig().getInt("engine.poi-tick-interval-seconds", 2);
        long intervalTicks = Math.max(20L, intervalSeconds * 20L);
        this.poiTickTask = new PoiTickTask(this);
        this.poiTickTask.runTaskTimer(this, intervalTicks, intervalTicks);

        // Commands
        RollCommand rollCommand = new RollCommand(this);
        getCommand("roll").setExecutor(rollCommand);
        getCommand("roll").setTabCompleter(rollCommand);
        AtkCommand atkCommand = new AtkCommand(this);
        getCommand("atk").setExecutor(atkCommand);
        getCommand("atk").setTabCompleter(atkCommand);
        TalkCommand talkCommand = new TalkCommand(this);
        getCommand("talk").setExecutor(talkCommand);
        getCommand("talk").setTabCompleter(talkCommand);
        SpawnNpcCommand spawnNpcCommand = new SpawnNpcCommand(this);
        getCommand("spawnnpc").setExecutor(spawnNpcCommand);
        getCommand("spawnnpc").setTabCompleter(spawnNpcCommand);
        NewNpcCommand newNpcCommand = new NewNpcCommand(this);
        getCommand("newnpc").setExecutor(newNpcCommand);
        getCommand("newnpc").setTabCompleter(newNpcCommand);
        DndCommand dndCommand = new DndCommand(this);
        getCommand("dnd").setExecutor(dndCommand);
        getCommand("dnd").setTabCompleter(dndCommand);
        BattleCommand battleCommand = new BattleCommand(this);
        getCommand("battle").setExecutor(battleCommand);
        getCommand("battle").setTabCompleter(battleCommand);
        CharacterCommand characterCommand = new CharacterCommand(this);
        getCommand("character").setExecutor(characterCommand);
        getCommand("character").setTabCompleter(characterCommand);
        CastCommand castCommand = new CastCommand(this);
        getCommand("cast").setExecutor(castCommand);
        getCommand("cast").setTabCompleter(castCommand);

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

    public NarratorService getNarratorService() {
        return narratorService;
    }

    public NpcDialogueService getNpcDialogueService() {
        return npcDialogueService;
    }

    public TurnManager getTurnManager() {
        return turnManager;
    }

    public SrdRulesStore getSrdRulesStore() {
        return srdRulesStore;
    }

    public PoiTickTask getPoiTickTask() {
        return poiTickTask;
    }

    public void setPoiTickTask(PoiTickTask task) {
        this.poiTickTask = task;
    }

    /**
     * Hot reload semua data stores dari disk — tanpa restart server.
     * Dipanggil dari /dnd reload.
     */
    public void reloadDataStores() {
        this.playerDataStore = new PlayerDataStore(this);
        this.npcDataStore = new NpcDataStore(this);
        this.storyTreeStore = new StoryTreeStore(this);
        this.poiStore = new PoiStore(this);
        if (this.srdRulesStore != null) {
            this.srdRulesStore.reload();
        }
        getLogger().info("Data stores reloaded from disk.");
    }
}