package xime.ui.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xime.ui.layout.Layout;
import xime.ui.view.EditText;
import xime.ui.view.ImageView;
import xime.ui.view.TextView;
import xime.R;

public class TextField extends Layout {

    private TextView mTextBase;
    private ImageView mIconView;

    public TextField(@NonNull Context context) {
        this(context, null);
    }

    public TextField(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TextField(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private EditText mHiddenEditText;

    private void init(Context context, AttributeSet attrs) {
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundResource(R.drawable.xoeris_bg_control_code);

        float textSize = 15.0f; // Default SP
        int textColor = android.graphics.Color.WHITE;
        String hint = "";
        int textColorHint = 0x60888899;
        int iconResId = 0;
        int iconTint = android.graphics.Color.WHITE;

        int paddingVal = -1;
        int paddingLeftVal = -1;
        int paddingTopVal = -1;
        int paddingRightVal = -1;
        int paddingBottomVal = -1;
        int paddingStartVal = -1;
        int paddingEndVal = -1;

        if (attrs != null) {
            // First, get standard android attributes (MUST be sorted in ascending order of resource IDs)
            int[] stdAttrs = new int[] {
                android.R.attr.textSize,        // 0x01010095 (index 0)
                android.R.attr.textColor,       // 0x01010098 (index 1)
                android.R.attr.padding,         // 0x010100d5 (index 2)
                android.R.attr.paddingLeft,     // 0x010100d6 (index 3)
                android.R.attr.paddingTop,      // 0x010100d7 (index 4)
                android.R.attr.paddingRight,    // 0x010100d8 (index 5)
                android.R.attr.paddingBottom,   // 0x010100d9 (index 6)
                android.R.attr.hint,            // 0x01010150 (index 7)
                android.R.attr.textColorHint,   // 0x01010151 (index 8)
                android.R.attr.paddingStart,    // 0x010103b3 (index 9)
                android.R.attr.paddingEnd       // 0x010103b4 (index 10)
            };
            TypedArray stdA = context.obtainStyledAttributes(attrs, stdAttrs);
            try {
                float rawSize = stdA.getDimension(0, -1);
                if (rawSize != -1) {
                    textSize = rawSize / getResources().getDisplayMetrics().scaledDensity;
                }
                textColor = stdA.getColor(1, textColor);
                paddingVal = stdA.getDimensionPixelSize(2, -1);
                paddingLeftVal = stdA.getDimensionPixelSize(3, -1);
                paddingTopVal = stdA.getDimensionPixelSize(4, -1);
                paddingRightVal = stdA.getDimensionPixelSize(5, -1);
                paddingBottomVal = stdA.getDimensionPixelSize(6, -1);
                hint = stdA.getString(7);
                if (hint == null) hint = "";
                textColorHint = stdA.getColor(8, textColorHint);
                paddingStartVal = stdA.getDimensionPixelSize(9, -1);
                paddingEndVal = stdA.getDimensionPixelSize(10, -1);
            } finally {
                stdA.recycle();
            }

            // Then get custom Xoeris attributes
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TextField);
            try {
                iconResId = a.getResourceId(R.styleable.TextField_xoerisIcon, 0);
                iconTint = a.getColor(R.styleable.TextField_xoerisIconTint, android.graphics.Color.WHITE);
                
                float cornerRadius = a.getDimension(R.styleable.TextField_xoerisCornerRadius, -1);
                if (cornerRadius != -1) {
                    android.graphics.drawable.Drawable bg = getBackground();
                    if (bg instanceof android.graphics.drawable.GradientDrawable) {
                        ((android.graphics.drawable.GradientDrawable) bg).setCornerRadius(cornerRadius);
                    }
                }
            } finally {
                a.recycle();
            }
        }

        // Ensure we have some padding for the "Bar" look
        float density = context.getResources().getDisplayMetrics().density;
        int defaultPaddingSide = (int) (density * 20.0f);
        int defaultPaddingTopBottom = (int) (density * 16.0f);

        int finalPaddingLeft = defaultPaddingSide;
        int finalPaddingTop = defaultPaddingTopBottom;
        int finalPaddingRight = defaultPaddingSide;
        int finalPaddingBottom = defaultPaddingTopBottom;

        if (paddingVal != -1) {
            finalPaddingLeft = paddingVal;
            finalPaddingTop = paddingVal;
            finalPaddingRight = paddingVal;
            finalPaddingBottom = paddingVal;
        }
        if (paddingLeftVal != -1) finalPaddingLeft = paddingLeftVal;
        if (paddingRightVal != -1) finalPaddingRight = paddingRightVal;
        if (paddingTopVal != -1) finalPaddingTop = paddingTopVal;
        if (paddingBottomVal != -1) finalPaddingBottom = paddingBottomVal;

        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            if (paddingStartVal != -1) finalPaddingRight = paddingStartVal;
            if (paddingEndVal != -1) finalPaddingLeft = paddingEndVal;
        } else {
            if (paddingStartVal != -1) finalPaddingLeft = paddingStartVal;
            if (paddingEndVal != -1) finalPaddingRight = paddingEndVal;
        }

        setPadding(finalPaddingLeft, finalPaddingTop, finalPaddingRight, finalPaddingBottom);

        mHiddenEditText = new EditText(context);
        mHiddenEditText.setBackground(null);
        mHiddenEditText.setTextColor(android.graphics.Color.TRANSPARENT);
        mHiddenEditText.setHintTextColor(android.graphics.Color.TRANSPARENT); 
        mHiddenEditText.setPadding(0, 0, 0, 0);
        mHiddenEditText.setCursorVisible(true);
        mHiddenEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        mHiddenEditText.setTypeface(Typeface.DEFAULT);
        mHiddenEditText.setMinimumHeight(0);
        mHiddenEditText.setMinimumWidth(0);
        mHiddenEditText.setIncludeFontPadding(false);
        mHiddenEditText.setGravity(android.view.Gravity.CENTER_VERTICAL);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            mHiddenEditText.setTextCursorDrawable(R.drawable.xoeris_white_cursor);
        }
        
        LayoutParams editLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        if (iconResId != 0) {
            editLp.rightMargin = (int) (density * 32.0f);
        }
        addView(mHiddenEditText, editLp);

        mTextBase = new TextView(context);
        mTextBase.setClickable(false);
        mTextBase.setFocusable(false);
        mTextBase.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        mTextBase.setTypeface(Typeface.DEFAULT);
        mTextBase.setTextColor(textColor);
        mTextBase.setHint(hint);
        mTextBase.setHintTextColor(textColorHint);
        mTextBase.setMinimumHeight(0);
        mTextBase.setMinimumWidth(0);
        mTextBase.setIncludeFontPadding(false);
        mTextBase.setGravity(android.view.Gravity.CENTER_VERTICAL);
        mTextBase.setPadding(0, 0, 0, 0);
        
        LayoutParams textLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        textLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        if (iconResId != 0) {
            textLp.rightMargin = (int) (density * 32.0f);
        }
        addView(mTextBase, textLp);

        if (iconResId != 0) {
            mIconView = new ImageView(context);
            mIconView.setImageResource(iconResId);
            mIconView.setColorFilter(iconTint);
            LayoutParams iconLp = new LayoutParams((int)(density * 20), (int)(density * 20));
            iconLp.gravity = android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL;
            addView(mIconView, iconLp);
        }

        mHiddenEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                mTextBase.setText(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        mHiddenEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                mHiddenEditText.postDelayed(() -> {
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                        getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        if (mHiddenEditText.hasWindowFocus()) {
                            if (!imm.showSoftInput(mHiddenEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)) {
                                imm.showSoftInput(mHiddenEditText, android.view.inputmethod.InputMethodManager.SHOW_FORCED);
                            }
                        }
                    }
                }, 150);
            }
        });

        setOnClickListener(v -> {
            mHiddenEditText.requestFocus();
        });
    }

    @Override
    public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            if (mHiddenEditText != null) mHiddenEditText.requestFocus();
        }
        return super.onInterceptTouchEvent(ev);
    }

    public EditText getEditText() {
        return mHiddenEditText;
    }

    public void setText(CharSequence text) {
        if (mHiddenEditText != null) {
            mHiddenEditText.setText(text);
        }
        if (mTextBase != null) {
            mTextBase.setText(text != null ? text.toString() : "");
        }
    }

    public String getText() {
        return mHiddenEditText != null ? mHiddenEditText.getText().toString() : "";
    }

    public void setHint(CharSequence hint) {
        if (mTextBase != null) {
            mTextBase.setHint(hint != null ? hint.toString() : "");
        }
        if (mHiddenEditText != null) {
            mHiddenEditText.setHint(hint);
        }
    }

    public void setTextSize(float size) {
        if (mTextBase != null) mTextBase.setTextSize(size);
        if (mHiddenEditText != null) mHiddenEditText.setTextSize(size);
    }

    public void addTextChangedListener(TextWatcher watcher) {
        if (mHiddenEditText != null) {
            mHiddenEditText.addTextChangedListener(watcher);
        }
    }

    public void setSelection(int index) {
        if (mHiddenEditText != null) {
            mHiddenEditText.setSelection(Math.min(index, mHiddenEditText.length()));
        }
    }

    public void setInputType(int type) {
        if (mHiddenEditText != null) {
            mHiddenEditText.setInputType(type);

            // Sync multiline state with the visual TextView
            if (mTextBase != null) {
                boolean isMultiLine = (type & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
                mTextBase.setSingleLine(!isMultiLine);
                if (isMultiLine) {
                    mTextBase.setEllipsize(null);
                } else {
                    mTextBase.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
                }
            }
        }
    }

    public void setMaxLines(int maxLines) {
        if (mHiddenEditText != null) {
            mHiddenEditText.setMaxLines(maxLines);
        }
        if (mTextBase != null) {
            mTextBase.setMaxLines(maxLines);
        }
    }

    public void setTextCursorDrawable(int resId) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (mHiddenEditText != null) {
                mHiddenEditText.setTextCursorDrawable(resId);
            }
        }
    }

    public void setIcon(int resId) {
        if (resId != 0) {
            if (mIconView == null) {
                mIconView = new ImageView(getContext());
                float density = getResources().getDisplayMetrics().density;
                LayoutParams iconLp = new LayoutParams((int)(density * 20), (int)(density * 20));
                iconLp.gravity = android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL;
                addView(mIconView, iconLp);
                
                // Adjust text and edit margins
                if (mHiddenEditText != null) {
                    ((LayoutParams) mHiddenEditText.getLayoutParams()).rightMargin = (int) (density * 32.0f);
                }
                if (mTextBase != null) {
                    ((LayoutParams) mTextBase.getLayoutParams()).rightMargin = (int) (density * 32.0f);
                }
            }
            mIconView.setImageResource(resId);
            mIconView.setVisibility(VISIBLE);
        } else if (mIconView != null) {
            mIconView.setVisibility(GONE);
        }
    }

    public void setIconTint(int color) {
        if (mIconView != null) {
            mIconView.setColorFilter(color);
        }
    }
}
