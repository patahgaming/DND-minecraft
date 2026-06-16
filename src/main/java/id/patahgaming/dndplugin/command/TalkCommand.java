package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.NpcDataStore;
import id.patahgaming.dndplugin.listener.NpcSpawnListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /talk <npc_id>
 *
 * Memulai dialog dengan NPC penting (story-critical) atau NPC side
 * yang ada di sekitar. Support TabCompleter untuk auto-complete
 * NPC id dari data store + NPC terdekat yang di-tag.
 */
public class TalkCommand implements CommandExecutor, TabCompleter {

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
            player.sendMessage("Gunakan: /talk <npc_id> (pakai TAB untuk auto-complete)");
            return true;
        }

        String npcId = args[0];
        NpcDataStore.NpcRecord record = plugin.getNpcDataStore().get(npcId);

        if (record == null) {
            player.sendMessage("NPC dengan id '" + npcId + "' tidak ditemukan.");
            player.sendMessage("§7Gunakan TAB untuk melihat daftar NPC yang tersedia.");
            return true;
        }

        // Panggil NpcDialogueService (DeepSeek async) — AI handle dialog + disposition
        plugin.getNpcDialogueService().generateDialogue(record, player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String input = args[0].toLowerCase();

            // 1. NPC penting dari NpcDataStore (story-critical, terdaftar di YAML)
            for (NpcDataStore.NpcRecord record : plugin.getNpcDataStore().getAll()) {
                if (record.npcId.toLowerCase().startsWith(input)) {
                    suggestions.add(record.npcId);
                }
            }

            // 2. NPC terdekat yang di-tag (side NPC atau important NPC yang sedang spawn)
            if (sender instanceof Player player) {
                for (Entity entity : player.getNearbyEntities(20, 10, 20)) {
                    if (entity.getPersistentDataContainer().has(
                            NpcSpawnListener.npcIdKey(), PersistentDataType.STRING)) {
                        String nearbyId = entity.getPersistentDataContainer().get(
                                NpcSpawnListener.npcIdKey(), PersistentDataType.STRING);
                        if (nearbyId != null && nearbyId.toLowerCase().startsWith(input)
                                && !suggestions.contains(nearbyId)) {
                            suggestions.add(nearbyId);
                        }
                    }
                }
            }

            suggestions.sort(String::compareToIgnoreCase);
            return suggestions;
        }
        return List.of();
    }
}