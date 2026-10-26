package com.nurdun.swipecode;

public final class AppSettings {
    public int keyHeightDp = 56;
    public int triggerDp = 12;
    public boolean haptics = true;
    public float returnDistanceDp() { return triggerDp * 2f / 3f; }
}
