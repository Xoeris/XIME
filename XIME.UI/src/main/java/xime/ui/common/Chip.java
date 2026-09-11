package xime.ui.common;

import android.content.Context;
import android.util.AttributeSet;

public class Chip extends com.google.android.material.chip.Chip {
    public Chip(Context context) {
        super(context);
        init();
    }

    public Chip(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Chip(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Material Chips do not support Marquee (throws UnsupportedOperationException)
        // We'll stick to standard end-truncation for Chips.
        setSingleLine(true);
        setEllipsize(android.text.TextUtils.TruncateAt.END);
        
        // Ensure default styling for Musify if context matches
        // Note: This is a bit of a hack for cross-module theme support
        int textColorResId = getContext().getResources().getIdentifier("chip_text_color", "color", getContext().getPackageName());
        if (textColorResId != 0) {
            setTextColor(androidx.core.content.ContextCompat.getColorStateList(getContext(), textColorResId));
        }
        
        int bgResId = getContext().getResources().getIdentifier("chip_background_color", "color", getContext().getPackageName());
        if (bgResId != 0) {
            setChipBackgroundColorResource(bgResId);
        }
    }

    @Override
    public boolean isFocused() {
        return super.isFocused();
    }
}

