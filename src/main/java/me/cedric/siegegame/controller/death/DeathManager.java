package me.cedric.siegegame.controller.death;

import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.amplifier.AmplifierType;
import me.cedric.siegegame.model.SiegeGameMatch;
import me.cedric.siegegame.model.game.WorldGame;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.player.kits.Kit;
import me.cedric.siegegame.model.player.kits.KitController;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DeathManager {

    private final SiegeGamePlugin plugin;
    private final HashMap<UUID, RespawnTask> deadPlayers = new HashMap<>();
    private final WorldGame worldGame;
    private final RespawnController respawnController;
    private final DeathLimiters deathLimiters;
    private volatile boolean respawnsPaused = false;

    public DeathManager(SiegeGamePlugin plugin, WorldGame worldGame) {
        this.plugin = plugin;
        this.worldGame = worldGame;
        this.respawnController = new RespawnController(this);
        this.deathLimiters = new DeathLimiters(this);
    }

    public void initialise() {
        plugin.getServer().getPluginManager().registerEvents(respawnController, plugin);
        plugin.getServer().getPluginManager().registerEvents(deathLimiters, plugin);
    }

    public void makeSpectator(Player player) {
        if (worldGame == null)
            return;

        GamePlayer gamePlayer = worldGame.getPlayer(player.getUniqueId());

        if (gamePlayer == null) {
            System.out.println("GamePlayer null! DeathManager, makeSpectator");
            return;
        }

        if (gamePlayer.isDead())
            return;

        Location respawnLocation = player.getLocation();

        if (gamePlayer.hasTeam())
            respawnLocation = gamePlayer.getTeam().getSafeSpawn();
        else {
            SiegeGameMatch gameMatch = plugin.getGameManager().getCurrentMatch();
            if (gameMatch != null)
                respawnLocation = gameMatch.getGameMap().getDefaultSpawn();
        }

        makeSpectator(player, respawnLocation);
    }

    private void makeSpectator(Player player, Location respawnLocation) {
        if (worldGame == null)
            return;

        if (player == null)
            return;

        GamePlayer gamePlayer = worldGame.getPlayer(player.getUniqueId());

        if (gamePlayer == null)
            return;

        player.setExp(0);
        player.setAllowFlight(true);
        player.setFlying(true);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(player.getUniqueId()))
                continue;

            p.hidePlayer(plugin, player);
        }

        if (deadPlayers.containsKey(gamePlayer.getUUID())) {
            deadPlayers.get(gamePlayer.getUUID()).cancel();
            deadPlayers.remove(gamePlayer.getUUID());
        }

        RespawnTask respawnTask = new RespawnTask(plugin, this, gamePlayer, plugin.getGameConfig().getRespawnTimer(), respawnLocation);
        deadPlayers.put(gamePlayer.getUUID(), respawnTask);

        if (!respawnsPaused) {
            respawnTask.start();
        }
        else {
            gamePlayer.setDead(true);
            player.sendMessage(Component.text("Respawns are currently disabled!", net.kyori.adventure.text.format.NamedTextColor.RED));
        }

        String title = ChatColor.translateAlternateColorCodes('&', "&4&lYOU DIED!");
        String subtitle = ChatColor.translateAlternateColorCodes('&', "&cYou will respawn in " + plugin.getGameConfig().getRespawnTimer() + " seconds");
        player.showTitle(Title.title(Component.text(title), Component.text(subtitle)));

        gamePlayer.setDead(true);
        Bukkit.getScheduler().runTaskLater(plugin, gamePlayer::grantNightVision, 10);
    }

    public void revivePlayer(UUID uuid) {
        if (worldGame == null)
            return;
         
        if (!deadPlayers.containsKey(uuid))
            return;

        RespawnTask task = deadPlayers.get(uuid);
        revivePlayer(worldGame.getPlayer(uuid), task.getOriginalRespawn());
    }

    private void revivePlayer(GamePlayer gamePlayer, Location originalRespawn) {
        if (worldGame == null)
            return;

        if (gamePlayer == null)
            return;

        Player player = gamePlayer.getBukkitPlayer();
        player.setFlying(false);
        player.setExp(0);
        player.setLevel(0);
        player.setFlying(false);
        player.setAllowFlight(false);

        player.teleport(originalRespawn);

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
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(player.getUniqueId()))
                continue;

            p.showPlayer(plugin, player);
        }

        gamePlayer.grantNightVision();

        deadPlayers.remove(gamePlayer.getUUID());
        gamePlayer.setDead(false);

        KitController kitController = plugin.getGameManager().getKitController();

        if (!kitController.hasKitManager(player.getUniqueId()))
            return;

        Kit kit = kitController.getKitManager(player.getUniqueId()).getKit(worldGame.getMapIdentifier());
        if (kit != null)
            gamePlayer.getBukkitPlayer().getInventory().setContents(kit.getContents().toArray(ItemStack[]::new));
    }

    public void shutdown() {

        new HashSet<>(deadPlayers.keySet()).forEach(this::revivePlayer);
        
        HandlerList.unregisterAll(respawnController);
        HandlerList.unregisterAll(deathLimiters);
        deadPlayers.clear();
    }

    public boolean isPlayerDead(UUID uuid) {
        return deadPlayers.containsKey(uuid);
    }

    public void setPausePlayer(UUID uuid, boolean b) {
        deadPlayers.get(uuid).setPause(b);
    }

    public void setRespawnsPaused(boolean pause) {
        this.respawnsPaused = pause;
        for (RespawnTask task : deadPlayers.values()) {
            task.setPause(pause);
        }
    }

    public boolean areRespawnsPaused() {
        return respawnsPaused;
    }

    public Set<UUID> getDeadPlayers() {
        return new HashSet<>(deadPlayers.keySet());
    }

    public WorldGame getWorldGame() {
        return worldGame;
    }

    private void setPlayerKit(Player player, ItemStack[] kit) {

    }
}



