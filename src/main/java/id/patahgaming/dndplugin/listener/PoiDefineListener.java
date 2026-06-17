package id.patahgaming.dndplugin.listener;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.ui.PoiParticleDisplay;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Left-click = corner 1, Right-click = corner 2 dengan POI stick tool.
 * Simpan corner ke PDC player + trigger particle display.
 */
public class PoiDefineListener implements Listener {

    private static final NamespacedKey KEY_POI_TOOL = new NamespacedKey("dndplugin", "poi_tool");
    private static final NamespacedKey KEY_CORNER1 = new NamespacedKey("dndplugin", "poi_corner1");
    private static final NamespacedKey KEY_CORNER2 = new NamespacedKey("dndplugin", "poi_corner2");

    private final DndPlugin plugin;

    public PoiDefineListener(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPoiToolClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.STICK) return;

        // Cek PDC poi_tool
        var pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(KEY_POI_TOOL, PersistentDataType.BOOLEAN)) return;

        // Cek player sedang dalam mode define
        var playerPdc = player.getPersistentDataContainer();
        if (!playerPdc.has(new NamespacedKey("dndplugin", "poi_define_id"), PersistentDataType.STRING)) {
            return; // bukan yang lagi define — ignore
        }

        event.setCancelled(true); // jangan biarkan stick interact normal

        Location loc = event.getClickedBlock() != null
                ? event.getClickedBlock().getLocation()
                : player.getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_AIR) {
            // Corner 1
            String data = serializeLoc(loc);
            playerPdc.set(KEY_CORNER1, PersistentDataType.STRING, data);
            player.sendMessage("§a📐 Corner 1 set: §f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());

            // Update particle display kalau corner 2 sudah ada
            updateParticles(player);

        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
            // Corner 2
            String data = serializeLoc(loc);
            playerPdc.set(KEY_CORNER2, PersistentDataType.STRING, data);
            player.sendMessage("§a📐 Corner 2 set: §f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            player.sendMessage("§eKetik §f/poi confirm §euntuk menyimpan POI!");

            // Update particle display
            updateParticles(player);
        }
    }

    private void updateParticles(Player player) {
        Location c1 = getCorner(player, KEY_CORNER1);
        Location c2 = getCorner(player, KEY_CORNER2);
        if (c1 != null && c2 != null) {
            plugin.getPoiParticleDisplay().startDefineDisplay(player, c1, c2);
        }
    }

    /** Read corner location dari PDC player. */
    public static Location getCorner(Player player, NamespacedKey key) {
        String data = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (data == null) return null;
        return deserializeLoc(data);
    }

    /** Create POI stick item. */
    public static ItemStack createPoiTool() {
        ItemStack stick = new ItemStack(Material.STICK);
        var meta = stick.getItemMeta();
        if (meta == null) return stick;
        meta.setDisplayName("§6§l📐 POI Tool");
        meta.setLore(java.util.List.of("§7Klik kiri = corner 1", "§7Klik kanan = corner 2", "§8[/poi confirm to save]"));
        meta.getPersistentDataContainer().set(KEY_POI_TOOL, PersistentDataType.BOOLEAN, true);
        stick.setItemMeta(meta);
        return stick;
    }

    private static String serializeLoc(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private static Location deserializeLoc(String data) {
        String[] parts = data.split(",");
        if (parts.length != 4) return null;
        try {
            return new Location(
                    org.bukkit.Bukkit.getWorld(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );
        } catch (Exception e) { return null; }
    }
}
