package com.liskovsoft.smartyoutubetv2.common.hackertube;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;

import java.util.List;

/**
 * A row/grid of HackerTube videos. Paged lists keep the API path and the next offset,
 * so SmartTube's normal "load more when scrolling" flow fetches the next page from hackertube.org.
 */
public class HtMediaGroup implements MediaGroup {
    private final String mTitle;
    private final List<MediaItem> mItems;
    /** API path without offset, e.g. "/api/list?sort=recent&n=36". Null when the list can't be paged. */
    private final String mPagedPath;
    private final int mNextOffset;

    public HtMediaGroup(String title, List<MediaItem> items, @Nullable String pagedPath, int nextOffset) {
        mTitle = title;
        mItems = items;
        mPagedPath = pagedPath;
        mNextOffset = nextOffset;
    }

    public String getPagedPath() {
        return mPagedPath;
    }

    public int getNextOffset() {
        return mNextOffset;
    }

    public boolean hasMore() {
        return mPagedPath != null;
    }

    @Override public int getType() { return TYPE_UNDEFINED; }
    @Nullable @Override public List<MediaItem> getMediaItems() { return mItems; }
    @Override public String getTitle() { return mTitle; }
    @Override public String getChannelId() { return null; }
    @Override public String getParams() { return null; }
    @Override public String getReloadPageKey() { return null; }
    @Override public String getNextPageKey() { return mPagedPath != null ? mPagedPath + "&offset=" + mNextOffset : null; }
    @Override public String getChannelUrl() { return null; }
    @Override public boolean isEmpty() { return mItems == null || mItems.isEmpty(); }
}
