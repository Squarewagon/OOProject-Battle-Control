import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * AssetLoader - Handles loading and caching of all game assets
 * Prevents stuttering by pre-loading all images, animations, and configs before gameplay starts
 */
public class AssetLoader {
    private List<AssetTask> assetTasks;
    private int currentTaskIndex = 0;
    private boolean loadingComplete = false;
    private String currentLoadingMessage = "Loading...";

    public AssetLoader(Gamma gamma) {
        this.assetTasks = new ArrayList<>();
        initializeAssetTasks();
    }

    /**
     * Initialize all assets that need to be loaded
     */
    private void initializeAssetTasks() {
        ConfigManager config = GameManager.getInstance().getConfigManager();

        // 1. Load building images and animations
        for (String buildingName : config.getAllBuildingStats().keySet()) {
            final String name = buildingName;
            assetTasks.add(new AssetTask("Loading building: " + name, () -> {
                BufferedImage img = Utilities.load(name.toLowerCase(), 1.0, 1.0);
                return img != null;
            }));
        }

        // 2. Load enemy images and animations
        for (String enemyName : config.getAllEnemyStats().keySet()) {
            final String name = enemyName;
            assetTasks.add(new AssetTask("Loading enemy: " + name, () -> {
                BufferedImage img = Utilities.load(name.toLowerCase(), 1.0, 1.0);
                return img != null;
            }));
        }

        // 3. Load weapon/projectile images
        for (String weaponName : config.getAllWeaponStats().keySet()) {
            final String name = weaponName;
            assetTasks.add(new AssetTask("Loading projectile: " + name, () -> {
                BufferedImage img = Utilities.load(name.toLowerCase(), 1.0, 1.0);
                return img != null;
            }));
        }

        // 4. Load UI elements
        String[] uiElements = {
            "normal_cursor", "repair_cursor", "sell_cursor",
            "menu_button", "title", "default"
        };
        for (String uiElement : uiElements) {
            final String name = uiElement;
            assetTasks.add(new AssetTask("Loading UI: " + name, () -> {
                BufferedImage img = Utilities.load(name, 1.0, 1.0);
                return img != null;
            }));
        }

        // 5. Load animation frames
        String[] animationNames = {
            "cat_cheese", "explode", "selection"
        };
        for (String animName : animationNames) {
            final String name = animName;
            assetTasks.add(new AssetTask("Caching animation: " + name, () -> {
                // Pre-load ALL animation frames for this animation
                // This ensures no I/O happens during gameplay
                String animPath = "resources/anims/";
                int frameNum = 1;
                boolean hasMore = true;
                while (hasMore) {
                    String frameName = name + String.format("%03d", frameNum);
                    try {
                        java.io.InputStream is = AssetLoader.class.getClassLoader().getResourceAsStream(animPath + name + "/" + frameName + ".png");
                        if (is != null) {
                            java.awt.image.BufferedImage frame = javax.imageio.ImageIO.read(is);
                            if (frame != null) {
                                frameNum++;
                            } else {
                                hasMore = false;
                            }
                        } else {
                            hasMore = false;
                        }
                    } catch (Exception e) {
                        hasMore = false;
                    }
                }
                return true;
            }));
        }

        // 6. Load fonts
        String[] fontNames = { "Romanov", "Arial" };
        for (String fontName : fontNames) {
            final String name = fontName;
            assetTasks.add(new AssetTask("Loading font: " + name, () -> {
                java.awt.Font font = Utilities.loadFont(name, 28f);
                return font != null;
            }));
        }
    }

    /**
     * Execute the next asset loading task
     * Returns true if loading is complete
     */
    public boolean update() {
        if (loadingComplete) {
            return true;
        }

        if (currentTaskIndex < assetTasks.size()) {
            AssetTask task = assetTasks.get(currentTaskIndex);
            currentLoadingMessage = task.message;

            try {
                task.execute();
                currentTaskIndex++;
            } catch (Exception e) {
                System.err.println("Error loading asset: " + task.message);
                e.printStackTrace();
                currentTaskIndex++; // Skip failed task and continue
            }
        } else {
            loadingComplete = true;
        }

        return loadingComplete;
    }

    /**
     * Get the current loading message
     */
    public String getCurrentMessage() {
        return currentLoadingMessage;
    }

    /**
     * Get loading progress as a percentage (0-100)
     */
    public int getProgress() {
        if (assetTasks.isEmpty()) {
            return 100;
        }
        return (int) ((currentTaskIndex * 100) / assetTasks.size());
    }

    /**
     * Get loading progress as a fraction
     */
    public String getProgressText() {
        return currentTaskIndex + " / " + assetTasks.size();
    }

    public boolean isComplete() {
        return loadingComplete;
    }

    /**
     * Inner class to represent an asset loading task
     */
    private static class AssetTask {
        String message;
        AssetLoadCallback callback;

        AssetTask(String message, AssetLoadCallback callback) {
            this.message = message;
            this.callback = callback;
        }

        void execute() throws Exception {
            callback.load();
        }
    }

    /**
     * Interface for asset loading callbacks
     */
    private interface AssetLoadCallback {
        boolean load() throws Exception;
    }
}
