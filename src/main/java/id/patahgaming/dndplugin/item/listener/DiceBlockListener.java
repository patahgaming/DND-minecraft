package id.patahgaming.dndplugin.item.listener;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.engine.combat.PendingRoll;
import id.patahgaming.dndplugin.engine.dice.DiceRoller;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
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

        // Consume 1 dice from hand (will be returned after animation)
        item.setAmount(item.getAmount() - 1);
        if (item.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }

        // Play dice animation
        Location diceLoc = event.getBlock().getLocation().add(0.5, 0, 0.5);
        playDiceAnimation(player, diceLoc, item);
    }

    private boolean isDiceItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String type = pdc.get(KEY_ITEM_TYPE, PersistentDataType.STRING);
        return "DICE".equals(type);
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
            if (pending != null) {
                // Resolve PendingRoll via RollCommand (full flow: hit/miss → damage → next turn)
                player.sendMessage("§7🎲 Dadu memicu roll...");
                plugin.getRollCommand().executePendingRoll(player, pending);
            } else {
                // Free d20 roll (no pending)
                int roll = DiceRoller.roll(20);
                player.sendTitle("§f§l🎲 " + roll, "§7Free roll (d20)", 5, 40, 10);
                player.sendMessage("§7🎲 Dadu d20: §f" + roll);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.0f);
            }

            // Return dice to player
            ItemStack copy = diceItem.clone();
            copy.setAmount(1);
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItem(player.getLocation(), copy);
            } else {
                player.getInventory().addItem(copy);
            }

        }, 22L); // ~1.1 detik
    }

    /**
     * Create a dice item (wool block with PDC).
     */
    public static ItemStack createDiceItem() {
        ItemStack dice = new ItemStack(Material.WHITE_WOOL);
        ItemMeta meta = dice.getItemMeta();
        if (meta == null) return dice;

        meta.setDisplayName("§f🎲 Dadu D20");
        meta.setLore(java.util.List.of("§7Letakkan di tanah untuk roll", "§8[DnD Dice]"));
        meta.getPersistentDataContainer().set(KEY_ITEM_TYPE, PersistentDataType.STRING, "DICE");
        meta.getPersistentDataContainer().set(KEY_DICE_TYPE, PersistentDataType.STRING, "D20");

        dice.setItemMeta(meta);
        return dice;
    }
}
