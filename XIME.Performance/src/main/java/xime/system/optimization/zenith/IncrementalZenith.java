package xime.system.optimization.zenith;

import java.util.List;

public final class IncrementalZenith {
    private IncrementalZenith() {}

    public interface ChunkCallback<T> {
        void onChunkProcessed(List<T> chunk, boolean isFinished);
    }

    public static <T> void processIncrementally(
            final List<T> source, 
            final int chunkSize, 
            final ChunkCallback<T> callback
    ) {
        if (source == null || source.isEmpty() || chunkSize <= 0 || callback == null) {
            if (callback != null) callback.onChunkProcessed(new java.util.ArrayList<>(), true);
            return;
        }

        BackgroundZenith.execute(new Runnable() {
            private int currentOffset = 0;

            @Override
            public void run() {
                if (currentOffset >= source.size()) {
                    BackgroundZenith.runOnUiThread(() -> callback.onChunkProcessed(new java.util.ArrayList<>(), true));
                    return;
                }

                int end = Math.min(currentOffset + chunkSize, source.size());
                final List<T> chunk = new java.util.ArrayList<>(source.subList(currentOffset, end));
                currentOffset = end;
                final boolean isFinished = currentOffset >= source.size();

                BackgroundZenith.runOnUiThread(() -> {
                    callback.onChunkProcessed(chunk, isFinished);
                    if (!isFinished) {
                        BackgroundZenith.execute(this);
                    }
                });
            }
        });
    }
}
