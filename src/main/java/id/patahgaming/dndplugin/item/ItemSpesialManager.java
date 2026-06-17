package id.patahgaming.dndplugin.item;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.engine.combat.CombatEngine;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/**
 * ItemSpesialManager — load/save YAML, build ItemStack, PDC read/write.
 *
 * PDC keys (semua item):
 *   dnd:item_id       String
 *   dnd:rarity        String
 *   dnd:item_type     String
 *
 * Weapon tambahan:
 *   dnd:damage_dice   String
 *   dnd:damage_bonus  Integer
 *   dnd:damage_type   String
 *   dnd:attack_type   String  (MELEE/RANGED)
 *   dnd:special_effect String
 */
public class ItemSpesialManager {

    // PDC keys
    public static final NamespacedKey KEY_ITEM_ID = new NamespacedKey("dndplugin", "item_id");
    public static final NamespacedKey KEY_RARITY = new NamespacedKey("dndplugin", "rarity");
    public static final NamespacedKey KEY_ITEM_TYPE = new NamespacedKey("dndplugin", "item_type");
    public static final NamespacedKey KEY_DAMAGE_DICE = new NamespacedKey("dndplugin", "damage_dice");
    public static final NamespacedKey KEY_DAMAGE_BONUS = new NamespacedKey("dndplugin", "damage_bonus");
    public static final NamespacedKey KEY_DAMAGE_TYPE = new NamespacedKey("dndplugin", "damage_type");
    public static final NamespacedKey KEY_ATTACK_TYPE = new NamespacedKey("dndplugin", "attack_type");
    public static final NamespacedKey KEY_SPECIAL_EFFECT = new NamespacedKey("dndplugin", "special_effect");
    public static final NamespacedKey KEY_DICE_TYPE = new NamespacedKey("dndplugin", "dice_type");

    private final DndPlugin plugin;
    private final Map<String, YamlConfiguration> itemCache = new LinkedHashMap<>();

    public ItemSpesialManager(DndPlugin plugin) {
        this.plugin = plugin;
        loadAll();
    }

    // ==================== YAML LOAD/SAVE ====================

    public void loadAll() {
        itemCache.clear();
        File itemsDir = new File(plugin.getDataFolder(), "data/items");
        if (!itemsDir.exists()) itemsDir.mkdirs();

        // Load dari templates (JAR) dulu
        loadFromJar("data/items/templates");

        // Load dari custom (disk) — override templates
        File customDir = new File(itemsDir, "custom");
        if (customDir.exists()) {
            File[] files = customDir.listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
                    String id = yaml.getString("item_id");
                    if (id != null) itemCache.put(id.toLowerCase(), yaml);
                }
            }
        }

        // Load templates dari disk (kalau ada override di data/items/templates/)
        File templatesDir = new File(itemsDir, "templates");
        if (templatesDir.exists()) {
            File[] files = templatesDir.listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
                    String id = yaml.getString("item_id");
                    if (id != null) itemCache.put(id.toLowerCase(), yaml);
                }
            }
        }

        plugin.getLogger().info("[ItemSpesial] Loaded " + itemCache.size() + " items");
    }

    private void loadFromJar(String jarPath) {
        // Built-in default templates yang di-bundle dalam JAR.
        // Template baru yang dibuat via /itemspesial create auto-discover dari disk
        // (folder data/items/custom/) — tidak perlu ditambah ke array ini.
        String[] templates = {
                "shortsword_iron", "longsword_iron", "shortbow_wood",
                "dagger", "greataxe", "warhammer", "light_crossbow", "quarterstaff",
                "longsword_plus1", "flaming_sword", "soul_reaper",
                "leather_vest", "studded_leather", "chainmail", "plate_of_thornhollow",
                "shield_wooden",
                "health_potion", "greater_health_potion", "mana_potion", "antidote",
                "ring_of_protection",
                "ancient_key", "elder_seal",
                "dice_d4", "dice_d6", "dice_d8", "dice_d10", "dice_d12", "dice_d20"
        };
        for (String id : templates) {
            String path = jarPath + "/" + id + ".yml";
            try {
                java.io.InputStream in = plugin.getClass().getClassLoader().getResourceAsStream(path);
                if (in != null) {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(in));
                    String yamlId = yaml.getString("item_id");
                    if (yamlId != null) {
                        itemCache.putIfAbsent(yamlId.toLowerCase(), yaml);
                    }
                }
            } catch (Exception e) {
                // template not found — skip silently
            }
        }
    }

    public void saveCustom(String itemId, YamlConfiguration yaml) {
        File customDir = new File(plugin.getDataFolder(), "data/items/custom");
        if (!customDir.exists()) customDir.mkdirs();
        File file = new File(customDir, itemId + ".yml");
        try {
            yaml.save(file);
            itemCache.put(itemId.toLowerCase(), yaml);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save item: " + itemId + " — " + e.getMessage());
        }
    }

    public YamlConfiguration getItemData(String itemId) {
        return itemCache.get(itemId.toLowerCase());
    }

    public Collection<String> getAllIds() {
        return itemCache.keySet();
    }

    public Collection<YamlConfiguration> getAll() {
        return itemCache.values();
    }

    // ==================== ITEMSTACK BUILDING ====================

    /**
     * Build ItemStack dari YAML definition + inject PDC tags.
     */
    public ItemStack buildItem(String itemId) {
        YamlConfiguration yaml = getItemData(itemId);
        if (yaml == null) return null;
        return buildItem(yaml);
    }

    public ItemStack buildItem(YamlConfiguration yaml) {
        String id = yaml.getString("item_id");
        String name = yaml.getString("display_name", id);
        String rarityStr = yaml.getString("rarity", "COMMON");
        ItemRarity rarity = ItemRarity.fromString(rarityStr);
        if (rarity == null) rarity = ItemRarity.COMMON;
        String typeStr = yaml.getString("item_type", "QUEST");
        ItemType type = ItemType.fromString(typeStr);
        if (type == null) type = ItemType.QUEST;

        // Material
        Material material;
        String matStr = yaml.getString("minecraft_material");
        try {
            material = Material.valueOf(matStr.toUpperCase());
        } catch (Exception e) {
            material = Material.PAPER; // fallback
        }

        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Display name
        meta.setDisplayName(rarity.getColor() + name);

        // Lore
        List<String> lore = new ArrayList<>();
        lore.add(rarity.getColor() + "§l" + rarity.getDisplay());

        if (type == ItemType.WEAPON) {
            String dice = yaml.getString("damage_dice", "1d6");
            int bonus = yaml.getInt("damage_bonus", rarity.getDamageBonus());
            String dmgType = yaml.getString("damage_type", "SLASHING");
            String atkType = yaml.getString("attack_type", "MELEE");
            String dmgLine = bonus > 0 ? dice + "+" + bonus : dice;
            lore.add("§7" + dmgLine + " " + dmgType + " (" + atkType.toLowerCase() + ")");
        } else if (type == ItemType.ARMOR) {
            int ac = yaml.getInt("ac_bonus", 1);
            lore.add("§7AC +" + ac);
        } else if (type == ItemType.CONSUMABLE) {
            String heal = yaml.getString("heal_dice", "");
            if (!heal.isEmpty()) lore.add("§7Heal: " + heal);
        }

        // Custom lore dari YAML
        List<String> customLore = yaml.getStringList("lore");
        if (customLore != null) lore.addAll(customLore);

        // Special effect
        String effect = yaml.getString("special_effect");
        if (effect != null && !effect.isEmpty() && !effect.equalsIgnoreCase("null")) {
            SpecialEffect se = SpecialEffect.fromString(effect);
            if (se != SpecialEffect.NONE && se.getDescription() != null) {
                lore.add(se.getDescription());
            }
        }

        // ID footer
        lore.add("§8ID: " + id);

        meta.setLore(lore);

        // PDC injection
        var pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_ITEM_ID, PersistentDataType.STRING, id);
        pdc.set(KEY_RARITY, PersistentDataType.STRING, rarity.name());
        pdc.set(KEY_ITEM_TYPE, PersistentDataType.STRING, type.name());

        if (type == ItemType.WEAPON) {
            pdc.set(KEY_DAMAGE_DICE, PersistentDataType.STRING, yaml.getString("damage_dice", "1d6"));
            pdc.set(KEY_DAMAGE_BONUS, PersistentDataType.INTEGER, yaml.getInt("damage_bonus", rarity.getDamageBonus()));
            pdc.set(KEY_DAMAGE_TYPE, PersistentDataType.STRING, yaml.getString("damage_type", "SLASHING"));
            pdc.set(KEY_ATTACK_TYPE, PersistentDataType.STRING, yaml.getString("attack_type", "MELEE"));
            String sfx = yaml.getString("special_effect");
            if (sfx != null && !sfx.isEmpty() && !sfx.equalsIgnoreCase("null")) {
                pdc.set(KEY_SPECIAL_EFFECT, PersistentDataType.STRING, sfx.toUpperCase());
            }
        } else if (type == ItemType.DICE) {
            pdc.set(KEY_DICE_TYPE, PersistentDataType.STRING, yaml.getString("dice_type", "D20"));
        }

        item.setItemMeta(meta);
        return item;
    }

    // ==================== PDC READING ====================

    /** Check if an ItemStack is a DnD special item. */
    public static boolean isSpecialItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_ITEM_ID, PersistentDataType.STRING);
    }

    /** Get item_id from PDC. */
    public static String getItemId(ItemStack item) {
        if (!isSpecialItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_ITEM_ID, PersistentDataType.STRING);
    }

    /** Get item type from PDC. */
    public static ItemType getItemType(ItemStack item) {
        if (!isSpecialItem(item)) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(KEY_ITEM_TYPE, PersistentDataType.STRING);
        return ItemType.fromString(s);
    }

    /** Read weapon damage dice from item PDC. Returns null if not a weapon. */
    public static String getWeaponDamageDice(ItemStack item) {
        if (!isSpecialItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_DAMAGE_DICE, PersistentDataType.STRING);
    }

    /** Read weapon damage bonus from item PDC. */
    public static int getWeaponDamageBonus(ItemStack item) {
        if (!isSpecialItem(item)) return 0;
        Integer b = item.getItemMeta().getPersistentDataContainer().get(KEY_DAMAGE_BONUS, PersistentDataType.INTEGER);
        return b != null ? b : 0;
    }

    /** Read weapon damage type from item PDC. */
    public static CombatEngine.DamageType getWeaponDamageType(ItemStack item) {
        if (!isSpecialItem(item)) return CombatEngine.DamageType.SLASHING;
        String s = item.getItemMeta().getPersistentDataContainer().get(KEY_DAMAGE_TYPE, PersistentDataType.STRING);
        if (s == null) return CombatEngine.DamageType.SLASHING;
        try { return CombatEngine.DamageType.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return CombatEngine.DamageType.SLASHING; }
    }

    /** Read weapon attack type from PDC. */
    public static String getWeaponAttackType(ItemStack item) {
        if (!isSpecialItem(item)) return "MELEE";
        String s = item.getItemMeta().getPersistentDataContainer().get(KEY_ATTACK_TYPE, PersistentDataType.STRING);
        return s != null ? s : "MELEE";
    }

    /** Read special effect from PDC. */
    public static SpecialEffect getSpecialEffect(ItemStack item) {
        if (!isSpecialItem(item)) return SpecialEffect.NONE;
        String s = item.getItemMeta().getPersistentDataContainer().get(KEY_SPECIAL_EFFECT, PersistentDataType.STRING);
        return SpecialEffect.fromString(s);
    }

    // ==================== PLAYER HELPERS ====================

    /** Get special weapon in player's main hand, or null. */
    public static ItemStack getWeaponInHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (isSpecialItem(item) && getItemType(item) == ItemType.WEAPON) {
            return item;
        }
        return null;
    }

    /** Get item_id for creating new custom items (checks uniqueness). */
    public boolean itemExists(String itemId) {
        return itemCache.containsKey(itemId.toLowerCase());
    }

    public void reload() {
        loadAll();
    }
}
