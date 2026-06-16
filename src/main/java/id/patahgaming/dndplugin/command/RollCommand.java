package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.CharacterClass;
import id.patahgaming.dndplugin.engine.dice.DiceRoller;
import id.patahgaming.dndplugin.ui.TitleDisplay;
import java.util.List;
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

        // PendingRoll auto-detect
        var pending = id.patahgaming.dndplugin.engine.combat.PendingRoll.get(player);

        if (args.length < 1) {
            if (pending != null) {
                // Auto-detect: roll sesuai pending
                player.sendMessage("§7Auto-detected: rolling " + pending.rollType.name().toLowerCase());
                executeRoll(player, pending.rollType.name().toLowerCase(), pending);
                return true;
            }
            player.sendMessage("§7Gunakan: /roll <jenis_tindakan>. Contoh: /roll stealth");
            return true;
        }

        String rollType = args[0].toLowerCase();

        // Guard: kalau ada pending, harus match
        if (pending != null && !pending.rollType.matches(rollType)) {
            player.sendMessage("§cYang perlu di-roll sekarang: §e" + pending.rollType.name().toLowerCase());
            player.sendMessage("§7Atau ketik §e/roll §7tanpa argumen untuk auto-detect.");
            return true;
        }
        executeRoll(player, rollType, pending);
        return true;
    }

    private void executeRoll(Player player, String rollType,
                             id.patahgaming.dndplugin.engine.combat.PendingRoll pending) {
        String ability = ROLL_TYPE_ABILITY.getOrDefault(rollType, "");

        int modifier = 0;
        if (!ability.isEmpty()) {
            modifier = plugin.getPlayerDataStore().getAbilityModifier(player, ability);
        }

        // Proficiency bonus
        if (!ability.isEmpty() && !rollType.equals("initiative") && !rollType.equals("attack")
                && !rollType.equals("death_save")) {
            CharacterClass charClass = CharacterClass.fromName(
                    plugin.getPlayerDataStore().getCharacterClass(player));
            if (charClass != null && charClass.hasSkillOption(rollType)) {
                List<String> options = charClass.getSkillOptions();
                int chosenIdx = options.indexOf(rollType.toLowerCase());
                if (chosenIdx >= 0 && chosenIdx < 2) {
                    int profBonus = id.patahgaming.dndplugin.engine.combat.CombatEngine
                            .proficiencyBonus(plugin.getPlayerDataStore().getLevel(player));
                    modifier += profBonus;
                }
            }
        }

        int roll = DiceRoller.roll(20);
        int total = roll + modifier;
        TitleDisplay.showRollAnimation(plugin, player, formatRollType(rollType), modifier, roll, total);

        // Resolve PendingRoll kalau ada
        if (pending != null) {
            resolvePendingRoll(player, pending, roll, total, modifier);
        }
    }

    /** Resolve pending roll — clear pending, narrate result. */
    private void resolvePendingRoll(Player player,
                                     id.patahgaming.dndplugin.engine.combat.PendingRoll pending,
                                     int roll, int total, int modifier) {
        // Clear pending — roll sudah dilakukan
        id.patahgaming.dndplugin.engine.combat.PendingRoll.clear(player);

        // Narration via NarratorService
        var ctx = new id.patahgaming.dndplugin.ai.NarratorContext(
                id.patahgaming.dndplugin.ai.NarratorContext.TriggerType.SKILL_CHECK_SUCCESS, player.getName())
                .addResult("skill", pending.rollType.name().toLowerCase())
                .addResult("roll", String.valueOf(roll))
                .addResult("total", String.valueOf(total))
                .addResult("modifier", String.valueOf(modifier))
                .withRecipient(id.patahgaming.dndplugin.ai.NarratorContext.Recipient.SELF);
        plugin.getNarratorService().narrate(ctx);
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