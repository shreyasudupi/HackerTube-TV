package com.liskovsoft.smartyoutubetv2.common.hackertube;

import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;

/**
 * Makes HackerTube videos start faster.
 *
 * Starting a video needs its stream info from YouTube, and the first one after launch also pays a
 * one-time warm-up (YouTube's player code is downloaded and run to unlock the streams), about 4 s
 * on a Fire TV Stick 4K Max. SmartTube keeps the most recently fetched stream info, so fetching it
 * while the user is still looking at a video card means pressing play can skip that step.
 *
 *  - warmUp(): once per app run, as soon as the HackerTube home rows arrive
 *  - onFocus(): when the user rests on a HackerTube video card for ~1 s
 */
public final class HtPrefetch {
    private static final long FOCUS_DELAY_MS = 900;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static boolean sWarmedUp;
    private static String sLastPrefetched;
    private static Runnable sPending;

    private HtPrefetch() {
    }

    /** One-time warm-up with the first HackerTube video, so the first play doesn't pay for it. */
    public static void warmUp(final String videoId) {
        if (sWarmedUp || videoId == null) {
            return;
        }
        sWarmedUp = true;
        sHandler.post(() -> prefetch(videoId, "warm-up"));
    }

    /** Called when a video card gets focus; prefetches if the user stays on it. */
    public static void onFocus(Video item) {
        if (sPending != null) {
            sHandler.removeCallbacks(sPending);
            sPending = null;
        }
        if (item == null || item.videoId == null || !HackerTubeService.isHackerTubeVideo(item.videoId)) {
            return;
        }
        final String videoId = item.videoId;
        if (videoId.equals(sLastPrefetched)) {
            return;
        }
        sPending = () -> {
            sPending = null;
            sWarmedUp = true;
            prefetch(videoId, "focus");
        };
        sHandler.postDelayed(sPending, FOCUS_DELAY_MS);
    }

    private static void prefetch(final String videoId, final String reason) {
        sLastPrefetched = videoId;
        final long t0 = System.currentTimeMillis();
        Video video = new Video();
        video.videoId = videoId;
        android.util.Log.i("HackerTube", "prefetch (" + reason + "): stream info for " + videoId);
        MediaServiceManager.instance().loadFormatInfo(video, formatInfo ->
                android.util.Log.i("HackerTube", "prefetch (" + reason + "): ready for " + videoId
                        + " in " + (System.currentTimeMillis() - t0) + "ms"));
    }
}
