package xime.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.Locale;

import xime.haptic.HapticEngine;
import xime.ui.layout.BlurLayout;
import xime.ui.layout.LinearLayout;
import xime.ui.view.TextView;
import xime.ui.utils.Window;

/**
 * Custom Analog & Digital Time Picker Dialog Widget for XIME.UI framework.
 */
public class TimePickerDialog extends LinearLayout {

    public interface OnTimeSelectedListener {
        void onTimeSelected(int hourOfDay, int minute);
    }

    private Calendar mCalendar;
    private OnTimeSelectedListener mListener;
    private Runnable mOnDismissListener;
    private HapticEngine mHapticEngine;

    private int mHour; // 1 to 12
    private int mMinute; // 0 to 59
    private boolean mIsPm;
    private boolean mIsHourMode = true;

    private TextView mHourTv;
    private TextView mMinTv;
    private TextView mAmTv;
    private TextView mPmTv;
    private View mClockView;
    private Dialog mDialog;

    public TimePickerDialog(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public TimePickerDialog(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public TimePickerDialog(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        setOrientation(VERTICAL);
        mHapticEngine = new HapticEngine(context);
        mCalendar = Calendar.getInstance();
        setupStateFromCalendar();
        buildUI(context);
    }

    public void setCalendar(Calendar calendar) {
        if (calendar != null) {
            this.mCalendar = (Calendar) calendar.clone();
            setupStateFromCalendar();
            updateDisplayState();
            if (mClockView != null) mClockView.invalidate();
        }
    }

    public void setOnTimeSelectedListener(OnTimeSelectedListener listener) {
        this.mListener = listener;
    }

    public void setOnDismissListener(Runnable listener) {
        this.mOnDismissListener = listener;
    }

    private void setupStateFromCalendar() {
        int hour24 = mCalendar.get(Calendar.HOUR_OF_DAY);
        mMinute = mCalendar.get(Calendar.MINUTE);
        mIsPm = hour24 >= 12;
        int hour12 = hour24 % 12;
        mHour = (hour12 == 0) ? 12 : hour12;
    }

    private void buildUI(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float density = dm.density;

        setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (20 * density);
        setPadding(pad, pad, pad, pad);

        // Header with Title and Close X Button
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, (int) (12 * density));

        TextView titleTv = new TextView(context);
        titleTv.setText("Select Time");
        titleTv.setTextSize(20f);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTypeface(null, Typeface.BOLD);

        TextView closeXBtn = new TextView(context);
        closeXBtn.setText("✕");
        closeXBtn.setTextSize(18f);
        closeXBtn.setTextColor(Color.parseColor("#8E8E93"));
        closeXBtn.setPadding((int) (8 * density), 0, 0, 0);

        LayoutParams titleLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(titleTv, titleLp);
        header.addView(closeXBtn);
        addView(header);

        // Digital Display Container
        LinearLayout digitalContainer = new LinearLayout(context);
        digitalContainer.setOrientation(HORIZONTAL);
        digitalContainer.setGravity(Gravity.CENTER);
        digitalContainer.setPadding(0, 0, 0, (int) (16 * density));

        int boxPadH = (int) (16 * density);
        int boxPadV = (int) (8 * density);

        mHourTv = new TextView(context);
        mHourTv.setTextSize(36f);
        mHourTv.setTypeface(null, Typeface.BOLD);
        mHourTv.setGravity(Gravity.CENTER);
        mHourTv.setPadding(boxPadH, boxPadV, boxPadH, boxPadV);

        TextView colonTv = new TextView(context);
        colonTv.setText(" : ");
        colonTv.setTextSize(36f);
        colonTv.setTextColor(Color.WHITE);
        colonTv.setTypeface(null, Typeface.BOLD);

        mMinTv = new TextView(context);
        mMinTv.setTextSize(36f);
        mMinTv.setTypeface(null, Typeface.BOLD);
        mMinTv.setGravity(Gravity.CENTER);
        mMinTv.setPadding(boxPadH, boxPadV, boxPadH, boxPadV);

        LinearLayout amPmBox = new LinearLayout(context);
        amPmBox.setOrientation(VERTICAL);
        amPmBox.setGravity(Gravity.CENTER);

        mAmTv = new TextView(context);
        mAmTv.setText("AM");
        mAmTv.setTextSize(14f);
        mAmTv.setGravity(Gravity.CENTER);
        mAmTv.setClickable(true);
        mAmTv.setFocusable(true);
        mAmTv.setPadding((int) (12 * density), (int) (6 * density), (int) (12 * density), (int) (6 * density));

        mPmTv = new TextView(context);
        mPmTv.setText("PM");
        mPmTv.setTextSize(14f);
        mPmTv.setGravity(Gravity.CENTER);
        mPmTv.setClickable(true);
        mPmTv.setFocusable(true);
        mPmTv.setPadding((int) (12 * density), (int) (6 * density), (int) (12 * density), (int) (6 * density));

        amPmBox.addView(mAmTv);
        amPmBox.addView(mPmTv);

        digitalContainer.addView(mHourTv);
        digitalContainer.addView(colonTv);
        digitalContainer.addView(mMinTv);

        LayoutParams amPmLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        amPmLp.setMargins((int) (16 * density), 0, 0, 0);
        digitalContainer.addView(amPmBox, amPmLp);
        LayoutParams digitalLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addView(digitalContainer, digitalLp);

        // Analog Clock Custom View
        mClockView = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int size = (int) (230 * density);
                setMeasuredDimension(size, size);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float width = getWidth();
                float height = getHeight();
                float centerX = width / 2f;
                float centerY = height / 2f;
                float radius = Math.min(width, height) / 2f - 4 * density;

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#1C2438"));
                canvas.drawCircle(centerX, centerY, radius, paint);

                float numRadius = radius * 0.72f;

                double angleRad;
                if (mIsHourMode) {
                    int h = mHour % 12;
                    angleRad = Math.toRadians((h * 30) - 90);
                } else {
                    angleRad = Math.toRadians((mMinute * 6) - 90);
                }

                float selectedX = centerX + (float) (numRadius * Math.cos(angleRad));
                float selectedY = centerY + (float) (numRadius * Math.sin(angleRad));

                paint.setColor(Color.parseColor("#2563EB"));
                paint.setStrokeWidth(3 * density);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(centerX, centerY, selectedX, selectedY, paint);

                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(centerX, centerY, 5 * density, paint);
                canvas.drawCircle(selectedX, selectedY, 18 * density, paint);

                paint.setTextSize(14 * density);
                paint.setTextAlign(Paint.Align.CENTER);

                for (int i = 1; i <= 12; i++) {
                    double numAngle = Math.toRadians((i * 30) - 90);
                    float nx = centerX + (float) (numRadius * Math.cos(numAngle));
                    float ny = centerY + (float) (numRadius * Math.sin(numAngle));

                    int displayNum = mIsHourMode ? i : (i * 5) % 60;
                    boolean isSelected;
                    if (mIsHourMode) {
                        isSelected = (mHour == i) || (mHour == 0 && i == 12);
                    } else {
                        isSelected = (mMinute == displayNum);
                    }

                    if (isSelected) {
                        paint.setColor(Color.WHITE);
                        paint.setTypeface(Typeface.DEFAULT_BOLD);
                    } else {
                        paint.setColor(Color.parseColor("#94A3B8"));
                        paint.setTypeface(Typeface.DEFAULT);
                    }

                    float textY = ny - ((paint.descent() + paint.ascent()) / 2);
                    String textStr = mIsHourMode ? String.valueOf(displayNum) : String.format(Locale.getDefault(), "%02d", displayNum);
                    canvas.drawText(textStr, nx, textY, paint);
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getX() - (getWidth() / 2f);
                    float dy = event.getY() - (getHeight() / 2f);
                    double angleDeg = Math.toDegrees(Math.atan2(dy, dx)) + 90;
                    if (angleDeg < 0) angleDeg += 360;

                    int prevH = mHour;
                    int prevM = mMinute;

                    if (mIsHourMode) {
                        int h = (int) Math.round(angleDeg / 30.0);
                        if (h == 0) h = 12;
                        mHour = h;
                        if (prevH != mHour && mHapticEngine != null) {
                            mHapticEngine.triggerTick();
                        }
                    } else {
                        mMinute = (int) Math.round(angleDeg / 6.0) % 60;
                        if (prevM != mMinute && mHapticEngine != null) {
                            mHapticEngine.triggerTick();
                        }
                    }
                    invalidate();
                    updateDisplayState();
                    return true;
                }
                return super.onTouchEvent(event);
            }
        };

        LayoutParams clockLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clockLp.gravity = Gravity.CENTER_HORIZONTAL;
        clockLp.setMargins(0, 0, 0, (int) (16 * density));
        addView(mClockView, clockLp);

        // Action Buttons Row
        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setOrientation(HORIZONTAL);
        actionRow.setGravity(Gravity.END);

        TextView cancelBtn = new TextView(context);
        cancelBtn.setText("Cancel");
        cancelBtn.setTextColor(Color.parseColor("#38BDF8"));
        cancelBtn.setTextSize(16f);
        cancelBtn.setPadding((int) (16 * density), (int) (8 * density), (int) (16 * density), (int) (8 * density));

        TextView okBtn = new TextView(context);
        okBtn.setText("OK");
        okBtn.setTextColor(Color.parseColor("#38BDF8"));
        okBtn.setTextSize(16f);
        okBtn.setTypeface(null, Typeface.BOLD);
        okBtn.setPadding((int) (16 * density), (int) (8 * density), (int) (16 * density), (int) (8 * density));

        actionRow.addView(cancelBtn);
        actionRow.addView(okBtn);
        addView(actionRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Click Listeners
        mHourTv.setOnClickListener(v -> {
            if (mHapticEngine != null) mHapticEngine.triggerClick();
            mIsHourMode = true;
            updateDisplayState();
            mClockView.invalidate();
        });

        mMinTv.setOnClickListener(v -> {
            if (mHapticEngine != null) mHapticEngine.triggerClick();
            mIsHourMode = false;
            updateDisplayState();
            mClockView.invalidate();
        });

        mAmTv.setOnClickListener(v -> {
            if (mHapticEngine != null) mHapticEngine.triggerClick();
            mIsPm = false;
            updateDisplayState();
        });

        mPmTv.setOnClickListener(v -> {
            if (mHapticEngine != null) mHapticEngine.triggerClick();
            mIsPm = true;
            updateDisplayState();
        });

        closeXBtn.setOnClickListener(v -> {
            if (mHapticEngine != null) mHapticEngine.triggerClick();
            dismissDialog();
        });

        cancelBtn.setOnClickListener(v -> {
            if (mHapticEngine != null) mHapticEngine.triggerClick();
            dismissDialog();
        });

        okBtn.setOnClickListener(v -> {
            if (mHapticEngine != null) mHapticEngine.triggerClick();
            int finalHour;
            if (mIsPm) {
                finalHour = (mHour == 12) ? 12 : mHour + 12;
            } else {
                finalHour = (mHour == 12) ? 0 : mHour;
            }
            mCalendar.set(Calendar.HOUR_OF_DAY, finalHour);
            mCalendar.set(Calendar.MINUTE, mMinute);

            if (mListener != null) {
                mListener.onTimeSelected(finalHour, mMinute);
            }
            dismissDialog();
        });

        updateDisplayState();
    }

    private void updateDisplayState() {
        DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        float density = dm.density;

        int displayHour;
        if (mIsPm) {
            displayHour = (mHour == 12) ? 12 : mHour + 12;
        } else {
            displayHour = (mHour == 12) ? 0 : mHour;
        }

        mHourTv.setText(String.format(Locale.getDefault(), "%02d", displayHour));
        mMinTv.setText(String.format(Locale.getDefault(), "%02d", mMinute));

        GradientDrawable activeBg = new GradientDrawable();
        activeBg.setColor(Color.parseColor("#2563EB"));
        activeBg.setCornerRadius(12 * density);

        GradientDrawable inactiveBg = new GradientDrawable();
        inactiveBg.setColor(Color.parseColor("#1C2438"));
        inactiveBg.setCornerRadius(12 * density);

        if (mIsHourMode) {
            mHourTv.setBackground(activeBg);
            mHourTv.setTextColor(Color.WHITE);
            mMinTv.setBackground(inactiveBg);
            mMinTv.setTextColor(Color.parseColor("#94A3B8"));
        } else {
            mHourTv.setBackground(inactiveBg);
            mHourTv.setTextColor(Color.parseColor("#94A3B8"));
            mMinTv.setBackground(activeBg);
            mMinTv.setTextColor(Color.WHITE);
        }

        GradientDrawable amPmBoxBg = new GradientDrawable();
        amPmBoxBg.setCornerRadius(8 * density);
        amPmBoxBg.setStroke((int) (1 * density), Color.parseColor("#2563EB"));
        amPmBoxBg.setColor(Color.parseColor("#1C2438"));

        GradientDrawable amActiveBg = new GradientDrawable();
        amActiveBg.setColor(Color.parseColor("#2563EB"));
        amActiveBg.setCornerRadii(new float[]{7 * density, 7 * density, 7 * density, 7 * density, 0, 0, 0, 0});

        GradientDrawable pmActiveBg = new GradientDrawable();
        pmActiveBg.setColor(Color.parseColor("#2563EB"));
        pmActiveBg.setCornerRadii(new float[]{0, 0, 0, 0, 7 * density, 7 * density, 7 * density, 7 * density});

        if (mIsPm) {
            mAmTv.setBackground(null);
            mAmTv.setTextColor(Color.parseColor("#94A3B8"));
            mPmTv.setBackground(pmActiveBg);
            mPmTv.setTextColor(Color.WHITE);
        } else {
            mAmTv.setBackground(amActiveBg);
            mAmTv.setTextColor(Color.WHITE);
            mPmTv.setBackground(null);
            mPmTv.setTextColor(Color.parseColor("#94A3B8"));
        }
        View amPmParent = (View) mAmTv.getParent();
        if (amPmParent != null) {
            amPmParent.setBackground(amPmBoxBg);
        }
    }

    private void dismissDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
        if (mOnDismissListener != null) {
            mOnDismissListener.run();
        }
    }

    /**
     * Helper method to present this TimePickerDialog inside a glassmorphic dialog.
     */
    public void showDialog(Context context, Calendar initialCal, OnTimeSelectedListener listener) {
        if (!(context instanceof Activity)) return;
        Activity activity = (Activity) context;

        setCalendar(initialCal);
        setOnTimeSelectedListener(listener);

        BlurLayout blurCard = new BlurLayout(context);
        blurCard.setCornerRadius(100f);
        blurCard.setBlurRadius(25f);
        blurCard.setGlassTint(Color.parseColor("#EA141419"));
        blurCard.setShowBorder(true);
        blurCard.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        blurCard.addView(this);

        mDialog = new Dialog(context);
        mDialog.setContentView(blurCard);
        mDialog.setToken(activity.getWindow().getDecorView().getWindowToken());
        mDialog.show();
        Window.applyGlassToPanel(blurCard, activity.getWindow().getDecorView());
    }
}
