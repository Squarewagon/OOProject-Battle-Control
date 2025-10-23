import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Centralized manager for all building-related logic.
 * Manages build mode, repair mode, sell mode, and build preview state.
 * 
 * Responsibilities:
 * - Build mode state management (what to build, preview info)
 * - Repair mode interactions (toggle repairing on buildings)
 * - Sell mode interactions (refund and remove buildings)
 * - Build preview rendering data (turret offsets, ranges, buildable cells)
 * - Mode transitions (mutual exclusivity of build/repair/sell)
 * 
 * Note: Icon, Turret, and related classes are inner classes in Gamma,
 * so this manager keeps state synchronized with Gamma's public fields.
 */
public class BuildingManager {
    
    // Repair and Sell modes (Build mode stays in Gamma.buildMode for now)
    private boolean repairMode = false;
    private boolean sellMode = false;
    
    // Reference to Gamma for state access
    private Gamma gamma;
    
    public BuildingManager(Gamma gamma) {
        this.gamma = gamma;
    }
    
    // ==================== BUILD MODE ====================
    // Note: buildMode, buildingToBuild, turretOffsets, etc. remain in Gamma
    // for now since they involve inner classes. This manager provides
    // mode transition logic and interaction handling.
    
    public void clearBuildMode() {
        gamma.buildMode = false;
        gamma.buildingToBuild = null;
        gamma.iconToBuild = null;
        gamma.turretOffsets.clear();
        gamma.turretRanges.clear();
        gamma.previewWidth = 0;
        gamma.previewHeight = 0;
        gamma.proOnCons = false;
        gamma.offOnCons = false;
        gamma.buildable.clear();
        gamma.unbuildable.clear();
    }
    
    // ==================== REPAIR MODE ====================
    
    public void setRepairMode(boolean enabled) {
        if (enabled && sellMode) {
            sellMode = false;  // Can't be both
        }
        if (enabled && gamma.buildMode) {
            clearBuildMode();  // Exit build mode when entering repair
        }
        repairMode = enabled;
    }
    
    public boolean isRepairMode() {
        return repairMode;
    }
    
    public void toggleRepairMode() {
        setRepairMode(!repairMode);
    }
    
    // ==================== SELL MODE ====================
    
    public void setSellMode(boolean enabled) {
        if (enabled && repairMode) {
            repairMode = false;  // Can't be both
        }
        if (enabled && gamma.buildMode) {
            clearBuildMode();  // Exit build mode when entering sell
        }
        sellMode = enabled;
    }
    
    public boolean isSellMode() {
        return sellMode;
    }
    
    public void toggleSellMode() {
        setSellMode(!sellMode);
    }
    
    // ==================== INTERACTIONS ====================
    
    /**
     * Process sell/repair interactions at the given cell
     * @param cellX the grid X coordinate
     * @param cellY the grid Y coordinate
     * @param gameManager reference to GameManager for resource management
     * @return true if interaction was successful
     */
    public boolean processCellInteraction(int cellX, int cellY, GameManager gameManager) {
        if (!(sellMode || repairMode)) return false;
        
        // Get building at this cell
        Building target = Location.buildingOccupancy.get(new Point(cellX, cellY));
        if (target == null) return false;
        
        if (sellMode && !(target instanceof Headquarter)) {
            // Delegate sell logic to Gamma since it has access to Icon class
            return gamma.sellBuilding(target);
        } else if (repairMode) {
            // Toggle repair mode on the building
            if (target.health >= target.maxHealth) {
                gamma.error("already max health");
            } else {
                target.repairing = !target.repairing;
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Clear all build/repair/sell modes
     */
    public void clearAllModes() {
        clearBuildMode();
        repairMode = false;
        sellMode = false;
    }
    
    /**
     * Get the desired cursor based on current mode
     * @return cursor name ("build_cursor", "repair_cursor", "sell_cursor", or "normal_cursor")
     */
    public String getDesiredCursor() {
        if (gamma.buildMode) return "build_cursor";
        if (repairMode) return "repair_cursor";
        if (sellMode) return "sell_cursor";
        return "normal_cursor";
    }
}
