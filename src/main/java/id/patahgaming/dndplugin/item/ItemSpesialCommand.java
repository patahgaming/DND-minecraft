package id.patahgaming.dndplugin.item;

import id.patahgaming.dndplugin.DndPlugin;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /itemspesial create|get|list|info|give
 */
public class ItemSpesialCommand implements CommandExecutor, TabCompleter {

    private final DndPlugin plugin;

    private static final List<String> RARITIES = Arrays.stream(ItemRarity.values())
            .map(r -> r.name().toLowerCase()).toList();
    private static final List<String> TYPES = Arrays.stream(ItemType.values())
            .filter(t -> t != ItemType.DICE)
            .map(t -> t.name().toLowerCase()).toList();

    public ItemSpesialCommand(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§e/itemspesial create|get|list|info|give");
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "get" -> handleGet(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "give" -> handleGive(sender, args);
            default -> {
                sender.sendMessage("§cUnknown: " + sub + ". Use: create, get, list, info, give");
                yield true;
            }
        };
    }

    // ==================== CREATE ====================

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cHanya player yang bisa membuat item.");
            return true;
        }

        if (args.length < 5) {
            player.sendMessage("§e/itemspesial create <rarity> <item_type> <item_id> <nama...>");
            player.sendMessage("§7Rarity: " + String.join(", ", RARITIES));
            player.sendMessage("§7Types: " + String.join(", ", TYPES));
            return true;
        }

        ItemRarity rarity = ItemRarity.fromString(args[1]);
        if (rarity == null) {
            player.sendMessage("§cInvalid rarity: " + args[1]);
            return true;
        }

        ItemType type = ItemType.fromString(args[2]);
        if (type == null || type == ItemType.DICE) {
            player.sendMessage("§cInvalid type: " + args[2]);
            return true;
        }

        String itemId = args[3].toLowerCase();
        ItemSpesialManager mgr = plugin.getItemSpesialManager();
        if (mgr.itemExists(itemId)) {
            player.sendMessage("§cItem ID '" + itemId + "' sudah ada. Gunakan ID lain.");
            return true;
        }

        // Cek item di main hand
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem.getType() == Material.AIR) {
            player.sendMessage("§cPegang item dulu di tangan kanan sebagai base visual.");
            return true;
        }

        // Build display name dari sisa argumen
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 4; i < args.length; i++) {
            if (i > 4) nameBuilder.append(" ");
            nameBuilder.append(args[i]);
        }
        String displayName = nameBuilder.toString();

        // Build YAML
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item_id", itemId);
        yaml.set("display_name", displayName);
        yaml.set("rarity", rarity.name());
        yaml.set("item_type", type.name());
        yaml.set("minecraft_material", handItem.getType().name());

        // Type-specific fields
        if (type == ItemType.WEAPON) {
            yaml.set("damage_dice", handItem.getType() == Material.BOW ? "1d6" : "1d8");
            yaml.set("damage_bonus", rarity.getDamageBonus());
            yaml.set("damage_type", "SLASHING");
            yaml.set("attack_type", handItem.getType() == Material.BOW ? "RANGED" : "MELEE");
            yaml.set("special_effect", null);
        } else if (type == ItemType.ARMOR) {
            // Detect armor slot from material
            String matName = handItem.getType().name();
            String slot = matName.contains("CHESTPLATE") ? "CHEST"
                    : matName.contains("HELMET") ? "HEAD"
                    : matName.contains("LEGGINGS") ? "LEGS"
                    : matName.contains("BOOTS") ? "FEET" : "CHEST";
            yaml.set("ac_bonus", rarity.getDamageBonus());
            yaml.set("slot", slot);
        } else if (type == ItemType.CONSUMABLE) {
            yaml.set("heal_dice", rarity == ItemRarity.COMMON ? "2d4+2" : "4d4+4");
        } else if (type == ItemType.QUEST) {
            yaml.set("quest_node", "");
            yaml.set("no_drop", true);
        }

        List<String> lore = new ArrayList<>();
        lore.add("§7Dibuat oleh " + player.getName());
        yaml.set("lore", lore);

        // Save to disk
        mgr.saveCustom(itemId, yaml);

        // Build & give item
        ItemStack specialItem = mgr.buildItem(yaml);
        player.getInventory().setItemInMainHand(specialItem);

        player.sendMessage("§a✅ Item dibuat: " + rarity.getColor() + displayName + " §8[" + itemId + "]");
        return true;
    }

    // ==================== GET ====================

    private boolean handleGet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cHanya player.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§e/itemspesial get <item_id>");
            return true;
        }

        String itemId = args[1].toLowerCase();
        ItemSpesialManager mgr = plugin.getItemSpesialManager();
        ItemStack item = mgr.buildItem(itemId);

        if (item == null) {
            player.sendMessage("§cItem tidak ditemukan: " + itemId);
            return true;
        }

        // Beri ke player
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), item);
            player.sendMessage("§eInventory penuh — item dijatuhkan di kakimu.");
        } else {
            player.getInventory().addItem(item);
        }
        player.sendMessage("§a✅ Dapat: " + item.getItemMeta().getDisplayName());
        return true;
    }

    // ==================== LIST ====================

    private boolean handleList(CommandSender sender) {
        ItemSpesialManager mgr = plugin.getItemSpesialManager();
        sender.sendMessage("§e=== Daftar Item Spesial (" + mgr.getAllIds().size() + ") ===");
        for (YamlConfiguration yaml : mgr.getAll()) {
            String id = yaml.getString("item_id");
            String name = yaml.getString("display_name", id);
            String rarity = yaml.getString("rarity", "COMMON");
            String type = yaml.getString("item_type", "?");
            ItemRarity r = ItemRarity.fromString(rarity);
            String color = r != null ? r.getColor() : "§7";
            sender.sendMessage("  " + color + name + " §8[" + id + "] §7(" + type + ")");
        }
        return true;
    }

    // ==================== INFO ====================

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e/itemspesial info <item_id>");
            return true;
        }

        String itemId = args[1].toLowerCase();
        YamlConfiguration yaml = plugin.getItemSpesialManager().getItemData(itemId);
        if (yaml == null) {
            sender.sendMessage("§cItem tidak ditemukan: " + itemId);
            return true;
        }

        sender.sendMessage("§e=== " + yaml.getString("display_name", itemId) + " ===");
        for (String key : yaml.getKeys(false)) {
            Object val = yaml.get(key);
            if (val instanceof List<?> list) {
                sender.sendMessage("§7" + key + ":");
                for (Object l : list) sender.sendMessage("  §f" + l);
            } else {
                sender.sendMessage("§7" + key + ": §f" + val);
            }
        }
        return true;
    }

    // ==================== GIVE (admin) ====================

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e/itemspesial give <player> <item_id>");
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer tidak ditemukan: " + args[1]);
            return true;
        }

        String itemId = args[2].toLowerCase();
        ItemStack item = plugin.getItemSpesialManager().buildItem(itemId);
        if (item == null) {
            sender.sendMessage("§cItem tidak ditemukan: " + itemId);
            return true;
        }

        if (target.getInventory().firstEmpty() == -1) {
            target.getWorld().dropItem(target.getLocation(), item);
        } else {
            target.getInventory().addItem(item);
        }
        target.sendMessage("§a✅ Kamu menerima: " + item.getItemMeta().getDisplayName());
        sender.sendMessage("§aDiberikan ke " + target.getName());
        return true;
    }

    // ==================== TAB COMPLETE ====================

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return List.of("create", "get", "list", "info", "give").stream()
                    .filter(s -> s.startsWith(input)).toList();
        }
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            return switch (args[0].toLowerCase()) {
                case "create" -> RARITIES.stream().filter(r -> r.startsWith(input)).toList();
                case "get", "info" -> plugin.getItemSpesialManager().getAllIds().stream()
                        .filter(id -> id.startsWith(input)).sorted().toList();
                case "give" -> null; // player names
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            String input = args[2].toLowerCase();
            return TYPES.stream().filter(t -> t.startsWith(input)).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String input = args[2].toLowerCase();
            return plugin.getItemSpesialManager().getAllIds().stream()
                    .filter(id -> id.startsWith(input)).sorted().toList();
        }
        return List.of();
    }
}
