import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

// cmd to convert gif to png frames at 60 fps
// ffmpeg -i INPUTNAME.mov -r 60 FRAMENAME%03d.png

public class Utilities {
    static final String imgPath = "resources/images/";
    static final String animPath = "resources/anims/";
    static final String fontPath = "resources/fonts/";
    
    // Font cache to avoid reloading fonts
    private static java.util.HashMap<String, java.awt.Font> fontCache = new java.util.HashMap<>();
    
    // Image cache to avoid reloading images from JAR
    private static java.util.HashMap<String, BufferedImage> imageCache = new java.util.HashMap<>();
    
    /**
     * Load an image from resources (works both in development and in JAR)
     */
    private static BufferedImage loadImageResource(String path) throws IOException {
        // Try loading from JAR first
        InputStream is = Utilities.class.getClassLoader().getResourceAsStream(path);
        if (is != null) {
            return ImageIO.read(is);
        }
        // Fall back to file system (for development)
        return ImageIO.read(new File(path));
    }
    
    public static BufferedImage load(String name, double scaleX, double scaleY) {
        // Create cache key including scale factors
        String cacheKey = name + "_" + scaleX + "_" + scaleY;
        
        // Check cache first
        BufferedImage cached = imageCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        BufferedImage image = null;
        try {
            // Try to load the specific image first
            image = loadImageResource(imgPath + name + ".png");
        } catch (IOException e) {
            // If not found, try to load default.png
            try {
                image = loadImageResource(imgPath + "default.png");
                System.out.println("Image not found: " + name + ".png, using default.png");
            } catch (IOException e2) {
                System.err.println("Default image also not found.");
            }
        }
        int width = Math.max(1, (int)(image.getWidth() * scaleX));
        int height = Math.max(1, (int)(image.getHeight() * scaleY));

        // Create scaled image
        BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2d = scaledImage.createGraphics();
        g2d.drawImage(image, 0, 0, width, height, null);
        g2d.dispose();
        
        // Cache the scaled image
        imageCache.put(cacheKey, scaledImage);
        
        return scaledImage;
    }

    public static class Animation {
        private Instance parent;
        private String name;
        private int x, y;
        private double scaleX, scaleY;
        private int currentFrame = 1;
        private boolean alive = true;
        private boolean loop = false;
        private double frameTimer = 0.0;
        private static final double FRAME_DURATION = 1.0 / 60.0; // 60 FPS animation speed
        
        // Cache for loaded frames to avoid reloading from JAR
        private java.util.HashMap<Integer, BufferedImage> frameCache = new java.util.HashMap<>();
        private java.util.HashMap<Integer, BufferedImage> scaledFrameCache = new java.util.HashMap<>();
        private int maxFrameChecked = 0;

        public Animation(String name, int x, int y, double scaleX, double scaleY, Instance parent, boolean loop) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.parent = parent;
            this.loop = loop;
            parent.anims.add(this);
        }

        public Animation(String name, int x, int y, Instance parent) {
            this(name, x, y, 1.0, 1.0, parent, false);
        }

        public void update(float deltaTime) {
            frameTimer += deltaTime;
            
            // Advance frames based on accumulated time
            while (frameTimer >= FRAME_DURATION) {
                frameTimer -= FRAME_DURATION;
                
                // Check if next frame exists (only check once per frame number)
                int nextFrame = currentFrame + 1;
                if (nextFrame > maxFrameChecked) {
                    String nextFrameName = name + String.format("%03d", nextFrame);
                    try {
                        BufferedImage frame = loadImageResource(animPath + name + "/" + nextFrameName + ".png");
                        frameCache.put(nextFrame, frame);
                        maxFrameChecked = nextFrame;
                        currentFrame = nextFrame;
                    } catch (IOException e) {
                        // No more frames
                        if (loop) {
                            currentFrame = 1; // Loop back to first frame
                        } else {
                            alive = false;
                            break;
                        }
                    }
                } else {
                    // Frame existence already checked
                    if (frameCache.containsKey(nextFrame)) {
                        currentFrame = nextFrame;
                    } else {
                        // Reached end of animation
                        if (loop) {
                            currentFrame = 1;
                        } else {
                            alive = false;
                            break;
                        }
                    }
                }
            }
        }

        public void render(java.awt.Graphics2D g) {
            if (!alive) return;

            // Check if we have the scaled frame cached
            BufferedImage scaledFrame = scaledFrameCache.get(currentFrame);
            if (scaledFrame != null) {
                // Use cached scaled frame
                g.drawImage(scaledFrame, x - scaledFrame.getWidth() / 2, y - scaledFrame.getHeight() / 2, null);
                return;
            }

            // Get cached frame or load it
            BufferedImage frame = frameCache.get(currentFrame);
            if (frame == null) {
                // Load and cache the frame
                String frameName = name + String.format("%03d", currentFrame);
                try {
                    frame = loadImageResource(animPath + name + "/" + frameName + ".png");
                    frameCache.put(currentFrame, frame);
                    if (currentFrame > maxFrameChecked) {
                        maxFrameChecked = currentFrame;
                    }
                } catch (IOException e) {
                    // Frame doesn't exist, mark as dead
                    alive = false;
                    return;
                }
            }

            // Scale the frame and cache it
            int width = Math.max(1, (int)(frame.getWidth() * scaleX));
            int height = Math.max(1, (int)(frame.getHeight() * scaleY));

            scaledFrame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = scaledFrame.createGraphics();
            g2d.drawImage(frame, 0, 0, width, height, null);
            g2d.dispose();
            
            // Cache the scaled frame
            scaledFrameCache.put(currentFrame, scaledFrame);

            // Draw at specified position
            g.drawImage(scaledFrame, x - scaledFrame.getWidth() / 2, y - scaledFrame.getHeight() / 2, null);
        }

        public boolean isAlive() {
            return alive;
        }
    }
    
    // Factory methods to create animations
    public static Animation animLoad(String name, int x, int y, double scaleX, double scaleY, Instance parent, boolean loop) {
        return new Animation(name, x, y, scaleX, scaleY, parent, loop);
    }

    public static Animation animLoad(String name, int x, int y, Instance parent) {
        return new Animation(name, x, y, 1.0, 1.0, parent, false);
    }

    public static Animation animLoad(String name, int x, int y, Instance parent, boolean loop) {
        return new Animation(name, x, y, 1.0, 1.0, parent, loop);
    }
    
    // Font loading methods
    public static java.awt.Font loadFont(String fontName, float size) {
        String cacheKey = fontName + "_" + size;
        
        // Check if font is already cached
        if (fontCache.containsKey(cacheKey)) {
            return fontCache.get(cacheKey);
        }
        
        java.awt.Font font = null;
        try {
            // Try to load custom font from JAR or file system
            java.awt.Font baseFont;
            InputStream fontStream = Utilities.class.getClassLoader().getResourceAsStream(fontPath + fontName + ".ttf");
            if (fontStream != null) {
                baseFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, fontStream);
            } else {
                // Fall back to file system (for development)
                baseFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, new File(fontPath + fontName + ".ttf"));
            }
            font = baseFont.deriveFont(size);
            
            // Register the font with the graphics environment (optional but recommended)
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(baseFont);
            
        } catch (Exception e) {
            // If custom font fails, try system fonts
            try {
                font = new java.awt.Font(fontName, java.awt.Font.PLAIN, (int)size);
            } catch (Exception e2) {
                // Fallback to Arial
                font = new java.awt.Font("Arial", java.awt.Font.PLAIN, (int)size);
                System.out.println("Font not found: " + fontName + ", using Arial");
            }
        }
        
        // Cache the font
        fontCache.put(cacheKey, font);
        return font;
    }
    
    // Convenience method with style parameter
    public static Font loadFont(String fontName, int style, float size) {
        Font baseFont = loadFont(fontName, size);
        return baseFont.deriveFont(style, size);
    }

    // set custom cursor
    public static void setCustomCursor(Component component, String cursorImageName, int offsetX, int offsetY) {
        BufferedImage cursorImage = load(cursorImageName, 1.0, 1.0);
        if (cursorImage.getWidth() > 32 || cursorImage.getHeight() > 32) {
            int newWidth = Math.min(32, cursorImage.getWidth());
            int newHeight = Math.min(32, cursorImage.getHeight());
            
            BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = resizedImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(cursorImage, 0, 0, newWidth, newHeight, null);
            g2d.dispose();
            
            cursorImage = resizedImage;
        }
        int hotspotX = Math.max(0, Math.min(cursorImage.getWidth() - 1, offsetX + cursorImage.getWidth() / 2));
        int hotspotY = Math.max(0, Math.min(cursorImage.getHeight() - 1, offsetY + cursorImage.getHeight() / 2));
        Point hotspot = new Point(hotspotX, hotspotY);
        Cursor customCursor = Toolkit.getDefaultToolkit().createCustomCursor(cursorImage, hotspot, "Custom Cursor");
        component.setCursor(customCursor);
    }
}
