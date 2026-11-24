package com.fieldconsole;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Standalone test APK process. Use only framework and Java runtime classes. */
public final class SyntheticShareReceiver extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String token = getIntent().getStringExtra("receipt");
        if (token != null && token.startsWith("com.fieldconsole.test.receipt.")) {
            Intent result = new Intent(token).setPackage("com.fieldconsole.debug");
            result.putExtra("text", getIntent().getStringExtra(Intent.EXTRA_TEXT));
            sendBroadcast(result);
        }
        finish();
    }
}
