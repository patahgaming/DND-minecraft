package id.patahgaming.dndplugin.dice;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Validasi dadu sebelum resolve roll.
 *
 * - Cek main hand → cocok?
 * - Scan inventory → hint slot
 * - Return hasil validasi
 */
public class DiceValidator {

    /** Result dari validasi dadu. */
    public static class ValidationResult {
        public boolean valid;           // dadu benar di main hand
        public boolean hasAnywhere;     // dadu ada di inventory (tapi bukan main hand)
        public int slotHint = -1;       // slot hotbar (0-8) atau inventory (9-35)
        public String slotHintText;     // text untuk player ("slot hotbar 3" / "inventory")
    }

    /**
     * Validate: player butuh requiredType di main hand.
     * Kalau tidak ada di main hand, scan inventory buat hint.
     */
    public static ValidationResult validate(Player player, DiceType requiredType) {
        ValidationResult result = new ValidationResult();

        // Cek main hand
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (DiceItemFactory.isDiceOfType(mainHand, requiredType)) {
            result.valid = true;
            return result;
        }

        // Main hand salah atau bukan dadu — scan inventory
        // Hotbar dulu (slot 0-8)
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (DiceItemFactory.isDiceOfType(item, requiredType)) {
                result.hasAnywhere = true;
                result.slotHint = slot;
                result.slotHintText = "§7" + requiredType.getLabel()
                        + " ada di §ehotbar slot " + (slot + 1)
                        + " §7— pindahkan ke tangan kanan";
                return result;
            }
        }
        // Main inventory (slot 9-35)
        for (int slot = 9; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (DiceItemFactory.isDiceOfType(item, requiredType)) {
                result.hasAnywhere = true;
                result.slotHint = slot;
                result.slotHintText = "§7" + requiredType.getLabel()
                        + " ada di §einventory §7— pindahkan dulu ke hotbar & tangan kanan";
                return result;
            }
        }

        // Tidak punya sama sekali
        return result;
    }

    /** Format pesan error untuk player. */
    public static String formatError(DiceType required, ValidationResult result) {
        if (result.hasAnywhere) {
            // Ada di inventory tapi bukan di tangan
            DiceType inHand = DiceItemFactory.getDiceType(
                    result.slotHint >= 0 ? null : null); // not needed
            String current = "";
            ItemStack hand = null; // we already checked above, skip
            return "§c⚠ Salah dadu! Butuh " + required.getColor() + required.getLabel()
                    + " (" + required.getColorName() + " Wool)§c.\n"
                    + result.slotHintText;
        } else {
            return "§c⚠ Kamu tidak punya " + required.getColor() + required.getLabel()
                    + " (" + required.getColorName() + " Wool)§c.\n"
                    + "§7Ketik §f/itemspesial get dice_"
                    + required.getLabel().toLowerCase() + "§7 untuk mendapatkannya.";
        }
    }
}
