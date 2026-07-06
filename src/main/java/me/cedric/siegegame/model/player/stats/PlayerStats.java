package me.cedric.siegegame.model.player.stats;
import java.util.UUID;

import me.cedric.siegegame.SiegeGamePlugin;

public class PlayerStats {
    private final UUID uuid;
    private final String playerName;
    private final int kills;
    private final int deaths;
    private final double totalDamageDealt;
    private final double totalDamageTaken;
    private final int gamesPlayed;
    private final int gamesWon;
    private double kdr;
    public PlayerStats(UUID uuid, String playerName, int kills, int deaths, double totalDamageDealt, double totalDamageTaken, int gamesPlayed, int gamesWon, double kdr) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.kills = kills;
        this.deaths = deaths;
        this.totalDamageDealt = totalDamageDealt;
        this.totalDamageTaken = totalDamageTaken;
        this.gamesPlayed = gamesPlayed;
        this.gamesWon = gamesWon;
        this.kdr = kdr;
    }

    public UUID getUuid() { return uuid; }
    public String getPlayerName() { return playerName; }
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public double getTotalDamageDealt() { return totalDamageDealt; }
    public double getTotalDamageTaken() { return totalDamageTaken; }
    public int getGamesPlayed() {return gamesPlayed;}
    public int getGamesWon() {return gamesWon;}
    public double getKDR() {return kdr;}
    
    @Override
    public String toString() {
                 
        return "§e" + playerName + "'s Stats:\n" +
               "§b  Kills: §a" + kills + "\n" +
               "§b  Deaths: §c" + deaths + "\n" +
               "§b  KDR: §f" + String.format("%.2f", (double) kills / Math.max(deaths, 1)) + "\n" +
               "§b  Damage Dealt: §f" + String.format("%.2f", totalDamageDealt) + "\n" +
               "§b  Damage Taken: §f" + String.format("%.2f", totalDamageTaken) + "\n" +
               "§b  Games Played: §f" + gamesPlayed + "\n" +
               "§b  Games Won: §f" + gamesWon;
              
            }

}


