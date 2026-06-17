package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.engine.combat.CombatEngine;
import id.patahgaming.dndplugin.engine.combat.PendingRoll;
import id.patahgaming.dndplugin.engine.combat.TurnManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /atk <melee|ranged> [target]
 *
 * Full D&D 5e two-step attack flow:
 * Step 1: set ATTACK PendingRoll → player /roll → RollCommand checks hit/miss
 * Step 2: hit → set DAMAGE PendingRoll → player /roll → RollCommand applies damage
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

        if (tm.getBattleState() != TurnManager.BattleState.ACTIVE) {
            player.sendMessage("§cTidak ada battle aktif. Gunakan /battle start.");
            return true;
        }

        if (!tm.isPlayerTurn(player)) {
            player.sendMessage("§cBukan giliran kamu! Tunggu giliran.");
            player.sendMessage("§7Turn: " + tm.getCurrentTurnDisplay());
            return true;
        }

        // Cek pending roll aktif
        if (PendingRoll.has(player)) {
            player.sendMessage("§cSelesaikan /roll dulu sebelum menyerang lagi.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§eGunakan: /atk <melee|ranged> [target]");
            return true;
        }

        String attackType = args[0].toLowerCase();
        if (!attackType.equals("melee") && !attackType.equals("ranged")) {
            player.sendMessage("§cGunakan 'melee' atau 'ranged'. Contoh: /atk melee goblin");
            return true;
        }
        boolean melee = attackType.equals("melee");

        // Cari target
        LivingEntity target;
        if (args.length >= 2) {
            target = findTarget(tm, args[1]);
        } else {
            target = findFirstMobTarget(tm);
        }

        if (target == null) {
            player.sendMessage("§cTarget tidak ditemukan. Gunakan TAB untuk lihat target.");
            player.sendMessage("§7Target tersedia (pakai ID):");
            listTargets(tm, player);
            return true;
        }

        if (target.isDead()) {
            player.sendMessage("§cTarget sudah mati.");
            return true;
        }

        // --- Distance check ---
        double dist = player.getLocation().distance(target.getLocation());
        double maxDist = melee ? 3.5 : 30.0; // melee ~3 blocks, ranged ~30 blocks
        if (dist > maxDist) {
            player.sendMessage("§cTerlalu jauh! " + (melee ? "Jarak melee max 3 block." : "Jarak ranged max 30 block.")
                    + " §7(jarak: " + String.format("%.1f", dist) + ")");
            return true;
        }

        // Select action
        tm.selectAction(player);

        // Build diceExpr + context
        int strMod = plugin.getPlayerDataStore().getAbilityModifier(player, "str");
        int dexMod = plugin.getPlayerDataStore().getAbilityModifier(player, "dex");
        int profBonus = CombatEngine.proficiencyBonus(plugin.getPlayerDataStore().getLevel(player));

        int atkBonus = melee ? (strMod + profBonus) : (dexMod + profBonus);
        String diceExpr = "d20+" + atkBonus;

        int targetAC = target instanceof Player tp
                ? CombatEngine.calculatePlayerAC(plugin, tp)
                : CombatEngine.calculateMobAC(target);
        String targetId = target.getUniqueId().toString().substring(0, 8);
        String targetName = target instanceof Player p ? p.getName()
                : (target.getName() != null ? target.getName() : target.getType().name());

        // Set ATTACK PendingRoll with context
        PendingRoll pr = new PendingRoll();
        pr.rollType = PendingRoll.RollType.ATTACK;
        pr.diceExpr = diceExpr;
        pr.promptMsg = (melee ? "§7[Melee] " : "§7[Ranged] ") + player.getName()
                + " menyerang " + targetName + "! Ketik §e/roll";
        pr.context = new LinkedHashMap<>();
        pr.context.put("targetId", targetId);
        pr.context.put("targetName", targetName);
        pr.context.put("attackType", attackType);
        pr.context.put("targetAC", String.valueOf(targetAC));
        pr.context.put("melee", String.valueOf(melee));

        PendingRoll.set(player, pr);

        // Prompt
        player.sendMessage((melee ? "§c⚔ [Melee] " : "§c🏹 [Ranged] ")
                + player.getName() + " menyerang " + targetName
                + "! Ketik §e/roll §7(" + diceExpr + " vs AC " + targetAC + ")");

        // player now controls flow via /roll — RollCommand resolves everything
        return true;
    }

    private LivingEntity findTarget(TurnManager tm, String partial) {
        for (var entry : tm.getParticipants().entrySet()) {
            LivingEntity e = entry.getValue();
            if (e instanceof Player) continue;
            String idStr = entry.getKey().toString().substring(0, 8);
            if (idStr.toLowerCase().startsWith(partial.toLowerCase())
                    || (e.getName() != null && e.getName().toLowerCase().contains(partial.toLowerCase()))) {
                return e;
            }
        }
        return null;
    }

    private void listTargets(TurnManager tm, Player player) {
        for (var entry : tm.getParticipants().entrySet()) {
            if (entry.getValue() instanceof Player) continue;
            String id = entry.getKey().toString().substring(0, 8);
            String name = entry.getValue().getName() != null ? entry.getValue().getName() : "?";
            int hp = (int) entry.getValue().getHealth();
            player.sendMessage("  §e" + id + " §7→ " + name + " §c[" + hp + " HP]");
        }
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
                String name = entry.getValue().getName() != null ? entry.getValue().getName() : "?";
                if (id.toLowerCase().startsWith(input) || name.toLowerCase().contains(input)) {
                    targets.add(id);  // UUID prefix — unique per entity
                }
            }
            return targets;
        }
        return List.of();
    }
}
