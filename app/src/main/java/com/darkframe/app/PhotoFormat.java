package com.darkframe.app;

import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Finds the format chosen at capture time, from the XMP written by the app (.xmp file or block in the JPEG). */
public final class PhotoFormat {
    /** The XMP block is inserted at the start of the JPEG, right after the Exif. */
    private static final int JPEG_HEAD = 128 * 1024;

    private PhotoFormat() {}

    /** Format name, or null if the photo has no crop. */
    public static String read(String folder, String filename) {
        if (folder == null || filename == null) return null;
        File dir = new File(new File(Environment.getExternalStorageDirectory(), "DCIM"), folder);
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        try {
            File xmp = find(dir, base + ".xmp");
            if (xmp != null) return XmpCrop.formatFromXmp(readHead(xmp, 64 * 1024));
            File jpeg = find(dir, base + ".JPG");
            if (jpeg != null) return XmpCrop.formatFromXmp(readHead(jpeg, JPEG_HEAD));
        } catch (IOException e) {
            Log.write("Playback: cannot read format of " + filename + ": " + e);
        }
        return null;
    }

    private static String readHead(File file, int max) throws IOException {
        byte[] buffer = new byte[(int) Math.min(max, file.length())];
        InputStream in = new FileInputStream(file);
        try {
            int n = 0, r;
            while (n < buffer.length && (r = in.read(buffer, n, buffer.length - n)) > 0) n += r;
            return new String(buffer, 0, n, "ISO-8859-1");
        } finally {
            in.close();
        }
    }

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
