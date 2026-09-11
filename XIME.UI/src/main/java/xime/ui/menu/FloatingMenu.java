package xime.ui.menu;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import xime.R;
import xime.ui.layout.BlurLayout;
import xime.ui.layout.Layout;
import xime.ui.view.ImageView;
import xime.ui.view.TextView;

public class FloatingMenu extends Layout {
    private View anchorView;
    private boolean isAnchorSelected = false;
    private final List<float[]> layerPositions = new ArrayList<>();
    private final List<View> layerViews = new ArrayList<>();
    private final OnLayerSelectedListener listener;
    private int selectedLayer;
    private final int totalLayers;
    private final int[] icons;
    private String[] labels;

    public interface OnLayerSelectedListener {
        void onLayerSelected(int layer);
    }

    public FloatingMenu(@NonNull Context context, int totalLayers, int currentLayer, int primaryColor, int[] icons, OnLayerSelectedListener listener) {
        this(context, totalLayers, currentLayer, primaryColor, icons, null, listener);
    }

    public FloatingMenu(@NonNull Context context, int totalLayers, int currentLayer, int primaryColor, int[] icons, String[] labels, OnLayerSelectedListener listener) {
        super(context);
        this.totalLayers = totalLayers;
        this.selectedLayer = currentLayer;
        this.icons = icons;
        this.labels = labels;
        this.listener = listener;
        init();
    }

    private void init() {
        // Background ring
        View ring = new View(getContext());
        int ringSize = dpToPx(220);
        GradientDrawable ringDrawable = new GradientDrawable();
        ringDrawable.setShape(GradientDrawable.OVAL);
        ringDrawable.setStroke(dpToPx(1), Color.parseColor("#15FFFFFF"));
        ring.setBackground(ringDrawable);
        addView(ring, new LayoutParams(ringSize, ringSize, 17));
        
        // Center anchor button
        BlurLayout blurAnchor = new BlurLayout(getContext());
        blurAnchor.setBlurType(BlurLayout.BlurType.CRYSTAL);
        this.anchorView = blurAnchor;
        int anchorSize = dpToPx(84);
        blurAnchor.setBlurRadius(50.0f);
        blurAnchor.setCornerRadius(anchorSize / 2.0f);
        
        GradientDrawable anchorBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, 
                new int[]{Color.parseColor("#8E8CFF"), Color.parseColor("#6A67FF")});
        anchorBg.setShape(GradientDrawable.OVAL);
        this.anchorView.setBackground(anchorBg);
        
        ImageView exitIcon = new ImageView(getContext());
        exitIcon.setImageResource(R.drawable.xoeris_exit);
        exitIcon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        exitIcon.setPadding(dpToPx(22), dpToPx(22), dpToPx(22), dpToPx(22));
        blurAnchor.addView(exitIcon, new LayoutParams(-1, -1));
        addView(this.anchorView, new LayoutParams(anchorSize, anchorSize, 17));
        
        float radius = dpToPx(110);
        float angleStep = totalLayers > 0 ? 360.0f / totalLayers : 90.0f;

        for (int i = 0; i < this.totalLayers; i++) {
            BlurLayout layerItem = new BlurLayout(getContext());
            layerItem.setBlurType(BlurLayout.BlurType.CRYSTAL);
            int itemSize = dpToPx(50);
            layerItem.setBlurRadius(40.0f);
            layerItem.setCornerRadius(itemSize / 2.0f);
            
            GradientDrawable itemBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, 
                    new int[]{Color.parseColor("#D0D9FF"), Color.parseColor("#A8B4FF")});
            itemBg.setShape(GradientDrawable.OVAL);
            layerItem.setBackground(itemBg);
            
            if (icons != null && i < icons.length && icons[i] != 0) {
                ImageView iconView = new ImageView(getContext());
                iconView.setImageResource(icons[i]);
                iconView.setImageTintList(ColorStateList.valueOf(Color.parseColor("#4A47A3")));
                iconView.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
                layerItem.addView(iconView, new LayoutParams(-1, -1));
            } else {
                TextView text = new TextView(getContext());
                String label = (labels != null && i < labels.length) ? labels[i] : String.valueOf(i + 1);
                text.setText(label);
                text.setGravity(17);
                text.setTextColor(Color.parseColor("#4A47A3"));
                text.setTextSize(10.0f);
                text.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                layerItem.addView(text, new LayoutParams(-1, -1));
            }
            
            this.layerViews.add(layerItem);
            
            // Positioning: Start at -45 degrees from top (top is 270)
            // -45 from top = 225. We go counter-clockwise (decreasing angle)
            double radians = Math.toRadians(225.0f - (i * angleStep));
            float fCos = (float) (radius * Math.cos(radians));
            float fSin = (float) (radius * Math.sin(radians));
            this.layerPositions.add(new float[]{fCos, fSin});
            
            LayoutParams lp = new LayoutParams(itemSize, itemSize, 17);
            lp.leftMargin = (int) fCos;
            lp.topMargin = (int) fSin;
            addView(layerItem, lp);
        }
        updateSelection(this.selectedLayer);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setBlurRootView(View root) {
        for (View v : this.layerViews) {
            if (v instanceof BlurLayout) {
                ((BlurLayout) v).setBlurRootView(root);
            }
        }
        if (this.anchorView instanceof BlurLayout) {
            ((BlurLayout) this.anchorView).setBlurRootView(root);
        }
    }

    public void updateSelectionByTouch(float relX, float relY) {
        float distToAnchorSq = (relX * relX) + (relY * relY);
        boolean onAnchor = distToAnchorSq < (dpToPx(50) * dpToPx(50));
        
        if (onAnchor) {
            if (!this.isAnchorSelected) {
                this.isAnchorSelected = true;
                this.anchorView.animate().scaleX(1.3f).scaleY(1.3f).setDuration(200).start();
                updateSelection(-1);
            }
            return;
        }
        
        if (this.isAnchorSelected) {
            this.isAnchorSelected = false;
            this.anchorView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
        }
        
        int closestLayer = -1;
        float minDistanceSq = Float.MAX_VALUE;
        for (int i = 0; i < this.layerPositions.size(); i++) {
            float[] pos = this.layerPositions.get(i);
            float dx = relX - pos[0];
            float dy = relY - pos[1];
            float distanceSq = (dx * dx) + (dy * dy);
            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
                closestLayer = i;
            }
        }
        
        if (minDistanceSq < dpToPx(80) * dpToPx(80) && closestLayer != this.selectedLayer) {
            this.selectedLayer = closestLayer;
            updateSelection(this.selectedLayer);
        }
    }

    private void updateSelection(int layer) {
        for (int i = 0; i < this.layerViews.size(); i++) {
            boolean isSelected = i == layer;
            View v = this.layerViews.get(i);
            float scale = isSelected ? 1.3f : 1.0f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(200)
                    .setInterpolator(new OvershootInterpolator()).start();
        }
    }

    public boolean isAnchorSelected() {
        return this.isAnchorSelected;
    }

    public int getSelectedLayer() {
        return this.selectedLayer;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
