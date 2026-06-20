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
 * /battle <start|flee|status|forceend|duel|pvp>
 *
 * start    — inisiasi PvE battle dengan mobs di sekitar player (radius 15 block)
 * flee     — coba kabur dari battle (DEX check)
 * status   — lihat status battle saat ini
 * forceend — force-stop battle (admin debug softlock guard)
 * duel <player> — 1v1 PvP duel melawan player lain
 * pvp <ally> <enemy1> [enemy2...] — team PvP (sender + ally vs enemies)
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
            player.sendMessage("§eGunakan: /battle <start|flee|status|forceend|duel|pvp>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "start" -> handleStart(player);
            case "flee" -> handleFlee(player);
            case "status" -> handleStatus(player);
            case "forceend" -> handleForceEnd(player);
            case "duel" -> handleDuel(player, args);
            case "pvp" -> handlePvP(player, args);
            default -> player.sendMessage("§cUnknown: " + sub + ". Use: start, flee, status, forceend, duel, pvp");
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

    /** /battle duel <player> — 1v1 PvP duel. */
    private void handleDuel(Player challenger, String[] args) {
        TurnManager tm = plugin.getTurnManager();
        if (tm.getBattleState() == TurnManager.BattleState.ACTIVE) {
            challenger.sendMessage("§cBattle sudah berlangsung! " + tm.getCurrentTurnDisplay());
            return;
        }

        if (args.length < 2) {
            challenger.sendMessage("§e/battle duel <player> §7— tantang player lain duel 1v1");
            return;
        }

        Player opponent = Bukkit.getPlayer(args[1]);
        if (opponent == null) {
            challenger.sendMessage("§cPlayer tidak ditemukan: " + args[1]);
            return;
        }
        if (opponent.equals(challenger)) {
            challenger.sendMessage("§cKamu tidak bisa duel dengan diri sendiri!");
            return;
        }

        // Mulai duel
        tm.startDuel(challenger, opponent);

        challenger.sendMessage("§aGunakan §e/atk melee §aatau §e/atk ranged §auntuk menyerang!");
        challenger.sendMessage("§7Lihat turn order: §e/battle status");
    }

    /** /battle pvp <ally> <enemy1> [enemy2...] — team PvP.
     *  Sender + ally = team 1, enemies = team 2. */
    private void handlePvP(Player sender, String[] args) {
        TurnManager tm = plugin.getTurnManager();
        if (tm.getBattleState() == TurnManager.BattleState.ACTIVE) {
            sender.sendMessage("§cBattle sudah berlangsung! " + tm.getCurrentTurnDisplay());
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§e/battle pvp <ally> <enemy1> [enemy2...]");
            sender.sendMessage("§7Contoh: /battle pvp Aric Lyra → kamu + Aric vs Lyra (2v1)");
            sender.sendMessage("§7Contoh: /battle pvp Aric Lyra Mira → kamu + Aric vs Lyra + Mira (2v2)");
            return;
        }

        // Parse teams: sender + ally = team 1, enemies = team 2
        List<Player> team1 = new ArrayList<>();
        team1.add(sender);

        Player ally = Bukkit.getPlayer(args[1]);
        if (ally != null && !ally.equals(sender)) {
            team1.add(ally);
        } else if (ally == null) {
            sender.sendMessage("§cAlly tidak ditemukan: " + args[1]);
            return;
        }

        List<Player> team2 = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            Player enemy = Bukkit.getPlayer(args[i]);
            if (enemy == null) {
                sender.sendMessage("§cPlayer tidak ditemukan: " + args[i]);
                return;
            }
            if (enemy.equals(sender) || team1.contains(enemy)) {
                sender.sendMessage("§cPlayer tidak bisa di dua team sekaligus: " + args[i]);
                return;
            }
            team2.add(enemy);
        }

        if (team2.isEmpty()) {
            sender.sendMessage("§cMinimal 1 musuh dibutuhkan!");
            return;
        }

        // Build team list for TurnManager
        List<List<Player>> teams = List.of(team1, team2);
        tm.startPvPBattle(teams);

        sender.sendMessage("§aGunakan §e/atk melee §aatau §e/atk ranged §auntuk menyerang!");
        sender.sendMessage("§7Lihat turn order: §e/battle status");
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
            for (String s : List.of("start", "flee", "status", "forceend", "duel", "pvp")) {
                if (s.startsWith(input)) subs.add(s);
            }
            return subs;
        }
        // Player name completion for duel and pvp
        if (args.length >= 2 && (args[0].equalsIgnoreCase("duel") || args[0].equalsIgnoreCase("pvp"))) {
            String input = args[args.length - 1].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender) && p.getName().toLowerCase().startsWith(input)) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
