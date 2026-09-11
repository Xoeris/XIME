package xime.haptic;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashMap;
import java.util.Map;

/**
 * * Provides audible feedback for scroll events and interactions.
 */
public class HapticSoundEffect {
    private static final int MAX_STREAMS = 8;
    private static final long MIN_INTERVAL_MS = 30;
    private static final float RATE_BASE = 0.98f;
    private static final float RATE_VARIANCE = 0.1f;
    private static final String TAG = "HapticSoundEffect";
    private static final float VOLUME = 1.0f;
    
    private int soundId;
    private SoundPool soundPool;
    private boolean loaded = false;
    private long lastPopTime = 0;
    private final Map<RecyclerView, VisibleRange> attachedViews = new HashMap<>();

    private static class VisibleRange {
        int first = -1;
        int last = -1;
    }

    public HapticSoundEffect(Context context) {
        this.soundId = -1;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        
        this.soundPool = new SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attrs)
                .build();
        
        this.soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            if (status == 0) {
                this.loaded = true;
                Log.d(TAG, "SoundPool loaded successfully. Sample ID: " + sampleId);
            } else {
                Log.e(TAG, "SoundPool failed to load sample. Status: " + status);
            }
        });
        
        // Use R.raw if available, or try identifying by name
        int resId = context.getResources().getIdentifier("xoeris_bubble_01", "raw", context.getPackageName());
        
        if (resId != 0) {
            this.soundId = this.soundPool.load(context, resId, 1);
        } else {
            Log.w(TAG, "Optional sound resource 'xoeris_bubble_01' not found.");
        }
    }

    public void attachTo(RecyclerView rv) {
        if (this.attachedViews.containsKey(rv)) return;

        VisibleRange range = new VisibleRange();
        this.attachedViews.put(rv, range);
        
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private long lastCheckTime = 0;

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy == 0 && dx == 0) return;
                
                long now = SystemClock.elapsedRealtime();
                if (now - this.lastCheckTime < 32) return;
                this.lastCheckTime = now;
                
                RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
                if (lm instanceof LinearLayoutManager) {
                    LinearLayoutManager llm = (LinearLayoutManager) lm;
                    int first = llm.findFirstVisibleItemPosition();
                    int last = llm.findLastVisibleItemPosition();
                    
                    VisibleRange r = attachedViews.get(recyclerView);
                    if (r != null && first != -1) {
                        if (r.first != -1) {
                            boolean enteredBottom = dy > 0 && last > r.last;
                            boolean enteredTop = dy < 0 && first < r.first;
                            boolean enteredRight = dx > 0 && last > r.last;
                            boolean enteredLeft = dx < 0 && first < r.first;
                            
                            if (enteredBottom || enteredTop || enteredRight || enteredLeft) {
                                pop();
                            }
                        }
                        r.first = first;
                        r.last = last;
                    }
                }
            }
        });
    }

    public void release() {
        if (this.soundPool != null) {
            this.soundPool.release();
            this.soundPool = null;
        }
        this.attachedViews.clear();
        this.loaded = false;
        Log.d(TAG, "SoundEffectHaptic released.");
    }

    private void pop() {
        if (!this.loaded || this.soundPool == null || this.soundId == -1) return;
        
        long now = SystemClock.elapsedRealtime();
        if (now - this.lastPopTime < MIN_INTERVAL_MS) return;
        this.lastPopTime = now;
        
        float rate = ((float) (Math.random() * RATE_VARIANCE)) + RATE_BASE;
        this.soundPool.play(this.soundId, VOLUME, VOLUME, 1, 0, rate);
    }
}

