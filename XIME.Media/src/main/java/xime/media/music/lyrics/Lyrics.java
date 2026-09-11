package xime.media.music.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-precision Lyrics Engine.
 * Token-based word synchronization for absolute accuracy.
 */
public class Lyrics {

    private static final Pattern TIME_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern WORD_TIME_PATTERN = Pattern.compile("<(\\d{1,2}):(\\d{1,2})[.:](\\d{1,3})>");

    public static class LyricLine {
        public long time;
        public String text = "";
        public final List<Word> words = new ArrayList<>();
        public long duration = -1;

        public LyricLine(long time) {
            this.time = time;
        }

        public static class Word {
            public final String text;
            public long startTime = -1;
            public long duration = -1;
            public int charStart = -1;
            public int charEnd = -1;

            public Word(String text, long startTime) {
                this.text = text;
                this.startTime = startTime;
            }
        }
    }

    private final List<LyricLine> lines = new ArrayList<>();
    private boolean isSynced = false;

    public void parse(String rawLyrics) {
        lines.clear();
        isSynced = false;

        if (rawLyrics == null || rawLyrics.trim().isEmpty()) return;

        String[] rawLines = rawLyrics.split("\\r?\\n");

        for (String line : rawLines) {
            line = line.trim();
            
            // If the line is actually empty (but not metadata), treat it as a musical break
            if (line.isEmpty()) {
                LyricLine breakLine = new LyricLine(-1);
                breakLine.text = "🎶";
                lines.add(breakLine);
                continue;
            }

            Matcher matcher = TIME_PATTERN.matcher(line);
            List<Long> times = new ArrayList<>();
            int lastIndex = 0;

            while (matcher.find()) {
                isSynced = true;
                times.add(parseTimestamp(matcher.group(1), matcher.group(2), matcher.group(3)));
                lastIndex = matcher.end();
            }

            if (!times.isEmpty()) {
                String content = line.substring(lastIndex).trim();
                for (long t : times) {
                    lines.add(parseLineTokens(t, content));
                }
            } else if (!isSynced) {
                if (!isMetadata(line)) {
                    LyricLine l = new LyricLine(-1);
                    l.text = line;
                    // Tokenize even if not synced for ASR alignment
                    String[] parts = line.split("(?<=\\s)|(?=\\s)");
                    for (String part : parts) {
                        if (!part.isEmpty()) l.words.add(new LyricLine.Word(part, -1));
                    }
                    lines.add(l);
                }
            }
        }
        
        if (isSynced) {
            Collections.sort(lines, (l1, l2) -> Long.compare(l1.time, l2.time));
        }
        finalizeTimings();
    }

    private LyricLine parseLineTokens(long lineTime, String content) {
        if (content == null || content.trim().isEmpty()) {
            LyricLine l = new LyricLine(lineTime);
            l.text = "🎶";
            return l;
        }

        LyricLine lyricLine = new LyricLine(lineTime);
        StringBuilder fullText = new StringBuilder();
        
        // Tokenize by word-time tags
        Matcher matcher = WORD_TIME_PATTERN.matcher(content);
        int lastIndex = 0;
        long currentWordTime = lineTime;

        while (matcher.find()) {
            String segment = content.substring(lastIndex, matcher.start());
            if (!segment.isEmpty()) {
                lyricLine.words.add(new LyricLine.Word(segment, currentWordTime));
                fullText.append(segment);
            }
            currentWordTime = parseTimestamp(matcher.group(1), matcher.group(2), matcher.group(3));
            lastIndex = matcher.end();
        }
        
        String lastSegment = content.substring(lastIndex);
        if (!lastSegment.isEmpty()) {
            lyricLine.words.add(new LyricLine.Word(lastSegment, currentWordTime));
            fullText.append(lastSegment);
        }

        // Fallback for non-word-sync LRC: Split by whitespace
        if (lyricLine.words.isEmpty()) {
            lyricLine.text = content.replaceAll("<[^>]*>", "").trim();
            String[] parts = lyricLine.text.split("\\s+");
            for (String part : parts) {
                if (!part.trim().isEmpty()) {
                    lyricLine.words.add(new LyricLine.Word(part, -1));
                }
            }
        } else {
            lyricLine.text = fullText.toString();
        }
        
        return lyricLine;
    }

    private void finalizeTimings() {
        for (int i = 0; i < lines.size(); i++) {
            LyricLine current = lines.get(i);
            
            // For unsynced lyrics, provide a baseline estimate (5s per line)
            if (current.time == -1) {
                current.time = (long) i * 5000;
            }

            long nextTime;
            if (i < lines.size() - 1) {
                nextTime = lines.get(i + 1).time;
                if (nextTime == -1) nextTime = (long) (i + 1) * 5000;
            } else {
                nextTime = current.time + 5000;
            }

            current.duration = Math.max(50, nextTime - current.time);
            
            if (!current.words.isEmpty()) {
                boolean hasWordSync = false;
                for (LyricLine.Word w : current.words) {
                    if (w.startTime != -1) {
                        hasWordSync = true;
                        break;
                    }
                }

                if (hasWordSync) {
                    for (int j = 0; j < current.words.size(); j++) {
                        LyricLine.Word w = current.words.get(j);
                        if (w.startTime == -1) w.startTime = current.time;
                        
                        long nextWTime;
                        if (j < current.words.size() - 1) {
                            nextWTime = current.words.get(j + 1).startTime;
                            if (nextWTime == -1) nextWTime = current.time + current.duration;
                        } else {
                            nextWTime = current.time + current.duration;
                        }
                        w.duration = Math.max(0, nextWTime - w.startTime);
                    }
                } else {
                    // Estimation based on char weight
                    int totalChars = 0;
                    for (LyricLine.Word w : current.words) totalChars += w.text.length();
                    if (totalChars > 0) {
                        long elapsed = 0;
                        for (LyricLine.Word w : current.words) {
                            w.startTime = current.time + elapsed;
                            w.duration = (current.duration * w.text.length()) / totalChars;
                            elapsed += w.duration;
                        }
                    } else {
                        // All words are empty? Just set to line start
                        for (LyricLine.Word w : current.words) {
                            w.startTime = current.time;
                            w.duration = current.duration / current.words.size();
                        }
                    }
                }

                // Ensure character offsets are always populated for UI highlighting
                int charOffset = 0;
                for (LyricLine.Word w : current.words) {
                    w.charStart = charOffset;
                    w.charEnd = charOffset + w.text.length();
                    charOffset = w.charEnd;
                }
            }
        }
    }

    private static long parseTimestamp(String minStr, String secStr, String msStr) {
        try {
            long min = Long.parseLong(minStr);
            long sec = Long.parseLong(secStr);
            long ms = 0;
            if (msStr != null) {
                ms = Long.parseLong(msStr);
                if (msStr.length() == 1) ms *= 100;
                else if (msStr.length() == 2) ms *= 10;
            }
            return (min * 60 * 1000) + (sec * 1000) + ms;
        } catch (Exception e) { return 0; }
    }

    private boolean isMetadata(String line) {
        return line.startsWith("[ar:") || line.startsWith("[ti:") || line.startsWith("[al:") || line.startsWith("[by:") || line.startsWith("[offset:");
    }

    public List<LyricLine> getLines() { return lines; }

    public boolean isSynced() { 
        if (isSynced) return true;
        // Even if not originally synced, if we have line timings (from ASR), it counts
        return !lines.isEmpty() && lines.get(0).time != -1;
    }

    public int getLineIndexForTime(long timeMs) {
        if (!isSynced() || lines.isEmpty()) return -1;
        int low = 0, high = lines.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).time < timeMs) low = mid + 1;
            else if (lines.get(mid).time > timeMs) high = mid - 1;
            else return mid;
        }
        return low - 1;
    }
}

