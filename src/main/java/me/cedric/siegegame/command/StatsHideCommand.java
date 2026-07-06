package me.cedric.siegegame.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.player.stats.PlayerStats;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class StatsHideCommand implements com.mojang.brigadier.Command<CommandSourceStack> {
    private final SiegeGamePlugin plugin;
    public boolean hidestats;

    public StatsHideCommand(SiegeGamePlugin plugin) {
        this.plugin = plugin;
        this.hidestats = false;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getExecutor();

        if (!sender.hasPermission("siegegame.stats.hide")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return 0;
        }
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage("This command can only be run by a player.");
            return 0;
        }
        if (sender instanceof Player) {
            Player player = ((Player) sender).getPlayer();
            java.util.UUID uuid = player.getUniqueId();
            boolean currentlyHidden = plugin.getDatabaseManager().isStatsHidden(uuid);
            plugin.getDatabaseManager().setStatsHidden(uuid, player.getName(), !currentlyHidden);

            if (currentlyHidden) {
                sender.sendMessage(ChatColor.GREEN + "Your stats are now visible to other players.");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Your stats are now hidden from other players.");
            }
            return 1;
        }
        return 0;
    }
}



