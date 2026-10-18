package com.bedroomtv.home;

import android.content.ComponentName;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import static com.bedroomtv.home.Ui.dp;

/** PS5-style quick settings panel on the right, opened by the gear button or the remote's settings key. */
public class QuickSettingsActivity extends PanelActivity {

    private static final String TV_SETTINGS = "com.android.tv.settings";

    /** Whether the panel is on screen, so the settings key can close it instead of reopening it. */
    static boolean showing;

    private boolean showingInputs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showTiles();
    }

    @Override
    protected void onStart() {
        super.onStart();
        showing = true;
    }

    @Override
    protected void onStop() {
        showing = false;
        super.onStop();
    }

    /** The settings key again while open: close, like Google TV's panel. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    private void showTiles() {
        showingInputs = false;
        setPanelTitle("Quick settings", "Bedroom TV");
        rows.removeAllViews();

        View first = addTile(R.drawable.ic_wifi, "Wi-Fi", MainActivity.networkName(this),
                v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        // This TV has no handler for ACTION_BLUETOOTH_SETTINGS; its "Remotes & accessories" page is a slice.
        addTile(R.drawable.ic_bluetooth, "Bluetooth", "Remotes, speakers", v -> open(
                new Intent("android.settings.SLICE_SETTINGS")
                        .putExtra("slice_uri", "content://com.android.tv.settings.accessories.sliceprovider/general")
                        .putExtra("slice_title", "Remotes & accessories"),
                new Intent("com.google.android.intent.action.CONNECT_INPUT")));
        // The TV's own Picture and Sound screens already open as right-side panels.
        addTile(R.drawable.ic_picture, "Picture", "Mode, backlight",
                v -> open(new Intent().setComponent(new ComponentName(TV_SETTINGS,
                        TV_SETTINGS + ".partnercustomizer.picture.PictureActivity"))));
        addTile(R.drawable.ic_sound, "Sound", "Style, bass, treble",
                v -> open(new Intent().setComponent(new ComponentName(TV_SETTINGS,
                        TV_SETTINGS + ".device.sound.SoundActivity"))));
        addTile(R.drawable.ic_input, "Inputs", "HDMI, PS5", v -> showInputs());
        addTile(R.drawable.ic_settings, "All settings", "Apps, system",
                v -> open(new Intent(Settings.ACTION_SETTINGS)));
        first.requestFocus();
    }

    /** Lists the TV's external inputs; a connected device's name (e.g. PS5) shows under its port. */
    private void showInputs() {
        showingInputs = true;
        setPanelTitle("Inputs", "Bedroom TV");
        rows.removeAllViews();

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        List<TvInputInfo> inputs = tim == null ? new ArrayList<>() : tim.getTvInputList();
        List<TvInputInfo> ports = new ArrayList<>();
        for (TvInputInfo info : inputs) {
            if (info.getParentId() == null && info.isPassthroughInput()) ports.add(info);
        }
        // HDMI 1..4 first, then anything else (Composite), by the port's own name.
        ports.sort((a, b) -> {
            String la = a.loadLabel(this).toString(), lb = b.loadLabel(this).toString();
            int ha = la.startsWith("HDMI") ? 0 : 1, hb = lb.startsWith("HDMI") ? 0 : 1;
            return ha != hb ? ha - hb : la.compareTo(lb);
        });
        View first = null;
        for (TvInputInfo info : ports) {
            String device = null;
            for (TvInputInfo child : inputs) {
                if (info.getId().equals(child.getParentId())) device = label(child);
            }
            String port = info.loadLabel(this).toString();
            String title = label(info);
            String subtitle = device != null ? device : "Nothing detected";
            if (!title.equals(port)) subtitle = port + " · " + subtitle;
            String id = info.getId();
            View row = addListRow(title, subtitle,
                    v -> open(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))));
            if (first == null) first = row;
        }
        if (first != null) first.requestFocus();
    }

    private String label(TvInputInfo info) {
        CharSequence custom = info.loadCustomLabel(this);
        return (custom != null && custom.length() > 0 ? custom : info.loadLabel(this)).toString();
    }

    @Override
    public void onBackPressed() {
        if (showingInputs) showTiles(); else super.onBackPressed();
    }

    /** Adds a half-width tile; tiles fill a two-column grid, left to right. */
    private View addTile(int icon, String title, String subtitle, View.OnClickListener onClick) {
        LinearLayout line = rows.getChildCount() == 0 ? null
                : (LinearLayout) rows.getChildAt(rows.getChildCount() - 1);
        if (line == null || line.getChildCount() == 2) {
            line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setClipChildren(false);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            llp.bottomMargin = dp(10);
            rows.addView(line, llp);
        }

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setBackgroundResource(R.drawable.panel_row_bg);
        tile.setFocusable(true);
        tile.setPadding(dp(12), 0, dp(8), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        if (line.getChildCount() == 0) lp.setMarginEnd(dp(5)); else lp.setMarginStart(dp(5));
        line.addView(tile, lp);

        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setDuplicateParentStateEnabled(true);
        iv.setImageTintList(getColorStateList(R.color.panel_text));
        tile.addView(iv, new LinearLayout.LayoutParams(dp(20), dp(20)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(dp(10));
        tile.addView(texts, tlp);
        texts.addView(text(title, 13, true));
        texts.addView(text(subtitle, 10, false));

        growOnFocus(tile, 1.06f);
        tile.setOnClickListener(onClick);
        return tile;
    }
}
