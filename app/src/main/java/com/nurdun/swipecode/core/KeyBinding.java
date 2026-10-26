package com.nurdun.swipecode.core;

import java.util.Arrays;
import java.util.Objects;

/** A swipe emits text or one physical key with optional modifiers. */
public final class KeyBinding {
    public enum Kind { TEXT, KEY, COMMAND }
    public static final String[] COMMANDS = {"SHIFT", "CTRL", "ALT", "CUSTOMIZE"};
    public static final String[] KEYS;
    static {
        java.util.List<String> keys = new java.util.ArrayList<>(Arrays.asList(
                "UP", "DOWN", "LEFT", "RIGHT", "ESC", "TAB", "ENTER", "BACKSPACE", "DELETE",
                "HOME", "END", "PAGE_UP", "PAGE_DOWN", "INSERT", "SPACE",
                "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"));
        for (char c = 'A'; c <= 'Z'; c++) keys.add(String.valueOf(c));
        for (char c = '0'; c <= '9'; c++) keys.add(String.valueOf(c));
        keys.addAll(Arrays.asList("GRAVE", "MINUS", "EQUALS", "LEFT_BRACKET", "RIGHT_BRACKET", "BACKSLASH", "SEMICOLON", "APOSTROPHE", "COMMA", "PERIOD", "SLASH", "PRINT_SCREEN", "PAUSE"));
        KEYS = keys.toArray(new String[0]);
    }
    public final Kind kind;
    public final String value;
    public final int modifiers;
    private KeyBinding(Kind kind, String value, int modifiers) { this.kind = kind; this.value = value; this.modifiers = modifiers; }
    public static KeyBinding text(String value) {
        if (!validText(value)) throw new IllegalArgumentException("请输入最多64个字符，不含控制字符或不完整Unicode字符");
        return new KeyBinding(Kind.TEXT, value, 0);
    }
    public static KeyBinding key(String key, int modifiers) {
        if (!Arrays.asList(KEYS).contains(key) || (modifiers & ~23) != 0) throw new IllegalArgumentException("Invalid key combination");
        return new KeyBinding(Kind.KEY, key, modifiers);
    }
    public static KeyBinding command(String command) {
        if (!Arrays.asList(COMMANDS).contains(command)) throw new IllegalArgumentException("Invalid keyboard action");
        return new KeyBinding(Kind.COMMAND, command, 0);
    }
    public static boolean validText(String text) {
        if (text == null || text.codePointCount(0, text.length()) > 64) return false;
        return text.codePoints().noneMatch(cp -> Character.isISOControl(cp) || (cp >= 0xD800 && cp <= 0xDFFF));
    }
    public boolean isEmpty() { return kind == Kind.TEXT && value.isEmpty(); }
    public boolean isArrow() { return kind == Kind.KEY && (value.equals("UP") || value.equals("DOWN") || value.equals("LEFT") || value.equals("RIGHT")); }
    public boolean repeats() { return kind == Kind.KEY && (isArrow() || value.equals("BACKSPACE") || value.equals("DELETE") || value.equals("PAGE_UP") || value.equals("PAGE_DOWN")); }
    public String label() {
        if (kind == Kind.TEXT) return value.equals(" ") ? "␠" : value;
        if (kind == Kind.COMMAND) return commandLabel(value);
        String prefix = KeyboardSession.modifierLabel(modifiers);
        return (prefix.isEmpty() ? "" : prefix + " + ") + keyLabel(value);
    }
    public String compactLabel() {
        if (kind != Kind.KEY) return label();
        return ((modifiers & KeyboardSession.CTRL) != 0 ? "^" : "")
                + ((modifiers & KeyboardSession.ALT) != 0 ? "⌥" : "")
                + ((modifiers & KeyboardSession.SHIFT) != 0 ? "⇧" : "")
                + ((modifiers & KeyboardSession.META) != 0 ? "◆" : "") + keyLabel(value);
    }
    public static String commandLabel(String command) {
        return switch (command) { case "SHIFT" -> "Shift"; case "CTRL" -> "Ctrl"; case "ALT" -> "Alt"; case "CUSTOMIZE" -> "定制"; default -> command; };
    }
    public static String keyLabel(String key) {
        return switch (key) {
            case "UP" -> "↑"; case "DOWN" -> "↓"; case "LEFT" -> "←"; case "RIGHT" -> "→";
            case "ESC" -> "Esc"; case "TAB" -> "Tab"; case "ENTER" -> "↵";
            case "BACKSPACE" -> "⌫"; case "DELETE" -> "Del";
            case "HOME" -> "Home"; case "END" -> "End"; case "PAGE_UP" -> "PgUp"; case "PAGE_DOWN" -> "PgDn";
            case "INSERT" -> "Ins"; case "SPACE" -> "Space";
            case "GRAVE" -> "`"; case "MINUS" -> "-"; case "EQUALS" -> "=";
            case "LEFT_BRACKET" -> "["; case "RIGHT_BRACKET" -> "]"; case "BACKSLASH" -> "\\";
            case "SEMICOLON" -> ";"; case "APOSTROPHE" -> "'"; case "COMMA" -> ","; case "PERIOD" -> "."; case "SLASH" -> "/";
            case "PRINT_SCREEN" -> "PrtSc"; case "PAUSE" -> "Pause"; default -> key;
        };
    }
    @Override public boolean equals(Object other) { return other instanceof KeyBinding b && kind == b.kind && value.equals(b.value) && modifiers == b.modifiers; }
    @Override public int hashCode() { return Objects.hash(kind, value, modifiers); }
}
