package com.liskovsoft.smartyoutubetv2.common.hackertube;

import com.liskovsoft.mediaserviceinterfaces.data.FeedbackEndpoint;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * One HackerTube video mapped onto SmartTube's MediaItem so the stock cards, player,
 * SponsorBlock and ad-free stream handling all work unchanged (playback is by YouTube video id).
 */
public class HtMediaItem implements MediaItem {
    private final String mVideoId;
    private final String mTitle;
    private final String mChannel;
    private final long mDurationMs;
    private final long mPublishedMs;
    private final String mYear;
    private final String mSecondTitle;
    private final String mThumb;
    /** "Why Hacker News recommends it" text for the description screens, or null. */
    private final String mHnNote;

    private HtMediaItem(JSONObject json) {
        mVideoId = json.optString("vid", null);
        mTitle = json.optString("title", "");
        mChannel = json.optString("channel", "");
        mDurationMs = json.optLong("duration_s", 0) * 1_000L;
        mPublishedMs = parseDate(json.optString("published", null));
        mYear = json.optString("published", "").length() >= 4 ? json.optString("published").substring(0, 4) : null;
        String thumb = json.optString("thumbnail", null);
        mThumb = thumb != null && !thumb.isEmpty() ? thumb : "https://i.ytimg.com/vi/" + mVideoId + "/mqdefault.jpg";

        StringBuilder second = new StringBuilder(mChannel);
        JSONObject hn = json.optJSONObject("hn");
        int recommenders = hn != null ? hn.optInt("recommenders", 0) : 0;
        if (recommenders > 0) {
            append(second, recommenders + (recommenders == 1 ? " HN recommender" : " HN recommenders"));
        }
        long views = json.optLong("views", 0);
        if (views > 0) {
            append(second, compact(views) + " views");
        }
        if (mYear != null) {
            append(second, mYear);
        }
        mSecondTitle = second.toString();
        mHnNote = buildHnNote(json, hn);
    }

    private static String buildHnNote(JSONObject json, JSONObject hn) {
        String why = json.optString("why_hn", "").trim();
        String summary = json.optString("summary", "").trim();
        if (why.isEmpty() && summary.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (!why.isEmpty()) {
            sb.append("Why Hacker News recommends it\n").append(why);
        }
        if (!summary.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(summary);
        }
        if (hn != null) {
            int recommenders = hn.optInt("recommenders", 0);
            int mentions = hn.optInt("mentions", 0);
            String first = hn.optString("first", ""), last = hn.optString("last", "");
            if (recommenders > 0 || mentions > 0) {
                sb.append("\n\n");
                if (recommenders > 0) {
                    sb.append("Recommended by ").append(recommenders).append(recommenders == 1 ? " HN commenter" : " HN commenters");
                } else {
                    sb.append("Mentioned ").append(mentions).append(mentions == 1 ? " time" : " times").append(" on HN");
                }
                if (!first.isEmpty() && !last.isEmpty()) {
                    sb.append(first.equals(last) ? " (" + first + ")" : " (" + first + "-" + last + ")");
                }
                sb.append(".");
            }
        }
        sb.append("\nvia hackertube.org");
        return sb.toString();
    }

    public String getHnNote() {
        return mHnNote;
    }

    public static HtMediaItem from(JSONObject json) {
        if (json == null || json.optString("vid", "").isEmpty()) {
            return null;
        }
        return new HtMediaItem(json);
    }

    private static void append(StringBuilder sb, String part) {
        if (sb.length() > 0) {
            sb.append(" • ");
        }
        sb.append(part);
    }

    private static String compact(long n) {
        if (n >= 1_000_000_000L) return trim(n / 1e9) + "B";
        if (n >= 1_000_000L) return trim(n / 1e6) + "M";
        if (n >= 1_000L) return trim(n / 1e3) + "K";
        return NumberFormat.getInstance(Locale.US).format(n);
    }

    private static String trim(double v) {
        return v >= 10 ? String.valueOf((long) v) : String.format(Locale.US, "%.1f", v).replace(".0", "");
    }

    private static long parseDate(String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0;
        }
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = fmt.parse(iso);
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String formatDuration(long ms) {
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec) : String.format(Locale.US, "%d:%02d", m, sec);
    }

    @Override public int getType() { return TYPE_VIDEO; }
    @Override public boolean isLive() { return false; }
    @Override public boolean isUpcoming() { return false; }
    @Override public boolean isShorts() { return false; }
    @Override public int getPercentWatched() { return -1; }
    @Override public int getStartTimeSeconds() { return 0; }
    @Override public String getAuthor() { return mChannel; }
    @Override public String getFeedbackToken() { return null; }
    @Override public String getFeedbackToken2() { return null; }
    @Override public FeedbackEndpoint getFeedbackEndpoint() { return null; }
    @Override public String getPlaylistId() { return null; }
    @Override public int getPlaylistIndex() { return -1; }
    @Override public String getParams() { return null; }
    @Override public String getReloadPageKey() { return null; }
    @Override public boolean hasNewContent() { return false; }
    @Override public int getId() { return mVideoId != null ? mVideoId.hashCode() : 0; }
    @Override public String getTitle() { return mTitle; }
    @Override public CharSequence getSecondTitle() { return mSecondTitle; }
    @Override public String getVideoId() { return mVideoId; }
    @Override public String getContentType() { return "video/mp4"; }
    @Override public long getDurationMs() { return mDurationMs; }
    @Override public String getBadgeText() { return mDurationMs > 0 ? formatDuration(mDurationMs) : null; }
    @Override public String getProductionDate() { return mYear; }
    @Override public long getPublishedDate() { return mPublishedMs; }
    @Override public String getCardImageUrl() { return mThumb; }
    @Override public String getBackgroundImageUrl() { return "https://i.ytimg.com/vi/" + mVideoId + "/hqdefault.jpg"; }
    @Override public int getWidth() { return 1280; }
    @Override public int getHeight() { return 720; }
    @Override public String getChannelId() { return null; }
    @Override public String getVideoPreviewUrl() { return null; }
    @Override public String getAudioChannelConfig() { return null; }
    @Override public String getPurchasePrice() { return null; }
    @Override public String getRentalPrice() { return null; }
    @Override public int getRatingStyle() { return 0; }
    @Override public double getRatingScore() { return 0; }
    @Override public boolean isMovie() { return false; }
    @Override public boolean hasUploads() { return false; }
    @Override public String getClickTrackingParams() { return null; }
    @Override public String getSearchQuery() { return null; }
}
