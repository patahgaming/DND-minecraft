package id.patahgaming.dndplugin.data;

import id.patahgaming.dndplugin.DndPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Load & filter SRD (System Reference Document) D&D 5e rules dari YAML.
 *
 * Rules dari https://github.com/BTMorton/dnd-5e-srd (5esrd.yaml, ~1.4MB).
 *
 * Digunakan oleh AI services (NarratorService, NpcDialogueService) untuk
 * lookup rules sebelum generate prompt. Filter by keyword — jangan embed
 * seluruh SRD ke prompt (1.4MB terlalu besar).
 */
public class SrdRulesStore {

    // Cache: keyword → flattened rule text (max 3000 chars)
    private final Map<String, String> searchCache = new ConcurrentHashMap<>();

    // Full parsed YAML tree (lazy-loaded)
    private Map<String, Object> fullTree;

    // Flattened index: lowercase keyword → list of matching section paths
    private Map<String, List<String>> keywordIndex;

    private final DndPlugin plugin;
    private boolean loaded = false;

    public SrdRulesStore(DndPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load SRD YAML dari JAR resource (saat plugin enable).
     * Heavy operation — hanya dipanggil sekali.
     */
    @SuppressWarnings("unchecked")
    public void load() {
        if (loaded) return;

        try (InputStream in = plugin.getResource("data/rules/srd/5esrd.yaml")) {
            if (in == null) {
                plugin.getLogger().warning("SRD resource not found: data/rules/srd/5esrd.yaml");
                return;
            }
            Yaml yaml = new Yaml();
            fullTree = yaml.load(in);
            if (fullTree == null) {
                plugin.getLogger().warning("SRD YAML loaded but empty.");
                return;
            }
            buildKeywordIndex();
            loaded = true;
            plugin.getLogger().info("SRD Rules loaded: " + keywordIndex.size() + " keywords indexed");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load SRD rules: " + e.getMessage());
        }
    }

    /**
     * Bangun index: lowercase keyword → list of section keys yang match.
     * Walk seluruh tree dan index setiap key + content snippet.
     */
    @SuppressWarnings("unchecked")
    private void buildKeywordIndex() {
        keywordIndex = new ConcurrentHashMap<>();
        walkTree("", fullTree);
    }

    @SuppressWarnings("unchecked")
    private void walkTree(String path, Map<String, Object> node) {
        if (node == null) return;

        for (var entry : node.entrySet()) {
            String key = entry.getKey();
            String fullPath = path.isEmpty() ? key : path + " > " + key;
            Object value = entry.getValue();

            // Index key words
            for (String word : key.toLowerCase().split("[^a-z0-9]+")) {
                if (word.length() < 3) continue;
                keywordIndex.computeIfAbsent(word, k -> new ArrayList<>()).add(fullPath);
            }

            // Index content words (top 2 levels only biar gak bloated)
            if (value instanceof String str && path.split(">").length < 3) {
                for (String word : str.toLowerCase().split("[^a-z0-9]+")) {
                    if (word.length() < 4) continue;
                    keywordIndex.computeIfAbsent(word, k -> new ArrayList<>()).add(fullPath);
                }
            }

            // Recurse
            if (value instanceof Map<?, ?> map) {
                walkTree(fullPath, (Map<String, Object>) map);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        walkTree(fullPath, (Map<String, Object>) itemMap);
                    }
                }
            }
        }
    }

    /**
     * Search SRD by keyword. Return filtered rule text (max ~3000 chars).
     * Hasil di-cache untuk keyword yang sama.
     *
     * @param keyword kata kunci (contoh: "stealth", "grapple", "fireball", "poisoned")
     * @return rule text yang relevan, atau "" kalau gak ketemu
     */
    public String search(String keyword) {
        if (!loaded || keywordIndex == null) return "";

        String kw = keyword.toLowerCase().trim();
        if (searchCache.containsKey(kw)) {
            return searchCache.get(kw);
        }

        // Cari di index — exact match dulu, lalu prefix
        List<String> paths = keywordIndex.get(kw);
        if (paths == null || paths.isEmpty()) {
            // Prefix match
            for (var entry : keywordIndex.entrySet()) {
                if (entry.getKey().startsWith(kw) || kw.contains(entry.getKey())) {
                    paths = entry.getValue();
                    break;
                }
            }
        }

        if (paths == null || paths.isEmpty()) {
            searchCache.put(kw, "");
            return "";
        }

        // Build result: ambil content dari path yang match
        StringBuilder result = new StringBuilder();
        int maxPaths = Math.min(paths.size(), 5); // max 5 sections
        for (int i = 0; i < maxPaths; i++) {
            String path = paths.get(i);
            String sectionText = extractSection(path);
            if (sectionText != null && !sectionText.isEmpty()) {
                result.append("[").append(path).append("]\n");
                result.append(sectionText).append("\n\n");
            }
        }

        // Truncate biar gak overweight di prompt
        String finalResult = result.length() > 3500
                ? result.substring(0, 3500) + "...[truncated]"
                : result.toString();

        searchCache.put(kw, finalResult);
        return finalResult;
    }

    /**
     * Multi-keyword search — combine results dari beberapa keyword.
     */
    public String searchMultiple(String... keywords) {
        StringBuilder sb = new StringBuilder();
        for (String kw : keywords) {
            String result = search(kw);
            if (!result.isEmpty()) {
                sb.append(result);
            }
        }
        return sb.toString();
    }

    /**
     * Ekstrak content text dari section path tertentu.
     * Path format: "Using Ability Scores > Stealth > content"
     */
    @SuppressWarnings("unchecked")
    private String extractSection(String path) {
        if (fullTree == null) return null;

        String[] parts = path.split(" > ");
        Object current = fullTree;

        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
                if (current == null) {
                    // Coba case-insensitive
                    for (var entry : map.entrySet()) {
                        if (entry.getKey().toString().equalsIgnoreCase(part)) {
                            current = entry.getValue();
                            break;
                        }
                    }
                }
            } else if (current instanceof List<?> list) {
                // Coba cari item dengan key matching
                boolean found = false;
                for (Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        for (var entry : itemMap.entrySet()) {
                            if (entry.getKey().toString().equalsIgnoreCase(part)) {
                                current = entry.getValue();
                                found = true;
                                break;
                            }
                        }
                        if (found) break;
                    }
                }
                if (!found) return null;
            } else {
                return null; // dead end
            }
        }

        // Return content as string
        if (current instanceof String str) {
            return str;
        } else if (current instanceof Map<?, ?> map) {
            // Flatten map ke text
            StringBuilder sb = new StringBuilder();
            for (var entry : map.entrySet()) {
                Object v = entry.getValue();
                if (v instanceof String s) {
                    sb.append(entry.getKey()).append(": ").append(s).append("\n");
                } else if (v instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof String s) {
                            sb.append("- ").append(s).append("\n");
                        }
                    }
                }
            }
            String result = sb.toString();
            return result.length() > 2000 ? result.substring(0, 2000) + "...[truncated]" : result;
        } else if (current instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof String s) {
                    sb.append("- ").append(s).append("\n");
                    if (sb.length() > 2000) { sb.append("...[truncated]"); break; }
                }
            }
            return sb.toString();
        }

        return current != null ? current.toString() : null;
    }

    /** Is SRD loaded and ready? */
    public boolean isLoaded() {
        return loaded;
    }

    /** Reload SRD dari disk (untuk /dnd reload). */
    public void reload() {
        searchCache.clear();
        keywordIndex = null;
        fullTree = null;
        loaded = false;
        load();
    }
}
