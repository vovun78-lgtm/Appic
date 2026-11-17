package com.example.appic.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.bumptech.glide.Glide;
import com.example.appic.R;
import com.example.appic.activities.OnboardingActivity;
import com.example.appic.utils.MediaHelper;
import com.example.appic.utils.ThemeHelper;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends PreferenceFragmentCompat {

    private ListPreference darkModePref;
    private ListPreference colorThemePref;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> splashImagePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) saveCustomSplash(uri);
                }
            });

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        ThemeHelper.migrateLegacyPrefs(requireContext());
        setPreferencesFromResource(R.xml.preferences, rootKey);

        darkModePref = findPreference(ThemeHelper.KEY_DARK_MODE);
        setupDarkModePreference();

        colorThemePref = findPreference(ThemeHelper.KEY_COLOR_THEME);
        setupColorThemePreference();

        Preference editTermsPref = findPreference("edit_terms");
        if (editTermsPref != null) {
            editTermsPref.setOnPreferenceClickListener(pref -> {
                startActivity(new Intent(requireContext(), OnboardingActivity.class));
                return true;
            });
        }

        Preference customSplashPref = findPreference("custom_splash");
        if (customSplashPref != null) {
            customSplashPref.setOnPreferenceClickListener(pref -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                splashImagePicker.launch(intent);
                return true;
            });
        }

        Preference resetSplashPref = findPreference("reset_splash");
        if (resetSplashPref != null) {
            resetSplashPref.setOnPreferenceClickListener(pref -> {
                File customSplash = new File(requireContext().getFilesDir(), "custom_splash.png");
                if (customSplash.exists()) {
                    customSplash.delete();
                    Glide.get(requireContext()).clearMemory();
                    Toast.makeText(getContext(), R.string.splash_reset, Toast.LENGTH_SHORT).show();
                    refreshPreferenceList();
                } else {
                    Toast.makeText(getContext(), R.string.splash_default_active, Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        Preference resetPref = findPreference("reset_defaults");
        if (resetPref != null) {
            resetPref.setOnPreferenceClickListener(pref -> {
                ThemeHelper.resetToDefaults(requireContext());
                Toast.makeText(getContext(), R.string.settings_reset_done, Toast.LENGTH_SHORT).show();
                requireActivity().recreate();
                return true;
            });
        }
    }

    private void saveCustomSplash(Uri uri) {
        Toast.makeText(getContext(), R.string.splash_saving, Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                File destFile = new File(requireContext().getFilesDir(), "custom_splash.png");
                MediaHelper.copyUriToFile(requireContext().getContentResolver(), uri, destFile);
                mainHandler.post(() -> {
                    if (isAdded() && getContext() != null) {
                        Glide.get(requireContext()).clearMemory();
                        Toast.makeText(getContext(), R.string.splash_saved, Toast.LENGTH_SHORT).show();
                        refreshPreferenceList();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(getContext(), R.string.splash_save_error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void refreshPreferenceList() {
        if (getListView() != null && getListView().getAdapter() != null) {
            getListView().getAdapter().notifyDataSetChanged();
        }
    }

    private void setupDarkModePreference() {
        if (darkModePref == null) return;
        int mode = ThemeHelper.getDarkMode(requireContext());
        darkModePref.setValue(String.valueOf(mode));
        updateDarkModeSummary(mode);

        darkModePref.setOnPreferenceChangeListener((preference, newValue) -> {
            try {
                int newMode = Integer.parseInt(newValue.toString());
                ThemeHelper.setDarkMode(requireContext(), newMode);
                updateDarkModeSummary(newMode);
                return true;
            } catch (NumberFormatException e) { return false; }
        });
    }

    private void updateDarkModeSummary(int mode) {
        if (darkModePref == null) return;
        String summary;
        switch (mode) {
            case ThemeHelper.DARK_MODE_LIGHT: summary = getString(R.string.dark_mode_light); break;
            case ThemeHelper.DARK_MODE_DARK: summary = getString(R.string.dark_mode_dark); break;
            case ThemeHelper.DARK_MODE_SYSTEM: summary = getString(R.string.dark_mode_system); break;
            default: summary = getString(R.string.dark_mode_unknown);
        }
        darkModePref.setSummary(summary);
    }

    private void setupColorThemePreference() {
        if (colorThemePref == null) return;
        String currentTheme = ThemeHelper.getColorTheme(requireContext());
        colorThemePref.setValue(currentTheme);
        updateColorThemeSummary(currentTheme);

        colorThemePref.setOnPreferenceChangeListener((preference, newValue) -> {
            String newTheme = newValue.toString();
            ThemeHelper.setColorTheme(requireContext(), newTheme);
            updateColorThemeSummary(newTheme);
            requireActivity().recreate();
            return true;
        });
    }

    private void updateColorThemeSummary(String themeValue) {
        if (colorThemePref == null) return;
        CharSequence[] entries = colorThemePref.getEntries();
        CharSequence[] values = colorThemePref.getEntryValues();
        String summary = getString(R.string.color_theme_default_summary);
        for (int i = 0; i < values.length; i++) {
            if (values[i].toString().equals(themeValue)) { summary = entries[i].toString(); break; }
        }
        colorThemePref.setSummary(summary);
    }

    /**
     * ИСПРАВЛЕНО (п.2): при recreate() (смена темы/цвета) фрагмент пересоздаётся,
     * старый executor остаётся жить. Теперь корректно останавливаем.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }
}