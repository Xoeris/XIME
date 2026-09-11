package xime.ui.menu;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.OvershootInterpolator;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.List;

import xime.R;
import xime.haptic.HapticEngine;
import xime.graphics.shader.bloom.SwipeBloom;
import xime.graphics.shader.blur.LegacyBlur;

import xime.ui.layout.Layout;
import xime.ui.layout.BlurLayout;
import xime.ui.layout.LinearLayout;
import xime.ui.view.BlurView;
import xime.ui.view.ImageView;
import xime.ui.view.TextView;
import xime.ui.utils.Elevator;

public class FooterMenu extends Layout {
    private static final int ANIM_DURATION = 300;
    private BlurLayout mGlassWrapper;
    private int activeColor;
    private int currentLayer = 0;
    private View elevatorView;
    private HapticEngine HapticEngine;
    private boolean hasElevator = false;
    private int iconSize;
    private int inactiveColor;
    private boolean isAnimating = false;
    private boolean isCenteringExclusionEnabled = false;
    private OnItemSelectedListener listener;
    private final Menu menu;
    private Layout overlayContainer;
    private Elevator pickerOverlay;
    private int selectedIconColor;
    private int selectedItemId = -1;
    private SwipeBloom swipeBloom;
    private MenuType menuType = MenuType.FLOATING_DEFAULT;

    public enum MenuType {
        FLOATING_DEFAULT(0),
        FLOATING_MINI(1),
        STICKY_BOTTOM(2);
        
        final int value;
        MenuType(int v) { this.value = v; }
        static MenuType fromInt(int v) {
            for (MenuType t : values()) if (t.value == v) return t;
            return FLOATING_DEFAULT;
        }
    }

    public interface OnItemSelectedListener {
        boolean onNavigationItemSelected(MenuItem item);
        default void onItemLongPressed(MenuItem item) {}
    }

    public FooterMenu(@NonNull Context context) {
        this(context, null);
    }

    public FooterMenu(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FooterMenu(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        
        mGlassWrapper = new BlurLayout(context, attrs, defStyleAttr);
        super.addView(mGlassWrapper, new LayoutParams(-1, -1));

        this.menu = new PopupMenu(context, this).getMenu();
        this.HapticEngine = new HapticEngine(context);
        this.swipeBloom = new SwipeBloom(this);
        
        mGlassWrapper.setBlurType(BlurLayout.BlurType.GLASS);
        
        mGlassWrapper.crystal.set3DBevel(0.92f, 0.6f);
        mGlassWrapper.crystal.setDepthEffect(0.06f);
        mGlassWrapper.crystal.setDistortionAmount(0.12f);
        mGlassWrapper.crystal.setFluidWarp(0.015f, 1.2f);
        
        this.activeColor = context.getColor(R.color.xoeris_primary);
        
        boolean isDark = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (isDark) {
            this.inactiveColor = Color.parseColor("#A0FFFFFF");
            this.selectedIconColor = ViewCompat.MEASURED_STATE_MASK;
        } else {
            this.inactiveColor = ViewCompat.MEASURED_STATE_MASK;
            this.selectedIconColor = Color.WHITE;
        }
        
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FooterMenu, defStyleAttr, 0);
        int menuRes = a.getResourceId(R.styleable.FooterMenu_xoerisMenu, 0);
        this.iconSize = a.getDimensionPixelSize(R.styleable.FooterMenu_xoerisIconSize, dpToPx(24));
        this.isCenteringExclusionEnabled = a.getBoolean(R.styleable.FooterMenu_xoerisCenteringExclusion, false);
        this.menuType = MenuType.fromInt(a.getInt(R.styleable.FooterMenu_xoerisMenuType, 0));
        a.recycle();
        
        applyMenuTypeConfig();
        
        if (menuRes != 0) {
            inflateMenu(menuRes);
        }
    }

    private void applyMenuTypeConfig() {
        if (mGlassWrapper == null) return;
        
        switch (menuType) {
            case FLOATING_DEFAULT:
                mGlassWrapper.setCornerRadius(dpToPx(32));
                this.iconSize = dpToPx(24);
                break;
            case FLOATING_MINI:
                mGlassWrapper.setCornerRadius(dpToPx(500)); 
                this.iconSize = dpToPx(22);
                break;
            case STICKY_BOTTOM:
                mGlassWrapper.setCornerRadius(0);
                this.iconSize = dpToPx(24);
                break;
        }
        applyDefaultTacticalLayout();
    }

    private void applyDefaultTacticalLayout() {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params == null) return;

        if (menuType == MenuType.FLOATING_MINI) {
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            if (params instanceof android.widget.LinearLayout.LayoutParams) {
                ((android.widget.LinearLayout.LayoutParams) params).gravity = Gravity.CENTER_HORIZONTAL;
            } else if (params instanceof RelativeLayout.LayoutParams) {
                ((RelativeLayout.LayoutParams) params).addRule(RelativeLayout.CENTER_HORIZONTAL);
            }
        } else {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) params;
            int margin = (menuType == MenuType.STICKY_BOTTOM) ? 0 : dpToPx(16);
            
            // Only apply default margins if they are currently 0 to avoid overriding explicit layout XML
            if (mlp.leftMargin == 0 && mlp.rightMargin == 0 && mlp.bottomMargin == 0) {
                mlp.setMargins(margin, margin, margin, margin);
            }
        }

        setScaleX(1.0f);
        setScaleY(1.0f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.swipeBloom != null) {
            this.swipeBloom.draw(canvas);
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (mGlassWrapper != null && child != mGlassWrapper) {
            mGlassWrapper.addView(child, index, params);
        } else {
            super.addView(child, index, params);
        }
    }

    @Override
    public void removeAllViews() {
        if (mGlassWrapper != null) {
            mGlassWrapper.removeAllViews();
        } else {
            super.removeAllViews();
        }
    }

    @Override
    public int getChildCount() {
        return mGlassWrapper != null ? mGlassWrapper.getChildCount() : super.getChildCount();
    }

    @Override
    public View getChildAt(int index) {
        return mGlassWrapper != null ? mGlassWrapper.getChildAt(index) : super.getChildAt(index);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();
        int widthSize = View.MeasureSpec.getSize(widthMeasureSpec);
        
        int height = (menuType == MenuType.FLOATING_MINI) ? dpToPx(48) : dpToPx(64);
        int totalWidth = widthSize;

        if (menuType == MenuType.FLOATING_MINI && count > 0) {
            // Image 1 style: Compact centered pill
            int itemWidth = dpToPx(64);
            totalWidth = Math.min(widthSize, itemWidth * count + dpToPx(16));
        }

        if (mGlassWrapper != null) {
            mGlassWrapper.measure(View.MeasureSpec.makeMeasureSpec(totalWidth, MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        if (count > 0) {
            int childWidth = totalWidth / count;
            int childWidthMeasureSpec = View.MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
            int childHeightMeasureSpec = View.MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            for (int i = 0; i < count; i++) {
                getChildAt(i).measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }
        setMeasuredDimension(totalWidth, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mGlassWrapper != null) {
            mGlassWrapper.layout(0, 0, r - l, b - t);
        }
        int count = getChildCount();
        if (count == 0) return;
        int width = r - l;
        int height = b - t;
        int childWidth = width / count;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            int left = i * childWidth;
            child.layout(left, 0, left + childWidth, height);
        }
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
        applyDefaultTacticalLayout();
        refreshTheme();
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
        boolean isDark = false;
        if (mGlassWrapper == null) return;
        
        LegacyBlur.ThemeMode myTheme = mGlassWrapper.getActiveTheme();
        if (myTheme == LegacyBlur.ThemeMode.DARK) {
            isDark = true;
        }
        
        ViewParent parent = getParent();
        while (parent != null) {
            if (parent instanceof BlurLayout) {
                isDark = ((BlurLayout) parent).getActiveTheme() == LegacyBlur.ThemeMode.DARK;
                break;
            }
            parent = parent.getParent();
        }
        
        if (isDark) {
            this.inactiveColor = Color.parseColor("#A0FFFFFF");
            this.selectedIconColor = ViewCompat.MEASURED_STATE_MASK;
        } else {
            this.inactiveColor = ViewCompat.MEASURED_STATE_MASK;
            this.selectedIconColor = Color.WHITE;
        }
        
        if (this.elevatorView != null) {
            View iconTop = this.elevatorView.findViewWithTag("elevator_icon_top");
            if (iconTop instanceof ImageView) {
                ((ImageView) iconTop).setImageTintList(ColorStateList.valueOf(this.inactiveColor));
            }
            View iconBottom = this.elevatorView.findViewWithTag("elevator_icon_bottom");
            if (iconBottom instanceof ImageView) {
                ((ImageView) iconBottom).setImageTintList(ColorStateList.valueOf(this.inactiveColor));
            }
        }
        updateSelectionState(false);
    }

    public void inflateMenu(int menuResId) {
        this.menu.clear();
        new PopupMenu(getContext(), this).getMenuInflater().inflate(menuResId, this.menu);
        refresh();
    }

    public Menu getMenu() {
        return this.menu;
    }

    public void setItemActiveIndicatorColor(ColorStateList color) {
        if (color != null) {
            this.activeColor = color.getDefaultColor();
            for (int i = 0; i < getChildCount(); i++) {
                View itemView = getChildAt(i);
                View indicator = itemView.findViewById(R.id.xoeris_indicator);
                if (indicator != null && (indicator.getBackground() instanceof GradientDrawable)) {
                    ((GradientDrawable) indicator.getBackground()).setColor(this.activeColor);
                }
            }
        }
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.listener = listener;
    }

    public void setCenteringExclusionEnabled(boolean enabled) {
        this.isCenteringExclusionEnabled = enabled;
        refresh();
    }

    public void refresh() {
        if (this.menu != null && this.menu.size() > 0) {
            removeAllViews();
            List<MenuItem> visibleItems = getVisibleMenuItems();

            // Standard: Use elevator if we have more than 4 items
            this.hasElevator = visibleItems.size() > 4;

            if (!this.hasElevator) {
                for (MenuItem item : visibleItems) {
                    addView(createItemView(item));
                }
            } else {
                renderElevatorLayer(visibleItems);
            }

            if (!visibleItems.isEmpty() && this.selectedItemId == -1) {
                setSelectedItemId(visibleItems.get(0).getItemId());
            } else {
                updateSelectionState(false);
            }
        }
    }

    private void renderElevatorLayer(List<MenuItem> items) {
        removeAllViews();
        int startIdx = this.currentLayer * 4;
        int endIdx = Math.min(startIdx + 4, items.size());
        
        View[] slots = new View[5];
        this.elevatorView = createElevatorView();
        
        // [MOD] Protocol: Centered Add button for 3-item menus (Shuffle, Add, Play)
        // Exclusion for Musify Playlist-style centering: [Item, Spacer, Item(Center), Item, Elevator]
        if (items.size() == 3 && (this.isCenteringExclusionEnabled || getContext().getPackageName().contains("musify"))) {
            slots[0] = createItemView(items.get(0));
            slots[1] = null; 
            slots[2] = createItemView(items.get(1)); // Add (Center)
            slots[3] = createItemView(items.get(2));
            slots[4] = this.elevatorView; // More (Far Right)
        } else {
            slots[2] = this.elevatorView;
            int layerItemCount = endIdx - startIdx;
            
            if (layerItemCount == 1) {
                slots[1] = createItemView(items.get(startIdx));
            } else if (layerItemCount == 2) {
                slots[1] = createItemView(items.get(startIdx));
                slots[3] = createItemView(items.get(startIdx + 1));
            } else if (layerItemCount == 3) {
                slots[0] = createItemView(items.get(startIdx));
                slots[1] = createItemView(items.get(startIdx + 1));
                slots[3] = createItemView(items.get(startIdx + 2));
            } else if (layerItemCount == 4) {
                slots[0] = createItemView(items.get(startIdx));
                slots[1] = createItemView(items.get(startIdx + 1));
                slots[3] = createItemView(items.get(startIdx + 2));
                slots[4] = createItemView(items.get(startIdx + 3));
            }
        }
        
        for (View slot : slots) {
            if (slot != null) {
                addView(slot);
            } else {
                View spacer = new View(getContext());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1.0f));
                addView(spacer);
            }
        }
    }

    private View createElevatorView() {
        Layout itemView = new Layout(getContext());
        itemView.setClickable(true);
        itemView.setFocusable(true);
        itemView.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1.0f));
        
        List<MenuItem> visibleItems = getVisibleMenuItems();
        int totalLayers = (int) Math.ceil(visibleItems.size() / 4.0);
        
        ImageView iconTop = new ImageView(getContext());
        iconTop.setTag("elevator_icon_top");
        iconTop.setImageResource(R.drawable.xoeris_keyboard_arrow_up);
        iconTop.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconTop.setImageTintList(ColorStateList.valueOf(this.inactiveColor));
        
        ImageView iconBottom = new ImageView(getContext());
        iconBottom.setTag("elevator_icon_bottom");
        iconBottom.setImageResource(R.drawable.xoeris_keyboard_arrow_down);
        iconBottom.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconBottom.setImageTintList(ColorStateList.valueOf(this.inactiveColor));
        
        LayoutParams iconLp = new LayoutParams(this.iconSize, this.iconSize, 17);
        itemView.addView(iconTop, iconLp);
        itemView.addView(iconBottom, iconLp);
        
        if (totalLayers <= 1) {
            itemView.setVisibility(GONE);
        } else if (this.currentLayer == 0) {
            iconTop.setAlpha(0.0f);
            iconBottom.setAlpha(1.0f);
        } else if (this.currentLayer == totalLayers - 1) {
            iconTop.setAlpha(1.0f);
            iconBottom.setAlpha(0.0f);
        } else {
            iconTop.setAlpha(1.0f);
            iconBottom.setAlpha(1.0f);
            iconTop.setTranslationY(-dpToPx(6));
            iconBottom.setTranslationY(dpToPx(6));
        }
        
        itemView.setOnTouchListener(new ElevatorTouchListener(totalLayers, visibleItems));
        return itemView;
    }

    private class ElevatorTouchListener implements OnTouchListener {
        private final int totalLayers;
        private final List<MenuItem> visibleItems;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private boolean isLongPressTriggered = false;
        private float initialX, initialY;
        
        private final Runnable longPressRunnable;

        ElevatorTouchListener(int totalLayers, List<MenuItem> visibleItems) {
            this.totalLayers = totalLayers;
            this.visibleItems = visibleItems;
            this.longPressRunnable = () -> {
            if (this.totalLayers >= 1) {
                isLongPressTriggered = true;
                if (HapticEngine != null) HapticEngine.onLongPress();
                showLayerPicker(this.totalLayers);
            }
        };
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isLongPressTriggered = false;
                    initialX = event.getRawX();
                    initialY = event.getRawY();
                    handler.postDelayed(longPressRunnable, 500);
                    v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(150).start();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPressRunnable);
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                    if (isLongPressTriggered && pickerOverlay != null) {
                        int selectedLayer = pickerOverlay.isAnchorSelected() ? currentLayer : pickerOverlay.getSelectedLayer();
                        hideLayerPicker(selectedLayer);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float currentX = event.getRawX();
                    float currentY = event.getRawY();
                    float diffY = currentY - initialY;
                    
                    if (isLongPressTriggered && pickerOverlay != null) {
                        int[] loc = new int[2];
                        v.getLocationOnScreen(loc);
                        float centerX = loc[0] + (v.getWidth() / 2.0f);
                        float centerY = loc[1] - dpToPx(142);
                        pickerOverlay.updateSelectionByTouch(currentX - centerX, currentY - centerY);
                    } else if (!isLongPressTriggered && Math.abs(diffY) > dpToPx(20)) {
                        handler.removeCallbacks(longPressRunnable);
                        if (!isAnimating && totalLayers > 1) {
                            int targetLayer;
                            if (diffY > 0) targetLayer = (currentLayer + 1) % totalLayers;
                            else targetLayer = (currentLayer - 1 + totalLayers) % totalLayers;
                            
                            if (HapticEngine != null) HapticEngine.onTap();
                            if (swipeBloom != null) swipeBloom.start(diffY > 0, activeColor, false, 1.0f);
                            animateLayerTransition(visibleItems, targetLayer);
                            initialY = currentY;
                        }
                    }
                    return true;
            }
            return false;
        }
    }

    private List<MenuItem> getVisibleMenuItems() {
        List<MenuItem> visibleItems = new ArrayList<>();
        for (int i = 0; i < this.menu.size(); i++) {
            if (this.menu.getItem(i).isVisible()) visibleItems.add(this.menu.getItem(i));
        }
        return visibleItems;
    }

    private void showLayerPicker(int totalLayers) {
        Activity activity = getActivity();
        if (activity == null) return;
        
        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
        this.overlayContainer = new Layout(getContext());
        this.overlayContainer.setBackgroundColor(Color.parseColor("#80000000"));
        this.overlayContainer.setAlpha(0.0f);
        
        View blurRoot = activity.findViewById(android.R.id.content);
        this.pickerOverlay = new Elevator(getContext(), totalLayers, this.currentLayer, this.activeColor, layer -> {
            if (HapticEngine != null) HapticEngine.onTap();
        });
        
        if (blurRoot != null) this.pickerOverlay.setBlurRootView(blurRoot);
        
        int[] loc = new int[2];
        this.elevatorView.getLocationOnScreen(loc);
        LayoutParams lp = new LayoutParams(dpToPx(280), dpToPx(280), 81);
        lp.bottomMargin = (root.getHeight() - loc[1]) + dpToPx(2);
        
        this.overlayContainer.addView(this.pickerOverlay, lp);
        root.addView(this.overlayContainer, new ViewGroup.LayoutParams(-1, -1));
        
        this.overlayContainer.animate().alpha(1.0f).setDuration(200).start();
        this.pickerOverlay.setScaleX(0.5f);
        this.pickerOverlay.setScaleY(0.5f);
        this.pickerOverlay.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(300)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();
    }

    private void hideLayerPicker(int finalLayer) {
        if (this.overlayContainer == null) return;
        if (finalLayer != this.currentLayer) {
            this.currentLayer = finalLayer;
            renderElevatorLayer(getVisibleMenuItems());
            updateSelectionState(false);
        }
        this.overlayContainer.animate().alpha(0.0f).setDuration(200).withEndAction(() -> {
            ViewParent parent = this.overlayContainer.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(this.overlayContainer);
            this.overlayContainer = null;
            this.pickerOverlay = null;
        }).start();
    }

    private void animateLayerTransition(List<MenuItem> items, int targetLayer) {
        if (this.isAnimating) return;
        this.isAnimating = true;
        
        int oldLayer = this.currentLayer;
        float direction = targetLayer > oldLayer ? 1.0f : -1.0f;
        float exitY = (-direction) * dpToPx(20);
        final float enterY = dpToPx(20) * direction;
        
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v == this.elevatorView) {
                View iconTop = v.findViewWithTag("elevator_icon_top");
                View iconBottom = v.findViewWithTag("elevator_icon_bottom");
                int totalLayers = (int) Math.ceil(items.size() / 4.0);
                if (iconTop != null && iconBottom != null) {
                    if (targetLayer == 0) {
                        iconTop.animate().alpha(0.0f).translationY(-dpToPx(20)).setDuration(300).start();
                        iconBottom.animate().alpha(1.0f).translationY(0.0f).setDuration(300).start();
                    } else if (targetLayer == totalLayers - 1) {
                        iconTop.animate().alpha(1.0f).translationY(0.0f).setDuration(300).start();
                        iconBottom.animate().alpha(0.0f).translationY(dpToPx(20)).setDuration(300).start();
                    } else {
                        if (oldLayer == 0) iconTop.setTranslationY(-dpToPx(20));
                        else if (oldLayer == totalLayers - 1) iconBottom.setTranslationY(dpToPx(20));
                        iconTop.animate().alpha(1.0f).translationY(-dpToPx(6)).setDuration(300).start();
                        iconBottom.animate().alpha(1.0f).translationY(dpToPx(6)).setDuration(300).start();
                    }
                }
            } else {
                v.animate().alpha(0.0f).translationY(exitY).setDuration(250).start();
            }
        }
        
        postDelayed(() -> {
            this.currentLayer = targetLayer;
            renderElevatorLayer(items);
            updateSelectionState(false);
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v != this.elevatorView) {
                    v.setAlpha(0.0f);
                    v.setTranslationY(enterY);
                    v.animate().alpha(1.0f).translationY(0.0f).setDuration(400)
                            .setInterpolator(new OvershootInterpolator(1.2f)).start();
                }
            }
            postDelayed(() -> this.isAnimating = false, 400);
        }, 250);
    }

    private View createItemView(MenuItem item) {
        Layout itemView = new Layout(getContext());
        itemView.setClickable(true);
        itemView.setFocusable(true);
        itemView.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1.0f));
        
        if (menuType != MenuType.STICKY_BOTTOM) {
            View indicator = new View(getContext());
            int indicatorSize = (int) (this.iconSize * (menuType == MenuType.FLOATING_MINI ? 1.5f : 1.8f));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(this.activeColor);
            indicator.setBackground(circle);
            indicator.setAlpha(0.0f);
            indicator.setScaleX(0.5f);
            indicator.setScaleY(0.5f);
            indicator.setId(R.id.xoeris_indicator);
            itemView.addView(indicator, new LayoutParams(indicatorSize, indicatorSize, 17));
        }
        
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        
        if (item.getIcon() != null) {
            ImageView icon = new ImageView(getContext());
            icon.setId(android.R.id.icon);
            icon.setImageDrawable(item.getIcon());
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageTintList(ColorStateList.valueOf(this.inactiveColor));
            
            int isize = (menuType == MenuType.STICKY_BOTTOM) ? dpToPx(24) : this.iconSize;
            content.addView(icon, new LinearLayout.LayoutParams(isize, isize));
        } else {
            TextView textIcon = new TextView(getContext());
            textIcon.setId(android.R.id.icon);
            textIcon.setText(item.getTitle());
            textIcon.setGravity(android.view.Gravity.CENTER);
            textIcon.setTextColor(this.inactiveColor);
            textIcon.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, (menuType == MenuType.FLOATING_MINI) ? 14 : 18);
            textIcon.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            content.addView(textIcon, new LinearLayout.LayoutParams(-2, -2));
        }

        if (menuType == MenuType.STICKY_BOTTOM) {
            TextView label = new TextView(getContext());
            label.setText(item.getTitle());
            label.setTextSize(10);
            label.setTextColor(this.inactiveColor);
            label.setGravity(Gravity.CENTER);
            label.setTag("item_label");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = dpToPx(2);
            content.addView(label, lp);
        }
        
        itemView.addView(content, new LayoutParams(-1, -1, 17));
        
        itemView.setOnTouchListener(new ItemTouchListener(item));
        itemView.setOnClickListener(v -> {
            if (HapticEngine != null) HapticEngine.onTap();
            if (listener != null) {
                if (listener.onNavigationItemSelected(item)) setSelectedItemId(item.getItemId());
            } else {
                setSelectedItemId(item.getItemId());
            }
        });
        itemView.setTag(item.getItemId());
        return itemView;
    }

    private class ItemTouchListener implements OnTouchListener {
        private final MenuItem item;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private boolean isHoldTriggered = false;
        private final Runnable holdRunnable;

        ItemTouchListener(MenuItem item) { 
            this.item = item;
            this.holdRunnable = () -> {
                isHoldTriggered = true;
                if (HapticEngine != null) HapticEngine.onLongPress();

                if (listener != null) listener.onItemLongPressed(this.item);
            };
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isHoldTriggered = false;
                    v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(150).setInterpolator(new OvershootInterpolator()).start();
                    handler.postDelayed(holdRunnable, 1000);
                    return true;
                case MotionEvent.ACTION_UP:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new OvershootInterpolator(2.0f)).start();
                    handler.removeCallbacks(holdRunnable);
                    if (!isHoldTriggered) v.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new OvershootInterpolator(2.0f)).start();
                    handler.removeCallbacks(holdRunnable);
                    return true;
            }
            return false;
        }
    }

    public void setSelectedItemId(int id) {
        if (this.selectedItemId == id) return;
        int oldId = this.selectedItemId;
        this.selectedItemId = id;
        
        List<MenuItem> visibleItems = getVisibleMenuItems();
        int oldIndex = -1, newIndex = -1;
        for (int i = 0; i < visibleItems.size(); i++) {
            if (visibleItems.get(i).getItemId() == oldId) oldIndex = i;
            if (visibleItems.get(i).getItemId() == id) newIndex = i;
        }
        
        if (this.hasElevator && newIndex != -1) {
            int targetLayer = newIndex / 4;
            if (targetLayer != this.currentLayer) {
                this.currentLayer = targetLayer;
                renderElevatorLayer(visibleItems);
            }
        }
        
        if (oldIndex != -1 && newIndex != -1 && this.swipeBloom != null) {
            int count = getChildCount();
            if (count > 0) {
                float childWidth = (float) getWidth() / count;
                int vOld = oldIndex;
                int vNew = newIndex;
                if (this.hasElevator) {
                    int offsetOld = oldIndex % 4;
                    vOld = offsetOld < 2 ? offsetOld : offsetOld + 1;
                    int offsetNew = newIndex % 4;
                    vNew = offsetNew < 2 ? offsetNew : offsetNew + 1;
                }
                this.swipeBloom.start(newIndex > oldIndex, this.activeColor, true, 1.1f, (vOld + 0.5f) * childWidth, (vNew + 0.5f) * childWidth);
            }
        }
        updateSelectionState(true);
    }

    public void performItemSelection(int id) {
        setSelectedItemId(id);
        if (this.listener != null) {
            MenuItem item = this.menu.findItem(id);
            if (item != null) this.listener.onNavigationItemSelected(item);
        }
    }

    public int getSelectedItemId() {
        return this.selectedItemId;
    }

    public int getReservedHeight() {
        if (getVisibility() != VISIBLE) return 0;
        int h = getHeight() > 0 ? getHeight() : getMeasuredHeight();
        if (h <= 0) h = dpToPx(64);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) getLayoutParams();
        int bottomMargin = lp != null ? lp.bottomMargin : 0;
        return h + bottomMargin;
    }

    private void updateSelectionState(boolean animate) {
        for (int i = 0; i < getChildCount(); i++) {
            View itemView = getChildAt(i);
            Object tag = itemView.getTag();
            if (tag instanceof Integer) {
                boolean isSelected = (Integer) tag == this.selectedItemId;
                View indicator = itemView.findViewById(R.id.xoeris_indicator);
                final View icon = itemView.findViewById(android.R.id.icon);
                View label = itemView.findViewWithTag("item_label");
                int targetIconColor = isSelected ? this.selectedIconColor : this.inactiveColor;
                int targetActiveColor = isSelected ? this.activeColor : this.inactiveColor;
                
                if (animate) {
                    if (indicator != null) {
                        indicator.animate().alpha(isSelected ? 1.0f : 0.0f)
                                .scaleX(isSelected ? 1.0f : 0.5f).scaleY(isSelected ? 1.0f : 0.5f)
                                .setDuration(isSelected ? 400 : 200)
                                .setInterpolator(new OvershootInterpolator(isSelected ? 1.8f : 0.0f)).start();
                    }
                    if (isSelected && icon != null) {
                        icon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).withEndAction(() -> 
                            icon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).setInterpolator(new OvershootInterpolator()).start()
                        ).start();
                    }
                    if (icon instanceof ImageView) {
                        int tint = (menuType == MenuType.STICKY_BOTTOM) ? targetActiveColor : targetIconColor;
                        ((ImageView) icon).setImageTintList(ColorStateList.valueOf(tint));
                    } else if (icon instanceof TextView) {
                        ((TextView) icon).setTextColor(targetIconColor);
                    }
                    
                    if (label instanceof TextView) {
                        ((TextView) label).setTextColor(targetActiveColor);
                    }
                } else {
                    if (indicator != null) {
                        indicator.setAlpha(isSelected ? 1.0f : 0.0f);
                        indicator.setScaleX(isSelected ? 1.0f : 0.5f);
                        indicator.setScaleY(isSelected ? 1.0f : 0.5f);
                    }
                    if (icon instanceof ImageView) {
                        int tint = (menuType == MenuType.STICKY_BOTTOM) ? targetActiveColor : targetIconColor;
                        ((ImageView) icon).setImageTintList(ColorStateList.valueOf(tint));
                    } else if (icon instanceof TextView) {
                        ((TextView) icon).setTextColor(targetIconColor);
                    }
                    
                    if (label instanceof TextView) {
                        ((TextView) label).setTextColor(targetActiveColor);
                    }
                }
            }
        }
    }

    private Activity getActivity() {
        Context ctx = getContext();
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) return (Activity) ctx;
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}


