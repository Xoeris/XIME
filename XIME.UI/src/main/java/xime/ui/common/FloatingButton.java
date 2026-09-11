package xime.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import xime.haptic.HapticEngine;

public class FloatingButton extends FloatingActionButton {
    private HapticEngine HapticEngine;

    public FloatingButton(Context context) {
        super(context);
        init(context);
    }

    public FloatingButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FloatingButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        HapticEngine = new HapticEngine(context);
    }

    @Override
    public boolean performClick() {
        if (HapticEngine != null) {
            HapticEngine.onTap();
        }
        return super.performClick();
    }
}

