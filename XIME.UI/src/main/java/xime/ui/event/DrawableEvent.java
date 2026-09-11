package xime.ui.event;

import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;

public interface DrawableEvent {

    void invalidateDrawable(@NonNull Drawable who);

    void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when);

    void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what);
}
