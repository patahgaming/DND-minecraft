package id.patahgaming.dndplugin.item;

/**
 * Item rarity — menentukan warna nama, damage bonus, dan efek spesial.
 */
public enum ItemRarity {

    COMMON("§f", "Common", 0),
    UNCOMMON("§a", "Uncommon", 1),
    RARE("§9", "Rare", 2),
    MYTHICAL("§5", "Mythical", 2),
    QUEST("§6", "Quest", 0),
    LEGENDARY("§c§l", "Legendary", 3);

    private final String color;
    private final String display;
    private final int damageBonus;

    ItemRarity(String color, String display, int damageBonus) {
        this.color = color;
        this.display = display;
        this.damageBonus = damageBonus;
    }

    public String getColor() { return color; }
    public String getDisplay() { return display; }
    public int getDamageBonus() { return damageBonus; }

    public static ItemRarity fromString(String s) {
        try { return valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
