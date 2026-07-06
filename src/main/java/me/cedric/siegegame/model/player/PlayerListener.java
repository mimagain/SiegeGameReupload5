package me.cedric.siegegame.model.player;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.github.sirblobman.combatlogx.api.event.PlayerUntagEvent;
import com.github.sirblobman.combatlogx.api.manager.ICombatManager;
import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.controller.stats.StatsController;
import me.cedric.siegegame.model.SiegeGameMatch;
import me.cedric.siegegame.model.teams.Team;
import me.cedric.siegegame.util.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import me.cedric.siegegame.amplifier.AmplifierEndEvent;
import me.cedric.siegegame.amplifier.AmplifierManager;
import me.cedric.siegegame.amplifier.AmplifierStartEvent;
import me.cedric.siegegame.amplifier.AmplifierType;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.*;

public class PlayerListener implements Listener {

    public SiegeGamePlugin plugin;
    private final Set<UUID> armorRemovalSuspects = new HashSet<>();

    private final Map<UUID, Boolean> lastKnownArmorStateInCombat = new HashMap<>();
    private StatsController statsController;
    
    public PlayerListener(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    private StatsController getStatsController() {
        if (statsController == null && plugin.getGameManager().getCurrentMatch() != null) {
            this.statsController = plugin.getGameManager().getCurrentMatch().getWorldGame().getStatsController();
        }
        return statsController;
    }

    private boolean wasWearingArmor(Player player) {
        if (player == null) return false;
        PlayerInventory inventory = player.getInventory();
        if (inventory == null) return false;

        ItemStack[] armor = inventory.getArmorContents();
        if (armor == null) return false; 

        for (ItemStack item : armor) {
            if (item != null && item.getType() != Material.AIR) {
                return true; 
            }
        }
        return false; 
    }
    @EventHandler
    public void onAmplifierStart(AmplifierStartEvent event) {
     System.out.println("Amplifier started: " + event.getAmplifierType());
    }
    @EventHandler
    public void onAmplifierEnd(AmplifierEndEvent event) {
     System.out.println("Amplifier ended: " + event.getAmplifierType());
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SiegeGameMatch match = plugin.getGameManager().getCurrentMatch();

        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setInvulnerable(false);
        }
        
        if (match != null) {
            match.getWorldGame().addPlayer(player.getUniqueId());
            GamePlayer gamePlayer = match.getWorldGame().getPlayer(player.getUniqueId());

            match.getWorldGame().assignTeam(gamePlayer);

            if (gamePlayer.hasTeam()) {
                player.teleport(gamePlayer.getTeam().getSafeSpawn());
                gamePlayer.getDisplayer().updateScoreboard();
            }
            gamePlayer.grantNightVision();
        
        }

        player.getInventory().clear();
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setLevel(0);
        player.sendMessage(ChatColor.DARK_AQUA + "Welcome! Use " + ChatColor.GOLD + "/resources" + ChatColor.DARK_AQUA + " for gear.");

        plugin.getGameManager().getKitController().applyPlayerKit(player, match == null ? null : match.getWorldGame());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        SiegeGameMatch match = plugin.getGameManager().getCurrentMatch();
        if (match != null)
            match.getWorldGame().removePlayer(playerId);

        plugin.getGameManager().getKitController().unload(playerId);

        for (PotionEffect potionEffect : player.getActivePotionEffects())
            player.removePotionEffect(potionEffect.getType());
    }

    @EventHandler
    public void onXP(PlayerExpChangeEvent event) {
        event.setAmount(0);
    }

    @EventHandler
    public void onPlayerUntagged(PlayerUntagEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        if (armorRemovalSuspects.remove(player.getUniqueId())) {
            plugin.getLogger().info("[ArmorExploit] Player " + player.getName() + " untagged from combat. Removed from armor suspect list.");
        }
        lastKnownArmorStateInCombat.remove(player.getUniqueId()); 
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        SiegeGameMatch match = plugin.getGameManager().getCurrentMatch();

        if (match == null)
            return;

        if (killer != null) {
            GamePlayer killerGamePlayer = match.getWorldGame().getPlayer(killer.getUniqueId());
            GamePlayer victimGamePlayer = match.getWorldGame().getPlayer(victim.getUniqueId());

            if (killerGamePlayer != null && killerGamePlayer.hasTeam()) {

                killer.playSound(killer.getLocation(), "entity.experience_orb.pickup", 1.0F, 0F);

            if (killerGamePlayer.getTeam() != victimGamePlayer.getTeam()){
                Team team = killerGamePlayer.getTeam();
                for (GamePlayer player : team.getPlayers()) {
                    Player bukkitPlayer = player.getBukkitPlayer();
                    if (bukkitPlayer != null) {
                        int levels = bukkitPlayer.getLevel();
                        bukkitPlayer.setLevel(levels + plugin.getGameConfig().getLevelsPerKill());
                    }
                }

                if (match.getWorldGame().getAmplifierManager().isActive(AmplifierType.SUDDEN_DEATH)){
                    team.addPoints(plugin.getGameConfig().getPointsPerKill()+500);
                    match.getWorldGame().updateAllScoreboards();}
                if (!match.getWorldGame().getAmplifierManager().isActive(AmplifierType.SUDDEN_DEATH)){

                    team.addPoints(plugin.getGameConfig().getPointsPerKill());
                    match.getWorldGame().updateAllScoreboards();}
                plugin.getDatabaseManager().updatePlayerStat(killer.getUniqueId(), killer.getName(), "kills", 1);

                plugin.getDatabaseManager().updatePlayerStat(victim.getUniqueId(), victim.getName(), "deaths", 1);
            }
            if (killerGamePlayer.getTeam() == victimGamePlayer.getTeam()){
                for (Team t : match.getWorldGame().getTeams()) {
                    if (!t.equals(killerGamePlayer.getTeam())) {
                       Team teamToAwardPoints = t;
                       for (GamePlayer player : teamToAwardPoints.getPlayers()) {
                        Player bukkitPlayer = player.getBukkitPlayer();
                        if (bukkitPlayer != null) {
                            int levels = bukkitPlayer.getLevel();
                            bukkitPlayer.setLevel(levels + plugin.getGameConfig().getLevelsPerKill());
                        }
                    }
                    
                    if (match.getWorldGame().getAmplifierManager().isActive(AmplifierType.SUDDEN_DEATH)){
                            teamToAwardPoints.addPoints(plugin.getGameConfig().getPointsPerKill()+500);
                            match.getWorldGame().updateAllScoreboards();}
                        if (!match.getWorldGame().getAmplifierManager().isActive(AmplifierType.SUDDEN_DEATH)){

                    teamToAwardPoints.addPoints(plugin.getGameConfig().getPointsPerKill());
                    match.getWorldGame().updateAllScoreboards();}
                    }
                }
            }

        }

        if (victimGamePlayer == null) {

            for (Player player : Bukkit.getOnlinePlayers()) {
                GamePlayer gamePlayer = match.getWorldGame().getPlayer(player.getUniqueId());
                if (gamePlayer != null) {
                    gamePlayer.getDisplayer().displayCombatLogKill(victim.getName());
                }
            }
        } else {

            for (Player player : Bukkit.getOnlinePlayers()) {
                GamePlayer gamePlayer = match.getWorldGame().getPlayer(player.getUniqueId());
                if (gamePlayer != null) {
               
                    gamePlayer.getDisplayer().displayKill(victimGamePlayer, killerGamePlayer);
                }
            }
        }

        if (killer != null) {
            if (killerGamePlayer != null && killerGamePlayer.hasTeam() && 
                    killerGamePlayer.getTeam().getPoints() >= plugin.getGameConfig().getPointsToEnd()) {
                plugin.getGameManager().startNextGame();
            }
        }
    }
    }
    @EventHandler
    public void onLMSEND(AmplifierEndEvent e) {
        if (e.getAmplifierType() == AmplifierType.LAST_MAN_STANDING || e.getAmplifierType() == AmplifierType.KING_OF_THE_HILL) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {

                for (Team team : plugin.getGameManager().getCurrentMatch().getWorldGame().getTeams()) {
                    if (team.getPoints() >= plugin.getGameConfig().getPointsToEnd()) {
                        plugin.getGameManager().startNextGame();
                    }
                }
            }, 2 * 20L); 
        }
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager))
            return;

        if (!(event.getEntity() instanceof Player player))
            return;

        SiegeGameMatch match = plugin.getGameManager().getCurrentMatch();

        if (match == null) {
            event.setCancelled(true);
            return;
        }

        GamePlayer damagerGamePlayer = match.getWorldGame().getPlayer(damager.getUniqueId());
        GamePlayer gamePlayer = match.getWorldGame().getPlayer(player.getUniqueId());

        if (damagerGamePlayer == null || gamePlayer == null)
            return;


        if (damagerGamePlayer.hasTeam() && gamePlayer.hasTeam() && damagerGamePlayer.getTeam().equals(gamePlayer.getTeam()) && !match.getWorldGame().getAmplifierManager().isActive(AmplifierType.WHO_IS_WHO)) {
            event.setCancelled(true);
            return;
        }
       
        if (!gamePlayer.hasTeam() || !damagerGamePlayer.hasTeam())
            return;

        double damage = event.getFinalDamage();


        Team damagerTeam = damagerGamePlayer.getTeam();
        Team team = gamePlayer.getTeam();

        BoundingBox damagerSafeArea = damagerTeam.getSafeArea().getBoundingBox();
        BoundingBox teamSafeArea = team.getSafeArea().getBoundingBox();

        if (damagerSafeArea.isColliding(damager.getLocation()) || damagerSafeArea.isColliding(player.getLocation())
                || teamSafeArea.isColliding(damager.getLocation()) || teamSafeArea.isColliding(player.getLocation())) {


            ICombatManager combatManager = plugin.getCombatLogX().getCombatManager();
            if (!(combatManager.isInCombat(damager) && combatManager.isInCombat(player)))
                event.setCancelled(true);
        }
   double DamageHP = event.getFinalDamage() / 2;
   plugin.getDatabaseManager().updatePlayerStat(player.getUniqueId(), player.getName(), "total_damage_taken", DamageHP);
   plugin.getDatabaseManager().updatePlayerStat(damager.getUniqueId(), damager.getName(), "total_damage_dealt", DamageHP);
    }

    @EventHandler
    public void onEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;

        if (event.getCause().equals(EntityPotionEffectEvent.Cause.BEACON))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerShutdown(PluginDisableEvent event) {
        StatsController stats = getStatsController();
        if (stats != null) {
            stats.shutdown();
        }
    }
}


