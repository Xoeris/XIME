package xime.media.music;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import android.media.audiofx.Visualizer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import xime.media.equalizer.EqualizerAcousticSpace;
import xime.media.equalizer.EqualizerBassDrive;
import xime.media.equalizer.EqualizerEchoSpace;
import xime.media.equalizer.EqualizerResonance;
import xime.media.equalizer.EqualizerSpatializer;
import xime.media.equalizer.EqualizerVolumeBoost;
import xime.media.equalizer.Equalizer;
import xime.media.Media;
import xime.media.Metadata;
import xime.media.Playlist;
import xime.media.Queue;
import xime.media.Signal;
import xime.media.spectrum.Spectrum;

public class Music implements Media {
    private static final String TAG = "XIME-Music";

    public enum PlaybackState { 
        IDLE, BUFFERING, READY, PLAYING, PAUSED, ENDED, ERROR;
        public static final PlaybackState COMPLETED = ENDED;
    }

    private final Context context;
    private final Signal<PlaybackState> stateSignal = new Signal<>();
    private final Signal<Progress> progressSignal = new Signal<>();
    private final Signal<Metadata> metadataSignal = new Signal<>();
    private final Signal<Void> preloadNextSignal = new Signal<>();

    private final Queue queue = new Queue();
    private volatile PlaybackState currentState = PlaybackState.IDLE;
    
    private AudioTrack audioTrack;
    private Visualizer visualizer;
    private int activeSampleRate;
    private int activeChannelConfig;
    private long trackHeadOffset = 0; // Hardware sync anchor
    private short[] pcmBuffer; // Cache for offline ASR

    private static class Channel {
        MediaExtractor extractor;
        MediaCodec decoder;
        Metadata metadata;
        long totalDurationUs;
        long currentPositionUs;
        boolean sawInputEOS;
        boolean sawOutputEOS;
        float volume = 1.0f;
        String dataSource;
        int sampleRate;
        int channelCount;

        void release() {
            if (decoder != null) {
                try { decoder.stop(); decoder.release(); } catch (Exception ignored) {}
                decoder = null;
            }
            if (extractor != null) {
                try { extractor.release(); } catch (Exception ignored) {}
                extractor = null;
            }
        }
    }

    private final Object channelLock = new Object();
    private Channel currentChannel;
    private Channel nextChannel;
    
    private final Object decoderLock = new Object();
    private final ExecutorService preparationExecutor = Executors.newSingleThreadExecutor();
    private Thread decoderThread;
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean isReleased = new AtomicBoolean(false);
    private final Object pauseLock = new Object();
    private volatile boolean isPaused = false;
    private volatile long seekToPositionUs = -1;

    private int crossfadeDurationMs = 0;
    private volatile boolean isCrossfading = false;

    // Fade System
    private static final int ACTION_FADE_DURATION_MS = 600;
    private volatile float globalVolume = 1.0f;
    private volatile float targetGlobalVolume = 1.0f;
    private long fadeStartTime = 0;
    private float fadeStartVolume = 1.0f;

    // Normalization logic
    private boolean normalizationEnabled = true;
    private float normalizationGain = 1.0f;
    private float targetLoudness = 0.8f; // Target amplitude [0..1]
    private float currentPeak = 0.0f;

    private final float[] spectrumBuffer = new float[64];
    private AudioManager audioManager;

    public static class Progress {
        public final long position;
        public final long duration;
        public Progress(long position, long duration) {
            this.position = position;
            this.duration = duration;
        }
    }

    public Music(Context context) {
        Context appCtx = context.getApplicationContext();
        this.context = (appCtx != null) ? appCtx : context;
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    public short[] extractFullPCM(String dataSource) {
        Channel channel = prepareChannel(dataSource, null);
        if (channel == null) return null;

        java.util.ArrayList<short[]> chunks = new java.util.ArrayList<>();
        int totalSize = 0;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        long startTime = System.currentTimeMillis();

        try {
            while (!channel.sawOutputEOS) {
                // Safety break: max 30 seconds for extraction
                if (System.currentTimeMillis() - startTime > 30000) {
                    Log.e(TAG, "PCM Extraction timed out");
                    break;
                }

                if (!channel.sawInputEOS) {
                    int inputBufferIndex = channel.decoder.dequeueInputBuffer(5000);
                    if (inputBufferIndex >= 0) {
                        java.nio.ByteBuffer inputBuffer = channel.decoder.getInputBuffer(inputBufferIndex);
                        if (inputBuffer != null) {
                            int sampleSize = channel.extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                channel.decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                channel.sawInputEOS = true;
                            } else {
                                channel.decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, channel.extractor.getSampleTime(), 0);
                                channel.extractor.advance();
                            }
                        }
                    }
                }

                int outputBufferIndex = channel.decoder.dequeueOutputBuffer(bufferInfo, 5000);
                if (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) channel.sawOutputEOS = true;
                    java.nio.ByteBuffer outputBuffer = channel.decoder.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        short[] chunk = new short[bufferInfo.size / 2];
                        outputBuffer.asShortBuffer().get(chunk);
                        chunks.add(chunk);
                        totalSize += chunk.length;
                    }
                    channel.decoder.releaseOutputBuffer(outputBufferIndex, false);
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Update sample rate if changed
                    MediaFormat format = channel.decoder.getOutputFormat();
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        channel.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                }
            }

            if (totalSize <= 0) return null;

            short[] fullPcm = new short[totalSize];
            int offset = 0;
            for (short[] chunk : chunks) {
                System.arraycopy(chunk, 0, fullPcm, offset, chunk.length);
                offset += chunk.length;
            }
            this.pcmBuffer = fullPcm;
            return fullPcm;
        } catch (Exception e) {
            Log.e(TAG, "PCM Extraction failed", e);
            return null;
        } finally {
            channel.release();
        }
    }

    public long getPrecisePositionMs() {
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            long currentHead = audioTrack.getPlaybackHeadPosition() & 0xFFFFFFFFL;
            long relativeHead = (currentHead >= trackHeadOffset) ? (currentHead - trackHeadOffset) : (0xFFFFFFFFL - trackHeadOffset + currentHead);
            return relativeHead * 1000 / activeSampleRate;
        }
        return 0;
    }

    public Queue getQueue() { return queue; }
    public Metadata getCurrentMetadata() {
        synchronized (channelLock) {
            return currentChannel != null ? currentChannel.metadata : null;
        }
    }
    public boolean isPlaying() { return isPlaying.get() && !isPaused; }

    public void setCrossfadeDuration(int seconds) {
        this.crossfadeDurationMs = seconds * 1000;
    }

    public void setShuffleEnabled(boolean enabled) {
        queue.setShuffleEnabled(enabled);
    }

    public void setPlaylist(Playlist playlist) {
        queue.setPlaylist(playlist);
    }

    public void setPlaylist(List<Metadata> items) {
        queue.setPlaylist(items);
    }

    public void setNormalizationEnabled(boolean enabled) {
        this.normalizationEnabled = enabled;
        if (!enabled) {
            normalizationGain = 1.0f;
            currentPeak = 0.0f;
        }
    }

    public void setTargetLoudness(float loudness) {
        this.targetLoudness = Math.max(0.0f, Math.min(2.0f, loudness));
    }

    @Override
    public void prepare(String dataSource) {
        prepareInternal(dataSource, true, null);
    }

    private Channel prepareChannel(String dataSource, Metadata metadata) {
        if (dataSource == null) return null;
        
        Channel channel = new Channel();
        channel.dataSource = dataSource;
        channel.metadata = metadata;

        try {
            channel.extractor = new MediaExtractor();
            if (dataSource.startsWith("content://") || dataSource.startsWith("file://")) {
                channel.extractor.setDataSource(context, Uri.parse(dataSource), null);
            } else {
                channel.extractor.setDataSource(dataSource);
            }

            int trackIndex = -1;
            for (int i = 0; i < channel.extractor.getTrackCount(); i++) {
                MediaFormat format = channel.extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i;
                    break;
                }
            }

            if (trackIndex < 0) throw new IOException("No audio track found.");
            channel.extractor.selectTrack(trackIndex);

            MediaFormat format = channel.extractor.getTrackFormat(trackIndex);
            channel.totalDurationUs = format.getLong(MediaFormat.KEY_DURATION);
            channel.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            channel.channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            String mime = format.getString(MediaFormat.KEY_MIME);
            channel.decoder = MediaCodec.createDecoderByType(mime);
            channel.decoder.configure(format, null, null, 0);
            channel.decoder.start();

            return channel;
        } catch (Exception e) {
            Log.e(TAG, "Failed to prepare channel for " + dataSource, e);
            channel.release();
            return null;
        }
    }

    private void prepareInternal(String dataSource, boolean asCurrent, Metadata metadata) {
        preparationExecutor.execute(() -> {
            if (asCurrent) {
                if (isPlaying.get() && !isPaused) {
                    startFade(0.0f);
                    // Wait for fade out or max 500ms
                    long start = System.currentTimeMillis();
                    while (globalVolume > 0.05f && System.currentTimeMillis() - start < 500) {
                        try { Thread.sleep(20); } catch (InterruptedException e) { break; }
                    }
                }
                
                stopInternal(); 
                setState(PlaybackState.BUFFERING);
                
                Channel ch = prepareChannel(dataSource, metadata != null ? metadata : queue.getCurrentItem());
                if (ch != null) {
                    synchronized (channelLock) {
                        currentChannel = ch;
                        setupAudioTrack(ch.sampleRate, ch.channelCount);
                        // Anchor hardware sync for manually started track
                        if (audioTrack != null) {
                            trackHeadOffset = audioTrack.getPlaybackHeadPosition() & 0xFFFFFFFFL;
                        }
                    }
                    setState(PlaybackState.READY);
                    
                    globalVolume = 0.0f;
                    startFade(1.0f);
                    playInternal();
                } else {
                    setState(PlaybackState.ERROR);
                }
            } else {
                Channel ch = prepareChannel(dataSource, metadata);
                synchronized (channelLock) {
                    if (nextChannel != null) nextChannel.release();
                    nextChannel = ch;
                }
            }
        });
    }

    public void prepareNext(String dataSource, Metadata metadata) {
        synchronized (channelLock) {
            if (nextChannel != null && dataSource.equals(nextChannel.dataSource)) {
                if (nextChannel.metadata == null) nextChannel.metadata = metadata;
                return;
            }
        }
        prepareInternal(dataSource, false, metadata);
    }

    private void setupAudioTrack(int sampleRate, int channelCount) {
        int channelConfig = (channelCount == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;

        if (audioTrack != null && this.activeSampleRate == sampleRate && this.activeChannelConfig == channelConfig) {
            return;
        }

        releaseVisualizer();

        if (audioTrack != null) {
            try { audioTrack.stop(); audioTrack.release(); } catch (Exception ignored) {}
        }

        this.activeSampleRate = sampleRate;
        this.activeChannelConfig = channelConfig;

        // 4x buffer trades a little latency for underrun resistance (no millisecond
        // gaps when the UI bursts on app reopen). Position sync still uses the hardware
        // playback head, so resume accuracy is unaffected.
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize <= 0) minBufferSize = 4096;
        
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build())
                .setBufferSizeInBytes(minBufferSize * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        setupVisualizer(audioTrack.getAudioSessionId());
        
        // Apply Equalizer Effects to Audio Session
        int sessionId = audioTrack.getAudioSessionId();
        Equalizer.getInstance(context).applyToSession(sessionId);
        EqualizerBassDrive.getInstance(context).applyToSession(sessionId);
        EqualizerSpatializer.getInstance(context).applyToSession(sessionId);
        EqualizerVolumeBoost.getInstance(context).applyToSession(sessionId);
        EqualizerEchoSpace.getInstance(context).applyToSession(sessionId);
        EqualizerAcousticSpace.getInstance(context).applyToSession(sessionId);
    }

    private void setupVisualizer(int sessionId) {
        try {
            visualizer = new Visualizer(sessionId);
            int captureSize = Visualizer.getCaptureSizeRange()[1];
            visualizer.setCaptureSize(captureSize);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int samplingRate) {}

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int samplingRate) {
                    processFftData(fft);
                }
            }, Visualizer.getMaxCaptureRate(), false, true);
            visualizer.setEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize hardware visualizer", e);
        }
    }

    private long lastVisualizerUpdate = 0;

    private void processFftData(byte[] fft) {
        if (fft == null) return;
        lastVisualizerUpdate = System.currentTimeMillis();
        int bands = 64;
        int n = fft.length / 2; // Number of complex bins (Re, Im pairs)
        float sampleRate = activeSampleRate > 0 ? activeSampleRate : 44100;

        for (int i = 0; i < bands; i++) {
            float fraction = (float) i / bands;
            float freq;

            // Musical-Weighted Frequency Distribution
            // Region 1: 0..25% -> Bass (20Hz - 250Hz)
            // Region 2: 25..50% -> Low-Mids (250Hz - 1.2kHz)
            // Region 3: 50..75% -> High-Mids (1.2kHz - 5kHz)
            // Region 4: 75..100% -> Treble (5kHz - 12kHz)

            if (fraction < 0.25f) {
                float f = fraction / 0.25f;
                freq = 20f + f * (250f - 20f);
            } else if (fraction < 0.5f) {
                float f = (fraction - 0.25f) / 0.25f;
                freq = 250f + f * (1200f - 250f);
            } else if (fraction < 0.75f) {
                float f = (fraction - 0.5f) / 0.25f;
                freq = 1200f + f * (5000f - 1200f);
            } else {
                float f = (fraction - 0.75f) / 0.25f;
                freq = 5000f + f * (12000f - 5000f);
            }

            int binIdx = (int) (freq * fft.length / sampleRate);
            if (binIdx < 1) binIdx = 1;
            if (binIdx >= n) binIdx = n - 1;
            
            // Use a small window around the target frequency for stability
            int windowSize = Math.max(1, (int)(2 * (float)i / bands));
            float max = 0;
            for (int k = binIdx; k < binIdx + windowSize && k < n; k++) {
                float r = fft[k * 2];
                float j = fft[k * 2 + 1];
                float mag = (float) Math.sqrt(r * r + j * j);
                if (mag > max) max = mag;
            }
            
            // Normalize with extreme sensitivity for highs
            // High frequencies are naturally attenuated in the FFT output
            float sensitivity = 28f;
            float magnitude = max / sensitivity;
            
            // Dynamic Treble Lift: exponentially increase gain for higher frequencies
            float trebleLift = 1.0f + (float) Math.pow(fraction, 2.0) * 8.0f;

            // Bass normalization: keep the low end from clipping the view
            if (i < 10) trebleLift *= 0.7f;

            float val = magnitude * trebleLift;

            // Compressive reaction curve
            val = (float) Math.sqrt(val);

            // Ensure visual continuity
            spectrumBuffer[i] = Math.max(0.02f, Math.min(1.0f, val));
        }
        Spectrum.getInstance().updateFromAudio(spectrumBuffer);
    }

    private void releaseVisualizer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception ignored) {}
            visualizer = null;
        }
    }

    public void play(Metadata metadata) {
        if (metadata == null) return;
        
        synchronized (channelLock) {
            if (nextChannel != null && metadata.getPath().equals(nextChannel.dataSource)) {
                if (currentState == PlaybackState.PLAYING) {
                    isCrossfading = true;
                    return;
                }
            }
        }
        
        int index = queue.getActiveQueue().indexOf(metadata);
        if (index != -1) queue.jumpTo(index);
        
        seekToPositionUs = -1;
        prepareInternal(metadata.getPath(), true, metadata);
        metadataSignal.emit(metadata);
    }

    @Override
    public void play() {
        if (isReleased.get()) return;
        if (currentState == PlaybackState.IDLE || currentState == PlaybackState.ERROR) {
            Metadata current = queue.getCurrentItem();
            if (current != null) { 
                prepareInternal(current.getPath(), true, current); 
                return; 
            }
        }
        startFade(1.0f);
        playInternal();
    }

    private void playInternal() {
        if (isPaused) { resume(); return; }
        
        synchronized (decoderLock) {
            if (decoderThread != null && decoderThread.isAlive()) return;

            isPlaying.set(true);
            isPaused = false;
            decoderThread = new Thread(this::runDecoderLoop, "Xoeris-Audio-Pipeline");
            decoderThread.setPriority(Thread.MAX_PRIORITY);
            decoderThread.start();
        }
        setState(PlaybackState.PLAYING);
    }

    public void resume() {
        if (isPaused) {
            startFade(1.0f);
            synchronized (pauseLock) { isPaused = false; pauseLock.notifyAll(); }
            setState(PlaybackState.PLAYING);
        }
    }

    @Override
    public void pause() {
        if (currentState == PlaybackState.PLAYING) {
            startFade(0.0f);
            new Thread(() -> {
                long start = System.currentTimeMillis();
                while (globalVolume > 0.05f && System.currentTimeMillis() - start < ACTION_FADE_DURATION_MS) {
                    try { Thread.sleep(20); } catch (InterruptedException e) { break; }
                }
                isPaused = true;
                Spectrum.getInstance().reset();
                setState(PlaybackState.PAUSED);
            }, "Xoeris-Pause-Wait-Thread").start();
        }
    }

    private void startFade(float target) {
        fadeStartTime = System.currentTimeMillis();
        fadeStartVolume = globalVolume;
        targetGlobalVolume = target;
    }

    @Override
    public void stop() {
        preparationExecutor.execute(this::stopInternal);
    }

    private void stopInternal() {
        isPlaying.set(false);
        synchronized (pauseLock) { isPaused = false; pauseLock.notifyAll(); }
        
        synchronized (decoderLock) {
            if (decoderThread != null) {
                try { 
                    // Give it a bit more time to finish cleanly
                    decoderThread.join(800); 
                    if (decoderThread.isAlive()) {
                        decoderThread.interrupt();
                    }
                } catch (InterruptedException ignored) {}
                decoderThread = null;
            }
        }
        
        synchronized (channelLock) {
            if (currentChannel != null) { currentChannel.release(); currentChannel = null; }
            if (nextChannel != null) { nextChannel.release(); nextChannel = null; }
        }
        if (audioTrack != null) { 
            try { 
                audioTrack.pause();
                audioTrack.flush(); 
                audioTrack.stop();
            } catch (Exception ignored) {}
        }
        isCrossfading = false;
        globalVolume = 1.0f;
        targetGlobalVolume = 1.0f;
        Spectrum.getInstance().reset();
        setState(PlaybackState.IDLE);
    }

    @Override
    public void seekTo(long positionMs) {
        seekToPositionUs = positionMs * 1000;
        // Emit immediately so UI and resume-state capture the seek target even while
        // paused (decoder loop emits nothing while paused, which used to leave a stale
        // pre-seek position saved for resume-after-reopen).
        try {
            long durationMs = 0;
            synchronized (channelLock) {
                if (currentChannel != null) durationMs = currentChannel.totalDurationUs / 1000;
            }
            if (durationMs <= 0) {
                Progress last = progressSignal.getLastEvent();
                if (last != null) durationMs = last.duration;
            }
            progressSignal.emit(new Progress(positionMs, durationMs));
        } catch (Exception ignored) {}
    }

    /** Pending user seek target in ms, or -1 if none (used for accurate resume saves). */
    public long getPendingSeekMs() {
        long us = seekToPositionUs;
        return us >= 0 ? us / 1000 : -1;
    }

    @Override
    public void release() {
        if (isReleased.getAndSet(true)) return;
        stop();
        releaseVisualizer();
        preparationExecutor.shutdownNow();
        if (audioTrack != null) { try { audioTrack.release(); } catch (Exception ignored) {} audioTrack = null; }
        stateSignal.clear();
        progressSignal.clear();
        metadataSignal.clear();
        preloadNextSignal.clear();
    }

    private void runDecoderLoop() {
        if (audioTrack != null) audioTrack.play();

        boolean preloadTriggered = false;
        long lastProgressEmitTime = 0;
        boolean playbackFinishedNaturally = false;

        try {
            while (isPlaying.get()) {
                synchronized (pauseLock) {
                    while (isPaused && isPlaying.get()) {
                        if (audioTrack != null) try { audioTrack.pause(); } catch (Exception ignored) {}
                        pauseLock.wait();
                        if (isPlaying.get() && audioTrack != null) try { audioTrack.play(); } catch (Exception ignored) {}
                    }
                }

                if (seekToPositionUs != -1) {
                    synchronized (channelLock) {
                        if (currentChannel != null) {
                            currentChannel.extractor.seekTo(seekToPositionUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            currentChannel.decoder.flush();
                            currentChannel.sawInputEOS = false;
                            currentChannel.sawOutputEOS = false;
                            
                            // Re-anchor precise position tracking
                            if (audioTrack != null) {
                                long head = audioTrack.getPlaybackHeadPosition() & 0xFFFFFFFFL;
                                trackHeadOffset = head - (seekToPositionUs / 1000 * activeSampleRate / 1000);
                            }
                        }
                    }
                    seekToPositionUs = -1;
                }

                Channel ch1, ch2;
                synchronized (channelLock) {
                    ch1 = currentChannel;
                    ch2 = nextChannel;
                }

                if (ch1 == null) break;

                long remainingUs = ch1.totalDurationUs - ch1.currentPositionUs;
                
                if (!preloadTriggered && remainingUs < 10_000_000L) {
                    preloadNextSignal.emit(null);
                    preloadTriggered = true;
                }

                if (crossfadeDurationMs > 0 && !isCrossfading && ch2 != null && remainingUs < crossfadeDurationMs * 1000L) {
                    if (ch1.sampleRate == ch2.sampleRate && ch1.channelCount == ch2.channelCount) {
                        isCrossfading = true;
                        Log.d(TAG, "Crossfade started");
                    }
                }

                byte[] currentData = processChannel(ch1);
                byte[] nextData = null;

                if (isCrossfading && ch2 != null) {
                    nextData = processChannel(ch2);
                    
                    long crossfadeElapsedUs = crossfadeDurationMs * 1000L - remainingUs;
                    float ratio = (float) crossfadeElapsedUs / (crossfadeDurationMs * 1000L);
                    ratio = Math.max(0, Math.min(1, ratio));
                    
                    ch1.volume = 1.0f - ratio;
                    ch2.volume = ratio;
                }

                // Update globalVolume based on time
                if (globalVolume != targetGlobalVolume) {
                    long elapsed = System.currentTimeMillis() - fadeStartTime;
                    if (elapsed >= ACTION_FADE_DURATION_MS) {
                        globalVolume = targetGlobalVolume;
                    } else {
                        float progress = (float) elapsed / ACTION_FADE_DURATION_MS;
                        globalVolume = fadeStartVolume + (targetGlobalVolume - fadeStartVolume) * progress;
                    }
                }

                if (isCrossfading && nextData != null) {
                    byte[] mixed = mixPCM(currentData, ch1.volume * globalVolume, nextData, ch2.volume * globalVolume);
                    if (mixed != null) {
                        if (normalizationEnabled) mixed = applyNormalization(mixed);
                        mixed = EqualizerResonance.getInstance(context).process(mixed);
                        analyzeAndPushSpectrum(mixed);
                        audioTrack.write(mixed, 0, mixed.length);
                    }
                } else if (currentData != null) {
                    float currentVol = ch1.volume * globalVolume;
                    if (currentVol < 0.99f) {
                        currentData = applyVolume(currentData, currentVol);
                    }
                    if (normalizationEnabled) currentData = applyNormalization(currentData);
                    currentData = EqualizerResonance.getInstance(context).process(currentData);

                    analyzeAndPushSpectrum(currentData);
                    audioTrack.write(currentData, 0, currentData.length);
                }

                if (ch1.sawOutputEOS || (isCrossfading && ch1.volume <= 0)) {
                    synchronized (channelLock) {
                        Log.d(TAG, "Transitioning to next channel");
                        ch1.release();
                        currentChannel = nextChannel;
                        nextChannel = null;
                        isCrossfading = false;
                        preloadTriggered = false;
                        
                        if (currentChannel != null) {
                            if (currentChannel.sampleRate != activeSampleRate || currentChannel.channelCount != activeChannelConfig) {
                                setupAudioTrack(currentChannel.sampleRate, currentChannel.channelCount);
                                audioTrack.play();
                            }
                            trackHeadOffset = audioTrack.getPlaybackHeadPosition() & 0xFFFFFFFFL; // Anchor to new track
                            metadataSignal.emit(currentChannel.metadata);
                            // Sync progress immediately on track change to prevent MediaSession "stuck" progress
                            progressSignal.emit(new Progress(0, currentChannel.totalDurationUs / 1000));
                        } else {
                            playbackFinishedNaturally = true;
                            break; 
                        }
                    }
                }
                
                long now = System.currentTimeMillis();
                if (now - lastProgressEmitTime > 30 && ch1 != null) {
                    progressSignal.emit(new Progress(ch1.currentPositionUs / 1000, ch1.totalDurationUs / 1000));
                    lastProgressEmitTime = now;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failure in decoder loop", e);
            setState(PlaybackState.ERROR);
        } finally {
            if (playbackFinishedNaturally && isPlaying.get()) {
                setState(PlaybackState.ENDED);
            }
            if (audioTrack != null) try { audioTrack.stop(); audioTrack.flush(); } catch (Exception ignored) {}
        }
    }

    private byte[] processChannel(Channel channel) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        if (!channel.sawInputEOS) {
            int inputBufferIndex = channel.decoder.dequeueInputBuffer(2000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = channel.decoder.getInputBuffer(inputBufferIndex);
                if (inputBuffer != null) {
                    int sampleSize = channel.extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        channel.decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        channel.sawInputEOS = true;
                    } else {
                        channel.decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, channel.extractor.getSampleTime(), 0);
                        channel.extractor.advance();
                    }
                }
            }
        }

        int outputBufferIndex = channel.decoder.dequeueOutputBuffer(bufferInfo, 2000);
        if (outputBufferIndex >= 0) {
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                channel.sawOutputEOS = true;
            }

            ByteBuffer outputBuffer = channel.decoder.getOutputBuffer(outputBufferIndex);
            if (outputBuffer != null) {
                byte[] chunk = new byte[bufferInfo.size];
                outputBuffer.get(chunk);
                outputBuffer.clear();
                channel.currentPositionUs = bufferInfo.presentationTimeUs;
                channel.decoder.releaseOutputBuffer(outputBufferIndex, false);
                return chunk;
            }
            channel.decoder.releaseOutputBuffer(outputBufferIndex, false);
        }
        return null;
    }

    private byte[] mixPCM(byte[] b1, float v1, byte[] b2, float v2) {
        if (b1 == null && b2 == null) return null;
        if (b1 == null) return applyVolume(b2, v2);
        if (b2 == null) return applyVolume(b1, v1);

        int len = Math.min(b1.length, b2.length);
        byte[] mixed = new byte[len];
        ShortBuffer s1 = ByteBuffer.wrap(b1).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        ShortBuffer s2 = ByteBuffer.wrap(b2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        ShortBuffer sm = ByteBuffer.wrap(mixed).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();

        for (int i = 0; i < len / 2; i++) {
            short sample1 = s1.get();
            short sample2 = s2.get();
            int mixedSample = (int) (sample1 * v1 + sample2 * v2);
            if (mixedSample > 32767) mixedSample = 32767;
            else if (mixedSample < -32768) mixedSample = -32768;
            sm.put((short) mixedSample);
        }
        return mixed;
    }

    private byte[] applyVolume(byte[] b, float v) {
        if (b == null) return null;
        if (v >= 0.99f) return b;
        if (v <= 0.0f) {
            for (int i = 0; i < b.length; i++) b[i] = 0;
            return b;
        }
        ShortBuffer sb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        for (int i = 0; i < b.length / 2; i++) {
            short sample = sb.get(i);
            sb.put(i, (short) (sample * v));
        }
        return b;
    }

    private byte[] applyNormalization(byte[] b) {
        if (b == null) return null;
        ShortBuffer sb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        int len = b.length / 2;
        
        // 1. Analyze Peak in this chunk
        float localPeak = 0;
        for (int i = 0; i < len; i++) {
            float sample = Math.abs(sb.get(i)) / 32768f;
            if (sample > localPeak) localPeak = sample;
        }

        // 2. Smoothly update normalizationGain
        // Simple peak follower with fast attack, slow decay
        if (localPeak > currentPeak) {
            currentPeak = localPeak; // Attack
        } else {
            currentPeak = currentPeak * 0.999f + localPeak * 0.001f; // Slow Decay
        }

        if (currentPeak > 0.01f) {
            float targetGain = targetLoudness / currentPeak;
            // Clamp gain to avoid over-amplification of silence
            targetGain = Math.min(targetGain, 2.0f);
            
            // Smooth gain transitions
            normalizationGain = normalizationGain * 0.95f + targetGain * 0.05f;
        }

        if (normalizationGain > 0.98f && normalizationGain < 1.02f) return b;

        for (int i = 0; i < len; i++) {
            int val = (int) (sb.get(i) * normalizationGain);
            if (val > 32767) val = 32767;
            else if (val < -32768) val = -32768;
            sb.put(i, (short) val);
        }

        return b;
    }

    private void analyzeAndPushSpectrum(byte[] pcmData) {
        if (pcmData == null || pcmData.length < 16) return;

        // Use hardware visualizer if it's active
        if (System.currentTimeMillis() - lastVisualizerUpdate < 500) {
            return;
        }

        // Software Fallback: Improved RMS-based binning
        ShortBuffer sb = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        int samples = pcmData.length / 2;
        int bands = 64;

        for (int b = 0; b < bands; b++) {
            // Log-scale mapping for software fallback
            float f1 = (float) b / bands;
            float f2 = (float) (b + 1) / bands;
            
            int start = (int) (samples * (Math.pow(10, f1) - 1) / 9.0);
            int end = (int) (samples * (Math.pow(10, f2) - 1) / 9.0);
            
            start = Math.max(0, start);
            end = Math.min(samples, end);
            if (end <= start) end = start + 1;

            float sumSq = 0;
            float maxVal = 0;
            int count = 0;
            
            for (int i = start; i < end && i < samples; i++) {
                float val = Math.abs(sb.get(i)) / 32768f;
                if (val > maxVal) maxVal = val;
                sumSq += val * val;
                count++;
            }
            
            float rms = count > 0 ? (float) Math.sqrt(sumSq / count) : 0f;
            // Weighted blend of Peak and RMS for software visualization
            float magnitude = maxVal * 0.4f + rms * 0.6f;
            
            // Apply gain curve to software fallback
            float boost = 1.0f + 0.5f * (1.0f - (float) b / bands); // Prefer lower frequencies visually
            magnitude *= boost;
            
            spectrumBuffer[b] = Math.max(0.0f, Math.min(1.0f, magnitude * 2.5f));
        }

        Spectrum.getInstance().updateFromAudio(spectrumBuffer);
    }

    public void updateMetadataSync(Metadata metadata, long position, long duration) {
        synchronized (channelLock) {
            if (currentChannel != null) {
                currentChannel.metadata = metadata;
                currentChannel.currentPositionUs = position * 1000;
                currentChannel.totalDurationUs = duration * 1000;
            }
        }
        metadataSignal.emit(metadata);
        progressSignal.emit(new Progress(position, duration));
    }

    public void syncState(PlaybackState state) { setState(state); }
    private void setState(PlaybackState state) {
        if (this.currentState != state) {
            this.currentState = state;
            stateSignal.emit(state);
        }
    }

    public Signal<PlaybackState> getStateSignal() { return stateSignal; }
    public Signal<Progress> getProgressSignal() { return progressSignal; }
    public Signal<Metadata> getMetadataSignal() { return metadataSignal; }
    public Signal<Void> getPreloadNextSignal() { return preloadNextSignal; }
    public PlaybackState getCurrentState() { return currentState; }
}
