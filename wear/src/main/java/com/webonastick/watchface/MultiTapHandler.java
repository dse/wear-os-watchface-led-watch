package com.webonastick.watchface;

import android.os.Handler;

public class MultiTapHandler {

    // Windows default double-tap threshold.
    public static final int MULTI_TAP_THRESHOLD_MS = 500;

    private boolean mClosed = false;
    private int mTapType = -1;
    private int mNumberOfTaps = -1;
    private Handler mHandler;
    private Runnable mRunnable;
    private int mThresholdMs = MULTI_TAP_THRESHOLD_MS;
    private MultiTapEventHandler mEventHandler;

    public MultiTapHandler(MultiTapEventHandler eventHandler) {
        mEventHandler = eventHandler;
        mRunnable = new Runnable() {
            @Override
            public void run() {
                int tapType = mTapType;
                int numberOfTaps = mNumberOfTaps;
                mTapType = -1;
                mNumberOfTaps = -1;
                mEventHandler.onMultiTapCommand(tapType, numberOfTaps);
            }
        };
        mHandler = new Handler();
    }

    public void onTapEvent(int tapType) {
        if (mClosed) {
            return;
        }
        if (tapType == mTapType) {
            mNumberOfTaps += 1;
        } else {
            mTapType = tapType;
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
            mTapType = -1;
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
