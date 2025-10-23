import java.util.ArrayList;
import java.util.List;

/**
 * Centralized manager for all game state.
 * Replaces static fields scattered throughout Gamma class.
 * This allows for easier testing, multiple game instances, and cleaner architecture.
 */
public class GameManager {
    private static GameManager instance;
    
    private List<Instance> instances;
    private List<Instance> instanceQueue; // Buffer for new instances added during updates
    private MapManager currentMap;
    private ConfigManager configManager;
    
    private int power;
    private int kromer;
    private int wave;
    
    public GameManager() {
        this.instances = new ArrayList<>();
        this.instanceQueue = new ArrayList<>();
        this.currentMap = new MapManager();
        this.configManager = new ConfigManager();
        
        this.power = 0;
        this.kromer = 1000;
        this.wave = 0;
        
        // Set as singleton instance
        if (instance == null) {
            instance = this;
        }
    }
    
    public static GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }
    
    // ==================== INSTANCES ====================
    
    public boolean addInstance(Instance instance, boolean restricted) {
        // Validation happens here before adding
        if (instance instanceof Building) {
            Building building = (Building) instance;
            if (!canPlaceBuilding(building.x, building.y, building.width, building.height, restricted)) {
                return false;
            }
        }
        instanceQueue.add(instance);
        return true;
    }
    
    public void addInstance(Instance instance) {
        addInstance(instance, false);
    }
    
    /**
     * Flushes the instance queue into the main instances list.
     * Call this once per update cycle.
     */
    public void flushInstanceQueue() {
        instances.addAll(instanceQueue);
        instanceQueue.clear();
    }
    
    /**
     * Returns the actual instances list (not a copy).
     * This allows external code to call .clear() and have it affect the real list.
     */
    public List<Instance> getInstances() {
        return instances;
    }
    
    public void removeDeadInstances() {
        instances.removeIf(instance -> !instance.isAlive());
    }
    
    private boolean canPlaceBuilding(int x, int y, int width, int height, boolean restricted) {
        // Check if building would go outside map bounds
        if (x < 0 || y < 0 || x + width > Location.cols || y + height > Location.rows) {
            return false;
        }
        
        for (int bx = x; bx < x + width; bx++) {
            for (int by = y; by < y + height; by++) {
                if (Location.occupancy.contains(new java.awt.Point(bx, by))) {
                    return false;
                }
            }
        }
        return true;
    }
    
    // ==================== RESOURCES ====================
    
    public int getPower() {
        return power;
    }
    
    public void setPower(int power) {
        this.power = power;
    }
    
    public void addPower(int amount) {
        this.power += amount;
    }
    
    public int getKromer() {
        return kromer;
    }
    
    public void setKromer(int kromer) {
        this.kromer = kromer;
    }
    
    public void addKromer(int amount) {
        this.kromer += amount;
    }
    
    /**
     * Initialize Kromer based on game mode
     */
    public void initializeKromerForMode(String mode) {
        if ("sandbox".equalsIgnoreCase(mode)) {
            this.kromer = 999999;
        } else {
            // normal, paper armor, blitzkrieg all start with 1000
            this.kromer = 1000;
        }
    }
    
    // ==================== WAVE ====================
    
    public int getWave() {
        return wave;
    }
    
    public void setWave(int wave) {
        this.wave = wave;
    }
    
    public void nextWave() {
        this.wave++;
    }
    
    // ==================== MAP ====================
    
    public MapManager getMap() {
        return currentMap;
    }
    
    public void setMap(MapManager map) {
        this.currentMap = map;
    }
    
    // ==================== CONFIG ====================
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    // ==================== RESET ====================
    
    /**
     * Clears all game state for a new game.
     */
    public void reset() {
        instances.clear();
        instanceQueue.clear();
        power = 0;
        kromer = 1000;
        wave = 0;
        currentMap.clear();
    }
    
    /**
     * Full reset including clearing all game state back to initial values
     */
    public void fullReset() {
        reset();
        power = 0;
        wave = 0;
    }
}
