package id.patahgaming.dndplugin.engine.combat;

import id.patahgaming.dndplugin.DndPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turn-based combat state machine:
 *
 *   WAITING_TURN → ACTION_SELECTED → RESOLVING → NEXT_TURN
 *
 * - Hanya satu participant yang aktif per giliran
 * - /atk dan /roll hanya valid saat giliran player aktif
 * - HP update → scoreboard sidebar di-refresh oleh listener yang sudah ada
 */
public class TurnManager {

    public enum CombatState {
        WAITING_TURN,    // Menunggu participant saat ini memilih aksi
        ACTION_SELECTED, // Aksi sudah dipilih, sedang di-resolve
        RESOLVING,       // Animasi/efek sedang berjalan
    }

    public enum BattleState {
        INACTIVE,  // Tidak ada battle
        ACTIVE     // Battle sedang berlangsung
    }

    private final DndPlugin plugin;
    private BattleState battleState = BattleState.INACTIVE;
    private CombatState combatState = CombatState.WAITING_TURN;

    private final List<InitiativeRoller.InitiativeEntry> turnOrder = new ArrayList<>();
    private int currentTurnIndex = 0;
    private int roundNumber = 0;

    private final Map<UUID, LivingEntity> participants = new HashMap<>();

    public TurnManager(DndPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Mulai battle — roll initiative, freeze mobs, mulai round 1.
     */
    public List<InitiativeRoller.InitiativeEntry> startBattle(List<LivingEntity> battleParticipants) {
        participants.clear();
        for (LivingEntity e : battleParticipants) {
            participants.put(e.getUniqueId(), e);
        }

        // Freeze semua mob AI — cuma bertindak pas giliran turn-based
        for (LivingEntity e : battleParticipants) {
            if (e instanceof Mob mob && !(e instanceof Player)) {
                mob.setAware(false);
            }
        }

        turnOrder.clear();
        turnOrder.addAll(InitiativeRoller.rollInitiative(plugin, battleParticipants));

        currentTurnIndex = -1; // akan maju ke 0 di beginFirstTurn
        roundNumber = 1;
        battleState = BattleState.ACTIVE;
        combatState = CombatState.WAITING_TURN;

        // Broadcast turn order
        plugin.getLogger().info("=== Battle Started — Round " + roundNumber + " ===");
        StringBuilder sb = new StringBuilder("§c§l⚔ BATTLE STARTED! ⚔§r");
        sb.append("\n§7Round 1 — Initiative:");
        for (int i = 0; i < turnOrder.size(); i++) {
            var e = turnOrder.get(i);
            sb.append("\n§e  ").append(i + 1).append(". ").append(e.displayName)
                    .append(" §7(").append(e.total).append(")");
        }
        String msg = sb.toString();
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));

        // Mulai turn pertama
        Bukkit.getScheduler().runTaskLater(plugin, this::nextTurn, 20L);

        return turnOrder;
    }

    /**
     * Panggil setelah aksi participant saat ini selesai di-resolve.
     * Maju ke participant berikutnya atau next round.
     */
    private int nextTurnDepth = 0;
    private static final int MAX_NEXT_TURN_DEPTH = 50;

    public void nextTurn() {
        nextTurnDepth++;
        if (nextTurnDepth > MAX_NEXT_TURN_DEPTH) {
            plugin.getLogger().warning("nextTurn recursion exceeded! Ending battle.");
            nextTurnDepth = 0;
            endBattle();
            return;
        }

        currentTurnIndex++;
        combatState = CombatState.WAITING_TURN;

        // Cek battle selesai? (semua musuh mati atau semua player mati)
        if (checkBattleEnd()) {
            nextTurnDepth = 0;
            endBattle();
            return;
        }

        // Wrap around ke round berikutnya
        if (currentTurnIndex >= turnOrder.size()) {
            currentTurnIndex = 0;
            roundNumber++;
            Bukkit.getOnlinePlayers().forEach(p ->
                    p.sendMessage("§c=== Round " + roundNumber + " ===§r"));
        }

        var current = turnOrder.get(currentTurnIndex);
        LivingEntity entity = participants.get(current.entityUuid);
        if (entity == null || entity.isDead()) {
            nextTurn(); // skip dead/missing
            return;
        }
        if (entity instanceof Player player) {
            player.sendMessage("§a⚔ Giliran kamu! §7/atk melee §8| §7/atk ranged §8| §7/cast §8| §7/battle flee");
            player.sendMessage("§7  Turn: " + getCurrentTurnDisplay());
        } else {
            // Mob turn — auto attack random player
            runMobTurn(entity);
        }
    }

    /** Auto-AI untuk mob: unfreeze → serang → re-freeze → next turn. */
    private void runMobTurn(LivingEntity mob) {
        // Unfreeze sementara untuk giliran mob
        if (mob instanceof Mob m && !(mob instanceof Player)) {
            m.setAware(true);
        }

        Player target = null;
        double closest = Double.MAX_VALUE;
        for (var entry : participants.entrySet()) {
            if (entry.getValue() instanceof Player p && !p.isDead()) {
                double dist = mob.getLocation().distanceSquared(p.getLocation());
                if (dist < closest) {
                    closest = dist;
                    target = p;
                }
            }
        }

        if (target == null) {
            if (mob instanceof Mob m && !(mob instanceof Player)) m.setAware(false);
            nextTurn();
            return;
        }

        // Mob attack resolution
        CombatEngine.AttackResult result = CombatEngine.resolveMobAttack(mob, target);

        if (result.hit) {
            int dmg = CombatEngine.applyDamage(target, result.totalDamage, result.damageType);
            Bukkit.broadcastMessage("§c[Combat]§r " + safeName(mob) + " §7→§r "
                    + safeName(target) + " §c-" + dmg + " HP §7[" + result.damageType + "]");

            if (target.getHealth() <= 0) {
                Bukkit.broadcastMessage("§c§l" + safeName(target) + " is unconscious!");
                removeParticipant(target);
            }
        } else {
            Bukkit.broadcastMessage("§7[Combat] " + safeName(mob) + " misses " + safeName(target) + "!");
        }

        // Re-freeze mob setelah giliran
        if (mob instanceof Mob m && !(mob instanceof Player)) {
            m.setAware(false);
        }

        // Delay ke next turn
        Bukkit.getScheduler().runTaskLater(plugin, this::nextTurn, 30L);
    }

    /**
     * Set state ke ACTION_SELECTED saat player pakai /atk atau /roll.
     */
    public void selectAction(Player player) {
        if (battleState != BattleState.ACTIVE) {
            player.sendMessage("§cTidak ada battle yang sedang berlangsung.");
            return;
        }
        if (combatState != CombatState.WAITING_TURN) {
            player.sendMessage("§cBukan giliran kamu atau aksi sedang di-resolve.");
            return;
        }

        var current = turnOrder.get(currentTurnIndex);
        if (!current.entityUuid.equals(player.getUniqueId())) {
            player.sendMessage("§cBukan giliran kamu! Tunggu giliran.");
            return;
        }

        combatState = CombatState.ACTION_SELECTED;
    }

    /**
     * Resolve aksi → set RESOLVING, lalu auto-next turn.
     */
    public void resolveAction() {
        combatState = CombatState.RESOLVING;
        // Auto-next setelah resolve (bisa ditambah delay animasi nanti)
        Bukkit.getScheduler().runTaskLater(plugin, this::nextTurn, 20L); // 1 detik
    }

    /** Cek apakah battle harus berakhir. */
    private boolean checkBattleEnd() {
        boolean allPlayersDead = participants.values().stream()
                .filter(e -> e instanceof Player)
                .allMatch(e -> e.isDead());
        boolean allMobsDead = participants.values().stream()
                .filter(e -> !(e instanceof Player))
                .allMatch(LivingEntity::isDead);

        return allPlayersDead || allMobsDead || participants.size() <= 1;
    }

    /** Akhiri battle — unfreeze semua mob yang selamat. */
    public void endBattle() {
        // Unfreeze semua mob yang masih hidup
        for (LivingEntity e : participants.values()) {
            if (e instanceof Mob mob && !(e instanceof Player) && !e.isDead()) {
                mob.setAware(true);
            }
        }

        battleState = BattleState.INACTIVE;
        combatState = CombatState.WAITING_TURN;
        turnOrder.clear();
        participants.clear();
        currentTurnIndex = 0;
        roundNumber = 0;

        Bukkit.getOnlinePlayers().forEach(p ->
                p.sendMessage("§a§l=== Battle Ended ===§r"));
        plugin.getLogger().info("Battle ended.");
    }

    /** Hapus participant dari battle (mati / kabur). */
    public void removeParticipant(LivingEntity entity) {
        participants.remove(entity.getUniqueId());
        turnOrder.removeIf(e -> e.entityUuid.equals(entity.getUniqueId()));
        if (currentTurnIndex >= turnOrder.size()) {
            currentTurnIndex = 0;
        }
    }

    /** Display turn info buat player. */
    public String getCurrentTurnDisplay() {
        if (battleState != BattleState.ACTIVE || turnOrder.isEmpty()) return "No battle";
        var current = turnOrder.get(currentTurnIndex);
        LivingEntity entity = participants.get(current.entityUuid);
        String name = entity instanceof Player p ? p.getName() : entity.getName();
        return "Round " + roundNumber + " — " + name + " [" + current.total + "]";
    }

    // --- Getters ---

    public BattleState getBattleState() { return battleState; }
    public CombatState getCombatState() { return combatState; }
    public int getCurrentTurnIndex() { return currentTurnIndex; }
    public int getRoundNumber() { return roundNumber; }
    public List<InitiativeRoller.InitiativeEntry> getTurnOrder() { return turnOrder; }
    public Map<UUID, LivingEntity> getParticipants() { return participants; }

    /** Safe name: fallback ke type name kalau getName() null (custom name override). */
    private String safeName(LivingEntity e) {
        if (e instanceof Player p) return p.getName();
        String name = e.getName();
        return name != null ? name : e.getType().name();
    }

    /** Cek apakah player tertentu sedang mendapat giliran. */
    public boolean isPlayerTurn(Player player) {
        if (battleState != BattleState.ACTIVE) return false;
        if (currentTurnIndex >= turnOrder.size()) return false;
        return turnOrder.get(currentTurnIndex).entityUuid.equals(player.getUniqueId());
    }
}
