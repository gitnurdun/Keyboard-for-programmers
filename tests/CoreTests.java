import com.nurdun.swipecode.core.*;
import java.util.HashSet;
import java.util.Objects;

/** No downloaded test framework: exercise the exact production core with the installed JDK. */
public final class CoreTests {
    private static int checks;
    private static void eq(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected [" + expected + "], got [" + actual + "]");
    }
    private static void yes(boolean value) { eq(true, value); }
    public static void main(String[] args) {
        defaults(); validation(); persistence(); gestures(); shift(); editing(); crashReport();
        System.out.println("PASS: " + checks + " assertions across layout, Unicode validation, persistence, gestures, multi-touch, shift, and editing.");
    }
    private static void defaults() {
        KeyboardLayout layout = KeyboardLayout.defaults();
        HashSet<Character> letters = new HashSet<>();
        for (char c : KeyboardLayout.LETTERS.toCharArray()) {
            letters.add(c); eq(String.valueOf(c), layout.get(c, Direction.CENTER));
            for (Direction d : Direction.values()) if (d != Direction.CENTER) yes(KeyboardLayout.validSymbol(layout.get(c, d)));
        }
        eq(26, letters.size());
        String digits = "1234567890";
        for (int i = 0; i < 10; i++) eq(digits.substring(i, i + 1), layout.get(KeyboardLayout.ROWS[0].charAt(i), Direction.UP));
        char[] pairKeys = {'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'z'};
        String[] left = {"(", "[", "{", "<", "'", ",", "+", "=", "&", ":"};
        String[] right = {")", "]", "}", ">", "\"", ".", "-", "%", "|", ";"};
        for (int i = 0; i < pairKeys.length; i++) { eq(left[i], layout.get(pairKeys[i], Direction.LEFT)); eq(right[i], layout.get(pairKeys[i], Direction.RIGHT)); }
        eq("\\", layout.get('x', Direction.LEFT));
        eq("`", layout.get('g', Direction.UP)); eq("?", layout.get('h', Direction.UP)); eq("!", layout.get('h', Direction.DOWN));
        for (char c : "cvbnm".toCharArray()) for (Direction d : Direction.values()) if (d != Direction.CENTER) eq("", layout.get(c, d));
        KeyboardLayout copy = layout.copy(); copy.set('a', Direction.LEFT, "中"); eq("(", layout.get('a', Direction.LEFT));
        copy.restoreKey('a'); eq("(", copy.get('a', Direction.LEFT));
    }
    private static void validation() {
        for (String s : new String[]{"", "'", "\"", "\\", "中", "“", "😀", " ", "$", "="}) yes(KeyboardLayout.validSymbol(s));
        for (String s : new String[]{"ab", "<=", "\n", "\t", "\r", "\uD800", "e\u0301", "👨‍💻"}) eq(false, KeyboardLayout.validSymbol(s));
        eq(false, KeyboardLayout.validSymbol(null));
        KeyboardLayout layout = KeyboardLayout.defaults();
        try { layout.set('a', Direction.LEFT, "ab"); throw new AssertionError("Invalid symbol accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { layout.set('a', Direction.CENTER, "!"); throw new AssertionError("Center overwritten"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void persistence() {
        KeyboardLayout original = KeyboardLayout.defaults();
        original.set('a', Direction.LEFT, ""); original.set('n', Direction.DOWN, "😀");
        original.set('b', Direction.UP, "中"); original.set('c', Direction.RIGHT, "\\");
        KeyboardLayout restored = LayoutCodec.decode(LayoutCodec.encode(original));
        for (char c : KeyboardLayout.LETTERS.toCharArray()) for (Direction d : Direction.values()) eq(original.get(c, d), restored.get(c, d));
        eq("(", LayoutCodec.decode(null).get('a', Direction.LEFT));
        eq("(", LayoutCodec.decode("garbage").get('a', Direction.LEFT));
        eq("(", LayoutCodec.decode("version=5\na.LEFT=!\n").get('a', Direction.LEFT));
        eq("(", LayoutCodec.decode("version=1\na.LEFT=two\n").get('a', Direction.LEFT));
        eq("", LayoutCodec.decode("version=1\na.LEFT=\n").get('a', Direction.LEFT));
        eq("(", LayoutCodec.decode("version=1\na.LEFT=\\uNOTHEX\n").get('a', Direction.LEFT));
    }
    private static void gestures() {
        GestureTracker tracker = new GestureTracker(12, 8);
        tracker.begin(0, "a", 100, 100);
        eq(Direction.CENTER, tracker.move(0, 111, 101).direction);
        eq(Direction.RIGHT, tracker.move(0, 112, 100).direction);
        eq(Direction.RIGHT, tracker.move(0, 110, 100).direction);
        eq(Direction.CENTER, tracker.move(0, 108, 100).direction);
        eq(Direction.LEFT, tracker.move(0, 80, 102).direction);
        eq(Direction.UP, tracker.move(0, 99, 75).direction);
        eq(Direction.DOWN, tracker.move(0, 101, 125).direction);
        eq(Direction.DOWN, tracker.move(0, 120, 120).direction);
        eq("a", tracker.move(0, 250, 100).key);
        eq(Direction.RIGHT, tracker.release(0, 250, 100, true).direction); eq(0, tracker.size());
        eq(null, tracker.release(0, 100, 100, true));
        tracker.begin(4, "q", 100, 100); eq(null, tracker.release(4, -20, -10, false)); eq(0, tracker.size());
        tracker.begin(0, "a", 100, 100); tracker.begin(7, "s", 200, 100);
        tracker.move(0, 80, 100); tracker.move(7, 220, 100); eq(2, tracker.size());
        eq("s", tracker.last().key); eq("a", tracker.get(0).key);
        eq(Direction.LEFT, tracker.release(0, 80, 100, true).direction); eq(1, tracker.size());
        eq(Direction.RIGHT, tracker.release(7, 220, 100, true).direction); eq(0, tracker.size());
        tracker.begin(0, "g", 100, 100); tracker.begin(1, "h", 200, 100); tracker.cancelAll(); eq(0, tracker.size());
        eq(null, tracker.release(0, 100, 100, true));
        eq(null, tracker.move(50, 1, 1));
        KeyboardLayout layout = KeyboardLayout.defaults();
        for (Direction direction : new Direction[]{Direction.LEFT, Direction.RIGHT, Direction.UP, Direction.DOWN}) {
            tracker.begin(0, "h", 100, 100);
            float x = direction == Direction.LEFT ? 80 : direction == Direction.RIGHT ? 120 : 100;
            float y = direction == Direction.UP ? 80 : direction == Direction.DOWN ? 120 : 100;
            eq(direction, tracker.release(0, x, y, true).direction);
            yes(!layout.get('h', direction).isEmpty());
        }
        tracker.begin(0, "c", 100, 100); GestureTracker.Stroke blank = tracker.release(0, 100, 120, true);
        eq("", layout.get(blank.key.charAt(0), blank.direction));
        tracker.configure(24, 16); tracker.begin(0, "a", 0, 0);
        eq(Direction.CENTER, tracker.move(0, 23, 0).direction); eq(Direction.RIGHT, tracker.move(0, 24, 0).direction);
        eq(Direction.CENTER, tracker.move(0, 16, 0).direction);
        try { new GestureTracker(8, 8); throw new AssertionError("Invalid thresholds"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void shift() {
        ShiftState state = new ShiftState(); eq("a", state.letter('a'));
        state.tap(1000); eq(ShiftState.Mode.ONCE, state.mode()); eq("A", state.letter('a'));
        state.consumeLetter(); eq("a", state.letter('a'));
        state.tap(2000); state.tap(2200); eq(ShiftState.Mode.LOCKED, state.mode());
        state.consumeLetter(); eq("Q", state.letter('q')); state.tap(2600); eq(ShiftState.Mode.OFF, state.mode());
        state.tap(3000); state.tap(3500); eq(ShiftState.Mode.OFF, state.mode());
        state.tap(4000); state.consumeLetter(); state.tap(4100); eq(ShiftState.Mode.ONCE, state.mode());
    }
    private static void editing() {
        eq("aXbc", TextEdits.insert("abc", 1, 1, "X").apply("abc"));
        eq("aZc", TextEdits.insert("abc", 1, 2, "Z").apply("abc"));
        eq("aZc", TextEdits.insert("abc", 2, 1, "Z").apply("abc"));
        eq("abc!", TextEdits.insert("abc", -1, -1, "!").apply("abc"));
        eq("!", TextEdits.insert("abc", -1, 0, "!").apply("abc"));
        eq("ab", TextEdits.backspace("abc", 3, 3).apply("abc"));
        eq("ac", TextEdits.backspace("abc", 2, 2).apply("abc"));
        eq("ac", TextEdits.backspace("abc", 1, 2).apply("abc"));
        eq("abc", TextEdits.backspace("abc", 0, 0).apply("abc"));
        eq("", TextEdits.backspace("", 0, 0).apply(""));
        eq("a", TextEdits.backspace("a😀", 3, 3).apply("a😀"));
        eq("ab", TextEdits.backspace("a😀b", 3, 3).apply("a😀b"));
        eq("aXb", TextEdits.insert("a😀b", 2, 2, "X").apply("a😀b"));
        String text = "";
        for (String value : new String[]{"中", "'", "\"", "\\", "\n", "😀"}) {
            TextEdits.Edit edit = TextEdits.insert(text, text.length(), text.length(), value); text = edit.apply(text); eq(text.length(), edit.cursor());
        }
        eq("中'\"\\\n😀", text);
        while (!text.isEmpty()) text = TextEdits.backspace(text, text.length(), text.length()).apply(text);
        eq("", text);
    }
    private static void crashReport() {
        Throwable failure = new IllegalStateException("startup", new NullPointerException("inner cause"));
        String report = CrashReportFormat.format("0.1.1", "HONOR test", "main", failure);
        yes(report.contains("SwipeCode 0.1.1")); yes(report.contains("Device: HONOR test"));
        yes(report.contains("Thread: main")); yes(report.contains("IllegalStateException: startup"));
        yes(report.contains("Caused by: java.lang.NullPointerException: inner cause"));
        yes(CrashReportFormat.matchesVersion(report, "0.1.1"));
        eq(false, CrashReportFormat.matchesVersion(report, "0.1.2"));
        eq(false, CrashReportFormat.matchesVersion(report, "0.1"));
        eq(false, CrashReportFormat.matchesVersion(null, "0.1.1"));
        eq(false, CrashReportFormat.matchesVersion(report, null));
        eq(false, CrashReportFormat.matchesVersion("SwipeCode 0.1.10\nold error", "0.1.1"));
    }
}
