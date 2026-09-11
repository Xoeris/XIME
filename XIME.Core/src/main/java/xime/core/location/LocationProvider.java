package xime.core.location;

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

public class LocationProvider {
    public interface LocationCallback {
        void onSuccess(double lat, double lon, String city);
        void onFailure(String error);
    }

    private final Context context;
    private final FusedLocationProviderClient fusedClient;

    public LocationProvider(Context context) {
        this.context = context;
        FusedLocationProviderClient client = null;
        try {
            client = LocationServices.getFusedLocationProviderClient(context);
        } catch (Throwable t) {
            client = null;
        }
        this.fusedClient = client;
    }

    @SuppressLint("MissingPermission")
    public void getCurrentLocation(LocationCallback callback) {
        if (fusedClient == null) {
            fallbackToLocationManager(callback);
            return;
        }
        try {
            fusedClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    processLocation(location, callback);
                } else {
                    fallbackToLocationManager(callback);
                }
            }).addOnFailureListener(e -> fallbackToLocationManager(callback));
        } catch (Throwable t) {
            fallbackToLocationManager(callback);
        }
    }

    @SuppressLint("MissingPermission")
    private void fallbackToLocationManager(LocationCallback callback) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location loc = null;
        try {
            loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {}

        if (loc != null) {
            processLocation(loc, callback);
        } else {
            callback.onFailure("Could not determine location");
        }
    }

    private void processLocation(Location location, LocationCallback callback) {
        String city = "Unknown";
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                city = addr.getLocality() != null ? addr.getLocality() : addr.getAdminArea();
            }
        } catch (Exception ignored) {}
        callback.onSuccess(location.getLatitude(), location.getLongitude(), city);
    }
}
