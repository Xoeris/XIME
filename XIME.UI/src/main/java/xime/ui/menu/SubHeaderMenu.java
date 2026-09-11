package xime.ui.menu;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.ChipGroup;

import xime.R;
import xime.ui.layout.Layout;
import xime.ui.layout.LinearLayout;
import xime.ui.view.TextView;
import xime.ui.bar.SearchBar;
import xime.ui.common.IconButton;
import xime.ui.common.RadioButton;
import xime.ui.common.Dropdown;

public class SubHeaderMenu extends LinearLayout {
    private SearchBar searchControl;
    private IconButton actionButton;
    private IconButton floatingTrigger;
    private LinearLayout searchLayer;
    private LinearLayout chipLayer;
    private LinearLayout selectionLayer;
    private View topSpacer;
    private FloatingMenu floatingOverlay;
    private Layout floatingContainer;
    private int[] floatingIcons;
    private FloatingMenu.OnLayerSelectedListener floatingListener;

    private int toolbarHeight = 0;

    public SubHeaderMenu(@NonNull Context context) {
        this(context, null);
    }

    public SubHeaderMenu(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SubHeaderMenu(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void setToolbarHeight(int height) {
        this.toolbarHeight = height;
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        int padding = (int) (context.getResources().getDisplayMetrics().density * 16.0f);
        setPadding(padding, padding, padding, padding);

        // Layer 0: Top Spacer
        topSpacer = new View(context);
        topSpacer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0));
        addView(topSpacer);

        // Layer 1: Search Layer
        searchLayer = new LinearLayout(context);
        searchLayer.setOrientation(HORIZONTAL);
        searchLayer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        searchLayer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(searchLayer);

        searchControl = new SearchBar(context);
        searchControl.setId(R.id.searchControl);
        searchControl.setVisibility(GONE);
        LayoutParams searchLp = new LayoutParams(0, dpToPx(52));
        searchLp.weight = 1;
        searchControl.setLayoutParams(searchLp);
        searchLayer.addView(searchControl);

        floatingTrigger = new IconButton(context);
        floatingTrigger.setId(View.generateViewId());
        floatingTrigger.setVisibility(GONE);
        floatingTrigger.setBackgroundResource(R.drawable.xoeris_bg_control_code);
        int fp = (int) (context.getResources().getDisplayMetrics().density * 14.0f);
        floatingTrigger.setPadding(fp, fp, fp, fp);
        floatingTrigger.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        LayoutParams floatingLp = new LayoutParams(dpToPx(52), dpToPx(52));
        floatingLp.leftMargin = (int) (context.getResources().getDisplayMetrics().density * 8.0f);
        floatingTrigger.setLayoutParams(floatingLp);
        searchLayer.addView(floatingTrigger);
        
        refreshTheme();

        actionButton = new IconButton(context);
        actionButton.setId(R.id.actionButton);
        actionButton.setVisibility(GONE);
        actionButton.setBackgroundResource(R.drawable.xoeris_bg_control_code);
        int ap = (int) (context.getResources().getDisplayMetrics().density * 4.0f);
        actionButton.setPadding(ap, ap, ap, ap);
        actionButton.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        LayoutParams actionLp = new LayoutParams(dpToPx(52), dpToPx(52));
        actionLp.leftMargin = (int) (context.getResources().getDisplayMetrics().density * 8.0f);
        actionButton.setLayoutParams(actionLp);
        searchLayer.addView(actionButton);

        // Layer 2: ChipGroup container
        chipLayer = new LinearLayout(context);
        chipLayer.setOrientation(VERTICAL);
        LayoutParams chipLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        chipLp.topMargin = (int) (context.getResources().getDisplayMetrics().density * 8.0f);
        chipLayer.setLayoutParams(chipLp);
        addView(chipLayer);

        // Layer 3: Dropdown or Radio container / Action Layer
        selectionLayer = new LinearLayout(context);
        selectionLayer.setOrientation(HORIZONTAL);
        selectionLayer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LayoutParams selectionLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        selectionLp.topMargin = (int) (context.getResources().getDisplayMetrics().density * 8.0f);
        selectionLayer.setLayoutParams(selectionLp);
        addView(selectionLayer);
    }

    public SearchBar getSearchControl() {
        if (searchControl != null) {
            searchControl.setVisibility(VISIBLE);
            searchControl.setAlpha(1.0f);
            searchControl.setTranslationY(0.0f);
        }
        return searchControl;
    }

    public IconButton getActionButton() {
        return actionButton;
    }

    public void setActionButtonIcon(int resId) {
        if (actionButton != null) {
            actionButton.setImageResource(resId);
            actionButton.setVisibility(resId != 0 ? VISIBLE : GONE);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshTheme();
        if (searchLayer != null) {
            searchLayer.setAlpha(1.0f);
        }
        if (searchControl != null && searchControl.getVisibility() == VISIBLE) {
            searchControl.setAlpha(1.0f);
            searchControl.setTranslationY(0.0f);
        }
    }

    public void refreshTheme() {
        boolean isDark = getActiveTheme() == ThemeMode.DARK;
        int primaryColor = getContext().getColor(isDark ? R.color.xoeris_text_primary : android.R.color.black);
        android.content.res.ColorStateList csl = android.content.res.ColorStateList.valueOf(primaryColor);
        
        if (floatingTrigger != null) floatingTrigger.setImageTintList(csl);
        if (actionButton != null) actionButton.setImageTintList(csl);
        
        // Theme other action views if they are ImageViews or CodeBase
        if (selectionLayer != null) {
            for (int i = 0; i < selectionLayer.getChildCount(); i++) {
                View v = selectionLayer.getChildAt(i);
                if (v instanceof android.widget.ImageView) {
                    ((android.widget.ImageView) v).setImageTintList(csl);
                } else if (v instanceof TextView) {
                    ((TextView) v).setTextColor(primaryColor);
                }
            }
        }
    }

    public IconButton getFloatingTrigger() {
        return floatingTrigger;
    }

    public void setupFloatingLogic(int iconRes, int[] icons, int currentLayer, FloatingMenu.OnLayerSelectedListener listener) {
        if (floatingTrigger == null) return;
        this.floatingIcons = icons;
        this.floatingListener = listener;
        floatingTrigger.setImageResource(iconRes);
        floatingTrigger.setVisibility(VISIBLE);
        
        floatingTrigger.setOnTouchListener(new android.view.View.OnTouchListener() {
            private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            private boolean isLongPressTriggered = false;
            private final Runnable longPressRunnable = () -> {
                isLongPressTriggered = true;
                showFloating(icons.length, currentLayer, listener);
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
                            int selected = floatingOverlay != null && floatingOverlay.isAnchorSelected() ? currentLayer : (floatingOverlay != null ? floatingOverlay.getSelectedLayer() : currentLayer);
                            if (floatingOverlay != null && floatingListener != null && !floatingOverlay.isAnchorSelected()) {
                                floatingListener.onLayerSelected(selected);
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
    }

    private android.app.Activity getActivity() {
        android.content.Context ctx = getContext();
        while (ctx instanceof android.content.ContextWrapper) {
            if (ctx instanceof android.app.Activity) return (android.app.Activity) ctx;
            ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }

    private void showFloating(int totalLayers, int currentLayer, FloatingMenu.OnLayerSelectedListener listener) {
        android.app.Activity activity = getActivity();
        if (activity == null) return;

        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
        floatingContainer = new Layout(getContext());
        floatingContainer.setBackgroundColor(android.graphics.Color.parseColor("#80000000"));
        floatingContainer.setAlpha(0.0f);

        float density = getResources().getDisplayMetrics().density;
        int menuSize = (int) (280 * density);

        floatingOverlay = new FloatingMenu(getContext(), totalLayers, currentLayer, android.graphics.Color.BLUE, floatingIcons, layer -> {});

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

    public void addSearchActionView(View view) {
        if (searchLayer != null) {
            if (view.getParent() != null) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.leftMargin = (int) (getContext().getResources().getDisplayMetrics().density * 8.0f);
            view.setLayoutParams(lp);
            searchLayer.addView(view);
        }
    }

    public void setTopSpacer(int height) {
        if (topSpacer != null) {
            topSpacer.getLayoutParams().height = height;
            topSpacer.requestLayout();
        }
    }

    public void addChipGroup(ChipGroup group) {
        if (chipLayer != null) {
            if (group.getParent() != null) {
                ((ViewGroup) group.getParent()).removeView(group);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            if (chipLayer.getChildCount() > 0) {
                lp.topMargin = (int) (getContext().getResources().getDisplayMetrics().density * 8.0f);
            }
            group.setLayoutParams(lp);
            chipLayer.addView(group);
        }
    }

    public void setDropdown(Dropdown dropdown) {
        if (selectionLayer != null) {
            selectionLayer.removeAllViews();
            selectionLayer.addView(dropdown);
        }
    }

    public void setRadioButton(RadioButton radio) {
        if (selectionLayer != null) {
            selectionLayer.removeAllViews();
            selectionLayer.addView(radio);
        }
    }

    public void addActionView(View view) {
        if (selectionLayer != null) {
            if (view.getParent() != null) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            if (selectionLayer.getChildCount() > 0) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                lp.leftMargin = (int) (getContext().getResources().getDisplayMetrics().density * 8.0f);
                view.setLayoutParams(lp);
            }
            selectionLayer.addView(view);
        }
    }

    public void addFlexibleSpacer() {
        if (selectionLayer != null) {
            View spacer = new View(getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1);
            lp.weight = 1;
            selectionLayer.addView(spacer, lp);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    public LinearLayout getChipLayer() {
        return chipLayer;
    }

    public void updateScroll(int scrollY) {
        float density = getResources().getDisplayMetrics().density;
        // Enhanced calculation: Elements fade exactly as they "touch" the floating toolbar area.
        int threshold = (toolbarHeight > 0) ? toolbarHeight : (int)(80 * density);
        float fadeRange = 25.0f * density;

        // Layer 1: Search Layer
        updateViewAlpha(searchLayer, 0, scrollY, threshold, fadeRange);
        
        // Ensure floating trigger visibility matches initialization state
        if (floatingTrigger != null) {
            if (floatingIcons == null) {
                floatingTrigger.setVisibility(GONE);
            } else {
                // If it exists, it follows the container alpha
                float sa = searchLayer != null ? searchLayer.getAlpha() : 1.0f;
                floatingTrigger.setVisibility(sa > 0 ? VISIBLE : INVISIBLE);
            }
        }

        // Layer 2: Chip Layer (Staggered by row)
        if (chipLayer != null) {
            int layerOffset = chipLayer.getTop();
            for (int i = 0; i < chipLayer.getChildCount(); i++) {
                View child = chipLayer.getChildAt(i);
                updateViewAlpha(child, layerOffset, scrollY, threshold, fadeRange);
            }
        }

        // Layer 3: Selection Layer
        updateViewAlpha(selectionLayer, 0, scrollY, threshold, fadeRange);
        
        if (actionButton != null) {
            actionButton.setVisibility(GONE);
        }
    }

    private void updateViewAlpha(View v, int parentOffset, int scrollY, int threshold, float fadeRange) {
        if (v == null || v.getVisibility() == GONE) return;
        
        // Revised robust launch fix:
        // If scroll is at 0, everything should be visible. We always use top spacers
        // to keep elements below the toolbar initially. This also bypasses issues
        // where getTop() returns 0 before the first layout pass is completed.
        if (scrollY <= 0) {
            v.setAlpha(1.0f);
            return;
        }

        // Relative top within SubHeaderMenu
        int relativeTop = v.getTop() + parentOffset;
        // Current absolute position relative to top of scrolling container
        float currentY = (float) relativeTop - scrollY;
        
        // Elements remain 100% opaque until they cross the threshold (toolbar bottom).
        // They then fade out over the fadeRange as they move further up behind the toolbar.
        float diff = (float) threshold - currentY;
        float alpha = 1.0f - Math.max(0.0f, Math.min(1.0f, diff / fadeRange));
        
        v.setAlpha(alpha);
    }
}
