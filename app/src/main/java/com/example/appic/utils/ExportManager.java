package com.example.appic.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.appic.database.DatabaseHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipOutputStream;

public final class ExportManager {

    private static final String TAG = "ExportManager";

    private static final Map<String, File> TEMP_SHARE_FILES = new ConcurrentHashMap<>();

    private ExportManager() {}

    private static OutputStream requireOutputStream(Context context, Uri destUri) throws IOException {
        OutputStream out = context.getContentResolver().openOutputStream(destUri);
        if (out == null) {
            Log.e(TAG, "openOutputStream returned null for uri=" + destUri);
            throw new IOException("Не удалось открыть поток записи для выбранного файла");
        }
        return out;
    }

    public static boolean performExport(Context context, Uri destUri) {
        File exportDir = null;
        try {
            exportDir = new File(context.getCacheDir(), "export_temp");
            if (!exportDir.exists()) exportDir.mkdirs();

            File dbCopy = new File(exportDir, "database.db");
            DatabaseSnapshotHelper.createCleanDatabaseCopy(context, dbCopy);

            List<String> usedMediaPaths = new ArrayList<>();
            try {
                SupportSQLiteDatabase db = DatabaseHelper.getSupportDatabase(context);
                try (Cursor c = db.query("SELECT path FROM media")) {
                    while (c.moveToNext()) {
                        String p = c.getString(0);
                        if (p != null && !p.isEmpty()) usedMediaPaths.add(p);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "performExport: failed to collect media paths", e);
            }

            File mediaCopyDir = new File(exportDir, "media");
            if (!mediaCopyDir.exists()) mediaCopyDir.mkdirs();
            for (String srcPath : usedMediaPaths) {
                File srcFile = new File(srcPath);
                if (srcFile.exists()) {
                    String relativePath = ZipFileUtils.getRelativePathFromMediaDir(context, srcFile);
                    File destFile = new File(mediaCopyDir, relativePath);
                    destFile.getParentFile().mkdirs();
                    ZipFileUtils.copyFile(srcFile, destFile);
                }
            }

            try (OutputStream out = requireOutputStream(context, destUri);
                 ZipOutputStream zos = new ZipOutputStream(out)) {
                ZipFileUtils.zipFolder(exportDir, "", zos);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Export error", e);
            return false;
        } finally {
            if (exportDir != null) ZipFileUtils.deleteFolder(exportDir);
        }
    }

    public static boolean performExportFiltered(Context context, Uri destUri, Long startDate, Long endDate) {
        File exportDir = null;
        try {
            exportDir = new File(context.getCacheDir(), "export_temp_filtered");
            if (!exportDir.exists()) exportDir.mkdirs();

            File tempDbFile = new File(exportDir, "database.db");
            boolean ok = DatabaseSnapshotHelper.createFilteredDatabase(context, tempDbFile, startDate, endDate);
            if (!ok || !tempDbFile.exists() || tempDbFile.length() == 0) {
                Log.e(TAG, "Filtered database not created");
                return false;
            }

            List<String> usedMediaPaths = DatabaseSnapshotHelper.getUsedMediaPaths(context, startDate, endDate);
            File mediaCopyDir = new File(exportDir, "media");
            if (!mediaCopyDir.exists()) mediaCopyDir.mkdirs();
            for (String srcPath : usedMediaPaths) {
                File srcFile = new File(srcPath);
                if (srcFile.exists()) {
                    String relativePath = ZipFileUtils.getRelativePathFromMediaDir(context, srcFile);
                    File destFile = new File(mediaCopyDir, relativePath);
                    destFile.getParentFile().mkdirs();
                    ZipFileUtils.copyFile(srcFile, destFile);
                }
            }

            try (OutputStream out = requireOutputStream(context, destUri);
                 ZipOutputStream zos = new ZipOutputStream(out)) {
                ZipFileUtils.zipFolder(exportDir, "", zos);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Filtered export error", e);
            return false;
        } finally {
            if (exportDir != null) ZipFileUtils.deleteFolder(exportDir);
        }
    }

    public static boolean performExportMachineToUri(Context context, Uri destUri, int machineId) {
        File exportDir = null;
        try {
            exportDir = new File(context.getCacheDir(), "export_temp_machine_" + System.currentTimeMillis());
            if (!exportDir.exists()) exportDir.mkdirs();

            File tempDbFile = new File(exportDir, "database.db");
            boolean ok = DatabaseSnapshotHelper.createFilteredDatabaseByMachine(context, tempDbFile, machineId);
            if (!ok) {
                Log.e(TAG, "performExportMachineToUri: machine not found, id=" + machineId);
                return false;
            }

            List<String> mediaPaths = DatabaseSnapshotHelper.getMediaPathsForMachine(context, machineId);
            File mediaCopyDir = new File(exportDir, "media");
            if (!mediaCopyDir.exists()) mediaCopyDir.mkdirs();
            for (String srcPath : mediaPaths) {
                File srcFile = new File(srcPath);
                if (srcFile.exists()) {
                    String relativePath = ZipFileUtils.getRelativePathFromMediaDir(context, srcFile);
                    File destFile = new File(mediaCopyDir, relativePath);
                    destFile.getParentFile().mkdirs();
                    ZipFileUtils.copyFile(srcFile, destFile);
                }
            }

            try (OutputStream out = requireOutputStream(context, destUri);
                 ZipOutputStream zos = new ZipOutputStream(out)) {
                ZipFileUtils.zipFolder(exportDir, "", zos);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Export machine error", e);
            return false;
        } finally {
            if (exportDir != null) ZipFileUtils.deleteFolder(exportDir);
        }
    }

    public static boolean performExportMachineGroupToUri(Context context, Uri destUri, int machineGroupId) {
        File exportDir = null;
        try {
            exportDir = new File(context.getCacheDir(), "export_temp_machine_group_" + System.currentTimeMillis());
            if (!exportDir.exists()) exportDir.mkdirs();

            File tempDbFile = new File(exportDir, "database.db");
            boolean ok = DatabaseSnapshotHelper.createFilteredDatabaseByMachineGroup(context, tempDbFile, machineGroupId);
            if (!ok) {
                Log.e(TAG, "performExportMachineGroupToUri: group not found, id=" + machineGroupId);
                return false;
            }

            List<String> mediaPaths = DatabaseSnapshotHelper.getMediaPathsForMachineGroup(context, machineGroupId);
            File mediaCopyDir = new File(exportDir, "media");
            if (!mediaCopyDir.exists()) mediaCopyDir.mkdirs();
            for (String srcPath : mediaPaths) {
                File srcFile = new File(srcPath);
                if (srcFile.exists()) {
                    String relativePath = ZipFileUtils.getRelativePathFromMediaDir(context, srcFile);
                    File destFile = new File(mediaCopyDir, relativePath);
                    destFile.getParentFile().mkdirs();
                    ZipFileUtils.copyFile(srcFile, destFile);
                }
            }

            try (OutputStream out = requireOutputStream(context, destUri);
                 ZipOutputStream zos = new ZipOutputStream(out)) {
                ZipFileUtils.zipFolder(exportDir, "", zos);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Export machine group error", e);
            return false;
        } finally {
            if (exportDir != null) ZipFileUtils.deleteFolder(exportDir);
        }
    }

    public static boolean performExportGroupToUri(Context context, Uri destUri, int groupId) {
        File exportDir = null;
        try {
            exportDir = new File(context.getCacheDir(), "export_temp_group_" + System.currentTimeMillis());
            if (!exportDir.exists()) exportDir.mkdirs();

            File tempDbFile = new File(exportDir, "database.db");
            boolean ok = DatabaseSnapshotHelper.createFilteredDatabaseByGroup(context, tempDbFile, groupId);
            if (!ok) {
                Log.e(TAG, "performExportGroupToUri: group not found, id=" + groupId);
                return false;
            }

            List<String> mediaPaths = DatabaseSnapshotHelper.getMediaPathsForGroup(context, groupId);
            File mediaCopyDir = new File(exportDir, "media");
            if (!mediaCopyDir.exists()) mediaCopyDir.mkdirs();
            for (String srcPath : mediaPaths) {
                File srcFile = new File(srcPath);
                if (srcFile.exists()) {
                    String relativePath = ZipFileUtils.getRelativePathFromMediaDir(context, srcFile);
                    File destFile = new File(mediaCopyDir, relativePath);
                    destFile.getParentFile().mkdirs();
                    ZipFileUtils.copyFile(srcFile, destFile);
                }
            }

            try (OutputStream out = requireOutputStream(context, destUri);
                 ZipOutputStream zos = new ZipOutputStream(out)) {
                ZipFileUtils.zipFolder(exportDir, "", zos);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Export group error", e);
            return false;
        } finally {
            if (exportDir != null) ZipFileUtils.deleteFolder(exportDir);
        }
    }

    public static boolean performExportFieldToUri(Context context, Uri destUri, int fieldId) {
        File exportDir = null;
        try {
            exportDir = new File(context.getCacheDir(), "export_temp_field_" + System.currentTimeMillis());
            if (!exportDir.exists()) exportDir.mkdirs();

            File tempDbFile = new File(exportDir, "database.db");
            boolean ok = DatabaseSnapshotHelper.createFilteredDatabaseByField(context, tempDbFile, fieldId);
            if (!ok) {
                Log.e(TAG, "performExportFieldToUri: field not found, id=" + fieldId);
                return false;
            }

            List<String> mediaPaths = DatabaseSnapshotHelper.getMediaPathsForField(context, fieldId);
            File mediaCopyDir = new File(exportDir, "media");
            if (!mediaCopyDir.exists()) mediaCopyDir.mkdirs();
            for (String srcPath : mediaPaths) {
                File srcFile = new File(srcPath);
                if (srcFile.exists()) {
                    String relativePath = ZipFileUtils.getRelativePathFromMediaDir(context, srcFile);
                    File destFile = new File(mediaCopyDir, relativePath);
                    destFile.getParentFile().mkdirs();
                    ZipFileUtils.copyFile(srcFile, destFile);
                }
            }

            try (OutputStream out = requireOutputStream(context, destUri);
                 ZipOutputStream zos = new ZipOutputStream(out)) {
                ZipFileUtils.zipFolder(exportDir, "", zos);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Export field error", e);
            return false;
        } finally {
            if (exportDir != null) ZipFileUtils.deleteFolder(exportDir);
        }
    }

    public static Uri exportToTempFileWithName(Context context, Long startDate, Long endDate, String fileName) throws IOException {
        String safeFileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safeFileName.trim().isEmpty() || safeFileName.equals("..") || safeFileName.equals(".")) {
            safeFileName = "App_Backup_" + System.currentTimeMillis() + ".zip";
        }
        File tempZip = new File(context.getCacheDir(), safeFileName);
        if (tempZip.exists()) tempZip.delete();

        File exportDir = new File(context.getCacheDir(), "export_temp_share_" + System.currentTimeMillis());
        if (!exportDir.exists()) exportDir.mkdirs();
        try {
            File tempDbFile = new File(exportDir, "database.db");
            boolean ok = DatabaseSnapshotHelper.createFilteredDatabase(context, tempDbFile, startDate, endDate);
            if (!ok) throw new IOException("Cannot create filtered database");

            List<String> usedMediaPaths = DatabaseSnapshotHelper.getUsedMediaPaths(context, startDate, endDate);
            File mediaCopyDir = new File(exportDir, "media");
            if (!mediaCopyDir.exists()) mediaCopyDir.mkdirs();
            for (String srcPath : usedMediaPaths) {
                File srcFile = new File(srcPath);
                if (srcFile.exists()) {
                    String relativePath = ZipFileUtils.getRelativePathFromMediaDir(context, srcFile);
                    File destFile = new File(mediaCopyDir, relativePath);
                    destFile.getParentFile().mkdirs();
                    ZipFileUtils.copyFile(srcFile, destFile);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(tempZip);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                ZipFileUtils.zipFolder(exportDir, "", zos);
            }
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", tempZip);
            TEMP_SHARE_FILES.put(uri.toString(), tempZip);
            return uri;
        } finally {
            ZipFileUtils.deleteFolder(exportDir);
        }
    }

    public static Uri exportMachineToTempFile(Context context, int machineId, String machineName) throws IOException {
        return exportEntityToTempFile(context, "Machine_", machineName, "export_temp_machine_",
                tempDb -> DatabaseSnapshotHelper.createFilteredDatabaseByMachine(context, tempDb, machineId),
                () -> DatabaseSnapshotHelper.getMediaPathsForMachine(context, machineId),
                context);
    }

    public static Uri exportMachineGroupToTempFile(Context context, int machineGroupId, String groupName) throws IOException {
        return exportEntityToTempFile(context, "MachineGroup_", groupName, "export_temp_machine_group_",
                tempDb -> DatabaseSnapshotHelper.createFilteredDatabaseByMachineGroup(context, tempDb, machineGroupId),
                () -> DatabaseSnapshotHelper.getMediaPathsForMachineGroup(context, machineGroupId),
                context);
    }

    public static Uri exportGroupToTempFile(Context context, int groupId, String groupName) throws IOException {
        return exportEntityToTempFile(context, "Group_", groupName, "export_temp_group_",
                tempDb -> DatabaseSnapshotHelper.createFilteredDatabaseByGroup(context, tempDb, groupId),
                () -> DatabaseSnapshotHelper.getMediaPathsForGroup(context, groupId),
                context);
    }

    public static Uri exportFieldToTempFile(Context context, int fieldId, String fieldTitle) throws IOException {
        return exportEntityToTempFile(context, "Field_", fieldTitle, "export_temp_field_",
                tempDb -> DatabaseSnapshotHelper.createFilteredDatabaseByField(context, tempDb, fieldId),
                () -> DatabaseSnapshotHelper.getMediaPathsForField(context, fieldId),
                context);
    }

    private interface DbBuilder { boolean build(File tempDb) throws IOException; }
    private interface MediaPathsSupplier { List<String> get(); }

    private static Uri exportEntityToTempFile(Context context, String prefix, String name,
                                              String dirPrefix, DbBuilder builder,
                                              MediaPathsSupplier mediaSupplier, Context ctx) throws IOException {
        String safeFileName = prefix + ZipFileUtils.sanitizeFileName(name) + "_"
                + System.currentTimeMillis() + ".zip";
        File tempZip = new File(context.getCacheDir(), safeFileName);
        if (tempZip.exists()) tempZip.delete();

        File exportDir = new File(context.getCacheDir(), dirPrefix + System.currentTimeMillis());
        if (!exportDir.exists()) exportDir.mkdirs();

        try {
            File tempDbFile = new File(exportDir, "database.db");
            boolean ok = builder.build(tempDbFile);
            if (!ok) throw new IOException("Failed to create filtered database for " + prefix);

            List<String> mediaPaths = mediaSupplier.get();
            File mediaCopyDir = new File(exportDir, "media");
            if (!mediaCopyDir.exists()) mediaCopyDir.mkdirs();
            for (String srcPath : mediaPaths) {
                File srcFile = new File(srcPath);
                if (srcFile.exists()) {
                    String relativePath = ZipFileUtils.getRelativePathFromMediaDir(context, srcFile);
                    File destFile = new File(mediaCopyDir, relativePath);
                    destFile.getParentFile().mkdirs();
                    ZipFileUtils.copyFile(srcFile, destFile);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(tempZip);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                ZipFileUtils.zipFolder(exportDir, "", zos);
            }
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", tempZip);
            TEMP_SHARE_FILES.put(uri.toString(), tempZip);
            return uri;
        } finally {
            ZipFileUtils.deleteFolder(exportDir);
        }
    }

    public static void cleanupTempShareFile(Uri uri) {
        if (uri == null) return;
        File f = TEMP_SHARE_FILES.remove(uri.toString());
        if (f != null && f.exists()) {
            boolean deleted = f.delete();
            if (!deleted) Log.w(TAG, "Failed to delete temp share file: " + f.getAbsolutePath());
        }
    }
}