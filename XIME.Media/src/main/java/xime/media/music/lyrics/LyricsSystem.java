package xime.media.music.lyrics;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import xime.media.Metadata;

public class LyricsSystem {

    public static String getLyrics(Context context, Metadata metadata) {
        return getLyrics(context, metadata, false);
    }

    public static String getLyrics(Context context, Metadata metadata, boolean forceDiskReload) {
        if (metadata == null) return null;

        String trackPath = metadata.getPath();

        // 1. HIGH PRIORITY: Try external sidecar .lrc/.txt files (Source B)
        // Users often use these for high-quality or hand-synced corrections.
        try {
            if (trackPath != null && !trackPath.isEmpty()) {
                int lastDot = trackPath.lastIndexOf('.');
                if (lastDot > 0) {
                    String base = trackPath.substring(0, lastDot);

                    File lrcFile = new File(base + ".lrc");
                    if (lrcFile.exists() && lrcFile.length() > 0) return readFile(lrcFile);

                    File txtFile = new File(base + ".txt");
                    if (txtFile.exists() && txtFile.length() > 0) return readFile(txtFile);
                }
            }
        } catch (Exception ignored) {}

        // 2. MEDIUM PRIORITY: Try embedded metadata tags (Source A)
        // Check USLT frame in ID3 header.
        if (trackPath != null && !trackPath.startsWith("content://")) {
            String binaryLyrics = extractLyricsBinary(trackPath);
            if (binaryLyrics != null && !binaryLyrics.isEmpty()) return binaryLyrics;
        }

        // 3. LOW PRIORITY: Try pre-loaded/cached metadata or MediaStore fallback
        if (!forceDiskReload) {
            String lyrics = metadata.getLyrics();
            if (lyrics != null && !lyrics.isEmpty() && lyrics.length() > 10) return lyrics;
        }

        try {
            android.net.Uri uri = metadata.getMediaUri();
            if (uri != null) {
                Metadata deepMeta = Metadata.fromUri(context, uri);
                if (deepMeta != null && deepMeta.getLyrics() != null && !deepMeta.getLyrics().isEmpty()) {
                    return deepMeta.getLyrics();
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static String extractLyricsBinary(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] header = new byte[10];
            if (fis.read(header) != 10) return null;

            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') return null;

            int version = header[3];
            int tagSize = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14) |
                          ((header[8] & 0x7F) << 7) | (header[9] & 0x7F);

            int bytesProcessed = 0;
            while (bytesProcessed < tagSize) {
                byte[] frameHeader = new byte[10];
                if (fis.read(frameHeader) != 10) break;
                bytesProcessed += 10;

                if (frameHeader[0] == 0) break; // Padding

                String frameId = new String(frameHeader, 0, 4, StandardCharsets.ISO_8859_1);
                int frameSize;
                if (version == 4) {
                    frameSize = ((frameHeader[4] & 0x7F) << 21) | ((frameHeader[5] & 0x7F) << 14) |
                                ((frameHeader[6] & 0x7F) << 7) | (frameHeader[7] & 0x7F);
                } else {
                    frameSize = ((frameHeader[4] & 0xFF) << 24) | ((frameHeader[5] & 0xFF) << 16) |
                                ((frameHeader[6] & 0xFF) << 8) | (frameHeader[7] & 0xFF);
                }

                if (frameSize <= 0 || frameSize > (tagSize - bytesProcessed)) break;

                if ("USLT".equals(frameId)) {
                    byte[] payload = new byte[frameSize];
                    if (fis.read(payload) == frameSize) {
                        return parseUsltPayload(payload);
                    }
                    break;
                } else {
                    long skipped = fis.skip(frameSize);
                    bytesProcessed += (int) skipped;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String parseUsltPayload(byte[] payload) {
        if (payload.length < 5) return null;
        int encoding = payload[0] & 0xFF;
        int textStart = 4;
        while (textStart < payload.length && payload[textStart] != 0) textStart++;
        textStart++;
        if ((encoding == 1 || encoding == 2) && textStart < payload.length && payload[textStart] == 0) textStart++;

        if (textStart >= payload.length) return null;

        Charset charset;
        switch (encoding) {
            case 1: charset = StandardCharsets.UTF_16; break;
            case 2: charset = StandardCharsets.UTF_16BE; break;
            case 3: charset = StandardCharsets.UTF_8; break;
            default: charset = StandardCharsets.ISO_8859_1; break;
        }
        return new String(payload, textStart, payload.length - textStart, charset).trim();
    }

    private static String readFile(File file) {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             Scanner scanner = new Scanner(fis)) {
            while (scanner.hasNextLine()) {
                sb.append(scanner.nextLine()).append("\n");
            }
        } catch (Exception e) {
            return null;
        }
        return sb.toString();
    }
}
