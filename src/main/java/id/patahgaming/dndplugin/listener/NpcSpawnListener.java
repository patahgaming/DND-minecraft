package id.patahgaming.dndplugin.listener;

import id.patahgaming.dndplugin.DndPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
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
}