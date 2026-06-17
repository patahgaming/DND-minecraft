package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.SpellDataStore;
import id.patahgaming.dndplugin.data.SpellDataStore.SpellData;
import id.patahgaming.dndplugin.engine.combat.PendingRoll;
import id.patahgaming.dndplugin.engine.combat.SpellEngine;
import id.patahgaming.dndplugin.engine.combat.TurnManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /cast <spell> [target]
 *
 * Spellcasting via SpellEngine:
 * - Spell YAML dari SpellDataStore
 * - 3 jalur: ATTACK_ROLL / SAVING_THROW / AUTO_HIT
 * - PendingRoll dua-step: attack roll → damage roll (RollCommand resolve)
 */
public class CastCommand implements CommandExecutor, TabCompleter {

    private final DndPlugin plugin;

    public CastCommand(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommand ini hanya bisa dijalankan oleh player.");
            return true;
        }

        TurnManager tm = plugin.getTurnManager();

        // Validasi battle
        if (tm.getBattleState() != TurnManager.BattleState.ACTIVE) {
            player.sendMessage("§cTidak ada battle aktif. Gunakan /battle start.");
            return true;
        }

        if (!tm.isPlayerTurn(player)) {
            player.sendMessage("§cBukan giliran kamu!");
            return true;
        }

        // Cek pending roll aktif
        if (PendingRoll.has(player)) {
            player.sendMessage("§cSelesaikan /roll dulu sebelum cast spell lain.");
            return true;
        }

        SpellDataStore spellStore = plugin.getSpellDataStore();
        if (spellStore == null) {
            player.sendMessage("§cSpell system not loaded.");
            return true;
        }

        if (args.length < 1) {
            listAvailable(player, spellStore);
            return true;
        }

        String spellId = args[0].toLowerCase();
        SpellData spell = spellStore.get(spellId);

        if (spell == null) {
            player.sendMessage("§cSpell '" + spellId + "' tidak dikenal.");
            player.sendMessage("§7Pakai TAB untuk lihat daftar spell.");
            return true;
        }

        // Class check
        String playerClass = plugin.getPlayerDataStore().getCharacterClass(player);
        if (!spell.isAllowedFor(playerClass)) {
            player.sendMessage("§cKamu tidak bisa cast §e" + spell.displayName()
                    + " §csebagai " + playerClass + ".");
            return true;
        }

        // Cari target
        LivingEntity target = findTarget(tm, args.length >= 2 ? args[1] : null, spell);
        if (target == null) {
            if (spell.area() == SpellDataStore.Area.CONE) {
                // Area spell: auto-target first mob
                target = findFirstMob(tm);
            }
            if (target == null) {
                player.sendMessage("§cTarget tidak ditemukan. Gunakan: /cast " + spellId + " <target>");
                player.sendMessage("§7Target tersedia (pakai ID):");
                for (var entry : tm.getParticipants().entrySet()) {
                    if (entry.getValue() instanceof Player) continue;
                    String id = entry.getKey().toString().substring(0, 8);
                    String name = entry.getValue().getName() != null ? entry.getValue().getName() : "?";
                    int hp = (int) entry.getValue().getHealth();
                    player.sendMessage("  §e" + id + " §7→ " + name + " §c[" + hp + " HP]");
                }
                return true;
            }
        }

        // --- Distance check ---
        // Spell range: ~20 blocks (D&D standard 60-120ft)
        double dist = player.getLocation().distance(target.getLocation());
        double maxDist = spell.attackType() == SpellDataStore.AttackType.ATTACK_ROLL ? 24.0 : 16.0;
        if (dist > maxDist) {
            player.sendMessage("§cTerlalu jauh! Jarak spell max " + (int) maxDist + " block."
                    + " §7(jarak: " + String.format("%.1f", dist) + ")");
            return true;
        }

        // Select action
        tm.selectAction(player);

        // Initiate via SpellEngine
        SpellEngine.InitiateResult result = SpellEngine.initiate(plugin, player, spell, target, tm);

        if (!result.success) {
            player.sendMessage(result.errorMsg);
            return true;
        }

        player.sendMessage("§bMana: " + result.spellSlotsLeft + "/2 spell slots left");
        return true;
    }

    private void listAvailable(Player player, SpellDataStore store) {
        String playerClass = plugin.getPlayerDataStore().getCharacterClass(player);
        var available = store.getAvailableFor(playerClass);
        if (available.isEmpty()) {
            player.sendMessage("§7Class kamu (" + playerClass
                    + ") tidak punya spell. Gunakan §e/atk§7!");
        } else {
            player.sendMessage("§eGunakan: /cast <spell> [target]");
            player.sendMessage("§7Spells tersedia (pakai TAB):");
            for (var s : available) {
                String typeLabel = switch (s.attackType()) {
                    case ATTACK_ROLL -> "§e[Atk]";
                    case SAVING_THROW -> "§b[Save]";
                    case AUTO_HIT -> "§a[Auto]";
                };
                player.sendMessage("  " + typeLabel + " §b" + s.spellId()
                        + " §7— " + s.displayName() + ": " + s.damageDice() + " " + s.damageType());
            }
        }
    }

    private LivingEntity findTarget(TurnManager tm, String partial, SpellData spell) {
        if (partial == null && spell.area() != SpellDataStore.Area.CONE) return null;
        for (var entry : tm.getParticipants().entrySet()) {
            LivingEntity e = entry.getValue();
            if (e instanceof Player) continue;
            String idStr = entry.getKey().toString().substring(0, 8);
            if (partial == null) return e; // first mob
            if (idStr.toLowerCase().startsWith(partial.toLowerCase())
                    || (e.getName() != null && e.getName().toLowerCase().contains(partial.toLowerCase()))) {
                return e;
            }
        }
        return null;
    }

    private LivingEntity findFirstMob(TurnManager tm) {
        for (var entry : tm.getParticipants().entrySet()) {
            if (!(entry.getValue() instanceof Player)) return entry.getValue();
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            if (sender instanceof Player player) {
                String input = args[0].toLowerCase();
                SpellDataStore store = plugin.getSpellDataStore();
                if (store == null) return List.of();
                String playerClass = plugin.getPlayerDataStore().getCharacterClass(player);
                return store.getAvailableFor(playerClass).stream()
                        .map(SpellData::spellId)
                        .filter(id -> id.startsWith(input))
                        .sorted().toList();
            }
            SpellDataStore store = plugin.getSpellDataStore();
            if (store == null) return List.of();
            return store.getAll().stream()
                    .map(SpellData::spellId)
                    .sorted().toList();
        }
        if (args.length == 2) {
            TurnManager tm = plugin.getTurnManager();
            String input = args[1].toLowerCase();
            List<String> targets = new ArrayList<>();
            for (var entry : tm.getParticipants().entrySet()) {
                if (entry.getValue() instanceof Player) continue;
                String id = entry.getKey().toString().substring(0, 8);
                String name = entry.getValue().getName() != null ? entry.getValue().getName() : "?";
                if (id.toLowerCase().startsWith(input) || name.toLowerCase().contains(input)) {
                    targets.add(id);  // UUID prefix — unique
                }
            }
            return targets;
        }
        return List.of();
    }
}
