package id.patahgaming.dndplugin.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;

/**
 * Combat visual + audio feedback untuk semua entity (player & mob).
 *
 * - Damage sound (hurt) + red flash title pada player
 * - Blood particles pada entity yang kena damage
 * - HP bar di bawah nama entity (custom name update)
 */
public class CombatListener implements Listener {

    private final Plugin plugin;

    public CombatListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (event.getDamage() <= 0) return;

        double newHp = Math.max(0, entity.getHealth() - event.getFinalDamage());
        double maxHp = entity.getAttribute(Attribute.MAX_HEALTH) != null
                ? entity.getAttribute(Attribute.MAX_HEALTH).getValue()
                : entity.getMaxHealth();

        // --- SOUND ---
        if (entity instanceof Player player) {
            // Player hurt sound (lebih keras)
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);

            // Red flash title ("-3 ❤")
            Title hurtTitle = Title.title(
                    Component.text("§c-" + (int) event.getFinalDamage() + " ❤"),
                    Component.text("§7" + (int) newHp + "/" + (int) maxHp + " HP"),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(400), Duration.ofMillis(100))
            );
            player.showTitle(hurtTitle);

            // Critical damage sound + efek kalau kena >25% max HP
            if (event.getFinalDamage() > maxHp * 0.25) {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.3f, 0.5f);
            }

            // Death sound
            if (newHp <= 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_DEATH, 0.6f, 1.0f);
            }
        } else {
            // Mob hurt sound (generic)
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_HURT, 0.6f, 1.0f);

            // Blood/redstone particles
            entity.getWorld().spawnParticle(
                    Particle.DAMAGE_INDICATOR,
                    entity.getLocation().add(0, entity.getHeight() / 2, 0),
                    5, 0.3, 0.3, 0.3, 0.1
            );

            // Mob death effects
            if (newHp <= 0) {
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_DEATH, 0.5f, 1.2f);
                entity.getWorld().spawnParticle(
                        Particle.EXPLOSION,
                        entity.getLocation().add(0, 1, 0),
                        3, 0.5, 0.5, 0.5, 0.05
                );
            }
        }

        // --- HP Bar Update (via custom name) ---
        // Update HP display 1 tick later (setelah damage applied)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateHpBar(entity);
        }, 1L);
    }

    /**
     * Update custom name entity dengan HP bar format.
     * Format: "[=====   ] 5/12" di bawah nama asli.
     */
    public static void updateHpBar(LivingEntity entity) {
        if (entity == null || !entity.isValid()) return;

        double current = entity.getHealth();
        double max = entity.getAttribute(Attribute.MAX_HEALTH) != null
                ? entity.getAttribute(Attribute.MAX_HEALTH).getValue()
                : entity.getMaxHealth();

        if (max <= 0) return;

        // Build HP bar text
        int barLength = 10;
        int filled = (int) Math.round((current / max) * barLength);
        filled = Math.max(0, Math.min(barLength, filled));

        String color;
        double ratio = current / max;
        if (ratio > 0.6) color = "§a";       // Green
        else if (ratio > 0.3) color = "§e";  // Yellow
        else color = "§c";                     // Red

        StringBuilder bar = new StringBuilder("§7[");
        bar.append(color);
        for (int i = 0; i < barLength; i++) {
            bar.append(i < filled ? "§l|" : "§8§l|");
        }
        bar.append("§7] §f").append((int) current).append("/").append((int) max);

        // Cari existing custom name (kalau ada NPC name, jangan di-overwrite)
        String existingName = null;
        if (entity.customName() != null) {
            existingName = entity.customName().toString();
        }

        // Set nama entity dengan HP bar
        String baseName;
        if (entity instanceof Player player) {
            // Player: keep vanilla name, show HP bar below via team suffix
            // For simplicity, use the player's display name
            baseName = player.getName();
        } else {
            baseName = entity.getName();
        }

        // For mobs: set custom name with HP below
        String displayText;
        if (existingName != null && !existingName.isEmpty()
                && !existingName.startsWith("§7[")) {
            // Preserve NPC custom name (gold/white bold) + tambah HP di bawah
            displayText = existingName + "\n" + bar.toString();
        } else {
            displayText = baseName + "\n" + bar.toString();
        }

        entity.setCustomNameVisible(true);
        entity.customName(Component.text(displayText));
    }

    /** Force refresh HP bar untuk semua entity dalam battle. */
    public static void refreshAllHpBars(Iterable<? extends LivingEntity> entities) {
        for (LivingEntity e : entities) {
            if (e.isValid()) updateHpBar(e);
        }
    }
}
