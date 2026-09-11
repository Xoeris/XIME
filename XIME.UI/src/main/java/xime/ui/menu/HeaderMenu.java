package xime.ui.menu;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xime.R;

import xime.core.theme.ThemeManager;
import xime.ui.layout.Layout;
import xime.ui.layout.BlurLayout;
import xime.ui.layout.LinearLayout;
import xime.ui.layout.RelativeLayout;
import xime.graphics.shader.blur.LegacyBlur;
import xime.ui.view.ImageView;
import xime.ui.view.TextView;
import xime.ui.bar.SearchBar;
import xime.ui.common.IconButton;

public class HeaderMenu extends Layout {
    private BlurLayout mGlassWrapper;
    private TextView bigTitle;
    private RelativeLayout bottomLayout;
    private LinearLayout startIconsContainer;
    private LinearLayout endIconsContainer;
    private int collapsedHeight;
    private SearchBar.OnSearchListener globalSearchListener;
    private boolean isCollapsed = false;
    private boolean isSearchExpanded = false;
    private int lastScrollY = -1;
    private View linkedSearchView;
    private boolean searchEnabledAttr = false;
    private IconButton searchIcon;
    private TextView smallTitle;
    private RelativeLayout toolbarContainer;
    private SearchBar toolbarSearchControl;
    private LinearLayout topLayout;
    private IconButton floatingMenuTrigger;
    private int[] linkedFloatingIcons;
    private FloatingMenu.OnLayerSelectedListener linkedFloatingListener;
    private FloatingMenu floatingOverlay;
    private Layout floatingContainer;

    /** ThemeManager listener, registered in onAttachedToWindow, removed in onDetachedFromWindow. */
    private final ThemeManager.OnThemeChangedListener mThemeListener = mode -> refreshTheme();

    public HeaderMenu(@NonNull Context context) {
        this(context, null);
    }

    public HeaderMenu(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HeaderMenu(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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
        // Flat top (flush with screen top/status bar), rounded bottom only
        mGlassWrapper.setTopCornerRadius(0f);
        mGlassWrapper.setBottomCornerRadius(dpToPx(28));
        setBackgroundColor(0);
        setElevation(0.0f);

        String bigTitleText = null;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.HeaderMenu);
            try {
                this.searchEnabledAttr = a.getBoolean(R.styleable.HeaderMenu_xoerisSearchEnabled, false);
                bigTitleText = a.getString(R.styleable.HeaderMenu_xoerisBigTitle);
            } finally {
                a.recycle();
            }
        }

        int statusBarHeight = getStatusBarHeight(context);
        int baseToolbarHeight = dpToPx(56);
        this.collapsedHeight = baseToolbarHeight + statusBarHeight;

        // Expanded Layout
        this.topLayout = new xime.ui.layout.LinearLayout(context);
        this.topLayout.setPadding(dpToPx(24), 0, dpToPx(24), 0);
        this.topLayout.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);

        this.bigTitle = new TextView(context);
        this.bigTitle.setTextSize(26.0f);
        this.bigTitle.setTypeface(Typeface.DEFAULT_BOLD);
        this.bigTitle.setTextColor(context.getColor(R.color.xoeris_text_primary));
        if (bigTitleText != null) this.bigTitle.setText(bigTitleText);

        this.topLayout.addView(this.bigTitle, new android.widget.LinearLayout.LayoutParams(-2, -2));
        
        // Fix: Use the same total height as collapsed view and use padding for status bar safety
        this.topLayout.setPadding(dpToPx(24), statusBarHeight + dpToPx(12), dpToPx(24), 0);
        LayoutParams topLp = new LayoutParams(-1, this.collapsedHeight);
        mGlassWrapper.addView(this.topLayout, topLp);

        // Collapsed Layout
        this.bottomLayout = new RelativeLayout(context);
        LayoutParams bottomLp = new LayoutParams(-1, this.collapsedHeight);

        this.toolbarContainer = new RelativeLayout(context);
        this.toolbarContainer.setId(View.generateViewId());
        RelativeLayout.LayoutParams containerLp = new RelativeLayout.LayoutParams(-1, baseToolbarHeight);
        containerLp.topMargin = statusBarHeight;

        this.startIconsContainer = new LinearLayout(context);
        this.startIconsContainer.setId(View.generateViewId());
        this.startIconsContainer.setOrientation(LinearLayout.HORIZONTAL);
        this.startIconsContainer.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        RelativeLayout.LayoutParams sicLp = new RelativeLayout.LayoutParams(-2, -1);
        sicLp.addRule(RelativeLayout.ALIGN_PARENT_START);
        this.toolbarContainer.addView(this.startIconsContainer, sicLp);

        this.endIconsContainer = new LinearLayout(context);
        this.endIconsContainer.setId(View.generateViewId());
        this.endIconsContainer.setOrientation(LinearLayout.HORIZONTAL);
        this.endIconsContainer.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
        RelativeLayout.LayoutParams eicLp = new RelativeLayout.LayoutParams(-2, -1);
        eicLp.addRule(RelativeLayout.ALIGN_PARENT_END);
        this.toolbarContainer.addView(this.endIconsContainer, eicLp);

        this.smallTitle = new TextView(context);
        this.smallTitle.setTextSize(17.0f);
        this.smallTitle.setTypeface(Typeface.DEFAULT_BOLD);
        this.smallTitle.setGravity(17);
        this.smallTitle.setAlpha(0.0f);
        this.smallTitle.setTextColor(context.getColor(R.color.xoeris_text_primary));
        this.smallTitle.setVisibility(GONE);
        if (bigTitleText != null) this.smallTitle.setText(bigTitleText);

        RelativeLayout.LayoutParams smallTitleLp = new RelativeLayout.LayoutParams(-2, -2);
        smallTitleLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        this.toolbarContainer.addView(this.smallTitle, smallTitleLp);

        if (this.searchEnabledAttr) ensureSearchIconExists();

        ensureFloatingMenuTriggerExists();

        this.bottomLayout.addView(this.toolbarContainer, containerLp);
        mGlassWrapper.addView(this.bottomLayout, bottomLp);
        this.bottomLayout.setVisibility(GONE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setClipToPadding(false);
        setClipChildren(false);
        if (mGlassWrapper != null) {
            mGlassWrapper.setClipToPadding(false);
            mGlassWrapper.setClipChildren(false);
        }
        refreshTheme();
        try {
            ThemeManager.get().addListener(mThemeListener);
        } catch (IllegalStateException ignored) {
            // ThemeManager not yet initialised, refreshTheme() above used BlurLayout fallback.
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            ThemeManager.get().removeListener(mThemeListener);
        } catch (IllegalStateException ignored) {}
    }

    public void setBlurRootView(View view) {
        if (mGlassWrapper != null) {
            mGlassWrapper.setBlurRootView(view);
        }
    }

    public void refreshImmediately() {
        if (mGlassWrapper != null) {
            mGlassWrapper.refreshImmediately();
        }
    }

    public void refreshTheme() {
        if (mGlassWrapper == null) return;
        boolean isDark;
        try {
            isDark = ThemeManager.get().isDark();
        } catch (IllegalStateException e) {
            // ThemeManager not yet initialised, fall back to BlurLayout's own state.
            isDark = mGlassWrapper.getActiveTheme() == LegacyBlur.ThemeMode.DARK;
        }
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

    private void ensureSearchIconExists() {
        if (this.searchIcon != null) return;
        this.searchIcon = new IconButton(getContext());
        this.searchIcon.setImageResource(R.drawable.xoeris_search);
        this.searchIcon.setAlpha(0.0f);
        this.searchIcon.setVisibility(GONE);
        this.searchIcon.setId(View.generateViewId());
        this.searchIcon.setBackground(null);
        this.searchIcon.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        this.searchIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        
        RelativeLayout.LayoutParams siLp = new RelativeLayout.LayoutParams(dpToPx(40), dpToPx(40));
        siLp.setMarginEnd(dpToPx(16));
        
        this.searchIcon.setOnClickListener(v -> {
            if (!this.isSearchExpanded) expandSearch();
            else collapseSearch();
        });
        addToolbarView(this.searchIcon, true, siLp);
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

    private void ensureToolbarSearchControlExists() {
        if (this.toolbarSearchControl != null) return;
        ensureSearchIconExists();
        this.toolbarSearchControl = new SearchBar(getContext());
        this.toolbarSearchControl.setVisibility(GONE);
        this.toolbarSearchControl.setId(View.generateViewId());
        this.toolbarSearchControl.setBackgroundColor(0);
        
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(-1, -1);
        lp.addRule(RelativeLayout.ALIGN_PARENT_START);
        lp.addRule(RelativeLayout.START_OF, this.endIconsContainer.getId());
        lp.setMarginEnd(dpToPx(8));
        lp.setMarginStart(dpToPx(16));
        
        if (this.globalSearchListener != null) {
            this.toolbarSearchControl.setOnSearchListener(this.globalSearchListener);
        }
        this.toolbarContainer.addView(this.toolbarSearchControl, lp);
    }

    public void expandSearch() {
        if (this.isSearchExpanded || this.toolbarContainer == null || this.searchIcon == null) return;
        this.isSearchExpanded = true;
        ensureToolbarSearchControlExists();
        
        this.endIconsContainer.bringToFront();
        
        this.startIconsContainer.animate().alpha(0.0f).setDuration(200).withEndAction(() -> {
            this.startIconsContainer.setVisibility(GONE);
            this.smallTitle.setVisibility(GONE);
        }).start();
        this.smallTitle.animate().alpha(0.0f).setDuration(200).start();
        
        for (int i = 0; i < this.endIconsContainer.getChildCount(); i++) {
            final View child = this.endIconsContainer.getChildAt(i);
            if (child != this.searchIcon) {
                child.animate().alpha(0.0f).setDuration(200).withEndAction(() -> child.setVisibility(GONE)).start();
            }
        }
        
        this.toolbarSearchControl.setVisibility(VISIBLE);
        this.toolbarSearchControl.setAlpha(0.0f);
        this.toolbarSearchControl.setTranslationX(dpToPx(30));
        this.toolbarSearchControl.animate().alpha(1.0f).translationX(0.0f).setDuration(350).setInterpolator(new DecelerateInterpolator()).start();
        this.toolbarSearchControl.focusSearch();
        
        this.searchIcon.animate().rotation(90.0f).setDuration(300).start();
        this.searchIcon.setImageResource(R.drawable.xoeris_remove);
    }

    public void collapseSearch() {
        if (!this.isSearchExpanded || this.toolbarContainer == null || this.searchIcon == null) return;
        this.isSearchExpanded = false;
        
        this.startIconsContainer.setVisibility(VISIBLE);
        this.startIconsContainer.animate().alpha(1.0f).setDuration(300).start();
        this.smallTitle.setVisibility(VISIBLE);
        this.smallTitle.animate().alpha(1.0f).setDuration(300).start();

        for (int i = 0; i < this.endIconsContainer.getChildCount(); i++) {
            View child = this.endIconsContainer.getChildAt(i);
            if (child != this.searchIcon) {
                child.setVisibility(VISIBLE);
                child.animate().alpha(1.0f).setDuration(300).start();
            }
        }
        
        this.toolbarSearchControl.animate().alpha(0.0f).translationX(dpToPx(30)).setDuration(250).withEndAction(() -> {
            this.toolbarSearchControl.setVisibility(GONE);
            this.toolbarSearchControl.clearQuery();
        }).start();
        
        this.searchIcon.animate().rotation(0.0f).setDuration(300).start();
        this.searchIcon.setImageResource(R.drawable.xoeris_search);
    }

    public void useSearchControl(View searchView) {
        if (this.linkedSearchView != null && this.linkedSearchView != searchView) {
            this.linkedSearchView.setAlpha(1.0f);
            this.linkedSearchView.setTranslationY(0.0f);
        }
        this.linkedSearchView = searchView;
        if (searchView != null) {
            searchView.setAlpha(1.0f);
            searchView.setTranslationY(0.0f);
            this.searchEnabledAttr = true;
            ensureSearchIconExists();
            if (this.isCollapsed) {
                this.searchIcon.setVisibility(VISIBLE);
                this.searchIcon.setAlpha(1.0f);
            }
        } else {
            this.searchEnabledAttr = false;
            if (this.searchIcon != null) {
                this.searchIcon.setVisibility(GONE);
                if (this.isSearchExpanded) collapseSearch();
            }
        }
    }

    public void useFloatingMenu(int iconRes, int[] radialIcons, FloatingMenu.OnLayerSelectedListener listener) {
        this.linkedFloatingIcons = radialIcons;
        this.linkedFloatingListener = listener;
        if (radialIcons != null) {
            ensureFloatingMenuTriggerExists();
            this.floatingMenuTrigger.setImageResource(iconRes);
            if (this.isCollapsed) {
                this.floatingMenuTrigger.setVisibility(VISIBLE);
                this.floatingMenuTrigger.setAlpha(1.0f);
            } else {
                this.floatingMenuTrigger.setVisibility(GONE);
                this.floatingMenuTrigger.setAlpha(0.0f);
            }
        } else if (this.floatingMenuTrigger != null) {
            this.floatingMenuTrigger.setVisibility(GONE);
        }
    }

    public void updateScroll(int scrollY) {
        if (scrollY < 0) scrollY = 0;
        if (scrollY != this.lastScrollY || scrollY == 0) {
            this.lastScrollY = scrollY;

            float threshold = dpToPx(56);
            float fraction = Math.max(0.0f, Math.min(1.0f, scrollY / threshold));
            
            if (mGlassWrapper != null && scrollY % 4 == 0) {
                mGlassWrapper.crystal.setDistortionAmount((0.05f * fraction) + 0.12f);
                mGlassWrapper.crystal.set3DBevel(0.92f + (0.03f * fraction), 0.6f);
                mGlassWrapper.crystal.setDepthEffect(0.06f + (0.02f * fraction));
            }

            if (fraction < 0.5f) {
                if (this.isCollapsed) {
                    this.isCollapsed = false;
                    this.topLayout.setVisibility(VISIBLE);
                    this.bottomLayout.setVisibility(GONE);
                    this.smallTitle.setVisibility(GONE);
                    if (this.searchIcon != null) {
                        this.searchIcon.setVisibility(GONE);
                    }
                    if (this.floatingMenuTrigger != null) {
                        this.floatingMenuTrigger.setVisibility(GONE);
                    }
                    if (this.isSearchExpanded) collapseSearch();
                }
                this.topLayout.setAlpha(1.0f - (2.0f * fraction));
            } else {
                if (!this.isCollapsed) {
                    this.isCollapsed = true;
                    this.topLayout.setVisibility(GONE);
                    this.bottomLayout.setVisibility(VISIBLE);
                    this.smallTitle.setVisibility(VISIBLE);

                    // Show search icon if enabled
                    if (this.searchIcon != null && (this.linkedSearchView != null || this.searchEnabledAttr)) {
                        this.searchIcon.setVisibility(VISIBLE);
                    }

                    // Show floating trigger if exists
                    if (this.floatingMenuTrigger != null && this.linkedFloatingIcons != null) {
                        this.floatingMenuTrigger.setVisibility(VISIBLE);
                    }

                    // Show any other custom icons
                    for (int i = 0; i < this.startIconsContainer.getChildCount(); i++) {
                        this.startIconsContainer.getChildAt(i).setVisibility(VISIBLE);
                    }
                    for (int i = 0; i < this.endIconsContainer.getChildCount(); i++) {
                        View child = this.endIconsContainer.getChildAt(i);
                        if (child != this.searchIcon && child != this.floatingMenuTrigger) {
                            child.setVisibility(VISIBLE);
                        }
                    }
                }
                float localFraction = (fraction - 0.5f) * 2.0f;
                this.bottomLayout.setAlpha(localFraction);
                localFractionAdjusted(fraction);
            }
            this.bigTitle.setTranslationY(dpToPx(-20) * fraction);
            this.smallTitle.setTranslationY((1.0f - fraction) * dpToPx(20));

            if (this.linkedSearchView != null) {
                float targetAlpha = scrollY > 0 ? Math.max(0.0f, 1.0f - (1.8f * fraction)) : 1.0f;
                float targetTransY = scrollY > 0 ? fraction * dpToPx(-15) : 0.0f;
                this.linkedSearchView.setAlpha(targetAlpha);
                this.linkedSearchView.setTranslationY(targetTransY);
            }
        }
    }

    private void localFractionAdjusted(float fraction) {
        this.smallTitle.setAlpha(1.0f);
        if (this.startIconsContainer != null) {
            this.startIconsContainer.setAlpha(1.0f);
            for (int i = 0; i < this.startIconsContainer.getChildCount(); i++) {
                View child = this.startIconsContainer.getChildAt(i);
                if (!this.isSearchExpanded) child.setAlpha(1.0f);
            }
        }
        if (this.endIconsContainer != null) {
            this.endIconsContainer.setAlpha(1.0f);
            for (int i = 0; i < this.endIconsContainer.getChildCount(); i++) {
                View child = this.endIconsContainer.getChildAt(i);
                if (!this.isSearchExpanded || child == this.searchIcon) {
                    child.setAlpha(1.0f);
                }
            }
        }
    }

    public void setTitle(CharSequence title) {
        String t = title != null ? title.toString() : "";
        if (this.bigTitle != null) this.bigTitle.setText(t);
        if (this.smallTitle != null) this.smallTitle.setText(t);
    }

    public void setBigTitle(CharSequence title) {
        if (this.bigTitle != null) this.bigTitle.setText(title);
    }

    public void setSmallTitle(CharSequence title) {
        if (this.smallTitle != null) this.smallTitle.setText(title);
    }

    public void setTitles(CharSequence bigTitleText, CharSequence smallTitleText) {
        setBigTitle(bigTitleText);
        setSmallTitle(smallTitleText);
    }

    public void setTitleColor(int color) {
        if (this.bigTitle != null) this.bigTitle.setTextColor(color);
        if (this.smallTitle != null) this.smallTitle.setTextColor(color);
    }

    public void setOnSearchListener(SearchBar.OnSearchListener listener) {
        this.globalSearchListener = listener;
        if (this.toolbarSearchControl != null) {
            this.toolbarSearchControl.setOnSearchListener(listener);
        }
    }

    public TextView getBigTitle() {
        return this.bigTitle;
    }

    public SearchBar getToolbarSearchControl() {
        ensureToolbarSearchControlExists();
        return this.toolbarSearchControl;
    }

    public IconButton getSearchIcon() {
        return this.searchIcon;
    }

    public TextView getSmallTitle() {
        return this.smallTitle;
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (mGlassWrapper != null && child != mGlassWrapper) {
            if (this.toolbarContainer != null && child != this.topLayout && child != this.bottomLayout) {
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

    @Override
    public void removeView(View view) {
        if (this.startIconsContainer != null && this.startIconsContainer.indexOfChild(view) != -1) {
            this.startIconsContainer.removeView(view);
            updateTitleConstraints();
            return;
        }
        if (this.endIconsContainer != null && this.endIconsContainer.indexOfChild(view) != -1) {
            this.endIconsContainer.removeView(view);
            updateTitleConstraints();
            return;
        }
        super.removeView(view);
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

        // Standardize IconTrigger size in HeaderMenu (e.g. for Filewave left side) to match Musify's 40dp
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
            // Add to end container. SearchIcon should always be far right (last index).
            if (view == this.searchIcon) {
                this.endIconsContainer.addView(view, lulp);
            } else {
                // Add others at the beginning or before search icon
                int index = 0;
                if (this.searchIcon != null && this.endIconsContainer.indexOfChild(this.searchIcon) != -1) {
                    index = Math.max(0, this.endIconsContainer.getChildCount() - 1);
                }
                this.endIconsContainer.addView(view, index, lulp);
            }
        } else {
            this.startIconsContainer.addView(view, lulp);
        }
        
        if (this.isCollapsed) {
            boolean shouldShow = true;
            if (view == this.searchIcon) shouldShow = (this.linkedSearchView != null || this.searchEnabledAttr);
            else if (view == this.floatingMenuTrigger) shouldShow = (this.linkedFloatingIcons != null);

            view.setVisibility(shouldShow ? VISIBLE : GONE);
            view.setAlpha(this.smallTitle != null ? this.smallTitle.getAlpha() : 1.0f);
        } else {
            view.setVisibility(GONE);
        }

        // Apply current theme color
        boolean isDark;
        try {
            isDark = ThemeManager.get().isDark();
        } catch (IllegalStateException e) {
            isDark = mGlassWrapper != null && mGlassWrapper.getActiveTheme() == LegacyBlur.ThemeMode.DARK;
        }
        int primaryColor = getContext().getColor(isDark ? R.color.xoeris_text_primary : android.R.color.black);
        if (view instanceof ImageView) {
            ((ImageView) view).setImageTintList(ColorStateList.valueOf(primaryColor));
        }

        updateTitleConstraints();
    }

    private void updateTitleConstraints() {
        if (this.smallTitle == null || this.toolbarContainer == null) return;
        
        // Always center in parent regardless of icons to prevent horizontal shifting
        RelativeLayout.LayoutParams titleLp = (RelativeLayout.LayoutParams) this.smallTitle.getLayoutParams();
        titleLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        
        // Remove rules that bind to side containers as they cause pushing when containers have different widths
        titleLp.removeRule(RelativeLayout.END_OF);
        titleLp.removeRule(RelativeLayout.START_OF);

        this.smallTitle.setLayoutParams(titleLp);
    }

    public void setSearchEnabled(boolean enabled) {
        this.searchEnabledAttr = enabled;
        if (this.searchIcon != null) {
            if (enabled && this.isCollapsed && (this.linkedSearchView != null || this.searchEnabledAttr)) {
                this.searchIcon.setVisibility(VISIBLE);
                this.searchIcon.setAlpha(1.0f);
            } else {
                this.searchIcon.setVisibility(GONE);
                if (this.isSearchExpanded) collapseSearch();
            }
        }
    }

    public boolean isSearchExpanded() {
        return this.isSearchExpanded;
    }

    public int getReservedHeight() {
        return getCollapsedHeight();
    }

    public int getCollapsedHeight() {
        return this.collapsedHeight;
    }

    private int getStatusBarHeight(Context context) {
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? context.getResources().getDimensionPixelSize(resourceId) : 0;
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

