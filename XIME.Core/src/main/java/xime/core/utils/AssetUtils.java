package xime.core.utils;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class AssetUtils {
    private AssetUtils() {}

    public static boolean copyAsset(Context context, String assetPath, File destFile) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = context.getAssets().open(assetPath);
            out = new FileOutputStream(destFile);
            IOUtils.copy(in, out);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
    }

    public static boolean setExecutable(File file) {
        return file.setExecutable(true, false);
    }
}
