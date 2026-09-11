package xime.imaging;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

import xime.core.log.ILogger;

/**
 * Journal-less disk cache for Prism.
 * Entries are keyed by SHA-1(url) as flat files under {cacheDir}/prism/.
 * A best-effort size cap evicts least-recently-used files on every write.
 */
final class DiskCache {

    private static final long MAX_CACHE_BYTES = 100L * 1024 * 1024; // 100MB

    private final File cacheDir;
    private final ILogger logger;

    DiskCache(Context context, ILogger logger) {
        this.logger = logger;
        this.cacheDir = new File(context.getCacheDir(), "prism");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    byte[] get(String url) {
        File file = fileFor(url);
        if (!file.exists()) {
            return null;
        }
        file.setLastModified(System.currentTimeMillis());
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            int read;
            while (offset < data.length && (read = in.read(data, offset, data.length - offset)) != -1) {
                offset += read;
            }
            return data;
        } catch (IOException e) {
            logger.e(Prism.TAG, "DiskCache read failed for " + url, e);
            return null;
        }
    }

    void put(String url, byte[] data) {
        File file = fileFor(url);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        } catch (IOException e) {
            logger.e(Prism.TAG, "DiskCache write failed for " + url, e);
            return;
        }
        trimToSize();
    }

    void clear() {
        File[] files = cacheDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            file.delete();
        }
    }

    private File fileFor(String url) {
        return new File(cacheDir, hash(url));
    }

    private void trimToSize() {
        File[] files = cacheDir.listFiles();
        if (files == null) {
            return;
        }

        long total = 0;
        for (File file : files) {
            total += file.length();
        }
        if (total <= MAX_CACHE_BYTES) {
            return;
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (total <= MAX_CACHE_BYTES) {
                break;
            }
            total -= file.length();
            file.delete();
        }
    }

    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(input.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            return String.valueOf(input.hashCode());
        }
    }
}

