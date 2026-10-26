package com.nurdun.swipecode;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

public final class Palette {
    public final boolean dark;
    public final int background, surface, keyboard, key, ink, muted, accent, accentSoft, line, accentInk;
    public Palette(Context context) {
        dark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        background = color(dark ? "#111C19" : "#F5F7F3");
        surface = color(dark ? "#1A2924" : "#FFFFFF");
        keyboard = color(dark ? "#182720" : "#E6EDE6");
        key = color(dark ? "#2A3C33" : "#FFFFFF");
        ink = color(dark ? "#EEF7EF" : "#17392C");
        muted = color(dark ? "#ADC3B5" : "#647B6C");
        accent = color(dark ? "#A8E2B9" : "#216A48");
        accentSoft = color(dark ? "#314D3B" : "#D5EBCF");
        line = color(dark ? "#3A4C41" : "#D8E1D6");
        accentInk = color(dark ? "#143321" : "#FFFFFF");
    }
    private static int color(String hex) { return Color.parseColor(hex); }
}
