package id.patahgaming.dndplugin.listener;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.engine.combat.TurnManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Combat visual + audio feedback untuk semua entity (player & mob).
 *
 * - Damage sound (hurt) + red flash title pada player
 * - Blood particles pada entity yang kena damage
 * - HP bar di bawah nama entity (custom name update)
 */
public class CombatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final long TITLE_COOLDOWN_MS = 600; // min 600ms antar title

    private final DndPlugin plugin;
    private final Map<UUID, Long> lastTitleTime = new java.util.concurrent.ConcurrentHashMap<>();

    public CombatListener(DndPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * IMMUNITY: Player hanya bisa kena damage dari mob saat BATTLE AKTIF.
     * Di luar battle, semua damage dari mob ke player di-cancel.
     *
     * Tetap allow: environmental damage (fall, lava, drowning, etc.),
     * player-vs-player (future PvP), dan damage dari non-mob source.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamageImmunity(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event instanceof EntityDamageByEntityEvent entityEvent)) return;

        // Resolve the actual attacker (handle projectiles: arrow → skeleton shooter)
        LivingEntity attacker = resolveAttacker(entityEvent);
        if (attacker == null) return; // bukan dari mob → allow (fall, lava, player, dispenser)

        TurnManager tm = plugin.getTurnManager();
        if (tm.getBattleState() != TurnManager.BattleState.ACTIVE) {
            // No active battle → cancel all mob damage
            event.setCancelled(true);
            return;
        }

        // Battle active → only allow damage from battle participants
        if (!tm.getParticipants().containsKey(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Resolve the actual mob attacker from a damage event.
     * Handles: direct Mob attack, Projectile shot by Mob.
     * Returns null if the source is not a mob (player, dispenser, environment, etc.).
     */
    private LivingEntity resolveAttacker(EntityDamageByEntityEvent event) {
        // Direct mob attack
        if (event.getDamager() instanceof Mob mob) {
            return mob;
        }

        // Projectile (arrow, trident, fireball, shulker bullet, etc.)
        if (event.getDamager() instanceof Projectile proj) {
            if (proj.getShooter() instanceof Mob mob) {
                return mob;
            }
            // Shooter is Player, Dispenser, or null → allow (not mob-sourced)
            return null;
        }

        // Other sources: player melee, falling block, explosion, etc. → allow
        return null;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastTitleTime.remove(event.getPlayer().getUniqueId());
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

            // Red flash title dengan cooldown (biar gak spam)
            long now = System.currentTimeMillis();
            Long last = lastTitleTime.get(player.getUniqueId());
            if (last == null || now - last > TITLE_COOLDOWN_MS) {
                lastTitleTime.put(player.getUniqueId(), now);
                Title hurtTitle = Title.title(
                        LEGACY.deserialize("§c-" + (int) event.getFinalDamage() + " ❤"),
                        LEGACY.deserialize("§7" + (int) newHp + "/" + (int) maxHp + " HP"),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(400), Duration.ofMillis(100))
                );
                player.showTitle(hurtTitle);
            }

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

        // Get BASE name (jangan baca existing custom name — itu udah stacking!)
        String baseName = getBaseName(entity);

        // Build HP bar
        int barLength = 10;
        int filled = (int) Math.round((current / max) * barLength);
        filled = Math.max(0, Math.min(barLength, filled));

        double ratio = current / max;
        String color = ratio > 0.6 ? "§a" : ratio > 0.3 ? "§e" : "§c";

        StringBuilder bar = new StringBuilder("§7[");
        bar.append(color);
        for (int i = 0; i < barLength; i++) {
            bar.append(i < filled ? "§l|" : "§8§l|");
        }
        bar.append("§7] §f").append((int) current).append("/").append((int) max);

        // Always regenerate fresh: base_name + "\n" + hp_bar
        String displayText = baseName + "\n" + bar.toString();
        entity.setCustomNameVisible(true);
        entity.customName(LEGACY.deserialize(displayText));
    }

    /**
     * Get the BASE name of an entity WITHOUT any HP bar.
     * NPC name from PDC (with color), player character name from PDC,
     * or vanilla name for untagged mobs.
     */
    private static String getBaseName(LivingEntity entity) {
        if (entity instanceof Player player) {
            // Gunakan D&D character name, fallback ke Minecraft username
            var pdc = player.getPersistentDataContainer();
            String charName = pdc.get(new NamespacedKey("dndplugin", "char_name"), PersistentDataType.STRING);
            if (charName != null && !charName.isBlank()) {
                return "§b§l" + charName;
            }
            return "§7" + player.getName();
        }

        // Check PDC for NPC tags
        var pdc = entity.getPersistentDataContainer();
        String npcName = pdc.get(new NamespacedKey("dndplugin", "npc_name"), PersistentDataType.STRING);
        String npcType = pdc.get(new NamespacedKey("dndplugin", "npc_type"), PersistentDataType.STRING);

        if (npcName != null) {
            // NPC penting = gold bold, side = white bold
            String prefix = "important".equals(npcType) ? "§6§l" : "§f§l";
            return prefix + npcName;
        }

        // Regular mob: vanilla name (bisa null kalau ada custom name)
        String vanillaName = entity.getName();
        return vanillaName != null ? vanillaName : entity.getType().name();
    }

    /** Force refresh HP bar untuk semua entity dalam battle. */
    public static void refreshAllHpBars(Iterable<? extends LivingEntity> entities) {
        for (LivingEntity e : entities) {
            if (e.isValid()) updateHpBar(e);
        }
    }
}
