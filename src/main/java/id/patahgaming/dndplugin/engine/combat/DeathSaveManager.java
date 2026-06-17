package id.patahgaming.dndplugin.engine.combat;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.engine.dice.DiceRoller;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Death Saving Throws — D&D 5e rules.
 *
 * Saat player HP = 0:
 *   - Player jadi UNCONSCIOUS (bukan langsung mati)
 *   - Tiap giliran: player harus /roll death_save (d20, no modifier)
 *   - Nat 20  → langsung stabil, HP = 1
 *   - Nat 1   → +2 failures sekaligus
 *   - 10+     → +1 success
 *   - 2–9     → +1 failure
 *   - 3 success → STABILIZED (HP = 1)
 *   - 3 failure → DEAD
 *
 * Mob skip unconscious player — tidak diserang.
 */
public class DeathSaveManager {

    public enum DeathSaveResult {
        PENDING,
        SUCCESS,
        FAILURE,
        STABILIZED,
        DEAD
    }

    public static class DeathSaveState {
        public int successes = 0;
        public int failures  = 0;
        public boolean unconscious = true;

        public boolean isDead()        { return failures  >= 3; }
        public boolean isStabilized()  { return successes >= 3; }
    }

    private final Map<UUID, DeathSaveState> states = new HashMap<>();
    private final DndPlugin plugin;

    public DeathSaveManager(DndPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== PUBLIC API ====================

    /**
     * Panggil saat player HP mencapai 0.
     * - Set HP ke 0.5 (minimum Minecraft supaya tidak langsung mati)
     * - Apply efek visual unconscious
     * - Register death save state
     */
    public void makeUnconscious(Player player) {
        if (states.containsKey(player.getUniqueId())) return;

        player.setHealth(0.5);

        int duration = Integer.MAX_VALUE;
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  duration, 5, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,  duration, 5, false, false));

        states.put(player.getUniqueId(), new DeathSaveState());

        Bukkit.broadcastMessage("§c§l" + player.getName() + " jatuh tak sadarkan diri!");
        player.sendMessage("§c§lKamu sekarat! Ketik §f/roll§c saat giliranmu untuk death saving throw.");
        player.sendTitle("§c§lUNCONSCIOUS", "§7Ketik /roll saat giliranmu", 10, 60, 20);
    }

    /**
     * Dipanggil dari RollCommand/TurnManager saat player /roll death save.
     */
    public DeathSaveResult processRoll(Player player) {
        DeathSaveState state = states.get(player.getUniqueId());
        if (state == null) {
            player.sendMessage("§cKamu tidak sedang dalam kondisi sekarat.");
            return DeathSaveResult.PENDING;
        }

        int roll = DiceRoller.roll(20);

        String rollColor = roll == 20 ? "§a" : roll == 1 ? "§c" : roll >= 10 ? "§e" : "§6";
        player.sendTitle(rollColor + "Death Save: " + roll, buildProgressBar(state), 5, 50, 10);
        player.sendMessage("§7[Death Save] Roll: " + rollColor + roll
                + " §8| §7Successes: §a" + state.successes
                + " §8| §7Failures: §c" + state.failures);

        if (roll == 20) {
            stabilize(player, state);
            return DeathSaveResult.STABILIZED;
        }

        if (roll == 1) {
            state.failures += 2;
            Bukkit.broadcastMessage("§c§l" + player.getName() + " nat 1! (+2 failures)");
        } else if (roll >= 10) {
            state.successes++;
        } else {
            state.failures++;
        }

        if (state.isStabilized()) {
            stabilize(player, state);
            return DeathSaveResult.STABILIZED;
        }

        if (state.isDead()) {
            kill(player, state);
            return DeathSaveResult.DEAD;
        }

        broadcastProgress(player, state);
        return roll >= 10 ? DeathSaveResult.SUCCESS : DeathSaveResult.FAILURE;
    }

    public boolean isUnconscious(Player player) {
        return states.containsKey(player.getUniqueId());
    }

    public void stabilizeExternal(Player player) {
        DeathSaveState state = states.get(player.getUniqueId());
        if (state == null) return;
        stabilize(player, state);
    }

    public void clearState(Player player) {
        states.remove(player.getUniqueId());
    }

    public DeathSaveState getState(Player player) {
        return states.get(player.getUniqueId());
    }

    // ==================== PRIVATE ====================

    private void stabilize(Player player, DeathSaveState state) {
        state.unconscious = false;
        states.remove(player.getUniqueId());

        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.WEAKNESS);

        player.setHealth(1.0);

        player.sendTitle("§a§lSTABILIZED!", "§7HP kembali ke 1", 10, 60, 20);
        Bukkit.broadcastMessage("§a" + player.getName() + " berhasil stabil! HP = 1");

        plugin.getLogger().info("[DeathSave] " + player.getName() + " stabilized.");
    }

    private void kill(Player player, DeathSaveState state) {
        state.unconscious = false;
        states.remove(player.getUniqueId());

        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.WEAKNESS);

        player.setHealth(0.0);

        player.sendTitle("§c§lYOU DIED", "§73 death save failures", 10, 80, 20);
        Bukkit.broadcastMessage("§c§l" + player.getName() + " telah gugur...");

        plugin.getLogger().info("[DeathSave] " + player.getName() + " died from failed death saves.");
    }

    private void broadcastProgress(Player player, DeathSaveState state) {
        String bar = buildProgressBar(state);
        Bukkit.getOnlinePlayers().forEach(p -> {
            if (!p.equals(player)) {
                p.sendMessage("§7[Death Save] §f" + player.getName() + " " + bar);
            }
        });
    }

    private String buildProgressBar(DeathSaveState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("§aSUC: ");
        for (int i = 0; i < 3; i++) sb.append(i < state.successes ? "✅" : "☐");
        sb.append(" §cFAIL: ");
        for (int i = 0; i < 3; i++) sb.append(i < state.failures ? "❌" : "☐");
        return sb.toString();
    }
}
