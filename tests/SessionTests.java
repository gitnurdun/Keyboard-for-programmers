import com.nurdun.swipecode.core.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Exercises the production key controller without Android or downloaded test libraries. */
public final class SessionTests {
    private static int checks;
    private record Event(String kind, String value, int modifiers) {}
    private static final class RecordingSink implements KeyboardSession.Sink {
        final List<Event> events = new ArrayList<>();
        public void insertText(String text, int modifiers) { events.add(new Event("text", text, modifiers)); }
        public void backspace(int modifiers) { events.add(new Event("backspace", "", modifiers)); }
        public void enter(int modifiers) { events.add(new Event("enter", "\n", modifiers)); }
        public void tab(int modifiers) { events.add(new Event("tab", "\t", modifiers)); }
        public void moveCursor(Direction direction, int modifiers) { events.add(new Event("arrow", direction.name(), modifiers)); }
        public void key(String key, int modifiers) { events.add(new Event("key", key, modifiers)); }
        Event last() { return events.get(events.size() - 1); }
    }
    private static void eq(Object expected, Object actual) {
        checks++; if (!Objects.equals(expected, actual)) throw new AssertionError("Expected [" + expected + "], got [" + actual + "]");
    }
    public static void main(String[] args) {
        KeyboardSession session = new KeyboardSession(KeyboardLayout.defaults()); RecordingSink sink = new RecordingSink(); session.setSink(sink);
        for (char c : KeyboardLayout.LETTERS.toCharArray()) {
            eq(String.valueOf(c), session.type(c, Direction.CENTER)); eq(new Event("text", String.valueOf(c), 0), sink.last());
        }
        eq(0, session.modifiers());
        session.press("TOP_SHIFT", 1000); eq(KeyboardSession.SHIFT, session.modifiers());
        session.type('a', Direction.CENTER); eq(new Event("text", "A", KeyboardSession.SHIFT), sink.last()); eq(0, session.modifiers());
        session.type('a', Direction.CENTER); eq(new Event("text", "a", 0), sink.last());
        session.press("CTRL", 1500); eq(KeyboardSession.CTRL, session.modifiers());
        session.press("LEFT", 1600); eq(new Event("arrow", "LEFT", KeyboardSession.CTRL), sink.last()); eq(KeyboardSession.CTRL, session.modifiers());
        session.press("BACKSPACE", 1700); eq(new Event("backspace", "", KeyboardSession.CTRL), sink.last()); eq(KeyboardSession.CTRL, session.modifiers());
        session.type('a', Direction.CENTER); eq(new Event("text", "a", KeyboardSession.CTRL), sink.last()); eq(0, session.modifiers());
        session.press("ALT", 1800); session.type('x', Direction.CENTER); eq(new Event("text", "x", KeyboardSession.ALT), sink.last()); eq(0, session.modifiers());
        session.press("CTRL", 1900); session.press("ALT", 1901); session.press("TOP_SHIFT", 2000);
        eq(7, session.modifiers()); session.type('q', Direction.CENTER); eq(new Event("text", "Q", 7), sink.last()); eq(0, session.modifiers());
        session.press("CTRL", 2100); session.press("CTRL", 2101); eq(0, session.modifiers());
        session.press("ALT", 2200); session.press("ALT", 2201); eq(0, session.modifiers());
        session.press("SHIFT", 3000); session.press("TOP_SHIFT", 3500); eq(0, session.modifiers());
        session.press("TOP_SHIFT", 4000); session.press("SHIFT", 4100); eq(ShiftState.Mode.LOCKED, session.shiftMode());
        eq(KeyboardSession.CAPS_LOCK, session.modifiers());
        session.type('a', Direction.CENTER); eq(new Event("text", "A", KeyboardSession.CAPS_LOCK), sink.last()); eq(KeyboardSession.CAPS_LOCK, session.modifiers());
        session.press("RIGHT", 4200); eq(new Event("arrow", "RIGHT", KeyboardSession.CAPS_LOCK), sink.last());
        session.press("SHIFT", 4500); eq(0, session.modifiers());
        session.press("TOP_SHIFT", 5000); session.type('g', Direction.RIGHT); eq(new Event("text", "\"", KeyboardSession.SHIFT), sink.last()); eq(0, session.modifiers());
        session.press("CTRL", 5100); int before = sink.events.size(); eq("", session.type('n', Direction.LEFT));
        eq(before, sink.events.size()); eq(KeyboardSession.CTRL, session.modifiers());
        eq(false, session.press("UNKNOWN", 5200)); eq(KeyboardSession.CTRL, session.modifiers());
        session.type('c', Direction.CENTER); eq(new Event("text", "c", KeyboardSession.CTRL), sink.last()); eq(0, session.modifiers());
        for (String action : new String[]{"TAB", "ENTER", "SPACE"}) {
            session.press("CTRL", 6000); session.press("ALT", 6001); session.press("TOP_SHIFT", 6100);
            session.press(action, 6200); eq(7, sink.last().modifiers()); eq(0, session.modifiers());
            eq(action.equals("TAB") ? "tab" : action.equals("ENTER") ? "enter" : "text", sink.last().kind());
        }
        for (String action : new String[]{"LEFT", "DOWN", "UP", "RIGHT"}) {
            eq(true, session.press(action, 7000)); eq(new Event("arrow", action, 0), sink.last());
        }
        session.press("TAB", 7100); eq(new Event("tab", "\t", 0), sink.last());
        KeyboardLayout edited = KeyboardLayout.defaults(); edited.set('a', Direction.LEFT, "中"); edited.set('b', Direction.DOWN, "😀"); session.setLayout(edited);
        session.press("ALT", 7200); session.type('a', Direction.LEFT); eq(new Event("text", "中", KeyboardSession.ALT), sink.last()); eq(0, session.modifiers());
        session.type('b', Direction.DOWN); eq(new Event("text", "😀", 0), sink.last());
        eq("Ctrl + Alt + Shift", KeyboardSession.modifierLabel(7)); eq("Caps", KeyboardSession.modifierLabel(KeyboardSession.CAPS_LOCK)); eq("", KeyboardSession.modifierLabel(0));
        KeyboardSession disconnected = new KeyboardSession(KeyboardLayout.defaults()); disconnected.press("CTRL", 8000); disconnected.type('a', Direction.CENTER); eq(0, disconnected.modifiers());
        System.out.println("PASS: " + checks + " control-row assertions covering sticky modifiers, combinations, arrows, Tab, Caps Lock, and custom symbols.");
    }
}
