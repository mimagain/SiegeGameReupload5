package me.cedric.siegegame.amplifier;

import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.model.game.WorldGame;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.teams.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ZoneKingsAmplifier implements Listener {

    // --- Zone Configuration ---
    private static final int ZONE_RADIUS = 7;
    private static final int ZONE_VERTICAL_RADIUS = 8;
    private static final int ZONE_CLEARANCE_HEIGHT = 15;
    private static final Material ZONE_BLOCK = Material.GOLD_BLOCK;
    private static final Material ZONE_BORDER = Material.LIGHT_WEIGHTED_PRESSURE_PLATE;

    private final SiegeGamePlugin plugin;
    // Use ConcurrentHashMap for originalBlocks as well, for better thread safety if any async operations ever touch it
    private final Map<Location, Material> originalBlocks = new ConcurrentHashMap<>();
    private final Map<Team, Integer> teamScores = new ConcurrentHashMap<>();
    private final Set<UUID> playersInZone = ConcurrentHashMap.newKeySet();

    private Location zoneCenter;
    private BukkitTask zoneUpdateTask;
    private boolean active = false;
    private boolean resultsProcessed = false;

    public ZoneKingsAmplifier(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    // --- Core Amplifier Events ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAmplifierStart(AmplifierStartEvent event) {
        if (event.getAmplifierType() != AmplifierType.KING_OF_THE_HILL) return;

        WorldGame worldGame = plugin.getGameManager().getCurrentMatch().getWorldGame();
        if (worldGame == null) {
            plugin.getLogger().warning("ZoneKingsAmplifier: AmplifierStartEvent triggered but current match world game is null.");
            return;
        }

        // --- CRITICAL RESET FOR NEW GAME/AMPLIFIER CYCLE ---
        // Clear all previous state before starting a new zone
        plugin.getLogger().info("ZoneKingsAmplifier: Resetting state for new amplifier start.");
        if (zoneUpdateTask != null) {
            zoneUpdateTask.cancel();
            zoneUpdateTask = null;
        }
        active = false; // Ensure previous run is marked inactive
        
        // Ensure any previous zone is removed BEFORE creating a new one
        // This handles cases where an AmplifierEndEvent might have been missed or delayed.
        removeZone(); // Will clear originalBlocks internally
        
        teamScores.clear();
        playersInZone.clear();
        resultsProcessed = false; // Reset the flag for a fresh start
        // --- END CRITICAL RESET ---

        List<Team> teams = new ArrayList<>(worldGame.getTeams());
        if (teams.size() < 2) {
            plugin.getLogger().warning("ZoneKingsAmplifier: Not enough teams to start KING OF THE HILL amplifier.");
            return;
        }

        Location loc1 = teams.get(0).getSafeSpawn();
        Location loc2 = teams.get(1).getSafeSpawn();
        if (loc1 == null || loc2 == null) {
            plugin.getLogger().warning("ZoneKingsAmplifier: Team safe spawns are null, cannot determine zone center.");
            return;
        }

        double midX = (loc1.getX() + loc2.getX()) / 2;
        double midZ = (loc1.getZ() + loc2.getZ()) / 2;
        int safeZoneY = loc1.getBlockY();
        
        World world = loc1.getWorld(); // Get world reference

        // Find the highest solid block at the calculated center
        // Ensure the chunk is loaded before trying to get highest block
        Chunk centerChunk = world.getChunkAt((int)midX, (int)midZ);
        if (!centerChunk.isLoaded()) {
            centerChunk.load(); // Load the chunk if not loaded
            // Give it a tiny moment if needed, though getHighestBlockAt usually forces load
        }
        int groundY = world.getHighestBlockAt((int) midX, (int) midZ).getY();

        int finalY = (groundY < safeZoneY && groundY > world.getMinHeight()) ? groundY : safeZoneY;
        
        this.zoneCenter = new Location(world, midX, finalY, midZ);

        createZone(); // This method now assumes originalBlocks is already cleared.
        startZoneTask();
        active = true; // Mark as active only after everything is set up
        plugin.getLogger().info("ZoneKingsAmplifier: Started at " + zoneCenter.toVector().toString());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAmplifierEnd(AmplifierEndEvent event) {
        if (event.getAmplifierType() != AmplifierType.KING_OF_THE_HILL) return;

        if (zoneUpdateTask != null) {
            zoneUpdateTask.cancel();
            zoneUpdateTask = null; // Important: nullify to prevent potential re-use or zombie tasks
        }
        active = false; // Mark as inactive immediately

        WorldGame worldGame = plugin.getGameManager().getCurrentMatch().getWorldGame();
        
        // Ensure this block runs only once for this specific end event
        if (!resultsProcessed) { 
            plugin.getLogger().info("ZoneKingsAmplifier: Processing end results for amplifier cycle.");
            broadcastFinalScores(worldGame);
            resultsProcessed = true; // Set flag after processing
        } else {
            plugin.getLogger().info("ZoneKingsAmplifier: End results already processed for this cycle, skipping final broadcast.");
        }

        // Cleanup actions, always attempt them regardless of resultsProcessed flag
        // This is crucial to ensure the world state is reverted
        removeZone(); // This will clear originalBlocks internally
        playersInZone.clear(); // Clear for the next run
        teamScores.clear(); // Clear for the next run
        
        plugin.getLogger().info("ZoneKingsAmplifier: Amplifier ended and cleaned up.");
    }
    
    private void startZoneTask() {
        // Cancel any existing task just in case
        if (zoneUpdateTask != null) {
            zoneUpdateTask.cancel();
        }
        zoneUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Defensive check: if conditions change during runtime
                if (!active || zoneCenter == null || plugin.getGameManager().getCurrentMatch() == null) {
                    plugin.getLogger().info("ZoneKingsAmplifier: Zone update task stopping due to inactive state or null game.");
                    this.cancel();
                    zoneUpdateTask = null; // Clean up task reference
                    return;
                }

                WorldGame worldGame = plugin.getGameManager().getCurrentMatch().getWorldGame();
                if (worldGame == null) {
                    plugin.getLogger().warning("ZoneKingsAmplifier: WorldGame is null during zone update task.");
                    // This could be a sign that the game ended without the AmplifierEndEvent firing correctly.
                    // Consider an emergency stop here if you want to be super defensive:
                    // new AmplifierEndEvent(AmplifierType.KING_OF_THE_HILL).callEvent(); 
                    return;
                }

                updateZonePresence(worldGame);
                sendActionBarUpdates(worldGame);
            }
        }.runTaskTimer(plugin, 0L, 20L); // 20L = 1 second
        plugin.getLogger().info("ZoneKingsAmplifier: Zone update task started.");
    }
    
    /**
     * Checks which players are in the zone, sends them messages, and awards points.
     * Modified to account for player subtraction when multiple teams are in the zone.
     */
    private void updateZonePresence(WorldGame worldGame) {
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
                if (gamePlayer != null && gamePlayer.getTeam() != null) {
                    playersCountPerTeamInZone.merge(gamePlayer.getTeam(), 1, Integer::sum); 
                }
                if (playersInZone.add(player.getUniqueId())) {
                    player.sendMessage(ChatColor.GOLD + "You entered the capture zone!");
                }
            }
        }

        new HashSet<>(playersInZone).forEach(uuid -> {
            if (!currentPlayersInZone.contains(uuid)) {
                playersInZone.remove(uuid);
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
                teamScores.merge(leadingTeam, pointsToAward, Integer::sum);
            }
        }
    }

    /**
     * Sends action bar updates to all players in the game, showing team scores.
     */
    private void sendActionBarUpdates(WorldGame worldGame) {
        StringBuilder actionBarText = new StringBuilder();
        
        List<Team> sortedTeams = worldGame.getTeams().stream()
            .sorted(Comparator.comparing(team -> teamScores.getOrDefault(team, 0), Comparator.reverseOrder()))
            .collect(Collectors.toList());

        for (int i = 0; i < sortedTeams.size(); i++) {
            Team team = sortedTeams.get(i);
            int totalSeconds = teamScores.getOrDefault(team, 0);
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            String formattedTime = String.format("%d:%02d", minutes, seconds);

            actionBarText.append(getChatColor(team.getColor()))
                         .append(team.getName())
                         .append(": ")
                         .append(ChatColor.WHITE)
                         .append(formattedTime);

            if (i < sortedTeams.size() - 1) {
                actionBarText.append(ChatColor.GRAY).append(" | "); // Separator between team scores
            }
        }

        for (GamePlayer gamePlayer : worldGame.getPlayers()) {
            Player player = gamePlayer.getBukkitPlayer();
            if (player != null && player.isOnline()) { // Added isOnline() check
                player.sendActionBar(Component.text(actionBarText.toString()));
            }
        }
    }

    // --- Zone Creation and Management ---
    
    private void createZone() {
        World world = zoneCenter.getWorld();
        int centerX = zoneCenter.getBlockX();
        int centerY = zoneCenter.getBlockY();
        int centerZ = zoneCenter.getBlockZ();

        originalBlocks.clear(); // Ensure it's clear before populating, this is crucial
        plugin.getLogger().info("ZoneKingsAmplifier: originalBlocks cleared before new zone creation. Size: " + originalBlocks.size());

        // Load all chunks that the zone will occupy before modifying blocks
        Set<Chunk> chunksToLoad = new HashSet<>();
        for (int x = -ZONE_RADIUS; x <= ZONE_RADIUS; x++) {
            for (int z = -ZONE_RADIUS; z <= ZONE_RADIUS; z++) {
                chunksToLoad.add(world.getChunkAt(centerX + x, centerZ + z));
            }
        }

        plugin.getLogger().info("ZoneKingsAmplifier: Loading " + chunksToLoad.size() + " chunks for zone creation.");
        for (Chunk chunk : chunksToLoad) {
            if (!chunk.isLoaded()) {
                chunk.load(true); // Load synchronously and generate if needed
            }
        }
        
        // Give the chunks a moment to fully load if it's a very large area or first time generation
        try {
            Thread.sleep(50); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Store and set base blocks
        for (int x = -ZONE_RADIUS; x <= ZONE_RADIUS; x++) {
            for (int z = -ZONE_RADIUS; z <= ZONE_RADIUS; z++) {
                Location loc = new Location(world, centerX + x, centerY, centerZ + z);
                Block block = loc.getBlock();
                originalBlocks.put(loc, block.getType()); // Store original type
                if (Math.abs(x) == ZONE_RADIUS || Math.abs(z) == ZONE_RADIUS) {
                    block.setType(ZONE_BORDER);
                } else {
                    block.setType(ZONE_BLOCK);
                }
            }
        }
        
        // Store and clear blocks above the zone
        for (int yOffset = 1; yOffset <= ZONE_CLEARANCE_HEIGHT; yOffset++) {
            for (int x = -ZONE_RADIUS; x <= ZONE_RADIUS; x++) {
                for (int z = -ZONE_RADIUS; z <= ZONE_RADIUS; z++) {
                    Location locToClear = new Location(world, centerX + x, centerY + yOffset, centerZ + z);
                    Block blockToClear = locToClear.getBlock();
                    
                    if (blockToClear.getChunk().isLoaded() && blockToClear.getType() != Material.AIR) { 
                        originalBlocks.put(locToClear, blockToClear.getType()); // Store original type
                        blockToClear.setType(Material.AIR);
                    }
                }
            }
        }
        plugin.getLogger().info("ZoneKingsAmplifier: Zone blocks created and original states saved. Total blocks: " + originalBlocks.size());
    }

    private void removeZone() {
        plugin.getLogger().info("ZoneKingsAmplifier: Attempting to remove zone. Blocks to restore: " + originalBlocks.size());
        List<Location> locationsToRestore = new ArrayList<>(originalBlocks.keySet());

        for (Location loc : locationsToRestore) {
            // Ensure the location is still valid and in a loaded chunk
            if (loc.getChunk().isLoaded()) {
                Material originalMaterial = originalBlocks.get(loc);
                if (originalMaterial != null) { 
                    loc.getBlock().setType(originalMaterial);
                } else {
                    plugin.getLogger().warning("ZoneKingsAmplifier: No original material found for location " + loc.toVector().toString() + " during zone removal. This should not happen.");
                }
            } else {
                plugin.getLogger().warning("ZoneKingsAmplifier: Chunk for location " + loc.toVector().toString() + " not loaded during zone removal. Block not restored. Consider pre-loading or persistent chunks.");
            }
        }
        originalBlocks.clear(); // Crucial: Clear the map after restoration attempts
        plugin.getLogger().info("ZoneKingsAmplifier: Zone blocks removal attempt finished. originalBlocks size after clear: " + originalBlocks.size());
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
        if (!active || !isZoneBlock(event.getBlock().getLocation())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "You cannot break the capture zone blocks!");
    }

    // --- Public API and Utility Methods ---

    public int getCurrentTimeSpent(Team team) {
        return teamScores.getOrDefault(team, 0);
    }

    private void broadcastFinalScores(WorldGame worldGame) {
        if (worldGame == null) return;
        Map.Entry<Team, Integer> winningEntry = teamScores.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        ComponentBuilder broadcast = Component.text().append(Component.text("\n\n")).append(Component.text("KING OF THE HILL - Game Over!", NamedTextColor.GOLD, TextDecoration.BOLD)).append(Component.text("\n\n"));
        teamScores.entrySet().stream().sorted(Map.Entry.<Team, Integer>comparingByValue().reversed()).forEachOrdered(entry -> {
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
            
            winningTeam.addPoints(5000); 
            
            worldGame.updateAllScoreboards(); // Assuming this updates main game scoreboards
            broadcast.append(Component.text("\n", NamedTextColor.GOLD)).append(Component.text(winningTeam.getName() + " ", getNamedTextColor(winningTeam.getColor()), TextDecoration.BOLD)).append(Component.text("wins with ", NamedTextColor.GOLD)).append(Component.text(String.format("%d:%02d", minutes, seconds), NamedTextColor.YELLOW, TextDecoration.BOLD)).append(Component.text(" in the zone!", NamedTextColor.GOLD)).append(Component.text("\n")).append(Component.text("+", NamedTextColor.GREEN)).append(Component.text(" 5000 points awarded to ", NamedTextColor.GRAY)).append(Component.text(winningTeam.getName(), getNamedTextColor(winningTeam.getColor())));
        } else {
            broadcast.append(Component.text("\nNo winner - no team scored any time in the zone!", NamedTextColor.GRAY));
        }
        Bukkit.broadcast(broadcast.build());
    }

    // --- Color Utility Methods ---

    private NamedTextColor getNamedTextColor(java.awt.Color color) {
        float[] hsb = java.awt.Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
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
    
    private ChatColor getChatColor(java.awt.Color color) {
        NamedTextColor named = getNamedTextColor(color);
        if (named == NamedTextColor.BLACK) return ChatColor.BLACK;
        if (named == NamedTextColor.DARK_BLUE) return ChatColor.DARK_BLUE;
        if (named == NamedTextColor.DARK_GREEN) return ChatColor.DARK_GREEN;
        if (named == NamedTextColor.DARK_AQUA) return ChatColor.DARK_AQUA;
        if (named == NamedTextColor.DARK_RED) return ChatColor.DARK_RED;
        if (named == NamedTextColor.DARK_PURPLE) return ChatColor.DARK_PURPLE;
        if (named == NamedTextColor.GOLD) return ChatColor.GOLD;
        if (named == NamedTextColor.GRAY) return ChatColor.GRAY;
        if (named == NamedTextColor.DARK_GRAY) return ChatColor.DARK_GRAY;
        if (named == NamedTextColor.BLUE) return ChatColor.BLUE;
        if (named == NamedTextColor.GREEN) return ChatColor.GREEN;
        if (named == NamedTextColor.AQUA) return ChatColor.AQUA;
        if (named == NamedTextColor.RED) return ChatColor.RED;
        if (named == NamedTextColor.BLACK) return ChatColor.BLACK; // Fallback to black for unknown NamedTextColor
        if (named == NamedTextColor.LIGHT_PURPLE) return ChatColor.LIGHT_PURPLE;
        if (named == NamedTextColor.YELLOW) return ChatColor.YELLOW;
        return ChatColor.WHITE;
    }
}