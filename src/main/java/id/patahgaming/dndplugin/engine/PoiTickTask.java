package id.patahgaming.dndplugin.engine;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.StoryTreeStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Engine tick loop — berjalan setiap interval yang dikonfigurasi (default 2 detik).
 * Mengecek story node dengan trigger bbox:
 *  - ALL_PLAYERS_IN_BBOX: semua player online harus berada di dalam bbox
 *  - SINGLE_PLAYER_EVENT: cukup 1 player memenuhi kondisi
 *
 * Setiap node hanya di-trigger sekali — setelah fulfilled, tidak akan re-trigger
 * sampai party pindah ke node lain.
 *
 * PENTING: Saat mengintegrasikan dengan DeepSeek (NarratorService), PASTIKAN
 * panggilan AI dilakukan dari dalam async task (BukkitScheduler.runTaskAsynchronously)
 * untuk menghindari blocking main server thread.
 */
public class PoiTickTask extends BukkitRunnable {

    private final DndPlugin plugin;
    private final Set<String> triggeredNodes = Collections.synchronizedSet(new HashSet<>());

    public PoiTickTask(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) return;

        String currentPartyNode = plugin.getPlayerDataStore().getPartyCurrentNode();
        List<StoryTreeStore.StoryNode> nodes = plugin.getStoryTreeStore().getAllWithBboxTrigger();

        for (StoryTreeStore.StoryNode node : nodes) {
            // Hanya proses node yang relevan dengan progress party saat ini
            if (!node.nodeId.equals(currentPartyNode)) {
                continue;
            }

            // Jangan re-trigger node yang sudah fulfilled
            if (triggeredNodes.contains(node.nodeId)) {
                continue;
            }

            switch (node.trigger) {
                case ALL_PLAYERS_IN_BBOX -> checkAllPlayersInBbox(node, online);
                case SINGLE_PLAYER_EVENT -> checkSinglePlayerEvent(node, online);
            }
        }
    }

    /**
     * Reset triggered state saat party pindah ke node baru — dipanggil dari PlayerDataStore
     * saat setPartyCurrentNode dipanggil dengan node berbeda.
     */
    public void resetForNewNode() {
        triggeredNodes.clear();
    }

    private void checkAllPlayersInBbox(StoryTreeStore.StoryNode node, Collection<? extends Player> online) {
        for (Player player : online) {
            if (!node.bbox.contains(player.getLocation().getX(), player.getLocation().getZ())) {
                return; // belum semua player di dalam bbox
            }
        }
        // Semua player di dalam bbox -> trigger narator
        onTriggerFulfilled(node, null);
    }

    private void checkSinglePlayerEvent(StoryTreeStore.StoryNode node, Collection<? extends Player> online) {
        for (Player player : online) {
            if (node.bbox != null && node.bbox.contains(player.getLocation().getX(), player.getLocation().getZ())) {
                onTriggerFulfilled(node, player);
                return; // hanya butuh 1 player
            }
        }
    }

    /**
     * Trigger terpenuhi — tandai node sebagai fulfilled, lalu broadcast narasi.
     *
     * TODO: Saat NarratorService sudah dibuat, ganti blok ini dengan:
     *   Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
     *       String narration = plugin.getNarratorService().generate(node, triggerPlayer);
     *       Bukkit.getScheduler().runTask(plugin, () -> {
     *           Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(Component.text(narration)));
     *       });
     *   });
     */
    private void onTriggerFulfilled(StoryTreeStore.StoryNode node, Player triggerPlayer) {
        triggeredNodes.add(node.nodeId);

        plugin.getLogger().info("Story node trigger fulfilled: " + node.nodeId
                + (triggerPlayer != null ? " by " + triggerPlayer.getName() : " (all players)"));

        // Placeholder narasi langsung — nanti diganti panggilan DeepSeek async
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(Component.text(node.narrative)));
    }
}