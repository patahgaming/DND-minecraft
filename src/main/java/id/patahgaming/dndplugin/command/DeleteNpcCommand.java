package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.NpcDataStore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * /deletenpc <npc_id>
 *
 * Hapus NPC: remove dari NpcDataStore + delete YAML file + kill entity di world.
 */
public class DeleteNpcCommand implements CommandExecutor, TabCompleter {

    private final DndPlugin plugin;

    public DeleteNpcCommand(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cHanya player yang bisa menggunakan command ini.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage("§cHanya operator yang bisa menghapus NPC.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§e/deletenpc <npc_id>");
            player.sendMessage("§7Gunakan TAB untuk lihat daftar NPC.");
            return true;
        }

        String npcId = args[0].toLowerCase();
        NpcDataStore.NpcRecord record = plugin.getNpcDataStore().get(npcId);

        if (record == null) {
            player.sendMessage("§cNPC tidak ditemukan: " + npcId);
            return true;
        }

        // Hapus dari data store + YAML
        boolean removed = plugin.getNpcDataStore().remove(npcId);
        if (!removed) {
            player.sendMessage("§cGagal menghapus NPC: " + npcId);
            return true;
        }

        // Kill semua entity di world yang punya PDC npc_id = npcId
        int killed = 0;
        for (var world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Player) continue;
                var pdc = entity.getPersistentDataContainer();
                String tagId = pdc.get(new NamespacedKey("dndplugin", "npc_id"), PersistentDataType.STRING);
                if (npcId.equals(tagId)) {
                    entity.remove();
                    killed++;
                }
            }
        }

        player.sendMessage("§a✅ NPC §f'" + record.displayName + "' §adihapus! §8[" + npcId + "]");
        if (killed > 0) {
            player.sendMessage("§7  " + killed + " entity dihapus dari world.");
        }

        plugin.getLogger().info("[NPC] " + player.getName() + " deleted NPC: " + npcId);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return plugin.getNpcDataStore().getAll().stream()
                    .map(r -> r.npcId)
                    .filter(id -> id.toLowerCase().startsWith(input))
                    .sorted().toList();
        }
        return List.of();
    }
}
