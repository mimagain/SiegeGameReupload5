package me.cedric.siegegame.command;

import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.model.SiegeGameMatch;
import me.cedric.siegegame.model.game.WorldGame;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.teams.Team;
import me.cedric.siegegame.model.teams.territory.Territory;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpectateCommand implements CommandExecutor {

    private final SiegeGamePlugin plugin;
    private final Map<UUID, Team> previousTeams = new HashMap<>();
    private final Map<UUID, Long> spectateCooldowns = new HashMap<>();
    private static final long SPECTATE_COOLDOWN_MS = 60 * 1000L; 

    public SpectateCommand(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isOnCooldown(UUID uuid) {
        if (!spectateCooldowns.containsKey(uuid)) return false;
        return (System.currentTimeMillis() - spectateCooldowns.get(uuid)) < SPECTATE_COOLDOWN_MS;
    }

    private long getCooldownRemaining(UUID uuid) {
        if (!spectateCooldowns.containsKey(uuid)) return 0;
        long elapsed = System.currentTimeMillis() - spectateCooldowns.get(uuid);
        return Math.max(0, (SPECTATE_COOLDOWN_MS - elapsed) / 1000) + 1; 
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        if (player.getGameMode() != GameMode.SPECTATOR && isOnCooldown(player.getUniqueId())) {
            long remaining = getCooldownRemaining(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "You must wait " + remaining + " seconds before using /spectate again.");
            return true;
        }

        SiegeGameMatch match = plugin.getGameManager().getCurrentMatch();

        if (match == null) {
            player.sendMessage(ChatColor.RED + "There is no active game to spectate.");
            return true;
        }

        WorldGame worldGame = match.getWorldGame();
        GamePlayer gamePlayer = worldGame.getPlayer(player.getUniqueId());

        if (gamePlayer == null) {
            player.sendMessage(ChatColor.RED + "You are not in the game.");
            return true;
        }

        Team currentTeam = gamePlayer.getTeam();
        
        if (player.getGameMode() != GameMode.SPECTATOR) {
            if (currentTeam == null) {
                player.sendMessage(ChatColor.RED + "You must be in a team to use /spectate.");
                return true;
            }

            Territory territory = currentTeam.getTerritory();
            if (territory == null) {
                player.sendMessage(ChatColor.RED + "Your team doesn't have a valid territory.");
                return true;
            }

            if (!territory.isInside(player.getLocation())) {
                player.sendMessage(ChatColor.RED + "You must be inside your team's claims to use /spectate.");
                return true;
            }
        }

        if (player.getGameMode() != GameMode.SPECTATOR) {
            spectateCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            if (currentTeam != null) {
                previousTeams.put(player.getUniqueId(), currentTeam);
                currentTeam.removePlayer(gamePlayer);
            }
            
            player.setGameMode(GameMode.SPECTATOR);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setCollidable(true);
            player.setInvulnerable(true);
            player.sendMessage(ChatColor.GREEN + "You are now spectating. Use /spectate again to return to the game.");
            
        } else {
            Team previousTeam = previousTeams.get(player.getUniqueId());
            
            if (previousTeam == null) {
                Team availableTeam = worldGame.getTeams().stream().findFirst().orElse(null);
                
                if (availableTeam == null) {
                    player.sendMessage(ChatColor.RED + "No teams available to join. Please contact an admin.");
                    return true;
                }
                
                player.sendMessage(ChatColor.YELLOW + "Your previous team was not found. Adding you to " + availableTeam.getName() + " team.");
                previousTeam = availableTeam;
            }
            
            previousTeam.addPlayer(gamePlayer);
            
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setInvulnerable(false);
            
            if (previousTeam.getSafeSpawn() != null) {
                player.teleport(previousTeam.getSafeSpawn());
            } else {
                player.teleport(player.getWorld().getSpawnLocation());
            }
            
            player.sendMessage(ChatColor.GREEN + "You are no longer spectating. Welcome back to " + previousTeam.getName() + " team!");
            
            
            previousTeams.remove(player.getUniqueId());
        }
        
        return true;
    }
}

