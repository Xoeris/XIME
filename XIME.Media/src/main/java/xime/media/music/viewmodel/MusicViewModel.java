package xime.media.music.viewmodel;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import xime.media.entity.TrackEntity;
import xime.media.music.service.MusicService;

import java.util.ArrayList;
import java.util.List;

public class MusicViewModel extends AndroidViewModel {

    protected MusicService.MusicBinder musicBinder;
    protected boolean serviceBound = false;

    private MutableLiveData<TrackEntity> currentTrack = new MutableLiveData<>();
    private MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private MutableLiveData<Long> currentPosition = new MutableLiveData<>(0L);
    private MutableLiveData<Long> duration = new MutableLiveData<>(0L);
    private MutableLiveData<List<TrackEntity>> currentPlaylist = new MutableLiveData<>(new ArrayList<>());

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicBinder = (MusicService.MusicBinder) service;
            serviceBound = true;
            
            // Attach direct framework-level observers for high-fidelity sync
            musicBinder.getService().musicSphere.getProgressSignal().observe(progress -> {
                currentPosition.postValue(progress.position);
                duration.postValue(progress.duration);
            });
            
            musicBinder.getService().musicSphere.getMetadataSignal().observe(metadata -> updateState());
            musicBinder.getService().musicSphere.getStateSignal().observe(state -> updateState());
            musicBinder.getService().musicSphere.getQueue().getQueueSignal().observe(v -> updateState());

            updateState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBinder = null;
            serviceBound = false;
        }
    };

    public MusicViewModel(@NonNull Application application, Class<? extends MusicService> serviceClass) {
        super(application);
        Intent intent = new Intent(application, serviceClass);
        application.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    public boolean isServiceBound() {
        return serviceBound;
    }

    public MusicService.MusicBinder getMusicBinder() {
        return musicBinder;
    }

    protected void updateState() {
        if (serviceBound && musicBinder != null) {
            isPlaying.postValue(musicBinder.isPlaying());
            currentTrack.postValue(musicBinder.getCurrentTrack());
            currentPlaylist.postValue(musicBinder.getCurrentPlaylist());
        }
    }

    public void play() { if (musicBinder != null) musicBinder.play(); }
    public void pause() { if (musicBinder != null) musicBinder.pause(); }
    public void playNext() { if (musicBinder != null) musicBinder.playNext(); }
    public void playPrevious() { if (musicBinder != null) musicBinder.playPrevious(); }
    public void seekTo(long pos) { if (musicBinder != null) musicBinder.seekTo(pos); }

    public void playTracks(List<TrackEntity> trackEntityEntities, int startIndex) {
        if (musicBinder != null) musicBinder.playTracks(trackEntityEntities, startIndex);
    }

    public LiveData<TrackEntity> getCurrentTrack() { return currentTrack; }
    public LiveData<Boolean> getIsPlaying() { return isPlaying; }
    public LiveData<Long> getCurrentPosition() { return currentPosition; }
    public LiveData<Long> getDuration() { return duration; }
    public LiveData<List<TrackEntity>> getCurrentPlaylist() { return currentPlaylist; }

    @Override
    protected void onCleared() {
        super.onCleared();
        getApplication().unbindService(connection);
    }
}
