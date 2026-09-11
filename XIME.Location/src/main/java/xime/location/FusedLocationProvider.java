package xime.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import java.util.List;
import java.util.Locale;

/**
 * Fused implementation — wraps FusedLocationProviderClient with LocationManager fallback.
 * Kept in XIME.Location (not XIME.Core) so Hyperion can exclude play-services when not needed.
 */
public class FusedLocationProvider implements LocationProvider {

    private final Context context;
    private final FusedLocationProviderClient fusedClient;

    public FusedLocationProvider(Context context) {
        this.context = context.getApplicationContext();
        this.fusedClient = LocationServices.getFusedLocationProviderClient(this.context);
    }

    @SuppressLint("MissingPermission")
    @Override public void getCurrentLocation(Callback callback) {
        fusedClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) process(location, callback);
                    else fallback(callback);
                })
                .addOnFailureListener(e -> fallback(callback));
    }

    @SuppressLint("MissingPermission")
    @Override public void getLastKnownLocation(Callback callback) {
        getCurrentLocation(callback);
    }

    @SuppressLint("MissingPermission")
    private void fallback(Callback callback) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location loc = null;
        try {
            loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {}
        if (loc != null) process(loc, callback);
        else callback.onFailure("Could not determine location");
    }

    private void process(Location location, Callback callback) {
        String city = "Unknown";
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                city = addr.getLocality() != null ? addr.getLocality() : addr.getAdminArea();
                if (city == null) city = "Unknown";
            }
        } catch (Exception ignored) {}
        callback.onSuccess(location.getLatitude(), location.getLongitude(), city);
    }
}
