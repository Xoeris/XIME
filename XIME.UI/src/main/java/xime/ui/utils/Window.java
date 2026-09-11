package xime.ui.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import xime.R;
import xime.ui.layout.BlurLayout;
import xime.ui.view.TextView;
import xime.ui.dialog.Dialog;

/**
 * Fully standalone counterpart to Window. Contains NO references to
 * android.app.Dialog anywhere, every method here operates on raw Views,
 * WindowManager.LayoutParams, and Dialog instead.
 */
public class Window {

    public static void setFullScreen(Activity activity) {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    /**
     * Helper to get screen width using DisplayMetrics.
     */
    public static int getWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public static int getHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    /**
     * LegacyBlur-blur variant for panels that are added directly via WindowManager
     * (e.g. xime.ui.view.widget.Dialog) rather than wrapped in a Dialog.
     * Operates on the panel's own view directly instead of a Dialog's Window.
     */
    public static void applyGlassToPanel(@NonNull View panelView, View rootView) {
        panelView.setBackgroundResource(android.R.color.transparent);
        applyGlassRecursively(panelView, rootView);
    }

    /**
     * Adjusts dim amount on a standalone panel's WindowManager.LayoutParams and
     * re-applies them. Call after mutating flags/dimAmount on an already-shown
     * panel.
     */
    public static void updatePanelDim(@NonNull View panelView,
                                      @NonNull WindowManager.LayoutParams params, float dimAmount) {
        params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        params.dimAmount = dimAmount;
        WindowManager wm = (WindowManager) panelView.getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        if (panelView.getParent() != null) {
            wm.updateViewLayout(panelView, params);
        }
    }

    /**
     * Panel equivalent of the old showCrystalDialog. Builds a message panel
     * and shows it via WindowManager instead of constructing an android.app.Dialog.
     *
     * @param hostToken window token from the host Activity, required by
     *                  Panel#setToken(...) since Panel has no Dialog to
     *                  derive one from automatically.
     */
    public static void showCrystalPanel(Context context, android.os.IBinder hostToken, View root,
                                        String title, String message, String positive, String negative,
                                        Runnable onPositive, Runnable onNegative) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog, null);
        TextView textTitle = view.findViewById(R.id.dialogTitle);
        TextView textMessage = view.findViewById(R.id.dialogMessage);
        TextView btnPos = view.findViewById(R.id.btn_positive);
        TextView btnNeg = view.findViewById(R.id.btn_negative);

        if (textTitle != null)
            textTitle.setText(title);
        if (textMessage != null)
            textMessage.setText(message);

        final Dialog panel = new Dialog(context);
        panel.setContentView(view);
        panel.setToken(hostToken);

        WindowManager.LayoutParams params = panel.getLayoutParams2();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.FILL;
        params.format = PixelFormat.TRANSLUCENT;

        if (btnPos != null) {
            btnPos.setText(positive != null ? positive : "OK");
            btnPos.setOnClickListener(v -> {
                panel.dismiss();
                if (onPositive != null)
                    onPositive.run();
            });
        }

        if (btnNeg != null) {
            if (negative == null) {
                btnNeg.setVisibility(View.GONE);
            } else {
                btnNeg.setText(negative);
                btnNeg.setOnClickListener(v -> {
                    panel.dismiss();
                    if (onNegative != null)
                        onNegative.run();
                });
            }
        }

        panel.show();
        applyGlassToPanel(view, root);
    }

    private static boolean applyGlassRecursively(View view, View rootView) {
        boolean found = false;
        if (view instanceof BlurLayout) {
            final BlurLayout glass = (BlurLayout) view;
            glass.setBlurRootView(rootView);
            glass.post(() -> glass.refreshBlur());
            return true;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (applyGlassRecursively(group.getChildAt(i), rootView)) {
                found = true;
            }
        }
        return found;
    }
}

