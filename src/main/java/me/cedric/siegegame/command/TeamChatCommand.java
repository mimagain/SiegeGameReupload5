package me.cedric.siegegame.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.enums.Permissions;
import me.cedric.siegegame.model.SiegeGameMatch;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.teams.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeamChatCommand implements Command<CommandSourceStack> {
    private final SiegeGamePlugin plugin;

    public TeamChatCommand(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    
    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();

        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players!").color(NamedTextColor.RED));
            return 0; 
        }

        Player player = (Player) sender;
        SiegeGameMatch match = plugin.getGameManager().getCurrentMatch();

        if (match == null) {
            player.sendMessage(Component.text("There is no active game!", NamedTextColor.RED));
            return 0; 
        }

        GamePlayer gamePlayer = match.getWorldGame().getPlayer(player.getUniqueId());

        if (gamePlayer == null) {
            player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
            return 0; 
        }

        if (!gamePlayer.hasTeam()) {
            player.sendMessage(Component.text("You must be in a team to use team chat!", NamedTextColor.RED));
            return 0; 
        }


        String permissionNode = Permissions.TEAM_CHAT.getPermission();
        if (permissionNode == null || permissionNode.isEmpty()) {
            Bukkit.getLogger().warning("[SiegeGame] Team chat permission node is not configured properly.");
            player.sendMessage(Component.text("Error: Team chat permission is not configured.", NamedTextColor.RED));
            return 0;
        }
        if (!player.hasPermission(permissionNode)) {
            player.sendMessage(Component.text("You don't have permission to use team chat!", NamedTextColor.RED));
            return 0; 
        }

        String messageContent = null;
        boolean argumentWasInContext = true;

        try {



            messageContent = context.getArgument("message", String.class);
        } catch (IllegalArgumentException e) {




            argumentWasInContext = false;
        }

        if (!argumentWasInContext) {


            toggleChatMode(player, gamePlayer);
        } else {



            if (messageContent.isEmpty()) {

                toggleChatMode(player, gamePlayer);
            } else {

                sendTeamMessage(player, gamePlayer, messageContent);
            }
        }
        return 1; 
    }

    
    private void toggleChatMode(Player player, GamePlayer gamePlayer) {
        boolean newState = !gamePlayer.isInTeamChatMode();
        gamePlayer.setTeamChatMode(newState);

        if (newState) {
            player.sendMessage(Component.text("Team chat enabled. All messages will be sent to your team.",
                    NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Team chat disabled. Messages will be sent to global chat.",
                    NamedTextColor.RED)); 
        }
    }

    
    private void sendTeamMessage(Player player, GamePlayer gamePlayer, String message) {
        Team team = gamePlayer.getTeam();

        if (team == null) {
            player.sendMessage(Component.text("Error: You are not on a team to send a team message.", NamedTextColor.RED));
            return;
        }


        TextColor teamColor = team.getColor() instanceof TextColor ? (TextColor) team.getColor() : TextColor.fromHexString("#00FFFF"); 
        TextColor playerColor = teamColor; 

        Component formattedMessage = Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(team.getName(), teamColor))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(player.getName(), playerColor))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.WHITE))
                .build();

        for (GamePlayer teamMember : team.getPlayers()) {
            Player member = teamMember.getBukkitPlayer(); 
            if (member != null && member.isOnline()) {
                member.sendMessage(formattedMessage);
            }
        }

        Bukkit.getConsoleSender().sendMessage(
                Component.text("[Team Chat] ")
                        .append(Component.text(player.getName(), NamedTextColor.AQUA)) 
                        .append(Component.text(" (", NamedTextColor.GRAY))
                        .append(Component.text(team.getName(), teamColor)) 
                        .append(Component.text("): ", NamedTextColor.GRAY))
                        .append(Component.text(message, NamedTextColor.WHITE)) 
        );
    }
}


