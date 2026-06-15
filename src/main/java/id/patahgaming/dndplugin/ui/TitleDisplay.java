package id.patahgaming.dndplugin.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Utility untuk menampilkan hasil roll/combat via /title (Adventure API).
 */
public class TitleDisplay {

    private static final Duration FADE_IN = Duration.ofMillis(200);
    private static final Duration STAY = Duration.ofSeconds(2);
    private static final Duration FADE_OUT = Duration.ofMillis(500);

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