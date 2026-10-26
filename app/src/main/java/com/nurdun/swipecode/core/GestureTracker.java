package com.nurdun.swipecode.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Each pointer owns its starting key until release; hysteresis prevents accidental taps. */
public final class GestureTracker {
    public static final class Stroke {
        public final int pointerId;
        public final String key;
        public final float startX, startY;
        public Direction direction = Direction.CENTER;
        private Stroke(int pointerId, String key, float x, float y) {
            this.pointerId = pointerId; this.key = key; startX = x; startY = y;
        }
    }

    private final Map<Integer, Stroke> strokes = new LinkedHashMap<>();
    private float trigger, returnDistance;

    public GestureTracker(float trigger, float returnDistance) { configure(trigger, returnDistance); }

    public void configure(float trigger, float returnDistance) {
        if (trigger <= 0 || returnDistance < 0 || returnDistance >= trigger) throw new IllegalArgumentException("Invalid thresholds");
        this.trigger = trigger; this.returnDistance = returnDistance;
    }

    public Stroke begin(int pointerId, String key, float x, float y) {
        Stroke stroke = new Stroke(pointerId, key, x, y);
        strokes.put(pointerId, stroke);
        return stroke;
    }

    public Stroke move(int pointerId, float x, float y) {
        Stroke stroke = strokes.get(pointerId);
        if (stroke == null) return null;
        float dx = x - stroke.startX, dy = y - stroke.startY;
        float distance = Math.max(Math.abs(dx), Math.abs(dy));
        if (distance <= returnDistance) stroke.direction = Direction.CENTER;
        else if (distance >= trigger) {
            // Keep an existing direction on an exact diagonal to avoid candidate flicker.
            if (Math.abs(dx) == Math.abs(dy) && stroke.direction != Direction.CENTER) return stroke;
            stroke.direction = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? Direction.RIGHT : Direction.LEFT)
                    : (dy > 0 ? Direction.DOWN : Direction.UP);
        }
        return stroke;
    }

    public Stroke release(int pointerId, float x, float y, boolean insideKeyboard) {
        Stroke stroke = move(pointerId, x, y);
        strokes.remove(pointerId);
        return insideKeyboard ? stroke : null;
    }

    public Stroke get(int pointerId) { return strokes.get(pointerId); }
    public Stroke last() { Stroke last = null; for (Stroke value : strokes.values()) last = value; return last; }
    public int size() { return strokes.size(); }
    public void cancelAll() { strokes.clear(); }
}
