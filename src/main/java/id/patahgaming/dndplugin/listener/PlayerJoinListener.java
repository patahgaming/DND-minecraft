package id.patahgaming.dndplugin.listener;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.ui.ScoreboardManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Saat player join:
 *  - Inisialisasi character sheet (PDC) jika belum ada
 *  - Tampilkan scoreboard sidebar (char sheet display)
 *
 * Saat player quit:
 *  - Simpan character sheet ke YAML
 */
public class PlayerJoinListener implements Listener {

    private final DndPlugin plugin;

    public PlayerJoinListener(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerDataStore().initIfAbsent(player);
        ScoreboardManager.showCharacterSheet(plugin, player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPlayerDataStore().save(event.getPlayer());
    }
}