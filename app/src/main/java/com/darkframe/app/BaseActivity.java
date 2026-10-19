package com.darkframe.app;

import android.app.Activity;
import android.content.Intent;
import android.view.KeyEvent;
import com.github.ma1co.openmemories.framework.DisplayManager;
import com.sony.scalar.sysutil.KeyStatus;
import com.sony.scalar.sysutil.ScalarInput;

/**
 * Common base: displays, color depth and camera keys.
 * Adapted from BaseActivity in PMCADemo (ma1co, MIT licence).
 */
public class BaseActivity extends Activity implements DisplayManager.Listener {
    private DisplayManager displayManager;

    @Override
    protected void onResume() {
        super.onResume();
        displayManager = DisplayManager.create(this);
        displayManager.addListener(this);
        setColorDepth(true);
        notifyAppInfo();
    }

    @Override
    protected void onPause() {
        super.onPause();
        setColorDepth(false);
        displayManager.release();
        displayManager = null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (event.getScanCode()) {
            case ScalarInput.ISV_KEY_UP:
                return onUpKeyDown();
            case ScalarInput.ISV_KEY_DOWN:
                return onDownKeyDown();
            case ScalarInput.ISV_KEY_LEFT:
                return onLeftKeyDown();
            case ScalarInput.ISV_KEY_RIGHT:
                return onRightKeyDown();
            case ScalarInput.ISV_KEY_ENTER:
                return onEnterKeyDown();
            case ScalarInput.ISV_KEY_S1_1:
                return onFocusKeyDown();
            case ScalarInput.ISV_KEY_S1_2:
                return true;
            case ScalarInput.ISV_KEY_S2:
                return onShutterKeyDown();
            case ScalarInput.ISV_KEY_PLAY:
                return true;
            case ScalarInput.ISV_KEY_CUSTOM1:
                return onC1KeyDown();
            case ScalarInput.ISV_KEY_CUSTOM2:
                return onC2KeyDown();
            case ScalarInput.ISV_KEY_CUSTOM3:
                return onC3KeyDown();
            case ScalarInput.ISV_KEY_DELETE:
            case ScalarInput.ISV_KEY_SK2:
                return true;
            case ScalarInput.ISV_KEY_FN:
                return onFnKeyDown();
            case KEY_MENU:
                return onMenuKeyDown();
            // Dials: direction and number of clicks read as in PMCADemo (status / 22), otherwise from the code.
            case ScalarInput.ISV_DIAL_1_CLOCKWISE:
            case ScalarInput.ISV_DIAL_1_COUNTERCW:
                return dial(DIAL_1, event, ScalarInput.ISV_DIAL_1_STATUS, ScalarInput.ISV_DIAL_1_CLOCKWISE);
            case ScalarInput.ISV_DIAL_2_CLOCKWISE:
            case ScalarInput.ISV_DIAL_2_COUNTERCW:
                return dial(DIAL_2, event, ScalarInput.ISV_DIAL_2_STATUS, ScalarInput.ISV_DIAL_2_CLOCKWISE);
            case ScalarInput.ISV_DIAL_3_CLOCKWISE:
            case ScalarInput.ISV_DIAL_3_COUNTERCW:
                return dial(DIAL_3, event, ScalarInput.ISV_DIAL_3_STATUS, ScalarInput.ISV_DIAL_3_CLOCKWISE);
            case ScalarInput.ISV_DIAL_KURU_CLOCKWISE:
            case ScalarInput.ISV_DIAL_KURU_COUNTERCW:
                return dial(WHEEL, event, ScalarInput.ISV_DIAL_KURU_STATUS, ScalarInput.ISV_DIAL_KURU_CLOCKWISE);
            // Mode dial (M, A, S, P...) and exposure compensation dial: read their position.
            case ScalarInput.ISV_KEY_MODE_DIAL:
                Log.write("Mode dial: " + keyStatus(ScalarInput.ISV_KEY_MODE_DIAL));
                return onModeDialChanged(keyStatus(ScalarInput.ISV_KEY_MODE_DIAL));
            case ScalarInput.ISV_KEY_EV_COMPENSATION:
                Log.write("EV dial: " + keyStatus(ScalarInput.ISV_KEY_EV_COMPENSATION));
                return onEvDialChanged(keyStatus(ScalarInput.ISV_KEY_EV_COMPENSATION));
            default:
                Log.write("Unhandled key: code " + event.getScanCode() + " (keyCode " + keyCode + ")");
                onUnknownKey(event.getScanCode());
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (event.getScanCode()) {
            case ScalarInput.ISV_KEY_S1_1:
                return onFocusKeyUp();
            case ScalarInput.ISV_KEY_S2:
                return onShutterKeyUp();
            case ScalarInput.ISV_KEY_PLAY:
                return onPlayKeyUp();
            case ScalarInput.ISV_KEY_DELETE:
            case ScalarInput.ISV_KEY_SK2:
                onBackPressed();
                return true;
            case ScalarInput.ISV_KEY_UP:
            case ScalarInput.ISV_KEY_DOWN:
            case ScalarInput.ISV_KEY_LEFT:
            case ScalarInput.ISV_KEY_RIGHT:
            case ScalarInput.ISV_KEY_ENTER:
            case ScalarInput.ISV_KEY_S1_2:
            case ScalarInput.ISV_KEY_CUSTOM1:
            case ScalarInput.ISV_KEY_CUSTOM2:
            case ScalarInput.ISV_KEY_CUSTOM3:
            case ScalarInput.ISV_DIAL_1_CLOCKWISE:
            case ScalarInput.ISV_DIAL_2_CLOCKWISE:
            case ScalarInput.ISV_DIAL_3_CLOCKWISE:
            case ScalarInput.ISV_DIAL_KURU_CLOCKWISE:
            case ScalarInput.ISV_DIAL_1_COUNTERCW:
            case ScalarInput.ISV_DIAL_2_COUNTERCW:
            case ScalarInput.ISV_DIAL_3_COUNTERCW:
            case ScalarInput.ISV_DIAL_KURU_COUNTERCW:
            case ScalarInput.ISV_KEY_FN:
            case KEY_MENU:
            case ScalarInput.ISV_KEY_MODE_DIAL:
            case ScalarInput.ISV_KEY_EV_COMPENSATION:
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    protected boolean onUpKeyDown() { return false; }
    protected boolean onDownKeyDown() { return false; }
    protected boolean onLeftKeyDown() { return false; }
    protected boolean onRightKeyDown() { return false; }
    protected boolean onEnterKeyDown() { return false; }
    protected boolean onFocusKeyDown() { return false; }
    protected boolean onFocusKeyUp() { return false; }
    protected boolean onShutterKeyDown() { return false; }
    protected boolean onShutterKeyUp() { return false; }
    protected boolean onPlayKeyUp() { return false; }
    protected boolean onC1KeyDown() { return false; }
    protected boolean onC2KeyDown() { return false; }
    protected boolean onC3KeyDown() { return false; }
    /** Dials: DIAL_1, DIAL_2 and DIAL_3 for the top dials, WHEEL for the control wheel. */
    protected static final int DIAL_1 = 1, DIAL_2 = 2, DIAL_3 = 3, WHEEL = 0;

    protected boolean onDialTurned(int dial, int direction) { return false; }
    protected boolean onFnKeyDown() { return false; }
    /** Menu key of the a7R (code measured on an a7R). */
    protected static final int KEY_MENU = 514;
    protected boolean onMenuKeyDown() { return false; }
    /** status: mode dial position, ScalarInput.ISV_KEY_MODE_xxx. */
    protected boolean onModeDialChanged(int status) { return false; }
    /** status: raw position of the exposure compensation dial. */
    protected boolean onEvDialChanged(int status) { return false; }
    protected void onUnknownKey(int scanCode) {}

    private boolean dial(int dial, KeyEvent event, int statusKey, int clockwiseCode) {
        int steps = keyStatus(statusKey) / 22;
        if (steps == 0 || steps == KeyStatus.STATUS_INVALID / 22)
            steps = event.getScanCode() == clockwiseCode ? 1 : -1;
        Log.write("Dial " + dial + ": code " + event.getScanCode() + ", steps " + steps);
        return onDialTurned(dial, steps);
    }

    /** Status of a key or dial, or KeyStatus.STATUS_INVALID if the camera does not report it. */
    protected static int keyStatus(int key) {
        try {
            KeyStatus status = ScalarInput.getKeyStatus(key);
            return status != null ? status.status : KeyStatus.STATUS_INVALID;
        } catch (Throwable e) {
            return KeyStatus.STATUS_INVALID;
        }
    }

    @Override
    public void displayChanged(DisplayManager.Display display) {}

    public DisplayManager getDisplayManager() {
        return displayManager;
    }

    /** Physical pixel width divided by its height on the active display (rear screen or viewfinder). */
    protected float getPixelAspect() {
        if (displayManager == null) return 1f;
        float physical = displayManager.getActiveDisplayInfo().aspectRatio;
        float frameBuffer = displayManager.getFrameBufferInfo().aspectRatio;
        return physical > 0 && frameBuffer > 0 ? physical / frameBuffer : 1f;
    }

    protected void setColorDepth(boolean highQuality) {
        displayManager.setColorDepth(highQuality ? DisplayManager.ColorDepth.HIGH : DisplayManager.ColorDepth.LOW);
    }

    /**
     * Registers the app with the camera system (required to receive keys).
     * As in BetterManual (MIT licence): the app is restarted on wake after sleep or power-off,
     * and exits when the USB cable is plugged in.
     */
    protected void notifyAppInfo() {
        Intent intent = new Intent("com.android.server.DAConnectionManagerService.AppInfoReceive");
        intent.putExtra("package_name", getComponentName().getPackageName());
        // Always the live view screen: it is the only activity the system can restart.
        // Announcing PlaybackActivity froze the camera on power-on (black screen, dead keys).
        intent.putExtra("class_name", MainActivity.class.getName());
        intent.putExtra("pullingback_key", new String[] { "KEY_USB_CONNECT" });
        intent.putExtra("resume_key", new String[] { "KEY_POWER_SLIDE_PON", "KEY_RELEASE_APO", "KEY_PLAY_APO",
                "KEY_MEDIA_INOUT_APO", "KEY_LENS_APO", "KEY_ACCESSORY_APO", "KEY_DEDICATED_APO", "KEY_POWER_APO",
                "KEY_PLAY_PON" });
        sendBroadcast(intent);
    }

    /** When quitting on purpose (trash key), the app must no longer be restarted on wake. */
    protected void forgetAppInfo() {
        Intent intent = new Intent("com.android.server.DAConnectionManagerService.AppInfoReceive");
        intent.putExtra("package_name", getComponentName().getPackageName());
        // Always the live view screen: it is the only activity the system can restart.
        // Announcing PlaybackActivity froze the camera on power-on (black screen, dead keys).
        intent.putExtra("class_name", MainActivity.class.getName());
        intent.putExtra("pullingback_key", new String[] {});
        intent.putExtra("resume_key", new String[] {});
        sendBroadcast(intent);
    }
}
