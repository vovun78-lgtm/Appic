package com.example.appic.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.appic.fragments.MediaItemFragment;

import java.util.ArrayList;

public class MediaPagerAdapter extends FragmentStateAdapter {

    private final FragmentActivity fragmentActivity;
    private final ArrayList<String> mediaPaths;
    private final ArrayList<String> mediaTypes;

    public MediaPagerAdapter(@NonNull FragmentActivity fragmentActivity,
                             ArrayList<String> mediaPaths,
                             ArrayList<String> mediaTypes) {
        super(fragmentActivity);
        this.fragmentActivity = fragmentActivity;
        this.mediaPaths = mediaPaths;
        this.mediaTypes = mediaTypes;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        String path = mediaPaths.get(position);
        String type = (mediaTypes != null && position < mediaTypes.size()) ? mediaTypes.get(position) : null;
        return MediaItemFragment.newInstance(path, type);
    }

    @Override
    public int getItemCount() {
        return mediaPaths.size();
    }

    /**
     * Возвращает уже созданный фрагмент для страницы.
     * <p>
     * Способ 1: внутренний тег FragmentStateAdapter — "f" + getItemId(position).
     * Стабилен в 1.0.0, но это не публичный контракт.
     * <p>
     * Способ 2 (fallback): перебор всех фрагментов FragmentManager и поиск
     * по mediaPath. Работает, даже если внутренний тег изменится в будущих
     * версиях библиотеки.
     */
    public MediaItemFragment getFragmentAt(int position) {
        if (position < 0 || position >= mediaPaths.size()) return null;
        String expectedPath = mediaPaths.get(position);

        // Способ 1
        Fragment byTag = fragmentActivity.getSupportFragmentManager()
                .findFragmentByTag("f" + getItemId(position));
        if (byTag instanceof MediaItemFragment) return (MediaItemFragment) byTag;

        // Способ 2 — fallback
        for (Fragment f : fragmentActivity.getSupportFragmentManager().getFragments()) {
            if (f instanceof MediaItemFragment) {
                MediaItemFragment mif = (MediaItemFragment) f;
                if (expectedPath.equals(mif.getMediaPath())) return mif;
            }
        }
        return null;
    }
}