package me.cedric.siegegame.lunarclient;

import com.lunarclient.apollo.Apollo;
import com.lunarclient.apollo.common.location.ApolloLocation;
import com.lunarclient.apollo.module.team.TeamMember;
import com.lunarclient.apollo.module.team.TeamModule;
import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.amplifier.AmplifierEndEvent;
import me.cedric.siegegame.amplifier.AmplifierStartEvent;
import me.cedric.siegegame.amplifier.AmplifierType;
import me.cedric.siegegame.model.game.WorldGame;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.teams.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TeamUpdateTask extends BukkitRunnable implements Listener {

    private final WorldGame worldGame;
    private final SiegeGamePlugin plugin;
    private boolean paused = false;

    public TeamUpdateTask(SiegeGamePlugin plugin, WorldGame worldGame) {
        this.plugin = plugin;
        this.worldGame = worldGame;
    }

    @Override
    public void run() {
        if (!paused) { 
            for (Team team : worldGame.getTeams()) {
                refresh(team);
            }
        }
    }

    private void refresh(Team team) {
        List<TeamMember> teammates = team.getPlayers().stream().filter(gamePlayer -> gamePlayer.getBukkitPlayer().isOnline())
                .map(gamePlayer -> createTeamMember(gamePlayer.getBukkitPlayer()))
                .toList();

        teammates.forEach(teamMember -> Apollo.getPlayerManager().getPlayer(teamMember.getPlayerUuid())
                .ifPresent(apolloPlayer -> Apollo.getModuleManager().getModule(TeamModule.class).updateTeamMembers(apolloPlayer, teammates)));
    }

    private TeamMember createTeamMember(Player member) {
        Location location = member.getLocation();

        return TeamMember.builder()
                .playerUuid(member.getUniqueId())
                .displayName(Component.text()
                        .content(member.getName())
                        .color(NamedTextColor.WHITE)
                        .build())
                .markerColor(Color.GREEN)
                .location(ApolloLocation.builder()
                        .world(location.getWorld().getName())
                        .x(location.getX())
                        .y(location.getY())
                        .z(location.getZ())
                        .build())
                .build();
    }

    private void hideTeamView(Team team) { 
        List<TeamMember> teammates = team.getPlayers().stream().filter(gamePlayer -> gamePlayer.getBukkitPlayer().isOnline())
                .map(gamePlayer -> createTeamMember(gamePlayer.getBukkitPlayer()))
                .toList();

        teammates.forEach(teamMember -> Apollo.getPlayerManager().getPlayer(teamMember.getPlayerUuid())
                .ifPresent(apolloPlayer -> Apollo.getModuleManager().getModule(TeamModule.class).updateTeamMembers(apolloPlayer, Collections.emptyList())));
    }
    
    @EventHandler
    public void onAmplifierStart(AmplifierStartEvent event) {
        if (event.getAmplifierType() == AmplifierType.WHO_IS_WHO) {
            for (Team team : worldGame.getTeams()) {
                hideTeamView(team);
            }
            paused = true;
        }
    }

    @EventHandler
    public void onAmplifierEnd(AmplifierEndEvent event) {
        if (event.getAmplifierType() == AmplifierType.WHO_IS_WHO) {
            paused = false;



            new BukkitRunnable() {
                @Override
                public void run() {
                    System.out.println("[TeamUpdateTask] Scheduled full re-send after AmplifierEndEvent for " + event.getAmplifierType());

                    for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                        Apollo.getPlayerManager().getPlayer(onlinePlayer.getUniqueId())
                            .ifPresent(apolloPlayer -> {

                                for (Team team : worldGame.getTeams()) {
                                    List<TeamMember> teammates = team.getPlayers().stream()
                                            .filter(gamePlayer -> gamePlayer.getBukkitPlayer().isOnline())
                                            .map(gamePlayer -> createTeamMember(gamePlayer.getBukkitPlayer()))
                                            .toList();

                                    Apollo.getModuleManager().getModule(TeamModule.class).updateTeamMembers(apolloPlayer, teammates);
                                }
                            });
                    }
                }
            }.runTaskLater(plugin, 1L); 

        }
    }
}


