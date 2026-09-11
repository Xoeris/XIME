package xime.ui.menu;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xime.R;
import xime.ui.layout.Layout;
import xime.ui.layout.BlurLayout;
import xime.ui.layout.LinearLayout;
import xime.ui.layout.RelativeLayout;
import xime.graphics.shader.blur.LegacyBlur;
import xime.ui.view.ImageView;
import xime.ui.view.TextView;
import xime.ui.common.IconButton;

/**
 * * TitleMenu - A simplified version of HeaderMenu that only displays a centered title.
 * It does not support scroll transitions but supports FloatingMenu triggers.
 */
public class TitleMenu extends Layout {
    private BlurLayout mGlassWrapper;
    private RelativeLayout toolbarContainer;
    private LinearLayout startIconsContainer;
    private LinearLayout endIconsContainer;
    private TextView smallTitle;

    private IconButton floatingMenuTrigger;
    private int[] linkedFloatingIcons;
    private FloatingMenu.OnLayerSelectedListener linkedFloatingListener;
    private FloatingMenu floatingOverlay;
    private Layout floatingContainer;

    public TitleMenu(@NonNull Context context) {
        this(context, null);
    }

    public TitleMenu(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TitleMenu(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        mGlassWrapper = new BlurLayout(context, attrs);
        super.addView(mGlassWrapper, new LayoutParams(-1, -1));

        mGlassWrapper.setBlurType(BlurLayout.BlurType.GLASS);
        mGlassWrapper.setBlurRadius(50.0f);
        mGlassWrapper.crystal.set3DBevel(0.92f, 0.6f);
        mGlassWrapper.crystal.setDepthEffect(0.06f);
        mGlassWrapper.crystal.setDistortionAmount(0.12f);
        mGlassWrapper.crystal.setFluidWarp(0.015f, 1.2f);
        setBackgroundColor(0);
        setElevation(0.0f);

        String titleText = null;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TitleMenu);
            try {
                titleText = a.getString(R.styleable.TitleMenu_xoerisSmallTitle);
            } finally {
                a.recycle();
            }
        }

        int baseToolbarHeight = dpToPx(56);

        this.toolbarContainer = new RelativeLayout(context);
        this.toolbarContainer.setId(View.generateViewId());
        
        this.startIconsContainer = new xime.ui.layout.LinearLayout(context);
        this.startIconsContainer.setId(View.generateViewId());
        this.startIconsContainer.setOrientation(LinearLayout.HORIZONTAL);
        this.startIconsContainer.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        RelativeLayout.LayoutParams sicLp = new RelativeLayout.LayoutParams(-2, -1);
        sicLp.addRule(RelativeLayout.ALIGN_PARENT_START);
        sicLp.setMarginStart(dpToPx(16));
        this.toolbarContainer.addView(this.startIconsContainer, sicLp);

        this.endIconsContainer = new xime.ui.layout.LinearLayout(context);
        this.endIconsContainer.setId(View.generateViewId());
        this.endIconsContainer.setOrientation(LinearLayout.HORIZONTAL);
        this.endIconsContainer.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
        RelativeLayout.LayoutParams eicLp = new RelativeLayout.LayoutParams(-2, -1);
        eicLp.addRule(RelativeLayout.ALIGN_PARENT_END);
        eicLp.setMarginEnd(dpToPx(16));
        this.toolbarContainer.addView(this.endIconsContainer, eicLp);

        this.smallTitle = new TextView(context);
        this.smallTitle.setTextSize(17.0f);
        this.smallTitle.setTypeface(Typeface.DEFAULT_BOLD);
        this.smallTitle.setGravity(android.view.Gravity.CENTER);
        this.smallTitle.setTextColor(context.getColor(R.color.xoeris_text_primary));
        if (titleText != null) this.smallTitle.setText(titleText);

        RelativeLayout.LayoutParams smallTitleLp = new RelativeLayout.LayoutParams(-2, -2);
        smallTitleLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        // Constraint to avoid overlap
        smallTitleLp.addRule(RelativeLayout.END_OF, this.startIconsContainer.getId());
        smallTitleLp.addRule(RelativeLayout.START_OF, this.endIconsContainer.getId());
        this.toolbarContainer.addView(this.smallTitle, smallTitleLp);

        mGlassWrapper.addView(this.toolbarContainer, new MarginLayoutParams(-1, baseToolbarHeight));
        updateTitleConstraints();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshTheme();
    }

    public void refreshTheme() {
        if (mGlassWrapper == null) return;
        boolean isDark = mGlassWrapper.getActiveTheme() == LegacyBlur.ThemeMode.DARK;
        int primaryColor = getContext().getColor(isDark ? R.color.xoeris_text_primary : android.R.color.black);
        setTitleColor(primaryColor);
        applyTintToContainer(this.startIconsContainer, primaryColor);
        applyTintToContainer(this.endIconsContainer, primaryColor);
    }

    private void applyTintToContainer(ViewGroup container, int color) {
        if (container == null) return;
        ColorStateList csl = ColorStateList.valueOf(color);
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof ImageView) {
                ((ImageView) child).setImageTintList(csl);
            }
        }
    }

    public void useFloatingMenu(int iconRes, int[] radialIcons, FloatingMenu.OnLayerSelectedListener listener) {
        this.linkedFloatingIcons = radialIcons;
        this.linkedFloatingListener = listener;
        if (radialIcons != null) {
            ensureFloatingMenuTriggerExists();
            this.floatingMenuTrigger.setImageResource(iconRes);
            this.floatingMenuTrigger.setVisibility(VISIBLE);
            this.floatingMenuTrigger.setAlpha(1.0f);
        } else if (this.floatingMenuTrigger != null) {
            this.floatingMenuTrigger.setVisibility(GONE);
        }
    }

    public void setFloatingMenuTriggerIcon(int iconRes) {
        if (this.floatingMenuTrigger != null) {
            this.floatingMenuTrigger.setImageResource(iconRes);
        }
    }

    private void ensureFloatingMenuTriggerExists() {
        if (this.floatingMenuTrigger != null) return;
        this.floatingMenuTrigger = new IconButton(getContext());
        this.floatingMenuTrigger.setAlpha(0.0f);
        this.floatingMenuTrigger.setVisibility(GONE);
        this.floatingMenuTrigger.setId(View.generateViewId());
        this.floatingMenuTrigger.setBackground(null);
        this.floatingMenuTrigger.setPadding(0, 0, 0, 0);
        this.floatingMenuTrigger.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        RelativeLayout.LayoutParams faLp = new RelativeLayout.LayoutParams(dpToPx(40), dpToPx(40));

        this.floatingMenuTrigger.setOnTouchListener(new OnTouchListener() {
            private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            private boolean isLongPressTriggered = false;
            private final Runnable longPressRunnable = () -> {
                if (linkedFloatingIcons != null) {
                    isLongPressTriggered = true;
                    showFloating(linkedFloatingIcons.length, 0, linkedFloatingListener);
                }
            };

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        isLongPressTriggered = false;
                        handler.postDelayed(longPressRunnable, 500);
                        v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(150).start();
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        handler.removeCallbacks(longPressRunnable);
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                        if (isLongPressTriggered) {
                            int selected = floatingOverlay != null && floatingOverlay.isAnchorSelected() ? 0 : (floatingOverlay != null ? floatingOverlay.getSelectedLayer() : 0);
                            if (floatingOverlay != null && linkedFloatingListener != null && !floatingOverlay.isAnchorSelected()) {
                                linkedFloatingListener.onLayerSelected(selected);
                            }
                            hideFloating();
                        } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                            v.performClick();
                        }
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (isLongPressTriggered && floatingOverlay != null) {
                            int[] fLoc = new int[2];
                            floatingOverlay.getLocationOnScreen(fLoc);
                            float fCenterX = fLoc[0] + (floatingOverlay.getWidth() / 2.0f);
                            float fCenterY = fLoc[1] + (floatingOverlay.getHeight() / 2.0f);
                            floatingOverlay.updateSelectionByTouch(event.getRawX() - fCenterX, event.getRawY() - fCenterY);
                        }
                        return true;
                }
                return false;
            }
        });

        addToolbarView(this.floatingMenuTrigger, true, faLp);
    }

    public void showFloatingMenu(int iconRes, int[] radialIcons, FloatingMenu.OnLayerSelectedListener listener) {
        this.linkedFloatingIcons = radialIcons;
        this.linkedFloatingListener = listener;
        if (radialIcons != null) {
            showFloating(radialIcons.length, 0, listener);
        }
    }

    public void updateFloatingMenuTouch(float rawX, float rawY) {
        if (floatingOverlay != null) {
            int[] fLoc = new int[2];
            floatingOverlay.getLocationOnScreen(fLoc);
            float fCenterX = fLoc[0] + (floatingOverlay.getWidth() / 2.0f);
            float fCenterY = fLoc[1] + (floatingOverlay.getHeight() / 2.0f);
            floatingOverlay.updateSelectionByTouch(rawX - fCenterX, rawY - fCenterY);
        }
    }

    public void finishFloatingMenuTouch() {
        if (floatingOverlay != null) {
            int selected = floatingOverlay.isAnchorSelected() ? 0 : floatingOverlay.getSelectedLayer();
            if (linkedFloatingListener != null && !floatingOverlay.isAnchorSelected()) {
                linkedFloatingListener.onLayerSelected(selected);
            }
            hideFloating();
        }
    }

    private void showFloating(int totalLayers, int currentLayer, FloatingMenu.OnLayerSelectedListener listener) {
        android.app.Activity activity = getActivity();
        if (activity == null) return;

        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
        floatingContainer = new Layout(getContext());
        floatingContainer.setBackgroundColor(android.graphics.Color.parseColor("#80000000"));
        floatingContainer.setAlpha(0.0f);

        int menuSize = dpToPx(280);

        floatingOverlay = new FloatingMenu(getContext(), totalLayers, currentLayer, android.graphics.Color.BLUE, linkedFloatingIcons, layer -> {});

        View blurRoot = activity.findViewById(android.R.id.content);
        if (blurRoot != null) floatingOverlay.setBlurRootView(blurRoot);

        LayoutParams lp = new LayoutParams(menuSize, menuSize, android.view.Gravity.CENTER);

        floatingContainer.addView(floatingOverlay, lp);
        root.addView(floatingContainer, new ViewGroup.LayoutParams(-1, -1));

        floatingContainer.animate().alpha(1.0f).setDuration(200).start();
        floatingOverlay.setScaleX(0.5f);
        floatingOverlay.setScaleY(0.5f);
        floatingOverlay.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(300)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();
    }

    private void hideFloating() {
        if (floatingContainer != null) {
            floatingContainer.animate().alpha(0.0f).setDuration(200).withEndAction(() -> {
                ViewGroup parent = (ViewGroup) floatingContainer.getParent();
                if (parent != null) parent.removeView(floatingContainer);
                floatingContainer = null;
                floatingOverlay = null;
            }).start();
        }
    }

    public void setTitle(CharSequence title) {
        if (this.smallTitle != null) this.smallTitle.setText(title);
    }

    public void setTitleColor(int color) {
        if (this.smallTitle != null) this.smallTitle.setTextColor(color);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (mGlassWrapper != null && child != mGlassWrapper) {
            if (this.toolbarContainer != null && child != this.toolbarContainer) {
                RelativeLayout.LayoutParams ap;
                if (params instanceof RelativeLayout.LayoutParams) {
                    ap = new RelativeLayout.LayoutParams((RelativeLayout.LayoutParams) params);
                } else if (params instanceof ViewGroup.MarginLayoutParams) {
                    ap = new RelativeLayout.LayoutParams((ViewGroup.MarginLayoutParams) params);
                } else {
                    ap = new RelativeLayout.LayoutParams(params);
                }
                
                int[] rules = ap.getRules();
                boolean isEnd = (rules[RelativeLayout.ALIGN_PARENT_END] != 0 || rules[11] != 0);
                
                addToolbarView(child, isEnd, ap);
                return;
            }
            mGlassWrapper.addView(child, index, params);
            return;
        }
        super.addView(child, index, params);
    }

    private void addToolbarView(View view, boolean isEnd, RelativeLayout.LayoutParams lp) {
        if (view.getId() == View.NO_ID) view.setId(View.generateViewId());
        
        if (view.getParent() != null) {
            ((ViewGroup) view.getParent()).removeView(view);
        }

        if (view instanceof IconButton) {
            view.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        }

        int width = lp.width;
        int height = lp.height;

        if (view instanceof IconButton) {
            if (width > 0 && width < dpToPx(40)) width = dpToPx(40);
            if (height > 0 && height < dpToPx(40)) height = dpToPx(40);
        }

        LinearLayout.LayoutParams lulp = new LinearLayout.LayoutParams(
                width > 0 ? width : dpToPx(40),
                height > 0 ? height : dpToPx(40)
        );
        lulp.gravity = android.view.Gravity.CENTER_VERTICAL;
        lulp.setMarginStart(lp.getMarginStart() > 0 ? lp.getMarginStart() : dpToPx(4));
        lulp.setMarginEnd(lp.getMarginEnd() > 0 ? lp.getMarginEnd() : dpToPx(4));

        if (isEnd) {
            this.endIconsContainer.addView(view, lulp);
        } else {
            this.startIconsContainer.addView(view, lulp);
        }

        // Apply current theme color
        if (mGlassWrapper != null) {
            boolean isDark = mGlassWrapper.getActiveTheme() == LegacyBlur.ThemeMode.DARK;
            int primaryColor = getContext().getColor(isDark ? R.color.xoeris_text_primary : android.R.color.black);
            if (view instanceof ImageView) {
                ((ImageView) view).setImageTintList(ColorStateList.valueOf(primaryColor));
            }
        }

        updateTitleConstraints();
    }

    private void updateTitleConstraints() {
        if (this.smallTitle == null || this.toolbarContainer == null) return;
        RelativeLayout.LayoutParams titleLp = (RelativeLayout.LayoutParams) this.smallTitle.getLayoutParams();
        
        titleLp.removeRule(RelativeLayout.CENTER_IN_PARENT);
        titleLp.removeRule(RelativeLayout.END_OF);
        titleLp.removeRule(RelativeLayout.START_OF);

        titleLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        titleLp.addRule(RelativeLayout.END_OF, this.startIconsContainer.getId());
        titleLp.addRule(RelativeLayout.START_OF, this.endIconsContainer.getId());
        
        int horizontalPadding = dpToPx(16);
        titleLp.setMarginStart(horizontalPadding);
        titleLp.setMarginEnd(horizontalPadding);
        
        this.smallTitle.setSingleLine(true);
        this.smallTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        this.smallTitle.setLayoutParams(titleLp);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private android.app.Activity getActivity() {
        android.content.Context ctx = getContext();
        while (ctx instanceof android.content.ContextWrapper) {
            if (ctx instanceof android.app.Activity) return (android.app.Activity) ctx;
            ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }
}


