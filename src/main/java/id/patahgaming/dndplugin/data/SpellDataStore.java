package id.patahgaming.dndplugin.data;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.engine.combat.CombatEngine;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * SpellDataStore — load spell definitions dari YAML files.
 *
 * Schema per spell (data/spells/<spell_id>.yml):
 *   spell_id, display_name, attack_type (ATTACK_ROLL|SAVING_THROW|AUTO_HIT),
 *   saving_stat, damage_dice, damage_bonus (optional), damage_type,
 *   on_save (HALF|NEGATES), area (SINGLE|CONE|AREA),
 *   spell_slot_cost, allowed_classes
 */
public class SpellDataStore {

    public enum AttackType { ATTACK_ROLL, SAVING_THROW, AUTO_HIT }
    public enum OnSave { HALF, NEGATES }
    public enum Area { SINGLE, CONE, AREA }

    public record SpellData(
            String spellId,
            String displayName,
            AttackType attackType,
            String savingStat,          // DEX/CON/WIS/INT/CHA, null untuk attack roll
            String damageDice,          // "1d10", "3d6", "3d4"
            int damageBonus,            // bonus flat (Magic Missile +3)
            CombatEngine.DamageType damageType,
            OnSave onSave,
            Area area,
            int spellSlotCost,
            List<String> allowedClasses
    ) {
        public boolean isAllowedFor(String playerClass) {
            return allowedClasses.contains(playerClass.toUpperCase());
        }
    }

    private final DndPlugin plugin;
    private final Map<String, SpellData> spells = new LinkedHashMap<>();

    public SpellDataStore(DndPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        spells.clear();
        File spellsDir = new File(plugin.getDataFolder(), "data/spells");
        if (!spellsDir.exists()) {
            spellsDir.mkdirs();
        }

        // Load dari JAR resources dulu (default spells)
        loadFromResources();

        // Override dari disk (kalau ada custom spells)
        File[] files = spellsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                SpellData spell = parseSpell(yaml);
                if (spell != null) {
                    spells.put(spell.spellId, spell); // override or add
                }
            }
        }

        plugin.getLogger().info("[SpellDataStore] Loaded " + spells.size() + " spells");
    }

    private void loadFromResources() {
        String[] resourcePaths = {
                "data/spells/fire_bolt.yml", "data/spells/magic_missile.yml",
                "data/spells/burning_hands.yml", "data/spells/chill_touch.yml",
                "data/spells/shocking_grasp.yml", "data/spells/ray_of_frost.yml",
                "data/spells/sacred_flame.yml", "data/spells/poison_spray.yml",
                "data/spells/acid_splash.yml", "data/spells/frostbite.yml",
                "data/spells/eldritch_blast.yml", "data/spells/word_of_radiance.yml"
        };

        for (String path : resourcePaths) {
            try {
                java.io.InputStream in = plugin.getClass().getClassLoader().getResourceAsStream(path);
                if (in != null) {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(in));
                    SpellData spell = parseSpell(yaml);
                    if (spell != null) {
                        spells.putIfAbsent(spell.spellId, spell);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load resource spell: " + path + " — " + e.getMessage());
            }
        }
    }

    private SpellData parseSpell(YamlConfiguration yaml) {
        try {
            String id = yaml.getString("spell_id");
            if (id == null) return null;

            String name = yaml.getString("display_name", id);
            AttackType atkType = AttackType.valueOf(
                    yaml.getString("attack_type", "ATTACK_ROLL").toUpperCase());
            String saveStat = yaml.getString("saving_stat");
            String dice = yaml.getString("damage_dice", "1d6");
            int bonus = yaml.getInt("damage_bonus", 0);
            CombatEngine.DamageType dmgType = CombatEngine.DamageType.valueOf(
                    yaml.getString("damage_type", "FORCE").toUpperCase());
            OnSave onSave = OnSave.valueOf(
                    yaml.getString("on_save", "NEGATES").toUpperCase());
            Area area = Area.valueOf(
                    yaml.getString("area", "SINGLE").toUpperCase());

            int cost = yaml.getInt("spell_slot_cost", 1);
            List<String> classes = yaml.getStringList("allowed_classes");

            return new SpellData(id, name, atkType, saveStat, dice, bonus,
                    dmgType, onSave, area, cost, classes);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse spell YAML: " + e.getMessage());
            return null;
        }
    }

    public SpellData get(String spellId) {
        return spells.get(spellId.toLowerCase());
    }

    public Collection<SpellData> getAll() {
        return spells.values();
    }

    public List<SpellData> getAvailableFor(String playerClass) {
        List<SpellData> available = new ArrayList<>();
        for (SpellData s : spells.values()) {
            if (s.isAllowedFor(playerClass)) {
                available.add(s);
            }
        }
        return available;
    }

    public void reload() {
        load();
    }
}
