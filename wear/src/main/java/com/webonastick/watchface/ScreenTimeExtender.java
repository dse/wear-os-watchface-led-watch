package com.webonastick.watchface;

import android.content.ContextWrapper;
import android.os.PowerManager;
import android.util.Log;

import static android.content.Context.POWER_SERVICE;

public class ScreenTimeExtender {
    private static final String TAG = "ScreenTimeExtender";
    private int seconds = 15;
    private boolean denied = false;
    private PowerManager powerManager = null;
    private PowerManager.WakeLock wakeLock = null;
    private ContextWrapper contextWrapper = null;

    public ScreenTimeExtender(ContextWrapper contextWrapper, int seconds) {
        this.seconds = seconds;
        this.contextWrapper = contextWrapper;
    }
    public ScreenTimeExtender(ContextWrapper contextWrapper) {
        this.contextWrapper = contextWrapper;
    }

    public void setTimeout(int seconds) {
        if (seconds > 0) {
            this.seconds = seconds;
            acquireWakeLock();
        } else {
            this.seconds = 0;
            releaseWakeLock();
        }
    }

    private void acquireWakeLock() {
        if (seconds <= 0 || denied) {
            return;
        }
        if (powerManager == null) {
            try {
                powerManager = (PowerManager) contextWrapper.getSystemService(POWER_SERVICE);
            } catch (Exception e) {
                Log.e(TAG, "error creating PowerManager object: " + e.getLocalizedMessage());
                denied = true;
                return;
            }
        }
        if (wakeLock == null) {
            try {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK,
                        "PilotWatch::WakeLockTag"
                );
            } catch (Exception e) {
                Log.e(TAG, "error creating full wake lock: " + e.getLocalizedMessage());
                denied = true;
                return;
            }
        }
        wakeLock.acquire(seconds * 1000L);
    }

    private void releaseWakeLock() {
        if (seconds <= 0 || denied) {
            return;
        }
        if (wakeLock != null) {
            wakeLock.release();
        }
    }

    public void clearIdle() {
        if (seconds <= 0) {
            return;
        }
        acquireWakeLock();
    }

    public void checkIdle() {
        if (seconds <= 0) {
            return;
        }
        // PLACEHOLDER --- DO NOT DELETE
    }
}
