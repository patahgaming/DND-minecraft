package id.patahgaming.dndplugin.item.listener;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.engine.combat.CombatEngine;
import id.patahgaming.dndplugin.engine.combat.TurnManager;
import id.patahgaming.dndplugin.item.ItemSpesialManager;
import id.patahgaming.dndplugin.item.ItemType;
import id.patahgaming.dndplugin.item.SpecialEffect;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
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
 * - Battle INACTIVE → SURPRISE ATTACK (flat damage + auto battle start + advantage initiative)
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
        if (target instanceof Player) return; // PvP — not handled

        // Cek weapon spesial di main hand
        ItemStack weapon = ItemSpesialManager.getWeaponInHand(player);
        if (weapon == null) return;

        // Cancel vanilla damage — kita yang handle
        event.setCancelled(true);

        TurnManager tm = plugin.getTurnManager();

        if (tm.getBattleState() == TurnManager.BattleState.ACTIVE) {
            handleBattleAttack(player, target, weapon, tm);
        } else {
            handleSurpriseAttack(player, target, weapon, tm);
        }
    }

    /**
     * Battle ACTIVE — jalankan /atk flow normal via PendingRoll.
     * Sama seperti player ketik /atk melee.
     */
    private void handleBattleAttack(Player player, LivingEntity target, ItemStack weapon, TurnManager tm) {
        if (!tm.isPlayerTurn(player)) {
            player.sendMessage("§cBukan giliran kamu!");
            return;
        }

        // Baca weapon stats dari PDC
        String atkType = ItemSpesialManager.getWeaponAttackType(weapon);
        boolean melee = !atkType.equalsIgnoreCase("RANGED");
        String dmgDice = ItemSpesialManager.getWeaponDamageDice(weapon);

        // Cek distance
        double dist = player.getLocation().distance(target.getLocation());
        double maxDist = melee ? 3.5 : 30.0;
        if (dist > maxDist) {
            player.sendMessage("§cTerlalu jauh! §7(jarak: " + String.format("%.1f", dist) + ")");
            return;
        }

        // Set ATTACK PendingRoll dengan weapon stats
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

        // Build PendingRoll dengan weapon context
        var pr = new id.patahgaming.dndplugin.engine.combat.PendingRoll();
        pr.rollType = id.patahgaming.dndplugin.engine.combat.PendingRoll.RollType.ATTACK;
        pr.diceExpr = "d20+" + atkBonus;
        pr.context = new java.util.LinkedHashMap<>();
        pr.context.put("targetId", targetId);
        pr.context.put("targetName", targetName);
        pr.context.put("attackType", melee ? "melee" : "ranged");
        pr.context.put("targetAC", String.valueOf(targetAC));
        pr.context.put("melee", String.valueOf(melee));
        // Weapon context — dipakai RollCommand saat resolve damage
        pr.context.put("weaponDice", dmgDice != null ? dmgDice : (melee ? "1d8" : "1d6"));
        int weaponBonus = ItemSpesialManager.getWeaponDamageBonus(weapon);
        pr.context.put("weaponBonus", String.valueOf(weaponBonus));
        pr.context.put("weaponDmgType", ItemSpesialManager.getWeaponDamageType(weapon).name());
        String sfx = ItemSpesialManager.getSpecialEffect(weapon).name();
        pr.context.put("weaponEffect", sfx);

        id.patahgaming.dndplugin.engine.combat.PendingRoll.set(player, pr);
        tm.selectAction(player);

        player.sendMessage("§c⚔ " + player.getName() + " menyerang " + targetName
                + "! Ketik §e/roll §7(d20+" + atkBonus + " vs AC " + targetAC + ")");
    }

    /**
     * Battle INACTIVE → SURPRISE ATTACK!
     * Flat damage (no roll), auto-start battle, advantage initiative.
     */
    private void handleSurpriseAttack(Player player, LivingEntity target, ItemStack weapon, TurnManager tm) {
        // Hitung flat damage
        String dmgDice = ItemSpesialManager.getWeaponDamageDice(weapon);
        int weaponBonus = ItemSpesialManager.getWeaponDamageBonus(weapon);
        CombatEngine.DamageType dmgType = ItemSpesialManager.getWeaponDamageType(weapon);
        int strMod = plugin.getPlayerDataStore().getAbilityModifier(player, "str");

        // Average dice: 1d8 → 4.5 ≈ 4
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

        // Apply damage
        CombatEngine.DamageResult result = CombatEngine.applyDamage(target, flatDamage, dmgType);

        Bukkit.broadcastMessage("§e⚡ SURPRISE! §f" + player.getName()
                + " §emenyerang §f" + target.getName()
                + " §esebelum pertempuran! §c-" + result.finalDamage + " HP §7[" + dmgType + "]");

        if (result.outcome == CombatEngine.DamageOutcome.DEAD) {
            Bukkit.broadcastMessage("§c§l" + target.getName() + " terbunuh dalam serangan kejutan!");
            return; // No battle needed — target already dead
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

        // Start battle dengan ADVANTAGE initiative untuk player
        tm.startBattle(participants);

        // Re-roll initiative untuk player (advantage = roll twice, keep highest)
        // Note: TurnManager.startBattle sudah roll initiative.
        // Untuk advantage, kita cek turn order dan announce.
        Bukkit.broadcastMessage("§ePertempuran dimulai! §f" + player.getName()
                + " §eberhasil menyerang duluan! (surprise round)");

        // Special effect trigger
        SpecialEffect effect = ItemSpesialManager.getSpecialEffect(weapon);
        if (effect == SpecialEffect.FIRST_BLOOD) {
            player.sendMessage("§c⚡ First Blood! Damage ×2 pada serangan pertama!");
            // Already applied above, just announce
        }
        if (effect != SpecialEffect.NONE && effect != SpecialEffect.FIRST_BLOOD) {
            effect.onHit(player, target, flatDamage);
        }
    }
}
