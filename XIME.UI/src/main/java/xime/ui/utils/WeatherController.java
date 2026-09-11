package xime.ui.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import xime.core.location.LocationProvider;
import xime.core.model.AirQualityData;
import xime.core.model.WeatherData;
import xime.core.repo.WeatherRepository;
import xime.ui.view.WeatherView;

public class WeatherController {
    private static final long WEATHER_REFRESH_INTERVAL = 10 * 60 * 1000; // 10 minutes
    private static final long CLOCK_TICK_INTERVAL = 1000; // 1 second

    private final Context context;
    private final WeatherView widget;
    private final WeatherRepository repository;
    private final LocationProvider locationProvider;
    private final SimpleDateFormat isoFormat;
    private final SimpleDateFormat timeFormat;
    private final SimpleDateFormat dateFormat;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String lastLocationName = "Provi, Kerala";
    private double lastLat = 11.2588;
    private double lastLon = 75.7804;
    private WeatherData lastWeatherData;
    private boolean isDestroyed = false;

    private final Runnable weatherRefreshTask = new Runnable() {
        @Override
        public void run() {
            if (isDestroyed) return;
            refresh();
            handler.postDelayed(this, WEATHER_REFRESH_INTERVAL);
        }
    };

    private final Runnable clockTickTask = new Runnable() {
        @Override
        public void run() {
            if (isDestroyed) return;
            updateTimeOnly();
            handler.postDelayed(this, CLOCK_TICK_INTERVAL);
        }
    };

    public WeatherController(Context context, WeatherView widget, WeatherRepository repository) {
        this.context = context;
        this.widget = widget;
        this.repository = repository;
        this.locationProvider = new LocationProvider(context);
        
        this.isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        this.isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        this.timeFormat = new SimpleDateFormat("h:mm:ss a", Locale.getDefault());
        this.dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

        startRealtimeSync();
    }

    public void startRealtimeSync() {
        handler.removeCallbacks(weatherRefreshTask);
        handler.removeCallbacks(clockTickTask);
        handler.post(weatherRefreshTask);
        handler.post(clockTickTask);
    }

    public void refresh() {
        locationProvider.getCurrentLocation(new LocationProvider.LocationCallback() {
            @Override
            public void onSuccess(double lat, double lon, String city) {
                lastLat = lat;
                lastLon = lon;
                lastLocationName = city;
                fetchWeatherData(lat, lon, city);
            }

            @Override
            public void onFailure(String error) {
                fetchWeatherData(lastLat, lastLon, lastLocationName);
            }
        });
    }

    public void refresh(double lat, double lon) {
        lastLat = lat;
        lastLon = lon;
        fetchWeatherData(lat, lon, lastLocationName);
    }

    private void fetchWeatherData(double lat, double lon, String locationName) {
        repository.fetchData(lat, lon, new WeatherRepository.WeatherCallback() {
            @Override
            public void onSuccess(WeatherData weather, AirQualityData aqi) {
                if (!isDestroyed) {
                    updateWidget(locationName, weather, aqi);
                }
            }

            @Override
            public void onError(String message) {
                // Keep existing UI or show error
            }
        });
    }

    private void updateWidget(String location, WeatherData weather, AirQualityData aqi) {
        this.lastWeatherData = weather;
        String temp = Math.round(weather.temperature) + "°";
        String condition = mapWeatherCode(weather.weatherCode);
        String time = timeFormat.format(new Date());
        String date = dateFormat.format(new Date());

        widget.setWeather(location, temp, condition, time, date);
        
        String aqiStatus = mapAqiStatus(aqi.aqi);
        float aqiProgress = Math.min(1.0f, aqi.aqi / 300f);
        widget.setAQI(aqi.aqi, aqiStatus, aqiProgress);

        String cloudStatus = mapCloudStatus((int) weather.cloudCover);
        widget.setCloudCover((int) weather.cloudCover, cloudStatus);
        
        widget.setWeatherMode(mapToWeatherMode(weather.weatherCode));

        updateSunPosition();
    }

    private void updateTimeOnly() {
        String time = timeFormat.format(new Date());
        String date = dateFormat.format(new Date());
        widget.setWeather(lastLocationName, null, null, time, date);
        updateSunPosition();
    }

    private void updateSunPosition() {
        if (lastWeatherData == null) return;
        try {
            long now = System.currentTimeMillis();
            long sunrise = isoFormat.parse(lastWeatherData.sunrise).getTime();
            long sunset = isoFormat.parse(lastWeatherData.sunset).getTime();
            float progress = (float) (now - sunrise) / (sunset - sunrise);
            widget.setSunProgress(Math.max(0f, Math.min(1f, progress)));
        } catch (Exception ignored) {}
    }

    private String mapWeatherCode(int code) {
        switch (code) {
            case 0: return "Pretty Sunny";
            case 1: case 2: return "Mainly Clear";
            case 3: return "Partly Cloudy";
            case 45: case 48: return "Foggy";
            case 51: case 53: case 55: return "Drizzling";
            case 61: case 63: case 65: return "Raining";
            case 71: case 73: case 75: return "Snowing";
            case 95: case 96: case 99: return "Stormy";
            default: return "Unknown";
        }
    }

    private WeatherView.WeatherMode mapToWeatherMode(int code) {
        if (code == 0) return WeatherView.WeatherMode.CLEAR;
        if (code <= 3) return WeatherView.WeatherMode.CLOUDY;
        if (code <= 65) return WeatherView.WeatherMode.RAINY;
        return WeatherView.WeatherMode.STORMY;
    }

    private String mapAqiStatus(int aqi) {
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Unhealthy (SG)";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Very Unhealthy";
        return "Hazardous";
    }

    private String mapCloudStatus(int cloudCover) {
        if (cloudCover < 20) return "Clean";
        if (cloudCover < 70) return "Partly Cloudy";
        return "Overcast";
    }

    public void destroy() {
        isDestroyed = true;
        handler.removeCallbacks(weatherRefreshTask);
        handler.removeCallbacks(clockTickTask);
        widget.destroy();
    }
}
