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

    private static final int DSEG_FONT_STYLE_NORMAL = 1;
    private static final int DSEG_FONT_STYLE_ITALIC = 2;

    private static final int DSEG_FONT_WEIGHT_LIGHT = 1;
    private static final int DSEG_FONT_WEIGHT_NORMAL = 2;
    private static final int DSEG_FONT_WEIGHT_BOLD = 3;

    private static final int DSEG_FONT_FAMILY_CLASSIC = 1;
    private static final int DSEG_FONT_FAMILY_MODERN = 2;

    private static final int DSEG_FONT_SIZE_MINI = 1;
    private static final int DSEG_FONT_SIZE_REGULAR = 2;

    private static final int TEXT_ALIGN_LEFT = 1;
    private static final int TEXT_ALIGN_CENTER = 2;
    private static final int TEXT_ALIGN_RIGHT = 3;

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
        private float mXOffsetBottomRight2;
        private float mYOffsetBottomRight2;

        private float mXOffsetAmPm;
        private float mYOffsetAm;
        private float mYOffsetPm;

        private Paint mBackgroundPaint = null;
        private Paint mTextPaintMiddle = null;
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
        private final boolean m100SansPercent = true;

        private Typeface mSixthsOfAPieTypeface;

        private boolean mDemoTimeMode = false;
        private boolean mEmulatorMode = false;

        private float mPixelDensity;

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
         *
         * This value will be higher for darker colors, and lower for
         * brighter colors.
         */
        private int getFaintAlphaFromForeground(int color) {
            float brightness = HSPColor.fromRGB(color).perceivedBrightness();
            float relFaintBrightness = LED_FAINT / brightness;
            int result = (int)(relFaintBrightness * 255f + 0.5f);
            return result;
        }

        /**
         * Calculate the alpha transparency at which to display the
         * "faint" segments so that they are visible enough.
         *
         * This value will be higher for darker colors, and lower for
         * brighter colors.
         */
        private int getFaintAlphaFromBackground(int color) {
            float brightness = HSPColor.fromRGB(color).perceivedBrightness();
            float newBrightness = brightness - LCD_FAINT;
            float alpha = (brightness - newBrightness) / brightness;
            alpha = (float)Math.min(alpha, 0.05f);
            int result = (int)(alpha * 255f + 0.5f);
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

        private float getSmallerTextSizeRatio() {
            switch (mThemeMode) {
                case LED:
                case LCD:
                    return 0.5f;
                case VINTAGE_LED:
                    return 0.625f;
                default:
                    return 0.5f;
            }
        }

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
                    mDSEGFontFamily = DSEGFontFamily.CLASSIC;
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

        private SharedPreferences mSharedPreferences;
        private AmbientRefresher mAmbientRefresher;
        private ScreenTimeExtender screenTimeExtender;

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

            screenTimeExtender = new ScreenTimeExtender(LEDWatchFace.this);
            screenTimeExtender.clearIdle();
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
                screenTimeExtender.clearIdle();
            }
        }

        private void updateSizeBasedProperties() {
            Resources resources = LEDWatchFace.this.getResources();

            mXOffsetMiddle = mSurfaceWidth / 2f;
            mXOffsetTopLeft = mSurfaceWidth / 2f;
            mXOffsetTopRight = mSurfaceWidth / 2f;
            mXOffsetBottomLeft = mSurfaceWidth / 2f;
            mXOffsetBottomRight = mSurfaceWidth / 2f;

            // compute text size
            Rect bounds = new Rect();
            mTextPaintMiddle.setTextSize(mSurfaceWidth); // for calculatory purposes
            String sampleText = "88:88";
            if (mLetterSpacing > 0) {
                sampleText = addLetterSpacing(sampleText, mLetterSpacing, TEXT_ALIGN_CENTER);
            }
            mTextPaintMiddle.getTextBounds(sampleText, 0, sampleText.length(), bounds);
            float textSize = (mSurfaceWidth - 16 * mPixelDensity) / bounds.width() * bounds.height();

            int width = bounds.width();
            int height = bounds.height();
            mXOffsetAmPm = 8 * mPixelDensity;
            if (mIsRound || mDemoTimeMode) {
                float angle = (float) Math.atan2(height, width);
                float cosine = (float) Math.cos(angle);
                textSize = textSize * cosine;
                mXOffsetAmPm = mSurfaceWidth / 2f - (mSurfaceWidth / 2f - 8 * mPixelDensity) * cosine;
            }

            float textSizeAmPm = (textSize / 4f) / 0.7f;

            mTextPaintMiddle.setTextSize(textSize);
            mTextPaintTopLeft.setTextSize(textSize * mSmallerTextSizeRatio);
            mTextPaintTopRight.setTextSize(textSize * mSmallerTextSizeRatio);
            mTextPaintBottomLeft.setTextSize(textSize * mSmallerTextSizeRatio);
            mTextPaintBottomRight.setTextSize(textSize * mSmallerTextSizeRatio);
            mTextPaintBottomRight2.setTextSize(textSize * mSmallerTextSizeRatio);
            mTextPaintAmPm.setTextSize(textSizeAmPm);

            float textAscent     = -textSize;
            float textAscentAmPm = -textSizeAmPm * 0.7f;

            float mLineSpacingDp = textSize * mSmallerTextSizeRatio * 0.5f;

            mYOffsetMiddle = mSurfaceHeight / 2f - textAscent / 2f;
            mYOffsetTopLeft = mYOffsetMiddle + textAscent - mLineSpacingDp;
            mYOffsetTopRight = mYOffsetMiddle + textAscent - mLineSpacingDp;
            mYOffsetBottomLeft = mYOffsetMiddle - textAscent * mSmallerTextSizeRatio + mLineSpacingDp;
            mYOffsetBottomRight = mYOffsetMiddle - textAscent * mSmallerTextSizeRatio + mLineSpacingDp;
            mYOffsetBottomRight2 = mYOffsetMiddle - textAscent * mSmallerTextSizeRatio + mLineSpacingDp;

            mYOffsetAm = mSurfaceHeight / 2f + textAscent / 4f - textAscentAmPm / 2f;
            mYOffsetPm = mSurfaceHeight / 2f - textAscent / 4f - textAscentAmPm / 2f;

            /* x offset for centered pie seconds */
            Rect bounds1 = new Rect();
            Rect bounds2 = new Rect();
            String text1 = addLetterSpacing("888", mLetterSpacing, TEXT_ALIGN_LEFT, ':');
            String text2 = addLetterSpacing("888", mLetterSpacing, TEXT_ALIGN_CENTER, ':');
            mTextPaintBottomRight.getTextBounds(text1, 0, text1.length(), bounds1);
            mTextPaintBottomRight.getTextBounds(text2, 0, text2.length(), bounds2);
            mXOffsetBottomRight2 = mXOffsetMiddle + bounds1.width();
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
            screenTimeExtender.clearIdle();
        }

        private void updateTextPaintProperties() {
            mTextPaintMiddle.setTextAlign(Paint.Align.CENTER);
            mTextPaintTopLeft.setTextAlign(Paint.Align.RIGHT);
            mTextPaintTopRight.setTextAlign(Paint.Align.LEFT);
            mTextPaintBottomLeft.setTextAlign(Paint.Align.RIGHT);
            mTextPaintBottomRight.setTextAlign(Paint.Align.LEFT);
            mTextPaintBottomRight2.setTextAlign(Paint.Align.RIGHT);
            mTextPaintMiddle.setTypeface(mSevenSegmentTypeface);
            mTextPaintTopLeft.setTypeface(mFourteenSegmentTypeface);
            mTextPaintTopRight.setTypeface(mSevenSegmentTypeface);
            mTextPaintBottomLeft.setTypeface(mFourteenSegmentTypeface);
            mTextPaintBottomRight.setTypeface(mSevenSegmentTypeface);
            mTextPaintBottomRight2.setTypeface(mSixthsOfAPieTypeface);
            if (mLowBitAmbient) {
                mTextPaintMiddle.setAntiAlias(false);
                mTextPaintTopLeft.setAntiAlias(false);
                mTextPaintTopRight.setAntiAlias(false);
                mTextPaintBottomLeft.setAntiAlias(false);
                mTextPaintBottomRight.setAntiAlias(false);
                mTextPaintBottomRight2.setAntiAlias(false);
            } else {
                mTextPaintMiddle.setAntiAlias(true);
                mTextPaintTopLeft.setAntiAlias(true);
                mTextPaintTopRight.setAntiAlias(true);
                mTextPaintBottomLeft.setAntiAlias(true);
                mTextPaintBottomRight.setAntiAlias(true);
                mTextPaintBottomRight2.setAntiAlias(true);
            }
            mTextPaintMiddle.setColor(mForegroundColor);
            mTextPaintTopLeft.setColor(mForegroundColor);
            mTextPaintTopRight.setColor(mForegroundColor);
            mTextPaintBottomLeft.setColor(mForegroundColor);
            mTextPaintBottomRight.setColor(mForegroundColor);
            mTextPaintBottomRight2.setColor(mForegroundColor);
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
                screenTimeExtender.clearIdle();
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

            String textMiddle = null;      // time of day
            String textTopLeft = null;     // day of week
            String textTopRight = null;    // day of momth
            String textBottomLeft = null;  // battery percentage
            String textBottomRight = null; // seconds

            if (mDemoTimeMode) {
                batteryPercentage = 89;
                blink = false;
            }

            // time of day
            if (is24Hour()) {
                textMiddle = String.format(Locale.getDefault(), "%02d:%02d", hour24, minute);
            } else {
                textMiddle = String.format(Locale.getDefault(), "%02d:%02d", hour12, minute);

                // replace leading zero with space (all-segments-off)
                if (textMiddle.charAt(0) == '0') {
                    textMiddle = "!" + textMiddle.substring(1);
                }

                if (isPM) {
                    canvas.drawText("P", mXOffsetAmPm, mYOffsetPm, mTextPaintAmPm);
                } else {
                    canvas.drawText("A", mXOffsetAmPm, mYOffsetAm, mTextPaintAmPm);
                }
            }

            if (mBlinkingColon && blink && !mAmbient) {
                textMiddle = textMiddle.replace(':', ' ');
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
                if (mShowSeconds && textBottomRight != null && !mAmbient) {
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
                if (mAmbient) {
                    canvas.drawText(textBottomRight, mXOffsetBottomRight2, mYOffsetBottomRight2, mTextPaintBottomRight2);
                } else {
                    canvas.drawText(textBottomRight, mXOffsetBottomRight, mYOffsetBottomRight, mTextPaintBottomRight);
                }
            }

            if (!mAmbient) {
                screenTimeExtender.checkIdle();
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

            // for faint all-segments-on characters
            String allSegmentsOnMiddle = "88:88";
            if (!is24Hour()) {
                allSegmentsOnMiddle = "18:88";
            }
            String allSegmentsOnTopLeft = "~~~";
            String allSegmentsOnTopRight = "888";
            String allSegmentsOnBottomLeft = "1~~~"; // "0%" to "100%"
            String allSegmentsOnBottomRight = "888";

            if (m100SansPercent) {
                allSegmentsOnBottomLeft = "~~~"; // "0%" to "99%" then "100"
            }

            if (mLetterSpacing > 0) {
                allSegmentsOnMiddle = addLetterSpacing(allSegmentsOnMiddle, mLetterSpacing, TEXT_ALIGN_CENTER);
                allSegmentsOnTopLeft = addLetterSpacing(allSegmentsOnTopLeft, mLetterSpacing, TEXT_ALIGN_RIGHT);
                allSegmentsOnTopRight = addLetterSpacing(allSegmentsOnTopRight, mLetterSpacing, TEXT_ALIGN_LEFT);
                allSegmentsOnBottomLeft = addLetterSpacing(allSegmentsOnBottomLeft, mLetterSpacing, TEXT_ALIGN_RIGHT);
                allSegmentsOnBottomRight = addLetterSpacing(allSegmentsOnBottomRight, mLetterSpacing, TEXT_ALIGN_LEFT);
            }

            if (mAmbient) {
                allSegmentsOnBottomRight = "\uf006";
            }

            Canvas backgroundCanvas = new Canvas();
            mBackgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            backgroundCanvas.setBitmap(mBackgroundBitmap);
            backgroundCanvas.drawRect(0, 0, width, height, mBackgroundPaint);

            mTextPaintMiddle.setAntiAlias(true);
            mTextPaintMiddle.setColor(mForegroundColor);
            mTextPaintTopLeft.setAntiAlias(true);
            mTextPaintTopLeft.setColor(mForegroundColor);
            mTextPaintTopRight.setAntiAlias(true);
            mTextPaintTopRight.setColor(mForegroundColor);
            mTextPaintBottomLeft.setAntiAlias(true);
            mTextPaintBottomLeft.setColor(mForegroundColor);
            mTextPaintBottomRight.setAntiAlias(true);
            mTextPaintBottomRight.setColor(mForegroundColor);
            mTextPaintBottomRight2.setAntiAlias(true);
            mTextPaintBottomRight2.setColor(mForegroundColor);
            mTextPaintAmPm.setAntiAlias(true);
            mTextPaintAmPm.setColor(mForegroundColor);
            mTextPaintMiddle.setAlpha(mFaintAlpha);
            mTextPaintTopLeft.setAlpha(mFaintAlpha);
            mTextPaintTopRight.setAlpha(mFaintAlpha);
            mTextPaintBottomLeft.setAlpha(mFaintAlpha);
            mTextPaintBottomRight.setAlpha(mFaintAlpha);
            mTextPaintBottomRight2.setAlpha(mFaintAlpha);
            mTextPaintAmPm.setAlpha(mFaintAlpha);
            backgroundCanvas.drawText(allSegmentsOnMiddle, mXOffsetMiddle, mYOffsetMiddle, mTextPaintMiddle);
            backgroundCanvas.drawText(allSegmentsOnTopLeft, mXOffsetTopLeft, mYOffsetTopLeft, mTextPaintTopLeft);
            backgroundCanvas.drawText(allSegmentsOnTopRight, mXOffsetTopRight, mYOffsetTopRight, mTextPaintTopRight);
            backgroundCanvas.drawText(allSegmentsOnBottomLeft, mXOffsetBottomLeft, mYOffsetBottomLeft, mTextPaintBottomLeft);
            if (mAmbient) {
                backgroundCanvas.drawText(allSegmentsOnBottomRight, mXOffsetBottomRight2, mYOffsetBottomRight2, mTextPaintBottomRight2);
            } else {
                backgroundCanvas.drawText(allSegmentsOnBottomRight, mXOffsetBottomRight, mYOffsetBottomRight, mTextPaintBottomRight);
            }
            if (!is24Hour()) {
                backgroundCanvas.drawText("A", mXOffsetAmPm, mYOffsetAm, mTextPaintAmPm);
                backgroundCanvas.drawText("P", mXOffsetAmPm, mYOffsetPm, mTextPaintAmPm);
            }
            mTextPaintMiddle.setAlpha(255);
            mTextPaintTopLeft.setAlpha(255);
            mTextPaintTopRight.setAlpha(255);
            mTextPaintBottomLeft.setAlpha(255);
            mTextPaintBottomRight.setAlpha(255);
            mTextPaintBottomRight2.setAlpha(255);
            mTextPaintAmPm.setAlpha(255);
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

    /**
     * Insert at each point between two characters a number of spaces,
     * returning the resulting string.
     * <p>
     * If textAlign is TEXT_ALIGN_LEFT, also pad spaces before the first character.
     * <p>
     * If textAlign is TEXT_ALIGN_RIGHT, also pad spaces after the last character.
     *
     * @param s         the original string
     * @param spacing   the number of spaces to insert at each location
     * @param textAlign TEXT_ALIGN_LEFT, _CENTER, or _RIGHT
     * @return the resulting string
     */
    private static String addLetterSpacing(String s, int spacing, int textAlign) {
        return addLetterSpacing(s, spacing, textAlign, ' ');
    }

    private static String addLetterSpacing(String s, int spacing, int textAlign, char space) {
        boolean insertSpaceAtEnd = false;
        boolean insertSpaceAtBeginning = false;
        if (textAlign == TEXT_ALIGN_LEFT) {
            insertSpaceAtBeginning = true;
        } else if (textAlign == TEXT_ALIGN_RIGHT) {
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

    private static float getCapHeight(Paint paint) {
        Rect bounds = new Rect();
        paint.getTextBounds("X", 0, 1, bounds);
        return bounds.height();
    }

}
