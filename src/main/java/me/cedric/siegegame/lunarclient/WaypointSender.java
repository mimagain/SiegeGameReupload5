package me.cedric.siegegame.lunarclient;

import com.lunarclient.apollo.Apollo;
import com.lunarclient.apollo.common.location.ApolloBlockLocation;
import com.lunarclient.apollo.module.waypoint.Waypoint;
import com.lunarclient.apollo.module.waypoint.WaypointModule;
import com.lunarclient.apollo.player.ApolloPlayer;
import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.model.game.WorldGame;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.teams.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.awt.*;

public class WaypointSender {

    private final WorldGame worldGame;

    public WaypointSender(WorldGame worldGame) {
        this.worldGame = worldGame;
    }

    public void send() {
        for (Team team : worldGame.getTeams()) {
            for (GamePlayer gamePlayer : team.getPlayers()) {
                Player player = gamePlayer.getBukkitPlayer();
                ApolloPlayer apolloPlayer = Apollo.getPlayerManager().getPlayer(player.getUniqueId()).orElse(null);

                if (apolloPlayer == null)
                    continue;

                Waypoint base = createWaypoint("Base", team.getSafeSpawn());
                Apollo.getModuleManager().getModule(WaypointModule.class).displayWaypoint(apolloPlayer, base);
            }

        }
    }

    public static void sendTemporaryWaypoint(SiegeGamePlugin plugin, Team team, org.bukkit.Location location, String name, int ticks) {
        if (team == null || location == null || name == null) {
            plugin.getLogger().warning("Invalid parameters for sendTemporaryWaypoint");
            return;
        }
        
        plugin.getLogger().info("Sending waypoint to team with " + team.getPlayers().size() + " members");
        int sentCount = 0;
        
        for (GamePlayer gamePlayer : team.getPlayers()) {
            if (gamePlayer != null) {
                org.bukkit.entity.Player bukkitPlayer = gamePlayer.getBukkitPlayer();
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    plugin.getLogger().info("Sending to " + bukkitPlayer.getName());
                    sendTemporaryWaypoint(plugin, bukkitPlayer, location, name, ticks);
                    sentCount++;
                } else {
                    plugin.getLogger().info("Skipping offline player: " + (gamePlayer.getUUID() != null ? gamePlayer.getUUID() : "unknown"));
                }
            }
        }
        
        plugin.getLogger().info("Waypoint sent to " + sentCount + " online players");
    }

    public static void sendTemporaryWaypoint(SiegeGamePlugin plugin, org.bukkit.entity.Player player, org.bukkit.Location location, String name, int ticks) {
        if (player == null || location == null || name == null) {
            plugin.getLogger().warning("Invalid parameters for sendTemporaryWaypoint");
            return;
        }
        
        plugin.getLogger().info("Attempting to send waypoint to " + player.getName() + " at " + 
                location.getX() + ", " + location.getY() + ", " + location.getZ());

        try {

            if (Apollo.getPlayerManager() == null) {
                plugin.getLogger().warning("Apollo PlayerManager is not initialized");
                return;
            }
            
            plugin.getLogger().info("Apollo PlayerManager is available");

            if (!Apollo.getPlayerManager().hasSupport(player.getUniqueId())) {
                plugin.getLogger().warning("Player " + player.getName() + " does not have Lunar Client support");
                return;
            }

            ApolloPlayer apolloPlayer = Apollo.getPlayerManager().getPlayer(player.getUniqueId()).orElse(null);
            if (apolloPlayer == null) {
                plugin.getLogger().warning("Failed to get ApolloPlayer for " + player.getName());
                return;
            }
            
            plugin.getLogger().info("Successfully retrieved ApolloPlayer for " + player.getName());

            Waypoint waypoint = createWaypoint(name, location);
            if (waypoint == null) {
                plugin.getLogger().warning("Failed to create waypoint for " + player.getName());
                return;
            }

            if (Apollo.getModuleManager() == null) {
                plugin.getLogger().warning("Apollo ModuleManager is not available");
                return;
            }
            
            plugin.getLogger().info("Apollo ModuleManager is available");
            
            WaypointModule waypointModule = Apollo.getModuleManager().getModule(WaypointModule.class);
            if (waypointModule == null) {
                plugin.getLogger().warning("Failed to get WaypointModule");
                return;
            }
            
            plugin.getLogger().info("Successfully retrieved WaypointModule");

            plugin.getLogger().info("Attempting to display waypoint for " + player.getName());
            waypointModule.displayWaypoint(apolloPlayer, waypoint);
            plugin.getLogger().info("Waypoint displayed for " + player.getName());

            plugin.getLogger().info("Scheduling waypoint removal in " + ticks + " ticks");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    plugin.getLogger().info("Removing waypoint for " + player.getName());
                    waypointModule.removeWaypoint(apolloPlayer, waypoint);
                    plugin.getLogger().info("Waypoint removed for " + player.getName());
                } catch (Exception e) {
                    plugin.getLogger().warning("Error removing waypoint: " + e.getMessage());
                    if (plugin.getConfig().getBoolean("debug", false)) {
                        e.printStackTrace();
                    }
                }
            }, ticks);
                    
            plugin.getLogger().info("Successfully sent waypoint \"" + name + "\" to " + player.getName());
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error sending waypoint to " + player.getName() + ": " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
        }
    }

    private static Waypoint createWaypoint(String name, Location loc) {
        return Waypoint.builder()
                .color(Color.CYAN)
                .name(name)
                .location(ApolloBlockLocation.builder().x(loc.getBlockX()).y(loc.getBlockY()).z(loc.getBlockZ()).world(loc.getWorld().getName()).build())
                .build();

    }

}


