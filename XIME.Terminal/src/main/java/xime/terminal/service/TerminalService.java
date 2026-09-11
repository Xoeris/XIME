package xime.terminal.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;
import java.util.List;
import xime.terminal.session.TerminalSession;

public final class TerminalService extends Service {
    private static final String CHANNEL_ID = "xime_terminal_channel";
    private static final int NOTIFICATION_ID = 101;
    
    private final IBinder binder = new TerminalBinder();
    private final List<TerminalSession> sessions = new ArrayList<>();

    public class TerminalBinder extends Binder {
        public TerminalService getService() {
            return TerminalService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public TerminalSession createSession(String cmd, String[] env, String cwd, int rows, int cols, TerminalSession.SessionCallback callback) {
        TerminalSession session = new TerminalSession(cmd, env, cwd, rows, cols, callback);
        sessions.add(session);
        return session;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "XIME Terminal",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("XIME Terminal Running")
                .setContentText("A terminal session is active.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        for (TerminalSession session : sessions) {
            session.close();
        }
        super.onDestroy();
    }
}
