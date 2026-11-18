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
    private static final NamespacedKey KEY_CLASS = key("class");
    private static final NamespacedKey KEY_CHAR_NAME = key("char_name");
    private static final NamespacedKey KEY_RACE = key("race");
    private static final NamespacedKey KEY_HAS_CHARACTER = key("has_character");

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
     * Cek apakah player sudah punya karakter atau belum.
     * Kalau belum, kasih tahu player buat /character create.
     */
    public void initIfAbsent(Player player) {
        UUID uuid = player.getUniqueId();
        var pdc = player.getPersistentDataContainer();

        // Cek tag has_character dulu
        if (pdc.has(KEY_HAS_CHARACTER, PersistentDataType.STRING)) {
            // Player udah punya karakter → load metadata dari YAML
            loadMetadataFromYaml(uuid);
            return;
        }

        // Coba restore dari YAML backup
        if (restoreFromYaml(player)) {
            pdc.set(KEY_HAS_CHARACTER, PersistentDataType.STRING, "true");
            loadMetadataFromYaml(uuid);
            return;
        }

        // Belum punya karakter — kasih tahu player
        player.sendMessage("§6§lWelcome to D&D AI System!");
        player.sendMessage("§7Kamu belum punya karakter. Buat dulu dengan:");
        player.sendMessage("§e  /character create <class>");
        player.sendMessage("§7Contoh: §e/character create fighter");
        player.sendMessage("§7Classes: Fighter, Rogue, Wizard, Cleric, Barbarian, Ranger, Paladin, Warlock, Monk, Druid");
    }

    /**
     * Create karakter baru — set class, stats, HP max, proficiency.
     */
    public void createCharacter(Player player, CharacterClass charClass, String charName, String race) {
        var pdc = player.getPersistentDataContainer();

        // Ability scores: standard array [15,14,13,12,10,8]
        // Primary = 15, secondary (CON unless primary is CON) = 14, lalu urutan lain
        int[] standardArray = {15, 14, 13, 12, 10, 8};
        String primary = charClass.getPrimaryAbility();

        // Secondary: CON for HP, unless primary IS CON (then DEX)
        String secondary = "con".equals(primary) ? "dex" : "con";

        Map<String, Integer> scores = new java.util.LinkedHashMap<>();
        int idx = 0;
        scores.put(primary, standardArray[idx++]);   // 15
        scores.put(secondary, standardArray[idx++]);  // 14
        // Remaining in order: next most useful
        String[] rest = {"dex", "str", "wis", "int", "cha"};
        for (String ab : rest) {
            if (!scores.containsKey(ab) && idx < standardArray.length) {
                scores.put(ab, standardArray[idx++]);
            }
        }

        // Write PDC
        pdc.set(KEY_LEVEL, PersistentDataType.INTEGER, 1);
        pdc.set(KEY_CLASS, PersistentDataType.STRING, charClass.name());
        pdc.set(KEY_CHAR_NAME, PersistentDataType.STRING, charName != null ? charName : player.getName());
        pdc.set(KEY_RACE, PersistentDataType.STRING, race != null ? race : "Human");
        pdc.set(KEY_HAS_CHARACTER, PersistentDataType.STRING, "true");
        pdc.set(KEY_STR, PersistentDataType.INTEGER, scores.getOrDefault("str", 10));
        pdc.set(KEY_DEX, PersistentDataType.INTEGER, scores.getOrDefault("dex", 10));
        pdc.set(KEY_CON, PersistentDataType.INTEGER, scores.getOrDefault("con", 10));
        pdc.set(KEY_INT, PersistentDataType.INTEGER, scores.getOrDefault("int", 10));
        pdc.set(KEY_WIS, PersistentDataType.INTEGER, scores.getOrDefault("wis", 10));
        pdc.set(KEY_CHA, PersistentDataType.INTEGER, scores.getOrDefault("cha", 10));

        // Set max HP = (class base + CON modifier) × HP_MULTIPLIER (first-campaign buff)
        int conMod = getAbilityModifier(player, "con");
        int maxHP = (int) ((charClass.getBaseHP() + conMod) * id.patahgaming.dndplugin.creation.CharacterCreationManager.HP_MULTIPLIER);
        player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(maxHP);
        player.setHealth(maxHP);

        // Ubah nama player sesuai character name (nametag, chat, tab list)
        applyCharacterName(player);

        // Log
        plugin.getLogger().info("Character created: " + charName + " (Lv1 " + charClass.getDisplayName() + ")");
        player.sendMessage("§a§lCharacter Created!");
        player.sendMessage("§6  " + charName + " §7— Lv1 " + charClass.getDisplayName() + " (" + race + ")");
        player.sendMessage("§7  HP: " + maxHP + " | Primary: " + primary.toUpperCase());
        player.sendMessage("§7  STR:" + scores.get("str") + " DEX:" + scores.get("dex") + " CON:" + scores.get("con")
                + " INT:" + scores.get("int") + " WIS:" + scores.get("wis") + " CHA:" + scores.get("cha"));
    }

    /**
     * Terapkan character name ke nama player:
     *  - nametag di atas kepala (character name + HP bar via CombatListener)
     *  - nama di chat (display name)
     *  - nama di tab list
     *
     * Display name & player list name TIDAK tersimpan otomatis oleh server,
     * jadi method ini dipanggil setelah pick/create karakter DAN setiap join.
     * Tidak melakukan apa-apa kalau player belum punya character name.
     */
    public void applyCharacterName(Player player) {
        String charName = player.getPersistentDataContainer()
                .get(KEY_CHAR_NAME, PersistentDataType.STRING);
        if (charName == null || charName.isBlank()) return;

        player.setDisplayName(charName);
        player.setPlayerListName("§b" + charName);

        // Nametag di atas kepala: character name + HP bar
        id.patahgaming.dndplugin.listener.CombatListener.updateHpBar(player);
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

    public String getCharacterClass(Player player) {
        return player.getPersistentDataContainer().getOrDefault(KEY_CLASS, PersistentDataType.STRING, "Fighter");
    }

    public String getCharacterName(Player player) {
        return player.getPersistentDataContainer().getOrDefault(KEY_CHAR_NAME, PersistentDataType.STRING, player.getName());
    }

    public String getRace(Player player) {
        return player.getPersistentDataContainer().getOrDefault(KEY_RACE, PersistentDataType.STRING, "Human");
    }

    public boolean hasCharacter(Player player) {
        return player.getPersistentDataContainer().has(KEY_HAS_CHARACTER, PersistentDataType.STRING);
    }

    /** Hapus semua data karakter player — reset ke state awal. */
    public void resetCharacter(Player player) {
        var pdc = player.getPersistentDataContainer();
        pdc.remove(KEY_LEVEL);
        pdc.remove(KEY_STR);
        pdc.remove(KEY_DEX);
        pdc.remove(KEY_CON);
        pdc.remove(KEY_INT);
        pdc.remove(KEY_WIS);
        pdc.remove(KEY_CHA);
        pdc.remove(KEY_CLASS);
        pdc.remove(KEY_CHAR_NAME);
        pdc.remove(KEY_RACE);
        pdc.remove(KEY_HAS_CHARACTER);

        // Reset HP ke default Minecraft
        player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20);
        player.setHealth(20);

        // Reset name tag ke Minecraft username
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
        player.customName(null);
        player.setCustomNameVisible(false);

        // Hapus YAML file
        UUID uuid = player.getUniqueId();
        File file = new File(playersFolder, uuid + ".yml");
        if (file.exists()) {
            file.delete();
        }
        cache.remove(uuid);
        playerNodeProgress.remove(uuid);

        plugin.getLogger().info("Character reset for " + player.getName());
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

    /** Direct set for AUTO mode narrator. */
    public void setCurrentNode(String nodeId) {
        this.partyCurrentNode = nodeId;
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

        // Character info
        data.put("class", getCharacterClass(player));
        data.put("char_name", getCharacterName(player));
        data.put("race", getRace(player));

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