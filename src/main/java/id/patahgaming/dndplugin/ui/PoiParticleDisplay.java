package id.patahgaming.dndplugin.ui;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.PoiStore;
import id.patahgaming.dndplugin.data.StoryTreeStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Visual particle borders untuk POI definition.
 *
 * - Saat define: wireframe antara corner 1 & corner 2 (repeating)
 * - Saat /poi info: border POI existing (5 detik)
 */
public class PoiParticleDisplay {

    private final DndPlugin plugin;
    private final Map<UUID, Integer> activeTasks = new HashMap<>();

    public PoiParticleDisplay(DndPlugin plugin) {
        this.plugin = plugin;
    }

    /** Mulai tampilkan wireframe untuk define yang sedang berlangsung. */
    public void startDefineDisplay(Player player, Location c1, Location c2) {
        stopDisplay(player); // stop existing
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!player.isOnline()) {
                stopDisplay(player);
                return;
            }
            drawWireframe(c1, c2);
        }, 0L, 10L); // tiap 0.5 detik
        activeTasks.put(player.getUniqueId(), taskId);
    }

    /** Tampilkan border POI existing selama 5 detik. */
    public void showExistingBorder(Player player, PoiStore.Poi poi) {
        StoryTreeStore.Bbox b = poi.bbox;
        if (b == null) return;
        World world = player.getWorld();
        Location c1 = new Location(world, b.x1, b.y1, b.z1);
        Location c2 = new Location(world, b.x2, b.y2, b.z2);

        // Draw 5 kali, tiap 20 ticks = 1 detik, total 5 detik
        for (int i = 0; i < 5; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) drawWireframe(c1, c2);
            }, 20L * i);
        }
    }

    /** Hapus display untuk player. */
    public void stopDisplay(Player player) {
        Integer taskId = activeTasks.remove(player.getUniqueId());
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    // ==================== DRAWING ====================

    /** Draw 12 edges of a 3D wireframe box. */
    private void drawWireframe(Location c1, Location c2) {
        World world = c1.getWorld();
        if (world == null || !c2.getWorld().equals(world)) return;

        double x1 = Math.min(c1.getX(), c2.getX());
        double y1 = Math.min(c1.getY(), c2.getY());
        double z1 = Math.min(c1.getZ(), c2.getZ());
        double x2 = Math.max(c1.getX(), c2.getX());
        double y2 = Math.max(c1.getY(), c2.getY());
        double z2 = Math.max(c1.getZ(), c2.getZ());

        // 8 corners
        Location[] corners = {
                new Location(world, x1, y1, z1), // 0: bottom-SW
                new Location(world, x2, y1, z1), // 1: bottom-SE
                new Location(world, x2, y1, z2), // 2: bottom-NE
                new Location(world, x1, y1, z2), // 3: bottom-NW
                new Location(world, x1, y2, z1), // 4: top-SW
                new Location(world, x2, y2, z1), // 5: top-SE
                new Location(world, x2, y2, z2), // 6: top-NE
                new Location(world, x1, y2, z2), // 7: top-NW
        };

        // 12 edges connecting corners
        int[][] edges = {
                {0,1}, {1,2}, {2,3}, {3,0}, // bottom face
                {4,5}, {5,6}, {6,7}, {7,4}, // top face
                {0,4}, {1,5}, {2,6}, {3,7}, // verticals
        };

        for (int[] edge : edges) {
            drawLine(corners[edge[0]], corners[edge[1]]);
        }
    }

    /** Draw particles along a line between two points. */
    private void drawLine(Location a, Location b) {
        World world = a.getWorld();
        if (world == null) return;
        double dist = a.distance(b);
        int steps = (int) (dist * 3); // 3 particles per block
        if (steps < 1) steps = 1;

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = a.getX() + (b.getX() - a.getX()) * t;
            double y = a.getY() + (b.getY() - a.getY()) * t;
            double z = a.getZ() + (b.getZ() - a.getZ()) * t;
            world.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
        }
    }
}
