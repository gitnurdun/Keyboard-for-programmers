package com.nurdun.swipecode.core;

/** Platform-independent routing decisions; values follow Android EditorInfo constants. */
public final class ImePolicy {
    public enum Menu { NONE, SELECT_ALL, COPY, CUT, PASTE, UNDO, REDO }
    private ImePolicy() {}
    public static boolean terminalEditor(int inputType, String packageName) {
        return (inputType & 15) == 0 || packageName != null && (packageName.equals("com.termux") || packageName.startsWith("com.termux."));
    }
    public static Menu menu(String key, int modifiers, boolean terminal) {
        if (terminal || (modifiers & KeyboardSession.CTRL) == 0 || (modifiers & (KeyboardSession.ALT | KeyboardSession.META)) != 0) return Menu.NONE;
        return switch (key.toUpperCase(java.util.Locale.ROOT)) {
            case "A" -> Menu.SELECT_ALL; case "C" -> Menu.COPY; case "X" -> Menu.CUT;
            case "V" -> Menu.PASTE; case "Z" -> (modifiers & KeyboardSession.SHIFT) != 0 ? Menu.REDO : Menu.UNDO;
            case "Y" -> Menu.REDO; default -> Menu.NONE;
        };
    }
    /** Zero means literal Enter. Do not turn terminal Enter or Shift+Enter into Send/Done. */
    public static int enterAction(int options, int customId, boolean customLabel, int modifiers, boolean terminal) {
        if (terminal || (modifiers & ~KeyboardSession.CAPS_LOCK) != 0 || (options & 0x40000000) != 0) return 0;
        if (customLabel && customId != 0) return customId;
        int action = options & 255;
        return action >= 2 && action <= 7 ? action : 0;
    }
}
