package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.NpcDataStore;
import id.patahgaming.dndplugin.listener.NpcSpawnListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /dnd spawnnpc <template_id>
 *
 * Spawn NPC penting (story-critical) dari template YAML di data/npcs/.
 * NPC yang di-spawn punya npc_id matching template → /talk langsung bekerja.
 *
 * Kalau gak ada argumen, spawn NPC side random (flavor only).
 */
public class SpawnNpcCommand implements CommandExecutor, TabCompleter {

    private final DndPlugin plugin;

    public SpawnNpcCommand(DndPlugin plugin) {
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
            player.sendMessage("§eGunakan: /dnd spawnnpc <template_id>");
            player.sendMessage("§7Template tersedia (pakai TAB):");
            for (NpcDataStore.NpcRecord record : plugin.getNpcDataStore().getAll()) {
                player.sendMessage("§7  - " + record.npcId + " §7→ " + record.displayName);
            }
            return true;
        }

        String templateId = args[0];
        NpcDataStore.NpcRecord record = plugin.getNpcDataStore().get(templateId);

        if (record == null) {
            player.sendMessage("§cTemplate NPC '" + templateId + "' tidak ditemukan.");
            player.sendMessage("§7Pakai TAB untuk lihat daftar template.");
            return true;
        }

        // Spawn NPC di lokasi player
        World world = player.getWorld();
        Location loc = player.getLocation().add(
                player.getLocation().getDirection().normalize().multiply(2));

        Villager villager = (Villager) world.spawnEntity(loc, EntityType.VILLAGER);

        // Set PDC tags — npc_id = template id, display_name = dari YAML, type = important
        villager.getPersistentDataContainer().set(
                NpcSpawnListener.npcIdKey(), PersistentDataType.STRING, record.npcId);
        villager.getPersistentDataContainer().set(
                NpcSpawnListener.npcNameKey(), PersistentDataType.STRING, record.displayName);
        villager.getPersistentDataContainer().set(
                NpcSpawnListener.npcTypeKey(), PersistentDataType.STRING, NpcSpawnListener.NPC_TYPE_IMPORTANT);

        // Important NPC = gold bold §6§l
        villager.customName(Component.text("§6§l" + record.displayName));
        villager.setCustomNameVisible(true);
        villager.setAware(true);

        // Set profession random biar gak monoton
        Villager.Profession[] profs = Villager.Profession.values();
        villager.setProfession(profs[(int) (Math.random() * profs.length)]);

        player.sendMessage("§aSpawned NPC: " + record.displayName + " §7(id: " + record.npcId + ")");
        player.sendMessage("§7Gunakan §e/talk " + record.npcId + "§7 untuk dialog.");
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
            for (NpcDataStore.NpcRecord record : plugin.getNpcDataStore().getAll()) {
                if (record.npcId.toLowerCase().startsWith(input)) {
                    suggestions.add(record.npcId);
                }
            }
            suggestions.sort(String::compareToIgnoreCase);
            return suggestions;
        }
        return List.of();
    }
}
