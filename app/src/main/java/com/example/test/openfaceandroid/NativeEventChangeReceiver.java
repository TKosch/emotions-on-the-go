package com.example.test.openfaceandroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NativeEventChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.
        String test = "123";
        test = "231";
    }
}
