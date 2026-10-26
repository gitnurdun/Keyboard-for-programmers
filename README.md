# SwipeCode · 滑码键盘

**A customizable Android keyboard for programming: tap to type, swipe for symbols and shortcuts.**

[中文说明](README.zh-CN.md) · [Install](#install) · [Default layout](#default-layout) · [Build](#build-from-source) · [Verification](VERIFICATION.md)

SwipeCode keeps the familiar QWERTY layout and gives every key five configurable actions: **tap, up, down, left, and right**. Put brackets under your thumbs, turn Alt into a directional pad, or bind an entire key combination to one swipe.

It works as a system input method alongside your existing keyboard. The app also includes a trial editor, a layout editor, and settings for key height, swipe sensitivity, and haptics. It is an independent Java/native View project and does not depend on Termux source code.

> **Version 0.5.0 · Early preview.** The APK builds offline and 1,751 core assertions pass. Android instrumentation tests compile, but the current IME has not yet been verified on a connected phone. Portrait use is the primary validation target.

## Features

- **Five actions per key:** customize all 37 keys, including the top controls, Space, Enter, and Backspace.
- **Characters, text, and shortcuts:** bind symbols, Unicode text snippets of up to 64 code points, physical keys, or Ctrl/Alt/Shift/Meta combinations.
- **Sticky modifiers:** tap Shift, Ctrl, or Alt, then enter the next character. Double-tap Shift for Caps Lock.
- **Two-thumb gestures:** each touch stays assigned to its starting key; move back to the center to restore the tap action. A raised preview shows the selected action.
- **Editor and terminal support:** Unicode-aware deletion, selection replacement, Search/Send actions, and native key events for terminal shortcuts.
- **Easy switching:** **🌐 输入法** opens Android's keyboard picker. Your usual keyboard can remain enabled.
- **Local configuration:** saved layouts and settings, colors that follow the system theme, no Internet permission, and no runtime libraries.

## Install

1. Download [SwipeCode-0.5.0.apk](downloads/SwipeCode-0.5.0.apk). On GitHub's file page, click **Download raw file**.
2. Transfer it to your Android phone and install it. The app is named **滑码键盘**. This APK is debug-signed for testing.
3. Open **启用** (Enable) and tap the first button to open Android's input-method settings. Enable **滑码键盘 · SwipeCode**.
4. Return to the app, tap **选择输入法** (Choose input method), and select SwipeCode.
5. Try the input field on that page or open a text field in another app. Use **🌐 输入法** to return to your normal keyboard, Chinese input, or another language.

Requires Android 8.0 / API 26 or later. The APK has no native CPU-specific libraries. Existing installations signed with the same key can be upgraded without uninstalling or losing saved settings.

**APK SHA-256:**

```text
ff9242f5252e71bd5f097e4bed74ee37a2113c57313c0165873f7818ea3ddbcd
```

## Default layout

The top row groups navigation and shortcuts together, leaving ordinary letter keys for letters and symbol characters.

| Key | Tap | Swipe up | Swipe down | Swipe left | Swipe right |
| --- | --- | --- | --- | --- | --- |
| Esc | Escape | Page Up | Page Down | — | — |
| Shift | Latch Shift | Tab | — | — | — |
| Ctrl | Latch Ctrl | Ctrl+A | Ctrl+L | Ctrl+P | Ctrl+C |
| Alt | Latch Alt | ↑ | ↓ | ← | → |
| Tab | Tab | — | — | — | — |
| Del | Forward Delete | — | — | — | — |

Q–P swipe upward to type 1–0. C, V, B, N, and M have no default swipe bindings. Other letter presets are:

| Letter | Swipe left | Swipe right | Swipe up | Swipe down |
| --- | --- | --- | --- | --- |
| A | `(` | `)` | — | — |
| S | `[` | `]` | — | — |
| D | `{` | `}` | — | — |
| F | `<` | `>` | — | — |
| G | Single quote | Double quote | Backtick | — |
| H | `,` | `.` | `?` | `!` |
| J | `+` | `-` | `*` | `/` |
| K | `=` | `%` | `~` | `^` |
| L | `&` | Vertical bar | `$` | `#` |
| Z | `:` | `;` | `_` | `@` |
| X | Backslash | — | — | — |

The bottom controls provide Shift, Backspace, customization, Space, and Enter. Their tap and swipe actions are editable too.

### Gesture behavior

- Tap and release for the center action. Swipe at least **12 dp** by default to choose a direction; return within **8 dp** to restore the center.
- Only the selected action runs on release. Swiping Ctrl does **not** also latch Ctrl.
- An empty direction, canceled touch, or release outside the keyboard produces no final action.
- Hold an arrow, Page Up/Down, Backspace, or Del action for **400 ms** to start repeating; release to stop.
- Shift/Ctrl/Alt can be stacked. A character, snippet, Space, Tab, Enter, or ordinary shortcut consumes one-shot modifiers. Arrows and Backspace preserve them; Caps Lock remains on until tapped off.
- Keycap abbreviations use `^` for Ctrl, `⌥` for Alt, `⇧` for Shift, and `◆` for Meta. The candidate preview shows the full binding.

## Customize any key

Open **自定义** (Customize), then tap a key in the preview. Edit its tap binding and its four swipe directions:

| Binding type | Examples |
| --- | --- |
| Text | `(`, a quote, `中`, an emoji, or a short snippet |
| Key or combination | Esc, Tab, arrows, Home/End, Page Up/Down, F1–F12, Ctrl+L, Ctrl+Shift+Z |
| Keyboard function | Latch Shift/Ctrl/Alt, or open customization |

Use your currently selected system keyboard to enter text. The type button opens the key/combination/function picker. You can clear a binding or fill four directions with arrows in one step while keeping the tap action.

The inner dialog applies changes to a draft; **保存** (Save) in the main key dialog commits them. **取消** (Cancel) discards them. Restoring a key resets all five bindings; restoring the whole layout asks for confirmation.

Key height defaults to **56 dp**, swipe distance to **12 dp**, and haptics to on. Layouts and settings are saved locally. Format-4 migration removes the old built-in letter-key shortcut duplicates while retaining other custom bindings. Shortcuts deliberately added again in the new format remain saved.

## Terminal mode and ordinary apps

The keyboard toolbar provides **终端** (Terminal), **定制** (Customize), **🌐 输入法** (Keyboard picker), and **收起** (Hide).

Terminal mode is selected automatically for Termux and raw key-input fields and can be toggled manually for the current input session. Ctrl+C, Ctrl+L, Tab, arrows, and other combinations then reach the application as native key events. Their effect depends on the receiving app: Ctrl+C may interrupt a process rather than copy text.

In ordinary text fields, common Ctrl shortcuts use editor operations: Select All, Copy, Paste, Cut, Undo, and Redo. Enter follows Search/Send/Done when appropriate; Shift+Enter preserves a newline. Unicode deletion and selection replacement use Android's input connection.

The **试打** (Trial) page remains a local text editor with a fixed SwipeCode keyboard. It does not emulate a terminal, so Ctrl+L does not clear that editor as a terminal screen.

## Privacy and permissions

- No `INTERNET` permission, network calls, analytics SDKs, or runtime dependencies.
- Other apps' typed text is not saved or logged. Layouts and settings stay in private local storage.
- Modifiers, touch sessions, candidate feedback, and repeat timers reset when input ends or the keyboard hides.
- Android's `BIND_INPUT_METHOD` protects the IME service; it is a restriction on who can bind, not a runtime permission requested from the user.
- Crash reports remain local and can be copied manually from the diagnostic screen. Nothing is uploaded automatically.

## Build from source

The verified toolchain is:

| Component | Version |
| --- | --- |
| JDK | 21; sources target Java 17 |
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.7.3 |
| Android platform / compile SDK | 35 |
| Android Build Tools | 35.0.0 |
| Minimum / target Android API | 26 / 35 |

Use Linux or WSL with these tools installed and Gradle dependencies already cached. There is no bundled Gradle wrapper. The scripts **do not download** SDK components, Gradle, or dependencies: they use `--offline` and stop when prerequisites are missing.

```bash
git clone https://github.com/gitnurdun/Keyboard-for-programmers.git
cd Keyboard-for-programmers

# Use your existing SDK and put the installed JDK and Gradle on PATH.
export ANDROID_HOME=/path/to/your/android-sdk

bash tools/build-offline.sh
```

Alternatively, set `SWIPECODE_ENV=/path/to/your/existing/env.sh` to source an environment file. If present, `$HOME/android/env.sh` is used by default. The script selects AAPT2 from the installed SDK rather than downloading a Maven binary.

Build outputs:

- App: `app/build/outputs/apk/debug/app-debug.apk`
- Device tests: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- Application ID: `com.nurdun.swipecode`

A different debug signing key cannot upgrade the bundled APK in place. Keep the original signing key for compatible upgrades. Signing keys and local SDK paths are excluded from Git.

## Tests and current status

Run the production core tests using an installed JDK:

```bash
bash tools/test-core.sh
```

**1,751 assertions passed** across layouts, Unicode, migration, gestures, multitouch, key combinations, modifiers, text edits, editor/terminal routing, and lifecycle resets. No downloaded test framework is required.

Android instrumentation uses the built-in framework and covers native views, gestures, customization, and the input-connection adapter. After installing both APKs on your connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.nurdun.swipecode.test/com.nurdun.swipecode.KeyboardInstrumentation
```

The runner temporarily uses preset configuration and restores saved preferences afterward. It does not enable or select the IME automatically. **The device tests have compiled but have not run on a connected phone for v0.5.0.** Keyboard switching, real-app input, Termux behavior, and navigation-area gestures still need phone validation. See [VERIFICATION.md](VERIFICATION.md).

## Project structure

```text
app/src/main/
  java/com/nurdun/swipecode/
    core/                       Layout, bindings, gestures, session state, IME policy
    SwipeKeyboardView.java      Shared rendering and touch handling
    SwipeCodeImeService.java    System-keyboard host and switching toolbar
    InputConnectionOutput.java Cross-app editing and native key events
    EditTextOutput.java         Trial editor adapter
    MainActivity.java           Trial, configuration, settings, activation
    KeyboardStore.java          Private local persistence
  res/xml/input_method.xml      IME declaration
app/src/androidTest/            Framework-only device tests
tests/                         Plain-Java production-core tests
tools/                         Offline build and test scripts
downloads/                     Prebuilt debug APK and checksum
```

The initial UI is Chinese, with an English/ASCII programming layout. Automatic correction, word prediction, and Chinese composition are not implemented; switch to your usual input method when needed. See [CHANGELOG.md](CHANGELOG.md) for version history and startup-fix notes.
