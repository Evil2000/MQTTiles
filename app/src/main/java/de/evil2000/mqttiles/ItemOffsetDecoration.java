package de.evil2000.mqttiles;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Uniform N-pixel padding around every item in a RecyclerView. */
public class ItemOffsetDecoration extends RecyclerView.ItemDecoration {

    private final int mItemOffset;

    public ItemOffsetDecoration(int itemOffsetPx) {
        this.mItemOffset = itemOffsetPx;
    }

    public ItemOffsetDecoration(@NonNull Context context, @DimenRes int dimenRes) {
        this(context.getResources().getDimensionPixelSize(dimenRes));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
        outRect.set(mItemOffset, mItemOffset, mItemOffset, mItemOffset);
    }
}
