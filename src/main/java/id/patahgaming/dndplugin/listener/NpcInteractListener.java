package id.patahgaming.dndplugin.listener;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.NpcDataStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Saat player interact (right-click) NPC:
 *  - Freeze NPC (disable pathfinding/AI) selama dialog
 *  - Auto-unfreeze setelah 5 detik jika tidak ada interaksi lanjutan
 *  - Cek apakah npc_id terdaftar sebagai NPC penting (NpcDataStore)
 *    -> jika ya: panggil AI dialog (NpcDialogueService — TODO)
 *    -> jika tidak: tampilkan 1 bubble cached / generate sekali (side NPC — TODO)
 *
 * PENTING: Saat mengintegrasikan dengan DeepSeek (NpcDialogueService), PASTIKAN
 * panggilan AI dilakukan secara async via BukkitScheduler.runTaskAsynchronously.
 */
public class NpcInteractListener implements Listener {

    private static final long UNFREEZE_DELAY_TICKS = 20L * 5; // 5 detik

    private final DndPlugin plugin;
    private final ConcurrentHashMap<UUID, Integer> unfreezeTasks = new ConcurrentHashMap<>();

    public NpcInteractListener(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();

        if (!entity.getPersistentDataContainer().has(NpcSpawnListener.npcIdKey(), PersistentDataType.STRING)) {
            return; // bukan NPC yang di-tag oleh plugin ini
        }

        String npcId = entity.getPersistentDataContainer().get(NpcSpawnListener.npcIdKey(), PersistentDataType.STRING);
        String npcName = entity.getPersistentDataContainer().get(NpcSpawnListener.npcNameKey(), PersistentDataType.STRING);

        Player player = event.getPlayer();

        // Freeze NPC: disable AI pathfinding sementara
        UUID entityUuid = entity.getUniqueId();
        if (entity instanceof Mob mob) {
            mob.setAware(false);

            // Jadwalkan unfreeze otomatis setelah UNFREEZE_DELAY_TICKS
            // Cancel task sebelumnya jika ada (restart timer tiap interaksi)
            Integer existingTaskId = unfreezeTasks.remove(entityUuid);
            if (existingTaskId != null) {
                Bukkit.getScheduler().cancelTask(existingTaskId);
            }

            int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (entity.isValid()) {
                    mob.setAware(true);
                }
                unfreezeTasks.remove(entityUuid);
            }, UNFREEZE_DELAY_TICKS).getTaskId();

            unfreezeTasks.put(entityUuid, taskId);
        }

        NpcDataStore.NpcRecord record = plugin.getNpcDataStore().get(npcId);

        if (record != null) {
            // NPC Penting (story-critical)
            handleImportantNpcDialogue(player, record);
        } else {
            // NPC Side (flavor only) — 1 bubble, tidak panggil AI berulang
            handleSideNpcDialogue(player, npcName);
        }

        event.setCancelled(true);
    }

    private void handleImportantNpcDialogue(Player player, NpcDataStore.NpcRecord record) {
        // TODO: panggil NpcDialogueService (DeepSeek) secara ASYNC dengan context:
        // disposition, history (shared party), story node, player party stats.
        // Contoh:
        //   Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        //       String response = plugin.getNpcDialogueService().generate(record, player);
        //       Bukkit.getScheduler().runTask(plugin, () -> {
        //           player.sendMessage(Component.text("[" + record.displayName + "] " + response));
        //       });
        //   });
        player.sendMessage(Component.text("[" + record.displayName + "] ...")
                .append(Component.text(" (dialog AI belum diimplementasi)")));
    }

    private void handleSideNpcDialogue(Player player, String npcName) {
        // TODO: generate sekali saat spawn & cache, tampilkan bubble statis di sini.
        player.sendMessage(Component.text("[" + npcName + "] *menyapa sekilas*"));
    }
}