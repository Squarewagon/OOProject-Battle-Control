import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Loads and caches all game configuration from JSON files.
 * Provides access to building, enemy, and weapon stats.
 * 
 * Files:
 * - resources/config/buildings.json
 * - resources/config/enemies.json
 * - resources/config/weapons.json
 */
public class ConfigManager {
    private static final String CONFIG_PATH = "resources/config/";
    private static final Gson gson = new Gson();
    
    private Map<String, BuildingStats> buildingStats;
    private Map<String, EnemyStats> enemyStats;
    private Map<String, WeaponDefinition> weaponStats;
    private boolean loaded = false;
    
    public ConfigManager() {
        this.buildingStats = new HashMap<>();
        this.enemyStats = new HashMap<>();
        this.weaponStats = new HashMap<>();
    }
    
    /**
     * Load all configuration files.
     * Call this once at game startup.
     */
    public void loadConfigs() {
        if (loaded) return;
        
        try {
            loadWeaponConfigs();
            loadBuildingConfigs();
            loadEnemyConfigs();
            loaded = true;
            System.out.println("[ConfigManager] Loaded " + weaponStats.size() + " weapon types");
            System.out.println("[ConfigManager] Loaded " + buildingStats.size() + " building types");
            System.out.println("[ConfigManager] Loaded " + enemyStats.size() + " enemy types");
        } catch (Exception e) {
            System.err.println("[ConfigManager] Error loading configs: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load weapon configuration from weapons.json
     */
    private void loadWeaponConfigs() throws Exception {
        File file = new File(CONFIG_PATH + "weapons.json");
        if (!file.exists()) {
            System.err.println("[ConfigManager] weapons.json not found at " + file.getAbsolutePath());
            return;
        }
        
        try (FileReader reader = new FileReader(file)) {
            JsonArray weaponsArray = gson.fromJson(reader, JsonArray.class);
            
            for (int i = 0; i < weaponsArray.size(); i++) {
                JsonObject weaponObj = weaponsArray.get(i).getAsJsonObject();
                WeaponDefinition stats = parseWeaponDefinition(weaponObj);
                weaponStats.put(stats.name, stats);
            }
        }
    }
    
    /**
     * Load building configuration from buildings.json
     */
    private void loadBuildingConfigs() throws Exception {
        File file = new File(CONFIG_PATH + "buildings.json");
        if (!file.exists()) {
            System.err.println("[ConfigManager] buildings.json not found at " + file.getAbsolutePath());
            return;
        }
        
        try (FileReader reader = new FileReader(file)) {
            JsonArray buildingsArray = gson.fromJson(reader, JsonArray.class);
            
            for (int i = 0; i < buildingsArray.size(); i++) {
                JsonObject buildingObj = buildingsArray.get(i).getAsJsonObject();
                BuildingStats stats = parseBuildingStats(buildingObj);
                buildingStats.put(stats.name, stats);
            }
        }
    }
    
    /**
     * Load enemy configuration from enemies.json
     */
    private void loadEnemyConfigs() throws Exception {
        File file = new File(CONFIG_PATH + "enemies.json");
        if (!file.exists()) {
            System.err.println("[ConfigManager] enemies.json not found at " + file.getAbsolutePath());
            return;
        }
        
        try (FileReader reader = new FileReader(file)) {
            JsonArray enemiesArray = gson.fromJson(reader, JsonArray.class);
            
            for (int i = 0; i < enemiesArray.size(); i++) {
                JsonObject enemyObj = enemiesArray.get(i).getAsJsonObject();
                EnemyStats stats = parseEnemyStats(enemyObj);
                enemyStats.put(stats.name, stats);
            }
        }
    }
    
    /**
     * Parse a building stats object from JSON
     */
    private BuildingStats parseBuildingStats(JsonObject obj) {
        BuildingStats stats = new BuildingStats();
        stats.name = obj.get("name").getAsString();
        stats.buildingType = obj.get("buildingType").getAsString();
        stats.cost = obj.get("cost").getAsInt();
        stats.buildTime = obj.get("buildTime").getAsDouble();
        stats.buildRange = obj.get("buildRange").getAsDouble();
        stats.width = obj.get("width").getAsInt();
        stats.height = obj.get("height").getAsInt();
        stats.health = obj.get("health").getAsInt();
        stats.power = obj.get("power").getAsInt();
        stats.description = obj.get("description").getAsString();
        stats.imageName = obj.get("imageName").getAsString();
        
        // Resolve building class by name
        try {
            stats.buildingClass = Class.forName(stats.name);
        } catch (ClassNotFoundException e) {
            System.err.println("[ConfigManager] Building class not found: " + stats.name);
        }
        
        if (obj.has("buildLimit")) {
            stats.buildLimit = obj.get("buildLimit").getAsInt();
        }
        
        // Parse prerequisites
        if (obj.has("prerequisites")) {
            JsonArray prereqArray = obj.get("prerequisites").getAsJsonArray();
            for (int i = 0; i < prereqArray.size(); i++) {
                stats.prerequisites.add(prereqArray.get(i).getAsString());
            }
        }
        
        // Parse turrets
        if (obj.has("turrets")) {
            JsonArray turretsArray = obj.get("turrets").getAsJsonArray();
            for (int i = 0; i < turretsArray.size(); i++) {
                TurretStats turretStats = parseTurretStats(turretsArray.get(i).getAsJsonObject());
                stats.turrets.add(turretStats);
            }
        }
        
        return stats;
    }
    
    /**
     * Parse an enemy stats object from JSON
     */
    private EnemyStats parseEnemyStats(JsonObject obj) {
        EnemyStats stats = new EnemyStats();
        stats.name = obj.get("name").getAsString();
        stats.health = obj.get("health").getAsInt();
        stats.maxSpeed = obj.get("maxSpeed").getAsDouble();
        stats.minSpeed = obj.get("minSpeed").getAsDouble();
        stats.acceleration = obj.get("acceleration").getAsDouble();
        stats.deceleration = obj.get("deceleration").getAsDouble();
        stats.rotationSpeed = obj.get("rotationSpeed").getAsInt();
        stats.description = obj.get("description").getAsString();
        stats.imageName = obj.get("imageName").getAsString();
        
        if (obj.has("kromerReward")) {
            stats.kromerReward = obj.get("kromerReward").getAsInt();
        }
        
        if (obj.has("zIndex")) {
            stats.zIndex = obj.get("zIndex").getAsInt();
        }
        
        // Parse hitbox
        if (obj.has("hitbox")) {
            JsonObject hitboxObj = obj.get("hitbox").getAsJsonObject();
            if (hitboxObj.has("offsetX")) {
                stats.hitboxOffsetX = hitboxObj.get("offsetX").getAsDouble();
            }
            if (hitboxObj.has("offsetY")) {
                stats.hitboxOffsetY = hitboxObj.get("offsetY").getAsDouble();
            }
            if (hitboxObj.has("width")) {
                stats.hitboxWidth = hitboxObj.get("width").getAsDouble();
            }
            if (hitboxObj.has("height")) {
                stats.hitboxHeight = hitboxObj.get("height").getAsDouble();
            }
        }
        
        // Parse turrets
        if (obj.has("turrets")) {
            JsonArray turretsArray = obj.get("turrets").getAsJsonArray();
            for (int i = 0; i < turretsArray.size(); i++) {
                TurretStats turretStats = parseTurretStats(turretsArray.get(i).getAsJsonObject());
                stats.turrets.add(turretStats);
            }
        }
        
        // Parse target weights
        if (obj.has("targetWeights")) {
            JsonObject weightsObj = obj.get("targetWeights").getAsJsonObject();
            for (String key : weightsObj.keySet()) {
                stats.targetWeights.put(key, weightsObj.get(key).getAsInt());
            }
        }
        
        return stats;
    }
    
    /**
     * Parse a turret stats object from JSON
     */
    private TurretStats parseTurretStats(JsonObject obj) {
        TurretStats stats = new TurretStats();
        stats.offsetX = obj.get("offsetX").getAsInt();
        stats.offsetY = obj.get("offsetY").getAsInt();
        stats.rotationSpeed = obj.get("rotationSpeed").getAsInt();
        stats.range = obj.get("range").getAsDouble();
        
        if (obj.has("targetInterval")) {
            stats.targetInterval = obj.get("targetInterval").getAsDouble();
        }
        if (obj.has("targetChance")) {
            stats.targetChance = obj.get("targetChance").getAsDouble();
        }
        if (obj.has("targetCooldown")) {
            stats.targetCooldown = obj.get("targetCooldown").getAsDouble();
        }
        if (obj.has("propName")) {
            stats.propName = obj.get("propName").getAsString();
        }
        if (obj.has("zIndex")) {
            stats.zIndex = obj.get("zIndex").getAsInt();
        }
        
        // Parse weapons
        if (obj.has("weapons")) {
            JsonArray weaponsArray = obj.get("weapons").getAsJsonArray();
            for (int i = 0; i < weaponsArray.size(); i++) {
                WeaponStats weaponStats = parseWeaponStats(weaponsArray.get(i).getAsJsonObject());
                stats.weapons.add(weaponStats);
            }
        }
        
        return stats;
    }
    
    /**
     * Parse a weapon definition from weapons.json
     */
    private WeaponDefinition parseWeaponDefinition(JsonObject obj) {
        WeaponDefinition stats = new WeaponDefinition();
        stats.name = obj.get("name").getAsString();
        stats.damage = obj.get("damage").getAsInt();
        stats.rof = obj.get("rof").getAsDouble();
        stats.projectileSpeed = obj.get("projectileSpeed").getAsDouble();
        stats.projectileType = obj.get("projectileType").getAsString();
        
        if (obj.has("pierce")) {
            stats.pierce = obj.get("pierce").getAsInt();
        }
        if (obj.has("burst")) {
            stats.burst = obj.get("burst").getAsInt();
        }
        if (obj.has("burstDelay")) {
            stats.burstDelay = obj.get("burstDelay").getAsDouble();
        }
        if (obj.has("spread")) {
            stats.spread = obj.get("spread").getAsDouble();
        }
        if (obj.has("description")) {
            stats.description = obj.get("description").getAsString();
        }
        
        return stats;
    }
    
    /**
     * Parse a weapon stats object from JSON (for use in turret definitions).
     * Now supports the clean format where only name and offset are specified in turrets,
     * and all other stats come from weapons.json.
     * 
     * Format in turrets:
     * { "name": "WeaponName", "offsetX": 10, "offsetY": 0 }
     * 
     * All stats (damage, rof, projectileType, etc.) are loaded from weapons.json via the name reference.
     */
    private WeaponStats parseWeaponStats(JsonObject obj) {
        WeaponStats stats = new WeaponStats();
        String weaponName = obj.get("name").getAsString();
        stats.name = weaponName;
        stats.offsetX = obj.get("offsetX").getAsInt();
        stats.offsetY = obj.get("offsetY").getAsInt();
        
        // Check if weapon is defined in weapons.json
        WeaponDefinition weaponDef = weaponStats.get(weaponName);
        if (weaponDef != null) {
            // Use stats from weapons.json
            stats.damage = weaponDef.damage;
            stats.rof = weaponDef.rof;
            stats.projectileSpeed = weaponDef.projectileSpeed;
            stats.projectileType = weaponDef.projectileType;
            stats.pierce = weaponDef.pierce;
            stats.burst = weaponDef.burst;
            stats.burstDelay = weaponDef.burstDelay;
            stats.spread = weaponDef.spread;
        } else {
            // Fallback: try to parse from inline definition (backwards compatibility)
            if (obj.has("damage")) {
                stats.damage = obj.get("damage").getAsInt();
            }
            if (obj.has("rof")) {
                stats.rof = obj.get("rof").getAsDouble();
            }
            if (obj.has("projectileSpeed")) {
                stats.projectileSpeed = obj.get("projectileSpeed").getAsDouble();
            }
            if (obj.has("projectileType")) {
                stats.projectileType = obj.get("projectileType").getAsString();
            }
            if (obj.has("pierce")) {
                stats.pierce = obj.get("pierce").getAsInt();
            }
            if (obj.has("burst")) {
                stats.burst = obj.get("burst").getAsInt();
            }
            if (obj.has("burstDelay")) {
                stats.burstDelay = obj.get("burstDelay").getAsDouble();
            }
            if (obj.has("spread")) {
                stats.spread = obj.get("spread").getAsDouble();
            }
        }
        
        return stats;
    }
    
    // ==================== GETTERS ====================
    
    public BuildingStats getBuildingStats(String name) {
        return buildingStats.get(name);
    }
    
    public EnemyStats getEnemyStats(String name) {
        return enemyStats.get(name);
    }
    
    public Map<String, BuildingStats> getAllBuildingStats() {
        return new HashMap<>(buildingStats);
    }
    
    public Map<String, EnemyStats> getAllEnemyStats() {
        return new HashMap<>(enemyStats);
    }
    
    public WeaponDefinition getWeaponStats(String name) {
        return weaponStats.get(name);
    }
    
    public Map<String, WeaponDefinition> getAllWeaponStats() {
        return new HashMap<>(weaponStats);
    }
}

/**
 * Holds weapon configuration data loaded from weapons.json
 */
class WeaponDefinition {
    public String name;
    public int damage;
    public double rof; // Rate of fire (cooldown between bursts in seconds)
    public double projectileSpeed;
    public String projectileType;
    public int pierce;
    public int burst; // Number of shots per burst (default 1)
    public double burstDelay; // Interval between shots in a burst
    public double spread; // Angular spread in degrees
    public String description;
}

