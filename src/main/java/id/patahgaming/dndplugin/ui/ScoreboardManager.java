package id.patahgaming.dndplugin.ui;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.PlayerDataStore;
import net.kyori.adventure.text.Component;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Menampilkan character sheet (HP, Level, ability scores) sebagai
 * scoreboard sidebar PRIVATE per player. Source of truth tetap di
 * PlayerDataStore (PDC); ini hanya display layer.
 *
 * Panggil refresh() setiap kali stat berubah (level up, HP change, dll).
 */
public class ScoreboardManager {

    private static final String OBJECTIVE_NAME = "dnd_charsheet";

    public static void showCharacterSheet(DndPlugin plugin, Player player) {
        Scoreboard board = plugin.getServer().getScoreboardManager().getNewScoreboard();

        Objective objective = board.registerNewObjective(
                OBJECTIVE_NAME,
                "dummy",
                Component.text("Character Sheet")
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        populate(plugin, player, objective);

        player.setScoreboard(board);
    }

    public static void refresh(DndPlugin plugin, Player player) {
        Scoreboard board = player.getScoreboard();
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            showCharacterSheet(plugin, player);
            return;
        }

        // Clear only entries from THIS objective (bukan semua entry di scoreboard)
        for (String entry : board.getEntries()) {
            // Hanya reset entries yang ada di objective ini
            if (objective.getScore(entry).isScoreSet()) {
                board.resetScores(entry);
            }
        }

        populate(plugin, player, objective);
    }

    private static void populate(DndPlugin plugin, Player player, Objective objective) {
        PlayerDataStore store = plugin.getPlayerDataStore();

        // Gunakan Attribute API langsung — getMaxHealth() bisa return base 20
        // meskipun attribute sudah di-modify via /attribute command
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) != null
                ? player.getAttribute(Attribute.MAX_HEALTH).getValue()
                : player.getMaxHealth();
        int hp = (int) player.getHealth();
        int maxHp = (int) maxHealth;
        int level = store.getLevel(player);

        int line = 10;
        objective.getScore("HP: " + hp + "/" + maxHp).setScore(line--);
        objective.getScore("Level: " + level).setScore(line--);
        objective.getScore("STR: " + store.getAbility(player, "str")
                + " (" + signed(store.getAbilityModifier(player, "str")) + ")").setScore(line--);
        objective.getScore("DEX: " + store.getAbility(player, "dex")
                + " (" + signed(store.getAbilityModifier(player, "dex")) + ")").setScore(line--);
        objective.getScore("CON: " + store.getAbility(player, "con")
                + " (" + signed(store.getAbilityModifier(player, "con")) + ")").setScore(line--);
        objective.getScore("INT: " + store.getAbility(player, "int")
                + " (" + signed(store.getAbilityModifier(player, "int")) + ")").setScore(line--);
        objective.getScore("WIS: " + store.getAbility(player, "wis")
                + " (" + signed(store.getAbilityModifier(player, "wis")) + ")").setScore(line--);
        objective.getScore("CHA: " + store.getAbility(player, "cha")
                + " (" + signed(store.getAbilityModifier(player, "cha")) + ")").setScore(line--);
        objective.getScore("Node: " + store.getPartyCurrentNode()).setScore(line--);
        objective.getScore(" ").setScore(line);
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }
}