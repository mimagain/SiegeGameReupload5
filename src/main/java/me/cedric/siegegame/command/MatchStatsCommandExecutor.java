
package me.cedric.siegegame.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.controller.stats.StatsController; 

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands; 

public class MatchStatsCommandExecutor implements com.mojang.brigadier.Command<CommandSourceStack> {
    private final SiegeGamePlugin plugin;
    private final StatsController statsController; 

    public MatchStatsCommandExecutor(SiegeGamePlugin plugin) {
        this.plugin = plugin;
        this.statsController = plugin.getStatsController();
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getExecutor();

        if (!sender.hasPermission("mystatsplugin.matchstats")) {
            sender.sendMessage("§cYou do not have permission to view match stats.");
            return 0;
        }
        StatsController statsController = plugin.getStatsController();
        if (statsController == null) {
            sender.sendMessage("§cNo active match stats available.");
            return 0;
        }
if ( plugin.getGameManager().getCurrentMatch() == null ) {
    sender.sendMessage("§cNo match found.");
    return 0;
} 

        UUID targetUuid = null;
        String targetName = null;

        String playerNameArg = null;
        try {
            playerNameArg = StringArgumentType.getString(context, "player");
        } catch (IllegalArgumentException e) {

        }

        if (playerNameArg == null) {

            if (sender instanceof Player) {
                Player playerSender = (Player) sender;
                targetUuid = playerSender.getUniqueId();
                targetName = playerSender.getName();
            } else {
                sender.sendMessage("§cUsage: /matchstats <player_name>");
                sender.sendMessage("§e(As Fconsole, you must specify a player name.)");
                return 0;
            }
        } else {

            Player onlineTarget = Bukkit.getPlayer(playerNameArg);
            if (onlineTarget != null) {
                targetUuid = onlineTarget.getUniqueId();
                targetName = onlineTarget.getName();
            } else {
                sender.sendMessage("§cPlayer §6" + playerNameArg + " §c is not online or currently participating in a match.");
                return 0;
            }
        }

        if (targetUuid == null) {
            sender.sendMessage("§cAn internal error occurred while determining player for match stats.");
            return 0;
        }

        final UUID finalTargetUuid = targetUuid;
        final String finalTargetName = targetName;
        final CommandSender finalSender = sender;

        

        int kills = statsController.getKillsMap().getOrDefault(finalTargetUuid, 0);
        int deaths = statsController.getDeathMap().getOrDefault(finalTargetUuid, 0);
        double damageDealt = statsController.getDamageMap().getOrDefault(finalTargetUuid, 0.0);

        if (kills == 0 && deaths == 0 && damageDealt == 0.0) {
            finalSender.sendMessage("§eNo current match stats found for §6" + finalTargetName + ". They might not be in an active game session or haven't accumulated stats yet.");
        } else {
            String statsMessage = "§eCurrent Match Stats for §6" + finalTargetName + ":\n" +
                                  "§a  Kills: §f" + kills + "\n" +
                                  "§c  Deaths: §f" + deaths + "\n" +
                                  "§e  Damage Dealt: §f" + String.format("%.2f", damageDealt);
            finalSender.sendMessage(statsMessage);
        }

        return 1;
    }
}


