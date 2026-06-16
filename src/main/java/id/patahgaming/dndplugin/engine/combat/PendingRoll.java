package id.patahgaming.dndplugin.engine.combat;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * PendingRoll — satu player, satu pending.
 * Disimpan di PDC biar survive server hiccup.
 *
 * RollType enum cocok untuk auto-detect di /roll:
 *   /roll           → auto pakai pending.rollType
 *   /roll <type>    → cek apakah match, kalau gak → tolak
 */
public class PendingRoll {

    public enum RollType {
        ATTACK("attack"), DAMAGE("damage"),
        SPELL_ATTACK("spell_attack"), SPELL_DAMAGE("spell_damage"),
        SKILL_CHECK("skill_check"), SAVING_THROW("saving_throw"),
        DEATH_SAVE("death_save");

        private final String key;

        RollType(String key) { this.key = key; }

        public boolean matches(String input) {
            return key.equalsIgnoreCase(input) || name().equalsIgnoreCase(input);
        }
    }

    private static final NamespacedKey KEY_TYPE = new NamespacedKey("dndplugin", "pending_type");
    private static final NamespacedKey KEY_TARGET = new NamespacedKey("dndplugin", "pending_target");
    private static final NamespacedKey KEY_EXTRA = new NamespacedKey("dndplugin", "pending_extra");
    private static final NamespacedKey KEY_SAVE_ABILITY = new NamespacedKey("dndplugin", "pending_save_ability");
    private static final NamespacedKey KEY_SAVE_DC = new NamespacedKey("dndplugin", "pending_save_dc");
    private static final NamespacedKey KEY_PROMPT = new NamespacedKey("dndplugin", "pending_prompt");

    public RollType rollType;
    public String targetId;          // UUID prefix target
    public String extraData;         // weapon/spell name, dll
    public String saveAbility;       // buat saving throw
    public int saveDC;
    public String promptMsg;

    /** Set pending roll di PDC player. Overwrite existing. */
    public static void set(Player player, PendingRoll pr) {
        var pdc = player.getPersistentDataContainer();
        pdc.set(KEY_TYPE, PersistentDataType.STRING, pr.rollType.name());
        if (pr.targetId != null) pdc.set(KEY_TARGET, PersistentDataType.STRING, pr.targetId);
        if (pr.extraData != null) pdc.set(KEY_EXTRA, PersistentDataType.STRING, pr.extraData);
        if (pr.saveAbility != null) pdc.set(KEY_SAVE_ABILITY, PersistentDataType.STRING, pr.saveAbility);
        pdc.set(KEY_SAVE_DC, PersistentDataType.INTEGER, pr.saveDC);
        if (pr.promptMsg != null) pdc.set(KEY_PROMPT, PersistentDataType.STRING, pr.promptMsg);
    }

    /** Baca pending roll dari PDC player. */
    public static PendingRoll get(Player player) {
        var pdc = player.getPersistentDataContainer();
        String typeStr = pdc.get(KEY_TYPE, PersistentDataType.STRING);
        if (typeStr == null) return null;
        try {
            PendingRoll pr = new PendingRoll();
            pr.rollType = RollType.valueOf(typeStr);
            pr.targetId = pdc.get(KEY_TARGET, PersistentDataType.STRING);
            pr.extraData = pdc.get(KEY_EXTRA, PersistentDataType.STRING);
            pr.saveAbility = pdc.get(KEY_SAVE_ABILITY, PersistentDataType.STRING);
            Integer dc = pdc.get(KEY_SAVE_DC, PersistentDataType.INTEGER);
            pr.saveDC = dc != null ? dc : 0;
            pr.promptMsg = pdc.get(KEY_PROMPT, PersistentDataType.STRING);
            return pr;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Hapus pending roll player. */
    public static void clear(Player player) {
        var pdc = player.getPersistentDataContainer();
        pdc.remove(KEY_TYPE); pdc.remove(KEY_TARGET); pdc.remove(KEY_EXTRA);
        pdc.remove(KEY_SAVE_ABILITY); pdc.remove(KEY_SAVE_DC); pdc.remove(KEY_PROMPT);
    }

    public static boolean has(Player player) {
        return player.getPersistentDataContainer().has(KEY_TYPE, PersistentDataType.STRING);
    }
}
