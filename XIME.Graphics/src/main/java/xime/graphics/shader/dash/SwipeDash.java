package xime.graphics.shader.dash;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

public class SwipeDash {
    private final View target;
    private final Paint paint;
    private float progress = 0f;
    private boolean isAnimating = false;
    private boolean directionDown = true;

    public SwipeDash(View target) {
        this.target = target;
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setStyle(Paint.Style.FILL);
    }

    public void start(boolean down, int color) {
        this.directionDown = down;
        this.paint.setColor(color);
        this.progress = 0f;
        this.isAnimating = true;
        animate();
    }

    private void animate() {
        if (!isAnimating) return;
        
        target.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                progress += 0.08f;
                if (progress >= 1.0f) {
                    progress = 0f;
                    isAnimating = false;
                } else {
                    target.invalidate();
                    animate();
                }
            }
        });
    }

    public void draw(Canvas canvas) {
        if (!isAnimating) return;

        int w = canvas.getWidth();
        int h = canvas.getHeight();
        
        float centerX = w / 2f;
        float centerY = h / 2f;
        
        // Swoosh logic: A stretching oval that moves vertically
        float swooshHeight = h * 0.4f * (1.0f - progress);
        float swooshWidth = w * 0.2f * (1.0f - progress);
        
        float yPos;
        if (directionDown) {
            yPos = centerY + (h * 0.5f * progress);
        } else {
            yPos = centerY - (h * 0.5f * progress);
        }

        paint.setAlpha((int) (150 * (1.0f - progress)));
        
        Path path = new Path();
        path.addOval(centerX - swooshWidth, yPos - swooshHeight, centerX + swooshWidth, yPos + swooshHeight, Path.Direction.CW);
        canvas.drawPath(path, paint);
    }
}
