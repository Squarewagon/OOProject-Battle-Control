import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.ArrayList;

/**
 * RenderSystem - Centralized rendering for the game.
 * Separates rendering concerns from game logic.
 * 
 * Responsibilities:
 * - renderMenus() - Main menu, map select, mode select states
 * - renderPausedGame() - Paused overlay with pause menu
 * - renderGameArea() - In-game map, instances, animations, health bars, ranges
 * - renderUIPanel() - Stats, building list, wave button, error messages
 * - renderBuildMode() - Build preview overlay and buildable areas
 */
public class RenderSystem {
    
    private Gamma gamma;
    
    // Cached colors for performance
    private static final Color BUILDABLE_COLOR = new Color(0, 255, 0, 100);
    private static final Color UNBUILDABLE_COLOR = new Color(255, 0, 0, 100);
    private static final Color PREVIEW_COLOR = new Color(255, 255, 255, 150);
    private static final BasicStroke PREVIEW_STROKE_THICK = new BasicStroke(2);
    private static final BasicStroke PREVIEW_STROKE_THIN = new BasicStroke(1);
    
    // Cached menu background image
    private BufferedImage menuBackgroundImg;
    private BufferedImage menuButtonImg;
    
    public RenderSystem(Gamma gamma) {
        this.gamma = gamma;
        // Pre-load menu assets
        this.menuBackgroundImg = Utilities.load("title", 1920.0 / 1920, 1080.0 / 1080);
        this.menuButtonImg = Utilities.load("menu_button", 1.0, 1.0);
    }
    
    /**
     * Main render entry point - handles all states
     */
    public void render(Graphics2D g2d) {
        // Enable anti-aliasing for smoother graphics
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Menus vs. Game (treat PAUSED separately)
        if (gamma.currentState != Gamma.GameState.IN_GAME && gamma.currentState != Gamma.GameState.PAUSED) {
            renderMenus(g2d);
            return;
        }
        
        if (gamma.currentState == Gamma.GameState.PAUSED) {
            renderPausedGame(g2d);
            return;
        }
        
        // In-game rendering
        renderGameArea(g2d);
        renderBuildMode(g2d);
        renderUIPanel(g2d);
    }
    
    // ========== MENU RENDERING ==========
    
    private void renderMenus(Graphics2D g2d) {
        // Draw cached background image with reduced opacity
        if (menuBackgroundImg != null) {
            Composite orig = g2d.getComposite();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            g2d.drawImage(menuBackgroundImg, 0, 0, 1920, 1080, null);
            g2d.setComposite(orig);
        }
        
        g2d.setFont(Utilities.loadFont("Romanov", Font.BOLD, 64f));
        g2d.setColor(Color.WHITE);
        String title = "Battle Control";
        FontMetrics tfm = g2d.getFontMetrics();
        g2d.drawString(title, (1920 - tfm.stringWidth(title)) / 2, 220);
        
        // Shared button image (drawn at original size; top-left anchored)
        int btnW = menuButtonImg != null ? menuButtonImg.getWidth() : 300;
        int btnH = menuButtonImg != null ? menuButtonImg.getHeight() : 60;
        int vGap = btnH + 24; // vertical spacing between buttons
        int bx = 810; // chosen left offset for all menu buttons (top-left placement)
        
        g2d.setFont(Utilities.loadFont("Romanov", Font.BOLD, 28f));
        FontMetrics bfm = g2d.getFontMetrics();
        
        if (gamma.currentState == Gamma.GameState.MAIN_MENU) {
            renderMainMenu(g2d, menuButtonImg, bx, btnW, btnH, vGap, bfm);
        } else if (gamma.currentState == Gamma.GameState.MAP_SELECT) {
            renderMapSelect(g2d, menuButtonImg, bx, btnW, btnH, vGap, bfm);
        } else if (gamma.currentState == Gamma.GameState.MODE_SELECT) {
            renderModeSelect(g2d, menuButtonImg, bx, btnW, btnH, vGap, bfm);
        }
    }
    
    private void renderMainMenu(Graphics2D g2d, BufferedImage btnImg, int bx, int btnW, int btnH, int vGap, FontMetrics bfm) {
        int byStart = 420;
        int byExit = byStart + vGap;
        
        // Draw buttons
        if (btnImg != null) g2d.drawImage(btnImg, bx, byStart, null);
        if (btnImg != null) g2d.drawImage(btnImg, bx, byExit, null);
        
        // Optional text overlay (centered horizontally and vertically)
        String startText = "Start";
        String exitText = "Exit";
        g2d.setColor(Color.WHITE);
        g2d.drawString(startText, bx + (btnW - bfm.stringWidth(startText)) / 2,
                byStart + (btnH - (bfm.getAscent() + bfm.getDescent())) / 2 + bfm.getAscent());
        g2d.drawString(exitText, bx + (btnW - bfm.stringWidth(exitText)) / 2,
                byExit + (btnH - (bfm.getAscent() + bfm.getDescent())) / 2 + bfm.getAscent());
        
        Rectangle startRect = new Rectangle(bx, byStart, btnW, btnH);
        Rectangle exitRect = new Rectangle(bx, byExit, btnW, btnH);
        
        if (gamma.m1) {
            if (startRect.contains(gamma.mx, gamma.my)) {
                gamma.currentState = Gamma.GameState.MAP_SELECT;
                gamma.m1 = false;
            } else if (exitRect.contains(gamma.mx, gamma.my)) {
                System.exit(0);
            }
        }
    }
    
    private void renderMapSelect(Graphics2D g2d, BufferedImage btnImg, int bx, int btnW, int btnH, int vGap, FontMetrics bfm) {
        String prompt = "Select Map";
        g2d.drawString(prompt, (1920 - bfm.stringWidth(prompt)) / 2, 320);
        
        // List of available maps
        String[] maps = new String[] { "Plain", "Outer Space" };
        int by = 420;
        ArrayList<Rectangle> mapRects = new ArrayList<>();
        
        // Draw map buttons
        for (int i = 0; i < maps.length; i++) {
            int y = by + i * vGap;
            if (btnImg != null) g2d.drawImage(btnImg, bx, y, null);
            g2d.setColor(Color.WHITE);
            g2d.drawString(maps[i], bx + (btnW - bfm.stringWidth(maps[i])) / 2,
                    y + (btnH - (bfm.getAscent() + bfm.getDescent())) / 2 + bfm.getAscent());
            mapRects.add(new Rectangle(bx, y, btnW, btnH));
        }
        
        // Back button
        int yBack = by + maps.length * vGap;
        if (btnImg != null) g2d.drawImage(btnImg, bx, yBack, null);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Back", bx + (btnW - bfm.stringWidth("Back")) / 2,
                yBack + (btnH - (bfm.getAscent() + bfm.getDescent())) / 2 + bfm.getAscent());
        Rectangle backRect = new Rectangle(bx, yBack, btnW, btnH);
        
        if (gamma.m1) {
            // Map selection
            for (int i = 0; i < maps.length; i++) {
                if (mapRects.get(i).contains(gamma.mx, gamma.my)) {
                    gamma.selectedMap = maps[i].toLowerCase();
                    gamma.currentState = Gamma.GameState.MODE_SELECT;
                    gamma.m1 = false;
                    return;
                }
            }
            // Back button
            if (backRect.contains(gamma.mx, gamma.my)) {
                gamma.currentState = Gamma.GameState.MAIN_MENU;
                gamma.m1 = false;
            }
        }
    }
    
    private void renderModeSelect(Graphics2D g2d, BufferedImage btnImg, int bx, int btnW, int btnH, int vGap, FontMetrics bfm) {
        String prompt = "Select Mode";
        g2d.drawString(prompt, (1920 - bfm.stringWidth(prompt)) / 2, 320);
        
        String[] modes = new String[] { "Normal", "Paper Armor", "Blitzkrieg", "Sandbox" };
        int by = 420;
        ArrayList<Rectangle> modeRects = new ArrayList<>();
        for (int i = 0; i < modes.length; i++) {
            int y = by + i * vGap;
            if (btnImg != null) g2d.drawImage(btnImg, bx, y, null);
            g2d.setColor(Color.WHITE);
            g2d.drawString(modes[i], bx + (btnW - bfm.stringWidth(modes[i])) / 2,
                    y + (btnH - (bfm.getAscent() + bfm.getDescent())) / 2 + bfm.getAscent());
            modeRects.add(new Rectangle(bx, y, btnW, btnH));
        }
        
        int yBack = by + modes.length * vGap;
        if (btnImg != null) g2d.drawImage(btnImg, bx, yBack, null);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Back", bx + (btnW - bfm.stringWidth("Back")) / 2,
                yBack + (btnH - (bfm.getAscent() + bfm.getDescent())) / 2 + bfm.getAscent());
        Rectangle backRect = new Rectangle(bx, yBack, btnW, btnH);
        
        if (gamma.m1) {
            // mode clicks
            for (int i = 0; i < modes.length; i++) {
                if (modeRects.get(i).contains(gamma.mx, gamma.my)) {
                    Gamma.selectedMode = modes[i].toLowerCase();
                    gamma.startGame();
                    gamma.m1 = false;
                    return;
                }
            }
            // back
            if (backRect.contains(gamma.mx, gamma.my)) {
                gamma.currentState = Gamma.GameState.MAP_SELECT;
                gamma.m1 = false;
            }
        }
    }
    
    // ========== PAUSED GAME RENDERING ==========
    
    private void renderPausedGame(Graphics2D g2d) {
        // Draw frozen game underneath
        renderGameArea(g2d);
        renderBuildMode(g2d);
        renderUIPanel(g2d);
        
        // Dim overlay
        Composite orig = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, 1920, 1080);
        g2d.setComposite(orig);
        
        // Optional background image centered at 960, 540
        BufferedImage pauseImg = Utilities.load("pause_ui", 1.0, 1.0);
        if (pauseImg != null) {
            g2d.drawImage(pauseImg, 960 - pauseImg.getWidth() / 2, 540 - pauseImg.getHeight() / 2, null);
        }
        
        // Title
        g2d.setFont(Utilities.loadFont("Romanov", Font.BOLD, 40f));
        FontMetrics titleFm = g2d.getFontMetrics();
        String title = "Paused";
        int titleX = (1920 - titleFm.stringWidth(title)) / 2;
        int titleY = 420;
        g2d.setColor(Color.WHITE);
        g2d.drawString(title, titleX, titleY);
        
        // Image buttons (top-left anchored), no stretching
        BufferedImage btnImg = Utilities.load("pause_button", 1.0, 1.0);
        int btnW = btnImg != null ? btnImg.getWidth() : 220;
        int btnH = btnImg != null ? btnImg.getHeight() : 56;
        int bx = 850; // chosen left offset for pause buttons
        int topY = 470;
        int vGap = btnH + 20;
        
        g2d.setFont(Utilities.loadFont("Romanov", Font.BOLD, 28f));
        FontMetrics fm = g2d.getFontMetrics();
        
        // Draw three buttons
        if (btnImg != null) g2d.drawImage(btnImg, bx, topY, null);
        if (btnImg != null) g2d.drawImage(btnImg, bx, topY + vGap, null);
        if (btnImg != null) g2d.drawImage(btnImg, bx, topY + vGap * 2, null);
        
        // Text overlays centered relative to button image size
        String contText = "Continue";
        String menuText = "Menu";
        String quitText = "Quit";
        g2d.setColor(Color.WHITE);
        
        g2d.drawString(contText, bx + (btnW - fm.stringWidth(contText)) / 2,
                topY + (btnH - (fm.getAscent() + fm.getDescent())) / 2 + fm.getAscent());
        g2d.drawString(menuText, bx + (btnW - fm.stringWidth(menuText)) / 2,
                topY + vGap + (btnH - (fm.getAscent() + fm.getDescent())) / 2 + fm.getAscent());
        g2d.drawString(quitText, bx + (btnW - fm.stringWidth(quitText)) / 2,
                topY + vGap * 2 + (btnH - (fm.getAscent() + fm.getDescent())) / 2 + fm.getAscent());
        
        Rectangle continueRect = new Rectangle(bx, topY, btnW, btnH);
        Rectangle menuRect = new Rectangle(bx, topY + vGap, btnW, btnH);
        Rectangle quitRect = new Rectangle(bx, topY + vGap * 2, btnW, btnH);
        
        if (gamma.m1) {
            if (continueRect.contains(gamma.mx, gamma.my)) {
                gamma.currentState = Gamma.GameState.IN_GAME;
                gamma.gameRunning = true;
                gamma.lastUpdateTime = System.currentTimeMillis();
                gamma.m1 = false;
            } else if (menuRect.contains(gamma.mx, gamma.my)) {
                gamma.goToMainMenu();
                gamma.m1 = false;
            } else if (quitRect.contains(gamma.mx, gamma.my)) {
                System.exit(0);
            }
        }
    }
    
    // ========== IN-GAME RENDERING ==========
    
    public void renderGameArea(Graphics2D g2d) {
        g2d.setColor(Location.bg);
        g2d.fillRect(0, 0, Gamma.GAME_WIDTH, Gamma.HEIGHT);
        g2d.setColor(Location.pc);
        for (Point p : Location.path) {
            g2d.fillRect(p.x * Location.cellSize, p.y * Location.cellSize, Location.cellSize, Location.cellSize);
        }
        g2d.setColor(new Color(255, 255, 255, 30));
        for (int x = 0; x < Gamma.GAME_WIDTH; x += Location.cellSize) {
            g2d.drawLine(x, 0, x, Gamma.HEIGHT);
        }
        for (int y = 0; y < Gamma.HEIGHT; y += Location.cellSize) {
            g2d.drawLine(0, y, Gamma.GAME_WIDTH, y);
        }
        
        // draw all instances in order of zIndex
        List<Elements> renderQueue = new ArrayList<>();
        for (Instance instance : GameManager.getInstance().getInstances()) {
            renderQueue.add(new Elements(instance, instance.zIndex));
            for (Turret turret : instance.turrets) {
                renderQueue.add(new Elements(turret, turret.zIndex));
            }
            for (Utilities.Animation anim : instance.anims) {
                renderQueue.add(new Elements(anim::render, 50)); // High z-index for animations
            }
        }
        renderQueue.sort(Comparator.comparingInt(obj -> obj.zIndex));

        for (Elements obj : renderQueue) {
            obj.r.render(g2d);
        }
        
        // Draw repair indicators last - on top of everything else
        for (Instance instance : GameManager.getInstance().getInstances()) {
            if (instance instanceof Building) {
                Building building = (Building) instance;
                if (building.repairing) {
                    BufferedImage repairImg = Utilities.load("repairing", 1.0, 1.0);
                    if (repairImg != null) {
                        int centerX = building.x * Location.cellSize + (building.width * Location.cellSize) / 2;
                        int centerY = building.y * Location.cellSize + (building.height * Location.cellSize) / 2;
                        g2d.drawImage(repairImg, centerX - 20, centerY - 20, 40, 40, null);
                    }
                }
            }
        }
        
        // When hovering over an instance, draw its range and health bar
        renderInstanceHoverInfo(g2d);
        
        // hitbox draw last
        if (gamma.showHitboxes) {
            drawHitbox(g2d);
        }
    }
    
    private void renderInstanceHoverInfo(Graphics2D g2d) {
        Instance hoveredInstance = null;
        for (Instance instance : GameManager.getInstance().getInstances()) {
            if (!instance.isAlive()) continue;
            // Use hitboxes for accurate hover detection
            for (Hitbox hitbox : instance.hitboxes) {
                Polygon poly = new Polygon();
                for (Point corner : hitbox.corners) {
                    poly.addPoint(corner.x, corner.y);
                }
                if (poly.contains(gamma.mx, gamma.my) && gamma.currentState != Gamma.GameState.PAUSED) {
                    hoveredInstance = instance;
                    break;
                }
            }
            if (hoveredInstance != null) break;
        }
        
        if (hoveredInstance != null) {
            // Draw range for turrets (if any) considering global range multiplier
            for (Turret turret : hoveredInstance.turrets) {
                int centerX = (int) turret.exactX;
                int centerY = (int) turret.exactY;
                int rangeRadius = (int) (turret.range * hoveredInstance.rangeMult * Location.cellSize);
                g2d.setColor(PREVIEW_COLOR);
                g2d.setStroke(PREVIEW_STROKE_THICK);
                g2d.drawOval(centerX - rangeRadius, centerY - rangeRadius, rangeRadius * 2, rangeRadius * 2);
            }
            
            // Draw health bar above the instance, centered based on hitbox size
            int barHeight = 5;
            int barWidth;
            int drawX, drawY;
            
            // Determine hitbox size for health bar width
            double hitboxW = 1, hitboxH = 1;
            if (!hoveredInstance.hitboxes.isEmpty()) {
                Hitbox hb = hoveredInstance.hitboxes.get(0);
                hitboxW = hb.width;
                hitboxH = hb.height;
            }
            int maxDim = (int) Math.round(Math.max(hitboxW, hitboxH) * Location.cellSize);
            barWidth = Math.max(60, maxDim * 2);
            
            if (hoveredInstance instanceof Building) {
                // Center above the building using width
                Building b = (Building) hoveredInstance;
                int px = b.x * Location.cellSize;
                int py = b.y * Location.cellSize;
                int w = b.width * Location.cellSize;
                drawX = px + (w - barWidth) / 2;
                drawY = py - 18;
            } else {
                // Center above instance using exactX
                drawX = (int) hoveredInstance.exactX - barWidth / 2;
                drawY = (int) hoveredInstance.exactY - maxDim / 2 - 18;
            }
            
            double healthPercent = Math.max(0, Math.min(1.0, hoveredInstance.health / (double) hoveredInstance.maxHealth));
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(drawX, drawY, barWidth, barHeight);
            g2d.setColor(Color.GREEN);
            g2d.fillRect(drawX, drawY, (int) (barWidth * healthPercent), barHeight);
            g2d.setColor(Color.BLACK);
            g2d.setStroke(PREVIEW_STROKE_THIN);
            g2d.drawRect(drawX, drawY, barWidth, barHeight);
            
            // Draw health text
            String healthText = hoveredInstance.health + " / " + hoveredInstance.maxHealth;
            g2d.setFont(Utilities.loadFont("Romanov", Font.BOLD, 12f));
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(healthText);
            g2d.setColor(Color.WHITE);
            g2d.drawString(healthText, drawX + (barWidth - textWidth) / 2, drawY + barHeight + fm.getAscent());
        }
    }
    
    public void renderUIPanel(Graphics2D g2d) {
        // ui setup
        g2d.drawImage(Utilities.load("ui", 1.0, 1.0), Gamma.GAME_WIDTH, 0, null);
        g2d.setFont(Utilities.loadFont("Romanov", Font.BOLD, 16f));
        
        // Draw Power centered at (uiMid - 120, 90)
        String powerStr = "Power: " + GameManager.getInstance().getPower();
        FontMetrics fm = g2d.getFontMetrics();
        int powerWidth = fm.stringWidth(powerStr);
        int powerX = gamma.uiMid - 90 - powerWidth / 2;
        int powerY = 85 + fm.getAscent() / 2 - fm.getDescent() / 2;
        int power = GameManager.getInstance().getPower();
        if (power < 0)
            g2d.setColor(Color.RED);
        else if (power == 0)
            g2d.setColor(Color.YELLOW);
        else
            g2d.setColor(Color.GREEN);
        g2d.drawString(powerStr, powerX, powerY);
        
        // Draw Kromer centered at (uiMid + 120, 90)
        String kromerStr = "Kromer: " + GameManager.getInstance().getKromer();
        int kromerWidth = fm.stringWidth(kromerStr);
        int kromerX = gamma.uiMid + 90 - kromerWidth / 2;
        int kromerY = 85 + fm.getAscent() / 2 - fm.getDescent() / 2;
        g2d.setColor(Color.YELLOW);
        g2d.drawString(kromerStr, kromerX, kromerY);
        
        g2d.setFont(Utilities.loadFont("Romanov", Font.BOLD, 20f));
        fm = g2d.getFontMetrics();
        
        // Draw tab buttons
        if (gamma.tabSelected.equals("productive")) {
            g2d.drawImage(Utilities.load("productiveSelect", 1.0, 1.0), 1591, 168, null);
            g2d.setColor(Color.BLACK);
            g2d.drawString("Productive", 1591 + (gamma.uiMid - 1591) / 2 - fm.stringWidth("Productive") / 2, 190);
            g2d.setColor(Color.WHITE);
            g2d.drawString("Offensive", gamma.uiMid + (gamma.uiMid - 1591) / 2 - fm.stringWidth("Offensive") / 2, 190);
        } else {
            g2d.drawImage(Utilities.load("offensiveSelect", 1.0, 1.0), 1591, 168, null);
            g2d.setColor(Color.WHITE);
            g2d.drawString("Productive", 1591 + (gamma.uiMid - 1591) / 2 - fm.stringWidth("Productive") / 2, 190);
            g2d.setColor(Color.BLACK);
            g2d.drawString("Offensive", gamma.uiMid + (gamma.uiMid - 1591) / 2 - fm.stringWidth("Offensive") / 2, 190);
        }
        
        renderBuildingList(g2d);
        
        renderErrorMessage(g2d);
        
        // Wave button and wave display
        Font buttonFont = Utilities.loadFont("Romanov", Font.BOLD, 20f);
        FontMetrics buttonFm = g2d.getFontMetrics(buttonFont);
        g2d.setFont(buttonFont);
        fm = buttonFm;
        g2d.setColor(Color.WHITE);
        
        String buttonText;
        int wave = GameManager.getInstance().getWave();
        if (wave == 0) {
            buttonText = "Start Wave 1";
        } else if (WaveManager.waveActive) {
            buttonText = gamma.fastForward ? "Fast Forward ON" : "Fast Forward OFF";
        } else if (WaveManager.waveCompleted) {
            buttonText = gamma.fastForward ? "Fast Forward ON" : "Fast Forward OFF";
        } else {
            buttonText = gamma.fastForward ? "Fast Forward ON" : "Fast Forward OFF";
        }
        g2d.drawString(buttonText, gamma.uiMid - fm.stringWidth(buttonText) / 2, 955);
        
        g2d.setFont(Utilities.loadFont("Romanov", Font.PLAIN, 30f));
        fm = g2d.getFontMetrics();
        
        String waveDisplay;
        if (wave == 0) {
            waveDisplay = "Intermission";
        } else if (WaveManager.waveCompleted) {
            waveDisplay = "Wave " + WaveManager.conqueredWave + " Completed!";
        } else {
            waveDisplay = "Wave: " + wave;
        }
        g2d.drawString(waveDisplay, gamma.uiMid - fm.stringWidth(waveDisplay) / 2, 1010);
        
        if (gamma.mx >= gamma.uiMid - 130 && gamma.mx <= gamma.uiMid + 130 && gamma.my >= 930 && gamma.my <= 967) {
            // mouse over start wave button, or other function after starting the wave
            if (gamma.m1 && wave == 0 && !WaveManager.waveActive) {
                // Start wave 1 from intermission
                GameManager.getInstance().setWave(1);
                WaveManager.startWave(1);
            } else if (gamma.m1 && wave > 0) {
                // Toggle fast forward for waves after wave 1
                gamma.fastForward = !gamma.fastForward;
            }
            gamma.m1 = false;
        }
        
        g2d.setFont(Utilities.loadFont("Romanov", Font.PLAIN, 15f));
        g2d.setStroke(new BasicStroke(1)); // information about the cursor location
        g2d.drawString(gamma.mx + ", " + gamma.my, gamma.mx + 20, gamma.my + 50);
        // also draw cell coordinates
        int cellX = gamma.mx / Location.cellSize;
        int cellY = gamma.my / Location.cellSize;
        g2d.drawString("Cell: " + cellX + ", " + cellY, gamma.mx + 20, gamma.my + 70);
    }
    
    private void renderErrorMessage(Graphics2D g2d) {
        if (!gamma.err.isEmpty()) {
            // Track error message timing
            if (gamma.errFadeStartTime == 0) {
                gamma.errFadeStartTime = System.currentTimeMillis();
            }
            long elapsed = System.currentTimeMillis() - gamma.errFadeStartTime;
            float alpha = 1.0f - Math.min(elapsed / 1500f, 1.0f); // Fade out over 1.5 seconds
            
            if (alpha > 0.01f) {
                g2d.setFont(Utilities.loadFont("Romanov", Font.BOLD, 25f));
                FontMetrics fm = g2d.getFontMetrics();
                g2d.setColor(new Color(255, 0, 0, (int) (255 * alpha)));
                g2d.drawString(gamma.err, gamma.uiMid - fm.stringWidth(gamma.err) / 2, 140);
            } else {
                gamma.err = "";
                gamma.errFadeStartTime = 0;
            }
        } else {
            gamma.errFadeStartTime = 0;
        }
    }
    
    private void renderBuildingList(Graphics2D g2d) {
        java.util.List<Gamma.Icon> iconsToDraw = gamma.tabSelected.equals("productive") ? Gamma.productive : Gamma.offensive;
        int startX = gamma.uiMid - 100;
        int startY = 220;
        int spaceX = 100;
        int iconsPerRow = 3;
        
        // Track hovered icon to draw tooltip last
        Gamma.Icon hoveredIcon = null;
        int hoveredX = 0;
        int hoveredY = 0;
        
        int visibleIndex = 0;
        for (Gamma.Icon icon : iconsToDraw) {
            if (!canBuild(icon.stats.buildingClass.getSimpleName())) {
                continue; // skip icons that cannot be built, don't leave space
            }
            int buildingCount = 0;
            for (Instance inst : GameManager.getInstance().getInstances()) {
                if (inst.getClass() == icon.stats.buildingClass && inst.isAlive()) {
                    buildingCount++;
                }
            }
            int row = visibleIndex / iconsPerRow;
            int col = visibleIndex % iconsPerRow;
            int x = startX + col * spaceX;
            int y = startY + row * spaceX;
            visibleIndex++;
            
            BufferedImage img = Utilities.load(icon.stats.buildingClass.getSimpleName().toLowerCase() + "_icon", 1.0, 1.0);
            // Safe fallback sizes when image missing
            int imgW = 64;
            int imgH = 64;
            if (img != null) {
                imgW = img.getWidth();
                imgH = img.getHeight();
            }
            if (img != null) {
                // Determine if this icon should be grayed out
                boolean shouldGrayOut = false;
                
                // Check if build limit reached for this building type
                if (icon.stats.buildLimit >= 0) {
                    if (buildingCount >= icon.stats.buildLimit) {
                        shouldGrayOut = true;
                    }
                }
                
                // Also gray out if construction in same category is ongoing
                if (!shouldGrayOut) {
                    if ("productive".equals(icon.stats.buildingType) && gamma.proOnCons && !icon.building && !icon.ready) {
                        shouldGrayOut = true;
                    } else if ("offensive".equals(icon.stats.buildingType) && gamma.offOnCons && !icon.building && !icon.ready) {
                        shouldGrayOut = true;
                    }
                }
                
                // Draw the icon with appropriate alpha
                if (shouldGrayOut) {
                    Composite originalComposite = g2d.getComposite();
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                    g2d.drawImage(img, x - img.getWidth() / 2, y, img.getWidth(), img.getHeight(), null);
                    g2d.setComposite(originalComposite);
                } else {
                    g2d.drawImage(img, x - img.getWidth() / 2, y, img.getWidth(), img.getHeight(), null);
                }
                
                // Draw construction timer or "READY" text
                if (icon.building) {
                    // Draw construction timer
                    double timeRemaining = icon.stats.buildTime - icon.constructionTimer;
                    String timerText = String.format("%.1f", Math.max(0, timeRemaining));
                    
                    g2d.setFont(Utilities.loadFont("Romanov", Font.BOLD, 24f));
                    FontMetrics fm = g2d.getFontMetrics();
                    int textWidth = fm.stringWidth(timerText);
                    int textHeight = fm.getAscent();
                    
                    // Draw semi-transparent background
                    g2d.setColor(new Color(0, 0, 0, 150));
                    g2d.fillOval(x - 30, y + img.getHeight() / 2 - 15, 60, 30);
                    
                    // Draw timer text
                    g2d.setColor(Color.WHITE);
                    g2d.drawString(timerText, x - textWidth / 2, y + img.getHeight() / 2 + textHeight / 2 - 3);
                    
                } else if (icon.ready) {
                    // Draw "READY" text
                    String readyText = "READY";
                    
                    g2d.setFont(Utilities.loadFont("Romanov", Font.BOLD, 16f));
                    FontMetrics fm = g2d.getFontMetrics();
                    int textWidth = fm.stringWidth(readyText);
                    int textHeight = fm.getAscent();
                    
                    // Draw semi-transparent background
                    g2d.setColor(new Color(0, 150, 0, 180));
                    g2d.fillOval(x - 35, y + img.getHeight() / 2 - 12, 70, 24);
                    
                    // Draw ready text
                    g2d.setColor(Color.WHITE);
                    g2d.drawString(readyText, x - textWidth / 2, y + img.getHeight() / 2 + textHeight / 2 - 3);
                }
            }
            
            boolean mouseOver = gamma.mx >= x - imgW / 2 && gamma.mx <= x + imgW / 2 && gamma.my >= y
                    && gamma.my <= y + imgH; // use height for vertical bound
            
            // Store hovered icon to draw tooltip after all icons are drawn
            if (mouseOver && gamma.currentState != Gamma.GameState.PAUSED) {
                hoveredIcon = icon;
                hoveredX = x;
                hoveredY = y;
            }
            
            if (mouseOver && gamma.m1) {
                // Check if we can start construction
                boolean canConstruct = false;
                
                if ("productive".equals(icon.stats.buildingType) && !gamma.proOnCons) {
                    canConstruct = true;
                } else if ("offensive".equals(icon.stats.buildingType) && !gamma.offOnCons) {
                    canConstruct = true;
                }
                if (GameManager.getInstance().getKromer() < icon.stats.cost && !icon.ready) {
                    canConstruct = false;
                    String[] messages = { "poor", };
                    int idx = (int) (Math.random() * messages.length);
                    gamma.error(messages[idx]);
                }
                if (!icon.ready && icon.building || buildingCount >= icon.stats.buildLimit && icon.stats.buildLimit >= 0) {
                    canConstruct = false;
                    String[] messages = { "chill out", };
                    int idx = (int) (Math.random() * messages.length);
                    gamma.error(messages[idx]);
                }
                
                if (canConstruct && !icon.building && !icon.ready) {
                    gamma.sellMode = false;
                    gamma.repairMode = false;
                    gamma.buildMode = false;
                    icon.construct();
                    GameManager.getInstance().addKromer(-icon.stats.cost);
                } else if (icon.ready) {
                    gamma.sellMode = false;
                    gamma.repairMode = false;
                    gamma.buildMode = true;
                    gamma.iconToBuild = icon;
                    gamma.buildingToBuild = icon.stats.buildingClass;
                    
                    // Extract turret info and building dimensions directly from config
                    gamma.turretOffsets.clear();
                    gamma.turretRanges.clear();
                    for (TurretStats turretStats : icon.stats.turrets) {
                        gamma.turretOffsets.add(new Point(turretStats.offsetX, turretStats.offsetY));
                        gamma.turretRanges.add(turretStats.range);
                    }
                    
                    // Cache building dimensions from config
                    gamma.previewWidth = icon.stats.width;
                    gamma.previewHeight = icon.stats.height;
                    
                    gamma.refresh();
                }
                gamma.m1 = false;
            } else if (mouseOver && gamma.m2) { // refund when right clicking constructing,ed icon
                if (icon.building || icon.ready) {
                    icon.building = false;
                    icon.ready = false;
                    icon.constructionTimer = 0.0;
                    if ("productive".equals(icon.stats.buildingType)) {
                        gamma.proOnCons = false;
                    } else if ("offensive".equals(icon.stats.buildingType)) {
                        gamma.offOnCons = false;
                    }
                    GameManager.getInstance().addKromer(icon.stats.cost); // refund
                }
                gamma.m2 = false;
            }
        }
        
        // Draw tooltip last so it appears on top of all icons
        if (hoveredIcon != null) {
            renderBuildingTooltip(g2d, hoveredIcon, hoveredX, hoveredY);
        }
    }
    
    private void renderBuildingTooltip(Graphics2D g2d, Gamma.Icon icon, int iconX, int iconY) {
        g2d.setFont(Utilities.loadFont("Romanov", Font.PLAIN, 20f));
        FontMetrics fm = g2d.getFontMetrics();
        String rawName = icon.stats.buildingClass.getSimpleName();
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 0; i < rawName.length(); i++) {
            char c = rawName.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(rawName.charAt(i - 1))) {
                nameBuilder.append(' ');
            }
            nameBuilder.append(c);
        }
        String name = nameBuilder.toString();
        String cost = "Cost: " + icon.stats.cost + " Kromer";
        String time = "Build Time: " + icon.stats.buildTime + "s";
        String desc = icon.stats.description;
        
        int tooltipWidth = 220;
        int padding = 8;
        int lineHeight = fm.getHeight();
        
        // Word wrap description, ensuring no line exceeds tooltipWidth - 2*padding
        List<String> descLines = new ArrayList<>();
        String[] words = desc.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String testLine = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(testLine) > tooltipWidth - 2 * padding) {
                if (line.length() > 0) descLines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) line.append(" ");
                line.append(word);
            }
        }
        if (line.length() > 0) descLines.add(line.toString());
        
        // Ensure each line does not exceed the tooltip width (hard break if needed)
        List<String> finalDescLines = new ArrayList<>();
        for (String l : descLines) {
            if (fm.stringWidth(l) <= tooltipWidth - 2 * padding) {
                finalDescLines.add(l);
            } else {
                // Hard break long lines
                StringBuilder sb = new StringBuilder();
                for (char c : l.toCharArray()) {
                    sb.append(c);
                    if (fm.stringWidth(sb.toString()) > tooltipWidth - 2 * padding) {
                        // Remove last char, add line, start new
                        sb.deleteCharAt(sb.length() - 1);
                        finalDescLines.add(sb.toString());
                        sb = new StringBuilder().append(c);
                    }
                }
                if (sb.length() > 0) finalDescLines.add(sb.toString());
            }
        }
        
        int tooltipHeight = (3 + finalDescLines.size()) * lineHeight + 2 * padding;
        
        // Prefer to place tooltip to the left of the cursor; if that would overflow,
        // place to the right
        int tooltipX = gamma.mx - tooltipWidth - 15;
        if (tooltipX < 0) {
            tooltipX = gamma.mx + 15;
        }
        int tooltipY = gamma.my - tooltipHeight / 2;
        
        // Ensure tooltip stays within UI bounds
        if (tooltipX < 0) tooltipX = 0;
        if (tooltipY < 0) tooltipY = 0;
        if (tooltipX + tooltipWidth > 1920) tooltipX = 1920 - tooltipWidth;
        if (tooltipY + tooltipHeight > 1080) tooltipY = 1080 - tooltipHeight;
        
        // Draw background and border
        g2d.setColor(new Color(0, 0, 0, 220));
        g2d.fillRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight);
        
        int textY = tooltipY + padding + fm.getAscent();
        g2d.drawString(name, tooltipX + padding, textY);
        textY += lineHeight;
        g2d.drawString(cost, tooltipX + padding, textY);
        textY += lineHeight;
        g2d.drawString(time, tooltipX + padding, textY);
        textY += lineHeight;
        
        for (String descLine : finalDescLines) {
            g2d.drawString(descLine, tooltipX + padding, textY);
            textY += lineHeight;
        }
    }
    
    private boolean canBuild(String buildingName) {
        Gamma.Icon icon = null;
        for (Gamma.Icon ic : Gamma.productive) {
            if (ic.stats.buildingClass.getSimpleName().equalsIgnoreCase(buildingName)) {
                icon = ic;
                break;
            }
        }
        for (Gamma.Icon ic : Gamma.offensive) {
            if (ic.stats.buildingClass.getSimpleName().equalsIgnoreCase(buildingName)) {
                icon = ic;
                break;
            }
        }
        if (icon == null) return false;
        for (String req : icon.stats.prerequisites) {
            boolean found = false;
            for (Instance instance : GameManager.getInstance().getInstances()) {
                if (instance instanceof Building && instance.getClass().getSimpleName().equalsIgnoreCase(req)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
    
    public void renderBuildMode(Graphics2D g2d) {
        if (!gamma.buildMode) return;
        g2d.setColor(BUILDABLE_COLOR);
        for (Point p : gamma.buildable) {
            g2d.fillRect(p.x * Location.cellSize, p.y * Location.cellSize, Location.cellSize, Location.cellSize);
        }
        g2d.setColor(UNBUILDABLE_COLOR);
        for (Point p : gamma.unbuildable) {
            g2d.fillRect(p.x * Location.cellSize, p.y * Location.cellSize, Location.cellSize, Location.cellSize);
        }
        if (gamma.status != "game" || gamma.buildingToBuild == null) return;
        
        // Use cached building dimensions (no reflection needed)
        int cellX = gamma.mx / Location.cellSize;
        int cellY = gamma.my / Location.cellSize;
        g2d.setColor(PREVIEW_COLOR);
        g2d.setStroke(PREVIEW_STROKE_THICK);
        g2d.drawRect(cellX * Location.cellSize, cellY * Location.cellSize,
                gamma.previewWidth * Location.cellSize, gamma.previewHeight * Location.cellSize);
        
        // range
        g2d.setColor(PREVIEW_COLOR);
        g2d.setStroke(PREVIEW_STROKE_THIN);
        
        // Check if RadarDish exists for preview boost
        boolean hasRadar = false;
        for (Instance inst : GameManager.getInstance().getInstances()) {
            if ("RadarDish".equals(inst.getClass().getSimpleName()) && inst.isAlive()) {
                hasRadar = true;
                break;
            }
        }
        
        for (int i = 0; i < gamma.turretOffsets.size() && i < gamma.turretRanges.size(); i++) {
            Point offset = gamma.turretOffsets.get(i);
            double range = gamma.turretRanges.get(i) * (hasRadar ? 1.25 : 1.0);
            
            // Calculate turret position based on building center
            // Building at cellX occupies cellX to cellX+width, so center is at cellX + width/2
            int buildingCenterX = cellX * Location.cellSize + (gamma.previewWidth * Location.cellSize) / 2;
            int buildingCenterY = cellY * Location.cellSize + (gamma.previewHeight * Location.cellSize) / 2;
            
            // Turret is offset from building center
            int turretX = buildingCenterX + offset.x;
            int turretY = buildingCenterY + offset.y;
            int rangeRadius = (int) (range * Location.cellSize);
            
            g2d.drawOval(turretX - rangeRadius, turretY - rangeRadius, rangeRadius * 2, rangeRadius * 2);
        }
    }
    
    private void drawHitbox(Graphics2D g2d) {
        for (Instance instance : GameManager.getInstance().getInstances()) {
            for (Hitbox hitbox : instance.hitboxes) {
                Polygon poly = new Polygon();
                for (Point corner : hitbox.corners) {
                    poly.addPoint(corner.x, corner.y);
                }
                g2d.setColor(new Color(255, 255, 255, 100));
                g2d.fillPolygon(poly);
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(1));
                g2d.drawPolygon(poly);
            }
        }
    }
}
