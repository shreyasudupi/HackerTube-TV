package com.liskovsoft.smartyoutubetv2.common.hackertube;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.FeedbackEndpoint;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;

/**
 * "Tristan's Tier List": Tristan Hume's ranking of YouTube channels
 * (https://thume.ca/2026/11/01/youtube-tier-list-2026/), one row per tier.
 * The list ships inside the app (res/raw/ht_tier_list.json, made by scripts/update-tier-list.py),
 * so this section needs no server. Each tile is the video Tristan picked for a channel and OK plays it;
 * channels without a pick open the channel page instead.
 */
public final class HtTierList {
    private static final String LOG_TAG = "HackerTube";

    private HtTierList() {
    }

    public static Observable<List<MediaGroup>> getTierListObserve(Context context) {
        final Context appContext = context.getApplicationContext();
        return RxHelper.create(emitter -> {
            JSONArray tiers = new JSONObject(readRaw(appContext)).getJSONArray("tiers");
            int emitted = 0;
            for (int i = 0; i < tiers.length() && !emitter.isDisposed(); i++) {
                JSONObject tier = tiers.getJSONObject(i);
                JSONArray channels = tier.optJSONArray("channels");
                List<MediaItem> items = new ArrayList<>();
                for (int j = 0; channels != null && j < channels.length(); j++) {
                    ChannelItem item = ChannelItem.from(channels.optJSONObject(j));
                    if (item != null) {
                        items.add(item);
                    }
                }
                if (!items.isEmpty()) {
                    String title = tier.optString("tier") + " tier: " + tier.optString("name");
                    emitter.onNext(Collections.singletonList(new HtMediaGroup(title, items, null, 0)));
                    emitted++;
                    android.util.Log.i(LOG_TAG, "tier loaded: " + tier.optString("tier") + " (" + items.size() + " channels)");
                }
            }
            if (emitted == 0) {
                emitter.tryOnError(new IllegalStateException("Tristan's Tier List is empty"));
            } else {
                emitter.onComplete();
            }
        });
    }

    private static String readRaw(Context context) throws Exception {
        InputStream in = context.getResources().openRawResource(R.raw.ht_tier_list);
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        try {
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        } finally {
            reader.close();
        }
        return sb.toString();
    }

    /**
     * One channel as a SmartTube card. With a picked video it's a video tile (OK plays it; the long-press
     * menu can still open the channel). Without one it's a channel tile; the two channels listed by a
     * custom URL have no id either, so those fall back to a YouTube search by name.
     */
    static final class ChannelItem implements MediaItem {
        private final String mName;
        private final String mChannelId;
        private final String mVideoId;
        private final String mTitle;
        private final String mThumb;
        private final String mSecondTitle;

        private ChannelItem(String name, String channelId, String videoId, String videoTitle) {
            mName = name;
            mChannelId = channelId;
            mVideoId = videoId;
            mTitle = videoId != null && videoTitle != null ? videoTitle : name;
            mThumb = videoId != null ? "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg" : null;
            mSecondTitle = videoId != null ? name
                    : channelId != null ? "Open the channel" : "Search YouTube for this channel";
        }

        static ChannelItem from(JSONObject json) {
            if (json == null || json.optString("name", "").isEmpty()) {
                return null;
            }
            return new ChannelItem(json.optString("name"), opt(json, "channelId"), opt(json, "videoId"), opt(json, "videoTitle"));
        }

        private static String opt(JSONObject json, String key) {
            String value = json.isNull(key) ? null : json.optString(key, null);
            return value != null && !value.isEmpty() ? value : null;
        }

        @Override public int getType() { return mVideoId != null ? TYPE_VIDEO : TYPE_CHANNEL; }
        @Override public boolean isLive() { return false; }
        @Override public boolean isUpcoming() { return false; }
        @Override public boolean isShorts() { return false; }
        @Override public int getPercentWatched() { return -1; }
        @Override public int getStartTimeSeconds() { return 0; }
        @Override public String getAuthor() { return mName; }
        @Override public String getFeedbackToken() { return null; }
        @Override public String getFeedbackToken2() { return null; }
        @Override public FeedbackEndpoint getFeedbackEndpoint() { return null; }
        @Override public String getPlaylistId() { return null; }
        @Override public int getPlaylistIndex() { return -1; }
        @Override public String getParams() { return null; }
        @Override public String getReloadPageKey() { return null; }
        @Override public boolean hasNewContent() { return false; }
        @Override public int getId() { return (mVideoId != null ? mVideoId : mChannelId != null ? mChannelId : mName).hashCode(); }
        @Override public String getTitle() { return mTitle; }
        @Override public CharSequence getSecondTitle() { return mSecondTitle; }
        @Override public String getVideoId() { return mVideoId; }
        @Override public String getContentType() { return mVideoId != null ? "video/mp4" : null; }
        @Override public long getDurationMs() { return 0; }
        @Override public String getBadgeText() { return null; }
        @Override public String getProductionDate() { return null; }
        @Override public long getPublishedDate() { return 0; }
        @Override public String getCardImageUrl() { return mThumb; }
        @Override public String getBackgroundImageUrl() { return mVideoId != null ? "https://i.ytimg.com/vi/" + mVideoId + "/hqdefault.jpg" : null; }
        @Override public int getWidth() { return 1280; }
        @Override public int getHeight() { return 720; }
        @Override public String getChannelId() { return mChannelId; }
        @Override public String getVideoPreviewUrl() { return null; }
        @Override public String getAudioChannelConfig() { return null; }
        @Override public String getPurchasePrice() { return null; }
        @Override public String getRentalPrice() { return null; }
        @Override public int getRatingStyle() { return 0; }
        @Override public double getRatingScore() { return 0; }
        @Override public boolean isMovie() { return false; }
        @Override public boolean hasUploads() { return false; }
        @Override public String getClickTrackingParams() { return null; }
        @Override public String getSearchQuery() { return mVideoId == null && mChannelId == null ? mName : null; }
    }
}
