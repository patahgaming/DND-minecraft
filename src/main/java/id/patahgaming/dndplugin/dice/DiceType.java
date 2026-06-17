package id.patahgaming.dndplugin.dice;

import org.bukkit.Material;

/**
 * Physical dice types — D&D standard polyhedral set.
 * Masing-masing punya wool color unik + display color + dice sides.
 */
public enum DiceType {

    D4("D4", "§d", "Pink", Material.PINK_WOOL, 4),
    D6("D6", "§6", "Orange", Material.ORANGE_WOOL, 6),
    D8("D8", "§a", "Lime", Material.LIME_WOOL, 8),
    D10("D10", "§b", "Cyan", Material.CYAN_WOOL, 10),
    D12("D12", "§5", "Purple", Material.PURPLE_WOOL, 12),
    D20("D20", "§f", "White", Material.WHITE_WOOL, 20);

    private final String label;
    private final String color;
    private final String colorName;
    private final Material woolMaterial;
    private final int sides;

    DiceType(String label, String color, String colorName, Material woolMaterial, int sides) {
        this.label = label;
        this.color = color;
        this.colorName = colorName;
        this.woolMaterial = woolMaterial;
        this.sides = sides;
    }

    public String getLabel() { return label; }
    public String getColor() { return color; }
    public String getColorName() { return colorName; }
    public Material getWoolMaterial() { return woolMaterial; }
    public int getSides() { return sides; }

    /** Extract required dice type from a diceExpr like "2d8+3" → D8, "d20+5" → D20 */
    public static DiceType fromDiceExpr(String diceExpr) {
        if (diceExpr == null) return D20;
        // Find "d<N>" pattern
        int dIdx = diceExpr.indexOf('d');
        if (dIdx < 0) return D20;
        String after = diceExpr.substring(dIdx + 1);
        // Extract number after 'd', stop at non-digit
        StringBuilder num = new StringBuilder();
        for (char c : after.toCharArray()) {
            if (Character.isDigit(c)) num.append(c);
            else break;
        }
        if (num.isEmpty()) return D20;
        try {
            int sides = Integer.parseInt(num.toString());
            for (DiceType dt : values()) {
                if (dt.sides == sides) return dt;
            }
        } catch (NumberFormatException ignored) {}
        return D20; // fallback
    }

    /** Detect from PDC string */
    public static DiceType fromString(String s) {
        if (s == null) return null;
        try { return valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
