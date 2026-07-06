package me.cedric.siegegame.amplifier;

import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.controller.TerritoryController;
import me.cedric.siegegame.controller.death.DeathManager;
import me.cedric.siegegame.events.PlayerTeamChangeEvent;
import me.cedric.siegegame.model.game.Module;
import me.cedric.siegegame.model.game.WorldGame;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.teams.Team;
import net.kyori.adventure.pointer.Pointered;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.md_5.bungee.api.ChatColor;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Color;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class AmplifierManager implements Module, Listener {

    private final Random random = new Random();
    private SiegeGamePlugin plugin;
    private WorldGame worldGame;
    private final Map<Team, Double> lmsTeamDamage = new HashMap<>();
    private final Set<UUID> lmsActivePlayers = new HashSet<>(); 
    private BukkitTask nextAmplifierTask = null;
    private AmplifierType queuedAmplifier = null;
// --- KING OF THE HILL AMPLIFIER VARIABLES (MOVED) ---
private static final int ZONE_RADIUS = 7;
private static final int ZONE_VERTICAL_RADIUS = 8;
private static final int ZONE_CLEARANCE_HEIGHT = 15;
private static final Material ZONE_BLOCK = Material.GOLD_BLOCK;
private static final Material ZONE_BORDER = Material.LIGHT_WEIGHTED_PRESSURE_PLATE;

private final Map<Location, Material> originalBlocks = new ConcurrentHashMap<>();
private final Map<Team, Integer> zoneKingsTeamScores = new ConcurrentHashMap<>();
private final Set<UUID> zoneKingsPlayersInZone = ConcurrentHashMap.newKeySet();

private Location zoneCenter;
private BukkitTask zoneUpdateTask;
private boolean zoneKingsResultsProcessed = false;
    public AmplifierType current = null;
    private long endTick = 0L;
    private BossBar bossBar;
    private static AmplifierManager instance;

    private final Set<AmplifierType> rotationPool = new HashSet<>();

    private Location pointsDropLoc;
    private BukkitTask pointsDropTimeout;

    private final Axaxa moonAmplifier;

    public AmplifierManager(SiegeGamePlugin plugin) {
        this.plugin = plugin;
        this.moonAmplifier = new Axaxa(plugin);
        this.plugin.getServer().getPluginManager().registerEvents(moonAmplifier, plugin);
    }

    @EventHandler
    public void onPlayerKill(PlayerDeathEvent event) {
        if (current == AmplifierType.SUDDEN_DEATH) {

            worldGame.updateAllScoreboards();
        }

        if (current == AmplifierType.LAST_MAN_STANDING) {
            Player victimPlayer = event.getEntity().getPlayer();

            if (victimPlayer != null) {
                UUID victimUUID = victimPlayer.getUniqueId();
                if (lmsActivePlayers.contains(victimUUID)) {
                    lmsActivePlayers.remove(victimUUID);


                }
            }




            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {


                if (current != AmplifierType.LAST_MAN_STANDING) {
                    return;
                }
                DeathManager dm = worldGame.getDeathManager();
                System.out.println(dm.getDeadPlayers());


                Set<Team> activeTeams = worldGame.getActivePlayers().stream()
                        .filter(GamePlayer::hasTeam) 
                        .map(GamePlayer::getTeam)    
                        .collect(Collectors.toSet()); 

                if (activeTeams.size() == 1) {


                    endAmplifier();

                    Team winningTeam = activeTeams.iterator().next();
                    Bukkit.broadcast(Component.text("Last Man Standing ended early! Only ", NamedTextColor.GRAY)
                            .append(Component.text(winningTeam.getName(), getNamedTextColor(winningTeam.getColor()), TextDecoration.BOLD))
                            .append(Component.text(" remains!", NamedTextColor.GRAY)));
                }
            }, 15L); 
        }
    }


@EventHandler 
 public void onPlayerLeave(PlayerQuitEvent event){

    if (current == AmplifierType.LAST_MAN_STANDING){
        Player victimPlayer = event.getPlayer(); 

        if (victimPlayer != null) {
            UUID victimUUID = victimPlayer.getUniqueId(); 

            if (lmsActivePlayers.contains(victimUUID)){
                lmsActivePlayers.remove(victimUUID);


            }

        }
        Set<Team> activeTeams = worldGame.getActivePlayers().stream()
                                  .filter(GamePlayer::hasTeam) 
                                  .map(GamePlayer::getTeam)    
                                  .collect(Collectors.toSet()); 

    if (activeTeams.size() == 1) {


        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (current == AmplifierType.LAST_MAN_STANDING) { 
                endAmplifier();

                Team winningTeam = activeTeams.iterator().next();
                Bukkit.broadcast(Component.text("Last Man Standing ended early! Only ", NamedTextColor.GRAY)
                                    .append(Component.text(winningTeam.getName(), getNamedTextColor(winningTeam.getColor()), TextDecoration.BOLD))
                                    .append(Component.text(" remains!", NamedTextColor.GRAY)));
            }
        }, 1L); 
    }

        } 

 }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        onPlayerJoin(event.getPlayer());
        if (current != null && current == AmplifierType.LAST_MAN_STANDING) {
            event.getPlayer().setHealth(0);
            event.getPlayer().sendMessage(ChatColor.RED + "You have been killed because Last Man Standing is active now.");
            
        }
    }

    public void onPlayerJoin(Player player) {
        if (current != null) {
            applyCurrentAmplifierToPlayer(player);
        }
        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (current != null) {

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (current != null) { 
                    Player player = event.getPlayer();

                    for (PotionEffect effect : player.getActivePotionEffects()) {
                        player.removePotionEffect(effect.getType());
                    }
                    applyCurrentAmplifierToPlayer(player);
                }
            }, 5L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) 
    public void onTeamChange(PlayerTeamChangeEvent event) {
        if (current == null) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (current == null) return; 
            
            GamePlayer gamePlayer = event.getPlayer();
            Player player = gamePlayer.getBukkitPlayer(); 
            if (player == null) return; 

            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            applyCurrentAmplifierToPlayer(player);
        }, 5L);
    }

    @Override
    public void onStartGame(SiegeGamePlugin plugin, WorldGame worldGame) {
        if (current != null) {
        endAmplifier();
        }
        this.plugin = plugin;
        this.worldGame = worldGame;
        instance = this;
   
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        rotationPool.clear();
        rotationPool.addAll(Arrays.asList(AmplifierType.values()));

        worldGame.getTeams().forEach(team -> lmsTeamDamage.put(team, 0.0));
       
        scheduleNext();
    }

    @Override
    public void onEndGame(SiegeGamePlugin plugin, WorldGame worldGame) {

        HandlerList.unregisterAll(this);

        if (current != null) {
                plugin.getLogger().info("AmplifierManager: Forcing endAmplifier() during onEndGame for " + current.name());
                endAmplifier();
            }

            if (bossBar != null) {
                bossBar.removeAll();
                bossBar.setVisible(false);
                bossBar = null;
            }
            
            current = null;


        if (nextAmplifierTask != null) {
            try {
                nextAmplifierTask.cancel();
            } catch (Exception ignored) {}
            nextAmplifierTask = null;
        }
        queuedAmplifier = null;
        originalBlocks.clear();
        zoneKingsTeamScores.clear();
        zoneKingsPlayersInZone.clear();
        zoneKingsResultsProcessed = false;
        if (zoneUpdateTask != null) {
            zoneUpdateTask.cancel();
            zoneUpdateTask = null;
        }
        rotationPool.clear();
        lmsTeamDamage.clear();
        lmsActivePlayers.clear(); 
        endTick = 0;

        instance = null;
    }

    private void scheduleNext() {

        if (nextAmplifierTask != null) {
            try {
                nextAmplifierTask.cancel();
            } catch (Exception ignored) {}
            nextAmplifierTask = null;
        }

        if (plugin == null || !plugin.isEnabled() || worldGame == null) {
            return;
        }

        if (queuedAmplifier != null) {
            AmplifierType next = queuedAmplifier;
            queuedAmplifier = null;
            startAmplifier(next);
            return;
        }

        if (current == null) {

            int delayMinutes = 5; 
            long ticks = delayMinutes * 60L * 20L;
            
            try {
                nextAmplifierTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {

                    if (plugin != null && plugin.isEnabled() && worldGame != null && current == null) {
                        startRandomAmplifier();
                    }
                }, ticks);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to schedule next amplifier: " + e.getMessage());
            }
        }
    }

    private void startRandomAmplifier() {
        if (current != null) {

            scheduleNext();
            return;
        }
        
        if (rotationPool.isEmpty())
            rotationPool.addAll(Arrays.asList(AmplifierType.values()));

        List<AmplifierType> available = new ArrayList<>(rotationPool);
        AmplifierType chosen = available.get(random.nextInt(available.size()));
        rotationPool.remove(chosen);
        startAmplifier(chosen);
    }

    private void startAmplifier(AmplifierType type) {
        if (current != null) {

            queuedAmplifier = type;
            return;
        }
        
        current = type;

        if (type == AmplifierType.LAST_MAN_STANDING || type == AmplifierType.SUDDEN_DEATH) {
            endTick = Bukkit.getCurrentTick() + 120 * 20L; 
        } else if (type == AmplifierType.NOBODY_SAFE) {
            endTick = Bukkit.getCurrentTick() + 15 * 20L; 
        } else if (type == AmplifierType.KING_OF_THE_HILL) {
            endTick = Bukkit.getCurrentTick() + 180 * 20L;
            endTick = Bukkit.getCurrentTick() + 180 * 20L;
            plugin.getLogger().info("AmplifierManager: Starting KING OF THE HILL. Initializing zone.");
            removeZone(); // Call removeZone() defensively here to clear originalBlocks
            createZone(); // Directly create the zone
            startZoneTask(); // Start the scoring task
            zoneKingsResultsProcessed = false; // Reset flag for new cycle
            zoneKingsTeamScores.clear(); // Reset KING OF THE HILL specific scores
            zoneKingsPlayersInZone.clear(); // Clear players in zone setf
        } else if (type == AmplifierType.WHO_IS_WHO) {
            endTick = Bukkit.getCurrentTick() + 30 * 20L;
        } else {
                    endTick = Bukkit.getCurrentTick() + 60 * 20L; 
        }
        System.out.println("[DEBUG] AmplifierManager: Attempting to call AmplifierStartEvent for " + type.name() + " with duration " + (endTick - Bukkit.getCurrentTick()) + " ticks.");

        Bukkit.getPluginManager().callEvent(new AmplifierStartEvent(type, endTick - Bukkit.getCurrentTick()));
        System.out.println("[DEBUG] AmplifierManager: AmplifierStartEvent called for " + type.name());

        if (type == AmplifierType.LAST_MAN_STANDING) {
            lmsTeamDamage.clear();
            worldGame.getTeams().forEach(team -> lmsTeamDamage.put(team, 0.0));
        }

        switch (type) {
            case NOBODY_SAFE -> applyNoOneIsSafe(true);
            case LAST_MAN_STANDING -> applyLastManStanding(true);
            case SUDDEN_DEATH -> {}
            case WE_SHARPER_NOW -> applySharperNow(true);
            case WHO_IS_WHO -> applyCantSee(true);
            case WE_FASTER_NOW -> applyFasterNow(true);
            case MOON -> applyMoon(true);
            case KING_OF_THE_HILL -> {}
        }



        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(
                    Component.text(type.getTitle(), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(type.getSubtitle(), NamedTextColor.YELLOW)
            ));
            p.playSound(p.getLocation(), type.getSound(), 1f, 1f);
        }

        bossBar = Bukkit.createBossBar(type.getTitle(), BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        bossBar.setProgress(1.0);
        
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (current == null) {
                    cancel();
                    return;
                }
                long remaining = endTick - Bukkit.getCurrentTick();
                if (remaining <= 0) {
                    endAmplifier();
                    cancel();
                    return;
                }

                long totalDurationTicks;
                if (current == AmplifierType.LAST_MAN_STANDING || current == AmplifierType.SUDDEN_DEATH) {
                    totalDurationTicks = 120 * 20L;  
                } else if (current == AmplifierType.WHO_IS_WHO) {
                    totalDurationTicks = 30 * 20L; 
                } else if (current == AmplifierType.NOBODY_SAFE) {
                    totalDurationTicks = 15 * 20L;   
                } else if (current == AmplifierType.KING_OF_THE_HILL) {
                    totalDurationTicks = 180 * 20L;
                } else {
                    totalDurationTicks = 60 * 20L;
                }
                double progress = Math.min(1.0, (double)remaining / totalDurationTicks);
                bossBar.setProgress(progress);

                long seconds = remaining / 20;
                String timeString = String.format("%02d:%02d", seconds / 60, seconds % 60);
                bossBar.setTitle(current.getTitle() + " - " + timeString);
            }
        }.runTaskTimer(plugin, 0, 20);
    }


    public AmplifierType getCurrent(){
        return current;
    }

    private void startLastManStanding(AmplifierType type){
        endTick = Bukkit.getCurrentTick() + 120 * 20L;
        switch (type) {
            case LAST_MAN_STANDING -> applyLastManStanding(true);
            default -> {}
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(
                    Component.text(type.getTitle(), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(type.getSubtitle(), NamedTextColor.YELLOW)
            ));
            p.playSound(p.getLocation(), type.getSound(), 1f, 1f);
        }

        bossBar = Bukkit.createBossBar(type.getTitle(), BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        bossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (current == null) {
                    cancel();
                    return;
                }
                long remaining = endTick - Bukkit.getCurrentTick();
                if (remaining <= 0) {
                    endAmplifier();
                    cancel();
                    return;
                }

                long totalDurationTicks = 120 * 20L;
                    
                double progress = Math.min(1.0, (double)remaining / totalDurationTicks);
                bossBar.setProgress(progress);

                long seconds = remaining / 20;
                String timeString = String.format("%02d:%02d", seconds / 60, seconds % 60);
                bossBar.setTitle(current.getTitle() + " - " + timeString);
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private void startSuddenDeath(AmplifierType type){
        endTick = Bukkit.getCurrentTick() + 120 * 20L;
        switch (type) {
            case SUDDEN_DEATH -> {}
            default -> {}
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(
                    Component.text(type.getTitle(), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(type.getSubtitle(), NamedTextColor.YELLOW)
            ));
            p.playSound(p.getLocation(), type.getSound(), 1f, 1f);
        }

        bossBar = Bukkit.createBossBar(type.getTitle(), BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        bossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (current == null) {
                    cancel();
                    return;
                }
                long remaining = endTick - Bukkit.getCurrentTick();
                if (remaining <= 0) {
                    endAmplifier();
                    cancel();
                    return;
                }

                long totalDurationTicks = 120 * 20L;
                    
                double progress = Math.min(1.0, (double)remaining / totalDurationTicks);
                bossBar.setProgress(progress);

                long seconds = remaining / 20;
                String timeString = String.format("%02d:%02d", seconds / 60, seconds % 60);
                bossBar.setTitle(current.getTitle() + " - " + timeString);
            }
        }.runTaskTimer(plugin, 0, 20);
    }
@EventHandler
 private void onLmsStart(AmplifierStartEvent e){
   if ( e.getAmplifierType() == AmplifierType.LAST_MAN_STANDING){
    lmsActivePlayers.clear();
    lmsTeamDamage.clear();
    worldGame.getTeams().forEach(team -> lmsTeamDamage.put(team, 0.0));
   }
 }
    private void startWeSharperNow(AmplifierType type){
        endTick = Bukkit.getCurrentTick() + 60 * 20L;
        switch (type) {
            case WE_SHARPER_NOW -> applySharperNow(true);
            default -> {}
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(
                    Component.text(type.getTitle(), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(type.getSubtitle(), NamedTextColor.YELLOW)
            ));
            p.playSound(p.getLocation(), type.getSound(), 1f, 1f);
        }

        bossBar = Bukkit.createBossBar(type.getTitle(), BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        bossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (current == null) {
                    cancel();
                    return;
                }
                long remaining = endTick - Bukkit.getCurrentTick();
                if (remaining <= 0) {
                    endAmplifier();
                    cancel();
                    return;
                }

                long totalDurationTicks = 60 * 20L;
                    
                double progress = Math.min(1.0, (double)remaining / totalDurationTicks);
                bossBar.setProgress(progress);

                long seconds = remaining / 20;
                String timeString = String.format("%02d:%02d", seconds / 60, seconds % 60);
                bossBar.setTitle(current.getTitle() + " - " + timeString);
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private void startNoOneIsSafe(AmplifierType type){
        endTick = Bukkit.getCurrentTick() + 60 * 20L;
        switch (type) {
            case NOBODY_SAFE -> applyNoOneIsSafe(true);
            default -> {}
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(
                    Component.text(type.getTitle(), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(type.getSubtitle(), NamedTextColor.YELLOW)
            ));
            p.playSound(p.getLocation(), type.getSound(), 1f, 1f);
        }

        bossBar = Bukkit.createBossBar(type.getTitle(), BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        bossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (current == null) {
                    cancel();
                    return;
                }
                long remaining = endTick - Bukkit.getCurrentTick();
                if (remaining <= 0) {
                    endAmplifier();
                    cancel();
                    return;
                }

                long totalDurationTicks = 60 * 20L;
                    
                double progress = Math.min(1.0, (double)remaining / totalDurationTicks);
                bossBar.setProgress(progress);

                long seconds = remaining / 20;
                String timeString = String.format("%02d:%02d", seconds / 60, seconds % 60);
                bossBar.setTitle(current.getTitle() + " - " + timeString);
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    public void endAmplifier() {
        Bukkit.getPluginManager().callEvent(new AmplifierEndEvent(current));
        if (current == AmplifierType.KING_OF_THE_HILL) {
            plugin.getLogger().info("AmplifierManager: Ending KING OF THE HILL. Processing results and removing zone.");
            if (zoneUpdateTask != null) {
                zoneUpdateTask.cancel();
                zoneUpdateTask = null;
            }
            if (!zoneKingsResultsProcessed) {
                broadcastFinalZoneKingsScores(worldGame);
                zoneKingsResultsProcessed = true;
            }
            removeZone();
            zoneKingsPlayersInZone.clear();
            zoneKingsTeamScores.clear();
        }

        switch (current) {
            case NOBODY_SAFE -> applyNoOneIsSafe(false);
            case LAST_MAN_STANDING -> applyLastManStanding(false);
            case SUDDEN_DEATH -> {}
            case WE_SHARPER_NOW -> applySharperNow(false);
            case WE_FASTER_NOW -> applyFasterNow(false);
            case WHO_IS_WHO -> applyCantSee(false);
            case MOON -> {}
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(Component.text(current.getTitle() + " ended", NamedTextColor.GRAY));
        }

        if (current == AmplifierType.LAST_MAN_STANDING) {
            lmsTeamDamage.clear(); 
        }

        current = null;
        endTick = 0;
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        scheduleNext();
    }

    private void cancelCurrent() {
        current = null;
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
    }

    private void applyNoOneIsSafe(boolean enable) {
        TerritoryController.setGlobalBypass(enable);
    }
// --- KING OF THE HILL AMPLIFIER METHODS (MOVED AND ADAPTED) ---

private void startZoneTask() {
    if (zoneUpdateTask != null) {
        zoneUpdateTask.cancel();
    }
    zoneUpdateTask = new BukkitRunnable() {
        @Override
        public void run() {
            if (current != AmplifierType.KING_OF_THE_HILL || worldGame == null) {
                plugin.getLogger().info("AmplifierManager: Zone update task stopping due to inactive state or null game (KING OF THE HILL).");
                this.cancel();
                zoneUpdateTask = null; // Clean up task reference
                return;
            }

            updateZonePresence();
            sendActionBarUpdates();
        }
    }.runTaskTimer(plugin, 0L, 20L); // 20L = 1 second
    plugin.getLogger().info("AmplifierManager: KING OF THE HILL update task started.");
}

/**
 * Checks which players are in the zone, sends them messages, and awards points.
 * Modified to account for player subtraction when multiple teams are in the zone.
 */
private void updateZonePresence() {
    Set<UUID> currentPlayersInZone = new HashSet<>();
    Map<Team, Integer> playersCountPerTeamInZone = new HashMap<>(); 
    
    List<Player> onlinePlayers = worldGame.getPlayers().stream()
            .map(GamePlayer::getBukkitPlayer)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    for (Player player : onlinePlayers) {
        if (isInZone(player.getLocation())) {
            currentPlayersInZone.add(player.getUniqueId());
            GamePlayer gamePlayer = worldGame.getPlayer(player.getUniqueId());
            if (gamePlayer != null && gamePlayer.getTeam() != null && !gamePlayer.isDead()) {
                playersCountPerTeamInZone.merge(gamePlayer.getTeam(), 1, Integer::sum); 
            }
            if (zoneKingsPlayersInZone.add(player.getUniqueId())) {
                player.sendMessage(ChatColor.GOLD + "You entered the capture zone!");
            }
        }
    ;}

    new HashSet<>(zoneKingsPlayersInZone).forEach(uuid -> {
        if (!currentPlayersInZone.contains(uuid)) {
            zoneKingsPlayersInZone.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(ChatColor.GRAY + "You left the capture zone!");
            }
        }
    });

    if (!playersCountPerTeamInZone.isEmpty()) {
        List<Map.Entry<Team, Integer>> sortedTeams = playersCountPerTeamInZone.entrySet().stream()
            .sorted(Map.Entry.<Team, Integer>comparingByValue().reversed())
            .collect(Collectors.toList());

        Team leadingTeam = sortedTeams.get(0).getKey();
        int leadingTeamPlayers = sortedTeams.get(0).getValue();

        int pointsToAward = 0;

        if (sortedTeams.size() == 1) {
            pointsToAward = leadingTeamPlayers;
        } else {
            Team secondTeam = sortedTeams.get(1).getKey();
            int secondTeamPlayers = sortedTeams.get(1).getValue();

            if (leadingTeamPlayers > secondTeamPlayers) {
                pointsToAward = leadingTeamPlayers - secondTeamPlayers;
            }
        }

        if (pointsToAward > 0) {
            zoneKingsTeamScores.merge(leadingTeam, pointsToAward, Integer::sum);
        }
    }
}

/**
 * Sends action bar updates to all players in the game, showing team scores.
 */
private void sendActionBarUpdates() {
    StringBuilder actionBarText = new StringBuilder();
    
    List<Team> sortedTeams = worldGame.getTeams().stream()
        .sorted(Comparator.comparing(team -> zoneKingsTeamScores.getOrDefault(team, 0), Comparator.reverseOrder()))
        .collect(Collectors.toList());

    for (int i = 0; i < sortedTeams.size(); i++) {
        Team team = sortedTeams.get(i);
        int totalSeconds = zoneKingsTeamScores.getOrDefault(team, 0);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String formattedTime = String.format("%d:%02d", minutes, seconds);

        actionBarText.append(getChatColor(team.getColor()))
                     .append(team.getName())
                     .append(": ")
                     .append(ChatColor.WHITE)
                     .append(formattedTime);

        if (i < sortedTeams.size() - 1) {
            actionBarText.append(ChatColor.GRAY).append(" | ");
        }
    }

    for (GamePlayer gamePlayer : worldGame.getPlayers()) {
        Player player = gamePlayer.getBukkitPlayer();
        if (player != null && player.isOnline()) {
            player.sendActionBar(Component.text(actionBarText.toString()));
        }
    }
}
    private org.bukkit.ChatColor getChatColor(java.awt.Color color) {
        NamedTextColor named = getNamedTextColor(color);
        if (named == NamedTextColor.BLACK) return org.bukkit.ChatColor.BLACK;
        if (named == NamedTextColor.DARK_BLUE) return org.bukkit.ChatColor.DARK_BLUE;
        if (named == NamedTextColor.DARK_GREEN) return org.bukkit.ChatColor.DARK_GREEN;
        if (named == NamedTextColor.DARK_AQUA) return org.bukkit.ChatColor.DARK_AQUA;
        if (named == NamedTextColor.DARK_RED) return org.bukkit.ChatColor.DARK_RED;
        if (named == NamedTextColor.DARK_PURPLE) return org.bukkit.ChatColor.DARK_PURPLE;
        if (named == NamedTextColor.GOLD) return org.bukkit.ChatColor.GOLD;
        if (named == NamedTextColor.GRAY) return org.bukkit.ChatColor.GRAY;
        if (named == NamedTextColor.DARK_GRAY) return org.bukkit.ChatColor.DARK_GRAY;
        if (named == NamedTextColor.BLUE) return org.bukkit.ChatColor.BLUE;
        if (named == NamedTextColor.GREEN) return org.bukkit.ChatColor.GREEN;
        if (named == NamedTextColor.AQUA) return org.bukkit.ChatColor.AQUA;
        if (named == NamedTextColor.RED) return org.bukkit.ChatColor.RED;
        if (named == NamedTextColor.BLACK) return org.bukkit.ChatColor.BLACK; // Fallback to black for unknown NamedTextColor
        if (named == NamedTextColor.LIGHT_PURPLE) return org.bukkit.ChatColor.LIGHT_PURPLE;
        if (named == NamedTextColor.YELLOW) return org.bukkit.ChatColor.YELLOW;
        return org.bukkit.ChatColor.WHITE;
    }

// --- Zone Creation and Management ---

private void createZone() {
    if (worldGame == null || worldGame.getTeams().isEmpty()) {
        plugin.getLogger().warning("AmplifierManager: Cannot create KING OF THE HILL zone, worldGame is null or no teams.");
        return;
    }

    List<Team> teams = new ArrayList<>(worldGame.getTeams());
    if (teams.size() < 2) {
        plugin.getLogger().warning("AmplifierManager: Not enough teams to determine zone center for KING OF THE HILL.");
        return;
    }

    Location loc1 = teams.get(0).getSafeSpawn();
    Location loc2 = teams.get(1).getSafeSpawn();
    if (loc1 == null || loc2 == null) {
        plugin.getLogger().warning("AmplifierManager: Team safe spawns are null, cannot determine zone center for KING OF THE HILL.");
        return;
    }

    double midX = (loc1.getX() + loc2.getX()) / 2;
    double midZ = (loc1.getZ() + loc2.getZ()) / 2;
    int safeZoneY = loc1.getBlockY();
    
    World world = loc1.getWorld();

    Chunk centerChunk = world.getChunkAt((int)midX, (int)midZ);
    if (!centerChunk.isLoaded()) {
        centerChunk.load();
    }
    int groundY = world.getHighestBlockAt((int) midX, (int) midZ).getY();

    int finalY = (groundY < safeZoneY && groundY > world.getMinHeight()) ? groundY : safeZoneY;
    
    this.zoneCenter = new Location(world, midX, finalY, midZ);
    
    int centerX = zoneCenter.getBlockX(); // Define centerX, centerY, centerZ
    int centerY = zoneCenter.getBlockY();
    int centerZ = zoneCenter.getBlockZ();

    originalBlocks.clear();
    plugin.getLogger().info("AmplifierManager: createZone() for KING OF THE HILL. originalBlocks cleared. Size: " + originalBlocks.size());

    Set<Chunk> chunksToLoad = new HashSet<>();
    for (int x = -ZONE_RADIUS; x <= ZONE_RADIUS; x++) {
        for (int z = -ZONE_RADIUS; z <= ZONE_RADIUS; z++) {
            chunksToLoad.add(world.getChunkAt(centerX + x, centerZ + z));
        }
    }

    plugin.getLogger().info("AmplifierManager: Loading " + chunksToLoad.size() + " chunks for KING OF THE HILL zone creation.");
    for (Chunk chunk : chunksToLoad) {
        if (!chunk.isLoaded()) {
            chunk.load(true);
        }
    }
    
    try {
        Thread.sleep(50); 
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    for (int x = -ZONE_RADIUS; x <= ZONE_RADIUS; x++) {
        for (int z = -ZONE_RADIUS; z <= ZONE_RADIUS; z++) {
            Location loc = new Location(world, centerX + x, centerY, centerZ + z);
            Block block = loc.getBlock();
            originalBlocks.put(loc, block.getType());
            if (Math.abs(x) == ZONE_RADIUS || Math.abs(z) == ZONE_RADIUS) {
                block.setType(ZONE_BORDER);
            } else {
                block.setType(ZONE_BLOCK);
            }
        }
    }
    
    for (int yOffset = 1; yOffset <= ZONE_CLEARANCE_HEIGHT; yOffset++) {
        for (int x = -ZONE_RADIUS; x <= ZONE_RADIUS; x++) {
            for (int z = -ZONE_RADIUS; z <= ZONE_RADIUS; z++) {
                Location locToClear = new Location(world, centerX + x, centerY + yOffset, centerZ + z);
                Block blockToClear = locToClear.getBlock();
                
                if (blockToClear.getChunk().isLoaded() && blockToClear.getType() != Material.AIR) { 
                    originalBlocks.put(locToClear, blockToClear.getType());
                    blockToClear.setType(Material.AIR);
                }
            }
        }
    }
    plugin.getLogger().info("AmplifierManager: KING OF THE HILL blocks created and original states saved. Total blocks: " + originalBlocks.size());
}

private void removeZone() {
    plugin.getLogger().info("AmplifierManager: removeZone() for KING OF THE HILL called. Blocks to restore: " + originalBlocks.size());
    List<Location> locationsToRestore = new ArrayList<>(originalBlocks.keySet());

    for (Location loc : locationsToRestore) {
        if (loc == null || loc.getWorld() == null) {
             plugin.getLogger().warning("AmplifierManager: Skipping restoration for null location or world during KING OF THE HILL removeZone.");
             continue;
        }
        if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            Material originalMaterial = originalBlocks.get(loc);
            if (originalMaterial != null) { 
                loc.getBlock().setType(originalMaterial);
            } else {
                plugin.getLogger().warning("AmplifierManager: No original material found for location " + loc.toVector().toString() + " during KING OF THE HILL removeZone. This should not happen.");
            }
        } else {
            plugin.getLogger().warning("AmplifierManager: Chunk for location " + loc.toVector().toString() + " not loaded during KING OF THE HILL removeZone. Block not restored. Attempting to load...");
            loc.getChunk().load(true); 
            Material originalMaterial = originalBlocks.get(loc);
            if (originalMaterial != null) {
                loc.getBlock().setType(originalMaterial);
                plugin.getLogger().info("AmplifierManager: Successfully loaded chunk and restored block at " + loc.toVector().toString() + " (KING OF THE HILL).");
            }
        }
    }
    originalBlocks.clear(); 
    plugin.getLogger().info("AmplifierManager: KING OF THE HILL blocks removal attempt finished. originalBlocks size after clear: " + originalBlocks.size());
}

private boolean isInZone(Location location) {
    if (zoneCenter == null || !location.getWorld().equals(zoneCenter.getWorld())) return false;
    boolean inXZ = location.getX() >= zoneCenter.getX() - ZONE_RADIUS && location.getX() <= zoneCenter.getX() + ZONE_RADIUS &&
                   location.getZ() >= zoneCenter.getZ() - ZONE_RADIUS && location.getZ() <= zoneCenter.getZ() + ZONE_RADIUS;
    boolean inY = location.getY() >= zoneCenter.getY() && location.getY() < zoneCenter.getY() + ZONE_VERTICAL_RADIUS;
    return inXZ && inY;
}

private boolean isZoneBlock(Location location) {
    Block block = location.getBlock();
    return originalBlocks.containsKey(location) && 
           (block.getType() == ZONE_BLOCK || block.getType() == ZONE_BORDER);
}

@EventHandler(ignoreCancelled = true)
public void onBlockBreak(BlockBreakEvent event) {
    if (current != AmplifierType.KING_OF_THE_HILL || !isZoneBlock(event.getBlock().getLocation())) return;
    event.setCancelled(true);
    event.getPlayer().sendMessage(ChatColor.RED + "You cannot break the capture zone blocks!");
}

public int getCurrentTimeSpentZoneKings(Team team) {
    return zoneKingsTeamScores.getOrDefault(team, 0);
}

private void broadcastFinalZoneKingsScores(WorldGame worldGame) {
    if (worldGame == null) return;
    Map.Entry<Team, Integer> winningEntry = zoneKingsTeamScores.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
    ComponentBuilder broadcast = Component.text().append(Component.text("\n\n")).append(Component.text("KING OF THE HILL - Game Over!", NamedTextColor.GOLD, TextDecoration.BOLD)).append(Component.text("\n\n"));
    zoneKingsTeamScores.entrySet().stream().sorted(Map.Entry.<Team, Integer>comparingByValue().reversed()).forEachOrdered(entry -> {
        Team team = entry.getKey();
        int score = entry.getValue();
        int minutes = score / 60;
        int seconds = score % 60;
        broadcast.append(Component.text("• ", NamedTextColor.GRAY)).append(Component.text(team.getName() + ": ", getNamedTextColor(team.getColor()))).append(Component.text(String.format("%d:%02d", minutes, seconds), NamedTextColor.WHITE)).append(Component.text("\n"));
    });
    if (winningEntry != null && winningEntry.getValue() > 0) {
        Team winningTeam = winningEntry.getKey();
        int winningScore = winningEntry.getValue();
        int minutes = winningScore / 60;
        int seconds = winningScore % 60;
        
        winningTeam.addPoints(3500);
        
        worldGame.updateAllScoreboards();
        broadcast.append(Component.text("\n", NamedTextColor.GOLD)).append(Component.text(winningTeam.getName() + " ", getNamedTextColor(winningTeam.getColor()), TextDecoration.BOLD)).append(Component.text("wins with ", NamedTextColor.GOLD)).append(Component.text(String.format("%d:%02d", minutes, seconds), NamedTextColor.YELLOW, TextDecoration.BOLD)).append(Component.text(" in the zone!", NamedTextColor.GOLD)).append(Component.text("\n")).append(Component.text("+", NamedTextColor.GREEN)).append(Component.text(" 3500 points awarded to ", NamedTextColor.GRAY)).append(Component.text(winningTeam.getName(), getNamedTextColor(winningTeam.getColor())));
    } else {
        broadcast.append(Component.text("\nNo winner - no team scored any time in the zone!", NamedTextColor.GRAY));
    }
    Bukkit.broadcast(broadcast.build());
}
    private void applyLastManStanding(boolean enable) {
        DeathManager dm = worldGame.getDeathManager();
        for (UUID deadPlayerId : new ArrayList<>(dm.getDeadPlayers())) {
            dm.revivePlayer(deadPlayerId);
        }
        dm.setRespawnsPaused(enable);
        if (!enable) {

            for (UUID deadPlayerId : new ArrayList<>(dm.getDeadPlayers())) {
                dm.revivePlayer(deadPlayerId);
            }

            Set<UUID> currentLmsActivePlayers = lmsActivePlayers; 

            Map<Team, Long> aliveLmsParticipants = currentLmsActivePlayers.stream()
                    .map(uuid -> worldGame.getPlayer(uuid)) 
                    .filter(Objects::nonNull) 
                    .filter(GamePlayer::hasTeam) 
                    .collect(Collectors.groupingBy(GamePlayer::getTeam, Collectors.counting())); 

            if (aliveLmsParticipants.isEmpty()) {
                Bukkit.broadcast(Component.text("Last Man Standing ended. No active participants found to declare a winner.", NamedTextColor.GRAY));
                return; 
            }

            long maxAlivePlayers = aliveLmsParticipants.values().stream()
                    .mapToLong(v -> v) 
                    .max() 
                    .orElse(0); 

            List<Team> contenders = aliveLmsParticipants.entrySet().stream()
                    .filter(e -> e.getValue() == maxAlivePlayers) 
                    .map(Map.Entry::getKey) 
                    .toList(); 

            Team finalWinner = null;
            if (contenders.size() == 1) {

                finalWinner = contenders.get(0);
            } else {


                finalWinner = contenders.stream()
                        .max(Comparator.comparingDouble(team -> lmsTeamDamage.getOrDefault(team, 0.0)))
                        .orElse(null); 
            }

            if (finalWinner != null) {
                awardLms(finalWinner, 2500); 
                Bukkit.broadcast(Component.text("Last Man Standing ended! ", NamedTextColor.GRAY)
                        .append(Component.text(finalWinner.getName(), getNamedTextColor(finalWinner.getColor()), TextDecoration.BOLD))
                        .append(Component.text(" wins!", NamedTextColor.GRAY)));
            } else {

                Bukkit.broadcast(Component.text("Last Man Standing ended in a multi-team tie. No clear winner found, no points awarded.", NamedTextColor.GRAY));
            }
            }
        }


 

    private NamedTextColor getNamedTextColor(Color color) {

        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float hue = hsb[0] * 360f;
        float saturation = hsb[1];
        float brightness = hsb[2];

        if (brightness < 0.2f) return NamedTextColor.BLACK;
        if (brightness > 0.9f && saturation < 0.2f) return NamedTextColor.WHITE;
        if (saturation < 0.2f) return NamedTextColor.GRAY;

        if (hue < 30) return NamedTextColor.RED; 
        if (hue < 60) return NamedTextColor.GOLD; 
        if (hue < 90) return NamedTextColor.YELLOW; 
        if (hue < 150) return NamedTextColor.GREEN; 
        if (hue < 210) return NamedTextColor.AQUA; 
        if (hue < 270) return NamedTextColor.BLUE; 
        if (hue < 330) return NamedTextColor.LIGHT_PURPLE; 
        return NamedTextColor.RED; 
    }

    private void awardLms(Team winner, int pts) {
        winner.addPoints(pts);
        worldGame.updateAllScoreboards();

        Component[] messageRef = new Component[1];
        messageRef[0] = Component.text("\n\n")
                .append(Component.text("=== LAST MAN STANDING RESULTS ===\n", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("Winner: ", NamedTextColor.GRAY))
                .append(Component.text(winner.getName(), getNamedTextColor(winner.getColor()), TextDecoration.BOLD))
                .append(Component.text(" earned " + pts + " points!\n\n", NamedTextColor.GOLD));

        List<GamePlayer> excludedPlayers = worldGame.getPlayers().stream()
                .filter(gp -> !gp.isDead() && gp.hasTeam() && !lmsActivePlayers.contains(gp.getUUID()))
                .toList();

        if (!excludedPlayers.isEmpty()) {
            messageRef[0] = messageRef[0].append(Component.text("Dead players / Excluded(no combat): ", NamedTextColor.GRAY));
            for (int i = 0; i < excludedPlayers.size(); i++) {
                if (i > 0) messageRef[0] = messageRef[0].append(Component.text(", ", NamedTextColor.GRAY));
                GamePlayer gp = excludedPlayers.get(i);
                messageRef[0] = messageRef[0].append(Component.text(gp.getBukkitPlayer().getName(), 
                    getNamedTextColor(gp.getTeam().getColor())));
            }
            messageRef[0] = messageRef[0].append(Component.text("\n"));
        }

        List<GamePlayer> countedPlayers = worldGame.getPlayers().stream()
                .filter(gp -> !gp.isDead() && gp.hasTeam() && lmsActivePlayers.contains(gp.getUUID()))
                .toList();

        if (!countedPlayers.isEmpty()) {
            messageRef[0] = messageRef[0].append(Component.text("Counted (combat participants): ", NamedTextColor.GRAY));
            for (int i = 0; i < countedPlayers.size(); i++) {
                if (i > 0) messageRef[0] = messageRef[0].append(Component.text(", ", NamedTextColor.GRAY));
                GamePlayer gp = countedPlayers.get(i);
                messageRef[0] = messageRef[0].append(Component.text(gp.getBukkitPlayer().getName(), 
                    getNamedTextColor(gp.getTeam().getColor())));
            }
            messageRef[0] = messageRef[0].append(Component.text("\n"));
        }

        Map<Team, Long> activeTeams = worldGame.getTeams().stream()
                .filter(team -> lmsTeamDamage.getOrDefault(team, 0.0) > 0)
                .collect(Collectors.toMap(team -> team, team -> lmsTeamDamage.getOrDefault(team, 0.0).longValue()));

        if (activeTeams.size() > 1) {
            messageRef[0] = messageRef[0].append(Component.text("\nTeam Damage Breakdown:\n", NamedTextColor.GOLD));
            activeTeams.entrySet().stream()
                    .sorted(Map.Entry.<Team, Long>comparingByValue().reversed())
                    .forEachOrdered(entry -> {
                        Team team = entry.getKey();
                        double damage = lmsTeamDamage.getOrDefault(team, 0.0);
                        messageRef[0] = messageRef[0].append(Component.text(String.format("%s: ", team.getName()), 
                                getNamedTextColor(team.getColor())))
                                .append(Component.text(String.format("%.1f damage\n", damage), NamedTextColor.GRAY));
                    });
        }

        messageRef[0] = messageRef[0].append(Component.text("=============================", NamedTextColor.GOLD));

        Bukkit.broadcast(messageRef[0]);
    }

    private void applyBunnyMode(boolean enable){
        for(Player p: Bukkit.getOnlinePlayers()){
            if(enable){
                int durationTicks = (int)(endTick - Bukkit.getCurrentTick());
                p.addPotionEffect(new PotionEffect(
                    PotionEffectType.JUMP_BOOST, 
                    durationTicks, 
                    1, 
                    true, 
                    false
                ));
            } else {
                p.removePotionEffect(PotionEffectType.JUMP_BOOST);
            }
        }
    }

    private void applySharperNow(boolean enable){
        for(Player p: Bukkit.getOnlinePlayers()){
            if(enable){
                int durationTicks = (int)(endTick - Bukkit.getCurrentTick());
                p.addPotionEffect(new PotionEffect(
                    PotionEffectType.STRENGTH,
                    durationTicks,
                    0,
                    true,
                    false
                ));
            } else {
                p.removePotionEffect(PotionEffectType.STRENGTH);
            }
        }
    }
    private void applyFasterNow(boolean enable){
        for(Player p: Bukkit.getOnlinePlayers()){
            if(enable){
                int durationTicks = (int)(endTick - Bukkit.getCurrentTick());
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED,
                        durationTicks,
                        2,
                        true,
                        false
                ));
            } else {
                p.removePotionEffect(PotionEffectType.SPEED);
            }
        }
    }
    private void applyCantSee(boolean enable){
        for(Player p: Bukkit.getOnlinePlayers()){
            if(enable){
                int durationTicks = (int)(endTick - Bukkit.getCurrentTick());
                p.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY,
                    durationTicks,
                    0,
                    true,
                    false
                ));
            } else {
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
        }
    }
    private void applyMoon(boolean enable){
        for(Player p: Bukkit.getOnlinePlayers()){
            if(enable){
                int durationTicks = (int)(endTick - Bukkit.getCurrentTick());
                p.addPotionEffect(new PotionEffect(
                    PotionEffectType.JUMP_BOOST,
                    durationTicks,
                    0,
                    true,
                    false
                ));
                p.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW_FALLING,
                    durationTicks,
                    1,
                    true,
                    false
                ));
            } else {
                p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                p.removePotionEffect(PotionEffectType.SLOW_FALLING);
            }
        }
    }
    private void applyCurrentAmplifierToPlayer(Player player) {
        if (current == null) return;
        
        int durationTicks = (int)(endTick - Bukkit.getCurrentTick());
        if (durationTicks <= 0) return;

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        
        switch (current) {
            case MOON -> {
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.JUMP_BOOST,
                    durationTicks,
                    0,
                    true,
                    false
            )); 
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_FALLING,
                durationTicks,
                1,
                true,
                false
            ));}
            case WE_SHARPER_NOW -> 
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.STRENGTH,
                    durationTicks,
                    0,
                    true,
                    false
                ));
            case WHO_IS_WHO -> 
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY,
                    durationTicks,
                    0,
                    true,
                    false
                ));
            case WE_FASTER_NOW ->
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.INVISIBILITY,
                            durationTicks,
                            0,
                            true,
                            false
                    ));
            case NOBODY_SAFE -> {} 
            case LAST_MAN_STANDING -> {} 
            case SUDDEN_DEATH -> {} 
            case KING_OF_THE_HILL -> {}
        }
    }

    private void applyPointsDrop(){
        World world = Bukkit.getWorlds().get(0);
        int radius = 200;
        int x = ThreadLocalRandom.current().nextInt(-radius,radius);
        int z = ThreadLocalRandom.current().nextInt(-radius,radius);
        int y = world.getHighestBlockYAt(x,z)+1;
        pointsDropLoc = new Location(world,x,y,z);

        Block block = pointsDropLoc.getBlock();
        block.setType(Material.DIAMOND_BLOCK);

        Bukkit.broadcast(Component.text("A diamond has dropped! First team to mine it gets 1000 points!", NamedTextColor.AQUA));

        pointsDropTimeout = Bukkit.getScheduler().runTaskLater(plugin, this::endAmplifier, 60*20L);
    }

    private void clearPointsDrop(){
        if(pointsDropTimeout!=null){
            pointsDropTimeout.cancel();
            pointsDropTimeout=null;
        }
        if(pointsDropLoc!=null){
            Block b = pointsDropLoc.getBlock();
            if(b.getType()==Material.DIAMOND_BLOCK){
                b.setType(Material.AIR);
            }
            pointsDropLoc=null;
        }
    }














    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (current != AmplifierType.LAST_MAN_STANDING) return;
        
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        
        if (!(victim instanceof Player) || !(damager instanceof Player)) return;
        
        GamePlayer damagerGP = worldGame.getPlayer(damager.getUniqueId());
        GamePlayer victimGP = worldGame.getPlayer(victim.getUniqueId());
        
        if (damagerGP == null || victimGP == null || !damagerGP.hasTeam() || !victimGP.hasTeam())
            return;

        if (!damagerGP.isDead()) {
            lmsActivePlayers.add(damager.getUniqueId());

            Team damagerTeam = damagerGP.getTeam();
            double damage = event.getFinalDamage();
            lmsTeamDamage.merge(damagerTeam, damage, Double::sum);
        }

        if (!victimGP.isDead()) {
            lmsActivePlayers.add(victim.getUniqueId());
        }
    }

    public boolean triggerManual(AmplifierType type){
        if(current!=null) {

            queuedAmplifier = type;
            return false;
        }
        
        if(!rotationPool.contains(type)) rotationPool.add(type); 
        rotationPool.remove(type);
        startAmplifier(type);
        return true;
    }

    public static boolean isActive(AmplifierType type){
        return instance!=null && instance.current==type;
    }

    public static AmplifierManager getInstance() {
        return instance;
    }

    public long getEndTick() {
        return endTick;
    }
}