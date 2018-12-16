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
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * A digital watch face with seconds, battery, and date.
 * The colon blinks.
 *
 * Does not display seconds or blink the colon in ambient mode, and
 * draws text without anti-aliasing.
 */
public class LEDWatchFace extends CanvasWatchFaceService {

    private static final String TAG = "LEDWatchFace";

    private static final int FOREGROUND_COLOR_GREENSCREEN     = 1;
    private static final int FOREGROUND_COLOR_AMBER           = 2;
    private static final int FOREGROUND_COLOR_RED             = 3;
    private static final int FOREGROUND_COLOR_WHITE           = 4;
    private static final int FOREGROUND_COLOR_YELLOW          = 5;
    private static final int FOREGROUND_COLOR_BLUE            = 6;
    private static final int FOREGROUND_COLOR_ORANGE          = 7;
    private static final int FOREGROUND_COLOR_VINTAGE_LED_RED = 8;

    private static final int FONT_STYLE_NORMAL = 1;
    private static final int FONT_STYLE_ITALIC = 2;

    private static final int FONT_WEIGHT_LIGHT  = 1;
    private static final int FONT_WEIGHT_NORMAL = 2;
    private static final int FONT_WEIGHT_BOLD   = 3;

    private static final int FONT_FAMILY_DSEG_CLASSIC = 1;
    private static final int FONT_FAMILY_DSEG_MODERN  = 2;

    private static final int FONT_SIZE_MINI    = 1;
    private static final int FONT_SIZE_REGULAR = 2;

    private static final int TEXT_ALIGN_LEFT   = 1;
    private static final int TEXT_ALIGN_CENTER = 2;
    private static final int TEXT_ALIGN_RIGHT  = 3;

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode.  1/2 second
     * for blinking colons.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1) / 2;

    /**
     * Handler message id for updating the time periodically in
     * interactive mode.
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

        private Paint mTextPaintMiddle;
        private Paint mTextPaintTopLeft;
        private Paint mTextPaintTopRight;
        private Paint mTextPaintBottomLeft;
        private Paint mTextPaintBottomRight;

        /**
         * Whether the display supports fewer bits for each color in
         * ambient mode.  When true, many watch faces disable
         * anti-aliasing in ambient mode.  This watch faces disables
         * it outright.
         */
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean mAmbient;

        private Typeface mSevenSegmentTypeface;
        private Typeface mFourteenSegmentTypeface;

        private boolean mAutoPosition = true;
        private boolean mAutoTextSize = true;

        private int mSurfaceWidth;
        private int mSurfaceHeight;

        Bitmap mBackgroundBitmap;

        /* CONFIGURABLE OPTIONS */

        private float mLineSpacing; // device pixels?  or small/medium/large enum?
        private int mSegmentsAlpha; // fraction?  or integer?
        private int mLetterSpacing = 0; // integer 0 to 3, 0 is good default
        private float mSmallerTextSizeRatio = 0.5f; // fraction, 0.5 is good default

        private int mFontStyle  = FONT_STYLE_ITALIC;            // normal or italic
        private int mFontWeight = FONT_WEIGHT_NORMAL;           // light, normal, or bold
        private int mFontFamily = FONT_FAMILY_DSEG_CLASSIC;     // classic or modern
        private int mFontSize   = FONT_SIZE_REGULAR;            // regular or mini variant
        private int mForegroundColor = FOREGROUND_COLOR_YELLOW; // a few selections

        private Boolean m24Hour = null;

        private boolean mBlinkingColon = true;

        private boolean mShowDayOfWeek    = true;
        private boolean mShowDayOfMonth   = true;
        private boolean mShowBatteryLevel = true;
        private boolean mShowSeconds      = true;

        private boolean mEasterEgg105850 = false; // ;-)

        private boolean mVintageMode = true;
        private boolean m100SansPercent = true;

        private int dpToPixels(float dp) {
            final float scale = getResources().getDisplayMetrics().density;
            return (int)(dp * scale + 0.5f);
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            WatchFaceStyle.Builder styleBuilder = new WatchFaceStyle.Builder(LEDWatchFace.this);
            styleBuilder.setAcceptsTapEvents(true);
            styleBuilder.setStatusBarGravity(Gravity.RIGHT | Gravity.TOP);
            // styleBuilder.setHotwordIndicatorGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            WatchFaceStyle style = styleBuilder.build();
            setWatchFaceStyle(style);

            mCalendar = Calendar.getInstance();

            Resources resources = LEDWatchFace.this.getResources();
            mLineSpacing = resources.getDimension(R.dimen.line_spacing);

            mSegmentsAlpha = resources.getInteger(R.integer.segments_alpha_opacity);

            if (mVintageMode) {
                mFontStyle = FONT_STYLE_ITALIC;
                mFontWeight = FONT_WEIGHT_LIGHT;
                mFontSize = FONT_SIZE_REGULAR;
                mForegroundColor = FOREGROUND_COLOR_VINTAGE_LED_RED;
                mLetterSpacing = 2;
                mLineSpacing = dpToPixels(16);
            }

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.background));

            resources = LEDWatchFace.this.getResources();
            mSevenSegmentTypeface = Typeface.createFromAsset(
                    resources.getAssets(),
                    getFontFilename(7, mFontStyle, mFontWeight, mFontFamily, mFontSize)
            );
            mFourteenSegmentTypeface = Typeface.createFromAsset(
                    resources.getAssets(),
                    getFontFilename(14, mFontStyle, mFontWeight, mFontFamily, mFontSize)
            );

            // time of day
            mTextPaintMiddle = new Paint();
            mTextPaintMiddle.setTypeface(mSevenSegmentTypeface);
            mTextPaintMiddle.setTextAlign(Paint.Align.CENTER);

            // day of week
            mTextPaintTopLeft = new Paint();
            mTextPaintTopLeft.setTypeface(mFourteenSegmentTypeface);
            mTextPaintTopLeft.setTextAlign(Paint.Align.RIGHT);

            // day of month
            mTextPaintTopRight = new Paint();
            mTextPaintTopRight.setTypeface(mSevenSegmentTypeface);
            mTextPaintTopRight.setTextAlign(Paint.Align.LEFT);

            // battery percentage, " 0%" to "99%", or "100"
            mTextPaintBottomLeft = new Paint();
            mTextPaintBottomLeft.setTypeface(mFourteenSegmentTypeface);
            mTextPaintBottomLeft.setTextAlign(Paint.Align.RIGHT);

            // seconds
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
                String sampleText = "88:88";
                if (mLetterSpacing > 0) {
                    sampleText = addLetterSpacing(sampleText, mLetterSpacing, TEXT_ALIGN_CENTER);
                }

                mTextPaintMiddle.getTextBounds(sampleText, 0, sampleText.length(), bounds);
                textSize = (float) Math.floor(mSurfaceWidth * (isRound ? 0.85f : 0.9f)
                        / (bounds.right - bounds.left)
                        * (bounds.bottom - bounds.top));
            } else {
                textSize = resources.getDimension(
                        isRound ? R.dimen.digital_text_size_round : R.dimen.digital_text_size
                );
            }

            mTextPaintMiddle.setTextSize(textSize);
            mTextPaintTopLeft.setTextSize(Math.round(textSize * mSmallerTextSizeRatio));
            mTextPaintTopRight.setTextSize(Math.round(textSize * mSmallerTextSizeRatio));
            mTextPaintBottomLeft.setTextSize(Math.round(textSize * mSmallerTextSizeRatio));
            mTextPaintBottomRight.setTextSize(Math.round(textSize * mSmallerTextSizeRatio));

            if (mAutoPosition) {
                mYOffsetMiddle = Math.round(mSurfaceHeight / 2f + textSize / 2f);
                mYOffsetTopLeft = mYOffsetMiddle - textSize - mLineSpacing;
                mYOffsetTopRight = mYOffsetMiddle - textSize - mLineSpacing;
                mYOffsetBottomLeft = mYOffsetMiddle + Math.round(textSize * mSmallerTextSizeRatio) + mLineSpacing;
                mYOffsetBottomRight = mYOffsetMiddle + Math.round(textSize * mSmallerTextSizeRatio) + mLineSpacing;
            } else {
                mYOffsetMiddle = resources.getDimension(R.dimen.digital_y_offset);
                mYOffsetTopLeft = mYOffsetMiddle - textSize - mLineSpacing;
                mYOffsetTopRight = mYOffsetMiddle - textSize - mLineSpacing;
                mYOffsetBottomLeft = mYOffsetMiddle + Math.round(textSize * mSmallerTextSizeRatio) + mLineSpacing;
                mYOffsetBottomRight = mYOffsetMiddle + Math.round(textSize * mSmallerTextSizeRatio) + mLineSpacing;
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
                int textColor;
                switch (mForegroundColor) {
                    case FOREGROUND_COLOR_AMBER:
                        textColor = ContextCompat.getColor(getApplicationContext(), R.color.foreground_color_amber);
                        break;
                    case FOREGROUND_COLOR_GREENSCREEN:
                        textColor = ContextCompat.getColor(getApplicationContext(), R.color.foreground_color_greenscreen);
                        break;
                    case FOREGROUND_COLOR_RED:
                        textColor = ContextCompat.getColor(getApplicationContext(), R.color.foreground_color_red);
                        break;
                    case FOREGROUND_COLOR_WHITE:
                        textColor = ContextCompat.getColor(getApplicationContext(), R.color.foreground_color_white);
                        break;
                    case FOREGROUND_COLOR_YELLOW:
                        textColor = ContextCompat.getColor(getApplicationContext(), R.color.foreground_color_yellow);
                        break;
                    case FOREGROUND_COLOR_BLUE:
                        textColor = ContextCompat.getColor(getApplicationContext(), R.color.foreground_color_blue);
                        break;
                    case FOREGROUND_COLOR_ORANGE:
                        textColor = ContextCompat.getColor(getApplicationContext(), R.color.foreground_color_orange);
                        break;
                    case FOREGROUND_COLOR_VINTAGE_LED_RED:
                        textColor = ContextCompat.getColor(getApplicationContext(), R.color.foreground_color_vintage_led_red);
                        break;
                    default:
                        textColor = ContextCompat.getColor(getApplicationContext(), R.color.digital_text);
                        break;
                }

                mTextPaintMiddle.setAntiAlias(true);
                mTextPaintMiddle.setColor(textColor);
                mTextPaintTopLeft.setAntiAlias(true);
                mTextPaintTopLeft.setColor(textColor);
                mTextPaintTopRight.setAntiAlias(true);
                mTextPaintTopRight.setColor(textColor);
                mTextPaintBottomLeft.setAntiAlias(true);
                mTextPaintBottomLeft.setColor(textColor);
                mTextPaintBottomRight.setAntiAlias(true);
                mTextPaintBottomRight.setColor(textColor);
            }
        }

        /**
         * Captures tap event (and tap type) and may perform actions
         * in the future if the user finishes a tap.
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
                    break;
            }
            invalidate();
        }

        Bitmap canvasBitmap;

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            // for faint all-segments-on characters
            String allSegmentsOnMiddle = ".88:88";
            String allSegmentsOnTopLeft = "~~~";
            String allSegmentsOnTopRight = "888";
            String allSegmentsOnBottomLeft = "1~~~"; // "0%" to "100%"
            String allSegmentsOnBottomRight = "888";

            if (m100SansPercent) {
                allSegmentsOnBottomLeft = "~~~"; // "0%" to "99%" then "100"
            }

            if (mLetterSpacing > 0) {
                allSegmentsOnMiddle      = addLetterSpacing(allSegmentsOnMiddle,      mLetterSpacing, TEXT_ALIGN_CENTER);
                allSegmentsOnTopLeft     = addLetterSpacing(allSegmentsOnTopLeft,     mLetterSpacing, TEXT_ALIGN_RIGHT);
                allSegmentsOnTopRight    = addLetterSpacing(allSegmentsOnTopRight,    mLetterSpacing, TEXT_ALIGN_LEFT);
                allSegmentsOnBottomLeft  = addLetterSpacing(allSegmentsOnBottomLeft,  mLetterSpacing, TEXT_ALIGN_RIGHT);
                allSegmentsOnBottomRight = addLetterSpacing(allSegmentsOnBottomRight, mLetterSpacing, TEXT_ALIGN_LEFT);
            }

            if (!mAmbient && mBackgroundBitmap == null && mSegmentsAlpha > 8) {
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
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, null);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            int batteryPercentage = 0;
            if (mShowBatteryLevel) {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = LEDWatchFace.this.registerReceiver(null, ifilter);

                int batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int batteryScale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                batteryPercentage = Math.round(batteryLevel * 100f / batteryScale);
            }

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            if (mEasterEgg105850) {
                mCalendar.set(2013, 5, 30, 10, 58, 50);
            }

            int hour12 = mCalendar.get(Calendar.HOUR);
            int hour24 = mCalendar.get(Calendar.HOUR_OF_DAY);
            int minute = mCalendar.get(Calendar.MINUTE);
            int second = mCalendar.get(Calendar.SECOND);
            int millis = mCalendar.get(Calendar.MILLISECOND);
            int dayOfWeek  = mCalendar.get(Calendar.DAY_OF_WEEK);
            int dayOfMonth = mCalendar.get(Calendar.DAY_OF_MONTH);

            boolean isPM = mCalendar.get(Calendar.AM_PM) == Calendar.PM;
            boolean blink = millis >= 400;

            String textMiddle = null;      // time of day
            String textTopLeft = null;     // day of week
            String textTopRight = null;    // day of momth
            String textBottomLeft = null;  // battery percentage
            String textBottomRight = null; // seconds

            boolean is24Hour;
            if (m24Hour == null) {
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
                    is24Hour = DateFormat.is24HourFormat(LEDWatchFace.this);
                }
            } else {
                is24Hour = m24Hour;
            }

            if (mEasterEgg105850) {
                batteryPercentage = 89;
                blink = false;
                is24Hour = true;
            }

            // time of day
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
                    textMiddle = "." + textMiddle; // '.' is always followed by blank numeral or '1'
                }
            }

            if (mBlinkingColon && blink && !mAmbient) {
                textMiddle = textMiddle.replace(':', ' ');
            }

            // seconds
            if (mShowSeconds && !mAmbient) {
                textBottomRight = String.format(Locale.getDefault(), "!%02d", second);
            }

            if (mShowBatteryLevel) {
                if (batteryPercentage < 0) {
                    if (m100SansPercent) {
                        textBottomLeft = "???";
                    } else {
                        textBottomLeft = "!???";
                    }
                } else if (batteryPercentage < 100) {
                    if (m100SansPercent) {
                        textBottomLeft = String.format(Locale.getDefault(), "%2d%%", batteryPercentage);
                        textBottomLeft = textBottomLeft.replaceAll(" ", "!"); // " 9%" => "!9%"
                    } else {
                        textBottomLeft = String.format(Locale.getDefault(), "!%2d%%", batteryPercentage);
                        textBottomLeft = textBottomLeft.replaceAll(" ", "!"); // "! 9%" => "!!9%"
                    }
                } else if (batteryPercentage == 100) {
                    if (m100SansPercent) {
                        textBottomLeft = String.format(Locale.getDefault(), "%3d", batteryPercentage);
                    } else {
                        textBottomLeft = String.format(Locale.getDefault(), "%3d%%", batteryPercentage);
                    }
                } else {
                    if (m100SansPercent) {
                        textBottomLeft = "???";
                    } else {
                        textBottomLeft = "!???";
                    }
                }
            }

            if (mShowDayOfWeek) {
                textTopLeft = mCalendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
                if (textTopLeft.length() > 3) {
                    textTopLeft = textTopLeft.substring(0, 3);
                }
                textTopLeft = textTopLeft.toUpperCase();
            }

            if (mShowDayOfMonth) {
                textTopRight = String.format(Locale.getDefault(), "!%2d", dayOfMonth);
                textTopRight = textTopRight.replace(" ", "!");
            }

            if (mLetterSpacing > 0) {
                if (textMiddle != null) {
                    textMiddle = addLetterSpacing(textMiddle, mLetterSpacing, TEXT_ALIGN_CENTER);
                }
                if (mShowDayOfWeek && textTopLeft != null) {
                    textTopLeft = addLetterSpacing(textTopLeft, mLetterSpacing, TEXT_ALIGN_RIGHT);
                }
                if (mShowDayOfMonth && textTopRight != null) {
                    textTopRight = addLetterSpacing(textTopRight, mLetterSpacing, TEXT_ALIGN_LEFT);
                }
                if (mShowBatteryLevel && textBottomLeft != null) {
                    textBottomLeft = addLetterSpacing(textBottomLeft, mLetterSpacing, TEXT_ALIGN_RIGHT);
                }
                if (mShowSeconds && textBottomRight != null) {
                    textBottomRight = addLetterSpacing(textBottomRight, mLetterSpacing, TEXT_ALIGN_LEFT);
                }
            }

            if (textMiddle != null) {
                textMiddle = textMiddle.replaceAll(",", "");
                canvas.drawText(textMiddle, mXOffsetMiddle, mYOffsetMiddle, mTextPaintMiddle);
            }
            if (mShowDayOfWeek && textTopLeft != null) {
                textTopLeft = textTopLeft.replaceAll(",", "");
                canvas.drawText(textTopLeft, mXOffsetTopLeft, mYOffsetTopLeft, mTextPaintTopLeft);
            }
            if (mShowDayOfMonth && textTopRight != null) {
                textTopRight = textTopRight.replaceAll(",", "");
                canvas.drawText(textTopRight, mXOffsetTopRight, mYOffsetTopRight, mTextPaintTopRight);
            }
            if (mShowBatteryLevel && textBottomLeft != null) {
                textBottomLeft = textBottomLeft.replaceAll(",", "");
                canvas.drawText(textBottomLeft, mXOffsetBottomLeft, mYOffsetBottomLeft, mTextPaintBottomLeft);
            }
            if (mShowSeconds && textBottomRight != null) {
                textBottomRight = textBottomRight.replaceAll(",", "");
                canvas.drawText(textBottomRight, mXOffsetBottomRight, mYOffsetBottomRight, mTextPaintBottomRight);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should
         * be running and isn't currently or stops it if it shouldn't
         * be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer
         * should be running. The timer should only run when we're
         * visible and in interactive mode.
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

        /**
         * Build the name of a font resources, returning it as a string.
         *
         * @param segments an integer, 7 or 14.
         * @param fontStyle one of the FONT_STYLE_* constants.
         * @param fontWeight one of the FONT_WEIGHT_* constants.
         * @param fontFamily one of the FONT_FAMILY_* constants.
         * @param fontSize one of the FONT_SIZE_* constants.
         * @return a font resource name.
         */
        private String getFontFilename(
                int segments,
                int fontStyle,
                int fontWeight,
                int fontFamily,
                int fontSize
        ) {
            String result = "fonts/DSEG";
            result += segments;
            switch (fontFamily) {
                case FONT_FAMILY_DSEG_CLASSIC:
                    result += "Classic";
                    break;
                case FONT_FAMILY_DSEG_MODERN:
                    result += "Modern";
                    break;
            }
            switch (fontSize) {
                case FONT_SIZE_MINI:
                    result += "Mini-";
                    break;
                default:
                    result += "-";
                    break;
            }
            switch (fontWeight) {
                case FONT_WEIGHT_LIGHT:
                    result += "Light";
                    break;
                case FONT_WEIGHT_NORMAL:
                    result += "Regular";
                    break;
                case FONT_WEIGHT_BOLD:
                    result += "Bold";
                    break;
            }
            switch (fontStyle) {
                case FONT_STYLE_NORMAL:
                    result += ".ttf";
                    break;
                case FONT_STYLE_ITALIC:
                    result += "Italic.ttf";
                    break;
            }
            return result;
        }
    }

    /**
     * Insert at each point between two characters a number of spaces,
     * returning the resulting string.
     *
     * If textAlign is TEXT_ALIGN_LEFT, also pad spaces before the first character.
     *
     * If textAlign is TEXT_ALIGN_RIGHT, also pad spaces after the last character.
     *
     * @param s the original string
     * @param spacing the number of spaces to insert at each location
     * @param textAlign
     * @return the resulting string
     */
    private static String addLetterSpacing(String s, int spacing, int textAlign) {
        int length = s.length();
        int lastIndex = length - 1;
        int firstIndex = 1;
        StringBuffer resultBuffer = new StringBuffer(s);
        if (textAlign == TEXT_ALIGN_LEFT) {
            firstIndex = 0;
        } else if (textAlign == TEXT_ALIGN_RIGHT) {
            lastIndex = length;
        }
        for (int i = lastIndex; i >= firstIndex; i -= 1) {
            // '.' at char 0 is PM indicator.
            // always occurs before blank numeral or '1'.
            if (i > 0 && s.charAt(i - 1) == '.') {
                continue;
            }
            for (int j = 1; j <= spacing; j += 1) {
                resultBuffer.insert(i, ' ');
            }
        }
        return resultBuffer.toString();
    }
}
