package xime.ui.common;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import xime.haptic.HapticEngine;
import xime.ui.layout.BlurLayout;

/**
 * Premium Dark Glassmorphic Dropdown component for XIME.UI framework.
 */
public class Dropdown extends xime.ui.layout.LinearLayout {

    public interface OnItemSelectedListener {
        void onItemSelected(int position, String item);
    }

    private TextView mSelectedTextView;
    private TextView mArrowIcon;
    private List<String> mItems = new ArrayList<>();
    private int mSelectedIndex = 0;
    private OnItemSelectedListener mListener;
    private PopupWindow mPopupWindow;
    private HapticEngine mHapticEngine;

    public Dropdown(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public Dropdown(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public Dropdown(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClickable(true);
        setFocusable(true);
        mHapticEngine = new HapticEngine(context);

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float density = dm.density;

        int paddingH = (int) (16 * density);
        int paddingV = (int) (12 * density);
        setPadding(paddingH, paddingV, paddingH, paddingV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1C1C1E"));
        bg.setCornerRadius(16 * density);
        bg.setStroke((int) (1 * density), Color.parseColor("#2C2C2E"));
        setBackground(bg);

        mSelectedTextView = new TextView(context);
        mSelectedTextView.setTextSize(14f);
        mSelectedTextView.setTextColor(Color.WHITE);
        mSelectedTextView.setTypeface(null, Typeface.BOLD);
        LayoutParams textLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        addView(mSelectedTextView, textLp);

        mArrowIcon = new TextView(context);
        mArrowIcon.setText("▼");
        mArrowIcon.setTextSize(12f);
        mArrowIcon.setTextColor(Color.parseColor("#8E8E93"));
        mArrowIcon.setPadding((int) (8 * density), 0, 0, 0);
        addView(mArrowIcon);

        setOnClickListener(v -> showGlassPopupWindow(context));
    }

    public void setItems(String[] items) {
        if (items != null) {
            setItems(Arrays.asList(items));
        }
    }

    public void setItems(List<String> items) {
        if (items != null && !items.isEmpty()) {
            this.mItems = new ArrayList<>(items);
            if (mSelectedIndex < 0 || mSelectedIndex >= mItems.size()) {
                mSelectedIndex = 0;
            }
            mSelectedTextView.setText(mItems.get(mSelectedIndex));
        }
    }

    public void setSelection(int index) {
        if (index >= 0 && index < mItems.size()) {
            this.mSelectedIndex = index;
            mSelectedTextView.setText(mItems.get(index));
        }
    }

    public int getSelectedIndex() {
        return mSelectedIndex;
    }

    public String getSelectedItem() {
        if (mSelectedIndex >= 0 && mSelectedIndex < mItems.size()) {
            return mItems.get(mSelectedIndex);
        }
        return null;
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.mListener = listener;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setClickable(enabled);
        setFocusable(enabled);
        setAlpha(enabled ? 1.0f : 0.4f);
    }

    private void showGlassPopupWindow(Context context) {
        if (mItems == null || mItems.isEmpty()) return;
        if (mHapticEngine != null) mHapticEngine.triggerClick();

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float density = dm.density;

        BlurLayout blurMenu = new BlurLayout(context);
        blurMenu.setCornerRadius(20f);
        blurMenu.setBlurRadius(20f);
        blurMenu.setGlassTint(Color.parseColor("#F018181F"));
        blurMenu.setShowBorder(true);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        android.widget.LinearLayout optionsLayout = new android.widget.LinearLayout(context);
        optionsLayout.setOrientation(VERTICAL);
        optionsLayout.setPadding((int) (8 * density), (int) (8 * density), (int) (8 * density), (int) (8 * density));

        for (int i = 0; i < mItems.size(); i++) {
            final int index = i;
            final String item = mItems.get(i);
            boolean isSelected = (index == mSelectedIndex);

            TextView optionView = new TextView(context);
            optionView.setText(item);
            optionView.setTextSize(14f);
            optionView.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            optionView.setLayoutParams(lp);
            optionView.setGravity(Gravity.CENTER);

            GradientDrawable itemBg = new GradientDrawable();
            itemBg.setCornerRadius(12 * density);
            if (isSelected) {
                itemBg.setColor(Color.parseColor("#FFD600"));
                optionView.setTextColor(Color.parseColor("#1C1C1E"));
                optionView.setTypeface(null, Typeface.BOLD);
            } else {
                itemBg.setColor(Color.TRANSPARENT);
                optionView.setTextColor(Color.parseColor("#E5E5EA"));
            }
            optionView.setBackground(itemBg);

            optionView.setOnClickListener(v -> {
                if (mHapticEngine != null) mHapticEngine.triggerClick();
                setSelection(index);
                if (mListener != null) {
                    mListener.onItemSelected(index, item);
                }
                if (mPopupWindow != null) {
                    mPopupWindow.dismiss();
                }
            });

            optionsLayout.addView(optionView);
        }

        scrollView.addView(optionsLayout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        blurMenu.addView(scrollView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int dropdownWidth = Math.max(getWidth(), (int) (220 * density));
        int maxHeight = (int) (260 * density);

        View rootDecor = null;
        if (context instanceof Activity) {
            rootDecor = ((Activity) context).getWindow().getDecorView();
        } else if (getRootView() != null) {
            rootDecor = getRootView();
        }
        if (rootDecor != null) {
            blurMenu.setBlurRootView(rootDecor);
        }

        mPopupWindow = new PopupWindow(blurMenu, dropdownWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setFocusable(true);
        mPopupWindow.setElevation(16 * density);

        blurMenu.measure(
                View.MeasureSpec.makeMeasureSpec(dropdownWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
        );

        int measuredHeight = blurMenu.getMeasuredHeight();
        if (measuredHeight > maxHeight) {
            mPopupWindow.setHeight(maxHeight);
        }

        mPopupWindow.showAsDropDown(this, 0, (int) (4 * density));
        blurMenu.post(blurMenu::refreshBlur);
    }
}
