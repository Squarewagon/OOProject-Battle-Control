import java.util.ArrayList;
import java.util.List;

/**
 * Defines stats for a building type.
 * Loaded from buildings.json configuration file.
 * Contains all information needed to construct a building without hardcoding.
 */
public class BuildingStats {
    public String name;                 // e.g., "AutoCannon", "PowerPlant"
    public String buildingType;         // "productive" or "offensive"
    public Class<?> buildingClass;      // the Java class (e.g., AutoCannon.class)
    
    // Construction
    public int cost;                    // kromer cost
    public double buildTime;            // seconds to build
    public double buildRange;           // cells away player can build from (0 = anywhere)
    public int buildLimit;              // -1 = unlimited
    public List<String> prerequisites;  // required buildings (e.g., ["PowerPlant"])
    
    // Physical
    public int width, height;           // grid cells
    public int health;
    public int power;                   // negative = consumes, positive = generates
    
    // Collision/Hitbox (auto-calculated from width/height but can be overridden)
    public double hitboxOffsetX = -1;   // -1 means auto-calculate from width/height. offset from center
    public double hitboxOffsetY = -1;
    public double hitboxWidth = -1;     // hitbox dimensions (in cells). -1 means use width
    public double hitboxHeight = -1;    // -1 means use height
    
    // Turrets and weapons
    public List<TurretStats> turrets;   // turrets this building has
    
    // Metadata
    public String description;
    public String imageName;            // for rendering
    
    public BuildingStats() {
        this.prerequisites = new ArrayList<>();
        this.turrets = new ArrayList<>();
        this.buildLimit = -1;
        this.buildRange = 0;
        this.power = 0;
    }
}
