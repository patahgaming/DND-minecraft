package id.patahgaming.dndplugin.engine.combat;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.dice.DiceRoller;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Initiative system D&D 5e: tiap participant roll d20 + DEX modifier,
 * urutkan descending → jadi turn order.
 */
public class InitiativeRoller {

    public static class InitiativeEntry {
        public UUID entityUuid;
        public String displayName;
        public int roll;
        public int dexModifier;
        public int total;
    }

    /**
     * Roll initiative untuk semua participant (player + mob).
     * Return list terurut dari total tertinggi ke terendah.
     */
    public static List<InitiativeEntry> rollInitiative(DndPlugin plugin, List<LivingEntity> participants) {
        List<InitiativeEntry> entries = new ArrayList<>();

        for (LivingEntity entity : participants) {
            InitiativeEntry entry = new InitiativeEntry();
            entry.entityUuid = entity.getUniqueId();
            entry.displayName = entity instanceof Player p ? p.getName() : entity.getName();
            entry.roll = DiceRoller.roll(20);

            // DEX modifier: player dari PDC, mob default +0
            if (entity instanceof Player player) {
                entry.dexModifier = plugin.getPlayerDataStore().getAbilityModifier(player, "dex");
            } else {
                entry.dexModifier = 0;
            }

            entry.total = entry.roll + entry.dexModifier;
            entries.add(entry);
        }

        // Sort descending by total, lalu by DEX modifier (tiebreaker), lalu by roll
        entries.sort(Comparator
                .comparingInt((InitiativeEntry e) -> e.total).reversed()
                .thenComparingInt(e -> e.dexModifier).reversed()
                .thenComparingInt(e -> e.roll).reversed());

        return entries;
    }
}
