package xime.media.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.os.FileObserver;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import xime.imaging.Prism;
import xime.imaging.PrismOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

import xime.R;
import xime.core.theme.ThemeManager;
import xime.ui.layout.ConstraintLayout;
import xime.ui.layout.Layout;
import xime.ui.layout.BlurLayout;
import xime.ui.layout.PagerLayout;
import xime.ui.view.GapView;
import xime.ui.bar.LinearProgressBar;
import xime.ui.layout.FlowLayout;
import xime.ui.layout.ReflectLayout;
import xime.media.music.lyrics.Lyrics;
import xime.media.Metadata;
import xime.media.music.MusicEngine;
import xime.media.Signal;
import xime.media.music.Music;
import xime.media.Queue;
import xime.media.music.lyrics.LyricsSystem;
import xime.media.MediaPrefs;
import xime.media.PlaybackSession;
import xime.media.PlaybackSessionListener;
import xime.media.PlaybackSnapshot;
import xime.ui.view.BlurView;
import xime.ui.view.PictureView;
import xime.ui.common.IconButton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PlayerView extends Layout {
    public boolean isAnimating = false;
    public boolean isProgressCollapsed = true;
    public boolean playerHiddenAttr = false;
    public static final int LAYOUT_COMPACT = 1;
    public static final int LAYOUT_EXPAND = 2;
    public static final int LAYOUT_MINI = 3;

    public int compactLayoutResId = LAYOUT_COMPACT;
    public int expandLayoutResId = LAYOUT_EXPAND;
    public int miniLayoutResId = LAYOUT_MINI;

    private final ConstraintSet compactSet = new ConstraintSet();
    private final ConstraintSet expandSet = new ConstraintSet();
    private final ConstraintSet widgetSet = new ConstraintSet();
    private boolean isLayoutCached = false;

    private ConstraintLayout flexRoot;
    private BlurLayout glassWrapper;
    private ConstraintLayout playerContent;
    private SpectrumLayout spectrumBackground;
    private PagerLayout mainPager;
    private View albumArtContainer;
    private ImageView albumArt;
    public TextView trackTitle;
    public TextView trackArtist;
    public ImageView playPauseButton;
    public ImageView shuffleButton;
    public ImageView repeatButton;
    private View nextButton;
    private View previousButton;
    public LinearProgressBar progressBar;
    private TextView currentTimeText;
    private TextView totalTimeText;

    private FlowLayout lyricsPage;
    private LyricsView lyricsView;
    private IconButton btnSyncLyrics;
    private Lyrics currentLyrics = new Lyrics();
    private boolean isLyricsAutoScrollEnabled = true;
    private boolean isUserScrollingLyrics = false;
    private long lastUserScrollTime = 0;
    private static final long SCROLL_RESUME_DELAY = 3000;

    private FileObserver lyricFileObserver;

    private PagerLayout bioPager;
    private View bioItemView;
    private String currentBioArtist = "";

    private OrbitLayout queueRecyclerView;
    private TextView queueStatusText;

    private MusicEngine musicEngine;
    /** The active PlaybackSession that drives transport commands and core UI. */
    private PlaybackSession playbackSession;
    private final PlaybackSessionListener sessionListener = new PlaybackSessionListener() {
        @Override
        public void onSnapshotUpdated(PlaybackSnapshot snapshot) {
            post(() -> applySnapshot(snapshot));
        }
        @Override
        public void onSessionAvailabilityChanged(boolean available) {
            post(() -> {
                setVisibility(available ? VISIBLE : GONE);
            });
        }
    };
    private View.OnClickListener externalClickListener;
    private GestureDetector gestureDetector;
    private float initialTouchX;
    private float initialTouchY;
    private boolean isSwiping = false;
    private static final int SWIPE_THRESHOLD = 100;
    // Host-driven seek fallback (collapsed mode): set when DOWN lands on the seek bar
    // but this view (not the bar) owns the gesture. Used to drive seeking directly.
    private boolean downOnSeekBar = false;
    private boolean fallbackSeekUsed = false;

    /**
     * Hit-test for the (seekable) progress bar with extra slop, since the bar is thin.
     */
    private boolean isTouchOnSeekBar(MotionEvent ev) {
        if (progressBar == null || !progressBar.isSeekable()) return false;
        if (progressBar.getVisibility() != VISIBLE || !progressBar.isShown()) return false;
        try {
            int[] barLoc = new int[2];
            progressBar.getLocationOnScreen(barLoc);
            float bx = ev.getRawX() - barLoc[0];
            float by = ev.getRawY() - barLoc[1];
            float slop = 16 * getResources().getDisplayMetrics().density;
            return bx >= -slop && bx <= progressBar.getWidth() + slop
                    && by >= -slop && by <= progressBar.getHeight() + slop;
        } catch (Exception ignored) {
            return false;
        }
    }

    private final Signal.Observer<Metadata> metadataObserver = this::updateMetadata;
    private final Signal.Observer<Music.Progress> progressObserver = this::updateProgress;
    private final Signal.Observer<Music.PlaybackState> stateObserver = this::updateState;
    private final Signal.Observer<Boolean> shuffleObserver = this::updateShuffleState;
    private final Signal.Observer<Queue.RepeatMode> repeatObserver = this::updateRepeatState;
    private final Signal.Observer<Void> queueObserver = v -> updateQueue();

    public interface OnExpandStateChangeListener {
        void onExpandStateChanged(boolean isExpanded);
    }
    private OnExpandStateChangeListener expandStateChangeListener;

    private final BroadcastReceiver spectrumVisibilityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (spectrumBackground != null) {
                boolean visible = intent.getBooleanExtra("visible", true);
                spectrumBackground.setSpectrumEnabled(visible);
            }
        }
    };

    public PlayerView(Context context) {
        super(context);
        init(null);
    }

    public PlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public PlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    public void setPlayerHidden(boolean hidden) {
        this.playerHiddenAttr = hidden;
        if (musicEngine != null) {
            updateState(musicEngine.getMusic().getCurrentState());
        }
    }

    public void setCompactLayout(int resId) {
        boolean changed = this.compactLayoutResId != resId || this.miniLayoutResId != resId;
        this.compactLayoutResId = resId;
        this.miniLayoutResId = resId;
        
        if (changed && mainPager != null) {
            View oldContent = findViewById(R.id.playerContent);
            if (oldContent != null) {
                mainPager.removeView(oldContent);
            }
            
            LayoutFactory.Variant variant = (miniLayoutResId == LAYOUT_MINI)
                    ? LayoutFactory.Variant.MINI
                    : LayoutFactory.Variant.COMPACT;
            View pContent = LayoutFactory.buildPlayerContent(getContext(), variant);
            mainPager.addPage(pContent, 0);
            this.playerContent = (ConstraintLayout) pContent;
            
            // Re-bind views
            this.albumArtContainer = findViewById(R.id.albumArtContainer);
            this.albumArt = (ImageView) findViewById(R.id.albumArt);
            this.trackTitle = (TextView) findViewById(R.id.trackTitle);
            this.trackArtist = (TextView) findViewById(R.id.artistName);
            this.playPauseButton = (ImageView) findViewById(R.id.playPauseButton);
            this.shuffleButton = (ImageView) findViewById(R.id.shuffleButton);
            this.repeatButton = (ImageView) findViewById(R.id.repeatButton);
            this.nextButton = findViewById(R.id.nextButton);
            this.previousButton = findViewById(R.id.previousButton);
            this.progressBar = (LinearProgressBar) findViewById(R.id.linearTrackBar);
            this.currentTimeText = (TextView) findViewById(R.id.currentTimeText);
            this.totalTimeText = (TextView) findViewById(R.id.totalTimeText);
            
            setupInternalListeners();
            applyLayoutState();
        }
    }

    public void setExpandLayout(int resId) {
        this.expandLayoutResId = resId;
    }

    /**
     * Forces a specific theme mode for all glass/blur elements owned by this PlayerView.
     * Reverts to system-following behaviour if {@link BlurLayout.ThemeMode#AUTO} is passed.
     *
     * @param mode {@link BlurLayout.ThemeMode#DARK}, {@link BlurLayout.ThemeMode#LIGHT} or {@link BlurLayout.ThemeMode#AUTO}
     */
    public void setThemeMode(BlurLayout.ThemeMode mode) {
        if (mode == null) return;
        
        if (this.glassWrapper != null) {
            this.glassWrapper.setThemeMode(mode);
        }

        // For global overrides, we still use ThemeManager if not AUTO.
        // If it is AUTO, we clear the force mode.
        try {
            if (mode == BlurLayout.ThemeMode.AUTO) {
                ThemeManager.get().clearForceMode();
            } else {
                ThemeManager.get().setForceMode(mode == BlurLayout.ThemeMode.DARK ? ThemeManager.Mode.DARK : ThemeManager.Mode.LIGHT);
            }
        } catch (IllegalStateException ignored) {
            // ThemeManager not yet initialised.
        }
    }

    public void refreshImmediately() {
        if (glassWrapper != null) {
            glassWrapper.refreshImmediately();
        }
    }

    public boolean isPlayerHidden() {
        return playerHiddenAttr;
    }

    private void init(AttributeSet attrs) {
        setClipChildren(false);
        setClipToPadding(false);
        BlurLayout glassLayout = LayoutFactory.buildWidgetShell(getContext());
        addView(glassLayout);
        this.glassWrapper = glassLayout;
        this.flexRoot = (ConstraintLayout) findViewById(R.id.flexRoot);
        this.spectrumBackground = (SpectrumLayout) findViewById(R.id.spectrumBackground);
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.PlayerView);
            try {
                this.playerHiddenAttr = a.getBoolean(R.styleable.PlayerView_xoerisPlayerHidden, false);
                this.compactLayoutResId = a.getResourceId(R.styleable.PlayerView_xoerisPlayerLayout, LAYOUT_COMPACT);
                this.expandLayoutResId = a.getResourceId(R.styleable.PlayerView_xoerisPlayerExpandLayout, LAYOUT_EXPAND);
                if (a.hasValue(R.styleable.PlayerView_xoerisPlayerLayout)) {
                    this.miniLayoutResId = this.compactLayoutResId;
                }
            } finally {
                a.recycle();
            }
        }
        
        if (attrs != null && spectrumBackground != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.PlayerView);
            try {
                if (a.hasValue(R.styleable.PlayerView_xoerisPlayerSpectrumEnabled)) {
                    spectrumBackground.setSpectrumEnabled(a.getBoolean(R.styleable.PlayerView_xoerisPlayerSpectrumEnabled, true));
                }
                if (a.hasValue(R.styleable.PlayerView_xoerisPlayerSpectrumColor)) {
                    spectrumBackground.setBarColor(a.getColor(R.styleable.PlayerView_xoerisPlayerSpectrumColor, 0x30FFFFFF));
                }
            } finally {
                a.recycle();
            }
        }

        this.mainPager = (PagerLayout) findViewById(R.id.mainPager);

        LayoutFactory.Variant variant = (miniLayoutResId == LAYOUT_MINI)
                ? LayoutFactory.Variant.MINI
                : LayoutFactory.Variant.COMPACT;
        View pContent = LayoutFactory.buildPlayerContent(getContext(), variant);
        if (mainPager != null) {
            mainPager.addPage(pContent, 0);
            mainPager.addPage(LayoutFactory.buildQueuePage(getContext()));
            mainPager.addPage(LayoutFactory.buildLyricsPage(getContext()));
            mainPager.addPage(LayoutFactory.buildBioPage(getContext()));
            mainPager.setCurrentPage(0);
        }
        this.playerContent = (ConstraintLayout) pContent;

        this.albumArtContainer = findViewById(R.id.albumArtContainer);
        this.albumArt = findViewById(R.id.albumArt);
        this.trackTitle = findViewById(R.id.trackTitle);
        this.trackArtist = findViewById(R.id.artistName);

        this.shuffleButton = findViewById(R.id.shuffleButton);
        this.repeatButton = findViewById(R.id.repeatButton);
        this.playPauseButton = findViewById(R.id.playPauseButton);
        this.nextButton = findViewById(R.id.nextButton);
        this.previousButton = findViewById(R.id.previousButton);
        this.progressBar = (LinearProgressBar) findViewById(R.id.linearTrackBar);
        this.currentTimeText = findViewById(R.id.currentTimeText);
        this.totalTimeText = findViewById(R.id.totalTimeText);

        this.lyricsPage = (FlowLayout) findViewById(R.id.lyricsPage);
        this.lyricsView = (LyricsView) findViewById(R.id.lyricsView);
        this.btnSyncLyrics = findViewById(R.id.btnSyncLyrics);

        if (this.lyricsView != null) {
            this.lyricsView.setOnActiveLineChangedListener((position, top, height) -> {
                if (isLyricsAutoScrollEnabled && !isUserScrollingLyrics && lyricsPage != null) {
                    int centerY = top + (height / 2) - (lyricsPage.getHeight() / 2);
                    lyricsPage.smoothScrollTo(0, Math.max(0, centerY));
                }
            });
        }

        this.bioPager = (PagerLayout) findViewById(R.id.bioPager);

        if (this.btnSyncLyrics != null) {
            this.btnSyncLyrics.setOnClickListener(v -> {
                isUserScrollingLyrics = false;
                btnSyncLyrics.setVisibility(GONE);
                if (musicEngine != null) {
                    updateProgress(musicEngine.getMusic().getProgressSignal().getLastEvent());
                }
            });
        }

        if (this.lyricsPage != null) {
            this.lyricsPage.setOnScrollStateChangeListener((view, newState) -> {
                if (newState == FlowLayout.SCROLL_STATE_DRAGGING) {
                    isUserScrollingLyrics = true;
                    lastUserScrollTime = System.currentTimeMillis();
                    if (btnSyncLyrics != null) btnSyncLyrics.setVisibility(VISIBLE);
                }
            });
        }

        this.queueRecyclerView = findViewById(R.id.queueRecyclerView);
        this.queueStatusText = findViewById(R.id.queueStatus);

        if (this.queueRecyclerView != null) {
            this.queueRecyclerView.setOnItemClickListener(new xime.media.ui.MediaOrbitAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(Metadata metadata, int position) {
                    if (musicEngine != null) {
                        musicEngine.getQueue().jumpTo(position);
                        musicEngine.getMusic().play(metadata);
                    }
                }
            });
        }
        
        setupInternalListeners();
        
        if (spectrumBackground != null) {
            MediaPrefs prefs = new MediaPrefs(getContext());
            spectrumBackground.setSpectrumEnabled(prefs.isShowSpectrum());
            try {
                spectrumBackground.setBarColor(ContextCompat.getColor(getContext(), R.color.xoeris_primary));
            } catch (Exception ignored) {}
        }

        super.setClickable(true);
        super.setFocusable(true);
        super.setOnClickListener(v -> {
            if (externalClickListener != null) externalClickListener.onClick(v);
        });

        setupGestures();
        applyLayoutState();
        if (!isInEditMode()) {
            Context ctx = getContext();
            if (ctx != null) {
                this.musicEngine = MusicEngine.getInstance(ctx);
            }
        }
    }

    /**
     * Binds this PlayerView to a PlaybackSession.
     * Transport controls (play/pause/seek/next/prev) will be routed through the session.
     * Capability flags (supportsSeek, supportsNextPrevious) will be used to adapt the UI.
     * @param session The session to bind. May be Internal or External.
     */
    public void bindSession(PlaybackSession session) {
        if (this.playbackSession != null) {
            this.playbackSession.removeListener(sessionListener);
            this.playbackSession.detach();
        }
        this.playbackSession = session;
        if (session != null) {
            session.addListener(sessionListener);
            session.attach();
            // Adapt UI to session capabilities
            if (progressBar != null) {
                progressBar.setEnabled(session.supportsSeek());
                progressBar.setVisibility(session.supportsSeek() ? VISIBLE : GONE);
            }
            if (nextButton != null) nextButton.setVisibility(session.supportsNextPrevious() ? VISIBLE : GONE);
            if (previousButton != null) previousButton.setVisibility(session.supportsNextPrevious() ? VISIBLE : GONE);
            // Trigger initial snapshot
            PlaybackSnapshot snap = session.getSnapshot();
            if (snap != null) applySnapshot(snap);
        }
    }

    /**
     * Applies a PlaybackSnapshot to the UI. Called from the PlaybackSessionListener.
     */
    private void applySnapshot(PlaybackSnapshot snapshot) {
        if (snapshot == null) return;
        // State
        boolean playing = snapshot.isPlaying;
        if (playPauseButton != null) {
            playPauseButton.setImageResource(playing ? R.drawable.xoeris_pause : R.drawable.xoeris_play);
        }
        if (spectrumBackground != null) {
            if (playing) spectrumBackground.startAnimation();
            else spectrumBackground.stopAnimation();
        }
        if (!playerHiddenAttr || playing) setVisibility(VISIBLE);
        else setVisibility(GONE);
        // Progress
        if (progressBar != null && !progressBar.isDragging()) {
            progressBar.setMax((int) snapshot.durationMs);
            progressBar.setProgress((int) snapshot.positionMs);
        }
        if (currentTimeText != null) currentTimeText.setText(formatTime(snapshot.positionMs));
        if (totalTimeText != null) totalTimeText.setText(formatTime(snapshot.durationMs));
        // Metadata
        if (snapshot.title != null && trackTitle != null) trackTitle.setText(snapshot.title);
        if (snapshot.artist != null && trackArtist != null) trackArtist.setText(snapshot.artist);
        if (albumArt != null) {
            if (snapshot.artwork != null) {
                albumArt.setImageBitmap(snapshot.artwork);
            } else if (musicEngine != null && musicEngine.getMusic().getCurrentMetadata() != null) {
                Metadata meta = musicEngine.getMusic().getCurrentMetadata();
                Prism.with(getContext())
                    .load(meta.getArtUri())
                    .apply(new PrismOptions()
                        .placeholder(R.drawable.xoeris_music_note, getContext())
                        .error(R.drawable.xoeris_music_note, getContext())
                        .preferRgb565())
                    .into(albumArt);
            }
        }
        // Bio hunt must follow track changes arriving through the session snapshot path,
        // not just the engine metadata signal (which only fires on pause/play cycles).
        // Cheap guard: currentBioArtist comparison makes per-tick calls no-ops.
        if (snapshot.artist != null && !snapshot.artist.trim().isEmpty()
                && !snapshot.artist.trim().equals(currentBioArtist)) {
            updateArtistBio(snapshot.artist, null);
        }
        // Lyrics progress (Internal mode only, MusicEngine is the source of truth)
        if (lyricsView != null) {
            lyricsView.updateProgress(snapshot.positionMs, currentLyrics);
        }
        if (isUserScrollingLyrics && System.currentTimeMillis() - lastUserScrollTime > SCROLL_RESUME_DELAY) {
            isUserScrollingLyrics = false;
            if (btnSyncLyrics != null) btnSyncLyrics.setVisibility(GONE);
        }
    }

    private void unusedInit() {
    }

    private void setupInternalListeners() {
        if (mainPager != null) {
            mainPager.setOnClickListener(v -> toggleProgressVisibility(!isProgressCollapsed));
            mainPager.setStateChangeListener(new PagerLayout.OnPagerStateChangeListener() {
                @Override
                public void onDraggingStarted() {
                    if (glassWrapper != null) glassWrapper.setPauseUpdates(true);
                    if (spectrumBackground != null) spectrumBackground.stopAnimation();
                }

                @Override
                public void onDraggingStopped() {
                    if (glassWrapper != null) {
                        glassWrapper.setPauseUpdates(false);
                        glassWrapper.refreshImmediately();
                    }
                    if (spectrumBackground != null && musicEngine != null && 
                        musicEngine.getMusic().getCurrentState() == Music.PlaybackState.PLAYING) {
                        spectrumBackground.startAnimation();
                    }
                }
            });
        } else {
            if (albumArtContainer != null) {
                albumArtContainer.setOnClickListener(v -> toggleProgressVisibility(!isProgressCollapsed));
            }
        }

        if (nextButton != null) nextButton.setOnClickListener(v -> {
            if (playbackSession != null) playbackSession.next();
            else if (musicEngine != null) musicEngine.playNext();
        });
        if (previousButton != null) previousButton.setOnClickListener(v -> {
            if (playbackSession != null) playbackSession.previous();
            else if (musicEngine != null) musicEngine.playPrevious();
        });

        if (playPauseButton != null) {
            playPauseButton.setOnClickListener(v -> {
                if (playbackSession != null) {
                    PlaybackSnapshot snap = playbackSession.getSnapshot();
                    if (snap != null && snap.isPlaying) playbackSession.pause();
                    else playbackSession.play();
                } else if (musicEngine != null) musicEngine.togglePlayback();
            });
        }

        if (shuffleButton != null) {
            shuffleButton.setOnClickListener(v -> { if (musicEngine != null) musicEngine.toggleShuffle(); });
        }

        if (repeatButton != null) {
            repeatButton.setOnClickListener(v -> { if (musicEngine != null) musicEngine.cycleRepeatMode(); });
        }

        if (progressBar != null) {
            // Horizontal drags seek; clearly-vertical drags fall through so swipe to
            // expand/collapse starting on the bar still works (esp. collapsed mode).
            progressBar.setAllowParentVerticalSteal(true);
            progressBar.setOnSeekBarChangeListener(new LinearProgressBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(LinearProgressBar bar, int progress, boolean fromUser) {
                    if (fromUser) {
                        if (playbackSession != null && playbackSession.supportsSeek()) playbackSession.seekTo(progress);
                        else if (musicEngine != null) musicEngine.getMusic().seekTo(progress);
                    }
                }
                @Override public void onStartTrackingTouch(LinearProgressBar bar) {}
                @Override public void onStopTrackingTouch(LinearProgressBar bar) {}
            });
        }
    }

    private void applyLayoutState() {
        if (flexRoot == null || playerContent == null) return;
        
        if (!isLayoutCached) {
            LayoutFactory.Variant variant = (compactLayoutResId == LAYOUT_MINI)
                    ? LayoutFactory.Variant.MINI
                    : LayoutFactory.Variant.COMPACT;
            compactSet.clone(ConstraintSets.buildCompactSet(getContext(), variant));
            expandSet.clone(ConstraintSets.buildExpandSet(getContext()));
            widgetSet.clone(ConstraintSets.buildWidgetSet());
            isLayoutCached = true;
        }

        ConstraintSet shellSet = new ConstraintSet();
        shellSet.clone(widgetSet);

        if (!isProgressCollapsed) {
            expandSet.applyTo(playerContent);

            if (glassWrapper != null) {
                ViewGroup.LayoutParams glp = glassWrapper.getLayoutParams();
                if (glp != null) {
                    glp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    glassWrapper.setLayoutParams(glp);
                }
            }

            ViewGroup.LayoutParams flp = flexRoot.getLayoutParams();
            if (flp != null) {
                flp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                flexRoot.setLayoutParams(flp);
            }

            shellSet.constrainHeight(R.id.spectrumBackground, ConstraintSet.MATCH_CONSTRAINT);
            shellSet.connect(R.id.spectrumBackground, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            shellSet.connect(R.id.spectrumBackground, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);

            shellSet.constrainHeight(R.id.mainPager, ConstraintSet.MATCH_CONSTRAINT);
            shellSet.connect(R.id.mainPager, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            shellSet.connect(R.id.mainPager, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            
            ViewGroup.LayoutParams mlp = mainPager.getLayoutParams();
            if (mlp != null) {
                mlp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                mainPager.setLayoutParams(mlp);
            }
        } else {
            compactSet.applyTo(playerContent);

            if (glassWrapper != null) {
                ViewGroup.LayoutParams glp = glassWrapper.getLayoutParams();
                if (glp != null) {
                    glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    glassWrapper.setLayoutParams(glp);
                }
            }

            shellSet.constrainHeight(R.id.spectrumBackground, ConstraintSet.MATCH_CONSTRAINT);
            shellSet.connect(R.id.spectrumBackground, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            shellSet.connect(R.id.spectrumBackground, ConstraintSet.BOTTOM, R.id.mainPager, ConstraintSet.BOTTOM);

            shellSet.constrainHeight(R.id.mainPager, ConstraintSet.WRAP_CONTENT);
            shellSet.connect(R.id.mainPager, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            shellSet.clear(R.id.mainPager, ConstraintSet.BOTTOM);

            ViewGroup.LayoutParams flp = flexRoot.getLayoutParams();
            if (flp != null) {
                flp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                flexRoot.setLayoutParams(flp);
            }

            ViewGroup.LayoutParams mlp = mainPager.getLayoutParams();
            if (mlp != null) {
                mlp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mainPager.setLayoutParams(mlp);
            }
        }
        
        shellSet.applyTo(flexRoot);

        if (shuffleButton != null) shuffleButton.setVisibility(isProgressCollapsed ? View.GONE : View.VISIBLE);
        if (repeatButton != null) repeatButton.setVisibility(isProgressCollapsed ? View.GONE : View.VISIBLE);
        if (currentTimeText != null) currentTimeText.setVisibility(isProgressCollapsed ? View.GONE : View.VISIBLE);
        if (totalTimeText != null) totalTimeText.setVisibility(isProgressCollapsed ? View.GONE : View.VISIBLE);
        
        ViewGroup.LayoutParams contentLp = playerContent.getLayoutParams();
        if (contentLp != null) {
            contentLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            contentLp.height = isProgressCollapsed ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
            playerContent.setLayoutParams(contentLp);
        }

        ViewGroup.LayoutParams myLp = getLayoutParams();
        if (myLp != null) {
            int targetH = isProgressCollapsed ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
            if (myLp.height != targetH) {
                myLp.height = targetH;
                setLayoutParams(myLp);
            }
        }

        if (mainPager != null) {
            if (isProgressCollapsed) {
                mainPager.setCurrentPage(0);
                mainPager.setPagingEnabled(false);
            } else {
                mainPager.setPagingEnabled(true);
            }
        }
        
        if (albumArtContainer != null) {
            albumArtContainer.setScaleX(1.0f);
            albumArtContainer.setScaleY(1.0f);
        }

        if (musicEngine != null) {
            updateState(musicEngine.getMusic().getCurrentState());
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isProgressCollapsed && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                    MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void setupGestures() {
        this.gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (!isProgressCollapsed) return false;
                    if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                        try {
                            Context context = getContext();
                            String pkg = context.getPackageName().toLowerCase();
                            String managerClass = pkg.contains("musify") ? "com.xoeris.elarion.musify.utils.GlobalMiniPlayerManager" :
                                                 pkg.contains("hyperion") ? "com.xoeris.elarion.hyperion.utils.GlobalMiniPlayerManager" :
                                                 pkg.contains("filewave") ? "com.xoeris.elarion.filewave.utils.GlobalMiniPlayerManager" : null;
                            if (managerClass != null) {
                                Activity activity = null;
                                if (context instanceof Activity) activity = (Activity) context;
                                else if (context instanceof android.content.ContextWrapper) {
                                    Context base = ((android.content.ContextWrapper) context).getBaseContext();
                                    if (base instanceof Activity) activity = (Activity) base;
                                }
                                if (activity != null) {
                                    try {
                                        Class.forName(managerClass).getMethod("toggleChat", Activity.class, boolean.class).invoke(null, activity, diffX < 0);
                                    } catch (Exception e) {
                                        Class.forName(managerClass).getMethod("toggleChat", Activity.class).invoke(null, activity);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                        return true;
                    }
                } else {
                    if (Math.abs(diffY) > 150 && Math.abs(velocityY) > 150) {
                        if (mainPager != null && mainPager.getCurrentPage() != 0) {
                            return false;
                        }

                        if (diffY < 0 && isProgressCollapsed) {
                            toggleProgressVisibility(false);
                            return true;
                        } else if (diffY > 0 && !isProgressCollapsed) {
                            toggleProgressVisibility(true);
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Peak into the event for internal gesture detection
        if (gestureDetector != null) gestureDetector.onTouchEvent(ev);
        
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialTouchX = ev.getRawX();
                initialTouchY = ev.getRawY();
                isSwiping = false;
                downOnSeekBar = isTouchOnSeekBar(ev);
                fallbackSeekUsed = false;
                if (downOnSeekBar) {
                    Log.d("PlayerViewTouch", "down on seekBar collapsed=" + isProgressCollapsed);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                downOnSeekBar = false;
                fallbackSeekUsed = false;
                break;
            case MotionEvent.ACTION_MOVE:
                // An active seek owns the gesture: the bar handles seeking itself, so
                // intercepting here would race expand/pager swipes. Once the bar hands
                // a vertical drag back (see setAllowParentVerticalSteal), flow falls
                // through to the normal swipe logic below.
                if (progressBar != null && progressBar.isDragging()) {
                    return false;
                }
                float diffX = Math.abs(ev.getRawX() - initialTouchX);
                float diffY = Math.abs(ev.getRawY() - initialTouchY);

                if (diffX > 15 || diffY > 15) {
                    isSwiping = true;
                }
                
                if (!isProgressCollapsed) {
                    // In expanded mode, never intercept horizontal swipes so PagerLayout can swipe between pages
                    if (diffX > diffY) {
                        return false;
                    }
                    
                    int currentPage = mainPager != null ? mainPager.getCurrentPage() : 0;
                    if (currentPage != 0) {
                        // ON QUEUE/BIO/LYRICS: Never intercept vertical swipes, allow list scrolling
                        if (diffY > diffX && diffY > 15) {
                            return false; 
                        }
                    } else {
                        // ON PLAYER PAGE (Page 0)
                        if (diffY > diffX && diffY > SWIPE_THRESHOLD) {
                            // Intercept vertical swipe down to collapse
                            return true;
                        }
                    }
                } else {
                    if (diffX > SWIPE_THRESHOLD || diffY > SWIPE_THRESHOLD) {
                        return true;
                    }
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) gestureDetector.onTouchEvent(ev);
        boolean handled = super.onTouchEvent(ev);
        int action = ev.getAction();
        // Fallback seek: this view owns a gesture that started on the seek bar (the bar
        // itself never took it). Drive horizontal drags as seeks; leave vertical drags
        // to the normal swipe/expand flow.
        if (downOnSeekBar && progressBar != null
                && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP)) {
            float dx = Math.abs(ev.getRawX() - initialTouchX);
            float dy = Math.abs(ev.getRawY() - initialTouchY);
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (fallbackSeekUsed) {
                if (dx >= dy) {
                    progressBar.moveFallbackSeek(ev.getRawX());
                } else {
                    progressBar.endFallbackSeek();
                }
            } else if (dx > slop && dx >= dy) {
                progressBar.beginFallbackSeek(ev.getRawX());
                // begin no-ops if the bar took over meanwhile; only count real starts.
                fallbackSeekUsed = true;
                Log.d("PlayerViewTouch", "fallbackSeek begin collapsed=" + isProgressCollapsed);
            }
        }
        // The seek bar consumes its own taps/drags, so reaching here means the gesture
        // was not a seek, a clean tap toggles expand as usual. A fallback-driven seek
        // must never also expand.
        if (action == MotionEvent.ACTION_UP && !isSwiping && !fallbackSeekUsed
                && (progressBar == null || !progressBar.isDragging())) {
            performClick();
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (fallbackSeekUsed && progressBar != null) progressBar.endFallbackSeek();
            downOnSeekBar = false;
            fallbackSeekUsed = false;
        }
        return handled;
    }

    @Override
    public boolean performClick() {
        toggleExpand();
        return super.performClick();
    }

    public void toggleProgressVisibility(boolean collapsed) {
        if (isAnimating || isProgressCollapsed == collapsed) return;
        isAnimating = true;
        isProgressCollapsed = collapsed;

        if (glassWrapper != null) glassWrapper.setPauseUpdates(true);

        ViewGroup transitionRoot = findTransitionRoot();
        Transition transition = new androidx.transition.AutoTransition();
        transition.setDuration(300);
        transition.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        transition.addListener(new Transition.TransitionListener() {
            @Override public void onTransitionStart(@NonNull Transition t) {}
            @Override public void onTransitionEnd(@NonNull Transition t) { 
                isAnimating = false;
                if (glassWrapper != null) glassWrapper.setPauseUpdates(false);
            }
            @Override public void onTransitionCancel(@NonNull Transition t) { 
                isAnimating = false;
                if (glassWrapper != null) glassWrapper.setPauseUpdates(false);
            }
            @Override public void onTransitionPause(@NonNull Transition t) {}
            @Override public void onTransitionResume(@NonNull Transition t) {}
        });

        TransitionManager.beginDelayedTransition(transitionRoot, transition);
        applyLayoutState();
        if (expandStateChangeListener != null) {
            expandStateChangeListener.onExpandStateChanged(!isProgressCollapsed);
        }
    }

    public void toggleExpand() {
        toggleProgressVisibility(!isProgressCollapsed);
    }

    public void togglePanel(boolean expand) {
        toggleProgressVisibility(!expand);
    }

    private ViewGroup findTransitionRoot() {
        ViewParent parent = getParent();
        while (parent != null) {
            if (parent instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) parent;
                if (vg.getId() == android.R.id.content) return vg;
                if (vg.getClass().getSimpleName().equals("DecorView")) return vg;
            }
            parent = parent.getParent();
        }
        return (ViewGroup) getRootView();
    }

    public boolean isExpanded() {
        return !isProgressCollapsed;
    }

    public boolean isProgressCollapsed() {
        return isProgressCollapsed;
    }

    public boolean handleBackPressed() {
        if (!isProgressCollapsed) {
            toggleProgressVisibility(true);
            return true;
        }
        return false;
    }

    public int getReservedHeight() {
        return dpToPx(isProgressCollapsed ? 80 : 0);
    }

    public int getReservedTotalHeight() {
        if (getVisibility() != VISIBLE) return 0;
        
        int measuredHeight = getMeasuredHeight();
        if (measuredHeight > 0) return measuredHeight;

        if (isProgressCollapsed) {
            return dpToPx(180); // Realistic fallback for player_mini.xml
        }
        return 0; // If expanded but not measured, don't reserve space yet to avoid jumps
    }

    public void setBlurRootView(View view) {
        if (glassWrapper != null) glassWrapper.setBlurRootView(view);
        if (spectrumBackground != null) {
            spectrumBackground.invalidate();
        }
    }

    public void setOnExpandStateChangeListener(OnExpandStateChangeListener listener) {
        this.expandStateChangeListener = listener;
    }

    public void setOnClickListener(OnClickListener l) {
        this.externalClickListener = l;
    }

    private void updateProgress(Music.Progress progress) {
        if (progress == null || progressBar == null) return;
        
        if (!progressBar.isDragging()) {
            progressBar.setMax((int) progress.duration);
            progressBar.setProgress((int) progress.position);
        }

        if (currentTimeText != null) currentTimeText.setText(formatTime(progress.position));
        if (totalTimeText != null) totalTimeText.setText(formatTime(progress.duration));

        if (lyricsView != null) {
            lyricsView.updateProgress(progress.position, currentLyrics);
        }

        if (isUserScrollingLyrics && System.currentTimeMillis() - lastUserScrollTime > SCROLL_RESUME_DELAY) {
            isUserScrollingLyrics = false;
            if (btnSyncLyrics != null) btnSyncLyrics.setVisibility(GONE);
        }
    }

    private void updateState(Music.PlaybackState state) {
        if (playPauseButton == null) return;
        
        boolean isPlaying = state == Music.PlaybackState.PLAYING;

        if (!playerHiddenAttr || isPlaying) {
            setVisibility(VISIBLE);
        } else {
            setVisibility(GONE);
        }

        playPauseButton.setImageResource(isPlaying ? R.drawable.xoeris_pause : R.drawable.xoeris_play);
        
        if (spectrumBackground != null) {
            if (isPlaying) spectrumBackground.startAnimation();
            else spectrumBackground.stopAnimation();
        }

        if (musicEngine != null) {
            updateMetadata(musicEngine.getMusic().getCurrentMetadata());
            updateShuffleState(musicEngine.isShuffleEnabled());
            updateRepeatState(musicEngine.getQueue().getRepeatMode());
        }
    }

    private void updateShuffleState(boolean enabled) {
        if (shuffleButton == null) return;
        shuffleButton.setImageResource(R.drawable.xoeris_shuffle);
        shuffleButton.setColorFilter(ContextCompat.getColor(getContext(), 
                enabled ? R.color.xoeris_primary : R.color.xoeris_text_secondary));
    }

    private void updateRepeatState(Queue.RepeatMode mode) {
        if (repeatButton == null) return;
        int icon = R.drawable.xoeris_repeat;
        boolean enabled = mode != Queue.RepeatMode.NONE;
        if (mode == Queue.RepeatMode.ONE) icon = R.drawable.xoeris_repeat_one;
        repeatButton.setImageResource(icon);
        repeatButton.setColorFilter(ContextCompat.getColor(getContext(), 
                enabled ? R.color.xoeris_primary : R.color.xoeris_text_secondary));
    }

    private void updateMetadata(Metadata metadata) {
        if (metadata == null) return;

        if (trackTitle != null) trackTitle.setText(metadata.getTitle());
        if (trackArtist != null) trackArtist.setText(metadata.getArtist());

        if (albumArt != null) {
            Prism.with(getContext())
                .load(metadata.getArtUri())
                .apply(new PrismOptions()
                    .placeholder(R.drawable.xoeris_music_note, getContext())
                    .error(R.drawable.xoeris_music_note, getContext())
                    .preferRgb565())
                .into(albumArt);
        }

        if (musicEngine != null) {
            updateProgress(musicEngine.getMusic().getProgressSignal().getLastEvent());
            updateQueue();
        }

        reloadLyrics(metadata, false);
        updateArtistBio(metadata.getArtist(), metadata.getAlbum());
    }

    private final ExecutorService lyricExecutor = Executors.newSingleThreadExecutor();
    private Future<?> lyricTask;
    private final ExecutorService bioExecutor = Executors.newSingleThreadExecutor();
    private Future<?> bioTask;

    private void reloadLyrics(Metadata metadata, boolean force) {
        if (metadata == null) return;
        if (lyricTask != null) lyricTask.cancel(true);

        lyricTask = lyricExecutor.submit(() -> {
            String lrc = LyricsSystem.getLyrics(getContext(), metadata);
            if (lrc != null && !lrc.isEmpty()) {
                Lyrics lyrics = new Lyrics();
                lyrics.parse(lrc);
                if (!lyrics.getLines().isEmpty()) {
                    post(() -> {
                        currentLyrics = lyrics;
                        if (lyricsView != null) lyricsView.setLyrics(lyrics);
                        startWatchingLyrics(metadata);
                    });
                }
            }
        });
    }

    private void startWatchingLyrics(Metadata metadata) {
        stopWatchingLyrics();
        if (metadata.getPath() == null) return;
        
        File file = new File(metadata.getPath());
        String parent = file.getParent();
        if (parent == null) return;

        lyricFileObserver = new FileObserver(parent, FileObserver.MODIFY | FileObserver.CLOSE_WRITE) {
            @Override
            public void onEvent(int event, @Nullable String path) {
                if (path != null && path.endsWith(".lrc")) {
                    post(() -> reloadLyrics(metadata, true));
                }
            }
        };
        lyricFileObserver.startWatching();
    }

    private void stopWatchingLyrics() {
        if (lyricFileObserver != null) {
            lyricFileObserver.stopWatching();
            lyricFileObserver = null;
        }
    }

    private void updateArtistBio(String artist, String album) {
        if (artist == null) return;
        artist = artist.trim();
        if (artist.isEmpty() || artist.equals(currentBioArtist)) return;
        currentBioArtist = artist;
        if (bioPager == null) return;

        if (this.bioItemView == null || bioItemView.getParent() != bioPager) {
            bioPager.clearPages();
            this.bioItemView = addBioPage(artist, "Source: Neural Hunt", "Retrieving artist information...", null);
        } else {
            TextView titleView = bioItemView.findViewById(R.id.artistBioName);
            TextView subtitleView = bioItemView.findViewById(R.id.artistBioSource);
            TextView contentView = bioItemView.findViewById(R.id.artistBioText);
            ImageView imageView = bioItemView.findViewById(R.id.artistBioImage);
            if (titleView != null) titleView.setText(artist);
            if (subtitleView != null) subtitleView.setText("Source: Neural Hunt");
            if (contentView != null) contentView.setText("Retrieving artist information...");
            if (imageView != null) imageView.setImageResource(R.drawable.xoeris_person);
        }

        if (this.bioItemView == null) return;
        bioItemView.setVisibility(VISIBLE);
        bioPager.setCurrentPage(0);
        huntArtistIntelligence(artist, bioItemView);
    }

    private View addBioPage(String title, String subtitle, String content, String imageUrl) {
        if (bioPager == null) return null;

        View view = LayoutInflater.from(getContext()).inflate(R.layout.player_bio_item, bioPager, false);
        TextView titleView = view.findViewById(R.id.artistBioName);
        TextView subtitleView = view.findViewById(R.id.artistBioSource);
        TextView contentView = view.findViewById(R.id.artistBioText);
        ImageView imageView = view.findViewById(R.id.artistBioImage);

        if (titleView != null) titleView.setText(title);
        if (subtitleView != null) subtitleView.setText(subtitle);
        if (contentView != null) {
            contentView.setText(content);
            contentView.setSingleLine(false);
            contentView.setEllipsize(null);
            contentView.setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
        }

        if (imageView != null && imageUrl != null) {
            Prism.with(getContext()).load(imageUrl).into(imageView);
        }

        bioPager.addPage(view);
        return view;
    }

    private void huntArtistIntelligence(final String artist, final View bioView) {
        if (bioTask != null) bioTask.cancel(true);
        bioTask = bioExecutor.submit(() -> {
            ArtistInfo info;
            try {
                info = fetchFromWikipedia(artist);
                if (info == null) info = fetchFromMusicBrainz(artist);
            } catch (Exception fetchFailure) {
                return;
            }
            if (info == null) {
                info = new ArtistInfo();
                info.subtitle = "Neural Hunt";
                info.bio = "No biography found for " + artist
                        + ". The artist may not be listed online or the network is unavailable.";
            }
            final ArtistInfo result = info;
            post(() -> {
                if (!artist.equals(currentBioArtist)) return;
                applyArtistInfo(bioView, result);
            });
        });
    }

    private void applyArtistInfo(View bioView, ArtistInfo info) {
        if (bioView.getVisibility() != VISIBLE) bioView.setVisibility(VISIBLE);
        TextView subtitleView = bioView.findViewById(R.id.artistBioSource);
        TextView contentView = bioView.findViewById(R.id.artistBioText);
        ImageView imageView = bioView.findViewById(R.id.artistBioImage);

        if (subtitleView != null) subtitleView.setText("Source: " + info.subtitle);
        if (contentView != null) contentView.setText(info.bio);
        if (imageView != null && info.imageUrl != null && !info.imageUrl.isEmpty()) {
            Prism.with(getContext()).load(info.imageUrl).into(imageView);
        }
    }

    private ArtistInfo fetchFromWikipedia(String artist) throws Exception {
        String encoded = URLEncoder.encode(artist.replace(' ', '_'), "UTF-8");
        String body = httpGetString("https://en.wikipedia.org/api/rest_v1/page/summary/" + encoded);
        if (body == null) return null;

        JSONObject json = new JSONObject(body);
        if (!"standard".equals(json.optString("type"))) return null;

        String extract = json.optString("extract", "");
        if (extract.isEmpty()) return null;

        ArtistInfo info = new ArtistInfo();
        info.subtitle = "Wikipedia";
        info.bio = extract;
        JSONObject original = json.optJSONObject("originalimage");
        JSONObject thumb = json.optJSONObject("thumbnail");
        if (original != null) info.imageUrl = original.optString("source", null);
        if (info.imageUrl == null && thumb != null) info.imageUrl = thumb.optString("source", null);
        return info;
    }

    private ArtistInfo fetchFromMusicBrainz(String artist) throws Exception {
        String encoded = URLEncoder.encode(artist, "UTF-8");
        String body = httpGetString("https://musicbrainz.org/ws/2/artist?query=" + encoded + "&fmt=json&limit=1");
        if (body == null) return null;

        JSONObject json = new JSONObject(body);
        JSONArray artists = json.optJSONArray("artists");
        if (artists == null || artists.length() == 0) return null;

        JSONObject a = artists.getJSONObject(0);
        String name = a.optString("name", artist);
        String type = a.optString("type", "");
        String country = a.optString("country", "");
        JSONObject area = a.optJSONObject("area");
        if (country.isEmpty() && area != null) country = area.optString("name", "");
        JSONObject lifeSpan = a.optJSONObject("life-span");
        String begin = lifeSpan != null ? lifeSpan.optString("begin", "") : "";
        String end = lifeSpan != null ? lifeSpan.optString("end", "") : "";

        StringBuilder bio = new StringBuilder();
        bio.append(name);
        if ("Group".equalsIgnoreCase(type) || "Orchestra".equalsIgnoreCase(type) || "Choir".equalsIgnoreCase(type)) {
            bio.append(" is a musical group");
        } else if ("Person".equalsIgnoreCase(type)) {
            bio.append(" is a musician");
        } else if (!type.isEmpty()) {
            bio.append(" is a music act");
        }
        if (!country.isEmpty()) bio.append(" from ").append(country);
        boolean hasBegin = !begin.isEmpty();
        if (hasBegin) bio.append(", active since ").append(begin);
        if (!end.isEmpty()) {
            bio.append(" until ").append(end);
        } else if (hasBegin) {
            bio.append(" and still active");
        }
        bio.append('.');

        JSONArray tags = a.optJSONArray("tags");
        if (tags != null && tags.length() > 0) {
            StringBuilder genres = new StringBuilder();
            int limit = Math.min(3, tags.length());
            for (int i = 0; i < limit; i++) {
                String genre = tags.getJSONObject(i).optString("name", "");
                if (genre.isEmpty()) continue;
                if (genres.length() > 0) genres.append(", ");
                genres.append(genre);
            }
            if (genres.length() > 0) bio.append(" Genres: ").append(genres).append('.');
        }

        ArtistInfo info = new ArtistInfo();
        info.subtitle = "MusicBrainz";
        info.bio = bio.toString();
        return info;
    }

    private static String httpGetString(String urlString) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "XIME-MediaPlayerView/1.0 (Android music player)");
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) response.append(buffer, 0, read);
            reader.close();
            return response.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static final class ArtistInfo {
        String subtitle;
        String bio;
        String imageUrl;
    }

    public void setMetadata(String title, String artist, Bitmap art) {
        if (trackTitle != null) trackTitle.setText(title);
        if (trackArtist != null) trackArtist.setText(artist);
        if (albumArt != null && art != null) {
            albumArt.setImageBitmap(art);
        }
    }

    public void setPlaybackState(boolean playing) {
        if (playPauseButton != null) {
            playPauseButton.setImageResource(playing ? R.drawable.xoeris_pause : R.drawable.xoeris_play);
        }
        if (spectrumBackground != null) {
            if (playing) spectrumBackground.startAnimation();
            else spectrumBackground.stopAnimation();
        }
    }

    public void setProgress(long currentMs, long totalMs) {
        if (progressBar != null) {
            progressBar.setMax((int) totalMs);
            progressBar.setProgress((int) currentMs);
        }
    }

    private void updateQueue() {
        if (queueRecyclerView != null && musicEngine != null) {
            List<Metadata> queueItems = musicEngine.getQueue().getActiveQueue();
            xime.media.ui.MediaOrbitAdapter adapter = new xime.media.ui.MediaOrbitAdapter(new xime.media.ui.MediaOrbitAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(Metadata metadata, int position) {
                    musicEngine.getQueue().jumpTo(position);
                    musicEngine.getMusic().play(metadata);
                }

                @Override
                public void onItemMoreClick(Metadata metadata, View view) {
                    // Handle more options if needed
                }
            });
            adapter.setItems(queueItems);
            queueRecyclerView.setAdapter(adapter);
            
            if (queueStatusText != null) {
                queueStatusText.setText(String.format(Locale.getDefault(), "%d tracks", queueItems.size()));
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyLayoutState();
        // MusicEngine signals retained for Queue, Shuffle/Repeat, Lyrics (Internal-only concerns)
        if (musicEngine != null) {
            musicEngine.getMusic().getMetadataSignal().observe(metadataObserver);
            musicEngine.getShuffleSignal().observe(shuffleObserver);
            musicEngine.getRepeatSignal().observe(repeatObserver);
            musicEngine.getQueue().getQueueSignal().observe(queueObserver);
        }
        // PlaybackSession handles state + progress observation
        if (playbackSession != null) {
            playbackSession.addListener(sessionListener);
            playbackSession.attach();
            PlaybackSnapshot snap = playbackSession.getSnapshot();
            if (snap != null) applySnapshot(snap);
        } else if (musicEngine != null) {
            // Fallback: no session bound yet, observe directly from engine
            musicEngine.getMusic().getStateSignal().observe(stateObserver);
            musicEngine.getMusic().getProgressSignal().observe(progressObserver);
            updateMetadata(musicEngine.getMusic().getCurrentMetadata());
            updateState(musicEngine.getMusic().getCurrentState());
        }
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(spectrumVisibilityReceiver, new IntentFilter("xime.media.SPECTRUM_VISIBILITY_CHANGED"));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Detach PlaybackSession
        if (playbackSession != null) {
            playbackSession.removeListener(sessionListener);
            playbackSession.detach();
        }
        // Always clean up MusicEngine observers
        if (musicEngine != null) {
            musicEngine.getMusic().getStateSignal().removeObserver(stateObserver);
            musicEngine.getMusic().getProgressSignal().removeObserver(progressObserver);
            musicEngine.getMusic().getMetadataSignal().removeObserver(metadataObserver);
            musicEngine.getShuffleSignal().removeObserver(shuffleObserver);
            musicEngine.getRepeatSignal().removeObserver(repeatObserver);
            musicEngine.getQueue().getQueueSignal().removeObserver(queueObserver);
        }
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(spectrumVisibilityReceiver);
        stopWatchingLyrics();
        if (lyricTask != null) lyricTask.cancel(true);
        if (bioTask != null) bioTask.cancel(true);
    }

    private String formatTime(long ms) {
        int sec = (int) (ms / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static class LayoutFactory {

        enum Variant {
            MINI(true),
            COMPACT(false);

            final boolean isMini;

            Variant(boolean isMini) {
                this.isMini = isMini;
            }
        }

        private static int dpToPx(Context context, float dp) {
            return Math.round(dp * context.getResources().getDisplayMetrics().density);
        }

        private static int resolveAttrColor(Context context, int attrResId, int defaultColor) {
            TypedValue tv = new TypedValue();
            if (context.getTheme().resolveAttribute(attrResId, tv, true)) {
                return tv.data;
            }
            return defaultColor;
        }

        private static int resolveAttrResourceId(Context context, int attrResId, int defaultResId) {
            TypedValue tv = new TypedValue();
            if (context.getTheme().resolveAttribute(attrResId, tv, true)) {
                return tv.resourceId;
            }
            return defaultResId;
        }

        public static BlurLayout buildWidgetShell(Context context) {
            BlurLayout glassWrapper = new BlurLayout(context);
            glassWrapper.setId(R.id.playerGlassWrapper);
            glassWrapper.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            glassWrapper.setCornerRadius(dpToPx(context, 20));
            glassWrapper.setBlurRadius(50.0f);
            glassWrapper.setBlurType(BlurLayout.BlurType.GLASS);
            glassWrapper.setClipChildren(false);
            glassWrapper.setClipToPadding(false);

            ConstraintLayout flexRoot = new ConstraintLayout(context);
            flexRoot.setId(R.id.flexRoot);
            flexRoot.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            flexRoot.setClipChildren(false);
            flexRoot.setClipToPadding(false);

            SpectrumLayout spectrumBackground = new SpectrumLayout(context);
            spectrumBackground.setId(R.id.spectrumBackground);
            flexRoot.addView(spectrumBackground, new ConstraintLayout.LayoutParams(0, 0));

            PagerLayout mainPager = new PagerLayout(context);
            mainPager.setId(R.id.mainPager);
            mainPager.setClipChildren(false);
            mainPager.setClipToPadding(false);
            flexRoot.addView(mainPager, new ConstraintLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT));

            glassWrapper.addView(flexRoot);
            return glassWrapper;
        }

        public static ConstraintLayout buildPlayerContent(Context context, Variant variant) {
            ConstraintLayout root = new ConstraintLayout(context);
            root.setId(R.id.playerContent);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            root.setClipChildren(false);
            root.setClipToPadding(false);
            if (variant.isMini) {
                root.setPadding(0, 0, 0, 0);
            }

            // Album Art Container
            BlurView albumArtContainer = new BlurView(context);
            albumArtContainer.setId(R.id.albumArtContainer);
            ConstraintLayout.LayoutParams artContainerLp = new ConstraintLayout.LayoutParams(
                    dpToPx(context, 54), dpToPx(context, 54));
            artContainerLp.leftMargin = dpToPx(context, 16);
            artContainerLp.setMarginStart(dpToPx(context, 16));
            artContainerLp.topMargin = dpToPx(context, 16);
            albumArtContainer.setLayoutParams(artContainerLp);
            albumArtContainer.setCornerRadius(dpToPx(context, 14));
            albumArtContainer.setBlurRadius(10.0f);
            albumArtContainer.setTransitionName("mini_art_morph");

            PictureView albumArt = new PictureView(context);
            albumArt.setId(R.id.albumArt);
            albumArt.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            albumArt.setImageResource(R.drawable.xoeris_music_note);
            albumArt.setTransitionName("mini_art_image_morph");
            albumArtContainer.addView(albumArt);
            root.addView(albumArtContainer);

            // Track Title
            TextView trackTitle = new TextView(context);
            trackTitle.setId(R.id.trackTitle);
            ConstraintLayout.LayoutParams titleLp = new ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleLp.leftMargin = dpToPx(context, 16);
            titleLp.setMarginStart(dpToPx(context, 16));
            titleLp.rightMargin = dpToPx(context, 16);
            titleLp.setMarginEnd(dpToPx(context, 16));
            trackTitle.setLayoutParams(titleLp);
            trackTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
            trackTitle.setSingleLine(true);
            trackTitle.setText(R.string.no_track_playing);
            trackTitle.setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
            trackTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
            trackTitle.setTypeface(trackTitle.getTypeface(), android.graphics.Typeface.BOLD);
            trackTitle.setTransitionName("mini_title_morph");
            root.addView(trackTitle);

            // Artist Name
            TextView artistName = new TextView(context);
            artistName.setId(R.id.artistName);
            ConstraintLayout.LayoutParams artistLp = new ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            artistLp.leftMargin = dpToPx(context, 16);
            artistLp.setMarginStart(dpToPx(context, 16));
            artistLp.rightMargin = dpToPx(context, 16);
            artistLp.setMarginEnd(dpToPx(context, 16));
            artistName.setLayoutParams(artistLp);
            artistName.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
            artistName.setSingleLine(true);
            artistName.setText(R.string.unknown_artist);
            artistName.setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666));
            artistName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
            artistName.setTransitionName("mini_artist_morph");
            root.addView(artistName);

            // Current Time Text
            TextView currentTimeText = new TextView(context);
            currentTimeText.setId(R.id.currentTimeText);
            ConstraintLayout.LayoutParams timeLp = new ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            timeLp.rightMargin = dpToPx(context, 8);
            timeLp.setMarginEnd(dpToPx(context, 8));
            currentTimeText.setLayoutParams(timeLp);
            currentTimeText.setText("0:00");
            currentTimeText.setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666));
            currentTimeText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f);
            currentTimeText.setVisibility(View.GONE);
            root.addView(currentTimeText);

            // Control Container
            android.widget.LinearLayout controlContainer = new android.widget.LinearLayout(context);
            controlContainer.setId(R.id.controlContainer);
            ConstraintLayout.LayoutParams controlLp = new ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            controlLp.topMargin = dpToPx(context, 16);
            controlContainer.setLayoutParams(controlLp);
            controlContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            controlContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
            controlContainer.setVisibility(View.VISIBLE);

            int selectableBg = resolveAttrResourceId(context, androidx.appcompat.R.attr.selectableItemBackgroundBorderless, 0);
            int primaryColor = resolveAttrColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFFFFFFFF);
            if (primaryColor == 0) primaryColor = 0xFFFFFFFF;

            // Shuffle Button
            IconButton shuffleBtn = new IconButton(context);
            shuffleBtn.setId(R.id.shuffleButton);
            shuffleBtn.setLayoutParams(new ViewGroup.LayoutParams(dpToPx(context, 40), dpToPx(context, 40)));
            shuffleBtn.setVisibility(View.GONE);
            if (selectableBg != 0) shuffleBtn.setBackgroundResource(selectableBg);
            shuffleBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            shuffleBtn.setImageDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.xoeris_shuffle));
            controlContainer.addView(shuffleBtn);

            // Previous Button
            androidx.appcompat.widget.AppCompatImageView prevBtn = new androidx.appcompat.widget.AppCompatImageView(context);
            prevBtn.setId(R.id.previousButton);
            prevBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dpToPx(context, 36), dpToPx(context, 36)));
            if (selectableBg != 0) prevBtn.setBackgroundResource(selectableBg);
            prevBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            prevBtn.setImageResource(R.drawable.xoeris_skip_previous);
            prevBtn.setColorFilter(primaryColor);
            controlContainer.addView(prevBtn);

            // Play/Pause Button
            androidx.appcompat.widget.AppCompatImageView playPauseBtn = new androidx.appcompat.widget.AppCompatImageView(context);
            playPauseBtn.setId(R.id.playPauseButton);
            android.widget.LinearLayout.LayoutParams playLp = new android.widget.LinearLayout.LayoutParams(dpToPx(context, 36), dpToPx(context, 36));
            playLp.leftMargin = dpToPx(context, 4);
            playLp.rightMargin = dpToPx(context, 4);
            playPauseBtn.setLayoutParams(playLp);
            if (selectableBg != 0) playPauseBtn.setBackgroundResource(selectableBg);
            playPauseBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            playPauseBtn.setImageResource(R.drawable.xoeris_play);
            playPauseBtn.setColorFilter(primaryColor);
            controlContainer.addView(playPauseBtn);

            // Next Button
            androidx.appcompat.widget.AppCompatImageView nextBtn = new androidx.appcompat.widget.AppCompatImageView(context);
            nextBtn.setId(R.id.nextButton);
            nextBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dpToPx(context, 36), dpToPx(context, 36)));
            if (selectableBg != 0) nextBtn.setBackgroundResource(selectableBg);
            nextBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            nextBtn.setImageResource(R.drawable.xoeris_skip_next);
            nextBtn.setColorFilter(primaryColor);
            controlContainer.addView(nextBtn);

            // Repeat Button
            IconButton repeatBtn = new IconButton(context);
            repeatBtn.setId(R.id.repeatButton);
            repeatBtn.setLayoutParams(new ViewGroup.LayoutParams(dpToPx(context, 40), dpToPx(context, 40)));
            repeatBtn.setVisibility(View.GONE);
            if (selectableBg != 0) repeatBtn.setBackgroundResource(selectableBg);
            repeatBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            repeatBtn.setImageDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.xoeris_repeat));
            controlContainer.addView(repeatBtn);

            root.addView(controlContainer);

            // Total Time Text
            TextView totalTimeText = new TextView(context);
            totalTimeText.setId(R.id.totalTimeText);
            ConstraintLayout.LayoutParams totalTimeLp = new ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (variant.isMini) {
                totalTimeLp.leftMargin = dpToPx(context, 8);
                totalTimeLp.setMarginStart(dpToPx(context, 8));
            }
            totalTimeText.setLayoutParams(totalTimeLp);
            totalTimeText.setText("0:00");
            totalTimeText.setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666));
            totalTimeText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f);
            totalTimeText.setVisibility(View.GONE);
            root.addView(totalTimeText);

            // Linear Track Bar
            LinearProgressBar trackBar = new LinearProgressBar(context);
            trackBar.setId(R.id.linearTrackBar);
            ConstraintLayout.LayoutParams barLp = new ConstraintLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT);
            barLp.topMargin = dpToPx(context, 20);
            barLp.leftMargin = dpToPx(context, 16);
            barLp.setMarginStart(dpToPx(context, 16));
            barLp.rightMargin = dpToPx(context, 16);
            barLp.setMarginEnd(dpToPx(context, 16));
            barLp.bottomMargin = dpToPx(context, variant.isMini ? 20 : 24);
            trackBar.setLayoutParams(barLp);
            trackBar.setVisibility(View.VISIBLE);
            trackBar.setProgressStyle(2);
            trackBar.setProgressHeight(dpToPx(context, 10));
            trackBar.setBarHeight(dpToPx(context, 3));
            trackBar.setShowIndicator(!variant.isMini);
            trackBar.setLabel(variant.isMini ? "" : "Now Playing");
            trackBar.setLabelAlignWithIndicator(true);
            trackBar.setProgressColor(ContextCompat.getColor(context, R.color.xoeris_primary));
            trackBar.setTrackColor(resolveAttrColor(context, com.google.android.material.R.attr.colorSurfaceVariant, 0xFFE0E0E0));
            trackBar.setIndicatorColor(ContextCompat.getColor(context, R.color.xoeris_primary));
            trackBar.setProgress(0);
            trackBar.setMax(100);
            root.addView(trackBar);

            return root;
        }

        public static View buildQueuePage(Context context) {
            ConstraintLayout root = new ConstraintLayout(context);
            root.setId(R.id.queuePage);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            android.widget.LinearLayout queueHeader = new android.widget.LinearLayout(context);
            queueHeader.setId(R.id.queueHeader);
            queueHeader.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            queueHeader.setPadding(dpToPx(context, 24), dpToPx(context, 24), dpToPx(context, 24), dpToPx(context, 24));
            ConstraintLayout.LayoutParams headerLp = new ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            headerLp.topToTop = ConstraintSet.PARENT_ID;
            headerLp.startToStart = ConstraintSet.PARENT_ID;
            headerLp.endToEnd = ConstraintSet.PARENT_ID;
            queueHeader.setLayoutParams(headerLp);

            TextView queueTitle = new TextView(context);
            queueTitle.setId(R.id.queueTitle);
            android.widget.LinearLayout.LayoutParams titleLp = new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            queueTitle.setLayoutParams(titleLp);
            queueTitle.setText("Playing Queue");
            queueTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f);
            queueTitle.setTypeface(queueTitle.getTypeface(), android.graphics.Typeface.BOLD);
            queueTitle.setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
            queueHeader.addView(queueTitle);

            TextView queueStatus = new TextView(context);
            queueStatus.setId(R.id.queueStatus);
            queueStatus.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            queueStatus.setText("0 tracks");
            queueStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
            queueStatus.setAlpha(0.7f);
            queueStatus.setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
            queueHeader.addView(queueStatus);

            root.addView(queueHeader);

            ReflectLayout sineReflect = new ReflectLayout(context);
            sineReflect.setId(R.id.queueSineReflect);
            sineReflect.setOrientation(ReflectLayout.ORIENTATION_VERTICAL);
            ConstraintLayout.LayoutParams sineLp = new ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0);
            sineLp.topToBottom = R.id.queueHeader;
            sineLp.bottomToBottom = ConstraintSet.PARENT_ID;
            sineLp.startToStart = ConstraintSet.PARENT_ID;
            sineLp.endToEnd = ConstraintSet.PARENT_ID;
            int marginHorizontal = dpToPx(context, 8);
            sineLp.leftMargin = marginHorizontal;
            sineLp.rightMargin = marginHorizontal;
            sineReflect.setLayoutParams(sineLp);

            OrbitLayout recyclerView = new OrbitLayout(context);
            recyclerView.setId(R.id.queueRecyclerView);
            recyclerView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            sineReflect.addView(recyclerView);

            root.addView(sineReflect);
            return root;
        }

        public static View buildLyricsPage(Context context) {
            android.widget.FrameLayout root = new android.widget.FrameLayout(context);
            root.setId(R.id.lyricsPageContainer);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            ReflectLayout sineReflect = new ReflectLayout(context);
            sineReflect.setId(R.id.lyricsSineReflect);
            sineReflect.setOrientation(ReflectLayout.ORIENTATION_VERTICAL);
            sineReflect.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            FlowLayout lyricsPage = new FlowLayout(context);
            lyricsPage.setId(R.id.lyricsPage);
            int padding = dpToPx(context, 32);
            lyricsPage.setPadding(padding, padding, padding, padding);
            lyricsPage.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            LyricsView lyricsView = new LyricsView(context);
            lyricsView.setId(R.id.lyricsView);
            lyricsView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            lyricsPage.addView(lyricsView);

            sineReflect.addView(lyricsPage);
            root.addView(sineReflect);

            IconButton btnSync = new IconButton(context);
            btnSync.setId(R.id.btnSyncLyrics);
            android.widget.FrameLayout.LayoutParams btnLp = new android.widget.FrameLayout.LayoutParams(
                    dpToPx(context, 48), dpToPx(context, 48));
            btnLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            btnLp.rightMargin = dpToPx(context, 24);
            btnLp.bottomMargin = dpToPx(context, 24);
            btnSync.setLayoutParams(btnLp);
            btnSync.setImageDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.xoeris_lyrics));
            btnSync.setVisibility(View.GONE);
            root.addView(btnSync);

            return root;
        }

        public static View buildBioPage(Context context) {
            PagerLayout bioPager = new PagerLayout(context);
            bioPager.setId(R.id.bioPager);
            bioPager.setOrientation(PagerLayout.ORIENTATION_HORIZONTAL);
            bioPager.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            return bioPager;
        }

        public static ConstraintLayout buildExpandContent(Context context) {
            ConstraintLayout root = new ConstraintLayout(context);
            root.setId(R.id.playerContent);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // Album Art Container
            BlurView albumArtContainer = new BlurView(context);
            albumArtContainer.setId(R.id.albumArtContainer);
            ConstraintLayout.LayoutParams artLp = new ConstraintLayout.LayoutParams(0, 0);
            artLp.topMargin = dpToPx(context, 32);
            albumArtContainer.setLayoutParams(artLp);
            albumArtContainer.setCornerRadius(dpToPx(context, 48));
            albumArtContainer.setBlurRadius(25.0f);
            albumArtContainer.setTransitionName("mini_art_morph");

            PictureView albumArt = new PictureView(context);
            albumArt.setId(R.id.albumArt);
            albumArt.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            albumArt.setImageResource(R.drawable.xoeris_music_note);
            albumArt.setTransitionName("mini_art_image_morph");
            albumArtContainer.addView(albumArt);
            root.addView(albumArtContainer);

            // Track Title
            TextView trackTitle = new TextView(context);
            trackTitle.setId(R.id.trackTitle);
            ConstraintLayout.LayoutParams titleLp = new ConstraintLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleLp.topMargin = dpToPx(context, 24);
            titleLp.leftMargin = dpToPx(context, 32);
            titleLp.setMarginStart(dpToPx(context, 32));
            titleLp.rightMargin = dpToPx(context, 32);
            titleLp.setMarginEnd(dpToPx(context, 32));
            trackTitle.setLayoutParams(titleLp);
            trackTitle.setGravity(android.view.Gravity.CENTER);
            trackTitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            trackTitle.setText(R.string.no_track_playing);
            trackTitle.setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
            trackTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32f);
            trackTitle.setTypeface(trackTitle.getTypeface(), android.graphics.Typeface.BOLD);
            trackTitle.setTransitionName("mini_title_morph");
            root.addView(trackTitle);

            // Artist Name
            TextView artistName = new TextView(context);
            artistName.setId(R.id.artistName);
            ConstraintLayout.LayoutParams artistLp = new ConstraintLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT);
            artistLp.topMargin = dpToPx(context, 8);
            artistLp.leftMargin = dpToPx(context, 32);
            artistLp.setMarginStart(dpToPx(context, 32));
            artistLp.rightMargin = dpToPx(context, 32);
            artistLp.setMarginEnd(dpToPx(context, 32));
            artistName.setLayoutParams(artistLp);
            artistName.setGravity(android.view.Gravity.CENTER);
            artistName.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            artistName.setText(R.string.unknown_artist);
            artistName.setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666));
            artistName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f);
            artistName.setTransitionName("mini_artist_morph");
            root.addView(artistName);

            // Controls
            xime.ui.layout.LinearLayout controlContainer = new xime.ui.layout.LinearLayout(context);
            controlContainer.setId(R.id.controlContainer);
            ConstraintLayout.LayoutParams controlLp = new ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            controlLp.topMargin = dpToPx(context, 32);
            controlContainer.setLayoutParams(controlLp);
            controlContainer.setOrientation(xime.ui.layout.LinearLayout.HORIZONTAL);
            controlContainer.setGravity(android.view.Gravity.CENTER);

            int selectableBg = resolveAttrResourceId(context, androidx.appcompat.R.attr.selectableItemBackgroundBorderless, 0);
            int primaryColor = resolveAttrColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFFD81B60);

            IconButton shuffleBtn = new IconButton(context);
            shuffleBtn.setId(R.id.shuffleButton);
            shuffleBtn.setLayoutParams(new xime.ui.layout.LinearLayout.LayoutParams(dpToPx(context, 50), dpToPx(context, 50)));
            if (selectableBg != 0) shuffleBtn.setBackgroundResource(selectableBg);
            shuffleBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            shuffleBtn.setImageDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.xoeris_shuffle));
            controlContainer.addView(shuffleBtn);

            IconButton prevBtn = new IconButton(context);
            prevBtn.setId(R.id.previousButton);
            xime.ui.layout.LinearLayout.LayoutParams prevLp = new xime.ui.layout.LinearLayout.LayoutParams(dpToPx(context, 50), dpToPx(context, 50));
            prevLp.leftMargin = dpToPx(context, 16);
            prevLp.setMarginStart(dpToPx(context, 16));
            prevBtn.setLayoutParams(prevLp);
            if (selectableBg != 0) prevBtn.setBackgroundResource(selectableBg);
            prevBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            prevBtn.setImageDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.xoeris_skip_previous));
            androidx.core.widget.ImageViewCompat.setImageTintList(prevBtn, android.content.res.ColorStateList.valueOf(primaryColor));
            controlContainer.addView(prevBtn);

            IconButton playPauseBtn = new IconButton(context);
            playPauseBtn.setId(R.id.playPauseButton);
            xime.ui.layout.LinearLayout.LayoutParams playLp = new xime.ui.layout.LinearLayout.LayoutParams(dpToPx(context, 50), dpToPx(context, 50));
            playLp.leftMargin = dpToPx(context, 16);
            playLp.rightMargin = dpToPx(context, 16);
            playLp.setMarginStart(dpToPx(context, 16));
            playLp.setMarginEnd(dpToPx(context, 16));
            playPauseBtn.setLayoutParams(playLp);
            if (selectableBg != 0) playPauseBtn.setBackgroundResource(selectableBg);
            playPauseBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            playPauseBtn.setImageDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.xoeris_play));
            androidx.core.widget.ImageViewCompat.setImageTintList(playPauseBtn, android.content.res.ColorStateList.valueOf(primaryColor));
            controlContainer.addView(playPauseBtn);

            IconButton nextBtn = new IconButton(context);
            nextBtn.setId(R.id.nextButton);
            xime.ui.layout.LinearLayout.LayoutParams nextLp = new xime.ui.layout.LinearLayout.LayoutParams(dpToPx(context, 50), dpToPx(context, 50));
            nextLp.rightMargin = dpToPx(context, 16);
            nextLp.setMarginEnd(dpToPx(context, 16));
            nextBtn.setLayoutParams(nextLp);
            if (selectableBg != 0) nextBtn.setBackgroundResource(selectableBg);
            nextBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            nextBtn.setImageDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.xoeris_skip_next));
            androidx.core.widget.ImageViewCompat.setImageTintList(nextBtn, android.content.res.ColorStateList.valueOf(primaryColor));
            controlContainer.addView(nextBtn);

            IconButton repeatBtn = new IconButton(context);
            repeatBtn.setId(R.id.repeatButton);
            repeatBtn.setLayoutParams(new xime.ui.layout.LinearLayout.LayoutParams(dpToPx(context, 50), dpToPx(context, 50)));
            if (selectableBg != 0) repeatBtn.setBackgroundResource(selectableBg);
            repeatBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            repeatBtn.setImageDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.xoeris_repeat));
            controlContainer.addView(repeatBtn);

            root.addView(controlContainer);

            // Progress Bar
            LinearProgressBar trackBar = new LinearProgressBar(context);
            trackBar.setId(R.id.linearTrackBar);
            ConstraintLayout.LayoutParams barLp = new ConstraintLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT);
            barLp.topMargin = dpToPx(context, 24);
            barLp.leftMargin = dpToPx(context, 24);
            barLp.setMarginStart(dpToPx(context, 24));
            barLp.rightMargin = dpToPx(context, 24);
            barLp.setMarginEnd(dpToPx(context, 24));
            trackBar.setLayoutParams(barLp);
            trackBar.setVisibility(View.VISIBLE);
            trackBar.setProgressStyle(2);
            trackBar.setProgressHeight(dpToPx(context, 14));
            trackBar.setBarHeight(dpToPx(context, 4));
            trackBar.setShowIndicator(true);
            trackBar.setLabel("Now Playing");
            trackBar.setLabelAlignWithIndicator(true);
            trackBar.setProgressColor(ContextCompat.getColor(context, R.color.xoeris_primary));
            trackBar.setTrackColor(resolveAttrColor(context, com.google.android.material.R.attr.colorSurfaceVariant, 0xFFE0E0E0));
            trackBar.setIndicatorColor(ContextCompat.getColor(context, R.color.xoeris_primary));
            trackBar.setProgress(0);
            trackBar.setMax(100);
            root.addView(trackBar);

            // Time Container
            xime.ui.layout.LinearLayout timeContainer = new xime.ui.layout.LinearLayout(context);
            timeContainer.setId(R.id.timeContainer);
            ConstraintLayout.LayoutParams timeLp = new ConstraintLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT);
            timeLp.topMargin = dpToPx(context, 4);
            timeLp.bottomMargin = dpToPx(context, 16);
            timeLp.leftMargin = dpToPx(context, 24);
            timeLp.setMarginStart(dpToPx(context, 24));
            timeLp.rightMargin = dpToPx(context, 24);
            timeLp.setMarginEnd(dpToPx(context, 24));
            timeContainer.setLayoutParams(timeLp);
            timeContainer.setOrientation(xime.ui.layout.LinearLayout.HORIZONTAL);
            timeContainer.setGravity(android.view.Gravity.CENTER);

            TextView currentTimeText = new TextView(context);
            currentTimeText.setId(R.id.currentTimeText);
            currentTimeText.setLayoutParams(new xime.ui.layout.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            currentTimeText.setText("0:00");
            currentTimeText.setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666));
            currentTimeText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
            currentTimeText.setVisibility(View.VISIBLE);
            timeContainer.addView(currentTimeText);

            GapView gapView = new GapView(context);
            xime.ui.layout.LinearLayout.LayoutParams spaceLp = new xime.ui.layout.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0);
            spaceLp.weight = 1.0f;
            gapView.setLayoutParams(spaceLp);
            timeContainer.addView(gapView);

            TextView totalTimeText = new TextView(context);
            totalTimeText.setId(R.id.totalTimeText);
            totalTimeText.setLayoutParams(new xime.ui.layout.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            totalTimeText.setText("0:00");
            totalTimeText.setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666));
            totalTimeText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
            totalTimeText.setVisibility(View.VISIBLE);
            timeContainer.addView(totalTimeText);

            root.addView(timeContainer);

            return root;
        }
    }

    private static class ConstraintSets {

        private static int dpToPx(Context context, float dp) {
            return Math.round(dp * context.getResources().getDisplayMetrics().density);
        }

        public static ConstraintSet buildWidgetSet() {
            ConstraintSet set = new ConstraintSet();
            
            set.connect(R.id.spectrumBackground, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            set.connect(R.id.spectrumBackground, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            set.connect(R.id.spectrumBackground, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            set.connect(R.id.spectrumBackground, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            set.constrainWidth(R.id.spectrumBackground, ConstraintSet.MATCH_CONSTRAINT);
            set.constrainHeight(R.id.spectrumBackground, ConstraintSet.MATCH_CONSTRAINT);

            set.connect(R.id.mainPager, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            set.connect(R.id.mainPager, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            set.connect(R.id.mainPager, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            set.connect(R.id.mainPager, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            set.constrainWidth(R.id.mainPager, ConstraintSet.MATCH_CONSTRAINT);
            set.constrainHeight(R.id.mainPager, ConstraintSet.MATCH_CONSTRAINT);

            return set;
        }

        public static ConstraintSet buildCompactSet(Context context, LayoutFactory.Variant variant) {
            ConstraintSet set = new ConstraintSet();

            int margin16 = dpToPx(context, 16);
            int margin8 = dpToPx(context, 8);

            set.connect(R.id.albumArtContainer, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, margin16);
            set.connect(R.id.albumArtContainer, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, margin16);
            set.constrainWidth(R.id.albumArtContainer, dpToPx(context, 54));
            set.constrainHeight(R.id.albumArtContainer, dpToPx(context, 54));

            // Control Container positioned to the right of the text elements
            set.connect(R.id.controlContainer, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, margin16);
            set.connect(R.id.controlContainer, ConstraintSet.TOP, R.id.albumArtContainer, ConstraintSet.TOP);
            set.connect(R.id.controlContainer, ConstraintSet.BOTTOM, R.id.albumArtContainer, ConstraintSet.BOTTOM);
            set.constrainWidth(R.id.controlContainer, ConstraintSet.WRAP_CONTENT);
            set.constrainHeight(R.id.controlContainer, ConstraintSet.WRAP_CONTENT);

            // Track Title (Between albumArtContainer and controlContainer)
            set.connect(R.id.trackTitle, ConstraintSet.START, R.id.albumArtContainer, ConstraintSet.END, margin16);
            set.connect(R.id.trackTitle, ConstraintSet.END, R.id.controlContainer, ConstraintSet.START, margin8);
            set.connect(R.id.trackTitle, ConstraintSet.TOP, R.id.albumArtContainer, ConstraintSet.TOP);
            set.connect(R.id.trackTitle, ConstraintSet.BOTTOM, R.id.artistName, ConstraintSet.TOP);
            set.setVerticalChainStyle(R.id.trackTitle, ConstraintSet.CHAIN_PACKED);
            set.constrainWidth(R.id.trackTitle, ConstraintSet.MATCH_CONSTRAINT);

            // Artist Name (Between albumArtContainer and controlContainer)
            set.connect(R.id.artistName, ConstraintSet.START, R.id.albumArtContainer, ConstraintSet.END, margin16);
            set.connect(R.id.artistName, ConstraintSet.END, R.id.controlContainer, ConstraintSet.START, margin8);
            set.connect(R.id.artistName, ConstraintSet.TOP, R.id.trackTitle, ConstraintSet.BOTTOM);
            set.connect(R.id.artistName, ConstraintSet.BOTTOM, R.id.albumArtContainer, ConstraintSet.BOTTOM);
            set.constrainWidth(R.id.artistName, ConstraintSet.MATCH_CONSTRAINT);

            // Linear Track Bar
            set.connect(R.id.linearTrackBar, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, margin16);
            set.connect(R.id.linearTrackBar, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, margin16);
            set.connect(R.id.linearTrackBar, ConstraintSet.TOP, R.id.albumArtContainer, ConstraintSet.BOTTOM, dpToPx(context, 20));
            set.connect(R.id.linearTrackBar, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, dpToPx(context, 20));
            set.constrainWidth(R.id.linearTrackBar, ConstraintSet.MATCH_CONSTRAINT);

            return set;
        }

        public static ConstraintSet buildExpandSet(Context context) {
            ConstraintSet set = new ConstraintSet();

            int margin32 = dpToPx(context, 32);
            int margin24 = dpToPx(context, 24);
            int margin16 = dpToPx(context, 16);
            int margin8 = dpToPx(context, 8);

            // Album Art Container (large rounded square filling top area)
            set.connect(R.id.albumArtContainer, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, margin24);
            set.connect(R.id.albumArtContainer, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, margin24);
            set.connect(R.id.albumArtContainer, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, margin24);
            set.connect(R.id.albumArtContainer, ConstraintSet.BOTTOM, R.id.trackTitle, ConstraintSet.TOP, margin24);
            set.constrainWidth(R.id.albumArtContainer, ConstraintSet.MATCH_CONSTRAINT);
            set.constrainHeight(R.id.albumArtContainer, 0);

            // Track Title (Left-aligned under album art)
            set.connect(R.id.trackTitle, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, margin24);
            set.connect(R.id.trackTitle, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, margin24);
            set.connect(R.id.trackTitle, ConstraintSet.BOTTOM, R.id.artistName, ConstraintSet.TOP, margin8);
            set.constrainWidth(R.id.trackTitle, ConstraintSet.MATCH_CONSTRAINT);
            set.constrainHeight(R.id.trackTitle, ConstraintSet.WRAP_CONTENT);

            // Artist Name (Left-aligned under track title)
            set.connect(R.id.artistName, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, margin24);
            set.connect(R.id.artistName, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, margin24);
            set.connect(R.id.artistName, ConstraintSet.BOTTOM, R.id.controlContainer, ConstraintSet.TOP, margin24);
            set.constrainWidth(R.id.artistName, ConstraintSet.MATCH_CONSTRAINT);
            set.constrainHeight(R.id.artistName, ConstraintSet.WRAP_CONTENT);

            // Control Container (Centered horizontally)
            set.connect(R.id.controlContainer, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            set.connect(R.id.controlContainer, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            set.connect(R.id.controlContainer, ConstraintSet.BOTTOM, R.id.linearTrackBar, ConstraintSet.TOP, margin24);
            set.constrainWidth(R.id.controlContainer, ConstraintSet.WRAP_CONTENT);
            set.constrainHeight(R.id.controlContainer, ConstraintSet.WRAP_CONTENT);

            // Current Time Text (Aligned to left margin)
            set.connect(R.id.currentTimeText, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, margin24);
            set.connect(R.id.currentTimeText, ConstraintSet.TOP, R.id.controlContainer, ConstraintSet.TOP);
            set.connect(R.id.currentTimeText, ConstraintSet.BOTTOM, R.id.controlContainer, ConstraintSet.BOTTOM);
            set.constrainWidth(R.id.currentTimeText, ConstraintSet.WRAP_CONTENT);
            set.constrainHeight(R.id.currentTimeText, ConstraintSet.WRAP_CONTENT);

            // Total Time Text (Aligned to right margin)
            set.connect(R.id.totalTimeText, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, margin24);
            set.connect(R.id.totalTimeText, ConstraintSet.TOP, R.id.controlContainer, ConstraintSet.TOP);
            set.connect(R.id.totalTimeText, ConstraintSet.BOTTOM, R.id.controlContainer, ConstraintSet.BOTTOM);
            set.constrainWidth(R.id.totalTimeText, ConstraintSet.WRAP_CONTENT);
            set.constrainHeight(R.id.totalTimeText, ConstraintSet.WRAP_CONTENT);

            // Linear Track Bar (At the bottom)
            set.connect(R.id.linearTrackBar, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, margin24);
            set.connect(R.id.linearTrackBar, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, margin24);
            set.connect(R.id.linearTrackBar, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, margin24);
            set.constrainWidth(R.id.linearTrackBar, ConstraintSet.MATCH_CONSTRAINT);
            set.constrainHeight(R.id.linearTrackBar, ConstraintSet.WRAP_CONTENT);

            return set;
        }

        private static int margin4To8(Context context) {
            return dpToPx(context, 4);
        }
    }
}


