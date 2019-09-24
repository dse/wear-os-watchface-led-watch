package com.webonastick.util;

import android.graphics.Color;

public class HSPColor {
    private float h = 0f;
    private float s = 0f;
    private float p = 0f;
    private float r = 0f;
    private float g = 0f;
    private float b = 0f;

    /* http://alienryderflex.com/hsp.html but with ITU-R BT.709 coefficients */
    private static final float PR = 0.2126f;
    private static final float PG = 0.7152f;
    private static final float PB = 0.0722f;

    public HSPColor(float h, float s, float p) {
        this.h = window(h);
        this.s = window(s);
        this.p = window(p);
        this.calculateRGB();
    }
    public HSPColor(int h, int s, int p) {
        this.h = window(h) / 255f;
        this.s = window(s) / 255f;
        this.p = window(p) / 255f;
        this.calculateRGB();
    }
    public HSPColor() {
        this.calculateRGB();
    }

    /* http://alienryderflex.com/hsp.html */
    private void calculateRGB() {
        float part;
        float minOverMax = 1f - s;

        float h = this.h;
        float p = this.p;

        if (minOverMax > 0f) {
            if (h < 1f/6f) {  // R > G > B
                h = 6f * (h - 0f/6f);
                part = 1f + h * (1f / minOverMax - 1f);
                b = p / (float)Math.sqrt(PR / minOverMax / minOverMax + PG * part * part + PB);
                r = b / minOverMax;
                g = b + h * (r - b);
            } else if (h < 2f/6f) {  // G > R > B
                h = 6f * (-h + 2f/6f);
                part = 1f + h * (1f / minOverMax - 1f);
                b = p / (float)Math.sqrt(PG / minOverMax / minOverMax + PR * part * part + PB);
                g = b / minOverMax;
                r = b + h * (g - b);
            } else if (h < 3f/6f) {  // G > B > R
                h = 6f * (h - 2f/6f);
                part = 1f + h * (1f / minOverMax - 1f);
                r = p / (float)Math.sqrt(PG / minOverMax / minOverMax + PB * part * part + PR);
                g = r / minOverMax;
                b = r + h * (g - r);
            } else if (h < 4f/6f) {  // B > G > R
                h = 6f * (-h + 4f/6f);
                part = 1f + h * (1f / minOverMax - 1f);
                r = p / (float)Math.sqrt(PB / minOverMax / minOverMax + PG * part * part + PR);
                b = r / minOverMax;
                g = r + h * (b - r);
            } else if (h < 5f/6f) {  // B > R > G
                h = 6f * (h - 4f/6f);
                part = 1f + h * (1f / minOverMax - 1f);
                g = p / (float)Math.sqrt(PB / minOverMax / minOverMax + PR * part * part + PG);
                b = g / minOverMax;
                r = g + h * (b - g);
            } else {  // R > B > G
                h = 6f * (-h + 6f/6f);
                part = 1f + h * (1f / minOverMax - 1f);
                g = p / (float)Math.sqrt(PR / minOverMax / minOverMax + PB * part * part + PG);
                r = g / minOverMax;
                b = g + h * (r - g);
            }
        } else {
            if (h < 1f/6f) {  // R > G > B
                h = 6f * (h - 0f/6f);
                r = (float)Math.sqrt(p * p / (PR + PG * h * h));
                g = r * h;
                b = 0f;
            } else if (h < 2f/6f) {  // G > R > B
                h = 6f * (-h + 2f/6f);
                g = (float)Math.sqrt(p * p / (PG + PR * h * h));
                r = g * h;
                b = 0f;
            } else if (h < 3f/6f) {  // G > B > R
                h = 6f * (h - 2f/6f);
                g = (float)Math.sqrt(p * p / (PG + PB * h * h));
                b = g * h;
                r = 0f;
            } else if (h < 4f/6f) {  // B > G > R
                h = 6f * (-h + 4f/6f);
                b = (float)Math.sqrt(p * p / (PB + PG * h * h));
                g = b * h;
                r = 0f;
            } else if (h < 5f/6f) {  // B > R > G
                h = 6f * (h - 4f/6f);
                b = (float)Math.sqrt(p * p / (PB + PR * h * h));
                r = b * h;
                g = 0f;
            } else {  // R > B > G
                h = 6f * (-h + 6f/6f);
                r = (float)Math.sqrt(p * p / (PR + PB * h * h));
                b = r * h;
                g = 0f;
            }
        }
        r = window(r);
        g = window(g);
        b = window(b);
    }
    
    private void calculateHSP() {
        p = (float)Math.sqrt(r * r * PR + g * g * PG + b * b * PB);
        if (r == g && r == b) {
            h = 0f;
            s = 0f;
            return;
        }
        if (r >= g && r >= b) {
            /* r is largest */
            if (b >= g) {
                h = 1f - 1f/6f * (b - g) / (r - g); s = 1f - g / r;
            } else {
                h = 1f/6f * (g - b) / (r - b); s = 1f - b / r;
            }
        } else if (g >= r && g >= b) {
            /* g is largest */
            if (r >= b) {
                h = 2f/6f - 1f/6f * (r - b) / (g - b); s = 1f - b / g;
            } else {
                h = 2f/6f + 1f/6f * (b - r) / (g - r); s = 1f - r / g;
            }
        } else {
            /* b is largest */
            if (g >= r) {
                h = 4f/6f - 1f/6f * (g - r) / (b - r); s = 1f - r / b;
            } else {
                h = 4f/6f + 1f/6f * (r - g) / (b - g); s = 1f - g / b;
            }
        }
    }
    
    public void setRGB(float r, float g, float b) {
        this.r = window(r);
        this.g = window(g);
        this.b = window(b);
        calculateHSP();
    }
    public void setRGB(int r, int g, int b) {
        setRGB(window(r) / 255f, window(g) / 255f, window(b) / 255f);
    }
    public void setRGB(int color) {
        setRGB(Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f);
    }
    
    public void setRed(float r) {
        this.r = window(r);
        calculateHSP();
    }
    public void setGreen(float g) {
        this.g = window(g);
        calculateHSP();
    }
    public void setBlue(float b) {
        this.b = window(b);
        calculateHSP();
    }

    public void setRed(int r) {
        setRed(window(r) / 255f);
    }
    public void setGreen(int g) {
        setRed(window(g) / 255f);
    }
    public void setBlue(int b) {
        setBlue(window(b) / 255f);
    }

    public void setHSP(float h, float s, float p) {
        this.h = window(h);
        this.s = window(s);
        this.p = window(p);
        calculateRGB();
    }
    public void setHSP(int h, int s, int p) {
        setHSP(window(h) / 255f, window(s) / 255f, window(p) / 255f);
    }

    public void setHue(float h) {
        this.h = window(h);
        calculateRGB();
    }
    public void setSaturation(float s) {
        this.s = window(s);
        calculateRGB();
    }
    public void setPerceivedBrightness(float p) {
        this.p = window(p);
        calculateRGB();
    }

    public void setHue(int h) {
        setHue(window(h) / 255f);
    }
    public void setSaturation(int s) {
        setSaturation(window(s) / 255f);
    }
    public void setPerceivedBrightness(int p) {
        setPerceivedBrightness(window(p) / 255f);
    }
    
    public float red() {
        return r;
    }
    public float green() {
        return g;
    }
    public float blue() {
        return b;
    }
    public float hue() {
        return h;
    }
    public float saturation() {
        return s;
    }
    public float perceivedBrightness() {
        return p;
    }

    public static HSPColor fromRGB(float r, float g, float b) {
        HSPColor hspColor = new HSPColor();
        hspColor.setRGB(r, g, b);
        return hspColor;
    }
    public static HSPColor fromRGB(int r, int g, int b) {
        return HSPColor.fromRGB(
                window(r) / 255f, 
                window(g) / 255f, 
                window(b) / 255f
        );
    }
    public static HSPColor fromRGB(int color) {
        return HSPColor.fromRGB(
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f
        );
    }

    public static float getPerceivedBrightnessFromRGB(int color) {
        return HSPColor.fromRGB(color).perceivedBrightness();
    }
    public static float getPerceivedBrightnessFromRGB(int r, int g, int b) {
        return HSPColor.fromRGB(r, g, b).perceivedBrightness();
    }
    public static float getPerceivedBrightnessFromRGB(float r, float g, float b) {
        return HSPColor.fromRGB(r, g, b).perceivedBrightness();
    }
    
    private static int window(int x) {
        if (x < 0) {
            return 0;
        }
        if (x > 255) {
            return 255;
        }
        return x;
    }
    private static float window(float x) {
        if (x < 0) {
            return 0;
        }
        if (x > 1) {
            return 1;
        }
        return x;
    }
}
