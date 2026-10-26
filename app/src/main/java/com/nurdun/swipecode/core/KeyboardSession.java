package com.nurdun.swipecode.core;

/** One-shot modifier state and key routing, shared by the prototype and a future IME. */
public final class KeyboardSession {
    public static final int SHIFT = 1, CTRL = 2, ALT = 4, CAPS_LOCK = 8, META = 16;
    public interface Sink {
        void insertText(String text, int modifiers);
        void backspace(int modifiers);
        void enter(int modifiers);
        void tab(int modifiers);
        void moveCursor(Direction direction, int modifiers);
        void key(String key, int modifiers);
    }
    private KeyboardLayout layout;
    private Sink sink;
    private final ShiftState shift = new ShiftState();
    private boolean ctrl, alt;

    public KeyboardSession(KeyboardLayout layout) { this.layout = layout; }
    public void setLayout(KeyboardLayout layout) { this.layout = layout; }
    public void setSink(Sink sink) { this.sink = sink; }
    public void reset() { shift.reset(); ctrl = false; alt = false; }
    public int modifiers() { return (shift.mode() == ShiftState.Mode.ONCE ? SHIFT : shift.mode() == ShiftState.Mode.LOCKED ? CAPS_LOCK : 0) | (ctrl ? CTRL : 0) | (alt ? ALT : 0); }
    public ShiftState.Mode shiftMode() { return shift.mode(); }
    public String letter(char lower) { return shift.letter(lower); }
    public static String modifierLabel(int modifiers) {
        StringBuilder text = new StringBuilder();
        if ((modifiers & CTRL) != 0) text.append("Ctrl");
        if ((modifiers & ALT) != 0) { if (text.length() > 0) text.append(" + "); text.append("Alt"); }
        if ((modifiers & SHIFT) != 0) { if (text.length() > 0) text.append(" + "); text.append("Shift"); }
        if ((modifiers & CAPS_LOCK) != 0) { if (text.length() > 0) text.append(" + "); text.append("Caps"); }
        if ((modifiers & META) != 0) { if (text.length() > 0) text.append(" + "); text.append("Meta"); }
        return text.toString();
    }
    public String type(char letter, Direction direction) {
        return activate(String.valueOf(Character.toLowerCase(letter)), direction, 0);
    }
    public String preview(String key, Direction direction) {
        KeyBinding binding = layout.binding(key, direction);
        if (binding.kind == KeyBinding.Kind.COMMAND && binding.value.equals("SHIFT") && shift.mode() == ShiftState.Mode.LOCKED) return "Caps";
        if (direction == Direction.CENTER && binding.kind == KeyBinding.Kind.TEXT && binding.value.length() == 1 && Character.isLetter(binding.value.charAt(0))) return shift.letter(binding.value.charAt(0));
        return binding.label();
    }
    public String activate(String key, Direction direction, long now) {
        KeyBinding binding = layout.binding(key, direction);
        if (binding.kind == KeyBinding.Kind.COMMAND) {
            switch (binding.value) {
                case "SHIFT" -> shift.tap(now); case "CTRL" -> ctrl = !ctrl; case "ALT" -> alt = !alt;
                // Navigation commands are performed by the keyboard view.
                case "CUSTOMIZE" -> { }
            }
            return binding.label();
        }
        if (binding.kind == KeyBinding.Kind.KEY) {
            int flags = modifiers() | binding.modifiers;
            if (sink != null) switch (binding.value) {
                case "BACKSPACE" -> sink.backspace(flags);
                case "TAB" -> sink.tab(flags);
                case "ENTER" -> sink.enter(flags);
                case "SPACE" -> sink.insertText(" ", flags);
                default -> sink.key(binding.value, flags);
            }
            if (!binding.isArrow() && !binding.value.equals("BACKSPACE")) consumeCharacter();
            return binding.label();
        }
        String value = direction == Direction.CENTER && binding.value.length() == 1 && Character.isLetter(binding.value.charAt(0)) ? shift.letter(binding.value.charAt(0)) : binding.value;
        if (!value.isEmpty()) {
            if (sink != null) sink.insertText(value, modifiers());
            consumeCharacter();
        }
        return value;
    }
    public boolean press(String key, long now) {
        if (KeyboardLayout.KEY_IDS.contains(key)) { activate(key, Direction.CENTER, now); return true; }
        switch (key) {
            case "LEFT" -> { if (sink != null) sink.moveCursor(Direction.LEFT, modifiers()); }
            case "RIGHT" -> { if (sink != null) sink.moveCursor(Direction.RIGHT, modifiers()); }
            case "UP" -> { if (sink != null) sink.moveCursor(Direction.UP, modifiers()); }
            case "DOWN" -> { if (sink != null) sink.moveCursor(Direction.DOWN, modifiers()); }
            default -> { return false; }
        }
        return true;
    }
    private void consumeCharacter() {
        ctrl = false; alt = false; shift.consumeLetter();
    }
}
