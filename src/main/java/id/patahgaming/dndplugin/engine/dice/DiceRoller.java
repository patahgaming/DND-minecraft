package id.patahgaming.dndplugin.engine.dice;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Dice roller: d20, dN, NdS+mod, plus dice expression parser.
 */
public class DiceRoller {

    /** Roll 1dN, hasil 1..n inklusif. */
    public static int roll(int sides) {
        return ThreadLocalRandom.current().nextInt(1, sides + 1);
    }

    /** Roll 1d20 + modifier. */
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

    /** Parse & roll dice expression: "d20+5", "1d8+3", "2d8+0", "3d6" */
    public static int parseAndRoll(String diceExpr) {
        if (diceExpr == null || diceExpr.isEmpty()) return 0;
        String expr = diceExpr.trim();
        int modifier = 0;
        int count = 1;
        int sides = 20;

        // Extract modifier: "d20+5" → modifier=5, "1d8-1" → modifier=-1
        int plusIdx = expr.lastIndexOf('+');
        int minusIdx = expr.lastIndexOf('-');
        if (plusIdx > 0) {
            modifier = Integer.parseInt(expr.substring(plusIdx + 1).trim());
            expr = expr.substring(0, plusIdx);
        } else if (minusIdx > 0) {
            modifier = -Integer.parseInt(expr.substring(minusIdx + 1).trim());
            expr = expr.substring(0, minusIdx);
        }

        // Parse dice: "d20" → count=1 sides=20, "1d8" → count=1 sides=8, "2d6" → count=2 sides=6
        String[] parts = expr.split("d");
        if (parts.length == 2) {
            count = parts[0].isEmpty() ? 1 : Integer.parseInt(parts[0]);
            sides = Integer.parseInt(parts[1]);
        }

        return rollDice(count, sides, modifier);
    }

    /** Given "1d8+3" crit → "2d8+3" (double dice, keep modifier) */
    public static String doubleDiceExpr(String diceExpr) {
        if (diceExpr == null) return null;
        String expr = diceExpr.trim();
        String modifierPart = "";
        int plusIdx = expr.lastIndexOf('+');
        int minusIdx = expr.lastIndexOf('-');
        if (plusIdx > 0) { modifierPart = expr.substring(plusIdx); expr = expr.substring(0, plusIdx); }
        else if (minusIdx > 0) { modifierPart = expr.substring(minusIdx); expr = expr.substring(0, minusIdx); }

        String[] parts = expr.split("d");
        if (parts.length == 2) {
            int count = parts[0].isEmpty() ? 1 : Integer.parseInt(parts[0]);
            return (count * 2) + "d" + parts[1] + modifierPart;
        }
        return diceExpr; // fallback
    }
}