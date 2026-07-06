package me.cedric.siegegame.amplifier;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.model.game.WorldGame;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.teams.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handles the visual-only "glass head" effect when the MOON amplifier is active.
 * <p>
 * The effect is achieved purely client-side via {@link Player#sendEquipmentChange} –
 * no server inventory or equipment state is modified.
 */
public class Axaxa implements Listener {

    private static final ItemStack LEATHER_CHESTPLATE = createWhiteLeatherArmor(Material.LEATHER_CHESTPLATE);
    private static final ItemStack LEATHER_BOOTS = createWhiteLeatherArmor(Material.LEATHER_BOOTS);
    private static final ItemStack LEATHER_LEGGINGS = createWhiteLeatherArmor(Material.LEATHER_LEGGINGS);
    private static final ItemStack ALLY_GLASS = new ItemStack(Material.BLUE_STAINED_GLASS);
    private static final ItemStack ENEMY_GLASS = new ItemStack(Material.RED_STAINED_GLASS);
    private final SiegeGamePlugin plugin;
    private BukkitTask refreshTask = null;
    private final Map<Location, BlockData> originalBlocks = new ConcurrentHashMap<>();
    private static final int TRANSFORM_RADIUS = 40;
    private static final int BLOCKS_PER_TICK = 1000; // Process up to 1000 blocks per tick
    private static final int CHUNK_PROCESS_DELAY = 1; // Ticks between chunk processing
    private static final Map<Material, Material> BLOCK_REPLACEMENTS = new HashMap<>();
    private final Set<ChunkCoord> processedChunks = ConcurrentHashMap.newKeySet();
    private final Queue<ChunkCoord> chunkQueue = new ConcurrentLinkedQueue<>();
    private BukkitTask transformationTask = null;

    static {
        BLOCK_REPLACEMENTS.put(Material.GRASS_BLOCK, Material.END_STONE);
        BLOCK_REPLACEMENTS.put(Material.DIRT, Material.END_STONE);
        BLOCK_REPLACEMENTS.put(Material.STONE, Material.END_STONE);
        BLOCK_REPLACEMENTS.put(Material.COBBLESTONE, Material.END_STONE);
        BLOCK_REPLACEMENTS.put(Material.OAK_LOG, Material.BIRCH_LOG);
        BLOCK_REPLACEMENTS.put(Material.OAK_LEAVES, Material.BIRCH_LEAVES);
        BLOCK_REPLACEMENTS.put(Material.OAK_PLANKS, Material.BIRCH_PLANKS);
        BLOCK_REPLACEMENTS.put(Material.OAK_SLAB, Material.BIRCH_SLAB);
        BLOCK_REPLACEMENTS.put(Material.OAK_FENCE, Material.BIRCH_FENCE);
        BLOCK_REPLACEMENTS.put(Material.OAK_FENCE_GATE, Material.BIRCH_FENCE_GATE);
    }

    private static ItemStack createWhiteLeatherArmor(Material material) {
        ItemStack item = new ItemStack(material);
        org.bukkit.inventory.meta.LeatherArmorMeta meta = (org.bukkit.inventory.meta.LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(org.bukkit.Color.WHITE);
            item.setItemMeta(meta);
        }
        return item;
    }

    public Axaxa(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    //  Amplifier lifecycle
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAmplifierStart(AmplifierStartEvent event) {
        if (event.getAmplifierType() != AmplifierType.MOON) return;

        // Apply glass heads to all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyGlassHead(player);
        }

        // Start block transformation
        transformBlocksAroundSafezones();

        // Start refresh task to ensure effects persist
        startRefreshTask();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAmplifierEnd(AmplifierEndEvent event) {
        if (event.getAmplifierType() != AmplifierType.MOON) return;

        // Cancel any ongoing transformations
        if (transformationTask != null) {
            transformationTask.cancel();
            transformationTask = null;
        }

        // Revert all visual changes
        revertAllBlocks();
        revertHeadsForAll();
        stopRefreshTask();

        // Clean up
        processedChunks.clear();
        chunkQueue.clear();
    }

    // -------------------------------------------------------------------------
    //  Late join / respawn handling while MOON is active
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (AmplifierManager.isActive(AmplifierType.MOON)) {
            applyGlassHead(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!AmplifierManager.isActive(AmplifierType.MOON)) return;
        // One-tick delay to ensure the player entity is fully spawned on the client
        Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("SiegeGame"), () ->
                applyGlassHead(event.getPlayer()), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!AmplifierManager.isActive(AmplifierType.MOON)) {
            return;
        }
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            applyGlassHeadToAll();
        }
    }

    // -------------------------------------------------------------------------
    //  Helper methods
    // -------------------------------------------------------------------------

    private void applyGlassHeadToAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyGlassHead(player);
        }
    }

    private void applyGlassHead(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            WorldGame worldgame = plugin.getGameManager().getCurrentMatch().getWorldGame();
            GamePlayer viewerGamePlayer = worldgame.getPlayer(viewer.getUniqueId());
            GamePlayer targetGamePlayer = worldgame.getPlayer(target.getUniqueId());
            if (viewerGamePlayer.getTeam() == targetGamePlayer.getTeam()) {
                viewer.sendEquipmentChange(target, EquipmentSlot.HEAD, ALLY_GLASS);
            } else {
                viewer.sendEquipmentChange(target, EquipmentSlot.HEAD, ENEMY_GLASS);
            }
            viewer.sendEquipmentChange(target, EquipmentSlot.CHEST, LEATHER_CHESTPLATE);
            viewer.sendEquipmentChange(target, EquipmentSlot.LEGS, LEATHER_LEGGINGS);

            viewer.sendEquipmentChange(target, EquipmentSlot.FEET, LEATHER_BOOTS);

        }
    }

    private void revertHeadsForAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            revertHead(player);
        }
    }

    /**
     * Sends the player's real helmet back to viewers.
     */
    private void revertHead(Player target) {
        ItemStack actualHelmet = target.getInventory().getHelmet();
        ItemStack actualChestplate = target.getInventory().getChestplate();
        ItemStack actualLeggings = target.getInventory().getLeggings();
        ItemStack actualBoots = target.getInventory().getBoots();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.sendEquipmentChange(target, EquipmentSlot.HEAD, actualHelmet);
            viewer.sendEquipmentChange(target, EquipmentSlot.CHEST, actualChestplate);
            viewer.sendEquipmentChange(target, EquipmentSlot.LEGS, actualLeggings);
            viewer.sendEquipmentChange(target, EquipmentSlot.FEET, actualBoots);
        }
    }

    // -------------------------------------------------------------------------
    //  Block transformation
    // -------------------------------------------------------------------------

    /**
     * Transforms blocks around all team safe zones to create a moon-like atmosphere
     */
    private void transformBlocksAroundSafezones() {
        WorldGame worldGame = plugin.getGameManager().getCurrentMatch().getWorldGame();
        if (worldGame == null) return;

        // Clear previous state
        processedChunks.clear();
        chunkQueue.clear();

        // Queue chunks around each safe zone
        for (Team team : worldGame.getTeams()) {
            Location safeZone = team.getSafeSpawn();
            if (safeZone == null) continue;

            queueChunksInRadius(safeZone, TRANSFORM_RADIUS);
        }

        // Start processing chunks if not already running
        if (transformationTask == null || transformationTask.isCancelled()) {
            startChunkProcessing();
        }
    }

    private void queueChunksInRadius(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return;

        int chunkRadius = (radius + 15) >> 4;
        int centerX = center.getBlockX() >> 4;
        int centerZ = center.getBlockZ() >> 4;

        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                if (x * x + z * z > chunkRadius * chunkRadius) continue;
                ChunkCoord coord = new ChunkCoord(world, centerX + x, centerZ + z);
                if (processedChunks.add(coord)) {
                    chunkQueue.add(coord);
                }
            }
        }
    }

    private void startChunkProcessing() {
        transformationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (chunkQueue.isEmpty()) {
                    transformationTask = null;
                    cancel();
                    return;
                }

                ChunkCoord coord = chunkQueue.poll();
                if (coord != null) {
                    processChunk(coord);
                }

                // Schedule next chunk if there are more
                if (!chunkQueue.isEmpty()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            startChunkProcessing();
                        }
                    }.runTaskLater(plugin, CHUNK_PROCESS_DELAY);
                } else {
                    transformationTask = null;
                }
            }
        }.runTask(plugin);
    }

    private void processChunk(ChunkCoord coord) {
        Chunk chunk = coord.world.getChunkAt(coord.x, coord.z);
        int blocksProcessed = 0;

        for (int x = 0; x < 16 && blocksProcessed < BLOCKS_PER_TICK; x++) {
            for (int z = 0; z < 16 && blocksProcessed < BLOCKS_PER_TICK; z++) {
                // Only process every other block to reduce load
                if ((x + z) % 2 != 0) continue;

                int worldX = (chunk.getX() << 4) + x;
                int worldZ = (chunk.getZ() << 4) + z;

                // Get highest block at this x,z (more likely to be visible)
                Block block = chunk.getBlock(x, chunk.getWorld().getHighestBlockYAt(worldX, worldZ), z);

                // Check if block should be transformed
                if (BLOCK_REPLACEMENTS.containsKey(block.getType())) {
                    transformBlock(block);
                    blocksProcessed++;
                }
            }
        }
    }

    /**
     * Transforms a single block to its moon variant
     */
    private void transformBlock(Block block) {
        Material originalType = block.getType();
        Material newType = BLOCK_REPLACEMENTS.get(originalType);

        if (newType == null) return;

        // Store original block data if not already stored
        Location loc = block.getLocation();
        if (!originalBlocks.containsKey(loc)) {
            originalBlocks.put(loc, block.getBlockData().clone());
        }

        // Create the new block data
        BlockData newData = newType.createBlockData();

        // Copy relevant block data properties if possible
        BlockData oldData = block.getBlockData();

        // Handle slab types
        if (oldData instanceof Slab && newData instanceof Slab) {
            ((Slab) newData).setType(((Slab) oldData).getType());
        }

        // Handle fence gate directions
        if (oldData instanceof Gate && newData instanceof Gate) {
            Gate oldGate = (Gate) oldData;
            Gate newGate = (Gate) newData;
            newGate.setFacing(oldGate.getFacing());
            newGate.setOpen(oldGate.isOpen());
            newGate.setPowered(oldGate.isPowered());
            if (oldGate instanceof Bisected) {
                ((Bisected) newData).setHalf(((Bisected) oldData).getHalf());
            }
        }

        // Apply the visual change to all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendBlockChange(loc, newData);
        }
    }

    /**
     * Reverts all transformed blocks back to their original state
     */
    private void revertAllBlocks() {
        for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
            Location loc = entry.getKey();
            BlockData originalData = entry.getValue();

            // Revert the visual change for all players
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendBlockChange(loc, originalData);
            }
        }
        originalBlocks.clear();
    }

    // -------------------------------------------------------------------------
    //  Refresh task
    // -------------------------------------------------------------------------

    private void startRefreshTask() {
        if (refreshTask != null) return;
        refreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!AmplifierManager.isActive(AmplifierType.MOON)) {
                    cancel();
                    refreshTask = null;
                    return;
                }
                applyGlassHeadToAll();
            }
        }.runTaskTimer(plugin, 1L, 1L); // every second
    }

    private void stopRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private static class ChunkCoord {
        final int x;
        final int z;
        final World world;

        ChunkCoord(World world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkCoord that = (ChunkCoord) o;
            return x == that.x && z == that.z && world.equals(that.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z, world);
        }
    }
}
