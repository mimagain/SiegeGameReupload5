package me.cedric.siegegame.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.cedric.siegegame.SiegeGamePlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Command executor for /stats modify <add|set> <player> <stat> <value>
 * Permission: siegegame.stats.modify
 */
public class StatsModifyCommandExecutor implements com.mojang.brigadier.Command<CommandSourceStack> {

    private final SiegeGamePlugin plugin;
    private static final Map<String, String> STAT_TO_COLUMN = new HashMap<>();

    static {
        STAT_TO_COLUMN.put("kills", "kills");
        STAT_TO_COLUMN.put("deaths", "deaths");
        STAT_TO_COLUMN.put("damage", "total_damage_dealt");
        STAT_TO_COLUMN.put("damagetaken", "total_damage_taken");
        STAT_TO_COLUMN.put("gamesplayed", "games_played");
        STAT_TO_COLUMN.put("gameswon", "games_won");
        STAT_TO_COLUMN.put("kdr", "kdr"); // Still present in schema even if not used for calculations
    }

    public StatsModifyCommandExecutor(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getExecutor();

        if (!sender.hasPermission("siegegame.stats.modify")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to modify stats.");
            return 0;
        }

        String action;
        String playerName;
        String statName;
        double value;

        try {
            action = StringArgumentType.getString(context, "action");
            playerName = StringArgumentType.getString(context, "player");
            statName = StringArgumentType.getString(context, "stat");
            value = DoubleArgumentType.getDouble(context, "value");
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + "Usage: /stats modify <add|set> <player> <stat> <value>");
            return 0;
        }

        String column = STAT_TO_COLUMN.get(statName.toLowerCase());
        if (column == null) {
            sender.sendMessage(ChatColor.RED + "Unknown stat. Allowed: kills, deaths, damage, damagetaken, gamesplayed, gameswon, kdr");
            return 0;
        }

        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(playerName);
        if (offlineTarget == null || (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline())) {
            sender.sendMessage(ChatColor.RED + "Player " + playerName + " not found.");
            return 0;
        }

        UUID uuid = offlineTarget.getUniqueId();
        String resolvedName = offlineTarget.getName() != null ? offlineTarget.getName() : playerName;

        switch (action.toLowerCase()) {
            case "add" -> plugin.getDatabaseManager().updatePlayerStat(uuid, resolvedName, column, value);
            case "set" -> plugin.getDatabaseManager().setPlayerStat(uuid, resolvedName, column, value);
            default -> {
                sender.sendMessage(ChatColor.RED + "Action must be add or set.");
                return 0;
            }
        }

        sender.sendMessage(ChatColor.GREEN + "Successfully updated " + resolvedName + "'s " + statName + ".");
        return 1;
    }
}
