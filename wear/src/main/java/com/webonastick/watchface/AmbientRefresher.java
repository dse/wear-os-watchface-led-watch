package com.webonastick.watchface;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import static android.app.AlarmManager.RTC_WAKEUP;

public class AmbientRefresher {
    private int seconds = 10;
    private static final String AMBIENT_UPDATE_ACTION = "com.webonastick.watchface.action.AMBIENT_UPDATE";

    private static final String TAG = "AmbientRefresher";

    private Intent intent = null;
    private PendingIntent pendingIntent = null;
    private BroadcastReceiver broadcastReceiver = null;
    private AlarmManager alarmManager = null;
    private IntentFilter intentFilter = null;
    private boolean receiverRegistered = false;

    private Handler handler = null;
    private Runnable runnable = null;
    private ContextWrapper contextWrapper = null;

    public AmbientRefresher(ContextWrapper contextWrapper, Runnable runnable) {
        this.contextWrapper = contextWrapper;
        this.runnable = runnable;
    }

    private void handle() {
        if (alarmManager == null) {
            alarmManager = (AlarmManager) contextWrapper.getSystemService(Context.ALARM_SERVICE);
            intent = new Intent(AMBIENT_UPDATE_ACTION);
            pendingIntent = PendingIntent.getBroadcast(
                    contextWrapper.getBaseContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT
            );
            broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (runnable != null) {
                        runnable.run();
                    }
                    handle();
                }
            };
            intentFilter = new IntentFilter(AMBIENT_UPDATE_ACTION);
        }
        if (!receiverRegistered) {
            contextWrapper.registerReceiver(broadcastReceiver, intentFilter);
            receiverRegistered = true;
        }
        long timeMs = System.currentTimeMillis();
        long delayMs = (seconds * 1000) - timeMs % (seconds * 1000);
        long triggerTimeMs = timeMs + delayMs;
        alarmManager.setExact(RTC_WAKEUP, triggerTimeMs, pendingIntent);
    }

    public void start() {
        handle();
    }

    public void stop() {
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
        if (receiverRegistered) {
            contextWrapper.unregisterReceiver(broadcastReceiver);
            receiverRegistered = false;
        }
    }
}
