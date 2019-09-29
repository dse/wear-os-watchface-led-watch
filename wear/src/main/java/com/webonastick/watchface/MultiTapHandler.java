package com.webonastick.watchface;

import android.os.Handler;

import androidx.annotation.Nullable;

public class MultiTapHandler<RegionType> {

    // Windows default double-tap threshold.
    public static final int MULTI_TAP_THRESHOLD_MS = 500;

    private boolean mClosed = false;
    private int mNumberOfTaps = -1;
    private Handler mHandler;
    private Runnable mRunnable;
    private int mThresholdMs = MULTI_TAP_THRESHOLD_MS;
    private MultiTapEventHandler mEventHandler;

    private @Nullable RegionType mRegion = null;

    public MultiTapHandler(MultiTapEventHandler eventHandler) {
        mEventHandler = eventHandler;
        mRunnable = new Runnable() {
            @Override
            public void run() {
                @Nullable RegionType region = mRegion;
                int numberOfTaps = mNumberOfTaps;
                mRegion = null;
                mNumberOfTaps = -1;
                mEventHandler.onMultiTapCommand(region, numberOfTaps);
            }
        };
        mHandler = new Handler();
    }

    public void onTapEvent(RegionType region) {
        if (mClosed) {
            return;
        }
        if (region == mRegion) {
            mNumberOfTaps += 1;
        } else {
            mRegion = region;
            mNumberOfTaps = 1;
        }
        schedule();
    }

    private void schedule() {
        if (mClosed) {
            return;
        }
        mHandler.removeCallbacks(mRunnable);
        mHandler.postDelayed(mRunnable, mThresholdMs);
    }

    public void cancel() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
            mRegion = null;
            mNumberOfTaps = -1;
        }
    }

    public void close() {
        cancel();
    }

    /**
     * Called before object removal from memory.
     */
    public void finalize() {
        close();
    }
}
