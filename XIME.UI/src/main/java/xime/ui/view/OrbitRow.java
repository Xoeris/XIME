package xime.ui.view;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xime.ui.common.IconButton;
import xime.ui.layout.CardLayout;
import xime.ui.layout.LinearLayout;

public class OrbitRow extends CardLayout {
    private TextView mediaDuration;
    private TextView mediaExtension;
    private PictureView mediaImage;
    private IconButton mediaMoreButton;
    private TextView mediaSubtitle;
    private TextView mediaTitle;

    public OrbitRow(@NonNull Context context) {
        super(context);
        init(context);
    }

    public OrbitRow(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OrbitRow(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setClickable(true);
        setFocusable(true);
        setRadius(dp(12));
        setCardElevation(0.0f);
        
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(-1, -2);
        layoutParams.setMargins(dp(16), dp(4), dp(16), dp(4));
        setLayoutParams(layoutParams);
        
        LinearLayout rootParallel = new LinearLayout(context);
        rootParallel.setOrientation(LinearLayout.HORIZONTAL);
        rootParallel.setGravity(android.view.Gravity.CENTER_VERTICAL);
        rootParallel.setPadding(dp(12), dp(12), dp(12), dp(12));
        addView(rootParallel, new FrameLayout.LayoutParams(-1, -2));
        
        CardLayout mediaCard = new CardLayout(context);
        mediaCard.setRadius(dp(8));
        mediaCard.setCardElevation(0.0f);
        LinearLayout.LayoutParams mediaCardParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        rootParallel.addView(mediaCard, mediaCardParams);
        
        this.mediaImage = new PictureView(context);
        this.mediaImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaCard.addView(this.mediaImage, new FrameLayout.LayoutParams(-1, -1));
        
        LinearLayout contentVertical = new LinearLayout(context);
        contentVertical.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        contentParams.setMarginStart(dp(12));
        contentParams.setMarginEnd(dp(8));
        rootParallel.addView(contentVertical, contentParams);
        
        LinearLayout textTopRow = new LinearLayout(context);
        textTopRow.setOrientation(LinearLayout.HORIZONTAL);
        textTopRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        contentVertical.addView(textTopRow, new LinearLayout.LayoutParams(-1, -2));
        
        this.mediaTitle = new TextView(context);
        this.mediaTitle.setMaxLines(1);
        this.mediaTitle.setEllipsize(TextUtils.TruncateAt.END);
        this.mediaTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        textTopRow.addView(this.mediaTitle, new LinearLayout.LayoutParams(0, -2, 1.0f));
        
        this.mediaExtension = new TextView(context);
        this.mediaExtension.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams extParams = new LinearLayout.LayoutParams(-2, -2);
        extParams.setMarginStart(dp(8));
        textTopRow.addView(this.mediaExtension, extParams);
        
        LinearLayout textBottomRow = new LinearLayout(context);
        textBottomRow.setOrientation(LinearLayout.HORIZONTAL);
        textBottomRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomRowParams = new LinearLayout.LayoutParams(-1, -2);
        bottomRowParams.topMargin = dp(2);
        contentVertical.addView(textBottomRow, bottomRowParams);
        
        this.mediaSubtitle = new TextView(context);
        this.mediaSubtitle.setMaxLines(1);
        this.mediaSubtitle.setEllipsize(TextUtils.TruncateAt.END);
        textBottomRow.addView(this.mediaSubtitle, new LinearLayout.LayoutParams(0, -2, 1.0f));
        
        this.mediaDuration = new TextView(context);
        this.mediaDuration.setAlpha(0.6f);
        LinearLayout.LayoutParams durParams = new LinearLayout.LayoutParams(-2, -2);
        durParams.setMarginStart(dp(8));
        textBottomRow.addView(this.mediaDuration, durParams);
        
        this.mediaMoreButton = new IconButton(context);
        this.mediaMoreButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        this.mediaMoreButton.setClickable(true);
        this.mediaMoreButton.setFocusable(true);
        rootParallel.addView(this.mediaMoreButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
    }

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value);
    }

    public PictureView getMediaImage() { return this.mediaImage; }
    public TextView getMediaTitle() { return this.mediaTitle; }
    public TextView getMediaSubtitle() { return this.mediaSubtitle; }
    public TextView getMediaExtension() { return this.mediaExtension; }
    public TextView getMediaDuration() { return this.mediaDuration; }
    public IconButton getMediaMoreButton() { return this.mediaMoreButton; }
}
