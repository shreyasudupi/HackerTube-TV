package com.liskovsoft.smartyoutubetv2.common.hackertube;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.reactivex.Observable;

/**
 * Client for the public hackertube.org JSON API (the same endpoints the website itself uses).
 * Everything returned here is a list of YouTube video ids, which SmartTube then plays ad-free.
 */
public class HackerTubeService {
    private static final String TAG = HackerTubeService.class.getSimpleName();
    public static final String BASE_URL = "https://hackertube.org";
    private static final int PAGE_SIZE = 36;
    /** HN Topics has ~150 rows, so each starts small (a TV row shows ~5 tiles) and pages in more on scroll. */
    private static final int TOPIC_PAGE_SIZE = 12;
    /**
     * Parallel requests when loading many rows, so 150 topic rows don't load one by one.
     * Kept low: hackertube.org rate-limits (HTTP 429) bursts, and 4 threads tripped it on a Fire TV.
     */
    private static final int ROW_LOAD_THREADS = 2;
    /** Retries after HTTP 429 (Too Many Requests), waiting Retry-After or a growing delay. */
    private static final int MAX_RETRIES_429 = 4;
    private static final int TIMEOUT_MS = 15_000;

    // Sidebar section ids. Kept below SidebarService.RESERVED_ID (100) and clear of MediaGroup.TYPE_* (0..22).
    public static final int SECTION_HOME = 90;
    public static final int SECTION_CATEGORIES = 91;
    public static final int SECTION_PLAYLISTS = 92;

    private static final int sSeed = new Random().nextInt(100_000_000);
    /** How many recently watched videos to send as "seen" so the feed doesn't repeat them. */
    private static final int MAX_SEEN = 150;
    /** Every video id that came from hackertube.org, so the player can tell HackerTube videos apart. */
    private static final Set<String> sKnownIds = Collections.synchronizedSet(new HashSet<String>());
    /** Logcat tag. Release builds keep android.util.Log, so the CI smoke test and adb debugging can read these lines. */
    private static final String LOG_TAG = "HackerTube";
    /** Small in-memory response cache so browsing back and forth doesn't re-hit hackertube.org. */
    private static final long CACHE_TTL_MS = 10 * 60 * 1000;
    private static final int CACHE_MAX_ENTRIES = 250;
    private static final Map<String, Object[]> sCache = new LinkedHashMap<String, Object[]>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object[]> eldest) {
            return size() > CACHE_MAX_ENTRIES;
        }
    };

    /** videoId -> "why HN recommends it" note, for the description screens. */
    private static final Map<String, String> sHnNotes = Collections.synchronizedMap(new HashMap<String, String>());

    public static boolean isHackerTubeVideo(String videoId) {
        return videoId != null && sKnownIds.contains(videoId);
    }

    /** The HN note for a video, or null if it isn't a HackerTube video. */
    public static String getHnNote(String videoId) {
        return videoId != null ? sHnNotes.get(videoId) : null;
    }

    /** Puts the HN note above a YouTube description (either may be missing). */
    public static String withHnNote(String videoId, String description) {
        String note = getHnNote(videoId);
        boolean hasDescription = description != null && !description.trim().isEmpty();
        if (note == null) {
            return description;
        }
        return hasDescription ? note + "\n\n\nFrom the YouTube description\n\n" + description : note;
    }

    // ---------- Observables used by the presenters ----------

    /** HackerTube home: personalised feed plus a few curated shelves, emitted row by row. */
    public static Observable<List<MediaGroup>> getHomeObserve(Context context) {
        final Context appContext = context.getApplicationContext();
        return RxHelper.create(emitter -> {
            String seen = enc(join(getRecentlyWatchedIds(appContext)));
            String[][] rows = {
                    {"Picked for you", "/api/feed?like=&dislike=&seen=" + seen + "&seed=" + sSeed + "&n=" + PAGE_SIZE},
                    {"Recently recommended on HN", "/api/list?sort=recent&n=" + PAGE_SIZE},
                    {"Hidden gems", "/api/list?gems=1&n=" + PAGE_SIZE},
                    {"Evergreen classics", "/api/list?evergreen=1&n=" + PAGE_SIZE},
                    {"Talks", "/api/list?type=talk&n=" + PAGE_SIZE},
                    {"Lectures", "/api/list?type=lecture&n=" + PAGE_SIZE},
                    {"Documentaries", "/api/list?type=documentary&n=" + PAGE_SIZE},
                    {"Over 1 hour", "/api/list?dur=xlong&n=" + PAGE_SIZE},
                    {"Under 4 minutes", "/api/list?dur=short&n=" + PAGE_SIZE},
            };
            emitRows(emitter, rows, true);
        });
    }

    /**
     * HN Topics: "New on HN" first, then one row per fine-grained category (about 150), grouped by
     * topic area. Each row is sorted by HN recommendation strength (the site's "Most recommended").
     */
    public static Observable<List<MediaGroup>> getCategoriesObserve() {
        return RxHelper.create(emitter -> {
            JSONArray areas = new JSONArray(get("/api/browse"));
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[] {"New on HN", "/api/list?sort=recent&n=" + PAGE_SIZE});
            for (int i = 0; i < areas.length(); i++) {
                JSONArray cats = areas.getJSONObject(i).optJSONArray("categories");
                if (cats == null) {
                    continue;
                }
                List<String[]> other = new ArrayList<>(); // the vague "(other)" bucket goes last in its area
                for (int j = 0; j < cats.length(); j++) {
                    JSONObject cat = cats.getJSONObject(j);
                    String id = cat.optString("id");
                    String[] row = {cat.optString("name"), "/api/list?cat=" + enc(id) + "&sort=hn&n=" + TOPIC_PAGE_SIZE};
                    (id.endsWith("-other") ? other : rows).add(row);
                }
                rows.addAll(other);
            }
            emitRowsParallel(emitter, rows);
        });
    }

    /** One row per playlist curated on hackertube.org, videos in the playlist's order. */
    public static Observable<List<MediaGroup>> getPlaylistsObserve() {
        return RxHelper.create(emitter -> {
            JSONArray playlists = new JSONArray(get("/api/playlists"));
            int emitted = 0;
            for (int i = 0; i < playlists.length() && !emitter.isDisposed(); i++) {
                JSONObject pl = playlists.getJSONObject(i);
                try {
                    JSONObject full = new JSONObject(get("/api/playlist?slug=" + enc(pl.optString("slug"))));
                    List<MediaItem> items = parseItems(full.optJSONArray("videos"));
                    if (!items.isEmpty()) {
                        emitter.onNext(Collections.singletonList(new HtMediaGroup(pl.optString("title"), items, null, 0)));
                        emitted++;
                        android.util.Log.i(LOG_TAG, "playlist loaded: " + pl.optString("slug") + " (" + items.size() + " videos)");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Playlist %s failed: %s", pl.optString("slug"), e.getMessage());
                    android.util.Log.w(LOG_TAG, "playlist failed: " + pl.optString("slug") + ": " + e.getMessage());
                }
            }
            finish(emitter, emitted);
        });
    }

    /** HackerTube search results as a single row (shown above the regular YouTube results). */
    public static Observable<List<MediaGroup>> getSearchObserve(String query) {
        return RxHelper.create(emitter -> {
            List<MediaGroup> result = new ArrayList<>();
            try {
                JSONObject json = new JSONObject(get("/api/search?q=" + enc(query)));
                List<MediaItem> items = parseItems(json.optJSONArray("results"));
                if (!items.isEmpty()) {
                    result.add(new HtMediaGroup("HackerTube: what HN recommends", items, null, 0));
                }
            } catch (Exception e) {
                Log.e(TAG, "Search failed: %s", e.getMessage());
                android.util.Log.w(LOG_TAG, "search failed: " + e.getMessage());
            }
            emitter.onNext(result); // empty list on failure, so YouTube results still show
            emitter.onComplete();
        });
    }

    /** "Up next from HackerTube": videos HN mentions alongside this one. Errors when there are none. */
    public static Observable<MediaGroup> getRelatedObserve(String videoId) {
        return RxHelper.create(emitter -> {
            List<MediaItem> items = parseItems(new JSONArray(get("/api/related?vid=" + enc(videoId) + "&n=20")));
            if (items.isEmpty()) {
                emitter.tryOnError(new IllegalStateException("HackerTube: no related videos"));
                return;
            }
            android.util.Log.i(LOG_TAG, "related loaded: " + videoId + " (" + items.size() + " videos)");
            emitter.onNext(new HtMediaGroup("Up next from HackerTube", items, null, 0));
            emitter.onComplete();
        });
    }

    /** True when the video is in local watch history (started or finished), used to skip it for autoplay. */
    public static boolean isWatched(Context context, String videoId) {
        try {
            return VideoStateService.instance(context).getByVideoId(videoId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Next page for a HackerTube row. Errors (rather than returning an empty page) when the list is exhausted. */
    public static Observable<MediaGroup> continueGroupObserve(HtMediaGroup group) {
        return RxHelper.create(emitter -> {
            if (!group.hasMore()) {
                emitter.tryOnError(new IllegalStateException("HackerTube: no more items"));
                return;
            }
            HtMediaGroup next = loadPage(group.getTitle(), group.getPagedPath(), group.getNextOffset());
            if (next.isEmpty()) {
                emitter.tryOnError(new IllegalStateException("HackerTube: no more items"));
                return;
            }
            emitter.onNext(next);
            emitter.onComplete();
        });
    }

    /**
     * Lets existing presenters stay one-line changes: HackerTube groups page from hackertube.org,
     * everything else keeps using YouTube's continuation.
     */
    public static Observable<MediaGroup> continueAny(ContentService contentService, MediaGroup group) {
        if (group instanceof HtMediaGroup) {
            return continueGroupObserve((HtMediaGroup) group);
        }
        return contentService.continueGroupObserve(group);
    }

    // ---------- internals ----------

    private static void emitRows(io.reactivex.ObservableEmitter<List<MediaGroup>> emitter, String[][] rows) {
        emitRows(emitter, rows, false);
    }

    private static void emitRows(io.reactivex.ObservableEmitter<List<MediaGroup>> emitter, String[][] rows, boolean warmUp) {
        int emitted = 0;
        for (String[] row : rows) {
            if (emitter.isDisposed()) {
                return;
            }
            try {
                HtMediaGroup group = loadPage(row[0], row[1], 0);
                if (!group.isEmpty()) {
                    emitter.onNext(Collections.singletonList(group));
                    if (warmUp && emitted == 0) {
                        HtPrefetch.warmUp(group.getMediaItems().get(0).getVideoId());
                    }
                    emitted++;
                    android.util.Log.i(LOG_TAG, "row loaded: " + row[0] + " (" + group.getMediaItems().size() + " videos)");
                }
            } catch (Exception e) {
                Log.e(TAG, "Row '%s' failed: %s", row[0], e.getMessage());
                android.util.Log.w(LOG_TAG, "row failed: " + row[0] + ": " + e.getMessage());
            }
        }
        finish(emitter, emitted);
    }

    /** Like emitRows, but fetches a few rows at a time and still emits them in order. */
    private static void emitRowsParallel(io.reactivex.ObservableEmitter<List<MediaGroup>> emitter, List<String[]> rows) {
        ExecutorService pool = Executors.newFixedThreadPool(ROW_LOAD_THREADS);
        try {
            List<Future<HtMediaGroup>> futures = new ArrayList<>();
            for (String[] row : rows) {
                futures.add(pool.submit(() -> emitter.isDisposed() ? null : loadPage(row[0], row[1], 0)));
            }
            int emitted = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (emitter.isDisposed()) {
                    return;
                }
                String title = rows.get(i)[0];
                try {
                    HtMediaGroup group = futures.get(i).get();
                    if (group != null && !group.isEmpty()) {
                        emitter.onNext(Collections.singletonList(group));
                        emitted++;
                        android.util.Log.i(LOG_TAG, "row loaded: " + title + " (" + group.getMediaItems().size() + " videos)");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                    Log.e(TAG, "Row '%s' failed: %s", title, cause.getMessage());
                    android.util.Log.w(LOG_TAG, "row failed: " + title + ": " + cause.getMessage());
                }
            }
            finish(emitter, emitted);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void finish(io.reactivex.ObservableEmitter<List<MediaGroup>> emitter, int emitted) {
        if (emitted == 0) {
            emitter.tryOnError(new IllegalStateException("Couldn't reach hackertube.org. Check the connection and try again."));
        } else {
            emitter.onComplete();
        }
    }

    private static HtMediaGroup loadPage(String title, String path, int offset) throws Exception {
        JSONArray array = new JSONArray(get(path + "&offset=" + offset));
        List<MediaItem> items = parseItems(array);
        boolean more = array.length() >= pageSizeOf(path);
        return new HtMediaGroup(title, items, more ? path : null, offset + array.length());
    }

    /** The n= of a list path, so paging knows a short page means the end. */
    private static int pageSizeOf(String path) {
        int i = path.indexOf("&n=");
        if (i < 0) {
            i = path.indexOf("?n=");
        }
        if (i >= 0) {
            int start = i + 3, end = start;
            while (end < path.length() && Character.isDigit(path.charAt(end))) {
                end++;
            }
            if (end > start) {
                return Integer.parseInt(path.substring(start, end));
            }
        }
        return PAGE_SIZE;
    }

    private static List<MediaItem> parseItems(JSONArray array) {
        List<MediaItem> items = new ArrayList<>();
        if (array == null) {
            return items;
        }
        for (int i = 0; i < array.length(); i++) {
            HtMediaItem item = HtMediaItem.from(array.optJSONObject(i));
            if (item != null) {
                items.add(item);
                sKnownIds.add(item.getVideoId());
                if (item.getHnNote() != null) {
                    sHnNotes.put(item.getVideoId(), item.getHnNote());
                }
            }
        }
        return items;
    }

    /** Most recent first, at most MAX_SEEN ids, from SmartTube's local watch history. */
    private static List<String> getRecentlyWatchedIds(Context context) {
        List<String> ids = new ArrayList<>();
        try {
            List<VideoStateService.State> states = new ArrayList<>(VideoStateService.instance(context).getStates());
            for (int i = states.size() - 1; i >= 0 && ids.size() < MAX_SEEN; i--) {
                VideoStateService.State state = states.get(i);
                String id = state != null && state.video != null ? state.video.videoId : null;
                if (id != null && !id.isEmpty() && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Can't read watch history: %s", e.getMessage());
        }
        return ids;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value != null ? value : "", "UTF-8");
    }

    private static String get(String path) throws Exception {
        long now = System.currentTimeMillis();
        synchronized (sCache) {
            Object[] hit = sCache.get(path);
            if (hit != null && now - (Long) hit[0] < CACHE_TTL_MS) {
                return (String) hit[1];
            }
        }
        long t0 = System.currentTimeMillis();
        String body = fetch(path);
        android.util.Log.i(LOG_TAG, "http: " + (System.currentTimeMillis() - t0) + "ms " + body.length() + "B " + (path.length() > 80 ? path.substring(0, 80) + "..." : path));
        synchronized (sCache) {
            sCache.put(path, new Object[] {now, body});
        }
        return body;
    }

    private static String fetch(String path) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try {
                return fetchOnce(path);
            } catch (RateLimitedException e) {
                if (attempt >= MAX_RETRIES_429) {
                    throw e;
                }
                long waitMs = e.retryAfterMs > 0 ? Math.min(e.retryAfterMs, 10_000) : 1000L * (attempt + 1);
                android.util.Log.i(LOG_TAG, "http: 429, retrying in " + waitMs + "ms " + path);
                Thread.sleep(waitMs);
            }
        }
    }

    private static class RateLimitedException extends IllegalStateException {
        final long retryAfterMs;

        RateLimitedException(String message, long retryAfterMs) {
            super(message);
            this.retryAfterMs = retryAfterMs;
        }
    }

    private static String fetchOnce(String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "HackerTubeTV (SmartTube fork; Android TV)");
        try {
            int code = conn.getResponseCode();
            if (code == 429) {
                long retryAfterMs = 0;
                try {
                    retryAfterMs = Long.parseLong(conn.getHeaderField("Retry-After").trim()) * 1000;
                } catch (Exception ignored) {
                    // missing or an HTTP date: fall back to the growing delay
                }
                throw new RateLimitedException("hackertube.org answered 429 for " + path, retryAfterMs);
            }
            if (code != 200) {
                throw new IllegalStateException("hackertube.org answered " + code + " for " + path);
            }
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
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
        } finally {
            conn.disconnect();
        }
    }
}
