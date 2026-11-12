package com.vladosiwe.gorewidow.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Shared drawing helpers for the Gorewidow's dark, crimson-trimmed panels and stat bars. */
public final class UiDraw {
    public static final int PANEL = 0xD0101317;
    public static final int PANEL_LIGHT = 0xD01A1F25;
    public static final int TRIM = 0xFF8E1E28;
    public static final int TEXT = 0xFFF0ECE4;
    public static final int TEXT_DIM = 0xFF9EA4AC;
    public static final int HEALTH = 0xFFD8403A;
    public static final int STAMINA = 0xFFE7C04A;
    public static final int FOOD = 0xFFE58A3A;
    public static final int WEIGHT = 0xFF9AA6B2;
    public static final int MELEE = 0xFFC9506E;
    public static final int SPEED = 0xFF6FB6FF;
    public static final int XP = 0xFF7FE07A;
    public static final int TAMING = 0xFF4FD0C8;
    public static final int EFFECTIVENESS = 0xFFB98CFF;

    private UiDraw() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, TRIM);
        g.fill(x, y + h - 1, x + w, y + h, TRIM);
        g.fill(x, y, x + 1, y + h, TRIM);
        g.fill(x + w - 1, y, x + w, y + h, TRIM);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x, y, x + w, y + h, 0xFF050607);
        int filled = Math.round((w - 2) * Mth.clamp(fraction, 0, 1));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
            g.fill(x + 1, y + 1, x + 1 + filled, y + 2, brighten(color));
        }
    }

    public static void labelledBar(GuiGraphics g, Font font, int x, int y, int w, String label, String value, float fraction, int color) {
        g.drawString(font, label, x, y, TEXT_DIM, false);
        g.drawString(font, value, x + w - font.width(value), y, TEXT, false);
        bar(g, x, y + 9, w, 4, fraction, color);
    }

    public static int brighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 50);
        int gr = Math.min(255, ((color >> 8) & 0xFF) + 50);
        int b = Math.min(255, (color & 0xFF) + 50);
        return (color & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    public static String num(float v) {
        return v >= 100 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
