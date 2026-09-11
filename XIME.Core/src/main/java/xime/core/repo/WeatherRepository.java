package xime.core.repo;

import org.json.JSONObject;
import xime.core.dispatch.Dispatcher;
import xime.core.model.AirQualityData;
import xime.core.model.WeatherData;
import xime.core.net.NetClient;
import xime.core.cache.CacheManager;
import xime.core.log.ILogger;

public class WeatherRepository {
    private final NetClient netClient;
    private final CacheManager cache;
    private final ILogger logger;

    public interface WeatherCallback {
        void onSuccess(WeatherData weather, AirQualityData aqi);
        void onError(String message);
    }

    public WeatherRepository(NetClient netClient, CacheManager cache, ILogger logger) {
        this.netClient = netClient;
        this.cache = cache;
        this.logger = logger;
    }

    public void fetchData(double lat, double lon, WeatherCallback callback) {
        String cacheKey = "weather_" + lat + "_" + lon;
        WeatherData cachedWeather = cache.get(cacheKey + "_w");
        AirQualityData cachedAqi = cache.get(cacheKey + "_a");

        if (cachedWeather != null && cachedAqi != null) {
            logger.i("WeatherRepo", "Returning cached data");
            callback.onSuccess(cachedWeather, cachedAqi);
            return;
        }

        Dispatcher.io(() -> {
            try {
                String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + 
                                    "&longitude=" + lon + "&current=temperature_2m,weather_code,cloud_cover" +
                                    "&daily=sunrise,sunset&timezone=auto";
                String aqiUrl = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + lat + 
                                 "&longitude=" + lon + "&current=us_aqi";

                String weatherJson = netClient.get(weatherUrl, true);
                String aqiJson = netClient.get(aqiUrl, true);

                WeatherData weather = parseWeather(weatherJson);
                AirQualityData aqi = parseAqi(aqiJson);

                cache.put(cacheKey + "_w", weather);
                cache.put(cacheKey + "_a", aqi);

                Dispatcher.main(() -> callback.onSuccess(weather, aqi));
            } catch (Exception e) {
                logger.e("WeatherRepo", "Fetch failed", e);
                Dispatcher.main(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private WeatherData parseWeather(String json) throws Exception {
        JSONObject obj = new JSONObject(json);
        JSONObject current = obj.getJSONObject("current");
        JSONObject daily = obj.getJSONObject("daily");
        return new WeatherData(
            current.getDouble("temperature_2m"),
            current.getInt("weather_code"),
            current.getDouble("cloud_cover"),
            daily.getJSONArray("sunrise").getString(0),
            daily.getJSONArray("sunset").getString(0)
        );
    }

    private AirQualityData parseAqi(String json) throws Exception {
        JSONObject obj = new JSONObject(json);
        return new AirQualityData(obj.getJSONObject("current").getInt("us_aqi"));
    }
}
