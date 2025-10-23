import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;

/**
 * Centralized input handler for the game.
 * Manages all keyboard and mouse input events.
 * 
 * Responsibilities:
 * - Key press/release handling (pause, repair, sell, show hitboxes)
 * - Mouse press/release handling (UI interactions, build mode, etc.)
 * - Mouse movement tracking (position updates)
 * - Tab selection (productive/offensive)
 * - Modal state transitions (pause/unpause, build/repair/sell modes)
 */
public class InputManager {
    
    // Reference to Gamma for state access and modification
    private Gamma gamma;
    
    public InputManager(Gamma gamma) {
        this.gamma = gamma;
    }
    
    /**
     * Handle key released events (primary input trigger point)
     */
    public void handleKeyReleased(KeyEvent e) {
        // Ignore non-ESC keys when paused
        if (gamma.currentState == Gamma.GameState.PAUSED && e.getKeyCode() != KeyEvent.VK_ESCAPE) {
            return;
        }
        
        switch (e.getKeyCode()) {
            case KeyEvent.VK_H:
                gamma.showHitboxes = !gamma.showHitboxes;
                break;
                
            case KeyEvent.VK_ESCAPE:
                if (gamma.currentState == Gamma.GameState.IN_GAME) {
                    gamma.currentState = Gamma.GameState.PAUSED;
                    gamma.gameRunning = false; // freeze updates
                } else if (gamma.currentState == Gamma.GameState.PAUSED) {
                    gamma.currentState = Gamma.GameState.IN_GAME;
                    gamma.gameRunning = true; // resume updates
                    gamma.lastUpdateTime = System.currentTimeMillis();
                }
                break;
                
            case KeyEvent.VK_Z:
                gamma.getBuildingManager().toggleRepairMode();
                break;
                
            case KeyEvent.VK_X:
                gamma.getBuildingManager().toggleSellMode();
                break;
        }
    }
    
    /**
     * Handle mouse press events
     */
    public void handleMousePressed(MouseEvent e) {
        // Menus (main menu, map select, mode select) - handled in paintComponent
        if (gamma.currentState != Gamma.GameState.IN_GAME && gamma.currentState != Gamma.GameState.PAUSED) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                gamma.m1 = true;
                gamma.m1Hold = true;
            }
            if (SwingUtilities.isRightMouseButton(e)) {
                gamma.m2 = true;
                gamma.m2Hold = true;
            }
            return;
        }

        // Paused state: set flags but let paintComponent handle logic
        if (gamma.currentState == Gamma.GameState.PAUSED) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                gamma.m1 = true;
                gamma.m1Hold = true;
            }
            if (SwingUtilities.isRightMouseButton(e)) {
                gamma.m2 = true;
                gamma.m2Hold = true;
            }
            return;
        }

        // In-game UI interactions - tab selection
        if (gamma.mx >= 1591 && gamma.mx <= gamma.uiMid && gamma.my >= 168 && gamma.my <= 200) {
            gamma.tabSelected = "productive";
        } else if (gamma.mx >= gamma.uiMid && gamma.mx <= gamma.uiMid + 130 && gamma.my >= 168 && gamma.my <= 200) {
            gamma.tabSelected = "offensive";
        }
        
        // Left mouse button
        if (SwingUtilities.isLeftMouseButton(e)) {
            gamma.m1 = true;
            gamma.m1Hold = true;
        }
        
        // Right mouse button
        if (SwingUtilities.isRightMouseButton(e)) {
            gamma.m2 = true;
            gamma.m2Hold = true;
            // Clear all build/repair/sell modes on right click
            gamma.getBuildingManager().clearAllModes();
        }
    }

    /**
     * Handle mouse release events
     */
    public void handleMouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            gamma.m1Timer = 0;
            gamma.m1Hold = false;
        }
        if (SwingUtilities.isRightMouseButton(e)) {
            gamma.m2Timer = 0;
            gamma.m2Hold = false;
        }
    }

    /**
     * Handle mouse dragged events (update position)
     */
    public void handleMouseDragged(MouseEvent e) {
        updateMousePosition(e);
    }

    /**
     * Handle mouse moved events (update position)
     */
    public void handleMouseMoved(MouseEvent e) {
        updateMousePosition(e);
    }

    /**
     * Update mouse position and determine if cursor is over game area or UI
     */
    private void updateMousePosition(MouseEvent e) {
        gamma.mx = e.getX();
        gamma.my = e.getY();
        
        if (gamma.mx >= 0 && gamma.mx < Gamma.GAME_WIDTH && gamma.my >= 0 && gamma.my < Gamma.HEIGHT) {
            gamma.status = "game";
        } else {
            gamma.status = "ui";
        }
    }
    
    /**
     * Empty stubs for unused event methods (required by listeners)
     */
    public void handleKeyTyped(KeyEvent e) {
        // Not used
    }
    
    public void handleKeyPressed(KeyEvent e) {
        // Not used
    }
    
    public void handleMouseClicked(MouseEvent e) {
        // Not used
    }
    
    public void handleMouseEntered(MouseEvent e) {
        // Not used
    }
    
    public void handleMouseExited(MouseEvent e) {
        // Not used
    }
}
