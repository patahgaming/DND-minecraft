package id.patahgaming.dndplugin.listener;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.NpcDataStore;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Auto-generate nama unik untuk Villager (NPC peaceful) dan hostile mob saat spawn.
 * Nama disimpan di PDC sebagai npc_id + npc_name agar bisa direferensikan
 * oleh NPC penting (story-critical) atau side NPC (flavor).
 *
 * Color coding:
 * - Side NPC (natural spawn): §f§l white bold
 * - Important NPC (via /spawnnpc): §6§l gold bold
 */
public class NpcSpawnListener implements Listener {

    public static final String NPC_TYPE_IMPORTANT = "important";
    public static final String NPC_TYPE_SIDE = "side";

    private static final NamespacedKey KEY_NPC_ID = new NamespacedKey("dndplugin", "npc_id");
    private static final NamespacedKey KEY_NPC_NAME = new NamespacedKey("dndplugin", "npc_name");
    private static final NamespacedKey KEY_NPC_TYPE = new NamespacedKey("dndplugin", "npc_type");

    // Pool nama sederhana — bisa diperluas/dipindah ke YAML config nanti
    private static final String[] FIRST_NAMES = {
            "Aldric", "Beren", "Cassia", "Dorin", "Elwen", "Fenwick", "Gareth",
            "Hilda", "Ivor", "Jora", "Kael", "Lira", "Mira", "Nolan", "Orin"
    };
    private static final String[] LAST_NAMES = {
            "Stonefield", "Riverwood", "Thornback", "Hollowmere", "Ashgrove",
            "Blackwell", "Greycastle", "Windmere", "Oakheart", "Dunmoor"
    };

    private final DndPlugin plugin;

    public NpcSpawnListener(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        EntityType type = event.getEntityType();

        boolean isVillager = type == EntityType.VILLAGER;
        boolean isHostile = entity instanceof Monster;

        if (!isVillager && !isHostile) {
            return; // hanya Villager dan hostile mob yang dapat nama unik
        }

        // Jangan re-tag jika sudah punya npc_id (dari /spawnnpc atau plugin lain)
        if (entity.getPersistentDataContainer().has(KEY_NPC_ID, PersistentDataType.STRING)) {
            return;
        }

        String npcId = "npc_" + UUID.randomUUID().toString().substring(0, 8);
        String npcName = generateRandomName();

        entity.getPersistentDataContainer().set(KEY_NPC_ID, PersistentDataType.STRING, npcId);
        entity.getPersistentDataContainer().set(KEY_NPC_NAME, PersistentDataType.STRING, npcName);
        entity.getPersistentDataContainer().set(KEY_NPC_TYPE, PersistentDataType.STRING, NPC_TYPE_SIDE);

        // Side NPC = white bold
        entity.customName(Component.text("§f§l" + npcName));
        entity.setCustomNameVisible(true);

        if (entity instanceof Villager villager) {
            villager.setAware(true);
        }

        // Semua named NPC (side + hostile) harus persistent — gak boleh despawn
        if (entity instanceof org.bukkit.entity.LivingEntity living) {
            living.setPersistent(true);
            living.setRemoveWhenFarAway(false);
            // NBT force: reflection setPersistenceRequired
            try {
                Object handle = living.getClass().getMethod("getHandle").invoke(living);
                handle.getClass().getMethod("setPersistenceRequired", boolean.class).invoke(handle, true);
            } catch (Exception ignored) {}
        }
    }

    private String generateRandomName() {
        String first = FIRST_NAMES[ThreadLocalRandom.current().nextInt(FIRST_NAMES.length)];
        String last = LAST_NAMES[ThreadLocalRandom.current().nextInt(LAST_NAMES.length)];
        return first + " " + last;
    }

    public static NamespacedKey npcIdKey() {
        return KEY_NPC_ID;
    }

    public static NamespacedKey npcNameKey() {
        return KEY_NPC_NAME;
    }

    public static NamespacedKey npcTypeKey() {
        return KEY_NPC_TYPE;
    }

    /**
     * Cegah NPC penting (hostile mob) nyerang player otomatis.
     * Zombie/Skeleton dll punya vanilla AI yang auto-target player.
     * Kita cancel targeting kecuali disposition = agro.
     *
     * Ini nge-handle kasus di mana YAML udah friendly tapi mob masih nyerang.
     */
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        Entity entity = event.getEntity();

        // Cuma proses entity yang di-tag NPC oleh plugin ini
        if (!entity.getPersistentDataContainer().has(KEY_NPC_ID, PersistentDataType.STRING)) {
            return;
        }

        // Cuma proses NPC penting (punya npc_type = important)
        String npcType = entity.getPersistentDataContainer().get(KEY_NPC_TYPE, PersistentDataType.STRING);
        if (!NPC_TYPE_IMPORTANT.equals(npcType)) {
            return; // side NPC — bebas
        }

        // Villager gak perlu dicegah (gak nyerang)
        if (entity instanceof Villager) {
            return;
        }

        // Cek disposition dari NpcDataStore
        String npcId = entity.getPersistentDataContainer().get(KEY_NPC_ID, PersistentDataType.STRING);
        if (npcId == null) return;

        NpcDataStore.NpcRecord record = plugin.getNpcDataStore().get(npcId);
        if (record == null) return;

        // Kalau disposition BUKAN agro → cancel targeting
        if (!"agro".equalsIgnoreCase(record.disposition)) {
            event.setCancelled(true);
            // Force set target ke null
            if (entity instanceof org.bukkit.entity.Mob mob) {
                mob.setTarget(null);
            }
        }
        // Kalau agro → biarin targeting (gak di-cancel)
    }
}