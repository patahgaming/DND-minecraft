package id.patahgaming.dndplugin.data;

import id.patahgaming.dndplugin.DndPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Load & simpan story tree nodes dari data/story/*.yml.
 * Setiap node punya: node_id, trigger (ALL_PLAYERS_IN_BBOX / SINGLE_PLAYER_EVENT),
 * bbox, narrative, choices (id + next_node).
 */
public class StoryTreeStore {

    public enum TriggerType {
        ALL_PLAYERS_IN_BBOX,
        SINGLE_PLAYER_EVENT
    }

    public static class Bbox {
        public int x1, z1, x2, z2;
        public int y1 = 0, y2 = 256; // 3D extension — default full world height

        /** 2D check (backward compat). */
        public boolean contains(double x, double z) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        /** 3D check. */
        public boolean contains(double x, double y, double z) {
            if (!contains(x, z)) return false;
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            return y >= minY && y <= maxY;
        }

        /** Check apakah bbox ini fully contains bbox lain (untuk parent-child validation). */
        public boolean fullyContains(Bbox other) {
            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
            int oMinX = Math.min(other.x1, other.x2), oMaxX = Math.max(other.x1, other.x2);
            int oMinY = Math.min(other.y1, other.y2), oMaxY = Math.max(other.y1, other.y2);
            int oMinZ = Math.min(other.z1, other.z2), oMaxZ = Math.max(other.z1, other.z2);
            return oMinX >= minX && oMaxX <= maxX
                    && oMinY >= minY && oMaxY <= maxY
                    && oMinZ >= minZ && oMaxZ <= maxZ;
        }
    }

    public static class Choice {
        public String id;
        public String nextNode;
    }

    public static class StoryNode {
        public String nodeId;
        public TriggerType trigger;
        public Bbox bbox;
        public String narrative;
        public List<Choice> choices = new ArrayList<>();
    }

    private final DndPlugin plugin;
    private final File storyFolder;
    private final Map<String, StoryNode> nodes = new ConcurrentHashMap<>();

    public StoryTreeStore(DndPlugin plugin) {
        this.plugin = plugin;
        this.storyFolder = new File(plugin.getDataFolder(), "data/story");
        if (!storyFolder.exists()) {
            storyFolder.mkdirs();
        }
        loadAll();
    }

    @SuppressWarnings("unchecked")
    private void loadAll() {
        File[] files = storyFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) return;

        Yaml yaml = new Yaml();
        for (File file : files) {
            try (InputStream in = java.nio.file.Files.newInputStream(file.toPath())) {
                Map<String, Object> data = yaml.load(in);
                if (data == null) continue;

                StoryNode node = new StoryNode();
                node.nodeId = String.valueOf(data.get("node_id"));

                Object triggerRaw = data.get("trigger");
                if (triggerRaw != null) {
                    node.trigger = TriggerType.valueOf(triggerRaw.toString());
                }

                Object bboxRaw = data.get("bbox");
                if (bboxRaw instanceof Map<?, ?> bboxMap) {
                    Bbox bbox = new Bbox();
                    bbox.x1 = toInt(bboxMap.get("x1"));
                    bbox.z1 = toInt(bboxMap.get("z1"));
                    bbox.x2 = toInt(bboxMap.get("x2"));
                    bbox.z2 = toInt(bboxMap.get("z2"));
                    node.bbox = bbox;
                }

                Object narrative = data.get("narrative");
                node.narrative = narrative != null ? narrative.toString() : "";

                Object choicesRaw = data.get("choices");
                if (choicesRaw instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Choice choice = new Choice();
                            choice.id = String.valueOf(m.get("id"));
                            choice.nextNode = String.valueOf(m.get("next_node"));
                            node.choices.add(choice);
                        }
                    }
                }

                nodes.put(node.nodeId, node);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load story node from " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(o));
    }

    public StoryNode get(String nodeId) {
        return nodes.get(nodeId);
    }

    public List<StoryNode> getAllWithBboxTrigger() {
        List<StoryNode> result = new ArrayList<>();
        for (StoryNode node : nodes.values()) {
            if (node.bbox != null && node.trigger != null) {
                result.add(node);
            }
        }
        return result;
    }
}