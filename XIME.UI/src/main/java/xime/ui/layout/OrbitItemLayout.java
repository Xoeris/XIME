package xime.ui.layout;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import xime.R;

import android.graphics.Outline;
import android.view.ViewOutlineProvider;
import xime.ui.view.ImageView;
import xime.ui.view.View;

public class OrbitItemLayout extends Layout {

    private ImageView imageView;
    private View overlay;
    private boolean isFocused = false;
    private float orbitProgress = 0f; // -1 to 1 based on center focus
    private float cornerRadius = 40f; // Default high corner radius for Orbit look

    public OrbitItemLayout(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public OrbitItemLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public OrbitItemLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(android.view.View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(imageView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        overlay = new View(context);
        overlay.setBackgroundColor(0xAA000000);
        overlay.setVisibility(VISIBLE);
        addView(overlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.OrbitView); // fallback since styleable
                                                                                         // renamed or doesn't matter
                                                                                         // much for this
            int imageRes = a.getResourceId(R.styleable.OrbitView_imageSource, -1);
            if (imageRes != -1) {
                imageView.setImageResource(imageRes);
            }
            // We could also read cornerRadius from attrs if added to
            // xime.media.R.styleable.OrbitView
            a.recycle();
        }
    }

    public void setImageResource(int resId) {
        imageView.setImageResource(resId);
    }

    public void setOrbitProgress(float progress) {
        this.orbitProgress = progress;
        float absProgress = Math.abs(progress);

        // Dynamic overlay opacity based on progress
        // Center (0) is fully visible, edges (1+) are darkened
        float overlayAlpha = Math.min(0.7f, absProgress * 0.5f);
        overlay.setAlpha(overlayAlpha);
        overlay.setVisibility(overlayAlpha > 0 ? VISIBLE : GONE);
    }

    public float getOrbitProgress() {
        return orbitProgress;
    }

    public void setFocused(boolean focused) {
        this.isFocused = focused;
        // Focused items have no overlay
        if (focused) {
            overlay.setVisibility(GONE);
        }
    }

    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        invalidateOutline();
    }

    @Override
    public boolean isFocused() {
        return isFocused;
    }
}

