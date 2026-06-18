package id.patahgaming.dndplugin.command;

import id.patahgaming.dndplugin.DndPlugin;
import id.patahgaming.dndplugin.model.NarratorMode;
import id.patahgaming.dndplugin.engine.PoiTickTask;
import id.patahgaming.dndplugin.ui.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * /dnd <subcommand>
 *
 * Parent command untuk DnD plugin admin tools:
 *   /dnd reload  — reload config + semua YAML data dari disk (hot swap)
 *   /dnd save    — force save semua data ke disk
 *   /dnd version — tampilkan versi plugin
 *   /dnd stop    — stop tick task (untuk debug)
 *   /dnd start   — start tick task (kalau lagi stop)
 */
public class DndCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "save", "version", "stop", "start", "mode");

    private final DndPlugin plugin;

    public DndCommand(DndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§eDnD AI System §7v" + plugin.getPluginMeta().getVersion());
            sender.sendMessage("§7Subcommands: §e/dnd reload §7| §e/dnd save §7| §e/dnd version §7| §e/dnd stop §7| §e/dnd start");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> handleReload(sender);
            case "save" -> handleSave(sender);
            case "version" -> handleVersion(sender);
            case "stop" -> handleStopTick(sender);
            case "start" -> handleStartTick(sender);
            case "mode" -> handleMode(sender, args);
            default -> sender.sendMessage("§cUnknown subcommand: " + sub + ". Use: reload, save, version, stop, start, mode");
        }

        return true;
    }

    /**
     * Hot reload: baca ulang config.yml + semua YAML data dari disk.
     * Gak perlu restart server. Story tree, NPCs, POIs semua refresh.
     */
    private void handleReload(CommandSender sender) {
        sender.sendMessage("§e[DnD] Reloading...");

        try {
            // 1. Reload config
            plugin.reloadConfig();
            sender.sendMessage("§a  ✓ config.yml reloaded");

            // 2. Stop tick task sementara
            PoiTickTask tickTask = plugin.getPoiTickTask();
            if (tickTask != null) {
                tickTask.cancel();
            }

            // 3. Reload all data stores (baca ulang dari disk)
            // Re-create stores — mereka otomatis load dari folder data/
            plugin.reloadDataStores();
            sender.sendMessage("§a  ✓ Story tree reloaded (" + plugin.getStoryTreeStore().getAllWithBboxTrigger().size() + " nodes)");
            sender.sendMessage("§a  ✓ NPCs reloaded (" + plugin.getNpcDataStore().getAll().size() + " npcs)");
            sender.sendMessage("§a  ✓ POIs reloaded (" + plugin.getPoiStore().getAll().size() + " pois)");

            // 4. Restart tick task dengan interval dari config (yang baru)
            int intervalSeconds = plugin.getConfig().getInt("engine.poi-tick-interval-seconds", 2);
            long intervalTicks = Math.max(20L, intervalSeconds * 20L);
            PoiTickTask newTask = new PoiTickTask(plugin);
            newTask.runTaskTimer(plugin, intervalTicks, intervalTicks);
            plugin.setPoiTickTask(newTask);
            sender.sendMessage("§a  ✓ Tick task restarted (interval: " + intervalSeconds + "s)");

            // 5. Refresh scoreboard semua player online
            for (Player p : Bukkit.getOnlinePlayers()) {
                ScoreboardManager.refresh(plugin, p);
            }
            sender.sendMessage("§a  ✓ Scoreboards refreshed for " + Bukkit.getOnlinePlayers().size() + " players");

            sender.sendMessage("§a[DnD] §a§lReload complete!");

        } catch (Exception e) {
            sender.sendMessage("§c[DnD] Reload failed: " + e.getMessage());
            plugin.getLogger().warning("Reload failed: " + e.getMessage());
        }
    }

    /** Force save semua data ke disk. */
    private void handleSave(CommandSender sender) {
        sender.sendMessage("§e[DnD] Saving all data...");
        int playerCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.getPlayerDataStore().save(p);
            playerCount++;
        }
        plugin.getNpcDataStore().saveAll();
        sender.sendMessage("§a[DnD] Saved " + playerCount + " players + " +
                plugin.getNpcDataStore().getAll().size() + " NPCs");
    }

    private void handleVersion(CommandSender sender) {
        String ver = plugin.getPluginMeta().getVersion();
        String api = plugin.getPluginMeta().getAPIVersion();
        sender.sendMessage("§6DnD AI System §7v" + ver);
        sender.sendMessage("§7  API: " + api + " | MC: " + Bukkit.getMinecraftVersion());
        sender.sendMessage("§7  Party node: " + plugin.getPlayerDataStore().getPartyCurrentNode());
        sender.sendMessage("§7  DeepSeek model: " + plugin.getConfig().getString("deepseek.model", "?"));
    }

    private void handleStopTick(CommandSender sender) {
        PoiTickTask task = plugin.getPoiTickTask();
        if (task != null) {
            task.cancel();
            plugin.setPoiTickTask(null);
            sender.sendMessage("§e[DnD] Tick task stopped. Story triggers disabled.");
        } else {
            sender.sendMessage("§7[DnD] Tick task already stopped.");
        }
    }

    private void handleStartTick(CommandSender sender) {
        if (plugin.getPoiTickTask() != null) {
            sender.sendMessage("§7[DnD] Tick task already running.");
            return;
        }
        int intervalSeconds = plugin.getConfig().getInt("engine.poi-tick-interval-seconds", 2);
        long intervalTicks = Math.max(20L, intervalSeconds * 20L);
        PoiTickTask newTask = new PoiTickTask(plugin);
        newTask.runTaskTimer(plugin, intervalTicks, intervalTicks);
        plugin.setPoiTickTask(newTask);
        sender.sendMessage("§a[DnD] Tick task started (interval: " + intervalSeconds + "s)");
    }

    private void handleMode(CommandSender sender, String[] args) {
        if (args.length < 2) {
            NarratorMode current = plugin.getPartySessionStore().getMode();
            sender.sendMessage("§e/dnd mode <semi|auto> §7— current: §f" + current.name().toLowerCase());
            sender.sendMessage("§7  semi: AI narasi, pilihan story butuh input player");
            sender.sendMessage("§7  auto: AI pilihkan next_node otomatis");
            return;
        }
        NarratorMode mode = NarratorMode.fromString(args[1]);
        plugin.getPartySessionStore().setMode(mode);
        sender.sendMessage("§a[DnD] Narrator mode set to: §f" + mode.name().toLowerCase());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && "mode".equalsIgnoreCase(args[0])) {
            String input = args[1].toLowerCase();
            return List.of("semi", "auto").stream()
                    .filter(s -> s.startsWith(input)).toList();
        }
        return List.of();
    }
}
