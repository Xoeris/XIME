package xime.system.optimization.zenith;

import java.util.ArrayList;
import java.util.List;
import xime.media.entity.TrackEntity;
import xime.media.Metadata;

public final class CoreZenith {
    private static final MemoryZenith<Long, Metadata> trackCache = new MemoryZenith<>(1000);

    private CoreZenith() {}

    public static MemoryZenith<Long, Metadata> getCache() {
        return trackCache;
    }

    public static Metadata getOrConvert(TrackEntity track) {
        if (track == null) return null;
        Metadata cached = trackCache.get(track.getId());
        if (cached == null) {
            cached = DataZenith.convertTrack(track);
            if (cached != null) {
                trackCache.put(track.getId(), cached);
            }
        }
        return cached;
    }

    public static void processTracks(
            final List<TrackEntity> source, 
            final String languageFilter, 
            final OnProcessedListener listener
    ) {
        BackgroundZenith.execute(() -> {
            final List<TrackEntity> filteredTracks = new ArrayList<>();
            final List<Metadata> metadataList = new ArrayList<>();
            long totalMs = 0;
            long totalBytes = 0;

            for (int i = 0; i < source.size(); i++) {
                TrackEntity t = source.get(i);
                if (languageFilter.equals("All") || languageFilter.equals(t.getLanguage())) {
                    Metadata converted = getOrConvert(t);
                    if (converted != null) {
                        filteredTracks.add(t);
                        metadataList.add(converted);
                        totalMs += t.getDuration();
                        totalBytes += t.getSize();
                    }
                }
            }

            final long finalMs = totalMs;
            final long finalBytes = totalBytes;
            BackgroundZenith.runOnUiThread(() -> {
                if (listener != null) {
                    listener.onProcessed(filteredTracks, metadataList, finalMs, finalBytes);
                }
            });
        });
    }

    public interface OnProcessedListener {
        void onProcessed(List<TrackEntity> tracks, List<Metadata> metadata, long totalDuration, long totalSize);
    }
}
