package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.NpcDataStore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /talk <npc_id>
 *
 * Alternatif untuk memulai dialog dengan NPC penting tanpa harus
 * right-click langsung (misal NPC sedang jauh, atau untuk testing).
 */
public class TalkCommand implements CommandExecutor {

    private final DndPlugin plugin;

    public TalkCommand(DndPlugin plugin) {
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
            player.sendMessage("Gunakan: /talk <npc_id>");
            return true;
        }

        String npcId = args[0];
        NpcDataStore.NpcRecord record = plugin.getNpcDataStore().get(npcId);

        if (record == null) {
            player.sendMessage("NPC dengan id '" + npcId + "' tidak ditemukan.");
            return true;
        }

        // Panggil NpcDialogueService (DeepSeek async) — AI handle dialog + disposition
        plugin.getNpcDialogueService().generateDialogue(record, player);
        return true;
    }
}