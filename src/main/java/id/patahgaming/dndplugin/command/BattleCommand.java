package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.engine.combat.TurnManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /battle <start|flee|status|forceend>
 *
 * start    — inisiasi battle dengan mobs di sekitar player (radius 15 block)
 * flee     — coba kabur dari battle (DEX check)
 * status   — lihat status battle saat ini
 * forceend — force-stop battle (admin debug softlock guard)
 */
public class BattleCommand implements CommandExecutor, TabCompleter {

    private final DndPlugin plugin;

    public BattleCommand(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommand ini hanya bisa dijalankan oleh player.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§eGunakan: /battle <start|flee|status|forceend>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "start" -> handleStart(player);
            case "flee" -> handleFlee(player);
            case "status" -> handleStatus(player);
            case "forceend" -> handleForceEnd(player);
            default -> player.sendMessage("§cUnknown: " + sub + ". Use: start, flee, status, forceend");
        }
        return true;
    }

    private void handleStart(Player player) {
        TurnManager tm = plugin.getTurnManager();
        if (tm.getBattleState() == TurnManager.BattleState.ACTIVE) {
            player.sendMessage("§cBattle sudah berlangsung! " + tm.getCurrentTurnDisplay());
            return;
        }

        // Kumpulin monster di sekitar player
        List<LivingEntity> targets = new ArrayList<>();
        for (var entity : player.getNearbyEntities(15, 8, 15)) {
            if (entity instanceof Monster
                    && entity instanceof LivingEntity living
                    && !living.isDead()) {
                targets.add(living);
            }
        }

        if (targets.isEmpty()) {
            player.sendMessage("§7Tidak ada monster di sekitar. Cari musuh dulu!");
            return;
        }

        // Include all online players sebagai party
        List<LivingEntity> participants = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            participants.add(p);
        }
        participants.addAll(targets);

        // Start battle! (TurnManager.startBattle handles broadcast + first turn)
        tm.startBattle(participants);

        player.sendMessage("§aGunakan §e/atk melee §aatau §e/atk ranged §auntuk menyerang!");
        player.sendMessage("§7Lihat turn order: §e/battle status");
    }

    private void handleFlee(Player player) {
        TurnManager tm = plugin.getTurnManager();
        if (tm.getBattleState() != TurnManager.BattleState.ACTIVE) {
            player.sendMessage("§7Tidak ada battle untuk di-flee.");
            return;
        }

        if (!tm.isPlayerTurn(player)) {
            player.sendMessage("§cTunggu giliran kamu untuk flee!");
            return;
        }

        // DEX check DC 12 untuk kabur
        int dexMod = plugin.getPlayerDataStore().getAbilityModifier(player, "dex");
        int roll = id.patahgaming.dndplugin.dice.DiceRoller.roll(20);
        int total = roll + dexMod;

        if (roll == 20 || total >= 12) {
            Bukkit.broadcastMessage("§e" + player.getName() + " flees from battle! [d20:" + roll + "+" + dexMod + "=" + total + "]");
            tm.removeParticipant(player);
            if (tm.getParticipants().values().stream().noneMatch(e -> e instanceof Player)) {
                tm.endBattle();
            }
        } else {
            player.sendMessage("§cGagal flee! [d20:" + roll + "+" + dexMod + "=" + total + " < DC12]");
            tm.selectAction(player);
            tm.resolveAction();
            player.sendMessage("§7Giliran kamu terbuang karena gagal kabur.");
        }
    }

    /**
     * Force-stop battle — jaga-jaga softlock.
     * Unfreeze semua mob, stabilize unconscious players, clear state.
     */
    private void handleForceEnd(Player player) {
        TurnManager tm = plugin.getTurnManager();
        if (tm.getBattleState() != TurnManager.BattleState.ACTIVE) {
            player.sendMessage("§7Tidak ada battle aktif untuk di-force end.");
            return;
        }
        tm.endBattle();
        Bukkit.broadcastMessage("§e⚔ Battle di-force end oleh " + player.getName() + ".");
        plugin.getLogger().warning("[Battle] Force-ended by " + player.getName());
    }

    private void handleStatus(Player player) {
        TurnManager tm = plugin.getTurnManager();
        if (tm.getBattleState() != TurnManager.BattleState.ACTIVE) {
            player.sendMessage("§7Tidak ada battle aktif.");
            return;
        }

        player.sendMessage("§c=== Battle Status ===§r");
        player.sendMessage("§7Round: " + tm.getRoundNumber() + " | Turn: " + tm.getCurrentTurnDisplay());
        player.sendMessage("§7Turn Order:");
        int idx = 0;
        for (var entry : tm.getTurnOrder()) {
            var entity = tm.getParticipants().get(entry.entityUuid);
            String marker = idx == tm.getCurrentTurnIndex() ? " §e◄ NOW" : "";
            String hp = entity != null ? " §7[HP:" + (int) entity.getHealth() + "]" : "";
            player.sendMessage("  " + (idx + 1) + ". " + entry.displayName
                    + " §7(" + entry.total + ")" + hp + marker);
            idx++;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> subs = new ArrayList<>();
            for (String s : List.of("start", "flee", "status", "forceend")) {
                if (s.startsWith(input)) subs.add(s);
            }
            return subs;
        }
        return List.of();
    }
}
