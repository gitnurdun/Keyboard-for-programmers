package com.nurdun.swipecode;

import android.content.Intent;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import com.nurdun.swipecode.core.ImePolicy;

/** System IME host; the same keyboard and local configuration are used by the trial app. */
public final class SwipeCodeImeService extends InputMethodService implements SwipeKeyboardView.Navigation {
    private KeyboardStore store;
    private SwipeKeyboardView keyboard;
    private InputConnectionOutput output;
    private CheckBox terminal;
    @Override public void onCreate() { super.onCreate(); store = new KeyboardStore(this); }
    @Override public View onCreateInputView() {
        Palette palette = new Palette(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(palette.keyboard);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, 0, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), 0, insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout toolbar = new LinearLayout(this); toolbar.setOrientation(LinearLayout.HORIZONTAL);
        Button switcher = button("🌐 输入法", palette); switcher.setContentDescription("切换到系统或其他输入法");
        switcher.setOnClickListener(view -> { if (keyboard != null) keyboard.resetInputState(); ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker(); });
        toolbar.addView(switcher, new LinearLayout.LayoutParams(0, dp(38), 1.3f));
        Button customize = button("定制", palette); customize.setOnClickListener(view -> openCustomization()); toolbar.addView(customize, new LinearLayout.LayoutParams(0, dp(38), .8f));
        terminal = new CheckBox(this); terminal.setText("终端"); terminal.setTextSize(12); terminal.setTextColor(palette.ink); terminal.setFocusable(false);
        terminal.setContentDescription("终端模式：快捷键直接发送到应用");
        terminal.setOnCheckedChangeListener((button, checked) -> { if (output != null) output.setTerminal(checked); updateEnterLabel(getCurrentInputEditorInfo(), checked); }); toolbar.addView(terminal, new LinearLayout.LayoutParams(0, dp(38), 1));
        Button hide = button("⌄ 收起", palette); hide.setOnClickListener(view -> requestHideSelf(0)); toolbar.addView(hide, new LinearLayout.LayoutParams(0, dp(38), 1)); root.addView(toolbar);
        AppSettings settings = imeSettings();
        keyboard = new SwipeKeyboardView(this, store.loadLayout(), settings, false);
        output = new InputConnectionOutput(this::getCurrentInputConnection, this::getCurrentInputEditorInfo);
        keyboard.setOutput(output); keyboard.setNavigation(this); root.addView(keyboard, new LinearLayout.LayoutParams(-1, -2));
        configureEditor(getCurrentInputEditorInfo()); return root;
    }
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting); if (keyboard != null) keyboard.resetInputState(); configureEditor(info);
    }
    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keyboard != null) { keyboard.resetInputState(); keyboard.update(store.loadLayout(), imeSettings()); }
        configureEditor(info);
    }
    private void configureEditor(EditorInfo info) {
        boolean useTerminal = info != null && ImePolicy.terminalEditor(info.inputType, info.packageName);
        if (terminal != null) terminal.setChecked(useTerminal);
        if (output != null) output.setTerminal(useTerminal);
        updateEnterLabel(info, useTerminal);
    }
    private void updateEnterLabel(EditorInfo info, boolean useTerminal) {
        if (keyboard == null) return;
        int action = info == null ? 0 : ImePolicy.enterAction(info.imeOptions, info.actionId, info.actionLabel != null, 0, useTerminal);
        String label = action == 0 ? null : info.actionLabel != null && info.actionId != 0 ? info.actionLabel.toString()
                : switch (action) { case EditorInfo.IME_ACTION_GO -> "前往"; case EditorInfo.IME_ACTION_SEARCH -> "搜索"; case EditorInfo.IME_ACTION_SEND -> "发送";
                    case EditorInfo.IME_ACTION_NEXT -> "下一项"; case EditorInfo.IME_ACTION_DONE -> "完成"; case EditorInfo.IME_ACTION_PREVIOUS -> "上一项"; default -> null; };
        keyboard.setEnterLabel(label);
    }
    private AppSettings imeSettings() {
        AppSettings settings = store.loadSettings();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) settings.keyHeightDp = Math.min(settings.keyHeightDp, 30);
        return settings;
    }
    @Override public void onFinishInputView(boolean finishingInput) { if (keyboard != null) keyboard.resetInputState(); super.onFinishInputView(finishingInput); }
    @Override public void onFinishInput() { if (keyboard != null) keyboard.resetInputState(); super.onFinishInput(); }
    @Override public void onWindowHidden() { if (keyboard != null) keyboard.resetInputState(); super.onWindowHidden(); }
    @Override public void onDestroy() { if (keyboard != null) keyboard.resetInputState(); super.onDestroy(); }
    @Override public void editKey(String key) { openCustomization(); }
    @Override public void openCustomization() {
        if (keyboard != null) keyboard.resetInputState(); requestHideSelf(0);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("page", 1));
    }
    private Button button(String text, Palette palette) {
        Button button = new Button(this); button.setText(text); button.setAllCaps(false); button.setTextSize(12); button.setTextColor(palette.accent);
        button.setPadding(0, 0, 0, 0); button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0); button.setFocusable(false);
        button.setBackgroundResource(android.R.drawable.list_selector_background); return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
