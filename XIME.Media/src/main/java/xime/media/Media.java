package xime.media;

public interface Media {
    /**
     * Prepares the hardware pipeline for playback.
     * @param dataSource The URI or path to the media source.
     */
    void prepare(String dataSource);

    /**
     * Starts or resumes playback.
     */
    void play();

    /**
     * Pauses the active playback.
     */
    void pause();

    /**
     * Stops playback and clears the pipeline.
     */
    void stop();

    /**
     * Seeks to a specific position in the media.
     * @param positionMs Position in milliseconds.
     */
    void seekTo(long positionMs);

    /**
     * Releases all native and hardware resources.
     */
    void release();
}

