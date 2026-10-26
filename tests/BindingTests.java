import com.nurdun.swipecode.core.*;
import java.util.*;

/** Exercises full custom-action persistence and dispatch, including upgrades from symbol-only layouts. */
public final class BindingTests {
    private static int checks;
    private static void eq(Object expected, Object actual) { checks++; if (!Objects.equals(expected, actual)) throw new AssertionError("Expected [" + expected + "], got [" + actual + "]"); }
    private static void invalid(Runnable action) { checks++; try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Invalid binding accepted"); }
    private record Event(String key, String text, int flags) {}
    private static final class Sink implements KeyboardSession.Sink {
        final List<Event> events = new ArrayList<>();
        public void insertText(String text, int flags) { events.add(new Event("TEXT", text, flags)); }
        public void key(String key, int flags) { events.add(new Event(key, "", flags)); }
        public void backspace(int flags) { key("BACKSPACE", flags); }
        public void enter(int flags) { key("ENTER", flags); }
        public void tab(int flags) { key("TAB", flags); }
        public void moveCursor(Direction direction, int flags) { key(direction.name(), flags); }
        Event last() { return events.get(events.size() - 1); }
    }
    public static void main(String[] args) {
        KeyboardLayout layout = KeyboardLayout.defaults();
        for (Direction d : Direction.values()) if (d != Direction.CENTER) eq(KeyBinding.text(""), layout.binding('v', d));
        eq(KeyBinding.text(""), layout.binding('c', Direction.UP));
        eq(KeyBinding.text(""), layout.binding('c', Direction.LEFT));
        eq(KeyBinding.text(""), layout.binding('p', Direction.DOWN));
        eq(KeyBinding.text(""), layout.binding('x', Direction.DOWN));
        // Every direction accepts both custom text and a complete physical-key combination.
        int index = 0;
        for (char c : KeyboardLayout.LETTERS.toCharArray()) for (Direction d : Direction.values()) if (d != Direction.CENTER) {
            KeyBinding value = index % 3 == 0 ? KeyBinding.text("中😀\\\"'" + index) : KeyBinding.key(KeyBinding.KEYS[index % KeyBinding.KEYS.length], index % 3 == 1 ? 23 : 0);
            layout.bind(c, d, value); eq(value, layout.binding(c, d)); index++;
        }
        KeyboardLayout restored = LayoutCodec.decode(LayoutCodec.encode(layout));
        for (char c : KeyboardLayout.LETTERS.toCharArray()) for (Direction d : Direction.values()) eq(layout.binding(c, d), restored.binding(c, d));
        KeyboardLayout copy = layout.copy(); copy.bind('a', Direction.UP, KeyBinding.key("F12", KeyboardSession.ALT)); eq(false, copy.binding('a', Direction.UP).equals(layout.binding('a', Direction.UP)));
        copy.restoreKey('a'); eq(KeyBinding.text("("), copy.binding('a', Direction.LEFT));
        // Upgrade retains explicit old symbols and leaves ordinary navigation directions free.
        KeyboardLayout upgraded = LayoutCodec.decode("version=1\ng.LEFT='\nc.UP=\nv.LEFT=中\na.LEFT=\n");
        eq(KeyBinding.text("'"), upgraded.binding('g', Direction.LEFT));
        eq(KeyBinding.text(""), upgraded.binding('c', Direction.UP));
        eq(KeyBinding.text("中"), upgraded.binding('v', Direction.LEFT));
        eq(KeyBinding.text(""), upgraded.binding('a', Direction.LEFT));
        // Clearing an action in the new format stays cleared on subsequent launches.
        upgraded.bind('c', Direction.UP, KeyBinding.text("")); eq(KeyBinding.text(""), LayoutCodec.decode(LayoutCodec.encode(upgraded)).binding('c', Direction.UP));
        String broken = "version=2\nc.UP.type=KEY\nc.UP.value=INVALID\nc.UP.modifiers=2\na.LEFT.type=TEXT\na.LEFT.value=hello\n";
        eq(KeyBinding.text(""), LayoutCodec.decode(broken).binding('c', Direction.UP));
        eq(KeyBinding.text("hello"), LayoutCodec.decode(broken).binding('a', Direction.LEFT));
        eq(KeyBinding.text(""), LayoutCodec.decode("version=2\nc.UP.type=KEY\nc.UP.value=L\nc.UP.modifiers=garbage\n").binding('c', Direction.UP));
        invalid(() -> KeyBinding.key("INVALID", 0)); invalid(() -> KeyBinding.key("A", 8)); invalid(() -> KeyBinding.key("A", -1)); invalid(() -> KeyBinding.key(null, 0));
        invalid(() -> KeyBinding.text("x".repeat(65))); invalid(() -> KeyBinding.text("\n")); invalid(() -> KeyBinding.text("\uD800")); invalid(() -> KeyBinding.text(null));
        eq("e\u0301", KeyBinding.text("e\u0301").value); eq("👨‍💻", KeyBinding.text("👨‍💻").value); eq(true, KeyBinding.text("x".repeat(64)).value.length() == 64);
        invalid(() -> copy.bind('a', Direction.CENTER, KeyBinding.key("UP", 0)));
        eq("Ctrl + Alt + Shift + Meta + F12", KeyBinding.key("F12", 23).label());
        eq("^L", KeyBinding.key("L", 2).compactLabel()); eq("↑", KeyBinding.key("UP", 0).compactLabel());
        Sink sink = new Sink(); KeyboardSession session = new KeyboardSession(KeyboardLayout.defaults()); session.setSink(sink);
        session.activate("CTRL", Direction.DOWN, 900); eq(new Event("L", "", 2), sink.last()); eq(0, session.modifiers());
        session.activate("CTRL", Direction.RIGHT, 910); eq(new Event("C", "", 2), sink.last());
        session.activate("CTRL", Direction.LEFT, 920); eq(new Event("P", "", 2), sink.last()); eq(0, session.modifiers());
        session.press("TOP_SHIFT", 1000); session.activate("ALT", Direction.LEFT, 1100); eq(new Event("LEFT", "", 1), sink.last()); eq(1, session.modifiers());
        session.type('a', Direction.CENTER); eq(new Event("TEXT", "A", 1), sink.last()); eq(0, session.modifiers());
        layout.bind('g', Direction.UP, KeyBinding.key("F12", KeyboardSession.CTRL)); session.setLayout(layout);
        session.press("ALT", 2000); session.type('g', Direction.UP); eq(new Event("F12", "", 6), sink.last()); eq(0, session.modifiers());
        layout.bind('g', Direction.DOWN, KeyBinding.text("hello😀")); session.type('g', Direction.DOWN); eq(new Event("TEXT", "hello😀", 0), sink.last());
        layout.bind('g', Direction.LEFT, KeyBinding.text("")); session.press("CTRL", 3000); int before = sink.events.size(); session.type('g', Direction.LEFT); eq(before, sink.events.size()); eq(2, session.modifiers());
        session.press("ESC", 3100); eq(new Event("ESC", "", 2), sink.last()); eq(0, session.modifiers());
        session.press("DELETE", 3200); eq(new Event("DELETE", "", 0), sink.last());
        // All supported key choices reach the sink with their exact modifier set.
        for (String key : KeyBinding.KEYS) {
            layout.bind('a', Direction.UP, KeyBinding.key(key, 23)); session.type('a', Direction.UP); eq(key.equals("SPACE") ? new Event("TEXT", " ", 23) : new Event(key, "", 23), sink.last());
        }
        System.out.println("PASS: " + checks + " custom-binding assertions across keys, shortcuts, text, migration, persistence, and dispatch.");
    }
}
