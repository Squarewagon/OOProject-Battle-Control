import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Comparator;

public class Gamma extends JPanel implements ActionListener, MouseListener, MouseMotionListener, KeyListener {

    public static final int FRAME_TIME = 1000 / 60; // milliseconds per frame

    private Timer gameTimer;
    long lastUpdateTime;
    boolean gameRunning;
    boolean fastForward;

    private static Gamma gameInstance;

    // Simple game state for menus
    enum GameState {
        MAIN_MENU, MAP_SELECT, MODE_SELECT, IN_GAME, PAUSED
    }

    GameState currentState = GameState.MAIN_MENU;
    String selectedMap = "plain"; // future: support more maps
    static public String selectedMode = "normal"; // normal | survival | challenge | sandbox

    public static final int GAME_WIDTH = 1560; // 13/16 * 1920
    public static final int UI_WIDTH = 360; // 3/16 * 1920
    public static final int HEIGHT = 1080;

    int uiMid = 1740; // midpoint of the UI panel at 1560 + 180 or 1920 - 180

    private static GameManager gameManager; // REFACTORED: Central game state
    private static GameLoop gameLoop; // REFACTORED: Timing and update logic
    private InputManager inputManager; // REFACTORED: Input handling
    private RenderSystem renderSystem; // REFACTORED: Rendering logic
    private BuildingManager buildingManager; // REFACTORED: Build/repair/sell logic
    private static List<Instance> Instances; // Delegate to gameManager
    private static List<Instance> iQueue = new ArrayList<>(); // Buffer for new instances
    static List<Icon> productive = new ArrayList<>();
    static List<Icon> offensive = new ArrayList<>();
    private static HashMap<Class<?>, Icon> iconByClass = new HashMap<>();
    public static int power; // Delegate to gameManager
    public static int kromer; // Delegate to gameManager

    private int timer = 0; // this will keeps on going each update, used for various purposes

    // mouse tracking
    int mx = -1;
    int my = -1;
    boolean m1 = false;
    boolean m2 = false;
    boolean m1Hold = false;
    boolean m2Hold = false;
    int m1Timer = 0;
    int m2Timer = 0;
    String status = "";
    String currentCursorName = "";

    String err = "";
    long errFadeStartTime = 0;
    String tabSelected = "productive";
    boolean showHitboxes = false;
    public boolean buildMode = false;
    boolean repairMode = false;
    boolean sellMode = false;
    public static boolean getConstructor = false;
    Icon iconToBuild = null;
    Class<?> buildingToBuild = null;
    ArrayList<Point> turretOffsets = new ArrayList<>();
    ArrayList<Double> turretRanges = new ArrayList<>();
    int previewWidth = 0;
    int previewHeight = 0;
    boolean proOnCons = false;
    boolean offOnCons = false;
    ArrayList<Point> buildable = new ArrayList<>();
    ArrayList<Point> unbuildable = new ArrayList<>();

    public static int wave = 0;

    // Cached graphics objects for performance
    private static final Color BUILDABLE_COLOR = new Color(0, 255, 0, 100);
    private static final Color UNBUILDABLE_COLOR = new Color(255, 0, 0, 100);
    private static final Color PREVIEW_COLOR = new Color(255, 255, 255, 150);
    private static final BasicStroke PREVIEW_STROKE_THICK = new BasicStroke(2);
    private static final BasicStroke PREVIEW_STROKE_THIN = new BasicStroke(1);

    // Initialize building list from ConfigManager
    void init() {
        ConfigManager config = gameManager.getConfigManager();
        
        // Define the building load order for UI display
        String[] buildingOrder = {
            "Headquarter", "PowerPlant", "OilRig", "RadarDish", "HeavyOrdnanceCenter",
            "AutoCannon", "LaserTower", "MissileLauncher"
        };
        
        // Load buildings in defined order to ensure consistent UI display
        for (String buildingName : buildingOrder) {
            BuildingStats stats = config.getAllBuildingStats().get(buildingName);
            if (stats != null) {
                Icon icon = new Icon(stats);
                
                if ("productive".equals(stats.buildingType)) {
                    productive.add(icon);
                } else if ("offensive".equals(stats.buildingType)) {
                    offensive.add(icon);
                }
                
                iconByClass.putIfAbsent(stats.buildingClass, icon);
            }
        }
        
        // Add any buildings not in the predefined order (for mod support)
        for (BuildingStats stats : config.getAllBuildingStats().values()) {
            if (iconByClass.containsKey(stats.buildingClass)) {
                continue; // Already added
            }
            
            Icon icon = new Icon(stats);
            
            if ("productive".equals(stats.buildingType)) {
                productive.add(icon);
            } else if ("offensive".equals(stats.buildingType)) {
                offensive.add(icon);
            }
            
            iconByClass.putIfAbsent(stats.buildingClass, icon);
        }
    }

    class Icon {
        Class<?> clazz;
        int cost;
        double buildTime;
        double buildRange;
        boolean buildable = false; // will be true when all prerequisites are met
        String desc;
        ArrayList<String> prerequisites = new ArrayList<>();
        int buildLimit = -1; // -1 means no limit

        // Construction state
        boolean building = false;
        double constructionTimer = 0.0;
        boolean ready = false;

        // NEW: Constructor that takes BuildingStats from config
        Icon(BuildingStats stats) {
            this.clazz = stats.buildingClass;
            this.cost = stats.cost;
            this.buildTime = stats.buildTime;
            this.buildRange = stats.buildRange;
            this.buildLimit = stats.buildLimit;
            this.prerequisites.addAll(stats.prerequisites);
            this.desc = stats.description;
        }

        Icon(Class<?> clazz, int cost, double buildTime, double buildRange, String prerequisites, String desc, int buildLimit) {
            this.clazz = clazz;
            this.cost = cost;
            this.buildTime = buildTime;
            this.buildRange = buildRange;
            this.buildLimit = buildLimit;
            this.prerequisites.addAll(List.of(prerequisites.toLowerCase().split(" ")));
            this.desc = desc;
            Class<?> superClass = clazz.getSuperclass();
            if (superClass == Productive.class) {
                productive.add(this);
            } else if (superClass == Offensive.class) {
                offensive.add(this);
            }
            iconByClass.putIfAbsent(clazz, this);
        }

        Icon(Class<?> clazz, int cost, double buildTime, double buildRange, String prerequisites, String desc) {
            this(clazz, cost, buildTime, buildRange, prerequisites, desc, -1);
        }

        void construct() {
            building = true;
            constructionTimer = 0.0;
            ready = false;

            // Set the appropriate category as under construction
            Class<?> superClass = clazz.getSuperclass();
            if (superClass == Productive.class) {
                proOnCons = true;
            } else if (superClass == Offensive.class) {
                offOnCons = true;
            }
        }

        void update(float deltaTime) {
            if (power < 0)
                deltaTime = deltaTime / 2;
            if (building && !ready) {
                constructionTimer += deltaTime;
                if (constructionTimer >= buildTime) {
                    ready = true;
                    building = false;
                }
            }
        }
    }

    public Gamma() {
        gameInstance = this; // Set static reference
        if (gameManager == null) gameManager = new GameManager(); // Initialize once
        if (gameLoop == null) gameLoop = new GameLoop(gameManager); // Initialize once
        inputManager = new InputManager(this); // REFACTORED: Initialize input manager
        renderSystem = new RenderSystem(this); // REFACTORED: Initialize render system
        buildingManager = new BuildingManager(this); // REFACTORED: Initialize building manager
        
        // Load all configuration from JSON files
        gameManager.getConfigManager().loadConfigs();
        
        setPreferredSize(new Dimension(1920, 1080));
        setBackground(Color.BLACK);
        setDoubleBuffered(true); // Important for smooth rendering
        setFocusable(true);
        requestFocusInWindow();

        // let it all begins
        Instances = gameManager.getInstances();
        power = gameManager.getPower();
        kromer = gameManager.getKromer();
        gameRunning = false;
        fastForward = false;
        lastUpdateTime = System.currentTimeMillis();

        // create a timer that'll update 60 times per second (60 FPS)
        gameTimer = new Timer(FRAME_TIME, this);

        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        init();
        Utilities.setCustomCursor(this, "normal_cursor", -16, -16);
        // Start timer immediately so menus render even when the game isn't running
        gameTimer.start();
    }

    public static Gamma getInstance() {
        return gameInstance;
    }

    public HashMap<Class<?>, Icon> getIconByClass() {
        return iconByClass;
    }

    public BuildingManager getBuildingManager() {
        return buildingManager;
    }

    public void startGame() {
        // Clear previous game state
        Instances.clear();
        iQueue.clear();
        Location.path.clear();
        Location.occupancy.clear();
        Location.buildingOccupancy.clear();
        Location.adjacency.clear();
        
        // Map selection (extend when new maps are added)
        if ("plain".equalsIgnoreCase(selectedMap)) {
            new Plain();
        } else {
            new Plain(); // fallback
        }

        // Mode configuration - set Kromer based on selected mode
        gameManager.initializeKromerForMode(selectedMode);
        kromer = gameManager.getKromer();
        power = gameManager.getPower();

        gameManager.setWave(0);
        wave = gameManager.getWave();
        fastForward = false;
        gameRunning = true;
        currentState = GameState.IN_GAME;
        WaveManager.currentCycle = 1; // Reset cycle when starting new game
        lastUpdateTime = System.currentTimeMillis();
    }

    // Return to main menu and reset runtime state
    public void goToMainMenu() {
        // Stop gameplay updates
        gameRunning = false;
        fastForward = false;

        // Clear world state
        if (Instances != null)
            Instances.clear();
        iQueue.clear();
        Location.occupancy.clear();
        Location.buildingOccupancy.clear();
        Location.adjacency.clear();
        Location.path.clear();

        // Reset UI/game flags
        buildingManager.clearAllModes();  // This clears buildMode, repairMode, sellMode, and preview data
        iconToBuild = null;
        buildingToBuild = null;
        tabSelected = "productive";
        err = "";

        // Reset construction icons state
        for (Icon icon : productive) {
            icon.building = false;
            icon.ready = false;
            icon.constructionTimer = 0.0;
        }
        for (Icon icon : offensive) {
            icon.building = false;
            icon.ready = false;
            icon.constructionTimer = 0.0;
        }

        // Reset power/waves in both Gamma and GameManager
        power = gameManager.getPower();
        wave = gameManager.getWave();
        gameManager.fullReset();
        WaveManager.waveActive = false;
        WaveManager.waveCompleted = false;
        WaveManager.currentCycle = 1; // Reset cycle when returning to main menu

        // Reset cursor
        Utilities.setCustomCursor(this, "normal_cursor", -16, -16);
        currentCursorName = "normal_cursor";

        // Back to menu
        currentState = GameState.MAIN_MENU;
        lastUpdateTime = System.currentTimeMillis();
    }

    // Method to add instances to the game, return success or failure
    public static boolean add(Instance instance, boolean restricted) {
        if (instance instanceof Building) {
            Building building = (Building) instance;
            if (canPlace(building.x, building.y, building.width, building.height, restricted)) {
                Instances.add(instance);
                for (int x = building.x; x < building.x + building.width; x++) {
                    for (int y = building.y; y < building.y + building.height; y++) {
                        // Add the building's occupied cell to buildingOccupancy
                        Location.buildingOccupancy.put(new Point(x, y), building);
                        // Get the buildRange from the Icon associated with this building's class
                        double buildRange = 1.0;
                        for (Icon ic : productive) {
                            if (ic.clazz == building.getClass()) {
                                buildRange = ic.buildRange;
                                break;
                            }
                        }
                        for (Icon ic : offensive) {
                            if (ic.clazz == building.getClass()) {
                                buildRange = ic.buildRange;
                                break;
                            }
                        }
                        int range = (int) Math.round(buildRange);
                        for (int dx = -range; dx <= range; dx++) {
                            for (int dy = -range; dy <= range; dy++) {
                                int nx = x + dx;
                                int ny = y + dy;
                                // Check if within circle radius
                                if (dx * dx + dy * dy <= range * range) {
                                    // Check bounds
                                    if (nx >= 0 && ny >= 0 && nx < Location.cols && ny < Location.rows) {
                                        Point adjPoint = new Point(nx, ny);
                                        if (!Location.adjacency.contains(adjPoint)) {
                                            Location.adjacency.add(adjPoint);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else
                return false; // failed to add building
            if (gameInstance != null && gameInstance.buildMode) {
                gameInstance.refresh();
            }
            return true;
        } else {
            iQueue.add(instance);
            return true;
        }
    }

    public static void add(Instance instance) {
        add(instance, false);
    }

    // method to check if a building can be placed at the given position
    private static boolean canPlace(int x, int y, int width, int height, boolean restricted) {
        // Check if building would go outside map bounds
        if (x < 0 || y < 0 || x + width > Location.cols || y + height > Location.rows) {
            return false;
        }

        for (int bx = x; bx < x + width; bx++) {
            for (int by = y; by < y + height; by++) {
                if (restricted && !Location.adjacency.contains(new Point(bx, by))) {
                    return false;
                }
                for (Point occupied : Location.occupancy) {
                    if (occupied.x == bx && occupied.y == by) {
                        return false;
                    }
                }
                for (Point occupied : Location.buildingOccupancy.keySet()) {
                    if (occupied.x == bx && occupied.y == by) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static List<Instance> getInstances() {
        return new ArrayList<>(Instances);
    }

    // This is called every 16.67ms (60 times per second)
    @Override
    public void actionPerformed(ActionEvent e) {
        // Use GameLoop to calculate delta time and update game
        float deltaTime = gameLoop.calculateDeltaTime(fastForward, gameManager.getWave());
        gameLoop.tick();
        timer = gameLoop.getTimer();

        if (gameRunning) {
            // Update Icon construction timers
            for (Icon icon : productive) {
                icon.update(deltaTime);
            }
            for (Icon icon : offensive) {
                icon.update(deltaTime);
            }
            
            // Update game objects (instances, wave, etc.)
            gameLoop.updateGameObjects(deltaTime, Instances, iQueue);
            
            // Update build mode placement
            if (buildMode && m1 && buildingToBuild != null) {
                int cellX = mx / Location.cellSize;
                int cellY = my / Location.cellSize;
                try {
                    Instance newBuilding = (Instance) buildingToBuild.getDeclaredConstructor(int.class, int.class)
                            .newInstance(cellX, cellY);
                    if (add(newBuilding, true)) {
                        iconToBuild.ready = false;
                        buildMode = false; // exit build mode after placing
                        buildingToBuild = null; // clear the building to build
                        turretOffsets.clear();
                        turretRanges.clear();
                        previewWidth = 0;
                        previewHeight = 0;
                        buildable.clear();
                        unbuildable.clear();
                        Class<?> superClass = iconToBuild.clazz.getSuperclass();
                        if (superClass == Productive.class) {
                            proOnCons = false;
                        } else if (superClass == Offensive.class) {
                            offOnCons = false;
                        }
                    } else {
                        getConstructor = true;
                        newBuilding.destroy();
                        getConstructor = false;
                    }
                } catch (Exception ex) {
                    // Failed to create building instance
                }
                m1 = false; // consume the click
            }
            
            // Update sell/repair mode interactions via BuildingManager
            if ((buildingManager.isSellMode() || buildingManager.isRepairMode()) && m1) {
                int cellX = Math.max(0, Math.min(Location.cols - 1, mx / Location.cellSize));
                int cellY = Math.max(0, Math.min(Location.rows - 1, my / Location.cellSize));
                
                if (buildingManager.processCellInteraction(cellX, cellY, gameManager)) {
                    m1 = false; // consume click once processed
                }
            }
            
            // Update cursor based on game mode
            String wantedCursor = gameLoop.getDesiredCursor(buildMode, buildingManager.isRepairMode(), buildingManager.isSellMode());
            if (!wantedCursor.equals(currentCursorName)) {
                Utilities.setCustomCursor(this, wantedCursor, -16, -16);
                currentCursorName = wantedCursor;
            }
        }
        
        // Debounced repaint so menus animate/respond
        repaint();

        // Mouse button timers and momentary click flags
        if (m1Timer > 0)
            m1 = false;
        if (m2Timer > 0)
            m2 = false;
        if (m1Hold)
            m1Timer++;
        if (m2Hold)
            m2Timer++;
    }

    // DEPRECATED: updateGame logic now handled by GameLoop and actionPerformed

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        
        // Delegate all rendering to RenderSystem
        renderSystem.render(g2d);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Battle Control");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            Gamma game = new Gamma();
            frame.setContentPane(game);
            frame.setSize(1920, 1080);
            frame.setUndecorated(true);
            frame.setResizable(false);
            frame.setLocationRelativeTo(null); // Center on screen
            frame.setVisible(true);
            // Start from menu; do not auto-start the game
        });
    }

    private void drawGameArea(Graphics2D g2d) {
        g2d.setColor(Location.bg);
        g2d.fillRect(0, 0, GAME_WIDTH, HEIGHT);
        g2d.setColor(Location.pc);
        for (Point p : Location.path) {
            g2d.fillRect(p.x * Location.cellSize, p.y * Location.cellSize, Location.cellSize, Location.cellSize);
        }
        g2d.setColor(new Color(255, 255, 255, 30));
        for (int x = 0; x < GAME_WIDTH; x += Location.cellSize) {
            g2d.drawLine(x, 0, x, HEIGHT);
        }
        for (int y = 0; y < HEIGHT; y += Location.cellSize) {
            g2d.drawLine(0, y, GAME_WIDTH, y);
        }

        // draw all instances in order of zIndex
        List<Elements> renderQueue = new ArrayList<>();
        for (Instance instance : Instances) {
            renderQueue.add(new Elements(instance, instance.zIndex));
            for (Turret turret : instance.turrets) {
                renderQueue.add(new Elements(turret, turret.zIndex));
            }
            for (Prop prop : instance.props) {
                renderQueue.add(new Elements(prop, prop.zIndex));
            }
            for (Utilities.Animation anim : instance.anims) {
                renderQueue.add(new Elements(anim::render, 50)); // High z-index for animations
            }
        }
        renderQueue.sort(Comparator.comparingInt(obj -> obj.zIndex));
        for (Elements obj : renderQueue) {
            obj.r.render(g2d);
        }

        // When hovering over an instance, draw its range and health bar
        Instance hoveredInstance = null;
        for (Instance instance : Instances) {
            if (!instance.isAlive())
                continue;
            // Use hitboxes for accurate hover detection
            for (Hitbox hitbox : instance.hitboxes) {
                Polygon poly = new Polygon();
                for (Point corner : hitbox.corners) {
                    poly.addPoint(corner.x, corner.y);
                }
                if (poly.contains(mx, my) && currentState != GameState.PAUSED) {
                    hoveredInstance = instance;
                    break;
                }
            }
            if (hoveredInstance != null)
                break;
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

            double healthPercent = Math.max(0,
                    Math.min(1.0, hoveredInstance.health / (double) hoveredInstance.maxHealth));
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

        // hitbox draw last
        if (showHitboxes) {
            drawHitbox(g2d);
        }
    }

    // this will actually also draw in the game area, but this will draw elements
    // like "interface" rather than game objects
    private void drawUIPanel(Graphics2D g2d) {
        // ui setup
        g2d.drawImage(Utilities.load("ui", 1.0, 1.0), GAME_WIDTH, 0, null);
        g2d.setFont(Utilities.loadFont("Romanov", Font.BOLD, 16f)); // You can replace "Arial" with your font name

        // Draw Power centered at (uiMid - 120, 90)
        String powerStr = "Power: " + power;
        FontMetrics fm = g2d.getFontMetrics();
        int powerWidth = fm.stringWidth(powerStr);
        int powerX = uiMid - 90 - powerWidth / 2;
        int powerY = 85 + fm.getAscent() / 2 - fm.getDescent() / 2;
        if (power < 0)
            g2d.setColor(Color.RED);
        else if (power == 0)
            g2d.setColor(Color.YELLOW);
        else
            g2d.setColor(Color.GREEN);
        g2d.drawString(powerStr, powerX, powerY);

        // Draw Kromer centered at (uiMid + 120, 90)
        String kromerStr = "Kromer: " + kromer;
        int kromerWidth = fm.stringWidth(kromerStr);
        int kromerX = uiMid + 90 - kromerWidth / 2;
        int kromerY = 85 + fm.getAscent() / 2 - fm.getDescent() / 2;
        g2d.setColor(Color.YELLOW);
        g2d.drawString(kromerStr, kromerX, kromerY);
        g2d.setFont(Utilities.loadFont("Romanov", Font.BOLD, 20f));
        fm = g2d.getFontMetrics();

        // Draw tab buttons
        if (tabSelected.equals("productive")) {
            g2d.drawImage(Utilities.load("productiveSelect", 1.0, 1.0), 1591, 168, null);
            g2d.setColor(Color.BLACK);
            g2d.drawString("Productive", 1591 + (uiMid - 1591) / 2 - fm.stringWidth("Productive") / 2, 190);
            g2d.setColor(Color.WHITE);
            g2d.drawString("Offensive", uiMid + (uiMid - 1591) / 2 - fm.stringWidth("Offensive") / 2, 190);
        } else {
            g2d.drawImage(Utilities.load("offensiveSelect", 1.0, 1.0), 1591, 168, null);
            g2d.setColor(Color.WHITE);
            g2d.drawString("Productive", 1591 + (uiMid - 1591) / 2 - fm.stringWidth("Productive") / 2, 190);
            g2d.setColor(Color.BLACK);
            g2d.drawString("Offensive", uiMid + (uiMid - 1591) / 2 - fm.stringWidth("Offensive") / 2, 190);
        }
        // Save current font and metrics for button
        Font buttonFont = Utilities.loadFont("Romanov", Font.BOLD, 20f);
        FontMetrics buttonFm = g2d.getFontMetrics(buttonFont);

        buildingList(g2d);

        if (!err.isEmpty()) {
            // Track error message timing
            if (errFadeStartTime == 0) {
                errFadeStartTime = System.currentTimeMillis();
            }
            long elapsed = System.currentTimeMillis() - errFadeStartTime;
            float alpha = 1.0f - Math.min(elapsed / 1500f, 1.0f); // Fade out over 1.5 seconds

            if (alpha > 0.01f) {
                g2d.setFont(Utilities.loadFont("Romanov", Font.BOLD, 25f));
                fm = g2d.getFontMetrics();
                g2d.setColor(new Color(255, 0, 0, (int) (255 * alpha)));
                g2d.drawString(err, uiMid - fm.stringWidth(err) / 2, 140);

                // Optional: draw a faded background
                AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f * alpha);
                Composite orig = g2d.getComposite();
                g2d.setComposite(ac);
                g2d.setColor(Color.BLACK);
                g2d.setComposite(orig);
            } else {
                err = "";
                errFadeStartTime = 0;
            }
        } else {
            errFadeStartTime = 0;
        }

        // Always use the same font and metrics for the wave button
        g2d.setFont(buttonFont);
        fm = buttonFm;
        // draw wave button and info
        g2d.setColor(Color.WHITE);
        String buttonText;
        if (gameManager.getWave() == 0) {
            buttonText = "Start Wave 1";
        } else if (WaveManager.waveActive) {
            buttonText = fastForward ? "Fast Forward ON" : "Fast Forward OFF";
        } else if (WaveManager.waveCompleted) {
            buttonText = fastForward ? "Fast Forward ON" : "Fast Forward OFF";
        } else {
            buttonText = fastForward ? "Fast Forward ON" : "Fast Forward OFF";
        }
        g2d.drawString(buttonText, uiMid - fm.stringWidth(buttonText) / 2, 955);
        g2d.setFont(Utilities.loadFont("Romanov", Font.PLAIN, 30f));
        fm = g2d.getFontMetrics();

        String waveDisplay;
        if (gameManager.getWave() == 0) {
            waveDisplay = "Intermission";
        } else if (WaveManager.waveCompleted) {
            waveDisplay = "Wave " + WaveManager.conqueredWave + " Completed!";
        } else {
            waveDisplay = "Wave: " + gameManager.getWave();
        }
        g2d.drawString(waveDisplay, uiMid - fm.stringWidth(waveDisplay) / 2, 1010);

        if (mx >= uiMid - 130 && mx <= uiMid + 130 && my >= 930 && my <= 967) {
            // mouse over start wave button, or other function after starting the wave
            if (m1 && gameManager.getWave() == 0 && !WaveManager.waveActive) {
                // Start wave 1 from intermission
                gameManager.setWave(1);
                wave = gameManager.getWave();
                WaveManager.startWave(wave);
            } else if (m1 && gameManager.getWave() > 0) {
                // Toggle fast forward for waves after wave 1
                fastForward = !fastForward;
            }
            m1 = false;
        }

        g2d.setFont(Utilities.loadFont("Romanov", Font.PLAIN, 15f));
        g2d.setStroke(new BasicStroke(1)); // information about the cursor location
        g2d.drawString(mx + ", " + my, mx + 20, my + 50);
    }

    // small error message function
    public void error(String message) {
        err = message;
        errFadeStartTime = 0; // reset fade timer
    }

    /**
     * Sell a building and return the refund amount to the player
     * @param building the building to sell
     * @return true if the sale was successful
     */
    public boolean sellBuilding(Building building) {
        if (building instanceof Headquarter) {
            return false; // Cannot sell HQ
        }
        
        Icon icon = iconByClass.get(building.getClass());
        if (icon != null) {
            int refund = (int) (icon.cost * 0.5f * (building.health / (float) building.maxHealth));
            gameManager.addKromer(refund);
            building.destroy();
            return true;
        }
        return false;
    }

    private void buildingList(Graphics2D g2d) {
        List<Icon> iconsToDraw = tabSelected.equals("productive") ? productive : offensive;
        int startX = uiMid - 100;
        int startY = 220;
        int spaceX = 100;
        int iconsPerRow = 3;

        // Track hovered icon to draw tooltip last
        Icon hoveredIcon = null;

        int visibleIndex = 0;
        for (Icon icon : iconsToDraw) {
            if (!canBuild(icon.clazz.getSimpleName())) {
                continue; // skip icons that cannot be built, don't leave space
            }
            int buildingCount = 0;
            for (Instance inst : Instances) {
                if (inst.getClass() == icon.clazz && inst.isAlive()) {
                    buildingCount++;
                }
            }
            int row = visibleIndex / iconsPerRow;
            int col = visibleIndex % iconsPerRow;
            int x = startX + col * spaceX;
            int y = startY + row * spaceX;
            visibleIndex++;

            BufferedImage img = Utilities.load(icon.clazz.getSimpleName().toLowerCase() + "_icon", 1.0, 1.0);
            // Safe fallback sizes when image missing
            int imgW = 64;
            int imgH = 64;
            if (img != null) {
                imgW = img.getWidth();
                imgH = img.getHeight();
            }
            if (img != null) {
                // Determine if this icon should be grayed out
                Class<?> superClass = icon.clazz.getSuperclass();
                boolean shouldGrayOut = false;

                // Check if build limit reached for this building type
                if (icon.buildLimit >= 0) {
                    if (buildingCount >= icon.buildLimit) {
                        shouldGrayOut = true;
                    }
                }

                // Also gray out if construction in same category is ongoing
                if (!shouldGrayOut) {
                    if (superClass == Productive.class && proOnCons && !icon.building && !icon.ready) {
                        shouldGrayOut = true;
                    } else if (superClass == Offensive.class && offOnCons && !icon.building && !icon.ready) {
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
                    double timeRemaining = icon.buildTime - icon.constructionTimer;
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

            boolean mouseOver = mx >= x - imgW / 2 && mx <= x + imgW / 2 && my >= y
                    && my <= y + imgH; // use height for vertical bound
            
            // Store hovered icon to draw tooltip after all icons are drawn
            if (mouseOver && currentState != GameState.PAUSED) {
                hoveredIcon = icon;
            }
            
            if (mouseOver && m1) {
                ;
                // Check if we can start construction
                Class<?> superClass = icon.clazz.getSuperclass();
                boolean canConstruct = false;

                if (superClass == Productive.class && !proOnCons) {
                    canConstruct = true;
                } else if (superClass == Offensive.class && !offOnCons) {
                    canConstruct = true;
                }
                if (kromer < icon.cost && !icon.ready) {
                    canConstruct = false;
                    String[] messages = {
                            "poor",
                    };
                    int idx = (int) (Math.random() * messages.length);
                    error(messages[idx]);
                }
                if (!icon.ready && icon.building || buildingCount >= icon.buildLimit && icon.buildLimit >= 0) {
                    canConstruct = false;
                    String[] messages = {
                            "chill out",
                    };
                    int idx = (int) (Math.random() * messages.length);
                    error(messages[idx]);
                }

                if (canConstruct && !icon.building && !icon.ready) {
                    sellMode = false;
                    repairMode = false;
                    buildMode = false;
                    icon.construct();
                    gameManager.addKromer(-icon.cost);
                    kromer = gameManager.getKromer();
                } else if (icon.ready) {
                    sellMode = false;
                    repairMode = false;
                    buildMode = true;
                    iconToBuild = icon;
                    buildingToBuild = icon.clazz;

                    // Extract turret info and building dimensions from dummy instance
                    getConstructor = true;
                    try {
                        Instance dummy = (Instance) buildingToBuild.getDeclaredConstructor(int.class, int.class)
                                .newInstance(0, 0);
                        turretOffsets.clear();
                        turretRanges.clear();
                        for (Turret turret : dummy.turrets) {
                            turretOffsets.add(new Point(turret.offsetX, turret.offsetY));
                            turretRanges.add(turret.range);
                        }

                        // Cache building dimensions
                        previewWidth = buildingToBuild.getDeclaredField("width").getInt(null);
                        previewHeight = buildingToBuild.getDeclaredField("height").getInt(null);
                    } catch (Exception e) {
                        turretOffsets.clear();
                        turretRanges.clear();
                        previewWidth = 1;
                        previewHeight = 1;
                    }
                    getConstructor = false;

                    refresh();
                }
                m1 = false;
            } else if (mouseOver && m2) { // refund when right clicking constructing,ed icon
                if (icon.building || icon.ready) {
                    icon.building = false;
                    icon.ready = false;
                    icon.constructionTimer = 0.0;
                    Class<?> superClass = icon.clazz.getSuperclass();
                    if (superClass == Productive.class) {
                        proOnCons = false;
                    } else if (superClass == Offensive.class) {
                        offOnCons = false;
                    }
                    gameManager.addKromer(icon.cost); // refund
                    kromer = gameManager.getKromer();
                }
                m2 = false;
            }
        }

        // Draw tooltip last so it appears on top of all icons
        if (hoveredIcon != null) {
            g2d.setFont(Utilities.loadFont("Romanov", Font.PLAIN, 20f));
            FontMetrics fm = g2d.getFontMetrics();
            String rawName = hoveredIcon.clazz.getSimpleName();
            StringBuilder nameBuilder = new StringBuilder();
            for (int i = 0; i < rawName.length(); i++) {
                char c = rawName.charAt(i);
                if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(rawName.charAt(i - 1))) {
                    nameBuilder.append(' ');
                }
                nameBuilder.append(c);
            }
            String name = nameBuilder.toString();
            String cost = "Cost: " + hoveredIcon.cost + " Kromer";
            String time = "Build Time: " + hoveredIcon.buildTime + "s";
            String desc = hoveredIcon.desc;

            int tooltipWidth = 220;
            int padding = 8;
            int lineHeight = fm.getHeight();

            // Word wrap description, ensuring no line exceeds tooltipWidth - 2*padding
            java.util.List<String> descLines = new ArrayList<>();
            String[] words = desc.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String testLine = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(testLine) > tooltipWidth - 2 * padding) {
                    if (line.length() > 0)
                        descLines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    if (line.length() > 0)
                        line.append(" ");
                    line.append(word);
                }
            }
            if (line.length() > 0)
                descLines.add(line.toString());

            // Ensure each line does not exceed the tooltip width (hard break if needed)
            java.util.List<String> finalDescLines = new ArrayList<>();
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
                    if (sb.length() > 0)
                        finalDescLines.add(sb.toString());
                }
            }

            int tooltipHeight = (3 + finalDescLines.size()) * lineHeight + 2 * padding;

            // Prefer to place tooltip to the left of the cursor; if that would overflow,
            // place to the right
            int tooltipX = mx - tooltipWidth - 15;
            if (tooltipX < 0) {
                tooltipX = mx + 15;
            }
            int tooltipY = my - tooltipHeight / 2;

            // Ensure tooltip stays within UI bounds
            if (tooltipX < 0)
                tooltipX = 0;
            if (tooltipY < 0)
                tooltipY = 0;
            if (tooltipX + tooltipWidth > 1920)
                tooltipX = 1920 - tooltipWidth;
            if (tooltipY + tooltipHeight > 1080)
                tooltipY = 1080 - tooltipHeight;

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
    }

    private boolean canBuild(String buildingName) {
        Icon icon = null;
        for (Icon ic : productive) {
            if (ic.clazz.getSimpleName().equalsIgnoreCase(buildingName)) {
                icon = ic;
                break;
            }
        }
        for (Icon ic : offensive) {
            if (ic.clazz.getSimpleName().equalsIgnoreCase(buildingName)) {
                icon = ic;
                break;
            }
        }
        if (icon == null)
            return false;
        for (String req : icon.prerequisites) {
            boolean found = false;
            for (Instance instance : Instances) {
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

    // this method will be used globally to update buildable cells in build mode
    public void refresh() {
        buildable.clear();
        unbuildable.clear();
        HashSet<Point> occupancySet = new HashSet<>(Location.occupancy);

        for (Point p : Location.adjacency) {
            if (occupancySet.contains(p) || Location.buildingOccupancy.containsKey(p)) {
                unbuildable.add(p);
            } else {
                buildable.add(p);
            }
        }

        Location.adjacency.clear();

        // Rebuild adjacency from all remaining buildings
        for (Building building : Location.buildingOccupancy.values()) {
            // Get the buildRange from the Icon associated with this building's class
            double buildRange = 1.0;
            for (Icon icon : productive) {
                if (icon.clazz == building.getClass()) {
                    buildRange = icon.buildRange;
                    break;
                }
            }
            for (Icon icon : offensive) {
                if (icon.clazz == building.getClass()) {
                    buildRange = icon.buildRange;
                    break;
                }
            }

            int range = (int) Math.round(buildRange);

            // Add adjacency for each cell occupied by this building
            for (Point occupied : building.occupiedCells) {
                for (int dx = -range; dx <= range; dx++) {
                    for (int dy = -range; dy <= range; dy++) {
                        int nx = occupied.x + dx;
                        int ny = occupied.y + dy;
                        // Check if within circle radius
                        if (dx * dx + dy * dy <= range * range) {
                            // Check bounds
                            if (nx >= 0 && ny >= 0 && nx < Location.cols && ny < Location.rows) {
                                Point adjPoint = new Point(nx, ny);
                                if (!Location.adjacency.contains(adjPoint)) {
                                    Location.adjacency.add(adjPoint);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void drawHitbox(Graphics2D g2d) {
        for (Instance instance : Instances) {
            if (!instance.isAlive())
                continue;

            // Set color based on instance type
            if (instance instanceof Building) {
                g2d.setColor(Color.GREEN);
            } else if (instance instanceof Enemy) {
                g2d.setColor(Color.RED);
            } else if (instance instanceof Projectile) {
                g2d.setColor(Color.PINK);
            } else {
                g2d.setColor(Color.YELLOW); // Default for other types
            }

            g2d.setStroke(new BasicStroke(2));

            // Draw all hitboxes for this instance
            for (Hitbox hitbox : instance.hitboxes) {
                int[] xPoints = new int[4];
                int[] yPoints = new int[4];
                for (int i = 0; i < 4; i++) {
                    xPoints[i] = hitbox.corners.get(i).x;
                    yPoints[i] = hitbox.corners.get(i).y;
                }
                g2d.drawPolygon(xPoints, yPoints, 4);
            }
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        inputManager.handleKeyPressed(e);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        inputManager.handleKeyReleased(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        inputManager.handleMouseClicked(e);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        inputManager.handleMousePressed(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        inputManager.handleMouseReleased(e);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        inputManager.handleMouseEntered(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        inputManager.handleMouseExited(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        inputManager.handleMouseDragged(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        inputManager.handleMouseMoved(e);
    }
}

// starts of the game area classes
class Elements {
    interface Renderable {
        void render(Graphics2D g);
    }

    Renderable r;
    int zIndex;

    public Elements(Renderable r, int zIndex) {
        this.r = r;
        this.zIndex = zIndex;
    }
}

class Hitbox {
    int offsetX, offsetY; // offset from instance center
    double width, height; // dimensions in cells
    ArrayList<Point> corners; // the actual 4 corner points for SAT collision

    public Hitbox(double width, double height, int offsetX, int offsetY) {
        this.width = width;
        this.height = height;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.corners = new ArrayList<>();
        // Initialize 4 corners for rectangle
        for (int i = 0; i < 4; i++) {
            this.corners.add(new Point(0, 0));
        }
    }

    // Update the actual corner points based on instance position and rotation
    public void update(double exactX, double exactY, double facing) {
        double halfWidth = (width * Location.cellSize) / 2.0;
        double halfHeight = (height * Location.cellSize) / 2.0;

        // Apply rotation to the offset
        double cos = Math.cos(facing);
        double sin = Math.sin(facing);
        double rotatedOffsetX = offsetX * cos - offsetY * sin;
        double rotatedOffsetY = offsetX * sin + offsetY * cos;

        double centerX = exactX + rotatedOffsetX;
        double centerY = exactY + rotatedOffsetY;

        // Relative corner positions (before rotation)
        double[] relativeX = { -halfWidth, halfWidth, halfWidth, -halfWidth };
        double[] relativeY = { -halfHeight, -halfHeight, halfHeight, halfHeight };

        // Rotate each corner around the center and store
        for (int i = 0; i < 4; i++) {
            double cornerX = centerX + (relativeX[i] * cos - relativeY[i] * sin);
            double cornerY = centerY + (relativeX[i] * sin + relativeY[i] * cos);
            corners.get(i).x = (int) cornerX;
            corners.get(i).y = (int) cornerY;
        }
    }
}

abstract class Location {
    public static final int cellSize = 40; // size of each grid cell in pixels
    public static final int cols = 39; // number of columns
    public static final int rows = 27; // number of rows
    public static ArrayList<Point> occupancy = new ArrayList<>(); // occupied cells for path and obstacles
    public static HashMap<Point, Building> buildingOccupancy = new HashMap<>(); // occupied cells for buildings
    public static ArrayList<Point> adjacency = new ArrayList<>(); // list of cells that a building provide adjacency
                                                                  // for, or in other word, buildable cells
    public static ArrayList<Point> path = new ArrayList<>(); // path for enemies to follow

    public static Color bg; // background color
    public static Color pc; // path color

    public static Point start; // starting point for enemies

    public Location() {
    }

    void mapPath(int x1, int y1, int x2, int y2) {
        // Only create path if line is horizontal or vertical
        if (x1 != x2 && y1 != y2) {
            return; // Not horizontal or vertical, do nothing
        }

        // add points to path
        if (x1 == x2) {
            // Vertical line
            if (y1 > y2) {
                for (int y = y1; y >= y2; y--) {
                    if (!pathExists(x1, y)) {
                        path.add(new Point(x1, y));
                        occupancy.add(new Point(x1, y));
                    }
                }
            } else {
                for (int y = y1; y <= y2; y++) {
                    if (!pathExists(x1, y)) {
                        path.add(new Point(x1, y));
                        occupancy.add(new Point(x1, y));
                    }
                }
            }
        } else {
            // Horizontal line
            if (x1 > x2) {
                for (int x = x1; x >= x2; x--) {
                    if (!pathExists(x, y1)) {
                        path.add(new Point(x, y1));
                        occupancy.add(new Point(x, y1));
                    }
                }
            } else {
                for (int x = x1; x <= x2; x++) {
                    if (!pathExists(x, y1)) {
                        path.add(new Point(x, y1));
                        occupancy.add(new Point(x, y1));
                    }
                }
            }
        }
    }

    boolean pathExists(int x, int y) {
        for (Point p : path) {
            if (p.x == x && p.y == y) {
                return true;
            }
        }
        return false;
    }
}

class Plain extends Location {
    public Plain() {
        bg = new Color(34, 139, 34);
        pc = new Color(50, 175, 50);

        start = new Point(43, 13);
        mapPath(38, 13, 32, 13);
        mapPath(32, 13, 32, 6);
        mapPath(32, 6, 14, 6);
        mapPath(14, 6, 14, 21);
        mapPath(14, 21, 19, 21);
        mapPath(19, 21, 19, 8);
        mapPath(19, 8, 25, 8);
        mapPath(25, 8, 25, 24);
        mapPath(25, 24, 8, 24);
        mapPath(8, 24, 8, 3);
        mapPath(8, 3, 5, 3);

        Gamma.add(new Empty());

        Gamma.add(new Headquarter(1, 1));
    }
}

abstract class Instance implements Elements.Renderable {
    int x, y; // in cells
    double exactX, exactY;
    int health;
    int maxHealth;
    double facing = 0; // angle in radians
    boolean alive = true;
    ArrayList<Turret> turrets = new ArrayList<>();
    ArrayList<Prop> props = new ArrayList<>();
    ArrayList<Weapon> weapons = new ArrayList<>();
    ArrayList<Utilities.Animation> anims = new ArrayList<>();
    int zIndex = 0; // Default Z-index
    ArrayList<Hitbox> hitboxes = new ArrayList<>(); // Multiple hitboxes per instance
    double timer = 0; // general purpose timer
    double trueTimer = 0; // general purpose timer that isn't affected by time dilation
    double healthMult = 1, damageMult = 1, speedMult = 1, rangeMult = 1, rofMult = 1;

    BufferedImage image;
    int imgX = Location.cellSize / 2, imgY = Location.cellSize / 2; // image offset
    int scaleX = 1, scaleY = 1; // image scale

    public Instance(int x, int y, int health) {
        this.x = x;
        this.y = y;
        exactX = x * Location.cellSize + Location.cellSize / 2.0;
        exactY = y * Location.cellSize + Location.cellSize / 2.0;
        this.health = (int) (health * healthMult);
        this.maxHealth = (int) (health * healthMult);
    }

    public Instance(double x, double y) { // for projectiles
        this.exactX = x;
        this.exactY = y;
    }

    abstract void update(float deltaTime);

    public abstract void render(Graphics2D g);

    void add(Turret turret) {
        turrets.add(turret);
    }

    void add(Prop prop) {
        props.add(prop);
    }

    void add(Weapon weapon) {
        weapons.add(weapon);
    }

    void add(Utilities.Animation animation) {
        anims.add(animation);
    }

    // Add a hitbox to this instance
    void hitbox(int offsetX, int offsetY, double width, double height) {
        hitboxes.add(new Hitbox(width, height, offsetX, offsetY));
        updateHitboxes();
    }

    void routine(float deltaTime) {
        timer += deltaTime;
        trueTimer += Gamma.FRAME_TIME / 1000;
        if (this instanceof Building)
            return;
        updateHitboxes();
    }

    // Update all hitboxes (call this every frame for moving instances)
    public void updateHitboxes() {
        for (Hitbox hitbox : hitboxes) {
            hitbox.update(exactX, exactY, facing);
        }
    }

    // Check if instance is still alive (for cleanup)
    public boolean isAlive() {
        return alive;
    }

    // Destroy this instance
    abstract public void destroy();
}

class Empty extends Instance {
    static boolean hasPlayedAnimation = false;

    public Empty() {
        super(0, 0, Integer.MAX_VALUE);
        zIndex = 0; // Default Z-index
    }

    @Override
    void update(float deltaTime) {
        // does nothing
    }

    @Override
    public void render(Graphics2D g) {
        if (!hasPlayedAnimation) {
            // Utilities.animLoad("explode", Gamma.GAME_WIDTH / 2, Gamma.HEIGHT / 2, 2, 2, this, true);
            hasPlayedAnimation = true;
        }
    }

    @Override
    public void destroy() {
        alive = false;
    }
}

abstract class Building extends Instance {
    int width, height; // dimensions in grid cells
    int power = 0; // power consumption or generation
    boolean repairing = false; // is currently repairing
    double repairTimer = 0; // health per second
    double kromerTimer = 0; // kromer per second
    ArrayList<Point> occupiedCells = new ArrayList<>(); // locally store cells occupied by this building
    private float nanoTimer = 0.7f; // counts down after build

    public Building(int x, int y, int health, int width, int height, int power) {
        int calHealth = health;
        if (Gamma.selectedMode.equals("paper armor")) {
            calHealth = health / 2;
        }
        super(x, y, calHealth);
        this.width = width;
        this.height = height;
        hitbox((width * Location.cellSize - Location.cellSize) / 2,
                (height * Location.cellSize - Location.cellSize) / 2, width, height);  
        this.power = power;
        imgX = (width * Location.cellSize) / 2;
        imgY = (height * Location.cellSize) / 2;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                occupiedCells.add(new Point(x + i, y + j));
            }
        }
        if (Gamma.getConstructor != true) {
            GameManager.getInstance().addPower(power);
            Gamma.power = GameManager.getInstance().getPower();
        }
        updateHitboxes();
    }

    void update(float deltaTime) {
        if (GameManager.getInstance().getWave() > 0) {
            kromerTimer += deltaTime;
            if (this instanceof Headquarter) {
                if (kromerTimer >= 10.0) {
                    GameManager.getInstance().addKromer(10);
                    Gamma.kromer = GameManager.getInstance().getKromer();
                    kromerTimer = 0;
                }
            } else if (this instanceof OilRig) {
                if (kromerTimer >= 7.0) {
                    GameManager.getInstance().addKromer(15);
                    Gamma.kromer = GameManager.getInstance().getKromer();
                    kromerTimer = 0;
                }
            }
        }
        if (repairing) {
            if (health >= maxHealth) {
                health = maxHealth;
                repairing = false;
                repairTimer = 0;
                return;
            }
            repairTimer += deltaTime;
            if (repairTimer >= 0.2 && GameManager.getInstance().getKromer() > 0) {
                GameManager.getInstance().addKromer(-1);
                Gamma.kromer = GameManager.getInstance().getKromer();
                health += 2;
                repairTimer = 0;
            }
        }

        if (nanoTimer > 0f) nanoTimer = Math.max(0f, nanoTimer - deltaTime);
    }

    @Override
    public void render(Graphics2D g) {
        if (image == null) {
            image = Utilities.load(getClass().getSimpleName().toLowerCase(), scaleX, scaleY);
        }

        if (image != null) {
            int drawX = (x * Location.cellSize);
            int drawY = (y * Location.cellSize);
            g.drawImage(image, drawX, drawY, null);
        }
        // Placement flash overlay: start fully visible and fade to transparent
        if (nanoTimer > 0f) {
            float t = Math.max(0f, nanoTimer / 0.7f);
            float alpha = (float) Math.sqrt(t); // ease-out: slower at start, faster at end
            int rx = x * Location.cellSize;
            int ry = y * Location.cellSize;
            int rw = width * Location.cellSize;
            int rh = height * Location.cellSize;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(new Color(0x4bbdff));
            g2.fillRect(rx, ry, rw, rh);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(rx, ry, rw, rh);
            g2.dispose();
        }
    }

    @Override
    public void destroy() {
        if (!Gamma.getConstructor) {
            for (Point occupied : occupiedCells) {
                Location.buildingOccupancy.remove(occupied);
            }
        }

        turrets.clear();
        props.clear();
        GameManager.getInstance().addPower(-this.power);
        Gamma.power = GameManager.getInstance().getPower();
        alive = false;

        // Update build mode cells
        Gamma.getInstance().refresh();
    }

    public void onLowPower() {

    }
}

// PRODUCTIVE BUILDINGS
// ---------------------------------------------------------------------------------------------
abstract class Productive extends Building { // declared only for type distinction
    public Productive(int x, int y, int health, int width, int height, int power) {
        super(x, y, health, width, height, power);
    }
}
// ---------------------------------------------------------------------------------------------

class Headquarter extends Productive {
    static final int width = 4;
    static final int height = 4;

    public Headquarter(int x, int y) {
        super(x, y, 2000, width, height, 25);
        zIndex = 0; // Buildings on bottom layer
    }
}

class PowerPlant extends Productive {
    static final int width = 2;
    static final int height = 2;

    public PowerPlant(int x, int y) {
        super(x, y, 150, width, height, 50);
        zIndex = 0; // Buildings on bottom layer
    }
}

class OilRig extends Productive {
    static final int width = 3;
    static final int height = 2;

    public OilRig(int x, int y) {
        super(x, y, 250, width, height, -40);
        zIndex = 0; // Buildings on bottom layer
    }
}

class RadarDish extends Productive {
    static final int width = 2;
    static final int height = 2;

    public RadarDish(int x, int y) {
        super(x, y, 300, width, height, -25);
        zIndex = 0; // Buildings on bottom layer
    }
}

class HeavyOrdnanceCenter extends Productive {
    static final int width = 3;
    static final int height = 3;

    public HeavyOrdnanceCenter(int x, int y) {
        super(x, y, 500, width, height, -50);
        zIndex = 0; // Buildings on bottom layer

        add(new Turret(this, -27, -27, 80));
        add(new ArtilleryW(turrets.get(0), 25, 0));
    }
}

// OFFENSIVE BUILDINGS
// ---------------------------------------------------------------------------------------------
abstract class Offensive extends Building { // declared only for type distinction
    public Offensive(int x, int y, int health, int width, int height, int power) {
        super(x, y, health, width, height, power);
    }
}
// ---------------------------------------------------------------------------------------------

class AutoCannon extends Offensive {
    static final int width = 1;
    static final int height = 1;

    public AutoCannon(int x, int y) {
        super(x, y, 100, width, height, -10);
        zIndex = 0; // Buildings on bottom layer

        add(new Turret(this, 0, 0, 120));
        add(new Prop("bar", turrets.get(0), 15, 0, 1, 1));
        add(new AutoCannonW(turrets.get(0), 15, 0));
    }
}

class Artillery extends Offensive {
    static final int width = 2;
    static final int height = 2;

    public Artillery(int x, int y) {
        super(x, y, 300, width, height, -25);
        zIndex = 0; // Buildings on bottom layer

        add(new Turret(this, 0, 0, 80));
        add(new ArtilleryW(turrets.get(0), 25, 0));
    }
}

// new building
// ENEMIES
// ---------------------------------------------------------------------------------------------
abstract class Enemy extends Instance {
    int imgX = 0, imgY = 0; // image offset
    double maxSpeed = 1;
    double minSpeed = 0.2;
    double speed = 1; // cells per second
    double acc = 1; // cells per second squared
    double dec = 1; // cells per second squared
    int rot = 90; // rotation in degrees per second
    int pathIndex = -1;
    int zIndex = 3; // Default Z-index (higher than buildings)

    boolean rotating = false; // is currently rotating

    HashMap<String, Integer> weight = new HashMap<>(); // store weights for target selection

    public Enemy(int x, int y, int health) {
        super(x, y, health);
        if (Gamma.selectedMode.equals("blitzkrieg")) {
            speedMult *= 1.5;
        }
        // finds which path index it is at
        for (int i = 0; i < Location.path.size(); i++) {
            Point p = Location.path.get(i);
            if (p.x == x && p.y == y) {
                pathIndex = i;
                if (pathIndex + 1 < Location.path.size()) {
                    Point next = Location.path.get(pathIndex + 1);
                    double angle = Math.atan2(next.y - p.y, next.x - p.x);
                    facing = angle;
                    break;
                }
            }
        }
        if (pathIndex == -1) {
            // face the first path point
            Point next = Location.path.get(0);
            double angle = Math.atan2(next.y - y, next.x - x);
            facing = angle;
        }
        setWeight();
    }

    @Override
    void update(float deltaTime) {
        if (pathIndex >= Location.path.size() - 1) {
            return;
        }

        Point currentTarget = Location.path.get(pathIndex + 1);
        double targetX = currentTarget.x * Location.cellSize + Location.cellSize / 2.0;
        double targetY = currentTarget.y * Location.cellSize + Location.cellSize / 2.0;

        double dx = targetX - exactX;
        double dy = targetY - exactY;
        double targetAngle = Math.atan2(dy, dx);

        double angleDiff = targetAngle - facing;
        while (angleDiff > Math.PI)
            angleDiff -= 2 * Math.PI;
        while (angleDiff < -Math.PI)
            angleDiff += 2 * Math.PI;

        // need to rotate?
        if (Math.abs(angleDiff) > 0.001) {
            // can rotate more than needed?
            rotating = true;
            double maxRotation = Math.toRadians(rot) * deltaTime * speedMult;
            if (Math.abs(angleDiff) <= maxRotation) {
                // snap
                facing = targetAngle;
            } else {
                // keep rotating
                double facingDelta = Math.signum(angleDiff) * maxRotation;
                facing += facingDelta;
                while (facing < 0) {
                    facing += 2 * Math.PI;
                }
                while (facing >= 2 * Math.PI) {
                    facing -= 2 * Math.PI;
                }
                // also rotate the turrets
                for (Turret turret : turrets) {
                    turret.facing += facingDelta;
                    while (turret.facing < 0) {
                        turret.facing += 2 * Math.PI;
                    }
                    while (turret.facing >= 2 * Math.PI) {
                        turret.facing -= 2 * Math.PI;
                    }
                }
                return;
            }
        }

        double distance = Math.sqrt(dx * dx + dy * dy);
        double moveDistance = speed * Location.cellSize * deltaTime * speedMult;

        if (distance <= moveDistance) {

            if (pathIndex + 2 < Location.path.size()) {
                Point nextTarget = Location.path.get(pathIndex + 2);

                double nextDx = nextTarget.x - currentTarget.x;
                double nextDy = nextTarget.y - currentTarget.y;
                double nextAngle = Math.atan2(nextDy, nextDx);

                double directionDiff = nextAngle - targetAngle;
                while (directionDiff > Math.PI)
                    directionDiff -= 2 * Math.PI;
                while (directionDiff < -Math.PI)
                    directionDiff += 2 * Math.PI;

                if (Math.abs(directionDiff) > 0.001) {
                    exactX = targetX;
                    exactY = targetY;
                    pathIndex++;
                    x = currentTarget.x;
                    y = currentTarget.y;
                } else {
                    pathIndex++;
                    double remainingDistance = moveDistance - distance;
                    exactX = targetX;
                    exactY = targetY;
                    x = currentTarget.x;
                    y = currentTarget.y;

                    double nextTargetX = nextTarget.x * Location.cellSize + Location.cellSize / 2.0;
                    double nextTargetY = nextTarget.y * Location.cellSize + Location.cellSize / 2.0;
                    double nextDx2 = nextTargetX - exactX;
                    double nextDy2 = nextTargetY - exactY;
                    double nextDistance = Math.sqrt(nextDx2 * nextDx2 + nextDy2 * nextDy2);

                    if (nextDistance > 0) {
                        exactX += (nextDx2 / nextDistance) * remainingDistance;
                        exactY += (nextDy2 / nextDistance) * remainingDistance;
                    }
                }
            } else {
                // snap to the last point
                exactX = targetX;
                exactY = targetY;
                pathIndex++;
                x = currentTarget.x;
                y = currentTarget.y;
            }
        } else {
            // keep moving
            rotating = false;
            exactX += (dx / distance) * moveDistance;
            exactY += (dy / distance) * moveDistance;
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (image == null) {
            image = Utilities.load(getClass().getSimpleName().toLowerCase(), scaleX, scaleY);
        }

        if (image != null) {
            // Save the current graphics state
            Graphics2D g2 = (Graphics2D) g.create();

            int drawX = (int) exactX - (image.getWidth() / 2) + imgX;
            int drawY = (int) exactY - (image.getHeight() / 2) + imgY;
            g2.rotate(facing, drawX + (image.getWidth() / 2) - imgX, drawY + (image.getHeight() / 2) - imgY);
            g2.drawImage(image, drawX, drawY, null);

            // Dispose of the graphics copy to restore original state
            g2.dispose();
        }
    }

    abstract void setWeight();

    void weight(String name, int value) {
        String key = name.toLowerCase();
        if (!weight.containsKey(key)) {
            weight.put(key, value);
        }
    }

    int getWeight(Building building) {
        String key = building.getClass().getSimpleName().toLowerCase();
        return weight.getOrDefault(key, 100);
    }
}

// ---------------------------------------------------------------------------------------------

class Recon extends Enemy {
    public Recon(int x, int y) {
        super(x, y, 30);
        maxSpeed = 2;
        minSpeed = 0.5;
        speed = 2;
        acc = 1;
        dec = 1;
        rot = 180;

        hitbox(0, 0, 0.7, 0.4); // Add hitbox for collision detection

        add(new Turret(this, 2, 0, 120, 1, 0.3, 7.0));
        add(new MG(turrets.get(0), 4, 0));
        add(new Prop("bar", turrets.get(0), 5, 0, 1, 1));
    }

    @Override
    void setWeight() {
        weight("headquarter", 50);
        weight("AutoCannon", 150);
        weight("powerplant", 100);
    }

    @Override
    public void destroy() {
        alive = false;
        turrets.clear();
        props.clear();
        GameManager.getInstance().addKromer(5);
        Gamma.kromer = GameManager.getInstance().getKromer();
    }
}

// new enemy
// ---------------------------------------------------------------------------------------------
abstract class Attachment implements Elements.Renderable {
    Instance parent;
    double exactX, exactY;
    int offsetX, offsetY; // offset from parent in pixels
    int imgX = 0, imgY = 0; // image offset
    double scaleX = 1, scaleY = 1; // image scale
    double facing; // angle relative to parent in radians (if parent.facing changes, this also
                   // changes)

    abstract void update(float deltaTime);

    public abstract void render(Graphics2D g);

    String getParent() {
        return parent.getClass().getSimpleName().toLowerCase();
    }
}

class Turret extends Attachment {
    int zIndex = 10;

    // universal
    int rot = 90; // rotation in degrees per second
    double range = 3;
    private boolean isTargeting = false;

    // for buildings
    private Enemy eTarget = null;

    // for enemies
    private Point bTarget = null;
    private double interval = 1.0; // time between target acquisition attempts
    private double cooldown = 2.0; // cooldown before able to shoot again after shooting (fire() will set this to
                                   // weapon().cooldown)
    private double intervalTimer = 0.0;
    private double cooldownTimer = 0.0;
    private double chance = 0.1; // 100% chance to acquire target

    private double durationTimer = 0.0;

    public Turret(Instance parent, int offsetX, int offsetY, int rot) { // for buildings
        this.parent = parent;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.rot = rot;
        facing = parent.facing;
    }

    public Turret(Instance parent, int offsetX, int offsetY, int rot, double interval, double chance, double cooldown) { // for
                                                                                                                         // enemies
        this.parent = parent;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.rot = rot;
        facing = parent.facing;
        this.interval = interval;
        this.chance = chance;
        this.cooldown = cooldown;
    }

    // with z-index
    public Turret(Instance parent, int offsetX, int offsetY, int rot, int zIndex) { // for buildings
        this.parent = parent;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.rot = rot;
        facing = parent.facing;
        this.zIndex = zIndex;
    }

    public Turret(Instance parent, int offsetX, int offsetY, int rot, double interval, double chance, double cooldown,
            int zIndex) { // for enemies
        this.parent = parent;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.rot = rot;
        facing = parent.facing;
        this.zIndex = zIndex;
        this.interval = interval;
        this.chance = chance;
        this.cooldown = cooldown;
    }

    @Override
    void update(float deltaTime) {
        if (GameManager.getInstance().getPower() < 0 && parent instanceof Building) {
            deltaTime = deltaTime / 2;
        }
        if (parent instanceof Enemy) {
            // Enemy targeting logic - target building cells
            Enemy eParent = (Enemy) parent;
            intervalTimer += deltaTime;
            cooldownTimer += deltaTime;

            if (isTargeting) {

                // decelerate while targeting
                if (eParent.speed > eParent.minSpeed)
                    eParent.speed -= eParent.dec * deltaTime;

                if (durationTimer >= maxDuration() || bTarget == null) {
                    isTargeting = false;
                    bTarget = null;
                    durationTimer = 0;
                } else {
                    double targetX = bTarget.x * Location.cellSize + Location.cellSize / 2.0;
                    double targetY = bTarget.y * Location.cellSize + Location.cellSize / 2.0;

                    double dx = targetX - exactX;
                    double dy = targetY - exactY;
                    double targetAngle = Math.atan2(dy, dx);

                    double angleDiff = targetAngle - facing;
                    while (angleDiff > Math.PI)
                        angleDiff -= 2 * Math.PI;
                    while (angleDiff < -Math.PI)
                        angleDiff += 2 * Math.PI;

                    if (Math.abs(angleDiff) > 0.001) {
                        double maxRotation = Math.toRadians(rot * parent.speedMult) * deltaTime;
                        if (Math.abs(angleDiff) <= maxRotation) {
                            facing = targetAngle;
                        } else {
                            facing += Math.signum(angleDiff) * maxRotation;
                        }
                    }

                    // if close enough like 5 degrees then durationTimer += deltaTime; and fire()
                    // the weapons
                    if (Math.abs(angleDiff) < Math.toRadians(5)) {
                        durationTimer += deltaTime;
                        fire(facing, null);

                    }
                }
            } else {
                // Check for new targets
                if (intervalTimer >= interval && cooldownTimer >= cooldown) {
                    intervalTimer = 0;

                    if (Math.random() < chance) {
                        // Try to find a building cell in range
                        Point target = findBuilding();
                        if (target != null) {
                            // actually set the cooldown on fire() but for now set it here
                            bTarget = target;
                            cooldownTimer = 0;
                            isTargeting = true;
                        }
                    }
                }

                // rotate to parent.facing when no target
                double angleDiff = parent.facing - facing;
                while (angleDiff > Math.PI)
                    angleDiff -= 2 * Math.PI;
                while (angleDiff < -Math.PI)
                    angleDiff += 2 * Math.PI;

                if (Math.abs(angleDiff) > 0.001) {
                    double maxRotation = Math.toRadians(rot * parent.speedMult) * deltaTime;
                    if (Math.abs(angleDiff) <= maxRotation) {
                        facing = parent.facing;
                    } else {
                        facing += Math.signum(angleDiff) * maxRotation;
                    }
                }
                recover();
                // accelerate back to normal speed
                ((Enemy) parent).speed += ((Enemy) parent).acc * deltaTime;
                if (((Enemy) parent).speed > ((Enemy) parent).maxSpeed) {
                    ((Enemy) parent).speed = ((Enemy) parent).maxSpeed;
                }
            }
        } else if (parent instanceof Building) {
            if (isTargeting) {
                if (eTarget == null || !eTarget.isAlive() || Math.sqrt(Math.pow(eTarget.exactX - exactX, 2)
                        + Math.pow(eTarget.exactY - exactY, 2)) > range * parent.rangeMult * Location.cellSize) {
                    isTargeting = false;
                    eTarget = null;
                } else {
                    // Predict enemy position
                    Point pos = predict(eTarget);
                    if (pos != null) {
                        double targetX = pos.x;
                        double targetY = pos.y;

                        double dx = targetX - exactX;
                        double dy = targetY - exactY;
                        double targetAngle = Math.atan2(dy, dx);

                        double angleDiff = targetAngle - facing;
                        while (angleDiff > Math.PI)
                            angleDiff -= 2 * Math.PI;
                        while (angleDiff < -Math.PI)
                            angleDiff += 2 * Math.PI;

                        if (Math.abs(angleDiff) > 0.001) {
                            double maxRotation = Math.toRadians(rot) * deltaTime;
                            if (Math.abs(angleDiff) <= maxRotation) {
                                facing = targetAngle;
                            } else {
                                facing += Math.signum(angleDiff) * maxRotation;
                            }
                        }

                        // If close enough like 1 degree then fire() the weapons, tighter because
                        // building is stationary
                        if (Math.abs(angleDiff) < Math.toRadians(1)) {
                            fire(facing, eTarget);
                        }
                    }
                }

            } else {
                Enemy target = findEnemy();
                if (target != null) {
                    eTarget = target;
                    isTargeting = true;
                }
            }
        }
    }

    private Point findBuilding() {
        if (!(parent instanceof Enemy)) {
            return null;
        }

        Enemy eParent = (Enemy) parent;
        HashMap<Building, ArrayList<Point>> buildingToCells = new HashMap<>();

        // Check all building occupied cells
        for (Point buildingCell : Location.buildingOccupancy.keySet()) {
            double cellCenterX = buildingCell.x * Location.cellSize + Location.cellSize / 2.0;
            double cellCenterY = buildingCell.y * Location.cellSize + Location.cellSize / 2.0;

            double distance = Math.sqrt(Math.pow(cellCenterX - exactX, 2) + Math.pow(cellCenterY - exactY, 2));
            double distanceInCells = distance / Location.cellSize;

            if (distanceInCells <= range * parent.rangeMult) {
                Building building = Location.buildingOccupancy.get(buildingCell);
                if (building != null) {
                    ArrayList<Point> cells = buildingToCells.get(building);
                    if (cells == null) {
                        cells = new ArrayList<>();
                        buildingToCells.put(building, cells);
                    }
                    cells.add(buildingCell);
                }
            }
        }

        if (buildingToCells.isEmpty()) { // no buildings in range
            return null;
        }

        ArrayList<Building> candidates = new ArrayList<>(buildingToCells.keySet());
        int totalWeight = 0;
        ArrayList<Integer> weights = new ArrayList<>();

        for (Building building : candidates) {
            int weight = Math.max(0, eParent.getWeight(building));
            weights.add(weight);
            totalWeight += weight;
        }

        Building selectedBuilding;
        if (totalWeight <= 0) {
            selectedBuilding = candidates.get((int) (Math.random() * candidates.size()));
        } else {
            int roll = (int) (Math.random() * totalWeight);
            int cumulative = 0;
            selectedBuilding = candidates.get(0); // fallback
            for (int i = 0; i < candidates.size(); i++) {
                cumulative += weights.get(i);
                if (roll < cumulative) {
                    selectedBuilding = candidates.get(i);
                    break;
                }
            }
        }

        ArrayList<Point> cells = buildingToCells.get(selectedBuilding);
        if (cells == null || cells.isEmpty()) {
            return null;
        }

        return cells.get((int) (Math.random() * cells.size()));
    }

    private Enemy findEnemy() {
        // scan for enemies in range
        ArrayList<Enemy> enemies = new ArrayList<>();
        int maxIdx = 0;
        for (Instance i : Gamma.getInstances()) {
            if (i instanceof Enemy) {
                Enemy enemy = (Enemy) i;
                double distance = Math.sqrt(Math.pow(enemy.exactX - exactX, 2) + Math.pow(enemy.exactY - exactY, 2))
                        / Location.cellSize;
                if (distance <= range * parent.rangeMult) {
                    enemies.add(enemy);
                }
            }
        }
        if (enemies.isEmpty()) {
            return null; // no enemy found in range
        }
        // find the first enemy in the path
        for (Enemy e : enemies) {
            if (e.pathIndex > maxIdx) {
                maxIdx = e.pathIndex;
            }
        }
        for (Enemy e : enemies) {
            if (e.pathIndex == maxIdx) {
                return e;
            }
        }
        return null;
    }

    public Point predict(Enemy enemy) {
        if (enemy == null) {
            return null;
        }

        if (enemy.rotating) {
            return new Point((int) enemy.exactX, (int) enemy.exactY);
        }

        // Get projectile speed from first weapon (use fastest if multiple)
        double pSpeed = 4.0; // default
        ArrayList<Weapon> weapons = getWeapons();
        if (!weapons.isEmpty()) {
            double fastestSpeed = 0;
            for (Weapon weapon : weapons) {
                if (weapon.pSpeed > fastestSpeed) {
                    fastestSpeed = weapon.pSpeed;
                }
            }
            if (fastestSpeed > 0) {
                pSpeed = fastestSpeed;
            }
        }

        // Calculate current distance to enemy
        double distance = Math.sqrt(Math.pow(enemy.exactX - exactX, 2) + Math.pow(enemy.exactY - exactY, 2));
        double timeToReach = distance / (pSpeed * Location.cellSize);

        // Predict enemy's position after that time
        double predictedX = enemy.exactX;
        double predictedY = enemy.exactY;

        // Calculate enemy's movement direction and speed
        if (enemy.pathIndex + 1 < Location.path.size()) {
            Point currentTarget = Location.path.get(enemy.pathIndex + 1);
            double targetX = currentTarget.x * Location.cellSize + Location.cellSize / 2.0;
            double targetY = currentTarget.y * Location.cellSize + Location.cellSize / 2.0;

            double dx = targetX - enemy.exactX;
            double dy = targetY - enemy.exactY;
            double distanceToTarget = Math.sqrt(dx * dx + dy * dy);

            if (distanceToTarget > 0) {
                // Calculate how far enemy will move in the time it takes projectile to reach
                double moveDistance = enemy.speed * Location.cellSize * timeToReach;
                predictedX += (dx / distanceToTarget) * moveDistance;
                predictedY += (dy / distanceToTarget) * moveDistance;
            }
        }

        return new Point((int) predictedX, (int) predictedY);
    }

    @Override
    public void render(Graphics2D g) {
        if (parent.image == null) {
            return; // parent image not loaded yet
        }

        Building bParent = parent instanceof Building ? (Building) parent : null;
        double cos = Math.cos(parent.facing);
        double sin = Math.sin(parent.facing);
        double rotatedOffsetX = (offsetX + (bParent != null ? (double)bParent.width / 2 * Location.cellSize - Location.cellSize / 2.0 : 0)) * cos - offsetY * sin;
        double rotatedOffsetY = offsetX * sin + (offsetY + (bParent != null ? (double)bParent.height / 2 * Location.cellSize - Location.cellSize / 2.0 : 0)) * cos;
        exactX = parent.exactX + rotatedOffsetX;
        exactY = parent.exactY + rotatedOffsetY;

        BufferedImage img = Utilities.load(getParent() + "_tur", scaleX, scaleY);
        if (img != null) {
            Graphics2D g2 = (Graphics2D) g.create();

            int drawX = (int) exactX - (img.getWidth() / 2) + imgX;
            int drawY = (int) exactY - (img.getHeight() / 2) + imgY;
            g2.rotate(facing, drawX + (img.getWidth() / 2) - imgX, drawY + (img.getHeight() / 2) - imgY);
            g2.drawImage(img, drawX, drawY, null);

            g2.dispose();
        }
    }

    // method to get all weapons with this as their parent
    ArrayList<Weapon> getWeapons() {
        ArrayList<Weapon> result = new ArrayList<>();
        for (Weapon weapon : parent.weapons) {
            if (weapon.parent == this) {
                result.add(weapon);
            }
        }
        return result;
    }

    // method to calculate most firing duration (burst * burstDelay)
    double maxDuration() {
        double maxDuration = 0.0;
        for (Weapon weapon : getWeapons()) {
            double weaponDuration = weapon.burst * weapon.burstDelay;
            if (weaponDuration > maxDuration) {
                maxDuration = weaponDuration;
            }
        }
        return maxDuration;
    }

    void recover() {
        for (Weapon weapon : getWeapons()) {
            weapon.shot = 0;
        }
    }

    // loop through all weapons with this turret as their parent and fire them
    void fire(double angle, Instance target) {
        for (Weapon weapon : getWeapons()) {
            weapon.fire(angle, target);
        }
    }
}

class Prop extends Attachment {
    int zIndex = 5;
    String name;
    Turret turParent;

    Prop(String name, Instance parent, int offsetX, int offsetY, int scaleX, int scaleY) {
        this.name = name;
        this.parent = parent;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        facing = parent.facing;
    }

    Prop(String name, Turret parent, int offsetX, int offsetY, int scaleX, int scaleY) {
        this.name = name;
        this.turParent = parent;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        facing = parent.facing;
    }

    // with z-index
    Prop(String name, Instance parent, int offsetX, int offsetY, int scaleX, int scaleY, int zIndex) {
        this.name = name;
        this.parent = parent;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.zIndex = zIndex;
        facing = parent.facing;
    }

    Prop(String name, Turret parent, int offsetX, int offsetY, int scaleX, int scaleY, int zIndex) {
        this.name = name;
        this.turParent = parent;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.zIndex = zIndex;
        facing = parent.facing;
    }

    @Override
    void update(float deltaTime) {
    }

    @Override
    public void render(Graphics2D g) {
        if (parent != null && parent.image == null) {
            return; // parent image not loaded yet
        }
        if (turParent != null && turParent.parent.image == null) {
            return; // parent image not loaded yet
        }

        if (parent != null) {
            double cos = Math.cos(parent.facing);
            double sin = Math.sin(parent.facing);
            double rotatedOffsetX = offsetX * cos - offsetY * sin;
            double rotatedOffsetY = offsetX * sin + offsetY * cos;
            exactX = parent.exactX + rotatedOffsetX;
            exactY = parent.exactY + rotatedOffsetY;
            facing = parent.facing;
        } else if (turParent != null) {
            Instance parentInstance = turParent.parent;
            double cos = Math.cos(parentInstance.facing);
            double sin = Math.sin(parentInstance.facing);
            double turretRotatedX = turParent.offsetX * cos - turParent.offsetY * sin;
            double turretRotatedY = turParent.offsetX * sin + turParent.offsetY * cos;
            double turretCos = Math.cos(turParent.facing);
            double turretSin = Math.sin(turParent.facing);
            double propRotatedX = offsetX * turretCos - offsetY * turretSin;
            double propRotatedY = offsetX * turretSin + offsetY * turretCos;
            exactX = parentInstance.exactX + turretRotatedX + propRotatedX;
            exactY = parentInstance.exactY + turretRotatedY + propRotatedY;
            facing = turParent.facing;
        }

        BufferedImage img = Utilities.load(getParent() + "_" + name, scaleX, scaleY);
        if (img != null) {
            // Save the current graphics state
            Graphics2D g2 = (Graphics2D) g.create();

            int drawX = (int) exactX - (img.getWidth() / 2) + imgX;
            int drawY = (int) exactY - (img.getHeight() / 2) + imgY;
            g2.rotate(facing, drawX + (img.getWidth() / 2) - imgX, drawY + (img.getHeight() / 2) - imgY);
            g2.drawImage(img, drawX, drawY, null);

            // Dispose of the graphics copy to restore original state
            g2.dispose();
        }
    }

    @Override
    String getParent() {
        if (parent != null) {
            return parent.getClass().getSimpleName().toLowerCase();
        } else if (turParent != null) {
            return turParent.parent.getClass().getSimpleName().toLowerCase();
        }
        return "unknown";
    }
}

abstract class Weapon { // can only be binded to a turret, define the weapon's behavior rather than
                        // providing a display
    Turret parent;
    int damage = 10;
    double rof = 1; // seconds between shots
    int pierce = 0;
    BufferedImage projectileImg;
    double pSpeed = 4.0; // cells per second
    int offsetX = 0, offsetY = 0; // offset from turret center in pixels
    int muzzleX = 0, muzzleY = 0; // projectile creation coordinates

    int burst = 1; // shots per trigger pull, enemies will use burst rather than rof
    double burstDelay = 0.5; // seconds between shots in a burst
    int shot = 0; // shots fired in current burst
    double spread = 0; // degrees of random spread

    double rofTimer = 0.0;
    double burstTimer = 0.0;

    public Weapon(Turret parent, int offsetX, int offsetY) {
        this.parent = parent;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    void update(float deltaTime) {
        if (GameManager.getInstance().getPower() < 0 && parent.parent instanceof Building) {
            deltaTime = deltaTime / 2;
        }

        // keep updating the timers even when not firing
        rofTimer += deltaTime;
        burstTimer += deltaTime;
        double cos = Math.cos(parent.facing);
        double sin = Math.sin(parent.facing);
        this.muzzleX = (int) (parent.exactX + (offsetX * cos - offsetY * sin));
        this.muzzleY = (int) (parent.exactY + (offsetX * sin + offsetY * cos));
    }

    abstract void fire(double angle, Instance target);
}

// BUILDING WEAPONS
// ---------------------------------------------------------------------------------------------
class AutoCannonW extends Weapon {
    public AutoCannonW(Turret parent, int offsetX, int offsetY) {
        super(parent, offsetX, offsetY);
        damage = 5;
        parent.range = 4;
        rof = 0.3;
        pSpeed = 10;
        spread = 5;
    }

    @Override
    void fire(double angle, Instance target) {
        if (rofTimer > rof / parent.parent.rofMult) {
            Gamma.add(new Bullet("bullet", 0.2, 0.2, this));
            rofTimer = 0;
        }
    }
}

class ArtilleryW extends Weapon {
    public ArtilleryW(Turret parent, int offsetX, int offsetY) {
        super(parent, offsetX, offsetY);
        damage = 75;
        parent.range = 14;
        rof = 5;
        pSpeed = 6;
        spread = 0;
    }

    @Override
    void fire(double angle, Instance target) {
        if (rofTimer > rof / parent.parent.rofMult) {
            Gamma.add(new HEShell("heshell", 0.2, 0.2, this, target));
            rofTimer = 0;
        }
    }
}

// ENEMY WEAPONS
// ---------------------------------------------------------------------------------------------
class MG extends Weapon {
    public MG(Turret parent, int muzzleX, int muzzleY) {
        super(parent, muzzleX, muzzleY);
        damage = 2;
        parent.range = 4;
        burst = 5;
        burstDelay = 0.4;
        pSpeed = 5.0;
        spread = 5;
    }

    @Override
    void fire(double angle, Instance target) {
        if (shot < burst && burstTimer > burstDelay / parent.parent.rofMult) {
            Gamma.add(new Bullet("small_bullet", .15, .15, this));
            shot++;
            burstTimer = 0;
        }
    }
}

// new weapon

abstract class Projectile extends Instance {
    Instance iParent; // the instance that fired this projectile
    Weapon parent;
    String imgName;
    int zIndex = 15;
    ArrayList<Instance> hit = new ArrayList<>(); // store hit instances to avoid hitting the same instance multiple
                                                 // times

    double speed = 5.0; // cells per second
    boolean affectAllies = false;
    boolean affectEnemies = true;
    double acc = 0.1; // acceleration in cells per second squared
    double dec = 0.1; // deceleration in cells per second squared

    public Projectile(String name, double hitboxW, double hitboxH, Weapon parent) {
        super(0, 0); // doesn't matter, we only need the exact one
        this.parent = parent;
        this.iParent = parent.parent.parent; // what the fuck lmfao
        this.exactX = parent.muzzleX;
        this.exactY = parent.muzzleY;
        this.facing = parent.parent.facing + Math.toRadians((Math.random() - 0.5) * parent.spread);
        this.speed = parent.pSpeed;
        hitbox(0, 0, hitboxW, hitboxH); // Add hitbox for projectile
        this.imgName = name;
    }

    public void render(Graphics2D g) {
        if (image == null) {
            image = Utilities.load(imgName, scaleX, scaleY);
        }

        if (image != null) {
            // Save the current graphics state
            Graphics2D g2 = (Graphics2D) g.create();

            int drawX = (int) exactX - (image.getWidth() / 2);
            int drawY = (int) exactY - (image.getHeight() / 2);
            g2.rotate(facing, drawX + (image.getWidth() / 2), drawY + (image.getHeight() / 2));
            g2.drawImage(image, drawX, drawY, null);

            // Dispose of the graphics copy to restore original state
            g2.dispose();
        }
    }

    public void destroy() { // used to check if this projectile should still exist
        double radius;
        if (image != null) {
            int imgW = image.getWidth();
            int imgH = image.getHeight();
            radius = 0.5 * Math.sqrt(imgW * imgW + imgH * imgH);
        } else {
            radius = 100;
        }
        if (exactX + radius < 0 || exactX - radius > Gamma.GAME_WIDTH ||
                exactY + radius < 0 || exactY - radius > Gamma.HEIGHT) {
            alive = false;
        }
        if (hit.size() > parent.pierce)
            alive = false; // exceeded pierce limit
    }

    // SAT algorithm or smthing, i don't know about these stuff
    Instance collision(String exclude) {
        // Parse exclusion list
        String[] excludedTypes = (exclude != null && !exclude.trim().isEmpty()) ? exclude.trim().split("\\s+")
                : new String[0];

        for (Instance instance : Gamma.getInstances()) {
            if (instance == iParent || hit.contains(instance))
                continue;

            String instanceType = instance.getClass().getSimpleName().toLowerCase();
            String superType;
            if (instance instanceof Building)
                superType = "building";
            else if (instance instanceof Enemy)
                superType = "enemy";
            else if (instance instanceof Projectile)
                superType = "projectile";
            else
                superType = "";

            boolean shouldExclude = false;
            for (String excludedType : excludedTypes) {
                String excluded = excludedType.toLowerCase();
                // if allies, check if same superclass (Enemy or Building)
                if (excluded.equals("allies")) {
                    if (iParent instanceof Enemy && superType == "enemy") {
                        shouldExclude = true;
                        break;
                    }
                    if (iParent instanceof Building && superType == "building") {
                        shouldExclude = true;
                        break;
                    }
                }
                if (instanceType.equals(excluded) || (!superType.isEmpty() && superType.equals(excluded))) {
                    shouldExclude = true;
                    break;
                }
            }
            if (shouldExclude)
                continue;

            for (Hitbox ourHitbox : hitboxes) {
                for (Hitbox theirHitbox : instance.hitboxes) {
                    boolean collision = true;

                    ArrayList<Point> allAxes = new ArrayList<>();

                    for (int i = 0; i < 4; i++) {
                        Point current = ourHitbox.corners.get(i);
                        Point next = ourHitbox.corners.get((i + 1) % 4);

                        int edgeX = next.x - current.x;
                        int edgeY = next.y - current.y;

                        Point axis = new Point(-edgeY, edgeX);

                        double length = Math.sqrt(axis.x * axis.x + axis.y * axis.y);
                        if (length > 0) {
                            axis.x = (int) (axis.x / length * 1000); // Scale up to avoid precision loss
                            axis.y = (int) (axis.y / length * 1000);
                        }

                        allAxes.add(axis);
                    }

                    for (int i = 0; i < 4; i++) {
                        Point current = theirHitbox.corners.get(i);
                        Point next = theirHitbox.corners.get((i + 1) % 4);

                        int edgeX = next.x - current.x;
                        int edgeY = next.y - current.y;

                        Point axis = new Point(-edgeY, edgeX);

                        double length = Math.sqrt(axis.x * axis.x + axis.y * axis.y);
                        if (length > 0) {
                            axis.x = (int) (axis.x / length * 1000); // Scale up to avoid precision loss
                            axis.y = (int) (axis.y / length * 1000);
                        }

                        allAxes.add(axis);
                    }

                    for (Point axis : allAxes) {
                        double ourMin = Double.MAX_VALUE;
                        double ourMax = Double.MIN_VALUE;

                        for (Point corner : ourHitbox.corners) {
                            double projection = (corner.x * axis.x + corner.y * axis.y) / 1000.0; // Scale back down

                            if (projection < ourMin)
                                ourMin = projection;
                            if (projection > ourMax)
                                ourMax = projection;
                        }

                        double theirMin = Double.MAX_VALUE;
                        double theirMax = Double.MIN_VALUE;

                        for (Point corner : theirHitbox.corners) {
                            double projection = (corner.x * axis.x + corner.y * axis.y) / 1000.0; // Scale back down

                            if (projection < theirMin)
                                theirMin = projection;
                            if (projection > theirMax)
                                theirMax = projection;
                        }

                        if (ourMax < theirMin || theirMax < ourMin) {
                            collision = false;
                            break; // Separating axis found, no collision
                        }
                    }
                    if (collision) {
                        hit.add(instance);
                        return instance; // Hit detected, return the hit instance
                    }
                }
            }
        }
        return null;
    }

    // movement type
    void simple(float deltaTime) {
        double moveDistance = speed * Location.cellSize * deltaTime;
        exactX += Math.cos(facing) * moveDistance;
        exactY += Math.sin(facing) * moveDistance;
        destroy();
    }

    void accelerate(float deltaTime) {
        double moveDistance = speed * Location.cellSize * deltaTime;
        speed += acc * deltaTime;
        exactX += Math.cos(facing) * moveDistance;
        exactY += Math.sin(facing) * moveDistance;
        destroy();
    }

    void decelerate(float deltaTime) {
        double moveDistance = speed * Location.cellSize * deltaTime;
        speed -= dec * deltaTime;
        if (speed < 0)
            speed = 0;
        exactX += Math.cos(facing) * moveDistance;
        exactY += Math.sin(facing) * moveDistance;
        destroy();
    }

    // interaction methods
    void damage(Instance target, int damage) {
        target.health -= (int) (damage * iParent.damageMult);
        if (target.health <= 0) {
            target.destroy();
        }
    }
    void explode(double radius) {
        Empty explosionHolder = new Empty();
        explosionHolder.exactX = this.exactX;
        explosionHolder.exactY = this.exactY;
        Gamma.add(explosionHolder);
        Utilities.animLoad("explode", (int) exactX, (int) exactY, 1, 1, explosionHolder, false);
        
        // Deal area damage to enemies within radius
        double explosionRadius = radius * Location.cellSize;
        for (Instance instance : Gamma.getInstances()) {
            if (instance == iParent)
                continue;

            String superType;
            if (instance instanceof Building)
                superType = "building";
            else if (instance instanceof Enemy)
                superType = "enemy";
            else
                superType = "";

            boolean isAlly = false;
            if (iParent instanceof Enemy && superType == "enemy") {
                isAlly = true;
            }
            if (iParent instanceof Building && superType == "building") {
                isAlly = true;
            }
            if (isAlly)
                continue;

            double distance = Math.hypot(instance.exactX - exactX, instance.exactY - exactY);
            if (distance <= explosionRadius) {
                damage(instance, parent.damage);
            }
        }
        alive = false; // destroy the projectile after exploding
    }
}

class Bullet extends Projectile {

    public Bullet(String name, double hitboxW, double hitboxH, Weapon parent) {
        super(name, hitboxW, hitboxH, parent);
    }

    @Override
    void update(float deltaTime) {
        if (!alive)
            return;
        // simply move forward
        simple(deltaTime);

        Instance target = collision("allies projectile");
        if (target != null) {
            damage(target, parent.damage);
        }
    }
}

class HEShell extends Projectile {
    double targetX, targetY;
    double travelDistance;
    double distanceTraveled = 0;
    
    public HEShell(String name, double hitboxW, double hitboxH, Weapon parent, Instance target) {
        super(name, hitboxW, hitboxH, parent);
        // Use the turret's predict() method to get the actual predicted position
        // facing is already set by Projectile constructor from parent.parent.facing
        
        if (target instanceof Enemy) {
            Point predicted = parent.parent.predict((Enemy) target);
            if (predicted != null) {
                this.targetX = predicted.x;
                this.targetY = predicted.y;
            } else {
                // Fallback to current position if prediction fails
                this.targetX = target.exactX;
                this.targetY = target.exactY;
            }
        } else {
            // For non-enemy targets, use exact position
            this.targetX = target.exactX;
            this.targetY = target.exactY;
        }
        
        // Calculate travel distance to predicted position
        double dx = targetX - exactX;
        double dy = targetY - exactY;
        this.travelDistance = Math.hypot(dx, dy);
    }

    @Override
    void update(float deltaTime) {
        if (!alive)
            return;
        
        // Move in the direction the turret was facing when it fired
        double moveDistance = speed * Location.cellSize * deltaTime;
        exactX += Math.cos(facing) * moveDistance;
        exactY += Math.sin(facing) * moveDistance;
        distanceTraveled += moveDistance;

        // Explode when traveled far enough to reach the predicted target position
        if (distanceTraveled >= travelDistance) {
            explode(1.5);
            return;
        }

        // Destroy if goes off-screen (safety check)
        double radius = 20; // approximate shell size
        if (exactX + radius < 0 || exactX - radius > Gamma.GAME_WIDTH ||
            exactY + radius < 0 || exactY - radius > Gamma.HEIGHT) {
            alive = false;
        }
    }
}

// new projectile

// WAVE SYSTEM
// ---------------------------------------------------------------------------------------------
class Wave {
    public Class<? extends Enemy> enemyClass;
    public int count;
    public double spawnInterval; // seconds between spawns of this enemy type
    public double delay; // delay before starting to spawn this enemy type

    public Wave(Class<? extends Enemy> enemyClass, int count, double spawnInterval, double delay) {
        this.enemyClass = enemyClass;
        this.count = count;
        this.spawnInterval = spawnInterval;
        this.delay = delay;
    }

    public Wave(Class<? extends Enemy> enemyClass, int count, double spawnInterval) {
        this(enemyClass, count, spawnInterval, 0.0);
    }
}

class WaveManager {
    private static HashMap<Integer, ArrayList<Wave>> waves = new HashMap<>();
    private static HashMap<Wave, Integer> currentSpawnCount = new HashMap<>();
    private static HashMap<Wave, Double> spawnTimer = new HashMap<>();
    private static HashMap<Wave, Double> delayTimer = new HashMap<>();
    public static boolean waveActive = false;
    private static double waveStartTime = 0;

    // Wave completion and auto-advance
    public static boolean waveCompleted = false;
    private static double waveCompletionTime = 0;
    public static int conqueredWave = 0;
    private static final double WAVE_INTERMISSION = 5.0; // 5 seconds

    // Infinite wave system
    public static int currentCycle = 1; // Tracks which cycle we're on (1st run, 2nd run, etc.)

    static {
        letTheFunBegins();
    }

    // --- Helpers to dynamically map continuous wave numbers to defined base
    // patterns ---
    private static ArrayList<Integer> getBasePatternKeys() {
        ArrayList<Integer> keys = new ArrayList<>(waves.keySet());
        keys.sort(Integer::compareTo);
        return keys;
    }

    private static int getBasePatternCount() {
        return getBasePatternKeys().size();
    }

    // Returns the actual base pattern key for an arbitrary wave number (1-based).
    // -1 if none defined.
    private static int getPatternKeyForWave(int waveNumber) {
        ArrayList<Integer> keys = getBasePatternKeys();
        if (keys.isEmpty() || waveNumber <= 0)
            return -1;
        int idx = (waveNumber - 1) % keys.size();
        return keys.get(idx);
    }

    public static void startWave(int waveNumber) {
        // Map any wave number to one of the defined base patterns dynamically
        int patternKey = getPatternKeyForWave(waveNumber);
        if (patternKey == -1 || !waves.containsKey(patternKey)) {
            return;
        }

        waveActive = true;
        waveStartTime = 0; // Will be set in update
        currentSpawnCount.clear();
        spawnTimer.clear();
        delayTimer.clear();

        // Initialize spawn tracking for this wave pattern
        for (Wave Wave : waves.get(patternKey)) {
            currentSpawnCount.put(Wave, 0);
            spawnTimer.put(Wave, 0.0);
            delayTimer.put(Wave, 0.0);
        }

    }

    public static void update(float deltaTime, int currentWave) {
        // Dynamically map currentWave to a base pattern
        int patternKey = getPatternKeyForWave(currentWave);
        if (!waveActive || patternKey == -1 || !waves.containsKey(patternKey))
            return;

        if (waveStartTime == 0) {
            waveStartTime = System.currentTimeMillis() / 1000.0;
        }

        boolean waveComplete = true;

        for (Wave Wave : waves.get(patternKey)) {
            // Ensure timers are initialized (safety check)
            if (!delayTimer.containsKey(Wave)) {
                delayTimer.put(Wave, 0.0);
            }
            if (!spawnTimer.containsKey(Wave)) {
                spawnTimer.put(Wave, 0.0);
            }
            if (!currentSpawnCount.containsKey(Wave)) {
                currentSpawnCount.put(Wave, 0);
            }

            // Update delay timer
            delayTimer.put(Wave, delayTimer.get(Wave) + deltaTime);

            // Part is done only if we've spawned the required count
            boolean partDone = currentSpawnCount.get(Wave) >= Wave.count;
            if (!partDone) {
                // If any part still has pending spawns (even if still in delay), wave is not
                // complete
                waveComplete = false;

                // Only start spawning once its delay has elapsed
                if (delayTimer.get(Wave) >= Wave.delay) {
                    // Update spawn timer
                    spawnTimer.put(Wave, spawnTimer.get(Wave) + deltaTime);

                    // Time to spawn?
                    if (spawnTimer.get(Wave) >= Wave.spawnInterval) {
                        spawnEnemy(Wave.enemyClass);
                        currentSpawnCount.put(Wave, currentSpawnCount.get(Wave) + 1);
                        spawnTimer.put(Wave, 0.0); // Reset spawn timer
                    }
                }
            }
        }

        // Check if wave is complete
        if (waveComplete) {
            waveActive = false;
            waveCompleted = true;
            waveCompletionTime = System.currentTimeMillis() / 1000.0;
            conqueredWave = currentWave;
        }
    }

    public static int updateWaveCompletion(float deltaTime, int currentWave) {
        if (waveCompleted) {
            double currentTime = System.currentTimeMillis() / 1000.0;
            if (currentTime - waveCompletionTime >= WAVE_INTERMISSION) {
                waveCompleted = false;
                // Always increment to next wave number
                int nextWave = conqueredWave + 1;

                // Update cycle based on number of base patterns
                int baseCount = getBasePatternCount();
                currentCycle = baseCount > 0 ? ((nextWave - 1) / baseCount) + 1 : 1;

                startWave(nextWave);
                return nextWave;
            }
        }
        return currentWave; // Return the same wave number if no change
    }

    private static void spawnEnemy(Class<? extends Enemy> enemyClass) {
        try {
            Enemy enemy = enemyClass.getDeclaredConstructor(int.class, int.class)
                    .newInstance(Location.start.x, Location.start.y);

            // Apply cycle multipliers to the enemy
            enemy.healthMult = currentCycle;

            // Recalculate health with new multiplier
            enemy.health = (int) (enemy.maxHealth * enemy.healthMult);
            enemy.maxHealth = (int) (enemy.maxHealth * enemy.healthMult);

            Gamma.add(enemy);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static double calmBeforeTheStorm() {
        if (!waveCompleted)
            return 0;
        double currentTime = System.currentTimeMillis() / 1000.0;
        double elapsed = currentTime - waveCompletionTime;
        return Math.max(0, WAVE_INTERMISSION - elapsed);
    }

    public static void addWave(int waveNumber, ArrayList<Wave> Wave) {
        waves.put(waveNumber, Wave);
    }

    public static void addWave(int waveNumber, Wave... parts) {
        ArrayList<Wave> list = waves.get(waveNumber);
        if (list == null) {
            list = new ArrayList<>();
            waves.put(waveNumber, list);
        }
        for (Wave p : parts) {
            if (p != null)
                list.add(p);
        }
    }

    public static Wave w(Class<? extends Enemy> enemyClass, int count, double spawnInterval) {
        return new Wave(enemyClass, count, spawnInterval);
    }

    public static Wave w(Class<? extends Enemy> enemyClass, int count, double spawnInterval, double delay) {
        return new Wave(enemyClass, count, spawnInterval, delay);
    }

    public static int getCurrentCycle() {
        return currentCycle;
    }

    private static void letTheFunBegins() {
        // format: addWave(waveNumber, w(EnemyClass.class, count, spawnInterval, delay), ...);
        addWave(1,
                w(Recon.class, 5, 2.0, 0.0));

        addWave(2,
                w(Recon.class, 5, 1.5, 0.0),
                w(Recon.class, 3, 2.0, 0.0));

        addWave(3,
                w(Recon.class, 4, 1.0, 0.0),
                w(Recon.class, 4, 1.0, 5.0),
                w(Recon.class, 4, 1.0, 10.0));

        addWave(4,
                w(Recon.class, 8, 1.0, 0.0),
                w(Recon.class, 8, 1.2, 0.0));

        addWave(5,
                w(Recon.class, 3, 3.0, 0.0),
                w(Recon.class, 10, 0.5, 5.0),
                w(Recon.class, 5, 1.0, 15.0));
    }
}