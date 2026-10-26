package com.nurdun.swipecode;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import com.nurdun.swipecode.core.Direction;
import com.nurdun.swipecode.core.KeyboardLayout;
import com.nurdun.swipecode.core.KeyboardSession;
import com.nurdun.swipecode.core.KeyBinding;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Framework-only device tests. Install/run only on a connected device; never fetch an emulator. */
public final class KeyboardInstrumentation extends Instrumentation {
    private MainActivity activity;
    private SwipeKeyboardView keyboard;
    private EditText editor;
    private int checks;
    private long clock = SystemClock.uptimeMillis();
    private interface UiAction { void run() throws Exception; }
    private void ui(UiAction action) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        runOnMainSync(() -> { try { action.run(); } catch (Throwable failure) { error.set(failure); } });
        if (error.get() != null) throw new Exception("UI test failed", error.get());
    }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences preferences = getTargetContext().getSharedPreferences("swipe_code_v1", 0);
        Map<String, ?> backup = new HashMap<>(preferences.getAll());
        int code = Activity.RESULT_OK;
        try {
            preferences.edit().clear().commit();
            Intent intent = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("page", 0);
            activity = (MainActivity) startActivitySync(intent); waitForIdleSync();
            ui(() -> {
                check(activity.getWindow().peekDecorView() != null, "Cold start creates its window before system-bar setup");
                keyboard = activity.findViewById(R.id.trial_keyboard); editor = activity.findViewById(R.id.trial_editor);
                check(keyboard != null && editor != null, "Cold start reaches the trial page instead of the crash report");
                check(keyboard.getWidth() > 0 && keyboard.getHeight() > 0, "Keyboard laid out");
                check(!editor.getShowSoftInputOnFocus(), "System keyboard disabled only in trial editor");
                keyboard.requestApplyInsets();
            });
            waitForIdleSync();
            testTypingAndGestures(); testEditing(); testTopControls(); testHold(); testCustomization(); testAllKeyCustomization();
            ui(() -> checks += ImeAdapterTests.run(activity));
            ui(() -> {
                findText(activity.getWindow().getDecorView(), "试打").performClick();
                editor = activity.findViewById(R.id.trial_editor);
                editor.setText("function hello(name) {\n  return \"Hello, \" + name;\n}\n\nhello('滑码');");
                editor.setSelection(editor.length());
            });
            waitForIdleSync(); screenshot("trial");
            ui(() -> findText(activity.getWindow().getDecorView(), "自定义").performClick()); waitForIdleSync(); screenshot("customization");
            ui(() -> activity.editKey('h')); waitForIdleSync(); SystemClock.sleep(500); screenshot("key-editor");
            ui(() -> dialog().dismiss());
            ui(() -> findText(activity.getWindow().getDecorView(), "手感").performClick()); waitForIdleSync(); screenshot("settings");
            result.putString("stream", "\nPASS: " + checks + " Android UI assertions. Screenshots saved in app external files/test-evidence.\n");
        } catch (Throwable failure) {
            code = 1; result.putString("stream", android.util.Log.getStackTraceString(failure)); result.putString("shortMsg", failure.toString());
        } finally {
            try { if (activity != null) ui(() -> activity.finish()); } catch (Exception ignored) {}
            SharedPreferences.Editor restore = preferences.edit().clear();
            for (Map.Entry<String, ?> item : backup.entrySet()) {
                Object value = item.getValue();
                if (value instanceof String) restore.putString(item.getKey(), (String) value);
                else if (value instanceof Integer) restore.putInt(item.getKey(), (Integer) value);
                else if (value instanceof Boolean) restore.putBoolean(item.getKey(), (Boolean) value);
            }
            restore.commit();
        }
        finish(code, result);
    }
    private void testTypingAndGestures() throws Exception {
        ui(() -> {
            editor.setText("");
            for (char letter : KeyboardLayout.LETTERS.toCharArray()) gesture(String.valueOf(letter), 0, 0);
            equal(KeyboardLayout.LETTERS, editor.getText().toString());
            editor.setText("");
            gesture("a", -20, 0); gesture("a", 20, 0); gesture("s", -20, 0); gesture("s", 20, 0);
            gesture("d", -20, 0); gesture("d", 20, 0); gesture("g", -20, 0); gesture("g", 20, 0); gesture("x", -20, 0);
            equal("()[]{}'\"\\", editor.getText().toString());
            editor.setText(""); for (char c : KeyboardLayout.ROWS[0].toCharArray()) gesture(String.valueOf(c), 0, -20);
            equal("1234567890", editor.getText().toString());
            editor.setText(""); gesture("n", -20, 0); equal("", editor.getText().toString());
            RectF a = keyboard.keyBounds("a");
            single(MotionEvent.ACTION_DOWN, a.centerX(), a.centerY());
            single(MotionEvent.ACTION_MOVE, a.centerX() - dp(20), a.centerY()); equal("", editor.getText().toString());
            single(MotionEvent.ACTION_MOVE, a.centerX(), a.centerY()); single(MotionEvent.ACTION_UP, a.centerX(), a.centerY()); equal("a", editor.getText().toString());
            editor.setText(""); single(MotionEvent.ACTION_DOWN, a.centerX(), a.centerY()); single(MotionEvent.ACTION_CANCEL, a.centerX(), a.centerY()); equal("", editor.getText().toString());
            single(MotionEvent.ACTION_DOWN, a.centerX(), a.centerY()); single(MotionEvent.ACTION_UP, -1, a.centerY()); equal("", editor.getText().toString());
            gesture("a", 65, 0); equal(")", editor.getText().toString());
            editor.setText("");
            RectF s = keyboard.keyBounds("s");
            event(MotionEvent.ACTION_DOWN, new int[]{0}, new float[]{a.centerX()}, new float[]{a.centerY()});
            event(MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{0, 7}, new float[]{a.centerX(), s.centerX()}, new float[]{a.centerY(), s.centerY()});
            event(MotionEvent.ACTION_MOVE, new int[]{0, 7}, new float[]{a.centerX() - dp(20), s.centerX() + dp(20)}, new float[]{a.centerY(), s.centerY()});
            event(MotionEvent.ACTION_POINTER_UP, new int[]{0, 7}, new float[]{a.centerX() - dp(20), s.centerX() + dp(20)}, new float[]{a.centerY(), s.centerY()});
            event(MotionEvent.ACTION_UP, new int[]{7}, new float[]{s.centerX() + dp(20)}, new float[]{s.centerY()}); equal("(]", editor.getText().toString());
            editor.setText(""); gesture("SHIFT", 0, 0); gesture("q", 0, 0); gesture("r", 0, 0); equal("Qr", editor.getText().toString());
            gesture("SHIFT", 0, 0); gesture("SHIFT", 0, 0); gesture("a", 0, 0); gesture("b", 0, 0); equal("QrAB", editor.getText().toString());
            gesture("SHIFT", 0, 0); gesture("c", 0, 0); equal("QrABc", editor.getText().toString());
            gesture("SHIFT", 0, 0); gesture("g", 20, 0); gesture("q", 0, 0); equal("QrABc\"q", editor.getText().toString());
            gesture("SPACE", 0, 0); gesture("ENTER", 0, 0); equal("QrABc\"q \n", editor.getText().toString());
        });
    }
    private void testEditing() throws Exception {
        ui(() -> {
            EditTextOutput output = new EditTextOutput(editor);
            editor.setText("abcd"); editor.setSelection(1, 3); output.insertText("中"); equal("a中d", editor.getText().toString()); equal(2, editor.getSelectionStart());
            output.backspace(); equal("ad", editor.getText().toString());
            output.insertText("😀"); output.backspace(); equal("ad", editor.getText().toString());
            editor.setSelection(0); output.backspace(); equal("ad", editor.getText().toString());
            editor.selectAll(); output.enter(); equal("\n", editor.getText().toString());
        });
    }
    private void testTopControls() throws Exception {
        ui(() -> {
            check(keyboard.keyBounds("TOP_SHIFT").bottom < keyboard.keyBounds("q").top, "Controls are above the letter keys");
            editor.setText(""); gesture("TOP_SHIFT", 0, 0); equal(KeyboardSession.SHIFT, keyboard.activeModifiers());
            gesture("a", 0, 0); equal("A", editor.getText().toString()); equal(0, keyboard.activeModifiers());
            gesture("TAB", 0, 0); equal("A\t", editor.getText().toString());
            editor.setText("abcd"); editor.setSelection(2);
            gesture("ALT", -20, 0); equal(1, editor.getSelectionStart());
            gesture("ALT", 20, 0); equal(2, editor.getSelectionStart());
            gesture("TOP_SHIFT", 0, 0); gesture("ALT", -20, 0);
            equal(1, Math.abs(editor.getSelectionEnd() - editor.getSelectionStart()));
            equal(KeyboardSession.SHIFT, keyboard.activeModifiers());
            gesture("x", 0, 0); equal("aXcd", editor.getText().toString()); equal(0, keyboard.activeModifiers());
            gesture("CTRL", 0, 0); equal(KeyboardSession.CTRL, keyboard.activeModifiers());
            gesture("a", 0, 0); equal(editor.length(), Math.abs(editor.getSelectionEnd() - editor.getSelectionStart()));
            equal(0, keyboard.activeModifiers()); gesture("z", 0, 0); equal("z", editor.getText().toString());
            gesture("ALT", 0, 0); equal(KeyboardSession.ALT, keyboard.activeModifiers());
            gesture("n", -20, 0); equal(KeyboardSession.ALT, keyboard.activeModifiers());
            gesture("ALT", 0, 0); equal(0, keyboard.activeModifiers());
            editor.setText("abc\ndef"); editor.setSelection(5);
        });
        waitForIdleSync();
        ui(() -> {
            gesture("ALT", 0, -20); equal(1, editor.getSelectionStart());
            gesture("ALT", 0, 20); equal(5, editor.getSelectionStart());
            check(keyboard.keyBounds("ESC") != null, "Esc is in the top control row");
            check(keyboard.keyBounds("LEFT") == null, "Arrows do not occupy dedicated keys");
            gesture("ESC", 0, 0);
            editor.setText("abc"); editor.setSelection(1); gesture("DELETE", 0, 0); equal("ac", editor.getText().toString());
            editor.setText("abc"); gesture("CTRL", 0, -20); equal(3, Math.abs(editor.getSelectionEnd() - editor.getSelectionStart()));
            gesture("q", 0, 0); equal("q", editor.getText().toString());
            new EditTextOutput(editor).key("1", KeyboardSession.SHIFT); equal("q!", editor.getText().toString());
        });
    }
    private void testHold() throws Exception {
        ui(() -> { editor.setText("abcdefghijklmnop"); editor.setSelection(editor.length()); RectF back = keyboard.keyBounds("BACKSPACE"); single(MotionEvent.ACTION_DOWN, back.centerX(), back.centerY()); });
        SystemClock.sleep(590);
        ui(() -> { RectF back = keyboard.keyBounds("BACKSPACE"); single(MotionEvent.ACTION_UP, back.centerX(), back.centerY()); check(editor.length() < 15, "Held backspace repeats"); });
        final int[] after = new int[1];
        ui(() -> { after[0] = editor.length(); RectF back = keyboard.keyBounds("BACKSPACE"); single(MotionEvent.ACTION_DOWN, back.centerX(), back.centerY()); single(MotionEvent.ACTION_CANCEL, back.centerX(), back.centerY()); });
        SystemClock.sleep(450);
        ui(() -> equal(after[0], editor.length()));
    }
    private void testCustomization() throws Exception {
        ui(() -> { activity.openCustomization(); activity.editKey('a'); }); waitForIdleSync();
        ui(() -> {
            AlertDialog dialog = dialog(); EditText field = dialog.findViewById(R.id.symbol_left); check(field.getShowSoftInputOnFocus(), "System keyboard enabled in mapping editor");
            field.setText("中"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            equal("中", new KeyboardStore(activity).loadLayout().get('a', Direction.LEFT));
            activity.editKey('a'); dialog = dialog(); ((EditText) dialog.findViewById(R.id.symbol_right)).setText("!"); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            equal(")", new KeyboardStore(activity).loadLayout().get('a', Direction.RIGHT));
            activity.editKey('a'); dialog = dialog(); ((EditText) dialog.findViewById(R.id.symbol_left)).setText("x".repeat(65)); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            check(dialog.isShowing(), "Invalid input keeps dialog open"); check(((EditText) dialog.findViewById(R.id.symbol_left)).getError() != null, "Validation error shown");
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            activity.editKey('a'); dialog = dialog(); dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            equal("(", ((EditText) dialog.findViewById(R.id.symbol_left)).getText().toString()); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            equal("中", new KeyboardStore(activity).loadLayout().get('a', Direction.LEFT));
            activity.editKey('a'); dialog = dialog(); dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            equal("(", new KeyboardStore(activity).loadLayout().get('a', Direction.LEFT));
            AppSettings settings = new KeyboardStore(activity).loadSettings(); settings.keyHeightDp = 64; settings.triggerDp = 16; settings.haptics = false;
            new KeyboardStore(activity).saveSettings(settings); AppSettings restored = new KeyboardStore(activity).loadSettings();
            equal(64, restored.keyHeightDp); equal(16, restored.triggerDp); equal(false, restored.haptics);
            activity.editKey('g'); dialog = dialog();
            BindingSlotView slot = (BindingSlotView) dialog.findViewById(R.id.symbol_up).getParent();
            slot.setBinding(KeyBinding.key("A", KeyboardSession.CTRL)); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            equal(KeyBinding.key("A", KeyboardSession.CTRL), new KeyboardStore(activity).loadLayout().binding('g', Direction.UP));
            activity.editKey('g'); dialog = dialog(); slot = (BindingSlotView) dialog.findViewById(R.id.symbol_up).getParent();
            slot.setBinding(KeyBinding.key("F12", KeyboardSession.ALT)); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            equal(KeyBinding.key("A", KeyboardSession.CTRL), new KeyboardStore(activity).loadLayout().binding('g', Direction.UP));
            activity.editKey('g'); dialog = dialog(); slot = (BindingSlotView) dialog.findViewById(R.id.symbol_down).getParent();
            slot.setBinding(KeyBinding.text("hello😀")); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            equal(KeyBinding.text("hello😀"), new KeyboardStore(activity).loadLayout().binding('g', Direction.DOWN));
            findText(activity.getWindow().getDecorView(), "试打").performClick();
        });
        waitForIdleSync();
        ui(() -> {
            keyboard = activity.findViewById(R.id.trial_keyboard); editor = activity.findViewById(R.id.trial_editor);
            editor.setText("abc"); editor.setSelection(3); gesture("g", 0, -24);
            equal(3, Math.abs(editor.getSelectionEnd() - editor.getSelectionStart()));
            gesture("g", 0, 24); equal("hello😀", editor.getText().toString());
        });
    }
    private void testAllKeyCustomization() throws Exception {
        ui(() -> {
            final int[] captured = new int[2];
            EditText capture = new EditText(activity) {
                @Override public boolean dispatchKeyEvent(KeyEvent event) { captured[0]++; captured[1] = event.getMetaState(); return true; }
            };
            new EditTextOutput(capture).key("P", KeyboardSession.CTRL | KeyboardSession.CAPS_LOCK);
            equal(2, captured[0]); check((captured[1] & KeyEvent.META_CTRL_ON) != 0, "Ctrl+P preserves Ctrl");
            check((captured[1] & KeyEvent.META_SHIFT_ON) == 0, "Caps Lock does not turn Ctrl+P into Ctrl+Shift+P");
            editor.setText("abcd"); editor.setSelection(4);
            gesture("CTRL", 0, -24); equal(4, Math.abs(editor.getSelectionEnd() - editor.getSelectionStart()));
            equal(0, keyboard.activeModifiers());
            gesture("TOP_SHIFT", 0, -24); equal("\t", editor.getText().toString()); equal(0, keyboard.activeModifiers());
            editor.setText("abcd"); editor.setSelection(2); gesture("ALT", -24, 0); equal(1, editor.getSelectionStart()); equal(0, keyboard.activeModifiers());
            gesture("ALT", 0, 0); equal(KeyboardSession.ALT, keyboard.activeModifiers()); gesture("ALT", 0, 0); equal(0, keyboard.activeModifiers());
            RectF ctrl = keyboard.keyBounds("CTRL"); single(MotionEvent.ACTION_DOWN, ctrl.centerX(), ctrl.centerY()); single(MotionEvent.ACTION_CANCEL, ctrl.centerX(), ctrl.centerY()); equal(0, keyboard.activeModifiers());
            activity.openCustomization();
        });
        waitForIdleSync();
        ui(() -> {
            keyboard = activity.findViewById(R.id.layout_preview); gesture("CTRL", 0, 0);
            AlertDialog dialog = dialog(); check(dialog.isShowing(), "Top-row keys can open the binding editor");
            ((BindingSlotView) dialog.findViewById(R.id.symbol_center).getParent()).setBinding(KeyBinding.text("tap"));
            ((BindingSlotView) dialog.findViewById(R.id.symbol_up).getParent()).setBinding(KeyBinding.key("F5", KeyboardSession.ALT));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            equal(KeyBinding.text("tap"), new KeyboardStore(activity).loadLayout().binding("CTRL", Direction.CENTER));
            equal(KeyBinding.key("F5", KeyboardSession.ALT), new KeyboardStore(activity).loadLayout().binding("CTRL", Direction.UP));
            activity.editKey("SPACE"); dialog = dialog();
            ((BindingSlotView) dialog.findViewById(R.id.symbol_right).getParent()).setBinding(KeyBinding.text("snippet")); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            activity.editKey("BACKSPACE"); dialog = dialog();
            ((BindingSlotView) dialog.findViewById(R.id.symbol_center).getParent()).setBinding(KeyBinding.key("DELETE", 0)); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            findText(activity.getWindow().getDecorView(), "试打").performClick();
        });
        waitForIdleSync();
        ui(() -> {
            keyboard = activity.findViewById(R.id.trial_keyboard); editor = activity.findViewById(R.id.trial_editor);
            editor.setText("ab"); editor.setSelection(1); gesture("CTRL", 0, 0); equal("atapb", editor.getText().toString()); equal(0, keyboard.activeModifiers());
            editor.setText("ab"); editor.setSelection(1); gesture("BACKSPACE", 0, 0); equal("a", editor.getText().toString());
            editor.setText(""); gesture("SPACE", 24, 0); equal("snippet", editor.getText().toString());
            activity.openCustomization(); activity.editKey("CTRL"); AlertDialog dialog = dialog();
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            equal(KeyBinding.command("CTRL"), new KeyboardStore(activity).loadLayout().binding("CTRL", Direction.CENTER));
        });
    }
    private AlertDialog dialog() throws Exception { Field field = MainActivity.class.getDeclaredField("keyDialog"); field.setAccessible(true); return (AlertDialog) field.get(activity); }
    private TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) { TextView result = findText(((ViewGroup) view).getChildAt(i), text); if (result != null) return result; }
        return null;
    }
    private float dp(float value) { return value * activity.getResources().getDisplayMetrics().density; }
    private void gesture(String key, float dx, float dy) {
        RectF bounds = keyboard.keyBounds(key); float x = bounds.centerX(), y = bounds.centerY();
        single(MotionEvent.ACTION_DOWN, x, y);
        if (dx != 0 || dy != 0) single(MotionEvent.ACTION_MOVE, x + dp(dx), y + dp(dy));
        single(MotionEvent.ACTION_UP, x + dp(dx), y + dp(dy));
    }
    private void single(int action, float x, float y) { event(action, new int[]{0}, new float[]{x}, new float[]{y}); }
    private void event(int action, int[] ids, float[] xs, float[] ys) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[ids.length];
        for (int i = 0; i < ids.length; i++) {
            properties[i] = new MotionEvent.PointerProperties(); properties[i].id = ids[i]; properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords(); coords[i].x = xs[i]; coords[i].y = ys[i]; coords[i].pressure = 1; coords[i].size = 1;
        }
        clock += 20;
        MotionEvent event = MotionEvent.obtain(clock - 20, clock, action, ids.length, properties, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        keyboard.onTouchEvent(event); event.recycle();
    }
    private void screenshot(String name) throws Exception {
        Bitmap screenshot = getUiAutomation().takeScreenshot(); if (screenshot == null) throw new AssertionError("Screenshot unavailable");
        File dir = new File(getTargetContext().getExternalFilesDir(null), "test-evidence"); if (!dir.isDirectory() && !dir.mkdirs()) throw new AssertionError("Cannot create evidence directory");
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, out); }
        screenshot.recycle();
    }
    private void equal(Object expected, Object actual) { checks++; if (!Objects.equals(expected, actual)) throw new AssertionError("Expected [" + expected + "], got [" + actual + "]"); }
    private void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
