package xime.core.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import xime.core.log.ILogger;

public class NetClient {
    private static final int TIMEOUT = 8000;
    private final ILogger logger;

    public NetClient(ILogger logger) {
        this.logger = logger;
    }

    public String get(String urlString, boolean retry) throws Exception {
        int attempts = 0;
        int maxAttempts = retry ? 3 : 1;
        long backoff = 1000;

        while (attempts < maxAttempts) {
            HttpURLConnection conn = null;
            try {
                logger.i("NetClient", "GET: " + urlString + " (Attempt " + (attempts + 1) + ")");
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) response.append(line);
                    in.close();
                    return response.toString();
                } else {
                    throw new Exception("HTTP " + responseCode);
                }
            } catch (Exception e) {
                attempts++;
                if (attempts >= maxAttempts) {
                    logger.e("NetClient", "Max retries reached for " + urlString, e);
                    throw e;
                }
                logger.d("NetClient", "Retry in " + backoff + "ms...");
                Thread.sleep(backoff);
                backoff *= 2;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return null;
    }
}
