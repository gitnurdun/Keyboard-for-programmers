package com.nurdun.swipecode;

import com.nurdun.swipecode.core.KeyboardSession;

/** Implement with InputConnection when the keyboard is hosted by an IME. */
public interface KeyboardOutput extends KeyboardSession.Sink {
    default void insertText(String text) { insertText(text, 0); }
    default void backspace() { backspace(0); }
    default void enter() { enter(0); }
}
