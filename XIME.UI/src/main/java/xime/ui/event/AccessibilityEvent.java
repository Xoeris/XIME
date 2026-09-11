package xime.ui.event;

public interface AccessibilityEvent {
    void sendAccessibilityEvent(int eventType);

    void sendAccessibilityEventUnchecked(android.view.accessibility.AccessibilityEvent event);
}
