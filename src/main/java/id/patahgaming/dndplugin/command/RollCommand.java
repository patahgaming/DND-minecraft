package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.CharacterClass;
import id.patahgaming.dndplugin.dice.DiceItemFactory;
import id.patahgaming.dndplugin.dice.DiceType;
import id.patahgaming.dndplugin.dice.DiceValidator;
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

        // /roll <skill> — skill check bypass validasi dadu (pakai D20 virtual)
        if (args.length >= 1 && ROLL_TYPE_ABILITY.containsKey(args[0].toLowerCase())) {
            String rollType = args[0].toLowerCase();
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

        // /roll (no args) — butuh dadu fisik
        if (args.length < 1) {
            if (pending != null) {
                // Combat roll — validasi dadu
                executePendingRoll(player, pending);
                return true;
            }
            // Free roll — butuh dadu di tangan
            freeDiceRoll(player);
            return true;
        }

        // /roll dengan argumen bukan skill → tolak
        player.sendMessage("§7Gunakan: §e/roll §7atau §e/roll <skill>");
        player.sendMessage("§7Skill: athletics acrobatics stealth perception investigation insight persuasion deception intimidation sleight_of_hand survival initiative");
        return true;
    }

    /** Free roll dengan dadu di tangan (tanpa PendingRoll). Broadcast ke semua. */
    private void freeDiceRoll(Player player) {
        DiceType dice = DiceItemFactory.getDiceType(player.getInventory().getItemInMainHand());
        if (dice == null) {
            player.sendMessage("§c⚠ Pegang dadu dulu untuk free roll.");
            player.sendMessage("§7Atau ketik §f/roll <skill>§7 untuk skill check.");
            return;
        }
        int roll = DiceRoller.roll(dice.getSides());
        player.sendTitle(dice.getColor() + "§l🎲 " + roll, "§7" + dice.getLabel() + " free roll", 5, 50, 20);
        Bukkit.broadcastMessage("§7🎲 " + dice.getColor() + player.getName()
                + " §7rolls " + dice.getLabel() + ": " + dice.getColor() + "§l" + roll);
        Bukkit.getOnlinePlayers().forEach(p ->
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.0f));
    }

    // ==================== PENDING ROLL EXECUTION ====================

    /** Public — dipanggil dari DiceBlockListener (dadu fisik). */
    public void executePendingRoll(Player player, PendingRoll pending) {
        // Validasi dadu — cek main hand cocok dengan diceExpr
        DiceType required = DiceType.fromDiceExpr(pending.diceExpr);
        if (required != null) {
            DiceValidator.ValidationResult v = DiceValidator.validate(player, required);
            if (!v.valid) {
                player.sendMessage(v.message);
                if (v.wasGiven) {
                    player.sendMessage("§eKetik §f/roll §elagi setelah pegang dadunya!");
                }
                player.sendTitle("§c⚠ Ganti Dadu!", "§7Butuh " + required.getColor() + required.getLabel()
                        + " §7(" + required.getColorName() + " Wool)", 5, 50, 10);
                return; // STOP — jangan resolve
            }
        }

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

        // Re-check jarak (player mungkin udah pindah sejak /atk)
        double dist = player.getLocation().distance(target.getLocation());
        double maxDist = melee ? 3.5 : 30.0;
        if (dist > maxDist) {
            player.sendMessage("§cTerlalu jauh! Target udah bergerak menjauh. §7(jarak: " + String.format("%.1f", dist) + ")");
            PendingRoll.clear(player);
            tm.resolveAction();
            checkQueue(player);
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
        // Read weapon stats dari context (diisi AtkCommand/WeaponAttackListener)
        String weaponDice = ctx.getOrDefault("weaponDice", melee ? "1d8" : "1d6");
        int weaponBonus = Integer.parseInt(ctx.getOrDefault("weaponBonus", "0"));
        String weaponDmgType = ctx.getOrDefault("weaponDmgType", melee ? "SLASHING" : "PIERCING");
        int dmgBonus = weaponBonus + (melee ? strMod : dexMod);
        String dmgDice = weaponDice + "+" + dmgBonus;
        String finalDmgDice = crit ? DiceRoller.doubleDiceExpr(dmgDice) : dmgDice;

        CombatEngine.DamageType dmgType;
        try { dmgType = CombatEngine.DamageType.valueOf(weaponDmgType); }
        catch (IllegalArgumentException e) { dmgType = CombatEngine.DamageType.SLASHING; }

        PendingRoll dmgPr = new PendingRoll();
        dmgPr.rollType = PendingRoll.RollType.DAMAGE;
        dmgPr.diceExpr = finalDmgDice;
        dmgPr.context = new java.util.LinkedHashMap<>();
        dmgPr.context.put("targetId", targetId);
        dmgPr.context.put("targetName", targetName);
        dmgPr.context.put("damageType", dmgType.name());
        dmgPr.context.put("isCrit", String.valueOf(crit));
        dmgPr.context.put("weaponEffect", ctx.getOrDefault("weaponEffect", "NONE"));
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

        // PATCH: DamageResult — handle UNCONSCIOUS vs DEAD
        CombatEngine.DamageResult dmgResult = CombatEngine.applyDamage(target, totalDamage, dmgType);
        int actualDmg = dmgResult.finalDamage;

        String critLabel = isCrit ? "§e§lCRIT! " : "";
        TitleDisplay.showCombatResult(player,
                critLabel + "§c-" + actualDmg + " §7" + dmgType,
                targetName);

        Bukkit.broadcastMessage("§c[Combat]§r " + player.getName() + " → "
                + targetName + " §c-" + actualDmg + " " + dmgType
                + (isCrit ? " §e§lCRIT!" : "")
                + " §7[rolled:" + totalDamage + "]" );

        // Handle outcome
        switch (dmgResult.outcome) {
            case UNCONSCIOUS -> {
                if (target instanceof org.bukkit.entity.Player tp) {
                    plugin.getDeathSaveManager().makeUnconscious(tp);
                }
            }
            case DEAD -> {
                Bukkit.broadcastMessage("§c§l" + targetName + " has been slain!");
                tm.removeParticipant(target);
            }
            case ALIVE -> { /* nothing */ }
        }

        // Trigger weapon special effect (FIRE_ON_HIT, etc.)
        String effectStr = ctx.getOrDefault("weaponEffect", "NONE");
        if (!effectStr.equals("NONE") && !effectStr.equals("null")) {
            try {
                id.patahgaming.dndplugin.item.SpecialEffect effect =
                        id.patahgaming.dndplugin.item.SpecialEffect.valueOf(effectStr);
                if (effect != id.patahgaming.dndplugin.item.SpecialEffect.NONE) {
                    effect.onHit(player, target, actualDmg);
                }
            } catch (IllegalArgumentException ignored) {}
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
        // Delegate ke TurnManager → DeathSaveManager.processRoll()
        // DeathSaveManager yang handle dice roll (DiceRoller.roll(20)),
        // update success/fail count, stabilize/kill decision.
        // rollResult dari pending sudah tidak dipakai — DeathSaveManager roll ulang.
        PendingRoll.clear(player);

        TurnManager tm = plugin.getTurnManager();
        tm.resolveDeathSave(player);
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
