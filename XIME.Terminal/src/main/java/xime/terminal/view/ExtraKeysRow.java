package xime.terminal.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import xime.R;
import xime.core.theme.ThemeManager;
import xime.terminal.session.TerminalSession;

public final class ExtraKeysRow extends LinearLayout {
    private TerminalSession session;

    public ExtraKeysRow(Context context) {
        super(context);
        init();
    }

    public ExtraKeysRow(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        boolean isDark;
        try {
            isDark = ThemeManager.get().isDark();
        } catch (IllegalStateException e) {
            int nm = getContext().getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            isDark = nm == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        setBackgroundColor(getContext().getColor(isDark ? R.color.xoeris_bg_dark : R.color.xoeris_bg_light));

        addKey("Esc", "\033");
        addKey("Tab", "\t");
        addKey("Ctrl", ""); // State handled in TerminalView usually, but here we'll mock simple keys
        addKey("Alt", "");
        addKey("↑", "\033[A");
        addKey("↓", "\033[B");
        addKey("←", "\033[D");
        addKey("→", "\033[C");
    }

    public void attachSession(TerminalSession session) {
        this.session = session;
    }

    private void addKey(String label, final String sequence) {
        Button btn = new Button(getContext());
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setOnClickListener(v -> {
            if (session != null) {
                session.write(sequence);
            }
        });
        addView(btn, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f));
    }
}
