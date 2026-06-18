package id.patahgaming.dndplugin.model;

import java.util.ArrayList;
import java.util.List;

/**
 * State per-player yang sedang dalam multi-turn NPC dialog.
 * Disimpan di Map<UUID, ActiveDialog> di NpcDialogueService.
 */
public class ActiveDialog {

    public static class Turn {
        public String speaker; // "npc" or "party"
        public String text;
        public Turn(String speaker, String text) { this.speaker = speaker; this.text = text; }
    }

    public String npcId;
    public String npcName;
    public List<Turn> conversation = new ArrayList<>();
    public int turnCount = 0;
    public static final int MAX_TURNS = 5;

    public ActiveDialog(String npcId, String npcName) {
        this.npcId = npcId;
        this.npcName = npcName;
    }

    public boolean isMaxedOut() { return turnCount >= MAX_TURNS; }

    public void addNpcLine(String text) {
        conversation.add(new Turn("npc", text));
        turnCount++;
    }

    public void addPartyLine(String playerName, String text) {
        conversation.add(new Turn("party", playerName + ": " + text));
    }

    /** Build conversation context untuk dikirim ke AI. */
    public String buildConversationContext() {
        StringBuilder sb = new StringBuilder();
        for (Turn t : conversation) {
            if ("npc".equals(t.speaker)) sb.append(t.speaker).append(": ").append(t.text).append("\n");
            else sb.append("party").append(": ").append(t.text).append("\n");
        }
        return sb.toString();
    }
}
