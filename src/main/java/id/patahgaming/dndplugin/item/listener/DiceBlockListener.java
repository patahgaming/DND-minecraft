package id.patahgaming.dndplugin.item.listener;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.dice.DiceItemFactory;
import id.patahgaming.dndplugin.dice.DiceType;
import id.patahgaming.dndplugin.engine.combat.PendingRoll;
import id.patahgaming.dndplugin.engine.dice.DiceRoller;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

import java.util.Random;

/**
 * Physical dice — player tempatkan "dadu" wool block → animasi roll → trigger /roll.
 *
 * ItemStack dadu:
 *   material: WHITE_WOOL
 *   PDC: dnd:item_type = DICE, dnd:dice_type = D20
 */
public class DiceBlockListener implements Listener {

    private static final NamespacedKey KEY_ITEM_TYPE = new NamespacedKey("dndplugin", "item_type");
    private static final NamespacedKey KEY_DICE_TYPE = new NamespacedKey("dndplugin", "dice_type");
    private final DndPlugin plugin;
    private final Random random = new Random();

    public DiceBlockListener(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDicePlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        // Cek item ada PDC dnd:item_type = DICE
        if (!isDiceItem(item)) return;

        // Cancel block place vanilla — jangan taruh wool beneran
        event.setCancelled(true);

        // CLONE dulu — getItemInHand() return snapshot di Paper modern,
        // kalau amount jadi 0 ItemMeta hilang → getDiceType() return null → "D?"
        ItemStack diceSnapshot = item.clone();

        // Consume 1 dice from hand (will be returned after animation)
        item.setAmount(item.getAmount() - 1);
        if (item.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }

        // Play dice animation dengan SNAPSHOT (masih punya amount + PDC valid)
        Location diceLoc = event.getBlock().getLocation().add(0.5, 0, 0.5);
        playDiceAnimation(player, diceLoc, diceSnapshot);
    }

    /** Cegah dadu di-drop — dadu permanen, gak bisa dibuang. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDiceDrop(PlayerDropItemEvent event) {
        if (DiceItemFactory.isDice(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c🎲 Dadu tidak bisa dibuang! Dadu selalu ada di hotbarmu.");
        }
    }

    /** Cegah dadu dipindah ke luar inventory (crafting table, chest, dll). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDiceMove(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // Cegah dadu ditaruh di luar player inventory (chest, crafting, etc.)
        if (event.getClickedInventory() != null
                && event.getClickedInventory().getType() != InventoryType.PLAYER
                && event.getClickedInventory().getType() != InventoryType.CRAFTING) {
            if (DiceItemFactory.isDice(cursor) || DiceItemFactory.isDice(current)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isDiceItem(ItemStack item) {
        return DiceItemFactory.isDice(item);
    }

    private void returnDice(Player player, ItemStack diceItem) {
        ItemStack copy = diceItem.clone();
        copy.setAmount(1);
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), copy);
        } else {
            player.getInventory().addItem(copy);
        }
    }

    private void playDiceAnimation(Player player, Location loc, ItemStack diceItem) {
        World world = loc.getWorld();
        if (world == null) return;

        // Spawn tiny ArmorStand with colored helmet (dice visual)
        ArmorStand stand = world.spawn(loc.add(0, 0.5, 0), ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setSmall(true);
            as.setMarker(true);
            as.setInvulnerable(true);
            as.setPersistent(false); // jangan survive chunk unload / server restart
        });

        // Use colored glass as dice face (based on dice item material or default white)
        Material displayMat = diceItem.getType();
        if (!displayMat.isBlock()) displayMat = Material.WHITE_WOOL;
        ItemStack headItem = new ItemStack(displayMat);
        stand.getEquipment().setHelmet(headItem);

        // Animation: 5 iterations, 200ms each (1 second total)
        int iterations = 5;
        for (int i = 0; i < iterations; i++) {
            final int iter = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Random rotation
                double yaw = random.nextDouble() * 360;
                double pitch = random.nextDouble() * 360;
                stand.setHeadPose(new EulerAngle(
                        Math.toRadians(pitch),
                        Math.toRadians(yaw),
                        0
                ));

                // Particles
                world.spawnParticle(Particle.CRIT,
                        stand.getLocation().add(0, 0.5, 0),
                        8, 0.3, 0.3, 0.3, 0.05);

                // Sound
                float pitchSound = 0.5f + (iter * 0.3f);
                world.playSound(stand.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, pitchSound);

            }, 4L * i); // 4 ticks = 200ms
        }

        // Final resolve after 1 second
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Final sound
            world.playSound(stand.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING,
                    stand.getLocation().add(0, 0.5, 0),
                    15, 0.5, 0.5, 0.5, 0.1);

            // Remove armor stand
            stand.remove();

            // Trigger roll untuk player — sama seperti /roll command
            PendingRoll pending = PendingRoll.get(player);
            DiceType placedDice = DiceItemFactory.getDiceType(diceItem);
            if (pending != null) {
                // Check dice type match
                DiceType required = DiceType.fromDiceExpr(pending.diceExpr);
                if (placedDice != required && required != null) {
                    player.sendMessage("§c⚠ Salah dadu! Butuh " + required.getColor() + required.getLabel()
                            + " (" + required.getColorName() + " Wool)§c, kamu letakkan "
                            + (placedDice != null ? placedDice.getColor() + placedDice.getLabel() : "?") + ".");
                    // Tetap kembalikan dadu
                    returnDice(player, diceItem);
                    return;
                }
                // Resolve PendingRoll via RollCommand (full flow)
                player.sendMessage("§7🎲 Dadu memicu roll...");
                plugin.getRollCommand().executePendingRoll(player, pending);
            } else {
                // Free roll sesuai tipe dadu
                int sides = placedDice != null ? placedDice.getSides() : 20;
                int roll = DiceRoller.roll(sides);
                String color = placedDice != null ? placedDice.getColor() : "§f";
                String label = placedDice != null ? placedDice.getLabel() : "D?";
                player.sendTitle(color + "🎲 " + roll, "§7Free roll (" + label + ")", 5, 40, 10);
                player.sendMessage("§7🎲 " + color + label + ": §f" + roll);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.0f);
            }

            returnDice(player, diceItem);

        }, 22L); // ~1.1 detik
    }

}
