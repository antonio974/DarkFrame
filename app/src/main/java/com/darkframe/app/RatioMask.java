package com.darkframe.app;

/**
 * Computes the black bars for a variable target ratio.
 * Pure Java (no Android dependency) so it can be tested off the camera.
 *
 * All dimensions are in view pixels. The pixelAspect parameter corrects for
 * screens with non-square pixels (physical width of a pixel / height).
 */
public final class RatioMask {
    /** a7R sensor ratio (3:2). */
    public static final float SENSOR_RATIO = 1.5f;
    /** a7R RAW size in pixels. */
    public static final int SENSOR_WIDTH = 7360, SENSOR_HEIGHT = 4912;

    /** Megapixels left after cropping to the given ratio (centered, no resampling). */
    public static float megapixels(float ratio) {
        float w, h;
        if (ratio > (float) SENSOR_WIDTH / SENSOR_HEIGHT) {
            w = SENSOR_WIDTH;
            h = Math.round(SENSOR_WIDTH / ratio);
        } else {
            w = Math.round(SENSOR_HEIGHT * ratio);
            h = SENSOR_HEIGHT;
        }
        return w * h / 1e6f;
    }

    /** "23.1 MP" */
    public static String megapixelText(float ratio) {
        return String.format(java.util.Locale.US, "%.1f MP", megapixels(ratio));
    }

    /** An available format: its display name and its width / height ratio. */
    public static final class Format {
        public final String name;
        public final float ratio;

        Format(String name, float ratio) {
            this.name = name;
            this.ratio = ratio;
        }
    }

    /** Formats cycled by the dial, by increasing ratio. */
    public static final Format[] FORMATS = {
            new Format("1:1", 1f),
            new Format("7:6", 7f / 6f),
            new Format("5:4", 5f / 4f),
            new Format("4:3", 4f / 3f),
            new Format("3:2", 3f / 2f),
            new Format("16:9", 16f / 9f),
            new Format("6x12", 2f),
            new Format("2.35:1", 2.35f),
            new Format("65x24", 65f / 24f),
    };

    /** Format on first launch: 2.35:1. */
    public static final int DEFAULT_FORMAT = 7;

    /** Index of the format with this name, or -1. The format is saved by name to survive changes to the list. */
    public static int indexOf(String name) {
        for (int i = 0; i < FORMATS.length; i++)
            if (FORMATS[i].name.equals(name)) return i;
        return -1;
    }

    /** Nearest valid index (guards against a stale saved value). */
    public static int clampIndex(int index) {
        return Math.max(0, Math.min(FORMATS.length - 1, index));
    }

    /** Next format, wrapping around: after the last comes the first. */
    public static int cycleFormat(int index) {
        return (clampIndex(index) + 1) % FORMATS.length;
    }

    /** Next (direction > 0) or previous (direction < 0) format, wrapping around. */
    public static int nextFormat(int index, int direction) {
        int step = direction > 0 ? 1 : direction < 0 ? -1 : 0;
        return (clampIndex(index) + step + FORMATS.length) % FORMATS.length;
    }

    /** Composition grids, in the order C1 cycles through them. */
    public static final String[] GRIDS = { "None", "Thirds", "Golden ratio", "Golden spiral", "Golden spiral (flipped)", "Center cross" };
    public static final int GRID_NONE = 0;

    /** Next grid, wrapping around. */
    public static int nextGrid(int grid) {
        return (clampGrid(grid) + 1) % GRIDS.length;
    }

    public static int clampGrid(int grid) {
        return grid >= 0 && grid < GRIDS.length ? grid : GRID_NONE;
    }

    /** Golden spiral: 1 normal, -1 flipped left / right, 0 none. */
    public static int gridSpiral(int grid) {
        int g = clampGrid(grid);
        return g == 3 ? 1 : g == 4 ? -1 : 0;
    }

    /** Line positions (vertical and horizontal), as fractions of the visible area. */
    public static float[] gridFractions(int grid) {
        switch (clampGrid(grid)) {
            case 1: return new float[] { 1f / 3, 2f / 3 };
            case 2: return new float[] { 0.382f, 0.618f };
            case 5: return new float[] { 0.5f };
            default: return new float[0];
        }
    }

    /** Rectangle in view pixels: [left, top) to [right, bottom). */
    public static final class Rect {
        public final int left, top, right, bottom;

        Rect(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int width() { return right - left; }
        public int height() { return bottom - top; }

        @Override
        public String toString() {
            return "[" + left + "," + top + " - " + right + "," + bottom + "]";
        }
    }

    /** Result: sensor image area and the area kept by the target ratio. */
    public static final class Frame {
        /** Area taken by the 3:2 image in the view. */
        public final Rect image;
        /** Area that stays visible; the rest of the view is masked. */
        public final Rect visible;

        Frame(Rect image, Rect visible) {
            this.image = image;
            this.visible = visible;
        }

        /** True if the bars are at top and bottom (ratio wider than the sensor). */
        public boolean isLetterbox() {
            return visible.height() < image.height();
        }
    }

    private RatioMask() {}





    /**
     * Computes the image area and the visible area.
     *
     * @param viewWidth   view width in pixels
     * @param viewHeight  view height in pixels
     * @param pixelAspect physical width of a pixel divided by its height (1 for square pixels)
     * @param imageRatio  ratio of the displayed image (SENSOR_RATIO for the a7R)
     * @param targetRatio wanted ratio, for example 2.35 or 1.33
     */
    public static Frame compute(int viewWidth, int viewHeight, float pixelAspect, float imageRatio, float targetRatio) {
        Rect image = fit(0, 0, viewWidth, viewHeight, pixelAspect, imageRatio);
        Rect visible = fit(image.left, image.top, image.width(), image.height(), pixelAspect, targetRatio);
        return new Frame(image, visible);
    }

    /** Largest rectangle of the given physical ratio, centered in the area, with symmetric bars. */
    private static Rect fit(int left, int top, int width, int height, float pixelAspect, float ratio) {
        float physicalRatio = width * pixelAspect / height;
        int bandX = 0, bandY = 0;
        if (ratio > physicalRatio + 0.0005f) {
            bandY = Math.round((height - width * pixelAspect / ratio) / 2);
        } else if (ratio < physicalRatio - 0.0005f) {
            bandX = Math.round((width - height * ratio / pixelAspect) / 2);
        }
        return new Rect(left + bandX, top + bandY, left + width - bandX, top + height - bandY);
    }
}
