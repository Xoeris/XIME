package xime.terminal.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import externs.termux.terminal.TerminalSessionClient;
import xime.system.optimization.zenith.BackgroundZenith;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * XIME TerminalSession providing lifecycle management, asynchronous execution,
 * multi-listener support, and high-level wrappers over the core Termux Terminal Engine.
 */
public final class TerminalSession {
    private final externs.termux.terminal.TerminalSession innerSession;
    private final CopyOnWriteArrayList<SessionCallback> callbacks = new CopyOnWriteArrayList<>();

    public interface SessionCallback {
        void onSessionUpdated();
        void onSessionClosed(int exitCode);
        default void onTitleChanged(String title) {}
        default void onBell() {}
        default void onColorsChanged() {}
    }

    public TerminalSession(String cmd, String[] env, String cwd, int rows, int cols, SessionCallback callback) {
        this(cmd, new String[0], env, cwd, rows, cols, 2000, callback);
    }

    public TerminalSession(String cmd, String[] args, String[] env, String cwd, int rows, int cols, Integer transcriptRows, SessionCallback callback) {
        if (callback != null) {
            this.callbacks.add(callback);
        }

        TerminalSessionClient client = new TerminalSessionClient() {
            @Override
            public void onTextChanged(@NonNull externs.termux.terminal.TerminalSession changedSession) {
                for (SessionCallback cb : callbacks) {
                    BackgroundZenith.runOnUiThread(cb::onSessionUpdated);
                }
            }

            @Override
            public void onTitleChanged(@NonNull externs.termux.terminal.TerminalSession changedSession) {
                String title = changedSession.getTitle();
                for (SessionCallback cb : callbacks) {
                    BackgroundZenith.runOnUiThread(() -> cb.onTitleChanged(title));
                }
            }

            @Override
            public void onSessionFinished(@NonNull externs.termux.terminal.TerminalSession finishedSession) {
                int exitStatus = finishedSession.getExitStatus();
                for (SessionCallback cb : callbacks) {
                    BackgroundZenith.runOnUiThread(() -> cb.onSessionClosed(exitStatus));
                }
            }

            @Override
            public void onCopyTextToClipboard(@NonNull externs.termux.terminal.TerminalSession session, String text) {}

            @Override
            public void onPasteTextFromClipboard(@Nullable externs.termux.terminal.TerminalSession session) {}

            @Override
            public void onBell(@NonNull externs.termux.terminal.TerminalSession session) {
                for (SessionCallback cb : callbacks) {
                    BackgroundZenith.runOnUiThread(cb::onBell);
                }
            }

            @Override
            public void onColorsChanged(@NonNull externs.termux.terminal.TerminalSession session) {
                for (SessionCallback cb : callbacks) {
                    BackgroundZenith.runOnUiThread(cb::onColorsChanged);
                }
            }

            @Override
            public void onTerminalCursorStateChange(boolean state) {}

            @Override
            public void setTerminalShellPid(@NonNull externs.termux.terminal.TerminalSession session, int pid) {}

            @Override
            public Integer getTerminalCursorStyle() {
                return null;
            }

            @Override
            public void logError(String tag, String message) {}

            @Override
            public void logWarn(String tag, String message) {}

            @Override
            public void logInfo(String tag, String message) {}

            @Override
            public void logDebug(String tag, String message) {}

            @Override
            public void logVerbose(String tag, String message) {}

            @Override
            public void logStackTraceWithMessage(String tag, String message, Exception e) {}

            @Override
            public void logStackTrace(String tag, Exception e) {}
        };

        this.innerSession = new externs.termux.terminal.TerminalSession(cmd, cwd, args, env, transcriptRows != null ? transcriptRows : 2000, client);
        if (rows > 0 && cols > 0) {
            this.innerSession.updateSize(cols, rows, 0, 0);
        }
    }

    public void addCallback(SessionCallback cb) {
        if (cb != null && !callbacks.contains(cb)) {
            callbacks.add(cb);
        }
    }

    public void removeCallback(SessionCallback cb) {
        if (cb != null) {
            callbacks.remove(cb);
        }
    }

    public void write(String data) {
        if (data != null && innerSession != null) {
            innerSession.write(data);
        }
    }

    public void write(byte[] data, int offset, int count) {
        if (data != null && innerSession != null) {
            innerSession.write(data, offset, count);
        }
    }

    public void resize(int rows, int cols) {
        if (innerSession != null) {
            innerSession.updateSize(cols, rows, 0, 0);
        }
    }

    public void resize(int rows, int cols, int cellWidthPixels, int cellHeightPixels) {
        if (innerSession != null) {
            innerSession.updateSize(cols, rows, cellWidthPixels, cellHeightPixels);
        }
    }

    public boolean isRunning() {
        return innerSession != null && innerSession.isRunning();
    }

    public int getPid() {
        return innerSession != null ? innerSession.getPid() : -1;
    }

    public int getExitStatus() {
        return innerSession != null ? innerSession.getExitStatus() : -1;
    }

    public String getTitle() {
        return innerSession != null ? innerSession.getTitle() : "";
    }

    public externs.termux.terminal.TerminalSession getInnerSession() {
        return innerSession;
    }

    public externs.termux.terminal.TerminalEmulator getEmulator() {
        return innerSession != null ? innerSession.getEmulator() : null;
    }

    public void close() {
        if (innerSession != null) {
            innerSession.finishIfRunning();
        }
    }
}
