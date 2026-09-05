import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Генератор графики для карточки Google Play.
 *
 * Скриншоты снять без устройства нельзя, а иконку 512x512 и feature graphic
 * 1024x500 Play требует обязательно — они рисуются здесь той же геометрией и
 * теми же цветами, что и адаптивная иконка приложения, чтобы карточка и
 * лаунчер не расходились визуально.
 *
 * Запуск: java tools/PlayAssets.java play-assets
 */
public final class PlayAssets {

    private static final Color INK = new Color(0x1B, 0x1B, 0x22);
    private static final Color INK_LIGHT = new Color(0x2A, 0x2A, 0x34);
    private static final Color PAPER = new Color(0xF2, 0xEF, 0xE6);
    private static final Color BRASS = new Color(0xC9, 0xA2, 0x27);
    private static final Color SPINE = new Color(0x8C, 0x8C, 0x99);

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "play-assets");
        dir.mkdirs();

        ImageIO.write(icon(512), "png", new File(dir, "play-icon-512.png"));
        ImageIO.write(featureGraphic(1024, 500), "png", new File(dir, "play-feature-graphic-1024x500.png"));
        System.out.println("Готово: " + dir.getAbsolutePath());
    }

    /** Иконка для карточки: тот же знак, что в лаунчере, но с полем и фоном. */
    private static BufferedImage icon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = quality(image.createGraphics());

        g.setPaint(new GradientPaint(0, 0, INK_LIGHT, size, size, INK));
        g.fillRect(0, 0, size, size);

        drawBookMark(g, size / 2.0, size / 2.0, size * 0.62);

        g.dispose();
        return image;
    }

    /**
     * Feature graphic: знак слева, название и обещание справа.
     * Текст держится в безопасной зоне — Play обрезает края на части устройств.
     */
    private static BufferedImage featureGraphic(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = quality(image.createGraphics());

        g.setPaint(new GradientPaint(0, 0, INK_LIGHT, width, height, INK));
        g.fillRect(0, 0, width, height);

        // Тонкая латунная линия внизу — та же деталь, что и на обложках в приложении.
        g.setColor(BRASS);
        g.fillRect(0, height - 6, width, 6);

        drawBookMark(g, width * 0.22, height / 2.0, height * 0.52);

        int textX = (int) (width * 0.40);
        // Play обрезает края на части устройств — держим правое поле.
        int maxTextWidth = width - textX - (int) (width * 0.06);

        g.setColor(PAPER);
        drawFitted(g, "ApriReader", textX, (int) (height * 0.44), maxTextWidth, Font.BOLD, 76);

        g.setColor(new Color(0xC9, 0xC7, 0xC2));
        drawFitted(g, "Читалка, которая подстраивается под книгу", textX, (int) (height * 0.60), maxTextWidth, Font.PLAIN, 30);

        g.setColor(BRASS);
        drawFitted(g, "Без рекламы, без трекеров, без облака", textX, (int) (height * 0.73), maxTextWidth, Font.PLAIN, 26);

        g.dispose();
        return image;
    }

    /** Раскрытая книга: две страницы и корешок — знак приложения. */
    private static void drawBookMark(Graphics2D g, double cx, double cy, double size) {
        double halfWidth = size * 0.46;
        double halfHeight = size * 0.40;
        double spine = size * 0.022;
        double curve = size * 0.07;

        Path2D left = new Path2D.Double();
        left.moveTo(cx - spine, cy - halfHeight);
        left.curveTo(cx - halfWidth * 0.5, cy - halfHeight - curve, cx - halfWidth * 0.8, cy - halfHeight - curve, cx - halfWidth, cy - halfHeight + curve * 0.4);
        left.lineTo(cx - halfWidth, cy + halfHeight - curve * 0.4);
        left.curveTo(cx - halfWidth * 0.8, cy + halfHeight - curve, cx - halfWidth * 0.5, cy + halfHeight - curve, cx - spine, cy + halfHeight);
        left.closePath();

        Path2D right = new Path2D.Double();
        right.moveTo(cx + spine, cy - halfHeight);
        right.curveTo(cx + halfWidth * 0.5, cy - halfHeight - curve, cx + halfWidth * 0.8, cy - halfHeight - curve, cx + halfWidth, cy - halfHeight + curve * 0.4);
        right.lineTo(cx + halfWidth, cy + halfHeight - curve * 0.4);
        right.curveTo(cx + halfWidth * 0.8, cy + halfHeight - curve, cx + halfWidth * 0.5, cy + halfHeight - curve, cx + spine, cy + halfHeight);
        right.closePath();

        g.setColor(PAPER);
        g.fill(left);
        g.setColor(BRASS);
        g.fill(right);

        g.setColor(SPINE);
        g.fill(new RoundRectangle2D.Double(cx - spine, cy - halfHeight, spine * 2, halfHeight * 2, spine, spine));

        // Строки текста на левой странице — намёк на то, что это именно читалка.
        g.setColor(new Color(0x8C, 0x8C, 0x99, 120));
        g.setStroke(new BasicStroke((float) (size * 0.018), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 4; i++) {
            double y = cy - halfHeight * 0.45 + i * halfHeight * 0.30;
            double inset = halfWidth * (i == 3 ? 0.55 : 0.80);
            g.drawLine(
                    (int) (cx - inset),
                    (int) y,
                    (int) (cx - spine * 3),
                    (int) y);
        }
    }

    /**
     * Рисует строку, уменьшая кегль, пока она не поместится в отведённую ширину.
     * Без этого длинная русская фраза уезжает за край изображения.
     */
    private static void drawFitted(Graphics2D g, String text, int x, int baseline, int maxWidth, int style, int size) {
        int current = size;
        Font font = pickFont(style, current);
        while (current > 12 && g.getFontMetrics(font).stringWidth(text) > maxWidth) {
            current -= 2;
            font = pickFont(style, current);
        }
        g.setFont(font);
        g.drawString(text, x, baseline);
    }

    private static Graphics2D quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    /** Берём первый доступный из привычных шрифтов, иначе — системный. */
    private static Font pickFont(int style, int size) {
        String[] candidates = {"Segoe UI", "Inter", "Roboto", "Arial", "SansSerif"};
        for (String name : candidates) {
            Font font = new Font(name, style, size);
            if (!font.getFamily().equals("Dialog") || name.equals("SansSerif")) {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    private PlayAssets() {
    }
}
