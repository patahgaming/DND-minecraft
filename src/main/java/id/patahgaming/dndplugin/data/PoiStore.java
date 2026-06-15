package id.patahgaming.dndplugin.data;

import id.patahgaming.dndplugin.DndPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Load POI (Point of Interest) dari data/poi/*.yml.
 * Untuk sekarang dibuat manual. AI narator menggunakan list POI ini
 * (bbox + biome + nama) sebagai konteks dunia.
 */
public class PoiStore {

    public static class Poi {
        public String poiId;
        public String name;
        public String biome;
        public StoryTreeStore.Bbox bbox;
    }

    private final DndPlugin plugin;
    private final File poiFolder;
    private final List<Poi> pois = new ArrayList<>();

    public PoiStore(DndPlugin plugin) {
        this.plugin = plugin;
        this.poiFolder = new File(plugin.getDataFolder(), "data/poi");
        if (!poiFolder.exists()) {
            poiFolder.mkdirs();
        }
        loadAll();
    }

    private void loadAll() {
        File[] files = poiFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) return;

        Yaml yaml = new Yaml();
        for (File file : files) {
            try (InputStream in = java.nio.file.Files.newInputStream(file.toPath())) {
                Map<String, Object> data = yaml.load(in);
                if (data == null) continue;

                Poi poi = new Poi();
                poi.poiId = String.valueOf(data.get("poi_id"));
                poi.name = String.valueOf(data.get("name"));
                poi.biome = String.valueOf(data.get("biome"));

                Object bboxRaw = data.get("bbox");
                if (bboxRaw instanceof Map<?, ?> bboxMap) {
                    StoryTreeStore.Bbox bbox = new StoryTreeStore.Bbox();
                    bbox.x1 = toInt(bboxMap.get("x1"));
                    bbox.z1 = toInt(bboxMap.get("z1"));
                    bbox.x2 = toInt(bboxMap.get("x2"));
                    bbox.z2 = toInt(bboxMap.get("z2"));
                    poi.bbox = bbox;
                }

                pois.add(poi);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load POI from " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(o));
    }

    public List<Poi> getAll() {
        return pois;
    }

    /** Cari POI yang mencakup koordinat (x, z) tertentu — untuk konteks AI narator. */
    public Poi findContaining(double x, double z) {
        for (Poi poi : pois) {
            if (poi.bbox != null && poi.bbox.contains(x, z)) {
                return poi;
            }
        }
        return null;
    }
}