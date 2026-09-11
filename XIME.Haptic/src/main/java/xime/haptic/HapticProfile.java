package xime.haptic;

import java.util.ArrayList;
import java.util.List;

public class HapticProfile {
    private final List<HapticEvent> events = new ArrayList<>();
    private final String name;

    public HapticProfile(String name) {
        this.name = name;
    }

    public void addEvent(HapticEvent event) {
        events.add(event);
    }

    public List<HapticEvent> getEvents() {
        return events;
    }

    public String getName() {
        return name;
    }

    public static HapticProfile createSimple(String name, HapticPattern pattern, HapticIntensity intensity) {
        HapticProfile profile = new HapticProfile(name);
        profile.addEvent(new HapticEvent(pattern, intensity));
        return profile;
    }
}
