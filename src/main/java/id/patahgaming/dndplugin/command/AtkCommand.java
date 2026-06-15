package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /atk <mob_id>
 *
 * Serangan saat giliran combat aktif. Validasi giliran & resolusi combat
 * di-handle oleh TurnManager (lihat engine.combat — TODO).
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

        String mobId = args[0];

        // TODO: validasi giliran via TurnManager, resolve attack roll vs target AC,
        // update HP via PlayerDataStore, tampilkan hasil via TitleDisplay.
        player.sendMessage("Menyerang " + mobId + "... (combat engine belum diimplementasi)");
        return true;
    }
}