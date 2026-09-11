package xime.net.gms;

import android.content.Context;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

/**
 * XIME.Net — thin Xoeris-native wrapper around play-services-base common scaffolding.
 * Narrowly scoped to GoogleApiAvailability checks / ConnectionResult handling.
 * Package xime.net.gms avoids collision with hyperion-net / xime.ai protocol networking.
 * Hyperion does NOT depend on this (gated closed per Phase2.7); standalone for XIME consumers.
 */
public final class GmsAvailability {
    private GmsAvailability() {}

    public static int isGooglePlayServicesAvailable(Context context) {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
    }

    public static boolean isAvailable(Context context) {
        return isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
    }

    public static String getErrorString(int errorCode) {
        return GoogleApiAvailability.getInstance().getErrorString(errorCode);
    }

    public static boolean isUserResolvableError(int errorCode) {
        return GoogleApiAvailability.getInstance().isUserResolvableError(errorCode);
    }
}
