package com.example.test.openfaceandroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;

public class ScreenReceiver extends BroadcastReceiver {

    private boolean screenOn;

    @Override
    public void onReceive(Context context, Intent intent) {
        screenOn = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            for (Display display : dm.getDisplays()) {
                if (display.getState() == Display.STATE_ON) {
                    screenOn = true;
                }
            }
        } else {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                screenOn = false;
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                screenOn = true;
            }
        }
        Intent i = new Intent(context, CameraService.class);
        i.putExtra("screen_state", screenOn);
        context.startService(i);
    }
}
