package com.nurdun.swipecode;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import com.nurdun.swipecode.core.KeyBinding;
import com.nurdun.swipecode.core.KeyboardSession;
import java.util.Arrays;

/** Edits a draft only; the surrounding key dialog owns save/cancel. */
public final class BindingSlotView extends LinearLayout {
    private final Palette palette;
    private final EditText input;
    private final String title;
    private KeyBinding binding;
    private AlertDialog detail;
    public BindingSlotView(Context context, Palette palette, String title, int fieldId, KeyBinding initial) {
        super(context); this.palette = palette; this.title = title;
        setOrientation(VERTICAL);
        TextView caption = new TextView(context); caption.setText(title); caption.setTextSize(12); caption.setTextColor(palette.muted); addView(caption);
        input = new EditText(context); input.setId(fieldId); input.setSingleLine(true); input.setTextSize(18);
        input.setSelectAllOnFocus(true); input.setTextColor(palette.ink); input.setHintTextColor(palette.muted); input.setHint("留空");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); input.setContentDescription(title + "绑定");
        addView(input, new LayoutParams(-1, dp(52)));
        Button change = new Button(context); change.setText("字符 / 按键 / 功能"); change.setAllCaps(false); change.setTextSize(11); change.setTextColor(palette.accent);
        change.setPadding(0, 0, 0, 0); change.setMinWidth(0); change.setMinimumWidth(0); change.setOnClickListener(view -> edit()); addView(change, new LayoutParams(-1, dp(42)));
        setBinding(initial);
    }
    public KeyBinding value() { return binding.kind == KeyBinding.Kind.TEXT ? KeyBinding.text(input.getText().toString()) : binding; }
    public boolean validateInput() {
        try { value(); input.setError(null); return true; }
        catch (IllegalArgumentException invalid) { input.setError(invalid.getMessage()); return false; }
    }
    public void setBinding(KeyBinding value) {
        binding = value; boolean text = value.kind == KeyBinding.Kind.TEXT;
        input.setFocusable(text); input.setFocusableInTouchMode(text); input.setCursorVisible(text);
        input.setText(text ? value.value : value.label()); input.setError(null);
        input.setTextColor(text ? palette.ink : palette.accent);
        input.setOnClickListener(text ? null : view -> edit());
    }
    public EditText textInput() { return binding.kind == KeyBinding.Kind.TEXT ? input : null; }
    private void edit() {
        Context context = getContext();
        LinearLayout panel = new LinearLayout(context); panel.setOrientation(VERTICAL); panel.setPadding(dp(20), dp(8), dp(20), dp(12));
        RadioGroup modes = new RadioGroup(context); modes.setOrientation(HORIZONTAL);
        RadioButton textMode = new RadioButton(context); textMode.setId(View.generateViewId()); textMode.setText("文字"); modes.addView(textMode);
        RadioButton keyMode = new RadioButton(context); keyMode.setId(View.generateViewId()); keyMode.setText("按键"); modes.addView(keyMode);
        RadioButton commandMode = new RadioButton(context); commandMode.setId(View.generateViewId()); commandMode.setText("功能"); modes.addView(commandMode); panel.addView(modes);
        EditText text = new EditText(context); text.setSingleLine(true); text.setHint("字符或短文本；留空清除此方向"); text.setTextColor(palette.ink);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        text.setText(binding.kind == KeyBinding.Kind.TEXT ? input.getText() : ""); panel.addView(text, new LayoutParams(-1, dp(54)));
        LinearLayout keyPanel = new LinearLayout(context); keyPanel.setOrientation(VERTICAL);
        Spinner keys = new Spinner(context); String[] labels = new String[KeyBinding.KEYS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = KeyBinding.keyLabel(KeyBinding.KEYS[i]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, labels); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); keys.setAdapter(adapter);
        keys.setContentDescription("目标按键"); keys.setSelection(binding.kind == KeyBinding.Kind.KEY ? Arrays.asList(KeyBinding.KEYS).indexOf(binding.value) : 0); keyPanel.addView(keys, new LayoutParams(-1, dp(54)));
        LinearLayout flags = new LinearLayout(context); flags.setOrientation(VERTICAL);
        String[] names = {"Ctrl", "Alt", "Shift", "Meta / Win"}; int[] bits = {KeyboardSession.CTRL, KeyboardSession.ALT, KeyboardSession.SHIFT, KeyboardSession.META};
        CheckBox[] checks = new CheckBox[bits.length];
        for (int i = 0; i < bits.length; i++) { checks[i] = new CheckBox(context); checks[i].setText(names[i]); checks[i].setChecked((binding.modifiers & bits[i]) != 0); flags.addView(checks[i]); }
        keyPanel.addView(flags); panel.addView(keyPanel);
        Spinner commands = new Spinner(context);
        String[] commandNames = {"Shift：保持 / 双击大写锁定", "Ctrl：保持到下一字符", "Alt：保持到下一字符", "打开自定义页面"};
        ArrayAdapter<String> commandAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, commandNames); commandAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); commands.setAdapter(commandAdapter);
        commands.setSelection(binding.kind == KeyBinding.Kind.COMMAND ? Arrays.asList(KeyBinding.COMMANDS).indexOf(binding.value) : 0); commands.setContentDescription("键盘功能"); panel.addView(commands, new LayoutParams(-1, dp(54)));
        TextView help = new TextView(context); help.setText("按键可发送Ctrl+L等组合；功能可保持Shift/Ctrl/Alt或打开自定义页。轻点与四个方向都可配置，留空可停用。实际快捷键效果由接收应用决定。"); help.setTextSize(12); help.setTextColor(palette.muted); panel.addView(help);
        modes.setOnCheckedChangeListener((group, id) -> {
            boolean isText = id == textMode.getId(); text.setVisibility(isText ? VISIBLE : GONE); keyPanel.setVisibility(id == keyMode.getId() ? VISIBLE : GONE); commands.setVisibility(id == commandMode.getId() ? VISIBLE : GONE);
            if (!isText) ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(text.getWindowToken(), 0);
        });
        modes.check(binding.kind == KeyBinding.Kind.TEXT ? textMode.getId() : binding.kind == KeyBinding.Kind.KEY ? keyMode.getId() : commandMode.getId());
        ScrollView scroll = new ScrollView(context); scroll.addView(panel);
        detail = new AlertDialog.Builder(context).setTitle(title).setView(scroll).setNegativeButton("取消", null).setNeutralButton("清空", (dialog, which) -> setBinding(KeyBinding.text(""))).setPositiveButton("应用到此方向", null).create();
        detail.setOnShowListener(unused -> detail.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (modes.getCheckedRadioButtonId() == textMode.getId()) {
                try { setBinding(KeyBinding.text(text.getText().toString())); }
                catch (IllegalArgumentException invalid) { text.setError(invalid.getMessage()); return; }
            } else if (modes.getCheckedRadioButtonId() == commandMode.getId()) {
                setBinding(KeyBinding.command(KeyBinding.COMMANDS[commands.getSelectedItemPosition()]));
            } else {
                int modifiers = 0; for (int i = 0; i < bits.length; i++) if (checks[i].isChecked()) modifiers |= bits[i];
                setBinding(KeyBinding.key(KeyBinding.KEYS[keys.getSelectedItemPosition()], modifiers));
            }
            detail.dismiss();
        }));
        detail.show();
    }
    @Override protected void onDetachedFromWindow() { if (detail != null) detail.dismiss(); super.onDetachedFromWindow(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
