package de.evil2000.mqttiles;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Grid layout manager that picks its span count from a desired per-column pixel width
 * and the screen's long/short edge (depending on rotation). Recomputes once per layout pass
 * when the column width changes.
 */
public class GridAutofitLayoutManager extends GridLayoutManager {

    private final AppCompatActivity mActivity;
    private int     mColumnWidth;
    private int     mRotation;            // Surface.ROTATION_*
    private boolean mColumnWidthChanged = true;

    public GridAutofitLayoutManager(Context context, int columnWidthPx, int rotation) {
        super(context, 1);
        this.mRotation = rotation;
        this.mActivity = (AppCompatActivity) context;
        setColumnWidth(checkedColumnWidth(context, columnWidthPx));
    }

    public GridAutofitLayoutManager(Context context, int columnWidthPx, int orientation, boolean reverseLayout) {
        super(context, 1, orientation, reverseLayout);
        this.mActivity = (AppCompatActivity) context;
        setColumnWidth(checkedColumnWidth(context, columnWidthPx));
    }

    /** Falls back to 48dp when caller provides a non-positive width. */
    private int checkedColumnWidth(Context context, int width) {
        if (width > 0) return width;
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f,
                context.getResources().getDisplayMetrics());
    }

    public void setColumnWidth(int width) {
        if (width <= 0 || width == this.mColumnWidth) return;
        this.mColumnWidth = width;
        this.mColumnWidthChanged = true;
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        DisplayMetrics dm = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int h = dm.heightPixels, w = dm.widthPixels;

        if (mColumnWidthChanged && mColumnWidth > 0 && w > 0 && h > 0) {
            boolean portraitLike = (mRotation == 0 || mRotation == 2);
            int available = portraitLike
                    ? (w - getPaddingRight() - getPaddingLeft())
                    : (w - getPaddingTop()   - getPaddingBottom());
            int cols = Math.max(1, available / mColumnWidth);
            Log.d("AUTOFIT", String.format(
                    "ROTATION: %d, W: %d, H: %d, TOTAL_SPACE: %d, COLS: %d",
                    mRotation, w, h, available, cols));
            setSpanCount(cols);
            mColumnWidthChanged = false;
        }
        super.onLayoutChildren(recycler, state);
    }
}
