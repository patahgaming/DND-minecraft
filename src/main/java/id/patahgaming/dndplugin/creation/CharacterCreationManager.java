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

    /** Give Buku 1 (race + class) to player. */
    public void giveStageOneBook(Player player) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return;

        // Page 1: Race selection
        StringBuilder pg1 = new StringBuilder("=== Petualang Baru ===\n\nPilih Race:\n");
        for (int i = 0; i < RACES.length; i++) {
            pg1.append(i + 1).append(". ").append(formatName(RACES[i])).append("\n");
        }
        pg1.append("\nTulis angka pilihanmu:\n[tulis di sini]");
        meta.addPage(pg1.toString());

        // Page 2: Class selection with info
        StringBuilder pg2 = new StringBuilder("=== Pilih Class ===\n\n");
        for (int i = 0; i < CLASSES.length; i++) {
            pg2.append(i + 1).append(". ").append(formatName(CLASSES[i])).append("\n");
        }
        pg2.append("\nTulis angka pilihanmu:\n[tulis di sini]");
        meta.addPage(pg2.toString());

        meta.setDisplayName("§6Buku Karakter §8- Tahap 1");
        meta.getPersistentDataContainer().set(KEY_BOOK_STAGE, PersistentDataType.INTEGER, 1);

        book.setItemMeta(meta);
        giveOrDrop(player, book);
    }

    /** Give Buku 2 (skills + ability scores) after stage 1 is parsed. */
    public void giveStageTwoBook(Player player, int classIdx) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return;

        // Page 1: Skills (contextual per class)
        StringBuilder pg1 = new StringBuilder("=== Skill Proficiency ===\n\n");
        pg1.append("Pilih 2 skill dari:\n");
        pg1.append("1. Athletics\n2. Acrobatics\n3. Stealth\n4. Perception\n");
        pg1.append("5. Investigation\n6. Insight\n7. Persuasion\n8. Deception\n");
        pg1.append("9. Intimidation\n10. Survival\n\n");
        pg1.append("Tulis 2 angka, pisah koma:\n[tulis di sini]");
        meta.addPage(pg1.toString());

        // Page 2: Background
        StringBuilder pg2 = new StringBuilder("=== Background ===\n\n");
        for (int i = 0; i < BACKGROUNDS.length; i++) {
            pg2.append(i + 1).append(". ").append(formatName(BACKGROUNDS[i])).append("\n");
        }
        pg2.append("\nTulis angka pilihanmu:\n[tulis di sini]");
        meta.addPage(pg2.toString());

        // Page 3: Ability Scores
        meta.addPage("=== Ability Scores ===\n\n" +
                "1. Standard Array\n   (15,14,13,12,10,8)\n\n" +
                "2. Roll (4d6 drop lowest)\n\n" +
                "Tulis angka pilihanmu:\n[tulis di sini]\n\n" +
                "Jika Standard Array,\n" +
                "urutkan ke (STR,DEX,CON,INT,WIS,CHA):\n" +
                "Contoh: 15,10,13,8,12,14\n\n" +
                "Tulis urutanmu:\n[tulis di sini]");

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
