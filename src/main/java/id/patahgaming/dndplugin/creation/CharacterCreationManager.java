package id.patahgaming.dndplugin.creation;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.dice.DiceRoller;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Orchestrate character creation flow via Book & Quill.
 * Two-stage: Buku 1 (race+class) → Buku 2 (skills+background+scores).
 */
public class CharacterCreationManager {

    private static final NamespacedKey KEY_BOOK_STAGE = new NamespacedKey("dndplugin", "book_stage");
    private static final NamespacedKey KEY_CHAR_NAME = new NamespacedKey("dndplugin", "char_name");
    private static final NamespacedKey KEY_CHAR_CLASS = new NamespacedKey("dndplugin", "char_class");
    private static final NamespacedKey KEY_CHAR_RACE = new NamespacedKey("dndplugin", "char_race");

    private final DndPlugin plugin;

    // Race list: index 1-14 → race_id
    public static final String[] RACES = {
            "human", "elf", "dwarf", "halfling", "half_orc",
            "tiefling", "dragonborn", "gnome", "half_elf"
    };

    // Class list: index 1-12 → class_id
    public static final String[] CLASSES = {
            "fighter", "rogue", "wizard", "cleric", "paladin", "barbarian",
            "monk", "druid", "sorcerer", "warlock", "ranger", "bard"
    };

    // Background list: index 1-8 → background_id
    public static final String[] BACKGROUNDS = {
            "acolyte", "criminal", "folk_hero", "noble", "sage", "soldier", "outlander", "charlatan"
    };

    public CharacterCreationManager(DndPlugin plugin) {
        this.plugin = plugin;
    }

    /** Give Buku 1 (race + class) to player.
     * Pages must stay under 14 lines (Minecraft Book & Quill limit). */
    public void giveStageOneBook(Player player) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return;

        // Page 1: Race selection — 3-column grid, 6 lines total
        // "Pilih Race:\n" + 3 grid lines + "\n" + "Tulis angka (1-9):"
        meta.addPage("Pilih Race (1-9):\n"
                + "1.Human   2.Elf    3.Dwarf\n"
                + "4.Halfling 5.H-Orc 6.Tiefling\n"
                + "7.Dragon  8.Gnome  9.H-Elf\n"
                + "\nTulis angka pilihan:");

        // Page 2: Class selection — 4×3 grid, 7 lines total
        meta.addPage("Pilih Class (1-12):\n"
                + "1.Fighter 2.Rogue  3.Wizard\n"
                + "4.Cleric  5.Paladin 6.Barbarian\n"
                + "7.Monk    8.Druid  9.Sorcerer\n"
                + "10.Warlock 11.Ranger 12.Bard\n"
                + "\nTulis angka pilihan:");

        meta.setDisplayName("§6Buku Karakter §8- Tahap 1");
        meta.getPersistentDataContainer().set(KEY_BOOK_STAGE, PersistentDataType.INTEGER, 1);

        book.setItemMeta(meta);
        giveOrDrop(player, book);
    }

    /** Give Buku 2 (skills + ability scores) after stage 1 is parsed.
     * Pages must stay under 14 lines (Minecraft Book & Quill limit). */
    public void giveStageTwoBook(Player player, int classIdx) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return;

        // Page 1: Skills — 2-column, 8 lines total
        meta.addPage("Pilih 2 Skill (pisah koma):\n"
                + "1.Athletic  2.Acrobat\n"
                + "3.Stealth   4.Percept\n"
                + "5.Invest    6.Insight\n"
                + "7.Persuade  8.Decept\n"
                + "9.Intimid   10.Survival\n"
                + "\nTulis 2 angka (contoh: 3,4):");

        // Page 2: Background — 2-column, 7 lines total
        meta.addPage("Pilih Background (1-8):\n"
                + "1.Acolyte   2.Criminal\n"
                + "3.FolkHero  4.Noble\n"
                + "5.Sage      6.Soldier\n"
                + "7.Outlander 8.Charlatan\n"
                + "\nTulis angka pilihan:");

        // Page 3: Ability Scores — compact, 7 lines total
        // Note: avoid bare 6-number sequences in pre-filled text (parser false-positive)
        meta.addPage("Ability Scores:\n"
                + "1.Standard Array\n"
                + "2.Roll 4d6 (drop lowest)\n"
                + "\nPilih 1 atau 2:\n"
                + "\nJika Std Array, tulis:\n"
                + "STR,DEX,CON,INT,WIS,CHA\n"
                + "Contoh: 15,10,13,8,12,14");

        meta.setDisplayName("§6Buku Karakter §8- Tahap 2");
        meta.getPersistentDataContainer().set(KEY_BOOK_STAGE, PersistentDataType.INTEGER, 2);
        meta.getPersistentDataContainer().set(KEY_CHAR_CLASS, PersistentDataType.INTEGER, classIdx);

        book.setItemMeta(meta);
        giveOrDrop(player, book);
    }

    /** Parse & finalize character from signed book. Returns true if valid. */
    public boolean finalizeCharacter(Player player, String charName, String raceId, int classIdx,
                                      int[] skillChoices, int bgIdx, int scoreMethod, int[] scoreDist) {
        // Validate
        if (charName == null || charName.isBlank() || charName.length() > 32) return false;
        if (raceId == null) return false;
        if (classIdx < 1 || classIdx > CLASSES.length) return false;

        String className = CLASSES[classIdx - 1];
        String race = raceId;

        // Ability scores
        int[] abilities;
        if (scoreMethod == 1) {
            if (scoreDist == null || scoreDist.length != 6) return false;
            abilities = scoreDist;
        } else {
            abilities = generateRolledScores();
        }

        int str = abilities[0], dex = abilities[1], con = abilities[2];
        int intel = abilities[3], wis = abilities[4], cha = abilities[5];

        // TODO: Apply race bonuses from race YAML

        // HP
        int baseHp = getClassBaseHp(className);
        int conMod = (con - 10) / 2;
        int maxHp = Math.max(1, baseHp + conMod);

        // Save to PDC
        var pdc = player.getPersistentDataContainer();
        pdc.set(new NamespacedKey("dndplugin", "level"), PersistentDataType.INTEGER, 1);
        pdc.set(new NamespacedKey("dndplugin", "str"), PersistentDataType.INTEGER, str);
        pdc.set(new NamespacedKey("dndplugin", "dex"), PersistentDataType.INTEGER, dex);
        pdc.set(new NamespacedKey("dndplugin", "con"), PersistentDataType.INTEGER, con);
        pdc.set(new NamespacedKey("dndplugin", "int"), PersistentDataType.INTEGER, intel);
        pdc.set(new NamespacedKey("dndplugin", "wis"), PersistentDataType.INTEGER, wis);
        pdc.set(new NamespacedKey("dndplugin", "cha"), PersistentDataType.INTEGER, cha);
        pdc.set(new NamespacedKey("dndplugin", "class"), PersistentDataType.STRING, className);
        pdc.set(new NamespacedKey("dndplugin", "char_name"), PersistentDataType.STRING, charName);
        pdc.set(new NamespacedKey("dndplugin", "race"), PersistentDataType.STRING, race);
        pdc.set(new NamespacedKey("dndplugin", "has_character"), PersistentDataType.BOOLEAN, true);

        // Set HP
        player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(maxHp);
        player.setHealth(maxHp);

        return true;
    }

    /** Generate 6 ability scores via 4d6 drop lowest. */
    public int[] generateRolledScores() {
        int[] scores = new int[6];
        for (int i = 0; i < 6; i++) {
            int[] rolls = new int[4];
            for (int j = 0; j < 4; j++) rolls[j] = DiceRoller.roll(6);
            Arrays.sort(rolls);
            scores[i] = rolls[1] + rolls[2] + rolls[3]; // drop lowest
        }
        return scores;
    }

    private int getClassBaseHp(String className) {
        return switch (className.toLowerCase()) {
            case "barbarian" -> 12;
            case "fighter", "paladin", "ranger" -> 10;
            case "rogue", "cleric", "monk", "druid", "bard", "warlock" -> 8;
            case "wizard", "sorcerer" -> 6;
            default -> 8;
        };
    }

    private static String formatName(String id) {
        String[] words = id.replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), item);
        } else {
            player.getInventory().addItem(item);
        }
    }
}
