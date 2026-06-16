package id.patahgaming.dndplugin.ai;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.PoiStore;
import id.patahgaming.dndplugin.data.StoryTreeStore;
import id.patahgaming.dndplugin.data.PlayerDataStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

/**
 * AI Narrator — dual mode:
 *
 * 1. ON-RAIL: Player mengikuti story tree → AI render narrative template +
 *    isi detail kontekstual (nama player, posisi, waktu), tawarkan choices.
 *
 * 2. OFF-RAIL: Player keluar dari story tree (tidak ada node yang match) →
 *    AI generate cerita baru, tetap constrained oleh world POI/lore yang ada
 *    di YAML (tanpa Qdrant — context langsung dari file POI + SRD relevan).
 *
 * Semua panggilan DeepSeek dilakukan secara ASYNC (BukkitScheduler).
 * Response AI ditampilkan via public chat (narasi grup).
 */
public class NarratorService {

    private final DndPlugin plugin;

    public NarratorService(DndPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * On-rail narration — player masih di dalam story tree.
     * AI memperkaya narrative template node dengan detail dunia + party.
     */
    public void narrateOnRail(StoryTreeStore.StoryNode node, Player triggerPlayer) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String systemPrompt = buildNarratorSystemPrompt();
                String userPrompt = buildOnRailPrompt(node, triggerPlayer);

                String narration = plugin.getDeepSeekClient().complete(systemPrompt, userPrompt);

                // Balik ke main thread untuk broadcast
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String formatted = "§6[Narrator]§r " + narration;
                    Bukkit.broadcastMessage(formatted);

                    // Tampilkan choices jika ada
                    if (!node.choices.isEmpty()) {
                        Bukkit.broadcastMessage("§e--- Choices ---");
                        for (StoryTreeStore.Choice choice : node.choices) {
                            Bukkit.broadcastMessage("§a  • " + choice.id + " §7→ " + choice.nextNode);
                        }
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Narrator on-rail failed: " + e.getMessage());
                // Fallback: tampilkan narrative template mentah
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.broadcastMessage("§6[Narrator]§r " + node.narrative);
                });
            }
        });
    }

    /**
     * Off-rail narration — player keluar dari story tree.
     * AI generate cerita baru dari nol, constrained oleh POI + party context.
     */
    public void narrateOffRail(Player triggerPlayer) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String systemPrompt = buildNarratorSystemPrompt();
                String userPrompt = buildOffRailPrompt(triggerPlayer);

                String narration = plugin.getDeepSeekClient().complete(systemPrompt, userPrompt);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.broadcastMessage("§6[Narrator]§r " + narration);
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Narrator off-rail failed: " + e.getMessage());
            }
        });
    }

    /**
     * System prompt: set kepribadian AI narator — omniscient, fantasy DM style.
     * Include relevant SRD rules kalau ada keyword yang match di story node.
     */
    private String buildNarratorSystemPrompt() {
        return """
                You are a Dungeon Master narrating a D&D 5e adventure set in a fantasy world.
                Your tone is immersive, descriptive, and atmospheric — like a skilled DM at a table.

                Rules:
                - Narrate in the present tense, second-person ("You see...")
                - Be concise but evocative (3-5 sentences for narration)
                - Include sensory details (sight, sound, smell)
                - When presenting choices, list them clearly
                - Stay consistent with the world lore and locations provided
                - NEVER break character or reference "the AI" or "the game"
                - Respond in English (the game uses English for narration)
                """;
    }

    /**
     * Lookup SRD rules yang relevan untuk narrative node.
     * Cari keyword dari narrative text + node id.
     */
    private String lookupRelevantRules(StoryTreeStore.StoryNode node) {
        if (plugin.getSrdRulesStore() == null || !plugin.getSrdRulesStore().isLoaded()) {
            return "";
        }
        // Cari keyword dari narrative text (kata benda spesifik)
        String[] keywords = {"stealth", "perception", "survival", "investigation",
                "combat", "grapple", "cover", "difficult terrain", "jump"};
        return plugin.getSrdRulesStore().searchMultiple(keywords);
    }

    /**
     * Build on-rail prompt: template narrative + party context + choices.
     */
    private String buildOnRailPrompt(StoryTreeStore.StoryNode node, Player triggerPlayer) {
        StringBuilder sb = new StringBuilder();
        sb.append("NARRATION CONTEXT (On-Rail)\n\n");

        // Narrative template dari story node
        sb.append("Base narrative template:\n");
        sb.append("\"").append(node.narrative).append("\"\n\n");

        // Party context
        appendPartyContext(sb, triggerPlayer);

        // POI context (POI yang mencakup posisi player)
        if (triggerPlayer != null) {
            PoiStore.Poi poi = plugin.getPoiStore().findContaining(
                    triggerPlayer.getLocation().getX(),
                    triggerPlayer.getLocation().getZ());
            if (poi != null) {
                sb.append("Current location: ").append(poi.name)
                        .append(" (").append(poi.biome).append(" biome)\n\n");
            }
        }

        // Choices
        if (!node.choices.isEmpty()) {
            sb.append("Available choices for the party:\n");
            for (StoryTreeStore.Choice choice : node.choices) {
                sb.append("- ").append(choice.id).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Expand the base narrative into a vivid 3-5 sentence narration. ");
        sb.append("Incorporate the party members' names naturally. ");
        if (!node.choices.isEmpty()) {
            sb.append("End by presenting the available choices to the party. ");
        }
        sb.append("Do NOT include meta-commentary or markdown formatting.");

        return sb.toString();
    }

    /**
     * Build off-rail prompt: no template, fully AI-generated constrained by context.
     */
    private String buildOffRailPrompt(Player triggerPlayer) {
        StringBuilder sb = new StringBuilder();
        sb.append("NARRATION CONTEXT (Off-Rail — Free Narration)\n\n");
        sb.append("The party has ventured off the main story path. ");
        sb.append("Generate a fitting narrative based on their current situation.\n\n");

        // Party context
        appendPartyContext(sb, triggerPlayer);

        // POI context — semua POI untuk world building
        sb.append("Known locations in this world:\n");
        for (PoiStore.Poi poi : plugin.getPoiStore().getAll()) {
            sb.append("- ").append(poi.name).append(" (").append(poi.biome)
                    .append(") at bbox [").append(poi.bbox.x1).append(",")
                    .append(poi.bbox.z1).append(" to ").append(poi.bbox.x2)
                    .append(",").append(poi.bbox.z2).append("]\n");
        }

        // Current POI (jika ada)
        if (triggerPlayer != null) {
            PoiStore.Poi currentPoi = plugin.getPoiStore().findContaining(
                    triggerPlayer.getLocation().getX(),
                    triggerPlayer.getLocation().getZ());
            if (currentPoi != null) {
                sb.append("\nParty is currently at: ").append(currentPoi.name)
                        .append(" (").append(currentPoi.biome).append(")\n");
            } else {
                sb.append("\nParty is currently in an unexplored area. ");
                sb.append("Describe what they discover.\n");
            }
        }

        sb.append("\nGenerate a 3-5 sentence narration describing what the party ");
        sb.append("encounters. Do NOT invent major plot points or new NPCs — focus on ");
        sb.append("atmosphere, environment, and minor details. Do NOT include meta-commentary.");

        return sb.toString();
    }

    /**
     * Append party member names, stats summary, dan story progress.
     */
    private void appendPartyContext(StringBuilder sb, Player triggerPlayer) {
        PlayerDataStore store = plugin.getPlayerDataStore();
        sb.append("Party members:\n");
        for (Player p : Bukkit.getOnlinePlayers()) {
            sb.append("- ").append(p.getName())
                    .append(" (Lv.").append(store.getLevel(p)).append(" ")
                    .append("HP: ").append((int) p.getHealth()).append("/")
                    .append((int) p.getMaxHealth()).append(")");
            if (p.equals(triggerPlayer)) {
                sb.append(" [triggering player]");
            }
            sb.append("\n");
        }
        sb.append("Party story node: ").append(store.getPartyCurrentNode()).append("\n\n");
    }
}
