package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.dice.DiceType;
import id.patahgaming.dndplugin.engine.combat.CombatEngine;
import id.patahgaming.dndplugin.engine.combat.PendingRoll;
import id.patahgaming.dndplugin.engine.combat.TurnManager;
import id.patahgaming.dndplugin.item.ItemSpesialManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        // Collect weapon stats dari item spesial di main hand
        WeaponStats wStats = null;
        ItemStack weapon = ItemSpesialManager.getWeaponInHand(player);
        if (weapon != null) {
            String wDice = ItemSpesialManager.getWeaponDamageDice(weapon);
            wStats = new WeaponStats(
                    wDice != null ? wDice : (melee ? "1d8" : "1d6"),
                    ItemSpesialManager.getWeaponDamageBonus(weapon),
                    ItemSpesialManager.getWeaponDamageType(weapon).name(),
                    ItemSpesialManager.getSpecialEffect(weapon).name()
            );
        }

        // Build & set ATTACK PendingRoll via shared builder
        PendingRoll pr = buildAttackPendingRoll(plugin, player, target, attackType, wStats);

        // Prompt — include dice info
        int targetAC = Integer.parseInt(pr.context.get("targetAC"));
        DiceType d20 = DiceType.D20;
        player.sendMessage((melee ? "§c⚔ [Melee] " : "§c🏹 [Ranged] ")
                + player.getName() + " menyerang " + pr.context.get("targetName")
                + "! Pegang " + d20.getColor() + d20.getLabel() + " (" + d20.getColorName() + " Wool)"
                + "§7 lalu §e/roll §7(" + pr.diceExpr + " vs AC " + targetAC + ")");

        // player now controls flow via /roll — RollCommand resolves everything
        return true;
    }

    private LivingEntity findTarget(TurnManager tm, String partial) {
        UUID attackerUuid = null; // filled below for PvP check
        for (var entry : tm.getParticipants().entrySet()) {
            LivingEntity e = entry.getValue();
            // PvE: skip players. PvP: skip players on same team.
            if (e instanceof Player && !tm.isPvP()) continue;
            String idStr = entry.getKey().toString().substring(0, 8);
            String name = e.getName() != null ? e.getName() : "";
            if (idStr.toLowerCase().startsWith(partial.toLowerCase())
                    || name.toLowerCase().contains(partial.toLowerCase())) {
                return e;
            }
        }
        return null;
    }

    private void listTargets(TurnManager tm, Player player) {
        for (var entry : tm.getParticipants().entrySet()) {
            LivingEntity e = entry.getValue();
            // PvE: skip players. PvP: skip players on same team.
            if (e instanceof Player targetPlayer) {
                if (!tm.isPvP()) continue;
                if (tm.isSameTeam(player.getUniqueId(), targetPlayer.getUniqueId())) continue;
            }
            String id = entry.getKey().toString().substring(0, 8);
            String name = e.getName() != null ? e.getName() : "?";
            int hp = (int) e.getHealth();
            String prefix = (e instanceof Player) ? "§b" : "§e";
            player.sendMessage("  " + prefix + id + " §7→ " + name + " §c[" + hp + " HP]");
        }
    }

    private LivingEntity findFirstMobTarget(TurnManager tm) {
        for (var entry : tm.getParticipants().entrySet()) {
            LivingEntity e = entry.getValue();
            // PvE: return first non-player. PvP: return first non-self player (enemy)
            if (tm.isPvP()) {
                if (e instanceof Player) return e; // any other player = enemy team
            } else {
                if (!(e instanceof Player)) return e;
            }
        }
        return null;
    }

    /**
     * Shared builder — dipakai AtkCommand + WeaponAttackListener.
     * Build ATTACK PendingRoll dengan weapon stats dari item di main hand.
     *
     * @param weaponStats nullable — kalau null, pakai default (1d8 melee / 1d6 ranged)
     */
    public static PendingRoll buildAttackPendingRoll(DndPlugin plugin, Player player,
                                                      LivingEntity target, String attackType,
                                                      WeaponStats weaponStats) {
        boolean melee = !attackType.equalsIgnoreCase("ranged");

        int strMod = plugin.getPlayerDataStore().getAbilityModifier(player, "str");
        int dexMod = plugin.getPlayerDataStore().getAbilityModifier(player, "dex");
        int profBonus = CombatEngine.proficiencyBonus(plugin.getPlayerDataStore().getLevel(player));
        int atkBonus = melee ? (strMod + profBonus) : (dexMod + profBonus);

        int targetAC = target instanceof Player tp
                ? CombatEngine.calculatePlayerAC(plugin, tp)
                : CombatEngine.calculateMobAC(target);
        String targetId = target.getUniqueId().toString().substring(0, 8);
        String targetName = target instanceof Player p ? p.getName()
                : (target.getName() != null ? target.getName() : target.getType().name());

        PendingRoll pr = new PendingRoll();
        pr.rollType = PendingRoll.RollType.ATTACK;
        pr.diceExpr = "d20+" + atkBonus;
        pr.context = new LinkedHashMap<>();
        pr.context.put("targetId", targetId);
        pr.context.put("targetName", targetName);
        pr.context.put("attackType", attackType);
        pr.context.put("targetAC", String.valueOf(targetAC));
        pr.context.put("melee", String.valueOf(melee));

        // Weapon stats
        if (weaponStats != null) {
            pr.context.put("weaponDice", weaponStats.dice);
            pr.context.put("weaponBonus", String.valueOf(weaponStats.bonus));
            pr.context.put("weaponDmgType", weaponStats.dmgType);
            pr.context.put("weaponEffect", weaponStats.effect);
        } else {
            pr.context.put("weaponDice", melee ? "1d8" : "1d6");
            pr.context.put("weaponBonus", "0");
            pr.context.put("weaponDmgType", melee ? "SLASHING" : "PIERCING");
            pr.context.put("weaponEffect", "NONE");
        }

        PendingRoll.set(player, pr);
        return pr;
    }

    /** Weapon stats from item PDC — digunakan oleh shared builder. */
    public record WeaponStats(String dice, int bonus, String dmgType, String effect) {}

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
                LivingEntity e = entry.getValue();
                // PvE: skip players. PvP: include enemy players.
                if (e instanceof Player) {
                    if (!tm.isPvP()) continue;
                }
                String id = entry.getKey().toString().substring(0, 8);
                String name = e.getName() != null ? e.getName() : "?";
                if (id.toLowerCase().startsWith(input) || name.toLowerCase().contains(input)) {
                    targets.add(id);
                }
            }
            return targets;
        }
        return List.of();
    }
}
