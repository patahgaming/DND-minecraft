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
import org.bukkit.inventory.EquipmentSlot;
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
        // Cegah double-fire: event triggered untuk main hand DAN off-hand.
        // Cuma proses main hand (EquipmentSlot.HAND).
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

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
                if (!entity.isValid()) {
                    unfreezeTasks.remove(entityUuid);
                    return;
                }

                // Restore AI: side NPC / villager → aware; important hostile → tetap passive
                NpcDataStore.NpcRecord rec = plugin.getNpcDataStore().get(
                        entity.getPersistentDataContainer().get(
                                NpcSpawnListener.npcIdKey(), PersistentDataType.STRING));
                if (rec != null && !"VILLAGER".equalsIgnoreCase(rec.entityType)) {
                    // Important NPC yang bukan villager → tetap passive (disposition != agro)
                    if (!"agro".equalsIgnoreCase(rec.disposition)) {
                        mob.setAware(false); // tetap pasif
                    } else {
                        mob.setAware(true); // agro → boleh nyerang
                    }
                } else {
                    // Side NPC atau villager → normal AI
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
        // Panggil NpcDialogueService (DeepSeek async) — AI yang handle dialog + disposition
        plugin.getNpcDialogueService().generateDialogue(record, player);
    }

    private void handleSideNpcDialogue(Player player, String npcName) {
        // Side NPC: generate greeting statis (cached by name), tidak panggil AI
        String biome = player.getLocation().getBlock().getBiome().toString().toLowerCase();
        String greeting = plugin.getNpcDialogueService().generateSideNpcGreeting(npcName, biome);
        player.sendMessage(Component.text("[" + npcName + "] " + greeting));
    }
}