package xime.imaging;

/** Builds a memory-cache key that encodes url + transform + target size. */
final class CacheKeys {

    private CacheKeys() {
    }

    static String build(String url, Transformation transformation, int width, int height) {
        StringBuilder builder = new StringBuilder(url);
        if (transformation != null) {
            builder.append("#t=").append(transformation.key());
        }
        if (width > 0 && height > 0) {
            builder.append("#s=").append(width).append('x').append(height);
        }
        return builder.toString();
    }
}

