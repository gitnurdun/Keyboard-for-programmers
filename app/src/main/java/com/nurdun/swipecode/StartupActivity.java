package com.nurdun.swipecode;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** A minimal independent entry point remains usable even if the keyboard UI fails. */
public final class StartupActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String report = CrashLog.readCurrentVersion(this);
        if (report.isEmpty()) startKeyboard(); else showReport(report);
    }
    private void startKeyboard() {
        try {
            startActivity(new Intent(this, MainActivity.class)); finish();
        } catch (RuntimeException | LinkageError failure) {
            CrashLog.save(this, Thread.currentThread().getName(), failure); showReport(CrashLog.read(this));
        }
    }
    private void showReport(String report) {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(48), dp(20), dp(28)); root.setBackgroundColor(0xFFF5F7F3);
        TextView title = new TextView(this); title.setText("启动异常详情"); title.setTextSize(24); title.setTextColor(0xFF17392C); root.addView(title);
        TextView hint = new TextView(this); hint.setText("请点击“复制错误”，将详情发到聊天中。错误只保存在本机，不会自动上传。");
        hint.setTextSize(14); hint.setTextColor(0xFF647B6C); hint.setPadding(0, dp(12), 0, dp(16)); root.addView(hint);
        TextView text = new TextView(this); text.setText(report); text.setTextIsSelectable(true); text.setTextSize(12); text.setTextColor(0xFF17392C); text.setTypeface(Typeface.MONOSPACE);
        ScrollView scroll = new ScrollView(this); scroll.addView(text); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button copy = new Button(this); copy.setText("复制错误"); copy.setOnClickListener(view -> {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("滑码启动异常", report));
            Toast.makeText(this, "错误详情已复制", Toast.LENGTH_SHORT).show();
        }); root.addView(copy, new LinearLayout.LayoutParams(-1, dp(52)));
        Button retry = new Button(this); retry.setText("重试启动"); retry.setOnClickListener(view -> { CrashLog.clear(this); startKeyboard(); });
        root.addView(retry, new LinearLayout.LayoutParams(-1, dp(52))); setContentView(root);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
