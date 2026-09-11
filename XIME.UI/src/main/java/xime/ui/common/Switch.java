package xime.ui.common;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import xime.ui.event.ParentEvent;
import xime.haptic.HapticEngine;

public class Switch extends SwitchCompat {
    private HapticEngine HapticEngine;
    private ParentEvent mBaseParent;

    public Switch(@NonNull Context context) {
        super(context);
        init(context);
    }

    public Switch(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public Switch(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        HapticEngine = new HapticEngine(context);
        setClickable(true);
        setFocusable(true);
    }

    public void assignParent(ParentEvent parent) {
        this.mBaseParent = parent;
    }

    public ParentEvent getBaseParent() {
        return mBaseParent;
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        if (mBaseParent != null) {
            mBaseParent.requestLayout();
        }
    }

    @Override
    public void invalidate() {
        if (mBaseParent != null) {
            mBaseParent.invalidateChild(null, null);
        } else {
            super.invalidate();
        }
    }

    @Override
    public void setChecked(boolean checked) {
        boolean changed = checked != isChecked();
        super.setChecked(checked);
        if (changed && HapticEngine != null) {
            HapticEngine.onTap();
        }
    }
}
