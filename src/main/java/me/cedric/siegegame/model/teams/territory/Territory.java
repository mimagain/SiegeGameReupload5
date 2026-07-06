package me.cedric.siegegame.model.teams.territory;

import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.teams.TeamFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class Territory {

    private final Polygon polygon;
    private final TeamFactory owner;
    private Location center = null;

    public Territory(SiegeGamePlugin plugin, Polygon polygon, TeamFactory owner) {
        this.polygon = polygon;
        this.owner = owner;
        calculateCenter();
    }

    private void calculateCenter() {
        if (polygon.getPoints().isEmpty()) {
            return;
        }

        double minX = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = Double.MIN_VALUE;
        
        for (Vector2D point : polygon.getPoints()) {
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minZ = Math.min(minZ, point.getZ());
            maxZ = Math.max(maxZ, point.getZ());
        }
        
        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;

        World world = Bukkit.getWorlds().get(0);
        this.center = new Location(world, centerX, 64, centerZ); 
    }

    
    public Location getCenter() {
        return center;
    }

    public boolean isInside(World world, int x, int z) {
        return polygon.isColliding(new Vector2D(x, z), world);
    }

    public boolean isInside(Location location) {
        return isInside(location.getWorld(), location.getBlockX(), location.getBlockZ());
    }

    public boolean isInside(Player player) {
        return isInside(player.getLocation());
    }

    public boolean isInside(GamePlayer player) {
        return isInside(player.getBukkitPlayer());
    }

    public void addSquare(Vector2D p1, Vector2D p2) {
        this.polygon.addSquare(p1, p2);

        calculateCenter();
    }

    public TeamFactory getTeam() {
        return owner;
    }
}



