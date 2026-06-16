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

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /newnpc <npc_id> <display_name> [spawn]
 *
 * Membuat NPC penting baru:
 * 1. Bikin template YAML di data/npcs/<npc_id>.yml
 * 2. Daftarin ke NpcDataStore in-memory
 * 3. Kalau ada arg "spawn", langsung spawn di lokasi player
 *
 * Contoh:
 *   /newnpc blacksmith_garin "Garin the Blacksmith"
 *   /newnpc forest_witch "Morgana the Witch" spawn
 */
public class NewNpcCommand implements CommandExecutor, TabCompleter {

    private final DndPlugin plugin;

    public NewNpcCommand(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommand ini hanya bisa dijalankan oleh player.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§eGunakan: /newnpc <npc_id> <display_name> [spawn]");
            player.sendMessage("§7Contoh: /newnpc blacksmith_garin \"Garin the Blacksmith\" spawn");
            player.sendMessage("§7  npc_id: lowercase + underscore (blacksmith_garin)");
            player.sendMessage("§7  display_name: nama yang muncul di game (bisa pakai spasi)");
            player.sendMessage("§7  spawn: optional, langsung spawn NPC");
            return true;
        }

        String npcId = args[0].toLowerCase().replace(" ", "_");

        // Gabungin sisa args jadi display_name (kecuali "spawn" di akhir)
        boolean doSpawn = false;
        int nameEnd = args.length;
        if (args[args.length - 1].equalsIgnoreCase("spawn")) {
            doSpawn = true;
            nameEnd = args.length - 1;
        }

        String displayName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, nameEnd));

        // Validasi: jangan overwrite existing
        NpcDataStore.NpcRecord existing = plugin.getNpcDataStore().get(npcId);
        if (existing != null) {
            player.sendMessage("§cNPC dengan id '" + npcId + "' sudah ada!");
            player.sendMessage("§7Nama: " + existing.displayName);
            player.sendMessage("§7Gunakan /spawnnpc " + npcId + " untuk spawn.");
            return true;
        }

        // 1. Bikin record di NpcDataStore
        NpcDataStore.NpcRecord record = plugin.getNpcDataStore().getOrCreate(npcId, displayName);
        record.disposition = "neutral";
        record.storyNode = plugin.getPlayerDataStore().getPartyCurrentNode(); // ikut story node saat ini

        // 2. Simpan ke file YAML
        saveNpcTemplate(record);

        player.sendMessage("§aNPC template created: §6§l" + displayName + " §7(id: " + npcId + ")");
        player.sendMessage("§7File: data/npcs/" + npcId + ".yml");
        player.sendMessage("§7Gunakan §e/spawnnpc " + npcId + "§7 untuk spawn kapan saja.");
        player.sendMessage("§7Gunakan §e/talk " + npcId + "§7 untuk dialog.");

        // 3. Spawn kalau diminta
        if (doSpawn) {
            spawnNpc(player, record);
        }

        return true;
    }

    /** Simpan NPC template ke file YAML. */
    private void saveNpcTemplate(NpcDataStore.NpcRecord record) {
        File npcsFolder = new File(plugin.getDataFolder(), "data/npcs");
        if (!npcsFolder.exists()) {
            npcsFolder.mkdirs();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("npc_id", record.npcId);
        data.put("display_name", record.displayName);
        data.put("disposition", record.disposition);
        data.put("story_node", record.storyNode);
        data.put("history", new ArrayList<>());

        File file = new File(npcsFolder, record.npcId + ".yml");
        org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
        options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(options);
        try (FileWriter writer = new FileWriter(file)) {
            yaml.dump(data, writer);
            plugin.getLogger().info("Created NPC template: " + file.getPath());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save NPC template for " + record.npcId + ": " + e.getMessage());
        }
    }

    /** Spawn NPC di depan player. */
    private void spawnNpc(Player player, NpcDataStore.NpcRecord record) {
        World world = player.getWorld();
        Location loc = player.getLocation().add(
                player.getLocation().getDirection().normalize().multiply(2));

        Villager villager = (Villager) world.spawnEntity(loc, EntityType.VILLAGER);

        villager.getPersistentDataContainer().set(
                NpcSpawnListener.npcIdKey(), PersistentDataType.STRING, record.npcId);
        villager.getPersistentDataContainer().set(
                NpcSpawnListener.npcNameKey(), PersistentDataType.STRING, record.displayName);
        villager.getPersistentDataContainer().set(
                NpcSpawnListener.npcTypeKey(), PersistentDataType.STRING, NpcSpawnListener.NPC_TYPE_IMPORTANT);

        villager.customName(Component.text("§6§l" + record.displayName));
        villager.setCustomNameVisible(true);
        villager.setAware(true);

        Villager.Profession[] profs = Villager.Profession.values();
        villager.setProfession(profs[(int) (Math.random() * profs.length)]);

        player.sendMessage("§aSpawned: §6§l" + record.displayName);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            // Suggest npc_id placeholder
            return List.of("npc_id_baru");
        }
        if (args.length == 2) {
            return List.of("\"NPC Name\"");
        }
        if (args.length == 3) {
            return List.of("spawn");
        }
        return List.of();
    }
}
