package com.nurdun.swipecode;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import com.nurdun.swipecode.core.Direction;
import com.nurdun.swipecode.core.GestureTracker;
import com.nurdun.swipecode.core.KeyboardLayout;
import com.nurdun.swipecode.core.KeyBinding;
import com.nurdun.swipecode.core.KeyboardSession;
import com.nurdun.swipecode.core.ShiftState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canvas keyboard with pointer-independent gestures; no dependency on the text editor. */
public final class SwipeKeyboardView extends View {
    public interface Navigation { void editKey(String key); void openCustomization(); }
    private static final class Key {
        final String id;
        final RectF bounds;
        Key(String id, RectF bounds) { this.id = id; this.bounds = bounds; }
        boolean letter() { return id.length() == 1 && id.charAt(0) >= 'a' && id.charAt(0) <= 'z'; }
    }
    private final Palette palette;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Key> keys = new ArrayList<>();
    private final GestureTracker gestures;
    private final KeyboardSession session;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Runnable> repeats = new HashMap<>();
    private final Set<Integer> repeated = new HashSet<>();
    private KeyboardLayout layout;
    private AppSettings settings;
    private Navigation navigation;
    private final boolean editMode;
    private int previewPointer = -1;
    private String lastShortcut = "";
    private String enterLabel;
    private final Runnable clearShortcut = () -> { lastShortcut = ""; invalidate(); };

    public SwipeKeyboardView(Context context, KeyboardLayout layout, AppSettings settings, boolean editMode) {
        super(context);
        this.layout = layout; this.settings = settings; this.editMode = editMode;
        session = new KeyboardSession(layout);
        palette = new Palette(context);
        gestures = new GestureTracker(dp(settings.triggerDp), dp(settings.returnDistanceDp()));
        setClickable(true);
        setFocusable(false);
        setContentDescription(editMode ? "键盘预览，点击任意键自定义轻点和四向滑动" : "滑码键盘，顶部Esc上下为翻页，Shift上滑为Tab，Ctrl四向为快捷键，Alt四向为箭头");
    }
    public void setOutput(KeyboardOutput output) { session.setSink(output); }
    public void setNavigation(Navigation navigation) { this.navigation = navigation; }
    public void setEnterLabel(String label) { enterLabel = label; invalidate(); }
    private String preview(String key, Direction direction) {
        KeyBinding binding = layout.binding(key, direction);
        if (!editMode && enterLabel != null && binding.kind == KeyBinding.Kind.KEY && binding.value.equals("ENTER")
                && ((session.modifiers() | binding.modifiers) & ~KeyboardSession.CAPS_LOCK) == 0) return enterLabel;
        return session.preview(key, direction);
    }
    public void resetInputState() { cancelTouches(); session.reset(); handler.removeCallbacks(clearShortcut); lastShortcut = ""; invalidate(); }
    public void update(KeyboardLayout layout, AppSettings settings) {
        cancelTouches(); this.layout = layout; this.settings = settings;
        session.setLayout(layout);
        gestures.configure(dp(settings.triggerDp), dp(settings.returnDistanceDp()));
        requestLayout(); invalidate();
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    private float headerHeight() { return dp(44); }
    private float controlsHeight() { return dp(56); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int wanted = Math.round(headerHeight() + controlsHeight() + dp(settings.keyHeightDp * 4 + 24));
        setMeasuredDimension(resolveSize(dpInt(380), widthSpec), resolveSize(wanted, heightSpec));
    }
    private int dpInt(int value) { return Math.round(dp(value)); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { buildKeys(w, h); }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) { super.onLayout(changed, l, t, r, b); buildKeys(r - l, b - t); }
    private void buildKeys(int width, int height) {
        keys.clear();
        float pad = dp(6), stride = (width - pad * 2) / 10f;
        float rowHeight = Math.max(dp(20), (height - headerHeight() - controlsHeight() - dp(24)) / 4f);
        float y = headerHeight(), gap = dp(3);
        String[] controls = KeyboardLayout.TOP_KEYS;
        float controlWidth = (width - pad * 2) / controls.length;
        for (int i = 0; i < controls.length; i++) addKey(controls[i], pad + i * controlWidth, y, controlWidth, dp(48), gap);
        y += controlsHeight();
        for (int row = 0; row < 2; row++) {
            String letters = KeyboardLayout.ROWS[row];
            float offset = row == 0 ? 0 : .5f;
            for (int i = 0; i < letters.length(); i++) addKey(letters.substring(i, i + 1), pad + (i + offset) * stride, y, stride, rowHeight, gap);
            y += rowHeight + dp(6);
        }
        addKey("SHIFT", pad, y, stride * 1.5f, rowHeight, gap);
        for (int i = 0; i < 7; i++) addKey(KeyboardLayout.ROWS[2].substring(i, i + 1), pad + (i + 1.5f) * stride, y, stride, rowHeight, gap);
        addKey("BACKSPACE", pad + 8.5f * stride, y, stride * 1.5f, rowHeight, gap);
        y += rowHeight + dp(6);
        addKey("SETTINGS", pad, y, stride * 1.8f, rowHeight, gap);
        addKey("SPACE", pad + 1.8f * stride, y, stride * 6.4f, rowHeight, gap);
        addKey("ENTER", pad + 8.2f * stride, y, stride * 1.8f, rowHeight, gap);
    }
    private void addKey(String id, float x, float y, float width, float height, float gap) {
        keys.add(new Key(id, new RectF(x + gap / 2, y, x + width - gap / 2, y + height)));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setColor(palette.keyboard);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(20), dp(20), paint);
        drawHeader(canvas);
        for (Key key : keys) drawKey(canvas, key);
    }
    private void drawHeader(Canvas canvas) {
        GestureTracker.Stroke active = gestures.get(previewPointer);
        if (active == null) active = gestures.last();
        if (active != null) {
            String symbol = preview(active.key, active.direction);
            String label = KeyboardLayout.keyName(active.key) + " · " + directionName(active.direction);
            fittedText(canvas, label, dp(10), dp(27), 11, palette.muted, Paint.Align.LEFT, false, dp(74));
            float half = Math.min(dp(85), getWidth() / 2f - dp(80));
            RectF bubble = new RectF(getWidth() / 2f - half, dp(5), getWidth() / 2f + half, dp(37));
            paint.setColor(palette.accentSoft); canvas.drawRoundRect(bubble, dp(10), dp(10), paint);
            fittedText(canvas, symbol.isEmpty() ? "空" : visible(symbol), bubble.centerX(), dp(27), symbol.length() > 3 ? 14 : 21, palette.accent, Paint.Align.CENTER, true, bubble.width() - dp(10));
            text(canvas, symbol.isEmpty() ? "松手不输入" : "松手输入", getWidth() - dp(14), dp(27), 11, palette.muted, Paint.Align.RIGHT, false);
        } else {
            int modifiers = editMode ? 0 : session.modifiers();
            String hint = editMode ? "点击任意键，设置轻点和四向" : modifiers != 0 ? KeyboardSession.modifierLabel(modifiers) + " 已按住"
                    : !lastShortcut.isEmpty() ? lastShortcut : "轻点字母 · 滑动符号";
            String badge = editMode ? "可自定义" : modifiers == KeyboardSession.CAPS_LOCK ? "大写锁定" : modifiers != 0 ? "输入字符后释放" : !lastShortcut.isEmpty() ? "已发送按键" : "QWERTY";
            text(canvas, hint, dp(14), dp(27), 12, palette.muted, Paint.Align.LEFT, false);
            text(canvas, badge, getWidth() - dp(14), dp(27), 10, palette.accent, Paint.Align.RIGHT, true);
        }
    }
    private void drawKey(Canvas canvas, Key key) {
        GestureTracker.Stroke active = null;
        // Two thumbs may highlight two keys simultaneously.
        for (int i = 0; i < 32; i++) {
            GestureTracker.Stroke candidate = gestures.get(i);
            if (candidate != null && candidate.key.equals(key.id)) { active = candidate; break; }
        }
        KeyBinding tap = layout.binding(key.id, Direction.CENTER);
        boolean latched = !editMode && tap.kind == KeyBinding.Kind.COMMAND && switch (tap.value) {
            case "SHIFT" -> session.shiftMode() != ShiftState.Mode.OFF;
            case "CTRL" -> (session.modifiers() & KeyboardSession.CTRL) != 0;
            case "ALT" -> (session.modifiers() & KeyboardSession.ALT) != 0;
            default -> false;
        };
        boolean emphasized = active != null || latched;
        paint.setColor(key.id.equals("ENTER") && !editMode ? palette.accent : emphasized ? palette.accentSoft : palette.key);
        RectF shadow = new RectF(key.bounds); shadow.offset(0, dp(1));
        if (!palette.dark) { int fill = paint.getColor(); paint.setColor(palette.line); canvas.drawRoundRect(shadow, dp(7), dp(7), paint); paint.setColor(fill); }
        canvas.drawRoundRect(key.bounds, dp(7), dp(7), paint);
        {
            String base = editMode ? tap.label() : preview(key.id, Direction.CENTER);
            if (key.id.equals("SHIFT") && tap.kind == KeyBinding.Kind.COMMAND && tap.value.equals("SHIFT")) base = session.shiftMode() == ShiftState.Mode.LOCKED ? "⇪" : "⇧";
            boolean compact = !key.letter() && base.length() > 1;
            int color = key.id.equals("ENTER") && !editMode ? palette.accentInk : latched || active != null && active.direction == Direction.CENTER ? palette.accent : palette.ink;
            fittedText(canvas, base, key.bounds.centerX(), key.bounds.centerY() + dp(compact ? 4 : 6), compact ? 11 : 18, color, Paint.Align.CENTER, true, key.bounds.width() * .8f);
            for (Direction direction : Direction.values()) if (direction != Direction.CENTER) {
                KeyBinding binding = layout.binding(key.id, direction);
                if (binding.isEmpty()) continue;
                String symbol = binding.compactLabel();
                boolean selected = active != null && active.direction == direction;
                float x = key.bounds.centerX(), y = key.bounds.centerY() + dp(3);
                Paint.Align align = Paint.Align.CENTER;
                if (direction == Direction.UP) y = key.bounds.top + dp(11);
                else if (direction == Direction.DOWN) y = key.bounds.bottom - dp(4);
                else if (direction == Direction.LEFT) { x = key.bounds.left + dp(4); align = Paint.Align.LEFT; }
                else { x = key.bounds.right - dp(4); align = Paint.Align.RIGHT; }
                float maxWidth = direction == Direction.LEFT || direction == Direction.RIGHT ? key.bounds.width() * .34f : key.bounds.width() - dp(3);
                fittedText(canvas, visible(symbol), x, y, 9, selected ? palette.accent : palette.muted, align, selected, maxWidth);
            }
        }
    }
    private void text(Canvas canvas, String value, float x, float y, float size, int color, Paint.Align align, boolean bold) {
        paint.setColor(color); paint.setTextSize(sp(size)); paint.setTextAlign(align);
        paint.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.create("sans-serif", Typeface.NORMAL));
        canvas.drawText(value, x, y, paint);
    }
    private void fittedText(Canvas canvas, String value, float x, float y, float size, int color, Paint.Align align, boolean bold, float width) {
        paint.setTextSize(sp(size)); paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        float adjusted = Math.max(7, Math.min(size, size * width / Math.max(1, paint.measureText(value))));
        paint.setTextSize(sp(adjusted));
        value = android.text.TextUtils.ellipsize(value, new android.text.TextPaint(paint), width, android.text.TextUtils.TruncateAt.END).toString();
        text(canvas, value, x, y, adjusted, color, align, bold);
    }
    private static String visible(String value) { return value.equals(" ") ? "␠" : value; }
    public static String directionName(Direction direction) {
        return switch (direction) { case UP -> "上滑"; case DOWN -> "下滑"; case LEFT -> "左滑"; case RIGHT -> "右滑"; default -> "轻点"; };
    }
    private static boolean isLetter(String key) { return key.length() == 1 && key.charAt(0) >= 'a' && key.charAt(0) <= 'z'; }
    private Key keyAt(float x, float y) { for (Key key : keys) if (key.bounds.contains(x, y)) return key; return null; }
    private Key keyById(String id) { for (Key key : keys) if (key.id.equals(id)) return key; return null; }
    private boolean inside(float x, float y) { return x >= 0 && x < getWidth() && y >= 0 && y < getHeight(); }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked(), index = event.getActionIndex();
        int id = event.getPointerId(index);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            Key key = keyAt(event.getX(index), event.getY(index));
            if (key != null) {
                gestures.begin(id, key.id, event.getX(index), event.getY(index)); previewPointer = id;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (settings.haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (!editMode && layout.binding(key.id, Direction.CENTER).repeats()) startRepeat(id);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int pointer = 0; pointer < event.getPointerCount(); pointer++) {
                int current = event.getPointerId(pointer);
                GestureTracker.Stroke prior = gestures.get(current);
                Direction previous = prior == null ? Direction.CENTER : prior.direction;
                GestureTracker.Stroke stroke = gestures.move(current, event.getX(pointer), event.getY(pointer));
                if (stroke == null) continue;
                previewPointer = current;
                if (!editMode && previous != stroke.direction) {
                    stopRepeat(current); repeated.remove(current);
                    if (inside(event.getX(pointer), event.getY(pointer)) && layout.binding(stroke.key, stroke.direction).repeats()) startRepeat(current);
                }
                if (!inside(event.getX(pointer), event.getY(pointer))) stopRepeat(current);
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            GestureTracker.Stroke prior = gestures.get(id);
            Direction previous = prior == null ? Direction.CENTER : prior.direction;
            GestureTracker.Stroke stroke = gestures.release(id, event.getX(index), event.getY(index), inside(event.getX(index), event.getY(index)));
            if (stroke != null && previous != stroke.direction) repeated.remove(id);
            stopRepeat(id);
            if (stroke != null) {
                if (editMode && stroke.direction == Direction.CENTER && navigation != null) navigation.editKey(stroke.key);
                else if (!editMode) commit(stroke, event.getEventTime(), repeated.contains(id));
            }
            repeated.remove(id);
            performClick();
            if (gestures.size() == 0 && getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        } else if (action == MotionEvent.ACTION_CANCEL) cancelTouches();
        invalidate(); return true;
    }
    private void commit(GestureTracker.Stroke stroke, long now, boolean alreadyRepeated) {
        KeyBinding binding = layout.binding(stroke.key, stroke.direction);
        if (alreadyRepeated && binding.repeats()) return;
        if (binding.kind == KeyBinding.Kind.COMMAND && binding.value.equals("CUSTOMIZE")) { if (navigation != null) navigation.openCustomization(); return; }
        int modifiers = session.modifiers();
        String value = session.activate(stroke.key, stroke.direction, now);
        if (!value.isEmpty() && (binding.kind == KeyBinding.Kind.KEY || binding.kind == KeyBinding.Kind.TEXT && (modifiers & (KeyboardSession.CTRL | KeyboardSession.ALT)) != 0)) {
            lastShortcut = binding.kind == KeyBinding.Kind.KEY ? KeyBinding.key(binding.value, (modifiers | binding.modifiers) & ~KeyboardSession.CAPS_LOCK).label()
                    : KeyboardSession.modifierLabel(modifiers) + " + " + value;
            handler.removeCallbacks(clearShortcut); handler.postDelayed(clearShortcut, 1600);
        }
    }
    private void startRepeat(int id) {
        Runnable action = new Runnable() {
            @Override public void run() {
                if (gestures.get(id) == null) return;
                GestureTracker.Stroke stroke = gestures.get(id);
                if (!layout.binding(stroke.key, stroke.direction).repeats()) return;
                repeated.add(id);
                session.activate(stroke.key, stroke.direction, android.os.SystemClock.uptimeMillis());
                handler.postDelayed(this, 55);
            }
        };
        repeats.put(id, action); handler.postDelayed(action, 400);
    }
    private void stopRepeat(int id) { Runnable action = repeats.remove(id); if (action != null) handler.removeCallbacks(action); }
    private void cancelTouches() {
        for (Runnable action : repeats.values()) handler.removeCallbacks(action);
        repeats.clear(); repeated.clear(); gestures.cancelAll(); previewPointer = -1;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }
    @Override protected void onDetachedFromWindow() { cancelTouches(); handler.removeCallbacks(clearShortcut); super.onDetachedFromWindow(); }
    @Override public boolean performClick() { return super.performClick(); }
    public RectF keyBounds(String id) { Key key = keyById(id); return key == null ? null : new RectF(key.bounds); }
    public int activeModifiers() { return session.modifiers(); }
}
