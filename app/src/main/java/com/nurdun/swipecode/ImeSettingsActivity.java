package com.nurdun.swipecode;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Public settings entry exposed to Android; configuration remains inside the app. */
public final class ImeSettingsActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, MainActivity.class).putExtra("page", 3)); finish();
    }
}
