package id.patahgaming.dndplugin.item.listener;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.dice.DiceItemFactory;
import id.patahgaming.dndplugin.dice.DiceType;
import id.patahgaming.dndplugin.engine.combat.PendingRoll;
import id.patahgaming.dndplugin.dice.DiceRoller;
import id.patahgaming.dndplugin.DndPlugin;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDicePlace(PlayerInteractEvent event) {
        // Handle dice placement (RIGHT_CLICK_BLOCK)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            ItemStack item = event.getItem();
            if (item == null) return;

            // Dice item → play dice animation
            if (isDiceItem(item)) {
                handleDicePlace(event, player, item);
                return;
            }

            // Consumable item → drink/eat
            if (isConsumable(item)) {
                handleConsumableUse(event, player, item);
                return;
            }
        }

        // Handle consumable right-click air
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            Player player = event.getPlayer();
            ItemStack item = event.getItem();
            if (item == null) return;

            if (isConsumable(item)) {
                handleConsumableUse(event, player, item);
            }
        }
    }

    private void handleDicePlace(PlayerInteractEvent event, Player player, ItemStack item) {
        // Cancel interaction — jangan taruh wool beneran (Adventure mode compat)
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
        Location diceLoc = event.getClickedBlock().getLocation().add(0.5, 0, 0.5);
        playDiceAnimation(player, diceLoc, diceSnapshot);
    }

    /**
     * Right-click consumable → heal player + consume item.
     */
    private void handleConsumableUse(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);

        // Read heal_dice from PDC
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String healDiceStr = pdc.get(
                new NamespacedKey("dndplugin", "heal_dice"),
                org.bukkit.persistence.PersistentDataType.STRING);
        if (healDiceStr == null || healDiceStr.isEmpty()) {
            player.sendMessage("§cItem konsumabel ini tidak memiliki heal_dice.");
            return;
        }

        // Parse heal dice: "2d4+2" or "4d4+4" or "1d8"
        int amount = 0;
        int sides = 0;
        int bonus = 0;
        try {
            String[] parts = healDiceStr.split("d");
            amount = Integer.parseInt(parts[0]);
            if (parts[1].contains("+")) {
                String[] split = parts[1].split("\\+");
                sides = Integer.parseInt(split[0]);
                bonus = Integer.parseInt(split[1]);
            } else if (parts[1].contains("-")) {
                String[] split = parts[1].split("-");
                sides = Integer.parseInt(split[0]);
                bonus = -Integer.parseInt(split[1]);
            } else {
                sides = Integer.parseInt(parts[1]);
            }
        } catch (Exception e) {
            player.sendMessage("§cGagal parse heal_dice: " + healDiceStr);
            return;
        }

        // Roll heal
        int heal = DiceRoller.rollDice(amount, sides, bonus);
        heal = Math.max(1, heal); // minimum 1

        // Apply heal
        double maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        double newHp = Math.min(maxHp, player.getHealth() + heal);
        player.setHealth(newHp);

        // Effects
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.8f, 1.2f);
        player.getWorld().spawnParticle(Particle.HEART,
                player.getLocation().add(0, 1.5, 0),
                8, 0.5, 0.5, 0.5, 0.1);

        // Title notification
        player.sendTitle("§c❤ +" + heal + " HP", "§7" + healDiceStr + " → " + heal, 5, 30, 15);

        // Consume 1 item
        item.setAmount(item.getAmount() - 1);
        if (item.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }

        // Update scoreboard HP
        id.patahgaming.dndplugin.ui.ScoreboardManager.refresh(
                DndPlugin.getInstance(), player);

        // Broadcast
        String itemName = item.getItemMeta().getDisplayName();
        Bukkit.broadcastMessage("§c🧪 " + player.getName() + " §7menggunakan "
                + (itemName != null ? itemName : "§fitem") + " §7→ §c❤ +" + heal + " HP");
    }

    /** Check if an ItemStack is a DND consumable item. */
    private boolean isConsumable(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String typeStr = pdc.get(KEY_ITEM_TYPE, org.bukkit.persistence.PersistentDataType.STRING);
        return "CONSUMABLE".equalsIgnoreCase(typeStr);
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

        // Dice info
        DiceType placedDice = DiceItemFactory.getDiceType(diceItem);
        String diceColor = placedDice != null ? placedDice.getColor() : "§f";
        String diceLabel = placedDice != null ? placedDice.getLabel() : "?";

        // Animation: 8 iterations, 150ms each (1.2 seconds total) — lebih lebai
        int iterations = 8;
        for (int i = 0; i < iterations; i++) {
            final int iter = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Random rotation — spin makin cepat
                double yaw = random.nextDouble() * 360 * (iter + 1);
                double pitch = random.nextDouble() * 360;
                double roll = random.nextDouble() * 360;
                stand.setHeadPose(new EulerAngle(
                        Math.toRadians(pitch),
                        Math.toRadians(yaw),
                        Math.toRadians(roll)
                ));

                // Bounce effect — stand naik turun
                double bounceY = Math.sin(iter * Math.PI / 4) * 0.4;
                stand.teleport(stand.getLocation().add(0, bounceY, 0));

                // Particles — makin banyak tiap iterasi
                world.spawnParticle(Particle.CRIT,
                        stand.getLocation().add(0, 0.3, 0),
                        5 + iter * 3, 0.4, 0.4, 0.4, 0.05);
                world.spawnParticle(Particle.ENCHANTED_HIT,
                        stand.getLocation().add(0, 0.5, 0),
                        3 + iter, 0.2, 0.2, 0.2, 0.1);

                // Sound — ascending pitch + bass drum
                float pitchSound = 0.5f + (iter * 0.15f);
                world.playSound(stand.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, pitchSound);
                if (iter % 2 == 0) {
                    world.playSound(stand.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.3f, 0.8f);
                }

                // Progress bar hanya ke player sendiri (title), jangan spam chat

            }, 3L * i); // 3 ticks = 150ms
        }

        // Final resolve after ~1.2 detik
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Grand finale effects
            world.playSound(stand.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
            world.playSound(stand.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.5f, 1.0f);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING,
                    stand.getLocation().add(0, 0.5, 0),
                    20, 0.6, 0.6, 0.6, 0.15);
            world.spawnParticle(Particle.END_ROD,
                    stand.getLocation().add(0, 0.3, 0),
                    10, 0.3, 0.3, 0.3, 0.08);

            // Remove armor stand
            stand.remove();

            // Resolve roll
            PendingRoll pending = PendingRoll.get(player);
            if (pending != null) {
                DiceType required = DiceType.fromDiceExpr(pending.diceExpr);
                if (placedDice != required && required != null) {
                    Bukkit.broadcastMessage("§c⚠ " + player.getName() + " salah dadu! Butuh "
                            + required.getColor() + required.getLabel() + "§c.");
                    returnDice(player, diceItem);
                    return;
                }
                Bukkit.broadcastMessage("§7🎲 " + diceColor + player.getName()
                        + " §7memutuskan nasib dengan " + diceLabel + "...");
                plugin.getRollCommand().executePendingRoll(player, pending);
            } else {
                int sides = placedDice != null ? placedDice.getSides() : 20;
                int roll = DiceRoller.roll(sides);
                String title = diceColor + "§l🎲 " + roll;
                String sub = "§7" + player.getName() + " rolls " + diceLabel;
                // Show title to roller, broadcast to all
                player.sendTitle(title, sub, 5, 60, 20);
                Bukkit.broadcastMessage("§7🎲 " + diceColor + player.getName()
                        + " §7rolls " + diceLabel + ": " + diceColor + "§l" + roll);
                Bukkit.getOnlinePlayers().forEach(p ->
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.0f));
            }

            returnDice(player, diceItem);

        }, 25L); // ~1.25 detik
    }

}
