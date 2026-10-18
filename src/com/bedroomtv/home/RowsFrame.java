package com.bedroomtv.home;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Holds the slidable rows. Children are measured at their full height instead of being squeezed
 * to fit (a plain FrameLayout shrinks the last row, which collapsed the PS5 tile's name).
 */
public class RowsFrame extends FrameLayout {

    public RowsFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void measureChildWithMargins(View child, int widthSpec, int widthUsed, int heightSpec, int heightUsed) {
        super.measureChildWithMargins(child, widthSpec, widthUsed,
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightUsed);
    }
}
