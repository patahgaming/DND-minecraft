package id.patahgaming.dndplugin.engine.dice;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Dice roller sederhana: d20, d6, dll + modifier.
 */
public class DiceRoller {

    /** Roll 1dN, hasil 1..n inklusif. */
    public static int roll(int sides) {
        return ThreadLocalRandom.current().nextInt(1, sides + 1) ;
    }

    /** Roll 1d20 + modifier — dipakai untuk initiative, attack roll, ability check. */
    public static int rollD20(int modifier) {
        return roll(20) + modifier;
    }

    /** Roll NdSides + modifier, contoh rollDice(2, 6, 3) = 2d6+3 */
    public static int rollDice(int count, int sides, int modifier) {
        int total = modifier;
        for (int i = 0; i < count; i++) {
            total += roll(sides);
        }
        return total;
    }
}