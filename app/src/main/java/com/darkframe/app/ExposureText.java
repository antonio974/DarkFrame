package com.darkframe.app;

import java.util.Locale;

/**
 * Formats the shooting settings shown in the bars.
 * Shutter speed format taken from CameraUtil in BetterManual (Martin Tofall, MIT license).
 * Pure Java so it can be tested off the camera.
 */
public final class ExposureText {
    private ExposureText() {}

    /** Shutter speed as a fraction n/d of a second: 1/125, 0.5", 2", BULB. */
    public static String shutter(int n, int d) {
        if (n <= 0 || d <= 0) return "--";
        if (n == 1 && d != 2 && d != 1)
            return "1/" + d;
        if (d == 1)
            return n == 65535 ? "BULB" : n + "\"";
        return String.format(Locale.US, "%.1f\"", (float) n / d);
    }

    /** Aperture in hundredths (560 = f5.6); 0 if the lens does not report it. */
    public static String aperture(int hundredths) {
        if (hundredths <= 0) return "f--";
        return String.format(Locale.US, "f%.1f", hundredths / 100f);
    }

    /** ISO setting (0 = auto) and, in auto, the value chosen by the camera (0 if unknown). */
    public static String iso(int setting, int autoValue) {
        if (setting > 0) return "ISO " + setting;
        return autoValue > 0 ? "ISO " + autoValue + " A" : "ISO AUTO";
    }

    /** Exposure compensation in EV: ±0.0, +0.7, -1.3. */
    public static String ev(float value) {
        if (Math.abs(value) < 0.05f) return "±0.0";
        return String.format(Locale.US, "%+.1f", value);
    }

    /**
     * a7R exposure compensation dial position in thirds of EV, measured on an a7R:
     * 0 to 9 for 0 to +3, 513 to 521 for -1/3 to -3. Integer.MIN_VALUE if the position is unknown.
     */
    public static int evDialThirds(int status) {
        if (status >= 0 && status <= 9) return status;
        if (status >= 513 && status <= 521) return -(status - 512);
        return Integer.MIN_VALUE;
    }

    /** Full line shown at the bottom of the screen. */
    public static String line(String shutter, String aperture, String iso, String ev) {
        return shutter + "   " + aperture + "   " + iso + "   " + ev;
    }
}
