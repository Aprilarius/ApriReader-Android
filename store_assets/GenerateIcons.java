import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenerateIcons {

    public static void main(String[] args) throws Exception {
        String storeDir = "c:/Users/Bahadur/Desktop/Apri/store_assets";
        String artifactDir = "C:/Users/Bahadur/.gemini/antigravity/brain/f029ed32-1792-4f5c-815b-3bb03597c7c6";

        new File(storeDir).mkdirs();

        // 1. Master 1024x1024 Full Bleed (Square)
        saveImage(renderIcon(1024, IconShape.SQUARE), storeDir + "/icon_master_1024x1024.png");
        saveImage(renderIcon(1024, IconShape.SQUARE), artifactDir + "/icon_master_1024x1024.png");

        // 2. Google Play Store 512x512 Full Bleed
        saveImage(renderIcon(512, IconShape.SQUARE), storeDir + "/icon_playstore_512x512.png");
        saveImage(renderIcon(512, IconShape.SQUARE), artifactDir + "/icon_playstore_512x512.png");

        // 3. RuStore 512x512 Full Bleed
        saveImage(renderIcon(512, IconShape.SQUARE), storeDir + "/icon_rustore_512x512.png");
        saveImage(renderIcon(512, IconShape.SQUARE), artifactDir + "/icon_rustore_512x512.png");

        // 4. Squircle 1024x1024 and 512x512
        saveImage(renderIcon(1024, IconShape.SQUIRCLE), storeDir + "/icon_squircle_1024x1024.png");
        saveImage(renderIcon(1024, IconShape.SQUIRCLE), artifactDir + "/icon_squircle_1024x1024.png");
        saveImage(renderIcon(512, IconShape.SQUIRCLE), storeDir + "/icon_squircle_512x512.png");
        saveImage(renderIcon(512, IconShape.SQUIRCLE), artifactDir + "/icon_squircle_512x512.png");

        // 5. Round Circle 512x512
        saveImage(renderIcon(512, IconShape.ROUND), storeDir + "/icon_round_512x512.png");
        saveImage(renderIcon(512, IconShape.ROUND), artifactDir + "/icon_round_512x512.png");

        // 6. Mipmap standard sizes
        int[] mipmapSizes = {192, 144, 96, 72, 48};
        String[] mipmapNames = {"xxxhdpi_192x192", "xxhdpi_144x144", "xhdpi_96x96", "hdpi_72x72", "mdpi_48x48"};
        for (int i = 0; i < mipmapSizes.length; i++) {
            saveImage(renderIcon(mipmapSizes[i], IconShape.SQUIRCLE), storeDir + "/ic_launcher_" + mipmapNames[i] + ".png");
        }

        // 7. Store Feature Graphic (1024x500 promo banner)
        saveImage(renderFeatureBanner(1024, 500), storeDir + "/store_feature_graphic_1024x500.png");
        saveImage(renderFeatureBanner(1024, 500), artifactDir + "/store_feature_graphic_1024x500.png");

        System.out.println("ALL ICONS GENERATED SUCCESSFULLY!");
    }

    enum IconShape { SQUARE, SQUIRCLE, ROUND }

    private static BufferedImage renderIcon(int size, IconShape shape) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        setupQualityHints(g);

        Shape clipShape;
        if (shape == IconShape.SQUIRCLE) {
            float cornerRadius = size * 0.225f;
            clipShape = new RoundRectangle2D.Float(0, 0, size, size, cornerRadius, cornerRadius);
        } else if (shape == IconShape.ROUND) {
            clipShape = new Ellipse2D.Float(0, 0, size, size);
        } else {
            clipShape = new Rectangle2D.Float(0, 0, size, size);
        }

        g.setClip(clipShape);

        // Background gradient: #262633 at top-left to #14141A at bottom-right
        Color bgTop = new Color(0x27, 0x27, 0x34);
        Color bgBottom = new Color(0x13, 0x13, 0x19);
        GradientPaint bgGradient = new GradientPaint(0, 0, bgTop, size, size, bgBottom);
        g.setPaint(bgGradient);
        g.fill(clipShape);

        // Subtle ambient radial glow behind the book
        float cx = size * 0.5f;
        float cy = size * 0.52f;
        float radius = size * 0.45f;
        RadialGradientPaint glow = new RadialGradientPaint(
            cx, cy, radius,
            new float[]{0.0f, 0.6f, 1.0f},
            new Color[]{
                new Color(0xC9, 0xA2, 0x27, 45),
                new Color(0xC9, 0xA2, 0x27, 10),
                new Color(0, 0, 0, 0)
            }
        );
        g.setPaint(glow);
        g.fill(clipShape);

        // Subtle outer border for squircle / round
        if (shape != IconShape.SQUARE) {
            g.setPaint(new Color(255, 255, 255, 25));
            g.setStroke(new BasicStroke(Math.max(1.5f, size * 0.006f)));
            g.draw(clipShape);
        }

        // Draw the book emblem scaled to the viewport (108x108 coordinate system)
        drawBookEmblem(g, size);

        g.dispose();
        return image;
    }

    private static void drawBookEmblem(Graphics2D g, int size) {
        double scale = size / 108.0;
        AffineTransform originalTransform = g.getTransform();
        g.scale(scale, scale);

        // Soft drop shadow beneath the book pages
        Path2D.Float shadowPath = new Path2D.Float();
        shadowPath.moveTo(28, 77);
        shadowPath.curveTo(36, 74, 44, 74, 52, 77);
        shadowPath.lineTo(56, 77);
        shadowPath.curveTo(64, 74, 72, 74, 80, 77);
        shadowPath.lineTo(79, 81);
        shadowPath.curveTo(71, 78, 63, 78, 55, 80);
        shadowPath.lineTo(53, 80);
        shadowPath.curveTo(45, 78, 37, 78, 29, 81);
        shadowPath.closePath();

        g.setColor(new Color(0, 0, 0, 70));
        g.fill(shadowPath);

        // Left page: M30,35 C36,32 44,32 52,35 L52,76 C44,73 36,73 30,76 Z
        Path2D.Float leftPage = new Path2D.Float();
        leftPage.moveTo(30, 35);
        leftPage.curveTo(36, 32, 44, 32, 52, 35);
        leftPage.lineTo(52, 76);
        leftPage.curveTo(44, 73, 36, 73, 30, 76);
        leftPage.closePath();

        // Left page fill: subtle warm paper gradient
        GradientPaint leftGradient = new GradientPaint(
            30, 35, new Color(0xFF, 0xFC, 0xF4),
            52, 76, new Color(0xE3, 0xDE, 0xD1)
        );
        g.setPaint(leftGradient);
        g.fill(leftPage);

        // Left page internal delicate text lines (reading lines motif)
        g.setColor(new Color(0xBB, 0xB6, 0xA8, 120));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Float l1 = new Path2D.Float();
        l1.moveTo(35, 45); l1.curveTo(39, 43.5, 43, 43.5, 47, 45);
        g.draw(l1);
        Path2D.Float l2 = new Path2D.Float();
        l2.moveTo(35, 52); l2.curveTo(39, 50.5, 43, 50.5, 47, 52);
        g.draw(l2);
        Path2D.Float l3 = new Path2D.Float();
        l3.moveTo(35, 59); l3.curveTo(39, 57.5, 43, 57.5, 47, 59);
        g.draw(l3);
        Path2D.Float l4 = new Path2D.Float();
        l4.moveTo(35, 66); l4.curveTo(38, 65, 41, 65, 44, 66);
        g.draw(l4);

        // Right page: M78,35 C72,32 64,32 56,35 L56,76 C64,73 72,73 78,76 Z
        Path2D.Float rightPage = new Path2D.Float();
        rightPage.moveTo(78, 35);
        rightPage.curveTo(72, 32, 64, 32, 56, 35);
        rightPage.lineTo(56, 76);
        rightPage.curveTo(64, 73, 72, 73, 78, 76);
        rightPage.closePath();

        // Right page fill: rich Apri gold gradient
        GradientPaint rightGradient = new GradientPaint(
            56, 35, new Color(0xE5, 0xBC, 0x3B),
            78, 76, new Color(0xB8, 0x8F, 0x18)
        );
        g.setPaint(rightGradient);
        g.fill(rightPage);

        // Right page golden highlight lines
        g.setColor(new Color(255, 255, 255, 70));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Float r1 = new Path2D.Float();
        r1.moveTo(61, 45); r1.curveTo(65, 43.5, 69, 43.5, 73, 45);
        g.draw(r1);
        Path2D.Float r2 = new Path2D.Float();
        r2.moveTo(61, 52); r2.curveTo(65, 50.5, 69, 50.5, 73, 52);
        g.draw(r2);
        Path2D.Float r3 = new Path2D.Float();
        r3.moveTo(61, 59); r3.curveTo(65, 57.5, 69, 57.5, 73, 59);
        g.draw(r3);
        Path2D.Float r4 = new Path2D.Float();
        r4.moveTo(61, 66); r4.curveTo(64, 65, 67, 65, 70, 66);
        g.draw(r4);

        // Spine: M52.4,34 L55.6,34 L55.6,77 L52.4,77 Z
        Path2D.Float spine = new Path2D.Float();
        spine.moveTo(52.2f, 33.8f);
        spine.lineTo(55.8f, 33.8f);
        spine.lineTo(55.8f, 77.2f);
        spine.lineTo(52.2f, 77.2f);
        spine.closePath();

        GradientPaint spineGradient = new GradientPaint(
            52, 34, new Color(0xA0, 0xA0, 0xB0),
            56, 77, new Color(0x6E, 0x6E, 0x7E)
        );
        g.setPaint(spineGradient);
        g.fill(spine);

        // Elegant center spine highlight
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(0.75f));
        g.drawLine(54, 34, 54, 77);

        g.setTransform(originalTransform);
    }

    private static BufferedImage renderFeatureBanner(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        setupQualityHints(g);

        // Dark modern background with warm golden ambient light
        GradientPaint bg = new GradientPaint(
            0, 0, new Color(0x1B, 0x1B, 0x24),
            width, height, new Color(0x0F, 0x0F, 0x14)
        );
        g.setPaint(bg);
        g.fillRect(0, 0, width, height);

        // Radial golden aura on left/center
        RadialGradientPaint aura = new RadialGradientPaint(
            width * 0.28f, height * 0.50f, width * 0.55f,
            new float[]{0.0f, 0.5f, 1.0f},
            new Color[]{
                new Color(0xC9, 0xA2, 0x27, 50),
                new Color(0xC9, 0xA2, 0x27, 12),
                new Color(0, 0, 0, 0)
            }
        );
        g.setPaint(aura);
        g.fillRect(0, 0, width, height);

        // Draw Icon on left side
        int iconSize = 250;
        int iconX = 80;
        int iconY = (height - iconSize) / 2;
        BufferedImage icon = renderIcon(iconSize, IconShape.SQUIRCLE);
        g.drawImage(icon, iconX, iconY, null);

        // Text Branding on right side
        int textX = iconX + iconSize + 65;

        // Title: "ApriReader"
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 68));
        g.drawString("ApriReader", textX, height / 2 - 25);

        // Subtitle / Tagline
        g.setColor(new Color(0xE5, 0xBC, 0x3B));
        g.setFont(new Font("SansSerif", Font.PLAIN, 28));
        g.drawString("Умный ридер книг и аудиокниг", textX, height / 2 + 25);

        // Features pill tags
        g.setColor(new Color(0x9E, 0x9E, 0xB0));
        g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g.drawString("EPUB  •  FB2  •  PDF  •  M4B  •  MP3  •  TTS Озвучка", textX, height / 2 + 75);

        g.dispose();
        return image;
    }

    private static void setupQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static void saveImage(BufferedImage img, String path) throws Exception {
        File file = new File(path);
        ImageIO.write(img, "PNG", file);
        System.out.println("Saved: " + file.getAbsolutePath() + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }
}
