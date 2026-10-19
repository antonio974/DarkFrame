package com.darkframe.app;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.List;

/** Reads the camera's accelerometer, if apps can access it, and smooths the measured gravity. */
public class LevelSensor implements SensorEventListener {
    public interface Listener {
        void onLevelChanged(float roll, float pitch);
    }

    private static final float SMOOTHING = 0.15f;
    private static boolean listLogged;
    private int rawLogs;
    private long lastRawLog;

    /** Smoothed gravity (x, y, z), for the debug display. */
    public float[] gravity() { return gravity; }

    private final SensorManager manager;
    private final Listener listener;
    private final float[] gravity = new float[3];
    private boolean hasValue;

    public LevelSensor(Context context, Listener listener) {
        this.manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.listener = listener;
    }

    /** True if an accelerometer was found and registered. */
    public boolean start() {
        if (manager == null) {
            Log.write("Level: no SensorManager");
            return false;
        }
        if (!listLogged) {
            listLogged = true;
            List<Sensor> all = manager.getSensorList(Sensor.TYPE_ALL);
            StringBuilder names = new StringBuilder();
            if (all != null)
                for (Sensor s : all)
                    names.append(" [").append(s.getType()).append(' ').append(s.getName()).append(']');
            Log.write("Sensors:" + (names.length() > 0 ? names.toString() : " none"));
        }
        Sensor sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor == null) return false;
        hasValue = false;
        return manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
    }

    public void stop() {
        if (manager != null) manager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        for (int i = 0; i < 3; i++)
            gravity[i] = hasValue ? gravity[i] + SMOOTHING * (event.values[i] - gravity[i]) : event.values[i];
        hasValue = true;
        // On the a7R, the sensor's z axis is vertical when the camera is level (x 0, y 0, z 1 measured on an a7R):
        // side tilt is read from x and z, forward / backward tilt from y.
        listener.onLevelChanged(Level.roll(gravity[0], gravity[2]), Level.pitch(gravity[0], gravity[2], gravity[1]));
        long now = System.currentTimeMillis();
        if (rawLogs < 30 && now - lastRawLog > 2000) {
            rawLogs++;
            lastRawLog = now;
            Log.write("Raw level: sensor " + event.sensor.getName() + ", x " + gravity[0] + ", y " + gravity[1] + ", z " + gravity[2]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
