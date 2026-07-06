package me.cedric.siegegame.view.display;

import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.amplifier.AmplifierType;
import me.cedric.siegegame.amplifier.ZoneKingsAmplifier;
import me.cedric.siegegame.enums.Messages;
import me.cedric.siegegame.model.SiegeGameMatch;
import me.cedric.siegegame.model.game.WorldGame;
import me.cedric.siegegame.model.teams.Team;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.teams.territory.Territory;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Displayer {

    private final SiegeGamePlugin plugin;
    private final GamePlayer gamePlayer;
    private Scoreboard scoreboard = null; 
    private BossBar bossBar = null;
    private static final Set<BossBar> activeBossBars = new HashSet<>();
    private ZoneKingsAmplifier zoneKingsAmplifier;
    public Displayer(SiegeGamePlugin plugin, GamePlayer gamePlayer) {
        this.plugin = plugin;
        this.gamePlayer = gamePlayer;

    }

    public void updateScoreboard() {
        if (gamePlayer == null)
            return;

        Player bukkitPlayer = gamePlayer.getBukkitPlayer();
        if (bukkitPlayer == null || !bukkitPlayer.isOnline()) {

            return;
        }


        SiegeGameMatch match = plugin.getGameManager().getCurrentMatch();

        if (match == null) {

            wipeScoreboard(); 
            return;
        }

        List<String> lines = new ArrayList<>();

        lines.add(ChatColor.DARK_PURPLE + ""); 
        lines.add(ChatColor.GOLD + "Map: " + ChatColor.GRAY + match.getGameMap().getDisplayName());
        lines.add(ChatColor.BLUE + ""); 

        List<Team> teams = match.getWorldGame().getTeams().stream()
                .sorted(Comparator.comparing(Team::getName)) 
                .collect(Collectors.toList());

        for (Team team : teams) {
            lines.add(ColorUtil.getRelationalColor(gamePlayer.getTeam(), team) + team.getName() + ": " +
                    ChatColor.WHITE + team.getPoints() + " points");
        }

        lines.add(ChatColor.RED + ""); 
        lines.add(ChatColor.YELLOW + plugin.getGameConfig().getServerIP());

   

        Scoreboard currentScoreboard = bukkitPlayer.getScoreboard();
        Objective objective;

        if (this.scoreboard != null && currentScoreboard == this.scoreboard && this.scoreboard.getObjective("sieges") != null) {
            objective = this.scoreboard.getObjective("sieges");
        } else {

            this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            objective = this.scoreboard.registerNewObjective("sieges", "dummy",
                    Component.text(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Sieges"), RenderType.INTEGER);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            bukkitPlayer.setScoreboard(this.scoreboard); 
        }


        List<String> currentEntries = new ArrayList<>(this.scoreboard.getEntries());
        for (String entry : currentEntries) {

            this.scoreboard.resetScores(entry);
        }



        int scoreValue = lines.size();
        for (String line : lines) {

            String entry = line;
            if (entry.isEmpty()) { 
                entry = String.valueOf(ChatColor.values()[scoreValue % ChatColor.values().length]) + ChatColor.RESET; 
            }
            if (entry.length() > 40) { 
                entry = entry.substring(0, 40);
            }


            while (objective.getScore(entry).isScoreSet()) {
                entry = entry + ChatColor.RESET; 
                if (entry.length() > 40) entry = entry.substring(0, 40); 
            }


            Score score = objective.getScore(entry);
            score.setScore(scoreValue--); 
        }




    }

    public void wipeScoreboard() {

        Player player = gamePlayer.getBukkitPlayer(); 
        if (player == null || !player.isOnline()) {
            return; 
        }

        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        this.scoreboard = null; 
    }




    public void displayKill(GamePlayer dead, GamePlayer killerGamePlayer) {

        if (gamePlayer == null || gamePlayer.getBukkitPlayer() == null || !gamePlayer.getBukkitPlayer().isOnline()) {
            return;
        }

        Team killerTeam = killerGamePlayer.getTeam();
        Player killer = killerGamePlayer.getBukkitPlayer();

        if (killer == null || killerTeam == null || dead == null || dead.getBukkitPlayer() == null) {
            return;
        }
WorldGame worldGame = plugin.getGameManager().getCurrentMatch().getWorldGame();

        int points = plugin.getGameConfig().getPointsPerKill();
        if (worldGame.getAmplifierManager() != null && 
            worldGame.getAmplifierManager().getCurrent() == AmplifierType.SUDDEN_DEATH) {
            points += 500;
        }
    TextComponent textComponent = Component.text("")
                .append(Component.text("[" + gamePlayer.getTeam().getName() + "]").color(TextColor.color(255, 170, 0)))
                .color(TextColor.color(85, 255, 255))
                .append(Component.text(" Siege of "+worldGame.getMapIdentifier()))
                .append(Component.text(" > "))
                .append(Component.text(ColorUtil.getRelationalColor(gamePlayer.getTeam(), dead.getTeam()) + dead.getBukkitPlayer().getName() + " "))
                .append(Component.text("was killed by "))
                .append(Component.text(ColorUtil.getRelationalColor(gamePlayer.getTeam(), killerTeam) + killer.getName()))
                .append(Component.text(" > "))
                .append(Component.text("Battle points: "))
                .append(Component.text(ColorUtil.getRelationalColor(gamePlayer.getTeam(), killerTeam)+"+" + points));
                TextComponent textComponent2 = Component.text("")
                .append(Component.text("[" + gamePlayer.getTeam().getName() + "]").color(TextColor.color(255, 170, 0)))
                .color(TextColor.color(85, 255, 255))
                .append(Component.text(" Siege of "+worldGame.getMapIdentifier()))
                .append(Component.text(" > "))
                .append(Component.text(ColorUtil.getRelationalColor(gamePlayer.getTeam(), dead.getTeam()) + dead.getBukkitPlayer().getName() + " "))
                .append(Component.text("was killed by "))
                .append(Component.text(ColorUtil.getRelationalColor(gamePlayer.getTeam(), killerTeam) + killer.getName()))
                .append(Component.text(" > "))
                .append(Component.text("Battle points: "))
                .append(Component.text(ColorUtil.getRelationalColor(gamePlayer.getTeam(), killerTeam)+"-" + points))            
                .append(Component.text("[Friendly Fire]"));
           if (dead.getTeam() != killerTeam){
        gamePlayer.getBukkitPlayer().sendMessage(textComponent);
           }
           if (dead.getTeam() == killerTeam){
            gamePlayer.getBukkitPlayer().sendMessage(textComponent2);
               }
        TextComponent xpLevels = Component.text("")
                .color(TextColor.color(0, 143, 26))
                .append(Component.text("+" + plugin.getGameConfig().getLevelsPerKill() + " XP Levels"));

        if (gamePlayer.hasTeam() && killerTeam.equals(gamePlayer.getTeam())) {
            gamePlayer.getBukkitPlayer().sendMessage(xpLevels);
        }
    }

    public void displayCombatLogKill(String dead) {

        if (gamePlayer == null || gamePlayer.getBukkitPlayer() == null || !gamePlayer.getBukkitPlayer().isOnline()) {
            return;
        }
        TextComponent textComponent = Component.text("")
                .color(TextColor.color(88, 140, 252))
                .append(Component.text(Messages.PREFIX.toString())
                        .append(Component.text(dead, TextColor.color(237, 77, 255))
                                .append(Component.text(" has logged out in combat. ", TextColor.color(252, 252, 53)))
                                .append(Component.text("Enemies have received ", TextColor.color(255, 194, 97)))
                                .append(Component.text("+" + plugin.getGameConfig().getPointsPerKill() + " points ", TextColor.color(255, 73, 23)))));

        gamePlayer.getBukkitPlayer().sendMessage(textComponent);
    }

    public void displayInsideClaims(WorldGame worldGame, Territory territory) {

        if (gamePlayer == null || gamePlayer.getBukkitPlayer() == null || !gamePlayer.getBukkitPlayer().isOnline()) {
            return;
        }
        if (bossBar != null)
            removeDisplayInsideClaims(); 

        String message = Messages.CLAIMS_ENTERED; 
        Team team = worldGame.getTeam(territory.getTeam().getConfigKey());

        if (team == null) return;

        Object relationalColor = gamePlayer.hasTeam() ? ColorUtil.getRelationalColor(gamePlayer.getTeam(), team) : ChatColor.WHITE;


        String teamNameDisplay = relationalColor + team.getName();
        String formattedMessage = String.format(message, teamNameDisplay); 

        gamePlayer.getBukkitPlayer().sendActionBar(Component.text(formattedMessage)); 

        String bossBarMessage = ChatColor.YELLOW + "You are currently in " + teamNameDisplay + ChatColor.YELLOW + " claims";
        bossBar = BossBar.bossBar(Component.text(bossBarMessage),
                1, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        gamePlayer.getBukkitPlayer().showBossBar(bossBar);
        activeBossBars.add(bossBar);
    }

    public void removeDisplayInsideClaims() {

        if (gamePlayer == null || gamePlayer.getBukkitPlayer() == null || !gamePlayer.getBukkitPlayer().isOnline()) {

            if (bossBar != null) {


            }

            if (bossBar != null) {
                activeBossBars.remove(bossBar);
                bossBar = null; 
            }
            return;
        }

        if (bossBar != null) {
            gamePlayer.getBukkitPlayer().hideBossBar(bossBar);
            activeBossBars.remove(bossBar);
            bossBar = null; 
        }
    }

    public void displayActionCancelled() {

        if (gamePlayer == null || gamePlayer.getBukkitPlayer() == null || !gamePlayer.getBukkitPlayer().isOnline()) {
            return;
        }


    }

    public void displayVictory() {

        if (gamePlayer == null || gamePlayer.getBukkitPlayer() == null || !gamePlayer.getBukkitPlayer().isOnline()) {
            return;
        }
        gamePlayer.getBukkitPlayer().sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "VICTORY", ChatColor.YELLOW + "gg ez yall are dog z tier rands");
    }

    public void displayLoss() {

        if (gamePlayer == null || gamePlayer.getBukkitPlayer() == null || !gamePlayer.getBukkitPlayer().isOnline()) {
            return;
        }
        gamePlayer.getBukkitPlayer().sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "DEFEAT", ChatColor.RED + "u folded gg ez dog");
    }

    public void displayXPGain(GamePlayer targetPlayer) { 

        if (targetPlayer == null || targetPlayer.getBukkitPlayer() == null || !targetPlayer.getBukkitPlayer().isOnline()) {
            return;
        }
        TextComponent xpLevels = Component.text("")
                .color(TextColor.color(0, 143, 26))
                .append(Component.text("+" + plugin.getGameConfig().getLevelsPerKill() + " XP Levels"));
        targetPlayer.getBukkitPlayer().sendMessage(xpLevels); 
    }

    public static void clearAllBossBars() {

        Set<BossBar> bossBarsCopy = new HashSet<>(activeBossBars);
        
        for (BossBar bar : bossBarsCopy) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bar);
            }
            activeBossBars.remove(bar);
        }
    }
}


