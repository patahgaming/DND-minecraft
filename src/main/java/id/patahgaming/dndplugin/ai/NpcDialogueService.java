package id.patahgaming.dndplugin.ai;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.NpcDataStore;
import id.patahgaming.dndplugin.data.PlayerDataStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * AI NPC Dialogue — handle dialog dengan NPC penting.
 *
 * AI decide real-time:
 * - Disposition: agro / teman / damai / sleight-of-hand
 * - Response berdasarkan: disposition, history per-NPC (shared party),
 *   story node, character party stats
 *
 * Semua panggilan DeepSeek dilakukan secara ASYNC.
 * Response AI dikirim via private message (whisper) ke player yang berinteraksi.
 */
public class NpcDialogueService {

    private final DndPlugin plugin;

    public NpcDialogueService(DndPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Generate dialog NPC penting — tanpa player message (greeting, dari interact).
     */
    public void generateDialogue(NpcDataStore.NpcRecord npc, Player player) {
        generateDialogue(npc, player, null);
    }

    /**
     * Generate dialog NPC penting dengan player message spesifik.
     * Dipanggil dari TalkCommand saat player ketik: /talk <npc_id> <pesan>
     *
     * @param npc NPC record dari data store
     * @param player Player yang ngomong
     * @param playerMessage Pesan dari player (null kalau cuma greeting)
     */
    public void generateDialogue(NpcDataStore.NpcRecord npc, Player player, String playerMessage) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String systemPrompt = buildNpcSystemPrompt(npc);
                String userPrompt = buildDialoguePrompt(npc, player, playerMessage);

                String response = plugin.getDeepSeekClient().complete(systemPrompt, userPrompt);

                // Parse disposition dari response (prefix [AGRO], [FRIENDLY], dll)
                String disposition = extractDisposition(response);
                String cleanResponse = cleanResponse(response);

                // Update disposition di data store jika berubah
                if (disposition != null && !disposition.equals(npc.disposition)) {
                    plugin.getNpcDataStore().setDisposition(npc.npcId, disposition);
                }

                // Append history
                String inputSummary = playerMessage != null
                        ? player.getName() + ": \"" + (playerMessage.length() > 60 ? playerMessage.substring(0, 60) + "..." : playerMessage) + "\""
                        : player.getName() + " greets you";
                plugin.getNpcDataStore().appendHistory(npc.npcId, inputSummary);
                String responseSummary = cleanResponse.length() > 80 ? cleanResponse.substring(0, 80) + "..." : cleanResponse;
                plugin.getNpcDataStore().appendHistory(npc.npcId, npc.displayName + ": " + responseSummary);

                // Kirim response ke player (whisper)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§e[" + npc.displayName + "]§r " + cleanResponse);

                    // Jika agro, kasih tahu player
                    if ("agro".equals(disposition)) {
                        player.sendMessage("§c" + npc.displayName + " becomes hostile!");
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("NPC dialogue failed for " + npc.npcId + ": " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§e[" + npc.displayName + "]§r *remains silent*");
                });
            }
        });
    }

    /**
     * Generate side NPC bubble text — cached, generated sekali saat spawn.
     * Karena side NPC tidak ada gameplay impact, pake prompt minimal.
     */
    public String generateSideNpcGreeting(String npcName, String biome) {
        // Side NPC greetings tidak perlu AI — cukup random dari pool statis.
        // Ini menghindari API call berlebihan untuk NPC flavor.
        String[] greetings = {
                "Good day to you, traveler.",
                "Lovely weather we're having.",
                "Watch your step out there.",
                "I've heard strange noises from the forest...",
                "Welcome to these parts.",
                "Need any supplies? ...Oh, I'm not a merchant.",
                "Don't mind me, just passing through.",
                "Stay safe on the roads.",
                "Another adventurer, eh? Good luck.",
                "Nice to meet a friendly face."
        };
        int index = Math.abs(npcName.hashCode()) % greetings.length;
        return greetings[index];
    }

    /**
     * Build system prompt untuk NPC — kepribadian berdasarkan disposition + history.
     */
    private String buildNpcSystemPrompt(NpcDataStore.NpcRecord npc) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are roleplaying as an NPC in a D&D 5e fantasy world.\n\n");

        sb.append("Your name: ").append(npc.displayName).append("\n");
        sb.append("Your current disposition toward the party: ").append(npc.disposition).append("\n\n");

        sb.append("Personality rules:\n");
        sb.append("- Stay in character ALWAYS — never break the fourth wall\n");
        sb.append("- Respond in 1-3 sentences, conversational tone\n");
        sb.append("- Your disposition affects how you speak:\n");
        sb.append("  • neutral: cautious but willing to talk\n");
        sb.append("  • friendly: warm, helpful, may share information freely\n");
        sb.append("  • agro/hostile: aggressive, threatening, may attack\n");
        sb.append("  • damai/peaceful: calm, serene, diplomatic\n");

        // History
        if (!npc.history.isEmpty()) {
            sb.append("\nPrevious interactions with this party:\n");
            for (int i = Math.max(0, npc.history.size() - 5); i < npc.history.size(); i++) {
                var entry = npc.history.get(i);
                sb.append("- ").append(entry.getOrDefault("summary", "")).append("\n");
            }
        }

        sb.append("\nIMPORTANT: Start your response with one of these tags on its own line:\n");
        sb.append("[FRIENDLY] [NEUTRAL] [AGRO] [PEACEFUL]\n");
        sb.append("Then write the dialogue. The tag determines your new disposition.\n");

        return sb.toString();
    }

    /**
     * Build user prompt — party context + what the player says.
     */
    private String buildDialoguePrompt(NpcDataStore.NpcRecord npc, Player player, String playerMessage) {
        StringBuilder sb = new StringBuilder();
        PlayerDataStore store = plugin.getPlayerDataStore();

        sb.append("A party member approaches you.\n\n");
        sb.append("Speaking to you: ").append(player.getName())
                .append(" (Lv.").append(store.getLevel(player)).append(")\n");

        // Party context
        sb.append("Other party members present: ");
        String others = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player))
                .map(Player::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
        sb.append(others).append("\n");

        sb.append("Current story context: ").append(store.getPartyCurrentNode()).append("\n\n");

        if (playerMessage != null && !playerMessage.isBlank()) {
            sb.append("The player says to you: \"").append(playerMessage).append("\"\n\n");
            sb.append("Respond naturally to what the player said. Stay in character. ");
            sb.append("If friendly, be helpful. If neutral, be cautious. If agro, be hostile.");
        } else {
            sb.append("The player greets you. Respond naturally based on your disposition. ");
            sb.append("If friendly, offer useful information about the area. ");
            sb.append("If neutral, be cautious but civil. ");
            sb.append("If agro, be hostile and threatening.");
        }

        return sb.toString();
    }

    /**
     * Extract disposition tag dari response AI.
     */
    private String extractDisposition(String response) {
        String upper = response.toUpperCase();
        if (upper.contains("[AGRO]") || upper.contains("[HOSTILE]")) return "agro";
        if (upper.contains("[FRIENDLY]")) return "friendly";
        if (upper.contains("[PEACEFUL]") || upper.contains("[DAMAI]")) return "damai";
        if (upper.contains("[NEUTRAL]")) return "neutral";
        return null; // tidak berubah
    }

    /**
     * Bersihkan tag disposition dari response.
     */
    private String cleanResponse(String response) {
        return response.replaceAll("(?i)\\[(FRIENDLY|NEUTRAL|AGRO|HOSTILE|PEACEFUL|DAMAI)\\]", "").trim();
    }
}
