package xime.media.external;

import android.content.Context;
import android.media.session.MediaSession;

/**
 * Coordinates discovery of external sources.
 * It observes active external sources (e.g. via NotificationListenerService)
 * and constructs/destroys ExternalPlaybackSession instances as needed.
 */
public class ExternalPlaybackSessionManager {
    private final Context context;

    public ExternalPlaybackSessionManager(Context context) {
        this.context = context;
    }

    /**
     * Creates a new session from a detected token.
     */
    public ExternalPlaybackSession createSessionFromToken(MediaSession.Token token) {
        try {
            PlaybackCaptureBridge bridge = new PlaybackCaptureBridge(context, token);
            return new ExternalPlaybackSession(bridge);
        } catch (android.os.RemoteException e) {
            return null;
        }
    }
}

