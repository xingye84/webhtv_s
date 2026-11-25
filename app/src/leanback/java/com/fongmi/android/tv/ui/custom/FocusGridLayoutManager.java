package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Keeps focused search cards visible with the smallest vertical scroll. */
public class FocusGridLayoutManager extends GridLayoutManager {

    public FocusGridLayoutManager(Context context, int spanCount) {
        super(context, spanCount);
    }

    @Override
    public boolean requestChildRectangleOnScreen(RecyclerView parent, View child, Rect rect,
                                                 boolean immediate, boolean focusedChildVisible) {
        if (focusedChildVisible || !child.hasFocus()) {
            return super.requestChildRectangleOnScreen(parent, child, rect, immediate, focusedChildVisible);
        }
        // Reveal the whole card even when its marquee title asks to show only its own bounds.
        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
        int top = getDecoratedTop(child) - params.topMargin - getPaddingTop();
        int bottom = getDecoratedBottom(child) + params.bottomMargin - (getHeight() - getPaddingBottom());
        int dy = top < 0 ? top : Math.min(top, Math.max(0, bottom));
        if (dy == 0) {
            if (parent.getScrollState() == RecyclerView.SCROLL_STATE_SETTLING) parent.stopScroll();
            return false;
        }
        // Retarget the animation without an intermediate idle callback that would flush results.
        parent.smoothScrollBy(0, dy, null, 180);
        return true;
    }
}
