package com.darkframe.app;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * After each shot, waits for the new photo on the card, then writes the crop: an XMP sidecar next to the RAW,
 * or an XMP block inside the JPEG for a JPEG-only photo.
 *
 * Shots are queued: each new photo found in the DCIM folder, in file-number order, gets the format of the next
 * queued shot. This way several photos taken in a row each keep their own format, even if the camera saves
 * them later.
 * All work runs on a separate thread, so it never delays the shutter or the live view.
 */
public class CropWriter {
    public interface Listener {
        void onCropResult(String message);
    }

    private static final long POLL_MS = 500;
    /** Give up on a shot when no photo has appeared after this delay. */
    private static final long SHOT_TIMEOUT_MS = 40000;
    /** After the last shot, keep watching the card for a while (burst photos). */
    private static final long WATCH_AFTER_SHOT_MS = 15000;
    /** Minimum wait for the RAW before treating a photo as JPEG-only (number of polls). */
    private static final int RAW_WAIT_POLLS = 6;

    /** A shot waiting for its photo; null format = no XMP to write. */
    private static final class Pending {
        final RatioMask.Format format;
        final FilmLook film;
        final long time;
        Pending(RatioMask.Format format, FilmLook film, long time) { this.format = format; this.film = film; this.time = time; }
    }

    /** A new photo in a folder, identified by its name without extension. */
    private static final class Photo {
        final File dir;
        final String base;
        final String key;
        int polls;
        long jpegSize = -1;
        Photo(File dir, String base) { this.dir = dir; this.base = base; key = key(dir, base); }
    }

    private final Handler main = new Handler();
    private final HandlerThread thread = new HandlerThread("xmp");
    private final Handler handler;
    private final Listener listener;

    // State read and written only on the worker thread.
    /** ARW and JPG files already known or processed (folder path + lowercase name). */
    private final java.util.Set<String> known = new java.util.HashSet<String>();
    private final java.util.Set<File> watchedDirs = new java.util.HashSet<File>();
    private final java.util.LinkedList<Pending> queue = new java.util.LinkedList<Pending>();
    private final java.util.Map<String, Photo> waiting = new java.util.HashMap<String, Photo>();
    private RatioMask.Format lastFormat;
    private FilmLook lastFilm;
    private long lastShotTime;
    private boolean polling;
    private boolean releasing;

    public CropWriter(Context context, Listener listener) {
        this.listener = listener;
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    /** Stops the worker thread (activity ends) once pending photos are done. */
    public void release() {
        handler.post(new Runnable() {
            @Override public void run() {
                releasing = true;
                if (!polling) thread.quit();
            }
        });
    }

    /** Records the files already on the card (on start, on half-press) when no photo is pending. */
    public void refreshSnapshot() {
        handler.post(new Runnable() {
            @Override public void run() {
                if (polling) return;
                try {
                    scan(true);
                } catch (RuntimeException e) {
                    Log.write("XMP: card state: " + e, e);
                }
            }
        });
    }

    private void post(final String message) {
        main.post(new Runnable() {
            @Override public void run() { listener.onCropResult(message); }
        });
    }

    /**
     * Call on each shot, in order; null format if the photo needs no XMP (no crop and no film).
     * Null film: no film simulation.
     */
    public void writeAfterShot(final RatioMask.Format format, final FilmLook film) {
        handler.post(new Runnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                if (known.isEmpty() && watchedDirs.isEmpty()) scan(true);
                queue.add(new Pending(format, film, now));
                lastFormat = format;
                lastFilm = film;
                lastShotTime = now;
                if (!polling) {
                    polling = true;
                    handler.postDelayed(poll, POLL_MS);
                }
            }
        });
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            try {
                scan(false);
                process();
            } catch (RuntimeException e) {
                Log.write("XMP: " + e, e);
            }
            long now = System.currentTimeMillis();
            if (!queue.isEmpty() && waiting.isEmpty() && now - queue.getFirst().time > SHOT_TIMEOUT_MS) {
                queue.removeFirst();
                post("XMP: photo not found");
                Log.write("XMP: no new photo after " + (SHOT_TIMEOUT_MS / 1000) + " s, folders " + watchedDirs);
            }
            if (!queue.isEmpty() || !waiting.isEmpty() || now - lastShotTime < WATCH_AFTER_SHOT_MS) {
                handler.postDelayed(this, POLL_MS);
            } else {
                polling = false;
                if (releasing) thread.quit();
            }
        }
    };

    /**
     * Scans the newest DCIM folder (and those already watched). With markKnown, everything found is recorded
     * as already present; otherwise new photos are added to the waiting list.
     */
    private void scan(boolean markKnown) {
        File newest = newestDcimFolder();
        if (newest != null) watchedDirs.add(newest);
        for (File dir : watchedDirs) {
            String[] list = dir.list();
            if (list == null) continue;
            for (String n : list) {
                String lower = n.toLowerCase();
                if (!lower.endsWith(".arw") && !lower.endsWith(".jpg")) continue;
                String base = n.substring(0, n.length() - 4);
                String key = key(dir, base);
                if (known.contains(key)) continue;
                if (markKnown) known.add(key);
                else if (!waiting.containsKey(key)) waiting.put(key, new Photo(dir, base));
            }
        }
    }

    /** Processes waiting photos in file-number order, each with the next queued shot. */
    private void process() {
        java.util.List<Photo> photos = new java.util.ArrayList<Photo>(waiting.values());
        java.util.Collections.sort(photos, new java.util.Comparator<Photo>() {
            @Override public int compare(Photo a, Photo b) {
                int c = a.dir.getName().compareTo(b.dir.getName());
                return c != 0 ? c : a.base.compareToIgnoreCase(b.base);
            }
        });
        for (Photo photo : photos) {
            photo.polls++;
            if (!ready(photo)) return; // keep the order: later photos wait for this one
            waiting.remove(photo.key);
            known.add(photo.key);
            Pending shot = queue.isEmpty() ? null : queue.removeFirst();
            RatioMask.Format format = shot != null ? shot.format : lastFormat;
            FilmLook film = shot != null ? shot.film : lastFilm;
            if (shot == null)
                Log.write("XMP: photo with no pending shot (burst?): " + photo.base);
            if (format == null) continue;
            post(write(photo.dir, photo.base, format, film));
        }
    }

    /** True when the photo can be processed: RAW present, or a complete JPEG-only file. */
    private static boolean ready(Photo photo) {
        if (find(photo.dir, photo.base + ".ARW") != null) return true;
        // No RAW (yet): give the RAW time to appear before handling the JPEG alone.
        if (photo.polls < RAW_WAIT_POLLS) return false;
        File jpeg = find(photo.dir, photo.base + ".JPG");
        if (jpeg == null) return false;
        // The JPEG must be fully written: same size on two polls.
        long size = jpeg.length();
        boolean stable = size > 0 && size == photo.jpegSize;
        photo.jpegSize = size;
        return stable;
    }

    private static String key(File dir, String base) {
        return dir.getPath() + "/" + base.toLowerCase();
    }

    /** Newest DCIM/xxxMSDCF folder (highest name), or null. */
    private static File newestDcimFolder() {
        File dcim = new File(Environment.getExternalStorageDirectory(), "DCIM");
        File[] dirs = dcim.listFiles();
        File newest = null;
        if (dirs != null)
            for (File d : dirs)
                if (d.isDirectory() && (newest == null || d.getName().compareTo(newest.getName()) > 0)) newest = d;
        return newest;
    }

    /** Writes the photo's crop and returns the message to display. */
    private String write(File dir, String base, RatioMask.Format format, FilmLook film) {
        XmpCrop crop = XmpCrop.forRatio(RatioMask.SENSOR_RATIO, format.ratio);
        String xmp = crop.toXmp(format.name, film);
        String done = (crop.isFull() ? "XMP" : "Crop " + format.name) + (film != null ? " + " + film.name : "") + ": " + base;
        if (find(dir, base + ".ARW") != null)
            return writeSidecar(new File(dir, base + ".xmp"), xmp, done);
        return embedInJpeg(find(dir, base + ".JPG"), xmp, done, base);
    }

    /** RAW: DSC01234.xmp file next to DSC01234.ARW. */
    private String writeSidecar(File xmp, String content, String done) {
        try {
            OutputStream out = new FileOutputStream(xmp);
            try {
                out.write(content.getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (IOException e) {
            Log.write("XMP: write failed " + xmp + ": " + e);
            return "XMP: write failed";
        }
        Log.write("XMP written: " + xmp + " (" + done + ")");
        return done;
    }

    /**
     * JPEG only: the crop is added to the JPEG metadata (pixels are not re-encoded).
     * A full copy is written and its size checked, then it replaces the original.
     */
    private String embedInJpeg(File jpeg, String content, String done, String base) {
        File tmp = new File(jpeg.getParentFile(), base + ".TMP");
        try {
            InputStream check = new FileInputStream(jpeg);
            try {
                if (JpegXmp.hasXmp(check)) {
                    Log.write("XMP: already present in " + jpeg);
                    return "Crop already present: " + base;
                }
            } finally {
                check.close();
            }

            String packet = JpegXmp.packet(content);
            InputStream in = new BufferedInputStream(new FileInputStream(jpeg), 64 * 1024);
            OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp), 64 * 1024);
            try {
                JpegXmp.insert(in, out, packet);
            } finally {
                in.close();
                out.close();
            }

            long expected = jpeg.length() + JpegXmp.segmentLength(packet) + 2;
            if (tmp.length() != expected)
                throw new IOException("incomplete copy: " + tmp.length() + " instead of " + expected);
            if (!jpeg.delete() || !tmp.renameTo(jpeg))
                throw new IOException("replace failed, copy kept in " + tmp);
        } catch (IOException e) {
            if (jpeg.exists()) tmp.delete();
            Log.write("XMP JPEG: failed for " + jpeg + ": " + e);
            return "JPEG crop failed";
        }
        Log.write("XMP added to JPEG: " + jpeg + " (" + done + ")");
        return done;
    }

    /** File in the folder, case-insensitive (DSC01234.ARW or dsc01234.arw), or null. */
    private static File find(File dir, String name) {
        File exact = new File(dir, name);
        if (exact.exists()) return exact;
        String[] names = dir.list();
        if (names == null) return null;
        for (String n : names)
            if (n.equalsIgnoreCase(name)) return new File(dir, n);
        return null;
    }
}
