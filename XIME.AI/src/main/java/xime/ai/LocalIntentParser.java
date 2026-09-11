package xime.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase2.5 local intent parser, ordered pattern matching, data-driven table intent→phrases/regex.
 * Pure Java, no Android/network deps (handoff §4.1 recommendation: lives in XIME.AI / hyperion-core shim).
 * Covers full Intent enum per §4.3; unmatched → QUERY with rawText preserved for future NLU.
 * Device-hint extraction reuses DeviceRegistry concept (deviceHint / target device string in params).
 */
public final class LocalIntentParser {

    private static final List<String> FILLERS = Arrays.asList(
            "hey sarah", "hey", "please", "could you", "would you", "can you", "my", "the", "a", "an"
    );

    private static class Entry {
        final Intent intent;
        final List<Pattern> patterns;
        final ParamExtractor extractor;
        Entry(Intent intent, List<String> regexes, ParamExtractor extractor) {
            this.intent = intent;
            this.patterns = new ArrayList<>();
            for (String r : regexes) this.patterns.add(Pattern.compile(r, Pattern.CASE_INSENSITIVE));
            this.extractor = extractor;
        }
    }

    interface ParamExtractor { Map<String,Object> extract(Matcher m, String normalized); }

    private final List<Entry> table = new ArrayList<>();

    public LocalIntentParser() {
        // Order = priority, first match wins. Keep specific before generic.
        // MEDIA_*
        add(Intent.MEDIA_PAUSE, Arrays.asList("\\bpause\\b", "\\bstop\\b(?!\\s*listening)", "\\bhold on\\b", "\\bpause music\\b"),
                (m,n)->empty());
        add(Intent.MEDIA_PLAY, Arrays.asList("\\bresume\\b", "\\bplay\\b", "\\bplay music\\b", "\\bcontinue\\b"),
                (m,n)->empty());
        add(Intent.MEDIA_NEXT, Arrays.asList("\\bnext\\b", "\\bskip\\b", "\\bnext song\\b", "\\bnext track\\b"),
                (m,n)->empty());
        add(Intent.MEDIA_VOLUME, Arrays.asList("volume\\s+up", "louder", "turn\\s+up", "volume\\s+down", "quieter", "turn\\s+down", "set\\s+volume\\s+to\\s+(\\d+)", "volume\\s+(\\d+)"),
                (m,n)->{
                    Map<String,Object> p=new HashMap<>();
                    String t=n.toLowerCase();
                    if (t.contains("up")||t.contains("louder")) p.put("delta","+1");
                    else if (t.contains("down")||t.contains("quieter")) p.put("delta","-1");
                    else {
                        Matcher num=Pattern.compile("(\\d+)").matcher(n);
                        if (num.find()) p.put("value", num.group(1));
                    }
                    return p;
                });

        // TV, must be before SYSTEM_SHUTDOWN (turn off TV should not be shutdown) and before APP_LAUNCH
        add(Intent.TV_POWER, Arrays.asList("turn\\s+on.*tv", "turn\\s+off.*tv", "\\btv\\s+on\\b", "\\btv\\s+off\\b"),
                (m,n)->{
                    Map<String,Object> p=new HashMap<>();
                    p.put("power", n.toLowerCase().contains("on") && !n.toLowerCase().contains("off") ? "on" : "off");
                    return p;
                });
        add(Intent.TV_INPUT_SWITCH, Arrays.asList("switch\\s+to\\s+hdmi\\s*(\\d*)", "change\\s+input\\b", "hdmi\\s*(\\d+)"),
                (m,n)->{
                    Map<String,Object> p=new HashMap<>();
                    if (m.groupCount()>=1 && m.group(1)!=null && !m.group(1).isEmpty()) p.put("source","HDMI"+m.group(1).trim());
                    else p.put("source","HDMI1");
                    return p;
                });

        // SYSTEM_*
        add(Intent.SYSTEM_SHUTDOWN, Arrays.asList("\\bshut\\s*down\\b", "\\bshutdown\\b", "\\bturn off\\b.*\\b(computer|laptop|phone|system)?\\b", "\\bpower off\\b"),
                (m,n)->empty());
        add(Intent.SYSTEM_SLEEP, Arrays.asList("\\bgo to sleep\\b", "\\bsleep\\b(?!.*mode)"),
                (m,n)->empty());
        add(Intent.SYSTEM_LOCK, Arrays.asList("\\block\\b", "\\block (my )?(laptop|phone|computer|device)\\b"),
                (m,n)->empty());
        add(Intent.SYSTEM_STATUS, Arrays.asList("\\bstatus\\b", "\\bhow are you\\b", "\\bsystem status\\b", "\\bhow.*doing\\b"),
                (m,n)->empty());

        // NAVIGATE, must be before APP_LAUNCH (open browser ... is more specific than open X)
        add(Intent.NAVIGATE, Arrays.asList("open\\s+browser\\s+on\\s+(laptop|phone|tv)", "navigate\\s+to\\s+(.+)", "go\\s+to\\s+(.+)\\s+on\\s+(laptop|phone|tv)"),
                (m,n)->{
                    Map<String,Object> p=new HashMap<>();
                    extractDeviceHint(n,p);
                    String g=m.groupCount()>=1 && m.group(1)!=null ? m.group(1).trim() : "";
                    if (!g.isEmpty()) p.put("action", g);
                    return p;
                });

        // APP_LAUNCH, generic, keep last before fallback
        add(Intent.APP_LAUNCH, Arrays.asList("open\\s+(.+)", "launch\\s+(.+)", "start\\s+(.+)", "run\\s+(.+)"),
                (m,n)->{
                    Map<String,Object> p=new HashMap<>();
                    if (m.groupCount()>=1 && m.group(1)!=null) p.put("target", m.group(1).trim());
                    extractDeviceHint(n,p);
                    return p;
                });

        // QUERY fallback handled after table
    }

    private void add(Intent intent, List<String> regexes, ParamExtractor ex) { table.add(new Entry(intent, regexes, ex)); }
    private static Map<String,Object> empty(){ return new HashMap<>(); }

    private static void extractDeviceHint(String normalized, Map<String,Object> out) {
        String t=normalized.toLowerCase();
        if (t.contains("laptop")) out.put("deviceHint","LAPTOP");
        else if (t.contains("phone")) out.put("deviceHint","PHONE");
        else if (t.contains(" tv")||t.startsWith("tv")) out.put("deviceHint","TV");
        else if (t.contains("tablet")||t.contains("device")) out.put("deviceHint","OTHER");
    }

    public ParsedCommand parse(String text) {
        String raw = text != null ? text : "";
        String n = normalize(raw);
        if (n.isEmpty()) return new ParsedCommand(Intent.QUERY, mapOf("raw",raw), raw);

        for (Entry e : table) {
            for (Pattern p : e.patterns) {
                Matcher m = p.matcher(n);
                if (m.find()) {
                    Map<String,Object> params = e.extractor != null ? e.extractor.extract(m, n) : empty();
                    params.putIfAbsent("raw", raw);
                    return new ParsedCommand(e.intent, params, raw);
                }
            }
        }
        // fallback
        Map<String,Object> p=new HashMap<>();
        p.put("raw", raw);
        p.put("text", n);
        return new ParsedCommand(Intent.QUERY, p, raw);
    }

    private static String normalize(String s) {
        String t=s.toLowerCase().trim().replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+"," ").trim();
        for (String f: FILLERS) {
            // strip filler words but keep device hints? we keep extraction before stripping deviceHint? Do after.
            // simple: remove phrase fillers via word boundaries
            t=t.replaceAll("\\b"+Pattern.quote(f)+"\\b"," ");
        }
        return t.replaceAll("\\s+"," ").trim();
    }

    private static Map<String,Object> mapOf(String k,Object v){ Map<String,Object> m=new HashMap<>(); m.put(k,v); return m; }
}
