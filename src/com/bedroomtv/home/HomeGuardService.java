package com.bedroomtv.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.view.Display;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.HashSet;
import java.util.Set;

/** Replaces Google TV's home screen (or the setup-recovery home) with ours whenever it comes to the front. */
public class HomeGuardService extends AccessibilityService {

    private long lastLaunch;

    /** Apps whose screenshots come back black (DRM video): the panel skips the screenshot over them. */
    private final Set<String> unframeable = new HashSet<>();

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cls = event.getClassName();
        if (cls == null) return;
        // RecoveryActivity is the setup app's fallback home when Google TV's launcher is disabled.
        String name = cls.toString();
        if (!name.endsWith("HomeActivity") && !name.endsWith("RecoveryActivity")) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunch < 800) return;
        lastLaunch = now;

        // As HOME, so it returns to the home task rather than starting a second copy in a normal task.
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClass(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
    }

    /**
     * The remote's settings key used to open Google TV's panel; it now opens ours. The Haier remote
     * sends it as KEYCODE_NOTIFICATION (scan code 473) rather than KEYCODE_SETTINGS.
     */
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_SETTINGS && code != KeyEvent.KEYCODE_NOTIFICATION) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            // Over home (or the open panel, which then closes) there is no app to screenshot.
            if (MainActivity.inFront || QuickSettingsActivity.showing) openPanel(true);
            else captureThenOpenPanel();
        }
        return true;
    }

    /** Over another app: blur a screenshot of it for the panel's frosted glass (window blur is off on this TV). */
    private void captureThenOpenPanel() {
        String app = foregroundApp();
        PanelActivity.backdrop = null;
        if (app != null && unframeable.contains(app)) {
            openPanel(false);
            return;
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult result) {
                HardwareBuffer buffer = result.getHardwareBuffer();
                Bitmap shot = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                PanelActivity.backdrop = shot == null ? null : PanelActivity.blur(shot);
                buffer.close();
                if (PanelActivity.backdrop == null && app != null) unframeable.add(app);
                openPanel(false);
            }

            @Override
            public void onFailure(int errorCode) {
                PanelActivity.backdrop = null;
                openPanel(false);
            }
        });
    }

    /** The package of the focused app window, or null if it can't be told. */
    private String foregroundApp() {
        for (AccessibilityWindowInfo window : getWindows()) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused()) continue;
            AccessibilityNodeInfo root = window.getRoot();
            return root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
        }
        return null;
    }

    private void openPanel(boolean overHome) {
        startActivity(new Intent(this, QuickSettingsActivity.class)
                .putExtra(PanelActivity.EXTRA_OVER_HOME, overHome)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override
    public void onInterrupt() {
    }
}
