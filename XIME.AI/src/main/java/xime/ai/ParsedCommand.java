package xime.ai;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ParsedCommand {
    public final Intent intent;
    public final Map<String, Object> params;
    public final String rawText;

    public ParsedCommand(Intent intent, Map<String, Object> params, String rawText) {
        this.intent = Objects.requireNonNull(intent);
        this.params = params != null ? Collections.unmodifiableMap(new HashMap<>(params)) : Collections.emptyMap();
        this.rawText = rawText != null ? rawText : "";
    }

    @Override public String toString() { return intent + " " + params + " raw='" + rawText + "'"; }
}
