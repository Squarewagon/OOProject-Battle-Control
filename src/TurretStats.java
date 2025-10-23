import java.util.ArrayList;
import java.util.List;

/**
 * Defines stats for a single turret.
 * A building/enemy can have multiple turrets.
 * Each turret can have multiple weapons.
 */
public class TurretStats {
    public int offsetX, offsetY;        // offset from parent center in pixels
    public int rotationSpeed;           // degrees per second
    public double range;                // cells
    
    // Targeting behavior (mainly for enemies)
    public double targetInterval;       // time between target acquisition attempts
    public double targetChance;         // probability of acquiring target
    public double targetCooldown;       // cooldown after firing
    
    public List<WeaponStats> weapons;   // weapons this turret uses
    public String propName;             // optional cosmetic prop (like "bar")
    public int zIndex;                  // rendering layer
    
    public TurretStats() {
        this.offsetX = 0;
        this.offsetY = 0;
        this.rotationSpeed = 90;
        this.range = 3;
        this.targetInterval = 1.0;
        this.targetChance = 0.1;
        this.targetCooldown = 2.0;
        this.weapons = new ArrayList<>();
        this.propName = null;
        this.zIndex = 10;
    }
}
