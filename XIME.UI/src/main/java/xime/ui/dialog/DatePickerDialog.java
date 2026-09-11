package xime.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import xime.haptic.HapticEngine;
import xime.ui.layout.BlurLayout;
import xime.ui.layout.LinearLayout;
import xime.ui.view.TextView;
import xime.ui.utils.Window;

/**
 * Custom Dark Glassmorphic Date Picker Dialog Widget for XIME.UI framework.
 */
public class DatePickerDialog extends LinearLayout {

    public interface OnDateSelectedListener {
        void onDateSelected(int year, int month, int dayOfMonth);
    }

    private Calendar mSelectedCalendar;
    private Calendar mPickerCalendar;
    private OnDateSelectedListener mListener;
    private Runnable mOnDismissListener;
    private Dialog mDialog;
    private HapticEngine mHapticEngine;

    private TextView mMonthTitle;
    private LinearLayout mGridContainer;

    public DatePickerDialog(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public DatePickerDialog(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public DatePickerDialog(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        setOrientation(VERTICAL);
        mHapticEngine = new HapticEngine(context);
        mSelectedCalendar = Calendar.getInstance();
        mPickerCalendar = (Calendar) mSelectedCalendar.clone();
        buildUI(context);
    }

    public void setCalendar(Calendar calendar) {
        if (calendar != null) {
            this.mSelectedCalendar = (Calendar) calendar.clone();
            this.mPickerCalendar = (Calendar) calendar.clone();
            updateGrid();
        }
    }

    public void setOnDateSelectedListener(OnDateSelectedListener listener) {
        this.mListener = listener;
    }

    public void setOnDismissListener(Runnable listener) {
        this.mOnDismissListener = listener;
    }

    private int mCalendarViewLevel = 0; // 0 = Days Grid, 1 = Months Grid, 2 = Years Grid
    private LinearLayout mDaysOfWeekRow;

    private void buildUI(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float density = dm.density;

        int pad = (int) (24 * density);
        setPadding(pad, pad, pad, pad);

        // Header: Prev Month, Title, Next Month, Close X
        LinearLayout monthHeader = new LinearLayout(context);
        monthHeader.setOrientation(HORIZONTAL);
        monthHeader.setGravity(Gravity.CENTER_VERTICAL);
        monthHeader.setPadding(0, 0, 0, (int) (16 * density));

        MaterialButton prevBtn = new MaterialButton(context);
        prevBtn.setText("◀");
        prevBtn.setTextSize(14f);
        prevBtn.setTextColor(Color.WHITE);
        prevBtn.setBackgroundColor(Color.TRANSPARENT);

        mMonthTitle = new TextView(context);
        mMonthTitle.setTextSize(18f);
        mMonthTitle.setTextColor(Color.WHITE);
        mMonthTitle.setTypeface(null, Typeface.BOLD);
        mMonthTitle.setGravity(Gravity.CENTER);
        mMonthTitle.setOnClickListener(v -> {
            if (mHapticEngine != null) mHapticEngine.triggerClick();
            mCalendarViewLevel = (mCalendarViewLevel + 1) % 3;
            updateGrid();
        });

        MaterialButton nextBtn = new MaterialButton(context);
        nextBtn.setText("▶");
        nextBtn.setTextSize(14f);
        nextBtn.setTextColor(Color.WHITE);
        nextBtn.setBackgroundColor(Color.TRANSPARENT);

        TextView closeXBtn = new TextView(context);
        closeXBtn.setText("✕");
        closeXBtn.setTextSize(18f);
        closeXBtn.setTextColor(Color.parseColor("#8E8E93"));
        closeXBtn.setPadding((int) (8 * density), 0, 0, 0);

        LayoutParams titleLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        monthHeader.addView(prevBtn);
        monthHeader.addView(mMonthTitle, titleLp);
        monthHeader.addView(nextBtn);
        monthHeader.addView(closeXBtn);
        addView(monthHeader);

        // Days of week header row (S M T W T F S)
        mDaysOfWeekRow = new LinearLayout(context);
        mDaysOfWeekRow.setOrientation(HORIZONTAL);
        mDaysOfWeekRow.setPadding(0, 0, 0, (int) (8 * density));
        String[] daysOfWeek = {"S", "M", "T", "W", "T", "F", "S"};
        for (String d : daysOfWeek) {
            TextView dTv = new TextView(context);
            dTv.setText(d);
            dTv.setTextSize(12f);
            dTv.setTextColor(Color.parseColor("#8E8E93"));
            dTv.setGravity(Gravity.CENTER);
            LayoutParams dLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mDaysOfWeekRow.addView(dTv, dLp);
        }
        addView(mDaysOfWeekRow);

        // Days Grid Container
        mGridContainer = new LinearLayout(context);
        mGridContainer.setOrientation(VERTICAL);
        addView(mGridContainer);

        // Go to Today Button
        MaterialButton closeBtn = new MaterialButton(context);
        closeBtn.setText("GO TO TODAY");
        closeBtn.setBackgroundColor(Color.parseColor("#2C2C2E"));
        closeBtn.setTextColor(Color.parseColor("#FFD600"));
        closeBtn.setOnClickListener(v -> {
            if (mHapticEngine != null) mHapticEngine.triggerClick();
            Date now = new Date();
            Calendar nowCal = Calendar.getInstance();
            nowCal.setTime(now);
            mSelectedCalendar.setTime(now);
            mPickerCalendar.setTime(now);
            mCalendarViewLevel = 0;
            updateGrid();

            if (mListener != null) {
                mListener.onDateSelected(
                        nowCal.get(Calendar.YEAR),
                        nowCal.get(Calendar.MONTH),
                        nowCal.get(Calendar.DAY_OF_MONTH)
                );
            }
            dismissDialog();
        });
        addView(closeBtn);

        prevBtn.setOnClickListener(v -> {
            if (mHapticEngine != null) mHapticEngine.triggerClick();
            if (mCalendarViewLevel == 2) {
                mPickerCalendar.add(Calendar.YEAR, -12);
            } else if (mCalendarViewLevel == 1) {
                mPickerCalendar.add(Calendar.YEAR, -1);
            } else {
                mPickerCalendar.add(Calendar.MONTH, -1);
            }
            updateGrid();
        });

        nextBtn.setOnClickListener(v -> {
            if (mHapticEngine != null) mHapticEngine.triggerClick();
            if (mCalendarViewLevel == 2) {
                mPickerCalendar.add(Calendar.YEAR, 12);
            } else if (mCalendarViewLevel == 1) {
                mPickerCalendar.add(Calendar.YEAR, 1);
            } else {
                mPickerCalendar.add(Calendar.MONTH, 1);
            }
            updateGrid();
        });

        closeXBtn.setOnClickListener(v -> {
            if (mHapticEngine != null) mHapticEngine.triggerClick();
            dismissDialog();
        });

        updateGrid();
    }

    private void updateGrid() {
        if (mMonthTitle == null || mGridContainer == null) return;
        DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        float density = dm.density;

        mGridContainer.removeAllViews();

        if (mCalendarViewLevel == 2) {
            // --- LEVEL 2: YEARS GRID ---
            int baseYear = mPickerCalendar.get(Calendar.YEAR);
            int startYear = baseYear - 6;
            int endYear = startYear + 11;
            mMonthTitle.setText(startYear + " - " + endYear);
            if (mDaysOfWeekRow != null) mDaysOfWeekRow.setVisibility(GONE);

            for (int row = 0; row < 3; row++) {
                LinearLayout rowLayout = new LinearLayout(getContext());
                rowLayout.setOrientation(HORIZONTAL);
                rowLayout.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

                for (int col = 0; col < 4; col++) {
                    int yearVal = startYear + (row * 4 + col);
                    boolean isSelYear = (yearVal == mSelectedCalendar.get(Calendar.YEAR));

                    TextView yearCell = new TextView(getContext());
                    yearCell.setText(String.valueOf(yearVal));
                    yearCell.setTextSize(14f);
                    yearCell.setGravity(Gravity.CENTER);
                    yearCell.setTypeface(null, Typeface.BOLD);

                    int cellHeight = (int) (44 * density);
                    LayoutParams cellLp = new LayoutParams(0, cellHeight, 1f);
                    cellLp.setMargins((int) (4 * density), 0, (int) (4 * density), 0);
                    yearCell.setLayoutParams(cellLp);

                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(16 * density);
                    if (isSelYear) {
                        bg.setColor(Color.parseColor("#4F80FF"));
                        yearCell.setTextColor(Color.WHITE);
                    } else {
                        bg.setColor(Color.parseColor("#1C1C1E"));
                        yearCell.setTextColor(Color.parseColor("#E5E5EA"));
                    }
                    yearCell.setBackground(bg);

                    yearCell.setOnClickListener(yv -> {
                        if (mHapticEngine != null) mHapticEngine.triggerClick();
                        mPickerCalendar.set(Calendar.YEAR, yearVal);
                        mSelectedCalendar.set(Calendar.YEAR, yearVal);
                        mCalendarViewLevel = 1; // Jump down to Months Grid
                        updateGrid();
                    });

                    rowLayout.addView(yearCell);
                }
                mGridContainer.addView(rowLayout);
            }
        } else if (mCalendarViewLevel == 1) {
            // --- LEVEL 1: MONTHS GRID ---
            mMonthTitle.setText(String.valueOf(mPickerCalendar.get(Calendar.YEAR)));
            if (mDaysOfWeekRow != null) mDaysOfWeekRow.setVisibility(GONE);

            String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            int curMonth = mPickerCalendar.get(Calendar.MONTH);

            for (int row = 0; row < 3; row++) {
                LinearLayout rowLayout = new LinearLayout(getContext());
                rowLayout.setOrientation(HORIZONTAL);
                rowLayout.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

                for (int col = 0; col < 4; col++) {
                    int mIndex = row * 4 + col;
                    boolean isSelMonth = (mIndex == curMonth && mPickerCalendar.get(Calendar.YEAR) == mSelectedCalendar.get(Calendar.YEAR));

                    TextView monthCell = new TextView(getContext());
                    monthCell.setText(months[mIndex]);
                    monthCell.setTextSize(14f);
                    monthCell.setGravity(Gravity.CENTER);
                    monthCell.setTypeface(null, Typeface.BOLD);

                    int cellHeight = (int) (44 * density);
                    LayoutParams cellLp = new LayoutParams(0, cellHeight, 1f);
                    cellLp.setMargins((int) (4 * density), 0, (int) (4 * density), 0);
                    monthCell.setLayoutParams(cellLp);

                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(16 * density);
                    if (isSelMonth) {
                        bg.setColor(Color.parseColor("#4F80FF"));
                        monthCell.setTextColor(Color.WHITE);
                    } else {
                        bg.setColor(Color.parseColor("#1C1C1E"));
                        monthCell.setTextColor(Color.parseColor("#E5E5EA"));
                    }
                    monthCell.setBackground(bg);

                    monthCell.setOnClickListener(mv -> {
                        if (mHapticEngine != null) mHapticEngine.triggerClick();
                        mPickerCalendar.set(Calendar.MONTH, mIndex);
                        mSelectedCalendar.set(Calendar.YEAR, mPickerCalendar.get(Calendar.YEAR));
                        mSelectedCalendar.set(Calendar.MONTH, mIndex);
                        mCalendarViewLevel = 0; // Jump down to Days Grid
                        updateGrid();
                    });

                    rowLayout.addView(monthCell);
                }
                mGridContainer.addView(rowLayout);
            }
        } else {
            // --- LEVEL 0: DAYS GRID ---
            if (mDaysOfWeekRow != null) mDaysOfWeekRow.setVisibility(VISIBLE);
            SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
            mMonthTitle.setText(monthFmt.format(mPickerCalendar.getTime()));

            Calendar calIter = (Calendar) mPickerCalendar.clone();
            calIter.set(Calendar.DAY_OF_MONTH, 1);
            int firstDayOfWeek = calIter.get(Calendar.DAY_OF_WEEK) - 1;

            calIter.add(Calendar.DAY_OF_MONTH, -firstDayOfWeek);

            for (int row = 0; row < 6; row++) {
                LinearLayout rowLayout = new LinearLayout(getContext());
                rowLayout.setOrientation(HORIZONTAL);
                rowLayout.setPadding(0, (int) (4 * density), 0, (int) (4 * density));

                for (int col = 0; col < 7; col++) {
                    final Calendar cellCal = (Calendar) calIter.clone();
                    boolean isSelected = isSameDay(cellCal, mSelectedCalendar);
                    boolean isCurrentMonth = cellCal.get(Calendar.MONTH) == mPickerCalendar.get(Calendar.MONTH);
                    boolean isToday = isSameDay(cellCal, Calendar.getInstance());

                    TextView dayCell = new TextView(getContext());
                    dayCell.setText(String.valueOf(cellCal.get(Calendar.DAY_OF_MONTH)));
                    dayCell.setTextSize(14f);
                    dayCell.setGravity(Gravity.CENTER);

                    int cellHeight = (int) (36 * density);
                    LayoutParams cellLp = new LayoutParams(0, cellHeight, 1f);
                    dayCell.setLayoutParams(cellLp);

                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(18 * density);
                    if (isSelected) {
                        bg.setColor(Color.parseColor("#4F80FF"));
                        dayCell.setTextColor(Color.WHITE);
                        dayCell.setTypeface(null, Typeface.BOLD);
                    } else if (isToday) {
                        bg.setStroke((int) (2 * density), Color.parseColor("#FFD600"));
                        dayCell.setTextColor(Color.parseColor("#FFD600"));
                        dayCell.setTypeface(null, Typeface.BOLD);
                    } else {
                        bg.setColor(Color.TRANSPARENT);
                        dayCell.setTextColor(isCurrentMonth ? Color.WHITE : Color.parseColor("#48484A"));
                    }
                    dayCell.setBackground(bg);

                    dayCell.setOnClickListener(v -> {
                        if (mHapticEngine != null) mHapticEngine.triggerClick();
                        mSelectedCalendar.setTime(cellCal.getTime());
                        mCalendarViewLevel = 0;
                        updateGrid();

                        if (mListener != null) {
                            mListener.onDateSelected(
                                    cellCal.get(Calendar.YEAR),
                                    cellCal.get(Calendar.MONTH),
                                    cellCal.get(Calendar.DAY_OF_MONTH)
                            );
                        }
                        dismissDialog();
                    });

                    rowLayout.addView(dayCell);
                    calIter.add(Calendar.DAY_OF_MONTH, 1);
                }
                mGridContainer.addView(rowLayout);
                if (row > 3 && calIter.get(Calendar.MONTH) != mPickerCalendar.get(Calendar.MONTH)) {
                    break;
                }
            }
        }
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) return false;
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
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
     * Helper method to present this DatePickerDialog inside a glassmorphic dialog.
     */
    public void showDialog(Context context, Calendar initialCal, OnDateSelectedListener listener) {
        if (!(context instanceof Activity)) return;
        Activity activity = (Activity) context;

        setCalendar(initialCal);
        setOnDateSelectedListener(listener);

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
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
