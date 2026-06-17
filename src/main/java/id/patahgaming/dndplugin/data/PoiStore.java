package id.patahgaming.dndplugin.data;

import id.patahgaming.dndplugin.DndPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.util.*;

/**
 * Load/save POI (Point of Interest) dari data/poi/*.yml.
 *
 * v2: 3D bbox (y1,y2) + parent_poi_id hierarchy + YAML save + deepest-match lookup.
 */
public class PoiStore {

    public static class Poi {
        public String poiId;
        public String name;
        public String biome;
        public String parentPoiId; // null = top-level
        public StoryTreeStore.Bbox bbox;

        /** Depth in hierarchy — 0 for top-level, 1+ for children. Computed externally via PoiStore. */
    }

    private final DndPlugin plugin;
    private final File poiFolder;
    private final Map<String, Poi> poiMap = new LinkedHashMap<>();

    public PoiStore(DndPlugin plugin) {
        this.plugin = plugin;
        this.poiFolder = new File(plugin.getDataFolder(), "data/poi");
        if (!poiFolder.exists()) poiFolder.mkdirs();
        loadAll();
    }

    // ==================== LOAD ====================

    private void loadAll() {
        poiMap.clear();
        File[] files = poiFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files != null) {
            Yaml yaml = new Yaml();
            for (File file : files) {
                try (InputStream in = java.nio.file.Files.newInputStream(file.toPath())) {
                    Map<String, Object> data = yaml.load(in);
                    if (data == null) continue;
                    Poi poi = parsePoi(data);
                    if (poi != null) poiMap.put(poi.poiId, poi);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load POI: " + file.getName() + " — " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info("[PoiStore] Loaded " + poiMap.size() + " POIs");
    }

    private Poi parsePoi(Map<String, Object> data) {
        Object idRaw = data.get("poi_id");
        if (idRaw == null) return null; // skip invalid POI
        Poi poi = new Poi();
        poi.poiId = String.valueOf(idRaw);
        poi.name = String.valueOf(data.getOrDefault("name", poi.poiId));
        poi.biome = String.valueOf(data.getOrDefault("biome", "unknown"));
        Object parentRaw = data.get("parent_poi_id");
        poi.parentPoiId = (parentRaw != null && !"null".equals(String.valueOf(parentRaw))
                && !"".equals(String.valueOf(parentRaw)))
                ? String.valueOf(parentRaw) : null;

        Object bboxRaw = data.get("bbox");
        if (bboxRaw instanceof Map<?, ?> bboxMap) {
            StoryTreeStore.Bbox bbox = new StoryTreeStore.Bbox();
            bbox.x1 = toInt(bboxMap.get("x1"));
            bbox.z1 = toInt(bboxMap.get("z1"));
            bbox.x2 = toInt(bboxMap.get("x2"));
            bbox.z2 = toInt(bboxMap.get("z2"));
            bbox.y1 = bboxMap.containsKey("y1") ? toInt(bboxMap.get("y1")) : 0;
            bbox.y2 = bboxMap.containsKey("y2") ? toInt(bboxMap.get("y2")) : 256;
            poi.bbox = bbox;
        }
        return poi;
    }

    // ==================== SAVE ====================

    /** Save POI ke YAML file di disk. */
    public void save(Poi poi) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("poi_id", poi.poiId);
        yaml.set("name", poi.name);
        yaml.set("biome", poi.biome);
        if (poi.parentPoiId != null) yaml.set("parent_poi_id", poi.parentPoiId);

        yaml.set("bbox.x1", poi.bbox.x1);
        yaml.set("bbox.y1", poi.bbox.y1);
        yaml.set("bbox.z1", poi.bbox.z1);
        yaml.set("bbox.x2", poi.bbox.x2);
        yaml.set("bbox.y2", poi.bbox.y2);
        yaml.set("bbox.z2", poi.bbox.z2);

        File file = new File(poiFolder, poi.poiId + ".yml");
        try {
            yaml.save(file);
            poiMap.put(poi.poiId, poi);
            plugin.getLogger().info("[PoiStore] Saved POI: " + poi.poiId);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save POI " + poi.poiId + ": " + e.getMessage());
        }
    }

    // ==================== QUERY ====================

    public Collection<Poi> getAll() { return poiMap.values(); }
    public Poi get(String poiId) { return poiMap.get(poiId); }
    public boolean exists(String poiId) { return poiMap.containsKey(poiId); }

    /** Get children of a specific POI. */
    public List<Poi> getChildren(String parentPoiId) {
        List<Poi> children = new ArrayList<>();
        for (Poi poi : poiMap.values()) {
            if (java.util.Objects.equals(parentPoiId, poi.parentPoiId)) children.add(poi);
        }
        return children;
    }

    /** Get all top-level POIs (no parent). */
    public List<Poi> getTopLevel() {
        List<Poi> roots = new ArrayList<>();
        for (Poi poi : poiMap.values()) {
            if (poi.parentPoiId == null) roots.add(poi);
        }
        return roots;
    }

    /**
     * Deepest-match lookup: return POI paling spesifik yang mencakup koordinat.
     * Walk tree → kalau ada child yang juga contains, masuk lebih dalam.
     */
    public Poi findContaining(double x, double y, double z) {
        Poi best = null;
        int bestDepth = -1;
        for (Poi poi : poiMap.values()) {
            if (poi.bbox != null && poi.bbox.contains(x, y, z)) {
                int depth = computeDepth(poi);
                if (depth > bestDepth) {
                    bestDepth = depth;
                    best = poi;
                }
            }
        }
        return best;
    }

    /** Backward compat — 2D lookup. */
    public Poi findContaining(double x, double z) {
        return findContaining(x, 64, z); // reasonable default Y
    }

    /** Compute tree depth for a POI. */
    private int computeDepth(Poi poi) {
        int depth = 0;
        String parent = poi.parentPoiId;
        Set<String> visited = new HashSet<>();
        visited.add(poi.poiId);
        while (parent != null && depth < 50) {
            if (!visited.add(parent)) break; // circular guard
            Poi p = poiMap.get(parent);
            if (p == null) break;
            depth++;
            parent = p.parentPoiId;
        }
        return depth;
    }

    // ==================== UTIL ====================

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); }
        catch (NumberFormatException e) { return 0; }
    }

    public void reload() { loadAll(); }
}
