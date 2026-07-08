package me.cedric.siegegame.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.enums.Permissions;
import me.cedric.siegegame.model.SiegeGameMatch;
import me.cedric.siegegame.model.game.WorldGame;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.teams.Team;
import me.cedric.siegegame.lunarclient.LunarClientModule;
import me.cedric.siegegame.lunarclient.WaypointSender;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public class RallyCommand implements Command<CommandSourceStack> {

    private final SiegeGamePlugin plugin;

    public RallyCommand(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> commandContext) throws CommandSyntaxException {
        CommandSender sender = commandContext.getSource().getSender();

        if (!sender.hasPermission(Permissions.RALLY.getPermission()))
            return 0;

        if (sender instanceof ConsoleCommandSender)
            return 0;

        Player player = (Player) sender;

        plugin.getLogger().info("Rally command executed by " + player.getName() + " (LC: " + LunarClientModule.isLunarClient(player.getUniqueId()) + ")");
        
        if (!LunarClientModule.isLunarClient(player.getUniqueId())) {
            String message = ChatColor.RED + "Lunar Client is required to use this feature.";
            player.sendMessage(message);
            plugin.getLogger().info("Player " + player.getName() + " doesn't have Lunar Client");
            return 0;
        }

        SiegeGameMatch match = plugin.getGameManager().getCurrentMatch();

        if (match == null) {
            player.sendMessage(ChatColor.RED + "No active match found.");
            return 0;
        }


        WorldGame worldGame = match.getWorldGame();
        if (worldGame == null) {
            player.sendMessage(ChatColor.RED + "World game is not initialized.");
            return 0;
        }


            GamePlayer gamePlayer = worldGame.getPlayer(player.getUniqueId());
        if (gamePlayer == null) {
            player.sendMessage(ChatColor.RED + "Player data not found.");
            return 0;
        }

        if (!gamePlayer.hasTeam()) {
            player.sendMessage(ChatColor.RED + "You must be on a team to use this command.");
            return 0;
        }

        Team team = gamePlayer.getTeam();
        if (team == null) {
            player.sendMessage(ChatColor.RED + "Team data not found.");
            return 0;
        }

        int memberCount = 0;
        for (GamePlayer member : team.getPlayers()) {
            if (member != null && member.getBukkitPlayer() != null) {
                memberCount++;
            }
        }

        if (memberCount == 0) {
            player.sendMessage(ChatColor.RED + "No team members found to send waypoint to.");
            return 0;
        }

        plugin.getLogger().info("Sending rally point to " + memberCount + " team members");

        org.bukkit.Location loc = player.getLocation();
        plugin.getLogger().info(String.format("Sending waypoint at %s, %s, %s in world %s", 
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getName()));
                
        WaypointSender.sendTemporaryWaypoint(plugin, team, loc, "Rally", 30 * 20);
        player.sendMessage(ChatColor.GREEN + "Rally point set! Your team has been notified.");
        plugin.getLogger().info("Waypoint sent to team with " + team.getPlayers().size() + " members");

        String message = ChatColor.YELLOW + player.getName() + ChatColor.GRAY + " has set a rally point!" + 
                        ChatColor.WHITE + " (" + player.getLocation().getBlockX() + 
                        ", " + player.getLocation().getBlockZ() + ")";
        
        for (GamePlayer member : team.getPlayers()) {
            if (member != null && !member.getUUID().equals(player.getUniqueId())) {
                Player bukkitPlayer = member.getBukkitPlayer();
                if (bukkitPlayer != null) {
                    bukkitPlayer.sendMessage(message);
                }
            }
        }

        return 0;
    }
}



