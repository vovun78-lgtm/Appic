package com.example.appic.utils;

import android.util.Log;

import java.io.File;

public class FileUtils {
    private static final String TAG = "FileUtils";

    public static boolean deleteFile(String path) {
        if (path == null) return false;
        File file = new File(path);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                Log.e(TAG, "Failed to delete file: " + path);
            }
            return deleted;
        }
        return false;
    }
}