package id.patahgaming.dndplugin.ai;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.NpcDataStore;
import id.patahgaming.dndplugin.data.PlayerDataStore;
import id.patahgaming.dndplugin.listener.NpcSpawnListener;
import id.patahgaming.dndplugin.model.ActiveDialog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI NPC Dialogue v2 — multi-turn dialog + state machine.
 *
 * IDLE → GREETING → CONVERSING (loop max 5) → RESOLVED
 * Saat dialog aktif: intercept chat via PlayerChatListener.
 */
public class NpcDialogueService {

    private final DndPlugin plugin;
    private final Map<UUID, ActiveDialog> activeDialogs = new ConcurrentHashMap<>();

    private static final String[] SIDE_GREETINGS = {
            "Good day to you, traveler.", "Lovely weather we're having.",
            "Watch your step out there.", "I've heard strange noises from the forest...",
            "Welcome to these parts.", "Need any supplies? ...Oh, I'm not a merchant.",
            "Don't mind me, just passing through.", "Stay safe on the roads.",
            "Another adventurer, eh? Good luck.", "Nice to meet a friendly face."
    };

    public NpcDialogueService(DndPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== PUBLIC API ====================

    public ActiveDialog getActiveDialog(Player player) {
        return activeDialogs.get(player.getUniqueId());
    }

    /** Start dialog — NPC greeting + first question. */
    public void generateDialogue(NpcDataStore.NpcRecord npc, Player player) {
        startConversation(npc, player, null);
    }

    public void generateDialogue(NpcDataStore.NpcRecord npc, Player player, String playerMessage) {
        startConversation(npc, player, playerMessage);
    }

    /** Process player chat response during active dialog. */
    public void processPlayerResponse(Player player, String message) {
        ActiveDialog dialog = activeDialogs.get(player.getUniqueId());
        if (dialog == null) return;

        dialog.addPartyLine(player.getName(), message);

        if (dialog.isMaxedOut()) {
            resolveDialog(player, dialog, "auto_resolved (max turns)");
            return;
        }

        // Kirim ke AI buat next NPC response
        NpcDataStore.NpcRecord npc = plugin.getNpcDataStore().get(dialog.npcId);
        if (npc == null) {
            endDialog(player, dialog);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String prompt = buildConversationPrompt(npc, player, dialog);
                String response = plugin.getDeepSeekClient().complete(buildNpcSystemPrompt(npc, dialog), prompt);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    String dialogText = extractField(response, "DIALOG");
                    String questionStr = extractField(response, "QUESTION");
                    String action = extractField(response, "ACTION");

                    boolean hasQuestion = "true".equalsIgnoreCase(questionStr);

                    if (!dialogText.isEmpty()) {
                        dialog.addNpcLine(dialogText);
                        player.sendMessage("§e[" + npc.displayName + "]§r " + dialogText);
                    }

                    if (hasQuestion) {
                        player.sendMessage("§7(Ketik di chat untuk menjawab)");
                    } else {
                        // NPC sudah punya keputusan — resolve
                        resolveDialog(player, dialog, action);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("NPC dialogue failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§e[" + dialog.npcName + "]§r *remains silent*");
                    endDialog(player, dialog);
                });
            }
        });
    }

    /** Side NPC greeting — statis, gak pake AI. */
    public String generateSideNpcGreeting(String npcName, String biome) {
        int index = Math.abs(npcName.hashCode()) % SIDE_GREETINGS.length;
        return SIDE_GREETINGS[index];
    }

    // ==================== DIALOG STATE MACHINE ====================

    private void startConversation(NpcDataStore.NpcRecord npc, Player player, String playerMessage) {
        // Cek existing dialog
        if (activeDialogs.containsKey(player.getUniqueId())) {
            player.sendMessage("§7Kamu masih dalam percakapan dengan NPC lain.");
            return;
        }

        ActiveDialog dialog = new ActiveDialog(npc.npcId, npc.displayName);
        activeDialogs.put(player.getUniqueId(), dialog);

        if (playerMessage != null) {
            dialog.addPartyLine(player.getName(), playerMessage);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String prompt = buildConversationPrompt(npc, player, dialog);
                String response = plugin.getDeepSeekClient().complete(buildNpcSystemPrompt(npc, dialog), prompt);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    String dialogText = extractField(response, "DIALOG");
                    String questionStr = extractField(response, "QUESTION");
                    String action = extractField(response, "ACTION");

                    boolean hasQuestion = "true".equalsIgnoreCase(questionStr);

                    if (!dialogText.isEmpty()) {
                        dialog.addNpcLine(dialogText);
                        player.sendMessage("§e[" + npc.displayName + "]§r " + dialogText);
                    }

                    if (hasQuestion) {
                        player.sendMessage("§7(Ketik di chat untuk menjawab)");
                    } else {
                        resolveDialog(player, dialog, action);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("NPC dialogue failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§e[" + npc.displayName + "]§r *remains silent*");
                    endDialog(player, dialog);
                });
            }
        });
    }

    private void resolveDialog(Player player, ActiveDialog dialog, String action) {
        action = action != null ? action.toUpperCase() : "NEUTRAL";
        NpcDataStore.NpcRecord npc = plugin.getNpcDataStore().get(dialog.npcId);

        // Generate summary & save to history
        if (npc != null) {
            String summary = "Party talked with " + npc.displayName
                    + " (" + dialog.turnCount + " turns). Outcome: " + action;
            plugin.getNpcDataStore().appendHistory(npc.npcId, summary);

            // Update disposition based on action
            switch (action) {
                case "FRIENDLY" -> plugin.getNpcDataStore().setDisposition(npc.npcId, "friendly");
                case "HOSTILE" -> {
                    plugin.getNpcDataStore().setDisposition(npc.npcId, "agro");
                    player.sendMessage("§c" + npc.displayName + " becomes hostile!");
                    activateNpcHostility(npc.npcId, player);
                }
                case "STEAL" -> player.sendMessage("§6" + npc.displayName + " mencoba mencuri itemmu!");
            }

            // Add session summary event
            plugin.getPartySessionStore().addEvent(
                    "Party spoke with " + npc.displayName + " (" + action.toLowerCase() + ")");
        }

        endDialog(player, dialog);
    }

    private void endDialog(Player player, ActiveDialog dialog) {
        activeDialogs.remove(player.getUniqueId());
        player.sendMessage("§7[Percakapan selesai]");
    }

    // ==================== PROMPT BUILDING ====================

    private String buildNpcSystemPrompt(NpcDataStore.NpcRecord npc, ActiveDialog dialog) {
        StringBuilder sb = new StringBuilder();
        sb.append("Kamu adalah ").append(npc.displayName).append(".\n");

        // Backstory
        if (npc.backstory != null && !npc.backstory.isBlank()
                && !npc.backstory.startsWith("(Add backstory")) {
            sb.append("BACKSTORY:\n").append(npc.backstory).append("\n\n");
        }

        sb.append("Kamu berbicara dengan party adventurer. Gunakan bahasa Indonesia + istilah fantasy Inggris.\n");
        sb.append("Disposition kamu: ").append(npc.disposition).append(".\n");
        sb.append("Max turns: ").append(ActiveDialog.MAX_TURNS)
                .append(". Turn sekarang: ").append(dialog.turnCount + 1).append(".\n\n");

        sb.append("Jika ini turn pertama: sapa party dan ajukan pertanyaan.\n");
        sb.append("Jika sudah cukup info: tentukan ACTION.\n");
        sb.append("Jika turn terakhir: HARUS tentukan ACTION.\n\n");

        sb.append("Jawab dalam format EXACT:\n");
        sb.append("DIALOG: <ucapan NPC>\n");
        sb.append("QUESTION: <true|false>\n");
        sb.append("ACTION: <NEUTRAL|FRIENDLY|HOSTILE|STEAL|NONE>\n");

        return sb.toString();
    }

    private String buildConversationPrompt(NpcDataStore.NpcRecord npc, Player player, ActiveDialog dialog) {
        StringBuilder sb = new StringBuilder();
        sb.append("Party member: ").append(player.getName())
                .append(" (Lv.").append(plugin.getPlayerDataStore().getLevel(player)).append(")\n\n");

        if (!dialog.conversation.isEmpty()) {
            sb.append("Percakapan sejauh ini:\n");
            sb.append(dialog.buildConversationContext()).append("\n");
        } else {
            sb.append("Party menyapamu.\n\n");
        }

        sb.append("Respon sebagai ").append(npc.displayName).append(".");
        return sb.toString();
    }

    // ==================== HELPERS ====================

    private String extractField(String response, String field) {
        if (response == null) return "";
        for (String line : response.split("\n")) {
            if (line.trim().toUpperCase().startsWith(field.toUpperCase() + ":")) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return "";
    }

    private void activateNpcHostility(String npcId, Player target) {
        for (Entity entity : target.getWorld().getEntities()) {
            if (entity.getPersistentDataContainer().has(
                    NpcSpawnListener.npcIdKey(), PersistentDataType.STRING)) {
                String foundId = entity.getPersistentDataContainer().get(
                        NpcSpawnListener.npcIdKey(), PersistentDataType.STRING);
                if (npcId.equals(foundId) && entity instanceof Mob mob) {
                    mob.setAware(true);
                    mob.setTarget(target);
                    break;
                }
            }
        }
    }
}
