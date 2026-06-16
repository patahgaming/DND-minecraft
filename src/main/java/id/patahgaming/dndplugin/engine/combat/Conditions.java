package id.patahgaming.dndplugin.engine.combat;

import id.patahgaming.dndplugin.engine.dice.DiceRoller;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * D&D 5e Conditions — diterapkan via Minecraft potion effects.
 *
 * | Kondisi      | Efek D&D                            | Minecraft Mapping              |
 * |-------------|-------------------------------------|-------------------------------|
 * | poisoned    | Disadvantage attack + ability check | POISON + SLOWNESS             |
 * | stunned     | Tidak bisa action, auto-fail STR/DEX| BLINDNESS + SLOWNESS + WEAKNESS|
 * | unconscious | Prone, attack adjacent = critical  | BLINDNESS + SLOWNESS (Cripple)|
 * | blinded     | Disadvantage attack, enemy advantage| BLINDNESS                     |
 */
public class Conditions {

    private static final int DEFAULT_DURATION_TICKS = 20 * 30; // 30 detik

    // --- Apply conditions ---

    public static void applyPoisoned(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, DEFAULT_DURATION_TICKS, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, DEFAULT_DURATION_TICKS, 0));
        player.sendMessage("§a[Condition] §cPoisoned! §7Disadvantage on attacks and ability checks.");
    }

    public static void applyStunned(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, DEFAULT_DURATION_TICKS, 4)); // sangat lambat
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, DEFAULT_DURATION_TICKS, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, DEFAULT_DURATION_TICKS, 0));
        player.sendMessage("§a[Condition] §cStunned! §7Cannot take actions. Auto-fail STR/DEX saves.");
    }

    public static void applyUnconscious(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, DEFAULT_DURATION_TICKS, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, DEFAULT_DURATION_TICKS, 5));
        player.sendMessage("§a[Condition] §cUnconscious! §7Prone. Adjacent attacks are critical hits.");
    }

    public static void applyBlinded(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, DEFAULT_DURATION_TICKS, 0));
        player.sendMessage("§a[Condition] §cBlinded! §7Disadvantage on attacks. Enemies have advantage.");
    }

    // --- Death Saving Throws ---

    /**
     * Roll death saving throw (d20, no modifier).
     * 10+ = success, 1 = 2 failures, nat20 = regain 1 HP.
     * Return true jika player stabil (3 success) atau mati (3 fail).
     */
    public static DeathSaveResult rollDeathSave(Player player, int successCount, int failCount) {
        int roll = DiceRoller.roll(20);

        if (roll == 20) {
            // Nat 20: regain 1 HP, bangun
            player.setHealth(1.0);
            player.sendMessage("§a§lNAT 20! §aYou regain consciousness with 1 HP!");
            return DeathSaveResult.STABILIZED;
        } else if (roll == 1) {
            // Nat 1: 2 failures
            failCount += 2;
            player.sendMessage("§c§lNAT 1! §cTwo death save failures! (" + failCount + "/3)");
        } else if (roll >= 10) {
            successCount++;
            player.sendMessage("§aDeath save success! (" + successCount + "/3)");
        } else {
            failCount++;
            player.sendMessage("§cDeath save failure! (" + failCount + "/3)");
        }

        if (successCount >= 3) {
            player.setHealth(1.0);
            player.sendMessage("§aYou stabilize with 1 HP!");
            return DeathSaveResult.STABILIZED;
        }
        if (failCount >= 3) {
            player.sendMessage("§c§lYou have died...");
            return DeathSaveResult.DEAD;
        }

        return DeathSaveResult.CONTINUE;
    }

    public enum DeathSaveResult {
        CONTINUE,   // Masih rolling
        STABILIZED, // 3 success atau nat20 → HP=1
        DEAD        // 3 fail
    }

    // --- Utility ---

    /** Clear semua condition effects dari player. */
    public static void clearAll(Player player) {
        player.removePotionEffect(PotionEffectType.POISON);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.sendMessage("§aConditions cleared.");
    }
}
