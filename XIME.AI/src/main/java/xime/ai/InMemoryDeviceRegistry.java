package xime.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryDeviceRegistry implements DeviceRegistry {
    private final ConcurrentMap<String, DeviceRecord> store = new ConcurrentHashMap<>();

    @Override public void register(DeviceRecord device) { store.put(device.deviceId, device); }
    @Override public void revoke(String deviceId) { store.remove(deviceId); }
    @Override public Optional<DeviceRecord> get(String deviceId) { return Optional.ofNullable(store.get(deviceId)); }
    @Override public List<DeviceRecord> all() { return new ArrayList<>(store.values()); }
}
