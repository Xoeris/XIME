package xime.media.external;

import android.content.Context;
import android.media.session.MediaSession;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import xime.media.PlaybackSnapshot;

/**
 * Isolates Android framework token types from the Xoeris PlaybackSession API.
 */
class PlaybackCaptureBridge {
    private final MediaControllerCompat controller;

    PlaybackCaptureBridge(Context context, MediaSession.Token token) throws android.os.RemoteException {
        // We isolate the conversion and token holding here.
        this.controller = new MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(token));
    }

    void release() {
        // Unregister callbacks
    }

    PlaybackSnapshot getCurrentSnapshot() {
        // Map controller.getPlaybackState() and controller.getMetadata() to PlaybackSnapshot
        return new PlaybackSnapshot.Builder()
                .setPlaying(false) // placeholder
                .setPositionMs(0)
                .setDurationMs(0)
                .build();
    }

    void play() {
        controller.getTransportControls().play();
    }

    void pause() {
        controller.getTransportControls().pause();
    }

    void next() {
        controller.getTransportControls().skipToNext();
    }

    void previous() {
        controller.getTransportControls().skipToPrevious();
    }

    void seekTo(long positionMs) {
        controller.getTransportControls().seekTo(positionMs);
    }

    boolean supportsSeek() {
        return true; 
    }

    boolean supportsNextPrevious() {
        return true; 
    }
}

