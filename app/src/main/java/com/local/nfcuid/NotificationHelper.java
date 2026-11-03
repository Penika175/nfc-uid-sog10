package com.local.nfcuid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationHelper {

    public static final String CH_SCAN = "scan";
    public static final String CH_SAVED = "saved";
    public static final String CH_PROGRESS = "progress";

    public static final int ID_SCAN = 1001;
    public static final int ID_SAVED = 1002;
    public static final int ID_PROGRESS = 1003;

    public static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel scan = new NotificationChannel(
                CH_SCAN, "新卡", NotificationManager.IMPORTANCE_HIGH);
        scan.setDescription("贴到未保存的卡时通知");
        nm.createNotificationChannel(scan);

        NotificationChannel saved = new NotificationChannel(
                CH_SAVED, "已存卡", NotificationManager.IMPORTANCE_LOW);
        saved.setDescription("贴到已保存的卡时通知");
        nm.createNotificationChannel(saved);

        NotificationChannel prog = new NotificationChannel(
                CH_PROGRESS, "切换进度", NotificationManager.IMPORTANCE_LOW);
        prog.setDescription("切换卡号时的进度");
        nm.createNotificationChannel(prog);
    }

    private static PendingIntent activityIntent(Context ctx, String uid, String action) {
        Intent i = new Intent(ctx, MainActivity.class)
                .putExtra("uid", uid)
                .putExtra("action", action)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(ctx, (uid + action).hashCode(), i, flags);
    }

    private static PendingIntent broadcastIntent(Context ctx, String uid, String action) {
        Intent i = new Intent(ctx, NotificationActionReceiver.class)
                .setAction(action)
                .putExtra("uid", uid);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, (uid + action).hashCode(), i, flags);
    }

    public static void notifyNewCard(Context ctx, String uid) {
        ensureChannels(ctx);
        Notification n = new NotificationCompat.Builder(ctx, CH_SCAN)
                .setSmallIcon(R.drawable.ic_nfc)
                .setContentTitle("发现新卡 " + uid)
                .setContentText("点此保存这张卡")
                .setContentIntent(activityIntent(ctx, uid, "save"))
                .addAction(0, "保存", activityIntent(ctx, uid, "save"))
                .addAction(0, "保存并使用", activityIntent(ctx, uid, "save_and_use"))
                .addAction(0, "忽略", broadcastIntent(ctx, uid, "dismiss"))
                .setAutoCancel(true)
                .build();
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_SCAN, n);
    }

    public static void notifySavedCard(Context ctx, String uid, String name) {
        ensureChannels(ctx);
        Notification n = new NotificationCompat.Builder(ctx, CH_SAVED)
                .setSmallIcon(R.drawable.ic_nfc)
                .setContentTitle("已保存：" + name)
                .setContentText(uid)
                .setContentIntent(activityIntent(ctx, uid, "switch"))
                .addAction(0, "切换", activityIntent(ctx, uid, "switch"))
                .addAction(0, "不再显示", broadcastIntent(ctx, uid, "mute"))
                .addAction(0, "忽略", broadcastIntent(ctx, uid, "dismiss"))
                .setAutoCancel(true)
                .build();
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_SAVED, n);
    }

    public static void notifyProgress(Context ctx, String uid) {
        ensureChannels(ctx);
        Notification n = new NotificationCompat.Builder(ctx, CH_PROGRESS)
                .setSmallIcon(R.drawable.ic_nfc)
                .setContentTitle("正在切换到 " + uid + "…")
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build();
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_PROGRESS, n);
    }

    public static void notifyProgressDone(Context ctx, String uid) {
        ensureChannels(ctx);
        Notification n = new NotificationCompat.Builder(ctx, CH_PROGRESS)
                .setSmallIcon(R.drawable.ic_nfc)
                .setContentTitle("已切换到 " + uid)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_PROGRESS, n);
    }

    public static void cancel(Context ctx, int id) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(id);
    }
}
