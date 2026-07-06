package me.cedric.siegegame.model.player;

import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.model.player.border.PlayerBorderHandler;
import me.cedric.siegegame.view.fake.FakeBlockManager;
import me.cedric.siegegame.view.display.Displayer;
import me.cedric.siegegame.model.teams.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class GamePlayer {

    private final SiegeGamePlugin plugin;
    private final UUID uuid;
    private final PlayerBorderHandler playerBorderHandler;
    private final FakeBlockManager fakeBlockManager;
    private final Displayer displayer;
    private boolean dead = false;
    private Team team;
    private boolean teamChatMode = false;
    private boolean hidestats = false;
    private static final String TEAM_CHAT_KEY = "team-chat-mode";

    public GamePlayer(UUID uuid, SiegeGamePlugin plugin) {
        this.uuid = uuid;
        this.team = null;
        this.plugin = plugin;
        this.playerBorderHandler = new PlayerBorderHandler(plugin, this);
        this.displayer = new Displayer(plugin, this);
        this.fakeBlockManager = new FakeBlockManager(plugin, getBukkitPlayer());

        load();
    }

    public Player getBukkitPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public Team getTeam() {
        return team;
    }

    public boolean hasTeam() {
        return team != null;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public UUID getUUID() {
        return uuid;
    }

    public PlayerBorderHandler getBorderHandler() {
        return playerBorderHandler;
    }

    public boolean isDead() {
        return dead;
    }

    public void setDead(boolean dead) {
        this.dead = dead;
    }

    public Displayer getDisplayer() {
        return displayer;
    }

    public FakeBlockManager getFakeBlockManager() {
        return fakeBlockManager;
    }

    public Boolean areStatsHidden() {
        return hidestats;
    }
    public void setStatsHidden(boolean hidestats) {
        this.hidestats = hidestats;

    }
    public void grantNightVision() {
        getBukkitPlayer().addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
    }
    
    public SiegeGamePlugin getPlugin() {
        return this.plugin;
    }
    
    public void reset() {
        getBukkitPlayer().setLevel(0);
        getBukkitPlayer().getInventory().clear();
        getBukkitPlayer().getEnderChest().clear();
        grantNightVision();
        getDisplayer().wipeScoreboard();

        setTeamChatMode(false);
    }

    public boolean isInTeamChatMode() {
        return teamChatMode;
    }

    public void setTeamChatMode(boolean teamChatMode) {
        this.teamChatMode = teamChatMode;

        Player player = getBukkitPlayer();
        if (player != null) {
            player.getPersistentDataContainer().set(
                new NamespacedKey(plugin, TEAM_CHAT_KEY),
                PersistentDataType.BYTE,
                teamChatMode ? (byte)1 : (byte)0
            );
        }
    }

    public void toggleTeamChatMode() {
        setTeamChatMode(!teamChatMode);
    }

    
    private void load() {
        Player player = getBukkitPlayer();
        if (player != null) {

            Byte chatModeByte = player.getPersistentDataContainer().get(
                new NamespacedKey(plugin, TEAM_CHAT_KEY),
                PersistentDataType.BYTE
            );
            if (chatModeByte != null) {
                this.teamChatMode = chatModeByte == 1;
            }
        }
    }
}



