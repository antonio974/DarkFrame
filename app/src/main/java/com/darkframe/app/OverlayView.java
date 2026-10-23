package com.darkframe.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Transparent view over the live view: draws the black bands and the current ratio.
 */
public class OverlayView extends View {
    public static final int MIN_OPACITY = 50;
    public static final int MAX_OPACITY = 100;

    private final Paint bandPaint = new Paint();
    private final Paint framePaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private RatioMask.Format format = RatioMask.FORMATS[RatioMask.DEFAULT_FORMAT];
    private int opacity = 100;
    private float pixelAspect = 1f;
    private String message;
    private String exposure;
    private String exposureRight;
    private int grid = RatioMask.GRID_NONE;
    /** Focus: 0 none, 1 working, 2 locked (green), 3 failed (red). */
    public static final int FOCUS_NONE = 0, FOCUS_WORKING = 1, FOCUS_LOCKED = 2, FOCUS_FAILED = 3;
    private int focus = FOCUS_NONE;
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Battery in %, or -1 if unknown (nothing shown). */
    private int battery = -1;
    private final Paint lowBatteryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spiralPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Roll in degrees, or NaN if the sensor does not respond (level hidden). */
    private float levelRoll = Float.NaN;
    private final Paint levelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Metered exposure offset in thirds of a stop, or NO_METER (scale hidden). */
    public static final int NO_METER = Integer.MIN_VALUE;
    private int meter = NO_METER;
    private final Paint meterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint meterTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Selected film (null: none), and highlighted row of the film menu (-1: menu closed; 0: "None"). */
    private FilmLook film;
    private int filmMenu = -1;
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint menuPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Shots remaining on the card, or -1 if unknown. */
    private int remaining = -1;

    public OverlayView(Context context) {
        this(context, null);
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        bandPaint.setColor(Color.BLACK);

        framePaint.setColor(Color.WHITE);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(1);

        spiralPaint.setColor(Color.WHITE);
        spiralPaint.setAlpha(140);
        spiralPaint.setStrokeWidth(1);
        spiralPaint.setStyle(Paint.Style.STROKE);

        gridPaint.setColor(Color.WHITE);
        gridPaint.setAlpha(140);
        gridPaint.setStrokeWidth(1);

        lowBatteryPaint.setColor(Color.rgb(255, 80, 80));
        lowBatteryPaint.setTextSize(22);
        lowBatteryPaint.setShadowLayer(2, 1, 1, Color.BLACK);

        levelPaint.setStrokeWidth(1);
        levelPaint.setShadowLayer(1, 0, 0, Color.BLACK);

        meterPaint.setColor(Color.WHITE);
        meterPaint.setStrokeWidth(1);
        meterPaint.setShadowLayer(1, 0, 0, Color.BLACK);
        meterTextPaint.setColor(Color.WHITE);
        meterTextPaint.setTextSize(11);
        meterTextPaint.setTextAlign(Paint.Align.CENTER);
        meterTextPaint.setShadowLayer(1, 0, 0, Color.BLACK);

        iconTextPaint.setTextAlign(Paint.Align.CENTER);
        iconTextPaint.setFakeBoldText(true);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(22);
        textPaint.setShadowLayer(2, 1, 1, Color.BLACK);
        updateAlpha();
    }

    public void setFormat(RatioMask.Format format) {
        this.format = format;
        invalidate();
    }

    public int getGrid() { return grid; }

    /** Framing lines in the visible area (RatioMask.GRIDS). */
    public void setGrid(int grid) {
        this.grid = RatioMask.clampGrid(grid);
        invalidate();
    }

    public void setFocus(int state) {
        if (state == focus) return;
        focus = state;
        invalidate();
    }

    /** Level: roll in degrees (NaN to hide). Redraws only on changes of 0.1 degree or more. */
    public void setLevel(float roll) {
        if (Float.isNaN(roll) ? Float.isNaN(levelRoll) : Math.abs(roll - levelRoll) < 0.1f) return;
        levelRoll = roll;
        invalidate();
    }

    private String levelDebug;

    /** Debug text shown under the level (raw sensor values), null for none. */
    public void setLevelDebug(String text) {
        if (text == null ? levelDebug == null : text.equals(levelDebug)) return;
        levelDebug = text;
        invalidate();
    }

    /** Exposure meter: offset in thirds of a stop (NO_METER to hide). */
    public void setMeter(int thirds) {
        if (thirds == meter) return;
        meter = thirds;
        invalidate();
    }

    public void setFilm(FilmLook film) {
        this.film = film;
        invalidate();
    }

    /** Opens the film menu on the given row (0: no film, 1 and up: FilmLook.FILMS), -1 to close it. */
    public void setFilmMenu(int row) {
        if (row == filmMenu) return;
        filmMenu = row;
        invalidate();
    }

    public void setRemaining(int shots) {
        if (shots == remaining) return;
        remaining = shots;
        invalidate();
    }

    public void setBattery(int percent) {
        if (percent == battery) return;
        battery = percent;
        invalidate();
    }

    public int getOpacity() { return opacity; }

    /** Band opacity in %, from 50 to 100. */
    public void setOpacity(int opacity) {
        this.opacity = Math.max(MIN_OPACITY, Math.min(MAX_OPACITY, opacity));
        updateAlpha();
        invalidate();
    }

    /** Physical pixel width divided by its height, for screens with non-square pixels. */
    public void setPixelAspect(float pixelAspect) {
        this.pixelAspect = pixelAspect > 0 ? pixelAspect : 1f;
        invalidate();
    }

    /** Shooting settings shown at the bottom of the screen (null to hide them). */
    public void setExposure(String exposure) {
        setExposure(exposure, null);
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** Bottom line: left part (mode, shutter speed, aperture) and right part (ISO, compensation), with the meter in between. */
    public void setExposure(String left, String right) {
        if (same(left, exposure) && same(right, exposureRight)) return;
        this.exposure = left;
        this.exposureRight = right;
        invalidate();
    }

    /** Temporary message shown under the ratio (null to clear it). */
    public void setMessage(String message) {
        this.message = message;
        invalidate();
    }

    private void updateAlpha() {
        bandPaint.setAlpha(Math.round(opacity * 255 / 100f));
    }

    /**
     * Golden spiral and its squares, stretched to the visible area as in Lightroom.
     * mirrored: spiral flipped left / right.
     */
    /** Focus frame in the center, two brackets [ ] as on the Sony screen. Uses the color of focusPaint. */
    private void drawFocusFrame(Canvas canvas, float w, float h) {
        float cx = w / 2, cy = h / 2, half = h * 0.0225f, arm = half * 0.6f;
        float left = cx - half * 1.3f, right = cx + half * 1.3f, top = cy - half, bottom = cy + half;
        int color = focusPaint.getColor();
        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeCap(Paint.Cap.SQUARE);
        // 1 px black outline on each side, then the 4 px colored stroke on top.
        focusPaint.setColor(Color.BLACK);
        focusPaint.setStrokeWidth(6);
        drawBrackets(canvas, left, right, top, bottom, arm);
        focusPaint.setColor(color);
        focusPaint.setStrokeWidth(4);
        drawBrackets(canvas, left, right, top, bottom, arm);
        focusPaint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawBrackets(Canvas canvas, float left, float right, float top, float bottom, float arm) {
        canvas.drawLine(left, top, left, bottom, focusPaint);
        canvas.drawLine(left, top, left + arm, top, focusPaint);
        canvas.drawLine(left, bottom, left + arm, bottom, focusPaint);
        canvas.drawLine(right, top, right, bottom, focusPaint);
        canvas.drawLine(right, top, right - arm, top, focusPaint);
        canvas.drawLine(right, bottom, right - arm, bottom, focusPaint);
    }

    /**
     * Sony-style level: a thin line that follows the roll, with a small triangle in the middle;
     * green when the camera is level, white otherwise.
     */
    private void drawLevel(Canvas canvas, float cx, float cy, float w) {
        levelPaint.setColor(Level.isLevel(levelRoll) ? Color.rgb(60, 220, 60) : Color.WHITE);
        canvas.save();
        canvas.rotate(levelRoll, cx, cy);
        float half = w * 0.22f;
        levelPaint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(cx - half, cy, cx + half, cy, levelPaint);
        android.graphics.Path triangle = new android.graphics.Path();
        triangle.moveTo(cx, cy - 1);
        triangle.lineTo(cx - 4, cy + 5);
        triangle.lineTo(cx + 4, cy + 5);
        triangle.close();
        levelPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(triangle, levelPaint);
        canvas.restore();
    }

    /**
     * Sony-style exposure meter: a -3 to +3 EV scale in thirds, with a marker under the value.
     * Beyond ±3 EV, the marker sticks to the end of the scale and turns orange.
     */
    private static final float METER_STEP = 7, METER_WIDTH = 18 * METER_STEP;

    private void drawMeter(Canvas canvas, float cx, float baseline) {
        float step = METER_STEP;
        float left = cx - 9 * step;
        for (int i = -9; i <= 9; i++) {
            float x = cx + i * step;
            float tick = i % 3 == 0 ? 6 : 3;
            canvas.drawLine(x, baseline - tick, x, baseline, meterPaint);
        }
        canvas.drawLine(left, baseline, cx + 9 * step, baseline, meterPaint);
        String[] labels = { "-3", "2", "1", "0", "1", "2", "+3" };
        for (int i = 0; i < labels.length; i++)
            canvas.drawText(labels[i], left + i * 3 * step, baseline - 8, meterTextPaint);
        boolean over = Math.abs(meter) > 9;
        int clamped = Math.max(-9, Math.min(9, meter));
        float mx = cx + clamped * step;
        android.graphics.Path marker = new android.graphics.Path();
        marker.moveTo(mx, baseline + 2);
        marker.lineTo(mx - 4, baseline + 8);
        marker.lineTo(mx + 4, baseline + 8);
        marker.close();
        meterPaint.setStyle(Paint.Style.FILL);
        meterPaint.setColor(over ? Color.rgb(255, 160, 0) : Color.WHITE);
        canvas.drawPath(marker, meterPaint);
        meterPaint.setColor(Color.WHITE);
        meterPaint.setStyle(Paint.Style.STROKE);
    }

    private android.graphics.Bitmap vignetteBitmap;
    private int vignetteAmount, vignetteW, vignetteH;
    private final Paint vignettePaint = new Paint();

    /**
     * Ellipse fitted to the visible image: transparent up to half radius, then darker towards the corners.
     * -20 (the strongest film) gives about 45% black in the corners. The screen has few transparency
     * levels, so a smooth gradient shows banding; hence a slight random dither, computed once
     * per image size and per film.
     */
    private void drawVignette(Canvas canvas, RatioMask.Rect v, int amount) {
        int vw = Math.round(v.right - v.left), vh = Math.round(v.bottom - v.top);
        if (vw <= 0 || vh <= 0) return;
        if (vignetteBitmap == null || vignetteAmount != amount || vignetteW != vw || vignetteH != vh) {
            if (vignetteBitmap != null) vignetteBitmap.recycle();
            vignetteBitmap = makeVignette(vw, vh, amount);
            vignetteAmount = amount;
            vignetteW = vw;
            vignetteH = vh;
        }
        canvas.drawBitmap(vignetteBitmap, v.left, v.top, vignettePaint);
    }

    private static android.graphics.Bitmap makeVignette(int w, int h, int amount) {
        float edge = Math.min(255, -amount * 6);
        int[] pixels = new int[w * h];
        java.util.Random random = new java.util.Random(1);
        float cx = w / 2f, cy = h / 2f;
        for (int y = 0; y < h; y++) {
            float dy = (y + 0.5f - cy) / cy;
            for (int x = 0; x < w; x++) {
                float dx = (x + 0.5f - cx) / cx;
                // 0 at the center, 1 in the corners; gradual darkening from half radius.
                float d = (float) Math.sqrt((dx * dx + dy * dy) / 2);
                float t = d <= 0.5f ? 0 : Math.min(1, (d - 0.5f) / 0.5f);
                float alpha = edge * t * t * (3 - 2 * t);
                if (alpha > 0) alpha += (random.nextFloat() - 0.5f) * 12;
                int a = Math.max(0, Math.min(255, Math.round(alpha)));
                pixels[y * w + x] = a << 24;
            }
        }
        return android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888);
    }

    /** Film icon: a label in the colors of its box, with its code (P400, V50...). */
    private void drawFilmIcon(Canvas canvas, FilmLook look, float left, float top, float width, float height) {
        android.graphics.RectF r = new android.graphics.RectF(left, top, left + width, top + height);
        iconPaint.setStyle(Paint.Style.FILL);
        iconPaint.setColor(0xFF000000 | look.iconColor);
        canvas.drawRoundRect(r, 4, 4, iconPaint);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(1);
        iconPaint.setColor(Color.BLACK);
        canvas.drawRoundRect(r, 4, 4, iconPaint);
        iconTextPaint.setColor(0xFF000000 | look.iconTextColor);
        iconTextPaint.setTextSize(height * 0.62f);
        canvas.drawText(look.code, r.centerX(), r.centerY() - (iconTextPaint.ascent() + iconTextPaint.descent()) / 2, iconTextPaint);
    }

    /** Film menu: "None" then each film with its icon, the selected row highlighted. */
    private void drawFilmMenu(Canvas canvas, int w, int h) {
        int rows = FilmLook.FILMS.length + 1;
        float rowH = Math.min(34, (h - 60) / (float) rows), panelW = 300, title = 36;
        float panelH = title + rows * rowH + 10;
        float left = 8, top = (h - panelH) / 2;
        menuPaint.setStyle(Paint.Style.FILL);
        menuPaint.setColor(Color.argb(225, 15, 15, 15));
        canvas.drawRoundRect(new android.graphics.RectF(left, top, left + panelW, top + panelH), 8, 8, menuPaint);
        canvas.drawText("Film simulation", left + 14, top + 8 - textPaint.ascent(), textPaint);
        for (int i = 0; i < rows; i++) {
            float rowTop = top + title + i * rowH;
            if (i == filmMenu) {
                menuPaint.setStyle(Paint.Style.FILL);
                menuPaint.setColor(Color.argb(255, 70, 70, 70));
                canvas.drawRoundRect(new android.graphics.RectF(left + 6, rowTop, left + panelW - 6, rowTop + rowH - 2), 5, 5, menuPaint);
                menuPaint.setStyle(Paint.Style.STROKE);
                menuPaint.setStrokeWidth(2);
                menuPaint.setColor(Color.rgb(255, 160, 0));
                canvas.drawRoundRect(new android.graphics.RectF(left + 6, rowTop, left + panelW - 6, rowTop + rowH - 2), 5, 5, menuPaint);
            }
            float iconH = rowH - 10, iconW = iconH * 2.4f, iconLeft = left + 16;
            String name;
            if (i == 0) {
                name = "None";
            } else {
                FilmLook look = FilmLook.FILMS[i - 1];
                drawFilmIcon(canvas, look, iconLeft, rowTop + 4, iconW, iconH);
                name = look.name;
            }
            canvas.drawText(name, iconLeft + iconW + 14, rowTop + (rowH - 2) / 2 - (textPaint.ascent() + textPaint.descent()) / 2, textPaint);
        }
    }

    private void drawSpiral(Canvas canvas, RatioMask.Rect v, boolean mirrored) {
        float sx = v.width() / GoldenSpiral.PHI, sy = v.height();
        canvas.save();
        if (mirrored)
            canvas.scale(-1, 1, (v.left + v.right) / 2f, 0);
        android.graphics.RectF oval = new android.graphics.RectF();
        for (GoldenSpiral.Step step : GoldenSpiral.steps(GoldenSpiral.DEFAULT_STEPS)) {
            canvas.drawLine(v.left + step.lineX1 * sx, v.top + step.lineY1 * sy,
                    v.left + step.lineX2 * sx, v.top + step.lineY2 * sy, spiralPaint);
            oval.set(v.left + (step.centerX - step.size) * sx, v.top + (step.centerY - step.size) * sy,
                    v.left + (step.centerX + step.size) * sx, v.top + (step.centerY + step.size) * sy);
            canvas.drawArc(oval, step.startAngle, 90, false, spiralPaint);
        }
        canvas.restore();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        RatioMask.Frame frame = RatioMask.compute(w, h, pixelAspect, RatioMask.SENSOR_RATIO, format.ratio);
        RatioMask.Rect v = frame.visible;

        // Mask everything outside the visible area, including the screen margins.
        if (v.top > 0) canvas.drawRect(0, 0, w, v.top, bandPaint);
        if (v.bottom < h) canvas.drawRect(0, v.bottom, w, h, bandPaint);
        if (v.left > 0) canvas.drawRect(0, v.top, v.left, v.bottom, bandPaint);
        if (v.right < w) canvas.drawRect(v.right, v.top, w, v.bottom, bandPaint);

        // (Vignetting in the live view removed: banding was visible on the camera screen.
        // Vignetting stays in the XMP. drawVignette is kept for a future attempt.)

        // Thin outline when the bands are semi-transparent, to show the edge.
        if (opacity < MAX_OPACITY)
            canvas.drawRect(v.left, v.top, v.right - 1, v.bottom - 1, framePaint);

        // Framing lines, fitted to the selected format.
        for (float f : RatioMask.gridFractions(grid)) {
            float gx = v.left + f * v.width();
            float gy = v.top + f * v.height();
            canvas.drawLine(gx, v.top, gx, v.bottom, gridPaint);
            canvas.drawLine(v.left, gy, v.right, gy, gridPaint);
        }

        if (RatioMask.gridSpiral(grid) != 0)
            drawSpiral(canvas, v, RatioMask.gridSpiral(grid) < 0);

        // Format at the top left, settings at the bottom, in the screen margins (also for vertical formats).
        float x = 8;
        float y = 8 - textPaint.ascent();
        String label = "AR " + format.name + "  " + RatioMask.megapixelText(format.ratio) + (opacity < MAX_OPACITY ? "  " + opacity + "%" : "");
        canvas.drawText(label, x, y, textPaint);
        // Selected film: its icon to the right of the format.
        if (film != null) {
            float ih = textPaint.getTextSize();
            drawFilmIcon(canvas, film, x + textPaint.measureText(label) + 12, y + textPaint.ascent() + 2, ih * 2.4f, ih);
        }
        // Focus confirmation at the bottom left, like the camera's green dot.
        if (focus != FOCUS_NONE) {
            float r = 9, fx = 8 + r, fy = h - 8 - r;
            focusPaint.setStyle(focus == FOCUS_WORKING ? Paint.Style.STROKE : Paint.Style.FILL);
            focusPaint.setStrokeWidth(2);
            focusPaint.setColor(focus == FOCUS_LOCKED ? Color.rgb(60, 220, 60)
                    : focus == FOCUS_FAILED ? Color.rgb(255, 80, 80) : Color.WHITE);
            canvas.drawCircle(fx, fy, r, focusPaint);
        }
        // Brackets always visible: gray when idle, then the focus color.
        if (focus == FOCUS_NONE) focusPaint.setColor(Color.rgb(150, 150, 150));
        drawFocusFrame(canvas, w, h);

        // Battery at the top right, red below 15%.
        if (battery >= 0) {
            String text = "Bat. " + battery + "%";
            Paint paint = battery <= 15 ? lowBatteryPaint : textPaint;
            canvas.drawText(text, w - 8 - paint.measureText(text), y, paint);
            if (remaining >= 0) {
                String shots = remaining + " shots";
                canvas.drawText(shots, w - 8 - paint.measureText(text) - 16 - textPaint.measureText(shots), y, textPaint);
            }
        } else if (remaining >= 0) {
            String shots = remaining + " shots";
            canvas.drawText(shots, w - 8 - textPaint.measureText(shots), y, textPaint);
        }
        if (message != null)
            canvas.drawText(message, x, y + textPaint.getTextSize() + 4, textPaint);
        // Bottom line: mode, shutter speed, aperture, exposure meter, ISO and compensation.
        if (exposure != null) {
            float textY = h - 8 - textPaint.descent();
            float gap = 14;
            boolean withMeter = meter != NO_METER && exposureRight != null;
            float wl = textPaint.measureText(exposure);
            float wr = exposureRight != null ? textPaint.measureText(exposureRight) : 0;
            float wm = withMeter ? METER_WIDTH : 0;
            float total = wl + (exposureRight != null ? gap + wr : 0) + (withMeter ? gap + wm : 0);
            float lx = (w - total) / 2;
            canvas.drawText(exposure, lx, textY, textPaint);
            float next = lx + wl + gap;
            if (withMeter) {
                drawMeter(canvas, next + wm / 2, textY - 7);
                next += wm + gap;
            }
            if (exposureRight != null)
                canvas.drawText(exposureRight, next, textY, textPaint);
        }
        // Level in the lower part of the image, above the bottom band.
        if (!Float.isNaN(levelRoll)) {
            drawLevel(canvas, w / 2f, v.top + v.height() * 0.85f, w);
            if (levelDebug != null)
                canvas.drawText(levelDebug, w / 2f, v.top + v.height() * 0.85f + 22, meterTextPaint);
        }
        if (filmMenu >= 0)
            drawFilmMenu(canvas, w, h);
    }
}
