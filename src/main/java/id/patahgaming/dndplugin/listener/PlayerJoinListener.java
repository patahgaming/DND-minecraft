package id.patahgaming.dndplugin.listener;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.ui.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Saat player join:
 *  - Inisialisasi character sheet (PDC) jika belum ada
 *  - Tampilkan scoreboard sidebar (char sheet display)
 *
 * Saat player quit:
 *  - Simpan character sheet ke YAML
 *
 * Saat HP berubah (damage / regen):
 *  - Refresh scoreboard sidebar biar HP selalu sync
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

    /**
     * Refresh scoreboard setelah player terkena damage.
     * Pakai runTaskLater 1 tick karena EntityDamageEvent fire SEBELUM HP berkurang.
     */
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> ScoreboardManager.refresh(plugin, player), 1L);
        }
    }

    /**
     * Refresh scoreboard setelah player regen HP.
     * EntityRegainHealthEvent juga fire sebelum HP bertambah, jadi delay 1 tick.
     */
    @EventHandler
    public void onPlayerRegen(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> ScoreboardManager.refresh(plugin, player), 1L);
        }
    }
}