package xime.media;

import android.content.Context;
import android.content.Intent;
import android.os.CountDownTimer;

import xime.media.music.manager.MusicManager;

public class SleepTimer {
    
    private static CountDownTimer sleepTimer;
    private static long remainingTimeMillis = 0;
    private static boolean isActive = false;
    private static Context appContext;
    
    public static void startTimer(Context context, int durationMinutes) {
        appContext = context.getApplicationContext();
        cancelTimer();
        
        long durationMillis = durationMinutes * 60 * 1000L;
        remainingTimeMillis = durationMillis;
        isActive = true;
        
        sleepTimer = new CountDownTimer(durationMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingTimeMillis = millisUntilFinished;
            }
            
            @Override
            public void onFinish() {
                pauseMusicAndReset();
            }
        };
        
        sleepTimer.start();
    }
    
    public static void cancelTimer() {
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
        }
        isActive = false;
        remainingTimeMillis = 0;
    }
    
    public static boolean isTimerActive() {
        return isActive && sleepTimer != null;
    }
    
    public static long getRemainingTime() {
        return remainingTimeMillis;
    }
    
    public static String getFormattedRemainingTime() {
        if (!isActive) return "00:00";
        long minutes = remainingTimeMillis / (60 * 1000);
        long seconds = (remainingTimeMillis % (60 * 1000)) / 1000;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    private static void pauseMusicAndReset() {
        isActive = false;
        remainingTimeMillis = 0;
        
        if (appContext != null) {
            // Pause through engine
            MusicManager.getInstance(appContext).getEngine().getMusic().pause();
            
            // Broadcast for UI
            Intent broadcastIntent = new Intent("xime.media.SLEEP_TIMER_FINISHED");
            appContext.sendBroadcast(broadcastIntent);
        }
        
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
        }
    }
}

