package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.dice.DiceItemFactory;
import id.patahgaming.dndplugin.dice.DiceRoller;
import id.patahgaming.dndplugin.dice.DiceType;
import id.patahgaming.dndplugin.engine.combat.PendingRoll;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * /dice — Roll dadu di tangan via command.
 * Fallback untuk Bedrock Edition player yang RIGHT_CLICK_BLOCK-nya
 * tidak terkirim saat pegang block item di Adventure mode.
 *
 * Juga bisa /dice melee|ranged untuk combat roll.
 */
public class DiceCommand implements CommandExecutor, TabCompleter {

    private final DndPlugin plugin;

    public DiceCommand(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cHanya player.");
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        DiceType diceType = DiceItemFactory.getDiceType(hand);

        if (diceType == null) {
            player.sendMessage("§cKamu harus memegang dadu di main hand!");
            player.sendMessage("§7Dadu ada di hotbar slot 3-8 (D4 D6 D8 D10 D12 D20)");
            return true;
        }

        String diceLabel = diceType.getLabel();
        String diceColor = diceType.getColor();
        int sides = diceType.getSides();

        // Consume 1 dari stack
        ItemStack diceSnapshot = hand.clone();
        hand.setAmount(hand.getAmount() - 1);
        if (hand.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }

        // Play sound & particle sederhana (no ArmorStand — Bedrock compatible)
        Location loc = player.getLocation();
        player.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 0.8f);
        player.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.2f);
        player.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.4f, 0.6f);

        // Resolve roll
        PendingRoll pending = PendingRoll.get(player);
        if (pending != null) {
            DiceType required = DiceType.fromDiceExpr(pending.diceExpr);
            if (diceType != required && required != null) {
                Bukkit.broadcastMessage("§c⚠ " + player.getName() + " salah dadu! Butuh "
                        + required.getColor() + required.getLabel() + "§c.");
                returnDice(player, diceSnapshot);
                return true;
            }
            Bukkit.broadcastMessage("§7🎲 " + diceColor + player.getName()
                    + " §7memutuskan nasib dengan " + diceLabel + "...");
            plugin.getRollCommand().executePendingRoll(player, pending);
        } else {
            int roll = DiceRoller.roll(sides);
            String title = diceColor + "§l🎲 " + roll;
            String sub = "§7" + player.getName() + " rolls " + diceLabel;
            player.sendTitle(title, sub, 5, 40, 15);
            Bukkit.broadcastMessage("§7🎲 " + diceColor + player.getName()
                    + " §7rolls " + diceLabel + ": " + diceColor + "§l" + roll);
            Bukkit.getOnlinePlayers().forEach(p ->
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.0f));
        }

        // Return dice ke inventory
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            returnDice(player, diceSnapshot);
        }, 2L);

        return true;
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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        return List.of();
    }
}
