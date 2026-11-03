package com.local.nfcuid;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class Prefs {

    private static final String NAME = "settings";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean notifyNewCard(Context c) {
        return sp(c).getBoolean("notify_new", true);
    }

    public static void setNotifyNewCard(Context c, boolean v) {
        sp(c).edit().putBoolean("notify_new", v).apply();
    }

    public static boolean notifySavedCard(Context c) {
        return sp(c).getBoolean("notify_saved", true);
    }

    public static void setNotifySavedCard(Context c, boolean v) {
        sp(c).edit().putBoolean("notify_saved", v).apply();
    }

    public static Set<String> muted(Context c) {
        return new HashSet<>(sp(c).getStringSet("muted", new HashSet<>()));
    }

    public static void mute(Context c, String uid) {
        Set<String> s = muted(c);
        s.add(uid);
        sp(c).edit().putStringSet("muted", s).apply();
    }

    public static void unmute(Context c, String uid) {
        Set<String> s = muted(c);
        s.remove(uid);
        sp(c).edit().putStringSet("muted", s).apply();
    }

    public static boolean isMuted(Context c, String uid) {
        return muted(c).contains(uid);
    }
}
