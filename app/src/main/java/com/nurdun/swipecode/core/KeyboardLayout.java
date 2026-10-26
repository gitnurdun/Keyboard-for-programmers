package com.nurdun.swipecode.core;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/** Device-independent layout model with immutable, typed swipe bindings. */
public final class KeyboardLayout {
    public static final String[] ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    public static final String LETTERS = String.join("", ROWS);
    public static final String[] TOP_KEYS = {"ESC", "TOP_SHIFT", "CTRL", "ALT", "TAB", "DELETE"};
    public static final List<String> KEY_IDS;
    static {
        List<String> ids = new ArrayList<>(java.util.Arrays.asList(TOP_KEYS));
        for (char letter : LETTERS.toCharArray()) ids.add(String.valueOf(letter));
        ids.addAll(java.util.Arrays.asList("SHIFT", "BACKSPACE", "SETTINGS", "SPACE", "ENTER"));
        KEY_IDS = java.util.Collections.unmodifiableList(ids);
    }
    private final Map<String, EnumMap<Direction, KeyBinding>> keys = new LinkedHashMap<>();

    public KeyboardLayout() {
        for (String key : KEY_IDS) {
            EnumMap<Direction, KeyBinding> slots = new EnumMap<>(Direction.class);
            for (Direction direction : Direction.values()) slots.put(direction, KeyBinding.text(""));
            slots.put(Direction.CENTER, defaultTap(key)); keys.put(key, slots);
        }
    }
    public static boolean isLetter(String key) { return key != null && key.length() == 1 && LETTERS.contains(key); }
    public static String keyName(String key) {
        return isLetter(key) ? key.toUpperCase(java.util.Locale.ROOT) : switch (key) { case "TOP_SHIFT", "SHIFT" -> "Shift"; case "CTRL" -> "Ctrl"; case "ALT" -> "Alt"; case "SETTINGS" -> "定制"; default -> KeyBinding.keyLabel(key); };
    }
    private static KeyBinding defaultTap(String key) {
        if (isLetter(key)) return KeyBinding.text(key);
        return switch (key) {
            case "TOP_SHIFT", "SHIFT" -> KeyBinding.command("SHIFT");
            case "CTRL", "ALT" -> KeyBinding.command(key);
            case "SETTINGS" -> KeyBinding.command("CUSTOMIZE");
            default -> KeyBinding.key(key, 0);
        };
    }

    public static KeyboardLayout defaults() {
        KeyboardLayout layout = new KeyboardLayout();
        String digits = "1234567890";
        for (int i = 0; i < ROWS[0].length(); i++) layout.set(ROWS[0].charAt(i), Direction.UP, digits.substring(i, i + 1));
        layout.pair('a', "(", ")"); layout.pair('s', "[", "]");
        layout.pair('d', "{", "}"); layout.pair('f', "<", ">");
        layout.pair('g', "'", "\""); layout.set('g', Direction.UP, "`");
        layout.pair('h', ",", "."); layout.set('h', Direction.UP, "?"); layout.set('h', Direction.DOWN, "!");
        layout.pair('j', "+", "-"); layout.set('j', Direction.UP, "*"); layout.set('j', Direction.DOWN, "/");
        layout.pair('k', "=", "%"); layout.set('k', Direction.UP, "~"); layout.set('k', Direction.DOWN, "^");
        layout.pair('l', "&", "|"); layout.set('l', Direction.UP, "$"); layout.set('l', Direction.DOWN, "#");
        layout.pair('z', ":", ";"); layout.set('z', Direction.UP, "_"); layout.set('z', Direction.DOWN, "@");
        layout.set('x', Direction.LEFT, "\\");
        layout.bind("ESC", Direction.UP, KeyBinding.key("PAGE_UP", 0));
        layout.bind("ESC", Direction.DOWN, KeyBinding.key("PAGE_DOWN", 0));
        layout.bind("TOP_SHIFT", Direction.UP, KeyBinding.key("TAB", 0));
        layout.bind("CTRL", Direction.UP, KeyBinding.key("A", KeyboardSession.CTRL));
        layout.bind("CTRL", Direction.RIGHT, KeyBinding.key("C", KeyboardSession.CTRL));
        layout.bind("CTRL", Direction.LEFT, KeyBinding.key("P", KeyboardSession.CTRL));
        layout.bind("CTRL", Direction.DOWN, KeyBinding.key("L", KeyboardSession.CTRL));
        for (Direction direction : Direction.values()) if (direction != Direction.CENTER) layout.bind("ALT", direction, KeyBinding.key(direction.name(), 0));
        return layout;
    }

    /** Removes only the former built-in duplicates while upgrading pre-IME layouts. */
    public void removeLegacyLetterPresets() {
        String[][] presets = {
                {"v", "UP", "UP", "0"}, {"v", "DOWN", "DOWN", "0"}, {"v", "LEFT", "LEFT", "0"}, {"v", "RIGHT", "RIGHT", "0"},
                {"c", "UP", "L", "2"}, {"c", "LEFT", "C", "2"}, {"c", "RIGHT", "V", "2"}, {"c", "DOWN", "A", "2"},
                {"p", "DOWN", "P", "2"}, {"x", "UP", "X", "2"}, {"x", "DOWN", "ESC", "0"},
                {"b", "UP", "HOME", "0"}, {"b", "DOWN", "END", "0"}, {"b", "LEFT", "PAGE_UP", "0"}, {"b", "RIGHT", "PAGE_DOWN", "0"},
                {"n", "UP", "Z", "2"}, {"n", "DOWN", "Y", "2"}, {"m", "UP", "TAB", "0"}, {"m", "DOWN", "ENTER", "0"}
        };
        for (String[] preset : presets) {
            Direction direction = Direction.valueOf(preset[1]);
            if (binding(preset[0], direction).equals(KeyBinding.key(preset[2], Integer.parseInt(preset[3])))) bind(preset[0], direction, KeyBinding.text(""));
        }
    }

    private void pair(char letter, String left, String right) {
        set(letter, Direction.LEFT, left); set(letter, Direction.RIGHT, right);
    }

    public String get(char letter, Direction direction) {
        KeyBinding value = binding(letter, direction);
        return value.kind == KeyBinding.Kind.TEXT ? value.value : "";
    }

    public void set(char letter, Direction direction, String symbol) {
        letter = Character.toLowerCase(letter);
        if (!keys.containsKey(String.valueOf(letter)) || direction == Direction.CENTER || !validSymbol(symbol))
            throw new IllegalArgumentException("A direction must contain one Unicode character or be empty");
        bind(String.valueOf(letter), direction, KeyBinding.text(symbol));
    }

    public KeyBinding binding(char letter, Direction direction) {
        return binding(String.valueOf(Character.toLowerCase(letter)), direction);
    }
    public KeyBinding binding(String key, Direction direction) {
        if (!keys.containsKey(key) || direction == null) throw new IllegalArgumentException("Unknown key or direction");
        return keys.get(key).get(direction);
    }
    public void bind(char letter, Direction direction, KeyBinding binding) {
        letter = Character.toLowerCase(letter);
        if (direction == Direction.CENTER) throw new IllegalArgumentException("Use the all-key API to configure taps");
        bind(String.valueOf(letter), direction, binding);
    }
    public void bind(String key, Direction direction, KeyBinding binding) {
        if (!keys.containsKey(key) || direction == null || binding == null) throw new IllegalArgumentException("Invalid binding");
        keys.get(key).put(direction, binding);
    }

    public void restoreKey(char letter) {
        restoreKey(String.valueOf(Character.toLowerCase(letter)));
    }
    public void restoreKey(String key) {
        KeyboardLayout defaults = defaults();
        for (Direction direction : Direction.values()) bind(key, direction, defaults.binding(key, direction));
    }

    public KeyboardLayout copy() {
        KeyboardLayout result = new KeyboardLayout();
        for (String key : KEY_IDS)
            for (Direction direction : Direction.values())
                result.bind(key, direction, binding(key, direction));
        return result;
    }

    public static boolean validSymbol(String text) {
        if (text == null) return false;
        if (text.isEmpty()) return true;
        if (text.codePointCount(0, text.length()) != 1) return false;
        int cp = text.codePointAt(0);
        return !Character.isISOControl(cp) && !(cp >= 0xD800 && cp <= 0xDFFF);
    }
}
