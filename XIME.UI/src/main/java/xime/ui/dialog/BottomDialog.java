package xime.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.view.WindowCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import xime.ui.layout.BlurLayout;
import xime.R;

/**
 * * BottomDialog - Ultra-responsive glass-blurred BottomSheetDialog.
 * Synced with Musify design architecture for instant neural feedback.
 */
public class BottomDialog extends BottomSheetDialog {

    private View mContentView;

    public BottomDialog(@NonNull Context context) {
        super(context, R.style.Xoeris_BottomPanel);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false);
            window.setStatusBarColor(0);
            window.setNavigationBarColor(0);
        }
    }

    @Override
    public void setContentView(@NonNull View view) {
        this.mContentView = view;
        super.setContentView(view);
        setupGlass();
    }

    @Override
    public void setContentView(int layoutResId) {
        View view = getLayoutInflater().inflate(layoutResId, null);
        setContentView(view);
    }

    private void setupGlass() {
        if (mContentView == null) return;

        // Aggressive Activity Root Detection
        View root = null;
        Context context = getContext();
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) {
                root = ((Activity) context).getWindow().getDecorView();
                break;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        if (root == null && mContentView.getRootView() != null) root = mContentView.getRootView();

        if (root != null) {
            final View finalRoot = root;
            
            // 1. Instant trigger on layout
            mContentView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (mContentView.getWidth() > 0 && mContentView.getHeight() > 0) {
                        applyBlurRecursively(mContentView, finalRoot);
                        mContentView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            });

            // 2. High-frequency sync during animation
            setOnShowListener(dialog -> {
                FrameLayout bottomSheet = findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    bottomSheet.setBackgroundColor(0);
                    BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                    behavior.setSkipCollapsed(true);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    
                    behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                        @Override
                        public void onStateChanged(@NonNull View bs, int newState) {
                            applyBlurRecursively(mContentView, finalRoot);
                        }

                        @Override
                        public void onSlide(@NonNull View bs, float slideOffset) {
                            // Fast-path refresh during slide to prevent "ghosting"
                            refreshGlassRecursively(mContentView);
                        }
                    });
                }
            });
        }
    }

    private void applyBlurRecursively(View view, View root) {
        if (view instanceof BlurLayout) {
            BlurLayout gf = (BlurLayout) view;
            gf.setBlurRootView(root);
            gf.setPauseUpdates(false);
            gf.setForceInitialBlurWhenPaused(true);
            gf.refreshImmediately();
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyBlurRecursively(vg.getChildAt(i), root);
            }
        }
    }

    private void refreshGlassRecursively(View view) {
        if (view instanceof BlurLayout) {
            ((BlurLayout) view).refreshBlur();
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                refreshGlassRecursively(vg.getChildAt(i));
            }
        }
    }
}

