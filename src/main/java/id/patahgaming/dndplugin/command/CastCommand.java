package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.CharacterClass;
import id.patahgaming.dndplugin.engine.combat.CombatEngine;
import id.patahgaming.dndplugin.engine.combat.TurnManager;
import id.patahgaming.dndplugin.engine.dice.DiceRoller;
import id.patahgaming.dndplugin.ui.TitleDisplay;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * /cast <spell> [target]
 *
 * Spellcasting system untuk combat:
 * - Spell list berdasarkan class
 * - Spell slots (level 1: 2 slots, regen on rest)
 * - Attack roll / saving throw vs target
 * - Damage + effects
 */
public class CastCommand implements CommandExecutor, TabCompleter {

    public record Spell(String id, String name, String damageDice, int damageBonus,
                        CombatEngine.DamageType damageType, String saveAbility, int dcBonus,
                        String description, boolean requiresAttackRoll, boolean halfOnSave,
                        List<String> classes) {}

    // Spell list (SRD cantrips + 1st level)
    private static final Map<String, Spell> SPELLS = new LinkedHashMap<>();
    static {
        // All casters get these
        var arcane = List.of("WIZARD", "WARLOCK");
        var divine = List.of("CLERIC", "PALADIN", "DRUID");
        var all = List.of("WIZARD", "WARLOCK", "CLERIC", "DRUID", "PALADIN");
        // True: half damage on save, False: save negates
        add("fire_bolt", "Fire Bolt", "1d10", 0, CombatEngine.DamageType.FIRE,
                null, 0, "Hurl a mote of fire.", true, false, arcane);
        add("magic_missile", "Magic Missile", "3d4", 3, CombatEngine.DamageType.FORCE,
                null, 0, "Three glowing darts. Auto-hit.", false, false, arcane);
        add("burning_hands", "Burning Hands", "3d6", 0, CombatEngine.DamageType.FIRE,
                "dex", 0, "15ft cone. DEX save for half.", false, true, arcane);
        add("chill_touch", "Chill Touch", "1d8", 0, CombatEngine.DamageType.NECROTIC,
                null, 0, "Ghostly hand.", true, false, arcane);
        add("shocking_grasp", "Shocking Grasp", "1d8", 0, CombatEngine.DamageType.LIGHTNING,
                null, 0, "Lightning touch.", true, false, arcane);
        add("ray_of_frost", "Ray of Frost", "1d8", 0, CombatEngine.DamageType.COLD,
                null, 0, "Frigid beam.", true, false, arcane);
        add("sacred_flame", "Sacred Flame", "1d8", 0, CombatEngine.DamageType.RADIANT,
                "dex", 0, "Flame-like radiance. DEX save negates.", false, false, divine);
        add("poison_spray", "Poison Spray", "1d12", 0, CombatEngine.DamageType.POISON,
                "con", 0, "Poison gas. CON save negates.", false, false, all);
        add("acid_splash", "Acid Splash", "1d6", 0, CombatEngine.DamageType.ACID,
                "dex", 0, "Acid bubble. DEX save negates.", false, false, arcane);
        add("frostbite", "Frostbite", "1d6", 0, CombatEngine.DamageType.COLD,
                "con", 0, "Numbing cold. CON save negates.", false, false, all);
        add("eldritch_blast", "Eldritch Blast", "1d10", 0, CombatEngine.DamageType.FORCE,
                null, 0, "Crackling beam.", true, false, List.of("WARLOCK"));
        add("word_of_radiance", "Word of Radiance", "1d6", 0, CombatEngine.DamageType.RADIANT,
                "con", 0, "Holy word. CON save negates.", false, false, List.of("CLERIC"));
    }

    private static void add(String id, String name, String dice, int bonus,
                            CombatEngine.DamageType type, String save, int dcBonus,
                            String desc, boolean attackRoll, boolean halfOnSave,
                            List<String> classes) {
        SPELLS.put(id.toLowerCase(), new Spell(id, name, dice, bonus, type, save, dcBonus, desc, attackRoll, halfOnSave, classes));
    }

    private static final NamespacedKey KEY_SPELL_SLOTS = new NamespacedKey("dndplugin", "spell_slots");
    private static final int MAX_SPELL_SLOTS = 2;

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

        if (args.length < 1) {
            player.sendMessage("§eGunakan: /cast <spell> [target]");
            player.sendMessage("§7Spells tersedia (pakai TAB):");
            for (Spell s : getAvailableSpells(player)) {
                player.sendMessage("§b  " + s.id + " §7— " + s.name + ": " + s.description);
            }
            return true;
        }

        String spellId = args[0].toLowerCase();
        Spell spell = SPELLS.get(spellId);

        if (spell == null) {
            player.sendMessage("§cSpell '" + spellId + "' tidak dikenal.");
            player.sendMessage("§7Pakai TAB untuk lihat daftar spell.");
            return true;
        }

        // Spell slots (persisted ke PDC)
        var pdc = player.getPersistentDataContainer();
        int slots = pdc.getOrDefault(KEY_SPELL_SLOTS, PersistentDataType.INTEGER, MAX_SPELL_SLOTS);
        if (slots <= 0) {
            player.sendMessage("§cSpell slots habis! Istirahat dulu untuk regen.");
            return true;
        }

        // Cari target
        LivingEntity target = findTarget(tm, args.length >= 2 ? args[1] : null);
        if (target == null) {
            player.sendMessage("§cTarget tidak ditemukan.");
            return true;
        }

        // Execute spell
        tm.selectAction(player);
        executeSpell(player, spell, target, tm);
        slots--;
        pdc.set(KEY_SPELL_SLOTS, PersistentDataType.INTEGER, slots);

        player.sendMessage("§bMana: " + slots + "/" + MAX_SPELL_SLOTS + " spell slots left");
        return true;
    }

    private void executeSpell(Player caster, Spell spell, LivingEntity target, TurnManager tm) {
        int profBonus = CombatEngine.proficiencyBonus(
                plugin.getPlayerDataStore().getLevel(caster));

        String primaryAbility = "int"; // default
        CharacterClass charClass = CharacterClass.fromName(
                plugin.getPlayerDataStore().getCharacterClass(caster));
        if (charClass != null) {
            primaryAbility = charClass.getPrimaryAbility();
        }
        int spellMod = plugin.getPlayerDataStore().getAbilityModifier(caster, primaryAbility);

        boolean hit;
        int totalDamage = 0;

        if (spell.requiresAttackRoll) {
            // Spell attack roll: d20 + spellMod + prof vs target AC
            int attackRoll = DiceRoller.roll(20);
            int totalAttack = attackRoll + spellMod + profBonus;

            int targetAC;
            if (target instanceof Player p) {
                targetAC = CombatEngine.calculatePlayerAC(plugin, p);
            } else {
                targetAC = CombatEngine.calculateMobAC(target);
            }

            hit = attackRoll == 20 || totalAttack >= targetAC;

            if (hit) {
                // Parse damage dice: "1d10" → 1x d10
                int[] dice = parseDice(spell.damageDice);
                totalDamage = spell.damageBonus;
                for (int i = 0; i < dice[0]; i++) {
                    totalDamage += DiceRoller.roll(dice[1]);
                }
                if (attackRoll == 20) {
                    totalDamage *= 2; // crit double
                }
                CombatEngine.applyDamage(target, totalDamage, spell.damageType);
            }

            // Sound
            caster.playSound(caster.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.5f);

            String mainText = hit
                    ? (attackRoll == 20 ? "§e§lCRIT! " + totalAttack : "§dHit! " + totalAttack)
                    : "§7Miss! " + totalAttack;
            TitleDisplay.showCombatResult(caster, mainText,
                    spell.name + ": §c-" + totalDamage + " §7[" + spell.damageType + "]");

            Bukkit.broadcastMessage("§d[Spell]§r " + caster.getName() + " casts §b" + spell.name
                    + "§r → " + target.getName()
                    + " §7[d20:" + attackRoll + "+" + (spellMod + profBonus)
                    + "=" + totalAttack + " vs AC:" + targetAC + "] "
                    + (hit ? "§c-" + totalDamage + " " + spell.damageType : "§7MISS"));

        } else {
            // Saving throw spell: target rolls d20 + saveMod vs DC (8 + spellMod + prof)
            String saveAbility = spell.saveAbility != null ? spell.saveAbility : "dex";
            int saveDC = 8 + spellMod + profBonus + spell.dcBonus;

            int targetSaveMod = 0;
            if (target instanceof Player p) {
                targetSaveMod = plugin.getPlayerDataStore().getAbilityModifier(p, saveAbility);
            }
            int saveRoll = DiceRoller.roll(20);
            int totalSave = saveRoll + targetSaveMod;
            hit = totalSave < saveDC; // hit kalau target GAGAL save

            if (hit) {
                // Target FAILED save → full damage
                int[] dice = parseDice(spell.damageDice);
                totalDamage = spell.damageBonus;
                for (int i = 0; i < dice[0]; i++) {
                    totalDamage += DiceRoller.roll(dice[1]);
                }
                CombatEngine.applyDamage(target, totalDamage, spell.damageType);
            } else if (spell.halfOnSave) {
                // Target SAVED, but spell does half damage
                int[] dice = parseDice(spell.damageDice);
                totalDamage = spell.damageBonus;
                for (int i = 0; i < dice[0]; i++) {
                    totalDamage += DiceRoller.roll(dice[1]);
                }
                totalDamage /= 2;
                if (totalDamage > 0) CombatEngine.applyDamage(target, totalDamage, spell.damageType);
            }
            // else: save negates → no damage

            caster.playSound(caster.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.8f);

            String subText;
            if (hit) {
                subText = "§c-" + totalDamage + " §7[" + spell.damageType + "]";
            } else if (spell.halfOnSave && totalDamage > 0) {
                subText = "§7Half: -" + totalDamage + " §7[" + spell.damageType + "]";
            } else {
                subText = "§aSaved! No damage";
            }
            TitleDisplay.showCombatResult(caster,
                    hit ? "§d" + spell.name : "§7Saved! " + spell.name,
                    subText);

            Bukkit.broadcastMessage("§d[Spell]§r " + caster.getName() + " casts §b" + spell.name
                    + "§r → " + target.getName()
                    + " §7[" + saveAbility.toUpperCase() + " save DC" + saveDC
                    + " | d20:" + saveRoll + "+" + targetSaveMod + "=" + totalSave + "] "
                    + (hit ? "§cFAIL -" + totalDamage + " " + spell.damageType
                    : (spell.halfOnSave && totalDamage > 0 ? "§eSAVED (half: -" + totalDamage + ")" : "§aSAVED")));
        }

        if (target.getHealth() <= 0) {
            Bukkit.broadcastMessage("§c§l" + target.getName() + " has been slain by magic!");
            tm.removeParticipant(target);
        }

        tm.resolveAction();
    }

    private int[] parseDice(String dice) {
        // "1d10" → [1, 10], "3d6" → [3, 6]
        String[] parts = dice.split("d");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    private List<Spell> getAvailableSpells(Player player) {
        String playerClass = plugin.getPlayerDataStore().getCharacterClass(player);
        List<Spell> available = new ArrayList<>();
        for (Spell s : SPELLS.values()) {
            if (s.classes.contains(playerClass.toUpperCase())
                    || s.classes.contains("ALL")) {
                available.add(s);
            }
        }
        return available;
    }

    private LivingEntity findTarget(TurnManager tm, String partial) {
        for (var entry : tm.getParticipants().entrySet()) {
            LivingEntity e = entry.getValue();
            if (e instanceof Player) continue;
            if (partial == null) return e; // first mob
            String idStr = entry.getKey().toString().substring(0, 8);
            if (idStr.toLowerCase().startsWith(partial.toLowerCase())
                    || (e.getName() != null && e.getName().toLowerCase().contains(partial.toLowerCase()))) {
                return e;
            }
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return SPELLS.keySet().stream()
                    .filter(s -> s.startsWith(input))
                    .sorted().toList();
        }
        if (args.length == 2) {
            TurnManager tm = plugin.getTurnManager();
            String input = args[1].toLowerCase();
            return tm.getParticipants().entrySet().stream()
                    .filter(e -> !(e.getValue() instanceof Player))
                    .map(e -> e.getValue().getName())
                    .filter(n -> n != null && n.toLowerCase().startsWith(input))
                    .toList();
        }
        return List.of();
    }
}
