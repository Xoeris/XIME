package xime.ui.adapter;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import xime.ui.layout.OrbitItemLayout;

public abstract class OrbitAdapter<VH extends OrbitAdapter.ViewHolder> {

    private final DataSetObservable mObservable = new DataSetObservable();

    public abstract int getItemCount();

    @NonNull
    public abstract VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType);

    public abstract void onBindViewHolder(@NonNull VH holder, int position);

    public int getItemViewType(int position) {
        return 0;
    }

    public final void notifyDataSetChanged() {
        mObservable.notifyChanged();
    }

    public void registerObserver(DataSetObserver observer) {
        mObservable.registerObserver(observer);
    }

    public void unregisterObserver(DataSetObserver observer) {
        mObservable.unregisterObserver(observer);
    }

    public static abstract class ViewHolder {
        public final OrbitItemLayout itemView;

        public ViewHolder(@NonNull OrbitItemLayout itemView) {
            this.itemView = itemView;
        }
    }
}

