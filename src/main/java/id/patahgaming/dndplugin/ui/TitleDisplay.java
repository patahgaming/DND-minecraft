package id.patahgaming.dndplugin.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;

/**
 * Utility untuk menampilkan hasil roll/combat via /title (Adventure API).
 *
 * Roll animation: "§7Rolling: §k████" obfuscated text + note block sounds →
 * final result color-coded berdasarkan nilai d20.
 *
 * Color map:
 *   Nat 1  → §c Red
 *   2-7    → §6 Orange
 *   8-17   → §e Yellow
 *   18-19 → §b Blue (almost crit)
 *   Nat 20 → §a Green (CRIT!)
 */
public class TitleDisplay {

    private static final Duration FADE_IN = Duration.ofMillis(200);
    private static final Duration STAY = Duration.ofSeconds(2);
    private static final Duration FADE_OUT = Duration.ofMillis(500);

    private static final Duration ROLL_IN = Duration.ZERO;
    private static final Duration ROLL_STAY = Duration.ofMillis(300);
    private static final Duration ROLL_OUT = Duration.ZERO;

    private static final int ROLL_DURATION_TICKS = 16; // ~800ms animasi

    /**
     * Animated roll with obfuscated text + note block sounds.
     *
     * Animation:
     *   0ms:    Title "§7Rolling: §k████" + Note Block sound (pitch 0.5)
     *   200ms:  Title refresh + Note Block sound (pitch 0.7)
     *   400ms:  Title refresh + Note Block sound (pitch 1.0)
     *   600ms:  Title refresh + Note Block sound (pitch 1.3)
     *   800ms:  Final color-coded result
     */
    public static void showRollAnimation(Plugin plugin, Player player,
                                          String rollType, int modifier, int roll, int total) {
        // Rolling animation — obfuscated text dengan note block ticks
        int[] noteTicks = {0, 4, 8, 12}; // ticks for each note
        float[] pitches = {0.5f, 0.7f, 0.9f, 1.2f};

        for (int i = 0; i < noteTicks.length; i++) {
            final float pitch = pitches[i];
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Obfuscated rolling text (§k = scramble)
                Title rollTitle = Title.title(
                        Component.text("§7Rolling: §f§k████"),
                        Component.text("§7" + rollType),
                        Title.Times.times(ROLL_IN, ROLL_STAY, ROLL_OUT)
                );
                player.showTitle(rollTitle);

                // Note block sound (bunyi dadu)
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, pitch);
            }, noteTicks[i]);
        }

        // Final result — color coded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String color = rollColor(roll);
            String mainText = color + roll + " + " + modifier + " = " + total;
            String subText;
            Sound finalSound;
            float finalPitch;

            if (roll == 20) {
                subText = "§a§lNAT 20! §7" + rollType;
                finalSound = Sound.BLOCK_NOTE_BLOCK_BELL;
                finalPitch = 2.0f;
            } else if (roll == 1) {
                subText = "§c§lCRITICAL FAIL! §7" + rollType;
                finalSound = Sound.BLOCK_NOTE_BLOCK_BASS;
                finalPitch = 0.3f;
            } else {
                subText = "§7" + rollType;
                finalSound = Sound.BLOCK_NOTE_BLOCK_PLING;
                finalPitch = switch (color) {
                    case "§6" -> 0.8f;  // orange → lower
                    case "§b" -> 1.5f;  // blue → higher (almost crit)
                    default -> 1.0f;    // yellow → normal
                };
            }

            Title finalTitle = Title.title(
                    Component.text(mainText),
                    Component.text(subText),
                    Title.Times.times(FADE_IN, STAY, FADE_OUT)
            );
            player.showTitle(finalTitle);
            player.playSound(player.getLocation(), finalSound, 0.8f, finalPitch);

            // Chat log
            player.sendMessage("§7[Roll] " + mainText + " §7— " + rollType);
        }, ROLL_DURATION_TICKS);
    }

    /** Color code berdasarkan nilai d20 roll murni. */
    private static String rollColor(int roll) {
        return switch (roll) {
            case 1 -> "§c";      // Red
            case 2, 3, 4, 5, 6, 7 -> "§6"; // Orange
            case 8, 9, 10, 11, 12, 13, 14, 15, 16, 17 -> "§e"; // Yellow
            case 18, 19 -> "§b"; // Blue
            case 20 -> "§a";     // Green (NAT 20!)
            default -> "§f";
        };
    }

    // --- Static (non-animated) fallbacks ---

    public static void showRollResult(Player player, String rollType, int total) {
        Title title = Title.title(
                Component.text("Roll: " + total),
                Component.text(rollType),
                Title.Times.times(FADE_IN, STAY, FADE_OUT)
        );
        player.showTitle(title);
    }

    public static void showCombatResult(Player player, String mainText, String subText) {
        Title title = Title.title(
                Component.text(mainText),
                Component.text(subText),
                Title.Times.times(FADE_IN, STAY, FADE_OUT)
        );
        player.showTitle(title);
    }
}