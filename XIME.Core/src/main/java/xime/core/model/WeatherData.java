package xime.core.model;

import java.util.List;

public class WeatherData {
    public final double temperature;
    public final int weatherCode;
    public final double cloudCover;
    public final String sunrise;
    public final String sunset;
    public final long timestamp;

    public WeatherData(double temperature, int weatherCode, double cloudCover, String sunrise, String sunset) {
        this.temperature = temperature;
        this.weatherCode = weatherCode;
        this.cloudCover = cloudCover;
        this.sunrise = sunrise;
        this.sunset = sunset;
        this.timestamp = System.currentTimeMillis();
    }
}
