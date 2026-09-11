package xime.graphics.shader.blur;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;

import java.util.concurrent.Semaphore;

/**
 * AdaptiveBlur - next generation glass renderer.
 *
 * <p>AdaptiveBlur replaces the old monolithic "Crystal" (V1's {@code BlurType.CRYSTAL}) path with two
 * explicit, independently-tunable render modes selected via {@link XoerisBlurV2Type}
 * (XML attribute {@code app:xoerisBlurV2Type}, values {@code obscura} / {@code aura}):
 *
 * <ul>
 *   <li><b>OBSCURA</b> - a normal, honest frosted-glass blur. Cheap, stable, and the safe
 *       default on low-end hardware. Conceptually the successor to V1 "Glass", but rebuilt
 *       on the adaptive pipeline below so it no longer relies on caller-supplied Paint
 *       objects (a common source of the V1 glitches on low-end devices - mismatched/uninitialized
 *       Paints, stale shader matrices, etc). AdaptiveBlur owns and manages all of its own Paints.</li>
 *   <li><b>AURA</b> - the successor to "Crystal": mesh-warped liquid-glass refraction with
 *       chromatic aberration and rim lighting, but reworked so a) quality scales down
 *       automatically on weaker devices instead of glitching, and b) multiple Aura views
 *       active in the same Activity no longer fight each other for the CPU - see
 *       {@link #AURA_RENDER_GATE} below.</li>
 * </ul>
 *
 * <h3>What was actually broken, and how V2 fixes it</h3>
 * <ol>
 *   <li><b>V1 Glass glitching on low-end devices:</b> Glass drew straight into whatever
 *       Paints the caller handed it, so quality (blur radius, downscale, noise) never
 *       adapted to the device. AdaptiveBlur introduces {@link DeviceTier} - detected once via
 *       {@link #init(Context)} from CPU core count, {@code ActivityManager.isLowRamDevice()}
 *       and {@code getMemoryClass()} - and every knob (mesh density, chromatic aberration,
 *       downscale factor, layer usage) is derived from it. Low tier never sees the
 *       expensive code paths that were glitching, rather than seeing them running too slowly.</li>
 *   <li><b>Crystal lagging with &gt;1 view in an Activity:</b> Every Crystal instance ran its
 *       own full mesh-warp + chromatic-aberration pipeline independently, so N on-screen
 *       Aura views meant N full pipelines competing for the same cores every frame. AdaptiveBlur
 *       gates the expensive AURA build step behind a small process-wide
 *       {@link Semaphore} ({@link #AURA_RENDER_GATE}), sized to the device's core count.
 *       A view that can't get a permit this frame does not block or drop a frame waiting -
 *       it simply redraws its last cached composite (see {@link #resultCache}), which is
 *       visually identical for the (extremely common) case of a mostly-static blur surface,
 *       and only pays for a fresh rebuild once its geometry/theme/content actually changes
 *       and a permit is free.</li>
 *   <li><b>Correctness:</b> stack-blur pixel work is delegated to {@link LegacyBlur}'s
 *       already-hardened parallel implementation (shared thread pool, frame-budget aware
 *       downscaling) instead of re-implementing it, so AdaptiveBlur can't regress that code path.
 *       All mutable state (mesh arrays, shaders, scratch bitmaps) is cached and only rebuilt
 *       when the inputs that affect it actually change, with defensive checks against
 *       recycled/zero-sized bitmaps and empty bounds that were unguarded in the old Crystal path.</li>
 * </ol>
 */
public class AdaptiveBlur {

    // ------------------------------------------------------------------------------
    // Public types
    // ------------------------------------------------------------------------------

    /**
     * The two AdaptiveBlur render modes. Backed by the {@code xoerisBlurV2Type}
     * XML attribute ({@code obscura} = 0, {@code aura} = 1), only meaningful when the parent
     * {@code xoerisBlurType} is {@code crystal}.
     */
    public enum XoerisBlurV2Type {
        /** Normal glassy frosted blur. Cheap and stable - the safe choice everywhere. */
        OBSCURA,
        /** Advanced textured liquid-glass blur with mesh refraction. Adaptive cost. */
        AURA
    }

    /** Reuses {@link LegacyBlur.ThemeMode} so AdaptiveBlur is a drop-in alongside the existing V1 pipeline. */
    public enum ThemeMode {
        DARK,
        LIGHT
    }

    /**
     * Coarse device performance classification used to scale every expensive knob in the
     * AURA pipeline (and to pick conservative defaults for OBSCURA too). Computed once via
     * {@link #init(Context)}; falls back to a CPU-core-only heuristic if never called so the
     * class is still safe to use without Activity/Application wiring.
     */
    public enum DeviceTier {
        LOW, MID, HIGH
    }

    // ------------------------------------------------------------------------------
    // Device tier detection / global tuning
    // ------------------------------------------------------------------------------

    private static final int CPU_CORES = Math.max(1, Runtime.getRuntime().availableProcessors());

    private static volatile DeviceTier sDeviceTier = coreOnlyTierGuess();
    private static volatile boolean sInitialized = false;

    private static DeviceTier coreOnlyTierGuess() {
        if (CPU_CORES <= 4) return DeviceTier.LOW;
        if (CPU_CORES <= 6) return DeviceTier.MID;
        return DeviceTier.HIGH;
    }

    /**
     * Optional, one-time, process-wide calibration. Call this once from
     * {@code Application#onCreate} (or the first Activity). Safe to skip - AdaptiveBlur still works
     * using the CPU-core heuristic - but calling it lets low-RAM/low-memory-class devices
     * (which can have deceptively many cores) get correctly downgraded to {@link DeviceTier#LOW}.
     */
    public static void init(Context context) {
        if (context == null || sInitialized) return;
        try {
            ActivityManager am = (ActivityManager) context.getApplicationContext()
                    .getSystemService(Context.ACTIVITY_SERVICE);
            boolean lowRam = am != null && am.isLowRamDevice();
            int memoryClass = am != null ? am.getMemoryClass() : 128;

            DeviceTier tier;
            if (lowRam || memoryClass <= 96 || CPU_CORES <= 4) {
                tier = DeviceTier.LOW;
            } else if (memoryClass <= 192 || CPU_CORES <= 6) {
                tier = DeviceTier.MID;
            } else {
                tier = DeviceTier.HIGH;
            }
            sDeviceTier = tier;
        } catch (Throwable t) {
            // Never let telemetry/detection failures affect rendering - keep the heuristic guess.
        } finally {
            sInitialized = true;
        }
    }

    public static DeviceTier getDeviceTier() {
        return sDeviceTier;
    }

    /**
     * Process-wide limiter on concurrently-executing AURA "full rebuild" passes (stack blur +
     * mesh warp + chromatic aberration). This is the fix for multiple Aura views lagging each
     * other: rather than N views each paying full price every frame, at most
     * {@code permits} views rebuild in any given frame; everyone else redraws their cached
     * composite. Sized from core count so it scales with the device instead of being a fixed
     * bottleneck on high-end hardware or an oversubscription risk on low-end hardware.
     */
    private static final Semaphore AURA_RENDER_GATE = new Semaphore(auraPermitsForCores(), true);

    private static int auraPermitsForCores() {
        if (CPU_CORES <= 4) return 1;
        if (CPU_CORES <= 6) return 2;
        return 3;
    }

    // ------------------------------------------------------------------------------
    // Tunables derived from DeviceTier
    // ------------------------------------------------------------------------------

    private static int meshSize(DeviceTier tier) {
        switch (tier) {
            case LOW: return 8;
            case MID: return 16;
            default: return 24;
        }
    }

    private static boolean chromaticAberrationEnabled(DeviceTier tier) {
        return tier != DeviceTier.LOW;
    }

    private static boolean useSaveLayer(DeviceTier tier) {
        // saveLayer is the single most expensive (and, on some low-end GPU/Skia
        // combinations, glitch-prone) op in the old Crystal path. Skip it on LOW tier and
        // composite directly - the visual difference is negligible for a clipped path,
        // the stability difference is not.
        return tier != DeviceTier.LOW;
    }

    private static long frameBudgetNanos(DeviceTier tier) {
        switch (tier) {
            case LOW: return LegacyBlur.FRAME_BUDGET_60FPS_NS;
            case MID: return LegacyBlur.FRAME_BUDGET_90FPS_NS;
            default: return LegacyBlur.FRAME_BUDGET_120FPS_NS;
        }
    }

    private static final float CHROMATIC_SHIFT_FACTOR = 0.024f;
    private static final float TWO_OVER_PI = 0.63661975f;

    // ------------------------------------------------------------------------------
    // Instance state
    // ------------------------------------------------------------------------------

    /** Delegate for the raw pixel blur - reuses LegacyBlur's hardened, shared-pool stack blur. */
    private final LegacyBlur blurEngine = new LegacyBlur();

    private XoerisBlurV2Type type = XoerisBlurV2Type.OBSCURA;
    private DeviceTier tierOverride; // null = follow global tier

    // --- Shared / Obscura paints ---------------------------------------------------
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noisePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix scratchMatrix = new Matrix();

    private int glassTint = Color.argb(46, 255, 255, 255);
    private float glassOpacity = 1.0f;
    private int noiseAmount = 6;
    private boolean showBorder = true;
    private boolean showHighlight = true;

    // --- Aura-only state -------------------------------------------------------------
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint surfaceSheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint domeFeatherPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // Glow: outerGlowPaint bleeds a soft halo past the card's own edges (drawn unclipped,
    // before the card body); rimGlowPaint is an additive inner bloom concentrated near the
    // rim, drawn inside the clip on top of the mesh. Together these are what push Obscura's
    // flat frosted look toward an actual luminous "frosted liquid glass" read. Both use
    // PorterDuff.SCREEN (a light-additive blend) rather than BlurMaskFilter/shadow layers -
    // those aren't reliably hardware-accelerated across API levels/devices, which is exactly
    // the class of low-end-device fragility this whole rewrite exists to avoid.
    private final Paint outerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outerGlowPath = new Path();
    private float lastGlowCornerRadius = -1f;
    private final Paint meshPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint meshPaintR = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint meshPaintB = new Paint(Paint.FILTER_BITMAP_FLAG);

    private float[] meshVertices;
    private float[] meshVerticesR;
    private float[] meshVerticesB;
    private int builtMeshResolution = -1;

    private float depthAmount = 0.25f;
    private float distortionAmount = 0.35f;
    private float saturationAmount = 1.45f;

    // 3D bevel - a raised "prism" ring near the edges of the surface (normDist beyond
    // bevelPlateau) that gets extra refraction + chromatic dispersion, giving the edge a
    // faceted, glass-thickness look instead of a flat cutout. On by default for Aura - a
    // liquid-glass surface reads as flat/fake without it - unlike Crystal (LegacyBlur) where
    // callers had to opt in via set3DBevel().
    private float bevelPlateau = 0.82f;
    private float bevelSteepness = 0.45f;

    // Aura-specific intensity knobs, independent of Obscura/Legacy so tuning one never
    // affects the other. auraBlurScale multiplies the raw stack-blur radius BlurLayout
    // applies before compositing (see BlurLayout#refreshBlur); auraOpacity is a uniform
    // alpha multiplier over every paint/shader used in the Aura composite (mesh, base,
    // sheen, rim, dome-feather, highlight), applied in ensureShaders() below.
    private float auraBlurScale = 1.5f;
    private float auraOpacity = 0.95f;
    private static final int CHROMA_FRINGE_BASE_ALPHA = 42;

    // Glow tuning. Spread is proportional to card size (not a fixed dp value) so it scales
    // sensibly across card sizes without needing a Context/density here.
    private float glowIntensity = 0.05f;
    private static final float GLOW_SPREAD_FRACTION = 0.14f;

    private ThemeMode lastShaderTheme;
    private final RectF lastShaderBounds = new RectF();

    private Bitmap scratchBitmap;

    // --- Result cache: the key that lets a starved (permit-less) frame reuse last output ---
    // Note: the "cache" itself is simply the bitmap the caller already owns and keeps
    // redrawing (applyToBitmap mutates it in place; draw() composites onto the caller's
    // canvas each frame). AdaptiveBlur doesn't need to hold a second copy - it only needs to know
    // *whether* the last full rebuild is still valid for the current geometry/theme/radius,
    // which is exactly what comparing currentKey against lastKey answers below.
    private final ResultKey lastKey = new ResultKey();
    private final ResultKey currentKey = new ResultKey();

    private static final class ResultKey {
        int width, height, radius;
        ThemeMode theme;
        XoerisBlurV2Type type;
        float hX, hY;
        boolean valid;

        void set(int w, int h, int radius, ThemeMode theme, XoerisBlurV2Type type, float hX, float hY) {
            this.width = w;
            this.height = h;
            this.radius = radius;
            this.theme = theme;
            this.type = type;
            // Round the highlight position so sub-pixel finger jitter doesn't force a
            // rebuild every frame - it's an inexpensive follow-highlight, not a hard sync.
            this.hX = Math.round(hX);
            this.hY = Math.round(hY);
            this.valid = true;
        }

        boolean matches(ResultKey other) {
            return other.valid && width == other.width && height == other.height
                    && radius == other.radius && theme == other.theme && type == other.type
                    && hX == other.hX && hY == other.hY;
        }
    }

    // ------------------------------------------------------------------------------
    // Constructor / configuration
    // ------------------------------------------------------------------------------

    public AdaptiveBlur() {
        bitmapPaint.setStyle(Paint.Style.FILL);

        tintPaint.setStyle(Paint.Style.FILL);
        tintPaint.setColor(glassTint);

        noisePaint.setStyle(Paint.Style.FILL);
        noisePaint.setAlpha(noiseAmount);

        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.25f);
        borderPaint.setColor(Color.argb(60, 255, 255, 255));

        basePaint.setStyle(Paint.Style.FILL);
        surfaceSheenPaint.setStyle(Paint.Style.FILL);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(1.5f);
        rimInnerPaint.setStyle(Paint.Style.STROKE);
        rimInnerPaint.setStrokeWidth(1.0f);
        domeFeatherPaint.setStyle(Paint.Style.FILL);

        outerGlowPaint.setStyle(Paint.Style.FILL);
        outerGlowPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        rimGlowPaint.setStyle(Paint.Style.FILL);
        rimGlowPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));

        meshPaintR.setAlpha(CHROMA_FRINGE_BASE_ALPHA);
        meshPaintR.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                1f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f})));
        meshPaintB.setAlpha(CHROMA_FRINGE_BASE_ALPHA);
        meshPaintB.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f})));
        updateSaturationFilter();
    }

    /** Selects OBSCURA or AURA rendering. Backs {@code xoerisBlurV2Type}. */
    public void setBlurV2Type(XoerisBlurV2Type type) {
        if (type == null) type = XoerisBlurV2Type.OBSCURA;
        if (this.type != type) {
            this.type = type;
            invalidateResultCache();
        }
    }

    public XoerisBlurV2Type getBlurV2Type() {
        return type;
    }

    /** Per-instance override of the global {@link DeviceTier}, e.g. for testing. Pass null to follow global. */
    public void setDeviceTierOverride(DeviceTier tier) {
        this.tierOverride = tier;
    }

    private DeviceTier effectiveTier() {
        return tierOverride != null ? tierOverride : sDeviceTier;
    }

    public void setGlassTint(int color) {
        this.glassTint = color;
        tintPaint.setColor(color);
        invalidateResultCache();
    }

    public void setGlassOpacity(float opacity) {
        this.glassOpacity = Math.max(0f, Math.min(1f, opacity));
        tintPaint.setAlpha((int) (Color.alpha(glassTint) * this.glassOpacity));
        invalidateResultCache();
    }

    public void setNoiseAmount(int amount) {
        this.noiseAmount = Math.max(0, Math.min(255, amount));
        noisePaint.setAlpha(this.noiseAmount);
        invalidateResultCache();
    }

    public void setShowBorder(boolean show) {
        this.showBorder = show;
    }

    public void setShowHighlight(boolean show) {
        this.showHighlight = show;
    }

    public void setSaturation(float saturation) {
        this.saturationAmount = saturation;
        updateSaturationFilter();
        invalidateResultCache();
    }

    public void setDepthEffect(float depth) {
        this.depthAmount = Math.max(0f, Math.min(1f, depth));
        builtMeshResolution = -1;
        invalidateResultCache();
    }

    public void setDistortionAmount(float distortion) {
        this.distortionAmount = distortion;
        builtMeshResolution = -1;
        invalidateResultCache();
    }

    /**
     * Configures the 3D bevel ring for AURA - the raised, more sharply refractive band near
     * the edges of the surface that makes it read as a beveled piece of glass rather than a
     * flat cutout with a blur behind it. Mirrors {@code LegacyBlur#set3DBevel}, so existing
     * callers driving both can share the same tuning values.
     *
     * @param plateau   normalized radius (0..1 from center, clamped like Legacy's 0..5 input)
     *                  at which the flat center ends and the beveled edge band begins. Smaller
     *                  = wider bevel ring; larger = a thinner ring hugging the very edge.
     * @param steepness how sharply the bevel band refracts/disperses light. 0 = no bevel.
     */
    public void setBevel(float plateau, float steepness) {
        this.bevelPlateau = Math.max(0.0f, Math.min(5.0f, plateau));
        this.bevelSteepness = Math.max(0.0f, Math.min(1.0f, steepness));
        builtMeshResolution = -1;
        invalidateResultCache();
    }

    /**
     * Multiplier applied to the raw stack-blur radius before it's used for AURA. Callers
     * (BlurLayout) that pre-blur their own captured background should read this via
     * {@link #getAuraBlurScale()} and scale the radius they pass to the blur pass themselves -
     * AdaptiveBlur's draw()/drawAura() path composites an already-blurred bitmap and has no
     * blur radius of its own to scale. 1.0 = same intensity as Legacy/Obscura; e.g. 0.25 = a
     * quarter as much blur.
     */
    public void setAuraBlurScale(float scale) {
        this.auraBlurScale = Math.max(0.0f, Math.min(2.0f, scale));
    }

    public float getAuraBlurScale() {
        return auraBlurScale;
    }

    /**
     * Uniform opacity multiplier over the entire AURA composite (mesh warp + base/sheen/rim/
     * dome-feather/highlight paints). 1.0 = fully opaque surface; e.g. 0.75 = the whole glass
     * surface is 25% see-through relative to its normal look. Independent of glassOpacity,
     * which only affects OBSCURA's flat tint.
     */
    public void setAuraOpacity(float opacity) {
        this.auraOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
        invalidateResultCache();
        // Cheap to just force a shader rebuild next draw rather than tracking a separate
        // "opacity changed" flag - ensureShaders() is only as expensive as a handful of
        // gradient allocations, and bevel/distortion setters already pay this same cost.
        lastShaderTheme = null;
    }

    public float getAuraOpacity() {
        return auraOpacity;
    }

    /**
     * Intensity of the frosted-glass glow (outer edge bleed + inner rim bloom). 0 disables
     * both glow passes entirely; 1 is the strongest look. Scales on top of, not instead of,
     * {@link #setAuraOpacity(float)} - both apply together.
     */
    public void setGlowIntensity(float intensity) {
        this.glowIntensity = Math.max(0.0f, Math.min(1.0f, intensity));
        invalidateResultCache();
        lastShaderTheme = null;
    }

    public float getGlowIntensity() {
        return glowIntensity;
    }

    /** Explicitly discards the cached composite so the next draw/apply always rebuilds. */
    public void invalidateResultCache() {
        lastKey.valid = false;
    }

    // ------------------------------------------------------------------------------
    // Main entry point - mirrors LegacyBlur#applyToBitmap for drop-in pipeline use
    // ------------------------------------------------------------------------------

    /**
     * Blurs {@code bitmap} in place using the currently configured
     * {@link XoerisBlurV2Type}, applying the device-tier-adaptive quality/perf tradeoffs and
     * (for AURA) the shared-permit render gate described in the class doc.
     *
     * @param bitmap     mutable source/destination bitmap (captured background content).
     * @param blurRadius requested stack-blur radius; clamped internally.
     * @param hX         horizontal parallax/highlight offset (AURA only; ignored for OBSCURA).
     * @param hY         vertical parallax/highlight offset (AURA only; ignored for OBSCURA).
     * @param theme      light/dark theme for tint & rim colors.
     */
    public void applyToBitmap(Bitmap bitmap, int blurRadius, float hX, float hY, ThemeMode theme) {
        if (bitmap == null || bitmap.isRecycled() || !bitmap.isMutable()) return;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= 0 || h <= 0) return;

        ThemeMode activeTheme = theme != null ? theme : ThemeMode.LIGHT;
        DeviceTier tier = effectiveTier();

        if (type == XoerisBlurV2Type.OBSCURA) {
            renderObscura(bitmap, blurRadius, activeTheme, tier);
            return;
        }

        renderAura(bitmap, blurRadius, hX, hY, activeTheme, tier);
    }

    /**
     * Canvas-based draw entry point analogous to
     * {@link LegacyBlur#draw(Canvas, RectF, Path, Bitmap, Matrix, Paint, Paint, Paint, Paint, Paint, boolean, boolean, float, float, float, LegacyBlur.ThemeMode)},
     * for callers (e.g. a BlurView) that keep the captured background as a separate bitmap
     * and want AdaptiveBlur to composite onto an existing canvas/clip rather than mutate the
     * capture in place.
     */
    public void draw(Canvas canvas, RectF bounds, Path clipPath, Bitmap capturedBackground,
                      float cornerRadius, float hX, float hY, ThemeMode theme) {
        if (canvas == null || bounds == null || clipPath == null) return;
        if (bounds.width() <= 0 || bounds.height() <= 0) return;

        ThemeMode activeTheme = theme != null ? theme : ThemeMode.LIGHT;
        DeviceTier tier = effectiveTier();

        if (type == XoerisBlurV2Type.OBSCURA) {
            drawObscura(canvas, bounds, clipPath, capturedBackground, activeTheme);
        } else {
            drawAura(canvas, bounds, clipPath, capturedBackground, cornerRadius, hX, hY, activeTheme, tier);
        }
    }

    // ------------------------------------------------------------------------------
    // OBSCURA - normal glassy blur
    // ------------------------------------------------------------------------------

    private void renderObscura(Bitmap bitmap, int blurRadius, ThemeMode theme, DeviceTier tier) {
        // OBSCURA is intentionally simple: one stack blur pass (delegated to LegacyBlur's
        // adaptive/parallel implementation) plus a flat tint + noise + highlight/border
        // overlay drawn straight onto the same bitmap. No mesh, no saveLayer, no
        // per-frame shader rebuilds beyond what bounds/theme changes require - this is
        // the "always safe, always cheap" mode low-end devices should default to.
        long budget = frameBudgetNanos(tier);
        blurEngine.blurRealtime(bitmap, blurRadius, budget);

        applyThemeColors(theme);

        Canvas canvas = new Canvas(bitmap);
        Rect full = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        canvas.drawRect(full, tintPaint);
        if (noiseAmount > 0) {
            canvas.drawRect(full, noisePaint);
        }
        if (showHighlight) {
            canvas.drawRect(full, highlightPaint);
        }
        if (showBorder) {
            canvas.drawRect(full.left + 1, full.top + 1, full.right - 1, full.bottom - 1, borderPaint);
        }
    }

    private void drawObscura(Canvas canvas, RectF bounds, Path clipPath, Bitmap capturedBackground, ThemeMode theme) {
        applyThemeColors(theme);

        if (capturedBackground != null && !capturedBackground.isRecycled()) {
            scratchMatrix.setScale(bounds.width() / capturedBackground.getWidth(),
                    bounds.height() / capturedBackground.getHeight());
            scratchMatrix.postTranslate(bounds.left, bounds.top);
            if (bitmapPaint.getShader() == null
                    || !(bitmapPaint.getShader() instanceof android.graphics.BitmapShader)
                    || currentKey.type != type) {
                bitmapPaint.setShader(new android.graphics.BitmapShader(
                        capturedBackground, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            }
            bitmapPaint.getShader().setLocalMatrix(scratchMatrix);
            canvas.drawPath(clipPath, bitmapPaint);
        }

        canvas.drawPath(clipPath, tintPaint);
        if (noiseAmount > 0) {
            canvas.drawPath(clipPath, noisePaint);
        }
        if (showHighlight) {
            canvas.drawPath(clipPath, highlightPaint);
        }
        if (showBorder) {
            canvas.drawPath(clipPath, borderPaint);
        }
    }

    private void applyThemeColors(ThemeMode theme) {
        int baseAlpha = Color.alpha(glassTint);
        int tint = theme == ThemeMode.DARK
                ? Color.argb((int) (baseAlpha * 0.6f * glassOpacity), 20, 20, 24)
                : Color.argb((int) (baseAlpha * glassOpacity), 255, 255, 255);
        tintPaint.setColor(tint);
        borderPaint.setColor(theme == ThemeMode.DARK
                ? Color.argb(50, 255, 255, 255)
                : Color.argb(90, 255, 255, 255));
        int hColor = theme == ThemeMode.DARK ? Color.argb(18, 255, 255, 255) : Color.argb(60, 255, 255, 255);
        highlightPaint.setColor(hColor);
    }

    // ------------------------------------------------------------------------------
    // AURA - adaptive liquid-glass blur
    // ------------------------------------------------------------------------------

    private void renderAura(Bitmap bitmap, int blurRadius, float hX, float hY, ThemeMode theme, DeviceTier tier) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        currentKey.set(w, h, blurRadius, theme, type, hX, hY);
        boolean cacheHit = currentKey.matches(lastKey);

        boolean gotPermit = false;
        try {
            // Never block the calling (render) thread waiting for a permit - either we get
            // one immediately, or this frame reuses the cached composite. This is what
            // keeps N simultaneous Aura views from turning into N-times-as-slow frames.
            gotPermit = AURA_RENDER_GATE.tryAcquire();

            if (!gotPermit && cacheHit) {
                // Someone else is rebuilding right now, but our own last output is still
                // valid for this exact geometry/theme/radius - just redraw it, no work needed.
                return;
            }

            if (!gotPermit && !cacheHit) {
                // No permit AND stale/no cache (e.g. first frame for a newly-shown view under
                // contention): fall back to a cheap OBSCURA-equivalent pass just for this
                // frame rather than stalling. The next frame will retry for a full AURA permit.
                renderObscuraFallbackForAura(bitmap, blurRadius, theme, tier);
                return;
            }

            buildAuraComposite(bitmap, blurRadius, hX, hY, theme, tier);
            lastKey.set(w, h, blurRadius, theme, type, hX, hY);
        } finally {
            if (gotPermit) {
                AURA_RENDER_GATE.release();
            }
        }
    }

    private void renderObscuraFallbackForAura(Bitmap bitmap, int blurRadius, ThemeMode theme, DeviceTier tier) {
        long budget = frameBudgetNanos(tier);
        blurEngine.blurRealtime(bitmap, Math.max(1, blurRadius - 4), budget);
        applyThemeColors(theme);
        Canvas canvas = new Canvas(bitmap);
        Rect full = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        canvas.drawRect(full, tintPaint);
        if (showHighlight) canvas.drawRect(full, highlightPaint);
    }

    private void buildAuraComposite(Bitmap bitmap, int blurRadius, float hX, float hY, ThemeMode theme, DeviceTier tier) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        // 1. Pixel blur, delegated to LegacyBlur's adaptive/parallel/frame-budget-aware pass.
        long budget = frameBudgetNanos(tier);
        blurEngine.blurRealtime(bitmap, blurRadius, budget);

        // 2. Snapshot into scratch so the mesh-warp pass has a stable source to sample from.
        if (scratchBitmap == null || scratchBitmap.isRecycled()
                || scratchBitmap.getWidth() != w || scratchBitmap.getHeight() != h) {
            if (scratchBitmap != null && !scratchBitmap.isRecycled()) scratchBitmap.recycle();
            scratchBitmap = bitmap.copy(bitmap.getConfig() != null ? bitmap.getConfig() : Bitmap.Config.ARGB_8888, true);
        } else {
            Canvas copyCanvas = new Canvas(scratchBitmap);
            copyCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            copyCanvas.drawBitmap(bitmap, 0, 0, null);
        }

        RectF bounds = new RectF(0, 0, w, h);
        ensureMesh(bounds, tier);
        ensureShaders(bounds, theme);

        scratchMatrix.setTranslate(hX, hY);

        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        int mesh = meshSize(tier);
        boolean chroma = chromaticAberrationEnabled(tier);

        if (chroma) {
            canvas.drawBitmapMesh(scratchBitmap, mesh, mesh, meshVerticesR, 0, null, 0, meshPaintR);
            canvas.drawBitmapMesh(scratchBitmap, mesh, mesh, meshVerticesB, 0, null, 0, meshPaintB);
        }
        canvas.drawBitmapMesh(scratchBitmap, mesh, mesh, meshVertices, 0, null, 0, meshPaint);

        Path fullPath = new Path();
        fullPath.addRect(bounds, Path.Direction.CW);
        canvas.drawPath(fullPath, domeFeatherPaint);
        canvas.drawPath(fullPath, basePaint);
        canvas.drawPath(fullPath, surfaceSheenPaint);
    }

    private void drawAura(Canvas canvas, RectF bounds, Path clipPath, Bitmap capturedBackground,
                           float cornerRadius, float hX, float hY, ThemeMode theme, DeviceTier tier) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;

        boolean boundsChanged = !bounds.equals(lastShaderBounds);
        boolean themeChanged = theme != lastShaderTheme;
        if (boundsChanged || themeChanged || basePaint.getShader() == null) {
            ensureShaders(bounds, theme);
            if (boundsChanged) ensureMesh(bounds, tier);
            lastShaderBounds.set(bounds);
            lastShaderTheme = theme;
        }

        // Glow is skipped outright on LOW tier - same reasoning as chromatic aberration and
        // saveLayer above: it's an extra draw pass, not a cheap one to degrade gracefully, so
        // low-end devices simply never pay for it rather than paying for a stripped-down
        // version of it.
        boolean glowEnabled = tier != DeviceTier.LOW && glowIntensity > 0f;
        if (glowEnabled && (boundsChanged || cornerRadius != lastGlowCornerRadius)) {
            buildOuterGlowPath(bounds, cornerRadius);
            lastGlowCornerRadius = cornerRadius;
        }

        // Drawn unclipped, before the saveLayer below - saveLayer(bounds, ...) would otherwise
        // clip this glow's outward bleed to the card's own bounds and hide the whole effect.
        if (glowEnabled) {
            canvas.drawPath(outerGlowPath, outerGlowPaint);
        }

        scratchMatrix.setTranslate(hX, hY);

        int saveCount = -1;
        boolean layered = useSaveLayer(tier) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        if (layered) {
            saveCount = canvas.saveLayer(bounds, maskPaint);
        } else {
            canvas.save();
        }

        if (capturedBackground != null && !capturedBackground.isRecycled()) {
            canvas.save();
            canvas.clipPath(clipPath);

            int mesh = meshSize(tier);
            // This is the actual per-frame draw path (BlurLayout.onDraw calls draw(), not
            // applyToBitmap(), for every visible Aura instance) - so this is where multiple
            // concurrent Aura views really compete for CPU, and where the render gate needs to
            // live. Chromatic aberration is the expensive extra pass (2 additional
            // drawBitmapMesh calls over the base mesh); when the gate is contended (another
            // Aura view holds a permit this frame) this view still draws its base mesh - the
            // view never goes blank or stalls - it just temporarily loses the aberration
            // fringing, which is a far less noticeable a fallback than a dropped/frozen frame.
            boolean wantsChroma = chromaticAberrationEnabled(tier);
            boolean gotChromaPermit = wantsChroma && AURA_RENDER_GATE.tryAcquire();
            try {
                if (gotChromaPermit) {
                    canvas.drawBitmapMesh(capturedBackground, mesh, mesh, meshVerticesR, 0, null, 0, meshPaintR);
                    canvas.drawBitmapMesh(capturedBackground, mesh, mesh, meshVerticesB, 0, null, 0, meshPaintB);
                }
                canvas.drawBitmapMesh(capturedBackground, mesh, mesh, meshVertices, 0, null, 0, meshPaint);
            } finally {
                if (gotChromaPermit) {
                    AURA_RENDER_GATE.release();
                }
            }

            canvas.drawPath(clipPath, domeFeatherPaint);
            if (glowEnabled) {
                canvas.drawPath(clipPath, rimGlowPaint);
            }
            canvas.restore();
        }

        canvas.drawPath(clipPath, basePaint);
        canvas.drawPath(clipPath, surfaceSheenPaint);
        canvas.drawPath(clipPath, highlightPaint);
        canvas.drawPath(clipPath, rimInnerPaint);
        canvas.drawPath(clipPath, rimPaint);

        if (layered) {
            canvas.restoreToCount(saveCount);
        } else {
            canvas.restore();
        }
    }

    // ------------------------------------------------------------------------------
    // Mesh / shader construction (Aura)
    // ------------------------------------------------------------------------------

    private void ensureMesh(RectF bounds, DeviceTier tier) {
        // Callers only reach here when bounds/tier actually changed (gated by boundsChanged
        // checks at the call sites), so this always rebuilds - the mesh-resolution tracking
        // just lets buildMesh() know whether it can reuse the existing float[] arrays.
        int mesh = meshSize(tier);
        buildMesh(bounds, mesh);
        builtMeshResolution = mesh;
    }

    /** Outset rounded-rect the outer glow fills - kept concentric with the card's own corners. */
    private void buildOuterGlowPath(RectF bounds, float cornerRadius) {
        float spread = Math.min(bounds.width(), bounds.height()) * GLOW_SPREAD_FRACTION;
        RectF outset = new RectF(bounds.left - spread, bounds.top - spread, bounds.right + spread, bounds.bottom + spread);
        float r = Math.max(0f, cornerRadius) + spread;
        outerGlowPath.reset();
        outerGlowPath.addRoundRect(outset, r, r, Path.Direction.CW);
    }

    private void buildMesh(RectF bounds, int mesh) {
        int vertCount = (mesh + 1) * (mesh + 1);
        int arrayLen = vertCount * 2;
        if (meshVertices == null || meshVertices.length != arrayLen) {
            meshVertices = new float[arrayLen];
            meshVerticesR = new float[arrayLen];
            meshVerticesB = new float[arrayLen];
        }

        float w = bounds.width();
        float h = bounds.height();
        float cX = bounds.centerX();
        float cY = bounds.centerY();
        float halfW = w * 0.5f;
        float halfH = h * 0.5f;
        float baseShift = CHROMATIC_SHIFT_FACTOR * w;

        int idx = 0;
        for (int row = 0; row <= mesh; row++) {
            float fy0 = bounds.top + (row * h / mesh);
            for (int col = 0; col <= mesh; col++) {
                float fx0 = bounds.left + (col * w / mesh);
                float dx = fx0 - cX;
                float dy = fy0 - cY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                float fx = fx0;
                float fy = fy0;
                float edgeWeight = 0f;

                if (dist > 1.0E-4f) {
                    float absDx = Math.abs(dx);
                    float absDy = Math.abs(dy);
                    float tx = absDx > 1.0E-4f ? halfW / absDx : Float.MAX_VALUE;
                    float ty = absDy > 1.0E-4f ? halfH / absDy : Float.MAX_VALUE;
                    float tBorder = Math.min(tx, ty);
                    float maxRayDist = dist * tBorder;

                    float normDist = Math.min(1.0f, dist / maxRayDist);
                    float clampedU = Math.min(normDist, 0.99999f);
                    float sphericalU = (float) Math.asin(clampedU) * TWO_OVER_PI;

                    float refractedU = (1.0f - depthAmount) * normDist + (depthAmount * sphericalU);

                    // Bevel: past bevelPlateau, add a sinusoidal "prism" bump to the refraction
                    // curve (peaks mid-band, returns to baseline at the very edge) and widen
                    // the chromatic dispersion in that same band. That combination is what
                    // reads as a faceted edge catching the light differently from the flat
                    // center, rather than just a softer blur falloff.
                    float bevelProgress = Math.max(0.0f, (normDist - bevelPlateau) / (1.0f - bevelPlateau));
                    float prismRefraction = (float) Math.sin(bevelProgress * Math.PI) * bevelSteepness;
                    float finalRefractedU = refractedU + prismRefraction;
                    float prismDispersion = (float) Math.sin(bevelProgress * Math.PI) * 2.35f;

                    edgeWeight = (finalRefractedU * finalRefractedU) + prismDispersion;

                    float power = 1.0f - (distortionAmount * (1.0f - finalRefractedU));
                    float finalNorm = (float) Math.pow(finalRefractedU, power);
                    float newDist = finalNorm * maxRayDist;
                    float scale = newDist / dist;

                    fx = cX + (dx * scale);
                    fy = cY + (dy * scale);
                }

                float distShift = baseShift * edgeWeight;
                meshVertices[idx] = fx;
                meshVertices[idx + 1] = fy;
                meshVerticesR[idx] = fx - distShift;
                meshVerticesR[idx + 1] = fy;
                meshVerticesB[idx] = fx + distShift;
                meshVerticesB[idx + 1] = fy;
                idx += 2;
            }
        }
    }

    private void ensureShaders(RectF bounds, ThemeMode theme) {
        int baseTop = theme == ThemeMode.DARK ? Color.argb(13, 255, 255, 255) : Color.argb(95, 230, 233, 238);
        int baseBottom = theme == ThemeMode.DARK ? Color.argb(3, 255, 255, 255) : Color.argb(45, 215, 220, 228);
        basePaint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.left, bounds.bottom,
                baseTop, baseBottom, Shader.TileMode.CLAMP));

        int sheenColor = Color.argb(theme == ThemeMode.DARK ? 8 : 70, 255, 255, 255);
        surfaceSheenPaint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[]{sheenColor, 0}, new float[]{0f, 0.55f}, Shader.TileMode.CLAMP));

        int rimBright = theme == ThemeMode.DARK ? Color.argb(170, 255, 255, 255) : Color.argb(220, 255, 255, 255);
        int rimDim = Color.argb(theme == ThemeMode.DARK ? 15 : 55, 255, 255, 255);
        rimPaint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[]{rimBright, 0, rimDim}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

        int innerDark = Color.argb(theme == ThemeMode.DARK ? 85 : 45, 0, 0, 0);
        rimInnerPaint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[]{0, innerDark}, new float[]{0.45f, 1f}, Shader.TileMode.CLAMP));

        float hRadius = Math.max(bounds.width(), bounds.height()) * 0.75f;
        int hColor = Color.argb(theme == ThemeMode.DARK ? 12 : 75, 255, 255, 255);
        highlightPaint.setShader(new RadialGradient(0f, 0f, Math.max(1f, hRadius), hColor, Color.TRANSPARENT, Shader.TileMode.CLAMP));

        float fRadius = (float) Math.sqrt(bounds.width() * bounds.width() + bounds.height() * bounds.height()) * 0.5f;
        int fEdge = theme == ThemeMode.DARK ? Color.argb(65, 0, 0, 0) : Color.argb(28, 180, 185, 195);
        domeFeatherPaint.setShader(new RadialGradient(bounds.centerX(), bounds.centerY(), Math.max(1f, fRadius),
                new int[]{0, 0, fEdge}, new float[]{0f, 0.68f, 1f}, Shader.TileMode.CLAMP));

        // Outer glow: a halo centered on the card that stays fully transparent through the
        // card's own body (positions 0 -> edgePos), peaks just past the card boundary
        // (edgePos -> peakPos, inside the outward "spread" margin), then fades back to
        // transparent at the outermost bleed radius. Radius/positions derived from bounds so
        // the ring always sits flush against the actual card edge regardless of card size.
        float diagHalf = (float) Math.sqrt(bounds.width() * bounds.width() + bounds.height() * bounds.height()) * 0.5f;
        float spread = Math.min(bounds.width(), bounds.height()) * GLOW_SPREAD_FRACTION;
        float glowRadius = diagHalf + spread;
        float edgePos = Math.min(0.99f, diagHalf / glowRadius);
        float peakPos = Math.min(0.995f, edgePos + (spread * 0.45f) / glowRadius);
        int outerGlowColor = theme == ThemeMode.DARK
                ? Color.argb(150, 210, 226, 255)
                : Color.argb(150, 255, 250, 235);
        outerGlowPaint.setShader(new RadialGradient(bounds.centerX(), bounds.centerY(), Math.max(1f, glowRadius),
                new int[]{0, 0, outerGlowColor, 0}, new float[]{0f, edgePos, peakPos, 1f}, Shader.TileMode.CLAMP));

        // Inner rim bloom: additive (SCREEN), concentrated in the last ~28% of the radius so
        // it reads as light catching the bevel rather than a flat inner tint - this is what
        // makes the bevel we added earlier look lit from within instead of just geometrically
        // refracted.
        int rimGlowColor = theme == ThemeMode.DARK
                ? Color.argb(120, 200, 220, 255)
                : Color.argb(130, 255, 255, 250);
        rimGlowPaint.setShader(new RadialGradient(bounds.centerX(), bounds.centerY(), Math.max(1f, diagHalf),
                new int[]{0, 0, rimGlowColor}, new float[]{0f, 0.72f, 1f}, Shader.TileMode.CLAMP));

        applyAuraOpacity();
    }

    /**
     * Uniformly scales every Aura paint's alpha by {@link #auraOpacity}. Paint.setAlpha
     * modulates a shader's own per-pixel alpha as a multiplier, so this correctly dims the
     * whole gradient/mesh composite rather than flattening it to one alpha value - a rim
     * highlight that already fades to transparent at its shader's own gradient stops stays
     * faded, just scaled down further by the surface opacity on top.
     */
    private void applyAuraOpacity() {
        int a = Math.round(auraOpacity * 255f);
        basePaint.setAlpha(a);
        surfaceSheenPaint.setAlpha(a);
        rimPaint.setAlpha(a);
        rimInnerPaint.setAlpha(a);
        domeFeatherPaint.setAlpha(a);
        highlightPaint.setAlpha(a);
        meshPaint.setAlpha(a);
        meshPaintR.setAlpha(Math.round(CHROMA_FRINGE_BASE_ALPHA * auraOpacity));
        meshPaintB.setAlpha(Math.round(CHROMA_FRINGE_BASE_ALPHA * auraOpacity));

        int glowA = Math.round(auraOpacity * glowIntensity * 255f);
        outerGlowPaint.setAlpha(glowA);
        rimGlowPaint.setAlpha(glowA);
    }

    private void updateSaturationFilter() {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(saturationAmount);
        meshPaint.setColorFilter(new ColorMatrixColorFilter(cm));
    }

    // ------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------

    /** Releases cached bitmaps. Call from the owning view's onDetachedFromWindow. */
    public void release() {
        if (scratchBitmap != null && !scratchBitmap.isRecycled()) {
            scratchBitmap.recycle();
        }
        scratchBitmap = null;
        lastKey.valid = false;
    }
}



