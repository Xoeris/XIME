package xime.ui.dialog;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import xime.ui.layout.Layout;

import xime.ui.menu.HeaderMenu;

public class EdgeDialog extends Layout {

    public EdgeDialog(@NonNull Context context) {
        super(context);
    }

    public EdgeDialog(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public EdgeDialog(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void open() {}
    public void close() {}
    public void linkToHeaderMenu(@NonNull HeaderMenu header) {}
}
