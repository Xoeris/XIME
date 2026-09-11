package xime.media.utils;

import android.animation.ValueAnimator;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Build;
import android.util.Log;
import android.view.Choreographer;

import androidx.annotation.RequiresApi;

import xime.media.ui.VideoView;

import java.nio.ByteBuffer;

/**
 * Video Interpolation Lucine Core v13.0 - Motion-Compensated Frame Flow.
 *
 * Drives the per-VSYNC alpha ramp between hardware frames and asks the
 * native layer to warp the draw mesh using real block-matching motion
 * vectors (see lucine-interpolator.c / VideoView.java) instead of a fixed
 * global displacement. If no motion field is available yet (first frame
 * after a seek/activity change, or before two captured frames exist), the
 * warp meshes degrade to identity and playback just shows a plain
 * cross-dissolve rather than a bogus warp.
 */
public class VideoInterpolation implements Choreographer.FrameCallback {

    static {
        System.loadLibrary("lucine-interpolator");
    }

    private MediaPlayer mediaPlayer;
    private xime.media.ui.VideoView videoBase;

    private boolean isSlowMotionActive = false;
    private float currentSpeed = 1.0f;
    private float targetSpeed = 1.0f;
    private float startSpeed = 1.0f;
    private boolean pitchSemiToneControl = true;
    private boolean aiInterpolationEnabled = true;

    private ValueAnimator rampAnimator;
    private static final int RAMP_DURATION_MS = 1000;

    private static final int MESH_WIDTH = 12;
    private static final int MESH_HEIGHT = 12;
    private static final int MESH_ARRAY_SIZE = (MESH_WIDTH + 1) * (MESH_HEIGHT + 1) * 2;

    private final float[] mPrevMesh = new float[MESH_ARRAY_SIZE];
    private final float[] mCurrMesh = new float[MESH_ARRAY_SIZE];

    public VideoInterpolation() {
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void attachMediaPlayer(MediaPlayer mp) {
        this.mediaPlayer = mp;
        if (currentSpeed != 1.0f) {
            applySpeedImmediate(currentSpeed);
        }
    }

    public void attachVideoBase(VideoView vb) {
        this.videoBase = vb;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        boolean active = false;
        try {
            active = aiInterpolationEnabled && videoBase != null && videoBase.isPlaying();
        } catch (Exception ignored) {}

        // Interpolate at ANY playback speed, not just slow-mo. The source
        // video's native decode rate (24/25/30fps, measured via
        // videoBase.getFrameDurationNanos() from actual hardware frame
        // arrivals) is almost always below the display's refresh rate, so
        // synthesizing in-between frames matters just as much at 1.0x as it
        // does below it.
        if (active) {
            long lastFrameNanos = videoBase.getLastHardwareFrameNanos();
            long frameDurationNanos = videoBase.getFrameDurationNanos();

            float alpha = 0.0f;
            if (lastFrameNanos != 0 && frameDurationNanos > 0) {
                long elapsed = frameTimeNanos - lastFrameNanos;
                alpha = (float) elapsed / frameDurationNanos;
                if (alpha > 1.0f) alpha = 1.0f;
            }

            // If the source is already delivering frames faster than we're
            // being asked to draw them (e.g. a 60fps source on a 60Hz
            // display), there's nothing to interpolate, skip the warp so we
            // don't burn cycles mesh-warping a near-zero motion field.
            boolean sourceNeedsUpsampling = frameDurationNanos > (long) (1_000_000_000L / 50);
            if (!sourceNeedsUpsampling) {
                videoBase.setInterpolationState(0, null, null, false);
                Choreographer.getInstance().postFrameCallback(this);
                return;
            }

            int dw = videoBase.getNeuralWidth();
            int dh = videoBase.getNeuralHeight();

            // Only warp once a real motion field exists between two captured
            // frames. Otherwise show a plain (unwarped) frame to avoid
            // mesh-warping against garbage/zeroed motion vectors.
            if (dw > 0 && dh > 0 && videoBase.hasMotionField()) {
                nativeFillFlowMeshFromMotion(MESH_WIDTH, MESH_HEIGHT, dw, dh, alpha, 1.0f, mPrevMesh);
                nativeFillFlowMeshFromMotion(MESH_WIDTH, MESH_HEIGHT, dw, dh, (1.0f - alpha), -1.0f, mCurrMesh);
                videoBase.setInterpolationState(alpha, mPrevMesh, mCurrMesh, true);
            } else {
                videoBase.setInterpolationState(0, null, null, false);
            }
        } else if (videoBase != null) {
            videoBase.setInterpolationState(0, null, null, false);
        }

        Choreographer.getInstance().postFrameCallback(this);
    }

    public boolean isAiInterpolationEnabled() {
        return aiInterpolationEnabled;
    }

    public void setAIInterpolationEnabled(boolean enabled) {
        this.aiInterpolationEnabled = enabled;
        Log.d("LucineAI", "Motion-compensated interpolation: " + (enabled ? "enabled" : "disabled"));
    }

    public void toggleSlowMotion() {
        setSpeed(isSlowMotionActive ? 1.0f : 0.5f, true);
    }

    public void setSpeed(float speed, boolean smooth) {
        this.targetSpeed = speed;
        this.startSpeed = currentSpeed;
        this.isSlowMotionActive = speed < 1.0f;

        if (smooth) {
            startNeuralRamp();
        } else {
            this.currentSpeed = speed;
            applySpeedImmediate(speed);
        }
    }

    private void startNeuralRamp() {
        if (rampAnimator != null) rampAnimator.cancel();

        rampAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        rampAnimator.setDuration(RAMP_DURATION_MS);
        rampAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            currentSpeed = nativeInterpolate(startSpeed, targetSpeed, fraction);
            applySpeedImmediate(currentSpeed);
        });
        rampAnimator.start();
    }

    private void applySpeedImmediate(float speed) {
        if (mediaPlayer == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            applyHardwareSpeed(speed);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void applyHardwareSpeed(float speed) {
        try {
            PlaybackParams params = mediaPlayer.getPlaybackParams();
            params.setSpeed(speed);
            params.setPitch(pitchSemiToneControl ? 1.0f : speed);
            mediaPlayer.setPlaybackParams(params);
        } catch (Exception e) {
            // Hardware surrendered.
        }
    }

    public void setPitchCorrection(boolean enabled) {
        this.pitchSemiToneControl = enabled;
        if (mediaPlayer != null) applySpeedImmediate(currentSpeed);
    }

    // --- Native bridge ---
    // Ease curve for the speed ramp animator (smootherstep).
    private native float nativeInterpolate(float start, float target, float fraction);

    // Block-matching motion estimation between two captured RGB_565 frames.
    // Called from VideoView's background capture thread, never from doFrame.
    public static native void nativeComputeMotionField(ByteBuffer prevBuffer, ByteBuffer currBuffer, int width, int height);

    // Builds a warp mesh by bilinearly sampling the motion field computed above.
    private native void nativeFillFlowMeshFromMotion(int meshW, int meshH, float w, float h, float alpha, float direction, float[] outMesh);

    // Legacy alias kept for any external callers; delegates to the real path.
    private native void nativeFillFlowMesh(int meshW, int meshH, float w, float h, float alpha, float direction, float[] outMesh);
}

