package xime.ui.layout;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LayerLayout extends Layout {
    private int layerDepth = 0;
    private boolean isBlurExcluded = false;

    public LayerLayout(@NonNull Context context) {
        super(context);
    }

    public LayerLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LayerLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LayerLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
    }

    public void setLayerDepth(int depth) {
        this.layerDepth = depth;
    }

    public int getLayerDepth() {
        return this.layerDepth;
    }

    public void setBlurExcluded(boolean exclude) {
        this.isBlurExcluded = exclude;
    }

    public boolean isBlurExcluded() {
        return this.isBlurExcluded;
    }
}
