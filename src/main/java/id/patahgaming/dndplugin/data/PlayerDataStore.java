package id.patahgaming.dndplugin.data;

import id.patahgaming.dndplugin.DndPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Source of truth untuk character sheet: PersistentDataContainer (PDC) pada
 * player + file YAML per player sebagai persistence/backup.
 *
 * Sinkronisasi:
 * - HP Minecraft (player.getHealth()) = HP karakter (1:1)
 * - level, str/dex/con/int/wis/cha disimpan di PDC
 * - story_progress.current_node di-track shared untuk seluruh party
 */
public class PlayerDataStore {

    private static final NamespacedKey KEY_LEVEL = key("level");
    private static final NamespacedKey KEY_STR = key("str");
    private static final NamespacedKey KEY_DEX = key("dex");
    private static final NamespacedKey KEY_CON = key("con");
    private static final NamespacedKey KEY_INT = key("int");
    private static final NamespacedKey KEY_WIS = key("wis");
    private static final NamespacedKey KEY_CHA = key("cha");

    private final DndPlugin plugin;
    private final File playersFolder;

    // Story progress shared untuk seluruh party (1 party = semua player)
    // volatile untuk visibility antar thread (main thread + async tick task)
    private volatile String partyCurrentNode = "forest_entrance";

    // Story node ter-advance dari semua player — digunakan untuk memilih
    // partyCurrentNode secara consensus (node paling jauh yang pernah dicapai)
    // Disimpan per-player UUID, dibaca saat join untuk menentukan party progress.
    private final Map<UUID, String> playerNodeProgress = new ConcurrentHashMap<>();

    private final Map<UUID, Map<String, Object>> cache = new ConcurrentHashMap<>();

    public PlayerDataStore(DndPlugin plugin) {
        this.plugin = plugin;
        this.playersFolder = new File(plugin.getDataFolder(), "data/players");
        if (!playersFolder.exists()) {
            playersFolder.mkdirs();
        }
    }

    private static NamespacedKey key(String name) {
        return new NamespacedKey("dndplugin", name);
    }

    /**
     * Inisialisasi karakter baru saat player pertama kali join.
     * Jika PDC belum ada, coba restore dari YAML backup dulu;
     * jika YAML juga tidak ada, inisialisasi dengan default stats.
     */
    public void initIfAbsent(Player player) {
        UUID uuid = player.getUniqueId();
        var pdc = player.getPersistentDataContainer();
        if (!pdc.has(KEY_LEVEL, PersistentDataType.INTEGER)) {
            // Coba restore dari YAML backup dulu
            if (!restoreFromYaml(player)) {
                pdc.set(KEY_LEVEL, PersistentDataType.INTEGER, 1);
                pdc.set(KEY_STR, PersistentDataType.INTEGER, 10);
                pdc.set(KEY_DEX, PersistentDataType.INTEGER, 10);
                pdc.set(KEY_CON, PersistentDataType.INTEGER, 10);
                pdc.set(KEY_INT, PersistentDataType.INTEGER, 10);
                pdc.set(KEY_WIS, PersistentDataType.INTEGER, 10);
                pdc.set(KEY_CHA, PersistentDataType.INTEGER, 10);
                plugin.getLogger().info("Initialized new character sheet for " + player.getName());
            }
        }
        // Load metadata (story progress, dll) dari YAML, update party consensus
        loadMetadataFromYaml(uuid);
    }

    /**
     * Coba restore stats dari YAML backup ke PDC.
     * Return true jika berhasil restore.
     */
    @SuppressWarnings("unchecked")
    private boolean restoreFromYaml(Player player) {
        File file = new File(playersFolder, player.getUniqueId() + ".yml");
        if (!file.exists()) return false;

        try (InputStream in = java.nio.file.Files.newInputStream(file.toPath())) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(in);
            if (data == null) return false;

            Object statsRaw = data.get("stats");
            if (statsRaw instanceof Map<?, ?> stats) {
                var pdc = player.getPersistentDataContainer();
                Object levelObj = data.get("level");
                pdc.set(KEY_LEVEL, PersistentDataType.INTEGER,
                        levelObj instanceof Number n ? n.intValue() : 1);
                pdc.set(KEY_STR, PersistentDataType.INTEGER, toInt(stats.get("str"), 10));
                pdc.set(KEY_DEX, PersistentDataType.INTEGER, toInt(stats.get("dex"), 10));
                pdc.set(KEY_CON, PersistentDataType.INTEGER, toInt(stats.get("con"), 10));
                pdc.set(KEY_INT, PersistentDataType.INTEGER, toInt(stats.get("int"), 10));
                pdc.set(KEY_WIS, PersistentDataType.INTEGER, toInt(stats.get("wis"), 10));
                pdc.set(KEY_CHA, PersistentDataType.INTEGER, toInt(stats.get("cha"), 10));
                plugin.getLogger().info("Restored character sheet from YAML for " + player.getName());
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to restore player data from YAML for " + player.getUniqueId() + ": " + e.getMessage());
        }
        return false;
    }

    private static int toInt(Object o, int defaultVal) {
        if (o instanceof Number n) return n.intValue();
        if (o != null) {
            try { return Integer.parseInt(o.toString()); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    public int getLevel(Player player) {
        return player.getPersistentDataContainer().getOrDefault(KEY_LEVEL, PersistentDataType.INTEGER, 1);
    }

    public int getAbility(Player player, String ability) {
        NamespacedKey k = switch (ability.toLowerCase()) {
            case "str" -> KEY_STR;
            case "dex" -> KEY_DEX;
            case "con" -> KEY_CON;
            case "int" -> KEY_INT;
            case "wis" -> KEY_WIS;
            case "cha" -> KEY_CHA;
            default -> throw new IllegalArgumentException("Unknown ability: " + ability);
        };
        return player.getPersistentDataContainer().getOrDefault(k, PersistentDataType.INTEGER, 10);
    }

    /** Modifier D&D standar: floor((score - 10) / 2) */
    public int getAbilityModifier(Player player, String ability) {
        return Math.floorDiv(getAbility(player, ability) - 10, 2);
    }

    public String getPartyCurrentNode() {
        return partyCurrentNode;
    }

    /**
     * Set party current node. Ini akan menyimpan node baru ke playerNodeProgress
     * untuk semua player yang sedang online, mereset PoiTickTask triggered set,
     * dan refresh scoreboard semua player.
     */
    public void setPartyCurrentNode(String nodeId) {
        if (nodeId.equals(this.partyCurrentNode)) return;

        this.partyCurrentNode = nodeId;

        // Update per-player tracking untuk semua player online
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            playerNodeProgress.put(p.getUniqueId(), nodeId);
        }

        // Reset PoiTickTask triggered set untuk node baru
        id.patahgaming.dndplugin.engine.PoiTickTask tickTask = plugin.getPoiTickTask();
        if (tickTask != null) {
            Bukkit.getScheduler().runTask(plugin, tickTask::resetForNewNode);
        }

        // Refresh scoreboard semua player (Node field berubah)
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            id.patahgaming.dndplugin.ui.ScoreboardManager.refresh(plugin, p);
        }
    }

    /**
     * Load metadata (story progress, inventory hotbar mapping, dll) dari
     * file YAML per player. Tidak overwrite partyCurrentNode secara buta —
     * menggunakan consensus (node paling jauh dari semua player).
     */
    @SuppressWarnings("unchecked")
    private void loadMetadataFromYaml(UUID uuid) {
        File file = new File(playersFolder, uuid + ".yml");
        if (!file.exists()) {
            cache.put(uuid, new HashMap<>());
            return;
        }
        try (InputStream in = java.nio.file.Files.newInputStream(file.toPath())) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(in);
            cache.put(uuid, data != null ? data : new HashMap<>());

            // Track per-player node progress (untuk party consensus)
            Object storyProgress = cache.get(uuid).get("story_progress");
            if (storyProgress instanceof Map<?, ?> sp) {
                Object node = sp.get("current_node");
                if (node != null) {
                    playerNodeProgress.put(uuid, node.toString());
                }
            }

            // Update party consensus setelah load
            updatePartyConsensus();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load player data for " + uuid + ": " + e.getMessage());
            cache.put(uuid, new HashMap<>());
        }
    }

    /**
     * Set party current node ke node paling jauh yang pernah dicapai
     * oleh SEMUA player yang pernah login (consensus-based).
     * Jika belum ada data player manapun, tetap di default.
     *
     * Story tree node diasumsikan di-load oleh StoryTreeStore dan
     * urutannya ditentukan oleh traversal tree (depth-first).
     */
    private void updatePartyConsensus() {
        if (playerNodeProgress.isEmpty()) return;

        // Gunakan node dari player pertama sebagai baseline,
        // lalu validasi dengan player lain
        String consensusNode = null;
        for (String node : playerNodeProgress.values()) {
            if (consensusNode == null) {
                consensusNode = node;
            } else if (!consensusNode.equals(node)) {
                // Jika terjadi perbedaan, ambil node yang berbeda dari default
                // (anggap node non-default lebih "advance")
                // TODO: Implementasi tree-depth comparison untuk konsensus yang lebih akurat
                if (!"forest_entrance".equals(node)) {
                    consensusNode = node;
                }
            }
        }

        if (consensusNode != null && !consensusNode.equals(partyCurrentNode)) {
            String oldNode = partyCurrentNode;
            partyCurrentNode = consensusNode;
            plugin.getLogger().info("Party consensus node: " + oldNode + " -> " + consensusNode);

            // Reset PoiTickTask triggered set untuk node baru
            id.patahgaming.dndplugin.engine.PoiTickTask tickTask = plugin.getPoiTickTask();
            if (tickTask != null) {
                Bukkit.getScheduler().runTask(plugin, tickTask::resetForNewNode);
            }
        }
    }

    public void save(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Object> data = cache.computeIfAbsent(uuid, u -> new HashMap<>());

        data.put("player_id", uuid.toString());
        data.put("name", player.getName());
        data.put("level", getLevel(player));

        Map<String, Object> hp = new HashMap<>();
        hp.put("current", player.getHealth());
        hp.put("max", player.getMaxHealth());

        Map<String, Object> stats = new HashMap<>();
        stats.put("hp", hp);
        stats.put("str", getAbility(player, "str"));
        stats.put("dex", getAbility(player, "dex"));
        stats.put("con", getAbility(player, "con"));
        stats.put("int", getAbility(player, "int"));
        stats.put("wis", getAbility(player, "wis"));
        stats.put("cha", getAbility(player, "cha"));
        data.put("stats", stats);

        Map<String, Object> storyProgress = new HashMap<>();
        storyProgress.put("current_node", partyCurrentNode);
        data.put("story_progress", storyProgress);

        // Track per-player node progress
        playerNodeProgress.put(uuid, partyCurrentNode);

        File file = new File(playersFolder, uuid + ".yml");
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        try (FileWriter writer = new FileWriter(file)) {
            yaml.dump(data, writer);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save player data for " + uuid + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            save(player);
        }
    }
}