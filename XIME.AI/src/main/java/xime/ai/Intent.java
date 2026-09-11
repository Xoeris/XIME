package xime.ai;

// Per Phase1 §3.2 — variable params (e.g. MEDIA_VOLUME delta) go in HyperionMessage.params
public enum Intent {
    MEDIA_PLAY,
    MEDIA_PAUSE,
    MEDIA_NEXT,
    MEDIA_VOLUME,
    SYSTEM_LOCK,
    SYSTEM_SLEEP,
    SYSTEM_SHUTDOWN,
    SYSTEM_STATUS,
    APP_LAUNCH,
    TV_INPUT_SWITCH,
    TV_POWER,
    NAVIGATE,
    QUERY
}
