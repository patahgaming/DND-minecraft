package id.patahgaming.dndplugin.model;

/** Narration mode — semi (wait for player input) or auto (AI chooses next node). */
public enum NarratorMode {
    SEMI,  // AI narasi, tapi pilihan story tetap butuh input player
    AUTO;  // AI pilihkan next_node otomatis

    public static NarratorMode fromString(String s) {
        try { return valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return SEMI; }
    }
}
