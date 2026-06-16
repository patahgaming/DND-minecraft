package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.CharacterClass;
import id.patahgaming.dndplugin.engine.dice.DiceRoller;
import id.patahgaming.dndplugin.ui.TitleDisplay;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /roll <jenis_tindakan>
 *
 * Roll d20 + ability modifier yang relevan untuk jenis tindakan tertentu.
 * Suggestion menampilkan daftar tindakan umum yang membutuhkan roll
 * (sesuai SRD: skill checks, saving throws, attack roll, dll).
 */
public class RollCommand implements CommandExecutor, TabCompleter {

    // Mapping jenis tindakan -> ability score yang relevan (default SRD)
    private static final List<String> ROLL_TYPES = List.of(
            "athletics", "acrobatics", "stealth", "perception",
            "investigation", "insight", "persuasion", "deception",
            "intimidation", "sleight_of_hand", "survival",
            "attack", "initiative", "death_save"
    );

    private static final Map<String, String> ROLL_TYPE_ABILITY = Map.ofEntries(
            Map.entry("athletics", "str"),
            Map.entry("acrobatics", "dex"),
            Map.entry("stealth", "dex"),
            Map.entry("sleight_of_hand", "dex"),
            Map.entry("perception", "wis"),
            Map.entry("survival", "wis"),
            Map.entry("insight", "wis"),
            Map.entry("investigation", "int"),
            Map.entry("persuasion", "cha"),
            Map.entry("deception", "cha"),
            Map.entry("intimidation", "cha"),
            Map.entry("initiative", "dex"),
            Map.entry("attack", "str"),
            Map.entry("death_save", "")
    );

    private final DndPlugin plugin;

    public RollCommand(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya bisa dijalankan oleh player.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("Gunakan: /roll <jenis_tindakan>. Contoh: /roll stealth");
            return true;
        }

        String rollType = args[0].toLowerCase();
        String ability = ROLL_TYPE_ABILITY.getOrDefault(rollType, "");

        int modifier = 0;
        if (!ability.isEmpty()) {
            modifier = plugin.getPlayerDataStore().getAbilityModifier(player, ability);
        }

        // Proficiency bonus: kalau skill ini adalah class skill → +prof
        if (!ability.isEmpty() && !rollType.equals("initiative") && !rollType.equals("attack")
                && !rollType.equals("death_save")) {
            CharacterClass charClass = CharacterClass.fromName(
                    plugin.getPlayerDataStore().getCharacterClass(player));
            if (charClass != null && charClass.hasSkillOption(rollType)) {
                int profBonus = id.patahgaming.dndplugin.engine.combat.CombatEngine
                        .proficiencyBonus(plugin.getPlayerDataStore().getLevel(player));
                modifier += profBonus;
            }
        }

        // Pisahkan raw d20 roll dan modifier untuk animasi
        int roll = DiceRoller.roll(20);         // d20 murni (1–20)
        int total = roll + modifier;

        // Animasi: 4 flash frame → final color-coded result
        TitleDisplay.showRollAnimation(plugin, player,
                formatRollType(rollType), modifier, roll, total);
        return true;
    }

    private String formatRollType(String rollType) {
        return rollType.replace("_", " ");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                    @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (String type : ROLL_TYPES) {
                if (type.startsWith(input)) {
                    result.add(type);
                }
            }
            return result;
        }
        return List.of();
    }
}