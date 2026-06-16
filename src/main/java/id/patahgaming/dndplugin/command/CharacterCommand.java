package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.data.CharacterClass;
import id.patahgaming.dndplugin.data.PlayerDataStore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /character <create|info> [args]
 *
 * create <class> [char_name] [race]  — buat karakter baru
 * info                                 — lihat stats karakter saat ini
 */
public class CharacterCommand implements CommandExecutor, TabCompleter {

    private final DndPlugin plugin;

    public CharacterCommand(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommand ini hanya bisa dijalankan oleh player.");
            return true;
        }

        if (args.length < 1) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "info" -> handleInfo(player);
            default -> sendUsage(player);
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        PlayerDataStore store = plugin.getPlayerDataStore();

        // Cek udah punya karakter
        if (store.hasCharacter(player)) {
            player.sendMessage("§cKamu sudah punya karakter!");
            player.sendMessage("§7  " + store.getCharacterName(player) + " — Lv" + store.getLevel(player)
                    + " " + store.getCharacterClass(player));
            player.sendMessage("§7Gunakan §e/character info §7untuk lihat detail.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§eGunakan: /character create <class> [char_name] [race]");
            player.sendMessage("§7Classes: §eFighter, Rogue, Wizard, Cleric, Barbarian, Ranger, Paladin, Warlock, Monk, Druid");
            player.sendMessage("§7Contoh: /character create rogue \"Shadow Walker\" Elf");
            return;
        }

        String className = args[1];
        CharacterClass charClass = CharacterClass.fromName(className);
        if (charClass == null) {
            player.sendMessage("§cClass '" + className + "' tidak dikenal.");
            player.sendMessage("§7Classes: Fighter, Rogue, Wizard, Cleric, Barbarian, Ranger, Paladin, Warlock, Monk, Druid");
            return;
        }

        // Parse char_name dan race dari sisa args
        String charName = player.getName(); // default = Minecraft name
        String race = "Human";               // default
        if (args.length >= 3) {
            charName = args[2];
        }
        if (args.length >= 4) {
            race = args[3];
        }
        if (args.length >= 5) {
            // Nama mungkin multi-kata
            charName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length - 1));
            race = args[args.length - 1];
        }

        store.createCharacter(player, charClass, charName, race);
    }

    private void handleInfo(Player player) {
        PlayerDataStore store = plugin.getPlayerDataStore();

        if (!store.hasCharacter(player)) {
            player.sendMessage("§7Belum punya karakter. Buat dulu: §e/character create <class>");
            return;
        }

        CharacterClass charClass = CharacterClass.fromName(store.getCharacterClass(player));
        String className = charClass != null ? charClass.getDisplayName() : store.getCharacterClass(player);

        player.sendMessage("§6§l=== " + store.getCharacterName(player) + " ===§r");
        player.sendMessage("§7Lv" + store.getLevel(player) + " " + className
                + " | " + store.getRace(player));
        player.sendMessage("§7HP: " + (int)player.getHealth() + "/" + (int)player.getMaxHealth()
                + " | AC: " + (10 + store.getAbilityModifier(player, "dex")));
        player.sendMessage("§7STR:" + pad(store.getAbility(player, "str"), store.getAbilityModifier(player, "str"))
                + " DEX:" + pad(store.getAbility(player, "dex"), store.getAbilityModifier(player, "dex"))
                + " CON:" + pad(store.getAbility(player, "con"), store.getAbilityModifier(player, "con")));
        player.sendMessage("§7INT:" + pad(store.getAbility(player, "int"), store.getAbilityModifier(player, "int"))
                + " WIS:" + pad(store.getAbility(player, "wis"), store.getAbilityModifier(player, "wis"))
                + " CHA:" + pad(store.getAbility(player, "cha"), store.getAbilityModifier(player, "cha")));

        if (charClass != null) {
            player.sendMessage("§7Proficient: " + String.join(", ", charClass.getSkillOptions()));
        }
    }

    private String pad(int score, int mod) {
        return score + " (" + (mod >= 0 ? "+" : "") + mod + ")";
    }

    private void sendUsage(Player player) {
        player.sendMessage("§e/character create <class> [name] [race] §7— Buat karakter baru");
        player.sendMessage("§e/character info §7— Lihat stats karakter");
        player.sendMessage("§7Classes: Fighter, Rogue, Wizard, Cleric, Barbarian, Ranger, Paladin, Warlock, Monk, Druid");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> subs = new ArrayList<>();
            for (String s : List.of("create", "info")) {
                if (s.startsWith(input)) subs.add(s);
            }
            return subs;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            String input = args[1].toLowerCase();
            List<String> classes = new ArrayList<>();
            for (CharacterClass c : CharacterClass.values()) {
                if (c.name().toLowerCase().startsWith(input)) {
                    classes.add(c.name().toLowerCase());
                }
            }
            return classes;
        }
        return List.of();
    }
}
