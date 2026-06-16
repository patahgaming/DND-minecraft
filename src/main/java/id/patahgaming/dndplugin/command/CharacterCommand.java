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
            case "pick" -> handlePick(player, args);
            case "list" -> handleList(player);
            case "info" -> handleInfo(player);
            case "reset" -> handleReset(player);
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

    private void handlePick(Player player, String[] args) {
        PlayerDataStore store = plugin.getPlayerDataStore();
        if (store.hasCharacter(player)) {
            player.sendMessage("§cKamu sudah punya karakter!");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§e/character pick <template> §7— Pilih karakter dari template");
            player.sendMessage("§7Gunakan §e/character list §7untuk lihat daftar.");
            return;
        }
        String templateId = args[1].toLowerCase();

        // Load template YAML
        var template = loadTemplate(templateId);
        if (template == null) {
            player.sendMessage("§cTemplate '" + templateId + "' tidak ditemukan.");
            player.sendMessage("§7Gunakan §e/character list §7untuk lihat daftar.");
            return;
        }

        String className = template.get("class");
        String charName = template.get("display_name");
        String race = template.get("race");
        String backstory = template.get("backstory");
        CharacterClass charClass = CharacterClass.fromName(className);

        if (charClass == null) {
            player.sendMessage("§cClass tidak valid di template: " + className);
            return;
        }

        store.createCharacter(player, charClass, charName, race);

        // Tampilkan backstory
        if (backstory != null && !backstory.isEmpty()) {
            player.sendMessage("§6§lBackstory:§r");
            player.sendMessage("§7" + backstory);
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, String> loadTemplate(String templateId) {
        try (var in = plugin.getResource("data/characters/" + templateId + ".yml")) {
            if (in == null) return null;
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            var data = yaml.load(in);
            if (data instanceof java.util.Map<?, ?> map) {
                java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
                for (var entry : map.entrySet()) {
                    result.put(String.valueOf(entry.getKey()),
                            entry.getValue() != null ? entry.getValue().toString() : "");
                }
                return result;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void handleList(Player player) {
        player.sendMessage("§6§l=== Character Templates ===§r");
        // Scan JAR resources for data/characters/*.yml
        boolean found = false;
        for (String templateId : scanTemplateIds()) {
            var tmpl = loadTemplate(templateId);
            if (tmpl != null) {
                found = true;
                String backstory = tmpl.getOrDefault("backstory", "");
                String snippet = backstory.length() > 60 ? backstory.substring(0, 60) + "..." : backstory;
                player.sendMessage("§e  " + templateId + " §7— §6" + tmpl.get("display_name")
                        + " §7(Lv1 " + tmpl.get("class") + " " + tmpl.get("race") + ")");
                player.sendMessage("§7    " + snippet);
            }
        }
        if (!found) {
            player.sendMessage("§7Tidak ada template tersedia.");
        }
        player.sendMessage("§7Pakai: §e/character pick <template_id>");
    }

    /** Scan known template IDs from JAR. */
    private List<String> scanTemplateIds() {
        // Known templates (bisa di-extend dengan dynamic resource listing nanti)
        return List.of("aric_fighter", "lyra_rogue", "merlin_wizard", "mira_cleric");
    }

    private void handleReset(Player player) {
        PlayerDataStore store = plugin.getPlayerDataStore();
        if (!store.hasCharacter(player)) {
            player.sendMessage("§7Kamu belum punya karakter. Buat dulu: §e/character list");
            return;
        }
        String name = store.getCharacterName(player);
        store.resetCharacter(player);
        player.sendMessage("§c§lCharacter Reset!");
        player.sendMessage("§7Karakter §e" + name + " §7telah dihapus.");
        player.sendMessage("§7Buat karakter baru: §e/character list");
    }

    private void sendUsage(Player player) {
        player.sendMessage("§e/character list §7— Lihat daftar template karakter");
        player.sendMessage("§e/character pick <template> §7— Pilih karakter dari template");
        player.sendMessage("§e/character create <class> [name] [race] §7— Buat kustom");
        player.sendMessage("§e/character info §7— Lihat stats");
        player.sendMessage("§e/character reset §7— Hapus karakter & mulai ulang");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> subs = new ArrayList<>();
            for (String s : List.of("list", "pick", "create", "info", "reset")) {
                if (s.startsWith(input)) subs.add(s);
            }
            return subs;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pick")) {
            String input = args[1].toLowerCase();
            return List.of("aric_fighter", "lyra_rogue", "merlin_wizard", "mira_cleric").stream()
                    .filter(s -> s.startsWith(input)).toList();
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
