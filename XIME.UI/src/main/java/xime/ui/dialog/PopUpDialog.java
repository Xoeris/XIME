package xime.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.IBinder;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.button.MaterialButton;
import xime.animation.MotionCurve;
import xime.ui.layout.BlurLayout;
import xime.ui.layout.LinearLayout;
import xime.ui.view.TextView;
import xime.ui.utils.Window;

/**
 * PopUpDialog - Glassmorphism bottom notification popup dialog.
 * Positions at the bottom above navigation bar and animates sliding in/out from down.
 */
public class PopUpDialog extends Dialog {

    private TextView mTitleView;
    private TextView mMessageView;
    private MaterialButton mActionButton;
    private BlurLayout mCardLayout;
    private FrameLayout mContentHolderRef;
    private boolean mIsCustomDismissing = false;

    public PopUpDialog(@NonNull Context context) {
        super(context);
        initView();
    }

    public PopUpDialog(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    private void initView() {
        Context context = getContext();
        float density = context.getResources().getDisplayMetrics().density;

        mCardLayout = new BlurLayout(context);
        mCardLayout.setCornerRadius(28f);
        mCardLayout.setBlurRadius(25f);
        mCardLayout.setGlassTint(Color.parseColor("#E6141419"));
        mCardLayout.setShowBorder(true);

        LinearLayout cardContent = new LinearLayout(context);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * density);
        cardContent.setPadding(padding, padding, padding, padding);

        // Title
        mTitleView = new TextView(context);
        mTitleView.setTextSize(16f);
        mTitleView.setTextColor(Color.parseColor("#FFD600"));
        mTitleView.setTypeface(null, android.graphics.Typeface.BOLD);
        mTitleView.setGravity(Gravity.CENTER);

        // Message
        mMessageView = new TextView(context);
        mMessageView.setTextSize(15f);
        mMessageView.setTextColor(Color.WHITE);
        mMessageView.setGravity(Gravity.CENTER);
        mMessageView.setPadding(0, (int) (8 * density), 0, (int) (16 * density));
        mMessageView.setLineSpacing(0, 1.2f);

        // Action Button
        mActionButton = new MaterialButton(context);
        mActionButton.setText("GOT IT");
        mActionButton.setTextSize(14f);
        mActionButton.setCornerRadius((int) (22 * density));
        mActionButton.setBackgroundColor(Color.parseColor("#FFD600"));
        mActionButton.setTextColor(Color.BLACK);
        mActionButton.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (44 * density));

        cardContent.addView(mTitleView);
        cardContent.addView(mMessageView);
        cardContent.addView(mActionButton, btnLp);

        mCardLayout.addView(cardContent, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(mCardLayout);
    }

    @Override
    public void setContentView(@NonNull View view) {
        super.setContentView(view);
        // Position at the bottom above navigation bar (bottom margin 95dp)
        float density = getContext().getResources().getDisplayMetrics().density;
        int bottomMargin = (int) (95 * density);
        int sideMargin = (int) (16 * density);

        if (view.getParent() instanceof FrameLayout) {
            FrameLayout container = (FrameLayout) view.getParent();
            this.mContentHolderRef = container;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) container.getLayoutParams();
            if (lp == null) {
                lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.setMargins(sideMargin, 0, sideMargin, bottomMargin);
            container.setLayoutParams(lp);
        }
    }

    @Override
    public void show() {
        super.show();

        // Custom bottom slide-up animation (appears from down)
        if (mContentHolderRef != null) {
            float density = getContext().getResources().getDisplayMetrics().density;
            mContentHolderRef.setScaleX(1.0f);
            mContentHolderRef.setScaleY(1.0f);
            mContentHolderRef.setTranslationY(250 * density);
            mContentHolderRef.setAlpha(0f);
            mContentHolderRef.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(320)
                    .setInterpolator(MotionCurve.FLUID)
                    .start();
        }
    }

    @Override
    public void dismiss() {
        if (mIsCustomDismissing) return;
        mIsCustomDismissing = true;

        if (mContentHolderRef != null) {
            float density = getContext().getResources().getDisplayMetrics().density;
            mContentHolderRef.animate()
                    .translationY(250 * density)
                    .alpha(0f)
                    .setDuration(260)
                    .setInterpolator(MotionCurve.FLUID)
                    .withEndAction(() -> super.dismiss())
                    .start();
        } else {
            super.dismiss();
        }
    }

    public void setTitle(String title) {
        if (mTitleView != null) {
            mTitleView.setText(title);
        }
    }

    public void setMessage(String message) {
        if (mMessageView != null) {
            mMessageView.setText(message);
        }
    }

    public void setActionButtonText(String text) {
        if (mActionButton != null) {
            mActionButton.setText(text);
        }
    }

    public void setOnActionClickListener(Runnable listener) {
        if (mActionButton != null) {
            mActionButton.setOnClickListener(v -> {
                dismiss();
                if (listener != null) {
                    listener.run();
                }
            });
        }
    }

    public static PopUpDialog show(Context context, IBinder token, String title, String message, String buttonText, Runnable onConfirm) {
        PopUpDialog dialog = new PopUpDialog(context);
        if (title != null) dialog.setTitle(title);
        if (message != null) dialog.setMessage(message);
        if (buttonText != null) dialog.setActionButtonText(buttonText);
        dialog.setOnActionClickListener(onConfirm);

        if (token != null) {
            dialog.setToken(token);
        }
        dialog.show();

        if (context instanceof Activity) {
            Window.applyGlassToPanel(dialog.mCardLayout, ((Activity) context).getWindow().getDecorView());
        }

        return dialog;
    }
}
