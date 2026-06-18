package id.patahgaming.dndplugin.listener;

import id.patahgaming.dndplugin.DndPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Intercept chat saat player sedang dalam multi-turn dialog dengan NPC.
 * Chat normal diteruskan, chat saat dialog → forward ke NpcDialogueService.
 */
public class PlayerChatListener implements Listener {

    private final DndPlugin plugin;

    public PlayerChatListener(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        var player = event.getPlayer();

        // Cek apakah player sedang dalam NPC dialog
        var dialog = plugin.getNpcDialogueService().getActiveDialog(player);
        if (dialog == null) return; // chat normal — biarkan lewat

        // Cancel normal chat — kita yang handle
        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Process dialog turn di main thread
        Bukkit.getScheduler().runTask(plugin, () ->
                plugin.getNpcDialogueService().processPlayerResponse(player, message));
    }
}
