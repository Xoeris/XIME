package xime.haptic;

import android.content.Context;

public class HapticRuntime {
    private static HapticRuntime instance;
    private final HapticController controller;
    private boolean enabled = true;

    private HapticRuntime(Context context) {
        Context appContext = context.getApplicationContext();
        this.controller = new HapticController(appContext != null ? appContext : context);
    }

    public static synchronized HapticRuntime getInstance(Context context) {
        if (instance == null) {
            instance = new HapticRuntime(context);
        }
        return instance;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled && controller.isAvailable();
    }

    public HapticController getController() {
        return controller;
    }
}
