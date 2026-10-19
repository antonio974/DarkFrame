package com.darkframe.app;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import com.github.ma1co.openmemories.framework.DisplayManager;
import com.github.ma1co.openmemories.framework.ImageInfo;
import com.github.ma1co.openmemories.framework.MediaManager;

import java.io.IOException;
import java.io.InputStream;

/**
 * Playback of the photos on the card, newest first, with the bands of the current format.
 * Inspired by PlaybackActivity and ImageActivity from PMCADemo (ma1co, MIT licence).
 *
 * Controls:
 *   left / right, dials         newer / older photo
 *   up / down                   try another format on this photo (each photo opens with its own)
 *   center                      show / hide the bands
 *   play, trash, shutter half-press   back to the live view
 */
public class PlaybackActivity extends BaseActivity {
    private PhotoView photo;
    private OverlayView overlay;
    private MediaManager mediaManager;
    private Cursor cursor;
    private Bitmap bitmap;
    private int formatIndex;
    /** Images are decoded on a separate thread so the screen and keys stay responsive. */
    private HandlerThread decoderThread;
    private Handler decoder;
    private final Handler main = new Handler();
    /** Number of the last requested photo: a decode that is no longer needed is dropped. */
    private int request;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playback);
        photo = (PhotoView) findViewById(R.id.photo);
        overlay = (OverlayView) findViewById(R.id.overlay);
        mediaManager = MediaManager.create(this);
        decoderThread = new HandlerThread("playback");
        decoderThread.start();
        decoder = new Handler(decoderThread.getLooper());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        decoderThread.quit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        android.content.SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        formatIndex = MainActivity.savedFormat(prefs);
        overlay.setFormat(RatioMask.FORMATS[formatIndex]);
        overlay.setOpacity(prefs.getInt(MainActivity.KEY_OPACITY, 100));
        overlay.setGrid(prefs.getInt(MainActivity.KEY_GRID, RatioMask.GRID_NONE));
        updatePixelAspect();

        // New query on every resume, to see the photo just taken.
        cursor = mediaManager.queryNewestImages();
        if (cursor != null && cursor.moveToFirst())
            show();
        else
            overlay.setMessage("No photos on the card");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // On power-off or sleep, close playback: on power-on, the app goes back to the live view.
        finish();
        request++;
        decoder.removeCallbacksAndMessages(null);
        main.removeCallbacksAndMessages(null);
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
        photo.setBitmap(null);
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
    }

    private void updatePixelAspect() {
        float pixelAspect = getPixelAspect();
        photo.setPixelAspect(pixelAspect);
        overlay.setPixelAspect(pixelAspect);
    }

    @Override
    public void displayChanged(DisplayManager.Display display) {
        updatePixelAspect();
    }

    /**
     * Shows the current photo: the name right away, the thumbnail as soon as it is read,
     * then the higher-quality preview. If another photo is selected meanwhile, decoding is dropped.
     */
    private void show() {
        final ImageInfo info = mediaManager.getImageInfo(cursor);
        final int id = ++request;
        // Bands for the photo's format, read from its XMP; without XMP, the photo is full 3:2.
        final String folder = info.getFolder(), filename = info.getFilename();
        overlay.setFormat(RatioMask.FORMATS[RatioMask.indexOf("3:2")]);
        overlay.setMessage((cursor.getPosition() + 1) + "/" + cursor.getCount() + "  " + info.getFilename());
        decoder.removeCallbacksAndMessages(null);
        decoder.post(new Runnable() {
            @Override
            public void run() {
                if (id != request) return;
                deliverFormat(id, PhotoFormat.read(folder, filename));
                if (id != request) return;
                if (bitmap == null) deliver(id, decode(info.getThumbnail(), 1));
                if (id != request) return;
                deliver(id, decode(info.getPreviewImage(), 2));
            }
        });
    }

    /** Decodes an image; sample = 2 halves the size (the a7R preview is larger than the screen). */
    private static Bitmap decode(InputStream in, int sample) {
        if (in == null) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeStream(in, null, options);
        } catch (Throwable e) {
            Log.write("Playback: decode failed: " + e);
            return null;
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
    }

    /** Applies the photo's format, if it still matches the requested photo. */
    private void deliverFormat(final int id, final String name) {
        final int index = name != null ? RatioMask.indexOf(name) : -1;
        main.post(new Runnable() {
            @Override
            public void run() {
                if (id != request) return;
                formatIndex = index >= 0 ? index : RatioMask.indexOf("3:2");
                overlay.setFormat(RatioMask.FORMATS[formatIndex]);
            }
        });
    }

    /** Hands the image to the UI thread, if it still matches the requested photo. */
    private void deliver(final int id, final Bitmap next) {
        if (next == null) return;
        main.post(new Runnable() {
            @Override
            public void run() {
                if (id != request) {
                    next.recycle();
                    return;
                }
                photo.setBitmap(next);
                if (bitmap != null && bitmap != next)
                    bitmap.recycle();
                bitmap = next;
            }
        });
    }

    /** direction > 0: older photo. */
    private boolean move(int direction) {
        if (cursor == null) return true;
        boolean moved = direction > 0 ? cursor.moveToNext() : cursor.moveToPrevious();
        if (moved)
            show();
        else if (direction > 0)
            cursor.moveToLast();
        else
            cursor.moveToFirst();
        return true;
    }

    private boolean changeFormat(int direction) {
        formatIndex = RatioMask.nextFormat(formatIndex, direction);
        overlay.setFormat(RatioMask.FORMATS[formatIndex]);
        return true;
    }

    @Override protected boolean onLeftKeyDown() { return move(-1); }
    @Override protected boolean onRightKeyDown() { return move(1); }
    /** Dials: same direction as Sony playback (opposite of the received value, measured on an a7R). */
    @Override protected boolean onDialTurned(int dial, int direction) { return move(-direction); }
    @Override protected boolean onUpKeyDown() { return changeFormat(-1); }
    @Override protected boolean onDownKeyDown() { return changeFormat(1); }

    @Override
    protected boolean onEnterKeyDown() {
        overlay.setVisibility(overlay.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
        return true;
    }

    @Override
    protected boolean onPlayKeyUp() {
        finish();
        return true;
    }

    @Override
    protected boolean onFocusKeyDown() {
        finish();
        return true;
    }
}
