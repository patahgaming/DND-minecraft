package id.patahgaming.dndplugin.engine.combat;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.engine.dice.DiceRoller;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Central combat logic — D&D 5e rules.
 *
 * - AC (Armor Class): 10 + DEX modifier (player) / natural armor (mob)
 * - Attack Roll: d20 + STR/DEX modifier + proficiency
 * - Hit: attack roll >= target AC
 * - Crit: nat 20 = double damage dice
 * - Damage types: slashing, piercing, bludgeoning, fire, cold, etc.
 * - Resistance: half damage, Vulnerability: double damage
 */
public class CombatEngine {

    public enum DamageType {
        SLASHING, PIERCING, BLUDGEONING,
        FIRE, COLD, LIGHTNING, ACID, POISON,
        NECROTIC, RADIANT, FORCE, PSYCHIC
    }

    public static class AttackResult {
        public boolean hit;
        public boolean crit;
        public int attackRoll;
        public int attackBonus;
        public int totalAttack;
        public int targetAC;
        public int damageRoll;
        public int damageBonus;
        public int totalDamage;
        public DamageType damageType;
        public String flavorText;
    }

    // --- AC Calculation ---

    /** Player AC = 10 + DEX modifier (+ armor bonus later) */
    public static int calculatePlayerAC(DndPlugin plugin, Player player) {
        int dexMod = plugin.getPlayerDataStore().getAbilityModifier(player, "dex");
        // TODO: armor bonus from equipment
        return 10 + dexMod;
    }

    /** Mob AC = natural armor (based on mob type) */
    public static int calculateMobAC(LivingEntity mob) {
        return switch (mob.getType()) {
            case ZOMBIE -> 8;
            case SKELETON -> 13;
            case SPIDER -> 14;
            case CREEPER -> 12;
            case ENDERMAN -> 15;
            case WITCH -> 12;
            case PILLAGER, VINDICATOR -> 15;
            case EVOKER -> 12;
            case IRON_GOLEM -> 20;
            case VILLAGER, WANDERING_TRADER -> 10;
            default -> 10;
        };
    }

    // --- Attack Resolution ---

    /**
     * Resolve melee attack: player vs target.
     * d20 + STR + proficiency vs target AC.
     */
    public static AttackResult resolveMeleeAttack(DndPlugin plugin, Player attacker, LivingEntity target) {
        AttackResult result = new AttackResult();

        int strMod = plugin.getPlayerDataStore().getAbilityModifier(attacker, "str");
        int profBonus = proficiencyBonus(plugin.getPlayerDataStore().getLevel(attacker));
        result.attackBonus = strMod + profBonus;
        result.attackRoll = DiceRoller.roll(20);
        result.totalAttack = result.attackRoll + result.attackBonus;
        result.crit = result.attackRoll == 20;

        // Target AC
        if (target instanceof Player targetPlayer) {
            result.targetAC = calculatePlayerAC(plugin, targetPlayer);
        } else {
            result.targetAC = calculateMobAC(target);
        }

        result.hit = result.crit || result.totalAttack >= result.targetAC;

        if (result.hit) {
            // Damage: weapon d8 + STR
            result.damageRoll = DiceRoller.roll(8);
            result.damageBonus = strMod;
            result.totalDamage = result.damageRoll + result.damageBonus;
            result.damageType = DamageType.SLASHING;

            if (result.crit) {
                result.damageRoll += DiceRoller.roll(8); // double dice
                result.totalDamage = result.damageRoll + result.damageBonus;
                result.flavorText = "§e§lCRITICAL HIT!";
            }
        } else {
            result.totalDamage = 0;
            result.flavorText = "§7MISS!";
        }

        return result;
    }

    /**
     * Resolve ranged attack: d20 + DEX + proficiency vs target AC.
     */
    public static AttackResult resolveRangedAttack(DndPlugin plugin, Player attacker, LivingEntity target) {
        AttackResult result = new AttackResult();

        int dexMod = plugin.getPlayerDataStore().getAbilityModifier(attacker, "dex");
        int profBonus = proficiencyBonus(plugin.getPlayerDataStore().getLevel(attacker));
        result.attackBonus = dexMod + profBonus;
        result.attackRoll = DiceRoller.roll(20);
        result.totalAttack = result.attackRoll + result.attackBonus;
        result.crit = result.attackRoll == 20;

        if (target instanceof Player targetPlayer) {
            result.targetAC = calculatePlayerAC(plugin, targetPlayer);
        } else {
            result.targetAC = calculateMobAC(target);
        }

        result.hit = result.crit || result.totalAttack >= result.targetAC;

        if (result.hit) {
            result.damageRoll = DiceRoller.roll(6); // shortbow d6
            result.damageBonus = dexMod;
            result.totalDamage = result.damageRoll + result.damageBonus;
            result.damageType = DamageType.PIERCING;

            if (result.crit) {
                result.damageRoll += DiceRoller.roll(6);
                result.totalDamage = result.damageRoll + result.damageBonus;
                result.flavorText = "§e§lCRITICAL HIT!";
            }
        } else {
            result.totalDamage = 0;
            result.flavorText = "§7MISS!";
        }

        return result;
    }

    /**
     * Mob attacks player — simplified AI.
     */
    public static AttackResult resolveMobAttack(LivingEntity mob, Player target) {
        AttackResult result = new AttackResult();

        // Mob attack bonus based on type
        int mobAtkBonus = switch (mob.getType()) {
            case ZOMBIE -> 3;
            case SKELETON -> 4;
            case SPIDER -> 5;
            case CREEPER -> 3;
            case ENDERMAN -> 7;
            case WITCH -> 5;
            case PILLAGER, VINDICATOR -> 5;
            case EVOKER -> 6;
            case IRON_GOLEM -> 10;
            default -> 3;
        };
        int mobDmgDice = switch (mob.getType()) {
            case ZOMBIE -> 6;
            case SKELETON -> 6;
            case SPIDER -> 6;
            case CREEPER -> 0; // explode instead
            case ENDERMAN -> 8;
            case WITCH -> 0; // spells
            case IRON_GOLEM -> 10;
            default -> 6;
        };
        int mobDmgBonus = switch (mob.getType()) {
            case ZOMBIE -> 1;
            case IRON_GOLEM -> 5;
            default -> 2;
        };

        result.attackBonus = mobAtkBonus;
        result.attackRoll = DiceRoller.roll(20);
        result.totalAttack = result.attackRoll + result.attackBonus;
        result.crit = result.attackRoll == 20;
        result.targetAC = 13; // base player AC approximation
        result.hit = result.crit || result.totalAttack >= result.targetAC;

        if (result.hit) {
            if (mobDmgDice > 0) {
                result.damageRoll = DiceRoller.roll(mobDmgDice);
            } else {
                result.damageRoll = 0;
            }
            result.damageBonus = mobDmgBonus;
            result.totalDamage = Math.max(1, result.damageRoll + result.damageBonus);
            result.damageType = DamageType.BLUDGEONING;

            if (result.crit) {
                result.totalDamage *= 2;
                result.flavorText = "§c§lCRIT!";
            }
        } else {
            result.totalDamage = 0;
            result.flavorText = "§7miss!";
        }

        return result;
    }

    // --- Proficiency ---

    /** Proficiency bonus based on character level (D&D 5e standard) */
    public static int proficiencyBonus(int level) {
        if (level >= 17) return 6;
        if (level >= 13) return 5;
        if (level >= 9) return 4;
        if (level >= 5) return 3;
        return 2;
    }

    // --- Damage Application ---

    /** Apply damage to entity, respecting resistance/vulnerability */
    public static int applyDamage(LivingEntity target, int damage, DamageType type) {
        double modifier = 1.0;

        // Resistance / vulnerability based on mob type
        modifier = switch (target.getType()) {
            case SKELETON -> type == DamageType.PIERCING ? 0.5 : type == DamageType.BLUDGEONING ? 2.0 : 1.0;
            case ZOMBIE -> type == DamageType.RADIANT ? 2.0 : 1.0;
            case ENDERMAN -> type == DamageType.FIRE || type == DamageType.LIGHTNING ? 0.5 : 1.0;
            case SPIDER -> type == DamageType.FIRE ? 2.0 : 1.0;
            case IRON_GOLEM -> type == DamageType.POISON || type == DamageType.PSYCHIC ? 0.0 : 1.0;
            default -> 1.0;
        };

        int finalDamage = (int) Math.max(1, damage * modifier);
        double newHp = Math.max(0, target.getHealth() - finalDamage);
        target.setHealth(newHp);

        return finalDamage;
    }
}
