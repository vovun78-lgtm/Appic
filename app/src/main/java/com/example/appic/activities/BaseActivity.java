package com.example.appic.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;

import com.example.appic.R;
import com.example.appic.utils.ThemeHelper;

public abstract class BaseActivity extends AppCompatActivity {

    private int currentThemeId = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        currentThemeId = ThemeHelper.getThemeResId(this);
        setTheme(currentThemeId);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int newThemeId = ThemeHelper.getThemeResId(this);
        if (currentThemeId != newThemeId) {
            recreate();
            return;
        }
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) ThemeHelper.applyToToolbar(toolbar);
    }

    protected boolean shouldAddGlobalSearch() { return true; }
    protected boolean shouldAddHelp() { return true; }
    protected boolean shouldAddSettings() { return true; }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);

        if (shouldAddGlobalSearch() && !(this instanceof GlobalSearchActivity)) {
            MenuItem searchItem = menu.add(Menu.NONE, R.id.action_search, 0, R.string.global_search);
            searchItem.setIcon(R.drawable.ic_search);
            searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            result = true;
        }

        if (shouldAddHelp() && !(this instanceof HelpActivity)) {
            // ИСПРАВЛЕНО: строки из ресурсов
            MenuItem helpItem = menu.add(Menu.NONE, R.id.action_help, 100, R.string.action_help_title);
            helpItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            result = true;
        }

        if (shouldAddSettings() && !(this instanceof SettingsActivity)) {
            MenuItem settingsItem = menu.add(Menu.NONE, R.id.action_settings, 200, R.string.action_settings_title);
            settingsItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            result = true;
        }

        return result;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_search) {
            if (!(this instanceof GlobalSearchActivity)) {
                startActivity(new Intent(this, GlobalSearchActivity.class));
            }
            return true;
        }

        if (id == R.id.action_help) {
            startActivity(new Intent(this, HelpActivity.class));
            return true;
        }

        if (id == R.id.action_settings) {
            if (!(this instanceof SettingsActivity)) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
            return true;
        }

        if (id == R.id.action_statistics) {
            startActivity(new Intent(this, StatisticsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}