
package me.cedric.siegegame.command;


import com.github.sirblobman.combatlogx.api.manager.ICombatManager;
import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.amplifier.AmplifierType;
import me.cedric.siegegame.enums.Permissions;
import me.cedric.siegegame.model.SiegeGameMatch;
import me.cedric.siegegame.model.game.WorldGame;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.teams.Team;
import me.cedric.siegegame.model.teams.territory.Territory;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.TimeUnit; 

public class SwitchCommand implements Command<CommandSourceStack> {

    private final SiegeGamePlugin plugin;
    private final Map<UUID, Long> switchCooldowns = new HashMap<>(); 

    public SwitchCommand(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> commandContext) throws CommandSyntaxException {
        CommandSender sender = commandContext.getSource().getSender();

        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage("This command can only be run by a player.");
            return 0;
        }
        Player player = (Player) sender;

        if (!player.hasPermission(Permissions.SWITCH.getPermission())) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return 0;
        }

        int cooldownSeconds = plugin.getGameConfig().getSwitchCommandCooldown();
        if (cooldownSeconds > 0 && switchCooldowns.containsKey(player.getUniqueId())) {
            long lastSwitchTime = switchCooldowns.get(player.getUniqueId());
            long currentTime = System.currentTimeMillis();
            long timeElapsed = currentTime - lastSwitchTime;
            long cooldownMillis = TimeUnit.SECONDS.toMillis(cooldownSeconds);

            if (timeElapsed < cooldownMillis) {
                long timeLeftMillis = cooldownMillis - timeElapsed;
                long timeLeftSeconds = TimeUnit.MILLISECONDS.toSeconds(timeLeftMillis);
                player.sendMessage(ChatColor.RED + "You must wait " + timeLeftSeconds + " more seconds to use /switch again.");
                return 0;
            }
        }


        SiegeGameMatch match = plugin.getGameManager().getCurrentMatch();
        if (match == null) {
            player.sendMessage(ChatColor.RED + "There is no active Siege game.");
            return 0;
        }
        WorldGame worldGame = match.getWorldGame();
        GamePlayer gamePlayer = worldGame.getPlayer(player.getUniqueId());

        if (gamePlayer == null || !gamePlayer.hasTeam()) {
            player.sendMessage(ChatColor.RED + "You are not currently in a team for this match.");
            return 0;
        }

        Team currentTeam = gamePlayer.getTeam();
        Territory territory = currentTeam.getTerritory();

        if (!territory.isInside(player.getLocation())) {
            player.sendMessage(ChatColor.RED + "You must be inside your team's claims to use /switch.");
            return 0;
        }

        ICombatManager combatManager = plugin.getCombatLogX().getCombatManager();
        if (combatManager.isInCombat(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use /switch while in combat.");
            return 0;
        }

        Set<Team> allTeams = worldGame.getTeams();
        if (allTeams.size() != 2) {
            player.sendMessage(ChatColor.RED + "Team switching is currently only supported in 2-team games.");
            return 0;
        }
        if (worldGame.getAmplifierManager().isActive(AmplifierType.LAST_MAN_STANDING)) {
            player.sendMessage(ChatColor.RED + "You cannot switch teams while Last Man Standing is active.");
            return 0;
        }

        Optional<Team> opposingTeamOpt = allTeams.stream()
                .filter(t -> !t.equals(currentTeam))
                .findFirst();

        if (opposingTeamOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Could not find an opposing team.");
            return 0;
        }

        Team opposingTeam = opposingTeamOpt.get();
        int currentTeamSize = currentTeam.getPlayers().size();
        int opposingTeamSize = opposingTeam.getPlayers().size();

        if (currentTeamSize <= opposingTeamSize) {
            player.sendMessage(ChatColor.RED + "You can only switch if your team has more players than the opposing team.");
            return 0;
        }


        plugin.getLogger().info("[DEBUG] SwitchCommand: Player " + player.getName() + " STARTING switch. Current team: " + (gamePlayer.hasTeam() ? gamePlayer.getTeam().getName() : "null"));

        plugin.getLogger().info("[DEBUG] SwitchCommand: Removing from team " + currentTeam.getName());
        currentTeam.removePlayer(gamePlayer);
        plugin.getLogger().info("[DEBUG] SwitchCommand: Player " + player.getName() + " team is NOW (after remove): " + (gamePlayer.hasTeam() ? gamePlayer.getTeam().getName() : "null"));

        plugin.getLogger().info("[DEBUG] SwitchCommand: Adding to team " + opposingTeam.getName());
        opposingTeam.addPlayer(gamePlayer);
        plugin.getLogger().info("[DEBUG] SwitchCommand: Player " + player.getName() + " team is NOW (after add): " + (gamePlayer.hasTeam() ? gamePlayer.getTeam().getName() : "null"));

       for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
            if (worldGame.getAmplifierManager().isActive(AmplifierType.WE_SHARPER_NOW)) {
                long remainingTicks = worldGame.getAmplifierManager().getEndTick() - Bukkit.getCurrentTick();
                if (remainingTicks > 0) {
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.STRENGTH,
                        (int) remainingTicks,
                        0,
                        true,
                        false
                    ));
                }
            }
           if (worldGame.getAmplifierManager().isActive(AmplifierType.WE_FASTER_NOW)) {
               long remainingTicks = worldGame.getAmplifierManager().getEndTick() - Bukkit.getCurrentTick();
               if (remainingTicks > 0) {
                   player.addPotionEffect(new PotionEffect(
                           PotionEffectType.SPEED,
                           (int) remainingTicks,
                           2,
                           true,
                           false
                   ));
               }
           }
             if (worldGame.getAmplifierManager().isActive(AmplifierType.WHO_IS_WHO)) {
                long remainingTicks = worldGame.getAmplifierManager().getEndTick() - Bukkit.getCurrentTick();
                if (remainingTicks > 0) {
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.INVISIBILITY,
                        (int) remainingTicks,
                        1,
                        true,
                        false
                    ));
                }
            }
            if (worldGame.getAmplifierManager().isActive(AmplifierType.MOON)) {
                long remainingTicks = worldGame.getAmplifierManager().getEndTick() - Bukkit.getCurrentTick();
                if (remainingTicks > 0) {
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.JUMP_BOOST,
                        (int) remainingTicks,
                        0,
                        true,
                        false
                    ));
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOW_FALLING,
                        (int) remainingTicks,
                        1,
                        true,
                        false
                    ));
                }
            }
        }
        player.getInventory().clear();
        Location targetSpawn = opposingTeam.getSafeSpawn();
        plugin.getLogger().info("[DEBUG] SwitchCommand: Target teleport location: " + targetSpawn);
        player.teleport(targetSpawn);
        plugin.getLogger().info("[DEBUG] SwitchCommand: Teleport executed.");
        plugin.getGameManager().getKitController().applyPlayerKit(player, worldGame);
        gamePlayer.grantNightVision();
        gamePlayer.getDisplayer().updateScoreboard();
        worldGame.updateAllScoreboards();

        player.sendMessage(ChatColor.GREEN + "You have successfully switched to team: " + opposingTeam.getName());
        String switchMessage = ChatColor.YELLOW + player.getName() + " has switched from " + currentTeam.getName() + " to " + opposingTeam.getName() + ".";
        ImmutableSet.copyOf(worldGame.getPlayers()).forEach(gp -> {
            Player p = gp.getBukkitPlayer();
            if (p != null) p.sendMessage(switchMessage);
        });

        if (cooldownSeconds > 0) {
            switchCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }

        return 1;
    }
}
