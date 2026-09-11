package xime.media.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.MediaController;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import xime.ui.utils.RifeFrameInterpolator;
import xime.media.utils.VideoInterpolation;

/**
 * VideoView v16.1 - High-performance rendering engine with interpolation support.
 */
public class VideoView extends FrameLayout implements TextureView.SurfaceTextureListener, MediaController.MediaPlayerControl {

    private static final String TAG = "VideoView";
    private TextureView mTextureView;
    private MediaPlayer mMediaPlayer;
    private Uri mUri;
    private Surface mSurface;
    private MediaPlayer.OnPreparedListener mOnPreparedListener;

    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;

    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;

    private int mVideoWidth;
    private int mVideoHeight;

    private Bitmap mPrevFrame;
    private Bitmap mCurrFrame;
    private Bitmap mCaptureBuffer;
    private ByteBuffer mPrevPixelBuffer;
    private ByteBuffer mCurrPixelBuffer;

    private final Paint mNeuralPaint = new Paint();
    private float[] mPrevWarpMesh;
    private float[] mCurrWarpMesh;
    private float mInterpolationAlpha = 0.0f;
    private boolean mIsInterpolating = false;
    private boolean mHasMotionField = false;

    private static final int MESH_WIDTH = 12;
    private static final int MESH_HEIGHT = 12;
    private static final int INTERPOLATION_HEIGHT = 240;

    private RifeFrameInterpolator mRifeInterpolator;
    private ExecutorService mInferenceExecutor;
    private final AtomicBoolean mInferenceInFlight = new AtomicBoolean(false);
    private volatile Bitmap mMidFrame;
    private volatile long mMidFrameGeneration = -1;
    private long mFrameGeneration = 0;
    private final Rect mDstRect = new Rect();

    private HandlerThread mCaptureThread;
    private Handler mCaptureHandler;
    private volatile boolean mIsCapturing = false;

    private long mLastHardwareFrameNanos = 0;
    private long mFrameDurationNanos = 33333333;

    private final android.graphics.Matrix mTransformMatrix = new android.graphics.Matrix();
    private final android.graphics.Matrix mBaseMatrix = new android.graphics.Matrix();
    private boolean mIsLooping = true;
    private float mPendingSpeed = 1.0f;

    public VideoView(@NonNull Context context) { 
        super(context); 
        init(context); 
    }
    
    public VideoView(@NonNull Context context, @Nullable AttributeSet attrs) { 
        super(context, attrs); 
        init(context); 
    }
    
    public VideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) { 
        super(context, attrs, defStyleAttr); 
        init(context); 
    }

    private void init(Context context) {
        mTextureView = new TextureView(context);
        mTextureView.setSurfaceTextureListener(this);
        addView(mTextureView, new LayoutParams(-1, -1, android.view.Gravity.CENTER));

        mNeuralPaint.setFilterBitmap(true);
        mNeuralPaint.setAntiAlias(false);
        mNeuralPaint.setDither(false);

        setBackgroundColor(android.graphics.Color.BLACK);
        setWillNotDraw(true);

        setClickable(false);
        setFocusable(false);
    }

    private void ensureInterpolationResources() {
        if (mCaptureThread == null) {
            mCaptureThread = new HandlerThread("LucineCapture");
            mCaptureThread.start();
            mCaptureHandler = new Handler(mCaptureThread.getLooper());
        }
        if (mInferenceExecutor == null) {
            mInferenceExecutor = Executors.newSingleThreadExecutor();
        }
        setWillNotDraw(false);
    }

    public void setVideoTransform(@Nullable android.graphics.Matrix matrix) {
        if (matrix == null) {
            mTransformMatrix.reset();
        } else {
            mTransformMatrix.set(matrix);
        }
        updateFinalTransform();
    }

    private void updateFinalTransform() {
        if (mTextureView == null) return;
        android.graphics.Matrix finalMatrix = new android.graphics.Matrix(mBaseMatrix);
        if (!mTransformMatrix.isIdentity()) {
            finalMatrix.postConcat(mTransformMatrix);
        }
        mTextureView.setTransform(finalMatrix);
        invalidate();
    }

    public void setPlaybackSpeed(float speed) {
        mPendingSpeed = speed;
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.setPlaybackParams(mMediaPlayer.getPlaybackParams().setSpeed(speed));
            } catch (Exception e) {
                Log.e(TAG, "Failed to set playback speed", e);
            }
        }
    }

    public void setLooping(boolean looping) {
        mIsLooping = looping;
        if (mMediaPlayer != null) {
            mMediaPlayer.setLooping(looping);
        }
    }

    public void initNeuralInterpolator(Context context, String modelAssetPath) {
        ensureInterpolationResources();
        Context appContext = context.getApplicationContext();
        mInferenceExecutor.execute(() -> {
            try {
                RifeFrameInterpolator interpolator = new RifeFrameInterpolator(appContext, modelAssetPath);
                synchronized (this) {
                    mRifeInterpolator = interpolator;
                }
            } catch (Exception e) {
                Log.e(TAG, "Neural interpolator failed to load: " + modelAssetPath, e);
            }
        });
    }

    public boolean isNeuralInterpolatorReady() {
        synchronized (this) { return mRifeInterpolator != null; }
    }

    public void setInterpolationState(float alpha, float[] prevMesh, float[] currMesh, boolean active) {
        if (active) ensureInterpolationResources();
        this.mInterpolationAlpha = alpha;
        this.mPrevWarpMesh = prevMesh;
        this.mCurrWarpMesh = currMesh;
        this.mIsInterpolating = active;
        setWillNotDraw(!active);
        postInvalidateOnAnimation();
    }

    public long getLastHardwareFrameNanos() { return mLastHardwareFrameNanos; }
    public long getFrameDurationNanos() { return mFrameDurationNanos; }
    public int getNeuralWidth() { synchronized (this) { return (mCurrFrame != null) ? mCurrFrame.getWidth() : 0; } }
    public int getNeuralHeight() { synchronized (this) { return (mCurrFrame != null) ? mCurrFrame.getHeight() : 0; } }
    public boolean hasMotionField() { return mHasMotionField; }

    public void setVideoURI(Uri uri) {
        setVideoURI(uri, true);
    }

    public void setVideoURI(Uri uri, boolean autoPlay) {
        mUri = uri;
        mTargetState = autoPlay ? STATE_PLAYING : STATE_PAUSED;
        openVideo();
    }

    public void setOnPreparedListener(MediaPlayer.OnPreparedListener listener) {
        mOnPreparedListener = listener;
    }

    private void openVideo() {
        if (mUri == null || mTextureView.getSurfaceTexture() == null) return;
        release();

        try {
            mMediaPlayer = new MediaPlayer();
            if (mSurface != null) mSurface.release();
            mSurface = new Surface(mTextureView.getSurfaceTexture());
            mMediaPlayer.setSurface(mSurface);
            mMediaPlayer.setDataSource(getContext(), mUri);

            mMediaPlayer.setOnPreparedListener(mp -> {
                mCurrentState = STATE_PREPARED;
                mVideoWidth = mp.getVideoWidth();
                mVideoHeight = mp.getVideoHeight();
                adjustTextureViewSize();
                mp.setLooping(mIsLooping);
                if (mPendingSpeed != 1.0f) {
                    try {
                        mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(mPendingSpeed));
                    } catch (Exception ignored) {}
                }
                if (mOnPreparedListener != null) mOnPreparedListener.onPrepared(mp);
                if (mTargetState == STATE_PLAYING) start();
            });

            mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                mCurrentState = STATE_ERROR;
                return true;
            });

            mCurrentState = STATE_PREPARING;
            mMediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Open Video Failed", e);
            mCurrentState = STATE_ERROR;
        }
    }

    public void release() {
        mCurrentState = STATE_IDLE;
        if (mMediaPlayer != null) {
            try { mMediaPlayer.stop(); mMediaPlayer.release(); } catch (Exception ignored) {}
            mMediaPlayer = null;
        }
        synchronized (this) {
            mVideoWidth = 0;
            mVideoHeight = 0;
            mBaseMatrix.reset();
            mTransformMatrix.reset();
            if (mPrevFrame != null) { mPrevFrame.recycle(); mPrevFrame = null; }
            if (mCurrFrame != null) { mCurrFrame.recycle(); mCurrFrame = null; }
            if (mCaptureBuffer != null) { mCaptureBuffer.recycle(); mCaptureBuffer = null; }
            if (mMidFrame != null) { mMidFrame.recycle(); mMidFrame = null; }
            mMidFrameGeneration = -1;
            mPrevPixelBuffer = null;
            mCurrPixelBuffer = null;
            mHasMotionField = false;
        }
        if (mTextureView != null) {
            mTextureView.setTransform(null);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        adjustTextureViewSize();
    }

    private void adjustTextureViewSize() {
        if (mVideoWidth == 0 || mVideoHeight == 0 || mTextureView == null) return;
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        float videoAspectRatio = (float) mVideoWidth / mVideoHeight;
        float viewAspectRatio = (float) viewWidth / viewHeight;

        mBaseMatrix.reset();
        float scale;
        if (videoAspectRatio > viewAspectRatio) {
            scale = (float) viewWidth / mVideoWidth;
            mBaseMatrix.setScale(1.0f, (scale * mVideoHeight) / viewHeight);
            mBaseMatrix.postTranslate(0, (viewHeight - (mVideoHeight * scale)) / 2f / viewHeight); // This is not quite right for TextureView matrix
        } else {
            scale = (float) viewHeight / mVideoHeight;
            mBaseMatrix.setScale((scale * mVideoWidth) / viewWidth, 1.0f);
        }
        
        // Correct way to do FIT_CENTER in TextureView matrix (normalized 0..1 coordinates)
        float sx = 1.0f, sy = 1.0f;
        float px = 0.0f, py = 0.0f;

        if (videoAspectRatio > viewAspectRatio) {
            sy = viewAspectRatio / videoAspectRatio;
            py = (1.0f - sy) / 2.0f;
        } else {
            sx = videoAspectRatio / viewAspectRatio;
            px = (1.0f - sx) / 2.0f;
        }

        mBaseMatrix.reset();
        mBaseMatrix.setScale(sx, sy, viewWidth / 2f, viewHeight / 2f);
        
        updateFinalTransform();
    }

    @Override
    public void draw(Canvas canvas) {
        if (mIsInterpolating) {
            canvas.save();
            canvas.concat(mTransformMatrix);
            boolean drewInterpolated = false;
            synchronized (this) {
                boolean midReady = (mMidFrame != null && mMidFrameGeneration == mFrameGeneration);
                if (midReady && mPrevFrame != null && mCurrFrame != null) {
                    drawNeuralBlend(canvas);
                    drewInterpolated = true;
                } else if (mPrevFrame != null && mCurrFrame != null && mPrevWarpMesh != null && mCurrWarpMesh != null) {
                    drawClassicalWarp(canvas);
                    drewInterpolated = true;
                }
            }
            canvas.restore();
            if (drewInterpolated) {
                dispatchDraw(canvas);
                return;
            }
        }
        super.draw(canvas);
    }

    private void drawNeuralBlend(Canvas canvas) {
        canvas.save();
        canvas.translate(mTextureView.getLeft(), mTextureView.getTop());
        mDstRect.set(0, 0, mTextureView.getWidth(), mTextureView.getHeight());

        Bitmap from, to;
        float localAlpha;
        if (mInterpolationAlpha < 0.5f) {
            from = mPrevFrame;
            to = mMidFrame;
            localAlpha = mInterpolationAlpha / 0.5f;
        } else {
            from = mMidFrame;
            to = mCurrFrame;
            localAlpha = (mInterpolationAlpha - 0.5f) / 0.5f;
        }

        mNeuralPaint.setAlpha(255);
        canvas.drawBitmap(from, null, mDstRect, mNeuralPaint);
        mNeuralPaint.setAlpha((int) (255 * localAlpha));
        canvas.drawBitmap(to, null, mDstRect, mNeuralPaint);

        canvas.restore();
    }

    private void drawClassicalWarp(Canvas canvas) {
        canvas.save();
        canvas.translate(mTextureView.getLeft(), mTextureView.getTop());
        float sx = (float) mTextureView.getWidth() / mPrevFrame.getWidth();
        float sy = (float) mTextureView.getHeight() / mPrevFrame.getHeight();
        canvas.scale(sx, sy);

        mNeuralPaint.setAlpha((int) (255 * (1.0f - mInterpolationAlpha)));
        canvas.drawBitmapMesh(mPrevFrame, MESH_WIDTH, MESH_HEIGHT, mPrevWarpMesh, 0, null, 0, mNeuralPaint);

        mNeuralPaint.setAlpha((int) (255 * mInterpolationAlpha));
        canvas.drawBitmapMesh(mCurrFrame, MESH_WIDTH, MESH_HEIGHT, mCurrWarpMesh, 0, null, 0, mNeuralPaint);

        canvas.restore();
    }

    public MediaPlayer getMediaPlayer() { return mMediaPlayer; }
    @Override public void start() { if (isInPlaybackState()) { try { mMediaPlayer.start(); mCurrentState = STATE_PLAYING; } catch (Exception ignored) {} } mTargetState = STATE_PLAYING; }
    @Override public void pause() { if (isInPlaybackState()) { try { if (mMediaPlayer.isPlaying()) { mMediaPlayer.pause(); mCurrentState = STATE_PAUSED; } } catch (Exception ignored) {} } mTargetState = STATE_PAUSED; }
    private boolean isInPlaybackState() { return (mMediaPlayer != null && mCurrentState != STATE_ERROR && mCurrentState != STATE_IDLE && mCurrentState != STATE_PREPARING); }
    @Override public boolean isPlaying() { if (isInPlaybackState()) { try { return mMediaPlayer.isPlaying(); } catch (Exception e) { return false; } } return false; }
    @Override public int getDuration() { return isInPlaybackState() ? mMediaPlayer.getDuration() : 0; }
    @Override public int getCurrentPosition() { return isInPlaybackState() ? mMediaPlayer.getCurrentPosition() : 0; }
    @Override public void seekTo(int pos) { if (isInPlaybackState()) mMediaPlayer.seekTo(pos); }
    @Override public int getBufferPercentage() { return 0; }
    @Override public boolean canPause() { return true; }
    @Override public boolean canSeekBackward() { return true; }
    @Override public boolean canSeekForward() { return true; }
    @Override public int getAudioSessionId() { return (mMediaPlayer != null) ? mMediaPlayer.getAudioSessionId() : 0; }
    @Override public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) { openVideo(); }
    @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) { adjustTextureViewSize(); }
    @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) { release(); if (mSurface != null) mSurface.release(); return true; }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
        if (!mIsInterpolating || mIsCapturing) return;

        long now = System.nanoTime();
        if (mLastHardwareFrameNanos != 0) {
            long delta = now - mLastHardwareFrameNanos;
            mFrameDurationNanos = (long) (mFrameDurationNanos * 0.1 + delta * 0.9);
        }
        mLastHardwareFrameNanos = now;

        mIsCapturing = true;
        mCaptureHandler.post(() -> {
            try {
                int h = INTERPOLATION_HEIGHT;
                int w = (int) (h * ((float) mTextureView.getWidth() / mTextureView.getHeight()));
                if (w <= 0 || h <= 0) return;

                if (mCaptureBuffer == null || mCaptureBuffer.getWidth() != w || mCaptureBuffer.getHeight() != h) {
                    if (mCaptureBuffer != null) mCaptureBuffer.recycle();
                    mCaptureBuffer = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
                    mPrevPixelBuffer = null;
                    mCurrPixelBuffer = null;
                    mHasMotionField = false;
                }

                mTextureView.getBitmap(mCaptureBuffer);

                if (mCurrPixelBuffer == null || mCurrPixelBuffer.capacity() < w * h * 2) {
                    mCurrPixelBuffer = ByteBuffer.allocateDirect(w * h * 2).order(ByteOrder.nativeOrder());
                }
                mCurrPixelBuffer.rewind();
                mCaptureBuffer.copyPixelsToBuffer(mCurrPixelBuffer);
                mCurrPixelBuffer.rewind();

                Bitmap prevForInference = null;
                Bitmap currForInference = null;
                long generation;

                synchronized (this) {
                    Bitmap oldPrev = mPrevFrame;
                    mPrevFrame = mCurrFrame;
                    mCurrFrame = Bitmap.createBitmap(mCaptureBuffer);
                    if (oldPrev != null) oldPrev.recycle();

                    ByteBuffer temp = mPrevPixelBuffer;
                    mPrevPixelBuffer = mCurrPixelBuffer;
                    mCurrPixelBuffer = temp;

                    generation = ++mFrameGeneration;

                    if (mRifeInterpolator != null && mPrevFrame != null && mCurrFrame != null
                            && !mInferenceInFlight.get()) {
                        prevForInference = mPrevFrame.copy(mPrevFrame.getConfig(), false);
                        currForInference = mCurrFrame.copy(mCurrFrame.getConfig(), false);
                    }
                }

                if (mPrevPixelBuffer != null && mCurrPixelBuffer != null) {
                    mPrevPixelBuffer.rewind();
                    mCurrPixelBuffer.rewind();
                    VideoInterpolation.nativeComputeMotionField(mPrevPixelBuffer, mCurrPixelBuffer, w, h);
                    mHasMotionField = true;
                } else {
                    mHasMotionField = false;
                }

                if (prevForInference != null && currForInference != null
                        && mInferenceInFlight.compareAndSet(false, true)) {
                    submitInference(prevForInference, currForInference, generation);
                }
            } finally {
                mIsCapturing = false;
            }
        });
    }

    private void submitInference(Bitmap prev, Bitmap curr, long generation) {
        final int outW = mTextureView.getWidth();
        final int outH = mTextureView.getHeight();
        mInferenceExecutor.execute(() -> {
            try {
                RifeFrameInterpolator interpolator;
                synchronized (this) { interpolator = mRifeInterpolator; }
                if (interpolator == null || outW <= 0 || outH <= 0) return;

                Bitmap mid = interpolator.interpolate(prev, curr, 0.5f, outW, outH);

                synchronized (this) {
                    if (generation == mFrameGeneration) {
                        if (mMidFrame != null) mMidFrame.recycle();
                        mMidFrame = mid;
                        mMidFrameGeneration = generation;
                    } else {
                        mid.recycle();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Neural interpolation inference failed", e);
            } finally {
                prev.recycle();
                curr.recycle();
                mInferenceInFlight.set(false);
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mCaptureThread != null) {
            mCaptureThread.quitSafely();
            mCaptureThread = null;
        }
        if (mInferenceExecutor != null) {
            mInferenceExecutor.shutdownNow();
            mInferenceExecutor = null;
        }
        synchronized (this) {
            if (mRifeInterpolator != null) {
                mRifeInterpolator.close();
                mRifeInterpolator = null;
            }
        }
    }
}
