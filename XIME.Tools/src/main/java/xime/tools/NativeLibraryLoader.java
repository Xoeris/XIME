package xime.tools;

import android.os.Build;

/**
 * Handles loading of {@code libxime-tools.so} with ABI-aware error surfacing.
 *
 * <p>The load is triggered lazily the first time {@link #load()} is called; subsequent
 * calls are no-ops. This mirrors the pattern in {@code TerminalNative} where
 * {@code System.loadLibrary} is called from a {@code static {}} initializer, but
 * centralises error handling here rather than letting a raw {@link UnsatisfiedLinkError}
 * propagate up with no context.
 */
public final class NativeLibraryLoader {

    private static final String LIBRARY_NAME = "xime-tools";

    private static volatile boolean loaded = false;
    private static volatile RuntimeException loadError = null;

    private NativeLibraryLoader() {}

    /**
     * Loads {@code libxime-tools.so}. Safe to call multiple times; the actual
     * {@link System#loadLibrary(String)} call is performed at most once.
     *
     * @throws RuntimeException wrapping the {@link UnsatisfiedLinkError} if the library
     *                          could not be found or loaded, with ABI and library name
     *                          included in the message for easier diagnosis.
     */
    public static void load() {
        if (loaded) {
            if (loadError != null) throw loadError;
            return;
        }
        synchronized (NativeLibraryLoader.class) {
            if (loaded) {
                if (loadError != null) throw loadError;
                return;
            }
            try {
                System.loadLibrary(LIBRARY_NAME);
            } catch (UnsatisfiedLinkError e) {
                String abi = getPrimaryAbi();
                loadError = new RuntimeException(
                    "XIME.Tools: failed to load native library '" + LIBRARY_NAME + "' "
                    + "for ABI '" + abi + "'. "
                    + "Ensure libxime-tools.so is packaged for this ABI in the APK. "
                    + "Original error: " + e.getMessage(), e);
                loaded = true;
                throw loadError;
            }
            loaded = true;
        }
    }

    /**
     * Returns {@code true} if the native library has been loaded successfully.
     */
    public static boolean isLoaded() {
        return loaded && loadError == null;
    }

    private static String getPrimaryAbi() {
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
            return Build.SUPPORTED_ABIS[0];
        }
        return "unknown";
    }
}
