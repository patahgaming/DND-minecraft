package id.patahgaming.dndplugin.item.listener;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.command.AtkCommand;
import id.patahgaming.dndplugin.engine.combat.CombatEngine;
import id.patahgaming.dndplugin.engine.combat.PendingRoll;
import id.patahgaming.dndplugin.engine.combat.TurnManager;
import id.patahgaming.dndplugin.item.ItemSpesialManager;
import id.patahgaming.dndplugin.item.ItemType;
import id.patahgaming.dndplugin.item.SpecialEffect;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Klik kiri mob dengan weapon spesial → auto /atk flow.
 *
 * - Battle ACTIVE + giliran player → jalankan /atk melee flow (PendingRoll)
 * - Battle INACTIVE → SURPRISE ATTACK (flat damage + auto battle start)
 */
public class WeaponAttackListener implements Listener {

    private final DndPlugin plugin;

    public WeaponAttackListener(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerAttackMob(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (target instanceof Player) return;

        ItemStack weapon = ItemSpesialManager.getWeaponInHand(player);
        if (weapon == null) return;

        event.setCancelled(true);

        TurnManager tm = plugin.getTurnManager();

        if (tm.getBattleState() == TurnManager.BattleState.ACTIVE) {
            handleBattleAttack(player, target, weapon, tm);
        } else {
            handleSurpriseAttack(player, target, weapon, tm);
        }
    }

    /**
     * Battle ACTIVE — gunakan shared builder dari AtkCommand.
     */
    private void handleBattleAttack(Player player, LivingEntity target, ItemStack weapon, TurnManager tm) {
        if (!tm.isPlayerTurn(player)) {
            player.sendMessage("§cBukan giliran kamu!");
            return;
        }

        String atkType = ItemSpesialManager.getWeaponAttackType(weapon);
        boolean melee = !atkType.equalsIgnoreCase("RANGED");

        // Distance check
        double dist = player.getLocation().distance(target.getLocation());
        double maxDist = melee ? 3.5 : 30.0;
        if (dist > maxDist) {
            player.sendMessage("§cTerlalu jauh! §7(jarak: " + String.format("%.1f", dist) + ")");
            return;
        }

        // Build weapon stats
        String wDice = ItemSpesialManager.getWeaponDamageDice(weapon);
        AtkCommand.WeaponStats wStats = new AtkCommand.WeaponStats(
                wDice != null ? wDice : (melee ? "1d8" : "1d6"),
                ItemSpesialManager.getWeaponDamageBonus(weapon),
                ItemSpesialManager.getWeaponDamageType(weapon).name(),
                ItemSpesialManager.getSpecialEffect(weapon).name()
        );

        // Shared builder
        PendingRoll pr = AtkCommand.buildAttackPendingRoll(plugin, player, target, atkType, wStats);
        tm.selectAction(player);

        int targetAC = Integer.parseInt(pr.context.get("targetAC"));
        player.sendMessage("§c⚔ " + player.getName() + " menyerang " + pr.context.get("targetName")
                + "! Ketik §e/roll §7(" + pr.diceExpr + " vs AC " + targetAC + ")");
    }

    /**
     * Battle INACTIVE → SURPRISE ATTACK!
     * Flat damage (average dice), auto-start battle.
     */
    private void handleSurpriseAttack(Player player, LivingEntity target, ItemStack weapon, TurnManager tm) {
        String dmgDice = ItemSpesialManager.getWeaponDamageDice(weapon);
        int weaponBonus = ItemSpesialManager.getWeaponDamageBonus(weapon);
        CombatEngine.DamageType dmgType = ItemSpesialManager.getWeaponDamageType(weapon);
        int strMod = plugin.getPlayerDataStore().getAbilityModifier(player, "str");

        // Average dice
        int avgDice = 4;
        if (dmgDice != null) {
            String[] parts = dmgDice.split("d");
            if (parts.length == 2) {
                int count = Integer.parseInt(parts[0]);
                int sides = Integer.parseInt(parts[1]);
                avgDice = count * (sides + 1) / 2;
            }
        }
        int flatDamage = avgDice + weaponBonus + strMod;

        CombatEngine.DamageResult result = CombatEngine.applyDamage(target, flatDamage, dmgType);

        Bukkit.broadcastMessage("§e⚡ SURPRISE! §f" + player.getName()
                + " §emenyerang §f" + target.getName()
                + " §esebelum pertempuran! §c-" + result.finalDamage + " HP §7[" + dmgType + "]");

        if (result.outcome == CombatEngine.DamageOutcome.DEAD) {
            Bukkit.broadcastMessage("§c§l" + target.getName() + " terbunuh dalam serangan kejutan!");
            return;
        }

        // Auto-start battle
        List<LivingEntity> targets = new ArrayList<>();
        for (var entity : player.getNearbyEntities(15, 8, 15)) {
            if (entity instanceof Monster && entity instanceof LivingEntity living && !living.isDead()) {
                targets.add(living);
            }
        }

        List<LivingEntity> participants = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) participants.add(p);
        participants.addAll(targets);

        tm.startBattle(participants);

        Bukkit.broadcastMessage("§ePertempuran dimulai! §f" + player.getName()
                + " §eberhasil menyerang duluan! (surprise round)");

        // Special effect trigger
        SpecialEffect effect = ItemSpesialManager.getSpecialEffect(weapon);
        if (effect == SpecialEffect.FIRST_BLOOD) {
            player.sendMessage("§c⚡ First Blood! Damage ×2 pada serangan pertama!");
        }
        if (effect != SpecialEffect.NONE && effect != SpecialEffect.FIRST_BLOOD) {
            effect.onHit(player, target, flatDamage);
        }
    }
}
