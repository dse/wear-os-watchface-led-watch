package com.webonastick.ledwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.text.Normalizer;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import com.webonastick.watchface.MultiTapEventHandler;
import com.webonastick.watchface.MultiTapHandler;
import com.webonastick.watchface.AmbientRefresher;
import com.webonastick.util.HSPColor;
import com.webonastick.watchface.ScreenTimeExtender;

/**
 * A digital watch face with seconds, battery, and date.
 * The colon blinks.
 * <p>
 * Does not display seconds or blink the colon in ambient mode, and
 * draws text without anti-aliasing.
 */
public class LEDWatchFace extends CanvasWatchFaceService {

    public enum LEDWatchThemeMode {
        LED("foreground", "led"),
        LCD("background", "lcd"),
        VINTAGE_LED("foreground", "vintage_led");

        private final String resourceName;
        private final String colorResourceType;

        LEDWatchThemeMode(String colorResourceType, String resourceName) {
            this.resourceName = resourceName;
            this.colorResourceType = colorResourceType;
        }

        public String getResourceName() {
            return resourceName;
        }

        public String getColorResourceType() {
            return colorResourceType;
        }

        public static LEDWatchThemeMode findThemeModeNamed(String themeModeName) {
            if (themeModeName == null) {
                return null;
            }
            for (LEDWatchThemeMode themeMode : LEDWatchThemeMode.values()) {
                if (themeModeName.equals(themeMode.resourceName)) {
                    return themeMode;
                }
            }
            return null;
        }

        public LEDWatchThemeMode nextThemeMode() {
            int ordinal = this.ordinal();
            int length = LEDWatchThemeMode.values().length;
            ordinal = (ordinal + 1) % length;
            return LEDWatchThemeMode.values()[ordinal];
        }
    }

    public enum LEDWatchThemeColor {
        RED("red"),
        BRIGHT_RED("bright_red"),
        ORANGE("orange"),
        AMBER("amber"),
        YELLOW("yellow"),
        GREEN("green"),
        CYAN("cyan"),
        BLUE("blue"),
        WHITE("white");

        private final String resourceName;

        LEDWatchThemeColor(String resourceName) {
            this.resourceName = resourceName;
        }

        public String getResourceName() {
            return resourceName;
        }

        public static LEDWatchThemeColor findThemeColorNamed(String themeColorName) {
            if (themeColorName == null) {
                return null;
            }
            for (LEDWatchThemeColor themeColor : LEDWatchThemeColor.values()) {
                if (themeColorName.equals(themeColor.resourceName)) {
                    return themeColor;
                }
            }
            return null;
        }

        public LEDWatchThemeColor nextThemeColor() {
            int ordinal = this.ordinal();
            int length = LEDWatchThemeColor.values().length;
            ordinal = (ordinal + 1) % length;
            return LEDWatchThemeColor.values()[ordinal];
        }
    }

    public enum DSEGFontSize {
        MINI("Mini-"),
        NORMAL("-");

        private final String filenamePortion;

        DSEGFontSize(String filenamePortion) {
            this.filenamePortion = filenamePortion;
        }

        public String getFilenamePortion() {
            return filenamePortion;
        }
    }

    public enum DSEGFontFamily {
        CLASSIC("Classic"),
        MODERN("Modern");

        private final String filenamePortion;

        DSEGFontFamily(String filenamePortion) {
            this.filenamePortion = filenamePortion;
        }

        public String getFilenamePortion() {
            return filenamePortion;
        }
    }

    public enum DSEGFontWeight {
        LIGHT("Light"),
        REGULAR("Regular"),
        BOLD("Bold");

        private final String filenamePortion;

        DSEGFontWeight(String filenamePortion) {
            this.filenamePortion = filenamePortion;
        }

        public String getFilenamePortion() {
            return filenamePortion;
        }
    }

    public enum DSEGFontStyle {
        NORMAL(""),
        ITALIC("Italic");

        private final String filenamePortion;

        DSEGFontStyle(String filenamePortion) {
            this.filenamePortion = filenamePortion;
        }

        public String getFilenamePortion() {
            return filenamePortion;
        }
    }

    public enum DSEGFontSegments {
        SEVEN("7"),
        FOURTEEN("14");

        private final String filenamePortion;

        DSEGFontSegments(String filenamePortion) {
            this.filenamePortion = filenamePortion;
        }

        public String getFilenamePortion() {
            return filenamePortion;
        }
    }

    private static final String TAG = "LEDWatchFace";

    private static final Typeface AM_PM_TYPEFACE =
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

    private static final int COLOR_DARK_RED = 0xff440000;
    private static final float LED_FAINT = HSPColor.fromRGB(COLOR_DARK_RED).perceivedBrightness();
    private static final float LCD_FAINT = LED_FAINT / 3f;

    private class Engine extends CanvasWatchFaceService.Engine implements MultiTapEventHandler {

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

        private LEDWatchThemeMode mThemeMode;
        private LEDWatchThemeColor mThemeColor;

        private DSEGFontFamily mDSEGFontFamily;
        private DSEGFontSize mDSEGFontSize;
        private DSEGFontStyle mDSEGFontStyle;
        private DSEGFontWeight mDSEGFontWeight;

        private float mYOffsetTop;
        private float mYOffsetMiddle;
        private float mYOffsetBottom;

        private float mXOffsetMiddle;
        private float mXOffsetLeft;
        private float mXOffsetRight;

        private float mXOffsetTopLeft;
        private float mXOffsetTopRight;

        private float mXOffsetBottomLeft;
        private float mXOffsetBottomRight;
        private float mXOffsetBottomRight2;

        private float mXOffsetAmPm;
        private float mYOffsetAm;
        private float mYOffsetPm;

        private Paint mBackgroundPaint = null;
        private Paint mTextPaintMiddle = null;
        private Paint mTextPaintLeft = null;
        private Paint mTextPaintRight = null;
        private Paint mTextPaintTopLeft = null;
        private Paint mTextPaintTopRight = null;
        private Paint mTextPaintBottomLeft = null;
        private Paint mTextPaintBottomRight = null;
        private Paint mTextPaintBottomRight2 = null;
        private Paint mTextPaintAmPm = null;

        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean mAmbient;
        private boolean mIsRound;

        private Typeface mSevenSegmentTypeface;
        private Typeface mFourteenSegmentTypeface;

        private int mSurfaceWidth;
        private int mSurfaceHeight;

        Bitmap mBackgroundBitmap;

        /* CONFIGURABLE OPTIONS */

        private int mFaintAlpha = 0;
        private int mLetterSpacing = 0;
        private int mLetterSpacing2 = 0;
        private float mSmallerTextSizeRatio = 0.5f;

        private int mForegroundColor = Color.WHITE;
        private int mBackgroundColor = Color.BLACK;
        private int mFaintForegroundColor = Color.BLACK;

        private final boolean mBlinkingColon = true;
        private final boolean mShowDayOfWeek = true;
        private final boolean mShowDayOfMonth = true;
        private final boolean mShowBatteryLevel = true;
        private final boolean mShowSeconds = true;

        // if true: show "100" then "99%"
        // if false: extra "1" segment to show "100%"
        private final boolean m100SansPercent = false;

        private Typeface mSixthsOfAPieTypeface;

        private boolean mDemoTimeMode = false;
        private boolean mEmulatorMode = false;

        private float mPixelDensity;

        private SharedPreferences mSharedPreferences;
        private AmbientRefresher mAmbientRefresher;
        private ScreenTimeExtender mScreenTimeExtender;

        private int getBackgroundColor() {
            if (mAmbient) {
                return Color.BLACK;
            }
            switch (mThemeMode) {
                case LED:
                case VINTAGE_LED:
                    return Color.BLACK;
                default:
                    return getThemeColor();
            }
        }

        private int getForegroundColor() {
            if (mAmbient) {
                return Color.WHITE;
            }
            switch (mThemeMode) {
                case LCD:
                    return Color.BLACK;
                default:
                    return getThemeColor();
            }
        }

        private int getThemeColor() {
            Resources resources = LEDWatchFace.this.getResources();
            String resourceName = mThemeMode.colorResourceType + "_color_" + mThemeMode.resourceName + "_" + mThemeColor.resourceName;
            int resourceId = resources.getIdentifier(resourceName, "color", getPackageName());
            if (resourceId == 0) {
                return Color.WHITE;
            }
            return resources.getInteger(resourceId);
        }

        private int getFaintAlpha() {
            switch (mThemeMode) {
                case LED:
                case VINTAGE_LED:
                    return getFaintAlphaFromForeground(getForegroundColor());
                case LCD:
                    return getFaintAlphaFromBackground(getBackgroundColor());
                default:
                    return 0;
            }
        }

        /**
         * Calculate the alpha transparency at which to display the
         * "faint" segments so that they are visible enough.
         * <p>
         * This value will be higher for darker colors, and lower for
         * brighter colors.
         */
        private int getFaintAlphaFromForeground(int color) {
            float brightness = HSPColor.fromRGB(color).perceivedBrightness();
            float relFaintBrightness = LED_FAINT / brightness;
            int result = (int) (relFaintBrightness * 255f + 0.5f);
            return result;
        }

        /**
         * Calculate the alpha transparency at which to display the
         * "faint" segments so that they are visible enough.
         * <p>
         * This value will be higher for darker colors, and lower for
         * brighter colors.
         */
        private int getFaintAlphaFromBackground(int color) {
            float brightness = HSPColor.fromRGB(color).perceivedBrightness();
            float newBrightness = brightness - LCD_FAINT;
            float alpha = (brightness - newBrightness) / brightness;
            alpha = (float) Math.min(alpha, 0.05f);
            int result = (int) (alpha * 255f + 0.5f);
            return result;
        }

        private int getFaintForegroundColor() {
            if (mLowBitAmbient) {
                return Color.BLACK;
            }
            int result = getForegroundColor();
            result = result & 0x00ffffff;
            int alpha = getFaintAlpha();
            result = result | ((alpha & 0xff) << 24);
            return result;
        }

        private static final float VINTAGE_LED_TEXT_SIZE_RATIO = 0.875f;

        /* as multiple of text size */
        private float getSmallerTextSizeRatio() {
            switch (mThemeMode) {
                case LED:
                case LCD:
                    return 0.5f;
                case VINTAGE_LED:
                    return 0.5f;
                default:
                    return 0.5f;
            }
        }

        /* letter spacing for time of day, as integer number of spaces */
        private int getLetterSpacing() {
            switch (mThemeMode) {
                case LED:
                case LCD:
                    return 0;
                case VINTAGE_LED:
                    return 2;
                default:
                    return 0;
            }
        }

        /* letter spacing for day, date, battery, and seconds, as integer number of spaces */
        private int getLetterSpacing2() {
            switch (mThemeMode) {
                case LED:
                case LCD:
                    return 0;
                case VINTAGE_LED:
                    return 4;
                default:
                    return 0;
            }
        }

        /* as multiple of text size */
        private float getLineSpacingRatio() {
            switch (mThemeMode) {
                case LED:
                case LCD:
                    return 0.25f;
                case VINTAGE_LED:
                    return 1f;
                default:
                    return 0.25f;
            }
        }

        private boolean hasFullWidthColon() {
            switch (mThemeMode) {
                case VINTAGE_LED:
                    return true;
                default:
                    return false;
            }
        }

        private char colonCharacter() {
            switch (mThemeMode) {
                case VINTAGE_LED:
                    return ':';
                default:
                    return ':';
            }
        }

        private String topLeftSegments() {
            /* 14-segment */
            return "~~~";
        }

        private String topRightSegments() {
            return "888";
        }

        private String bottomLeftSegments() {
            /* 14-segment */
            if (mThemeMode == LEDWatchThemeMode.VINTAGE_LED) {
                return m100SansPercent ? "~~~" : "~~~~";
            }
            return m100SansPercent ? "~~~" : "1~~~";
        }

        private String bottomRightSegments() {
            return "888";
        }

        private String leftSegments() {
            return is24Hour() ? "88" : "18";
        }

        private String middleSegments() {
            return "" + colonCharacter();
        }

        private String rightSegments() {
            return "88";
        }

        private void updateProperties() {
            updateThemeBasedProperties();
            updateColors();
            updateTypefaces();
            updateTextPaintProperties();
            updateSizeBasedProperties();
        }

        private void updateThemeBasedProperties() {
            Resources resources = LEDWatchFace.this.getResources();

            mLetterSpacing = getLetterSpacing();
            mLetterSpacing2 = getLetterSpacing2();
            mFaintAlpha = getFaintAlpha();
            mSmallerTextSizeRatio = getSmallerTextSizeRatio();

            switch (mThemeMode) {
                case LED:
                    mDSEGFontFamily = DSEGFontFamily.CLASSIC;
                    mDSEGFontSize = DSEGFontSize.NORMAL;
                    mDSEGFontStyle = DSEGFontStyle.ITALIC;
                    mDSEGFontWeight = DSEGFontWeight.REGULAR;
                    break;
                case VINTAGE_LED:
                    mDSEGFontFamily = DSEGFontFamily.MODERN;
                    mDSEGFontSize = DSEGFontSize.NORMAL;
                    mDSEGFontStyle = DSEGFontStyle.ITALIC;
                    mDSEGFontWeight = DSEGFontWeight.LIGHT;
                    break;
                case LCD:
                    mDSEGFontFamily = DSEGFontFamily.CLASSIC;
                    mDSEGFontSize = DSEGFontSize.NORMAL;
                    mDSEGFontStyle = DSEGFontStyle.ITALIC;
                    mDSEGFontWeight = DSEGFontWeight.BOLD;
                    break;
                default:
                    mDSEGFontFamily = DSEGFontFamily.CLASSIC;
                    mDSEGFontSize = DSEGFontSize.NORMAL;
                    mDSEGFontStyle = DSEGFontStyle.ITALIC;
                    mDSEGFontWeight = DSEGFontWeight.REGULAR;
                    break;
            }
        }

        private String getFontFilename(int segments) {
            String result = "fonts/DSEG";
            result += segments;
            result += mDSEGFontFamily.getFilenamePortion();
            result += mDSEGFontSize.getFilenamePortion();
            result += mDSEGFontWeight.getFilenamePortion();
            result += mDSEGFontStyle.getFilenamePortion();
            result += ".ttf";
            return result;
        }

        private void updateColors() {
            mForegroundColor = getForegroundColor();
            mBackgroundColor = getBackgroundColor();
            mFaintForegroundColor = getFaintForegroundColor();
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mBackgroundColor);
        }

        private void updateTypefaces() {
            Resources resources = LEDWatchFace.this.getResources();
            mSevenSegmentTypeface = Typeface.createFromAsset(
                    resources.getAssets(),
                    getFontFilename(7)
            );
            mFourteenSegmentTypeface = Typeface.createFromAsset(
                    resources.getAssets(),
                    getFontFilename(14)
            );
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            cancelMultiTap();

            if (Build.MODEL.startsWith("sdk_") || Build.FINGERPRINT.contains("/sdk_")) {
                mEmulatorMode = true;
            }
            mPixelDensity = getResources().getDisplayMetrics().density;

            WatchFaceStyle.Builder styleBuilder = new WatchFaceStyle.Builder(LEDWatchFace.this);
            styleBuilder.setAcceptsTapEvents(true);
            styleBuilder.setStatusBarGravity(Gravity.RIGHT | Gravity.TOP);
            WatchFaceStyle style = styleBuilder.build();
            setWatchFaceStyle(style);

            Resources resources = LEDWatchFace.this.getResources();

            Context context = getBaseContext();
            mSharedPreferences = context.getSharedPreferences(
                    getString(R.string.preference_file_key),
                    Context.MODE_PRIVATE
            );

            mSixthsOfAPieTypeface = Typeface.createFromAsset(
                    resources.getAssets(),
                    "fonts/sixths-of-a-pie.ttf"
            );

            getThemePreference();

            mTextPaintMiddle = new Paint();
            mTextPaintLeft = new Paint();
            mTextPaintRight = new Paint();
            mTextPaintTopLeft = new Paint();
            mTextPaintTopRight = new Paint();
            mTextPaintBottomLeft = new Paint();
            mTextPaintBottomRight = new Paint();
            mTextPaintBottomRight2 = new Paint();
            mTextPaintAmPm = new Paint();

            mCalendar = Calendar.getInstance();
            updateProperties();
            mBackgroundBitmap = null;

            mAmbientRefresher = new AmbientRefresher(LEDWatchFace.this, new Runnable() {
                @Override
                public void run() {
                    invalidate();
                }
            });

            mScreenTimeExtender = new ScreenTimeExtender(LEDWatchFace.this);
            mScreenTimeExtender.clearIdle();
        }

        private void getThemePreference() {
            String themeColorName = mSharedPreferences.getString("theme_color", null);
            mThemeColor = LEDWatchThemeColor.findThemeColorNamed(themeColorName);
            if (mThemeColor == null) {
                mThemeColor = LEDWatchThemeColor.WHITE;
            }
            String themeModeName = mSharedPreferences.getString("theme_mode", null);
            mThemeMode = LEDWatchThemeMode.findThemeModeNamed(themeModeName);
            if (mThemeMode == null) {
                mThemeMode = LEDWatchThemeMode.LED;
            }
        }

        private void saveThemePreference() {
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putString("theme_color", mThemeColor.resourceName);
            editor.putString("theme_mode", mThemeMode.resourceName);
            editor.commit();
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
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            cancelMultiTap();
            super.onSurfaceChanged(holder, format, width, height);
            mPixelDensity = getResources().getDisplayMetrics().density;
            mSurfaceWidth = width;
            mSurfaceHeight = height;
            mIsRound = getApplicationContext().getResources().getConfiguration().isScreenRound();
            updateProperties();
            mBackgroundBitmap = null;
            if (!mAmbient) {
                mScreenTimeExtender.clearIdle();
            }
        }

        private void updateSizeBasedProperties() {
            Log.d(TAG, "updateSizeBasedProperties()");
            computeTimeOfDayTextSizeAndOffsets();
            computeAmPmTextSizeAndHorizontalOffsets();
            computeDayDateTextSizeAndHorizontalOffsets();
            computeBatterySecondsTextSizeAndHorizontalOffsets();
            computeVerticalOffsets();
        }
        
        private void computeTimeOfDayTextSizeAndOffsets() {
            Log.d(TAG, String.format(
                    "mSurfaceWidth = %f",
                    (float)mSurfaceWidth
            ));
            mTextPaintMiddle.setTextSize(mSurfaceWidth); // this size only for calculatory purposes
            String sampleText = hasFullWidthColon() ? "88888" : "88:88";
            sampleText = addLetterSpacing(sampleText, mLetterSpacing);
            float rawHeight = getTextHeight(sampleText, mTextPaintMiddle);
            float rawWidth = getTextWidth(sampleText, mTextPaintMiddle);
            float textSize = (mSurfaceWidth - 16 * mPixelDensity * 0) / rawWidth * rawHeight;
            Log.d(TAG, String.format(
                    "sampleText [%s] rawHeight [%f] rawWidth [%f] textSize [%f]",
                    sampleText, rawHeight, rawWidth, textSize
            ));

            mTextPaintMiddle.setTextSize(textSize);
            Log.d(TAG, String.format(
                    "  test width is %f",
                    getTextWidth(sampleText, mTextPaintMiddle)
            ));

            if (mThemeMode == LEDWatchThemeMode.VINTAGE_LED) {
                textSize *= VINTAGE_LED_TEXT_SIZE_RATIO;
                Log.d(TAG, String.format(
                        "=> textSize [%f] (vintage LED)",
                        textSize
                ));
            }

            float cookedWidth = rawWidth / mSurfaceWidth * textSize;
            float cookedHeight = rawHeight / mSurfaceWidth * textSize;

            Log.d(TAG, String.format(
                    "cookedHeight [%f] cookedWidth [%f]",
                    cookedHeight, cookedWidth
            ));

            // slightly smaller for round displays
            if (mIsRound || mDemoTimeMode) {
                float angle = (float) Math.atan2(cookedHeight, cookedWidth);
                float cosine = (float) Math.cos(angle);
                textSize *= cosine;
                cookedWidth *= cosine;
                cookedHeight *= cosine;
                Log.d(TAG, String.format(
                        "=> textSize [%f]",
                        textSize
                ));
            }
            mXOffsetAmPm = mSurfaceWidth / 2f - cookedWidth / 2f;
            mXOffsetLeft = mSurfaceWidth / 2f - cookedWidth / 2f;
            mXOffsetRight = mSurfaceWidth / 2f + cookedWidth / 2f;
            mXOffsetMiddle = mSurfaceWidth / 2f;

            Log.d(TAG, String.format(
                    "mXOffsetAmPm [%f] mXOffsetLeft [%f] mXOffsetRight [%f] mXOffsetMiddle [%f]",
                    mXOffsetAmPm, mXOffsetLeft, mXOffsetRight, mXOffsetMiddle
            ));

            mTextPaintMiddle.setTextSize(textSize);
            mTextPaintLeft.setTextSize(textSize);
            mTextPaintRight.setTextSize(textSize);
        }
        
        private void computeAmPmTextSizeAndHorizontalOffsets() {
            float textSize = mTextPaintMiddle.getTextSize();

            float textSizeAmPm = (textSize / 4f) / 0.7f; /* "A" or "P" */
            mTextPaintAmPm.setTextSize(textSizeAmPm);

            Log.d(TAG, String.format(
                    "textSize [%f] => textSizeAmPm [%f]",
                    textSize, textSizeAmPm
            ));
        }

        private void computeDayDateTextSizeAndHorizontalOffsets() {
            float textSize = mTextPaintMiddle.getTextSize();
            float smallerTextSize = textSize * mSmallerTextSizeRatio;

            mTextPaintTopLeft.setTextSize(smallerTextSize);
            mTextPaintTopRight.setTextSize(smallerTextSize);

            String sampleText = topLeftSegments() + topRightSegments();
            sampleText = addLetterSpacing(sampleText, mLetterSpacing2);
            float cookedWidth = getTextWidth(sampleText, mTextPaintTopLeft);
            mXOffsetTopLeft = mSurfaceWidth / 2f - cookedWidth / 2f;
            mXOffsetTopRight = mSurfaceWidth / 2f + cookedWidth / 2f;

            Log.d(TAG, String.format(
                    "textSize [%f] => smallerTextSize [%f] sampleText [%s] cookedWidth [%f] mXOffsetTopLeft [%f] mXOffsetTopRight [%f]",
                    textSize, smallerTextSize, sampleText, cookedWidth, mXOffsetTopLeft, mXOffsetTopRight
            ));
        }

        private void computeBatterySecondsTextSizeAndHorizontalOffsets() {
            float textSize = mTextPaintMiddle.getTextSize();
            float smallerTextSize = textSize * mSmallerTextSizeRatio;

            mTextPaintBottomLeft.setTextSize(smallerTextSize);
            mTextPaintBottomRight.setTextSize(smallerTextSize);
            mTextPaintBottomRight2.setTextSize(smallerTextSize);

            String sampleText = bottomLeftSegments() + bottomRightSegments();
            sampleText = addLetterSpacing(sampleText, mLetterSpacing2);
            float cookedWidth = getTextWidth(sampleText, mTextPaintBottomLeft);
            if (sampleText.startsWith("1")) {
                cookedWidth -= getTextWidthDifference("1", "8", mTextPaintBottomLeft);
            }
            mXOffsetBottomRight = mSurfaceWidth / 2f + cookedWidth / 2f;
            mXOffsetBottomRight2 = mSurfaceWidth / 2f + cookedWidth / 2f;
            mXOffsetBottomLeft = mSurfaceWidth / 2f - cookedWidth / 2f;
            if (sampleText.startsWith("1")) {
                mXOffsetBottomLeft -= getTextWidthDifference("1", "8", mTextPaintBottomLeft);
            }
        }

        private void computeVerticalOffsets() {
            float textSize = mTextPaintMiddle.getTextSize();
            float textSizeAmPm = mTextPaintAmPm.getTextSize();
            
            float textAscent = -textSize;
            float textAscentAmPm = -textSizeAmPm * 0.7f;
            float mLineSpacingDp = textSize * getLineSpacingRatio();
            mYOffsetMiddle = mSurfaceHeight / 2f - textAscent / 2f;
            mYOffsetTop = mYOffsetMiddle + textAscent - mLineSpacingDp;
            mYOffsetBottom = mYOffsetMiddle - textAscent * mSmallerTextSizeRatio + mLineSpacingDp;
            mYOffsetAm = mSurfaceHeight / 2f + textAscent / 4f - textAscentAmPm / 2f;
            mYOffsetPm = mSurfaceHeight / 2f - textAscent / 4f - textAscentAmPm / 2f;
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

            updateProperties();
            mBackgroundBitmap = null;
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
            updateProperties();
            mBackgroundBitmap = null;

            if (mAmbient) {
                mAmbientRefresher.start();
            } else {
                mAmbientRefresher.stop();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
            mScreenTimeExtender.clearIdle();
        }

        private void updateTextPaintProperties() {
            mTextPaintMiddle.setTextAlign(Paint.Align.CENTER);
            mTextPaintLeft.setTextAlign(Paint.Align.LEFT);
            mTextPaintRight.setTextAlign(Paint.Align.RIGHT);
            mTextPaintTopLeft.setTextAlign(Paint.Align.LEFT);
            mTextPaintTopRight.setTextAlign(Paint.Align.RIGHT);
            mTextPaintBottomLeft.setTextAlign(Paint.Align.LEFT);
            mTextPaintBottomRight.setTextAlign(Paint.Align.RIGHT);
            mTextPaintBottomRight2.setTextAlign(Paint.Align.RIGHT);
            mTextPaintAmPm.setTextAlign(Paint.Align.LEFT);

            mTextPaintMiddle.setTypeface(mSevenSegmentTypeface);
            mTextPaintLeft.setTypeface(mSevenSegmentTypeface);
            mTextPaintRight.setTypeface(mSevenSegmentTypeface);
            mTextPaintTopLeft.setTypeface(mFourteenSegmentTypeface);
            mTextPaintTopRight.setTypeface(mSevenSegmentTypeface);
            mTextPaintBottomLeft.setTypeface(mFourteenSegmentTypeface);
            mTextPaintBottomRight.setTypeface(mSevenSegmentTypeface);
            mTextPaintBottomRight2.setTypeface(mSixthsOfAPieTypeface);
            mTextPaintAmPm.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            
            setAntiAlias(!mLowBitAmbient);
            setColor(mForegroundColor);
        }
        
        private void setAntiAlias(boolean flag) {
            mTextPaintMiddle.setAntiAlias(flag);
            mTextPaintLeft.setAntiAlias(flag);
            mTextPaintRight.setAntiAlias(flag);
            mTextPaintTopLeft.setAntiAlias(flag);
            mTextPaintTopRight.setAntiAlias(flag);
            mTextPaintBottomLeft.setAntiAlias(flag);
            mTextPaintBottomRight.setAntiAlias(flag);
            mTextPaintBottomRight2.setAntiAlias(flag);
            mTextPaintAmPm.setAntiAlias(flag);
        }
        
        private void setColor(int color) {
            mTextPaintMiddle.setColor(color);
            mTextPaintLeft.setColor(color);
            mTextPaintRight.setColor(color);
            mTextPaintTopLeft.setColor(color);
            mTextPaintTopRight.setColor(color);
            mTextPaintBottomLeft.setColor(color);
            mTextPaintBottomRight.setColor(color);
            mTextPaintBottomRight2.setColor(color);
            mTextPaintAmPm.setColor(color);
        }
        
        private void setAlpha(int alpha) {
            mTextPaintMiddle.setAlpha(alpha);
            mTextPaintLeft.setAlpha(alpha);
            mTextPaintRight.setAlpha(alpha);
            mTextPaintTopLeft.setAlpha(alpha);
            mTextPaintTopRight.setAlpha(alpha);
            mTextPaintBottomLeft.setAlpha(alpha);
            mTextPaintBottomRight.setAlpha(alpha);
            mTextPaintBottomRight2.setAlpha(alpha);
            mTextPaintAmPm.setAlpha(alpha);
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
                    // float xx = x;
                    // float yy = y;
                    multiTapEvent(MULTI_TAP_TYPE_TIME);
                    break;
            }
            if (!mAmbient) {
                mScreenTimeExtender.clearIdle();
            }
        }

        private final int MULTI_TAP_TYPE_TIME = 1;
        private final int MULTI_TAP_TYPE_TOP = 2;
        private final int MULTI_TAP_TYPE_BOTTOM = 3;

        public void onMultiTapCommand(int type, int numberOfTaps) {
            switch (type) {
                case MULTI_TAP_TYPE_TOP:
                    break;
                case MULTI_TAP_TYPE_BOTTOM:
                    break;
                case MULTI_TAP_TYPE_TIME:
                    switch (numberOfTaps) {
                        case 2:
                            mThemeColor = mThemeColor.nextThemeColor();
                            saveThemePreference();
                            updateProperties();
                            mBackgroundBitmap = null;
                            invalidate();
                            break;
                        case 3:
                            mThemeMode = mThemeMode.nextThemeMode();
                            saveThemePreference();
                            updateProperties();
                            mBackgroundBitmap = null;
                            invalidate();
                            break;
                        case 4:
                            if (mEmulatorMode) {
                                mDemoTimeMode = !mDemoTimeMode;
                                mBackgroundBitmap = null;
                                updateProperties();
                                invalidate();
                            }
                            break;
                    }
            }
        }

        private MultiTapHandler mMultiTapHandler = null;

        private void multiTapEvent(int type) {
            if (mMultiTapHandler == null) {
                mMultiTapHandler = new MultiTapHandler(this);
            }
            mMultiTapHandler.onTapEvent(type);
        }

        private void cancelMultiTap() {
            if (mMultiTapHandler != null) {
                mMultiTapHandler.cancel();
            }
        }

        private boolean is24Hour() {
            if (mDemoTimeMode) {
                return false;
            }
            int is24HourInt;
            try {
                is24HourInt = Settings.System.getInt(getContentResolver(), Settings.System.TIME_12_24);
            } catch (Settings.SettingNotFoundException e) {
                is24HourInt = -1;
            }
            if (is24HourInt == 24) {
                return true;
            } else if (is24HourInt == 12) {
                return false;
            } else {
                return DateFormat.is24HourFormat(LEDWatchFace.this);
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            createBackgroundBitmap(canvas.getWidth(), canvas.getHeight());

            // Draw the background.
            if (mLowBitAmbient) {
                canvas.drawColor(Color.BLACK);
            } else {
                if (mBackgroundBitmap != null && mFaintAlpha > 0) {
                    canvas.drawBitmap(mBackgroundBitmap, 0, 0, null);
                } else {
                    canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                }
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
            if (mDemoTimeMode) {
                mCalendar.set(2013, 5 /* JUN */, 30, 10, 58, 50);
            } else {
                mCalendar.setTimeInMillis(now);
            }

            int hour12 = mCalendar.get(Calendar.HOUR);
            if (hour12 == 0) {
                hour12 = 12;
            }
            int hour24 = mCalendar.get(Calendar.HOUR_OF_DAY);
            int minute = mCalendar.get(Calendar.MINUTE);
            int second = mCalendar.get(Calendar.SECOND);
            int millis = mCalendar.get(Calendar.MILLISECOND);
            int dayOfWeek = mCalendar.get(Calendar.DAY_OF_WEEK);
            int dayOfMonth = mCalendar.get(Calendar.DAY_OF_MONTH);

            boolean isPM = mCalendar.get(Calendar.AM_PM) == Calendar.PM;
            boolean blink = millis >= 400;

            String textTopLeft = null;     // day of week
            String textTopRight = null;    // day of momth
            String textBottomLeft = null;  // battery percentage
            String textBottomRight = null; // seconds
            String textLeft = null;
            String textRight = null;

            if (mDemoTimeMode) {
                batteryPercentage = 89;
                blink = false;
            }

            // time of day
            if (is24Hour()) {
                textLeft = String.format(Locale.getDefault(), "%02d", hour24);
                textRight = String.format(Locale.getDefault(), "%02d", minute);
            } else {
                textLeft = String.format(Locale.getDefault(), "%02d", hour12);
                textRight = String.format(Locale.getDefault(), "%02d", minute);

                // replace leading zero with space (all-segments-off)
                if (textLeft.charAt(0) == '0') {
                    textLeft = "!" + textLeft.substring(1);
                }

                if (isPM) {
                    canvas.drawText("P", mXOffsetAmPm, mYOffsetPm, mTextPaintAmPm);
                } else {
                    canvas.drawText("A", mXOffsetAmPm, mYOffsetAm, mTextPaintAmPm);
                }
            }

            // seconds
            if (mShowSeconds) {
                if (mAmbient) {
                    textBottomRight = new String(Character.toChars(0xf000 + second / 10));
                } else {
                    textBottomRight = String.format(Locale.getDefault(), "!%02d", second);
                }
            }

            if (mShowBatteryLevel) {
                if (batteryPercentage < 0) {
                    if (m100SansPercent) {
                        textBottomLeft = "???";
                    } else {
                        textBottomLeft = "????";
                    }
                } else if (batteryPercentage < 100) {
                    if (m100SansPercent) {
                        textBottomLeft = String.format(Locale.getDefault(), "%2d%%", batteryPercentage);
                    } else {
                        textBottomLeft = String.format(Locale.getDefault(), "%3d%%", batteryPercentage);
                    }
                    textBottomLeft = textBottomLeft.replaceAll(" ", "!"); // " 9%" => "!9%"
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
                        textBottomLeft = "????";
                    }
                }
            }

            if (mShowDayOfWeek) {
                textTopLeft = mCalendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());

                /* remove accents thx https://stackoverflow.com/a/3322174 */
                textTopLeft = Normalizer.normalize(textTopLeft, Normalizer.Form.NFD);
                textTopLeft = textTopLeft.replaceAll("\\p{M}", "");

                if (textTopLeft.length() > 3) {
                    textTopLeft = textTopLeft.substring(0, 3);
                }

                textTopLeft = textTopLeft.toUpperCase();
            }

            if (mShowDayOfMonth) {
                textTopRight = String.format(Locale.getDefault(), "!%2d", dayOfMonth);
                textTopRight = textTopRight.replace(" ", "!");
            }

            textLeft = addLetterSpacing(textLeft, mLetterSpacing);
            textRight = addLetterSpacing(textRight, mLetterSpacing);
            if (mShowDayOfWeek && textTopLeft != null) {
                textTopLeft = addLetterSpacing(textTopLeft, mLetterSpacing2, Paint.Align.RIGHT);
            }
            if (mShowDayOfMonth && textTopRight != null) {
                textTopRight = addLetterSpacing(textTopRight, mLetterSpacing2, Paint.Align.LEFT);
            }
            if (mShowBatteryLevel && textBottomLeft != null) {
                textBottomLeft = addLetterSpacing(textBottomLeft, mLetterSpacing2, Paint.Align.RIGHT);
            }
            if (mShowSeconds && textBottomRight != null && !mAmbient) {
                textBottomRight = addLetterSpacing(textBottomRight, mLetterSpacing2, Paint.Align.LEFT);
            }

            canvas.drawText(textLeft, mXOffsetLeft, mYOffsetMiddle, mTextPaintLeft);
            canvas.drawText(textRight, mXOffsetRight, mYOffsetMiddle, mTextPaintRight);
            if (!(mBlinkingColon && blink && !mAmbient)) {
                canvas.drawText("" + colonCharacter(), mXOffsetMiddle, mYOffsetMiddle, mTextPaintMiddle);
            }
            if (mShowDayOfWeek && textTopLeft != null) {
                textTopLeft = textTopLeft.replaceAll(",", "");
                canvas.drawText(textTopLeft, mXOffsetTopLeft, mYOffsetTop, mTextPaintTopLeft);
            }
            if (mShowDayOfMonth && textTopRight != null) {
                textTopRight = textTopRight.replaceAll(",", "");
                canvas.drawText(textTopRight, mXOffsetTopRight, mYOffsetTop, mTextPaintTopRight);
            }
            if (mShowBatteryLevel && textBottomLeft != null) {
                textBottomLeft = textBottomLeft.replaceAll(",", "");
                canvas.drawText(textBottomLeft, mXOffsetBottomLeft, mYOffsetBottom, mTextPaintBottomLeft);
            }
            if (mShowSeconds && textBottomRight != null) {
                textBottomRight = textBottomRight.replaceAll(",", "");
                if (mAmbient) {
                    canvas.drawText(textBottomRight, mXOffsetBottomRight2, mYOffsetBottom, mTextPaintBottomRight2);
                } else {
                    canvas.drawText(textBottomRight, mXOffsetBottomRight, mYOffsetBottom, mTextPaintBottomRight);
                }
            }

            if (!mAmbient) {
                mScreenTimeExtender.checkIdle();
            }
        }

        private void createBackgroundBitmap(int width, int height) {
            if (mLowBitAmbient) {
                return;
            }
            if (mBackgroundBitmap != null) {
                return;
            }
            if (mFaintAlpha <= 0) {
                return;
            }

            String allSegmentsOnLeft = leftSegments();
            String allSegmentsOnRight = rightSegments();
            String allSegmentsOnTopLeft = topLeftSegments();
            String allSegmentsOnTopRight = topRightSegments();
            String allSegmentsOnBottomLeft = bottomLeftSegments();
            String allSegmentsOnBottomRight = bottomRightSegments();

            allSegmentsOnLeft = addLetterSpacing(allSegmentsOnLeft, mLetterSpacing);
            allSegmentsOnRight = addLetterSpacing(allSegmentsOnRight, mLetterSpacing);
            allSegmentsOnTopLeft = addLetterSpacing(allSegmentsOnTopLeft, mLetterSpacing2);
            allSegmentsOnTopRight = addLetterSpacing(allSegmentsOnTopRight, mLetterSpacing2);
            allSegmentsOnBottomLeft = addLetterSpacing(allSegmentsOnBottomLeft, mLetterSpacing2);
            allSegmentsOnBottomRight = addLetterSpacing(allSegmentsOnBottomRight, mLetterSpacing2);

            if (mAmbient) {
                allSegmentsOnBottomRight = "\uf006";
            }

            Canvas backgroundCanvas = new Canvas();
            mBackgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            backgroundCanvas.setBitmap(mBackgroundBitmap);
            backgroundCanvas.drawRect(0, 0, width, height, mBackgroundPaint);
            
            setAntiAlias(!mLowBitAmbient);
            setColor(mForegroundColor);
            setAlpha(mFaintAlpha);

            backgroundCanvas.drawText(allSegmentsOnLeft, mXOffsetLeft, mYOffsetMiddle, mTextPaintLeft);
            backgroundCanvas.drawText(allSegmentsOnRight, mXOffsetRight, mYOffsetMiddle, mTextPaintRight);
            if (hasFullWidthColon()) {
                backgroundCanvas.drawText("8", mXOffsetMiddle, mYOffsetMiddle, mTextPaintMiddle);
                if (colonCharacter() == ':') {
                    backgroundCanvas.drawText(":", mXOffsetMiddle, mYOffsetMiddle, mTextPaintMiddle);
                }
            } else {
                backgroundCanvas.drawText(":", mXOffsetMiddle, mYOffsetMiddle, mTextPaintMiddle);
            }

            backgroundCanvas.drawText(allSegmentsOnTopLeft, mXOffsetTopLeft, mYOffsetTop, mTextPaintTopLeft);
            backgroundCanvas.drawText(allSegmentsOnTopRight, mXOffsetTopRight, mYOffsetTop, mTextPaintTopRight);
            backgroundCanvas.drawText(allSegmentsOnBottomLeft, mXOffsetBottomLeft, mYOffsetBottom, mTextPaintBottomLeft);
            if (mAmbient) {
                backgroundCanvas.drawText(allSegmentsOnBottomRight, mXOffsetBottomRight2, mYOffsetBottom, mTextPaintBottomRight2);
            } else {
                backgroundCanvas.drawText(allSegmentsOnBottomRight, mXOffsetBottomRight, mYOffsetBottom, mTextPaintBottomRight);
            }
            if (!is24Hour()) {
                backgroundCanvas.drawText("A", mXOffsetAmPm, mYOffsetAm, mTextPaintAmPm);
                backgroundCanvas.drawText("P", mXOffsetAmPm, mYOffsetPm, mTextPaintAmPm);
            }

            setAlpha(255);
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
    }

    private static float getTextWidthDifference(String s1, String s2, Paint textPaint) {
        Rect bounds1 = new Rect();
        Rect bounds2 = new Rect();
        textPaint.getTextBounds(s1, 0, s1.length(), bounds1);
        textPaint.getTextBounds(s2, 0, s2.length(), bounds2);
        return 0f + bounds2.width() - bounds1.width();
    }

    private static float getTextWidth(String s, Paint textPaint) {
        return textPaint.getRunAdvance(
                s, 0, s.length(), 0, s.length(), false, s.length()
        );
    }

    /**
     * Insert at each point between two characters a number of spaces,
     * returning the resulting string.
     * <p>
     * If textAlign is Paint.Align.LEFT, also add pad spaces before the first character.
     * <p>
     * If textAlign is Paint.Align.RIGHT, also add pad spaces after the last character.
     *
     * @param s         the original string
     * @param spacing   the number of spaces to insert at each location
     * @param textAlign Paint.Align.LEFT, CENTER, or RIGHT
     * @return the resulting string
     */
    private static String addLetterSpacing(String s, int spacing, Paint.Align textAlign) {
        return addLetterSpacing(s, spacing, textAlign, ' ');
    }

    private static String addLetterSpacing(String s, int spacing, char space) {
        return addLetterSpacing(s, spacing, Paint.Align.CENTER, space);
    }

    private static String addLetterSpacing(String s, int spacing) {
        return addLetterSpacing(s, spacing, Paint.Align.CENTER, ' ');
    }

    private static String addLetterSpacing(String s, int spacing, Paint.Align textAlign, char space) {
        if (spacing < 1) {
            return s;
        }
        boolean insertSpaceAtEnd = false;
        boolean insertSpaceAtBeginning = false;
        if (textAlign == Paint.Align.LEFT) {
            insertSpaceAtBeginning = true;
        } else if (textAlign == Paint.Align.RIGHT) {
            insertSpaceAtEnd = true;
        }

        int length = s.length();
        int halfSpacing = (int) Math.round(spacing / 2f);
        StringBuffer resultBuffer = new StringBuffer(s);

        if (insertSpaceAtEnd) {
            for (int j = 1; j <= halfSpacing; j += 1) {
                resultBuffer.append(space);
            }
        }
        for (int i = length - 1; i >= 1; i -= 1) {
            for (int j = 1; j <= spacing; j += 1) {
                resultBuffer.insert(i, space);
            }
        }
        if (insertSpaceAtBeginning) {
            for (int j = 1; j <= halfSpacing; j += 1) {
                resultBuffer.insert(0, space);
            }
        }
        return resultBuffer.toString();
    }

    private static float getTextHeight(String testString, Paint paint) {
        Rect bounds = new Rect();
        paint.getTextBounds(testString, 0, 1, bounds);
        return bounds.height();
    }

}
