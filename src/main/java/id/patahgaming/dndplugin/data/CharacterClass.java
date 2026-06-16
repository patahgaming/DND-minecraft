package id.patahgaming.dndplugin.data;

import java.util.List;
import java.util.Map;

/**
 * D&D 5e Character Classes — data dari SRD.
 *
 * Menentukan: base HP, hit dice, primary ability, saving throw proficiencies,
 * dan skill proficiencies yang bisa dipilih.
 */
public enum CharacterClass {

    FIGHTER("Fighter", 10, "d10", "str", List.of("str", "con"),
            List.of("athletics", "perception", "survival", "intimidation", "acrobatics")),

    ROGUE("Rogue", 8, "d8", "dex", List.of("dex", "int"),
            List.of("stealth", "acrobatics", "sleight_of_hand", "investigation", "perception", "deception")),

    WIZARD("Wizard", 6, "d6", "int", List.of("int", "wis"),
            List.of("investigation", "insight", "perception")),

    CLERIC("Cleric", 8, "d8", "wis", List.of("wis", "cha"),
            List.of("insight", "persuasion", "perception", "survival")),

    BARBARIAN("Barbarian", 12, "d12", "str", List.of("str", "con"),
            List.of("athletics", "intimidation", "survival", "perception")),

    RANGER("Ranger", 10, "d10", "dex", List.of("str", "dex"),
            List.of("stealth", "survival", "perception", "investigation", "athletics")),

    PALADIN("Paladin", 10, "d10", "str", List.of("wis", "cha"),
            List.of("athletics", "intimidation", "persuasion", "insight")),

    WARLOCK("Warlock", 8, "d8", "cha", List.of("wis", "cha"),
            List.of("deception", "intimidation", "investigation", "persuasion")),

    MONK("Monk", 8, "d8", "dex", List.of("str", "dex"),
            List.of("acrobatics", "athletics", "stealth", "insight")),

    DRUID("Druid", 8, "d8", "wis", List.of("int", "wis"),
            List.of("survival", "perception", "insight", "investigation"));

    private final String displayName;
    private final int baseHP;         // HP di level 1 (sebelum CON mod)
    private final String hitDice;
    private final String primaryAbility;       // "str", "dex", dll
    private final List<String> savingThrows;   // 2 saving throw proficiencies
    private final List<String> skillOptions;   // skill proficiencies yang bisa dipilih

    CharacterClass(String displayName, int baseHP, String hitDice,
                   String primaryAbility, List<String> savingThrows,
                   List<String> skillOptions) {
        this.displayName = displayName;
        this.baseHP = baseHP;
        this.hitDice = hitDice;
        this.primaryAbility = primaryAbility;
        this.savingThrows = savingThrows;
        this.skillOptions = skillOptions;
    }

    public String getDisplayName() { return displayName; }
    public int getBaseHP() { return baseHP; }
    public String getHitDice() { return hitDice; }
    public String getPrimaryAbility() { return primaryAbility; }
    public List<String> getSavingThrows() { return savingThrows; }
    public List<String> getSkillOptions() { return skillOptions; }

    /** Is skill a class proficiency option? */
    public boolean hasSkillOption(String skill) {
        return skillOptions.contains(skill.toLowerCase());
    }

    /** Is saving throw proficient for this class? */
    public boolean hasSavingProficiency(String ability) {
        return savingThrows.contains(ability.toLowerCase());
    }

    /** Get class by name (case-insensitive). */
    public static CharacterClass fromName(String name) {
        for (CharacterClass c : values()) {
            if (c.name().equalsIgnoreCase(name) || c.displayName.equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }
}
