package xime.ui.adapter;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import xime.ui.layout.RecyclerView;

public class SingleAdapter extends RecyclerView.Adapter<SingleAdapter.ViewHolder> {

    private final View view;

    public SingleAdapter(@NonNull View view) {
        this.view = view;
        setHasStableIds(true);
    }

    public View getView() {
        return view;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (view.getParent() != null) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        return new ViewHolder(view);
    }

    @Override
    public int getItemViewType(int position) {
        return view.hashCode();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.setIsRecyclable(false);
    }

    @Override
    public long getItemId(int position) {
        return view.hashCode();
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}

