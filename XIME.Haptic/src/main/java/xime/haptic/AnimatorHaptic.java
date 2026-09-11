package xime.haptic;

import android.os.Handler;
import android.os.Looper;

public class AnimatorHaptic {
    private final HapticController controller;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public AnimatorHaptic(HapticController controller) {
        this.controller = controller;
    }

    public void animate(HapticProfile profile) {
        long currentDelay = 0;
        for (HapticEvent event : profile.getEvents()) {
            currentDelay += event.getDelay();
            handler.postDelayed(() -> controller.perform(event), currentDelay);
        }
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        controller.cancel();
    }
}
