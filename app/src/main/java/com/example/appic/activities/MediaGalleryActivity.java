package com.example.appic.activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.appic.R;
import com.example.appic.adapters.MediaPagerAdapter;
import com.example.appic.fragments.MediaItemFragment;
import com.example.appic.utils.ThemeHelper;

import java.util.ArrayList;

public class MediaGalleryActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_PATHS = "media_paths";
    public static final String EXTRA_MEDIA_TYPES = "media_types";
    public static final String EXTRA_START_POSITION = "start_position";

    private ViewPager2 viewPager;
    private TextView counterTextView;
    private MediaPagerAdapter pagerAdapter;
    private ArrayList<String> mediaPaths;
    private ArrayList<String> mediaTypes;

    private int lastVideoPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Применяем тему до super.onCreate()
        setTheme(ThemeHelper.getThemeResId(this));
        super.onCreate(savedInstanceState);

        // Системные бары видны (как в остальных экранах).
        // Стандартный (не edge-to-edge) режим: система сама применяет insets
        // к DecorView, контент располагается под статус-баром и над навигацией.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        setContentView(R.layout.activity_media_gallery);

        viewPager = findViewById(R.id.viewPager);
        counterTextView = findViewById(R.id.counterTextView);

        mediaPaths = getIntent().getStringArrayListExtra(EXTRA_MEDIA_PATHS);
        mediaTypes = getIntent().getStringArrayListExtra(EXTRA_MEDIA_TYPES);
        int startPosition = getIntent().getIntExtra(EXTRA_START_POSITION, 0);

        if (mediaPaths == null || mediaPaths.isEmpty()) {
            finish();
            return;
        }

        pagerAdapter = new MediaPagerAdapter(this, mediaPaths, mediaTypes);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setCurrentItem(startPosition, false);

        updateCounter(startPosition + 1, mediaPaths.size());
        lastVideoPosition = startPosition;

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateCounter(position + 1, mediaPaths.size());
                controlVideoPlayback(position);
            }
        });

        // Стартовая страница не получает onPageSelected —
        // откладываем вызов, чтобы ViewPager2 успел создать фрагменты.
        final int finalStart = startPosition;
        viewPager.post(() -> controlVideoPlayback(finalStart));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewPager != null && pagerAdapter != null) {
            final int current = viewPager.getCurrentItem();
            viewPager.post(() -> controlVideoPlayback(current));
        }
    }

    private void controlVideoPlayback(int currentPosition) {
        if (pagerAdapter == null) return;
        int total = pagerAdapter.getItemCount();

        if (lastVideoPosition != -1 && lastVideoPosition != currentPosition) {
            MediaItemFragment prev = pagerAdapter.getFragmentAt(lastVideoPosition);
            if (prev != null) {
                prev.setCurrentPage(false);
                prev.pauseVideo();
            }
        }

        for (int delta = -1; delta <= 1; delta++) {
            int pos = currentPosition + delta;
            if (pos < 0 || pos >= total) continue;
            MediaItemFragment f = pagerAdapter.getFragmentAt(pos);
            if (f == null) continue;
            if (pos == currentPosition) {
                f.setCurrentPage(true);
            } else {
                f.setCurrentPage(false);
                f.pauseVideo();
            }
        }

        lastVideoPosition = currentPosition;
    }

    private void updateCounter(int current, int total) {
        counterTextView.setText(getString(R.string.gallery_counter, current, total));
    }
}