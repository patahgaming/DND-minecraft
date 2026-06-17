package id.patahgaming.dndplugin.ai;

import java.util.*;

/**
 * Payload standard untuk semua narasi.
 * mechanicResult TIDAK BOLEH diubah AI — AI hanya bungkus dalam narasi dramatis.
 */
public class NarratorContext {

    public enum TriggerType {
        // POI / Story
        POI_ON_RAIL, POI_OFF_RAIL,

        // Combat
        COMBAT_START, COMBAT_ATTACK_HIT, COMBAT_ATTACK_MISS, COMBAT_ATTACK_CRIT,
        COMBAT_CAST_SPELL, COMBAT_KILL, COMBAT_WIN, COMBAT_LOSE, COMBAT_FLEE,
        COMBAT_DAMAGE_RECEIVED,

        // Death saves
        DEATH_SAVE_SUCCESS, DEATH_SAVE_FAIL, DEATH_SAVE_STABILIZE, DEATH_SAVE_DEAD,

        // Saving throws
        SAVING_THROW_SUCCESS, SAVING_THROW_FAIL,

        // Skills & items
        SKILL_CHECK_SUCCESS, SKILL_CHECK_FAIL, ITEM_USE,

        // NPC
        NPC_DIALOG_START, NPC_DIALOG_END, NPC_DIALOG_AGRO,

        // Character
        CHARACTER_LEVEL_UP
    }

    public enum Priority {
        P0_IMMEDIATE,  // bypass queue, kirim instan (DEATH_SAVE_DEAD, COMBAT_LOSE)
        P1_QUEUE,      // antre 500ms debounce (COMBAT_ATTACK_*, DEATH_SAVE_*)
        P2_DEFERRABLE  // debounce 1 detik (POI, SKILL_CHECK, NPC_DIALOG)
    }

    public enum Recipient {
        SELF, PARTY, BROADCAST
    }

    public TriggerType triggerType;
    public Priority priority = Priority.P1_QUEUE;
    public String subject;
    public String target;
    public Map<String, String> mechanicResult = new LinkedHashMap<>(); // key=value pairs, immutable by AI
    public Map<String, String> extraData = new LinkedHashMap<>();
    public Recipient recipient = Recipient.PARTY;
    public String phase; // "CAST" or "RESOLVE" for multi-phase events (spells)
    public String fallbackTemplate; // hardcoded template kalau DeepSeek timeout

    public NarratorContext(TriggerType type, String subject) {
        this.triggerType = type;
        this.subject = subject;
        this.priority = priorityFor(type);
        this.recipient = defaultRecipient(type);
    }

    public NarratorContext addResult(String key, String value) {
        mechanicResult.put(key, value);
        return this;
    }

    public NarratorContext addExtra(String key, String value) {
        extraData.put(key, value);
        return this;
    }

    public NarratorContext withTarget(String target) {
        this.target = target;
        return this;
    }

    public NarratorContext withRecipient(Recipient r) {
        this.recipient = r;
        return this;
    }

    public NarratorContext withFallback(String template) {
        this.fallbackTemplate = template;
        return this;
    }

    public NarratorContext withPhase(String phase) {
        this.phase = phase;
        return this;
    }

    /** Default priority per trigger type. */
    public static Priority priorityFor(TriggerType type) {
        return switch (type) {
            case DEATH_SAVE_DEAD, COMBAT_LOSE -> Priority.P0_IMMEDIATE;
            case COMBAT_ATTACK_HIT, COMBAT_ATTACK_MISS, COMBAT_ATTACK_CRIT,
                 COMBAT_CAST_SPELL, COMBAT_KILL, COMBAT_WIN, COMBAT_FLEE,
                 COMBAT_DAMAGE_RECEIVED, DEATH_SAVE_SUCCESS, DEATH_SAVE_FAIL,
                 DEATH_SAVE_STABILIZE, SAVING_THROW_SUCCESS, SAVING_THROW_FAIL,
                 NPC_DIALOG_AGRO, CHARACTER_LEVEL_UP -> Priority.P1_QUEUE;
            default -> Priority.P2_DEFERRABLE;
        };
    }

    /** Default recipient per trigger type. */
    public static Recipient defaultRecipient(TriggerType type) {
        return switch (type) {
            case SKILL_CHECK_SUCCESS, SKILL_CHECK_FAIL, ITEM_USE,
                 NPC_DIALOG_START, NPC_DIALOG_END -> Recipient.SELF;
            case COMBAT_WIN, COMBAT_LOSE -> Recipient.BROADCAST;
            default -> Recipient.PARTY;
        };
    }
}
