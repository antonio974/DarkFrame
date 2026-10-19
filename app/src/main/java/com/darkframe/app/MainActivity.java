package com.darkframe.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import com.github.ma1co.openmemories.framework.DisplayManager;
import com.sony.scalar.hardware.CameraEx;


/**
 * Live view with black bars.
 * Live view is adapted from CameraActivity in PMCADemo (ma1co, MIT licence);
 * shooting follows jonasjuffinger's TimeLapse app (ShutterListener + picture review).
 *
 * Controls:
 *   C1                          guides: none, thirds, golden ratio, golden spiral, flipped golden spiral, centre cross
 *   C2                          next format, cycling (1:1 ... 65x24, then 1:1)
 *   left / right                previous / next format (fallback if C2 is not received)
 *   front dial                  shutter speed (M, S); in A: aperture
 *   rear dial                   aperture (M, A); in S: shutter speed
 *   control wheel               ISO (AUTO then 100 to 25600 in thirds), in all modes
 *   ±3 dial                     exposure compensation
 *   Fn                          next exposure mode (M, A, S, P); the mode dial is also followed
 *   up / down                   bar opacity ± 10% (50 to 100%)
 *   centre                      show / hide the bars
 *   half press                  focus; full press: photo (full 3:2 file)
 *   after each photo: crop XMP file next to the RAW, read by Lightroom on import
 *   playback                    review photos, with the bars
 *   trash                       quit
 *   top: format; bottom: mode, shutter speed, aperture, ISO, exposure compensation
 */
public class MainActivity extends BaseActivity implements SurfaceHolder.Callback, CameraEx.ShutterListener, CropWriter.Listener {
    static final String PREFS = "bandes";
    static final String KEY_FORMAT = "formatName";

    /** Saved format, by name; otherwise the default format. */
    static int savedFormat(SharedPreferences prefs) {
        int index = RatioMask.indexOf(prefs.getString(KEY_FORMAT, ""));
        return index >= 0 ? index : RatioMask.DEFAULT_FORMAT;
    }
    static final String KEY_OPACITY = "opacity";
    /** Time of the last exit via the trash button, and the delay during which an automatic relaunch is refused. */
    private static final String KEY_QUIT_AT = "quitAt";
    private static final long QUIT_GUARD_MS = 10000;
    static final String KEY_GRID = "grid";
    static final String KEY_FILM = "film";
    /** Camera picture settings before the film preview, restored on exit (also kept in case of a crash). */
    private static final String KEY_ORIGINAL_LOOK = "originalLook";
    private static final int OPACITY_STEP = 10;
    private static final long MESSAGE_DURATION_MS = 2500;

    private SurfaceHolder surfaceHolder;
    private OverlayView overlay;
    private CameraEx camera;
    private int formatIndex;
    /** Chosen film, or null; filmMenu: highlighted row of the film menu, -1 when closed. */
    private FilmLook film;
    private int filmMenu = -1;
    private FilmLook cropFilm;
    private boolean takingPicture;
    private CropWriter cropWriter;
    /** Format to apply to the current photo, or null if no crop. */
    private RatioMask.Format cropFormat;
    // Settings shown at the bottom of the screen.
    private String shutterText = "--", apertureText = "f--", evText = "";
    private int isoSetting, isoAuto;
    private String sceneMode = "";
    private static final String MODE_M = CameraEx.ParametersModifier.SCENE_MODE_MANUAL_EXPOSURE;
    private static final String MODE_A = CameraEx.ParametersModifier.SCENE_MODE_APERTURE_PRIORITY;
    private static final String MODE_S = CameraEx.ParametersModifier.SCENE_MODE_SHUTTER_PRIORITY;
    private static final String MODE_P = CameraEx.ParametersModifier.SCENE_MODE_PROGRAM_AUTO;
    private final Handler handler = new Handler();
    /** Battery level, broadcast by the system on each change (as in the TimeLapse app). */
    private final android.content.BroadcastReceiver batteryReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            overlay.setBattery(level >= 0 && scale > 0 ? level * 100 / scale : -1);
        }
    };
    private final Runnable clearMessage = new Runnable() {
        @Override public void run() { overlay.setMessage(null); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashLog();
        Intent launch = getIntent();
        Log.write("Launch: " + (launch != null ? launch.getAction() + " " + launch.getExtras() : "?"));
        // Right after an exit via the trash button, the camera sometimes relaunches the app by itself: close it again.
        long sinceQuit = System.currentTimeMillis() - getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_QUIT_AT, 0);
        if (sinceQuit >= 0 && sinceQuit < QUIT_GUARD_MS) {
            Log.write("Relaunch " + sinceQuit + " ms after exit: ignored");
            forgetAppInfo();
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        overlay = (OverlayView) findViewById(R.id.overlay);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setFormat(savedFormat(prefs));
        overlay.setOpacity(prefs.getInt(KEY_OPACITY, 100));
        overlay.setGrid(prefs.getInt(KEY_GRID, RatioMask.GRID_NONE));
        film = FilmLook.byName(prefs.getString(KEY_FILM, null));
        overlay.setFilm(film);
        cropWriter = new CropWriter(this, this);
        levelSensor = new LevelSensor(this, new LevelSensor.Listener() {
            @Override
            public void onLevelChanged(final float roll, float pitch) {
                handler.post(new Runnable() {
                    @Override public void run() { overlay.setLevel(roll); }
                });
            }
        });
    }

    private LevelSensor levelSensor;

    /** Shots remaining on the card, read from the Sony index on a separate thread (the call can be slow). */
    private void updateRemaining() {
        new Thread("remaining") {
            @Override
            public void run() {
                int count = -1;
                try {
                    String[] ids = com.sony.scalar.provider.AvindexStore.getExternalMediaIds();
                    if (ids != null && ids.length > 0)
                        count = com.sony.scalar.provider.AvindexStore.Images.getAvailableCount(ids[0]);
                } catch (Throwable e) {
                    Log.write("Remaining shots unavailable: " + e);
                }
                final int shots = count;
                handler.post(new Runnable() {
                    @Override public void run() { overlay.setRemaining(shots); }
                });
            }
        }.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        takingPicture = false;
        previewing = false;
        openTries = 0;
        surfaceHolder.addCallback(this);
        updatePixelAspect();
        cropWriter.refreshSnapshot();
        if (!levelSensor.start()) {
            Log.write("Level: no accelerometer");
            overlay.setLevel(Float.NaN);
        }
        updateRemaining();
        registerReceiver(batteryReceiver, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        openCamera.run();
    }

    private boolean previewing;
    private int openTries;
    private static final int MAX_OPEN_TRIES = 20;
    private static final long RETRY_MS = 300;

    /**
     * Opens the sensor and starts live view. On wake from standby the camera is not always ready right away:
     * retry rather than stay on a black screen.
     */
    private final Runnable openCamera = new Runnable() {
        @Override
        public void run() {
            if (camera == null) {
                try {
                    camera = CameraEx.open(0, null);
                } catch (Throwable e) {
                    camera = null;
                    Log.write("Opening sensor, attempt " + (openTries + 1) + ": " + e);
                }
                if (camera == null) {
                    if (++openTries < MAX_OPEN_TRIES) handler.postDelayed(this, RETRY_MS);
                    else showMessage("Sensor unavailable: quit and restart the app");
                    return;
                }
                camera.setShutterListener(MainActivity.this);
                camera.setAutoPictureReviewControl(new CameraEx.AutoPictureReviewControl());
                startExposureInfo();
                applyFilmPreview(film);
            }
            // If the display surface already exists (it was not destroyed during standby),
            // surfaceCreated will not be called again: start live view ourselves.
            if (surfaceHolder.getSurface() != null && surfaceHolder.getSurface().isValid())
                startPreview(surfaceHolder);
        }
    };

    private void startPreview(SurfaceHolder holder) {
        if (camera == null || previewing) return;
        try {
            camera.getNormalCamera().setPreviewDisplay(holder);
            camera.getNormalCamera().startPreview();
            previewing = true;
        } catch (Throwable e) {
            Log.write("Live view: " + e, e);
            if (++openTries < MAX_OPEN_TRIES) handler.postDelayed(openCamera, RETRY_MS);
            else showMessage("Live view error");
        }
    }

    /**
     * Reads the current settings, then follows their changes (auto mode, auto ISO, lens).
     * Inspired by ManualActivity in BetterManual (Martin Tofall, MIT licence).
     * If something goes wrong on a camera, the display is simply dropped.
     */
    private void startExposureInfo() {
        try {
            android.hardware.Camera.Parameters params = camera.getNormalCamera().getParameters();
            CameraEx.ParametersModifier modifier = camera.createParametersModifier(params);
            android.util.Pair<Integer, Integer> speed = modifier.getShutterSpeed();
            shutterText = ExposureText.shutter(speed.first, speed.second);
            apertureText = ExposureText.aperture(modifier.getAperture());
            isoSetting = modifier.getISOSensitivity();
            evText = ExposureText.ev(params.getExposureCompensation() * params.getExposureCompensationStep());
            sceneMode = params.getSceneMode();
            try {
                Log.write("Image flip modes: " + modifier.getSupportedFlipModes());
            } catch (Throwable e) {
                Log.write("Image flip modes: unavailable (" + e + ")");
            }
            Log.write("Exposure mode at startup: " + sceneMode
                    + ", mode dial: " + keyStatus(com.sony.scalar.sysutil.ScalarInput.ISV_KEY_MODE_DIAL));
            // The dials only set shutter speed and aperture in M, A or S: follow the mode dial,
            // otherwise switch to M.
            try {
                String fromDial = modeForDial(keyStatus(com.sony.scalar.sysutil.ScalarInput.ISV_KEY_MODE_DIAL));
                if (fromDial != null)
                    setSceneMode(fromDial);
                else if (!isExposureMode(sceneMode))
                    setSceneMode(MODE_M);
            } catch (Throwable e) {
                Log.write("Exposure mode: " + e, e);
            }
            // Compensation: the ±3 dial wins if it is not on 0.
            int evDial = keyStatus(com.sony.scalar.sysutil.ScalarInput.ISV_KEY_EV_COMPENSATION);
            if (ExposureText.evDialThirds(evDial) != Integer.MIN_VALUE && ExposureText.evDialThirds(evDial) != 0) {
                try {
                    applyEvDial(evDial);
                } catch (Throwable e) {
                    Log.write("EV dial at startup: " + e, e);
                }
            }

            camera.setShutterSpeedChangeListener(new CameraEx.ShutterSpeedChangeListener() {
                @Override
                public void onShutterSpeedChange(CameraEx.ShutterSpeedInfo info, CameraEx cameraEx) {
                    shutterText = ExposureText.shutter(info.currentShutterSpeed_n, info.currentShutterSpeed_d);
                    updateExposureInfo();
                }
            });
            camera.setApertureChangeListener(new CameraEx.ApertureChangeListener() {
                @Override
                public void onApertureChange(CameraEx.ApertureInfo info, CameraEx cameraEx) {
                    apertureText = ExposureText.aperture(info.currentAperture);
                    updateExposureInfo();
                }
            });
            camera.setAutoFocusDoneListener(new CameraEx.AutoFocusDoneListener() {
                @Override
                public void onDone(int status, int[] areas, CameraEx cameraEx) {
                    showFocus(status, areas);
                }
            });
            // Exposure meter: the camera reports the offset from the metered exposure (as in BetterManual).
            camera.setProgramLineRangeOverListener(new CameraEx.ProgramLineRangeOverListener() {
                @Override
                public void onAERange(boolean ae, boolean shutter, boolean aperture, CameraEx cameraEx) {}

                @Override
                public void onEVRange(final int ev, CameraEx cameraEx) {
                    if (meterLogs < 10) {
                        meterLogs++;
                        Log.write("Metered exposure: " + ev);
                    }
                    handler.post(new Runnable() {
                        @Override public void run() { overlay.setMeter(ev); }
                    });
                }

                @Override
                public void onMeteringRange(boolean outOfRange, CameraEx cameraEx) {
                    Log.write("Metering out of range: " + outOfRange);
                }
            });
            camera.setAutoISOSensitivityListener(new CameraEx.AutoISOSensitivityListener() {
                @Override
                public void onChanged(int iso, CameraEx cameraEx) {
                    isoAuto = iso;
                    updateExposureInfo();
                }
            });
            updateExposureInfo();
        } catch (Throwable e) {
            Log.write("Shooting settings unavailable: " + e, e);
            overlay.setExposure(null);
        }
    }

    private int focusLogs, meterLogs;

    /** Focus point: green when sharp, red if the camera cannot focus. */
    private void showFocus(final int status, int[] areas) {
        if (focusLogs < 5) {
            focusLogs++;
            Log.write("Focus: status " + status + ", areas " + java.util.Arrays.toString(areas));
        }
        final int state;
        switch (status) {
            case CameraEx.AutoFocusDoneListener.STATUS_LOCK: state = OverlayView.FOCUS_LOCKED; break;
            case CameraEx.AutoFocusDoneListener.STATUS_LOCK_WARN: state = OverlayView.FOCUS_FAILED; break;
            case CameraEx.AutoFocusDoneListener.STATUS_WORKING:
            case CameraEx.AutoFocusDoneListener.STATUS_CONTINUOUS: state = OverlayView.FOCUS_WORKING; break;
            default: state = OverlayView.FOCUS_NONE; break;
        }
        handler.post(new Runnable() {
            @Override public void run() { overlay.setFocus(state); }
        });
    }

    private void updateExposureInfo() {
        overlay.setExposure(modeLetter() + "   " + shutterText + "   " + apertureText,
                ExposureText.iso(isoSetting, isoAuto) + "   " + evText);
    }

    private static boolean isExposureMode(String mode) {
        return MODE_M.equals(mode) || MODE_A.equals(mode) || MODE_S.equals(mode) || MODE_P.equals(mode);
    }

    private String modeLetter() {
        if (MODE_M.equals(sceneMode)) return "M";
        if (MODE_A.equals(sceneMode)) return "A";
        if (MODE_S.equals(sceneMode)) return "S";
        if (MODE_P.equals(sceneMode)) return "P";
        return "?";
    }

    /** Mode matching the mode dial position, or null (Auto, Scene, unknown...). */
    private static String modeForDial(int status) {
        switch (status) {
            case com.sony.scalar.sysutil.ScalarInput.ISV_KEY_MODE_MANUAL: return MODE_M;
            case com.sony.scalar.sysutil.ScalarInput.ISV_KEY_MODE_AEA: return MODE_A;
            case com.sony.scalar.sysutil.ScalarInput.ISV_KEY_MODE_AES: return MODE_S;
            case com.sony.scalar.sysutil.ScalarInput.ISV_KEY_MODE_PROGRAM: return MODE_P;
            default: return null;
        }
    }

    /** Like setSceneMode in BetterManual (Martin Tofall, MIT licence). */
    private void setSceneMode(String mode) {
        if (mode.equals(sceneMode) && mode.equals(camera.getNormalCamera().getParameters().getSceneMode()))
            return;
        android.hardware.Camera.Parameters change = camera.createEmptyParameters();
        change.setSceneMode(mode);
        camera.getNormalCamera().setParameters(change);
        sceneMode = camera.getNormalCamera().getParameters().getSceneMode();
        Log.write("Exposure mode requested: " + mode + ", got: " + sceneMode);
        // Re-read shutter speed and aperture, which can change with the mode.
        CameraEx.ParametersModifier modifier = camera.createParametersModifier(camera.getNormalCamera().getParameters());
        android.util.Pair<Integer, Integer> speed = modifier.getShutterSpeed();
        shutterText = ExposureText.shutter(speed.first, speed.second);
        apertureText = ExposureText.aperture(modifier.getAperture());
        updateExposureInfo();
    }

    /**
     * Fn: film menu (on the a7R, C3 is the trash button). The M, A, S, P mode is set
     * with the mode dial, which the app follows.
     */
    @Override
    protected boolean onFnKeyDown() {
        return openOrChooseFilm();
    }

    @Override
    protected boolean onModeDialChanged(int status) {
        if (camera == null) return true;
        String mode = modeForDial(status);
        try {
            if (mode != null) {
                setSceneMode(mode);
                showMessage("Mode " + modeLetter());
            } else {
                showMessage("Mode dial: position " + status);
            }
        } catch (Throwable e) {
            Log.write("Mode dial: " + e, e);
        }
        return true;
    }

    /** ±3 dial: sets exposure compensation to the dial position. */
    @Override
    protected boolean onEvDialChanged(int status) {
        if (camera == null) return true;
        try {
            applyEvDial(status);
        } catch (Throwable e) {
            Log.write("EV dial: " + e, e);
        }
        return true;
    }

    private void applyEvDial(int status) {
        int thirds = ExposureText.evDialThirds(status);
        if (thirds == Integer.MIN_VALUE) {
            showMessage("EV dial: position " + status);
            return;
        }
        android.hardware.Camera.Parameters params = camera.getNormalCamera().getParameters();
        float step = params.getExposureCompensationStep();
        int value = step > 0 ? Math.round(thirds / 3f / step) : 0;
        setExposureCompensation(params, value);
    }

    @Override
    protected void onUnknownKey(int scanCode) {
        showMessage("Key " + scanCode);
    }

    /** Trash: quit the app for good (no relaunch on wake). */
    @Override
    public void onBackPressed() {
        // Trash in the film menu: close it without changing anything.
        if (filmMenu >= 0) {
            closeFilmMenu(false);
            return;
        }
        forgetAppInfo();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_QUIT_AT, System.currentTimeMillis()).commit();
        Log.write("Exit via trash button");
        super.onBackPressed();
    }

    /**
     * An app cannot show the Sony menu on top of itself, so the Menu key leaves the app
     * for the camera, where the native menu opens. Relaunch on wake is kept so we come back to the app.
     */
    @Override
    protected boolean onMenuKeyDown() {
        Log.write("Menu key: back to camera");
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cropWriter != null) cropWriter.release();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clearMessage);
        handler.removeCallbacks(openCamera);
        unregisterReceiver(batteryReceiver);
        levelSensor.stop();
        surfaceHolder.removeCallback(this);
        previewing = false;
        if (camera == null) {
            save();
            return;
        }
        camera.setShutterListener(null);
        camera.setShutterSpeedChangeListener(null);
        camera.setApertureChangeListener(null);
        camera.setAutoISOSensitivityListener(null);
        camera.setAutoFocusDoneListener(null);
        camera.setProgramLineRangeOverListener(null);
        camera.setAutoPictureReviewControl(null);
        applyFilmPreview(null);
        camera.release();
        camera = null;
        save();
    }

    private void setFormat(int index) {
        formatIndex = RatioMask.clampIndex(index);
        overlay.setFormat(RatioMask.FORMATS[formatIndex]);
    }

    private void save() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_FORMAT, RatioMask.FORMATS[formatIndex].name)
                .putInt(KEY_OPACITY, overlay.getOpacity())
                .putInt(KEY_GRID, overlay.getGrid())
                .putString(KEY_FILM, film != null ? film.name : null)
                .commit();
    }

    /** Rear screen and viewfinder do not necessarily have the same pixel shape. */
    private void updatePixelAspect() {
        overlay.setPixelAspect(getPixelAspect());
    }

    @Override
    public void displayChanged(DisplayManager.Display display) {
        updatePixelAspect();
    }

    private void showMessage(String message) {
        overlay.setMessage(message);
        handler.removeCallbacks(clearMessage);
        handler.postDelayed(clearMessage, MESSAGE_DURATION_MS);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startPreview(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        previewing = false;
    }

    private boolean changeFormat(int direction) {
        setFormat(RatioMask.nextFormat(formatIndex, direction));
        save();
        return true;
    }

    /** C1: next guides (none, thirds, golden ratio, centre cross). */
    @Override
    protected boolean onC1KeyDown() {
        overlay.setGrid(RatioMask.nextGrid(overlay.getGrid()));
        showMessage("Guides: " + RatioMask.GRIDS[overlay.getGrid()]);
        save();
        return true;
    }

    /** C2: next format, cycling (65x24 then 1:1). */
    @Override
    protected boolean onC2KeyDown() {
        setFormat(RatioMask.cycleFormat(formatIndex));
        save();
        return true;
    }

    /**
     * Dials: exposure, as in BetterManual (Martin Tofall, MIT licence).
     * The actual change then arrives through the listeners, which update the display.
     */
    @Override
    protected boolean onDialTurned(int dial, int direction) {
        if (filmMenu >= 0) return moveFilmMenu(direction > 0 ? 1 : -1);
        if (camera == null) return true;
        try {
            switch (dialSetting(dial)) {
                case SET_SHUTTER:
                    if (direction > 0) camera.incrementShutterSpeed(); else camera.decrementShutterSpeed();
                    break;
                case SET_APERTURE:
                    if (direction > 0) camera.incrementAperture(); else camera.decrementAperture();
                    break;
                case SET_EV:
                    changeExposureCompensation(direction);
                    break;
                case SET_ISO:
                    changeIso(direction);
                    break;
            }
        } catch (Throwable e) {
            Log.write("Dial " + dial + ": " + e, e);
            showMessage("Dial: error " + e.getClass().getSimpleName());
        }
        return true;
    }

    private static final int SET_SHUTTER = 0, SET_APERTURE = 1, SET_EV = 2, SET_ISO = 3;

    /** What each dial sets in the current mode (in A, P or S, shutter speed or aperture is automatic). */
    private int dialSetting(int dial) {
        // Measured on an a7R: DIAL_1 = front dial, DIAL_2 = rear dial,
        // DIAL_3 = control wheel. As with the stock functions: front shutter speed, rear aperture,
        // control wheel ISO. Compensation has its own dial (±3).
        if (dial == DIAL_3 || dial == WHEEL) return SET_ISO;
        boolean front = dial == DIAL_1;
        if (MODE_M.equals(sceneMode)) return front ? SET_SHUTTER : SET_APERTURE;
        if (MODE_A.equals(sceneMode)) return SET_APERTURE;
        if (MODE_S.equals(sceneMode)) return SET_SHUTTER;
        return SET_ISO;
    }

    private void changeExposureCompensation(int direction) {
        android.hardware.Camera.Parameters params = camera.getNormalCamera().getParameters();
        setExposureCompensation(params, params.getExposureCompensation() + direction);
    }

    private void setExposureCompensation(android.hardware.Camera.Parameters params, int requested) {
        int value = Math.max(params.getMinExposureCompensation(),
                Math.min(params.getMaxExposureCompensation(), requested));
        android.hardware.Camera.Parameters change = camera.createEmptyParameters();
        change.setExposureCompensation(value);
        camera.getNormalCamera().setParameters(change);
        evText = ExposureText.ev(value * params.getExposureCompensationStep());
        updateExposureInfo();
    }

    /** Next or previous ISO in the camera's list, AUTO included. */
    @SuppressWarnings("unchecked")
    private void changeIso(int direction) {
        android.hardware.Camera.Parameters params = camera.getNormalCamera().getParameters();
        java.util.List<Integer> supported = (java.util.List<Integer>) camera.createParametersModifier(params).getSupportedISOSensitivities();
        java.util.List<Integer> isos = new java.util.ArrayList<Integer>();
        isos.add(0);
        if (supported != null)
            for (Integer iso : supported)
                if (iso != null && iso > 0) isos.add(iso);
        // All values in 1/3 EV steps, even if the camera only advertises some of them.
        for (int iso : STANDARD_ISOS)
            if (!isos.contains(iso)) isos.add(iso);
        java.util.Collections.sort(isos);
        Log.write("ISO: camera " + supported + ", setting " + isoSetting);
        int index = nearestIso(isos, isoSetting);
        // One click = one value, whatever number of clicks the dial reports.
        int next = Math.max(0, Math.min(isos.size() - 1, index + (direction > 0 ? 1 : -1)));
        isoSetting = isos.get(next);
        android.hardware.Camera.Parameters change = camera.createEmptyParameters();
        camera.createParametersModifier(change).setISOSensitivity(isoSetting);
        camera.getNormalCamera().setParameters(change);
        // Value actually kept by the camera (it may refuse a value it does not support).
        int actual = camera.createParametersModifier(camera.getNormalCamera().getParameters()).getISOSensitivity();
        if (actual != isoSetting) {
            Log.write("ISO: requested " + isoSetting + ", got " + actual);
            isoSetting = actual;
        }
        updateExposureInfo();
        showMessage(ExposureText.iso(isoSetting, 0));
    }

    /** Index of the nearest value in the list (AUTO = 0 first). */
    private static int nearestIso(java.util.List<Integer> isos, int iso) {
        if (iso <= 0) return 0;
        int best = 1;
        for (int i = 1; i < isos.size(); i++)
            if (Math.abs(isos.get(i) - iso) < Math.abs(isos.get(best) - iso)) best = i;
        return best;
    }

    private static final int[] STANDARD_ISOS = { 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250,
            1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600 };

    @Override
    protected boolean onLeftKeyDown() {
        if (filmMenu >= 0) return moveFilmMenu(-1);
        return changeFormat(-1);
    }

    @Override
    protected boolean onRightKeyDown() {
        if (filmMenu >= 0) return moveFilmMenu(1);
        return changeFormat(1);
    }

    /** Opens the film menu; a second press confirms the highlighted film. */
    private boolean openOrChooseFilm() {
        if (filmMenu >= 0) {
            closeFilmMenu(true);
        } else {
            overlay.setVisibility(View.VISIBLE);
            filmMenu = film == null ? 0 : java.util.Arrays.asList(FilmLook.FILMS).indexOf(film) + 1;
            overlay.setFilmMenu(filmMenu);
        }
        return true;
    }

    private boolean moveFilmMenu(int direction) {
        int rows = FilmLook.FILMS.length + 1;
        filmMenu = ((filmMenu + direction) % rows + rows) % rows;
        overlay.setFilmMenu(filmMenu);
        return true;
    }

    /** Closes the menu; with choose, the highlighted film becomes the current film (preview, XMP of the next photos). */
    private void closeFilmMenu(boolean choose) {
        if (filmMenu < 0) return;
        if (choose) {
            film = filmMenu == 0 ? null : FilmLook.FILMS[filmMenu - 1];
            overlay.setFilm(film);
            applyFilmPreview(film);
            showMessage(film != null ? "Film: " + film.name : "No film");
            save();
        }
        filmMenu = -1;
        overlay.setFilmMenu(-1);
    }

    /**
     * Film preview in live view using the camera's settings: contrast, saturation, white balance
     * shift, black and white effect. They also apply to JPEGs, not to RAWs (the RAW gets the XMP).
     * The original settings are recorded first and restored when choosing "None" or leaving the app.
     */
    private void applyFilmPreview(FilmLook look) {
        if (camera == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(KEY_ORIGINAL_LOOK, null);
        try {
            android.hardware.Camera.Parameters params = camera.getNormalCamera().getParameters();
            CameraEx.ParametersModifier current = camera.createParametersModifier(params);
            if (saved == null && look == null) return;
            if (saved == null) {
                saved = current.getContrast() + "," + current.getSaturation() + "," + (current.getWhiteBalanceShiftMode() ? 1 : 0)
                        + "," + current.getWhiteBalanceShiftLB() + "," + current.getWhiteBalanceShiftCC() + "," + params.getColorEffect();
                prefs.edit().putString(KEY_ORIGINAL_LOOK, saved).commit();
                Log.write("Film: original settings " + saved + ", contrast " + current.getMinContrast() + ".." + current.getMaxContrast()
                        + ", saturation " + current.getMinSaturation() + ".." + current.getMaxSaturation()
                        + ", shift A-B " + current.getMinWhiteBalanceShiftLB() + ".." + current.getMaxWhiteBalanceShiftLB()
                        + ", G-M " + current.getMinWhiteBalanceShiftCC() + ".." + current.getMaxWhiteBalanceShiftCC()
                        + ", effects " + params.getSupportedColorEffects());
            }
            String[] o = saved.split(",", 6);
            android.hardware.Camera.Parameters change = camera.createEmptyParameters();
            CameraEx.ParametersModifier m = camera.createParametersModifier(change);
            java.util.List<String> effects = params.getSupportedColorEffects();
            if (look == null) {
                m.setContrast(Integer.parseInt(o[0]));
                m.setSaturation(Integer.parseInt(o[1]));
                m.setWhiteBalanceShiftMode("1".equals(o[2]));
                m.setWhiteBalanceShiftLB(Integer.parseInt(o[3]));
                m.setWhiteBalanceShiftCC(Integer.parseInt(o[4]));
                if (!"null".equals(o[5]) && effects != null && effects.contains(o[5])) change.setColorEffect(o[5]);
            } else {
                m.setContrast(clamp(look.contrast, current.getMinContrast(), current.getMaxContrast()));
                boolean mono = look.mono && effects != null && effects.contains("mono");
                m.setSaturation(look.mono && !mono ? current.getMinSaturation()
                        : clamp(look.saturation, current.getMinSaturation(), current.getMaxSaturation()));
                boolean shift = look.shiftAmberBlue != 0 || look.shiftGreenMagenta != 0;
                if (shift || current.getWhiteBalanceShiftMode()) {
                    m.setWhiteBalanceShiftMode(true);
                    m.setWhiteBalanceShiftLB(clamp(look.shiftAmberBlue, current.getMinWhiteBalanceShiftLB(), current.getMaxWhiteBalanceShiftLB()));
                    m.setWhiteBalanceShiftCC(clamp(look.shiftGreenMagenta, current.getMinWhiteBalanceShiftCC(), current.getMaxWhiteBalanceShiftCC()));
                }
                if (effects != null && effects.contains("none")) change.setColorEffect(mono ? "mono" : "none");
            }
            camera.getNormalCamera().setParameters(change);
            if (look == null) prefs.edit().remove(KEY_ORIGINAL_LOOK).commit();
            Log.write("Film: preview " + (look != null ? look.name : "removed"));
        } catch (Throwable e) {
            Log.write("Film: preview failed: " + e, e);
        }
    }

    private static int clamp(int value, int min, int max) {
        return min <= max ? Math.max(min, Math.min(max, value)) : value;
    }

    @Override
    protected boolean onUpKeyDown() {
        if (filmMenu >= 0) return moveFilmMenu(-1);
        overlay.setOpacity(overlay.getOpacity() + OPACITY_STEP);
        showMessage("Opacity " + overlay.getOpacity() + "%");
        save();
        return true;
    }

    @Override
    protected boolean onDownKeyDown() {
        if (filmMenu >= 0) return moveFilmMenu(1);
        overlay.setOpacity(overlay.getOpacity() - OPACITY_STEP);
        showMessage("Opacity " + overlay.getOpacity() + "%");
        save();
        return true;
    }

    @Override
    protected boolean onEnterKeyDown() {
        if (filmMenu >= 0) {
            closeFilmMenu(true);
            return true;
        }
        overlay.setVisibility(overlay.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
        return true;
    }

    @Override
    protected boolean onPlayKeyUp() {
        if (!takingPicture)
            startActivity(new Intent(this, PlaybackActivity.class));
        return true;
    }

    @Override
    protected boolean onFocusKeyDown() {
        closeFilmMenu(false);
        if (camera == null) return true;
        if (!takingPicture) {
            camera.getNormalCamera().autoFocus(null);
            cropWriter.refreshSnapshot();
        }
        return true;
    }

    @Override
    protected boolean onFocusKeyUp() {
        if (camera == null) return true;
        if (!takingPicture) {
            camera.getNormalCamera().cancelAutoFocus();
            overlay.setFocus(OverlayView.FOCUS_NONE);
        }
        return true;
    }

    @Override
    protected boolean onShutterKeyDown() {
        if (camera == null) return true;
        if (!takingPicture) {
            takingPicture = true;
            // Fire first: nothing must delay the photo.
            camera.burstableTakePicture();
            cropFormat = wantsCrop() ? RatioMask.FORMATS[formatIndex]
                    : film != null ? RatioMask.FORMATS[RatioMask.indexOf("3:2")] : null;
            cropFilm = film;
            closeFilmMenu(false);
        }
        return true;
    }

    @Override
    protected boolean onShutterKeyUp() {
        // The shot ends in onShutter, once the image has been handed over for saving.
        return true;
    }

    @Override
    public void onShutter(int status, CameraEx cameraEx) {
        cameraEx.cancelTakePicture();
        takingPicture = false;
        overlay.setFocus(OverlayView.FOCUS_NONE);
        // Every shot enters the queue, even without a crop, to keep the photos in order.
        cropWriter.writeAfterShot(cropFormat, cropFilm);
        cropFormat = null;
        cropFilm = null;
    }

    /** No crop in 3:2 or when the bars are hidden. */
    private boolean wantsCrop() {
        return overlay.getVisibility() == View.VISIBLE
                && !XmpCrop.forRatio(RatioMask.SENSOR_RATIO, RatioMask.FORMATS[formatIndex].ratio).isFull();
    }

    @Override
    public void onCropResult(String message) {
        showMessage(message);
        updateRemaining();
    }

    @Override
    protected void setColorDepth(boolean highQuality) {
        // As in PMCADemo: reduced colour depth during live view.
        super.setColorDepth(false);
    }

    /** On a crash, writes the error to DARKFRAME/LOG.TXT on the SD card. */
    private void installCrashLog() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                Log.write("Crash: " + error, error);
                if (previous != null)
                    previous.uncaughtException(thread, error);
            }
        });
    }
}
