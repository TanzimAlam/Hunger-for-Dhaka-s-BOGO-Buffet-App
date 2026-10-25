import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Cuts the circular emblem out of the supplied artwork and writes every icon density Android
 * needs, plus the in-app logo. Run with the JDK: javac -d tools tools/IconGen.java && java -cp tools IconGen <source> <res>
 */
public class IconGen {

    // Square window around the emblem inside the 1792x2400 artwork, with a little breathing room.
    private static final int CROP_X = 86;
    private static final int CROP_Y = 358;
    private static final int CROP = 1620;

    private static BufferedImage emblem;

    public static void main(String[] args) throws Exception {
        BufferedImage source = ImageIO.read(new File(args[0]));
        File res = new File(args[1]);
        emblem = crop(source);

        File logoDir = new File(res, "drawable-nodpi");
        logoDir.mkdirs();
        write(scale(emblem, 512, 512, false), new File(logoDir, "app_logo.png"));

        int[] legacySizes = {48, 72, 96, 144, 192};
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        for (int i = 0; i < densities.length; i++) {
            File dir = new File(res, "mipmap-" + densities[i]);
            dir.mkdirs();
            BufferedImage icon = scale(emblem, legacySizes[i], legacySizes[i], false);
            write(icon, new File(dir, "ic_launcher.png"));
            write(icon, new File(dir, "ic_launcher_round.png"));
        }

        // Adaptive foreground: 108dp canvas with the emblem inside the 72dp mask-safe area.
        int[] adaptiveSizes = {108, 162, 216, 324, 432};
        for (int i = 0; i < densities.length; i++) {
            File dir = new File(res, "mipmap-" + densities[i]);
            dir.mkdirs();
            write(foreground(adaptiveSizes[i]), new File(dir, "ic_launcher_foreground.png"));
        }

        System.out.println("icons written to " + res.getAbsolutePath());
    }

    private static BufferedImage crop(BufferedImage source) {
        int x = Math.max(0, Math.min(CROP_X, source.getWidth() - CROP));
        int y = Math.max(0, Math.min(CROP_Y, source.getHeight() - CROP));
        BufferedImage out = new BufferedImage(CROP, CROP, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, CROP, CROP, x, y, x + CROP, y + CROP, null);
        g.dispose();
        return out;
    }

    private static BufferedImage foreground(int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int inner = Math.round(size * 72f / 108f);
        int pad = (size - inner) / 2;
        g.drawImage(emblem, pad, pad, inner, inner, null);
        g.dispose();
        return out;
    }

    private static BufferedImage scale(BufferedImage source, int width, int height, boolean alpha) {
        BufferedImage out = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    private static void write(BufferedImage image, File file) throws Exception {
        ImageIO.write(image, "png", file);
    }
}
