package xime.ui.layout;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import xime.R;

public class CycleLayout extends RecyclerView {

    private int mGravity = android.view.Gravity.TOP | android.view.Gravity.START;

    public CycleLayout(@NonNull Context context) {
        super(context);
        init(context, null, 0);
    }

    public CycleLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public CycleLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        setOverScrollMode(OVER_SCROLL_NEVER);
        // Force zero elevation as per XIME standards
        setElevation(0);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Layout, defStyleAttr, 0);
            mGravity = a.getInt(R.styleable.Layout_xoerisGravity, android.view.Gravity.TOP | android.view.Gravity.START);
            a.recycle();
        }
    }

    public void setGravity(int gravity) {
        if (mGravity != gravity) {
            mGravity = gravity;
            requestLayout();
        }
    }

    public int getGravity() {
        return mGravity;
    }
}

