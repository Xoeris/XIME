package xime.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class HyperionMessage {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    @SerializedName("hyperionProtocolVersion")
    public final int hyperionProtocolVersion = 1;

    @SerializedName("messageId")
    public final String messageId;

    @SerializedName("deviceId")
    public final String deviceId;

    @SerializedName("type")
    public final MessageType type;

    @SerializedName("intent")
    public final Intent intent; // nullable, only for COMMAND

    @SerializedName("params")
    public final Map<String, Object> params;

    @SerializedName("timestamp")
    public final long timestamp;

    public HyperionMessage(String messageId, String deviceId, MessageType type, Intent intent, Map<String, Object> params, long timestamp) {
        this.messageId = messageId != null ? messageId : UUID.randomUUID().toString();
        this.deviceId = Objects.requireNonNull(deviceId);
        this.type = Objects.requireNonNull(type);
        this.intent = intent;
        this.params = params != null ? Collections.unmodifiableMap(new HashMap<>(params)) : Collections.emptyMap();
        this.timestamp = timestamp != 0 ? timestamp : System.currentTimeMillis();
    }

    // Factories
    public static HyperionMessage command(String deviceId, Intent intent, Map<String, Object> params) {
        return new HyperionMessage(UUID.randomUUID().toString(), deviceId, MessageType.COMMAND, intent, params, System.currentTimeMillis());
    }
    public static HyperionMessage pairRequest(String deviceId) {
        Map<String, Object> p = new HashMap<>();
        // PHASE1-INSECURE: plaintext secret
        p.put("pairSecret", UUID.randomUUID().toString());
        return new HyperionMessage(UUID.randomUUID().toString(), deviceId, MessageType.PAIR_REQUEST, null, p, System.currentTimeMillis());
    }
    public static HyperionMessage pairResponse(String deviceId, String pairedKey) {
        Map<String, Object> p = new HashMap<>();
        p.put("pairedKey", pairedKey);
        return new HyperionMessage(UUID.randomUUID().toString(), deviceId, MessageType.PAIR_RESPONSE, null, p, System.currentTimeMillis());
    }
    public static HyperionMessage ack(String deviceId, String replyTo) {
        Map<String, Object> p = new HashMap<>();
        p.put("replyTo", replyTo);
        return new HyperionMessage(UUID.randomUUID().toString(), deviceId, MessageType.ACK, null, p, System.currentTimeMillis());
    }
    public static HyperionMessage result(String deviceId, String replyTo, Object resultPayload) {
        Map<String, Object> p = new HashMap<>();
        p.put("replyTo", replyTo);
        p.put("result", resultPayload);
        return new HyperionMessage(UUID.randomUUID().toString(), deviceId, MessageType.RESULT, null, p, System.currentTimeMillis());
    }

    public String toJson() { return GSON.toJson(this); }
    public static HyperionMessage fromJson(String json) { return GSON.fromJson(json, HyperionMessage.class); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HyperionMessage)) return false;
        HyperionMessage that = (HyperionMessage) o;
        return hyperionProtocolVersion == that.hyperionProtocolVersion && timestamp == that.timestamp && messageId.equals(that.messageId) && deviceId.equals(that.deviceId) && type == that.type && intent == that.intent && params.equals(that.params);
    }
    @Override public int hashCode() { return Objects.hash(hyperionProtocolVersion, messageId, deviceId, type, intent, params, timestamp); }
    @Override public String toString() { return toJson(); }
}
