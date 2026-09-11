package xime.ai;

import java.util.List;
import java.util.Optional;

public interface DeviceRegistry {
    void register(DeviceRecord device);
    void revoke(String deviceId);
    Optional<DeviceRecord> get(String deviceId);
    List<DeviceRecord> all();
}
