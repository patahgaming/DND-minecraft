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
     * Arrange semua 6 dadu ke hotbar paling kanan (slot 3-8).
     * Dipanggil saat player join.
     */
    public static void arrangeDiceToHotbar(Player player) {
        PlayerInventory inv = player.getInventory();
        DiceType[] allDice = DiceType.values(); // D4, D6, D8, D10, D12, D20

        // Kumpulin dadu dari seluruh inventory + hotbar
        ItemStack[] found = new ItemStack[allDice.length];
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null) continue;
            DiceType dt = DiceItemFactory.getDiceType(item);
            if (dt != null) {
                int idx = dt.ordinal();
                if (found[idx] == null) {
                    found[idx] = item.clone();
                    inv.setItem(slot, null); // remove dari posisi lama
                }
            }
        }

        // Taruh di slot paling kanan hotbar: slot 3-8
        int hotbarSlot = 8; // mulai dari paling kanan
        for (int i = found.length - 1; i >= 0; i--) {
            if (found[i] != null && hotbarSlot >= 3) {
                inv.setItem(hotbarSlot, found[i]);
                hotbarSlot--;
            }
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
