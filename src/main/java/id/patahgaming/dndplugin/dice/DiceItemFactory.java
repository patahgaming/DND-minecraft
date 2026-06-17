package id.patahgaming.dndplugin.dice;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Build ItemStack dadu dengan PDC tags.
 *
 * PDC keys:
 *   dnd:item_type  = "DICE"
 *   dnd:dice_type  = "D20" / "D8" / etc.
 */
public class DiceItemFactory {

    private static final NamespacedKey KEY_ITEM_TYPE = new NamespacedKey("dndplugin", "item_type");
    private static final NamespacedKey KEY_DICE_TYPE = new NamespacedKey("dndplugin", "dice_type");

    /** Build satu dadu ItemStack. */
    public static ItemStack create(DiceType type) {
        ItemStack dice = new ItemStack(type.getWoolMaterial(), 1);
        ItemMeta meta = dice.getItemMeta();
        if (meta == null) return dice;

        meta.setDisplayName(type.getColor() + "🎲 Dadu " + type.getLabel());
        meta.setLore(List.of(
                "§7Letakkan di tanah untuk roll bebas",
                "§7Pegang di tangan saat /roll untuk combat",
                "§8[DnD Dice — " + type.getLabel() + "]"
        ));

        var pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_ITEM_TYPE, PersistentDataType.STRING, "DICE");
        pdc.set(KEY_DICE_TYPE, PersistentDataType.STRING, type.name());

        dice.setItemMeta(meta);
        return dice;
    }

    /** Build 6-dice starter pack — 4 pasang tiap dadu (24 items total). */
    public static ItemStack[] createStarterPack() {
        DiceType[] all = DiceType.values();
        ItemStack[] pack = new ItemStack[all.length];
        for (int i = 0; i < all.length; i++) {
            pack[i] = create(all[i]);
            pack[i].setAmount(all[i].getSides()); // D4=×4, D6=×6, D8=×8, D10=×10, D12=×12, D20=×20
        }
        return pack;
    }

    /** Detect dice type from ItemStack PDC. Null if not a dice. */
    public static DiceType getDiceType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String typeStr = pdc.get(KEY_ITEM_TYPE, PersistentDataType.STRING);
        if (!"DICE".equals(typeStr)) return null;
        String diceStr = pdc.get(KEY_DICE_TYPE, PersistentDataType.STRING);
        return DiceType.fromString(diceStr);
    }

    /** Check if an ItemStack is a dice item. */
    public static boolean isDice(ItemStack item) {
        return getDiceType(item) != null;
    }

    /** Check if item is a specific dice type. */
    public static boolean isDiceOfType(ItemStack item, DiceType type) {
        DiceType dt = getDiceType(item);
        return dt == type;
    }
}
