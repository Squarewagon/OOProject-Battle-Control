import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.awt.Color;

/**
 * Manages map state. Converts Location from static class to instance-based.
 * Each game can have its own map instance with independent state.
 */
public class MapManager {
    public static final int CELL_SIZE = 40;
    public static final int COLS = 39;
    public static final int ROWS = 27;
    
    private ArrayList<Point> occupancy;          // occupied cells for path and obstacles
    private HashMap<Point, Building> buildingOccupancy; // buildings at positions
    private ArrayList<Point> adjacency;          // buildable cells
    private ArrayList<Point> path;               // enemy path
    
    private Color backgroundColor;
    private Color pathColor;
    private Point startPoint;                    // where enemies spawn
    
    public MapManager() {
        this.occupancy = new ArrayList<>();
        this.buildingOccupancy = new HashMap<>();
        this.adjacency = new ArrayList<>();
        this.path = new ArrayList<>();
    }
    
    // ==================== GETTERS ====================
    
    public ArrayList<Point> getOccupancy() {
        return occupancy;
    }
    
    public HashMap<Point, Building> getBuildingOccupancy() {
        return buildingOccupancy;
    }
    
    public ArrayList<Point> getAdjacency() {
        return adjacency;
    }
    
    public ArrayList<Point> getPath() {
        return path;
    }
    
    public Color getBackgroundColor() {
        return backgroundColor;
    }
    
    public Color getPathColor() {
        return pathColor;
    }
    
    public Point getStartPoint() {
        return startPoint;
    }
    
    // ==================== SETTERS ====================
    
    public void setBackgroundColor(Color color) {
        this.backgroundColor = color;
    }
    
    public void setPathColor(Color color) {
        this.pathColor = color;
    }
    
    public void setStartPoint(Point point) {
        this.startPoint = point;
    }
    
    // ==================== PATH BUILDING ====================
    
    /**
     * Add a line segment to the enemy path
     */
    public void mapPath(int x1, int y1, int x2, int y2) {
        // Only create path if line is horizontal or vertical
        if (x1 != x2 && y1 != y2) {
            return; // Diagonal paths not supported
        }

        // add points to path
        if (x1 == x2) {
            // Vertical line
            int min = Math.min(y1, y2);
            int max = Math.max(y1, y2);
            for (int y = min; y <= max; y++) {
                Point p = new Point(x1, y);
                if (!path.contains(p)) {
                    path.add(p);
                }
            }
        } else {
            // Horizontal line
            int min = Math.min(x1, x2);
            int max = Math.max(x1, x2);
            for (int x = min; x <= max; x++) {
                Point p = new Point(x, y1);
                if (!path.contains(p)) {
                    path.add(p);
                }
            }
        }
    }

    /**
     * Check if a point is on the path
     */
    public boolean pathExists(int x, int y) {
        for (Point p : path) {
            if (p.x == x && p.y == y) {
                return true;
            }
        }
        return false;
    }

    // ==================== RESET ====================
    
    public void clear() {
        occupancy.clear();
        buildingOccupancy.clear();
        adjacency.clear();
        path.clear();
    }
}
