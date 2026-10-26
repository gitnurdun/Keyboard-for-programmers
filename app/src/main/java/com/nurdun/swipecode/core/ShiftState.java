package com.nurdun.swipecode.core;

public final class ShiftState {
    public enum Mode { OFF, ONCE, LOCKED }
    private Mode mode = Mode.OFF;
    private long lastTap = -1000;
    public void tap(long now) {
        if (mode == Mode.LOCKED) mode = Mode.OFF;
        else if (now - lastTap >= 0 && now - lastTap <= 300 && mode == Mode.ONCE) mode = Mode.LOCKED;
        else mode = mode == Mode.OFF ? Mode.ONCE : Mode.OFF;
        lastTap = now;
    }
    public String letter(char lower) { return String.valueOf(mode == Mode.OFF ? lower : Character.toUpperCase(lower)); }
    public void consumeLetter() { if (mode == Mode.ONCE) mode = Mode.OFF; lastTap = -1000; }
    public Mode mode() { return mode; }
    public void reset() { mode = Mode.OFF; lastTap = -1000; }
}
