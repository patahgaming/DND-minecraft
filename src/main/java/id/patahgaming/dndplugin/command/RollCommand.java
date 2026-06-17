package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.CharacterClass;
import id.patahgaming.dndplugin.engine.combat.CombatEngine;
import id.patahgaming.dndplugin.engine.combat.PendingRoll;
import id.patahgaming.dndplugin.engine.combat.SpellEngine;
import id.patahgaming.dndplugin.engine.combat.TurnManager;
import id.patahgaming.dndplugin.engine.dice.DiceRoller;
import id.patahgaming.dndplugin.ui.TitleDisplay;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
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
import java.util.Map;

/**
 * /roll [type] — dice roll + PendingRoll resolver.
 *
 * Dua mode:
 * 1. Ada PendingRoll → diceExpr dari engine (anti-cheat), resolve sesuai type
 * 2. Tidak ada → free skill check dengan ability + proficiency
 *
 * Two-step flow:
 *   ATTACK/SPELL_ATTACK → roll attack → hit? set DAMAGE/SPELL_DAMAGE : clear
 *   DAMAGE/SPELL_DAMAGE → roll damage → apply → advance turn or next target
 */
public class RollCommand implements CommandExecutor, TabCompleter {

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

        PendingRoll pending = PendingRoll.get(player);

        if (args.length < 1) {
            if (pending != null) {
                player.sendMessage("§7Auto-detected: rolling " + pending.rollType.name().toLowerCase());
                executePendingRoll(player, pending);
                return true;
            }
            player.sendMessage("§7Gunakan: /roll <jenis_tindakan>. Contoh: /roll stealth");
            return true;
        }

        String rollType = args[0].toLowerCase();

        // Guard: kalau ada pending, harus match
        if (pending != null && !pending.rollType.matches(rollType)) {
            player.sendMessage("§cYang perlu di-roll sekarang: §e" + pending.rollType.getKey());
            player.sendMessage("§7Atau ketik §e/roll §7tanpa argumen untuk auto-detect.");
            return true;
        }

        if (pending != null) {
            executePendingRoll(player, pending);
        } else {
            executeFreeRoll(player, rollType);
        }
        return true;
    }

    // ==================== PENDING ROLL EXECUTION ====================

    private void executePendingRoll(Player player, PendingRoll pending) {
        // Roll the engine diceExpr (anti-cheat)
        int rollResult = DiceRoller.parseAndRoll(pending.diceExpr);

        // Animate
        String displayType = pending.rollType.name().toLowerCase().replace("_", " ");
        TitleDisplay.showRollResult(player,
                displayType + " " + pending.diceExpr, rollResult);

        pending.lastRollResult = rollResult;
        pending.resolved = true;

        TurnManager tm = plugin.getTurnManager();

        switch (pending.rollType) {
            case ATTACK -> resolveAttackRoll(player, rollResult, pending, tm);
            case DAMAGE -> resolveDamageRoll(player, rollResult, pending, tm);
            case SPELL_ATTACK -> resolveSpellAttack(player, rollResult, pending, tm);
            case SPELL_DAMAGE -> resolveSpellDamage(player, rollResult, pending, tm);
            case SAVING_THROW -> resolveSavingThrow(player, rollResult, pending, tm);
            case DEATH_SAVE -> resolveDeathSave(player, rollResult);
            default -> {
                PendingRoll.clear(player);
                checkQueue(player);
            }
        }
    }

    // --- ATTACK (physical combat) ---
    private void resolveAttackRoll(Player player, int rollResult, PendingRoll pending, TurnManager tm) {
        Map<String, String> ctx = pending.context;
        String targetId = ctx != null ? ctx.getOrDefault("targetId", "") : "";
        String targetName = ctx != null ? ctx.getOrDefault("targetName", "?") : "?";
        boolean melee = ctx != null && "melee".equalsIgnoreCase(ctx.getOrDefault("attackType", "melee"));

        // Cari target
        LivingEntity target = findTarget(tm, targetId);
        if (target == null || target.isDead()) {
            player.sendMessage("§cTarget sudah mati atau tidak ditemukan.");
            PendingRoll.clear(player);
            tm.resolveAction();
            return;
        }

        // Calculate hit
        int strMod = plugin.getPlayerDataStore().getAbilityModifier(player, "str");
        int dexMod = plugin.getPlayerDataStore().getAbilityModifier(player, "dex");
        int profBonus = CombatEngine.proficiencyBonus(plugin.getPlayerDataStore().getLevel(player));

        int atkBonus = melee ? (strMod + profBonus) : (dexMod + profBonus);
        int totalAttack = rollResult + atkBonus;

        int targetAC = target instanceof Player tp
                ? CombatEngine.calculatePlayerAC(plugin, tp)
                : CombatEngine.calculateMobAC(target);

        boolean crit = rollResult == 20;
        boolean hit = crit || totalAttack >= targetAC;

        if (!hit) {
            // MISS
            PendingRoll.clear(player);
            TitleDisplay.showCombatResult(player,
                    "§7Miss! " + totalAttack,
                    targetName + " §7[AC:" + targetAC + "]");
            Bukkit.broadcastMessage("§c[Combat]§r " + player.getName() + " menyerang "
                    + targetName + "! §7[d20:" + rollResult + "+" + atkBonus
                    + "=" + totalAttack + " vs AC:" + targetAC + "] §7MISS");
            tm.resolveAction();
            checkQueue(player);
            return;
        }

        // HIT → set DAMAGE pending
        String baseDmgDice = melee ? "1d8" : "1d6";
        int dmgBonus = melee ? strMod : dexMod;
        String dmgDice = baseDmgDice + "+" + dmgBonus;
        String finalDmgDice = crit ? DiceRoller.doubleDiceExpr(dmgDice) : dmgDice;

        CombatEngine.DamageType dmgType = melee
                ? CombatEngine.DamageType.SLASHING
                : CombatEngine.DamageType.PIERCING;

        PendingRoll dmgPr = new PendingRoll();
        dmgPr.rollType = PendingRoll.RollType.DAMAGE;
        dmgPr.diceExpr = finalDmgDice;
        dmgPr.context = new java.util.LinkedHashMap<>();
        dmgPr.context.put("targetId", targetId);
        dmgPr.context.put("targetName", targetName);
        dmgPr.context.put("damageType", dmgType.name());
        dmgPr.context.put("isCrit", String.valueOf(crit));
        PendingRoll.set(player, dmgPr);

        String critLabel = crit ? "§e§lCRIT! " : "§aHit! ";
        TitleDisplay.showCombatResult(player,
                critLabel + totalAttack,
                "Ketik §e/roll §r(" + finalDmgDice + " " + dmgType + ")");

        player.sendMessage(critLabel + "§7[d20:" + rollResult + "+" + atkBonus
                + "=" + totalAttack + " vs AC:" + targetAC + "]");
        player.sendMessage("§eKetik /roll untuk damage: " + finalDmgDice + " " + dmgType);

        Bukkit.broadcastMessage("§c[Combat]§r " + player.getName() + " menyerang "
                + targetName + "! §7[d20:" + rollResult + "+" + atkBonus
                + "=" + totalAttack + " vs AC:" + targetAC + "] "
                + (crit ? "§e§lCRIT!" : "§aHIT"));

        // Play sound
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.2f);
    }

    // --- DAMAGE (physical combat) ---
    private void resolveDamageRoll(Player player, int totalDamage, PendingRoll pending, TurnManager tm) {
        Map<String, String> ctx = pending.context;
        String targetId = ctx != null ? ctx.getOrDefault("targetId", "") : "";
        String targetName = ctx != null ? ctx.getOrDefault("targetName", "?") : "?";
        String dmgTypeStr = ctx != null ? ctx.getOrDefault("damageType", "SLASHING") : "SLASHING";
        boolean isCrit = ctx != null && "true".equalsIgnoreCase(ctx.getOrDefault("isCrit", "false"));

        CombatEngine.DamageType dmgType;
        try { dmgType = CombatEngine.DamageType.valueOf(dmgTypeStr); }
        catch (IllegalArgumentException e) { dmgType = CombatEngine.DamageType.SLASHING; }

        LivingEntity target = findTarget(tm, targetId);
        if (target == null || target.isDead()) {
            player.sendMessage("§cTarget sudah mati.");
            PendingRoll.clear(player);
            tm.resolveAction();
            checkQueue(player);
            return;
        }

        int actualDmg = CombatEngine.applyDamage(target, totalDamage, dmgType);

        String critLabel = isCrit ? "§e§lCRIT! " : "";
        TitleDisplay.showCombatResult(player,
                critLabel + "§c-" + actualDmg + " §7" + dmgType,
                targetName);

        Bukkit.broadcastMessage("§c[Combat]§r " + player.getName() + " → "
                + targetName + " §c-" + actualDmg + " " + dmgType
                + (isCrit ? " §e§lCRIT!" : "")
                + " §7[rolled:" + totalDamage + "]" );

        // Kill check
        if (target.getHealth() <= 0) {
            Bukkit.broadcastMessage("§c§l" + targetName + " has been slain!");
            tm.removeParticipant(target);
        }

        PendingRoll.clear(player);
        tm.resolveAction();
        checkQueue(player);
    }

    // --- SPELL ATTACK ---
    private void resolveSpellAttack(Player player, int rollResult, PendingRoll pending, TurnManager tm) {
        SpellEngine.ResolveResult r = SpellEngine.resolveSpellAttack(plugin, player, rollResult, pending, tm);

        if (!r.done && r.nextPrompt != null) {
            // Hit → damage pending set by SpellEngine
            player.sendMessage(r.nextPrompt);
        }

        if (r.done) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.0f);
            checkQueue(player);
        }
    }

    // --- SPELL DAMAGE ---
    private void resolveSpellDamage(Player player, int totalDamage, PendingRoll pending, TurnManager tm) {
        SpellEngine.ResolveResult r = SpellEngine.resolveSpellDamage(plugin, player, totalDamage, pending, tm);

        if (!r.done && r.nextPrompt != null) {
            // Multi-target: next target pending set by SpellEngine
            player.sendMessage(r.nextPrompt);
        }

        if (r.done) {
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 1.5f);
            checkQueue(player);
        }
    }

    // --- SAVING THROW ---
    private void resolveSavingThrow(Player player, int rollResult, PendingRoll pending, TurnManager tm) {
        Map<String, String> ctx = pending.context;
        String saveStat = ctx != null ? ctx.getOrDefault("saveStat", "CON") : "CON";
        int saveDC = ctx != null ? Integer.parseInt(ctx.getOrDefault("saveDC", "12")) : 12;
        String effect = ctx != null ? ctx.getOrDefault("effect", "an effect") : "an effect";

        int saveMod = plugin.getPlayerDataStore().getAbilityModifier(player, saveStat.toLowerCase());
        int total = rollResult + saveMod;
        boolean success = rollResult == 20 || total >= saveDC;

        PendingRoll.clear(player);

        String resultText;
        if (success) {
            resultText = "§aSaved! " + total + " vs DC " + saveDC;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.5f);
        } else {
            resultText = "§cFailed! " + total + " vs DC " + saveDC;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
        }

        Bukkit.broadcastMessage("§e[Saving Throw]§r " + player.getName()
                + " §7[" + saveStat.toUpperCase() + " d20:" + rollResult + "+" + saveMod
                + "=" + total + " vs DC" + saveDC + "] "
                + (success ? "§aSaved vs " + effect : "§cFailed vs " + effect));

        TitleDisplay.showCombatResult(player, resultText, saveStat.toUpperCase() + " save vs " + effect);

        checkQueue(player);
    }

    // --- DEATH SAVE ---
    private void resolveDeathSave(Player player, int rollResult) {
        PendingRoll.clear(player);

        String resultText;
        Sound sound;
        float pitch;

        if (rollResult == 20) {
            resultText = "§a§lNAT 20! Regain 1 HP";
            sound = Sound.BLOCK_BELL_RESONATE;
            pitch = 1.5f;
            player.setHealth(1.0);
        } else if (rollResult == 1) {
            resultText = "§c§lNAT 1! Two failures";
            sound = Sound.BLOCK_NOTE_BLOCK_BASS;
            pitch = 0.5f;
        } else if (rollResult >= 10) {
            resultText = "§aSuccess! (" + rollResult + ")";
            sound = Sound.BLOCK_NOTE_BLOCK_CHIME;
            pitch = 1.2f;
        } else {
            resultText = "§cFailure! (" + rollResult + ")";
            sound = Sound.BLOCK_NOTE_BLOCK_BASS;
            pitch = 0.8f;
        }

        TitleDisplay.showCombatResult(player, resultText, "Death Save");
        player.playSound(player.getLocation(), sound, 0.6f, pitch);
        Bukkit.broadcastMessage("§8[Death Save]§r " + player.getName()
                + " rolled " + rollResult + " — " + resultText);
    }

    // ==================== QUEUE ====================

    private void checkQueue(Player player) {
        PendingRoll next = PendingRoll.advanceQueue(player);
        if (next != null) {
            int remaining = PendingRoll.getQueueSize(player);
            player.sendMessage("§6⚠ Queue: §e/roll §6selanjutnya menunggu!" + (remaining > 0 ? " §7(" + remaining + " lagi)" : ""));
            if (next.promptMsg != null) {
                player.sendMessage(next.promptMsg);
            } else {
                player.sendMessage("§eKetik /roll (" + next.rollType.getKey() + ": " + next.diceExpr + ")");
            }
        }
    }

    // ==================== FREE ROLL (no pending) ====================

    private void executeFreeRoll(Player player, String rollType) {
        String ability = ROLL_TYPE_ABILITY.getOrDefault(rollType, "");
        int modifier = 0;
        if (!ability.isEmpty()) {
            modifier = plugin.getPlayerDataStore().getAbilityModifier(player, ability);
        }
        if (!ability.isEmpty() && !rollType.equals("initiative") && !rollType.equals("attack")
                && !rollType.equals("death_save")) {
            CharacterClass charClass = CharacterClass.fromName(
                    plugin.getPlayerDataStore().getCharacterClass(player));
            if (charClass != null && charClass.hasSkillOption(rollType)) {
                List<String> options = charClass.getSkillOptions();
                if (options.indexOf(rollType.toLowerCase()) >= 0 && options.indexOf(rollType.toLowerCase()) < 2) {
                    modifier += CombatEngine.proficiencyBonus(plugin.getPlayerDataStore().getLevel(player));
                }
            }
        }
        int roll = DiceRoller.roll(20);
        int total = roll + modifier;
        TitleDisplay.showRollAnimation(plugin, player, formatRollType(rollType), modifier, roll, total);
    }

    private String formatRollType(String rollType) {
        return rollType.replace("_", " ");
    }

    // ==================== HELPERS ====================

    private LivingEntity findTarget(TurnManager tm, String idPrefix) {
        for (var entry : tm.getParticipants().entrySet()) {
            String idStr = entry.getKey().toString().substring(0, 8);
            if (idStr.equalsIgnoreCase(idPrefix) || idStr.toLowerCase().startsWith(idPrefix.toLowerCase())) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                    @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (String type : ROLL_TYPES) {
                if (type.startsWith(input)) result.add(type);
            }
            return result;
        }
        return List.of();
    }
}
