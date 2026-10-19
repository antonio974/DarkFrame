package com.darkframe.app;

/**
 * Computes the level from the gravity measured by the accelerometer.
 * Pure Java so it can be tested off the camera.
 */
public final class Level {
    /** Tolerance, in degrees, for the camera to count as level. */
    public static final float TOLERANCE = 0.5f;

    private Level() {}

    /**
     * Side tilt in degrees, from -45 to +45, whatever the sensor mounting orientation
     * and whether the camera is held horizontally or vertically.
     */
    public static float roll(float gx, float gy) {
        double angle = Math.toDegrees(Math.atan2(gx, gy));
        return (float) (angle - 90 * Math.round(angle / 90));
    }

    /** Forward / backward tilt in degrees (0 when the lens points at the horizon). */
    public static float pitch(float gx, float gy, float gz) {
        return (float) Math.toDegrees(Math.atan2(gz, Math.sqrt(gx * gx + gy * gy)));
    }

    public static boolean isLevel(float degrees) {
        return Math.abs(degrees) < TOLERANCE;
    }
}
