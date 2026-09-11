package xime.media;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

public class Session {
    private static final String TAG = "Session";
    private final MediaSessionCompat mediaSession;

    public interface Callback {
        void onPlay();
        void onPause();
        void onSkipToNext();
        void onSkipToPrevious();
        void onSeekTo(long pos);
        void onStop();
    }

    public Session(Context context, String tag, Callback callback) {
        this.mediaSession = new MediaSessionCompat(context, tag);
        
        this.mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        this.mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { callback.onPlay(); }
            @Override public void onPause() { callback.onPause(); }
            @Override public void onSkipToNext() { callback.onSkipToNext(); }
            @Override public void onSkipToPrevious() { callback.onSkipToPrevious(); }
            @Override public void onSeekTo(long pos) { callback.onSeekTo(pos); }
            @Override public void onStop() { callback.onStop(); }
        });

        this.mediaSession.setActive(true);
    }

    public void updatePlaybackState(boolean isPlaying, long position) {
        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SEEK_TO |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_STOP
                )
                .setState(state, position, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    public void updateMetadata(Metadata metadata) {
        updateMetadata(metadata, null);
    }

    public void updateMetadata(Metadata metadata, Bitmap artwork) {
        if (metadata == null) return;

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, metadata.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, metadata.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, metadata.getAlbum())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, metadata.getDurationMs());

        if (artwork != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork);
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork);
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artwork);
        } else if (metadata.getArtBytes() != null) {
            try {
                Bitmap bitmap = BitmapFactory.decodeByteArray(metadata.getArtBytes(), 0, metadata.getArtBytes().length);
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap);
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap);
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap);
            } catch (Exception e) {
                Log.e(TAG, "Failed to decode artwork bytes", e);
            }
        }

        mediaSession.setMetadata(builder.build());
    }

    public void setSessionActivity(PendingIntent intent) {
        mediaSession.setSessionActivity(intent);
    }

    public MediaSessionCompat.Token getSessionToken() {
        return mediaSession.getSessionToken();
    }

    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    public void release() {
        mediaSession.setActive(false);
        mediaSession.release();
    }
}
