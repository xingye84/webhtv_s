package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchResultDownFocusTest {

    @Test
    public void validDownPressFocusesTheNextResultImmediately() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");
        int start = source.indexOf("private boolean onSearchDown(int position, int count)");
        int end = source.indexOf("boolean onLoadMore", start);

        assertTrue("result-row navigation must define its down policy", start >= 0 && end > start);
        String down = source.substring(start, end);
        assertFalse("a valid down press must not defer movement to the system focus search",
                down.contains("if (bottom) mScroller.checkMore();\n            return bottom;"));
        assertTrue("a valid down press must return true before a new row is focused",
                down.contains("focusSearchTarget(next);") && down.contains("return true;"));
        assertTrue("navigation must still load the next row before focusing it",
                down.contains("mSearchAdapter.ensureLoaded(next + 1, count * 3);"));
    }

    @Test
    public void deferredResultFocusLaysOutMissingRowsAndRejectsStaleRequests() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");
        int start = source.indexOf("private void focusSearchTarget(int position)");
        int end = source.indexOf("private boolean canFocusSearchResult(int position)");

        assertTrue("search results must define a deferred focus policy", start >= 0 && end > start);
        String focus = source.substring(start, end);
        assertTrue("the grid must try to lay out an adjacent row before explicitly scrolling",
                focus.indexOf("recycler.focusSearch(focused, View.FOCUS_DOWN);") >= 0
                        && focus.indexOf("recycler.scrollToPosition(position);") > focus.indexOf("recycler.focusSearch(focused, View.FOCUS_DOWN);"));
        assertTrue("focus must be requested after the fallback row layout completes",
                focus.contains("OneShotPreDrawListener.add(recycler,")
                        && focus.contains("if (laidOutTarget != null) laidOutTarget.requestFocus();"));
        assertTrue("an attached card should take focus without forcing a new row layout",
                focus.contains("target.requestFocus();") && !focus.contains("scrollToPositionWithOffset"));
        assertTrue("a later down press must invalidate a queued focus request",
                focus.contains("int generation = ++mSearchFocusGeneration;")
                        && focus.contains("if (generation != mSearchFocusGeneration"));
    }

    @Test
    public void resultGridUsesFocusScrollingAndSkipsBaselineMeasurement() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");
        String layout = read("app/src/leanback/res/layout/activity_collect.xml");
        assertTrue(source.contains("mBinding.recycler.setLayoutManager(new FocusGridLayoutManager(this, count));"));
        assertTrue(layout.contains("android:layout_weight=\"1\"\n        android:baselineAligned=\"false\"\n        android:orientation=\"horizontal\""));
    }

    @Test
    public void scrollCallbacksDeferResultLoadingUntilAfterLayout() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");
        int listenerStart = source.indexOf("public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy)");
        int listenerEnd = source.indexOf("public void onScrollStateChanged", listenerStart);
        assertTrue("scroll callback must defer all row preparation", listenerStart >= 0 && listenerEnd > listenerStart);
        String listener = source.substring(listenerStart, listenerEnd);
        assertTrue("onScrolled must post row preparation after the callback returns",
                listener.contains("recyclerView.post(() ->")
                        && listener.contains("ensureSearchRows(count, 2);")
                        && listener.contains("preloadNextRows(count);"));
        String stateListener = source.substring(listenerEnd, source.indexOf("mBinding.recycler.setAdapter", listenerEnd));
        assertTrue("stopping focus scrolling must defer pending insertions until outside layout",
                stateListener.indexOf("recyclerView.post(() ->") >= 0
                        && stateListener.indexOf("flushPendingItems();") > stateListener.indexOf("recyclerView.post(() ->"));
        int start = source.indexOf("private void preloadNextRows(int count)");
        int end = source.indexOf("@Override\n    public void onItemClick", start);

        assertTrue("search scroll helpers must be present", start >= 0 && end > start);
        String scroll = source.substring(start, end);
        assertTrue("scroll callbacks must route adapter changes through the layout-safe helper",
                scroll.contains("ensureSearchItemsLoaded(direction, count * 2);")
                        && scroll.contains("ensureSearchItemsLoaded(last + 1, count * rows);"));
        assertTrue("adapter insertion must be posted when RecyclerView is computing its layout",
                scroll.contains("if (mBinding.recycler.isComputingLayout())")
                        && scroll.contains("mBinding.recycler.post(() -> ensureSearchItemsLoaded(position, count));"));
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
