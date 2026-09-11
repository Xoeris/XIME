package xime.system.optimization.zenith;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

public final class GPUClippingZenith {
    private GPUClippingZenith() {}

    public static void clipCanvasToView(Canvas canvas, View view) {
        if (canvas == null || view == null) return;
        Rect clipRect = new Rect(0, 0, view.getWidth(), view.getHeight());
        canvas.clipRect(clipRect);
    }

    public static void enableHardwareLayer(View view) {
        if (view == null) return;
        if (view.getLayerType() != View.LAYER_TYPE_HARDWARE) {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
    }
}
