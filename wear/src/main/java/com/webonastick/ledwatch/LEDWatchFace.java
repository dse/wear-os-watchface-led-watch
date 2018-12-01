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

        private float mXOffset;
        private float mYOffset;
        private float mXOffsetTopLeft;
        private float mYOffsetTopLeft;
        private float mXOffsetTopRight;
        private float mYOffsetTopRight;
        private float mXOffsetBottomLeft;
        private float mYOffsetBottomLeft;
        private float mXOffsetBottomRight;
        private float mYOffsetBottomRight;

        private float lineSpacing;

        private Paint mTextPaint;
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

        private Resources resources;
        private Typeface sevenSegmentTypeface;
        private Typeface fourteenSegmentTypeface;

        private boolean autoPosition = true;
        private boolean autoTextSize = true;
        private boolean defaultIs24Hour = true;

        private int surfaceWidth;
        private int surfaceHeight;

        private static final String TAG = "LEDWatchFace";

        private int segmentsAlpha;

        private IntentFilter ifilter;
        private Intent batteryStatus;

        Bitmap mBackgroundBitmap;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(LEDWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .build());

            mCalendar = Calendar.getInstance();

            Resources resources = LEDWatchFace.this.getResources();
            if (autoPosition) {
                // do nothing
            } else {
                mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            }
            lineSpacing = resources.getDimension(R.dimen.line_spacing);

            segmentsAlpha = resources.getInteger(R.integer.segments_alpha_opacity);

            // Initializes background.
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.background));

            resources = LEDWatchFace.this.getResources();
            sevenSegmentTypeface = Typeface.createFromAsset(
                    resources.getAssets(), "fonts/DSEG7ClassicMini-Italic.ttf"
            );
            fourteenSegmentTypeface = Typeface.createFromAsset(
                    resources.getAssets(), "fonts/DSEG14ClassicMini-Italic.ttf"
            );

            // Initializes Watch Face.
            mTextPaint = new Paint();
            mTextPaint.setTypeface(sevenSegmentTypeface);
            mTextPaint.setTextAlign(Paint.Align.CENTER);

            mTextPaintTopLeft = new Paint();
            mTextPaintTopLeft.setTypeface(fourteenSegmentTypeface);
            mTextPaintTopLeft.setTextAlign(Paint.Align.RIGHT);

            mTextPaintTopRight = new Paint();
            mTextPaintTopRight.setTypeface(sevenSegmentTypeface);
            mTextPaintTopRight.setTextAlign(Paint.Align.LEFT);

            mTextPaintBottomLeft = new Paint();
            mTextPaintBottomLeft.setTypeface(fourteenSegmentTypeface);
            mTextPaintBottomLeft.setTextAlign(Paint.Align.RIGHT);

            mTextPaintBottomRight = new Paint();
            mTextPaintBottomRight.setTypeface(sevenSegmentTypeface);
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
            surfaceWidth = metrics.widthPixels;
            surfaceHeight = metrics.heightPixels;

            if (autoPosition) {
                mXOffset = Math.round(surfaceWidth / 2f);
                mXOffsetTopLeft = Math.round(surfaceWidth / 2f);
                mXOffsetTopRight = Math.round(surfaceWidth / 2f);
                mXOffsetBottomLeft = Math.round(surfaceWidth / 2f);
                mXOffsetBottomRight = Math.round(surfaceWidth / 2f);
            } else {
                mXOffset = resources.getDimension(
                        isRound ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset
                );
                mXOffsetTopLeft = mXOffset;
                mXOffsetTopRight = mXOffset;
                mXOffsetBottomLeft = mXOffset;
                mXOffsetBottomRight = mXOffset;
            }

            float textSize;

            if (autoTextSize) {
                Rect bounds = new Rect();
                mTextPaint.setTextSize(surfaceWidth);
                mTextPaint.getTextBounds("00:00", 0, 5, bounds);
                textSize = (float) Math.floor(surfaceWidth * (isRound ? 0.85f : 0.9f)
                        / (bounds.right - bounds.left)
                        * (bounds.bottom - bounds.top));
            } else {
                textSize = resources.getDimension(
                        isRound ? R.dimen.digital_text_size_round : R.dimen.digital_text_size
                );
            }

            mTextPaint.setTextSize(textSize);
            mTextPaintTopLeft.setTextSize(Math.round(textSize / 2f));
            mTextPaintTopRight.setTextSize(Math.round(textSize / 2f));
            mTextPaintBottomLeft.setTextSize(Math.round(textSize / 2f));
            mTextPaintBottomRight.setTextSize(Math.round(textSize / 2f));

            if (autoPosition) {
                mYOffset = Math.round(surfaceHeight / 2f + textSize / 2f);
                mYOffsetTopLeft = mYOffset - textSize - lineSpacing;
                mYOffsetTopRight = mYOffset - textSize - lineSpacing;
                mYOffsetBottomLeft = mYOffset + Math.round(textSize / 2f) + lineSpacing;
                mYOffsetBottomRight = mYOffset + Math.round(textSize / 2f) + lineSpacing;
            } else {
                mYOffset = resources.getDimension(R.dimen.digital_y_offset);
                mYOffsetTopLeft = mYOffset - textSize - lineSpacing;
                mYOffsetTopRight = mYOffset - textSize - lineSpacing;
                mYOffsetBottomLeft = mYOffset + Math.round(textSize / 2f) + lineSpacing;
                mYOffsetBottomRight = mYOffset + Math.round(textSize / 2f) + lineSpacing;
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
                mTextPaint.setAntiAlias(false);
                mTextPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.ambient_digital_text));
                mTextPaintTopLeft.setAntiAlias(false);
                mTextPaintTopLeft.setColor(ContextCompat.getColor(getApplicationContext(), R.color.ambient_digital_text));
                mTextPaintTopRight.setAntiAlias(false);
                mTextPaintTopRight.setColor(ContextCompat.getColor(getApplicationContext(), R.color.ambient_digital_text));
                mTextPaintBottomLeft.setAntiAlias(false);
                mTextPaintBottomLeft.setColor(ContextCompat.getColor(getApplicationContext(), R.color.ambient_digital_text));
                mTextPaintBottomRight.setAntiAlias(false);
                mTextPaintBottomRight.setColor(ContextCompat.getColor(getApplicationContext(), R.color.ambient_digital_text));
            } else {
                mTextPaint.setAntiAlias(true);
                mTextPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
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
            String allSegmentsOn = ".88:88";
            String allSegmentsOnTopLeft = "~~~";
            String allSegmentsOnTopRight = "!88";
            String allSegmentsOnBottomLeft = "~~~";
            String allSegmentsOnBottomRight = "!88";

            if (!mAmbient && mBackgroundBitmap == null && segmentsAlpha > 8) {
                Log.d(TAG, "A");
                // Initializes background.
                mBackgroundBitmapPaint = new Paint();
                mBackgroundBitmapPaint.setColor(Color.BLACK);

                Canvas backgroundCanvas = new Canvas();
                mBackgroundBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
                backgroundCanvas.setBitmap(mBackgroundBitmap);
                backgroundCanvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

                mTextPaint.setAntiAlias(true);
                mTextPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaintTopLeft.setAntiAlias(true);
                mTextPaintTopLeft.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaintTopRight.setAntiAlias(true);
                mTextPaintTopRight.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaintBottomLeft.setAntiAlias(true);
                mTextPaintBottomLeft.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaintBottomRight.setAntiAlias(true);
                mTextPaintBottomRight.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
                mTextPaint.setAlpha(segmentsAlpha);
                mTextPaintTopLeft.setAlpha(segmentsAlpha);
                mTextPaintTopRight.setAlpha(segmentsAlpha);
                mTextPaintBottomLeft.setAlpha(segmentsAlpha);
                mTextPaintBottomRight.setAlpha(segmentsAlpha);

                backgroundCanvas.drawText(allSegmentsOn, mXOffset, mYOffset, mTextPaint);
                backgroundCanvas.drawText(allSegmentsOnTopLeft, mXOffsetTopLeft, mYOffsetTopLeft, mTextPaintTopLeft);
                backgroundCanvas.drawText(allSegmentsOnTopRight, mXOffsetTopRight, mYOffsetTopRight, mTextPaintTopRight);
                backgroundCanvas.drawText(allSegmentsOnBottomLeft, mXOffsetBottomLeft, mYOffsetBottomLeft, mTextPaintBottomLeft);
                backgroundCanvas.drawText(allSegmentsOnBottomRight, mXOffsetBottomRight, mYOffsetBottomRight, mTextPaintBottomRight);
                mTextPaint.setAlpha(255);
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
            } else if (!mAmbient && mBackgroundBitmap != null && segmentsAlpha > 8) {
                Log.d(TAG, "B");
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, null);
            } else {
                Log.d(TAG, "C");
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            batteryStatus = LEDWatchFace.this.registerReceiver(null, ifilter);

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

            String text = null;
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
                text = String.format(Locale.getDefault(), "%02d:%02d", hour24, minute);
            } else {
                text = String.format(Locale.getDefault(), "%02d:%02d", hour12, minute);

                // replace leading zero with space (all-segments-off)
                if (text.charAt(0) == '0') {
                    text = "!" + text.substring(1);
                }

                // PM indicator
                if (isPM) {
                    text = "." + text; // always followed by blank or 1
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
                text = text.replace(':', ' ');
            }

            if (text != null) {
                canvas.drawText(text, mXOffset, mYOffset, mTextPaint);
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
