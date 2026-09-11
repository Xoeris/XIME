package xime.ui.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import xime.graphics.shader.blur.LegacyBlur;
import xime.haptic.HapticEngine;
import xime.ui.layout.BlurLayout;
import xime.ui.layout.Layout;
import xime.ui.layout.LinearLayout;
import xime.ui.layout.RelativeLayout;
import xime.ui.view.TextView;

/**
 * Custom Daily Spin wheel of fortune widget applying Crystal 3D glass bevel directly to each individual wheel item sector.
 */
public class DailySpinView extends Layout {

    public static class SpinItem {
        public String label;
        public String icon; // XC, ⚡
        public int rewardAmount;

        public SpinItem(String label, String icon, int rewardAmount) {
            this.label = label;
            this.icon = icon;
            this.rewardAmount = rewardAmount;
        }
    }

    public interface OnSpinCompleteListener {
        void onSpinComplete(SpinItem winningItem);
    }

    private TextView mWinTitle;
    private TextView mWinSubtitle;
    private WheelView mWheelView;
    private TextView mSpinButton;
    private boolean mIsSpinning = false;
    private OnSpinCompleteListener mListener;
    private HapticEngine mHapticEngine;
    private List<SpinItem> mItems = new ArrayList<>();
    private Runnable mOnBackClickListener;

    public DailySpinView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public DailySpinView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DailySpinView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mHapticEngine = new HapticEngine(context);

        // Default Reward Items with XC (Xoeris Coin) and ⚡
        mItems.add(new SpinItem("100", "XC", 100));
        mItems.add(new SpinItem("5", "XC", 5));
        mItems.add(new SpinItem("3", "⚡", 3));
        mItems.add(new SpinItem("2", "⚡", 2));
        mItems.add(new SpinItem("500", "XC", 500));
        mItems.add(new SpinItem("5", "⚡", 5));
        mItems.add(new SpinItem("10", "XC", 10));
        mItems.add(new SpinItem("2", "XC", 2));

        float density = getResources().getDisplayMetrics().density;
        int p16 = (int) (16 * density);
        int p24 = (int) (24 * density);

        // Main LegacyBlur Blur Dialog Card
        BlurLayout glassCard = new BlurLayout(context);
        glassCard.setBlurType(BlurLayout.BlurType.GLASS);
        glassCard.setCornerRadius(32f);
        glassCard.setBlurRadius(35f);
        glassCard.setGlassTint(Color.parseColor("#300F172A"));
        glassCard.setShowBorder(true);
        glassCard.crystal.set3DBevel(0.92f, 0.6f);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setPadding(p16, p16, p16, p24);

        // 1. Top Header Row (Back ← | Title | History 🕒)
        RelativeLayout topHeader = new RelativeLayout(context);
        topHeader.setPadding(0, 0, 0, (int) (8 * density));

        TextView backBtn = new TextView(context);
        backBtn.setText("←");
        backBtn.setTextSize(20f);
        backBtn.setTextColor(Color.WHITE);
        backBtn.setGravity(Gravity.CENTER);
        int circleSize = (int) (40 * density);
        android.graphics.drawable.GradientDrawable circleBg = new android.graphics.drawable.GradientDrawable();
        circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circleBg.setColor(Color.parseColor("#30FFFFFF"));
        backBtn.setBackground(circleBg);
        RelativeLayout.LayoutParams backLp = new RelativeLayout.LayoutParams(circleSize, circleSize);
        backLp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        backLp.addRule(RelativeLayout.CENTER_VERTICAL);
        topHeader.addView(backBtn, backLp);
        backBtn.setOnClickListener(v -> {
            if (mOnBackClickListener != null) {
                mOnBackClickListener.run();
            }
        });

        TextView headerTitle = new TextView(context);
        headerTitle.setText("Daily spin");
        headerTitle.setTextSize(18f);
        headerTitle.setTextColor(Color.WHITE);
        headerTitle.setTypeface(null, Typeface.BOLD);
        RelativeLayout.LayoutParams titleLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        topHeader.addView(headerTitle, titleLp);

        TextView historyBtn = new TextView(context);
        historyBtn.setText("🕒");
        historyBtn.setTextSize(16f);
        historyBtn.setGravity(Gravity.CENTER);
        historyBtn.setBackground(circleBg);
        RelativeLayout.LayoutParams historyLp = new RelativeLayout.LayoutParams(circleSize, circleSize);
        historyLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        historyLp.addRule(RelativeLayout.CENTER_VERTICAL);
        topHeader.addView(historyBtn, historyLp);

        container.addView(topHeader, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 2. Win Title & Subtitle
        mWinTitle = new TextView(context);
        mWinTitle.setText("Congratulations!");
        mWinTitle.setTextSize(26f);
        mWinTitle.setTextColor(Color.WHITE);
        mWinTitle.setTypeface(null, Typeface.BOLD);
        mWinTitle.setGravity(Gravity.CENTER);

        mWinSubtitle = new TextView(context);
        mWinSubtitle.setText("You won");
        mWinSubtitle.setTextSize(15f);
        mWinSubtitle.setTextColor(Color.parseColor("#E2E8F0"));
        mWinSubtitle.setGravity(Gravity.CENTER);
        mWinSubtitle.setPadding(0, (int) (4 * density), 0, (int) (12 * density));

        container.addView(mWinTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(mWinSubtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 3. Overscaled Wheel Container View with Crystal 3D LegacyBlur Bevel applied to each wheel item sector
        mWheelView = new WheelView(context);
        int wheelWidth = (int) (320 * density);
        int wheelHeight = (int) (270 * density);

        LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(wheelWidth, wheelHeight);
        wheelLp.setMargins(0, 0, 0, (int) (16 * density));
        container.addView(mWheelView, wheelLp);

        // 4. Spin Button
        mSpinButton = new TextView(context);
        mSpinButton.setText("Spin 1x");
        mSpinButton.setTextSize(16f);
        mSpinButton.setTextColor(Color.WHITE);
        mSpinButton.setTypeface(null, Typeface.BOLD);
        mSpinButton.setGravity(Gravity.CENTER);
        int btnHeight = (int) (52 * density);

        android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
        btnBg.setCornerRadius(100f);
        btnBg.setColors(new int[]{Color.parseColor("#7C3AED"), Color.parseColor("#4C1D95")});
        btnBg.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT);
        mSpinButton.setBackground(btnBg);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, btnHeight);
        container.addView(mSpinButton, btnLp);

        mSpinButton.setOnClickListener(v -> spin());

        glassCard.addView(container, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int marginHorizontal = (int) (20 * density);
        LayoutParams cardLp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        cardLp.setMargins(marginHorizontal, 0, marginHorizontal, 0);
        addView(glassCard, cardLp);
    }

    public void setOnSpinCompleteListener(OnSpinCompleteListener listener) {
        this.mListener = listener;
    }

    public void setOnBackClickListener(Runnable listener) {
        this.mOnBackClickListener = listener;
    }

    public void spin() {
        if (mIsSpinning) return;
        mIsSpinning = true;
        mSpinButton.setAlpha(0.6f);

        int targetIndex = new Random().nextInt(mItems.size());
        mWheelView.spinToItem(targetIndex, () -> {
            mIsSpinning = false;
            mSpinButton.setAlpha(1.0f);

            SpinItem winner = mItems.get(targetIndex);
            mWinSubtitle.setText("You won " + winner.label + " " + winner.icon + "!");
            if (mHapticEngine != null) {
                mHapticEngine.triggerSuccess();
            }
            if (mListener != null) {
                mListener.onSpinComplete(winner);
            }
        });
    }

    // --- Custom Canvas Wheel View (Applying Crystal 3D LegacyBlur Bevel to EACH Wheel Item Sector) ---
    private class WheelView extends View {
        private Paint mArcPaint;
        private Paint mTextPaint;
        private Paint mIconPaint;
        private Paint mPointerPaint;
        private Paint mCenterPaint;
        private Paint mSectorBevelStrokePaint;
        private RectF mRectF;
        private float mRotationAngle = 0f;
        private float mGradientAnimProgress = 0f;
        private ValueAnimator mGradientAnimator;
        private final LegacyBlur mSectorCrystal = new LegacyBlur();

        public WheelView(Context context) {
            super(context);
            initView();
        }

        private void initView() {
            float density = getResources().getDisplayMetrics().density;

            mSectorCrystal.set3DBevel(0.88f, 1.8f);
            mSectorCrystal.setDepthEffect(0.12f);
            mSectorCrystal.setDistortionAmount(0.15f);

            mArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mArcPaint.setStyle(Paint.Style.FILL);

            mSectorBevelStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mSectorBevelStrokePaint.setStyle(Paint.Style.STROKE);
            mSectorBevelStrokePaint.setStrokeWidth(3.5f * density);
            mSectorBevelStrokePaint.setColor(Color.parseColor("#60FFFFFF"));

            mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mTextPaint.setColor(Color.WHITE);
            mTextPaint.setTextSize(14f * density);
            mTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            mTextPaint.setTextAlign(Paint.Align.CENTER);

            mIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mIconPaint.setColor(Color.parseColor("#FFD600"));
            mIconPaint.setTextSize(18f * density);
            mIconPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            mIconPaint.setTextAlign(Paint.Align.CENTER);

            mPointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPointerPaint.setColor(Color.parseColor("#7C3AED"));
            mPointerPaint.setStyle(Paint.Style.FILL);

            mCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mCenterPaint.setColor(Color.parseColor("#4C1D95"));
            mCenterPaint.setStyle(Paint.Style.FILL);

            mRectF = new RectF();

            // Continuous Non-Stop Gradient Color Shimmer Animation on Spinner Items
            mGradientAnimator = ValueAnimator.ofFloat(0f, 1f);
            mGradientAnimator.setDuration(3000);
            mGradientAnimator.setInterpolator(new LinearInterpolator());
            mGradientAnimator.setRepeatCount(ValueAnimator.INFINITE);
            mGradientAnimator.addUpdateListener(anim -> {
                mGradientAnimProgress = (float) anim.getAnimatedValue();
                invalidate();
            });
            mGradientAnimator.start();
        }

        public void spinToItem(int targetIndex, Runnable onEnd) {
            int itemCount = mItems.size();
            float sweepAngle = 360f / itemCount;
            
            float targetSliceCenter = (targetIndex * sweepAngle) + (sweepAngle / 2f);
            float finalAngle = (360f * 5) + (270f - targetSliceCenter);

            ValueAnimator animator = ValueAnimator.ofFloat(mRotationAngle % 360f, finalAngle);
            animator.setDuration(3500);
            animator.setInterpolator(new DecelerateInterpolator(2.5f));
            
            final float[] lastTickAngle = {0f};

            animator.addUpdateListener(animation -> {
                mRotationAngle = (float) animation.getAnimatedValue();
                if (Math.abs(mRotationAngle - lastTickAngle[0]) > sweepAngle) {
                    lastTickAngle[0] = mRotationAngle;
                    if (mHapticEngine != null) {
                        mHapticEngine.triggerClick();
                    }
                }
                invalidate();
            });

            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (onEnd != null) {
                        onEnd.run();
                    }
                }
            });

            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;

            float centerX = width / 2f;
            float centerY = height * 0.82f;
            float radius = width * 0.62f;

            mRectF.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

            int itemCount = mItems.size();
            float sweepAngle = 360f / itemCount;

            canvas.save();
            canvas.rotate(mRotationAngle, centerX, centerY);

            for (int i = 0; i < itemCount; i++) {
                SpinItem item = mItems.get(i);
                float startAngle = i * sweepAngle;
                float sliceMidAngle = startAngle + (sweepAngle / 2f);

                double rad = Math.toRadians(sliceMidAngle);
                float endX = (float) (centerX + Math.cos(rad) * radius);
                float endY = (float) (centerY + Math.sin(rad) * radius);

                // Animated rainbow gradient colors per slice sector
                float hue1 = (i * (360f / itemCount) + (mGradientAnimProgress * 360f)) % 360f;
                float hue2 = (hue1 + 60f) % 360f;
                int c1 = Color.HSVToColor(new float[]{hue1, 0.85f, 0.95f});
                int c2 = Color.HSVToColor(new float[]{hue2, 0.90f, 0.70f});

                LinearGradient sliceGradient = new LinearGradient(
                        centerX, centerY, endX, endY,
                        new int[]{c1, c2}, null, Shader.TileMode.CLAMP);

                mArcPaint.setShader(sliceGradient);

                // 1. Define slice sector path for wheel item i
                Path slicePath = new Path();
                slicePath.moveTo(centerX, centerY);
                slicePath.arcTo(mRectF, startAngle, sweepAngle, false);
                slicePath.close();

                // 2. Draw live animated gradient sector
                canvas.drawPath(slicePath, mArcPaint);

                // 3. Apply Crystal 3D LegacyBlur Bevel & Refraction Sheen to EACH WHEEL ITEM SECTOR
                canvas.save();
                canvas.clipPath(slicePath);

                float sheenX = (float) (centerX + Math.cos(rad) * radius * 0.5f);
                float sheenY = (float) (centerY + Math.sin(rad) * radius * 0.5f);

                RadialGradient sectorGlassSheen = new RadialGradient(
                        sheenX, sheenY, radius * 0.65f,
                        new int[]{Color.parseColor("#65FFFFFF"), Color.parseColor("#15FFFFFF"), Color.parseColor("#50000000")},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);

                Paint sectorGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                sectorGlassPaint.setStyle(Paint.Style.FILL);
                sectorGlassPaint.setShader(sectorGlassSheen);
                canvas.drawPath(slicePath, sectorGlassPaint);

                // 4. Sector Crystal 3D Bevel Edge Border
                canvas.drawPath(slicePath, mSectorBevelStrokePaint);

                canvas.restore();

                // Draw Item Text and Icon inside sector
                canvas.save();
                float itemAngle = startAngle + (sweepAngle / 2f);

                double itemRad = Math.toRadians(itemAngle);
                float distFromCenter = radius * 0.62f;
                float sliceCenterX = (float) (centerX + Math.cos(itemRad) * distFromCenter);
                float sliceCenterY = (float) (centerY + Math.sin(itemRad) * distFromCenter);

                canvas.translate(sliceCenterX, sliceCenterY);
                // Rotate text to align along the sector radius
                float rot = itemAngle + 90f;
                // Flip upside down text for bottom half readability
                if (itemAngle > 90f && itemAngle < 270f) {
                    rot += 180f;
                }
                canvas.rotate(rot);

                float dp = getResources().getDisplayMetrics().density;
                canvas.drawText(item.icon, 0, -dp * 4f, mIconPaint);
                canvas.drawText(item.label, 0, dp * 14f, mTextPaint);

                canvas.restore();
            }

            canvas.restore();

            // Draw Center Hub Button
            float hubRadius = radius * 0.22f;
            canvas.drawCircle(centerX, centerY, hubRadius, mCenterPaint);

            float density = getResources().getDisplayMetrics().density;
            Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            starPaint.setColor(Color.WHITE);
            starPaint.setTextSize(28f * density);
            starPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("✦", centerX, centerY + 10f * density, starPaint);

            // Draw Top Pointer Arrow
            Path pointerPath = new Path();
            float pointerWidth = 36f * density;
            float pointerHeight = 44f * density;
            float pointerTop = centerY - radius - 12f * density;

            pointerPath.moveTo(centerX - pointerWidth / 2f, pointerTop);
            pointerPath.lineTo(centerX + pointerWidth / 2f, pointerTop);
            pointerPath.lineTo(centerX, pointerTop + pointerHeight);
            pointerPath.close();

            canvas.drawPath(pointerPath, mPointerPaint);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (mGradientAnimator != null) {
                mGradientAnimator.cancel();
            }
        }
    }
}

