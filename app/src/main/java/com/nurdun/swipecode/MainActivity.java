package com.nurdun.swipecode;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.content.ComponentName;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.nurdun.swipecode.core.Direction;
import com.nurdun.swipecode.core.KeyboardLayout;
import com.nurdun.swipecode.core.KeyBinding;
import java.util.EnumMap;

public final class MainActivity extends Activity implements SwipeKeyboardView.Navigation {
    private Palette palette;
    private KeyboardStore store;
    private KeyboardLayout layout;
    private AppSettings settings;
    private LinearLayout root, tabs;
    private FrameLayout content;
    private EditText editor;
    private SwipeKeyboardView keyboard;
    private String draft = "";
    private int selectionStart, selectionEnd, page;
    private AlertDialog keyDialog;
    private TextView imeStatus;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { initialize(state); }
        catch (RuntimeException | LinkageError failure) {
            CrashLog.save(this, Thread.currentThread().getName(), failure);
            startActivity(new Intent(this, StartupActivity.class)); finish();
        }
    }

    private void initialize(Bundle state) {
        palette = new Palette(this);
        store = new KeyboardStore(this); layout = store.loadLayout(); settings = store.loadSettings();
        if (state != null) {
            draft = state.getString("draft", ""); selectionStart = state.getInt("selection_start");
            selectionEnd = state.getInt("selection_end"); page = state.getInt("page");
        } else page = getIntent().getIntExtra("page", imeEnabled() ? 0 : 3);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT < 30 && !palette.dark) getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root = column(); root.setBackgroundColor(palette.background);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom));
                // PhoneWindow's controller getter can throw before DecorView exists on Android 16.
                // Resolve through the attached content view only, after the window has been created.
                if (view.isAttachedToWindow()) {
                    android.view.WindowInsetsController controller = view.getWindowInsetsController();
                    if (controller != null) {
                        int flags = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                        controller.setSystemBarsAppearance(palette.dark ? 0 : flags, flags);
                    }
                }
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        buildHeader();
        content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); root.requestApplyInsets(); showPage(page);
    }

    private void buildHeader() {
        LinearLayout header = column(); header.setPadding(dp(20), dp(16), dp(20), dp(12));
        LinearLayout titleRow = row(); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("滑码", 28, palette.ink, true); title.setLetterSpacing(.08f);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = label("SWIPE / CODE", 10, palette.accent, true);
        badge.setLetterSpacing(.12f); badge.setPadding(dp(11), dp(7), dp(11), dp(7)); badge.setBackground(shape(palette.accentSoft, 18, false));
        titleRow.addView(badge); header.addView(titleRow);
        TextView tagline = label("你的符号，顺手就到。", 13, palette.muted, false); tagline.setPadding(0, dp(5), 0, dp(15)); header.addView(tagline);
        tabs = row(); tabs.setPadding(dp(4), dp(4), dp(4), dp(4)); tabs.setBackground(shape(palette.keyboard, 14, false));
        String[] names = {"试打", "自定义", "手感", "启用"};
        for (int i = 0; i < names.length; i++) {
            final int selected = i;
            TextView tab = label(names[i], 14, palette.muted, true); tab.setGravity(Gravity.CENTER); tab.setContentDescription(names[i] + "页面");
            tab.setOnClickListener(view -> showPage(selected)); tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(38), 1));
        }
        header.addView(tabs); root.addView(header);
    }

    private void captureDraft() {
        if (editor != null) { draft = editor.getText().toString(); selectionStart = Math.max(0, editor.getSelectionStart()); selectionEnd = Math.max(0, editor.getSelectionEnd()); }
    }
    private void showPage(int selected) {
        captureDraft(); editor = null; keyboard = null; imeStatus = null; page = selected;
        content.removeAllViews();
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView tab = (TextView) tabs.getChildAt(i);
            tab.setTextColor(i == page ? palette.accent : palette.muted);
            tab.setBackground(i == page ? shape(palette.surface, 11, false) : null);
            tab.setSelected(i == page);
        }
        if (selected == 0) {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(), 0); buildPlay();
        } else if (selected == 1) buildCustomization(); else if (selected == 2) buildSettings(); else buildEnable();
    }

    private boolean imeEnabled() {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        for (android.view.inputmethod.InputMethodInfo info : manager.getEnabledInputMethodList())
            if (info.getPackageName().equals(getPackageName()) && info.getServiceName().equals(SwipeCodeImeService.class.getName())) return true;
        return false;
    }
    private String imeState() {
        String selected = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        ComponentName current = selected == null ? null : ComponentName.unflattenFromString(selected);
        if (new ComponentName(this, SwipeCodeImeService.class).equals(current)) return "滑码键盘已选中，可以在其他应用输入。";
        return imeEnabled() ? "滑码键盘已启用，点击下面的“选择输入法”即可使用。" : "尚未启用。原有输入法可保持启用，随时切换。";
    }
    private void buildEnable() {
        LinearLayout body = pageColumn(); body.addView(sectionTitle("把滑码用到其他应用"));
        imeStatus = description(imeState()); imeStatus.setPadding(0, 0, 0, dp(12)); body.addView(imeStatus);
        Button enable = quietButton("1 · 打开输入法设置，启用滑码键盘");
        enable.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))); body.addView(enable, new LinearLayout.LayoutParams(-1, dp(54)));
        Button choose = quietButton("2 · 选择输入法：滑码键盘");
        choose.setOnClickListener(view -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker()); body.addView(choose, new LinearLayout.LayoutParams(-1, dp(54)));
        body.addView(description("无需关闭或卸载原有输入法。键盘上的 🌐 输入法 可随时切回；系统的输入法切换按钮也可使用。"));
        EditText test = new EditText(this); test.setId(R.id.ime_test_editor); test.setTextColor(palette.ink); test.setHintTextColor(palette.muted); test.setTextSize(17);
        test.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        test.setShowSoftInputOnFocus(true); test.setHint("点击这里，试用当前系统输入法…"); test.setMinLines(3); test.setGravity(Gravity.TOP);
        test.setContentDescription("系统输入法测试编辑区"); body.addView(test, new LinearLayout.LayoutParams(-1, dp(130)));
        TextView note = description("终端模式自动识别Termux和原始按键输入框，也可在键盘工具栏手动切换。终端模式下Ctrl+C发送中断；普通编辑框中Ctrl+C复制。配置仅保存在本机，不申请联网权限。");
        note.setPadding(0, dp(12), 0, dp(16)); body.addView(note); addScroll(body);
    }
    @Override protected void onResume() {
        super.onResume();
        if (store != null) { layout = store.loadLayout(); settings = store.loadSettings(); if (keyboard != null) keyboard.update(layout, settings); }
        if (imeStatus != null) imeStatus.setText(imeState());
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); if (content != null) showPage(intent.getIntExtra("page", page));
    }

    private void buildPlay() {
        LinearLayout body = column(); body.setPadding(dp(12), 0, dp(12), dp(8));
        LinearLayout card = column(); card.setBackground(shape(palette.surface, 18, true)); card.setPadding(dp(16), dp(8), dp(16), dp(12));
        LinearLayout toolbar = row(); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label("试打区", 12, palette.muted, true);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView count = label("0 字符", 11, palette.muted, false); toolbar.addView(count);
        Button copy = quietButton("复制"); copy.setOnClickListener(view -> {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("滑码试打", editor.getText())); toast("已复制");
        }); toolbar.addView(copy, new LinearLayout.LayoutParams(dp(54), dp(42)));
        Button clear = quietButton("清空"); clear.setOnClickListener(view -> editor.setText("")); toolbar.addView(clear, new LinearLayout.LayoutParams(dp(54), dp(42)));
        card.addView(toolbar);
        editor = new EditText(this); editor.setId(R.id.trial_editor);
        editor.setTextColor(palette.ink); editor.setHintTextColor(palette.muted); editor.setTextSize(17);
        editor.setTypeface(Typeface.MONOSPACE); editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setShowSoftInputOnFocus(false); editor.setBackgroundColor(Color.TRANSPARENT);
        editor.setPadding(0, dp(8), 0, dp(8)); editor.setLineSpacing(dp(5), 1f);
        editor.setHint("从这里开始试打…\n\n上滑 Q → 1\n左滑 A → (    右滑 A → )");
        editor.setContentDescription("试打编辑区，可选择文字和移动光标");
        editor.setText(draft); editor.setSelection(Math.min(selectionStart, draft.length()), Math.min(selectionEnd, draft.length()));
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int after) { count.setText(s.toString().codePointCount(0, s.length()) + " 字符"); }
            @Override public void afterTextChanged(Editable s) {}
        };
        editor.addTextChangedListener(watcher); watcher.onTextChanged(editor.getText(), 0, 0, 0);
        card.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(card, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView hint = label("Shift / Ctrl / Alt 点亮后保持，输入字符自动释放", 11, palette.muted, false);
        hint.setGravity(Gravity.CENTER); hint.setPadding(0, dp(9), 0, dp(9)); body.addView(hint);
        keyboard = new SwipeKeyboardView(this, layout, settings, false); keyboard.setId(R.id.trial_keyboard);
        keyboard.setOutput(new EditTextOutput(editor)); keyboard.setNavigation(this);
        body.addView(keyboard, new LinearLayout.LayoutParams(-1, -2)); content.addView(body, new FrameLayout.LayoutParams(-1, -1));
        editor.requestFocus();
    }

    private void buildCustomization() {
        LinearLayout body = pageColumn();
        body.addView(sectionTitle("每个按键，五种用途"));
        body.addView(description("点选任意键，包括顶部与底部功能键，编辑轻点及上下左右。可绑定文字、按键组合或Shift/Ctrl/Alt等保持功能。"));
        keyboard = new SwipeKeyboardView(this, layout, settings, true); keyboard.setId(R.id.layout_preview); keyboard.setNavigation(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2); previewParams.topMargin = dp(18); body.addView(keyboard, previewParams);
        LinearLayout note = column(); note.setPadding(dp(16), dp(14), dp(16), dp(14)); note.setBackground(shape(palette.surface, 16, true));
        note.addView(label("成对的符号，同一个键", 14, palette.ink, true));
        TextView pairs = label("A  (  )     S  [  ]     D  {  }     F  <  >", 13, palette.accent, false);
        pairs.setTypeface(Typeface.MONOSPACE); pairs.setPadding(0, dp(9), 0, 0); note.addView(pairs);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2); noteParams.topMargin = dp(16); body.addView(note, noteParams);
        Button restore = quietButton("恢复全部绑定预设"); restore.setId(R.id.restore_layout);
        restore.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("恢复全部绑定预设？")
                .setMessage("这会覆盖全部按键的轻点、符号、文字、组合键及功能绑定，手感设置保持不变。")
                .setNegativeButton("取消", null).setPositiveButton("恢复", (dialog, which) -> {
                    layout = KeyboardLayout.defaults(); store.saveLayout(layout); keyboard.update(layout, settings); toast("已恢复绑定预设");
                }).show());
        LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(-1, dp(52)); restoreParams.topMargin = dp(14); body.addView(restore, restoreParams);
        addScroll(body);
    }

    public void editKey(char letter) { editKey(String.valueOf(Character.toLowerCase(letter))); }
    @Override public void editKey(String key) {
        EnumMap<Direction, BindingSlotView> fields = new EnumMap<>(Direction.class);
        LinearLayout panel = column(); panel.setPadding(dp(24), dp(4), dp(24), dp(8));
        panel.addView(description("轻点和四个方向均可修改。可填文字、选择按键组合，或设置Shift/Ctrl/Alt保持功能。点击保存后生效。"));
        Button arrows = quietButton("将此键四个方向设为 ↑ ↓ ← →");
        arrows.setOnClickListener(view -> { for (Direction d : fields.keySet()) if (d != Direction.CENTER) fields.get(d).setBinding(KeyBinding.key(d.name(), 0)); }); panel.addView(arrows);
        BindingSlotView tap = new BindingSlotView(this, palette, "● 轻点", fieldId(Direction.CENTER), layout.binding(key, Direction.CENTER));
        panel.addView(tap, new LinearLayout.LayoutParams(-1, -2)); fields.put(Direction.CENTER, tap);
        Direction[][] rows = {{Direction.UP, Direction.DOWN}, {Direction.LEFT, Direction.RIGHT}};
        for (Direction[] directions : rows) {
            LinearLayout row = row();
            for (int i = 0; i < directions.length; i++) {
                Direction direction = directions[i];
                LinearLayout slot = column(); slot.setPadding(i == 0 ? 0 : dp(8), dp(14), i == 0 ? dp(8) : 0, 0);
                BindingSlotView input = new BindingSlotView(this, palette, arrow(direction) + " " + SwipeKeyboardView.directionName(direction), fieldId(direction), layout.binding(key, direction));
                slot.addView(input, new LinearLayout.LayoutParams(-1, -2)); fields.put(direction, input);
                row.addView(slot, new LinearLayout.LayoutParams(0, -2, 1));
            }
            panel.addView(row);
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("编辑 " + KeyboardLayout.keyName(key) + " 位置")
                .setView(scroll).setPositiveButton("保存", null).setNegativeButton("取消", null).setNeutralButton("恢复此键", null).create();
        keyDialog = dialog;
        dialog.setOnShowListener(unused -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                boolean valid = true;
                for (BindingSlotView field : fields.values()) if (!field.validateInput()) valid = false;
                if (!valid) return;
                for (Direction direction : fields.keySet()) layout.bind(key, direction, fields.get(direction).value());
                store.saveLayout(layout); if (keyboard != null) keyboard.update(layout, settings); dialog.dismiss(); toast("已保存 " + KeyboardLayout.keyName(key));
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                KeyboardLayout defaults = KeyboardLayout.defaults();
                for (Direction direction : fields.keySet()) fields.get(direction).setBinding(defaults.binding(key, direction));
                toast("已填入预设，点击保存后生效");
            });
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        });
        dialog.setOnDismissListener(unused -> keyDialog = null);
        dialog.show();
    }
    private int fieldId(Direction direction) {
        return switch (direction) { case UP -> R.id.symbol_up; case DOWN -> R.id.symbol_down; case LEFT -> R.id.symbol_left; case RIGHT -> R.id.symbol_right; case CENTER -> R.id.symbol_center; };
    }
    private static String arrow(Direction direction) {
        return switch (direction) { case UP -> "↑"; case DOWN -> "↓"; case LEFT -> "←"; case RIGHT -> "→"; default -> "·"; };
    }

    private void buildSettings() {
        LinearLayout body = pageColumn(); body.addView(sectionTitle("找到你的输入节奏"));
        body.addView(description("设置自动保存在本机。调整后回到试打页体验。"));
        addSlider(body, "键帽高度", "更高的键帽给四个方向留出更多空间。", settings.keyHeightDp, 44, 72, value -> {
            settings.keyHeightDp = value; store.saveSettings(settings);
        });
        addSlider(body, "滑动触发距离", "距离越小越灵敏；距离越大越不容易误触。", settings.triggerDp, 8, 24, value -> {
            settings.triggerDp = value; store.saveSettings(settings);
        });
        LinearLayout haptics = column(); haptics.setPadding(dp(16), dp(14), dp(16), dp(14)); haptics.setBackground(shape(palette.surface, 16, true));
        Switch toggle = new Switch(this); toggle.setText("触感反馈"); toggle.setTextSize(15); toggle.setTextColor(palette.ink); toggle.setChecked(settings.haptics);
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{palette.accent, palette.muted}));
        toggle.setOnCheckedChangeListener((button, checked) -> { settings.haptics = checked; store.saveSettings(settings); });
        haptics.addView(toggle, new LinearLayout.LayoutParams(-1, dp(44))); haptics.addView(description("遵循手机系统的触感设置。"));
        LinearLayout.LayoutParams hapticsParams = new LinearLayout.LayoutParams(-1, -2); hapticsParams.topMargin = dp(16); body.addView(haptics, hapticsParams);
        TextView local = description("滑码 0.5.0 · 数据仅保存在本机\n已提供系统输入法。打开“启用”页面完成设置，原有输入法可继续保留。");
        local.setPadding(dp(4), dp(24), dp(4), dp(16)); body.addView(local); addScroll(body);
    }
    private interface ValueChanged { void changed(int value); }
    private void addSlider(LinearLayout parent, String title, String subtitle, int initial, int min, int max, ValueChanged listener) {
        LinearLayout card = column(); card.setPadding(dp(16), dp(16), dp(16), dp(10)); card.setBackground(shape(palette.surface, 16, true));
        LinearLayout titleRow = row(); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(label(title, 15, palette.ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = label(initial + " dp", 14, palette.accent, true); titleRow.addView(value); card.addView(titleRow);
        TextView detail = description(subtitle); detail.setPadding(0, dp(9), 0, dp(8)); card.addView(detail);
        SeekBar seek = new SeekBar(this); seek.setMax(max - min); seek.setProgress(initial - min); seek.setContentDescription(title);
        seek.setProgressTintList(ColorStateList.valueOf(palette.accent)); seek.setThumbTintList(ColorStateList.valueOf(palette.accent));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { value.setText((progress + min) + " dp"); if (fromUser) listener.changed(progress + min); }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        card.addView(seek, new LinearLayout.LayoutParams(-1, dp(46)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(16); parent.addView(card, params);
    }

    @Override public void openCustomization() { showPage(1); }
    private LinearLayout pageColumn() { LinearLayout body = column(); body.setPadding(dp(16), dp(8), dp(16), dp(24)); return body; }
    private void addScroll(LinearLayout body) { ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(body); content.addView(scroll, new FrameLayout.LayoutParams(-1, -1)); }
    private TextView sectionTitle(String text) { TextView title = label(text, 21, palette.ink, true); title.setPadding(0, dp(4), 0, dp(10)); return title; }
    private TextView description(String text) { TextView view = label(text, 13, palette.muted, false); view.setLineSpacing(dp(4), 1f); return view; }
    private TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }
    private Button quietButton(String text) {
        Button button = new Button(this); button.setText(text); button.setTextSize(12); button.setTextColor(palette.accent);
        button.setAllCaps(false); button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setPadding(dp(4), 0, dp(4), 0); button.setBackgroundResource(android.R.drawable.list_selector_background); button.setFocusable(false);
        return button;
    }
    private GradientDrawable shape(int color, int radius, boolean stroke) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius));
        if (stroke) drawable.setStroke(dp(1), palette.line); return drawable;
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        captureDraft(); state.putString("draft", draft); state.putInt("selection_start", selectionStart); state.putInt("selection_end", selectionEnd); state.putInt("page", page);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { if (keyDialog != null) keyDialog.dismiss(); super.onDestroy(); }
}
