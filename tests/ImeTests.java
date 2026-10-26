import com.nurdun.swipecode.core.*;
import java.util.Objects;

public final class ImeTests {
    private static int checks;
    private static void eq(Object expected, Object actual) { checks++; if (!Objects.equals(expected, actual)) throw new AssertionError("Expected [" + expected + "], got [" + actual + "]"); }
    public static void main(String[] args) {
        eq(true, ImePolicy.terminalEditor(0, "example")); eq(true, ImePolicy.terminalEditor(0, null));
        eq(true, ImePolicy.terminalEditor(1, "com.termux")); eq(true, ImePolicy.terminalEditor(1, "com.termux.debug"));
        eq(false, ImePolicy.terminalEditor(1, "example")); eq(false, ImePolicy.terminalEditor(1, null)); eq(false, ImePolicy.terminalEditor(1, "com.termuxother"));
        for (String key : new String[]{"A", "C", "V", "X", "Z", "Y", "L", "P"}) {
            eq(ImePolicy.Menu.NONE, ImePolicy.menu(key, 2, true)); eq(ImePolicy.Menu.NONE, ImePolicy.menu(key, 0, false));
            eq(ImePolicy.Menu.NONE, ImePolicy.menu(key, 6, false)); eq(ImePolicy.Menu.NONE, ImePolicy.menu(key, 18, false));
        }
        eq(ImePolicy.Menu.SELECT_ALL, ImePolicy.menu("a", 2, false)); eq(ImePolicy.Menu.COPY, ImePolicy.menu("C", 2, false));
        eq(ImePolicy.Menu.PASTE, ImePolicy.menu("V", 2, false)); eq(ImePolicy.Menu.CUT, ImePolicy.menu("X", 2, false));
        eq(ImePolicy.Menu.UNDO, ImePolicy.menu("Z", 2, false)); eq(ImePolicy.Menu.REDO, ImePolicy.menu("Z", 3, false));
        eq(ImePolicy.Menu.UNDO, ImePolicy.menu("Z", 10, false)); eq(ImePolicy.Menu.REDO, ImePolicy.menu("Y", 2, false));
        eq(ImePolicy.Menu.NONE, ImePolicy.menu("L", 2, false)); eq(ImePolicy.Menu.NONE, ImePolicy.menu("P", 2, false));
        for (int action = 0; action <= 9; action++) {
            eq(action >= 2 && action <= 7 ? action : 0, ImePolicy.enterAction(action, 0, false, 0, false));
            eq(0, ImePolicy.enterAction(action, 0, false, 0, true));
            eq(0, ImePolicy.enterAction(action | 0x40000000, 0, false, 0, false));
            eq(0, ImePolicy.enterAction(action, 0, false, 1, false));
            eq(0, ImePolicy.enterAction(action, 0, false, 2, false));
        }
        eq(42, ImePolicy.enterAction(0, 42, true, 0, false)); eq(0, ImePolicy.enterAction(0, 42, false, 0, false));
        eq(6, ImePolicy.enterAction(6, 0, false, 8, false));
        KeyboardLayout layout = KeyboardLayout.defaults();
        for (char letter : KeyboardLayout.LETTERS.toCharArray()) for (Direction d : Direction.values()) if (d != Direction.CENTER) eq(KeyBinding.Kind.TEXT, layout.binding(letter, d).kind);
        for (char letter : "cvbnm".toCharArray()) for (Direction d : Direction.values()) if (d != Direction.CENTER) eq(true, layout.binding(letter, d).isEmpty());
        eq(KeyBinding.text("("), layout.binding('a', Direction.LEFT)); eq(KeyBinding.text("\\"), layout.binding('x', Direction.LEFT));
        // Simulate a 0.4 layout with duplicates plus real user changes.
        layout.bind('c', Direction.UP, KeyBinding.key("L", 2)); layout.bind('v', Direction.LEFT, KeyBinding.key("LEFT", 0));
        layout.bind('b', Direction.UP, KeyBinding.key("F5", 0)); layout.bind('n', Direction.UP, KeyBinding.text("中"));
        layout.bind("CTRL", Direction.CENTER, KeyBinding.text("custom tap"));
        KeyboardLayout upgraded = LayoutCodec.decode(LayoutCodec.encode(layout).replace("version=4", "version=3"));
        eq(true, upgraded.binding('c', Direction.UP).isEmpty()); eq(true, upgraded.binding('v', Direction.LEFT).isEmpty());
        eq(KeyBinding.key("F5", 0), upgraded.binding('b', Direction.UP)); eq(KeyBinding.text("中"), upgraded.binding('n', Direction.UP));
        eq(KeyBinding.text("custom tap"), upgraded.binding("CTRL", Direction.CENTER));
        // A new intentional binding equal to an old preset must survive v4 round trips.
        upgraded.bind('c', Direction.UP, KeyBinding.key("L", 2)); eq(KeyBinding.key("L", 2), LayoutCodec.decode(LayoutCodec.encode(upgraded)).binding('c', Direction.UP));
        KeyboardSession session = new KeyboardSession(KeyboardLayout.defaults()); session.press("CTRL", 1000); session.press("ALT", 1001); session.press("SHIFT", 1002); eq(7, session.modifiers());
        session.reset(); eq(0, session.modifiers()); eq("a", session.letter('a'));
        session.press("SHIFT", 2000); session.press("SHIFT", 2100); eq(8, session.modifiers()); session.reset(); eq(0, session.modifiers());
        session.press("SHIFT", 2150); eq(1, session.modifiers());
        System.out.println("PASS: " + checks + " IME-policy assertions across terminal shortcuts, editor actions, layout migration, and lifecycle reset.");
    }
}
