package xime.core.model;

import androidx.annotation.Nullable;

/**
 * Data model for geographic locations in XIME.
 */
public class MapLocation {
    public final double latitude;
    public final double longitude;
    
    @Nullable public final String city;
    @Nullable public final String country;
    @Nullable public final String addressLine;
    
    public final float accuracyRadiusKm;
    public final boolean isPreciseFix;
    
    public MapLocation(double latitude, double longitude, @Nullable String city, 
                       @Nullable String country, @Nullable String addressLine, 
                       float accuracyRadiusKm, boolean isPreciseFix) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.city = city;
        this.country = country;
        this.addressLine = addressLine;
        this.accuracyRadiusKm = accuracyRadiusKm;
        this.isPreciseFix = isPreciseFix;
    }
}
