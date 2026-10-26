package com.nurdun.swipecode.core;

public final class TextEdits {
    public static final class Edit {
        public final int start, end;
        public final String replacement;
        public Edit(int start, int end, String replacement) { this.start = start; this.end = end; this.replacement = replacement; }
        public int cursor() { return start + replacement.length(); }
        public String apply(String text) { return text.substring(0, start) + replacement + text.substring(end); }
    }
    private TextEdits() {}
    public static Edit insert(CharSequence text, int selectionStart, int selectionEnd, String value) {
        int first = clamp(selectionStart, text.length()), last = clamp(selectionEnd, text.length());
        int start = Math.min(first, last), end = Math.max(first, last);
        // A malformed external selection must not cut a UTF-16 surrogate pair in half.
        if (betweenSurrogates(text, start)) start--;
        if (betweenSurrogates(text, end)) end++;
        return new Edit(start, end, value);
    }
    public static Edit backspace(CharSequence text, int selectionStart, int selectionEnd) {
        Edit selection = insert(text, selectionStart, selectionEnd, "");
        if (selection.start != selection.end || selection.start == 0) return selection;
        int start = selection.start - 1;
        if (start > 0 && Character.isLowSurrogate(text.charAt(start)) && Character.isHighSurrogate(text.charAt(start - 1))) start--;
        return new Edit(start, selection.end, "");
    }
    private static boolean betweenSurrogates(CharSequence text, int at) {
        return at > 0 && at < text.length() && Character.isHighSurrogate(text.charAt(at - 1)) && Character.isLowSurrogate(text.charAt(at));
    }
    private static int clamp(int value, int length) { return value < 0 ? length : Math.min(value, length); }
}
