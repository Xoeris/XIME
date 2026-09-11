package xime.ai;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class DeviceRecord {
    public final String deviceId;
    public final DeviceType deviceType;
    public final Set<Intent> capabilities;
    public final String pairedKey; // PHASE1-INSECURE: plaintext stand-in, see HyperionMessage
    public final long pairedAt;

    public DeviceRecord(String deviceId, DeviceType deviceType, Set<Intent> capabilities, String pairedKey, long pairedAt) {
        this.deviceId = Objects.requireNonNull(deviceId);
        this.deviceType = deviceType != null ? deviceType : DeviceType.OTHER;
        this.capabilities = capabilities != null ? Collections.unmodifiableSet(EnumSet.copyOf(capabilities)) : Collections.emptySet();
        this.pairedKey = pairedKey;
        this.pairedAt = pairedAt;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeviceRecord)) return false;
        DeviceRecord that = (DeviceRecord) o;
        return pairedAt == that.pairedAt && deviceId.equals(that.deviceId) && deviceType == that.deviceType && capabilities.equals(that.capabilities) && Objects.equals(pairedKey, that.pairedKey);
    }
    @Override public int hashCode() { return Objects.hash(deviceId, deviceType, capabilities, pairedKey, pairedAt); }
    @Override public String toString() { return "DeviceRecord{" + deviceId + "," + deviceType + "," + capabilities + "}"; }
}
