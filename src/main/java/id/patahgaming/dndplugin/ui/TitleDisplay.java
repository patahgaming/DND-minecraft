package id.patahgaming.dndplugin.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility untuk menampilkan hasil roll/combat via /title (Adventure API).
 *
 * Roll animation: angka random flashing cepat (4 frame @ 80ms) →
 * final result color-coded berdasarkan nilai d20.
 *
 * Color map:
 *   Nat 1  → §c Red
 *   2-7    → §6 Orange
 *   8-17   → §e Yellow
 *   18-19  → §b Blue (almost crit)
 *   Nat 20 → §a Green (CRIT!)
 */
public class TitleDisplay {

    private static final Duration FADE_IN = Duration.ofMillis(200);
    private static final Duration STAY = Duration.ofSeconds(2);
    private static final Duration FADE_OUT = Duration.ofMillis(500);

    private static final Duration FLASH_IN = Duration.ofMillis(0);
    private static final Duration FLASH_STAY = Duration.ofMillis(80);
    private static final Duration FLASH_OUT = Duration.ofMillis(0);

    private static final int FLASH_FRAMES = 4;

    /**
     * Animated roll: 4 flash frames of random numbers, then final result.
     */
    public static void showRollAnimation(Plugin plugin, Player player,
                                          String rollType, int modifier, int roll, int total) {
        // Frame 1-4: angka random flashing dengan cepat
        for (int i = 0; i < FLASH_FRAMES; i++) {
            final int frame = i;
            final int fakeRoll = 1 + ThreadLocalRandom.current().nextInt(20);
            final int fakeTotal = fakeRoll + modifier;
            final int delayTicks = frame * 4; // 80ms * 4 ticks per frame

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Warna netral selama animasi (cyan flash)
                String animText = "§3" + fakeRoll + " + " + modifier + " = " + fakeTotal;
                Title flash = Title.title(
                        Component.text(animText),
                        Component.text("§7Rolling... " + rollType),
                        Title.Times.times(FLASH_IN, FLASH_STAY, FLASH_OUT)
                );
                player.showTitle(flash);
            }, delayTicks);
        }

        // Frame final: hasil asli dengan color coding
        int finalDelay = FLASH_FRAMES * 4 + 2;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String color = rollColor(roll);
            String mainText = color + roll + " + " + modifier + " = " + total;
            String subText;
            if (roll == 1) {
                subText = "§c§lCRITICAL FAIL! §7" + rollType;
            } else if (roll == 20) {
                subText = "§a§lNAT 20! §7" + rollType;
            } else {
                subText = "§7" + rollType;
            }
            Title finalTitle = Title.title(
                    Component.text(mainText),
                    Component.text(subText),
                    Title.Times.times(FADE_IN, STAY, FADE_OUT)
            );
            player.showTitle(finalTitle);

            // Juga kirim pesan chat biar ada log
            player.sendMessage("§7[Roll] " + mainText + " §7— " + rollType);
        }, finalDelay);
    }

    /** Color code berdasarkan nilai d20 roll murni. */
    private static String rollColor(int roll) {
        return switch (roll) {
            case 1 -> "§c";      // Red
            case 2, 3, 4, 5, 6, 7 -> "§6"; // Orange
            case 8, 9, 10, 11, 12, 13, 14, 15, 16, 17 -> "§e"; // Yellow
            case 18, 19 -> "§b"; // Blue (almost crit!)
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