package xime.ui.utils;

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class Views {

    @IntDef(value = {SCROLL_AXIS_NONE, SCROLL_AXIS_HORIZONTAL, SCROLL_AXIS_VERTICAL}, flag = true)
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScrollAxis {}

    public static final int SCROLL_AXIS_NONE = 0;
    public static final int SCROLL_AXIS_HORIZONTAL = 1;
    public static final int SCROLL_AXIS_VERTICAL = 1 << 1;

    @IntDef({TYPE_TOUCH, TYPE_NON_TOUCH})
    @Retention(RetentionPolicy.SOURCE)
    public @interface NestedScrollType {}

    public static final int TYPE_TOUCH = 0;
    public static final int TYPE_NON_TOUCH = 1;

    private Views() {}
}
