package de.evil2000.mqttiles;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Variant of {@link GridLayoutManager} that (a) auto-picks the column count from a requested
 * per-column width and the RecyclerView's measured width, and (b) installs a spacer
 * {@link RecyclerView.ItemDecoration} so the leftover horizontal space is distributed evenly.
 */
public class AutoColumnGridLayoutManager extends GridLayoutManager {

    private int     requestedColumnWidth;
    private int     minColumnSpacing = 0;
    private int     rowSpacing       = 0;
    private boolean matchSpacing     = false;

    @Nullable
    private SpacerDecoration spacerDecoration;

    public AutoColumnGridLayoutManager(Context context, int requestedColumnWidthPx) {
        super(context, 1);
        this.requestedColumnWidth = requestedColumnWidthPx;
    }

    public void setColumnWidth(int width, RecyclerView recyclerView) {
        this.requestedColumnWidth = width;
        setSpanCount(determineColumnCount(width, recyclerView));
    }

    public void setMinColumnSpacing(int spacing, RecyclerView recyclerView) {
        this.minColumnSpacing = spacing;
        setSpanCount(determineColumnCount(requestedColumnWidth, recyclerView));
    }

    public void setRowSpacing(int spacing)              { this.rowSpacing = spacing; }
    public void setMatchRowAndColumnSpacing(boolean on) { this.matchSpacing = on; }

    /**
     * Find the largest N such that N columns of {@code columnWidth} fit inside the RecyclerView's
     * inner width with at least {@link #minColumnSpacing} horizontal slack per column.
     * Defers to a one-shot global layout listener if the RV has not been measured yet.
     */
    private int determineColumnCount(int columnWidth, RecyclerView rv) {
        if (rv.getWidth() == 0) {
            rv.getViewTreeObserver().addOnGlobalLayoutListener(new LayoutListener(rv));
            return 1;
        }
        int inner = rv.getWidth() - (rv.getPaddingLeft() + rv.getPaddingRight());
        int cols  = inner / columnWidth;
        int used  = cols * columnWidth;
        while ((inner - used) - (minColumnSpacing * cols) < 0) {
            cols--;
            used = cols * columnWidth;
        }
        if (spacerDecoration != null) {
            spacerDecoration.update(rv, columnWidth, cols);
        } else {
            spacerDecoration = new SpacerDecoration(rv, columnWidth, cols);
            rv.addItemDecoration(spacerDecoration);
        }
        return cols;
    }

    @Override
    public void onAttachedToWindow(RecyclerView rv) {
        super.onAttachedToWindow(rv);
        setColumnWidth(this.requestedColumnWidth, rv);
    }

    @Override
    public void onDetachedFromWindow(RecyclerView rv, RecyclerView.Recycler recycler) {
        super.onDetachedFromWindow(rv, recycler);
        if (spacerDecoration != null) rv.removeItemDecoration(spacerDecoration);
    }

    /** One-shot listener: once the RV has a real width, recompute spans and detach itself. */
    private final class LayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {
        private final RecyclerView rv;
        LayoutListener(RecyclerView rv) { this.rv = rv; }

        @Override
        public void onGlobalLayout() {
            rv.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            ((GridLayoutManager) rv.getLayoutManager())
                    .setSpanCount(determineColumnCount(requestedColumnWidth, rv));
        }
    }

    /** Pads each cell so N columns of {@code columnWidth} visually fill the row. */
    private final class SpacerDecoration extends RecyclerView.ItemDecoration {
        private int space;

        SpacerDecoration(RecyclerView rv, int columnWidth, int spanCount) {
            update(rv, columnWidth, spanCount);
        }

        void update(RecyclerView rv, int columnWidth, int spanCount) {
            int leftover = (rv.getWidth() - (rv.getPaddingLeft() + rv.getPaddingRight()))
                         - (columnWidth * spanCount);
            int slots = spanCount * 2;                 // each cell gets L + R spacer
            this.space = (leftover < slots) ? 0 : leftover / slots;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            outRect.left  = space;
            outRect.right = space;
            outRect.bottom = matchSpacing ? (space * 2) : rowSpacing;
        }
    }
}
