package xime.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xime.animation.MotionCurve;
import xime.ui.layout.Layout;
import xime.ui.utils.Window;

/**
 * Standalone panel window with solid 50% opacity theme-aware backdrop
 * and strictly centered content dialog layout.
 */
public class Dialog extends Layout {

    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;
    private FrameLayout mOuterBackdrop;
    private FrameLayout mContentHolder;
    private boolean mIsShowing = false;
    private boolean mCancelable = true;
    private boolean mCanceledOnTouchOutside = true;
    private OnDismissListener mOnDismissListener;
    private OnShowListener mOnShowListener;

    public Dialog(@NonNull Context context) {
        super(context);
        init();
    }

    public Dialog(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Dialog(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mWindowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        mLayoutParams = createDefaultLayoutParams();

        setFitsSystemWindows(false);
        setPadding(0, 0, 0, 0);
        setGravity(Gravity.CENTER);

        // 1. Full-screen outer backdrop view (dim background ONLY scaled 1.5x)
        mOuterBackdrop = new FrameLayout(getContext());
        mOuterBackdrop.setBackgroundColor(getBackdropColor());
        mOuterBackdrop.setFitsSystemWindows(false);
        mOuterBackdrop.setPadding(0, 0, 0, 0);
        mOuterBackdrop.setScaleX(1.5f);
        mOuterBackdrop.setScaleY(1.5f);

        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mOuterBackdrop.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(mOuterBackdrop, (v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });

        // 2. Centered content container (unscaled 1.0x)
        mContentHolder = new FrameLayout(getContext());
        mContentHolder.setPadding(0, 0, 0, 0);

        super.addView(mOuterBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams contentHolderLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        super.addView(mContentHolder, contentHolderLp);

        // Tap outer backdrop to dismiss
        mOuterBackdrop.setOnClickListener(v -> {
            if (mCancelable && mCanceledOnTouchOutside) {
                dismiss();
            }
        });

        // Prevent clicks inside content card from dismissing
        mContentHolder.setOnClickListener(v -> { /* Consume click */ });
    }

    private int getBackdropColor() {
        boolean isDarkMode = (getContext().getResources().getConfiguration().uiMode 
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return isDarkMode ? Color.parseColor("#80000000") : Color.parseColor("#80FFFFFF");
    }

    private WindowManager.LayoutParams createDefaultLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.FILL;
        lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        lp.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        lp.dimAmount = 0.0f;
        return lp;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int realWidth = MeasureSpec.getSize(widthMeasureSpec);
        int realHeight = MeasureSpec.getSize(heightMeasureSpec);
        Activity activity = findActivity(getContext());
        if (activity != null) {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
            realWidth = dm.widthPixels;
            realHeight = dm.heightPixels;
        }
        setMeasuredDimension(realWidth, realHeight);

        if (mOuterBackdrop != null) {
            int extraMargin = (int) (200 * getResources().getDisplayMetrics().density);
            int backdropWidth = realWidth + (extraMargin * 2);
            int backdropHeight = realHeight + (extraMargin * 2);
            mOuterBackdrop.measure(
                    MeasureSpec.makeMeasureSpec(backdropWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(backdropHeight, MeasureSpec.EXACTLY)
            );
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mOuterBackdrop != null) {
            int extraMargin = (int) (200 * getResources().getDisplayMetrics().density);
            mOuterBackdrop.layout(-extraMargin, -extraMargin, getMeasuredWidth() + extraMargin, getMeasuredHeight() + extraMargin);
        }
    }

    public void setToken(@NonNull android.os.IBinder token) {
        mLayoutParams.token = token;
    }

    public WindowManager.LayoutParams getLayoutParams2() {
        return mLayoutParams;
    }

    public void setContentView(@NonNull View view) {
        mContentHolder.removeAllViews();
        int margin16 = (int) (16 * getResources().getDisplayMetrics().density);
        
        ViewGroup.LayoutParams childLp = view.getLayoutParams();
        if (childLp == null) {
            childLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        }
        childLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        view.setLayoutParams(childLp);

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View cv = vg.getChildAt(i);
                ViewGroup.LayoutParams clp = cv.getLayoutParams();
                if (clp != null) {
                    clp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    cv.setLayoutParams(clp);
                }
            }
        }

        FrameLayout.LayoutParams frameLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        frameLp.setMargins(margin16, 0, margin16, 0);
        view.setMinimumWidth(0);
        mContentHolder.addView(view, frameLp);
    }

    public void setCancelable(boolean cancelable) {
        mCancelable = cancelable;
    }

    public void setCanceledOnTouchOutside(boolean cancel) {
        mCanceledOnTouchOutside = cancel;
        if (cancel) {
            mLayoutParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        } else {
            mLayoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        }
    }

    public void setOnDismissListener(OnDismissListener listener) {
        mOnDismissListener = listener;
    }

    public void setOnShowListener(OnShowListener listener) {
        mOnShowListener = listener;
    }

    public boolean isShowing() {
        return mIsShowing;
    }

    private boolean mIsDismissing = false;

    public void show() {
        if (mIsShowing)
            return;
        if (mLayoutParams.token == null) {
            throw new IllegalStateException(
                    "Dialog requires a window token before show(). Call setToken(...) first, "
                            + "e.g. panel.setToken(activity.getWindow().getDecorView().getWindowToken())");
        }
        mIsDismissing = false;

        if (mOuterBackdrop != null) {
            mOuterBackdrop.setBackgroundColor(getBackdropColor());
        }

        Activity hostActivity = findActivity(getContext());
        if (hostActivity != null) {
            View decorView = hostActivity.getWindow().getDecorView();
            Window.applyGlassToPanel(this, decorView);
        }

        if (getParent() == null) {
            mWindowManager.addView(this, mLayoutParams);
        }
        mIsShowing = true;

        mOuterBackdrop.setAlpha(0f);
        mOuterBackdrop.animate()
                .alpha(1f)
                .setDuration(240)
                .start();

        mContentHolder.setAlpha(0f);
        mContentHolder.setScaleX(0.85f);
        mContentHolder.setScaleY(0.85f);
        mContentHolder.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280)
                .setInterpolator(MotionCurve.FLUID)
                .start();

        if (mOnShowListener != null) {
            mOnShowListener.onShow(this);
        }
    }

    public void dismiss() {
        if (!mIsShowing || mIsDismissing)
            return;

        mIsDismissing = true;

        mOuterBackdrop.animate()
                .alpha(0f)
                .setDuration(200)
                .start();

        mContentHolder.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(200)
                .setInterpolator(MotionCurve.FLUID)
                .withEndAction(() -> {
                    mIsShowing = false;
                    mIsDismissing = false;
                    if (getParent() != null) {
                        try {
                            mWindowManager.removeView(Dialog.this);
                        } catch (Exception ignored) {}
                    }
                    if (mOnDismissListener != null) {
                        mOnDismissListener.onDismiss(Dialog.this);
                    }
                })
                .start();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (mCancelable && mCanceledOnTouchOutside
                && ev.getActionMasked() == android.view.MotionEvent.ACTION_OUTSIDE) {
            dismiss();
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (mCancelable && keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            dismiss();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private Activity findActivity(Context context) {
        if (context instanceof Activity) {
            return (Activity) context;
        } else if (context instanceof android.content.ContextWrapper) {
            return findActivity(((android.content.ContextWrapper) context).getBaseContext());
        }
        return null;
    }

    public interface OnDismissListener {
        void onDismiss(Dialog panel);
    }

    public interface OnShowListener {
        void onShow(Dialog panel);
    }
}
