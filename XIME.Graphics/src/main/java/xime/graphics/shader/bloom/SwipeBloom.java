package xime.graphics.shader.bloom;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

public class SwipeBloom {
    private final View target;
    private final Paint paint;
    private final Paint glowPaint;
    private float progress = 0f;
    private float progressStep = 0.05f;
    private boolean isAnimating = false;
    private boolean isHorizontal = false;
    private boolean isForward = true;
    private float sizeMultiplier = 1.0f;
    
    private float startCoord = 0f;
    private float endCoord = 0f;
    private int baseColor;
    
    private static final float PI = (float) Math.PI;
    
    private final Runnable animator = new Runnable() {
        @Override
        public void run() {
            if (!isAnimating) return;
            progress += progressStep;
            if (progress >= 1.0f) {
                progress = 0f;
                isAnimating = false;
                target.invalidate();
            } else {
                target.invalidate();
                target.postOnAnimation(this);
            }
        }
    };

    public SwipeBloom(View target) {
        this.target = target;
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setStyle(Paint.Style.FILL);
        
        this.glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.glowPaint.setStyle(Paint.Style.FILL);
        
        this.target.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void start(boolean forward, int color, boolean horizontal, float sizeMultiplier) {
        start(forward, color, horizontal, sizeMultiplier, 0, 0);
    }

    public void start(boolean forward, int color, boolean horizontal, float sizeMultiplier, float startCoord, float endCoord) {
        this.isForward = forward;
        this.isHorizontal = horizontal;
        this.sizeMultiplier = sizeMultiplier;
        this.startCoord = startCoord;
        this.endCoord = endCoord;
        this.baseColor = color;
        this.paint.setColor(color);
        this.glowPaint.setColor(color);
        
        // Soft glow for the edges
        this.glowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, 22f * sizeMultiplier), BlurMaskFilter.Blur.NORMAL));
        // Soften the core too to make it a "fade shape"
        this.paint.setMaskFilter(new BlurMaskFilter(Math.max(1f, 8f * sizeMultiplier), BlurMaskFilter.Blur.NORMAL));
        
        if (horizontal) {
            float distance = Math.abs(endCoord - startCoord);
            float baseFrames = 10f; 
            float distanceFactor = distance / Math.max(1, target.getWidth());
            float totalFrames = baseFrames + (14f * distanceFactor);
            this.progressStep = 1.0f / totalFrames;
        } else {
            this.progressStep = 0.08f; 
        }
        
        if (!isAnimating) {
            this.progress = 0f;
            this.isAnimating = true;
            target.postOnAnimation(animator);
        } else {
            this.progress = 0f;
        }
    }

    public void draw(Canvas canvas) {
        if (!isAnimating) return;

        final int w = canvas.getWidth();
        final int h = canvas.getHeight();
        
        final float bellCurve = (float) Math.sin(progress * PI);
        final int alpha = (int) (230 * bellCurve); 
        
        paint.setAlpha(alpha);
        glowPaint.setAlpha(alpha / 2);

        if (isHorizontal) {
            final float centerY = h / 2f;
            final float currentX = startCoord + (endCoord - startCoord) * progress;
            
            final float bubbleBaseSize = h * 0.48f;
            final float bubbleSize = (bubbleBaseSize + (h * 0.12f * bellCurve)) * sizeMultiplier;
            
            final float stretch = 1.0f + (0.5f * bellCurve); 
            final float halfWidth = (bubbleSize * stretch) * 0.5f;
            final float halfHeight = (bubbleSize / stretch) * 0.5f;

            // Draw with gradients for a "fade" feel
            int transparentColor = Color.argb(0, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
            RadialGradient gradient = new RadialGradient(currentX, centerY, halfWidth * 1.2f, 
                    new int[]{baseColor, transparentColor}, null, Shader.TileMode.CLAMP);
            
            paint.setShader(gradient);
            paint.setAlpha(alpha);

            canvas.drawOval(currentX - halfWidth, centerY - halfHeight, 
                    currentX + halfWidth, centerY + halfHeight, paint);
            
            paint.setShader(null); // Clean up
        } else {
            final float centerX = w / 2f;
            final float streakWidth = w * 0.08f * (1.0f - (progress * 0.4f)) * sizeMultiplier;
            final float streakHeight = h * 1.3f * (1.0f - (progress * 0.3f)) * sizeMultiplier;
            
            float startY;
            if (isForward) {
                startY = h * 1.6f * progress - (h * 0.3f);
            } else {
                startY = h * (1.0f - (1.6f * progress)) + (h * 0.3f);
            }
            
            final float top = isForward ? startY - streakHeight : startY;
            final float bottom = isForward ? startY : startY + streakHeight;

            int transparentColor = Color.argb(0, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
            LinearGradient gradient = new LinearGradient(centerX, top, centerX, bottom,
                    isForward ? new int[]{transparentColor, baseColor} : new int[]{baseColor, transparentColor}, 
                    null, Shader.TileMode.CLAMP);
            
            paint.setShader(gradient);
            paint.setAlpha(alpha);

            canvas.drawRoundRect(centerX - streakWidth * 0.5f, top, 
                    centerX + streakWidth * 0.5f, bottom, 
                    streakWidth * 0.5f, streakWidth * 0.5f, paint);
            
            paint.setShader(null);
        }
    }
}
