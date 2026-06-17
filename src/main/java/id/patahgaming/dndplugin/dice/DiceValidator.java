package id.patahgaming.dndplugin.dice;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Validasi dadu sebelum resolve roll.
 *
 * - Cek main hand → cocok?
 * - Scan inventory → hint slot
 * - Kalau gak punya sama sekali → auto-give ke rightmost hotbar
 * - Arrange 6 dadu ke hotbar paling kanan (slot 3-8)
 */
public class DiceValidator {

    public static class ValidationResult {
        public boolean valid;           // dadu benar di main hand
        public boolean hasAnywhere;     // dadu ada di inventory (tapi bukan main hand)
        public boolean wasGiven;        // dadu baru aja di-auto-give
        public int slotHint = -1;
        public String message;          // pesan buat player
    }

    /**
     * Validate + auto-give kalau gak punya sama sekali.
     * Dadu selalu di-arrange ke rightmost hotbar.
     */
    public static ValidationResult validate(Player player, DiceType requiredType) {
        ValidationResult result = new ValidationResult();
        PlayerInventory inv = player.getInventory();

        // 1. Cek main hand
        ItemStack mainHand = inv.getItemInMainHand();
        if (DiceItemFactory.isDiceOfType(mainHand, requiredType)) {
            result.valid = true;
            return result;
        }

        // 2. Scan hotbar (slot 0-8)
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = inv.getItem(slot);
            if (DiceItemFactory.isDiceOfType(item, requiredType)) {
                result.hasAnywhere = true;
                result.slotHint = slot;
                result.message = "§7" + requiredType.getLabel()
                        + " ada di §ehotbar slot " + (slot + 1)
                        + " §7— pindahkan ke tangan kanan";
                return result;
            }
        }

        // 3. Scan main inventory (slot 9-35)
        for (int slot = 9; slot < 36; slot++) {
            ItemStack item = inv.getItem(slot);
            if (DiceItemFactory.isDiceOfType(item, requiredType)) {
                result.hasAnywhere = true;
                result.slotHint = slot;
                result.message = "§7" + requiredType.getLabel()
                        + " ada di §einventory §7— pindahkan dulu ke hotbar & tangan kanan";
                return result;
            }
        }

        // 4. Gak punya sama sekali → auto-give ke rightmost hotbar
        ItemStack newDice = DiceItemFactory.create(requiredType);
        int targetSlot = findRightmostEmptyHotbar(inv);
        if (targetSlot >= 0) {
            inv.setItem(targetSlot, newDice);
            result.slotHint = targetSlot;
            result.message = "§a✅ " + requiredType.getColor() + requiredType.getLabel()
                    + " §adiberikan! §7Di hotbar slot " + (targetSlot + 1)
                    + " — pindahkan ke tangan kanan";
        } else {
            // Hotbar penuh → taruh di inventory
            int emptySlot = inv.firstEmpty();
            if (emptySlot >= 0) {
                inv.setItem(emptySlot, newDice);
                result.slotHint = emptySlot;
                result.message = "§a✅ " + requiredType.getColor() + requiredType.getLabel()
                        + " §adiberikan ke inventory — pindahkan ke hotbar";
            } else {
                // Inventory penuh → drop di kaki
                player.getWorld().dropItem(player.getLocation(), newDice);
                result.message = "§a✅ " + requiredType.getColor() + requiredType.getLabel()
                        + " §adijatuhkan di kakimu — ambil & pegang";
            }
        }
        result.hasAnywhere = true;
        result.wasGiven = true;
        return result;
    }

    /**
     * LOCK dadu ke slot spesifik hotbar (selalu, permanen):
     *   slot 8 = D20, slot 7 = D12, slot 6 = D10,
     *   slot 5 = D8,  slot 4 = D6,  slot 3 = D4
     *
     * Tiap slot dapat 4 pasang dadu (4× stack) buat roll cepat.
     * Kalau ada yang hilang → auto-create.
     * Dipanggil saat player join.
     */
    public static void arrangeDiceToHotbar(Player player) {
        PlayerInventory inv = player.getInventory();
        DiceType[] allDice = DiceType.values(); // D4, D6, D8, D10, D12, D20

        // Bersihin semua dadu dari inventory (supaya gak duplicate di tempat lain)
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inv.getItem(slot);
            if (DiceItemFactory.isDice(item)) {
                inv.setItem(slot, null);
            }
        }

        // Taruh 4 pasang tiap dadu di slot yang sudah ditentukan
        // D20 = slot 8 (index paling kanan), D4 = slot 3
        for (int i = allDice.length - 1; i >= 0; i--) {
            int slot = 8 - (allDice.length - 1 - i); // D20→8, D12→7, D10→6, D8→5, D6→4, D4→3
            ItemStack dice = DiceItemFactory.create(allDice[i]);
            dice.setAmount(allDice[i].getSides()); // D4=×4, D6=×6, D8=×8, D10=×10, D12=×12, D20=×20
            inv.setItem(slot, dice);
        }
    }

    /** Cari slot hotbar kosong dari paling kanan (8 → 3). Return -1 kalau penuh. */
    private static int findRightmostEmptyHotbar(PlayerInventory inv) {
        for (int slot = 8; slot >= 3; slot--) {
            if (inv.getItem(slot) == null) return slot;
        }
        // Kalau slot 3-8 penuh, coba slot 0-2
        for (int slot = 2; slot >= 0; slot--) {
            if (inv.getItem(slot) == null) return slot;
        }
        return -1;
    }
}
