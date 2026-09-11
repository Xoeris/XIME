package xime.core.model;

public class AirQualityData {
    public final int aqi;
    public final long timestamp;

    public AirQualityData(int aqi) {
        this.aqi = aqi;
        this.timestamp = System.currentTimeMillis();
    }
}
