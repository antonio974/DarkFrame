package com.darkframe.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * Shows a photo in the same place as the live view (same computation as the bands),
 * so that OverlayView, laid on top, lines up exactly.
 */
public class PhotoView extends View {
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap bitmap;
    private float pixelAspect = 1f;

    public PhotoView(Context context) {
        this(context, null);
    }

    public PhotoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        invalidate();
    }

    public void setPixelAspect(float pixelAspect) {
        this.pixelAspect = pixelAspect > 0 ? pixelAspect : 1f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null) return;
        float ratio = (float) bitmap.getWidth() / bitmap.getHeight();
        RatioMask.Rect r = RatioMask.compute(getWidth(), getHeight(), pixelAspect, ratio, ratio).image;
        canvas.drawBitmap(bitmap, null, new Rect(r.left, r.top, r.right, r.bottom), paint);
    }
}
