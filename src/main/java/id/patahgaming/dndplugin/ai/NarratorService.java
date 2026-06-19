package id.patahgaming.dndplugin.ai;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.ai.NarratorContext.TriggerType;
import id.patahgaming.dndplugin.ai.NarratorContext.Priority;
import id.patahgaming.dndplugin.ai.NarratorContext.Recipient;
import id.patahgaming.dndplugin.data.PoiStore;
import id.patahgaming.dndplugin.data.StoryTreeStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * NarratorService v2 — Unified Narration Layer.
 *
 * Single entry point untuk semua narasi. Semua komponen lain (combat engine,
 * NPC dialogue, POI tick, command handler) tidak boleh langsung kirim chat ke
 * player — semuanya lewat sini.
 *
 * Fitur:
 * - Template prompt per TriggerType
 * - Priority queue + debounce (P0 immediate, P1 500ms, P2 1s)
 * - Fallback hardcoded template kalau DeepSeek timeout (8s)
 * - mechanicResult immutable — AI gak boleh ubah angka
 */
public class NarratorService {

    private final DndPlugin plugin;
    private final Queue<NarratorContext> queue = new ConcurrentLinkedQueue<>();
    private boolean processing = false;
    private int scheduledTaskId = -1;

    public NarratorService(DndPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== PUBLIC API ====================

    /** Kirim narasi ke queue. P0 langsung, P1/P2 di-debounce. */
    public void narrate(NarratorContext ctx) {
        if (ctx.priority == Priority.P0_IMMEDIATE) {
            // Bypass queue — kirim instan
            executeNarration(ctx);
            return;
        }
        queue.add(ctx);
        scheduleProcessing();
    }

    /** On-rail narration (backward compat). */
    public void narrateOnRail(StoryTreeStore.StoryNode node, Player triggerPlayer) {
        NarratorContext ctx = new NarratorContext(TriggerType.POI_ON_RAIL, "Party")
                .withRecipient(Recipient.PARTY)
                .addExtra("narrative", node.narrative)
                .addExtra("currentNode", node.nodeId)
                .addExtra("recent_events", String.join("; ",
                        plugin.getPartySessionStore().getEvents()));
        if (triggerPlayer != null) ctx.addExtra("triggerPlayer", triggerPlayer.getName());
        narrate(ctx);
    }

    /** Off-rail narration (backward compat). */
    public void narrateOffRail(Player triggerPlayer) {
        NarratorContext ctx = new NarratorContext(TriggerType.POI_OFF_RAIL, "Party")
                .withRecipient(Recipient.PARTY)
                .addExtra("recent_events", String.join("; ",
                        plugin.getPartySessionStore().getEvents()));
        if (triggerPlayer != null) ctx.addExtra("triggerPlayer", triggerPlayer.getName());
        narrate(ctx);
    }

    // ==================== QUEUE & DEBOUNCE ====================

    private void scheduleProcessing() {
        if (processing) return; // udah ada yang lagi jalan
        if (scheduledTaskId != -1) return; // udah dijadwalkan

        int delayTicks = 10; // 500ms default (P1)
        // Kalau semua item P2, bisa delay lebih lama
        boolean allP2 = queue.stream().allMatch(c -> c.priority == Priority.P2_DEFERRABLE);
        if (allP2) delayTicks = 20; // 1 detik

        scheduledTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scheduledTaskId = -1;
            processQueue();
        }, delayTicks).getTaskId();
    }

    private void processQueue() {
        processing = true;
        try {
            // Gabungin semua item di queue jadi 1 batch
            List<NarratorContext> batch = new ArrayList<>();
            NarratorContext ctx;
            while ((ctx = queue.poll()) != null) {
                batch.add(ctx);
            }
            if (batch.isEmpty()) return;

            if (batch.size() == 1) {
                executeNarration(batch.get(0));
            } else {
                executeBatchNarration(batch);
            }
        } finally {
            processing = false;
            // Kalau ada item baru yang datang selama processing, schedule lagi
            if (!queue.isEmpty()) scheduleProcessing();
        }
    }

    // ==================== AI EXECUTION ====================

    private void executeNarration(NarratorContext ctx) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String prompt = buildPrompt(ctx);
                String systemPrompt = buildSystemPrompt();
                String response = plugin.getDeepSeekClient().complete(systemPrompt, prompt);

                // Parse NARASI + ADVANCE dari response AI
                String narasi = extractField(response, "NARASI");
                String advance = extractField(response, "ADVANCE");
                if (narasi.isEmpty()) narasi = response;

                final String finalNarasi = narasi;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    route(ctx, finalNarasi);
                    // AUTO mode: pilihkan next node
                    if (plugin.getPartySessionStore().isAutoMode()
                            && advance != null && !advance.isEmpty()
                            && !"NONE".equalsIgnoreCase(advance)) {
                        var node = plugin.getStoryTreeStore().get(advance);
                        if (node != null) {
                            plugin.getPlayerDataStore().setPartyCurrentNode(advance);
                            plugin.getPartySessionStore().setCurrentNode(advance);
                            plugin.getLogger().info("[Narrator] Auto-advanced: " + advance);
                        }
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Narrator failed for " + ctx.triggerType + ": " + e.getMessage());
                // Fallback
                String fallback = ctx.fallbackTemplate != null
                        ? injectVars(ctx.fallbackTemplate, ctx) : null;
                if (fallback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> routePlain(ctx, fallback));
                }
            }
        });
    }

    private void executeBatchNarration(List<NarratorContext> batch) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("Several events happened in quick succession. Narrate them as a flowing scene (3-4 sentences).\n\n");
                for (int i = 0; i < batch.size(); i++) {
                    NarratorContext ctx = batch.get(i);
                    sb.append("Event ").append(i + 1).append(": ");
                    sb.append(describeEvent(ctx)).append("\n");
                    for (var e : ctx.mechanicResult.entrySet()) {
                        sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                    }
                }
                sb.append("\nNarrate these events together naturally. Do NOT list them. Do NOT include meta-commentary.");

                String response = plugin.getDeepSeekClient().complete(buildSystemPrompt(), sb.toString());

                // Parse NARASI dari batch response
                String batchNarasi = extractField(response, "NARASI");
                if (batchNarasi.isEmpty()) batchNarasi = response;
                final String finalBatchNarasi = batchNarasi;

                // Broadcast to PARTY (default for batches)
                Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage("§6[Narrator]§r " + finalBatchNarasi)));

            } catch (Exception e) {
                // Fallback: kirim per event
                for (NarratorContext ctx : batch) {
                    String fb = ctx.fallbackTemplate != null ? injectVars(ctx.fallbackTemplate, ctx) : describeEvent(ctx);
                    Bukkit.getScheduler().runTask(plugin, () -> routePlain(ctx, fb));
                }
            }
        });
    }

    // ==================== PROMPT BUILDING ====================

    private String buildSystemPrompt() {
        boolean isAuto = plugin.getPartySessionStore().getMode()
                == id.patahgaming.dndplugin.model.NarratorMode.AUTO;

        return """
                Kamu adalah narator DnD untuk sesi permainan dalam Minecraft.
                Gunakan bahasa Indonesia yang natural dan immersive.
                Istilah fantasy/DnD dalam bahasa Inggris TIDAK diterjemahkan
                (Sneak Attack, Spell Slot, Saving Throw, dll).

                Aturan narasi:
                - Untuk event biasa: 1-2 kalimat
                - Untuk momen penting (boss, plot twist): hingga 4 kalimat
                - JANGAN sebut koordinat atau istilah Minecraft (block, chunk)
                - Sebut pemain dengan nama karakter mereka, bukan username
                - Stay in character — jangan break the fourth wall
                - ANGKA yang diberikan bersifat FINAL — jangan diubah atau dibulatkan

                %s

                Jawab dalam format:
                NARASI: <teks narasi>
                ADVANCE: <%s>
                """.formatted(
                    isAuto
                        ? "Jika diberikan narrative_template, ikuti alurnya."
                            + " Jika TIDAK ada template, narasikan secara bebas berdasarkan konteks."
                        : "Narasikan berdasarkan konteks yang diberikan.",
                    isAuto ? "next_node_id, atau NONE" : "NONE"
                );
    }

    private String buildPrompt(NarratorContext ctx) {
        return switch (ctx.triggerType) {
            case COMBAT_ATTACK_HIT -> templateAttackHit(ctx);
            case COMBAT_ATTACK_MISS -> templateAttackMiss(ctx);
            case COMBAT_ATTACK_CRIT -> templateAttackCrit(ctx);
            case COMBAT_CAST_SPELL -> templateCastSpell(ctx);
            case COMBAT_KILL -> templateKill(ctx);
            case COMBAT_WIN -> templateCombatWin(ctx);
            case COMBAT_LOSE -> templateCombatLose(ctx);
            case COMBAT_START -> templateCombatStart(ctx);
            case COMBAT_DAMAGE_RECEIVED -> templateDamageReceived(ctx);
            case DEATH_SAVE_SUCCESS -> templateDeathSave(ctx, "success");
            case DEATH_SAVE_FAIL -> templateDeathSave(ctx, "fail");
            case DEATH_SAVE_STABILIZE -> templateDeathSave(ctx, "stabilized");
            case DEATH_SAVE_DEAD -> templateDeathSave(ctx, "dead");
            case SAVING_THROW_SUCCESS -> templateSavingThrow(ctx, true);
            case SAVING_THROW_FAIL -> templateSavingThrow(ctx, false);
            case SKILL_CHECK_SUCCESS, SKILL_CHECK_FAIL -> templateSkillCheck(ctx);
            case NPC_DIALOG_START -> templateNpcDialogStart(ctx);
            case NPC_DIALOG_END -> templateNpcDialogEnd(ctx);
            case NPC_DIALOG_AGRO -> templateNpcDialogAgro(ctx);
            case CHARACTER_LEVEL_UP -> templateLevelUp(ctx);
            case POI_ON_RAIL -> templateOnRail(ctx);
            case POI_OFF_RAIL -> templateOffRail(ctx);
            default -> templateGeneric(ctx);
        };
    }

    // ==================== TRIGGER TEMPLATES ====================

    private String templateAttackHit(NarratorContext ctx) {
        String dmg = ctx.mechanicResult.getOrDefault("damage", "?");
        String type = ctx.mechanicResult.getOrDefault("damageType", "?");
        String roll = ctx.mechanicResult.getOrDefault("roll", "?");
        return String.format(
                "%s attacks %s. Attack roll: %s. Damage: %s (%s). Narrate this hit in 2 sentences, dramatic, past tense.%s",
                ctx.subject, ctx.target, roll, dmg, type,
                ctx.fallbackTemplate != null ? "" : setFallback(ctx, String.format("%s hits %s for %s %s damage!", ctx.subject, ctx.target, dmg, type)));
    }

    private String templateAttackMiss(NarratorContext ctx) {
        String roll = ctx.mechanicResult.getOrDefault("roll", "?");
        return String.format(
                "%s attacks %s but misses (roll: %s). Narrate the miss in 1 sentence, past tense.%s",
                ctx.subject, ctx.target, roll,
                setFallback(ctx, String.format("%s swings at %s but misses!", ctx.subject, ctx.target)));
    }

    private String templateAttackCrit(NarratorContext ctx) {
        String dmg = ctx.mechanicResult.getOrDefault("damage", "?");
        return String.format(
                "%s lands a CRITICAL HIT on %s! Damage: %s. Narrate this critical hit in 2 dramatic sentences.%s",
                ctx.subject, ctx.target, dmg,
                setFallback(ctx, String.format("CRITICAL HIT! %s devastates %s for %s damage!", ctx.subject, ctx.target, dmg)));
    }

    private String templateCastSpell(NarratorContext ctx) {
        String spell = ctx.mechanicResult.getOrDefault("spell", "?");
        String dmg = ctx.mechanicResult.getOrDefault("damage", "0");
        String type = ctx.mechanicResult.getOrDefault("damageType", "force");
        String phase = ctx.phase != null ? ctx.phase : "RESOLVE";

        if ("CAST".equals(phase)) {
            return String.format(
                    "%s begins casting %s. Narrate the spellcasting buildup in 1 sentence with magical flavor text. Do NOT describe the outcome yet.%s",
                    ctx.subject, spell,
                    setFallback(ctx, String.format("%s begins casting %s...", ctx.subject, spell)));
        }

        // RESOLVE phase
        String targets = ctx.mechanicResult.getOrDefault("targetResults", "");
        if (!targets.isEmpty()) {
            return String.format(
                    "%s cast %s. Results: %s. Damage type: %s. Narrate the spell outcome in 2-3 sentences, dramatic, past tense. Mention each target's fate.%s",
                    ctx.subject, spell, targets, type,
                    setFallback(ctx, String.format("%s casts %s! %s", ctx.subject, spell, targets)));
        }

        return String.format(
                "%s casts %s on %s. Damage: %s (%s). Narrate the spellcasting in 2 sentences with magical flavor text.%s",
                ctx.subject, spell, ctx.target, dmg, type,
                setFallback(ctx, String.format("%s casts %s! %s takes %s %s damage.", ctx.subject, spell, ctx.target, dmg, type)));
    }

    private String templateKill(NarratorContext ctx) {
        return String.format(
                "%s has slain %s! Narrate the killing blow in 1-2 dramatic sentences.%s",
                ctx.subject, ctx.target,
                setFallback(ctx, String.format("%s has been slain by %s!", ctx.target, ctx.subject)));
    }

    private String templateCombatWin(NarratorContext ctx) {
        String enemies = ctx.mechanicResult.getOrDefault("enemiesDefeated", "?");
        return String.format(
                "The party has won the battle! Enemies defeated: %s. Narrate the victory in 2 sentences.%s",
                enemies,
                setFallback(ctx, "Victory! All enemies have been defeated."));
    }

    private String templateCombatLose(NarratorContext ctx) {
        return String.format(
                "The party has been defeated... Narrate this tragic moment in 2 somber sentences. Do not mention mechanics.%s",
                setFallback(ctx, "The party has fallen..."));
    }

    private String templateCombatStart(NarratorContext ctx) {
        String count = ctx.mechanicResult.getOrDefault("enemyCount", "?");
        return String.format(
                "Battle begins! %s enemies stand against the party. Narrate the battle opening in 1-2 sentences.%s",
                count,
                setFallback(ctx, "Battle begins! Roll for initiative!"));
    }

    private String templateDamageReceived(NarratorContext ctx) {
        String dmg = ctx.mechanicResult.getOrDefault("damage", "?");
        return String.format(
                "%s attacks %s dealing %s damage. Narrate in 1 sentence from the target's perspective.%s",
                ctx.target != null ? ctx.target : "An enemy", ctx.subject, dmg,
                setFallback(ctx, String.format("%s takes %s damage from %s!", ctx.subject, dmg, ctx.target)));
    }

    private String templateDeathSave(NarratorContext ctx, String outcome) {
        String roll = ctx.mechanicResult.getOrDefault("roll", "?");
        String count = ctx.mechanicResult.getOrDefault("count", "?");
        return String.format(
                "%s is making death saves. Roll: %s (%s, count: %s). Narrate in 1 sentence. Do NOT say 'death save' literally.%s",
                ctx.subject, roll, outcome, count,
                setFallback(ctx, outcome.equals("stabilized") ? ctx.subject + " stabilizes!" :
                        outcome.equals("dead") ? ctx.subject + " has died..." :
                        ctx.subject + " struggles... (" + count + ")"));
    }

    private String templateSavingThrow(NarratorContext ctx, boolean success) {
        String roll = ctx.mechanicResult.getOrDefault("roll", "?");
        String dc = ctx.mechanicResult.getOrDefault("dc", "?");
        String stat = ctx.mechanicResult.getOrDefault("stat", "CON");
        String effect = ctx.extraData.getOrDefault("effect", "an effect");
        return String.format(
                "%s %s a %s saving throw (roll: %s vs DC %s) against %s. Narrate in 1 sentence.%s",
                ctx.subject, success ? "succeeds" : "fails", stat, roll, dc, effect,
                setFallback(ctx, success ? ctx.subject + " resists " + effect + "!" :
                        ctx.subject + " succumbs to " + effect + "!"));
    }

    private String templateSkillCheck(NarratorContext ctx) {
        String skill = ctx.mechanicResult.getOrDefault("skill", "?");
        String roll = ctx.mechanicResult.getOrDefault("roll", "?");
        String success = ctx.triggerType == TriggerType.SKILL_CHECK_SUCCESS ? "success" : "failure";
        return String.format(
                "%s attempts a %s check (roll: %s, %s). Narrate the outcome in 1 sentence.%s",
                ctx.subject, skill, roll, success,
                setFallback(ctx, ctx.subject + " " + success + " on " + skill + " check (roll: " + roll + ")"));
    }

    private String templateNpcDialogStart(NarratorContext ctx) {
        String npc = ctx.extraData.getOrDefault("npcName", ctx.target != null ? ctx.target : "someone");
        return String.format(
                "%s begins a conversation with %s. Narrate this moment briefly. %s",
                ctx.subject, npc,
                setFallback(ctx, ctx.subject + " approaches " + npc + "."));
    }

    private String templateNpcDialogEnd(NarratorContext ctx) {
        String npc = ctx.extraData.getOrDefault("npcName", ctx.target != null ? ctx.target : "someone");
        return String.format(
                "The conversation between %s and %s has ended. Narrate the departure. %s",
                ctx.subject, npc,
                setFallback(ctx, ctx.subject + " finishes talking with " + npc + "."));
    }

    private String templateNpcDialogAgro(NarratorContext ctx) {
        String npc = ctx.extraData.getOrDefault("npcName", ctx.target);
        return String.format(
                "%s becomes hostile toward %s! Narrate in 1 threatening sentence.%s",
                npc, ctx.subject,
                setFallback(ctx, npc + " becomes hostile!"));
    }

    private String templateLevelUp(NarratorContext ctx) {
        String lvl = ctx.mechanicResult.getOrDefault("level", "2");
        return String.format(
                "%s has leveled up to level %s! Narrate in 1-2 sentences of personal growth.%s",
                ctx.subject, lvl,
                setFallback(ctx, ctx.subject + " reaches level " + lvl + "!"));
    }

    private String templateOnRail(NarratorContext ctx) {
        String narrative = ctx.extraData.getOrDefault("narrative", "");
        String currentNode = ctx.extraData.getOrDefault("currentNode", "?");
        String events = ctx.extraData.getOrDefault("recent_events", "");
        return String.format(
                "Story node: %s. Template: %s. Recent events: %s. %s",
                currentNode, narrative, events,
                setFallback(ctx, narrative.isEmpty() ? "Party arrives at " + currentNode + "." : narrative));
    }

    private String templateOffRail(NarratorContext ctx) {
        String events = ctx.extraData.getOrDefault("recent_events", "");
        return String.format(
                "Party is exploring freely (no active story node). Recent events: %s. %s",
                events,
                setFallback(ctx, "The party continues their journey."));
    }

    private String templateGeneric(NarratorContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Event: ").append(ctx.triggerType).append("\n");
        sb.append("Subject: ").append(ctx.subject).append("\n");
        if (ctx.target != null) sb.append("Target: ").append(ctx.target).append("\n");
        for (var e : ctx.mechanicResult.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }
        sb.append("Narrate in 1-2 sentences.");
        return sb.toString();
    }

    // ==================== HELPERS ====================

    private String setFallback(NarratorContext ctx, String fb) {
        if (ctx.fallbackTemplate == null) ctx.fallbackTemplate = fb;
        return "";
    }

    /** Extract field value from "FIELD: value" format. Handles multi-line values. */
    private String extractField(String response, String field) {
        if (response == null) return "";
        String[] lines = response.split("\n");
        StringBuilder value = new StringBuilder();
        boolean found = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.toUpperCase().startsWith(field.toUpperCase() + ":")) {
                value.append(line.substring(line.indexOf(':') + 1).trim());
                found = true;
                // Collect continuation lines until next FIELD: or end
                for (int j = i + 1; j < lines.length; j++) {
                    String next = lines[j].trim();
                    if (next.isEmpty()) continue;
                    // Stop at next FIELD: marker
                    if (next.matches("^[A-Z_]+:.*")) break;
                    value.append(" ").append(next.trim());
                }
                break;
            }
        }
        return found ? value.toString().trim() : "";
    }

    private String describeEvent(NarratorContext ctx) {
        StringBuilder sb = new StringBuilder(ctx.triggerType + ": " + ctx.subject);
        if (ctx.target != null) sb.append(" → ").append(ctx.target);
        for (var e : ctx.mechanicResult.entrySet()) {
            sb.append(" | ").append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    private String injectVars(String template, NarratorContext ctx) {
        return template
                .replace("{subject}", ctx.subject != null ? ctx.subject : "?")
                .replace("{target}", ctx.target != null ? ctx.target : "?");
    }

    // ==================== ROUTING ====================

    private void route(NarratorContext ctx, String narration) {
        String formatted = "§6[Narrator]§r " + narration;
        routePlain(ctx, formatted);

        // Special: death saves — SELF + PARTY dapet narasi berbeda
        if (ctx.triggerType.name().startsWith("DEATH_SAVE") && ctx.recipient == Recipient.PARTY) {
            Player subject = Bukkit.getPlayer(ctx.subject);
            if (subject != null && subject.isOnline()) {
                subject.sendMessage("§7[You] " + narration);
            }
        }
    }

    private void routePlain(NarratorContext ctx, String message) {
        switch (ctx.recipient) {
            case SELF -> {
                Player p = Bukkit.getPlayerExact(ctx.subject);
                if (p != null && p.isOnline()) p.sendMessage(message);
            }
            case PARTY -> Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(message));
            case BROADCAST -> Bukkit.broadcastMessage(message);
        }
    }
}
