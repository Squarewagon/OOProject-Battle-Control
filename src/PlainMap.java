import java.awt.Point;
import java.awt.Color;

/**
 * Plain map implementation.
 * Replaces the Plain extends Location pattern.
 */
public class PlainMap extends MapManager {
    public PlainMap() {
        super();
        
        // Set colors
        setBackgroundColor(new Color(34, 139, 34));
        setPathColor(new Color(50, 175, 50));
        
        // Set spawn point
        setStartPoint(new Point(43, 13));
        
        // Build the path
        mapPath(38, 13, 32, 13);
        mapPath(32, 13, 32, 6);
        mapPath(32, 6, 14, 6);
        mapPath(14, 6, 14, 21);
        mapPath(14, 21, 19, 21);
        mapPath(19, 21, 19, 8);
        mapPath(19, 8, 25, 8);
        mapPath(25, 8, 25, 24);
        mapPath(25, 24, 8, 24);
        mapPath(8, 24, 8, 3);
        mapPath(8, 3, 5, 3);
    }
}
