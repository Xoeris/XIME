package xime.ui.layout;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;

import java.lang.reflect.Method;
import java.util.Random;

import xime.R;
import xime.core.theme.ThemeManager;

import xime.graphics.shader.blur.AdaptiveBlur;
import xime.graphics.shader.blur.LegacyBlur;
import xime.ui.manager.BlurManager;

public class BlurLayout extends Layout {
    private static Bitmap sharedNoiseBitmap;
    private LegacyBlur.ThemeMode activeTheme;
    private ThemeMode mManualThemeMode = ThemeMode.AUTO;
    private final Paint bitmapPaint;
    private int blurIntervalMs = 16;
    private float blurRadius = 15.0f;
    private View blurRootView;
    private View[] excludedContentViews;
    private View[] excludedLayers;
    private boolean excludeOtherBlurLayouts = true;
    private BitmapShader blurShader;
    private BlurType blurType = BlurType.GLASS;
    private BlurManager blurManager;
    private Bitmap blurredBackground;
    private float bottomLeftRadius;
    private float bottomRightRadius;
    private Canvas captureCanvas;
    private float captureScale = 0.25f;
    private final Path clipPath = new Path();
    private final float[] cornerRadii = new float[8];
    public final LegacyBlur crystal = new LegacyBlur();
    public final AdaptiveBlur adaptive = new AdaptiveBlur();
    private final Paint glassTintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean isCapturing = false;
    private long lastBlurTime = 0L;
    private float noiseAlpha = 0.016f;
    private final Paint noisePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean pauseUpdates = false;
    private final RectF rectF = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private boolean showBorder = true;
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** ThemeManager listener, held as a field so we can remove it in onDetachedFromWindow. */
    private final ThemeManager.OnThemeChangedListener mThemeListener = mode -> {
        if (mManualThemeMode == ThemeMode.AUTO) {
            updateTheme(mode == ThemeManager.Mode.DARK ? LegacyBlur.ThemeMode.DARK : LegacyBlur.ThemeMode.LIGHT);
            notifyChildrenThemeRefresh();
            invalidate();
        }
    };
    private final BlurManager.OnBlurChangedListener mBlurChangedListener = isBlurDisabled -> {
        if (isBlurDisabled) {
            recycleBlur();
        }
        invalidate();
    };
    private float topLeftRadius;
    private float topRightRadius;
    private BlurCallback blurCallback;

    private final ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            if (blurManager != null && blurManager.isBlurDisabled()) {
                return true;
            }
            if (isShown() && getVisibility() == VISIBLE && !isCapturing && !pauseUpdates) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBlurTime >= blurIntervalMs) {
                    lastBlurTime = currentTime;
                    refreshBlur();
                }
            }
            return true;
        }
    };

    public enum BlurType {
        GLASS,
        CRYSTAL,
        NONE
    }

    public enum ThemeMode {
        DARK,
        LIGHT,
        AUTO
    }

    public BlurLayout(Context context) {
        super(context);
        this.bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        init(context, null, 0);
    }

    public BlurLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        init(context, attrs, 0);
    }

    public BlurLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        init(context, attrs, defStyleAttr);
    }

    public BlurLayout(android.view.View rootView, android.os.Handler handler) {
        this(rootView.getContext());
        this.blurRootView = rootView;
    }

    private float getSafeDimensionOrFloat(TypedArray a, int index, float defaultValue) {
        if (!a.hasValue(index)) return defaultValue;
        TypedValue tv = new TypedValue();
        a.getValue(index, tv);
        if (tv.type == TypedValue.TYPE_DIMENSION) {
            return a.getDimension(index, defaultValue);
        } else if (tv.type == TypedValue.TYPE_FLOAT || tv.type == TypedValue.TYPE_INT_DEC) {
            return a.getFloat(index, defaultValue);
        }
        try {
            return a.getDimension(index, defaultValue);
        } catch (Exception e) {
            try {
                return a.getFloat(index, defaultValue);
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        this.blurManager = new BlurManager(context);

        if (Layout.isLowEndDevice(context)) {
            this.blurIntervalMs = 64;
            this.captureScale = 0.15f;
        }

        TypedArray a = context.obtainStyledAttributes(attrs, xime.graphics.R.styleable.BlurView, defStyleAttr, 0);
        try {
            int blurTypeOrdinal = a.getInteger(xime.graphics.R.styleable.BlurView_xoerisBlurType, 0);
            this.blurType = (blurTypeOrdinal >= 0 && blurTypeOrdinal < BlurType.values().length) ? BlurType.values()[blurTypeOrdinal] : BlurType.GLASS;
            this.blurRadius = getSafeDimensionOrFloat(a, xime.graphics.R.styleable.BlurView_xoerisBlurRadius, 15.0f);
            float cornerRadius = getSafeDimensionOrFloat(a, xime.graphics.R.styleable.BlurView_xoerisCornerRadius, dp(32.0f));
            this.topLeftRadius = getSafeDimensionOrFloat(a, xime.graphics.R.styleable.BlurView_xoerisTopLeftCornerRadius, cornerRadius);
            this.topRightRadius = getSafeDimensionOrFloat(a, xime.graphics.R.styleable.BlurView_xoerisTopRightCornerRadius, cornerRadius);
            this.bottomLeftRadius = getSafeDimensionOrFloat(a, xime.graphics.R.styleable.BlurView_xoerisBottomLeftCornerRadius, cornerRadius);
            this.bottomRightRadius = getSafeDimensionOrFloat(a, xime.graphics.R.styleable.BlurView_xoerisBottomRightCornerRadius, cornerRadius);
            this.showBorder = a.getBoolean(xime.graphics.R.styleable.BlurView_xoerisShowBorder, true);
            this.excludeOtherBlurLayouts = a.getBoolean(xime.graphics.R.styleable.BlurView_xoerisExcludeOtherBlurLayouts, true);
            
            int themeModeOrdinal = a.getInteger(xime.graphics.R.styleable.BlurView_xoerisThemeMode, 2); // default to AUTO
            this.mManualThemeMode = ThemeMode.values()[themeModeOrdinal];
        } finally {
            a.recycle();
        }

        this.strokePaint.setStyle(Paint.Style.STROKE);
        this.strokePaint.setStrokeWidth(dp(1.0f));
        updateCornerRadii();
        setupNoise();

        if (mManualThemeMode == ThemeMode.AUTO) {
            // Use ThemeManager if already initialised; fall back to direct uiMode read if init
            // hasn't been called yet (e.g. during layout inflation in tests or preview).
            boolean darkAtInit;
            try {
                darkAtInit = ThemeManager.get().isDark();
            } catch (IllegalStateException e) {
                int nightMode = context.getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                darkAtInit = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            }
            updateTheme(darkAtInit ? LegacyBlur.ThemeMode.DARK : LegacyBlur.ThemeMode.LIGHT);
        } else {
            updateTheme(mManualThemeMode == ThemeMode.DARK ? LegacyBlur.ThemeMode.DARK : LegacyBlur.ThemeMode.LIGHT);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(this.preDrawListener);
        BlurManager.addListener(this.mBlurChangedListener);
        // Sync to the current ThemeManager state on attach, then subscribe for live updates.
        try {
            ThemeManager tm = ThemeManager.get();
            updateTheme(tm.isDark() ? LegacyBlur.ThemeMode.DARK : LegacyBlur.ThemeMode.LIGHT);
            tm.addListener(mThemeListener);
        } catch (IllegalStateException ignored) {
            // ThemeManager not yet initialised, theme stays at the value set during init().
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnPreDrawListener(this.preDrawListener);
        BlurManager.removeListener(this.mBlurChangedListener);
        try {
            ThemeManager.get().removeListener(mThemeListener);
        } catch (IllegalStateException ignored) {}
        recycleBlur();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        this.rectF.set(0.0f, 0.0f, w, h);
        updateCornerRadii();
    }

    @Override
    public void draw(Canvas canvas) {
        if (this.isCapturing) return;
        // A sibling or parent BlurLayout is currently capturing the background.
        // Skip drawing entirely during capture without mutating View visibility,
        // preventing layout invalidation loops and glowing repaint artifacts.
        if (sGlassCaptureDepth > 0 && this.excludeOtherBlurLayouts) {
            return;
        }
        canvas.save();
        canvas.clipPath(this.clipPath);
        super.draw(canvas);
        canvas.restore();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getWidth() <= 0 || getHeight() <= 0) return;

        // Render 100% transparently during capture if not skipped
        if (sGlassCaptureDepth > 0) {
            return;
        }

        if (this.blurManager != null && this.blurManager.isBlurDisabled()) {
            canvas.drawPath(this.clipPath, this.glassTintPaint);
            if (this.showBorder) canvas.drawPath(this.clipPath, this.strokePaint);
            return;
        }

        if (this.blurType != BlurType.NONE && this.blurredBackground != null && !this.blurredBackground.isRecycled() && this.blurShader != null) {
            LegacyBlur.ThemeMode themeToApply = this.activeTheme;
            if (themeToApply == null) themeToApply = getActiveTheme();

            if (this.blurManager.isAdaptive()) {
                this.adaptive.setBlurV2Type(this.blurManager.getAdaptiveBlurType());
                this.adaptive.draw(canvas, this.rectF, this.clipPath, this.blurredBackground, this.topLeftRadius, this.rectF.centerX(), this.rectF.centerY(), themeToApply == LegacyBlur.ThemeMode.DARK ? AdaptiveBlur.ThemeMode.DARK : AdaptiveBlur.ThemeMode.LIGHT);
                return;
            } else {
                xime.graphics.shader.blur.LegacyBlur.BlurType legacyType = this.blurManager.getLegacyBlurType();
                if (legacyType == xime.graphics.shader.blur.LegacyBlur.BlurType.CRYSTAL) {
                    this.crystal.drawLiquidGlass(canvas, this.blurredBackground, this.rectF, this.clipPath, this.topLeftRadius, this.rectF.centerX(), this.rectF.centerY(), themeToApply);
                    return;
                } else {
                    this.shaderMatrix.setScale(this.rectF.width() / this.blurredBackground.getWidth(), this.rectF.height() / this.blurredBackground.getHeight());
                    this.blurShader.setLocalMatrix(this.shaderMatrix);
                    this.bitmapPaint.setShader(this.blurShader);
                    canvas.drawPath(this.clipPath, this.bitmapPaint);
                }
            }
        }

        canvas.drawPath(this.clipPath, this.glassTintPaint);
        canvas.drawPath(this.clipPath, this.noisePaint);
        if (this.showBorder) canvas.drawPath(this.clipPath, this.strokePaint);
    }

    public void refreshBlur() {
        if (this.blurType == BlurType.NONE) return;
        if (this.blurManager != null && this.blurManager.isBlurDisabled()) return;
        View target = this.blurRootView;
        if (target == null) {
            Object parent = getParent();
            if (parent instanceof View) target = (View) parent;
        }
        if (target == null) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int bw = Math.max(1, (int) (w * this.captureScale));
        int bh = Math.max(1, (int) (h * this.captureScale));

        if (this.blurredBackground == null || this.blurredBackground.getWidth() != bw || this.blurredBackground.getHeight() != bh) {
            try {
                recycleBlur();
                this.blurredBackground = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                this.blurShader = new BitmapShader(this.blurredBackground, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                this.captureCanvas = new Canvas(this.blurredBackground);
            } catch (Exception e) {
                return;
            }
        }

        if (this.captureCanvas == null && this.blurredBackground != null) {
            try {
                this.captureCanvas = new Canvas(this.blurredBackground);
            } catch (Exception e) {
                return;
            }
        }
        if (this.captureCanvas == null) return;

        int[] targetLoc = new int[2];
        int[] myLoc = new int[2];
        target.getLocationOnScreen(targetLoc);
        getLocationOnScreen(myLoc);

        this.captureCanvas.save();
        this.captureCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        this.captureCanvas.scale(this.captureScale, this.captureScale);
        this.captureCanvas.translate(targetLoc[0] - myLoc[0], targetLoc[1] - myLoc[1]);

        try {
            this.isCapturing = true;
            incrementGlassCaptureDepth();
            Drawable originalBg = target.getBackground();
            if (target == this) target.setBackground(null);

            int[] excludedVisibility = hideExcludedContentViews();
            int[] excludedLayersVis = hideViews(this.excludedLayers);

            try {
                target.draw(this.captureCanvas);
            } finally {
                restoreViews(this.excludedLayers, excludedLayersVis);
                restoreExcludedContentViews(excludedVisibility);
            }
            if (target == this) target.setBackground(originalBg);
            this.isCapturing = false;
            this.captureCanvas.restore();

            // Both the Legacy (Glass/Crystal) and Adaptive (Obscura/Aura) draw paths do their
            // own mesh-warp/tint/rim compositing every onDraw() frame from a *pre-blurred* raw
            // capture - refreshBlur() only needs to blur the raw pixels once per capture
            // interval here. AdaptiveBlur.applyToBitmap() is a separate, complete standalone
            // pass (blur + full composite in one call) meant for callers who don't have their
            // own canvas/onDraw loop - it was previously being called here *in addition to*
            // adaptive.draw() in onDraw(), which meant Aura's mesh-warp and tint were being
            // applied twice per frame (once baked into blurredBackground, then again on top of
            // that already-warped result). That double-application is what was producing the
            // broken/garbled Aura render. Just blur the raw pixels here for both paths; the
            // draw() call in onDraw() owns compositing, exactly once.
            int rawRadius = (int) (this.blurRadius * 2.8f);
            if (this.blurManager.isAdaptive() && this.blurManager.getAdaptiveBlurType() == AdaptiveBlur.XoerisBlurV2Type.AURA) {
                // Aura intentionally runs a lighter blur than Legacy/Obscura - see
                // AdaptiveBlur#setAuraBlurScale. Only scales the radius for AURA; GLASS,
                // CRYSTAL and OBSCURA are unaffected.
                rawRadius = Math.round(rawRadius * this.adaptive.getAuraBlurScale());
            }
            LegacyBlur.stackBlur(this.blurredBackground, Math.max(1, rawRadius));
            invalidate();
        } catch (Throwable th) {
            this.isCapturing = false;
            try { this.captureCanvas.restore(); } catch (Exception ignored) {}
        } finally {
            decrementGlassCaptureDepth();
        }
    }

    public void setExcludeOtherBlurLayouts(boolean exclude) {
        this.excludeOtherBlurLayouts = exclude;
        invalidate();
    }

    public boolean isExcludeOtherBlurLayouts() {
        return this.excludeOtherBlurLayouts;
    }

    public void setExcludedLayers(View... layers) {
        this.excludedLayers = layers;
    }

    public View[] getExcludedLayers() {
        return this.excludedLayers;
    }

    private int[] hideViews(View[] views) {
        if (views == null || views.length == 0) return null;
        int[] prevVisibility = new int[views.length];
        for (int i = 0; i < views.length; i++) {
            if (views[i] != null) {
                prevVisibility[i] = views[i].getVisibility();
                views[i].setVisibility(INVISIBLE);
            }
        }
        return prevVisibility;
    }

    private void restoreViews(View[] views, int[] prevVisibility) {
        if (views == null || prevVisibility == null) return;
        for (int i = 0; i < views.length && i < prevVisibility.length; i++) {
            if (views[i] != null) {
                views[i].setVisibility(prevVisibility[i]);
            }
        }
    }

    /**
     * Views registered here (typically this card's own foreground text/content) are hidden
     * while this panel captures "what's behind it", so its own content never gets baked into
     * its own blur source. See GlassLayout#setExcludedContentViews for the full explanation.
     */
    public void setExcludedContentViews(View... views) {
        this.excludedContentViews = views;
    }

    private int[] hideExcludedContentViews() {
        if (this.excludedContentViews == null || this.excludedContentViews.length == 0) return null;
        int[] prevVisibility = new int[this.excludedContentViews.length];
        for (int i = 0; i < this.excludedContentViews.length; i++) {
            View v = this.excludedContentViews[i];
            if (v != null) {
                prevVisibility[i] = v.getVisibility();
                v.setVisibility(INVISIBLE);
            }
        }
        return prevVisibility;
    }

    private void restoreExcludedContentViews(int[] prevVisibility) {
        if (prevVisibility == null || this.excludedContentViews == null) return;
        for (int i = 0; i < this.excludedContentViews.length && i < prevVisibility.length; i++) {
            View v = this.excludedContentViews[i];
            if (v != null) v.setVisibility(prevVisibility[i]);
        }
    }

    private void updateTheme(LegacyBlur.ThemeMode theme) {
        this.activeTheme = theme;
        if (theme == LegacyBlur.ThemeMode.DARK) {
            this.glassTintPaint.setColor(Color.argb(55, 0, 0, 0));
            this.strokePaint.setColor(Color.argb(27, 255, 255, 255));
        } else {
            this.glassTintPaint.setColor(Color.argb(85, 255, 255, 255));
            this.strokePaint.setColor(Color.argb(18, 0, 0, 0));
        }
    }


    private void updateCornerRadii() {
        this.cornerRadii[0] = this.cornerRadii[1] = this.topLeftRadius;
        this.cornerRadii[2] = this.cornerRadii[3] = this.topRightRadius;
        this.cornerRadii[4] = this.cornerRadii[5] = this.bottomRightRadius;
        this.cornerRadii[6] = this.cornerRadii[7] = this.bottomLeftRadius;
        if (this.rectF.width() > 0.0f && this.rectF.height() > 0.0f) {
            this.clipPath.reset();
            this.clipPath.addRoundRect(this.rectF, this.cornerRadii, Path.Direction.CW);
        }

        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                if (topLeftRadius != topRightRadius || topLeftRadius != bottomLeftRadius || topLeftRadius != bottomRightRadius) {
                    outline.setRect(0, 0, view.getWidth(), view.getHeight());
                } else {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), topLeftRadius);
                }
            }
        });
        setClipToOutline(true);
        invalidate();
    }

    private void setupNoise() {
        if (sharedNoiseBitmap == null) {
            sharedNoiseBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            Random r = new Random(42L);
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    sharedNoiseBitmap.setPixel(x, y, Color.argb(r.nextInt(5) + 1, 255, 255, 255));
                }
            }
        }
        this.noisePaint.setShader(new BitmapShader(sharedNoiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        this.noisePaint.setAlpha((int) (this.noiseAlpha * 255.0f));
    }

    private void notifyChildrenThemeRefresh() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            try {
                Method m = child.getClass().getMethod("refreshTheme");
                m.invoke(child);
            } catch (Exception ignored) {}
        }
    }

    private void recycleBlur() {
        if (this.blurredBackground != null && !this.blurredBackground.isRecycled()) {
            this.blurredBackground.recycle();
        }
        this.blurredBackground = null;
        this.blurShader = null;
        this.captureCanvas = null;
    }

    public void setBlurRootView(View view) {
        this.blurRootView = view;
        invalidate();
    }

    public void refreshImmediately() {
        recycleBlur();
        refreshBlur();
    }

    /**
     * Returns the current effective theme as a {@link LegacyBlur.ThemeMode} rendering hint.
     * Delegates to {@link ThemeManager} as the single source of truth.
     *
     * <p>Callers such as {@code HeaderMenu.refreshTheme()} should prefer calling
     * {@code ThemeManager.get().isDark()} directly; this method is kept for API
     * compatibility with existing callers.
     */
    public LegacyBlur.ThemeMode getActiveTheme() {
        try {
            return ThemeManager.get().isDark() ? LegacyBlur.ThemeMode.DARK : LegacyBlur.ThemeMode.LIGHT;
        } catch (IllegalStateException e) {
            // ThemeManager not yet initialised, fall back to the cached activeTheme.
            return this.activeTheme != null ? this.activeTheme : LegacyBlur.ThemeMode.LIGHT;
        }
    }

    public void setThemeMode(ThemeMode mode) {
        if (mode == null) return;
        this.mManualThemeMode = mode;
        if (mode == ThemeMode.AUTO) {
            try {
                ThemeManager tm = ThemeManager.get();
                updateTheme(tm.isDark() ? LegacyBlur.ThemeMode.DARK : LegacyBlur.ThemeMode.LIGHT);
            } catch (IllegalStateException ignored) {}
        } else {
            updateTheme(mode == ThemeMode.DARK ? LegacyBlur.ThemeMode.DARK : LegacyBlur.ThemeMode.LIGHT);
        }
        notifyChildrenThemeRefresh();
        invalidate();
    }
    
    public ThemeMode getThemeMode() {
        return mManualThemeMode;
    }

    public void setBlurType(BlurType type) {
        this.blurType = type;
        invalidate();
    }

    public void setPauseUpdates(boolean pause) {
        this.pauseUpdates = pause;
    }

    public void setSkipBlur(boolean skip) {
        this.blurType = skip ? BlurType.NONE : BlurType.GLASS;
        invalidate();
    }

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value);
    }

    public void setBlurRatio(float ratio, boolean animate) {
        setAlpha(ratio);
    }

    public void showBlur(boolean show, boolean animate, boolean isNPV, boolean isSwitch) {
        setVisibility(show ? VISIBLE : GONE);
        setAlpha(show ? 1.0f : 0.0f);
    }


    public void setBlurRadius(float radius) {
        this.blurRadius = radius;
        this.crystal.setBlurRadius(radius);
        invalidate();
    }

    public void setCornerRadius(float radius) {
        this.topLeftRadius = radius;
        this.topRightRadius = radius;
        this.bottomLeftRadius = radius;
        this.bottomRightRadius = radius;
        updateCornerRadii();
    }

    /** Sets top-left and top-right radii only, leaving bottom radii unchanged. */
    public void setTopCornerRadius(float radius) {
        this.topLeftRadius = radius;
        this.topRightRadius = radius;
        updateCornerRadii();
    }

    /** Sets bottom-left and bottom-right radii only, leaving top radii unchanged. */
    public void setBottomCornerRadius(float radius) {
        this.bottomLeftRadius = radius;
        this.bottomRightRadius = radius;
        updateCornerRadii();
    }

    public float getCornerRadius() {
        return this.topLeftRadius;
    }

    public void setGlassAlpha(float alpha) {
        setAlpha(alpha);
    }

    public void setGlassTint(int color) {
        this.glassTintPaint.setColor(color);
        invalidate();
    }

    public void setShowBorder(boolean show) {
        this.showBorder = show;
        invalidate();
    }

    public void setShowHighlight(boolean show) {
        invalidate();
    }

    public void setDisableGlass(boolean disable) {
        if (disable) {
            this.blurType = BlurType.NONE;
        }
        invalidate();
    }

    public boolean isGlassDisabled() {
        return this.blurType == BlurType.NONE;
    }

    public void setGlassEnabled(boolean enabled) {
        setDisableGlass(!enabled);
    }

    public boolean isGlassEnabled() {
        return !isGlassDisabled();
    }

    public void set3DBevel(float plateauStart, float steepness) {
        this.crystal.set3DBevel(plateauStart, steepness);
        this.adaptive.setBevel(plateauStart, steepness);
        invalidate();
    }

    public void setHighlightPosition(float x, float y) {
        invalidate();
    }

    public void setForceInitialBlurWhenPaused(boolean force) {
        invalidate();
    }

    public void setCallback(BlurCallback callback) {
        this.blurCallback = callback;
    }

    public interface BlurCallback {
        void setBlurRatio(float ratio);
        void notifyBlurRatioChanged(float ratio);
        void setSwitchedFromNPV(boolean switched);
    }
}

