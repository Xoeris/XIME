package xime.ui.adapter;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

import xime.ui.dialog.SurfaceDialog;

/**
 * ViewPager2-backed adapter that pages between screens, where each page's
 * root content view is a {@link SurfaceDialog} rather than a plain layout.
 * <p>
 * SurfaceDialog itself stays Fragment-free (still just a Layout/ViewGroup).
 * The Fragment indirection lives entirely in this adapter and in the
 * generated {@link SurfaceDialogPageFragment} host, so SurfaceDialog can still
 * be reused standalone (e.g. inside EdgePanel) without ever knowing about
 * FragmentManager.
 */
public class SurfaceAdapter extends FragmentStateAdapter {

    private final List<PageProvider> mProviders = new ArrayList<>();

    public SurfaceAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    public SurfaceAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    public SurfaceAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
        super(fragmentManager, lifecycle);
    }

    public void setPages(@NonNull List<PageProvider> providers) {
        mProviders.clear();
        mProviders.addAll(providers);
        notifyDataSetChanged();
    }

    public void addPage(@NonNull PageProvider provider) {
        mProviders.add(provider);
        notifyItemInserted(mProviders.size() - 1);
    }

    public void removePageAt(int position) {
        if (position < 0 || position >= mProviders.size()) return;
        mProviders.remove(position);
        notifyItemRemoved(position);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return SurfaceDialogPageFragment.newInstance(position);
    }

    @Override
    public int getItemCount() {
        return mProviders.size();
    }

    PageProvider getProvider(int position) {
        if (position < 0 || position >= mProviders.size()) return null;
        return mProviders.get(position);
    }

    /**
     * Supplies the content view for a single page's SurfaceDialog.
     */
    public interface PageProvider {
        @NonNull
        View onCreatePageContent(@NonNull Context context, int position);

        /**
         * Optional hook fired once the page's SurfaceDialog is created and populated.
         */
        default void onPageSurfaceDialogReady(@NonNull SurfaceDialog panel, int position) {
        }
    }

    /**
     * Internal host Fragment. Not meant to be instantiated directly outside
     * this adapter, its only job is to give ViewPager2 a Fragment to manage
     * while the actual content lives in a SurfaceDialog.
     */
    public static class SurfaceDialogPageFragment extends Fragment {
        private static final String ARG_POSITION = "surface_panel_position";

        public static SurfaceDialogPageFragment newInstance(int position) {
            SurfaceDialogPageFragment fragment = new SurfaceDialogPageFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_POSITION, position);
            fragment.setArguments(args);
            return fragment;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            int position = getArguments() != null ? getArguments().getInt(ARG_POSITION) : 0;

            SurfaceDialog panel = new SurfaceDialog(inflater.getContext());

            SurfaceAdapter adapter = findAdapter(container);
            PageProvider provider = adapter != null ? adapter.getProvider(position) : null;

            if (provider != null) {
                View content = provider.onCreatePageContent(inflater.getContext(), position);
                panel.addView(content, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                provider.onPageSurfaceDialogReady(panel, position);
            }

            return panel;
        }

        /**
         * ViewPager2 doesn't hand the Fragment a direct adapter reference, so
         * we walk up from the container to the ViewPager2 to find it. This is
         * the standard workaround for this limitation.
         */
        @Nullable
        private SurfaceAdapter findAdapter(@Nullable ViewGroup container) {
            if (container == null) return null;
            ViewGroup parent = container;
            while (parent != null) {
                if (parent instanceof androidx.viewpager2.widget.ViewPager2) {
                    androidx.viewpager2.widget.ViewPager2 pager = (androidx.viewpager2.widget.ViewPager2) parent;
                    if (pager.getAdapter() instanceof SurfaceAdapter) {
                        return (SurfaceAdapter) pager.getAdapter();
                    }
                }
                if (parent.getParent() instanceof ViewGroup) {
                    parent = (ViewGroup) parent.getParent();
                } else {
                    break;
                }
            }
            return null;
        }
    }
}
