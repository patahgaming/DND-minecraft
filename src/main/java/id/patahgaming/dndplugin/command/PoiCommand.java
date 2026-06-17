package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.PoiStore;
import id.patahgaming.dndplugin.data.StoryTreeStore;
import id.patahgaming.dndplugin.listener.PoiDefineListener;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /poi — Point of Interest management.
 *
 * define  <poi_id> <name> [biome] [parent_poi_id]  — start defining POI with stick tool
 * confirm                                            — save the defined POI
 * cancel                                             — cancel definition
 * list [parent]                                      — list all POIs (optional parent filter)
 * info <poi_id>                                      — show details + visual border
 * tree                                               — show hierarchy tree
 */
public class PoiCommand implements CommandExecutor, TabCompleter {

    private final DndPlugin plugin;

    private static final NamespacedKey KEY_DEFINE_ID = new NamespacedKey("dndplugin", "poi_define_id");
    private static final NamespacedKey KEY_DEFINE_NAME = new NamespacedKey("dndplugin", "poi_define_name");
    private static final NamespacedKey KEY_DEFINE_BIOME = new NamespacedKey("dndplugin", "poi_define_biome");
    private static final NamespacedKey KEY_DEFINE_PARENT = new NamespacedKey("dndplugin", "poi_define_parent");
    private static final NamespacedKey KEY_CORNER1 = new NamespacedKey("dndplugin", "poi_corner1");
    private static final NamespacedKey KEY_CORNER2 = new NamespacedKey("dndplugin", "poi_corner2");

    private static final List<String> SUBCOMMANDS = List.of("define", "confirm", "cancel", "list", "info", "tree");

    public PoiCommand(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cHanya player yang bisa menggunakan command ini.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§e/poi define|confirm|cancel|list|info|tree");
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "define" -> handleDefine(player, args);
            case "confirm" -> handleConfirm(player);
            case "cancel" -> handleCancel(player);
            case "list" -> handleList(player, args);
            case "info" -> handleInfo(player, args);
            case "tree" -> handleTree(player);
            default -> {
                player.sendMessage("§cUnknown: " + args[0] + ". Use: define, confirm, cancel, list, info, tree");
                yield true;
            }
        };
    }

    // ==================== DEFINE ====================

    private boolean handleDefine(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§e/poi define <poi_id> <name> [biome] [parent_poi_id]");
            player.sendMessage("§7Contoh: /poi define cave_entrance \"Goa Gelap\" forest");
            return true;
        }

        String poiId = args[1].toLowerCase();
        String name = args[2];
        String biome = args.length >= 4 ? args[3] : "unknown";
        String parentId = args.length >= 5 ? args[4].toLowerCase() : null;

        // Validate parent exists
        if (parentId != null && !plugin.getPoiStore().exists(parentId)) {
            player.sendMessage("§cParent POI '" + parentId + "' tidak ditemukan.");
            player.sendMessage("§7Gunakan §f/poi list §7untuk lihat POI yang ada.");
            return true;
        }

        // Cek ID unik
        if (plugin.getPoiStore().exists(poiId)) {
            player.sendMessage("§cPOI '" + poiId + "' sudah ada. Gunakan ID lain.");
            return true;
        }

        // Save define data ke PDC player
        var pdc = player.getPersistentDataContainer();
        pdc.set(KEY_DEFINE_ID, PersistentDataType.STRING, poiId);
        pdc.set(KEY_DEFINE_NAME, PersistentDataType.STRING, name);
        pdc.set(KEY_DEFINE_BIOME, PersistentDataType.STRING, biome);
        if (parentId != null) pdc.set(KEY_DEFINE_PARENT, PersistentDataType.STRING, parentId);
        else pdc.remove(KEY_DEFINE_PARENT);
        pdc.remove(KEY_CORNER1);
        pdc.remove(KEY_CORNER2);

        // Beri stick tool
        ItemStack tool = PoiDefineListener.createPoiTool();
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), tool);
            player.sendMessage("§eInventory penuh — POI tool dijatuhkan di kakimu.");
        } else {
            player.getInventory().addItem(tool);
        }

        String parentInfo = parentId != null ? " §7(child of §f" + parentId + "§7)" : "";
        player.sendMessage("§a📐 Defining POI: §f" + name + " §8[" + poiId + "]" + parentInfo);
        player.sendMessage("§7Klik §eKIRI §7= corner 1 §8| §7Klik §eKANAN §7= corner 2");
        player.sendMessage("§7Ketik §f/poi confirm §7untuk menyimpan, §f/poi cancel §7untuk batal.");
        return true;
    }

    // ==================== CONFIRM ====================

    private boolean handleConfirm(Player player) {
        var pdc = player.getPersistentDataContainer();
        String poiId = pdc.get(KEY_DEFINE_ID, PersistentDataType.STRING);
        if (poiId == null) {
            player.sendMessage("§cTidak ada POI yang sedang di-define. Gunakan /poi define dulu.");
            return true;
        }

        String name = pdc.get(KEY_DEFINE_NAME, PersistentDataType.STRING);
        String biome = pdc.get(KEY_DEFINE_BIOME, PersistentDataType.STRING);
        String parentId = pdc.get(KEY_DEFINE_PARENT, PersistentDataType.STRING);

        Location c1 = PoiDefineListener.getCorner(player, KEY_CORNER1);
        Location c2 = PoiDefineListener.getCorner(player, KEY_CORNER2);

        if (c1 == null || c2 == null) {
            player.sendMessage("§cKedua corner harus di-set dulu! Klik kiri + kanan dengan POI tool.");
            return true;
        }

        // Build bbox
        StoryTreeStore.Bbox bbox = new StoryTreeStore.Bbox();
        bbox.x1 = c1.getBlockX(); bbox.y1 = c1.getBlockY(); bbox.z1 = c1.getBlockZ();
        bbox.x2 = c2.getBlockX(); bbox.y2 = c2.getBlockY(); bbox.z2 = c2.getBlockZ();

        // Validate parent containment
        if (parentId != null) {
            PoiStore.Poi parent = plugin.getPoiStore().get(parentId);
            if (parent != null && parent.bbox != null && !parent.bbox.fullyContains(bbox)) {
                player.sendMessage("§cBbox di luar parent! Pastikan seluruh area di dalam §e" + parent.name + "§c.");
                player.sendMessage("§7Parent bbox: x" + parent.bbox.x1 + "-" + parent.bbox.x2
                        + " y" + parent.bbox.y1 + "-" + parent.bbox.y2
                        + " z" + parent.bbox.z1 + "-" + parent.bbox.z2);
                return true;
            }
        }

        // Build & save
        PoiStore.Poi poi = new PoiStore.Poi();
        poi.poiId = poiId;
        poi.name = name;
        poi.biome = biome;
        poi.parentPoiId = parentId;
        poi.bbox = bbox;

        plugin.getPoiStore().save(poi);

        // Cleanup
        cleanupDefine(player);

        player.sendMessage("§a✅ POI §f'" + name + "' §adisimpan! §8[" + poiId + "]");
        plugin.getLogger().info("[POI] " + player.getName() + " created POI: " + poiId);
        return true;
    }

    // ==================== CANCEL ====================

    private boolean handleCancel(Player player) {
        var pdc = player.getPersistentDataContainer();
        if (!pdc.has(KEY_DEFINE_ID, PersistentDataType.STRING)) {
            player.sendMessage("§cTidak ada POI yang sedang di-define.");
            return true;
        }
        cleanupDefine(player);
        plugin.getPoiParticleDisplay().stopDisplay(player);
        player.sendMessage("§e📐 POI definition dibatalkan.");
        return true;
    }

    // ==================== LIST ====================

    private boolean handleList(Player player, String[] args) {
        PoiStore store = plugin.getPoiStore();
        String parentFilter = args.length >= 2 ? args[1].toLowerCase() : null;

        java.util.Collection<PoiStore.Poi> pois;
        if (parentFilter != null) {
            pois = store.getChildren(parentFilter);
            player.sendMessage("§e=== POI di bawah §f" + parentFilter + " §e(" + pois.size() + ") ===");
        } else {
            pois = store.getAll();
            player.sendMessage("§e=== Semua POI (" + pois.size() + ") ===");
        }

        for (var poi : pois) {
            String indent = poi.parentPoiId != null ? "  §7└ " : "";
            String size = poi.bbox != null ? " §7["
                    + Math.abs(poi.bbox.x2 - poi.bbox.x1) + "×"
                    + Math.abs(poi.bbox.y2 - poi.bbox.y1) + "×"
                    + Math.abs(poi.bbox.z2 - poi.bbox.z1) + "]" : "";
            player.sendMessage(indent + "§f" + poi.name + " §8[" + poi.poiId + "]" + size);
        }
        return true;
    }

    // ==================== INFO ====================

    private boolean handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§e/poi info <poi_id>");
            return true;
        }

        PoiStore.Poi poi = plugin.getPoiStore().get(args[1].toLowerCase());
        if (poi == null) {
            player.sendMessage("§cPOI tidak ditemukan: " + args[1]);
            return true;
        }

        player.sendMessage("§e=== §f" + poi.name + " §8[" + poi.poiId + "] §e===");
        player.sendMessage("§7Biome: §f" + poi.biome);
        player.sendMessage("§7Parent: §f" + (poi.parentPoiId != null ? poi.parentPoiId : "— (top-level)"));
        if (poi.bbox != null) {
            player.sendMessage("§7Bbox: §fx" + poi.bbox.x1 + "-" + poi.bbox.x2
                    + " y" + poi.bbox.y1 + "-" + poi.bbox.y2
                    + " z" + poi.bbox.z1 + "-" + poi.bbox.z2);
        }

        // Children
        var children = plugin.getPoiStore().getChildren(poi.poiId);
        if (!children.isEmpty()) {
            player.sendMessage("§7Children (" + children.size() + "):");
            for (var child : children) {
                player.sendMessage("  §f" + child.name + " §8[" + child.poiId + "]");
            }
        }

        // Visual border
        plugin.getPoiParticleDisplay().showExistingBorder(player, poi);
        return true;
    }

    // ==================== TREE ====================

    private boolean handleTree(Player player) {
        var roots = plugin.getPoiStore().getTopLevel();
        if (roots.isEmpty()) {
            player.sendMessage("§7Belum ada POI. Gunakan §f/poi define§7 untuk membuat.");
            return true;
        }

        player.sendMessage("§e=== POI Hierarchy Tree ===");
        for (var root : roots) {
            printTree(player, root, "");
        }
        return true;
    }

    private void printTree(Player player, PoiStore.Poi poi, String prefix) {
        String size = poi.bbox != null ? " §7["
                + Math.abs(poi.bbox.x2 - poi.bbox.x1) + "×"
                + Math.abs(poi.bbox.y2 - poi.bbox.y1) + "×"
                + Math.abs(poi.bbox.z2 - poi.bbox.z1) + "]" : "";
        player.sendMessage(prefix + "§f" + poi.name + " §8[" + poi.poiId + "]" + size);

        var children = plugin.getPoiStore().getChildren(poi.poiId);
        for (int i = 0; i < children.size(); i++) {
            boolean last = i == children.size() - 1;
            String childPrefix = prefix + (last ? "  §7└─ " : "  §7├─ ");
            printTree(player, children.get(i), childPrefix);
        }
    }

    // ==================== CLEANUP ====================

    private void cleanupDefine(Player player) {
        var pdc = player.getPersistentDataContainer();
        pdc.remove(KEY_DEFINE_ID);
        pdc.remove(KEY_DEFINE_NAME);
        pdc.remove(KEY_DEFINE_BIOME);
        pdc.remove(KEY_DEFINE_PARENT);
        pdc.remove(KEY_CORNER1);
        pdc.remove(KEY_CORNER2);

        // Remove stick tool dari inventory
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && item.getType() == org.bukkit.Material.STICK && item.hasItemMeta()) {
                var pdc2 = item.getItemMeta().getPersistentDataContainer();
                if (pdc2.has(new NamespacedKey("dndplugin", "poi_tool"), PersistentDataType.BOOLEAN)) {
                    player.getInventory().setItem(slot, null);
                }
            }
        }

        plugin.getPoiParticleDisplay().stopDisplay(player);
    }

    // ==================== TAB COMPLETE ====================

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(input)).toList();
        }
        if (args.length == 4 && "define".equalsIgnoreCase(args[0])) {
            // Suggest existing POI IDs for parent
            String input = args[3].toLowerCase();
            return plugin.getPoiStore().getAll().stream()
                    .map(p -> p.poiId)
                    .filter(id -> id.startsWith(input))
                    .sorted().toList();
        }
        if (args.length == 2 && ("info".equalsIgnoreCase(args[0]) || "list".equalsIgnoreCase(args[0]))) {
            String input = args[1].toLowerCase();
            return plugin.getPoiStore().getAll().stream()
                    .map(p -> p.poiId)
                    .filter(id -> id.startsWith(input))
                    .sorted().toList();
        }
        return List.of();
    }
}
