package me.cedric.siegegame.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.model.player.stats.PlayerStats;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class TopStatsCommandExecutor implements com.mojang.brigadier.Command<CommandSourceStack> {
    private final SiegeGamePlugin plugin;
    private static final int TOP_LIMIT = 10;
    private static final Map<String, String> STAT_DISPLAY_NAMES = new HashMap<>();
    
    static {
        STAT_DISPLAY_NAMES.put("kills", "Kills");
        STAT_DISPLAY_NAMES.put("deaths", "Deaths");
        STAT_DISPLAY_NAMES.put("damage", "Total Damage");
        STAT_DISPLAY_NAMES.put("games", "Games Played");
        STAT_DISPLAY_NAMES.put("wins", "Games Won");
        STAT_DISPLAY_NAMES.put("kdr", "KDR");
    }

    public TopStatsCommandExecutor(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getExecutor();
        
        if (!sender.hasPermission("siegegame.stats.top")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return 0;
        }

        String statType = "kills"; 
        try {
            statType = StringArgumentType.getString(context, "stat");
        } catch (IllegalArgumentException e) {

        }

        String dbColumn = mapStatToColumn(statType);
        if (dbColumn == null) {
            sendUsage(sender);
            return 0;
        }

        String displayName = STAT_DISPLAY_NAMES.getOrDefault(statType.toLowerCase(), statType);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerStats> topPlayers = plugin.getDatabaseManager().getTopPlayers(dbColumn, TOP_LIMIT);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sendTopStats(sender, topPlayers, displayName);
            });
        });

        return 1;
    }

    private String mapStatToColumn(String stat) {
        switch (stat.toLowerCase()) {
            case "kills":
                return "kills";
            case "deaths":
                return "deaths";
            case "damage":
                return "total_damage_dealt";
            case "games":
                return "games_played";
            case "wins":
                return "games_won";
            case "kdr":
                return "kdr";
            default:
                return null;
        }
    }

    private void sendTopStats(CommandSender sender, List<PlayerStats> topPlayers, String statName) {
        if (topPlayers.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No stats found.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Top " + TOP_LIMIT + " Players by " + statName);
        sender.sendMessage("");

        for (int i = 0; i < topPlayers.size(); i++) {
            PlayerStats stats = topPlayers.get(i);
            String position = (i + 1) + ".";
            String playerName = stats.getPlayerName();
            String value = getStatValue(stats, statName);
            
            String entry = String.format("%s %s %s%s: %s%s",
                    ChatColor.YELLOW + position,
                    ChatColor.WHITE + playerName,
                    ChatColor.GRAY,
                    statName,
                    ChatColor.GREEN,
                    value);
                    
            sender.sendMessage(entry);
        }
    }

    private String getStatValue(PlayerStats stats, String statName) {
        return switch (statName.toLowerCase()) {
            case "kills" -> String.valueOf(stats.getKills());
            case "deaths" -> String.valueOf(stats.getDeaths());
            case "total damage" -> String.format("%.2f", stats.getTotalDamageDealt());
            case "games played" -> String.valueOf(stats.getGamesPlayed());
            case "games won" -> String.valueOf(stats.getGamesWon());
            case "kdr" -> String.format("%.2f", stats.getKDR());
            default -> "0";
        };
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage: /stats top <kills|deaths|damage|games|wins|kdr>");
    }
}



