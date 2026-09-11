package xime.ui.common;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import com.google.android.material.button.MaterialButton;
import xime.R;
import xime.haptic.HapticEngine;

public class Button extends MaterialButton {
    private HapticEngine HapticEngine;

    public Button(Context context) {
        this(context, null);
    }

    public Button(Context context, AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialButtonStyle);
    }

    public Button(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        HapticEngine = new HapticEngine(context);
        // Disable system haptics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
            setHapticFeedbackEnabled(false);
        }

        // Ensure high contrast on primary background
        setTextColor(android.graphics.Color.BLACK);
        setIconTint(android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK));

        if (attrs != null) {
            android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, xime.haptic.R.styleable.Trigger, defStyleAttr, 0);
            String text = a.getString(xime.haptic.R.styleable.Trigger_xoerisText);
            if (text != null) setText(text);
        
            int iconRes = a.getResourceId(xime.haptic.R.styleable.Trigger_xoerisTriggerIcon, 0);
            if (iconRes != 0) {
                setIconResource(iconRes);
                setIconTintResource(android.R.color.black);
            }
        
            a.recycle();
        }
    }

    @Override
    public boolean performClick() {
        if (HapticEngine != null) {
            HapticEngine.onTap();
        }
        return super.performClick();
    }
}

