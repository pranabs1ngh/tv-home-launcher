package com.bedroomtv.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The apps pinned to the home row, in order, saved across restarts. */
final class HomeApps {

    private static final String[] DEFAULTS = {
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "in.startv.hotstar",
            "com.amazon.amazonvideo.livingroom",
            "com.google.android.youtube.tvmusic",
            "com.android.vending",
    };

    private static final String PREFS = "home";
    private static final String KEY = "apps";

    private HomeApps() {
    }

    static List<String> load(Context context) {
        String saved = prefs(context).getString(KEY, null);
        if (saved == null) return new ArrayList<>(Arrays.asList(DEFAULTS));
        List<String> apps = new ArrayList<>();
        for (String pkg : saved.split(",")) if (!pkg.isEmpty()) apps.add(pkg);
        return apps;
    }

    /** The pinned apps that are installed, i.e. the ones the home row actually shows. */
    static List<String> loadInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        List<String> apps = load(context);
        apps.removeIf(pkg -> launchIntent(pm, pkg) == null);
        return apps;
    }

    static void save(Context context, List<String> apps) {
        prefs(context).edit().putString(KEY, String.join(",", apps)).apply();
    }

    /** The app's TV launch intent, or its phone one, or null when it isn't installed. */
    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        return launch != null ? launch : pm.getLaunchIntentForPackage(pkg);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
