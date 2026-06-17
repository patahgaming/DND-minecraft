package id.patahgaming.dndplugin.item;

public enum ItemType {
    WEAPON,
    ARMOR,
    ACCESSORY,
    CONSUMABLE,
    QUEST,
    DICE;

    public static ItemType fromString(String s) {
        try { return valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
