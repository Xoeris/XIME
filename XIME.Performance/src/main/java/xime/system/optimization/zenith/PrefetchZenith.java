package xime.system.optimization.zenith;

import android.content.Context;
import xime.imaging.Prism;
import java.util.List;
import xime.media.Metadata;

public final class PrefetchZenith {
    private PrefetchZenith() {}

    public static void prefetchImages(Context context, List<Metadata> items, int startIndex, int count) {
        if (context == null || items == null || items.isEmpty()) return;
        int end = Math.min(startIndex + count, items.size());
        for (int i = Math.max(0, startIndex); i < end; i++) {
            Metadata item = items.get(i);
            if (item != null) {
                Object artSource = item.getArtUri() != null ? item.getArtUri() : item.getArtBytes();
                if (artSource != null) {
                    Prism.with(context.getApplicationContext()).load(artSource).preload();
                }
            }
        }
    }
}
