package me.cedric.siegegame.listeners;

import me.cedric.siegegame.SiegeGamePlugin;

import me.cedric.siegegame.model.game.WorldGame;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.teams.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;


import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import io.papermc.paper.event.player.AsyncChatEvent;


public class TeamChatListener implements Listener {
    private final SiegeGamePlugin plugin;

    public TeamChatListener(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    
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
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChatLate(AsyncChatEvent event) {
        Player player = event.getPlayer();
        WorldGame worldGame = plugin.getGameManager().getCurrentMatch().getWorldGame();
        if ( worldGame == null ){
            return;
        }
        GamePlayer gamePlayer = worldGame.getPlayer(player.getUniqueId());

        if (gamePlayer == null || !gamePlayer.isInTeamChatMode()) {
            return;
        }

        Team team = gamePlayer.getTeam();
        if (team == null) {
            return;
        }
        TextColor teamColor = team.getColor() instanceof TextColor ? (TextColor) team.getColor() : TextColor.fromHexString("#00FFFF");

        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Limit recipients of this chat event to team members only
        java.util.Set<Audience> viewers = event.viewers();
        viewers.clear();
        for (GamePlayer member : team.getPlayers()) {
            Player pMember = member.getBukkitPlayer();
            if (pMember != null) {
                viewers.add(pMember);
            }
        }

        event.setCancelled(true); // Prevent default global chat broadcast
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Send to team members
            sendTeamMessage(player, gamePlayer, plainMessage);
            // Log to console
            Bukkit.getConsoleSender().sendMessage(
                    Component.text("[Team Chat] ")
                            .append(Component.text(player.getName(), NamedTextColor.AQUA))
                            .append(Component.text(" (", NamedTextColor.GRAY))
                            .append(Component.text(team.getName(), teamColor))
                            .append(Component.text("): ", NamedTextColor.GRAY))
                            .append(Component.text(plainMessage, NamedTextColor.WHITE))
            );
        });
    }
}
