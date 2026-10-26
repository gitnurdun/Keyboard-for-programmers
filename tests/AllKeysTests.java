import com.nurdun.swipecode.core.*;
import java.util.*;

/** Covers the real binding-driven path used for all physical key positions. */
public final class AllKeysTests {
    private static int checks;
    private static void eq(Object expected, Object actual) { checks++; if (!Objects.equals(expected, actual)) throw new AssertionError("Expected [" + expected + "], got [" + actual + "]"); }
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
        KeyboardLayout layout = KeyboardLayout.defaults(); Sink sink = new Sink(); KeyboardSession session = new KeyboardSession(layout); session.setSink(sink);
        eq(37, new HashSet<>(KeyboardLayout.KEY_IDS).size());
        String[] top = {"ESC", "TOP_SHIFT", "CTRL", "ALT", "TAB", "DELETE"}; eq(Arrays.asList(top), Arrays.asList(KeyboardLayout.TOP_KEYS));
        session.activate("ESC", Direction.CENTER, 1000); eq(new Event("ESC", "", 0), sink.last());
        session.activate("ESC", Direction.UP, 1100); eq(new Event("PAGE_UP", "", 0), sink.last());
        session.activate("ESC", Direction.DOWN, 1200); eq(new Event("PAGE_DOWN", "", 0), sink.last());
        session.activate("TOP_SHIFT", Direction.UP, 1300); eq(new Event("TAB", "", 0), sink.last()); eq(0, session.modifiers());
        String[] ctrl = {"A", "C", "P", "L"}; Direction[] dirs = {Direction.UP, Direction.RIGHT, Direction.LEFT, Direction.DOWN};
        for (int i = 0; i < dirs.length; i++) {
            session.activate("CTRL", dirs[i], 2000 + i); eq(new Event(ctrl[i], "", 2), sink.last()); eq(0, session.modifiers());
        }
        for (Direction dir : dirs) { session.activate("ALT", dir, 3000); eq(new Event(dir.name(), "", 0), sink.last()); eq(0, session.modifiers()); }
        int before = sink.events.size(); session.activate("CTRL", Direction.CENTER, 4000); eq(before, sink.events.size()); eq(2, session.modifiers());
        session.activate("CTRL", Direction.LEFT, 4100); eq(new Event("P", "", 2), sink.last()); eq(0, session.modifiers());
        session.activate("ALT", Direction.CENTER, 5000); eq(4, session.modifiers()); session.activate("ALT", Direction.CENTER, 5100); eq(0, session.modifiers());
        session.activate("TOP_SHIFT", Direction.CENTER, 6000); eq(1, session.modifiers());
        session.activate("ALT", Direction.UP, 6100); eq(new Event("UP", "", 1), sink.last()); eq(1, session.modifiers());
        session.activate("q", Direction.CENTER, 6200); eq(new Event("TEXT", "Q", 1), sink.last()); eq(0, session.modifiers());
        session.activate("TOP_SHIFT", Direction.CENTER, 7000); session.activate("SHIFT", Direction.CENTER, 7100); eq(8, session.modifiers());
        session.activate("q", Direction.CENTER, 7200); eq(new Event("TEXT", "Q", 8), sink.last());
        session.activate("SHIFT", Direction.CENTER, 7300); eq(0, session.modifiers());
        session.activate("SPACE", Direction.CENTER, 7400); eq(new Event("TEXT", " ", 0), sink.last());
        session.activate("BACKSPACE", Direction.CENTER, 7500); eq(new Event("BACKSPACE", "", 0), sink.last());
        session.activate("ENTER", Direction.CENTER, 7600); eq(new Event("ENTER", "", 0), sink.last());
        // Direction changes never execute the tap: Ctrl-up followed by a return to center emits only the final choice.
        GestureTracker gestures = new GestureTracker(12, 8); gestures.begin(1, "CTRL", 50, 50);
        gestures.move(1, 50, 30); eq(0, session.modifiers());
        gestures.move(1, 50, 50); GestureTracker.Stroke stroke = gestures.release(1, 50, 50, true);
        session.activate(stroke.key, stroke.direction, 8000); eq(2, session.modifiers());
        session.activate("CTRL", Direction.CENTER, 8100); eq(0, session.modifiers());
        gestures.begin(1, "ALT", 50, 50); gestures.move(1, 50, 30); gestures.cancelAll(); eq(null, gestures.release(1, 50, 30, true)); eq(0, session.modifiers());
        // Every key, including taps, can hold text, a shortcut, a command, or an explicit empty binding.
        int i = 0;
        for (String key : KeyboardLayout.KEY_IDS) for (Direction dir : Direction.values()) {
            KeyBinding binding = switch (i++ % 4) {
                case 0 -> KeyBinding.text("custom😀 " + i); case 1 -> KeyBinding.key("F12", 7);
                case 2 -> KeyBinding.command("ALT"); default -> KeyBinding.text("");
            };
            layout.bind(key, dir, binding); eq(binding, layout.binding(key, dir));
        }
        KeyboardLayout decoded = LayoutCodec.decode(LayoutCodec.encode(layout)); KeyboardLayout copied = layout.copy();
        for (String key : KeyboardLayout.KEY_IDS) for (Direction dir : Direction.values()) {
            eq(layout.binding(key, dir), decoded.binding(key, dir)); eq(layout.binding(key, dir), copied.binding(key, dir));
        }
        decoded.restoreKey("CTRL"); eq(KeyBinding.command("CTRL"), decoded.binding("CTRL", Direction.CENTER)); eq(KeyBinding.key("P", 2), decoded.binding("CTRL", Direction.LEFT));
        decoded.restoreKey("a"); eq(KeyBinding.text("a"), decoded.binding("a", Direction.CENTER));
        KeyboardLayout upgraded = LayoutCodec.decode("version=2\na.LEFT.type=TEXT\na.LEFT.value=中\nv.UP.type=TEXT\nv.UP.value=\n");
        eq(KeyBinding.text("中"), upgraded.binding('a', Direction.LEFT)); eq(KeyBinding.text(""), upgraded.binding('v', Direction.UP));
        eq(KeyBinding.key("PAGE_UP", 0), upgraded.binding("ESC", Direction.UP)); eq(KeyBinding.key("P", 2), upgraded.binding("CTRL", Direction.LEFT));
        upgraded.bind("CTRL", Direction.CENTER, KeyBinding.text("replacement")); upgraded.bind("ALT", Direction.UP, KeyBinding.text(""));
        upgraded = LayoutCodec.decode(LayoutCodec.encode(upgraded)); session.setLayout(upgraded);
        session.activate("CTRL", Direction.CENTER, 9000); eq(new Event("TEXT", "replacement", 0), sink.last()); eq(0, session.modifiers());
        session.press("CTRL", 9050); eq(new Event("TEXT", "replacement", 0), sink.last()); eq(0, session.modifiers());
        before = sink.events.size(); session.activate("ALT", Direction.UP, 9100); eq(before, sink.events.size());
        upgraded.bind("SPACE", Direction.CENTER, KeyBinding.text(" ")); session.activate("SPACE", Direction.CENTER, 9200); eq(new Event("TEXT", " ", 0), sink.last());
        upgraded.bind("a", Direction.CENTER, KeyBinding.text("z")); session.activate("TOP_SHIFT", Direction.CENTER, 9300); session.activate("a", Direction.CENTER, 9400); eq(new Event("TEXT", "Z", 1), sink.last());
        eq(true, KeyBinding.key("PAGE_UP", 0).repeats()); eq(false, KeyBinding.key("A", 2).repeats()); eq(false, KeyBinding.command("CTRL").repeats());
        System.out.println("PASS: " + checks + " all-key assertions covering top-row gestures, editable taps, commands, migration, cancellation, persistence, and restore.");
    }
}
