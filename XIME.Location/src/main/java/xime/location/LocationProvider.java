package xime.location;

import android.content.Context;

/**
 * XIME.Location, thin Xoeris-native wrapper around play-services-location subset.
 * Narrowly scoped to last-known-location / simple fetch, not full geofencing.
 * Interface + implementation split, matching xime.ai pattern.
 * Hyperion does NOT depend on this (gated closed per Phase2.7); standalone for XIME consumers (Musify etc.).
 */
public interface LocationProvider {
    interface Callback {
        void onSuccess(double lat, double lon, String city);
        void onFailure(String error);
    }
    void getCurrentLocation(Callback callback);
    void getLastKnownLocation(Callback callback);
}
