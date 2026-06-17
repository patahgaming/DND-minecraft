package id.patahgaming.dndplugin.engine.combat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Type;
import java.util.*;

/**
 * PendingRoll v2 — one active + queue per player.
 *
 * Disimpan di PDC sebagai JSON biar support queue + context map.
 * Flow:
 *   set(active) → player /roll → resolve → advanceQueue() → next
 *
 * Queue hanya dipakai saat mob cast spell ke player (rare edge case).
 * Saat giliran player sendiri, gak ada yang masuk queue.
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
        public String getKey() { return key; }
    }

    // PDC keys
    private static final NamespacedKey KEY_ACTIVE = new NamespacedKey("dndplugin", "pending_active");
    private static final NamespacedKey KEY_QUEUE = new NamespacedKey("dndplugin", "pending_queue");

    private static final Gson GSON = new Gson();
    private static final Type QUEUE_TYPE = new TypeToken<List<PendingRollData>>(){}.getType();

    // --- JSON-serializable data object ---
    public static class PendingRollData {
        public String rollType;
        public String diceExpr;          // "d20+5", "1d8+3", "2d8+3"
        public String promptMsg;         // prompt ke player
        public int lastRollResult;
        public boolean resolved;
        public Map<String, String> context = new LinkedHashMap<>(); // spell context, target info, dll
    }

    // --- Public fields (populated by get()) ---
    public RollType rollType;
    public String diceExpr;
    public String promptMsg;
    public int lastRollResult;
    public boolean resolved;
    public Map<String, String> context = new LinkedHashMap<>();

    /** Convert domain object → serializable data. */
    private PendingRollData toData() {
        PendingRollData d = new PendingRollData();
        d.rollType = rollType != null ? rollType.name() : null;
        d.diceExpr = diceExpr;
        d.promptMsg = promptMsg;
        d.lastRollResult = lastRollResult;
        d.resolved = resolved;
        d.context = context;
        return d;
    }

    /** Convert serializable data → domain object. */
    private static PendingRoll fromData(PendingRollData d) {
        if (d == null || d.rollType == null) return null;
        PendingRoll pr = new PendingRoll();
        try {
            pr.rollType = RollType.valueOf(d.rollType);
        } catch (IllegalArgumentException e) {
            return null;
        }
        pr.diceExpr = d.diceExpr;
        pr.promptMsg = d.promptMsg;
        pr.lastRollResult = d.lastRollResult;
        pr.resolved = d.resolved;
        pr.context = d.context != null ? d.context : new LinkedHashMap<>();
        return pr;
    }

    // ==================== ACTIVE SLOT ====================

    /** Set active PendingRoll. Overwrite existing active. */
    public static void set(Player player, PendingRoll pr) {
        var pdc = player.getPersistentDataContainer();
        PendingRollData data = pr.toData();
        pdc.set(KEY_ACTIVE, PersistentDataType.STRING, GSON.toJson(data));
    }

    /** Get active PendingRoll, or null. */
    public static PendingRoll get(Player player) {
        var pdc = player.getPersistentDataContainer();
        String json = pdc.get(KEY_ACTIVE, PersistentDataType.STRING);
        if (json == null) return null;
        try {
            PendingRollData data = GSON.fromJson(json, PendingRollData.class);
            return fromData(data);
        } catch (Exception e) {
            return null;
        }
    }

    /** Check if player has active pending roll. */
    public static boolean has(Player player) {
        return player.getPersistentDataContainer().has(KEY_ACTIVE, PersistentDataType.STRING);
    }

    /** Clear active pending roll. */
    public static void clear(Player player) {
        player.getPersistentDataContainer().remove(KEY_ACTIVE);
    }

    // ==================== QUEUE ====================

    /** Enqueue a PendingRoll. If no active, set as active instead. */
    public static void enqueue(Player player, PendingRoll pr) {
        if (!has(player)) {
            set(player, pr);
            return;
        }
        // Add to queue
        var pdc = player.getPersistentDataContainer();
        String json = pdc.get(KEY_QUEUE, PersistentDataType.STRING);
        List<PendingRollData> queue;
        if (json != null) {
            try {
                queue = GSON.fromJson(json, QUEUE_TYPE);
            } catch (Exception e) {
                queue = new ArrayList<>();
            }
        } else {
            queue = new ArrayList<>();
        }
        queue.add(pr.toData());
        pdc.set(KEY_QUEUE, PersistentDataType.STRING, GSON.toJson(queue));
    }

    /** Dequeue next PendingRoll and set as active. Returns null if queue empty. */
    public static PendingRoll advanceQueue(Player player) {
        clear(player); // clear current active first

        var pdc = player.getPersistentDataContainer();
        String json = pdc.get(KEY_QUEUE, PersistentDataType.STRING);
        if (json == null) return null;

        try {
            List<PendingRollData> queue = GSON.fromJson(json, QUEUE_TYPE);
            if (queue == null || queue.isEmpty()) {
                pdc.remove(KEY_QUEUE);
                return null;
            }
            PendingRollData next = queue.remove(0);
            if (queue.isEmpty()) {
                pdc.remove(KEY_QUEUE);
            } else {
                pdc.set(KEY_QUEUE, PersistentDataType.STRING, GSON.toJson(queue));
            }

            PendingRoll pr = fromData(next);
            if (pr != null) {
                set(player, pr);
                return pr;
            }
            return null;
        } catch (Exception e) {
            pdc.remove(KEY_QUEUE);
            return null;
        }
    }

    /** Get queue size (not including active). */
    public static int getQueueSize(Player player) {
        var pdc = player.getPersistentDataContainer();
        String json = pdc.get(KEY_QUEUE, PersistentDataType.STRING);
        if (json == null) return 0;
        try {
            List<PendingRollData> queue = GSON.fromJson(json, QUEUE_TYPE);
            return queue != null ? queue.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ==================== CONTEXT HELPERS ====================

    /** Build context key for target save result. */
    public static String targetCtxKey(int index) {
        return "target_" + index;
    }

    /** Parse target context: {id, saved} from context map entry. */
    public static TargetSaveInfo parseTargetCtx(Map<String, String> ctx, int index) {
        String key = targetCtxKey(index);
        String json = ctx.get(key);
        if (json == null) return null;
        try {
            return GSON.fromJson(json, TargetSaveInfo.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static class TargetSaveInfo {
        public String id;       // UUID prefix (8 chars)
        public String name;     // display name
        public boolean saved;   // true = berhasil save
    }

    /** Serialize target info to context entry. */
    public static void putTargetCtx(Map<String, String> ctx, int index, TargetSaveInfo info) {
        ctx.put(targetCtxKey(index), GSON.toJson(info));
    }
}
