package xime.imaging;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import xime.core.log.ILogger;
import xime.core.log.LogcatLogger;

/**
 * Prism image loading engine.
 *
 * Drop-in replacement for Glide across XIME.* modules. Handles memory +
 * disk caching, downsampled background decoding, and RecyclerView-safe
 * cancellation (a stale async result for a recycled ImageView is dropped
 * instead of being applied).
 *
 * Usage (mirrors Glide.with(context).load(url).into(imageView)):
 *
 *   Prism.load(url).into(imageView);
 *
 * Optional builder calls:
 *
 *   Prism.load(url)
 *       .placeholder(R.drawable.placeholder, context)
 *       .error(R.drawable.broken_image, context)
 *       .transform(new CircleTransformation())
 *       .override(256, 256)
 *       .into(imageView);
 */
public final class Prism {

    static final String TAG = "Prism";

    private static volatile Prism sInstance;

    private final MemoryCache memoryCache;
    private final DiskCache diskCache;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final ILogger logger;

    private final Object pauseLock = new Object();
    private volatile boolean paused = false;
    private final Context context;

    private Prism(Context context) {
        this.context = context.getApplicationContext();
        this.logger = new LogcatLogger();
        this.memoryCache = new MemoryCache();
        this.diskCache = new DiskCache(this.context, logger);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = buildExecutor();
    }

    Context context() {
        return context;
    }

    private static ExecutorService buildExecutor() {
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        final AtomicInteger threadCount = new AtomicInteger(0);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "Prism-Worker-" + threadCount.incrementAndGet());
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
        return new ThreadPoolExecutor(
                poolSize, poolSize,
                30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory
        );
    }

    static Prism getInstance(Context context) {
        if (sInstance == null) {
            synchronized (Prism.class) {
                if (sInstance == null && context != null) {
                    sInstance = new Prism(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * Globally pause all Prism load requests.
     */
    public static void pauseRequests(Context context) {
        getInstance(context).setPaused(true);
    }

    /**
     * Globally resume all Prism load requests.
     */
    public static void resumeRequests(Context context) {
        getInstance(context).setPaused(false);
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        if (!paused) {
            synchronized (pauseLock) {
                pauseLock.notifyAll();
            }
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public Object getPauseLock() {
        return pauseLock;
    }

    /**
     * Get a PrismRequestManager instance for loading images with context.
     */
    public static PrismRequestManager with(Context context) {
        return new PrismRequestManager(context);
    }

    /**
     * RequestManager to build requests with context and handle pause/resume.
     */
    public static class PrismRequestManager {
        private final Context context;
        PrismRequestManager(Context context) {
            this.context = context;
        }
        public PrismRequest load(String url) {
            return new PrismRequest(url, context);
        }
        public PrismRequest load(Uri uri) {
            return new PrismRequest(uri, context);
        }
        public PrismRequest load(byte[] bytes) {
            return new PrismRequest(bytes, context);
        }
        public PrismRequest load(Integer resId) {
            return new PrismRequest(resId, context);
        }
        public PrismRequest load(Object source) {
            return new PrismRequest(source, context);
        }
        public void pauseRequests() {
            Prism.pauseRequests(context);
        }
        public void resumeRequests() {
            Prism.resumeRequests(context);
        }
    }

    /** Begin a load request for a remote/local URL. Call .into(ImageView) to execute it. */
    public static PrismRequest load(String url) {
        return new PrismRequest(url);
    }

    /** Begin a load request for a content:// / file:// / res:// Uri. */
    public static PrismRequest load(Uri uri) {
        return new PrismRequest(uri);
    }

    /** Begin a load request for raw byte data. */
    public static PrismRequest load(byte[] bytes) {
        return new PrismRequest(bytes);
    }

    /** Begin a load request for a drawable resource ID. */
    public static PrismRequest load(Integer resId) {
        return new PrismRequest(resId);
    }

    /**
     * Begin a load request for any supported source type (String, Uri, byte[], Integer).
     */
    public static PrismRequest load(Object source) {
        return new PrismRequest(source);
    }

    /** Clears the in-memory bitmap cache. Safe to call from onTrimMemory/onLowMemory. */
    public static void clearMemory(Context context) {
        getInstance(context).memoryCache.clear();
    }

    /** Clears the on-disk cache synchronously. Call from a background thread for large caches. */
    public static void clearDiskCache(Context context) {
        getInstance(context).diskCache.clear();
    }

    MemoryCache memoryCache() {
        return memoryCache;
    }

    DiskCache diskCache() {
        return diskCache;
    }

    ExecutorService executor() {
        return executor;
    }

    Handler mainHandler() {
        return mainHandler;
    }

    ILogger logger() {
        return logger;
    }
}

