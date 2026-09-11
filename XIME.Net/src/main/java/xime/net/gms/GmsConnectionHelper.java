package xime.net.gms;

import android.app.Activity;
import android.content.Context;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

/**
 * Small helper for common ConnectionResult handling, not a general Play Services wrapper.
 */
public final class GmsConnectionHelper {
    private GmsConnectionHelper() {}

    public static void showResolvableErrorIfNeeded(Activity activity, int errorCode, int requestCode) {
        if (GoogleApiAvailability.getInstance().isUserResolvableError(errorCode)) {
            GoogleApiAvailability.getInstance().getErrorDialog(activity, errorCode, requestCode).show();
        }
    }

    public static boolean handleConnectionResult(Context context, ConnectionResult result) {
        return result.isSuccess();
    }
}
