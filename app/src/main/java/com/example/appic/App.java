package com.example.appic;

import android.app.Application;
import android.database.Cursor;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.appic.database.AppDatabase;
import com.example.appic.utils.ThemeHelper;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {

    private static final String TAG = "App";

    private static final long STALE_FILE_AGE_MS = 24L * 60 * 60 * 1000;

    private final ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        // Миграция legacy prefs больше не нужна — приложение новое.
        // Применяем сохранённую тему (или дефолтную) при старте.
        ThemeHelper.setDarkMode(this, ThemeHelper.getDarkMode(this));
        cleanupExecutor.execute(this::runCleanup);
    }

    private void runCleanup() {
        try {
            cleanupStaleCache();
        } catch (Exception e) {
            Log.w(TAG, "cleanupStaleCache failed", e);
        }
        try {
            cleanupOrphanedMedia();
        } catch (Exception e) {
            Log.w(TAG, "cleanupOrphanedMedia failed", e);
        }
    }

    private void cleanupStaleCache() {
        File cacheDir = getCacheDir();
        if (cacheDir == null || !cacheDir.isDirectory()) return;

        File[] files = cacheDir.listFiles();
        if (files == null) return;

        long now = System.currentTimeMillis();
        int deletedZips = 0;
        int deletedDirs = 0;

        for (File f : files) {
            if (f == null) continue;
            String name = f.getName();
            try {
                if (f.isFile()) {
                    long lm = f.lastModified();
                    boolean stale = name.endsWith(".zip")
                            && lm > 0
                            && (now - lm > STALE_FILE_AGE_MS);
                    if (stale) {
                        if (f.delete()) deletedZips++;
                        else Log.w(TAG, "Failed to delete stale temp zip: " + name);
                    }
                } else if (f.isDirectory()) {
                    if (name.startsWith("export_temp") || name.equals("import_temp")) {
                        deleteRecursive(f);
                        deletedDirs++;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Cleanup failed for: " + name, e);
            }
        }

        if (deletedZips > 0 || deletedDirs > 0) {
            Log.i(TAG, "Cache cleanup: zips=" + deletedZips + " dirs=" + deletedDirs);
        }
    }

    private void deleteRecursive(File fileOrDir) {
        if (fileOrDir == null) return;
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        if (!fileOrDir.delete()) {
            Log.w(TAG, "Failed to delete: " + fileOrDir.getAbsolutePath());
        }
    }

    private void cleanupOrphanedMedia() {
        File mediaDir = getExternalFilesDir("media");
        if (mediaDir == null) mediaDir = new File(getFilesDir(), "media");
        if (!mediaDir.isDirectory()) return;

        File[] files = mediaDir.listFiles();
        if (files == null || files.length == 0) return;

        Set<String> referenced = new HashSet<>();
        try {
            SupportSQLiteDatabase db = AppDatabase.getInstance(this)
                    .getOpenHelper()
                    .getWritableDatabase();
            Cursor c = null;
            try {
                c = db.query("SELECT path FROM media");
                int idx = c.getColumnIndex("path");
                if (idx < 0) return;
                while (c.moveToNext()) {
                    String p = c.getString(idx);
                    if (p != null && !p.isEmpty()) referenced.add(p);
                }
            } finally {
                if (c != null) c.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "cleanupOrphanedMedia: DB unavailable, skip", e);
            return;
        }

        long now = System.currentTimeMillis();
        int deleted = 0;
        for (File f : files) {
            if (f == null || !f.isFile()) continue;
            if (referenced.contains(f.getAbsolutePath())) continue;

            long lm = f.lastModified();
            if (lm > 0 && (now - lm) > STALE_FILE_AGE_MS) {
                if (f.delete()) deleted++;
                else Log.w(TAG, "Failed to delete orphan media: " + f.getName());
            }
        }

        if (deleted > 0) {
            Log.i(TAG, "Orphan media cleanup: deleted=" + deleted);
        }
    }
}