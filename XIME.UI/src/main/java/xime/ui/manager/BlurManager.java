package xime.ui.manager;

import android.content.Context;
import android.content.SharedPreferences;

import xime.graphics.shader.blur.AdaptiveBlur;
import xime.graphics.shader.blur.AdaptiveBlur.XoerisBlurV2Type;
import xime.graphics.shader.blur.LegacyBlur.BlurType;

public class BlurManager {
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_BLUR_VERSION = "blur_version";
    private static final String KEY_BLUR_STYLE = "blur_style";
    public static final String KEY_DISABLE_BLUR = "disable_glass_obscura";

    public static final String VERSION_LEGACY = "legacy";
    public static final String VERSION_ADAPTIVE = "adaptive";

    private final SharedPreferences prefs;

    public BlurManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isBlurDisabled() {
        return prefs.getBoolean(KEY_DISABLE_BLUR, false);
    }

    public interface OnBlurChangedListener {
        void onBlurChanged(boolean isBlurDisabled);
    }

    private static final java.util.List<java.lang.ref.WeakReference<OnBlurChangedListener>> sListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addListener(OnBlurChangedListener listener) {
        if (listener == null) return;
        for (java.lang.ref.WeakReference<OnBlurChangedListener> ref : sListeners) {
            if (ref.get() == listener) return;
        }
        sListeners.add(new java.lang.ref.WeakReference<>(listener));
    }

    public static void removeListener(OnBlurChangedListener listener) {
        if (listener == null) return;
        sListeners.removeIf(ref -> {
            OnBlurChangedListener target = ref.get();
            return target == null || target == listener;
        });
    }

    private static void notifyListeners(boolean disabled) {
        xime.core.dispatch.Dispatcher.main(() -> {
            for (java.lang.ref.WeakReference<OnBlurChangedListener> ref : sListeners) {
                OnBlurChangedListener listener = ref.get();
                if (listener != null) {
                    listener.onBlurChanged(disabled);
                }
            }
        });
    }

    public void setBlurDisabled(boolean disabled) {
        prefs.edit().putBoolean(KEY_DISABLE_BLUR, disabled).apply();
        notifyListeners(disabled);
    }

    public String getBlurVersion() {
        return prefs.getString(KEY_BLUR_VERSION, VERSION_ADAPTIVE);
    }

    public void setBlurVersion(String version) {
        prefs.edit().putString(KEY_BLUR_VERSION, version).apply();
        notifyListeners(isBlurDisabled());
    }

    public String getBlurStyle() {
        return prefs.getString(KEY_BLUR_STYLE, "obscura");
    }

    public void setBlurStyle(String style) {
        prefs.edit().putString(KEY_BLUR_STYLE, style).apply();
        notifyListeners(isBlurDisabled());
    }

    public boolean isAdaptive() {
        return VERSION_ADAPTIVE.equals(getBlurVersion());
    }

    public BlurType getLegacyBlurType() {
        String style = getBlurStyle();
        if ("crystal".equalsIgnoreCase(style)) {
            return BlurType.CRYSTAL;
        }
        return BlurType.GLASS;
    }

    public XoerisBlurV2Type getAdaptiveBlurType() {
        String style = getBlurStyle();
        if ("aura".equalsIgnoreCase(style)) {
            return XoerisBlurV2Type.AURA;
        }
        return XoerisBlurV2Type.OBSCURA;
    }
}
