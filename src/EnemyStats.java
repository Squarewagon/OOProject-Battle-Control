import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

/**
 * Defines stats for an enemy type.
 * Loaded from enemies.json configuration file.
 */
public class EnemyStats {
    public String name;                 // e.g., "Recon"
    public Class<?> enemyClass;         // the Java class (e.g., Recon.class)
    
    // Physical
    public int health;
    public int width, height;           // grid cells
    
    // Collision/Hitbox
    public double hitboxOffsetX = 0;    // hitbox offset from center
    public double hitboxOffsetY = 0;
    public double hitboxWidth = 1.0;    // hitbox dimensions (in cells)
    public double hitboxHeight = 1.0;
    
    // Movement
    public double maxSpeed;             // cells per second
    public double minSpeed;
    public double acceleration;         // cells per second squared
    public double deceleration;
    public int rotationSpeed;           // degrees per second
    
    // Combat
    public List<TurretStats> turrets;   // turrets this enemy has
    
    // Rewards
    public int kromerReward;            // kromer given when killed
    
    // Targeting weights (for building selection)
    public HashMap<String, Integer> targetWeights; // e.g., {"Headquarter": 50, "AutoCannon": 150}
    
    // Metadata
    public String description;
    public String imageName;
    
    public EnemyStats() {
        this.turrets = new ArrayList<>();
        this.targetWeights = new HashMap<>();
        this.kromerReward = 5;
    }
}
