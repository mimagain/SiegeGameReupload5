package me.cedric.siegegame.model.teams.territory;

import me.cedric.siegegame.model.map.GameMap;
import me.cedric.siegegame.util.Box2D;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Polygon {

    private final Set<Box2D> boxes = new HashSet<>();
    private final GameMap gameMap;
    private final Set<Vector2D> points = new HashSet<>();

    public Polygon(GameMap gameMap, Vector2D p1, Vector2D p2) {
        this.gameMap = gameMap;
        addSquare(p1, p2);
    }

    public void addSquare(Vector2D p1, Vector2D p2) {
        Vector2D min = new Vector2D(Math.min(p1.getX(), p2.getX()), Math.min(p1.getZ(), p2.getZ()));
        Vector2D max = new Vector2D(Math.max(p1.getX(), p2.getX()), Math.max(p1.getZ(), p2.getZ()));

        Box2D box = new Box2D(min, max);
        boxes.add(box);

        points.add(min);
        points.add(max);
        points.add(new Vector2D(min.getX(), max.getZ()));
        points.add(new Vector2D(max.getX(), min.getZ()));
    }

    public void clear() {
        boxes.clear();
        points.clear();
    }

    public boolean isColliding(Vector2D v, World world) {
        for (Box2D box : boxes) {
            if (box.isColliding(v) && world.equals(gameMap.getWorld()))
                return true;
        }
        return false;
    }
    
    
    public Set<Vector2D> getPoints() {
        return points;
    }
}



