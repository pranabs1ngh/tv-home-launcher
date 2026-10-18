package com.bedroomtv.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ChangedPackages;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bedroomtv.home.Ui.dp;

public class MainActivity extends Activity {

    /** HDMI 1 on this Haier/MediaTek set; found by label if the id ever changes. */
    private static final String PS5_INPUT_ID = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5";
    private static final String PS5_INPUT_LABEL = "HDMI 1";

    private static final int TILE_W_DP = 57;
    private static final int TILE_H_DP = 57;
    private static final int GAP_DP = 32;
    private static final float FOCUS_SCALE = 1.6f;

    private LinearLayout appsRow;
    private View ps5;
    /** The saved list and the system's package-change counter the row was last built from. */
    private List<String> builtFrom;
    private int packagesSeq;
    private boolean gamesFocused;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        appsRow = findViewById(R.id.apps_row);

        findViewById(R.id.wifi_pill).setOnClickListener(v -> Ui.start(this, new Intent(Settings.ACTION_WIFI_SETTINGS)));
        findViewById(R.id.settings_pill).setOnClickListener(
                v -> Ui.start(this, new Intent(this, QuickSettingsActivity.class).putExtra(PanelActivity.EXTRA_OVER_HOME, true)));

        LinearLayout inputsRow = findViewById(R.id.inputs_row);
        ps5 = makeTile(getDrawable(R.drawable.brand_playstation), "PS5", v -> openPs5());
        ps5.setId(View.generateViewId());
        inputsRow.addView(ps5);

        ChangedPackages changed = getPackageManager().getChangedPackages(0);
        if (changed != null) packagesSeq = changed.getSequenceNumber();
        buildAppsRow();
        appsRow.getChildAt(0).requestFocus();

        // Selecting Games slides the rows up a little so the game tile and its name sit clear of the edge.
        // The block is clipped just below the clock, so whatever slides past that edge is hidden.
        View rowsBlock = findViewById(R.id.rows_block);
        rowsBlock.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            boolean games = newFocus != null && newFocus.getParent() == inputsRow;
            if (games == gamesFocused) return;
            gamesFocused = games;
            rowsBlock.animate().translationY(games ? -dp(80) : 0).setDuration(220).start();
        });
    }

    /** Whether the home screen is in front; the quick settings panel only frosts the wallpaper over it. */
    static boolean inFront;

    @Override
    protected void onPause() {
        super.onPause();
        inFront = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        inFront = true;
        TextView wifiName = findViewById(R.id.wifi_name);
        wifiName.setText(networkName(this));
        // Redraw only when the add/move/remove panel saved a new order or an app was installed,
        // removed or updated since the row was built.
        ChangedPackages changed = getPackageManager().getChangedPackages(packagesSeq);
        if (changed != null) packagesSeq = changed.getSequenceNumber();
        if (changed != null || !HomeApps.load(this).equals(builtFrom)) {
            View focused = getCurrentFocus();
            int index = focused != null ? appsRow.indexOfChild(focused) : -1;
            buildAppsRow();
            if (index >= 0) appsRow.getChildAt(Math.min(index, appsRow.getChildCount() - 1)).requestFocus();
        }
    }

    /** The Wi-Fi network name, or "Wi-Fi" when unknown (needs the location permission granted at install). */
    static String networkName(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm == null || !wm.isWifiEnabled()) return "Wi-Fi off";
        WifiInfo info = wm.getConnectionInfo();
        String ssid = info == null ? null : info.getSSID();
        if (ssid == null || ssid.equals(WifiManager.UNKNOWN_SSID) || ssid.isEmpty()) return "Wi-Fi";
        return ssid.replaceAll("^\"|\"$", "");
    }

    private void buildAppsRow() {
        appsRow.removeAllViews();
        builtFrom = HomeApps.load(this);
        PackageManager pm = getPackageManager();
        for (String pkg : builtFrom) {
            Intent launch = HomeApps.launchIntent(pm, pkg);
            if (launch == null) continue;

            // PS5-style: a white logo centred on a black tile, rather than the app's banner.
            ResolveInfo ri = pm.resolveActivity(launch, 0);
            if (ri == null) continue;
            ActivityInfo ai = ri.activityInfo;
            Drawable art = monoIcon(pkg, ai, pm);
            String label = ai.loadLabel(pm).toString();
            final Intent target = launch;
            View tile = makeTile(art, label, v -> Ui.start(this, target));
            // Hold OK on a tile to move or remove it.
            tile.setOnLongClickListener(v -> {
                Ui.start(this, new Intent(this, AppsPanelActivity.class).putExtra(AppsPanelActivity.EXTRA_PACKAGE, pkg));
                return true;
            });
            appsRow.addView(tile);
        }
        appsRow.addView(makeTile(getDrawable(R.drawable.ic_add), "Add app",
                v -> Ui.start(this, new Intent(this, AppsPanelActivity.class))));

        // Up from any app goes to the top bar; down goes to the PS5 tile.
        View first = appsRow.getChildAt(0);
        for (int i = 0; i < appsRow.getChildCount(); i++) {
            View tile = appsRow.getChildAt(i);
            tile.setId(View.generateViewId());
            tile.setNextFocusUpId(R.id.settings_pill);
            tile.setNextFocusDownId(ps5.getId());
        }
        ps5.setNextFocusUpId(first.getId());
        findViewById(R.id.wifi_pill).setNextFocusDownId(first.getId());
        findViewById(R.id.settings_pill).setNextFocusDownId(first.getId());
    }

    /** White brand logos (Simple Icons, CC0) for the default apps. */
    private static final Map<String, Integer> BRAND_ICONS = new HashMap<>();
    static {
        BRAND_ICONS.put("com.google.android.youtube.tv", R.drawable.brand_youtube);
        BRAND_ICONS.put("com.netflix.ninja", R.drawable.brand_netflix);
        BRAND_ICONS.put("com.google.android.youtube.tvmusic", R.drawable.brand_youtubemusic);
        BRAND_ICONS.put("com.android.vending", R.drawable.brand_googleplay);
        // Not in Simple Icons; drawn for TV Home.
        BRAND_ICONS.put("in.startv.hotstar", R.drawable.brand_hotstar);
        BRAND_ICONS.put("com.amazon.amazonvideo.livingroom", R.drawable.brand_primevideo);
    }

    /**
     * A black-and-white icon: the bundled brand logo, else the app's own monochrome (themed)
     * icon tinted white, else its normal icon in greyscale.
     */
    private Drawable monoIcon(String pkg, ActivityInfo ai, PackageManager pm) {
        Integer brand = BRAND_ICONS.get(pkg);
        if (brand != null) return getDrawable(brand);
        Drawable icon = ai.loadUnbadgedIcon(pm);
        if (icon instanceof AdaptiveIconDrawable && ((AdaptiveIconDrawable) icon).getMonochrome() != null) {
            Drawable mono = ((AdaptiveIconDrawable) icon).getMonochrome().mutate();
            mono.setTint(0xFFFFFFFF);
            // The monochrome layer is drawn on a 108dp canvas with a 72dp safe zone; trim the margin.
            return new InsetDrawable(mono, -0.2f);
        }
        ColorMatrix grey = new ColorMatrix();
        grey.setSaturation(0f);
        icon = icon.mutate();
        icon.setColorFilter(new ColorMatrixColorFilter(grey));
        return icon;
    }

    /** A square card with the app's logo and a name that only shows while focused. */
    private View makeTile(Drawable art, String label, View.OnClickListener onClick) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setClipChildren(false);
        tile.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(TILE_W_DP), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(GAP_DP));
        tile.setLayoutParams(lp);

        // A soft radial glow behind the card instead of an elevation shadow, which parents clip to a hard edge.
        FrameLayout holder = new FrameLayout(this);
        holder.setClipChildren(false);
        tile.addView(holder, new LinearLayout.LayoutParams(dp(TILE_W_DP), dp(TILE_H_DP)));
        View glow = new View(this);
        glow.setBackgroundResource(R.drawable.tile_glow);
        glow.setAlpha(0f);
        glow.setScaleX(2.6f);
        glow.setScaleY(2.6f);
        holder.addView(glow, new FrameLayout.LayoutParams(dp(TILE_W_DP), dp(TILE_H_DP)));

        // Subtle corners normally; the selected tile rounds off as it grows.
        GradientDrawable cardBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1E1F24, 0xFF08080A});
        cardBg.setCornerRadius(dp(6));
        GradientDrawable gloss = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x38FFFFFF, 0x0AFFFFFF, 0x00FFFFFF});
        gloss.setCornerRadius(dp(6));
        FrameLayout card = new FrameLayout(this);
        card.setBackground(cardBg);
        card.setForeground(gloss);
        card.setDuplicateParentStateEnabled(true);
        card.setClipToOutline(true);
        holder.addView(card, new FrameLayout.LayoutParams(dp(TILE_W_DP), dp(TILE_H_DP)));

        ImageView image = new ImageView(this);
        image.setImageDrawable(art);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int inset = dp(13);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        ilp.setMargins(inset, inset, inset, inset);
        card.addView(image, ilp);

        // PS5-style light streak, swept once across the tile when it gains focus.
        int shineW = dp(TILE_W_DP) / 2;
        View shine = new View(this);
        shine.setBackgroundResource(R.drawable.tile_shine);
        shine.setRotation(20f);
        shine.setScaleY(1.8f);
        shine.setVisibility(View.INVISIBLE);
        card.addView(shine, new FrameLayout.LayoutParams(shineW, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER);
        name.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                Math.round(dp(TILE_W_DP) * FOCUS_SCALE), LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(24);
        tile.addView(name, nlp);

        tile.setOnFocusChangeListener((v, hasFocus) -> {
            float s = hasFocus ? FOCUS_SCALE : 1f;
            card.animate().scaleX(s).scaleY(s).setDuration(140).start();
            // Radius is set before scaling, so 8dp reads as ~15dp on the enlarged tile.
            float radius = dp(hasFocus ? 8 : 6);
            cardBg.setCornerRadius(radius);
            gloss.setCornerRadius(radius);
            card.invalidateOutline();
            glow.animate().alpha(hasFocus ? 1f : 0f).setDuration(220).start();
            name.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
            shine.animate().cancel();
            if (hasFocus) {
                shine.setTranslationX(-shineW * 1.5f);
                shine.setVisibility(View.VISIBLE);
                shine.animate().translationX(dp(TILE_W_DP) + shineW).setStartDelay(90).setDuration(650)
                        .withEndAction(() -> shine.setVisibility(View.INVISIBLE)).start();
            } else {
                shine.setVisibility(View.INVISIBLE);
            }
        });
        tile.setOnClickListener(onClick);
        return tile;
    }

    private void openPs5() {
        String inputId = findPs5Input();
        if (inputId == null) {
            Toast.makeText(this, "HDMI 1 not found", Toast.LENGTH_SHORT).show();
            return;
        }
        Ui.start(this, new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId)));
    }

    private String findPs5Input() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return null;
        String byLabel = null;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (PS5_INPUT_ID.equals(info.getId())) return info.getId();
            if (info.getType() == TvInputInfo.TYPE_HDMI && info.getParentId() == null
                    && PS5_INPUT_LABEL.contentEquals(info.loadLabel(this))) {
                byLabel = info.getId();
            }
        }
        return byLabel;
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to.
    }
}
