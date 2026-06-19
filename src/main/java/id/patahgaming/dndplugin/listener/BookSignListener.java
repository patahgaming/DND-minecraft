package id.patahgaming.dndplugin.listener;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.creation.CharacterCreationManager;
import id.patahgaming.dndplugin.ui.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handle Book & Quill signing for character creation.
 * Two-stage flow: Stage 1 (race+class) → Stage 2 (skills+background+scores).
 */
public class BookSignListener implements Listener {

    private static final NamespacedKey KEY_BOOK_STAGE = new NamespacedKey("dndplugin", "book_stage");
    private static final NamespacedKey KEY_CHAR_CLASS = new NamespacedKey("dndplugin", "char_class");

    private final DndPlugin plugin;

    public BookSignListener(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBookSign(PlayerEditBookEvent event) {
        if (!event.isSigning()) return;

        BookMeta meta = event.getNewBookMeta();
        var pdc = meta.getPersistentDataContainer();
        Integer stage = pdc.get(KEY_BOOK_STAGE, PersistentDataType.INTEGER);
        if (stage == null) return; // bukan buku karakter kita

        event.setCancelled(true); // kita yang handle — jangan kasih buku jadi

        Player player = event.getPlayer();
        String charName = meta.getTitle(); // nama dari sign dialog

        if (stage == 1) {
            handleStageOne(player, charName, meta.getPages());
        } else if (stage == 2) {
            Integer classIdx = pdc.get(KEY_CHAR_CLASS, PersistentDataType.INTEGER);
            handleStageTwo(player, charName, classIdx != null ? classIdx : 1, meta.getPages());
        }

        // Hapus buku dari inventory
        player.getInventory().remove(event.getPlayer().getInventory().getItemInMainHand());
    }

    private void handleStageOne(Player player, String charName, java.util.List<String> pages) {
        CharacterCreationManager mgr = plugin.getCharacterCreationManager();

        // Parse race dari halaman 1
        int raceIdx = extractLastNumber(pages.get(0));
        if (raceIdx < 1 || raceIdx > CharacterCreationManager.RACES.length) {
            player.sendMessage("§cRace tidak valid. Pilih angka 1-" + CharacterCreationManager.RACES.length);
            mgr.giveStageOneBook(player);
            return;
        }
        String raceId = CharacterCreationManager.RACES[raceIdx - 1];

        // Parse class dari halaman 2
        int classIdx = extractLastNumber(pages.get(1));
        if (classIdx < 1 || classIdx > CharacterCreationManager.CLASSES.length) {
            player.sendMessage("§cClass tidak valid. Pilih angka 1-" + CharacterCreationManager.CLASSES.length);
            mgr.giveStageOneBook(player);
            return;
        }

        // Simpan progress sementara ke PDC untuk dipakai stage 2
        var pdc = player.getPersistentDataContainer();
        pdc.set(new NamespacedKey("dndplugin", "tmp_char_name"), PersistentDataType.STRING, charName);
        pdc.set(new NamespacedKey("dndplugin", "tmp_char_race"), PersistentDataType.STRING, raceId);
        pdc.set(new NamespacedKey("dndplugin", "tmp_char_class_idx"), PersistentDataType.INTEGER, classIdx);

        // Beri Buku 2
        mgr.giveStageTwoBook(player, classIdx);
        player.sendMessage("§a📖 Tahap 1 selesai! §7Isi Buku 2 (skills + ability scores).");
    }

    private void handleStageTwo(Player player, String charName, int classIdx, java.util.List<String> pages) {
        CharacterCreationManager mgr = plugin.getCharacterCreationManager();

        // Parse skill choices (page 0)
        String page0 = pages.get(0);
        int[] skills = extractTwoNumbers(page0);
        if (skills == null) skills = new int[]{1, 3}; // default

        // Parse background (page 1)
        int bgIdx = extractLastNumber(pages.get(1));
        if (bgIdx < 1 || bgIdx > CharacterCreationManager.BACKGROUNDS.length) bgIdx = 1;

        // Parse ability method + distribution (page 2)
        String page2 = pages.get(2);
        String[] lines = page2.split("\n");
        int scoreMethod = 1;
        int[] scoreDist = null;
        for (String line : lines) {
            String[] parts = line.split(":")[0].split(",");
            if (parts.length == 6) {
                try {
                    scoreDist = new int[6];
                    for (int j = 0; j < 6; j++) scoreDist[j] = Integer.parseInt(parts[j].trim());
                    scoreMethod = 1;
                } catch (NumberFormatException ignored) {}
                break;
            }
        }
        if (scoreDist == null) scoreMethod = 2; // roll

        // Read tmp data from PDC
        var pdc = player.getPersistentDataContainer();
        String raceId = pdc.get(new NamespacedKey("dndplugin", "tmp_char_race"), PersistentDataType.STRING);
        if (raceId == null) raceId = "human";

        boolean ok = mgr.finalizeCharacter(player, charName, raceId, classIdx,
                skills, bgIdx, scoreMethod, scoreDist);

        if (!ok) {
            player.sendMessage("§cValidasi gagal. Isi ulang buku dengan benar.");
            mgr.giveStageTwoBook(player, classIdx);
            return;
        }

        // Cleanup tmp PDC
        pdc.remove(new NamespacedKey("dndplugin", "tmp_char_name"));
        pdc.remove(new NamespacedKey("dndplugin", "tmp_char_race"));
        pdc.remove(new NamespacedKey("dndplugin", "tmp_char_class_idx"));

        // Welcome!
        String className = CharacterCreationManager.CLASSES[classIdx - 1];
        ScoreboardManager.showCharacterSheet(plugin, player);
        player.sendTitle("§a§lSelamat Datang, " + charName + "!",
                "§7" + formatName(raceId) + " " + formatName(className) + " — Level 1", 10, 80, 20);
        Bukkit.broadcastMessage("§a✨ " + charName + " telah bergabung dengan party! §7("
                + formatName(raceId) + " " + formatName(className) + ")");
    }

    private int extractLastNumber(String text) {
        if (text == null) return -1;
        String[] lines = text.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.matches("\\d+")) return Integer.parseInt(line);
        }
        return -1;
    }

    private int[] extractTwoNumbers(String text) {
        if (text == null) return null;
        for (String line : text.split("\n")) {
            String[] parts = line.trim().split(",");
            if (parts.length == 2) {
                try {
                    return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
                } catch (NumberFormatException e) { return null; }
            }
        }
        return null;
    }

    private String formatName(String id) { return id.replace("_", " ").substring(0, 1).toUpperCase() + id.substring(1); }
}
