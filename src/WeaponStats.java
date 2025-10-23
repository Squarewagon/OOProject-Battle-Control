/**
 * Defines stats for a single weapon.
 * Used by turrets to fire projectiles.
 */
public class WeaponStats {
    public String name;
    public int damage;
    public double rof;              // seconds between shots
    public int pierce;              // how many targets can be hit
    public double projectileSpeed;  // cells per second
    public int offsetX, offsetY;    // offset from turret center in pixels
    
    // Burst/spread properties (for enemies mainly)
    public int burst;               // shots per trigger
    public double burstDelay;       // seconds between burst shots
    public double spread;           // degrees of random spread
    
    // Projectile type
    public String projectileType;   // "bullet" or "shell"
    
    public WeaponStats() {
        this.damage = 10;
        this.rof = 1.0;
        this.pierce = 0;
        this.projectileSpeed = 4.0;
        this.offsetX = 0;
        this.offsetY = 0;
        this.burst = 1;
        this.burstDelay = 0.5;
        this.spread = 0;
        this.projectileType = "bullet";
    }
}
