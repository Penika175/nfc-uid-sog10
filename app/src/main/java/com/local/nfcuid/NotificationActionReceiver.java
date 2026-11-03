package com.local.nfcuid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String uid = intent.getStringExtra("uid");
        if (uid == null) return;

        if ("mute".equals(action)) {
            Prefs.mute(context, uid);
            NotificationHelper.cancel(context, NotificationHelper.ID_SAVED);
        } else if ("dismiss".equals(action)) {
            NotificationHelper.cancel(context, NotificationHelper.ID_SCAN);
            NotificationHelper.cancel(context, NotificationHelper.ID_SAVED);
        }
    }
}
