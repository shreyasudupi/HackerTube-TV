package com.liskovsoft.smartyoutubetv2.common.hackertube;

import android.os.SystemClock;

/**
 * Video start-up timing, logged under the "HackerTube" logcat tag as
 *   timing: +1234ms <stage> [videoId]
 * where the time is measured from the moment a new video was opened.
 * Read it with: adb logcat -s HackerTube
 */
public final class HtTiming {
    private static final String TAG = "HackerTube";
    private static long sStartMs;
    private static long sLastMs;
    private static String sVideoId;

    private HtTiming() {
    }

    public static synchronized void start(String videoId) {
        sStartMs = SystemClock.elapsedRealtime();
        sLastMs = sStartMs;
        sVideoId = videoId;
        android.util.Log.i(TAG, "timing: +0ms open video [" + videoId + "]");
    }

    /** Logs a stage with total time since start and the time since the previous stage. */
    public static synchronized void mark(String stage) {
        if (sStartMs == 0) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        android.util.Log.i(TAG, "timing: +" + (now - sStartMs) + "ms (step " + (now - sLastMs) + "ms) " + stage + " [" + sVideoId + "]");
        sLastMs = now;
    }

    public static synchronized boolean isRunning() {
        return sStartMs != 0;
    }

    public static synchronized void stop() {
        sStartMs = 0;
    }
}
