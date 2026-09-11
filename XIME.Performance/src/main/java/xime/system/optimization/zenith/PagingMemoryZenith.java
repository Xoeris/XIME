package xime.system.optimization.zenith;

import java.util.ArrayList;
import java.util.List;

public final class PagingMemoryZenith {
    private PagingMemoryZenith() {}

    public static <T> List<T> getPage(List<T> source, int page, int pageSize) {
        if (source == null || source.isEmpty() || page < 0 || pageSize <= 0) {
            return new ArrayList<>();
        }
        int fromIndex = page * pageSize;
        if (fromIndex >= source.size()) {
            return new ArrayList<>();
        }
        int toIndex = Math.min(fromIndex + pageSize, source.size());
        return new ArrayList<>(source.subList(fromIndex, toIndex));
    }

    public static int getTotalPages(int totalCount, int pageSize) {
        if (pageSize <= 0 || totalCount <= 0) return 0;
        return (int) Math.ceil((double) totalCount / pageSize);
    }
}
