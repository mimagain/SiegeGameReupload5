package me.cedric.siegegame.model.player.stats.entity;


import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;

@Entity
@Table(name = "match_stats")
public class MatchStatsEntity implements Serializable {

    @EmbeddedId
    private MatchStatsId id;

    @Column(name = "kills", nullable = false)
    private int kills = 0;

    @Column(name = "deaths", nullable = false)
    private int deaths = 0;

    @Column(name = "damage", nullable = false)
    private double damage = 0.0;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "won", nullable = false)
    private boolean won = false;

    @Column(name = "match_end_time", nullable = false)
    private long matchEndTime;


    public MatchStatsEntity() {
    }

    public MatchStatsEntity(UUID matchId, UUID playerId) {
        this.id = new MatchStatsId(matchId, playerId);
        this.matchEndTime = System.currentTimeMillis();
    }

    public MatchStatsId getId() {
        return id;
    }

    public void setId(MatchStatsId id) {
        this.id = id;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public int getDeaths() {
        return deaths;
    }
    
    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public boolean isWon() {
        return won;
    }

    public void setWon(boolean won) {
        this.won = won;
    }

    public long getMatchEndTime() {
        return matchEndTime;
    }

    public void setMatchEndTime(long matchEndTime) {
        this.matchEndTime = matchEndTime;
    }

    public void incrementKills() {
        this.kills++;
    }

    public void incrementDeaths() {
        this.deaths++;
    }

    public void addDamage(double damage) {
        this.damage += damage;
    }


    @Embeddable
    public static class MatchStatsId implements Serializable {
        @Column(name = "match_id")
        private UUID matchId;

        @Column(name = "player_id")
        private UUID playerId;

        public MatchStatsId() {
        }

        public MatchStatsId(UUID matchId, UUID playerId) {
            this.matchId = matchId;
            this.playerId = playerId;
        }

        public UUID getMatchId() {
            return matchId;
        }

        public void setMatchId(UUID matchId) {
            this.matchId = matchId;
        }
        public UUID getPlayerId() {
            return playerId;
        }
        public void setPlayerId(UUID playerId) {
            this.playerId = playerId;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MatchStatsId that = (MatchStatsId) o;
            return matchId.equals(that.matchId) && playerId.equals(that.playerId);
        }
        @Override
        public int hashCode() {
            return Objects.hash(matchId, playerId);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchStatsEntity that = (MatchStatsEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}



