package xime.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xime.ui.layout.BlurLayout;

public class BlurView extends BlurLayout {
    public BlurView(@NonNull Context context) {
        this(context, null);
    }

    public BlurView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BlurView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
}

