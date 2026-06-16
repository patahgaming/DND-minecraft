package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.engine.combat.CombatEngine;
import id.patahgaming.dndplugin.engine.combat.PendingRoll;
import id.patahgaming.dndplugin.engine.combat.TurnManager;
import id.patahgaming.dndplugin.ui.TitleDisplay;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /atk <melee|ranged> [target_uuid_prefix]
 *
 * Full D&D 5e attack resolution:
 * - d20 + modifier + proficiency vs target AC
 * - Hit on >= AC, nat20 = crit (double dice)
 * - Damage with type, resistance/vulnerability applied
 */
public class AtkCommand implements CommandExecutor, TabCompleter {

    private final DndPlugin plugin;

    public AtkCommand(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommand ini hanya bisa dijalankan oleh player.");
            return true;
        }

        TurnManager tm = plugin.getTurnManager();

        // Validasi battle
        if (tm.getBattleState() != TurnManager.BattleState.ACTIVE) {
            player.sendMessage("§cTidak ada battle aktif. Gunakan /battle start.");
            return true;
        }

        if (!tm.isPlayerTurn(player)) {
            player.sendMessage("§cBukan giliran kamu! Tunggu giliran.");
            player.sendMessage("§7Turn: " + tm.getCurrentTurnDisplay());
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§eGunakan: /atk <melee|ranged> [target]");
            return true;
        }

        String attackType = args[0].toLowerCase();
        boolean melee = !attackType.equals("ranged");

        // Cari target
        LivingEntity target;
        if (args.length >= 2) {
            target = findTarget(tm, args[1]);
        } else {
            // Auto-target: musuh pertama dalam turn order
            target = findFirstMobTarget(tm);
        }

        if (target == null) {
            player.sendMessage("§cTarget tidak ditemukan. Gunakan TAB untuk lihat target.");
            return true;
        }

        if (target.isDead()) {
            player.sendMessage("§cTarget sudah mati.");
            return true;
        }

        // Select action
        tm.selectAction(player);

        // Build diceExpr + resolve attack
        int strMod = plugin.getPlayerDataStore().getAbilityModifier(player, "str");
        int dexMod = plugin.getPlayerDataStore().getAbilityModifier(player, "dex");
        int profBonus = CombatEngine.proficiencyBonus(plugin.getPlayerDataStore().getLevel(player));

        String diceExpr;
        CombatEngine.AttackResult result;
        if (melee) {
            diceExpr = "d20+" + (strMod + profBonus);
            result = CombatEngine.resolveMeleeAttack(plugin, player, target);
        } else {
            diceExpr = "d20+" + (dexMod + profBonus);
            result = CombatEngine.resolveRangedAttack(plugin, player, target);
        }

        // PendingRoll: set ATTACK → player /roll confirm
        PendingRoll pr = new PendingRoll();
        pr.rollType = PendingRoll.RollType.ATTACK;
        pr.diceExpr = diceExpr;
        pr.targetId = target.getUniqueId().toString().substring(0, 8);
        pr.promptMsg = (melee ? "§7[Melee] " : "§7[Ranged] ") + player.getName()
                + " menyerang " + target.getName() + "! Ketik §e/roll";
        PendingRoll.set(player, pr);

        // Prompt
        player.sendMessage(pr.promptMsg + " (" + diceExpr + " vs AC " + result.targetAC + ")");

        // Auto-resolve for now (full manual two-step later)
        tm.resolveAction();

        // Apply damage
        int actualDmg = 0;
        if (result.hit) {
            actualDmg = CombatEngine.applyDamage(target, result.totalDamage, result.damageType);
        }
        PendingRoll.clear(player);

        // Title + broadcast
        String mainText = result.hit
                ? (result.crit ? "§e§lCRIT " + result.totalAttack : "§aHit! " + result.totalAttack)
                : "§7Miss! " + result.totalAttack;
        String subText = (result.hit ? "§c-" + actualDmg + " §7| " : "") +
                target.getName() + " §7[AC:" + result.targetAC + "]";
        TitleDisplay.showCombatResult(player, mainText, subText);

        String hitMsg = result.hit
                ? "§aHIT §7→ DMG: §c" + actualDmg + " §7(" + result.damageType + ")"
                : "§7MISS §7→ AC " + result.targetAC + " too high";
        Bukkit.broadcastMessage("§c[Combat]§r " + player.getName() + " menyerang "
                + target.getName() + "! §7[" + diceExpr + "=" + result.totalAttack
                + " vs AC:" + result.targetAC + "] " + hitMsg);

        // Kill check
        if (target.getHealth() <= 0) {
            Bukkit.broadcastMessage("§c§l" + target.getName() + " has been slain!");
            tm.removeParticipant(target);
        }
        return true;
    }

    private LivingEntity findTarget(TurnManager tm, String partial) {
        for (var entry : tm.getParticipants().entrySet()) {
            LivingEntity e = entry.getValue();
            if (e instanceof Player) continue; // skip player targets normally
            String idStr = entry.getKey().toString().substring(0, 8);
            if (idStr.toLowerCase().startsWith(partial.toLowerCase())
                    || (e.getName() != null && e.getName().toLowerCase().contains(partial.toLowerCase()))) {
                return e;
            }
        }
        return null;
    }

    private LivingEntity findFirstMobTarget(TurnManager tm) {
        for (var entry : tm.getParticipants().entrySet()) {
            if (!(entry.getValue() instanceof Player)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("melee", "ranged");
        }
        if (args.length == 2) {
            TurnManager tm = plugin.getTurnManager();
            List<String> targets = new ArrayList<>();
            String input = args[1].toLowerCase();
            for (var entry : tm.getParticipants().entrySet()) {
                if (entry.getValue() instanceof Player) continue;
                String id = entry.getKey().toString().substring(0, 8);
                String name = entry.getValue().getName() != null ? entry.getValue().getName() : id;
                if (id.toLowerCase().startsWith(input) || name.toLowerCase().startsWith(input)) {
                    targets.add(name);
                }
            }
            return targets;
        }
        return List.of();
    }
}
