package com.nurdun.swipecode;

import android.content.Context;
import android.content.SharedPreferences;
import com.nurdun.swipecode.core.KeyboardLayout;
import com.nurdun.swipecode.core.LayoutCodec;

public final class KeyboardStore {
    private final SharedPreferences preferences;
    public KeyboardStore(Context context) { preferences = context.getSharedPreferences("swipe_code_v1", Context.MODE_PRIVATE); }
    public KeyboardLayout loadLayout() { return LayoutCodec.decode(preferences.getString("layout", null)); }
    public void saveLayout(KeyboardLayout layout) { preferences.edit().putString("layout", LayoutCodec.encode(layout)).apply(); }
    public AppSettings loadSettings() {
        AppSettings settings = new AppSettings();
        settings.keyHeightDp = Math.max(44, Math.min(72, preferences.getInt("key_height", 56)));
        settings.triggerDp = Math.max(8, Math.min(24, preferences.getInt("trigger", 12)));
        settings.haptics = preferences.getBoolean("haptics", true);
        return settings;
    }
    public void saveSettings(AppSettings settings) {
        preferences.edit().putInt("key_height", settings.keyHeightDp).putInt("trigger", settings.triggerDp)
                .putBoolean("haptics", settings.haptics).apply();
    }
}
