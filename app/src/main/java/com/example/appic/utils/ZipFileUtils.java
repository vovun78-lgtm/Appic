package com.example.appic.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ZipFileUtils {

    private static final String TAG = "ZipFileUtils";

    private ZipFileUtils() {}

    public static void copyFile(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
    }

    public static void copyFolder(File src, File dst) throws IOException {
        if (!dst.exists()) dst.mkdirs();
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) copyFolder(f, new File(dst, f.getName()));
            else copyFile(f, new File(dst, f.getName()));
        }
    }

    public static void deleteFolder(File folder) {
        if (folder == null) return;
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) for (File f : files) deleteFolder(f);
        }
        folder.delete();
    }

    public static void zipFolder(File folder, String parentPath, ZipOutputStream zos) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            String entryName = parentPath + file.getName();
            if (file.isDirectory()) {
                zos.putNextEntry(new ZipEntry(entryName + "/"));
                zos.closeEntry();
                zipFolder(file, entryName + "/", zos);
            } else {
                zos.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = fis.read(buf)) > 0) zos.write(buf, 0, len);
                }
                zos.closeEntry();
            }
        }
    }

    public static File findFileByName(File dir, String fileName) {
        if (dir == null || !dir.exists()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                File found = findFileByName(f, fileName);
                if (found != null) return found;
            } else if (f.getName().equals(fileName)) return f;
        }
        return null;
    }

    public static String generateUniqueFileName(File dir, String originalName) {
        String name = originalName;
        String base = originalName;
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) {
            base = originalName.substring(0, dot);
            ext = originalName.substring(dot);
        }
        int counter = 1;
        while (new File(dir, name).exists()) {
            name = base + "_" + counter + ext;
            counter++;
        }
        return name;
    }

    public static String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public static String getRelativePathFromMediaDir(Context context, File mediaFile) {
        File mediaDir = context.getExternalFilesDir("media");
        if (mediaDir == null) mediaDir = new File(context.getFilesDir(), "media");
        String absolutePath = mediaFile.getAbsolutePath();
        String mediaDirPath = mediaDir.getAbsolutePath();
        String prefix = mediaDirPath.endsWith(File.separator)
                ? mediaDirPath
                : mediaDirPath + File.separator;
        if (absolutePath.startsWith(prefix)) return absolutePath.substring(prefix.length());
        return mediaFile.getName();
    }
}