package com.bedroomtv.home;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import static com.bedroomtv.home.Ui.dp;

/** Shared look for the PS5-style right-side panels. */
abstract class PanelActivity extends Activity {

    /** False when opened over another app: the blurred home wallpaper would be the wrong picture there. */
    static final String EXTRA_OVER_HOME = "over_home";

    /** A blurred screenshot of the app underneath, handed over by HomeGuardService. */
    static Bitmap backdrop;

    /** Downscale steps for the cheap blur: shrink hard, then let bilinear filtering smear it. */
    private static final int[][] BLUR_STEPS = {{48, 27}, {192, 108}, {64, 36}};
    private static final int BLUR_W = 240, BLUR_H = 135;

    protected LinearLayout rows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_settings);
        rows = findViewById(R.id.rows);
        View panel = findViewById(R.id.panel);
        // Plain dark glass until the frosted copy is laid out, and for good if there is nothing to
        // frost (copy-protected video screenshots as black).
        panel.setBackgroundResource(R.drawable.panel_glass_plain);
        Bitmap blur = null;
        if (getIntent().getBooleanExtra(EXTRA_OVER_HOME, true)) {
            blur = BitmapFactory.decodeResource(getResources(), R.drawable.wallpaper_blur);
        } else if (backdrop != null) {
            blur = backdrop;
            backdrop = null;
        }
        if (blur != null) {
            Bitmap frosted = blur;
            panel.post(() -> frost(panel, frosted));
        }
    }

    /** Panels are one-shot: once hidden they close, so they never sit in the background holding a bitmap. */
    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    /**
     * Blurs and dims a screenshot for the panel, recycling the full-size copies straight away.
     * Returns null when the screenshot is black (DRM video), as there is nothing to frost.
     */
    static Bitmap blur(Bitmap shot) {
        Bitmap b = shot.copy(Bitmap.Config.RGB_565, false);
        shot.recycle();
        for (int[] size : BLUR_STEPS) {
            Bitmap next = Bitmap.createScaledBitmap(b, size[0], size[1], true);
            b.recycle();
            b = next;
        }
        if (isBlack(b)) {
            b.recycle();
            return null;
        }
        Bitmap out = Bitmap.createBitmap(BLUR_W, BLUR_H, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(b, null, new Rect(0, 0, BLUR_W, BLUR_H), new Paint(Paint.FILTER_BITMAP_FLAG));
        b.recycle();
        // Dim bright app content so the panel stays dark themed.
        canvas.drawColor(0x66000000);
        return out;
    }

    private static boolean isBlack(Bitmap small) {
        int[] pixels = new int[small.getWidth() * small.getHeight()];
        small.getPixels(pixels, 0, small.getWidth(), 0, 0, small.getWidth(), small.getHeight());
        for (int p : pixels) {
            if ((p & 0xF0F0F0) != 0) return false;
        }
        return true;
    }

    /**
     * Fakes frosted glass (this TV has window blur disabled): paints a pre-blurred picture,
     * aligned to where the panel sits on screen, under a translucent tint.
     */
    private void frost(View panel, Bitmap blur) {
        int[] at = new int[2];
        panel.getLocationOnScreen(at);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Matrix m = new Matrix();
        m.setScale((float) dm.widthPixels / blur.getWidth(), (float) dm.heightPixels / blur.getHeight());
        m.postTranslate(-at[0], -at[1]);
        BitmapShader shader = new BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(m);

        float r = dp(26);
        ShapeDrawable glass = new ShapeDrawable(new RoundRectShape(new float[]{r, r, r, r, r, r, r, r}, null, null));
        glass.getPaint().setShader(shader);
        glass.getPaint().setFilterBitmap(true);
        panel.setBackground(new LayerDrawable(new Drawable[]{glass, getDrawable(R.drawable.panel_glass)}));
    }

    protected void setPanelTitle(String title, String subtitle) {
        ((TextView) findViewById(R.id.panel_title)).setText(title);
        ((TextView) findViewById(R.id.panel_subtitle)).setText(subtitle);
    }

    /** A full-width row with a title and an optional smaller line under it. */
    protected View addListRow(String title, String subtitle, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.panel_row_bg);
        row.setFocusable(true);
        row.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(subtitle == null ? 42 : 50));
        lp.bottomMargin = dp(8);
        rows.addView(row, lp);
        row.addView(text(title, 13, true));
        if (subtitle != null) row.addView(text(subtitle, 11, false));
        growOnFocus(row, 1.04f);
        row.setOnClickListener(onClick);
        return row;
    }

    /** Panel text: a Medium title or a Regular (theme font) secondary line, lit with its row's focus. */
    protected TextView text(String value, int sp, boolean title) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setSingleLine(true);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (title) tv.setTypeface(getResources().getFont(R.font.inter_medium));
        tv.setTextColor(getColorStateList(title ? R.color.panel_text : R.color.panel_subtext));
        tv.setDuplicateParentStateEnabled(true);
        return tv;
    }

    protected static void growOnFocus(View view, float scale) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            float s = hasFocus ? scale : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(120).start();
        });
    }

    /** Opens the first target the TV lets us start and closes the panel. */
    protected void open(Intent... targets) {
        if (Ui.start(this, targets)) finish();
    }
}
