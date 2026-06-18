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
 * /newnpc <npc_id> <display_name> [entity_type] [spawn]
 *
 * Membuat NPC penting baru. Bisa spawn semua jenis mob Minecraft.
 *
 * Entity types: VILLAGER, ZOMBIE, SKELETON, WITCH, PILLAGER,
 * VINDICATOR, EVOKER, WANDERING_TRADER, IRON_GOLEM, ENDERMAN, SPIDER
 *
 * Contoh:
 *   /newnpc blacksmith_garin "Garin the Blacksmith"
 *   /newnpc zombie_king "King Rotjaw" ZOMBIE spawn
 *   /newnpc forest_witch "Morgana" WITCH spawn
 */
public class NewNpcCommand implements CommandExecutor, TabCompleter {

    private static final List<String> VALID_ENTITY_TYPES = List.of(
            "VILLAGER", "ZOMBIE", "SKELETON", "WITCH",
            "PILLAGER", "VINDICATOR", "EVOKER", "WANDERING_TRADER",
            "IRON_GOLEM", "ENDERMAN", "SPIDER"
    );

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
            player.sendMessage("§eGunakan: /newnpc <npc_id> <display_name> [entity_type] [spawn]");
            player.sendMessage("§7Contoh: /newnpc zombie_king \"King Rotjaw\" ZOMBIE spawn");
            player.sendMessage("§7  entity_type: VILLAGER, ZOMBIE, SKELETON, WITCH, dll");
            player.sendMessage("§7  spawn: langsung spawn NPC setelah dibuat");
            return true;
        }

        String npcId = args[0].toLowerCase().replace(" ", "_");

        // Parse entity_type dan spawn flag dari args
        String entityType = "VILLAGER";
        boolean doSpawn = false;
        int nameEnd = args.length;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("spawn")) {
                doSpawn = true;
                nameEnd = Math.min(nameEnd, i);
            } else if (VALID_ENTITY_TYPES.contains(arg.toUpperCase())) {
                entityType = arg.toUpperCase();
                nameEnd = Math.min(nameEnd, i);
            }
        }

        String displayName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, nameEnd));

        // Validasi
        NpcDataStore.NpcRecord existing = plugin.getNpcDataStore().get(npcId);
        if (existing != null) {
            player.sendMessage("§cNPC id '" + npcId + "' sudah ada: " + existing.displayName);
            player.sendMessage("§7Gunakan /spawnnpc " + npcId + " untuk spawn.");
            return true;
        }

        // Buat record
        NpcDataStore.NpcRecord record = plugin.getNpcDataStore().getOrCreate(npcId, displayName);
        record.disposition = "neutral";
        record.entityType = entityType;
        record.backstory = "(Add backstory here — edit data/npcs/" + npcId + ".yml)";
        record.storyNode = plugin.getPlayerDataStore().getPartyCurrentNode();

        // Simpan YAML
        saveNpcTemplate(record);

        player.sendMessage("§aNPC created: §6§l" + displayName + " §7(id: " + npcId + ", type: " + entityType + ")");
        player.sendMessage("§7  File: data/npcs/" + npcId + ".yml");
        player.sendMessage("§7  Edit YAML untuk isi backstory karakter.");
        player.sendMessage("§7  §e/spawnnpc " + npcId + " §7— spawn  §e/talk " + npcId + " §7— dialog");

        if (doSpawn) {
            spawnNpc(player, record);
        }

        return true;
    }

    private void saveNpcTemplate(NpcDataStore.NpcRecord record) {
        File npcsFolder = new File(plugin.getDataFolder(), "data/npcs");
        if (!npcsFolder.exists()) npcsFolder.mkdirs();

        Map<String, Object> data = new HashMap<>();
        data.put("npc_id", record.npcId);
        data.put("display_name", record.displayName);
        data.put("disposition", record.disposition);
        data.put("entity_type", record.entityType);
        data.put("backstory", record.backstory);
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
            plugin.getLogger().warning("Failed to save NPC template: " + e.getMessage());
        }
    }

    private void spawnNpc(Player player, NpcDataStore.NpcRecord record) {
        World world = player.getWorld();
        Location loc = player.getLocation().add(
                player.getLocation().getDirection().normalize().multiply(2));

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(record.entityType.toUpperCase());
        } catch (IllegalArgumentException e) {
            entityType = EntityType.VILLAGER;
        }

        org.bukkit.entity.LivingEntity entity = (org.bukkit.entity.LivingEntity)
                world.spawnEntity(loc, entityType);

        // Jangan despawn saat chunk unload
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);

        entity.getPersistentDataContainer().set(
                NpcSpawnListener.npcIdKey(), PersistentDataType.STRING, record.npcId);
        entity.getPersistentDataContainer().set(
                NpcSpawnListener.npcNameKey(), PersistentDataType.STRING, record.displayName);
        entity.getPersistentDataContainer().set(
                NpcSpawnListener.npcTypeKey(), PersistentDataType.STRING, NpcSpawnListener.NPC_TYPE_IMPORTANT);

        entity.customName(Component.text("§6§l" + record.displayName));
        entity.setCustomNameVisible(true);

        if (entity instanceof Villager villager) {
            villager.setAware(true);
            Villager.Profession[] profs = Villager.Profession.values();
            villager.setProfession(profs[(int) (Math.random() * profs.length)]);
        }

        // Hostile mob NPCs: jangan auto-attack — cuma nyerang pas disposition jadi agro
        // Kecualikan Villager & WanderingTrader (peaceful)
        if (entity instanceof org.bukkit.entity.Mob mob
                && !(entity instanceof Villager)
                && !(entity instanceof org.bukkit.entity.WanderingTrader)) {
            mob.setAware(false);
        }

        player.sendMessage("§aSpawned: §6§l" + record.displayName + " §7(" + entityType + ")");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) return List.of("npc_id_baru");
        if (args.length >= 2) {
            List<String> suggestions = new ArrayList<>();
            String input = args[args.length - 1].toUpperCase();
            for (String type : VALID_ENTITY_TYPES) {
                if (type.startsWith(input)) suggestions.add(type);
            }
            suggestions.add("spawn");
            return suggestions;
        }
        return List.of();
    }
}
