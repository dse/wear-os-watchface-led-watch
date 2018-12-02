package com.webonastick.ledwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class LEDWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. Defaults to one second
     * because the watch face needs to update seconds in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1) / 2;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<LEDWatchFace.Engine> mWeakReference;

        public EngineHandler(LEDWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            LEDWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;

        private Paint mBackgroundPaint;
        private Paint mBackgroundBitmapPaint;

        private float mXOffsetMiddle;
        private float mYOffsetMiddle;
        private float mXOffsetTopLeft;
        private float mYOffsetTopLeft;
        private float mXOffsetTopRight;
        private float mYOffsetTopRight;
        private float mXOffsetBottomLeft;
        private float mYOffsetBottomLeft;
        private float mXOffsetBottomRight;
        private float mYOffsetBottomRight;

        private float mLineSpacing;

        private Paint mTextPaintMiddle;
        private Paint mTextPaintTopLeft;
        private Paint mTextPaintTopRight;
        private Paint mTextPaintBottomLeft;
        private Paint mTextPaintBottomRight;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean mAmbient;

        private Resources mResources; // FIXME: can be deleted?
        private Typeface mSevenSegmentTypeface;
        private Typeface mFourteenSegmentTypeface;

        private boolean mAutoPosition = true;
        private boolean mAutoTextSize = true;
        private boolean mDefaultIs24Hour = true;

        private int mSurfaceWidth;
        private int mSurfaceHeight;

        private static final String TAG = "LEDWatchFace";

        private int mSegmentsAlpha;

        Bitmap mBackgroundBitmap;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(LEDWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .build());

            mCalendar = Calendar.getInstance();

            Resources resources = LEDWatchFace.this.getResources();
            if (mAutoPosition) {
                // do nothing
            } else {
                mYOffsetMiddle = resources.getDimension(R.dimen.digital_y_offset);
            }
            mLineSpacing = resources.getDimension(R.dimen.line_spacing);

            mSegmentsAlpha = resources.getInteger(R.integer.segments_alpha_opacity);

            // Initializes background.
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.background));

            resources = LEDWatchFace.this.getResources();
            mSevenSegmentTypeface = Typeface.createFromAsset(
                    resources.getAssets(), "fonts/DSEG7ClassicMini-Italic.ttf"
            );
            mFourteenSegmentTypeface = Typeface.createFromAsset(
                    resources.getAssets(), "fonts/DSEG14ClassicMini-Italic.ttf"
            );

            // Initializes Watch Face.
            mTextPaintMiddle = new Paint();
            mTextPaintMiddle.setTypeface(mSevenSegmentTypeface);
            mTextPaintMiddle.setTextAlign(Paint.Align.CENTER);

            mTextPaintTopLeft = new Paint();
            mTextPaintTopLeft.setTypeface(mFourteenSegmentTypeface);
            mTextPaintTopLeft.setTextAlign(Paint.Align.RIGHT);

            mTextPaintTopRight = new Paint();
            mTextPaintTopRight.setTypeface(mSevenSegmentTypeface);
            mTextPaintTopRight.setTextAlign(Paint.Align.LEFT);

            mTextPaintBottomLeft = new Paint();
            mTextPaintBottomLeft.setTypeface(mFourteenSegmentTypeface);
            mTextPaintBottomLeft.setTextAlign(Paint.Align.RIGHT);

            mTextPaintBottomRight = new Paint();
            mTextPaintBottomRight.setTypeface(mSevenSegmentTypeface);
            mTextPaintBottomRight.setTextAlign(Paint.Align.LEFT);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            LEDWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            LEDWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = LEDWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            DisplayMetrics metrics = resources.getDisplayMetrics();
            mSurfaceWidth = metrics.widthPixels;
            mSurfaceHeight = metrics.heightPixels;

            if (mAutoPosition) {
                mXOffsetMiddle = Math.round(mSurfaceWidth / 2f);
                mXOffsetTopLeft = Math.round(mSurfaceWidth / 2f);
                mXOffsetTopRight = Math.round(mSurfaceWidth / 2f);
                mXOffsetBottomLeft = Math.round(mSurfaceWidth / 2f);
                mXOffsetBottomRight = Math.round(mSurfaceWidth / 2f);
            } else {
                mXOffsetMiddle = resources.getDimension(isRound ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
                mXOffsetTopLeft = mXOffsetMiddle;
                mXOffsetTopRight = mXOffsetMiddle;
                mXOffsetBottomLeft = mXOffsetMiddle;
                mXOffsetBottomRight = mXOffsetMiddle;
            }

            float textSize;

            if (mAutoTextSize) {
                Rect bounds = new Rect();
                mTextPaintMiddle.setTextSize(mSurfaceWidth);
                mTextPaintMiddle.getTextBounds("00:00", 0, 5, bounds);
                textSize = (float) Math.floor(mSurfaceWidth * (isRound ? 0.85f : 0.9f)
                        / (bounds.right - bounds.left)
                        * (bounds.bottom - bounds.top));
            } else {
                textSize = resources.getDimension(
                        isRound ? R.dimen.digital_text_size_round : R.dimen.digital_text_size
                );
            }

            mTextPaintMiddle.setTextSize(textSize);
            mTextPaintTopLeft.setTextSize(Math.round(textSize / 2f));
            mTextPaintTopRight.setTextSize(Math.round(textSize / 2f));
            mTextPaintBottomLeft.setTextSize(Math.round(textSize / 2f));
            mTextPaintBottomRight.setTextSize(Math.round(textSize / 2f));

            if (mAutoPosition) {
                mYOffsetMiddle = Math.round(mSurfaceHeight / 2f + textSize / 2f);
                mYOffsetTopLeft = mYOffsetMiddle - textSize - mLineSpacing;
                mYOffsetTopRight = mYOffsetMiddle - textSize - mLineSpacing;
                mYOffsetBottomLeft = mYOffsetMiddle + Math.round(textSize / 2f) + mLineSpacing;
                mYOffsetBottomRight = mYOffsetMiddle + Math.round(textSize / 2f) + mLineSpacing;
            } else {
                mYOffsetMiddle = resources.getDimension(R.dimen.digital_y_offset);
                mYOffsetTopLeft = mYOffsetMiddle - textSize - mLineSpacing;
                mYOffsetTopRight = mYOffsetMiddle - textSize - mLineSpacing;
                mYOffsetBottomLeft = mYOffsetMiddle + Math.round(textSize / 2f) + mLineSpacing;
                mYOffsetBottomRight = mYOffsetMiddle + Math.round(textSize / 2f) + mLineSpacing;
            }

            mBackgroundBitmap = null;
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

            updateTextPaintProperties();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            mAmbient = inAmbientMode;
            updateTextPaintProperties();

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void updateTextPaintProperties() {
            if (mAmbient) {
                mTextPaintMiddle.setAntiAlias(false);
                mTextPaintMiddle.setColor(ContextCompat.getColor(getApplicationContext(), R.color.ambient_digital_text));
                mTextPaintTopLeft.setAntiAlias(false);
                mTextPaintTopLeft.setColor(ContextCompat.getColor(getApplicationContext(), R.color.ambient_digital_text));
                mTextPaintTopRight.setAntiAlias(false);
                mTextPaintTopRight.setColor(ContextCompat.getColor(getApplicationContext(), R.color.ambient_digital_text));
                mTextPaintBottomLeft.setAntiAlias(false);
                mTextPaintBottomLeft.setColor(ContextCompat.getColor(getApplicationContext(), R.color.ambient_digital_text));
                mTextPaintBottomRight.setAntiAlias(false);
                mTextPaintBottomRight.setColor(ContextCompat.getColor(getApplicationContext(), R.color.ambient_digital_text));
            } else {
                mTextPaintMiddle.setAntiAlias(true);
                mTextPaintMiddle.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaintTopLeft.setAntiAlias(true);
                mTextPaintTopLeft.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaintTopRight.setAntiAlias(true);
                mTextPaintTopRight.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaintBottomLeft.setAntiAlias(true);
                mTextPaintBottomLeft.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaintBottomRight.setAntiAlias(true);
                mTextPaintBottomRight.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
            }
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        Bitmap canvasBitmap;

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            String allSegmentsOnMiddle = ".88:88";
            String allSegmentsOnTopLeft = "~~~";
            String allSegmentsOnTopRight = "!88";
            String allSegmentsOnBottomLeft = "~~~";
            String allSegmentsOnBottomRight = "!88";

            if (!mAmbient && mBackgroundBitmap == null && mSegmentsAlpha > 8) {
                Log.d(TAG, "A");
                // Initializes background.
                mBackgroundBitmapPaint = new Paint();
                mBackgroundBitmapPaint.setColor(Color.BLACK);

                Canvas backgroundCanvas = new Canvas();
                mBackgroundBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
                backgroundCanvas.setBitmap(mBackgroundBitmap);
                backgroundCanvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

                mTextPaintMiddle.setAntiAlias(true);
                mTextPaintMiddle.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaintTopLeft.setAntiAlias(true);
                mTextPaintTopLeft.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaintTopRight.setAntiAlias(true);
                mTextPaintTopRight.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaintBottomLeft.setAntiAlias(true);
                mTextPaintBottomLeft.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaintBottomRight.setAntiAlias(true);
                mTextPaintBottomRight.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaintMiddle.setAlpha(mSegmentsAlpha);
                mTextPaintTopLeft.setAlpha(mSegmentsAlpha);
                mTextPaintTopRight.setAlpha(mSegmentsAlpha);
                mTextPaintBottomLeft.setAlpha(mSegmentsAlpha);
                mTextPaintBottomRight.setAlpha(mSegmentsAlpha);
                backgroundCanvas.drawText(allSegmentsOnMiddle, mXOffsetMiddle, mYOffsetMiddle, mTextPaintMiddle);
                backgroundCanvas.drawText(allSegmentsOnTopLeft, mXOffsetTopLeft, mYOffsetTopLeft, mTextPaintTopLeft);
                backgroundCanvas.drawText(allSegmentsOnTopRight, mXOffsetTopRight, mYOffsetTopRight, mTextPaintTopRight);
                backgroundCanvas.drawText(allSegmentsOnBottomLeft, mXOffsetBottomLeft, mYOffsetBottomLeft, mTextPaintBottomLeft);
                backgroundCanvas.drawText(allSegmentsOnBottomRight, mXOffsetBottomRight, mYOffsetBottomRight, mTextPaintBottomRight);
                mTextPaintMiddle.setAlpha(255);
                mTextPaintTopLeft.setAlpha(255);
                mTextPaintTopRight.setAlpha(255);
                mTextPaintBottomLeft.setAlpha(255);
                mTextPaintBottomRight.setAlpha(255);

                canvasBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
                canvas.setBitmap(canvasBitmap);
            }

            // Draw the background.
            if (mAmbient) {
                canvas.drawColor(Color.BLACK);
            } else if (!mAmbient && mBackgroundBitmap != null && mSegmentsAlpha > 8) {
                Log.d(TAG, "B");
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, null);
            } else {
                Log.d(TAG, "C");
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = LEDWatchFace.this.registerReceiver(null, ifilter);

            int batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int batteryScale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPercentage = Math.round(batteryLevel * 100f / batteryScale);

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            int hour12 = mCalendar.get(Calendar.HOUR);
            int hour24 = mCalendar.get(Calendar.HOUR_OF_DAY);
            int minute = mCalendar.get(Calendar.MINUTE);
            int second = mCalendar.get(Calendar.SECOND);
            int millis = mCalendar.get(Calendar.MILLISECOND);

            boolean isPM = mCalendar.get(Calendar.AM_PM) == Calendar.PM;
            boolean blink = millis >= 400;

            String textMiddle = null;
            String textTopLeft = null;
            String textTopRight = null;
            String textBottomLeft = null;
            String textBottomRight = null;


            boolean is24HourDF = DateFormat.is24HourFormat(LEDWatchFace.this);
            boolean is24Hour;
            int is24HourInt;
            try {
                is24HourInt = Settings.System.getInt(getContentResolver(), Settings.System.TIME_12_24);
            } catch (Settings.SettingNotFoundException e) {
                is24HourInt = -1;
            }
            if (is24HourInt == 24) {
                is24Hour = true;
            } else if (is24HourInt == 12) {
                is24Hour = false;
            } else {
                is24Hour = is24HourDF;
            }

            if (is24Hour) {
                textMiddle = String.format(Locale.getDefault(), "%02d:%02d", hour24, minute);
            } else {
                textMiddle = String.format(Locale.getDefault(), "%02d:%02d", hour12, minute);

                // replace leading zero with space (all-segments-off)
                if (textMiddle.charAt(0) == '0') {
                    textMiddle = "!" + textMiddle.substring(1);
                }

                // PM indicator
                if (isPM) {
                    textMiddle = "." + textMiddle; // always followed by blank or 1
                }
            }

            if (!mAmbient) {
                textBottomRight = String.format(Locale.getDefault(), "!%02d", second);
            }

            if (batteryPercentage < 0) {
                textBottomLeft = "???";
            } else if (batteryPercentage < 100) {
                textBottomLeft = String.format(Locale.getDefault(), "%2d%%", batteryPercentage);
            } else if (batteryPercentage < 1000) {
                textBottomLeft = String.format(Locale.getDefault(), "%3d", batteryPercentage);
            } else {
                textBottomLeft = "???";
            }

            textTopLeft = mCalendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
            if (textTopLeft.length() > 3) {
                textTopLeft = textTopLeft.substring(0, 3);
            }
            textTopLeft = textTopLeft.toUpperCase();

            textTopRight = String.format(Locale.getDefault(), "!%2d", mCalendar.get(Calendar.DAY_OF_MONTH));
            textTopRight = textTopRight.replace(" ", "!");

            if (blink && !mAmbient) {
                textMiddle = textMiddle.replace(':', ' ');
            }

            if (textMiddle != null) {
                canvas.drawText(textMiddle, mXOffsetMiddle, mYOffsetMiddle, mTextPaintMiddle);
            }
            if (textTopLeft != null) {
                canvas.drawText(textTopLeft, mXOffsetTopLeft, mYOffsetTopLeft, mTextPaintTopLeft);
            }
            if (textTopRight != null) {
                canvas.drawText(textTopRight, mXOffsetTopRight, mYOffsetTopRight, mTextPaintTopRight);
            }
            if (textBottomLeft != null) {
                canvas.drawText(textBottomLeft, mXOffsetBottomLeft, mYOffsetBottomLeft, mTextPaintBottomLeft);
            }
            if (textBottomRight != null) {
                canvas.drawText(textBottomRight, mXOffsetBottomRight, mYOffsetBottomRight, mTextPaintBottomRight);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
