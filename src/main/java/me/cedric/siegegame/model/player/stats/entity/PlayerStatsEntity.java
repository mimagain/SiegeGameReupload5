package me.cedric.siegegame.model.player.stats.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "player_stats")
public class PlayerStatsEntity {

    @Id
    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "total_kills", nullable = false)
    private int totalKills = 0;

    @Column(name = "total_deaths", nullable = false)
    private int totalDeaths = 0;

    @Column(name = "total_damage", nullable = false)
    private double totalDamage = 0.0;

    @Column(name = "games_played", nullable = false)
    private int gamesPlayed = 0;

    @Column(name = "wins", nullable = false)
    private int wins = 0;

    @Column(name = "last_updated", nullable = false)
    private long lastUpdated;

    public PlayerStatsEntity() {
        this.lastUpdated = System.currentTimeMillis();
    }

    public PlayerStatsEntity(UUID playerId) {
        this.playerId = playerId;
        this.lastUpdated = System.currentTimeMillis();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public void setTotalKills(int totalKills) {
        this.totalKills = totalKills;
        updateTimestamp();
    }

    public int getTotalDeaths() {
        return totalDeaths;
    }

    public void setTotalDeaths(int totalDeaths) {
        this.totalDeaths = totalDeaths;
        updateTimestamp();
    }

    public double getTotalDamage() {
        return totalDamage;
    }

    public void setTotalDamage(double totalDamage) {
        this.totalDamage = totalDamage;
        updateTimestamp();
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public void setGamesPlayed(int gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
        updateTimestamp();
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
        updateTimestamp();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void incrementKills() {
        this.totalKills++;
        updateTimestamp();
    }

    public void incrementDeaths() {
        this.totalDeaths++;
        updateTimestamp();
    }

    public void addDamage(double damage) {
        this.totalDamage += damage;
        updateTimestamp();
    }

    public void incrementGamesPlayed() {
        this.gamesPlayed++;
        updateTimestamp();
    }

    public void incrementWins() {
        this.wins++;
        updateTimestamp();
    }

    private void updateTimestamp() {
        this.lastUpdated = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerStatsEntity that = (PlayerStatsEntity) o;
        return Objects.equals(playerId, that.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId);
    }
}



