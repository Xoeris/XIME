package xime.ui.layout;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

import xime.R;
import xime.graphics.shader.blur.LegacyBlur;
import xime.haptic.HapticEngine;
import xime.haptic.HapticIntensity;
import xime.haptic.HapticPattern;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

public class ReflectLayout extends Layout implements NestedScrollingParent3 {
    private static final float DAMPING = 0.9f;
    private static final float FLING_MOMENTUM_SCALE = 0.6f;
    private static final float MAX_OVERSCROLL = 400.0f;
    private static final float RAW_PULL_RESISTANCE = 1000.0f;
    private static final float PRE_SCROLL_GAIN = 1.2f;
    public static final int ORIENTATION_HORIZONTAL = 1;
    public static final int ORIENTATION_VERTICAL = 0;
    private static final float STIFFNESS = 200.0f;

    private static final float RELOAD_TRIGGER_RATIO = 0.5f;
    private static final float RELOAD_APPEAR_EPSILON = 0.02f;
    private static final long SPIN_CYCLE_MS = 900L;
    private static final long SPIN_SETTLE_MS = 250L;
    private static final float ICON_REST_DP = 24.0f;
    private static final float ICON_SIZE_DP = 36.0f;
    private static final float ICON_Z_DP = 32.0f;
    private static final float ICON_MIN_SCALE = 0.35f;

    private static final long SCRIM_FADE_MS = 200L;
    private static final int SCRIM_DIM_COLOR = 0x52000000;
    private static final int SCRIM_BLUR_RADIUS = 12;
    private static final int SCRIM_DOWNSCALE = 4;

    private static final long ONE_SHOT_RELOAD_MS = 1000L;
    private static final long DEFAULT_RELOAD_TIMEOUT_MS = 10000L;

    private final HapticEngine HapticEngine;
    private boolean isBeingDragged = false;
    private boolean isSpringing = false;
    private float lastFlingVelocity = 0.0f;
    private final int maximumVelocity;
    private int orientation = ORIENTATION_VERTICAL;
    private OnOverscrollChangeListener overscrollChangeListener;
    private final NestedScrollingParentHelper parentHelper;
    private float rawOverscroll = 0.0f;
    private SpringAnimation springAnimation;
    private VelocityTracker velocityTracker;

    private boolean isReloadUI = false;
    private boolean isReloading = false;
    private ImageView reloadIcon;
    private ObjectAnimator spinAnimator;
    private OnReloadListener reloadListener;
    private View reloadScrim;
    private Bitmap scrimBackdrop;
    private long reloadTimeoutMs = DEFAULT_RELOAD_TIMEOUT_MS;
    private Runnable autoFinishRunnable;
    private final int[] tmpSelfLocation = new int[2];
    private final int[] tmpRootLocation = new int[2];

    public interface OnReloadListener {
        void onReload();
    }

    public interface OnOverscrollChangeListener {
        void onOverscrollChanged(ReflectLayout engine, float visualTranslation);
    }

    public ReflectLayout(Context context) {
        this(context, null);
    }

    public ReflectLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ReflectLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setMeasureAllChildren(true);
        this.parentHelper = new NestedScrollingParentHelper(this);
        this.HapticEngine = new HapticEngine(context);
        this.maximumVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ReflectLayout, defStyleAttr, 0);
            this.orientation = a.getInt(R.styleable.ReflectLayout_xoerisOrientation, ORIENTATION_VERTICAL);
            this.isReloadUI = a.getBoolean(R.styleable.ReflectLayout_xoerisIsReloadUI, false);
            a.recycle();
        }

        setupReloadIcon();
        setupSpring();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachReloadIconToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelSpin();
        cancelSpringBack();
        cancelAutoFinish();
        this.isReloading = false;
        if (this.reloadScrim != null) {
            this.reloadScrim.animate().cancel();
            this.reloadScrim.setAlpha(0.0f);
        }
        if (this.reloadIcon != null) {
            this.reloadIcon.animate().cancel();
            this.reloadIcon.setRotation(0.0f);
            this.reloadIcon.setAlpha(0.0f);
            this.reloadIcon.setScaleX(0.0f);
            this.reloadIcon.setScaleY(0.0f);
        }
        releaseScrimBackdrop();
        super.onDetachedFromWindow();
    }

    public void setReloadUIEnabled(boolean enabled) {
        if (this.isReloadUI == enabled) return;
        this.isReloadUI = enabled;
        if (enabled) {
            setupReloadIcon();
            attachReloadIconToWindow();
        } else {
            cancelSpin();
            this.isReloading = false;
            cancelAutoFinish();
            if (this.reloadScrim != null) {
                this.reloadScrim.animate().cancel();
                if (this.reloadScrim.getParent() instanceof ViewGroup) {
                    ((ViewGroup) this.reloadScrim.getParent()).removeView(this.reloadScrim);
                }
            }
            releaseScrimBackdrop();
            if (this.reloadIcon != null) {
                this.reloadIcon.animate().cancel();
                if (this.reloadIcon.getParent() instanceof ViewGroup) {
                    ((ViewGroup) this.reloadIcon.getParent()).removeView(this.reloadIcon);
                }
                this.reloadIcon = null;
            }
        }
    }

    private void setupReloadIcon() {
        if (this.isReloadUI && this.reloadIcon == null) {
            float density = getResources().getDisplayMetrics().density;
            int iconSize = Math.round(ICON_SIZE_DP * density);
            this.reloadIcon = new ImageView(getContext());
            this.reloadIcon.setImageResource(R.drawable.xoeris_refresh);
            this.reloadIcon.setAlpha(0.0f);
            this.reloadIcon.setScaleX(0.0f);
            this.reloadIcon.setScaleY(0.0f);
            this.reloadIcon.setTranslationZ(ICON_Z_DP * density);
            this.reloadIcon.setVisibility(VISIBLE);
            attachReloadIconToWindow();
        }
    }

    private ViewGroup resolveRootContainer() {
        View root = getRootView();
        if (!(root instanceof ViewGroup)) return null;
        View content = root.findViewById(android.R.id.content);
        return content instanceof ViewGroup ? (ViewGroup) content : (ViewGroup) root;
    }

    private void attachReloadIconToWindow() {
        ImageView icon = this.reloadIcon;
        if (icon == null || !isAttachedToWindow()) return;
        ViewGroup root = resolveRootContainer();
        if (root == null || icon.getParent() == root) return;
        if (icon.getParent() instanceof ViewGroup) {
            ((ViewGroup) icon.getParent()).removeView(icon);
        }
        float density = getResources().getDisplayMetrics().density;
        int iconSize = Math.round(ICON_SIZE_DP * density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.TOP | Gravity.START);
        root.addView(icon, lp);
        icon.bringToFront();
    }

    private void setupSpring() {
        this.springAnimation = new SpringAnimation(this,
                this.orientation == ORIENTATION_VERTICAL ? SpringAnimation.TRANSLATION_Y : SpringAnimation.TRANSLATION_X, 0.0f);
        this.springAnimation.getSpring().setStiffness(STIFFNESS);
        this.springAnimation.getSpring().setDampingRatio(DAMPING);
        this.springAnimation.addEndListener((animation, canceled, value, velocity) -> this.isSpringing = false);
    }

    private void cancelSpringBack() {
        if (this.springAnimation != null && this.springAnimation.isRunning()) {
            this.springAnimation.cancel();
        }
        this.isSpringing = false;
    }

    private void cancelSpin() {
        if (this.spinAnimator != null) {
            this.spinAnimator.cancel();
            this.spinAnimator = null;
        }
    }

    private float currentTranslation() {
        return this.orientation == ORIENTATION_VERTICAL ? getTranslationY() : getTranslationX();
    }

    private void applyTranslation(float translation) {
        if (this.orientation == ORIENTATION_VERTICAL) setTranslationY(translation);
        else setTranslationX(translation);
    }

    private float visualFromRaw(float raw) {
        float sign = Math.signum(raw);
        float absPull = Math.abs(raw);
        return MAX_OVERSCROLL * sign * (1.0f - ((float) Math.exp((-absPull) / RAW_PULL_RESISTANCE)));
    }

    private float reloadThreshold() {
        return MAX_OVERSCROLL * RELOAD_TRIGGER_RATIO;
    }

    private float restPx() {
        return ICON_REST_DP * getResources().getDisplayMetrics().density;
    }

    private void syncStateToCurrentPosition() {
        cancelSpringBack();
        float currentTrans = currentTranslation();
        if (currentTrans != 0.0f) {
            float absVisual = Math.min(Math.abs(currentTrans), MAX_OVERSCROLL - 1f);
            float absPull = (float) (Math.log(1.0f - (absVisual / MAX_OVERSCROLL)) * (-RAW_PULL_RESISTANCE));
            this.rawOverscroll = Math.signum(currentTrans) * absPull;
        } else {
            this.rawOverscroll = 0.0f;
        }
        this.lastFlingVelocity = 0.0f;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            syncStateToCurrentPosition();
            if (this.velocityTracker == null) {
                this.velocityTracker = VelocityTracker.obtain();
            } else {
                this.velocityTracker.clear();
            }
        }
        if (this.velocityTracker != null) {
            this.velocityTracker.addMovement(ev);
        }
        boolean handled = super.dispatchTouchEvent(ev);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            float currentTrans = currentTranslation();

            if (this.isReloadUI && !this.isReloading && Math.abs(currentTrans) >= reloadThreshold()) {
                beginReload();
                if (this.reloadListener != null) {
                    this.reloadListener.onReload();
                }
            }

            if (currentTrans != 0.0f && !this.isSpringing && !this.isBeingDragged) {
                startSpringBack(getFinalVelocity());
            }
            recycleVelocityTracker();
        }
        return handled;
    }

    private float getFinalVelocity() {
        if (this.velocityTracker != null) {
            this.velocityTracker.computeCurrentVelocity(1000, this.maximumVelocity);
            return this.orientation == ORIENTATION_VERTICAL ? this.velocityTracker.getYVelocity() : this.velocityTracker.getXVelocity();
        }
        return 0.0f;
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
        int unconsumed = this.orientation == ORIENTATION_VERTICAL ? dyUnconsumed : dxUnconsumed;
        if (type == ViewCompat.TYPE_TOUCH && unconsumed != 0) {
            if (!this.isBeingDragged && this.HapticEngine != null) {
                this.HapticEngine.trigger(new HapticPattern(HapticPattern.SINE_REFLECT), HapticIntensity.SOFT);
            }
            this.isBeingDragged = true;
            this.rawOverscroll -= unconsumed;
            applyTranslation(visualFromRaw(this.rawOverscroll));
            if (this.orientation == ORIENTATION_VERTICAL) consumed[1] = dyUnconsumed;
            else consumed[0] = dxUnconsumed;
            return;
        }
        if (type == ViewCompat.TYPE_NON_TOUCH && unconsumed != 0) {
            float currentTrans = currentTranslation();
            if (currentTrans == 0.0f && !this.isSpringing && this.lastFlingVelocity != 0.0f) {
                if (this.HapticEngine != null) {
                    this.HapticEngine.trigger(new HapticPattern(HapticPattern.SINE_REFLECT), HapticIntensity.SOFT);
                }
                startSpringBack(this.lastFlingVelocity * FLING_MOMENTUM_SCALE);
                this.lastFlingVelocity = 0.0f;
            }
        }
    }

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        return this.orientation == ORIENTATION_VERTICAL ? (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0 : (axes & ViewCompat.SCROLL_AXIS_HORIZONTAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        this.parentHelper.onNestedScrollAccepted(child, target, axes, type);
        if (type == ViewCompat.TYPE_TOUCH) {
            cancelSpringBack();
        }
    }

    @Override
    public void onStopNestedScroll(@NonNull View target, int type) {
        this.parentHelper.onStopNestedScroll(target, type);
        if (type == ViewCompat.TYPE_TOUCH) {
            this.isBeingDragged = false;
            float currentTrans = currentTranslation();
            if (currentTrans != 0.0f && !this.isSpringing) {
                startSpringBack(getFinalVelocity());
            }
        }
    }

    private void startSpringBack(float velocity) {
        if (this.isSpringing) return;
        this.isSpringing = true;
        this.springAnimation.setStartValue(currentTranslation());
        this.springAnimation.setStartVelocity(velocity);
        this.springAnimation.start();
        this.rawOverscroll = 0.0f;
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        if (this instanceof PagerLayout) return;
        float currentTrans = currentTranslation();
        int delta = this.orientation == ORIENTATION_VERTICAL ? dy : dx;
        if (currentTrans == 0.0f || type != ViewCompat.TYPE_TOUCH) return;

        if ((currentTrans > 0.0f && delta > 0) || (currentTrans < 0.0f && delta < 0)) {
            this.rawOverscroll -= delta * PRE_SCROLL_GAIN;
            float sign = Math.signum(this.rawOverscroll);
            float nextTrans = visualFromRaw(this.rawOverscroll);

            if (Math.signum(nextTrans) != sign && sign != 0.0f) {
                applyTranslation(0.0f);
                this.rawOverscroll = 0.0f;
            } else {
                applyTranslation(nextTrans);
            }

            if (this.orientation == ORIENTATION_VERTICAL) consumed[1] = dy;
            else consumed[0] = dx;
        }
    }

    @Override
    public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        this.lastFlingVelocity = this.orientation == ORIENTATION_VERTICAL ? -velocityY : -velocityX;
        return false;
    }

    @Override
    public boolean onNestedFling(@NonNull View target, float velocityX, float velocityY, boolean consumed) {
        return false;
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, new int[2]);
    }

    @Override public int getNestedScrollAxes() { return parentHelper.getNestedScrollAxes(); }
    @Override public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int nestedScrollAxes) { return onStartNestedScroll(child, target, nestedScrollAxes, ViewCompat.TYPE_TOUCH); }
    @Override public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int nestedScrollAxes) { onNestedScrollAccepted(child, target, nestedScrollAxes, ViewCompat.TYPE_TOUCH); }
    @Override public void onStopNestedScroll(@NonNull View target) { onStopNestedScroll(target, ViewCompat.TYPE_TOUCH); }
    @Override public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) { onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, ViewCompat.TYPE_TOUCH, new int[2]); }
    @Override public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed) { onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH); }

    public void setOnOverscrollChangeListener(OnOverscrollChangeListener listener) {
        this.overscrollChangeListener = listener;
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        if (this.overscrollChangeListener != null && this.orientation == ORIENTATION_VERTICAL) {
            this.overscrollChangeListener.onOverscrollChanged(this, translationY);
        }
        updateReloadVisuals(translationY);
    }

    @Override
    public void setTranslationX(float translationX) {
        super.setTranslationX(translationX);
        if (this.overscrollChangeListener != null && this.orientation == ORIENTATION_HORIZONTAL) {
            this.overscrollChangeListener.onOverscrollChanged(this, translationX);
        }
        updateReloadVisuals(translationX);
    }

    private void updateReloadVisuals(float translation) {
        if (!this.isReloadUI || this.reloadIcon == null) return;
        if (this.isReloading) {
            positionReloadIcon(translation);
            return;
        }

        float abs = Math.abs(translation);
        float progress = Math.min(1.0f, abs / reloadThreshold());
        if (progress <= RELOAD_APPEAR_EPSILON) {
            this.reloadIcon.setAlpha(0.0f);
            this.reloadIcon.setScaleX(0.0f);
            this.reloadIcon.setScaleY(0.0f);
            return;
        }

        positionReloadIcon(translation);
        this.reloadIcon.setAlpha(Math.min(1.0f, progress * 1.4f));
        float scale = ICON_MIN_SCALE + (1.0f - ICON_MIN_SCALE) * easeOutBack(progress);
        this.reloadIcon.setScaleX(scale);
        this.reloadIcon.setScaleY(scale);
        this.reloadIcon.setRotation(progress * 360.0f);
    }

    private void positionReloadIcon(float translation) {
        ImageView icon = this.reloadIcon;
        if (icon == null) return;
        ViewGroup root = (ViewGroup) icon.getParent();
        if (root == null) return;

        float abs = Math.abs(translation);
        boolean positiveSide = translation >= 0.0f;
        float rest = restPx();
        float capped = Math.min(abs, reloadThreshold());
        float offset = rest + capped - abs;

        getLocationOnScreen(this.tmpSelfLocation);
        root.getLocationOnScreen(this.tmpRootLocation);

        float targetX;
        float targetY;
        if (this.orientation == ORIENTATION_VERTICAL) {
            targetX = this.tmpSelfLocation[0] + (getWidth() - icon.getWidth()) / 2.0f;
            targetY = positiveSide
                    ? this.tmpSelfLocation[1] + offset
                    : this.tmpSelfLocation[1] + getHeight() - icon.getHeight() - offset;
        } else {
            targetY = this.tmpSelfLocation[1] + (getHeight() - icon.getHeight()) / 2.0f;
            targetX = positiveSide
                    ? this.tmpSelfLocation[0] + offset
                    : this.tmpSelfLocation[0] + getWidth() - icon.getWidth() - offset;
        }
        icon.setTranslationX(targetX - this.tmpRootLocation[0]);
        icon.setTranslationY(targetY - this.tmpRootLocation[1]);
    }

    private static float easeOutBack(float t) {
        final float c1 = 1.70158f;
        final float c3 = c1 + 1.0f;
        float p = t - 1.0f;
        return 1.0f + c3 * p * p * p + c1 * p * p;
    }

    public void setOnReloadListener(OnReloadListener listener) {
        this.reloadListener = listener;
    }

    public boolean isReloading() {
        return this.isReloading;
    }

    public void setReloading(boolean reloading) {
        if (reloading) {
            beginReload();
        } else {
            finishReload();
        }
    }

    private void beginReload() {
        if (!this.isReloadUI || this.isReloading) return;
        this.isReloading = true;

        ImageView icon = this.reloadIcon;
        if (icon == null) return;

        showReloadScrim();
        icon.animate().cancel();
        cancelSpin();
        icon.setAlpha(1.0f);
        icon.setScaleX(1.0f);
        icon.setScaleY(1.0f);
        positionReloadIcon(currentTranslation());

        float from = icon.getRotation() % 360.0f;
        this.spinAnimator = ObjectAnimator.ofFloat(icon, View.ROTATION, from, from + 360.0f);
        this.spinAnimator.setDuration(SPIN_CYCLE_MS);
        this.spinAnimator.setInterpolator(new LinearInterpolator());
        this.spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
        this.spinAnimator.setRepeatMode(ValueAnimator.RESTART);
        this.spinAnimator.start();
        scheduleAutoFinish();
    }

    private void scheduleAutoFinish() {
        cancelAutoFinish();
        boolean hasListener = this.reloadListener != null;
        long delay = hasListener ? this.reloadTimeoutMs : ONE_SHOT_RELOAD_MS;
        if (hasListener && delay <= 0) return;
        this.autoFinishRunnable = () -> {
            if (this.isReloading) finishReload();
        };
        postDelayed(this.autoFinishRunnable, delay);
    }

    private void cancelAutoFinish() {
        if (this.autoFinishRunnable != null) {
            removeCallbacks(this.autoFinishRunnable);
            this.autoFinishRunnable = null;
        }
    }

    public void setReloadTimeoutMs(long milliseconds) {
        this.reloadTimeoutMs = milliseconds;
    }

    private void finishReload() {
        if (!this.isReloading) {
            hideReloadIcon();
            return;
        }
        this.isReloading = false;
        cancelSpin();
        cancelAutoFinish();
        hideReloadScrim();

        ImageView icon = this.reloadIcon;
        if (icon == null) return;

        icon.animate().cancel();
        float from = icon.getRotation();
        float target = (float) (Math.ceil(from / 360.0f) * 360.0f);
        icon.animate()
                .rotation(target)
                .alpha(0.0f)
                .scaleX(0.0f)
                .scaleY(0.0f)
                .setDuration(SPIN_SETTLE_MS)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    if (this.reloadIcon != null && !this.isReloading) {
                        this.reloadIcon.setRotation(0.0f);
                        this.reloadIcon.setAlpha(0.0f);
                        this.reloadIcon.setScaleX(0.0f);
                        this.reloadIcon.setScaleY(0.0f);
                    }
                })
                .start();
    }

    private void hideReloadIcon() {
        if (this.reloadIcon == null) return;
        hideReloadScrim();
        this.reloadIcon.animate().cancel();
        this.reloadIcon.setRotation(0.0f);
        this.reloadIcon.setAlpha(0.0f);
        this.reloadIcon.setScaleX(0.0f);
        this.reloadIcon.setScaleY(0.0f);
    }

    private void showReloadScrim() {
        ImageView icon = this.reloadIcon;
        if (!(icon != null && icon.getParent() instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) icon.getParent();

        View scrim = this.reloadScrim;
        if (scrim == null) {
            scrim = new ReloadScrimView(getContext());
            this.reloadScrim = scrim;
        }
        if (scrim.getParent() instanceof ViewGroup) {
            ((ViewGroup) scrim.getParent()).removeView(scrim);
        }
        releaseScrimBackdrop();

        captureScrimBackdrop(root);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.TOP | Gravity.START);
        root.addView(scrim, Math.max(0, root.indexOfChild(icon)), lp);
        scrim.setAlpha(0.0f);
        scrim.animate().cancel();
        scrim.animate().alpha(1.0f).setDuration(SCRIM_FADE_MS).start();
    }

    private void hideReloadScrim() {
        View scrim = this.reloadScrim;
        if (scrim == null || scrim.getParent() == null) return;
        scrim.animate().cancel();
        scrim.animate()
                .alpha(0.0f)
                .setDuration(SCRIM_FADE_MS)
                .withEndAction(() -> {
                    if (scrim.isAttachedToWindow() && scrim.getParent() instanceof ViewGroup) {
                        ((ViewGroup) scrim.getParent()).removeView(scrim);
                    }
                })
                .start();
    }

    private void captureScrimBackdrop(ViewGroup root) {
        int w = root.getWidth();
        int h = root.getHeight();
        if (w <= 0 || h <= 0) return;
        int sw = Math.max(1, w / SCRIM_DOWNSCALE);
        int sh = Math.max(1, h / SCRIM_DOWNSCALE);
        Bitmap small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(small);
        float scale = sw / (float) w;
        canvas.scale(scale, scale);
        root.draw(canvas);
        LegacyBlur.stackBlur(small, SCRIM_BLUR_RADIUS);
        this.scrimBackdrop = small;
    }

    private void releaseScrimBackdrop() {
        if (this.scrimBackdrop != null && !this.scrimBackdrop.isRecycled()) {
            this.scrimBackdrop.recycle();
        }
        this.scrimBackdrop = null;
    }

    private final class ReloadScrimView extends View {
        private final Paint backdropPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Rect destRect = new Rect();

        ReloadScrimView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            this.destRect.set(0, 0, getWidth(), getHeight());
            Bitmap backdrop = ReflectLayout.this.scrimBackdrop;
            if (backdrop != null && !backdrop.isRecycled()) {
                canvas.drawBitmap(backdrop, null, this.destRect, this.backdropPaint);
                canvas.drawColor(SCRIM_DIM_COLOR);
            } else {
                canvas.drawColor(SCRIM_DIM_COLOR);
            }
        }
    }

    public void applyOverscroll(float delta) {
        cancelSpringBack();
        this.rawOverscroll += delta;
        applyTranslation(visualFromRaw(this.rawOverscroll));
    }

    public void setOrientation(int orientation) {
        if (this.orientation == orientation) return;
        this.orientation = orientation;
        cancelSpringBack();
        setupSpring();
    }

    @Override
    public boolean performClick() {
        if (this.HapticEngine != null) {
            this.HapticEngine.onTap();
        }
        return super.performClick();
    }

    private void recycleVelocityTracker() {
        if (this.velocityTracker != null) {
            this.velocityTracker.recycle();
            this.velocityTracker = null;
        }
    }
}

