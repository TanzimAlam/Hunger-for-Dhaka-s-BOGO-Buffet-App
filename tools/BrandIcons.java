import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Turns the supplied circular emblem artwork into the app's launcher icons and in-app logo.
 *
 * The source is a round badge on a flat grey backdrop. This tool measures the badge, cuts it out
 * without clipping any of the ring, and then fits that whole badge — never crops it — inside each
 * icon:
 *
 *   res/drawable-nodpi/app_logo.png                  - the header mark (badge with a hairline margin)
 *   res/mipmap-<density>/ic_launcher.png             - legacy launcher icon
 *   res/mipmap-<density>/ic_launcher_round.png
 *   res/mipmap-<density>/ic_launcher_foreground.png  - adaptive foreground inside the 66dp safe zone
 *
 * Usage: java tools/BrandIcons.java <source.png>
 */
public final class BrandIcons {

    private static final String[] DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    private static final int[] LEGACY_PX = {48, 72, 96, 144, 192};
    private static final int[] FOREGROUND_PX = {108, 162, 216, 324, 432};

    /** Adaptive icons are 108dp; the guaranteed-safe circle is 66dp, so the badge stays inside it. */
    private static final double FOREGROUND_FILL = 0.60;
    /** Legacy icons keep a small margin so no launcher mask touches the ring. */
    private static final double LEGACY_FILL = 0.92;
    /** The in-app mark sits in a rounded tile, so it needs only a hairline margin. */
    private static final double LOGO_FILL = 0.94;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: BrandIcons <source.png>");
            System.exit(2);
        }
        BufferedImage source = ImageIO.read(new File(args[0]));
        if (source == null) {
            System.err.println("could not read " + args[0]);
            System.exit(2);
        }

        BufferedImage badge = cutBadge(source);
        System.out.println("badge: " + badge.getWidth() + "x" + badge.getHeight());

        File res = new File("app/src/main/res");
        write(compose(badge, 512, LOGO_FILL), new File(res, "drawable-nodpi/app_logo.png"), 512);

        for (int i = 0; i < DENSITIES.length; i++) {
            File dir = new File(res, "mipmap-" + DENSITIES[i]);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IllegalStateException("cannot create " + dir);
            }
            BufferedImage legacy = compose(badge, LEGACY_PX[i], LEGACY_FILL);
            write(legacy, new File(dir, "ic_launcher.png"), LEGACY_PX[i]);
            write(legacy, new File(dir, "ic_launcher_round.png"), LEGACY_PX[i]);
            write(
                compose(badge, FOREGROUND_PX[i], FOREGROUND_FILL),
                new File(dir, "ic_launcher_foreground.png"),
                FOREGROUND_PX[i]
            );
            System.out.println("wrote " + dir.getName());
        }
    }

    /**
     * Measures the badge against the flat studio backdrop and returns it as a square RGBA image,
     * with everything outside the ring transparent. The square is the badge's larger dimension,
     * so no part of the artwork is ever cut off.
     */
    private static BufferedImage cutBadge(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();

        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!isBackdrop(source.getRGB(x, y))) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX <= minX || maxY <= minY) {
            throw new IllegalStateException("no badge found in the artwork");
        }

        // Two spare pixels keep the antialiased ring edge inside the cut-out.
        int side = Math.max(maxX - minX + 1, maxY - minY + 1) + 2;
        int cx = (minX + maxX) / 2;
        int cy = (minY + maxY) / 2;
        int left = cx - side / 2;
        int top = cy - side / 2;

        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        double radius = side / 2.0;
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int sx = left + x;
                int sy = top + y;
                if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue;
                double dx = x - radius + 0.5;
                double dy = y - radius + 0.5;
                double distance = Math.sqrt(dx * dx + dy * dy);
                // One-pixel soft edge, so the ring is not jagged when scaled down.
                double alpha = clamp((radius - distance) / 1.5, 0, 1);
                int rgb = source.getRGB(sx, sy);
                int a = (int) Math.round(((rgb >>> 24) & 0xff) * alpha);
                out.setRGB(x, y, (a << 24) | (rgb & 0x00ffffff));
            }
        }
        return out;
    }

    /** Centres the whole badge in a square canvas at [fill] of its width, leaving the rest clear. */
    private static BufferedImage compose(BufferedImage badge, int size, double fill) {
        BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Half-pixel offsets keep a scaled circle round rather than one pixel off-centre.
        double inner = size * fill;
        double offset = (size - inner) / 2.0;
        g.drawImage(
            scale(badge, (int) Math.round(inner)),
            (int) Math.round(offset),
            (int) Math.round(offset),
            null
        );
        g.dispose();
        return canvas;
    }

    private static void write(BufferedImage image, File target, int size) throws Exception {
        BufferedImage scaled = image.getWidth() == size && image.getHeight() == size
            ? image
            : scale(image, size);
        ImageIO.write(scaled, "png", target);
    }

    /** Progressive halving keeps small launcher icons free of the aliasing a single big jump adds. */
    private static BufferedImage scale(BufferedImage source, int size) {
        BufferedImage current = source;
        while (current.getWidth() / 2 >= size) {
            current = resize(current, current.getWidth() / 2);
        }
        return resize(current, size);
    }

    private static BufferedImage resize(BufferedImage source, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    /**
     * The backdrop is a flat neutral studio grey. Anything with some colour, or clearly lighter
     * or darker than the grey, belongs to the badge.
     */
    private static boolean isBackdrop(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        int chroma = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
        int luma = (r * 299 + g * 587 + b * 114) / 1000;
        return chroma <= 8 && luma >= 60 && luma <= 130;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
