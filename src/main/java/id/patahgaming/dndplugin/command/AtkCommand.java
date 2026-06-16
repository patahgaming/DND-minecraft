package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.engine.combat.TurnManager;
import id.patahgaming.dndplugin.engine.dice.DiceRoller;
import id.patahgaming.dndplugin.ui.TitleDisplay;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * /atk <mob_id>
 *
 * Serangan saat giliran combat aktif. Validasi giliran via TurnManager.
 * Attack roll = d20 + STR modifier (default melee).
 */
public class AtkCommand implements CommandExecutor {

    private final DndPlugin plugin;

    public AtkCommand(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya bisa dijalankan oleh player.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("Gunakan: /atk <mob_id>");
            return true;
        }

        // Validasi giliran via TurnManager
        TurnManager tm = plugin.getTurnManager();
        if (tm.getBattleState() != TurnManager.BattleState.ACTIVE) {
            player.sendMessage("§cTidak ada battle yang sedang berlangsung.");
            player.sendMessage("§7Gunakan /atk hanya saat combat aktif.");
            return true;
        }

        if (!tm.isPlayerTurn(player)) {
            player.sendMessage("§cBukan giliran kamu! Tunggu giliran.");
            return true;
        }

        tm.selectAction(player);

        String mobId = args[0];
        LivingEntity target = findTarget(mobId);

        if (target == null) {
            player.sendMessage("§cMob dengan id '" + mobId + "' tidak ditemukan.");
            return true;
        }

        // Attack roll: d20 + STR modifier
        int strMod = plugin.getPlayerDataStore().getAbilityModifier(player, "str");
        int attackRoll = DiceRoller.roll(20);
        int totalAttack = attackRoll + strMod;

        // Damage: weapon damage (d8 default) + STR modifier
        int damageRoll = DiceRoller.roll(8);
        int totalDamage = Math.max(1, damageRoll + strMod);

        // Kritikal? Nat 20 = double damage
        boolean isCrit = attackRoll == 20;
        if (isCrit) {
            totalDamage *= 2;
        }

        // Apply damage ke target
        double newHp = Math.max(0, target.getHealth() - totalDamage);
        target.setHealth(newHp);

        // Title display
        String mainText = isCrit ? "§e§lCRIT! " + totalAttack : "Attack: " + totalAttack;
        String subText = "DMG: " + totalDamage + " | " + target.getName() + " HP: " + (int) newHp;
        TitleDisplay.showCombatResult(player, mainText, subText);

        // Broadcast hasil ke semua player
        String critMsg = isCrit ? " §e§lCRITICAL HIT!" : "";
        Bukkit.broadcastMessage("§c[Combat]§r " + player.getName() + " menyerang "
                + target.getName() + "! Roll: " + attackRoll + "+" + strMod
                + " = " + totalAttack + " | DMG: " + totalDamage + critMsg);

        // Cek target mati
        if (newHp <= 0) {
            Bukkit.broadcastMessage("§c" + target.getName() + " has been slain!");
        }

        // Resolve aksi
        tm.resolveAction();
        return true;
    }

    /**
     * Cari target dari participants TurnManager berdasarkan UUID prefix atau nama.
     */
    private LivingEntity findTarget(String mobId) {
        TurnManager tm = plugin.getTurnManager();
        for (var entry : tm.getParticipants().entrySet()) {
            UUID uuid = entry.getKey();
            LivingEntity entity = entry.getValue();
            // Skip player targets (players shouldn't attack other players normally)
            if (entity instanceof Player) continue;

            String uuidStr = uuid.toString().substring(0, Math.min(8, mobId.length()));
            if (uuidStr.equalsIgnoreCase(mobId.substring(0, Math.min(8, uuidStr.length())))) {
                return entity;
            }
            if (entity.getName() != null && entity.getName().equalsIgnoreCase(mobId)) {
                return entity;
            }
        }
        return null;
    }
}