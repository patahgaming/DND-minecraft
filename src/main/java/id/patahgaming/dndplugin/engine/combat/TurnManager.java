package id.patahgaming.dndplugin.engine.combat;

import id.patahgaming.dndplugin.DndPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Turn-based combat state machine:
 *
 *   WAITING_TURN → ACTION_SELECTED → RESOLVING → NEXT_TURN
 *
 * PATCH v1.0.0.39: Death saving throw flow.
 * - Player HP 0 → UNCONSCIOUS (DeathSaveManager)
 * - Giliran unconscious → promptDeathSave (set PendingRoll DEATH_SAVE)
 * - Mob skip unconscious player
 * - Battle end: unconscious ≠ dead
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

    // PvP: team assignment — 0 = party (default PvE), 1+ = PvP teams
    // null entry = team 0 (party). During PvP, players are on different teams.
    private final Map<UUID, Integer> teamAssignments = new HashMap<>();
    private boolean isPvP = false;

    // DeathSaveManager — di-inject dari DndPlugin
    private DeathSaveManager deathSaveManager;

    public TurnManager(DndPlugin plugin) {
        this.plugin = plugin;
    }

    /** Dipanggil setelah plugin init DeathSaveManager. */
    public void setDeathSaveManager(DeathSaveManager deathSaveManager) {
        this.deathSaveManager = deathSaveManager;
    }

    // ==================== BATTLE START ====================

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

    // ==================== PVP BATTLE ====================

    /**
     * Mulai duel 1v1 antara dua player. PvP mode: 2 teams.
     */
    public List<InitiativeRoller.InitiativeEntry> startDuel(Player challenger, Player opponent) {
        participants.clear();
        teamAssignments.clear();
        isPvP = true;

        List<LivingEntity> fighters = List.of(challenger, opponent);
        for (LivingEntity e : fighters) {
            participants.put(e.getUniqueId(), e);
        }

        // Team assignment: challenger = team 1, opponent = team 2
        teamAssignments.put(challenger.getUniqueId(), 1);
        teamAssignments.put(opponent.getUniqueId(), 2);

        turnOrder.clear();
        turnOrder.addAll(InitiativeRoller.rollInitiative(plugin, fighters));

        currentTurnIndex = -1;
        roundNumber = 1;
        battleState = BattleState.ACTIVE;
        combatState = CombatState.WAITING_TURN;

        // Broadcast
        Bukkit.broadcastMessage("§c§l⚔ PvP DUEL! ⚔§r");
        Bukkit.broadcastMessage("§c" + challenger.getName() + " §7vs §c" + opponent.getName());
        Bukkit.broadcastMessage("§7Initiative:");
        for (int i = 0; i < turnOrder.size(); i++) {
            var e = turnOrder.get(i);
            Bukkit.broadcastMessage("§e  " + (i + 1) + ". " + e.displayName + " §7(" + e.total + ")");
        }

        Bukkit.getScheduler().runTaskLater(plugin, this::nextTurn, 20L);
        plugin.getLogger().info("[PvP] Duel started: " + challenger.getName() + " vs " + opponent.getName());
        return turnOrder;
    }

    /**
     * Mulai PvP team battle. Setiap list Player adalah satu team.
     * Contoh: [[attacker1, attacker2], [defender]] = 2v1
     */
    public List<InitiativeRoller.InitiativeEntry> startPvPBattle(List<List<Player>> teams) {
        participants.clear();
        teamAssignments.clear();
        isPvP = true;

        List<LivingEntity> allFighters = new ArrayList<>();
        for (int t = 0; t < teams.size(); t++) {
            for (Player p : teams.get(t)) {
                participants.put(p.getUniqueId(), p);
                teamAssignments.put(p.getUniqueId(), t + 1); // team 1, 2, 3...
                allFighters.add(p);
            }
        }

        if (allFighters.size() < 2) {
            battleState = BattleState.INACTIVE;
            isPvP = false;
            return List.of();
        }

        turnOrder.clear();
        turnOrder.addAll(InitiativeRoller.rollInitiative(plugin, allFighters));

        currentTurnIndex = -1;
        roundNumber = 1;
        battleState = BattleState.ACTIVE;
        combatState = CombatState.WAITING_TURN;

        // Build team display
        StringBuilder teamInfo = new StringBuilder();
        for (int t = 0; t < teams.size(); t++) {
            if (t > 0) teamInfo.append(" §7vs ");
            teamInfo.append("§c");
            teamInfo.append(String.join("§7, §c", teams.get(t).stream().map(Player::getName).toList()));
        }

        Bukkit.broadcastMessage("§c§l⚔ PvP BATTLE! ⚔§r");
        Bukkit.broadcastMessage(teamInfo.toString());
        Bukkit.broadcastMessage("§7Initiative:");
        for (int i = 0; i < turnOrder.size(); i++) {
            var e = turnOrder.get(i);
            Bukkit.broadcastMessage("§e  " + (i + 1) + ". " + e.displayName + " §7(" + e.total + ")");
        }

        Bukkit.getScheduler().runTaskLater(plugin, this::nextTurn, 20L);
        plugin.getLogger().info("[PvP] Team battle started");
        return turnOrder;
    }

    /** Check apakah dua entity di team yang sama. */
    public boolean isSameTeam(UUID a, UUID b) {
        if (!isPvP) return true; // PvE: all players same party
        Integer teamA = teamAssignments.get(a);
        Integer teamB = teamAssignments.get(b);
        if (teamA == null || teamB == null) return true; // unassigned = same side
        return teamA.equals(teamB);
    }

    /** Check apakah ini battle PvP. */
    public boolean isPvP() { return isPvP; }

    /** Dapatkan team ID untuk entity (1+ = PvP team, 0/null = party). */
    public int getTeam(UUID uuid) {
        Integer t = teamAssignments.get(uuid);
        return t != null ? t : 0;
    }

    // ==================== NEXT TURN — PATCHED ====================

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

        // Cek battle selesai?
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
            // PATCH: cek apakah player sedang unconscious
            if (deathSaveManager != null && deathSaveManager.isUnconscious(player)) {
                promptDeathSave(player);
            } else {
                player.sendMessage("§a⚔ Giliran kamu! §7/atk melee §8| §7/atk ranged §8| §7/cast §8| §7/battle flee");
                player.sendMessage("§7  Turn: " + getCurrentTurnDisplay());
            }
        } else {
            // Mob turn — auto attack random player
            runMobTurn(entity);
        }

        // Reset recursion guard — normal flow selesai
        nextTurnDepth = 0;
    }

    // ==================== DEATH SAVE — PATCHED ====================

    /**
     * Prompt player untuk death saving throw.
     * Dipanggil saat giliran player yang unconscious.
     */
    private void promptDeathSave(Player player) {
        DeathSaveManager.DeathSaveState state = deathSaveManager != null
                ? deathSaveManager.getState(player) : null;
        String progress = state != null
                ? " §8(S:" + state.successes + "/3 F:" + state.failures + "/3)"
                : "";

        player.sendMessage("§c💀 Giliran death saving throw!" + progress);
        player.sendMessage("§7Pegang §fD20 (White Wool)§7 lalu ketik §f/roll");
        player.sendTitle("§c§lDEATH SAVE", "§7Pegang D20 + /roll" + progress, 5, 60, 10);

        // Set PendingRoll DEATH_SAVE — RollCommand resolve via resolveDeathSave()
        PendingRoll pr = new PendingRoll();
        pr.rollType = PendingRoll.RollType.DEATH_SAVE;
        pr.diceExpr = "d20";
        pr.context = new LinkedHashMap<>();
        PendingRoll.set(player, pr);
    }

    /**
     * RollCommand panggil ini setelah player /roll saat ada PendingRoll DEATH_SAVE.
     */
    public void resolveDeathSave(Player player) {
        if (deathSaveManager == null) return;

        DeathSaveManager.DeathSaveResult result = deathSaveManager.processRoll(player);

        switch (result) {
            case STABILIZED -> {
                plugin.getLogger().info("[DeathSave] " + player.getName() + " stabilized.");
                Bukkit.getScheduler().runTaskLater(plugin, this::nextTurn, 20L);
            }
            case DEAD -> {
                plugin.getLogger().info("[DeathSave] " + player.getName() + " died.");
                removeParticipant(player);
                Bukkit.getScheduler().runTaskLater(plugin, this::nextTurn, 40L);
            }
            case SUCCESS, FAILURE -> {
                // Masih dalam death save — lanjut ke turn berikutnya
                Bukkit.getScheduler().runTaskLater(plugin, this::nextTurn, 20L);
            }
            default -> {
                plugin.getLogger().warning("[DeathSave] Unexpected PENDING state for " + player.getName());
            }
        }
    }

    // ==================== MOB TURN — PATCHED ====================

    /** Auto-AI untuk mob: unfreeze → serang → re-freeze → next turn. */
    private void runMobTurn(LivingEntity mob) {
        // Unfreeze sementara untuk giliran mob
        if (mob instanceof Mob m && !(mob instanceof Player)) {
            m.setAware(true);
        }

        // PATCH: cari target — skip player unconscious
        Player target = null;
        double closest = Double.MAX_VALUE;
        for (var entry : participants.entrySet()) {
            if (entry.getValue() instanceof Player p && !p.isDead()) {
                // Skip player yang sedang unconscious
                if (deathSaveManager != null && deathSaveManager.isUnconscious(p)) {
                    continue;
                }
                double dist = mob.getLocation().distanceSquared(p.getLocation());
                if (dist < closest) {
                    closest = dist;
                    target = p;
                }
            }
        }

        if (target == null) {
            if (mob instanceof Mob m && !(mob instanceof Player)) m.setAware(false);
            Bukkit.broadcastMessage("§7[Combat] " + safeName(mob) + " tidak menemukan target.");
            Bukkit.getScheduler().runTaskLater(plugin, this::nextTurn, 20L);
            return;
        }

        // Mob attack resolution
        CombatEngine.AttackResult result = CombatEngine.resolveMobAttack(mob, target);

        if (result.hit) {
            // PATCH: gunakan DamageResult, handle UNCONSCIOUS
            CombatEngine.DamageResult dmgResult = CombatEngine.applyDamage(
                    target, result.totalDamage, result.damageType);

            Bukkit.broadcastMessage("§c[Combat]§r " + safeName(mob) + " §7→§r "
                    + safeName(target) + " §c-" + dmgResult.finalDamage + " HP §7[" + result.damageType + "]");

            switch (dmgResult.outcome) {
                case UNCONSCIOUS -> {
                    if (deathSaveManager != null) {
                        deathSaveManager.makeUnconscious(target);
                    }
                    // Player tetap di turnOrder — giliran mereka nanti prompt death save
                }
                case DEAD -> {
                    Bukkit.broadcastMessage("§c§l" + safeName(target) + " has been slain!");
                    removeParticipant(target);
                }
                case ALIVE -> { /* nothing to do */ }
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

    // ==================== ACTION HANDLING ====================

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

        // PATCH: unconscious player tidak bisa aksi normal
        if (deathSaveManager != null && deathSaveManager.isUnconscious(player)) {
            player.sendMessage("§cKamu sedang unconscious! Ketik /roll untuk death saving throw.");
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

    // ==================== BATTLE END — PATCHED ====================

    /** Cek apakah battle harus berakhir. */
    private boolean checkBattleEnd() {
        if (isPvP) {
            return checkPvPBattleEnd();
        }

        // PvE: player unconscious ≠ dead
        long consciousPlayerCount = participants.values().stream()
                .filter(e -> e instanceof Player && !e.isDead())
                .filter(e -> {
                    if (e instanceof Player p && deathSaveManager != null) {
                        return !deathSaveManager.isUnconscious(p);
                    }
                    return true;
                })
                .count();

        boolean allPlayersDead = consciousPlayerCount == 0;

        boolean allMobsDead = participants.values().stream()
                .filter(e -> !(e instanceof Player))
                .allMatch(LivingEntity::isDead);

        return allPlayersDead || allMobsDead || participants.size() <= 1;
    }

    /**
     * PvP battle end: kalau semua conscious player di satu team mati/unconscious,
     * team lawan menang.
     */
    private boolean checkPvPBattleEnd() {
        // Kumpulin alive/conscious players per team
        Map<Integer, Boolean> teamHasAlive = new java.util.LinkedHashMap<>();
        for (var entry : participants.entrySet()) {
            if (!(entry.getValue() instanceof Player p)) continue;
            int team = getTeam(entry.getKey());
            boolean alive = !p.isDead()
                    && (deathSaveManager == null || !deathSaveManager.isUnconscious(p));
            teamHasAlive.merge(team, alive, (a, b) -> a || b);
        }

        // Hitung team yang masih punya player alive
        long aliveTeams = teamHasAlive.values().stream().filter(v -> v).count();

        // Battle end kalau 1 atau 0 team tersisa
        return aliveTeams <= 1 || participants.size() <= 1;
    }

    /** Akhiri battle — unfreeze semua mob yang selamat. */
    public void endBattle() {
        // Unfreeze semua mob yang masih hidup
        for (LivingEntity e : participants.values()) {
            if (e instanceof Mob mob && !(e instanceof Player) && !e.isDead()) {
                mob.setAware(true);
            }
        }

        // PATCH: stabilize unconscious players saat battle selesai
        if (deathSaveManager != null) {
            for (LivingEntity e : participants.values()) {
                if (e instanceof Player p && deathSaveManager.isUnconscious(p)) {
                    deathSaveManager.stabilizeExternal(p);
                }
            }
        }

        // PvP winner announcement
        if (isPvP) {
            int winningTeam = findWinningTeam();
            if (winningTeam > 0) {
                // Cari nama team yang menang
                List<String> winners = new ArrayList<>();
                for (var entry : teamAssignments.entrySet()) {
                    if (entry.getValue() == winningTeam) {
                        LivingEntity e = participants.get(entry.getKey());
                        if (e instanceof Player p) winners.add(p.getName());
                    }
                }
                Bukkit.broadcastMessage("§6§l🏆 Team " + String.join(", ", winners) + " wins! 🏆§r");
            } else {
                Bukkit.broadcastMessage("§7§l⚔ PvP Battle Ended — Draw! ⚔§r");
            }
            isPvP = false;
            teamAssignments.clear();
        }

        battleState = BattleState.INACTIVE;
        combatState = CombatState.WAITING_TURN;
        turnOrder.clear();
        participants.clear();
        teamAssignments.clear();
        currentTurnIndex = 0;
        roundNumber = 0;
        isPvP = false;

        Bukkit.getOnlinePlayers().forEach(p ->
                p.sendMessage("§a§l=== Battle Ended ===§r"));
        plugin.getLogger().info("Battle ended.");
    }

    /** Cari team yang masih punya player alive (untuk PvP winner). */
    private int findWinningTeam() {
        Map<Integer, Boolean> teamAlive = new java.util.LinkedHashMap<>();
        for (var entry : participants.entrySet()) {
            if (entry.getValue() instanceof Player p && !p.isDead()) {
                int team = getTeam(entry.getKey());
                teamAlive.put(team, true);
            }
        }
        if (teamAlive.size() == 1) {
            return teamAlive.keySet().iterator().next();
        }
        return 0; // draw / multiple teams still alive
    }

    /** Hapus participant dari battle (mati / kabur). */
    public void removeParticipant(LivingEntity entity) {
        participants.remove(entity.getUniqueId());
        turnOrder.removeIf(e -> e.entityUuid.equals(entity.getUniqueId()));
        if (currentTurnIndex >= turnOrder.size()) {
            currentTurnIndex = 0;
        }
    }

    // ==================== UTILITIES ====================

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
