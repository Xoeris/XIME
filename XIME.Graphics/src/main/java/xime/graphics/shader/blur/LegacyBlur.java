package xime.graphics.shader.blur;

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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class LegacyBlur {
    private static final int MAX_RADIUS = 25; // Increased slightly for better look if needed

    // --- Realtime / 120fps tuning -------------------------------------------------
    // A 120Hz display gives ~8.33ms per frame, 90Hz ~11.1ms, 60Hz ~16.6ms. The blur is
    // O(w*h) regardless of radius (stack blur), so the two levers that matter for hitting
    // those budgets are (a) fewer pixels to touch and (b) more cores touching them at once.
    public static final long FRAME_BUDGET_120FPS_NS = 8_333_333L;
    public static final long FRAME_BUDGET_90FPS_NS = 11_111_111L;
    public static final long FRAME_BUDGET_60FPS_NS = 16_666_667L;

    // Below this pixel count, thread hand-off/synchronization costs more than the work
    // saved, so we stay single-threaded.
    private static final int PARALLEL_THRESHOLD_PIXELS = 96 * 96;

    private static final int CPU_CORES = Math.max(1, Runtime.getRuntime().availableProcessors());
    // Leave one core for the UI/render thread so the blur workers don't starve it -
    // starving the UI thread would defeat the point of blurring faster.
    private static final int BLUR_WORKER_COUNT = Math.max(1, CPU_CORES - 1);

    private static final ThreadFactory BLUR_THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger idx = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "GlassBlur-" + idx.incrementAndGet());
            t.setDaemon(true);
            // Slightly above default so blur work isn't starved behind background chores,
            // but intentionally not THREAD_PRIORITY_URGENT_DISPLAY - that belongs to the UI thread.
            t.setPriority(Thread.NORM_PRIORITY + 1);
            return t;
        }
    };

    // Fixed-size pool, shared process-wide: blur work is bursty (once per capture), so a
    // pool sized to (cores - 1) avoids oversubscription when multiple BlurLayout
    // instances blur back to back.
    private static final ExecutorService BLUR_EXECUTOR =
            BLUR_WORKER_COUNT > 1 ? Executors.newFixedThreadPool(BLUR_WORKER_COUNT, BLUR_THREAD_FACTORY) : null;

    // Per-thread scratch so parallel row/column chunks don't allocate on every call once warm.
    private static final ThreadLocal<int[]> threadStack = new ThreadLocal<>();
    private static final ThreadLocal<int[]> threadVmin = new ThreadLocal<>();

    // Global capture depth tracker for multi-layer blur rendering
    private static final AtomicInteger sCaptureDepth = new AtomicInteger(0);

    public static int getCaptureDepth() {
        return sCaptureDepth.get();
    }

    public static void setCaptureDepth(int depth) {
        sCaptureDepth.set(depth);
    }

    public static int incrementCaptureDepth() {
        return sCaptureDepth.incrementAndGet();
    }

    public static int decrementCaptureDepth() {
        int val = sCaptureDepth.decrementAndGet();
        if (val < 0) {
            sCaptureDepth.set(0);
            return 0;
        }
        return val;
    }

    public static boolean isCapturing() {
        return sCaptureDepth.get() > 0;
    }

    // Caches for instance-based blur to avoid frequent allocations
    private int[] cachedPix;
    private int[] cachedR;
    private int[] cachedG;
    private int[] cachedB;
    private int[] cachedDv;
    private int cachedWh = 0;
    private int cachedDvSize = 0;

    // --- Crystal (V2) Fields ------------------------------------------------------
    private static final float CHROMATIC_SHIFT_FACTOR = 0.024f;
    private static final int MESH_HEIGHT = 24;
    private static final int MESH_WIDTH = 24;
    private static final float TWO_OVER_PI = 0.63661975f;

    private final Paint basePaint;
    private LinearGradient baseShader;
    private final Paint domeFeatherPaint;
    private RadialGradient domeFeatherShader;
    private final Paint glowPaint;
    private final Paint highlightPaint;
    private ThemeMode lastTheme;
    private RadialGradient liquidShader;
    private final Paint maskPaint;
    private final Paint meshPaint;
    private final Paint meshPaintB;
    private final Paint meshPaintR;
    private float[] meshVertices;
    private float[] meshVerticesB;
    private float[] meshVerticesR;
    private final Paint rimInnerPaint;
    private LinearGradient rimInnerShader;
    private final Paint rimPaint;
    private LinearGradient rimShader;
    private final Paint surfaceSheenPaint;
    private LinearGradient surfaceSheenShader;

    private final Matrix crystalShaderMatrix = new Matrix();
    private final RectF crystalLastBounds = new RectF();

    // Cache for applyToBitmap() so we don't rebuild the mesh/gradients every single frame
    private final RectF lastBitmapBounds = new RectF();
    private ThemeMode lastBitmapTheme;
    private int lastBitmapBlurRadius = -1;
    private Bitmap crystalScratchBitmap;

    private static final int BLUR_DOWNSCALE_THRESHOLD = 400;
    private static final int BLUR_DOWNSCALE_FACTOR = 4;

    private long targetFrameBudgetNanos = FRAME_BUDGET_120FPS_NS;

    private final Path bitmapRectPath = new Path();

    private float depthAmount = 0.05f;
    private float distortionAmount = 0.25f;
    private float fluidWarpAmount = 0.025f;
    private float warpFrequency = 0.0f;
    private float saturationAmount = 1.5f;
    private float bevelPlateau = 0.95f;
    private float bevelSteepness = 1.5f;

    private BlurType blurType = BlurType.GLASS;

    /**
     * Defines the type of blur effect to be rendered.
     */
    public enum BlurType {
        /**
         * Original LegacyBlur blur renderer.
         */
        GLASS,
        /**
         * Crystal liquid glass blur renderer.
         */
        CRYSTAL
    }

    /**
     * Theme modes supported by the V2 liquid glass renderer.
     */
    public enum ThemeMode {
        /**
         * Dark theme mode.
         */
        DARK,
        /**
         * Light theme mode.
         */
        LIGHT
    }

    /**
     * Constructs a new LegacyBlur instance and initializes the V2 paint objects.
     */
    public LegacyBlur() {
        this.basePaint = new Paint(1);
        this.basePaint.setStyle(Paint.Style.FILL);

        this.surfaceSheenPaint = new Paint(1);
        this.surfaceSheenPaint.setStyle(Paint.Style.FILL);

        this.highlightPaint = new Paint(1);
        this.highlightPaint.setStyle(Paint.Style.FILL);
        this.highlightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));

        this.glowPaint = new Paint(1);
        this.glowPaint.setStyle(Paint.Style.STROKE);

        this.rimPaint = new Paint(1);
        this.rimPaint.setStyle(Paint.Style.STROKE);
        this.rimPaint.setStrokeWidth(1.5f);

        this.rimInnerPaint = new Paint(1);
        this.rimInnerPaint.setStyle(Paint.Style.STROKE);
        this.rimInnerPaint.setStrokeWidth(1.0f);

        this.maskPaint = new Paint(1);
        this.meshPaint = new Paint(3);

        this.domeFeatherPaint = new Paint(1);
        this.domeFeatherPaint.setStyle(Paint.Style.FILL);

        updateColorFilter();

        this.meshPaintR = new Paint(3);
        this.meshPaintR.setAlpha(42);
        this.meshPaintR.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f})));

        this.meshPaintB = new Paint(3);
        this.meshPaintB.setAlpha(42);
        this.meshPaintB.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f})));
    }

    /**
     * Sets the blur type of the glass renderer.
     *
     * @param blurType The blur type to use (V1 or V2).
     */
    public void setBlurType(BlurType blurType) {
        this.blurType = blurType;
    }

    /**
     * Configures the 3D bevel properties for the V2 renderer.
     *
     * @param plateau The width of the flat center plateau.
     * @param steepness The steepness of the bevel edge.
     */
    public void set3DBevel(float plateau, float steepness) {
        this.bevelPlateau = Math.max(0.0f, Math.min(5.0f, plateau));
        this.bevelSteepness = Math.max(0.0f, Math.min(1.0f, steepness));
        this.crystalLastBounds.setEmpty();
    }

    /**
     * Sets the depth effect strength for V2.
     *
     * @param depth The depth factor (0.0f to 1.0f).
     */
    public void setDepthEffect(float depth) {
        this.depthAmount = Math.max(0.0f, Math.min(1.0f, depth));
        this.crystalLastBounds.setEmpty();
    }

    /**
     * Sets the refraction/bevel distortion amount for V2.
     *
     * @param distortion The distortion factor.
     */
    public void setDistortionAmount(float distortion) {
        this.distortionAmount = distortion;
        this.crystalLastBounds.setEmpty();
    }

    /**
     * Sets fluid warp distortion parameters for V2.
     *
     * @param amount The amplitude of the warp.
     * @param frequency The spatial frequency of the warp waves.
     */
    public void setFluidWarp(float amount, float frequency) {
        this.fluidWarpAmount = amount;
        this.warpFrequency = frequency;
        this.crystalLastBounds.setEmpty();
    }

    /**
     * Sets the color saturation boost for the V2 renderer.
     *
     * @param saturation The saturation multiplier.
     */
    public void setSaturation(float saturation) {
        this.saturationAmount = saturation;
        updateColorFilter();
    }

    /**
     * Adjusts V2 distortion based on a blur radius.
     *
     * @param radius The radius value.
     */
    public void setBlurRadius(float radius) {
        this.distortionAmount = 0.15f + (radius / 100f);
        this.crystalLastBounds.setEmpty();
    }

    /**
     * Sets outer glow color and stroke size for V2.
     *
     * @param color The ARGB color of the glow.
     * @param size The stroke width of the glow.
     */
    public void setOuterGlow(int color, float size) {
        this.glowPaint.setColor(color);
        this.glowPaint.setStrokeWidth(size);
    }

    /**
     * Sets the mesh resolution for V2 (retained for compatibility).
     *
     * @param width The mesh width.
     * @param height The mesh height.
     */
    public void setMeshResolution(int width, int height) {
        // Kept for API compatibility
    }

    /**
     * Sets the target frame budget in nanoseconds for the V2 blur pipeline.
     *
     * @param frameBudgetNanos The frame budget limit.
     */
    public void setTargetFrameBudgetNanos(long frameBudgetNanos) {
        this.targetFrameBudgetNanos = frameBudgetNanos > 0 ? frameBudgetNanos : LegacyBlur.FRAME_BUDGET_120FPS_NS;
    }

    /**
     * Draws the blur effect on the canvas using either the V1 or V2 renderer based on the current blur type.
     */
    public void draw(Canvas canvas, RectF bounds, Path clipPath, Bitmap displayBitmap,
                     Matrix shaderMatrix, Paint bitmapPaint, Paint tintPaint,
                     Paint noisePaint, Paint highlightPaint, Paint strokePaint,
                     boolean showHighlight, boolean showBorder, float cornerRadius,
                     float hX, float hY, ThemeMode theme) {
        if (blurType == BlurType.CRYSTAL) {
            drawLiquidGlass(canvas, displayBitmap, bounds, clipPath, cornerRadius, hX, hY, theme);
        } else {
            drawV1(canvas, bounds, clipPath, displayBitmap, shaderMatrix, bitmapPaint,
                   tintPaint, noisePaint, highlightPaint, strokePaint, showHighlight, showBorder);
        }
    }

    /**
     * @deprecated Use the unified {@link #draw(Canvas, RectF, Path, Bitmap, Matrix, Paint, Paint, Paint, Paint, Paint, boolean, boolean, float, float, float, ThemeMode)} instead.
     */
    @Deprecated
    public void draw(Canvas canvas, RectF bounds, Path clipPath, Bitmap displayBitmap, Matrix shaderMatrix, Paint bitmapPaint, Paint tintPaint, Paint noisePaint, Paint highlightPaint, Paint strokePaint, boolean showHighlight, boolean showBorder) {
        drawV1(canvas, bounds, clipPath, displayBitmap, shaderMatrix, bitmapPaint, tintPaint, noisePaint, highlightPaint, strokePaint, showHighlight, showBorder);
    }

    private void drawV1(Canvas canvas, RectF bounds, Path clipPath, Bitmap displayBitmap, Matrix shaderMatrix, Paint bitmapPaint, Paint tintPaint, Paint noisePaint, Paint highlightPaint, Paint strokePaint, boolean showHighlight, boolean showBorder) {
        if (displayBitmap != null && !displayBitmap.isRecycled() && bitmapPaint.getShader() != null) {
            shaderMatrix.setScale(bounds.width() / displayBitmap.getWidth(), bounds.height() / displayBitmap.getHeight());
            bitmapPaint.getShader().setLocalMatrix(shaderMatrix);
            canvas.drawPath(clipPath, bitmapPaint);
        }
        canvas.drawPath(clipPath, tintPaint);
        canvas.drawPath(clipPath, noisePaint);

        if (showHighlight && highlightPaint != null) {
            canvas.drawPath(clipPath, highlightPaint);
        }
        if (showBorder && strokePaint != null) {
            canvas.drawPath(clipPath, strokePaint);
        }
    }

    /**
     * Renders the V2 liquid glass effect onto the canvas.
     */
    public void drawLiquidGlass(Canvas canvas, Bitmap blurredBackground, RectF bounds, Path clipPath, float cornerRadius, float hX, float hY, ThemeMode theme) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;

        ThemeMode activeTheme = theme != null ? theme : ThemeMode.LIGHT;
        boolean boundsChanged = !bounds.equals(this.crystalLastBounds);
        boolean themeChanged = activeTheme != this.lastTheme;

        if (boundsChanged || themeChanged || this.baseShader == null) {
            rebuildShaders(bounds, activeTheme);
            if (boundsChanged) {
                updateMesh(bounds);
            }
        }

        this.crystalShaderMatrix.setTranslate(hX, hY);
        if (this.liquidShader != null) {
            this.liquidShader.setLocalMatrix(this.crystalShaderMatrix);
        }

        this.crystalLastBounds.set(bounds);
        this.lastTheme = activeTheme;

        drawSteppedOuterGlow(canvas, bounds, cornerRadius, activeTheme);

        int count = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            count = canvas.saveLayer(bounds, this.maskPaint);
        }

        if (blurredBackground != null && !blurredBackground.isRecycled()) {
            canvas.save();
            canvas.clipPath(clipPath);

            // Chromatic Aberration Layers (Red & Blue shifts)
            canvas.drawBitmapMesh(blurredBackground, MESH_WIDTH, MESH_HEIGHT, this.meshVerticesR, 0, null, 0, this.meshPaintR);
            canvas.drawBitmapMesh(blurredBackground, MESH_WIDTH, MESH_HEIGHT, this.meshVerticesB, 0, null, 0, this.meshPaintB);

            // Base distorted layer
            canvas.drawBitmapMesh(blurredBackground, MESH_WIDTH, MESH_HEIGHT, this.meshVertices, 0, null, 0, this.meshPaint);

            canvas.drawPath(clipPath, this.domeFeatherPaint);
            canvas.restore();
        }

        canvas.drawPath(clipPath, this.basePaint);
        canvas.drawPath(clipPath, this.surfaceSheenPaint);
        canvas.drawPath(clipPath, this.highlightPaint);
        canvas.drawPath(clipPath, this.rimInnerPaint);
        canvas.drawPath(clipPath, this.rimPaint);

        canvas.restoreToCount(count);
    }

    private void updateMesh(RectF bounds) {
        int vertCount = (MESH_WIDTH + 1) * (MESH_HEIGHT + 1);
        int arrayLen = vertCount * 2;

        if (this.meshVertices == null || this.meshVertices.length != arrayLen) {
            this.meshVertices = new float[arrayLen];
            this.meshVerticesR = new float[arrayLen];
            this.meshVerticesB = new float[arrayLen];
        }

        float w = bounds.width();
        float h = bounds.height();
        float cX = bounds.centerX();
        float cY = bounds.centerY();
        float halfW = w * 0.5f;
        float halfH = h * 0.5f;
        float baseShift = CHROMATIC_SHIFT_FACTOR * w;

        int idx = 0;
        for (int row = 0; row <= MESH_HEIGHT; row++) {
            float fy0 = bounds.top + (row * h / MESH_HEIGHT);
            for (int col = 0; col <= MESH_WIDTH; col++) {
                float fx0 = bounds.left + (col * w / MESH_WIDTH);
                float dx = fx0 - cX;
                float dy = fy0 - cY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                float fx = fx0;
                float fy = fy0;
                float edgeWeight = 0.0f;

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

                    float baseRefractedU = (1.0f - this.depthAmount) * normDist + (this.depthAmount * sphericalU);
                    float bevelProgress = Math.max(0.0f, (normDist - this.bevelPlateau) / (1.0f - this.bevelPlateau));
                    float prismRefraction = (float) Math.sin(bevelProgress * Math.PI) * this.bevelSteepness;
                    float finalRefractedU = baseRefractedU + prismRefraction;

                    float prismDispersion = (float) Math.sin(bevelProgress * Math.PI) * 2.35f;
                    edgeWeight = (finalRefractedU * finalRefractedU) + prismDispersion;

                    float power = 1.0f - (this.distortionAmount * (1.0f - finalRefractedU));
                    float finalNorm = (float) Math.pow(finalRefractedU, power);
                    float newDist = finalNorm * maxRayDist;
                    float scale = newDist / dist;

                    float bulgedX = cX + (dx * scale);
                    float bulgedY = cY + (dy * scale);

                    float normX = dx / halfW;
                    float normY = dy / halfH;
                    float waveEnvelope = (float) Math.sin(normDist * Math.PI);
                    float warpX = (float) Math.sin(normY * Math.PI * this.warpFrequency) * w * this.fluidWarpAmount * waveEnvelope;
                    float warpY = (float) Math.cos(normX * Math.PI * this.warpFrequency) * h * this.fluidWarpAmount * waveEnvelope;

                    fx = bulgedX + warpX;
                    fy = bulgedY + warpY;
                }

                float distShift = baseShift * edgeWeight;
                this.meshVertices[idx] = fx;
                this.meshVertices[idx + 1] = fy;
                this.meshVerticesR[idx] = fx - distShift;
                this.meshVerticesR[idx + 1] = fy;
                this.meshVerticesB[idx] = fx + distShift;
                this.meshVerticesB[idx + 1] = fy;
                idx += 2;
            }
        }
    }

    private void rebuildShaders(RectF bounds, ThemeMode theme) {
        int baseTop = theme == ThemeMode.DARK ? Color.argb(13, 255, 255, 255) : Color.argb(95, 230, 233, 238);
        int baseBottom = theme == ThemeMode.DARK ? Color.argb(3, 255, 255, 255) : Color.argb(45, 215, 220, 228);
        this.baseShader = new LinearGradient(bounds.left, bounds.top, bounds.left, bounds.bottom, baseTop, baseBottom, Shader.TileMode.CLAMP);
        this.basePaint.setShader(this.baseShader);

        int sheenColor = Color.argb(theme == ThemeMode.DARK ? 8 : 75, 255, 255, 255);
        this.surfaceSheenShader = new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[]{sheenColor, 0}, new float[]{0.0f, 0.55f}, Shader.TileMode.CLAMP);
        this.surfaceSheenPaint.setShader(this.surfaceSheenShader);

        int rimBright = theme == ThemeMode.DARK ? Color.argb(180, 255, 255, 255) : Color.argb(230, 255, 255, 255);
        int rimDim = Color.argb(theme == ThemeMode.DARK ? 15 : 60, 255, 255, 255);
        this.rimShader = new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[]{rimBright, 0, rimDim}, new float[]{0.0f, 0.5f, 1.0f}, Shader.TileMode.CLAMP);
        this.rimPaint.setShader(this.rimShader);

        int innerDark = Color.argb(theme == ThemeMode.DARK ? 90 : 50, 0, 0, 0);
        this.rimInnerShader = new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[]{0, innerDark}, new float[]{0.45f, 1.0f}, Shader.TileMode.CLAMP);
        this.rimInnerPaint.setShader(this.rimInnerShader);

        float hRadius = Math.max(bounds.width(), bounds.height()) * 0.75f;
        int hColor = Color.argb(theme == ThemeMode.DARK ? 12 : 80, 255, 255, 255);
        this.liquidShader = new RadialGradient(0.0f, 0.0f, hRadius, hColor, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        this.highlightPaint.setShader(this.liquidShader);

        float fRadius = (float) Math.sqrt(bounds.width() * bounds.width() + bounds.height() * bounds.height()) * 0.5f;
        int fEdge = theme == ThemeMode.DARK ? Color.argb(70, 0, 0, 0) : Color.argb(30, 180, 185, 195);
        this.domeFeatherShader = new RadialGradient(bounds.centerX(), bounds.centerY(), fRadius,
                new int[]{0, 0, fEdge}, new float[]{0.0f, 0.68f, 1.0f}, Shader.TileMode.CLAMP);
        this.domeFeatherPaint.setShader(this.domeFeatherShader);
    }

    private void drawSteppedOuterGlow(Canvas canvas, RectF bounds, float radius, ThemeMode theme) {
        int baseR = theme == ThemeMode.DARK ? 110 : 180;
        int baseG = theme == ThemeMode.DARK ? 160 : 210;
        int glowBase = Color.argb(255, baseR, baseG, 255);
        int r = Color.red(glowBase);
        int g = Color.green(glowBase);
        int b = Color.blue(glowBase);

        float[] widths = {6.0f, 14.0f, 28.0f};
        int[] alphas = theme == ThemeMode.DARK ? new int[]{24, 12, 5} : new int[]{45, 22, 8};

        for (int i = 0; i < 3; i++) {
            this.glowPaint.setStrokeWidth(widths[i]);
            this.glowPaint.setColor(Color.argb(alphas[i], r, g, b));
            canvas.drawRoundRect(bounds, radius, radius, this.glowPaint);
        }
    }

    private void updateColorFilter() {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(this.saturationAmount);
        float[] matrix = cm.getArray();
        matrix[4] = 5.0f;
        matrix[9] = 5.0f;
        matrix[14] = 5.0f;
        this.meshPaint.setColorFilter(new ColorMatrixColorFilter(cm));
    }

    /**
     * Applies stack blur and V2 mesh distortion to the given bitmap.
     *
     * @param bitmap The mutable bitmap to modify in place.
     * @param blurRadius The radius of the stack blur pass.
     * @param x Unused position coordinate.
     * @param y Unused position coordinate.
     * @param theme The theme mode to use for rendering overlays.
     */
    public void applyToBitmap(Bitmap bitmap, int blurRadius, int x, int y, ThemeMode theme) {
        if (bitmap == null || bitmap.isRecycled()) return;
        if (bitmap.isMutable()) {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();

            // 1. Apply stack blur using the local blurRealtime method
            this.blurRealtime(bitmap, blurRadius, this.targetFrameBudgetNanos);

            // 2. Apply mesh distortion by drawing the bitmap onto itself via a reused scratch buffer
            RectF bounds = new RectF(0, 0, w, h);
            if (this.crystalScratchBitmap == null || this.crystalScratchBitmap.isRecycled()
                    || this.crystalScratchBitmap.getWidth() != w || this.crystalScratchBitmap.getHeight() != h) {
                if (this.crystalScratchBitmap != null && !this.crystalScratchBitmap.isRecycled()) {
                    this.crystalScratchBitmap.recycle();
                }
                this.crystalScratchBitmap = bitmap.copy(bitmap.getConfig(), true);
            } else {
                Canvas copyCanvas = new Canvas(this.crystalScratchBitmap);
                copyCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                copyCanvas.drawBitmap(bitmap, 0, 0, null);
            }

            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            ThemeMode activeTheme = theme != null ? theme : ThemeMode.LIGHT;
            boolean boundsChanged = !bounds.equals(this.lastBitmapBounds);
            boolean themeChanged = activeTheme != this.lastBitmapTheme;
            boolean radiusChanged = blurRadius != this.lastBitmapBlurRadius;
            if (boundsChanged || themeChanged || this.meshVertices == null) {
                updateMesh(bounds);
                rebuildShaders(bounds, activeTheme);
                this.bitmapRectPath.reset();
                this.bitmapRectPath.addRect(bounds, Path.Direction.CW);
                this.lastBitmapBounds.set(bounds);
                this.lastBitmapTheme = activeTheme;
                this.lastBitmapBlurRadius = blurRadius;
            } else if (radiusChanged) {
                this.lastBitmapBlurRadius = blurRadius;
            }

            // Draw distorted mesh using old logic order (R, B, then Main)
            canvas.drawBitmapMesh(this.crystalScratchBitmap, MESH_WIDTH, MESH_HEIGHT, this.meshVerticesR, 0, null, 0, this.meshPaintR);
            canvas.drawBitmapMesh(this.crystalScratchBitmap, MESH_WIDTH, MESH_HEIGHT, this.meshVerticesB, 0, null, 0, this.meshPaintB);
            canvas.drawBitmapMesh(this.crystalScratchBitmap, MESH_WIDTH, MESH_HEIGHT, this.meshVertices, 0, null, 0, this.meshPaint);

            // Add overlays
            canvas.drawPath(this.bitmapRectPath, this.domeFeatherPaint);
            canvas.drawPath(this.bitmapRectPath, this.basePaint);
            canvas.drawPath(this.bitmapRectPath, this.surfaceSheenPaint);
        }
    }

    /**
     * Releases cached resources such as the V2 scratch bitmap.
     */
    public void release() {
        if (this.crystalScratchBitmap != null && !this.crystalScratchBitmap.isRecycled()) {
            this.crystalScratchBitmap.recycle();
        }
        this.crystalScratchBitmap = null;
    }

    /**
     * Optimized instance-based blur that reuses internal buffers and, for bitmaps large
     * enough to make it worthwhile, spreads the work across a worker pool so it lands
     * inside a 120fps frame budget instead of a 60fps one.
     * Call this instead of the static version for better performance.
     */
    public void blur(Bitmap bitmap, int radius) {
        if (radius < 1 || bitmap == null || bitmap.isRecycled()) return;

        int r = Math.min(radius, MAX_RADIUS);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int wh = w * h;
        if (wh <= 0) return;

        ensureInstanceCaches(w, h, r);

        int div = r + r + 1;

        bitmap.getPixels(cachedPix, 0, w, 0, 0, w, h);
        runStackBlur(cachedPix, w, h, r, cachedR, cachedG, cachedB, cachedDv, div);
        bitmap.setPixels(cachedPix, 0, w, 0, 0, w, h);
    }

    /**
     * Same as {@link #blur(Bitmap, int)}, but if the blur can't be completed within
     * {@code frameBudgetNanos} at the bitmap's current resolution, it downsamples first
     * (stack blur cost is O(w*h*constant), so halving each dimension roughly quarters the
     * work) and upscales the result back in place. This is the entry point to reach for
     * when the caller has a hard frame budget (e.g. {@link #FRAME_BUDGET_120FPS_NS}).
     */
    public void blurRealtime(Bitmap bitmap, int radius, long frameBudgetNanos) {
        if (radius < 1 || bitmap == null || bitmap.isRecycled() || frameBudgetNanos <= 0) {
            blur(bitmap, radius);
            return;
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int scale = recommendedDownscaleFactor(w, h, frameBudgetNanos);

        if (scale <= 1) {
            blur(bitmap, radius);
            return;
        }

        int smallW = Math.max(1, w / scale);
        int smallH = Math.max(1, h / scale);
        Bitmap small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true);
        blur(small, radius);

        Canvas c = new Canvas(bitmap);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        c.drawBitmap(small, null, new Rect(0, 0, w, h), p);
        small.recycle();
    }

    /**
     * Estimates how much to downscale so the blur (parallelized across
     * {@link #BLUR_WORKER_COUNT} workers) fits inside {@code frameBudgetNanos}. This is a
     * throughput estimate, not a hard guarantee - thermal throttling and other UI-thread
     * work still apply - but it's grounded in the same O(w*h) cost model the blur actually
     * has, split across the same worker count actually used at blur time.
     */
    public static int recommendedDownscaleFactor(int width, int height, long frameBudgetNanos) {
        if (width <= 0 || height <= 0 || frameBudgetNanos <= 0) return 1;

        // Conservative empirical throughput for the inner stack-blur loop on a mid-range
        // core: ~45M pixel-passes/sec per worker (two passes - horizontal + vertical - per
        // pixel, buffer-reuse, no allocation). Downscaling is the safety margin, not a
        // precise predictor, so this only needs to be right to within a factor of ~2.
        final long pixelPassesPerSecondPerWorker = 45_000_000L;
        int workers = BLUR_EXECUTOR != null ? BLUR_WORKER_COUNT : 1;
        long budgetPixelPasses = (frameBudgetNanos * pixelPassesPerSecondPerWorker * workers) / 1_000_000_000L;
        long neededPixelPasses = (long) width * height * 2L;

        if (neededPixelPasses <= budgetPixelPasses || budgetPixelPasses <= 0) return 1;

        double ratio = (double) neededPixelPasses / (double) budgetPixelPasses;
        // Area scales with the square of the linear downscale factor.
        int scale = (int) Math.ceil(Math.sqrt(ratio));
        return Math.max(1, Math.min(scale, 8));
    }

    public static void stackBlur(Bitmap bitmap, int radius) {
        if (radius < 1 || bitmap == null || bitmap.isRecycled()) return;

        int r = Math.min(radius, MAX_RADIUS);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int wh = w * h;
        if (wh <= 0) return;

        int[] pix = new int[wh];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int div = r + r + 1;
        int divsum = (div + 1) >> 1;
        int divsum2 = divsum * divsum;
        int[] dv = new int[divsum2 * 256];
        for (int i = 0; i < dv.length; i++) dv[i] = i / divsum2;

        runStackBlur(pix, w, h, r, new int[wh], new int[wh], new int[wh], dv, div);
        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
    }

    public static void scaledStackBlur(Bitmap bitmap, int radius, int scale) {
        if (scale <= 1) {
            stackBlur(bitmap, radius);
            return;
        }
        if (radius < 1 || bitmap == null) return;

        int origW = bitmap.getWidth();
        int origH = bitmap.getHeight();
        int smallW = Math.max(1, origW / scale);
        int smallH = Math.max(1, origH / scale);

        Bitmap small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true);
        stackBlur(small, radius);

        Canvas c = new Canvas(bitmap);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(small, null, new Rect(0, 0, origW, origH), p);
        small.recycle();
    }

    // ------------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------------

    private void ensureInstanceCaches(int w, int h, int radius) {
        int wh = w * h;
        if (cachedPix == null || cachedWh < wh) {
            cachedPix = new int[wh];
            cachedR = new int[wh];
            cachedG = new int[wh];
            cachedB = new int[wh];
            cachedWh = wh;
        }

        int div = radius + radius + 1;
        int divsum = (div + 1) >> 1;
        int divsum2 = divsum * divsum;
        int dvSize = divsum2 * 256;
        if (cachedDv == null || cachedDvSize < dvSize) {
            cachedDv = new int[dvSize];
            cachedDvSize = dvSize;
            for (int i = 0; i < dvSize; i++) cachedDv[i] = i / divsum2;
        }
    }

    /**
     * Runs the two-pass stack blur (horizontal then vertical). For bitmaps above
     * {@link #PARALLEL_THRESHOLD_PIXELS}, each pass is split into independent row/column
     * chunks and run across {@link #BLUR_EXECUTOR}: within the horizontal pass every row is
     * computed purely from the original (unblurred) pixel buffer, and within the vertical
     * pass every column is computed purely from the horizontal pass's r/g/b buffers - so
     * chunks never read a value another chunk is concurrently writing.
     */
    private static void runStackBlur(int[] pix, int w, int h, int radius, int[] r, int[] g, int[] b, int[] dv, int div) {
        int wh = w * h;
        boolean parallel = BLUR_EXECUTOR != null && wh >= PARALLEL_THRESHOLD_PIXELS;

        if (!parallel) {
            int[] stack = new int[div * 3];
            int[] vmin = new int[Math.max(w, h)];
            horizontalPass(pix, w, h, radius, r, g, b, dv, div, 0, h, stack, vmin);
            verticalPass(pix, w, h, radius, r, g, b, dv, div, 0, w, stack, vmin);
            return;
        }

        int workers = Math.min(BLUR_WORKER_COUNT, Math.max(1, h));
        runChunked(workers, h, (start, end, latch) -> {
            int[] stack = obtainThreadStack(div * 3);
            int[] vmin = obtainThreadVmin(Math.max(w, h));
            try {
                horizontalPass(pix, w, h, radius, r, g, b, dv, div, start, end, stack, vmin);
            } finally {
                latch.countDown();
            }
        });

        int colWorkers = Math.min(BLUR_WORKER_COUNT, Math.max(1, w));
        runChunked(colWorkers, w, (start, end, latch) -> {
            int[] stack = obtainThreadStack(div * 3);
            int[] vmin = obtainThreadVmin(Math.max(w, h));
            try {
                verticalPass(pix, w, h, radius, r, g, b, dv, div, start, end, stack, vmin);
            } finally {
                latch.countDown();
            }
        });
    }

    private interface ChunkTask {
        void run(int start, int end, CountDownLatch latch);
    }

    /**
     * Splits [0, total) into {@code workers} contiguous chunks, submits one task per chunk
     * to {@link #BLUR_EXECUTOR}, and blocks the calling (UI) thread until all finish. This
     * runs on the UI thread by design - Canvas/Bitmap capture that feeds the blur already
     * has to happen there, so the wait here is the real end-to-end latency, kept as short
     * as the worker count allows.
     */
    private static void runChunked(int workers, int total, ChunkTask task) {
        if (workers <= 1) {
            task.run(0, total, new CountDownLatch(1));
            return;
        }

        CountDownLatch latch = new CountDownLatch(workers);
        int chunkSize = (total + workers - 1) / workers;
        for (int i = 0; i < workers; i++) {
            int start = i * chunkSize;
            int end = Math.min(total, start + chunkSize);
            if (start >= end) {
                latch.countDown();
                continue;
            }
            BLUR_EXECUTOR.execute(() -> task.run(start, end, latch));
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int[] obtainThreadStack(int size) {
        int[] s = threadStack.get();
        if (s == null || s.length < size) {
            s = new int[size];
            threadStack.set(s);
        }
        return s;
    }

    private static int[] obtainThreadVmin(int size) {
        int[] v = threadVmin.get();
        if (v == null || v.length < size) {
            v = new int[size];
            threadVmin.set(v);
        }
        return v;
    }

    /**
     * Horizontal (row) pass restricted to rows [yStart, yEnd). Reads only from {@code pix}
     * (untouched by any pass this call) and writes only into {@code r/g/b} at row indices
     * within its own range - safe to run concurrently with other row ranges.
     */
    private static void horizontalPass(int[] pix, int w, int h, int radius, int[] r, int[] g, int[] b, int[] dv, int div, int yStart, int yEnd, int[] stack, int[] vmin) {
        int wm = w - 1;
        int r1 = radius + 1;

        // vmin[x] depends only on x/radius/wm, not on y, so it's computed once up front
        // rather than gated on "y == 0" - correct regardless of which row chunk runs first.
        for (int x = 0; x < w; x++) {
            vmin[x] = Math.min(x + radius + 1, wm);
        }

        for (int y = yStart; y < yEnd; y++) {
            int yi = y * w;
            int yw = yi;
            int rsum = 0, gsum = 0, bsum = 0;
            int rinsum = 0, ginsum = 0, binsum = 0, routsum = 0, goutsum = 0, boutsum = 0;

            for (int i = -radius; i <= radius; i++) {
                int p = pix[yi + Math.min(wm, Math.max(i, 0))];
                int stackIdx = (i + radius) * 3;
                stack[stackIdx] = (p & 0xff0000) >> 16;
                stack[stackIdx + 1] = (p & 0xff00) >> 8;
                stack[stackIdx + 2] = (p & 0xff);

                int rbs = r1 - Math.abs(i);
                rsum += stack[stackIdx] * rbs;
                gsum += stack[stackIdx + 1] * rbs;
                bsum += stack[stackIdx + 2] * rbs;

                if (i > 0) {
                    rinsum += stack[stackIdx];
                    ginsum += stack[stackIdx + 1];
                    binsum += stack[stackIdx + 2];
                } else {
                    routsum += stack[stackIdx];
                    goutsum += stack[stackIdx + 1];
                    boutsum += stack[stackIdx + 2];
                }
            }

            int stackpointer = radius;
            int rowIdx = yi;
            for (int x = 0; x < w; x++) {
                r[rowIdx] = dv[rsum];
                g[rowIdx] = dv[gsum];
                b[rowIdx] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                int stackstart = stackpointer - radius + div;
                int sirIdx = (stackstart % div) * 3;

                routsum -= stack[sirIdx];
                goutsum -= stack[sirIdx + 1];
                boutsum -= stack[sirIdx + 2];

                int p = pix[yw + vmin[x]];

                stack[sirIdx] = (p & 0xff0000) >> 16;
                stack[sirIdx + 1] = (p & 0xff00) >> 8;
                stack[sirIdx + 2] = (p & 0xff);

                rinsum += stack[sirIdx];
                ginsum += stack[sirIdx + 1];
                binsum += stack[sirIdx + 2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sirIdx = stackpointer * 3;

                routsum += stack[sirIdx];
                goutsum += stack[sirIdx + 1];
                boutsum += stack[sirIdx + 2];

                rinsum -= stack[sirIdx];
                ginsum -= stack[sirIdx + 1];
                binsum -= stack[sirIdx + 2];

                rowIdx++;
            }
        }
    }

    /**
     * Vertical (column) pass restricted to columns [xStart, xEnd). Reads only from the
     * fully-populated {@code r/g/b} buffers (written by every horizontal-pass chunk before
     * this method is ever called - enforced by the latch barrier in {@link #runStackBlur})
     * and writes only into {@code pix} at column indices within its own range - safe to run
     * concurrently with other column ranges.
     */
    private static void verticalPass(int[] pix, int w, int h, int radius, int[] r, int[] g, int[] b, int[] dv, int div, int xStart, int xEnd, int[] stack, int[] vmin) {
        int hm = h - 1;
        int r1 = radius + 1;

        // vmin[y] depends only on y/radius/hm/w, not on x - computed once up front.
        for (int y = 0; y < h; y++) {
            vmin[y] = Math.min(y + r1, hm) * w;
        }

        for (int x = xStart; x < xEnd; x++) {
            int rsum = 0, gsum = 0, bsum = 0;
            int rinsum = 0, ginsum = 0, binsum = 0, routsum = 0, goutsum = 0, boutsum = 0;
            int yp = -radius * w;

            for (int i = -radius; i <= radius; i++) {
                int yi = Math.max(0, yp) + x;
                int stackIdx = (i + radius) * 3;
                stack[stackIdx] = r[yi];
                stack[stackIdx + 1] = g[yi];
                stack[stackIdx + 2] = b[yi];

                int rbs = r1 - Math.abs(i);
                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += stack[stackIdx];
                    ginsum += stack[stackIdx + 1];
                    binsum += stack[stackIdx + 2];
                } else {
                    routsum += stack[stackIdx];
                    goutsum += stack[stackIdx + 1];
                    boutsum += stack[stackIdx + 2];
                }

                if (i < hm) yp += w;
            }

            int yi = x;
            int stackpointer = radius;
            for (int y = 0; y < h; y++) {
                pix[yi] = (pix[yi] & 0xff000000) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                int stackstart = stackpointer - radius + div;
                int sirIdx = (stackstart % div) * 3;

                routsum -= stack[sirIdx];
                goutsum -= stack[sirIdx + 1];
                boutsum -= stack[sirIdx + 2];

                int p = x + vmin[y];

                stack[sirIdx] = r[p];
                stack[sirIdx + 1] = g[p];
                stack[sirIdx + 2] = b[p];

                rinsum += stack[sirIdx];
                ginsum += stack[sirIdx + 1];
                binsum += stack[sirIdx + 2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sirIdx = (stackpointer % div) * 3;

                routsum += stack[sirIdx];
                goutsum += stack[sirIdx + 1];
                boutsum += stack[sirIdx + 2];

                rinsum -= stack[sirIdx];
                ginsum -= stack[sirIdx + 1];
                binsum -= stack[sirIdx + 2];

                yi += w;
            }
        }
    }
}

