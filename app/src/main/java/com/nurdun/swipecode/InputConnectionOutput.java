package com.nurdun.swipecode;

import android.os.SystemClock;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import com.nurdun.swipecode.core.Direction;
import com.nurdun.swipecode.core.ImePolicy;
import com.nurdun.swipecode.core.KeyboardSession;
import java.util.function.Supplier;

/** Uses a fresh connection for every action; never retains or logs another app's text. */
public final class InputConnectionOutput implements KeyboardOutput {
    private final Supplier<InputConnection> connections;
    private final Supplier<EditorInfo> editors;
    private boolean terminal;
    public InputConnectionOutput(Supplier<InputConnection> connections, Supplier<EditorInfo> editors) { this.connections = connections; this.editors = editors; }
    public void setTerminal(boolean terminal) { this.terminal = terminal; }
    @Override public void insertText(String text, int modifiers) {
        InputConnection connection = connections.get(); if (connection == null || text.isEmpty()) return;
        if ((modifiers & (KeyboardSession.CTRL | KeyboardSession.ALT | KeyboardSession.META)) == 0) {
            EditorInfo info = editors.get();
            if (info != null && (info.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_NULL && sendCharacters(connection, text, modifiers)) return;
            connection.commitText(text, 1); return;
        }
        if (text.length() == 1 && contextAction(connection, text, modifiers)) return;
        sendCharacters(connection, text, modifiers);
    }
    @Override public void backspace(int modifiers) {
        InputConnection connection = connections.get(); if (connection == null) return;
        if (terminal || hasShortcutModifiers(modifiers)) { sendKey(connection, KeyEvent.KEYCODE_DEL, modifiers); return; }
        delete(connection, false);
    }
    @Override public void enter(int modifiers) {
        InputConnection connection = connections.get(); if (connection == null) return;
        EditorInfo info = editors.get();
        int action = info == null ? 0 : ImePolicy.enterAction(info.imeOptions, info.actionId, info.actionLabel != null, modifiers, terminal);
        if (action != 0 && connection.performEditorAction(action)) return;
        if (terminal || hasShortcutModifiers(modifiers)) sendKey(connection, KeyEvent.KEYCODE_ENTER, modifiers);
        else connection.commitText("\n", 1);
    }
    @Override public void tab(int modifiers) {
        InputConnection connection = connections.get(); if (connection == null) return;
        if (terminal || hasShortcutModifiers(modifiers)) sendKey(connection, KeyEvent.KEYCODE_TAB, modifiers);
        else connection.commitText("\t", 1);
    }
    @Override public void moveCursor(Direction direction, int modifiers) {
        if (direction == Direction.CENTER) throw new IllegalArgumentException("A cursor action requires a direction");
        key(direction.name(), modifiers);
    }
    @Override public void key(String key, int modifiers) {
        InputConnection connection = connections.get(); if (connection == null) return;
        if (key.length() == 1 && contextAction(connection, key, modifiers)) return;
        switch (key) {
            case "TAB" -> { tab(modifiers); return; } case "ENTER" -> { enter(modifiers); return; }
            case "BACKSPACE" -> { backspace(modifiers); return; } case "SPACE" -> { insertText(" ", modifiers); return; }
            case "DELETE" -> { if (!terminal && !hasShortcutModifiers(modifiers)) { delete(connection, true); return; } }
        }
        String androidKey = switch (key) {
            case "UP", "DOWN", "LEFT", "RIGHT" -> "DPAD_" + key;
            case "ESC" -> "ESCAPE"; case "BACKSPACE" -> "DEL"; case "DELETE" -> "FORWARD_DEL";
            case "HOME" -> "MOVE_HOME"; case "END" -> "MOVE_END";
            case "PRINT_SCREEN" -> "SYSRQ"; case "PAUSE" -> "BREAK"; default -> key;
        };
        int code = KeyEvent.keyCodeFromString("KEYCODE_" + androidKey);
        if (code != KeyEvent.KEYCODE_UNKNOWN) sendKey(connection, code, modifiers);
    }
    private boolean contextAction(InputConnection connection, String key, int modifiers) {
        int action = switch (ImePolicy.menu(key, modifiers, terminal)) {
            case SELECT_ALL -> android.R.id.selectAll; case COPY -> android.R.id.copy; case CUT -> android.R.id.cut;
            case PASTE -> android.R.id.paste; case UNDO -> android.R.id.undo; case REDO -> android.R.id.redo; default -> 0;
        };
        return action != 0 && connection.performContextMenuAction(action);
    }
    private static boolean hasShortcutModifiers(int modifiers) { return (modifiers & (KeyboardSession.CTRL | KeyboardSession.ALT | KeyboardSession.META)) != 0; }
    private static void delete(InputConnection connection, boolean forward) {
        connection.beginBatchEdit();
        try {
            CharSequence selected = connection.getSelectedText(0);
            if (selected != null && selected.length() != 0) { connection.commitText("", 1); return; }
            if (connection.deleteSurroundingTextInCodePoints(forward ? 0 : 1, forward ? 1 : 0)) return;
            // Old/custom editors may not implement code-point deletion. Keep surrogate pairs intact.
            CharSequence near = forward ? connection.getTextAfterCursor(2, 0) : connection.getTextBeforeCursor(2, 0);
            if (near == null) { sendKey(connection, forward ? KeyEvent.KEYCODE_FORWARD_DEL : KeyEvent.KEYCODE_DEL, 0); return; }
            int count = 1;
            if (near.length() >= 2 && (forward ? Character.isSurrogatePair(near.charAt(0), near.charAt(1)) : Character.isSurrogatePair(near.charAt(near.length() - 2), near.charAt(near.length() - 1)))) count = 2;
            if (!connection.deleteSurroundingText(forward ? 0 : count, forward ? count : 0)) sendKey(connection, forward ? KeyEvent.KEYCODE_FORWARD_DEL : KeyEvent.KEYCODE_DEL, 0);
        } finally { connection.endBatchEdit(); }
    }
    private static boolean sendCharacters(InputConnection connection, String text, int modifiers) {
        String value = (modifiers & KeyboardSession.CAPS_LOCK) != 0 && (modifiers & KeyboardSession.SHIFT) == 0 ? text.toLowerCase(java.util.Locale.ROOT) : text;
        KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(value.toCharArray());
        if (events == null) return false;
        for (KeyEvent event : events) if (!KeyEvent.isModifierKey(event.getKeyCode())) {
            long now = SystemClock.uptimeMillis();
            int meta = KeyEvent.normalizeMetaState(event.getMetaState() | metaState(modifiers));
            connection.sendKeyEvent(new KeyEvent(now, now, event.getAction(), event.getKeyCode(), 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD));
        }
        return true;
    }
    private static int metaState(int modifiers) {
        return ((modifiers & KeyboardSession.SHIFT) == 0 ? 0 : KeyEvent.META_SHIFT_ON)
                | ((modifiers & KeyboardSession.CTRL) == 0 ? 0 : KeyEvent.META_CTRL_ON)
                | ((modifiers & KeyboardSession.ALT) == 0 ? 0 : KeyEvent.META_ALT_ON)
                | ((modifiers & KeyboardSession.CAPS_LOCK) == 0 ? 0 : KeyEvent.META_CAPS_LOCK_ON)
                | ((modifiers & KeyboardSession.META) == 0 ? 0 : KeyEvent.META_META_ON);
    }
    private static void sendKey(InputConnection connection, int code, int modifiers) {
        long now = SystemClock.uptimeMillis(); int meta = KeyEvent.normalizeMetaState(metaState(modifiers));
        connection.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD));
        connection.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD));
    }
}
