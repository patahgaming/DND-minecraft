package id.patahgaming.dndplugin.data;

import id.patahgaming.dndplugin.DndPlugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Menyimpan disposition + history per NPC penting.
 * History bersifat global per-NPC (shared untuk seluruh party), bukan per-player.
 */
public class NpcDataStore {

    public static class NpcRecord {
        public String npcId;
        public String displayName;
        public String disposition = "neutral";
        public String storyNode;
        public List<Map<String, Object>> history = Collections.synchronizedList(new ArrayList<>());
    }

    private final DndPlugin plugin;
    private final File npcsFolder;
    private final Map<String, NpcRecord> records = new ConcurrentHashMap<>();

    public NpcDataStore(DndPlugin plugin) {
        this.plugin = plugin;
        this.npcsFolder = new File(plugin.getDataFolder(), "data/npcs");
        if (!npcsFolder.exists()) {
            npcsFolder.mkdirs();
        }
        loadAll();
    }

    @SuppressWarnings("unchecked")
    private void loadAll() {
        File[] files = npcsFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) return;

        Yaml yaml = new Yaml();
        for (File file : files) {
            try (InputStream in = java.nio.file.Files.newInputStream(file.toPath())) {
                Map<String, Object> data = yaml.load(in);
                if (data == null) continue;

                NpcRecord record = new NpcRecord();
                record.npcId = String.valueOf(data.get("npc_id"));
                record.displayName = String.valueOf(data.getOrDefault("display_name", record.npcId));
                record.disposition = String.valueOf(data.getOrDefault("disposition", "neutral"));
                Object node = data.get("story_node");
                record.storyNode = node != null ? node.toString() : null;

                Object history = data.get("history");
                if (history instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            record.history.add((Map<String, Object>) m);
                        }
                    }
                }

                records.put(record.npcId, record);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load NPC data from " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public NpcRecord get(String npcId) {
        return records.get(npcId);
    }

    public NpcRecord getOrCreate(String npcId, String displayName) {
        return records.computeIfAbsent(npcId, id -> {
            NpcRecord r = new NpcRecord();
            r.npcId = id;
            r.displayName = displayName;
            return r;
        });
    }

    /** Tambah entri history baru ke NPC (shared party). */
    public void appendHistory(String npcId, String summary) {
        NpcRecord record = records.get(npcId);
        if (record == null) return;

        Map<String, Object> entry = new HashMap<>();
        entry.put("timestamp", Instant.now().toString());
        entry.put("summary", summary);
        record.history.add(entry);
    }

    public void setDisposition(String npcId, String disposition) {
        NpcRecord record = records.get(npcId);
        if (record != null) {
            record.disposition = disposition;
        }
    }

    public void save(String npcId) {
        NpcRecord record = records.get(npcId);
        if (record == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("npc_id", record.npcId);
        data.put("display_name", record.displayName);
        data.put("disposition", record.disposition);
        if (record.storyNode != null) {
            data.put("story_node", record.storyNode);
        }
        data.put("history", record.history);

        File file = new File(npcsFolder, record.npcId + ".yml");
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        try (FileWriter writer = new FileWriter(file)) {
            yaml.dump(data, writer);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save NPC data for " + npcId + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (String npcId : records.keySet()) {
            save(npcId);
        }
    }
}