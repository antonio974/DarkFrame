package com.darkframe.app;

import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

/**
 * Log in DARKFRAME/LOG.TXT on the SD card, for debugging on the camera.
 * Off by default: the app never creates the folder. To turn logging on, create a DARKFRAME folder
 * at the root of the card.
 */
public final class Log {
    private Log() {}

    public static synchronized void write(String message) {
        write(message, null);
    }

    public static synchronized void write(String message, Throwable error) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "DARKFRAME");
            if (!dir.isDirectory()) return;
            PrintWriter out = new PrintWriter(new FileWriter(new File(dir, "LOG.TXT"), true));
            try {
                out.println(new Date() + " : " + message);
                if (error != null)
                    error.printStackTrace(out);
            } finally {
                out.close();
            }
        } catch (IOException ignored) {}
    }
}
