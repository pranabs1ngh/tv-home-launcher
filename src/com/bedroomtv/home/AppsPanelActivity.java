package com.bedroomtv.home;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Side panel for the home row: with {@link #EXTRA_PACKAGE} it moves or removes that app,
 * otherwise it lists installed TV apps to add.
 */
public class AppsPanelActivity extends PanelActivity {

    static final String EXTRA_PACKAGE = "package";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pkg = getIntent().getStringExtra(EXTRA_PACKAGE);
        if (pkg != null) showEdit(pkg); else showAdd();
    }

    /** Moves are between the tiles you can see, so uninstalled pinned apps are left out (and dropped). */
    private void showEdit(String pkg) {
        setPanelTitle(appLabel(pkg), "On your home screen");
        List<String> apps = HomeApps.loadInstalled(this);
        int i = apps.indexOf(pkg);
        View first = null;
        if (i > 0) {
            first = addListRow("Move left", null, v -> {
                apps.remove(i);
                apps.add(i - 1, pkg);
                done(apps);
            });
        }
        if (i >= 0 && i < apps.size() - 1) {
            View right = addListRow("Move right", null, v -> {
                apps.remove(i);
                apps.add(i + 1, pkg);
                done(apps);
            });
            if (first == null) first = right;
        }
        View remove = addListRow("Remove from home", null, v -> {
            apps.remove(pkg);
            done(apps);
        });
        (first != null ? first : remove).requestFocus();
    }

    private void showAdd() {
        setPanelTitle("Add an app", "Installed apps");
        List<String> shown = HomeApps.load(this);
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
        // Labels load other apps' resources, so read each once rather than on every comparison.
        Map<ResolveInfo, String> labels = new HashMap<>();
        for (ResolveInfo ri : found) labels.put(ri, ri.loadLabel(pm).toString());
        found.sort((a, b) -> labels.get(a).compareToIgnoreCase(labels.get(b)));

        Set<String> skip = new HashSet<>(shown);
        skip.add(getPackageName());
        View first = null;
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (!skip.add(pkg)) continue;
            View row = addListRow(labels.get(ri), null, v -> {
                shown.add(pkg);
                done(shown);
            });
            if (first == null) first = row;
        }
        if (first == null) {
            first = addListRow("Every installed app is already on home", null, v -> finish());
        }
        first.requestFocus();
    }

    private void done(List<String> apps) {
        HomeApps.save(this, apps);
        finish();
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}
