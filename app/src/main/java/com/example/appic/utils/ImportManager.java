package com.example.appic.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.appic.database.DatabaseHelper;
import com.example.appic.database.models.Machine;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ImportManager {

    private static final String TAG = "ImportManager";

    // ИСПРАВЛЕНО (3.1.4): защита от Zip-bomb. Ограничиваем общий распакованный
    // объём (2 ГБ) и количество entry (10 000). Без этого архив с высокой
    // степенью сжатия мог занять всю память/диск устройства.
    private static final long MAX_UNCOMPRESSED_SIZE = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 10_000;

    public interface ImportProgressListener {
        void onProgressUpdate(int percent);
        void onFinished(boolean success);
    }

    private static void safeProgress(ImportProgressListener listener, int percent) {
        if (listener == null) return;
        try { listener.onProgressUpdate(percent); } catch (Exception ignored) {}
    }

    private static long safeInsert(SupportSQLiteDatabase db, String table, ContentValues values) {
        long id = db.insert(table, SQLiteDatabase.CONFLICT_NONE, values);
        if (id == -1) Log.e(TAG, "safeInsert failed for table=" + table);
        return id;
    }

    private static long requireInsert(SupportSQLiteDatabase db, String table, ContentValues values) throws IOException {
        long id = db.insert(table, SQLiteDatabase.CONFLICT_NONE, values);
        if (id == -1) {
            Log.e(TAG, "requireInsert failed for table=" + table);
            throw new IOException("Insert failed: " + table);
        }
        return id;
    }

    public static boolean performImport(Context context, Uri sourceUri,
                                        ImportProgressListener listener, int mode) {
        final AtomicBoolean finished = new AtomicBoolean(false);
        final java.util.function.Consumer<Boolean> finishCallback = (success) -> {
            if (finished.compareAndSet(false, true) && listener != null) {
                try { listener.onFinished(success); } catch (Exception e) {}
            }
        };

        File importDir = null;
        try {
            safeProgress(listener, 0);
            importDir = new File(context.getCacheDir(), "import_temp");
            if (importDir.exists()) ZipFileUtils.deleteFolder(importDir);
            if (!importDir.exists()) importDir.mkdirs();

            InputStream rawIn = context.getContentResolver().openInputStream(sourceUri);
            if (rawIn == null) {
                Log.e(TAG, "performImport: openInputStream returned null for " + sourceUri);
                finishCallback.accept(false);
                return false;
            }

            try (InputStream in = rawIn; ZipInputStream zis = new ZipInputStream(in)) {
                ZipEntry entry;
                int extracted = 0;
                long totalUncompressed = 0L;
                byte[] buffer = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    extracted++;
                    // ИСПРАВЛЕНО (3.1.4): лимит на количество entry.
                    if (extracted > MAX_ENTRIES) {
                        Log.e(TAG, "Import rejected: entry count exceeds " + MAX_ENTRIES);
                        finishCallback.accept(false);
                        return false;
                    }

                    File targetFile = new File(importDir, entry.getName());
                    String canonicalPath = targetFile.getCanonicalPath();
                    String importDirPath = importDir.getCanonicalPath();
                    if (!canonicalPath.startsWith(importDirPath + File.separator)
                            && !canonicalPath.equals(importDirPath)) {
                        throw new SecurityException("Zip entry outside target directory");
                    }
                    if (entry.isDirectory()) {
                        targetFile.mkdirs();
                    } else {
                        targetFile.getParentFile().mkdirs();
                        long writtenThisEntry = 0L;
                        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                                writtenThisEntry += len;
                                // ИСПРАВЛЕНО (3.1.4): считаем по фактически записанным
                                // байтам, а не по entry.getSize() — для stream-архивов
                                // getSize() может быть -1 (неизвестно).
                                totalUncompressed += len;
                                if (totalUncompressed > MAX_UNCOMPRESSED_SIZE) {
                                    Log.e(TAG, "Import rejected: uncompressed size exceeds "
                                            + MAX_UNCOMPRESSED_SIZE);
                                    throw new IOException("Uncompressed size limit exceeded");
                                }
                            }
                        }
                    }
                    zis.closeEntry();
                    safeProgress(listener, 5 + Math.min(15, extracted));
                }
            }

            File dbImportFile = new File(importDir, "database.db");
            if (!dbImportFile.exists()) { finishCallback.accept(false); return false; }
            File mediaImportDir = new File(importDir, "media");
            safeProgress(listener, 20);
            boolean success = importDataSmartMerge(context, dbImportFile, mediaImportDir, listener, mode);
            finishCallback.accept(success);
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Import error", e);
            finishCallback.accept(false);
            return false;
        } finally {
            if (importDir != null) ZipFileUtils.deleteFolder(importDir);
        }
    }

    /**
     * ИСПРАВЛЕНО (№6): сначала пробуем найти файл по его ОТНОСИТЕЛЬНОМУ пути внутри media/,
     * чтобы не перепутать файлы с одинаковыми именами. Только если такой путь не найден —
     * фолбэк на старый поиск по basename.
     */
    private static File findMediaSourceFile(File mediaImportDir, String oldPath) {
        if (oldPath == null) return null;
        String normalized = oldPath.replace('\\', '/');
        int idx = normalized.lastIndexOf("/media/");
        if (idx >= 0) {
            String relativePath = normalized.substring(idx + "/media/".length());
            File candidate = new File(mediaImportDir, relativePath);
            if (candidate.exists() && candidate.isFile()) return candidate;
        }
        return ZipFileUtils.findFileByName(mediaImportDir, new File(oldPath).getName());
    }

    private static boolean importDataSmartMerge(Context context, File dbImportFile, File mediaImportDir,
                                                 ImportProgressListener listener, int mode) throws IOException {
        SQLiteDatabase importDb = SQLiteDatabase.openDatabase(
                dbImportFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

        if (!DatabaseSnapshotHelper.tableExistsImport(importDb, DatabaseHelper.TABLE_MACHINES)
                || !DatabaseSnapshotHelper.tableExistsImport(importDb, DatabaseHelper.TABLE_FIELDS)
                || !DatabaseSnapshotHelper.tableExistsImport(importDb, DatabaseHelper.TABLE_MEDIA)) {
            importDb.close();
            return false;
        }

        boolean hasCustomValues = DatabaseSnapshotHelper.doesColumnExistImport(importDb,
                DatabaseHelper.TABLE_FIELDS, DatabaseHelper.COLUMN_CUSTOM_VALUES);
        boolean hasTemplates = DatabaseSnapshotHelper.tableExistsImport(importDb, DatabaseHelper.TABLE_FIELD_TEMPLATES);
        boolean hasMachineGroups = DatabaseSnapshotHelper.tableExistsImport(importDb, DatabaseHelper.TABLE_MACHINE_GROUPS);
        boolean hasGroups = DatabaseSnapshotHelper.tableExistsImport(importDb, DatabaseHelper.TABLE_GROUPS);
        boolean hasCreatedAt = DatabaseSnapshotHelper.doesColumnExistImport(importDb,
                DatabaseHelper.TABLE_FIELDS, DatabaseHelper.COLUMN_CREATED_AT);
        boolean hasUpdatedAt = DatabaseSnapshotHelper.doesColumnExistImport(importDb,
                DatabaseHelper.TABLE_FIELDS, DatabaseHelper.COLUMN_UPDATED_AT);
        boolean hasIsRead = DatabaseSnapshotHelper.doesColumnExistImport(importDb,
                DatabaseHelper.TABLE_FIELDS, DatabaseHelper.COLUMN_IS_READ);
        boolean hasFieldSortOrder = DatabaseSnapshotHelper.doesColumnExistImport(importDb,
                DatabaseHelper.TABLE_FIELDS, DatabaseHelper.COLUMN_SORT_ORDER);
        boolean hasFieldGroupIdCol = DatabaseSnapshotHelper.doesColumnExistImport(importDb,
                DatabaseHelper.TABLE_FIELDS, DatabaseHelper.COLUMN_GROUP_ID);
        boolean hasMachineGroupIdCol = DatabaseSnapshotHelper.doesColumnExistImport(importDb,
                DatabaseHelper.TABLE_MACHINES, DatabaseHelper.COLUMN_GROUP_ID);

        // -------- Отчёты (F) --------
        boolean hasReportDefinitions = DatabaseSnapshotHelper.tableExistsImport(importDb,
                DatabaseHelper.TABLE_REPORT_DEFINITIONS);
        int importedReportCount = 0;
        if (hasReportDefinitions) {
            try (Cursor rc = importDb.query(DatabaseHelper.TABLE_REPORT_DEFINITIONS,
                    new String[]{DatabaseHelper.COLUMN_ID}, null, null, null, null, null)) {
                importedReportCount = rc.getCount();
            }
        }

        SupportSQLiteDatabase db = DatabaseHelper.getSupportDatabase(context);

        Map<Integer, Integer> machineGroupIdMap = new HashMap<>();
        Map<Integer, Integer> machineIdMap = new HashMap<>();
        Map<Integer, Integer> groupIdMap = new HashMap<>();
        Map<Integer, Integer> fieldIdMap = new HashMap<>();
        Map<Integer, Integer> templateIdMap = new HashMap<>();
        List<String> filesToDeleteAfterCommit = new ArrayList<>();
        List<File> copiedMediaFiles = new ArrayList<>();

        boolean committed = false;
        db.beginTransaction();
        try {
            if (mode == ExportImportHelper.IMPORT_MODE_REPLACE) {
                try (Cursor c = db.query("SELECT path FROM media")) {
                    while (c.moveToNext()) {
                        String p = c.getString(0);
                        if (p != null) filesToDeleteAfterCommit.add(p);
                    }
                }
                db.execSQL("DELETE FROM " + DatabaseHelper.TABLE_MEDIA);
                db.execSQL("DELETE FROM " + DatabaseHelper.TABLE_FIELDS);
                db.execSQL("DELETE FROM " + DatabaseHelper.TABLE_GROUPS);
                db.execSQL("DELETE FROM " + DatabaseHelper.TABLE_MACHINES);
                db.execSQL("DELETE FROM " + DatabaseHelper.TABLE_MACHINE_GROUPS);
                db.execSQL("DELETE FROM " + DatabaseHelper.TABLE_FIELD_TEMPLATES);

                // ИСПРАВЛЕНО (F): отчёты удаляем ТОЛЬКО если в архиве есть непустая
                // таблица report_definitions. Если архив filtered (по дате / по машине),
                // report_definitions пуста — значит отчётов в архиве нет, и удалять
                // пользовательские отчёты в текущей БД нельзя.
                if (hasReportDefinitions && importedReportCount > 0) {
                    db.execSQL("DELETE FROM " + DatabaseHelper.TABLE_REPORT_DEFINITIONS);
                }
            }

            if (hasTemplates) {
                boolean hasAutoFill = DatabaseSnapshotHelper.doesColumnExistImport(importDb,
                        DatabaseHelper.TABLE_FIELD_TEMPLATES, "is_auto_fill");
                try (Cursor cursor = importDb.query(DatabaseHelper.TABLE_FIELD_TEMPLATES,
                        null, null, null, null, null, null)) {
                    while (cursor.moveToNext()) {
                        int oldId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID));
                        String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                        String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                        String options = cursor.getString(cursor.getColumnIndexOrThrow("options"));
                        boolean isRequired = cursor.getInt(cursor.getColumnIndexOrThrow("is_required")) == 1;
                        int sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order"));
                        boolean isAutoFill = hasAutoFill && cursor.getInt(cursor.getColumnIndexOrThrow("is_auto_fill")) == 1;

                        Integer existingId = null;
                        try (Cursor tc = db.query("SELECT " + DatabaseHelper.COLUMN_ID
                                + " FROM " + DatabaseHelper.TABLE_FIELD_TEMPLATES
                                + " WHERE name=? AND type=? LIMIT 1", new Object[]{name, type})) {
                            if (tc.moveToFirst()) existingId = tc.getInt(0);
                        }

                        int newId;
                        if (existingId != null) newId = existingId;
                        else {
                            ContentValues values = new ContentValues();
                            values.put("name", name);
                            values.put("type", type);
                            values.put("options", options);
                            values.put("is_required", isRequired ? 1 : 0);
                            values.put("is_auto_fill", isAutoFill ? 1 : 0);
                            values.put("sort_order", sortOrder);
                            newId = (int) requireInsert(db, DatabaseHelper.TABLE_FIELD_TEMPLATES, values);
                        }
                        templateIdMap.put(oldId, newId);
                    }
                }
            }

            int totalSteps = 0;
            if (hasMachineGroups) {
                try (Cursor c = importDb.query(DatabaseHelper.TABLE_MACHINE_GROUPS, null, null, null, null, null, null)) { totalSteps += c.getCount(); }
            }
            try (Cursor c = importDb.query(DatabaseHelper.TABLE_MACHINES, null, null, null, null, null, null)) { totalSteps += c.getCount(); }
            if (hasGroups) {
                try (Cursor c = importDb.query(DatabaseHelper.TABLE_GROUPS, null, null, null, null, null, null)) { totalSteps += c.getCount(); }
            }
            try (Cursor c = importDb.query(DatabaseHelper.TABLE_FIELDS, null, null, null, null, null, null)) { totalSteps += c.getCount(); }
            try (Cursor c = importDb.query(DatabaseHelper.TABLE_MEDIA, null, null, null, null, null, null)) { totalSteps += c.getCount(); }
            if (totalSteps == 0) totalSteps = 1;
            int stepProgress = 0;

            if (hasMachineGroups && DatabaseSnapshotHelper.doesColumnExistImport(importDb,
                    DatabaseHelper.TABLE_MACHINE_GROUPS, DatabaseHelper.COLUMN_NAME)) {
                boolean hasMGSortOrder = DatabaseSnapshotHelper.doesColumnExistImport(importDb,
                        DatabaseHelper.TABLE_MACHINE_GROUPS, DatabaseHelper.COLUMN_SORT_ORDER);
                String mgOrderBy = hasMGSortOrder ? DatabaseHelper.COLUMN_SORT_ORDER + " ASC" : null;

                try (Cursor cursor = importDb.query(DatabaseHelper.TABLE_MACHINE_GROUPS,
                        null, null, null, null, null, mgOrderBy)) {
                    while (cursor.moveToNext()) {
                        int oldId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID));
                        String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NAME));

                        Integer existingId = null;
                        if (mode == ExportImportHelper.IMPORT_MODE_MERGE) {
                            try (Cursor ec = db.query("SELECT id FROM "
                                    + DatabaseHelper.TABLE_MACHINE_GROUPS + " WHERE name=? LIMIT 1",
                                    new Object[]{name})) {
                                if (ec.moveToFirst()) existingId = ec.getInt(0);
                            }
                        }

                        int newId;
                        if (existingId != null) newId = existingId;
                        else {
                            Integer maxOrder = null;
                            try (Cursor mc = db.query("SELECT MAX(sort_order) FROM "
                                    + DatabaseHelper.TABLE_MACHINE_GROUPS)) {
                                if (mc.moveToFirst() && !mc.isNull(0)) maxOrder = mc.getInt(0);
                            }
                            int newOrder = (maxOrder == null) ? 0 : maxOrder + 1;

                            ContentValues values = new ContentValues();
                            values.put(DatabaseHelper.COLUMN_NAME, name);
                            values.put(DatabaseHelper.COLUMN_SORT_ORDER, newOrder);
                            newId = (int) requireInsert(db, DatabaseHelper.TABLE_MACHINE_GROUPS, values);
                        }
                        machineGroupIdMap.put(oldId, newId);
                        stepProgress++;
                        safeProgress(listener, Math.min(20 + (stepProgress * 60 / totalSteps), 80));
                    }
                }
            }

            try (Cursor cursor = importDb.query(DatabaseHelper.TABLE_MACHINES, null, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    int oldId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NAME));
                    int oldGroupId = hasMachineGroupIdCol
                            ? cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_GROUP_ID)) : 0;

                    int newGroupId;
                    if (oldGroupId != 0) {
                        Integer mapped = machineGroupIdMap.get(oldGroupId);
                        newGroupId = (mapped != null) ? mapped : 0;
                    } else newGroupId = 0;

                    Machine existing = (mode == ExportImportHelper.IMPORT_MODE_MERGE) ? findMachineByName(db, name) : null;
                    int newId;
                    if (existing != null) {
                        ContentValues values = new ContentValues();
                        values.put(DatabaseHelper.COLUMN_GROUP_ID, newGroupId);
                        db.update(DatabaseHelper.TABLE_MACHINES, SQLiteDatabase.CONFLICT_NONE, values,
                                "id=?", new Object[]{existing.getId()});
                        newId = existing.getId();
                    } else {
                        Integer maxOrder = null;
                        String whereMaxOrder = (newGroupId == 0)
                                ? "(group_id = 0 OR group_id IS NULL)"
                                : "group_id = " + newGroupId;
                        try (Cursor mc = db.query("SELECT MAX(sort_order) FROM "
                                + DatabaseHelper.TABLE_MACHINES + " WHERE " + whereMaxOrder)) {
                            if (mc.moveToFirst() && !mc.isNull(0)) maxOrder = mc.getInt(0);
                        }
                        int newOrder = (maxOrder == null) ? 0 : maxOrder + 1;

                        ContentValues values = new ContentValues();
                        values.put(DatabaseHelper.COLUMN_NAME, name);
                        values.put(DatabaseHelper.COLUMN_SORT_ORDER, newOrder);
                        values.put(DatabaseHelper.COLUMN_GROUP_ID, newGroupId);
                        newId = (int) requireInsert(db, DatabaseHelper.TABLE_MACHINES, values);
                    }
                    machineIdMap.put(oldId, newId);
                    stepProgress++;
                    safeProgress(listener, Math.min(20 + (stepProgress * 60 / totalSteps), 80));
                }
            }

            if (hasGroups
                    && DatabaseSnapshotHelper.doesColumnExistImport(importDb, DatabaseHelper.TABLE_GROUPS, DatabaseHelper.COLUMN_NAME)
                    && DatabaseSnapshotHelper.doesColumnExistImport(importDb, DatabaseHelper.TABLE_GROUPS, DatabaseHelper.COLUMN_MACHINE_ID)) {
                boolean hasGroupSortOrder = DatabaseSnapshotHelper.doesColumnExistImport(importDb,
                        DatabaseHelper.TABLE_GROUPS, DatabaseHelper.COLUMN_SORT_ORDER);
                String groupOrderBy = hasGroupSortOrder
                        ? DatabaseHelper.COLUMN_MACHINE_ID + " ASC, " + DatabaseHelper.COLUMN_SORT_ORDER + " ASC"
                        : DatabaseHelper.COLUMN_MACHINE_ID + " ASC";

                try (Cursor cursor = importDb.query(DatabaseHelper.TABLE_GROUPS,
                        null, null, null, null, null, groupOrderBy)) {
                    while (cursor.moveToNext()) {
                        int oldId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID));
                        int oldMachineId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_MACHINE_ID));
                        String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NAME));

                        Integer newMachineId = machineIdMap.get(oldMachineId);
                        if (newMachineId == null) { stepProgress++; continue; }

                        Integer existingId = null;
                        if (mode == ExportImportHelper.IMPORT_MODE_MERGE) {
                            try (Cursor ec = db.query("SELECT id FROM " + DatabaseHelper.TABLE_GROUPS
                                    + " WHERE machine_id=? AND name=? LIMIT 1",
                                    new Object[]{newMachineId, name})) {
                                if (ec.moveToFirst()) existingId = ec.getInt(0);
                            }
                        }

                        int newId;
                        if (existingId != null) newId = existingId;
                        else {
                            Integer maxOrder = null;
                            try (Cursor mc = db.query("SELECT MAX(sort_order) FROM "
                                    + DatabaseHelper.TABLE_GROUPS + " WHERE machine_id=?",
                                    new Object[]{newMachineId})) {
                                if (mc.moveToFirst() && !mc.isNull(0)) maxOrder = mc.getInt(0);
                            }
                            int newOrder = (maxOrder == null) ? 0 : maxOrder + 1;

                            ContentValues values = new ContentValues();
                            values.put(DatabaseHelper.COLUMN_MACHINE_ID, newMachineId);
                            values.put(DatabaseHelper.COLUMN_NAME, name);
                            values.put(DatabaseHelper.COLUMN_SORT_ORDER, newOrder);
                            newId = (int) requireInsert(db, DatabaseHelper.TABLE_GROUPS, values);
                        }
                        groupIdMap.put(oldId, newId);
                        stepProgress++;
                        safeProgress(listener, Math.min(20 + (stepProgress * 60 / totalSteps), 80));
                    }
                }
            }

            String fieldOrderBy;
            if (hasFieldGroupIdCol && hasFieldSortOrder) {
                fieldOrderBy = DatabaseHelper.COLUMN_GROUP_ID + " ASC, " + DatabaseHelper.COLUMN_SORT_ORDER + " ASC";
            } else if (hasFieldSortOrder) {
                fieldOrderBy = DatabaseHelper.COLUMN_SORT_ORDER + " ASC";
            } else fieldOrderBy = null;
            final boolean canMatchByCreatedAt = hasCreatedAt && hasUpdatedAt;

            try (Cursor cursor = importDb.query(DatabaseHelper.TABLE_FIELDS,
                    null, null, null, null, null, fieldOrderBy)) {
                while (cursor.moveToNext()) {
                    int oldId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID));
                    int oldMachineId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_MACHINE_ID));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TITLE));
                    String text = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TEXT));
                    String customVals = hasCustomValues
                            ? cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CUSTOM_VALUES)) : null;
                    int oldGroupId = hasFieldGroupIdCol
                            ? cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_GROUP_ID)) : 0;
                    long createdAt = hasCreatedAt
                            ? cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CREATED_AT))
                            : System.currentTimeMillis();
                    long updatedAt = hasUpdatedAt
                            ? cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_UPDATED_AT))
                            : createdAt;
                    boolean isRead = hasIsRead
                            && cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_IS_READ)) != 0;

                    Integer newMachineId = machineIdMap.get(oldMachineId);
                    if (newMachineId == null) { stepProgress++; continue; }

                    int newGroupId;
                    if (oldGroupId != 0) {
                        Integer mapped = groupIdMap.get(oldGroupId);
                        newGroupId = (mapped != null) ? mapped : 0;
                    } else newGroupId = 0;

                    if (hasTemplates && hasCustomValues
                            && customVals != null && !customVals.trim().isEmpty()
                            && !customVals.equals("{}")) {
                        customVals = remapCustomValues(customVals, templateIdMap);
                    }

                    Integer existingFieldId = null;
                    if (canMatchByCreatedAt && createdAt > 0) {
                        existingFieldId = findFieldIdByMachineAndCreatedAt(db, newMachineId, createdAt);
                    }

                    if (existingFieldId != null) {
                        long existingUpdatedAt = getFieldUpdatedAt(db, existingFieldId);
                        if (updatedAt > existingUpdatedAt) {
                            ContentValues values = new ContentValues();
                            values.put(DatabaseHelper.COLUMN_TITLE, title);
                            values.put(DatabaseHelper.COLUMN_TEXT, text);
                            values.put(DatabaseHelper.COLUMN_CUSTOM_VALUES, customVals);
                            values.put(DatabaseHelper.COLUMN_UPDATED_AT, updatedAt);
                            values.put(DatabaseHelper.COLUMN_GROUP_ID, newGroupId);
                            values.put(DatabaseHelper.COLUMN_IS_READ, 0);
                            db.update(DatabaseHelper.TABLE_FIELDS, SQLiteDatabase.CONFLICT_NONE,
                                    values, "id = ?", new Object[]{existingFieldId});
                            deleteMediaForFieldWithinDb(db, existingFieldId, filesToDeleteAfterCommit);
                            fieldIdMap.put(oldId, existingFieldId);
                            stepProgress++;
                            safeProgress(listener, Math.min(20 + (stepProgress * 60 / totalSteps), 80));
                            continue;
                        } else {
                            stepProgress++;
                            safeProgress(listener, Math.min(20 + (stepProgress * 60 / totalSteps), 80));
                            continue;
                        }
                    }

                    Integer maxOrder = null;
                    String whereMaxOrder;
                    Object[] args;
                    if (newGroupId == 0) {
                        whereMaxOrder = "machine_id = ? AND (group_id = 0 OR group_id IS NULL)";
                        args = new Object[]{newMachineId};
                    } else {
                        whereMaxOrder = "machine_id = ? AND group_id = ?";
                        args = new Object[]{newMachineId, newGroupId};
                    }
                    try (Cursor mc = db.query("SELECT MAX(sort_order) FROM "
                            + DatabaseHelper.TABLE_FIELDS + " WHERE " + whereMaxOrder, args)) {
                        if (mc.moveToFirst() && !mc.isNull(0)) maxOrder = mc.getInt(0);
                    }
                    int newOrder = (maxOrder == null) ? 0 : maxOrder + 1;

                    ContentValues values = new ContentValues();
                    values.put(DatabaseHelper.COLUMN_MACHINE_ID, newMachineId);
                    values.put(DatabaseHelper.COLUMN_TITLE, title);
                    values.put(DatabaseHelper.COLUMN_TEXT, text);
                    values.put(DatabaseHelper.COLUMN_CUSTOM_VALUES, customVals);
                    values.put(DatabaseHelper.COLUMN_CREATED_AT, createdAt);
                    values.put(DatabaseHelper.COLUMN_UPDATED_AT, updatedAt);
                    values.put(DatabaseHelper.COLUMN_SORT_ORDER, newOrder);
                    values.put(DatabaseHelper.COLUMN_GROUP_ID, newGroupId);
                    values.put(DatabaseHelper.COLUMN_IS_READ, isRead ? 1 : 0);

                    long newId = requireInsert(db, DatabaseHelper.TABLE_FIELDS, values);
                    fieldIdMap.put(oldId, (int) newId);
                    stepProgress++;
                    safeProgress(listener, Math.min(20 + (stepProgress * 60 / totalSteps), 80));
                }
            }

            File destDir = context.getExternalFilesDir("media");
            if (destDir == null) destDir = new File(context.getFilesDir(), "media");
            if (!destDir.exists()) destDir.mkdirs();

            try (Cursor cursor = importDb.query(DatabaseHelper.TABLE_MEDIA, null, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    int oldFieldId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_FIELD_ID));
                    String type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TYPE));
                    String oldPath = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PATH));
                    Integer newFieldId = fieldIdMap.get(oldFieldId);
                    if (newFieldId == null) { stepProgress++; continue; }

                    File sourceFile = findMediaSourceFile(mediaImportDir, oldPath);
                    if (sourceFile == null || !sourceFile.exists()) { stepProgress++; continue; }

                    String uniqueFileName = ZipFileUtils.generateUniqueFileName(destDir, sourceFile.getName());
                    File destFile = new File(destDir, uniqueFileName);
                    ZipFileUtils.copyFile(sourceFile, destFile);
                    copiedMediaFiles.add(destFile);

                    ContentValues values = new ContentValues();
                    values.put(DatabaseHelper.COLUMN_FIELD_ID, newFieldId);
                    values.put(DatabaseHelper.COLUMN_TYPE, type);
                    values.put(DatabaseHelper.COLUMN_PATH, destFile.getAbsolutePath());
                    // ИСПРАВЛЕНО (3.2.6): раньше использовался safeInsert — при ошибке
                    // вставки файл уже скопирован, но в БД не учтён, и потом его удалял
                    // cleanupOrphanedMedia. Теперь — requireInsert: при ошибке
                    // откатываем копию файла, чтобы не оставлять сироту.
                    try {
                        requireInsert(db, DatabaseHelper.TABLE_MEDIA, values);
                    } catch (IOException insertErr) {
                        Log.e(TAG, "Media insert failed, removing copied file", insertErr);
                        if (!destFile.delete()) {
                            Log.w(TAG, "Failed to delete unused media file: " + destFile.getAbsolutePath());
                        }
                        copiedMediaFiles.remove(destFile);
                    }

                    stepProgress++;
                    safeProgress(listener, Math.min(20 + (stepProgress * 60 / totalSteps), 80));
                }
            }

            // -------- Импорт отчётов (F) --------
            // Полный архив — здесь есть отчёты, переносим.
            // Filtered архив — таблица пуста, importedReportCount = 0, ничего не делаем
            // (текущие отчёты сохраняются).
            if (hasReportDefinitions && importedReportCount > 0) {
                try (Cursor rc = importDb.query(DatabaseHelper.TABLE_REPORT_DEFINITIONS,
                        null, null, null, null, null, null)) {
                    int nameIdx = rc.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NAME);
                    int configIdx = rc.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CONFIG_JSON);
                    int createdIdx = rc.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CREATED_AT);
                    int updatedIdx = rc.getColumnIndexOrThrow(DatabaseHelper.COLUMN_UPDATED_AT);

                    while (rc.moveToNext()) {
                        String rName = rc.getString(nameIdx);
                        String rConfig = rc.getString(configIdx);
                        long rCreated = rc.getLong(createdIdx);
                        long rUpdated = rc.getLong(updatedIdx);

                        // В merge-режиме не перезаписываем отчёт с тем же именем
                        if (mode == ExportImportHelper.IMPORT_MODE_MERGE) {
                            try (Cursor ec = db.query("SELECT id FROM "
                                    + DatabaseHelper.TABLE_REPORT_DEFINITIONS
                                    + " WHERE name=? LIMIT 1", new Object[]{rName})) {
                                if (ec.moveToFirst()) continue;
                            }
                        }

                        ContentValues values = new ContentValues();
                        values.put(DatabaseHelper.COLUMN_NAME, rName);
                        values.put(DatabaseHelper.COLUMN_CONFIG_JSON, rConfig);
                        values.put(DatabaseHelper.COLUMN_CREATED_AT, rCreated);
                        values.put(DatabaseHelper.COLUMN_UPDATED_AT, rUpdated);
                        safeInsert(db, DatabaseHelper.TABLE_REPORT_DEFINITIONS, values);
                    }
                }
            }

            db.setTransactionSuccessful();
            committed = true;
        } catch (Exception e) {
            Log.e(TAG, "importDataSmartMerge failed", e);
            return false;
        } finally {
            db.endTransaction();
            importDb.close();
            if (!committed) {
                for (File f : copiedMediaFiles) {
                    if (f != null && f.exists()) {
                        if (!f.delete()) Log.w(TAG, "Failed to delete copied media: " + f.getAbsolutePath());
                    }
                }
            }
        }
        if (committed) {
            for (String p : filesToDeleteAfterCommit) FileUtils.deleteFile(p);
        }
        safeProgress(listener, 100);
        return committed;
    }

    private static Integer findFieldIdByMachineAndCreatedAt(SupportSQLiteDatabase db,
                                                             int machineId, long createdAt) {
        try (Cursor c = db.query("SELECT id FROM " + DatabaseHelper.TABLE_FIELDS
                + " WHERE machine_id = ? AND created_at = ? LIMIT 1",
                new Object[]{machineId, createdAt})) {
            if (c.moveToFirst()) return c.getInt(0);
        } catch (Exception e) {
            Log.w(TAG, "findFieldIdByMachineAndCreatedAt failed", e);
        }
        return null;
    }

    private static long getFieldUpdatedAt(SupportSQLiteDatabase db, int fieldId) {
        try (Cursor c = db.query("SELECT updated_at FROM " + DatabaseHelper.TABLE_FIELDS
                + " WHERE id = ? LIMIT 1", new Object[]{fieldId})) {
            if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception e) {
            Log.w(TAG, "getFieldUpdatedAt failed", e);
        }
        return 0L;
    }

    private static void deleteMediaForFieldWithinDb(SupportSQLiteDatabase db, int fieldId,
                                                     List<String> filesToDeleteAfterCommit) {
        try (Cursor c = db.query("SELECT path FROM " + DatabaseHelper.TABLE_MEDIA
                + " WHERE field_id = ?", new Object[]{fieldId})) {
            while (c.moveToNext()) {
                String p = c.getString(0);
                if (p != null && !p.isEmpty()) filesToDeleteAfterCommit.add(p);
            }
        } catch (Exception e) {
            Log.w(TAG, "deleteMediaForFieldWithinDb: collect paths failed", e);
        }
        db.execSQL("DELETE FROM " + DatabaseHelper.TABLE_MEDIA + " WHERE field_id = ?",
                new Object[]{fieldId});
    }

    private static String remapCustomValues(String json, Map<Integer, Integer> templateIdMap) {
        if (json == null || json.isEmpty()) return json;
        try {
            Type mapType = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> values = new Gson().fromJson(json, mapType);
            if (values == null || values.isEmpty()) return json;

            Map<String, String> remapped = new HashMap<>(values.size());
            for (Map.Entry<String, String> e : values.entrySet()) {
                int oldId;
                try { oldId = Integer.parseInt(e.getKey()); }
                catch (NumberFormatException ex) { remapped.put(e.getKey(), e.getValue()); continue; }
                Integer newId = templateIdMap.get(oldId);
                if (newId != null) remapped.put(String.valueOf(newId), e.getValue());
            }
            return new Gson().toJson(remapped);
        } catch (Exception ex) {
            Log.w(TAG, "remapCustomValues failed, keep original", ex);
            return json;
        }
    }

    private static Machine findMachineByName(SupportSQLiteDatabase db, String name) {
        try (Cursor cursor = db.query(
                "SELECT id, name, sort_order, group_id FROM machines WHERE name=?",
                new Object[]{name})) {
            if (cursor.moveToFirst()) {
                return new Machine(cursor.getInt(0), cursor.getString(1),
                        cursor.getInt(2), cursor.getInt(3));
            }
        }
        return null;
    }
}