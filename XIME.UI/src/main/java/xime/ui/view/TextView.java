package xime.ui.view;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

public class TextView extends AppCompatTextView {
    public TextView(Context context) {
        super(context);
        init();
    }

    public TextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.MARQUEE);
        setMarqueeRepeatLimit(-1);
        setSelected(true);
    }

    @Override
    public boolean isFocused() {
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
