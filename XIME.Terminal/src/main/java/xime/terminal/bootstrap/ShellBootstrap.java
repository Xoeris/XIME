package xime.terminal.bootstrap;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.io.File;
import xime.core.utils.AssetUtils;
import xime.system.optimization.zenith.BackgroundZenith;

public final class ShellBootstrap {
    private static final String PREFS_NAME = "xime_terminal_prefs";
    private static final String KEY_SHELL_VERSION = "shell_version";
    private static final int CURRENT_VERSION = 1; // Increment this when binaries in assets are updated

    public interface BootstrapCallback {
        void onBootstrapComplete(String shellPath);
        void onBootstrapFailed(String error);
    }

    public static void ensureShell(Context context, BootstrapCallback callback) {
        BackgroundZenith.execute(() -> {
            try {
                File binDir = context.getDir("bin", Context.MODE_PRIVATE);
                if (!binDir.exists() && !binDir.mkdirs()) {
                    notifyFailed(callback, "Could not create bin directory");
                    return;
                }

                String abi = getSupportedAbi();
                String assetPath = "bin/" + abi + "/sh";
                File shellFile = new File(binDir, "sh");

                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                int installedVersion = prefs.getInt(KEY_SHELL_VERSION, -1);

                if (!shellFile.exists() || installedVersion < CURRENT_VERSION) {
                    if (!AssetUtils.copyAsset(context, assetPath, shellFile)) {
                        notifyFailed(callback, "Binary missing or copy failed for ABI: " + abi + " (Path: " + assetPath + ")");
                        return;
                    }
                    
                    if (!AssetUtils.setExecutable(shellFile)) {
                        notifyFailed(callback, "Failed to set executable bit on " + shellFile.getAbsolutePath());
                        return;
                    }
                    
                    prefs.edit().putInt(KEY_SHELL_VERSION, CURRENT_VERSION).apply();
                }

                if (!shellFile.canExecute()) {
                    // Try setting executable bit again if it failed for some reason
                    AssetUtils.setExecutable(shellFile);
                    if (!shellFile.canExecute()) {
                        notifyFailed(callback, "Binary exists but is not executable: " + shellFile.getAbsolutePath());
                        return;
                    }
                }

                BackgroundZenith.runOnUiThread(() -> callback.onBootstrapComplete(shellFile.getAbsolutePath()));
            } catch (Exception e) {
                notifyFailed(callback, "Unexpected bootstrap error: " + e.getMessage());
            }
        });
    }

    private static void notifyFailed(BootstrapCallback callback, String message) {
        BackgroundZenith.runOnUiThread(() -> callback.onBootstrapFailed(message));
    }

    private static String getSupportedAbi() {
        if (Build.SUPPORTED_ABIS.length > 0) {
            String primary = Build.SUPPORTED_ABIS[0];
            if (primary.contains("arm64")) return "arm64-v8a";
            if (primary.contains("x86_64")) return "x86_64";
            if (primary.contains("armeabi-v7a")) return "armeabi-v7a";
        }
        return "arm64-v8a"; // Default fallback
    }
}
