package xime.media.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.GestureDetector;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import xime.media.ui.VideoView;
import xime.ui.view.ImageView;
import xime.ui.common.IconButton;
import xime.ui.bar.LinearProgressBar;
import xime.imaging.Prism;
import xime.media.R;
import xime.ui.layout.BlurLayout;

public class MediaView extends BlurLayout {

    public enum MediaType {
        IMAGE(0), VIDEO(1);
        final int value;
        MediaType(int v) { this.value = v; }
        static MediaType fromInt(int v) {
            for (MediaType t : values()) if (t.value == v) return t;
            return IMAGE;
        }
    }

    private ImageView imageView;
    private VideoView playerView;
    private ViewGroup bottomToolBar;
    private ViewGroup playbackControls;
    
    private IconButton btnPlayPause;
    private IconButton btnMute;
    private IconButton btnSpeed;
    private LinearProgressBar progressBar;

    private MediaType mediaType = MediaType.IMAGE;

    private final Matrix matrix = new Matrix();
    private final Matrix savedMatrix = new Matrix();
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;
    private final PointF start = new PointF();
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private boolean isMuted = false;
    private boolean isDragging = false;
    private float baseScale = 1.0f;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private static final int HIDE_CONTROLS_DELAY = 3000;

    public interface OnTapListener {
        void onTap();
    }

    public interface OnVisibilityChangeListener {
        void onVisibilityChanged(boolean visible);
    }

    public interface OnSpeedChangeListener {
        void onSpeedMenuRequested(View anchor, int[] icons);
        void onSpeedMenuTouchUpdated(float rawX, float rawY);
        void onSpeedMenuTouchFinished();
    }

    private OnTapListener onTapListener;
    private OnVisibilityChangeListener visibilityChangeListener;
    private OnSpeedChangeListener speedChangeListener;

    public void setOnTapListener(OnTapListener listener) { this.onTapListener = listener; }
    public void setOnVisibilityChangeListener(OnVisibilityChangeListener listener) { this.visibilityChangeListener = listener; }
    public void setOnSpeedChangeListener(OnSpeedChangeListener listener) { this.speedChangeListener = listener; }

    public MediaView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public MediaView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public MediaView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.widget_media_view, this, true);
        imageView = findViewById(R.id.media_image);
        playerView = findViewById(R.id.media_player);
        bottomToolBar = findViewById(R.id.bottomToolBar);
        playbackControls = findViewById(R.id.playbackControls);
        
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnMute = findViewById(R.id.btnMute);
        btnSpeed = findViewById(R.id.btnSpeed);
        progressBar = findViewById(R.id.progressBar);

        if (attrs != null) {
            try {
                TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MediaView);
                try {
                    int typeInt = a.getInt(R.styleable.MediaView_xoerisMediaType, 0);
                    this.mediaType = MediaType.fromInt(typeInt);
                } finally {
                    a.recycle();
                }
            } catch (Throwable t) {
                // Fallback via dynamic resource identifier lookup if styleable constant mapping differs across APK merges
                try {
                    int attrId = context.getResources().getIdentifier("xoerisMediaType", "attr", context.getPackageName());
                    if (attrId == 0) {
                        attrId = context.getResources().getIdentifier("xoerisMediaType", "attr", "xime.ui");
                    }
                    if (attrId != 0) {
                        TypedArray a = context.obtainStyledAttributes(attrs, new int[]{attrId});
                        try {
                            int typeInt = a.getInt(0, 0);
                            this.mediaType = MediaType.fromInt(typeInt);
                        } finally {
                            a.recycle();
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        setupMediaType();
        setupVideoControls();
        
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureListener());
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleControls();
                if (onTapListener != null) onTapListener.onTap();
                return true;
            }
        });

        setCornerRadius(20);
        setBlurType(BlurType.NONE);
        setGlassAlpha(0.8f);
    }

    private void setupMediaType() {
        if (mediaType == MediaType.VIDEO) {
            imageView.setVisibility(GONE);
            playerView.setVisibility(VISIBLE);
            playbackControls.setVisibility(VISIBLE);
        } else {
            imageView.setVisibility(VISIBLE);
            playerView.setVisibility(GONE);
            playbackControls.setVisibility(GONE);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupVideoControls() {
        btnPlayPause.setOnClickListener(v -> {
            if (playerView.isPlaying()) playerView.pause();
            else playerView.start();
            updatePlayPauseIcon();
            scheduleHideControls();
        });

        btnMute.setOnClickListener(v -> {
            isMuted = !isMuted;
            if (playerView.getMediaPlayer() != null) {
                playerView.getMediaPlayer().setVolume(isMuted ? 0 : 1, isMuted ? 0 : 1);
            }
            btnMute.setImageResource(isMuted ? xime.R.drawable.xoeris_volume_off : xime.R.drawable.xoeris_volume_up);
            scheduleHideControls();
        });

        btnSpeed.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(150).start();
                    showSpeedMenu();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                    if (speedChangeListener != null) speedChangeListener.onSpeedMenuTouchFinished();
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        v.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (speedChangeListener != null) speedChangeListener.onSpeedMenuTouchUpdated(event.getRawX(), event.getRawY());
                    return true;
            }
            return false;
        });

        progressBar.setOnSeekBarChangeListener(new LinearProgressBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(LinearProgressBar linearTrackBar, int progress, boolean fromUser) {
                if (fromUser && playerView.getMediaPlayer() != null) {
                    int newPos = (int) ((long) progress * playerView.getDuration() / 1000);
                    playerView.seekTo(newPos);
                }
            }

            @Override
            public void onStartTrackingTouch(LinearProgressBar linearTrackBar) {
                isDragging = true;
                uiHandler.removeCallbacks(hideControlsAction);
            }

            @Override
            public void onStopTrackingTouch(LinearProgressBar linearTrackBar) {
                isDragging = false;
                scheduleHideControls();
            }
        });
    }

    private void showSpeedMenu() {
        int[] speedIcons = new int[]{
                xime.R.drawable.xoeris_speed_0_25,
                xime.R.drawable.xoeris_speed_0_50,
                xime.R.drawable.xoeris_speed_0_75,
                xime.R.drawable.xoeris_speed_1_00,
                xime.R.drawable.xoeris_speed_1_25,
                xime.R.drawable.xoeris_speed_1_50,
                xime.R.drawable.xoeris_speed_1_75,
                xime.R.drawable.xoeris_speed_2_00
        };
        if (speedChangeListener != null) speedChangeListener.onSpeedMenuRequested(btnSpeed, speedIcons);
    }

    private void updatePlayPauseIcon() {
        btnPlayPause.setImageResource(playerView.isPlaying() ? xime.R.drawable.xoeris_pause : xime.R.drawable.xoeris_play_arrow);
    }

    public void toggleControls() {
        if (bottomToolBar.getVisibility() == VISIBLE) {
            hideControls();
        } else {
            showControls();
        }
    }

    public void showControls() {
        showControls(true);
    }

    public void showControlsPersistent() {
        showControls(false);
    }

    public void showControls(boolean autoHide) {
        bottomToolBar.setVisibility(VISIBLE);
        if (mediaType == MediaType.VIDEO) {
            playbackControls.setVisibility(VISIBLE);
            updateProgress();
            updatePlayPauseIcon();
        }
        if (visibilityChangeListener != null) visibilityChangeListener.onVisibilityChanged(true);
        
        uiHandler.removeCallbacks(hideControlsAction);
        if (autoHide) {
            scheduleHideControls();
        }
    }

    public void hideControls() {
        if (isDragging) return;
        bottomToolBar.setVisibility(GONE);
        if (mediaType == MediaType.VIDEO) {
            playbackControls.setVisibility(GONE);
        }
        if (visibilityChangeListener != null) visibilityChangeListener.onVisibilityChanged(false);
    }

    private final Runnable hideControlsAction = this::hideControls;

    private void scheduleHideControls() {
        uiHandler.removeCallbacks(hideControlsAction);
        uiHandler.postDelayed(hideControlsAction, HIDE_CONTROLS_DELAY);
    }

    private final Runnable updateProgressAction = new Runnable() {
        @Override
        public void run() {
            updateProgress();
        }
    };

    private void updateProgress() {
        if (playerView.getMediaPlayer() == null || isDragging) return;

        int position = playerView.getCurrentPosition();
        int duration = playerView.getDuration();

        if (duration > 0) {
            long pos = 1000L * position / duration;
            progressBar.setProgress((int) pos);
        }

        if (playerView.isPlaying()) {
            uiHandler.postDelayed(updateProgressAction, 1000 - (position % 1000));
        }
    }

    public void setMediaType(MediaType type) {
        this.mediaType = type;
        setupMediaType();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean scaleHandled = scaleDetector.onTouchEvent(event);
        boolean gestureHandled = gestureDetector.onTouchEvent(event);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                if (matrix.isIdentity()) {
                    initMatrixForMedia();
                }
                savedMatrix.set(matrix);
                start.set(event.getX(), event.getY());
                mode = DRAG;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (matrix.isIdentity()) {
                    initMatrixForMedia();
                }
                mode = ZOOM;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    float[] v = new float[9];
                    matrix.getValues(v);
                    float s = v[Matrix.MSCALE_X];
                    if (s > baseScale * 1.01f) {
                        matrix.set(savedMatrix);
                        matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
                        applyTransform();
                    }
                }
                break;
        }

        float[] values = new float[9];
        matrix.getValues(values);
        float scale = values[Matrix.MSCALE_X];
        
        boolean isZoomed = scale > baseScale * 1.01f;
        if (isZoomed) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
        
        return scaleHandled || gestureHandled || isZoomed || mode == ZOOM;
    }

    private void applyTransform() {
        if (matrix.isIdentity()) {
            if (imageView.getVisibility() == VISIBLE) {
                imageView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                imageView.setImageMatrix(null);
            } else if (playerView.getVisibility() == VISIBLE) {
                playerView.setVideoTransform(null);
            }
            return;
        }

        if (imageView.getVisibility() == VISIBLE) {
            if (imageView.getScaleType() != android.widget.ImageView.ScaleType.MATRIX) {
                imageView.setScaleType(android.widget.ImageView.ScaleType.MATRIX);
            }
            imageView.setImageMatrix(matrix);
        } else if (playerView.getVisibility() == VISIBLE) {
            playerView.setVideoTransform(matrix);
        }
    }

    private void initMatrixForMedia() {
        if (imageView.getVisibility() == VISIBLE) {
            Matrix currentMatrix = imageView.getImageMatrix();
            if (currentMatrix != null && !currentMatrix.isIdentity()) {
                matrix.set(currentMatrix);
                savedMatrix.set(matrix);
                float[] values = new float[9];
                matrix.getValues(values);
                baseScale = values[Matrix.MSCALE_X];
                if (baseScale <= 0) baseScale = 1.0f;
                return;
            }
        } else if (playerView.getVisibility() == VISIBLE) {
            baseScale = 1.0f;
            return;
        }
    }

    private class ScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private float lastFocusX;
        private float lastFocusY;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            lastFocusX = detector.getFocusX();
            lastFocusY = detector.getFocusY();
            mode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float[] values = new float[9];
            matrix.getValues(values);
            float currentMatrixScale = values[Matrix.MSCALE_X];
            float newScale = currentMatrixScale * scaleFactor;
            
            if (newScale >= baseScale * 0.8f && newScale <= baseScale * 5.0f) {
                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();
                
                matrix.postTranslate(focusX - lastFocusX, focusY - lastFocusY);
                matrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
                
                lastFocusX = focusX;
                lastFocusY = focusY;
                
                applyTransform();
            }
            return true;
        }
    }

    private void resetTransform() {
        matrix.reset();
        savedMatrix.reset();
        baseScale = 1.0f;
        mode = NONE;
        if (imageView.getVisibility() == VISIBLE) {
            imageView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            imageView.setImageMatrix(null);
        } else {
            playerView.setVideoTransform(null);
        }
    }

    public void bindImage(Uri uri) {
        setMediaType(MediaType.IMAGE);
        imageView.setImageDrawable(null);
        resetTransform();
        Prism.with(getContext()).load(uri).into(imageView);
    }

    public void bindVideo(Uri uri) {
        setMediaType(MediaType.VIDEO);
        resetTransform();
        playerView.setVideoURI(uri);
    }

    public void setPlaybackSpeed(float speed) { playerView.setPlaybackSpeed(speed); }
    public void setSpeedIcon(int iconRes) { btnSpeed.setImageResource(iconRes); }
    public ImageView getImageView() { return imageView; }
    public VideoView getPlayerView() { return playerView; }
    public ViewGroup getBottomToolBar() { return bottomToolBar; }
}
