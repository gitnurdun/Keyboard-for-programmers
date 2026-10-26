package com.nurdun.swipecode;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.text.Editable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import com.nurdun.swipecode.core.KeyboardSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Device-side adapter checks need no installed external app and never change the selected IME. */
final class ImeAdapterTests {
    private static int checks;
    private static void eq(Object expected, Object actual) { checks++; if (!Objects.equals(expected, actual)) throw new AssertionError("Expected [" + expected + "], got [" + actual + "]"); }
    private static final class Connection extends BaseInputConnection {
        final SpannableStringBuilder text = new SpannableStringBuilder();
        final List<KeyEvent> events = new ArrayList<>();
        int menu, action; boolean acceptMenu, codePoints = true;
        Connection(Context context) { super(new View(context), true); reset("", 0, 0); }
        void reset(String value, int start, int end) { text.clear(); text.append(value); Selection.setSelection(text, start, end); events.clear(); menu = action = 0; }
        @Override public Editable getEditable() { return text; }
        @Override public boolean sendKeyEvent(KeyEvent event) { events.add(event); return true; }
        @Override public boolean performContextMenuAction(int id) { menu = id; return acceptMenu; }
        @Override public boolean performEditorAction(int id) { action = id; return true; }
        @Override public boolean deleteSurroundingTextInCodePoints(int before, int after) { return codePoints && super.deleteSurroundingTextInCodePoints(before, after); }
    }
    static int run(Context context) throws Exception {
        checks = 0;
        ServiceInfo service = context.getPackageManager().getServiceInfo(new ComponentName(context, SwipeCodeImeService.class), PackageManager.GET_META_DATA);
        eq(true, service.exported); eq("android.permission.BIND_INPUT_METHOD", service.permission); eq(true, service.metaData.containsKey("android.view.im"));
        EditorInfo info = new EditorInfo(); info.inputType = InputType.TYPE_CLASS_TEXT; info.packageName = "example.editor";
        Connection first = new Connection(context); InputConnection[] current = {first};
        InputConnectionOutput output = new InputConnectionOutput(() -> current[0], () -> info);
        first.reset("abcd", 1, 3); output.insertText("中😀", 0); eq("a中😀d", first.text.toString());
        output.backspace(0); eq("a中d", first.text.toString());
        first.reset("a😀b", 1, 1); output.key("DELETE", 0); eq("ab", first.text.toString());
        first.reset("a😀b", 1, 3); output.backspace(0); eq("ab", first.text.toString());
        first.codePoints = false; first.reset("a😀b", 3, 3); output.backspace(0); eq("ab", first.text.toString()); first.codePoints = true;
        first.reset("", 0, 0); output.tab(0); output.enter(0); eq("\t\n", first.text.toString());
        info.imeOptions = EditorInfo.IME_ACTION_SEARCH; first.reset("", 0, 0); output.enter(0); eq(EditorInfo.IME_ACTION_SEARCH, first.action); eq("", first.text.toString());
        info.imeOptions = EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_ENTER_ACTION; first.reset("", 0, 0); output.enter(0); eq(0, first.action); eq("\n", first.text.toString());
        info.imeOptions = EditorInfo.IME_ACTION_SEND; first.reset("", 0, 0); output.enter(KeyboardSession.SHIFT); eq(0, first.action); eq("\n", first.text.toString());
        first.acceptMenu = true; first.reset("abc", 0, 3); output.key("C", KeyboardSession.CTRL); eq(android.R.id.copy, first.menu); eq(0, first.events.size());
        output.setTerminal(true); first.reset("", 0, 0); output.key("C", KeyboardSession.CTRL);
        eq(0, first.menu); eq(2, first.events.size()); eq(KeyEvent.KEYCODE_C, first.events.get(0).getKeyCode()); eq(true, first.events.get(0).isCtrlPressed());
        first.reset("", 0, 0); output.key("L", KeyboardSession.CTRL); eq(KeyEvent.KEYCODE_L, first.events.get(0).getKeyCode()); eq(0, first.menu);
        first.reset("", 0, 0); output.key("P", KeyboardSession.CTRL | KeyboardSession.CAPS_LOCK); eq(KeyEvent.KEYCODE_P, first.events.get(0).getKeyCode()); eq(false, first.events.get(0).isShiftPressed());
        first.reset("", 0, 0); output.enter(0); eq(0, first.action); eq(KeyEvent.KEYCODE_ENTER, first.events.get(0).getKeyCode());
        first.reset("", 0, 0); output.tab(0); eq(KeyEvent.KEYCODE_TAB, first.events.get(0).getKeyCode());
        first.reset("", 0, 0); output.key("UP", KeyboardSession.SHIFT); eq(KeyEvent.KEYCODE_DPAD_UP, first.events.get(0).getKeyCode()); eq(true, first.events.get(0).isShiftPressed());
        first.reset("", 0, 0); output.key("ESC", 0); eq(KeyEvent.KEYCODE_ESCAPE, first.events.get(0).getKeyCode());
        output.setTerminal(false); first.acceptMenu = false; first.reset("", 0, 0); output.key("C", KeyboardSession.CTRL); eq(android.R.id.copy, first.menu); eq(2, first.events.size());
        Connection second = new Connection(context); current[0] = second; output.insertText("new", 0); eq("new", second.text.toString()); eq("", first.text.toString());
        current[0] = null; output.insertText("ignored", 0); output.backspace(0); output.enter(0); output.tab(0); output.key("ESC", 0); eq("new", second.text.toString());
        return checks;
    }
}
