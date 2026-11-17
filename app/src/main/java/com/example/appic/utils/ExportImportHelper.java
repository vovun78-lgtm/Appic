package com.example.appic.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

/**
 * Публичный фасад экспорта/импорта.
 * Лаунчеры системных окон + делегирование в {@link ExportManager} / {@link ImportManager}.
 */
public final class ExportImportHelper {

    public static final int IMPORT_MODE_MERGE = 0;
    public static final int IMPORT_MODE_REPLACE = 1;

    public interface ImportProgressListener extends ImportManager.ImportProgressListener {}

    private ExportImportHelper() {}

    // ======================== LAUNCHERS ========================
    public static void exportData(AppCompatActivity activity, ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "App_Backup.zip");
        launcher.launch(intent);
    }

    public static void exportMachineToUri(AppCompatActivity activity, ActivityResultLauncher<Intent> launcher,
                                          int machineId, String machineName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        String safeName = "Machine_" + ZipFileUtils.sanitizeFileName(machineName) + "_"
                + System.currentTimeMillis() + ".zip";
        intent.putExtra(Intent.EXTRA_TITLE, safeName);
        launcher.launch(intent);
    }

    public static void exportMachineGroupToUri(AppCompatActivity activity,
                                               ActivityResultLauncher<Intent> launcher,
                                               int machineGroupId, String groupName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        String safeName = "MachineGroup_" + ZipFileUtils.sanitizeFileName(groupName) + "_"
                + System.currentTimeMillis() + ".zip";
        intent.putExtra(Intent.EXTRA_TITLE, safeName);
        launcher.launch(intent);
    }

    public static void exportGroupToUri(AppCompatActivity activity, ActivityResultLauncher<Intent> launcher,
                                        int groupId, String groupName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        String safeName = "Group_" + ZipFileUtils.sanitizeFileName(groupName) + "_"
                + System.currentTimeMillis() + ".zip";
        intent.putExtra(Intent.EXTRA_TITLE, safeName);
        launcher.launch(intent);
    }

    public static void exportFieldToUri(AppCompatActivity activity, ActivityResultLauncher<Intent> launcher,
                                        int fieldId, String fieldTitle) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        String safeName = "Field_" + ZipFileUtils.sanitizeFileName(fieldTitle) + "_"
                + System.currentTimeMillis() + ".zip";
        intent.putExtra(Intent.EXTRA_TITLE, safeName);
        launcher.launch(intent);
    }

    public static void importData(AppCompatActivity activity, ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        launcher.launch(intent);
    }

    // ======================== EXPORT ========================
    public static boolean performExport(Context c, Uri uri) { return ExportManager.performExport(c, uri); }
    public static boolean performExportFiltered(Context c, Uri uri, Long s, Long e) { return ExportManager.performExportFiltered(c, uri, s, e); }
    public static boolean performExportMachineToUri(Context c, Uri uri, int id) { return ExportManager.performExportMachineToUri(c, uri, id); }
    public static boolean performExportMachineGroupToUri(Context c, Uri uri, int id) { return ExportManager.performExportMachineGroupToUri(c, uri, id); }
    public static boolean performExportGroupToUri(Context c, Uri uri, int id) { return ExportManager.performExportGroupToUri(c, uri, id); }
    public static boolean performExportFieldToUri(Context c, Uri uri, int id) { return ExportManager.performExportFieldToUri(c, uri, id); }

    public static Uri exportToTempFileWithName(Context c, Long s, Long e, String n) throws IOException {
        return ExportManager.exportToTempFileWithName(c, s, e, n);
    }
    public static Uri exportMachineToTempFile(Context c, int id, String n) throws IOException { return ExportManager.exportMachineToTempFile(c, id, n); }
    public static Uri exportMachineGroupToTempFile(Context c, int id, String n) throws IOException { return ExportManager.exportMachineGroupToTempFile(c, id, n); }
    public static Uri exportGroupToTempFile(Context c, int id, String n) throws IOException { return ExportManager.exportGroupToTempFile(c, id, n); }
    public static Uri exportFieldToTempFile(Context c, int id, String n) throws IOException { return ExportManager.exportFieldToTempFile(c, id, n); }

    public static void cleanupTempShareFile(Uri uri) { ExportManager.cleanupTempShareFile(uri); }

    // ======================== IMPORT ========================
    public static boolean performImport(Context c, Uri uri, ImportProgressListener l, int mode) {
        return ImportManager.performImport(c, uri, l, mode);
    }
}