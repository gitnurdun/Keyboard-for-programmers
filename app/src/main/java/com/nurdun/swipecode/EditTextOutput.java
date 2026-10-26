package com.nurdun.swipecode;

import android.text.Editable;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.widget.EditText;
import com.nurdun.swipecode.core.Direction;
import com.nurdun.swipecode.core.KeyboardSession;
import com.nurdun.swipecode.core.TextEdits;

public final class EditTextOutput implements KeyboardOutput {
    private final EditText editor;
    public EditTextOutput(EditText editor) { this.editor = editor; }
    @Override public void insertText(String text, int modifiers) {
        if ((modifiers & (KeyboardSession.CTRL | KeyboardSession.ALT | KeyboardSession.META)) == 0) {
            apply(TextEdits.insert(editor.getText(), editor.getSelectionStart(), editor.getSelectionEnd(), text));
            return;
        }
        editor.requestFocus();
        if ((modifiers & KeyboardSession.CTRL) != 0 && (modifiers & (KeyboardSession.ALT | KeyboardSession.META)) == 0 && text.length() == 1) {
            int menuAction = switch (Character.toLowerCase(text.charAt(0))) {
                case 'a' -> android.R.id.selectAll;
                case 'c' -> android.R.id.copy;
                case 'v' -> android.R.id.paste;
                case 'x' -> android.R.id.cut;
                case 'z' -> (modifiers & KeyboardSession.SHIFT) != 0 ? android.R.id.redo : android.R.id.undo;
                case 'y' -> android.R.id.redo;
                default -> 0;
            };
            if (menuAction != 0) { editor.onTextContextMenuItem(menuAction); return; }
        }
        // Use actual Android key events for Alt and other control combinations.
        // Unhandled shortcuts do not insert an unrelated plain character.
        String eventText = (modifiers & KeyboardSession.CAPS_LOCK) != 0 && (modifiers & KeyboardSession.SHIFT) == 0 ? text.toLowerCase(java.util.Locale.ROOT) : text;
        KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(eventText.toCharArray());
        if (events == null) return;
        for (KeyEvent event : events) if (!KeyEvent.isModifierKey(event.getKeyCode())) {
            long now = SystemClock.uptimeMillis();
            int meta = KeyEvent.normalizeMetaState(event.getMetaState() | metaState(modifiers));
            editor.dispatchKeyEvent(new KeyEvent(now, now, event.getAction(), event.getKeyCode(), 0, meta,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD));
        }
    }
    @Override public void backspace(int modifiers) {
        if ((modifiers & (KeyboardSession.CTRL | KeyboardSession.ALT | KeyboardSession.META)) != 0) dispatchKey(KeyEvent.KEYCODE_DEL, modifiers);
        else apply(TextEdits.backspace(editor.getText(), editor.getSelectionStart(), editor.getSelectionEnd()));
    }
    @Override public void enter(int modifiers) {
        if ((modifiers & (KeyboardSession.CTRL | KeyboardSession.ALT | KeyboardSession.META)) != 0) dispatchKey(KeyEvent.KEYCODE_ENTER, modifiers);
        else insertText("\n", 0);
    }
    @Override public void tab(int modifiers) {
        if ((modifiers & (KeyboardSession.CTRL | KeyboardSession.ALT | KeyboardSession.META)) != 0) dispatchKey(KeyEvent.KEYCODE_TAB, modifiers);
        else insertText("\t", 0);
    }
    @Override public void moveCursor(Direction direction, int modifiers) {
        int code = switch (direction) {
            case LEFT -> KeyEvent.KEYCODE_DPAD_LEFT;
            case RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT;
            case UP -> KeyEvent.KEYCODE_DPAD_UP;
            case DOWN -> KeyEvent.KEYCODE_DPAD_DOWN;
            default -> throw new IllegalArgumentException("A cursor action requires a direction");
        };
        dispatchKey(code, modifiers);
    }
    @Override public void key(String key, int modifiers) {
        // Letters route through the same native editor shortcuts as sticky Ctrl/Alt.
        if (key.length() == 1 && "ACVXYZ".contains(key) && (modifiers & KeyboardSession.CTRL) != 0 && (modifiers & (KeyboardSession.ALT | KeyboardSession.META)) == 0) {
            String text = (modifiers & (KeyboardSession.SHIFT | KeyboardSession.CAPS_LOCK)) != 0 ? key : key.toLowerCase(java.util.Locale.ROOT);
            insertText(text, modifiers); return;
        }
        switch (key) {
            case "TAB" -> { tab(modifiers); return; }
            case "ENTER" -> { enter(modifiers); return; }
            case "BACKSPACE" -> { backspace(modifiers); return; }
            case "SPACE" -> { insertText(" ", modifiers); return; }
        }
        String androidKey = switch (key) {
            case "UP", "DOWN", "LEFT", "RIGHT" -> "DPAD_" + key;
            case "ESC" -> "ESCAPE"; case "DELETE" -> "FORWARD_DEL";
            case "HOME" -> "MOVE_HOME"; case "END" -> "MOVE_END";
            case "PRINT_SCREEN" -> "SYSRQ"; case "PAUSE" -> "BREAK";
            default -> key;
        };
        int code = KeyEvent.keyCodeFromString("KEYCODE_" + androidKey);
        if (code != KeyEvent.KEYCODE_UNKNOWN) dispatchKey(code, modifiers);
    }
    private static int metaState(int modifiers) {
        return ((modifiers & KeyboardSession.SHIFT) == 0 ? 0 : KeyEvent.META_SHIFT_ON)
                | ((modifiers & KeyboardSession.CTRL) == 0 ? 0 : KeyEvent.META_CTRL_ON)
                | ((modifiers & KeyboardSession.ALT) == 0 ? 0 : KeyEvent.META_ALT_ON)
                | ((modifiers & KeyboardSession.CAPS_LOCK) == 0 ? 0 : KeyEvent.META_CAPS_LOCK_ON)
                | ((modifiers & KeyboardSession.META) == 0 ? 0 : KeyEvent.META_META_ON);
    }
    private void dispatchKey(int code, int modifiers) {
        editor.requestFocus();
        long now = SystemClock.uptimeMillis(); int meta = KeyEvent.normalizeMetaState(metaState(modifiers));
        editor.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD));
        editor.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD));
    }
    private void apply(TextEdits.Edit edit) {
        Editable text = editor.getText();
        text.replace(edit.start, edit.end, edit.replacement);
        editor.setSelection(edit.cursor());
        editor.requestFocus();
    }
}
