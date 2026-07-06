package me.cedric.siegegame.command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.model.player.stats.PlayerStats;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.papermc.paper.command.brigadier.CommandSourceStack;


public class StatsCommandExecutor implements com.mojang.brigadier.Command<CommandSourceStack> {
    private final SiegeGamePlugin plugin;

    public StatsCommandExecutor(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {


        CommandSender sender = context.getSource().getExecutor(); 

        if (!sender.hasPermission("siegegame.stats")) {
            sender.sendMessage("§cYou do not have permission to view player stats.");
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
                sender.sendMessage("§cUsage: /stats <player_name>");
                sender.sendMessage("§e(As console, you must specify a player name.)");
                return 0;
            }
        } else {
            Player onlineTarget = Bukkit.getPlayer(playerNameArg);
            if (onlineTarget != null) {
                targetUuid = onlineTarget.getUniqueId();
                targetName = onlineTarget.getName();
            } else {
                OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(playerNameArg);
                if (offlineTarget != null && offlineTarget.hasPlayedBefore()) {
                    targetUuid = offlineTarget.getUniqueId();
                    targetName = offlineTarget.getName();
                } else {
                    sender.sendMessage("§cPlayer §6" + playerNameArg + " §cnot found or has no recorded data.");
                    return 0;
                }
            }
        }

        if (targetUuid == null) {
            sender.sendMessage("§cAn internal error occurred while determining player stats.");
            return 0;
        }

        final UUID finalTargetUuid = targetUuid;
        final String finalTargetName = targetName;

        CompletableFuture.runAsync(() -> {
            boolean hidden = plugin.getDatabaseManager().isStatsHidden(finalTargetUuid);

            // Only bypass hidden flag if the requester IS the target or has the bypass permission
            boolean bypass = false;
            if (sender instanceof Player) {
                Player p = (Player) sender;
                bypass = p.getUniqueId().equals(finalTargetUuid) || p.hasPermission("siegegame.stats.hide.bypass");
            }

            if (hidden && !bypass) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage("§eThe player has chosen to hide their stats."));
                return;
            }

            PlayerStats stats = plugin.getDatabaseManager().getPlayerStats(finalTargetUuid);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (stats != null) {
                    sender.sendMessage(stats.toString());
                } else {
                    sender.sendMessage("§eStats for §6" + finalTargetName + " §e not found. They might not have any recorded activity yet.");
                }
            });
        });

        return 1;
    }
}

