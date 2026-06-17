package id.patahgaming.dndplugin.engine.combat;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.CharacterClass;
import id.patahgaming.dndplugin.data.SpellDataStore;
import id.patahgaming.dndplugin.data.SpellDataStore.SpellData;
import id.patahgaming.dndplugin.engine.dice.DiceRoller;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * SpellEngine — resolves the 3 spell paths.
 *
 * Jalur A: ATTACK_ROLL → SPELL_ATTACK pending → /roll → hit? SPELL_DAMAGE : next turn
 * Jalur B: SAVING_THROW → auto-roll saves → SPELL_DAMAGE per target → /roll loop
 * Jalur C: AUTO_HIT → SPELL_DAMAGE pending → /roll → apply → next turn
 *
 * Called from CastCommand (initiate) and RollCommand (resolve).
 */
public class SpellEngine {

    /**
     * Result dari initiateSpell — memberitahu CastCommand apa yang terjadi.
     */
    public static class InitiateResult {
        public boolean success;
        public String errorMsg;        // kalau gagal
        public String promptMsg;       // prompt /roll ke player
        public int spellSlotsLeft;
    }

    /**
     * Result dari resolveSpellRoll — memberitahu RollCommand langkah selanjutnya.
     */
    public static class ResolveResult {
        public boolean done;           // true = all targets resolved, advance turn
        public String nextPrompt;      // prompt untuk /roll berikutnya (multi-target)
        public String narrationText;   // text untuk broadcast/narasi
        public boolean kill;           // target terbunuh?
        public String killName;        // nama yang mati
    }

    // ==================== INITIATE ====================

    /**
     * Initiate spell casting. Validates, deducts slot, sets first PendingRoll.
     */
    public static InitiateResult initiate(DndPlugin plugin, Player caster,
                                          SpellData spell, LivingEntity target,
                                          TurnManager tm) {
        InitiateResult result = new InitiateResult();

        // Spell slots
        var pdc = caster.getPersistentDataContainer();
        var keySlots = new org.bukkit.NamespacedKey("dndplugin", "spell_slots");
        int slots = pdc.getOrDefault(keySlots, org.bukkit.persistence.PersistentDataType.INTEGER, 2);
        if (slots <= 0) {
            result.success = false;
            result.errorMsg = "§cSpell slots habis! Istirahat dulu untuk regen.";
            return result;
        }

        // Deduct slot
        slots--;
        pdc.set(keySlots, org.bukkit.persistence.PersistentDataType.INTEGER, slots);
        result.spellSlotsLeft = slots;

        int spellMod = getSpellMod(plugin, caster);
        int profBonus = CombatEngine.proficiencyBonus(plugin.getPlayerDataStore().getLevel(caster));

        // Sound
        caster.playSound(caster.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.5f);

        switch (spell.attackType()) {
            case ATTACK_ROLL -> initiateAttackRoll(plugin, caster, spell, target, spellMod, profBonus, result);
            case SAVING_THROW -> initiateSavingThrow(plugin, caster, spell, target, spellMod, profBonus, result);
            case AUTO_HIT -> initiateAutoHit(plugin, caster, spell, target, result);
        }

        result.success = true;
        return result;
    }

    // --- Jalur A: ATTACK_ROLL ---
    private static void initiateAttackRoll(DndPlugin plugin, Player caster, SpellData spell,
                                           LivingEntity target, int spellMod, int profBonus,
                                           InitiateResult result) {
        String diceExpr = "d20+" + (spellMod + profBonus);
        int targetAC = target instanceof Player tp
                ? CombatEngine.calculatePlayerAC(plugin, tp)
                : CombatEngine.calculateMobAC(target);

        PendingRoll pr = new PendingRoll();
        pr.rollType = PendingRoll.RollType.SPELL_ATTACK;
        pr.diceExpr = diceExpr;
        pr.context = new LinkedHashMap<>();
        pr.context.put("spellId", spell.spellId());
        pr.context.put("spellName", spell.displayName());
        pr.context.put("damageType", spell.damageType().name());
        pr.context.put("damageDice", spell.damageDice());
        pr.context.put("damageBonus", String.valueOf(spell.damageBonus()));
        pr.context.put("targetId", target.getUniqueId().toString().substring(0, 8));
        pr.context.put("targetName", safeName(target));
        pr.context.put("targetAC", String.valueOf(targetAC));
        pr.context.put("spellMod", String.valueOf(spellMod));
        pr.context.put("profBonus", String.valueOf(profBonus));

        PendingRoll.set(caster, pr);

        result.promptMsg = "§d🔮 " + spell.displayName() + "! Ketik §e/roll §d(" + diceExpr + " vs AC " + targetAC + ")";
        caster.sendMessage(result.promptMsg);
    }

    // --- Jalur B: SAVING_THROW ---
    private static void initiateSavingThrow(DndPlugin plugin, Player caster, SpellData spell,
                                            LivingEntity target, int spellMod, int profBonus,
                                            InitiateResult result) {
        int saveDC = 8 + spellMod + profBonus;
        String saveStat = spell.savingStat() != null ? spell.savingStat() : "dex";

        // Auto-roll save untuk target
        boolean saved = rollTargetSave(target, saveStat, saveDC);

        PendingRoll pr = new PendingRoll();
        pr.rollType = PendingRoll.RollType.SPELL_DAMAGE;
        pr.diceExpr = spell.damageDice();
        if (spell.damageBonus() > 0) pr.diceExpr += "+" + spell.damageBonus();

        pr.context = new LinkedHashMap<>();
        pr.context.put("spellId", spell.spellId());
        pr.context.put("spellName", spell.displayName());
        pr.context.put("damageType", spell.damageType().name());
        pr.context.put("damageDice", spell.damageDice());
        pr.context.put("damageBonus", String.valueOf(spell.damageBonus()));
        pr.context.put("onSave", spell.onSave().name());
        pr.context.put("saveDC", String.valueOf(saveDC));
        pr.context.put("saveStat", saveStat);
        pr.context.put("autoHit", "false");
        pr.context.put("isCrit", "false");
        pr.context.put("currentTargetIndex", "0");
        pr.context.put("totalTargets", "1");

        // Single target info
        PendingRoll.TargetSaveInfo info = new PendingRoll.TargetSaveInfo();
        info.id = target.getUniqueId().toString().substring(0, 8);
        info.name = safeName(target);
        info.saved = saved;
        PendingRoll.putTargetCtx(pr.context, 0, info);

        PendingRoll.set(caster, pr);

        String saveResultText = saved ? "§aberhasil save" : "§cgagal save";
        String dmgHint = saved && spell.onSave() == SpellDataStore.OnSave.NEGATES
                ? "§7(0 damage — save negates)"
                : saved ? "§7(half damage)"
                : "§7(full damage)";

        result.promptMsg = "§d🔮 " + spell.displayName() + "! §7[" + saveStat.toUpperCase()
                + " save " + saveResultText + "§7] Ketik §e/roll §d(" + pr.diceExpr + " " + spell.damageType() + " → " + safeName(target) + ") " + dmgHint;
        caster.sendMessage(result.promptMsg);
    }

    // --- Jalur C: AUTO_HIT ---
    private static void initiateAutoHit(DndPlugin plugin, Player caster, SpellData spell,
                                        LivingEntity target, InitiateResult result) {
        PendingRoll pr = new PendingRoll();
        pr.rollType = PendingRoll.RollType.SPELL_DAMAGE;
        pr.diceExpr = spell.damageDice();
        if (spell.damageBonus() > 0) pr.diceExpr += "+" + spell.damageBonus();

        pr.context = new LinkedHashMap<>();
        pr.context.put("spellId", spell.spellId());
        pr.context.put("spellName", spell.displayName());
        pr.context.put("damageType", spell.damageType().name());
        pr.context.put("damageDice", spell.damageDice());
        pr.context.put("damageBonus", String.valueOf(spell.damageBonus()));
        pr.context.put("onSave", "NEGATES");
        pr.context.put("autoHit", "true");
        pr.context.put("isCrit", "false");
        pr.context.put("currentTargetIndex", "0");
        pr.context.put("totalTargets", "1");

        PendingRoll.TargetSaveInfo info = new PendingRoll.TargetSaveInfo();
        info.id = target.getUniqueId().toString().substring(0, 8);
        info.name = safeName(target);
        info.saved = false; // auto-hit = never saved
        PendingRoll.putTargetCtx(pr.context, 0, info);

        PendingRoll.set(caster, pr);

        result.promptMsg = "§d✨ " + spell.displayName() + " auto-hit! Ketik §e/roll §d(" + pr.diceExpr + " " + spell.damageType() + " damage)";
        caster.sendMessage(result.promptMsg);
    }

    // ==================== RESOLVE SPELL ATTACK ROLL ====================

    /**
     * Resolve SPELL_ATTACK roll. Returns ResolveResult.
     * - nat20: set SPELL_DAMAGE with doubled dice
     * - hit: set SPELL_DAMAGE with normal dice
     * - miss/nat1: clear pending, advance turn
     */
    public static ResolveResult resolveSpellAttack(DndPlugin plugin, Player caster,
                                                   int rollResult, PendingRoll pending, TurnManager tm) {
        ResolveResult r = new ResolveResult();

        Map<String, String> ctx = pending.context;
        int spellMod = Integer.parseInt(ctx.getOrDefault("spellMod", "0"));
        int profBonus = Integer.parseInt(ctx.getOrDefault("profBonus", "0"));
        int targetAC = Integer.parseInt(ctx.getOrDefault("targetAC", "10"));
        String spellName = ctx.getOrDefault("spellName", "Spell");
        String damageDice = ctx.getOrDefault("damageDice", "1d6");
        int damageBonus = Integer.parseInt(ctx.getOrDefault("damageBonus", "0"));
        String damageType = ctx.getOrDefault("damageType", "FORCE");
        String targetId = ctx.getOrDefault("targetId", "?");
        String targetName = ctx.getOrDefault("targetName", "?");

        int totalAttack = rollResult + spellMod + profBonus;
        boolean crit = rollResult == 20;
        boolean hit = crit || totalAttack >= targetAC;

        // Find target entity
        LivingEntity target = findTarget(tm, targetId);

        if (!hit) {
            // MISS
            PendingRoll.clear(caster);
            r.done = true;
            r.narrationText = "§7Miss! " + totalAttack + " vs AC " + targetAC;
            Bukkit.broadcastMessage("§d[Spell]§r " + caster.getName() + " casts §b" + spellName
                    + "§r → " + targetName + " §7[d20:" + rollResult + "+" + (spellMod + profBonus)
                    + "=" + totalAttack + " vs AC:" + targetAC + "] §7MISS");
            tm.resolveAction();
            return r;
        }

        // HIT → set SPELL_DAMAGE
        String finalDice = crit ? DiceRoller.doubleDiceExpr(damageDice) : damageDice;
        if (damageBonus > 0 && !finalDice.contains("+")) {
            finalDice += "+" + damageBonus;
        }

        PendingRoll damagePr = new PendingRoll();
        damagePr.rollType = PendingRoll.RollType.SPELL_DAMAGE;
        damagePr.diceExpr = finalDice;
        damagePr.context = new LinkedHashMap<>(ctx);
        damagePr.context.put("isCrit", String.valueOf(crit));
        damagePr.context.put("currentTargetIndex", "0");
        damagePr.context.put("totalTargets", "1");
        damagePr.context.put("autoHit", "false");
        damagePr.context.put("onSave", "NEGATES");

        // Single target info
        PendingRoll.TargetSaveInfo info = new PendingRoll.TargetSaveInfo();
        info.id = targetId;
        info.name = targetName;
        info.saved = false;
        PendingRoll.putTargetCtx(damagePr.context, 0, info);

        PendingRoll.set(caster, damagePr);

        String critLabel = crit ? "§e§lCRIT! " : "§aHit! ";
        r.nextPrompt = "§d" + critLabel + totalAttack + " vs AC " + targetAC
                + "§d! Ketik §e/roll §d(" + finalDice + " " + damageType + " damage)";
        r.narrationText = critLabel + spellName + ": " + finalDice + " " + damageType;

        Bukkit.broadcastMessage("§d[Spell]§r " + caster.getName() + " casts §b" + spellName
                + "§r → " + targetName + " §7[d20:" + rollResult + "+" + (spellMod + profBonus)
                + "=" + totalAttack + " vs AC:" + targetAC + "] "
                + (crit ? "§e§lCRIT!" : "§aHIT"));

        return r;
    }

    // ==================== RESOLVE SPELL DAMAGE ROLL ====================

    /**
     * Resolve SPELL_DAMAGE roll. Applies damage to current target.
     * If more targets remain, advances to next target and sets new PendingRoll.
     *
     * @param totalDamage total hasil roll (diceExpr sudah di-parse oleh RollCommand)
     */
    public static ResolveResult resolveSpellDamage(DndPlugin plugin, Player caster,
                                                   int totalDamage, PendingRoll pending, TurnManager tm) {
        ResolveResult r = new ResolveResult();

        Map<String, String> ctx = pending.context;
        String spellName = ctx.getOrDefault("spellName", "Spell");
        String damageTypeStr = ctx.getOrDefault("damageType", "FORCE");
        CombatEngine.DamageType damageType;
        try {
            damageType = CombatEngine.DamageType.valueOf(damageTypeStr);
        } catch (IllegalArgumentException e) {
            damageType = CombatEngine.DamageType.FORCE;
        }

        String onSaveStr = ctx.getOrDefault("onSave", "NEGATES");
        boolean halfOnSave = onSaveStr.equalsIgnoreCase("HALF");
        boolean isCrit = Boolean.parseBoolean(ctx.getOrDefault("isCrit", "false"));

        int currentIdx = Integer.parseInt(ctx.getOrDefault("currentTargetIndex", "0"));
        int totalTargets = Integer.parseInt(ctx.getOrDefault("totalTargets", "1"));

        // Get current target info
        PendingRoll.TargetSaveInfo targetInfo = PendingRoll.parseTargetCtx(ctx, currentIdx);
        if (targetInfo == null) {
            // No target info — just clear and advance
            PendingRoll.clear(caster);
            r.done = true;
            tm.resolveAction();
            return r;
        }

        LivingEntity target = findTarget(tm, targetInfo.id);
        if (target == null || target.isDead()) {
            // Target already dead/missing → skip to next
            return advanceToNextTarget(caster, spellName, damageType, halfOnSave,
                    currentIdx, totalTargets, ctx, tm, r);
        }

        // Calculate actual damage
        int appliedDamage;
        if (targetInfo.saved) {
            if (halfOnSave) {
                appliedDamage = Math.max(1, totalDamage / 2);
            } else {
                appliedDamage = 0; // save negates
            }
        } else {
            appliedDamage = totalDamage;
        }

        // Apply damage — PATCH: DamageResult
        int actualDmg = 0;
        CombatEngine.DamageResult dmgResult = null;
        if (appliedDamage > 0) {
            dmgResult = CombatEngine.applyDamage(target, appliedDamage, damageType);
            actualDmg = dmgResult.finalDamage;
        }

        // Broadcast
        String saveNote = targetInfo.saved
                ? (halfOnSave && actualDmg > 0 ? " §7(saved, half: -" + actualDmg + ")"
                    : " §a(saved, no damage)")
                : " §7(failed save)";
        String critNote = isCrit ? " §e§lCRIT!" : "";

        Bukkit.broadcastMessage("§d[Spell]§r " + caster.getName() + " → " + targetInfo.name
                + " §c-" + actualDmg + " " + damageTypeStr + critNote + saveNote
                + " §7[rolled:" + totalDamage + "]");

        r.narrationText = spellName + ": " + targetInfo.name + " takes " + actualDmg + " " + damageTypeStr;

        // Handle outcome — PATCH
        if (dmgResult != null) {
            switch (dmgResult.outcome) {
                case UNCONSCIOUS -> {
                    if (target instanceof org.bukkit.entity.Player tp) {
                        // DeathSaveManager handles via DndPlugin
                    }
                }
                case DEAD -> {
                    Bukkit.broadcastMessage("§c§l" + targetInfo.name + " has been slain by magic!");
                    tm.removeParticipant(target);
                    r.kill = true;
                    r.killName = targetInfo.name;
                }
                case ALIVE -> { /* nothing */ }
            }
        }

        // Advance to next target or finish
        return advanceToNextTarget(caster, spellName, damageType, halfOnSave,
                currentIdx, totalTargets, ctx, tm, r);
    }

    private static ResolveResult advanceToNextTarget(Player caster, String spellName,
                                                     CombatEngine.DamageType damageType,
                                                     boolean halfOnSave, int currentIdx,
                                                     int totalTargets, Map<String, String> ctx,
                                                     TurnManager tm, ResolveResult r) {
        int nextIdx = currentIdx + 1;

        if (nextIdx >= totalTargets) {
            // All targets done
            PendingRoll.clear(caster);
            r.done = true;
            tm.resolveAction();
            return r;
        }

        // Set next target as active
        PendingRoll.TargetSaveInfo nextTarget = PendingRoll.parseTargetCtx(ctx, nextIdx);
        if (nextTarget == null) {
            PendingRoll.clear(caster);
            r.done = true;
            tm.resolveAction();
            return r;
        }

        // Build new PendingRoll for next target
        String diceExpr = ctx.getOrDefault("damageDice", "1d6");
        int bonus = Integer.parseInt(ctx.getOrDefault("damageBonus", "0"));
        if (bonus > 0 && !diceExpr.contains("+")) diceExpr += "+" + bonus;

        PendingRoll nextPr = new PendingRoll();
        nextPr.rollType = PendingRoll.RollType.SPELL_DAMAGE;
        nextPr.diceExpr = diceExpr;
        nextPr.context = new LinkedHashMap<>(ctx);
        nextPr.context.put("currentTargetIndex", String.valueOf(nextIdx));

        PendingRoll.set(caster, nextPr);

        String saveResultText = nextTarget.saved ? "§aberhasil save" : "§cgagal save";
        String dmgHint = nextTarget.saved && !halfOnSave
                ? "§7(0 damage — save negates)"
                : nextTarget.saved ? "§7(half damage)"
                : "§7(full damage)";

        r.nextPrompt = "§d🔮 Target berikutnya: " + nextTarget.name
                + " §7[" + saveResultText + "§7] Ketik §e/roll §d(" + diceExpr + " " + damageType + " → " + nextTarget.name + ") " + dmgHint;

        return r;
    }

    // ==================== HELPERS ====================

    private static int getSpellMod(DndPlugin plugin, Player caster) {
        CharacterClass charClass = CharacterClass.fromName(
                plugin.getPlayerDataStore().getCharacterClass(caster));
        String primaryAbility = charClass != null ? charClass.getPrimaryAbility() : "int";
        return plugin.getPlayerDataStore().getAbilityModifier(caster, primaryAbility);
    }

    private static boolean rollTargetSave(LivingEntity target, String saveStat, int saveDC) {
        int saveMod = 0;
        if (target instanceof Player p) {
            // Would need plugin ref, but for now mobs don't cast spells at players
            saveMod = 0;
        }
        // Mob saves: approximate based on type
        saveMod += switch (target.getType()) {
            case ZOMBIE -> saveStat.equalsIgnoreCase("con") ? 3 : -2;
            case SKELETON -> saveStat.equalsIgnoreCase("dex") ? 2 : 0;
            case SPIDER -> saveStat.equalsIgnoreCase("dex") ? 3 : 1;
            case ENDERMAN -> saveStat.equalsIgnoreCase("dex") ? 4 : 2;
            case WITCH -> saveStat.equalsIgnoreCase("wis") ? 4 : 2;
            case EVOKER -> saveStat.equalsIgnoreCase("wis") ? 5 : 3;
            case IRON_GOLEM -> saveStat.equalsIgnoreCase("con") ? 6 : 0;
            default -> 0;
        };

        int roll = DiceRoller.roll(20);
        int total = roll + saveMod;
        return total >= saveDC;
    }

    private static LivingEntity findTarget(TurnManager tm, String idPrefix) {
        for (var entry : tm.getParticipants().entrySet()) {
            String idStr = entry.getKey().toString().substring(0, 8);
            if (idStr.equalsIgnoreCase(idPrefix) || idStr.toLowerCase().startsWith(idPrefix.toLowerCase())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String safeName(LivingEntity e) {
        if (e instanceof Player p) return p.getName();
        String name = e.getName();
        return name != null ? name : e.getType().name();
    }
}
