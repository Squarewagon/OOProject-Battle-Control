import java.util.List;

/**
 * Manages game loop timing and update logic.
 * Separates timing concerns from rendering.
 * 
 * Responsibilities:
 * - Delta time calculation
 * - Game state updates (instances, wave, range boost)
 * - Build/repair/sell mode interactions
 * - Cursor updates based on game mode
 */
public class GameLoop {
    private long lastUpdateTime = 0;
    private int timer = 0; // Increments each update, used for animations
    
    private final GameManager gameManager;
    
    public GameLoop(GameManager gameManager) {
        this.gameManager = gameManager;
    }
    
    /**
     * Reset the game loop state (called when starting a new game)
     */
    public void reset() {
        lastUpdateTime = 0;
        timer = 0;
    }
    
    /**
     * Calculate delta time based on current time and time scale.
     * Also accounts for wave 0 which updates slower initially.
     */
    public float calculateDeltaTime(boolean fastForward, int wave) {
        long currentTime = System.currentTimeMillis();
        float timeScale = Math.min(fastForward ? 500.0f : 1000.0f, wave == 0 ? 10.0f : 1000.0f);
        float deltaTime = lastUpdateTime == 0 ? 0 : (currentTime - lastUpdateTime) / timeScale;
        lastUpdateTime = currentTime;
        return deltaTime;
    }
    
    /**
     * Increment the universal game timer (used for animations and timing)
     */
    public void tick() {
        timer += 1;
    }
    
    /**
     * Get the current universal game timer
     */
    public int getTimer() {
        return timer;
    }
    
    /**
     * Update all game objects and state.
     * This is the main update loop.
     */
    public void updateGameObjects(float deltaTime, 
                          List<Instance> instances, List<Instance> iQueue) {
        
        // Update wave system
        int currentWave = gameManager.getWave();
        WaveManager.update(deltaTime, currentWave);
        int newWave = WaveManager.updateWaveCompletion(deltaTime, currentWave);
        if (newWave != currentWave) {
            gameManager.setWave(newWave);
        }

        // Apply dynamic range boost from Radar Dish (if present)
        boolean hasRadar = false;
        for (Instance inst : instances) {
            if (inst instanceof RadarDish && inst.isAlive()) {
                hasRadar = true;
                break;
            }
        }
        double rangeBoost = hasRadar ? 1.25 : 1.0;
        for (Instance inst : instances) {
            if (inst instanceof Building) {
                inst.rangeMult = rangeBoost;
            }
        }

        // Update all instances
        for (Instance instance : instances) {
            if (instance.isAlive()) {
                instance.update(deltaTime);
                instance.routine(deltaTime);
                for (Turret turret : instance.turrets) {
                    turret.update(deltaTime);
                }
                for (Weapon weapon : instance.weapons) {
                    weapon.update(deltaTime);
                }
                for (Utilities.Animation anim : instance.anims) {
                    anim.update(deltaTime);
                }
                // Remove dead animations
                instance.anims.removeIf(anim -> !anim.isAlive());
            }
        }
        
        // Add queued instances and remove dead ones
        if (!iQueue.isEmpty()) {
            instances.addAll(iQueue);
            iQueue.clear();
        }
        instances.removeIf(instance -> !instance.isAlive());
    }
    
    /**
     * Determine which cursor should be displayed based on game mode
     */
    public String getDesiredCursor(boolean buildMode, boolean repairMode, boolean sellMode) {
        if (buildMode) {
            return "normal_cursor"; // placeholder for build cursor
        } else if (repairMode) {
            return "repair_cursor";
        } else if (sellMode) {
            return "sell_cursor";
        }
        return "normal_cursor";
    }
}
