package com.liskovsoft.smartyoutubetv2.common.hackertube;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;

/**
 * Logs how long each step of starting a video takes (see {@link HtTiming}).
 * Registered first in PlaybackPresenter so it sees each event before the other controllers.
 */
public class HtTimingController extends BasePlayerController {
    private boolean mStarted;
    private int mRebuffers;

    @Override
    public void onNewVideo(Video item) {
        mStarted = false;
        mRebuffers = 0;
        HtTiming.start(item != null ? item.videoId : null);
    }

    @Override
    public void onEngineInitialized() {
        HtTiming.mark("player engine ready");
    }

    @Override
    public void onSourceChanged(Video item) {
        HtTiming.mark("stream handed to the player");
    }

    @Override
    public void onVideoLoaded(Video item) {
        HtTiming.mark("first video format ready");
    }

    @Override
    public void onBuffering() {
        if (mStarted) {
            mRebuffers++;
            HtTiming.mark("re-buffering #" + mRebuffers + " during playback");
        } else {
            HtTiming.mark("buffering");
        }
    }

    @Override
    public void onPlay() {
        if (!mStarted) {
            mStarted = true;
            HtTiming.mark("PLAYING (start-up complete)");
        }
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        HtTiming.mark("player error type=" + type + ": " + (error != null ? error.getMessage() : "unknown"));
    }

    @Override
    public void onEngineReleased() {
        HtTiming.stop();
    }
}
