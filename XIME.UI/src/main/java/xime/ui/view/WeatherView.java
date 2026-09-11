package xime.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import xime.R;
import xime.ui.bar.LinearProgressBar;
import xime.ui.menu.FooterMenu;
import xime.ui.layout.Layout;
import xime.ui.layout.BlurLayout;
import xime.ui.drawable.SunPathDrawable;

/**
 * * High-fidelity Weather Widget based on Xoeris Augmented Experience design.
 * Built using strictly XIME.UI base components.
 */
public class WeatherView extends Layout {

    public enum WeatherMode { CLEAR, CLOUDY, RAINY, STORMY }

    private TextView locationText;
    private TextView tempText;
    private TextView conditionText;
    private TextView timeText;
    private TextView dateText;
    private PictureView userAvatar;
    private View sunPathView;
    private View cloudLayerView;
    private LinearProgressBar aqiBar;
    private LinearProgressBar cloudBar;
    private TextView aqiValue;
    private TextView aqiStatus;
    private TextView cloudValue;
    private TextView cloudStatus;
    private FooterMenu footerMenu;
    private BlurLayout addButton;

    private SunPathDrawable sunPathDrawable;
    private CloudLayerDrawable cloudLayerDrawable;
    private ValueAnimator sunAnimator;
    private ValueAnimator glowAnimator;
    private ValueAnimator cloudDriftAnimator;
    private WeatherMode currentMode = WeatherMode.CLEAR;

    public WeatherView(@NonNull Context context) {
        this(context, null);
    }

    public WeatherView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeatherView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.widget_weather, this, true);

        // Bind Views using XIME.UI custom components
        locationText = (TextView) findViewById(R.id.weather_location_text);
        tempText = (TextView) findViewById(R.id.weather_temp);
        conditionText = (TextView) findViewById(R.id.weather_condition);
        timeText = (TextView) findViewById(R.id.weather_time);
        dateText = (TextView) findViewById(R.id.weather_date);
        userAvatar = (PictureView) findViewById(R.id.weather_user_avatar);
        sunPathView = (View) findViewById(R.id.weather_sun_path);
        cloudLayerView = (View) findViewById(R.id.weather_cloud_layer);
        aqiBar = (LinearProgressBar) findViewById(R.id.weather_aqi_bar);
        cloudBar = (LinearProgressBar) findViewById(R.id.weather_cloud_bar);
        aqiValue = (TextView) findViewById(R.id.weather_aqi_value);
        aqiStatus = (TextView) findViewById(R.id.weather_aqi_status);
        cloudValue = (TextView) findViewById(R.id.weather_cloud_value);
        cloudStatus = (TextView) findViewById(R.id.weather_cloud_status);
        footerMenu = (FooterMenu) findViewById(R.id.weather_footer_menu);
        addButton = (BlurLayout) findViewById(R.id.weather_add_button);

        if (footerMenu != null) {
            footerMenu.setSelectedItemId(R.id.nav_home);
        }

        sunPathDrawable = new SunPathDrawable();
        cloudLayerDrawable = new CloudLayerDrawable();

        setupDesign();
    }

    private void setupDesign() {
        setBackground(new android.graphics.drawable.Drawable() {
            @Override
            public void draw(@NonNull Canvas canvas) {
                RectF rect = new RectF(0, 0, getWidth(), getHeight());
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                Shader shader = new LinearGradient(0, 0, getWidth(), getHeight(),
                        new int[]{Color.parseColor("#4285F4"), Color.parseColor("#90CAF9"), Color.parseColor("#FFFFFF")},
                        new float[]{0.0f, 0.5f, 1.0f}, Shader.TileMode.CLAMP);
                paint.setShader(shader);
                float radius = dpToPx(32);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {}
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        });

        if (sunPathView != null) {
            sunPathView.setBackground(sunPathDrawable);
            sunPathView.post(() -> animateSunTo(0.5f, 1500));
        }
        if (cloudLayerView != null) cloudLayerView.setBackground(cloudLayerDrawable);

        startAmbientGlow();
        startCloudDrift();
    }

    private void startAmbientGlow() {
        glowAnimator = ValueAnimator.ofFloat(0.6f, 1.0f);
        glowAnimator.setDuration(3000);
        glowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimator.addUpdateListener(animation -> {
            if (sunPathDrawable != null) {
                sunPathDrawable.setAlpha((int) ((float) animation.getAnimatedValue() * 255));
            }
        });
        glowAnimator.start();
    }

    private void startCloudDrift() {
        if (cloudDriftAnimator != null) cloudDriftAnimator.cancel();
        cloudDriftAnimator = ValueAnimator.ofFloat(0f, 1f);
        cloudDriftAnimator.setDuration(60000); // Slower drift for a more natural look
        cloudDriftAnimator.setInterpolator(new LinearInterpolator());
        cloudDriftAnimator.setRepeatCount(ValueAnimator.INFINITE);
        cloudDriftAnimator.addUpdateListener(anim -> {
            if (cloudLayerDrawable != null) {
                cloudLayerDrawable.setOffset((float) anim.getAnimatedValue());
            }
        });
        cloudDriftAnimator.start();
    }

    public void setWeather(String location, String temp, String condition, String time, String date) {
        if (locationText != null && location != null) locationText.setText(location);
        if (tempText != null && temp != null) tempText.setText(temp);
        if (conditionText != null && condition != null) conditionText.setText(condition);
        if (timeText != null && time != null) timeText.setText(time);
        if (dateText != null && date != null) dateText.setText(date);
    }

    public void setAQI(int value, String status, float progress) {
        if (aqiValue != null) aqiValue.setText(String.valueOf(value));
        if (aqiStatus != null) aqiStatus.setText(status);
        if (aqiBar != null) aqiBar.setProgress(value);
    }

    public void setCloudCover(int percentage, String status) {
        if (cloudValue != null) cloudValue.setText(percentage + "%");
        if (cloudStatus != null) cloudStatus.setText(status);
        if (cloudBar != null) cloudBar.setProgress(percentage);
        if (cloudLayerDrawable != null) cloudLayerDrawable.setCoverage(percentage / 100f);
    }

    public void setWeatherMode(WeatherMode mode) {
        this.currentMode = mode;
        if (cloudLayerDrawable != null) {
            cloudLayerDrawable.setMode(mode);
        }
    }

    public void animateSunTo(float targetProgress, long durationMs) {
        if (sunAnimator != null) sunAnimator.cancel();
        float current = sunPathDrawable != null ? sunPathDrawable.getProgress() : 0f;
        sunAnimator = ValueAnimator.ofFloat(current, targetProgress);
        sunAnimator.setDuration(durationMs);
        sunAnimator.setInterpolator(new DecelerateInterpolator());
        sunAnimator.addUpdateListener(animation -> {
            if (sunPathDrawable != null) {
                sunPathDrawable.setProgress((float) animation.getAnimatedValue());
            }
        });
        sunAnimator.start();
    }

    public void setSunProgress(float progress) {
        if (sunPathDrawable != null) sunPathDrawable.setProgress(progress);
    }

    public void destroy() {
        if (sunAnimator != null) {
            sunAnimator.cancel();
            sunAnimator = null;
        }
        if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator = null;
        }
        if (cloudDriftAnimator != null) {
            cloudDriftAnimator.cancel();
            cloudDriftAnimator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        destroy();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private static class CloudLayerDrawable extends Drawable {
        private static class Puff {
            float xRatio, yRatio, radiusRatio, speed, alpha, driftOffset;
            Puff(float x, float y, float r, float s, float a, float d) {
                xRatio = x; yRatio = y; radiusRatio = r; speed = s; alpha = a; driftOffset = d;
            }
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Puff> puffs = new ArrayList<>();
        private float offset = 0f;       
        private float coverage = 0.12f;
        private int cloudBaseColor = Color.WHITE;

        public CloudLayerDrawable() {
            Random r = new Random();
            // Create a randomized set of puffs for a soft, misty look
            for (int i = 0; i < 10; i++) {
                puffs.add(new Puff(
                    r.nextFloat(),            // random x start
                    0.2f + r.nextFloat() * 0.8f,  // concentrate clouds lower to keep sky blue visible
                    0.15f + r.nextFloat() * 0.2f, // scaled down radius ratio (was 0.5-0.9)
                    0.05f + r.nextFloat() * 0.2f, // slower, more ambient speed
                    0.05f + r.nextFloat() * 0.15f, // much lower base alpha for mist effect
                    r.nextFloat()             // random initial phase
                ));
            }
        }

        public void setOffset(float offset) { this.offset = offset; invalidateSelf(); }

        public void setCoverage(float coverage) {
            this.coverage = Math.max(0f, Math.min(1f, coverage));
            invalidateSelf();
        }

        public void setMode(WeatherMode mode) {
            switch (mode) {
                case STORMY: cloudBaseColor = Color.parseColor("#4A4A4A"); break;
                case RAINY: cloudBaseColor = Color.parseColor("#808080"); break;
                case CLOUDY: cloudBaseColor = Color.parseColor("#E0E0E0"); break;
                default: cloudBaseColor = Color.WHITE; break;
            }
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (coverage <= 0.01f) return; 

            int w = getBounds().width();
            int h = getBounds().height();

            for (Puff p : puffs) {
                // Random drift calculation for looping
                float x = ((p.xRatio + (offset + p.driftOffset) * p.speed) % 1.4f) * w - 0.2f * w;
                float y = p.yRatio * h;
                float radius = p.radiusRatio * w * (0.6f + coverage * 0.8f);
                float puffAlpha = p.alpha * coverage * 2.0f; 
                puffAlpha = Math.min(puffAlpha, 0.3f); // Cap alpha to 30% to keep it misty

                int color = Color.argb(
                    (int) (puffAlpha * 255),
                    Color.red(cloudBaseColor),
                    Color.green(cloudBaseColor),
                    Color.blue(cloudBaseColor)
                );

                Shader shader = new RadialGradient(
                    x, y, radius,
                    new int[]{ color, Color.TRANSPARENT },
                    new float[]{ 0f, 1f },
                    Shader.TileMode.CLAMP
                );
                paint.setShader(shader);
                canvas.drawCircle(x, y, radius, paint);
            }
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

