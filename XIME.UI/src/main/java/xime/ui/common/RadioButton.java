package xime.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import xime.R;

public class RadioButton extends xime.ui.layout.LinearLayout {
    private OnCheckedChangeListener listener;
    private int checkedId = -1;
    private final List<Chip> chips = new ArrayList<>();

    public interface OnCheckedChangeListener {
        void onCheckedChanged(RadioButton group, int checkedId);
    }

    public RadioButton(@NonNull Context context) {
        this(context, null);
    }

    public RadioButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadioButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(LinearLayout.HORIZONTAL);
    }

    public void addOption(String text, int id) {
        Chip chip = new Chip(getContext());
        chip.setText(text);
        chip.setId(id);
        chip.setCheckable(true);
        chip.setOnClickListener(v -> check(id));
        
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        if (!chips.isEmpty()) {
            lp.leftMargin = (int) (getContext().getResources().getDisplayMetrics().density * 8.0f);
        }
        chip.setLayoutParams(lp);
        
        chips.add(chip);
        addView(chip);
        updateChipStyles();
    }

    public void check(int id) {
        if (this.checkedId == id) return;
        this.checkedId = id;
        for (Chip chip : chips) {
            chip.setChecked(chip.getId() == id);
        }
        updateChipStyles();
        if (listener != null) {
            listener.onCheckedChanged(this, id);
        }
    }

    private void updateChipStyles() {
        for (Chip chip : chips) {
            if (chip.isChecked()) {
                chip.setChipBackgroundColorResource(R.color.xoeris_primary);
                chip.setTextColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK));
                chip.setChipIconTintResource(android.R.color.black);
                chip.setChipStrokeWidth(0);
            }
 else {
                chip.setChipBackgroundColorResource(android.R.color.transparent);
                chip.setTextColor(getContext().getColor(R.color.xoeris_text_secondary));
                chip.setChipStrokeWidth(getContext().getResources().getDisplayMetrics().density);
                chip.setChipStrokeColorResource(R.color.xoeris_text_secondary);
            }
        }
    }

    public int getCheckedId() {
        return checkedId;
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }
}
