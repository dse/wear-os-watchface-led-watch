package com.webonastick.watchface.ledwatch;

public class Utility {
    public enum LEDWatchThemeMode {
        LED("foreground", "led"),
        LCD("background", "lcd"),
        VINTAGE_LED("foreground", "vintage_led");

        protected final String resourceName;
        protected final String colorResourceType;

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

        protected final String resourceName;

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

        protected final String filenamePortion;

        DSEGFontSize(String filenamePortion) {
            this.filenamePortion = filenamePortion;
        }

        public String getFilenamePortion() {
            return filenamePortion;
        }
    }

    public enum DSEGFontSegments {
        SEVEN("7"),
        FOURTEEN("14");

        protected final String filenamePortion;

        DSEGFontSegments(String filenamePortion) {
            this.filenamePortion = filenamePortion;
        }

        public String getFilenamePortion() {
            return filenamePortion;
        }
    }

    public enum DSEGFontFamily {
        CLASSIC("Classic"),
        MODERN("Modern");

        protected final String filenamePortion;

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

        protected final String filenamePortion;

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

        protected final String filenamePortion;

        DSEGFontStyle(String filenamePortion) {
            this.filenamePortion = filenamePortion;
        }

        public String getFilenamePortion() {
            return filenamePortion;
        }
    }

    enum Region {
        TOP,
        MIDDLE,
        BOTTOM
    }
}
