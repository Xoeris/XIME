package xime.ui.common;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import xime.haptic.HapticEngine;
import xime.ui.view.ImageView;

public class IconButton extends ImageView {
    private HapticEngine HapticEngine;

    public IconButton(Context context) {
        super(context);
        init(context);
    }

    public IconButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public IconButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        this.HapticEngine = new HapticEngine(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
            setHapticFeedbackEnabled(false);
        }
    }

    @Override
    public boolean performClick() {
        if (this.HapticEngine != null) {
            this.HapticEngine.onTap();
        }
        return super.performClick();
    }

    @Override
    public boolean performLongClick() {
        if (this.HapticEngine != null) {
            this.HapticEngine.onLongPress();
        }
        return super.performLongClick();
    }
}
