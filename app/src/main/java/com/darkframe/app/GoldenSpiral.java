package com.darkframe.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Golden spiral in a golden rectangle of width PHI and height 1: remove a square on the left, top,
 * right, bottom, then repeat; each square holds a quarter circle.
 * Pure Java so it can be tested off the camera. Angles in degrees like Canvas.drawArc
 * (0 to the right, clockwise on screen).
 */
public final class GoldenSpiral {
    public static final float PHI = (float) ((1 + Math.sqrt(5)) / 2);
    public static final int DEFAULT_STEPS = 8;

    /** One square: the line separating it from the rest, and its quarter circle. */
    public static final class Step {
        public final float lineX1, lineY1, lineX2, lineY2;
        public final float centerX, centerY, size, startAngle;

        Step(float lx1, float ly1, float lx2, float ly2, float cx, float cy, float size, float startAngle) {
            lineX1 = lx1; lineY1 = ly1; lineX2 = lx2; lineY2 = ly2;
            centerX = cx; centerY = cy; this.size = size; this.startAngle = startAngle;
        }

        public float startX() { return centerX + size * (float) Math.cos(Math.toRadians(startAngle)); }
        public float startY() { return centerY + size * (float) Math.sin(Math.toRadians(startAngle)); }
        public float endX() { return centerX + size * (float) Math.cos(Math.toRadians(startAngle + 90)); }
        public float endY() { return centerY + size * (float) Math.sin(Math.toRadians(startAngle + 90)); }
    }

    private GoldenSpiral() {}

    public static List<Step> steps(int count) {
        List<Step> steps = new ArrayList<Step>();
        float x = 0, y = 0, w = PHI, h = 1;
        for (int i = 0; i < count; i++) {
            float s;
            switch (i % 4) {
                case 0: // square on the left
                    s = h;
                    steps.add(new Step(x + s, y, x + s, y + h, x + s, y + s, s, 180));
                    x += s; w -= s;
                    break;
                case 1: // square at the top
                    s = w;
                    steps.add(new Step(x, y + s, x + w, y + s, x, y + s, s, 270));
                    y += s; h -= s;
                    break;
                case 2: // square on the right
                    s = h;
                    steps.add(new Step(x + w - s, y, x + w - s, y + h, x + w - s, y, s, 0));
                    w -= s;
                    break;
                default: // square at the bottom
                    s = w;
                    steps.add(new Step(x, y + h - s, x + w, y + h - s, x + w, y + h - s, s, 90));
                    h -= s;
                    break;
            }
        }
        return steps;
    }
}
