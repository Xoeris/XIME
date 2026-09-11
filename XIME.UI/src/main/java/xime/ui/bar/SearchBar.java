package xime.ui.bar;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;

import xime.ui.common.TextField;
import xime.ui.layout.LinearLayout;
import xime.ui.view.EditText;
import xime.ui.view.PictureView;

import xime.R;

public class SearchBar extends LinearLayout {
    private PictureView clearButton;
    private OnSearchListener listener;
    private TextField searchEditText;

    public interface OnSearchListener {
        void onSearchChanged(String text);
        void onSearchCleared();
    }

    public SearchBar(Context context) {
        this(context, null);
    }

    public SearchBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        
        setBackgroundResource(R.drawable.xoeris_bg_control_code);
        int paddingSide = (int) (context.getResources().getDisplayMetrics().density * 4.0f);
        int paddingTopBottom = (int) (context.getResources().getDisplayMetrics().density * 4.0f);
        setPadding(paddingSide, paddingTopBottom, paddingSide, paddingTopBottom);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        
        LayoutInflater.from(context).inflate(R.layout.search_bar, this, true);
        
        this.searchEditText = (TextField) findViewById(R.id.search_edit_text);
        this.clearButton = (PictureView) findViewById(R.id.clear_button);
        
        setupEditText();
        setupClearButton();
        setupTextWatcher();
        
        setOnClickListener(v -> focusSearch());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            focusSearch();
        }
        return super.onInterceptTouchEvent(ev);
    }

    public void focusSearch() {
        if (this.searchEditText != null && this.searchEditText.getEditText() != null) {
            final EditText et = this.searchEditText.getEditText();
            et.requestFocus();
            if (et.getText() != null) {
                et.setSelection(et.getText().length());
            }
            et.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    // Check if window has focus before showing keyboard
                    if (et.hasWindowFocus()) {
                        if (!imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)) {
                            imm.showSoftInput(et, InputMethodManager.SHOW_FORCED);
                        }
                    }
                }
            }, 150);
        }
    }

    private void setupEditText() {
        if (this.searchEditText == null) return;
        this.searchEditText.setMaxLines(1);
        this.searchEditText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        this.searchEditText.setBackground(new ColorDrawable(0));
        this.searchEditText.setTextCursorDrawable(R.drawable.xoeris_white_cursor);
        this.searchEditText.setFocusable(true);
        this.searchEditText.setFocusableInTouchMode(true);
        this.searchEditText.setClickable(true);
        
        if (this.searchEditText.getEditText() != null) {
            final EditText et = this.searchEditText.getEditText();
            et.setFocusable(true);
            et.setFocusableInTouchMode(true);
            et.setClickable(true);
            et.setOnClickListener(v -> focusSearch());
            et.setOnTouchListener((v, event) -> {
                int action = event.getAction();
                if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) && v.getParent() != null) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                }
                return false;
            });
        }
    }

    private void setupClearButton() {
        if (this.clearButton == null) return;
        this.clearButton.setOnClickListener(v -> {
            if (this.searchEditText != null) this.searchEditText.setText("");
            if (this.listener != null) this.listener.onSearchCleared();
        });
    }

    private void setupTextWatcher() {
        if (this.searchEditText == null) return;
        this.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (clearButton != null) clearButton.setVisibility(s.length() > 0 ? VISIBLE : GONE);
                if (listener != null) {
                    listener.onSearchChanged(s.toString());
                }
            }
        });
    }

    public void setOnSearchListener(OnSearchListener listener) {
        this.listener = listener;
    }

    public void setHint(String hint) {
        if (this.searchEditText != null) {
            this.searchEditText.setHint(hint);
        }
    }

    public void setTextSize(float size) {
        if (this.searchEditText != null) {
            this.searchEditText.setTextSize(size);
        }
    }

    public String getQuery() {
        return this.searchEditText != null ? this.searchEditText.getText().toString() : "";
    }

    public void setQuery(String query) {
        if (this.searchEditText != null) {
            this.searchEditText.setText(query);
            if (query != null && !query.isEmpty()) {
                this.searchEditText.setSelection(query.length());
            }
        }
    }

    public void clearQuery() {
        if (this.searchEditText != null) {
            this.searchEditText.setText("");
        }
        if (this.listener != null) {
            this.listener.onSearchCleared();
        }
    }
}
