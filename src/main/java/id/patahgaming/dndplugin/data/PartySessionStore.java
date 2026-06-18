package id.patahgaming.dndplugin.data;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.model.NarratorMode;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Party-wide session state: narrator mode, current story node, rolling event log.
 * File: data/party/session.yml
 */
public class PartySessionStore {

    private final DndPlugin plugin;
    private final File sessionFile;
    private NarratorMode narratorMode = NarratorMode.SEMI;
    private String currentPartyNode; // volatile
    private final List<String> events = new ArrayList<>();
    private static final int MAX_EVENTS = 10;

    public PartySessionStore(DndPlugin plugin) {
        this.plugin = plugin;
        this.sessionFile = new File(plugin.getDataFolder(), "data/party/session.yml");
        load();
    }

    private void load() {
        if (!sessionFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sessionFile);
        narratorMode = NarratorMode.fromString(yaml.getString("narrator_mode", "semi"));
        currentPartyNode = yaml.getString("current_node");
        List<String> loaded = yaml.getStringList("events");
        events.clear();
        events.addAll(loaded);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("narrator_mode", narratorMode.name().toLowerCase());
        if (currentPartyNode != null) yaml.set("current_node", currentPartyNode);
        yaml.set("events", new ArrayList<>(events));
        try {
            sessionFile.getParentFile().mkdirs();
            yaml.save(sessionFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save session: " + e.getMessage());
        }
    }

    // --- Mode ---
    public NarratorMode getMode() { return narratorMode; }
    public void setMode(NarratorMode mode) { this.narratorMode = mode; save(); }
    public boolean isAutoMode() { return narratorMode == NarratorMode.AUTO; }

    // --- Events (rolling 10) ---
    public void addEvent(String event) {
        events.add(event);
        while (events.size() > MAX_EVENTS) events.remove(0);
        save();
    }
    public List<String> getEvents() { return new ArrayList<>(events); }

    // --- Party node ---
    public String getCurrentNode() { return currentPartyNode; }
    public void setCurrentNode(String node) { this.currentPartyNode = node; save(); }
}
