package xime.terminal.view;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import externs.termux.view.TerminalViewClient;
import xime.R;
import xime.core.theme.ThemeManager;
import xime.system.optimization.zenith.BatchRenderingZenith;
import xime.terminal.session.TerminalSession;

/**
 * XIME TerminalView offering rich terminal rendering via the underlying Termux View Engine,
 * integrated seamlessly with XIME ThemeManager, batch rendering, and custom callbacks.
 */
public final class TerminalView extends FrameLayout implements TerminalSession.SessionCallback {
    private externs.termux.view.TerminalView innerView;
    private TerminalSession currentSession;

    public TerminalView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public TerminalView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public TerminalView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        innerView = new externs.termux.view.TerminalView(context, attrs);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(innerView, lp);

        innerView.setTerminalViewClient(new TerminalViewClient() {
            @Override
            public float onScale(float scale) {
                return 1.0f;
            }

            @Override
            public void onSingleTapUp(MotionEvent e) {
                if (innerView != null) {
                    innerView.requestFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                            getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(innerView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }

            @Override
            public boolean shouldBackButtonBeMappedToEscape() {
                return false;
            }

            @Override
            public boolean shouldEnforceCharBasedInput() {
                return false;
            }

            @Override
            public boolean shouldUseCtrlSpaceWorkaround() {
                return false;
            }

            @Override
            public boolean isTerminalViewSelected() {
                return true;
            }

            @Override
            public void copyModeChanged(boolean copyMode) {}

            @Override
            public boolean onKeyDown(int keyCode, KeyEvent e, externs.termux.terminal.TerminalSession session) {
                return false;
            }

            @Override
            public boolean onKeyUp(int keyCode, KeyEvent e) {
                return false;
            }

            @Override
            public boolean onLongPress(MotionEvent event) {
                return false;
            }

            @Override
            public boolean readControlKey() {
                return false;
            }

            @Override
            public boolean readAltKey() {
                return false;
            }

            @Override
            public boolean readShiftKey() {
                return false;
            }

            @Override
            public boolean readFnKey() {
                return false;
            }

            @Override
            public boolean onCodePoint(int codePoint, boolean ctrlDown, externs.termux.terminal.TerminalSession session) {
                return false;
            }

            @Override
            public void onEmulatorSet() {
                if (innerView != null) {
                    innerView.invalidate();
                }
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
        });

        applyTheme();
    }

    public void attachSession(@NonNull TerminalSession session) {
        if (this.currentSession != null) {
            this.currentSession.removeCallback(this);
        }
        this.currentSession = session;
        session.addCallback(this);
        if (innerView != null && session.getInnerSession() != null) {
            innerView.attachSession(session.getInnerSession());
        }
    }

    public void applyTheme() {
        boolean isDark;
        try {
            isDark = ThemeManager.get().isDark();
        } catch (IllegalStateException e) {
            int nm = getContext().getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            isDark = nm == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        setBackgroundColor(getContext().getColor(isDark ? R.color.xoeris_bg_dark : R.color.xoeris_bg_light));
    }

    public externs.termux.view.TerminalView getInnerView() {
        return innerView;
    }

    public TerminalSession getSession() {
        return currentSession;
    }

    public void setTextSize(int textSize) {
        if (innerView != null) {
            innerView.setTextSize(textSize);
        }
    }

    @Override
    public void onSessionUpdated() {
        BatchRenderingZenith.runBatched(this, () -> {
            if (innerView != null) {
                innerView.onScreenUpdated();
                innerView.invalidate();
            }
        });
    }

    @Override
    public void onSessionClosed(int exitCode) {
        if (innerView != null) {
            innerView.onScreenUpdated();
            innerView.invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (currentSession != null) {
            currentSession.removeCallback(this);
        }
    }
}
