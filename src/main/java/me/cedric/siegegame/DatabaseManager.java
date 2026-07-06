package me.cedric.siegegame;



import java.io.File; 

import java.sql.Connection;

import java.sql.DriverManager;

import java.sql.PreparedStatement;

import java.sql.ResultSet;

import java.sql.SQLException;

import java.sql.Statement;

import java.util.ArrayList;

import java.util.Arrays;

import java.util.List;

import java.util.UUID;



import me.cedric.siegegame.model.player.stats.PlayerStats; 



public class DatabaseManager {

private Connection connection; 
private SiegeGamePlugin plugin;

private final File databaseFile; 


  public DatabaseManager(SiegeGamePlugin plugin, File databaseFile) {

    this.plugin = plugin;

    this.databaseFile = databaseFile;

  }



  public void establishConnection() {


    if (!databaseFile.getParentFile().exists()) {

      databaseFile.getParentFile().mkdirs(); 

    }



      try {


      String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

      Class.forName("org.sqlite.JDBC"); 

      connection = DriverManager.getConnection(url); 



      plugin.getLogger().info("Database connection established for SQLite.");

    } catch (SQLException | ClassNotFoundException e) {

      plugin.getLogger().severe("Could not connect to SQLite database: " + e.getMessage());

      e.printStackTrace(); 

    }

  }



  public void createTable() {

    if (connection == null) {

      plugin.getLogger().severe("Cannot create table: SQLite database connection is not established.");

      return;

    }

    try (Statement statement = connection.createStatement()) {


      String createTableSql = "CREATE TABLE IF NOT EXISTS player_stats (" +

                  "uuid VARCHAR(36) PRIMARY KEY," +

                  "player_name VARCHAR(16) NOT NULL," +

                  "kills INT DEFAULT 0," +

                  "deaths INT DEFAULT 0," +

                  "total_damage_dealt DOUBLE DEFAULT 0.0," +

                  "total_damage_taken DOUBLE DEFAULT 0.0" +

                  ");"; 




      statement.execute(createTableSql);

      plugin.getLogger().info("Player stats table created or already exists.");



      addMissingColumn(statement, "player_stats", "games_played", "INT DEFAULT 0");

      addMissingColumn(statement, "player_stats", "games_won", "INT DEFAULT 0");

      addMissingColumn(statement, "player_stats", "kdr", "DOUBLE DEFAULT 0.0");
      
      // Column that stores whether a player has chosen to hide their stats (0 = visible, 1 = hidden)
      addMissingColumn(statement, "player_stats", "stats_hidden", "INT DEFAULT 0");





    } catch (SQLException e) {

      plugin.getLogger().severe("Error creating or altering table in SQLite: " + e.getMessage());

      e.printStackTrace();

    }

  }



  private void addMissingColumn(Statement statement, String tableName, String columnName, String columnDefinition) throws SQLException {


    try (ResultSet rs = statement.executeQuery("PRAGMA table_info(" + tableName + ");")) {

      boolean columnExists = false;

      while (rs.next()) {

        if (rs.getString("name").equalsIgnoreCase(columnName)) {

          columnExists = true;

          break;

        }

      }

      if (!columnExists) {

        String alterTableSql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition + ";";

        statement.execute(alterTableSql);

        plugin.getLogger().info("Added missing column '" + columnName + "' to table '" + tableName + "'.");

      }

    }

  }

  public void setPlayerStat(UUID uuid, String playerName, String statColumn, double value) {
    if (connection == null) {
      plugin.getLogger().warning("Database connection is not established. Cannot set player stat in SQLite.");
      return;
    }
    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
      try (PreparedStatement ps = connection.prepareStatement(
          "INSERT INTO player_stats (uuid, player_name, " + statColumn + ") VALUES (?, ?, ?) " +
          "ON CONFLICT(uuid) DO UPDATE SET player_name = excluded.player_name, " + statColumn + " = ?")) {
        ps.setString(1, uuid.toString());
        ps.setString(2, playerName);
        ps.setDouble(3, value);
        ps.setDouble(4, value);
        ps.executeUpdate();
      } catch (SQLException e) {
        plugin.getLogger().severe("Error setting player stat " + statColumn + " for " + playerName + " in SQLite: " + e.getMessage());
        e.printStackTrace();
      }
    });
  }

  public void updatePlayerStat(UUID uuid, String playerName, String statColumn, double amount) {


    if (connection == null) {

      plugin.getLogger().warning("Database connection is not established. Cannot update player stat in SQLite.");

      return;

    }



    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {

      try (PreparedStatement ps = connection.prepareStatement(



          "INSERT INTO player_stats (uuid, player_name, " + statColumn + ") VALUES (?, ?, ?) " +

          "ON CONFLICT(uuid) DO UPDATE SET player_name = excluded.player_name, " + statColumn + " = " + statColumn + " + ?"

      )) {

        ps.setString(1, uuid.toString());

        ps.setString(2, playerName);

        ps.setDouble(3, amount);

        ps.setDouble(4, amount); 



        ps.executeUpdate();

      } catch (SQLException e) {

        plugin.getLogger().severe("Error updating player stat " + statColumn + " for " + playerName + " in SQLite: " + e.getMessage());

        e.printStackTrace();

      }

    });

  }



  public PlayerStats getPlayerStats(UUID uuid) {


    if (connection == null) {

      plugin.getLogger().warning("Database connection is not established. Cannot retrieve player stats from SQLite.");

      return null;

    }


    try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_stats WHERE uuid = ?")) {

      ps.setString(1, uuid.toString());

      try (ResultSet rs = ps.executeQuery()) {

        if (rs.next()) {

          String playerName = rs.getString("player_name");

          int kills = rs.getInt("kills");

          int deaths = rs.getInt("deaths");

          double totalDamageDealt = rs.getDouble("total_damage_dealt");

          double totalDamageTaken = rs.getDouble("total_damage_taken");

          int gamesPlayed = rs.getInt("games_played");

          int gamesWon = rs.getInt("games_won");

          int safeDeaths = deaths == 0 ? 1 : deaths;
           double kdr = (double) kills / safeDeaths;

          return new PlayerStats(uuid, playerName, kills, deaths, totalDamageDealt, totalDamageTaken, gamesPlayed, gamesWon, kdr);

        }

      }

    } catch (SQLException e) {

      plugin.getLogger().severe("Error retrieving player stats for UUID " + uuid.toString() + " from SQLite: " + e.getMessage());

      e.printStackTrace();

    }

    return null; 

  }



  

  public List<PlayerStats> getTopPlayers(String stat, int limit) {

    List<PlayerStats> topPlayers = new ArrayList<>();


    List<String> validStats = Arrays.asList("kills", "deaths", "total_damage_dealt", "games_played", "games_won", "kdr");

    if (!validStats.contains(stat.toLowerCase())) {

      plugin.getLogger().warning("Invalid stat type: " + stat);

      return topPlayers;

    }

   

    String sql;
    if (stat.equalsIgnoreCase("kdr")) {
      // Compute KDR dynamically so it stays accurate without needing its own column
      sql = "SELECT *, (CASE WHEN deaths = 0 THEN kills ELSE CAST(kills AS REAL)/deaths END) AS kdr_value " +
            "FROM player_stats ORDER BY kdr_value DESC LIMIT ?";
    } else {
      sql = "SELECT * FROM player_stats ORDER BY " + stat + " DESC LIMIT ?";
    }

   

    try (PreparedStatement ps = connection.prepareStatement(sql)) {

      ps.setInt(1, limit);

     

      try (ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {

          UUID uuid = UUID.fromString(rs.getString("uuid"));

          String playerName = rs.getString("player_name");

          int kills = rs.getInt("kills");

          int deaths = rs.getInt("deaths");

          double damageDealt = rs.getDouble("total_damage_dealt");

          double damageTaken = rs.getDouble("total_damage_taken");

          int gamesPlayed = rs.getInt("games_played");

          int gamesWon = rs.getInt("games_won");

          int safeDeaths = deaths == 0 ? 1 : deaths;
           double kdr = (double) kills / safeDeaths;

         

          topPlayers.add(new PlayerStats(uuid, playerName, kills, deaths,

              damageDealt, damageTaken, gamesPlayed, gamesWon, kdr));

        }

      }

    } catch (SQLException e) {

      plugin.getLogger().severe("Error getting top players for stat " + stat + ": " + e.getMessage());

      e.printStackTrace();

    }

   

    return topPlayers;

  }



  /*
   * ---------------------------------------------------------------------
   *  Stats Hidden helpers
   * ---------------------------------------------------------------------
   */
  public void setStatsHidden(UUID uuid, String playerName, boolean hidden) {
    if (connection == null) {
      plugin.getLogger().warning("Database connection is not established. Cannot update stats_hidden in SQLite.");
      return;
    }

    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
      try (PreparedStatement ps = connection.prepareStatement(
          "INSERT INTO player_stats (uuid, player_name, stats_hidden) VALUES (?, ?, ?) " +
          "ON CONFLICT(uuid) DO UPDATE SET player_name = excluded.player_name, stats_hidden = ?")) {
        ps.setString(1, uuid.toString());
        ps.setString(2, playerName);
        ps.setInt(3, hidden ? 1 : 0);
        ps.setInt(4, hidden ? 1 : 0);
        ps.executeUpdate();
      } catch (SQLException e) {
        plugin.getLogger().severe("Error updating stats_hidden for " + playerName + " in SQLite: " + e.getMessage());
        e.printStackTrace();
      }
    });
  }

  public boolean isStatsHidden(UUID uuid) {
    if (connection == null) {
      plugin.getLogger().warning("Database connection is not established. Cannot query stats_hidden in SQLite.");
      return false;
    }

    try (PreparedStatement ps = connection.prepareStatement("SELECT stats_hidden FROM player_stats WHERE uuid = ?")) {
      ps.setString(1, uuid.toString());
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getInt("stats_hidden") == 1;
        }
      }
    } catch (SQLException e) {
      plugin.getLogger().severe("Error retrieving stats_hidden for UUID " + uuid.toString() + " from SQLite: " + e.getMessage());
      e.printStackTrace();
    }
    return false;
  }

  public void closeConnection() {

    if (connection != null) {

      try {

        connection.close();

        plugin.getLogger().info("SQLite database connection closed.");

      } catch (SQLException e) {

        plugin.getLogger().severe("Error closing SQLite database connection: " + e.getMessage());

      } finally {

        connection = null; 

      }

    }

  }

}


