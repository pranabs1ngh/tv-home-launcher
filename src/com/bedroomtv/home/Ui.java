package com.bedroomtv.home;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.widget.Toast;

/** Small helpers shared by the home screen and the panels. */
final class Ui {

    private Ui() {
    }

    static int dp(int value) {
        return Math.round(value * Resources.getSystem().getDisplayMetrics().density);
    }

    /** Starts the first of these the TV lets us open; says so if none can be. */
    static boolean start(Context context, Intent... targets) {
        for (Intent target : targets) {
            try {
                context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (ActivityNotFoundException | SecurityException e) {
                // Try the next one.
            }
        }
        Toast.makeText(context, "Can't open that", Toast.LENGTH_SHORT).show();
        return false;
    }
}
