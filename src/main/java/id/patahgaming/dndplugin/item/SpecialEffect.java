package id.patahgaming.dndplugin.item;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Special effects untuk item MYTHICAL/LEGENDARY.
 */
public enum SpecialEffect {

    NONE {
        @Override public void onHit(Player attacker, LivingEntity target, int damage) {}
        @Override public String getDescription() { return null; }
    },
    FIRE_ON_HIT {
        @Override public void onHit(Player attacker, LivingEntity target, int damage) {
            target.setFireTicks(40); // 2 detik
        }
        @Override public String getDescription() { return "§c🔥 Fire on hit (2s)"; }
    },
    LIFE_STEAL {
        @Override public void onHit(Player attacker, LivingEntity target, int damage) {
            int heal = Math.max(1, damage / 4);
            double newHp = Math.min(attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),
                    attacker.getHealth() + heal);
            attacker.setHealth(newHp);
        }
        @Override public String getDescription() { return "§c❤ Life steal (25%)"; }
    },
    FIRST_BLOOD {
        @Override public void onHit(Player attacker, LivingEntity target, int damage) {
            // Damage ×2 handled by caller; this is just marker
        }
        @Override public String getDescription() { return "§c⚡ First Blood (×2 first hit)"; }
    },
    SOUL_CLEAVE {
        @Override public void onHit(Player attacker, LivingEntity target, int damage) {
            // Nat20 skip turn handled by caller
        }
        @Override public String getDescription() { return "§5💀 Soul Cleave (crit skips target turn)"; }
    },
    UNBREAKING {
        @Override public void onHit(Player attacker, LivingEntity target, int damage) {}
        @Override public String getDescription() { return "§a🛡 Unbreaking (no drop on death)"; }
    };

    public abstract void onHit(Player attacker, LivingEntity target, int damage);
    public abstract String getDescription();

    public static SpecialEffect fromString(String s) {
        if (s == null || s.isEmpty() || s.equalsIgnoreCase("null")) return NONE;
        try { return valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return NONE; }
    }
}
