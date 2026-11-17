package com.example.appic.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.appic.database.AppDatabase;
import com.example.appic.database.DatabaseHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DatabaseSnapshotHelper {

    private static final String TAG = "DatabaseSnapshotHelper";

    private DatabaseSnapshotHelper() {}

    public static void createCleanDatabaseCopy(Context context, File destFile) throws IOException {
        if (destFile.exists()) destFile.delete();

        SupportSQLiteDatabase db = DatabaseHelper.getSupportDatabase(context);

        boolean copied = false;
        try {
            String path = destFile.getAbsolutePath().replace("'", "''");
            db.execSQL("VACUUM INTO '" + path + "'");
            copied = true;
        } catch (Exception e) {
            Log.d(TAG, "VACUUM INTO не поддерживается, используем checkpoint+copy", e);
        }

        if (!copied) {
            try (Cursor c = db.query("PRAGMA wal_checkpoint(TRUNCATE)")) {
                if (c != null) c.moveToFirst();
            } catch (Exception e) {
                Log.w(TAG, "WAL checkpoint failed", e);
            }
            File srcDb = context.getDatabasePath(DatabaseHelper.DATABASE_NAME);
            ZipFileUtils.copyFile(srcDb, destFile);
        }

        if (!destFile.exists() || destFile.length() == 0) {
            throw new IOException("Failed to create database snapshot");
        }
    }

    public static boolean createFilteredDatabase(Context context, File destDbFile, Long startDate, Long endDate) {
        SupportSQLiteDatabase originalDb = DatabaseHelper.getSupportDatabase(context);
        SQLiteDatabase filteredDb = null;
        try {
            if (destDbFile.exists()) destDbFile.delete();
            filteredDb = SQLiteDatabase.openDatabase(destDbFile.getAbsolutePath(),
                    null, SQLiteDatabase.CREATE_IF_NECESSARY);
            createAllTables(filteredDb);

            if (!AppDatabase.columnExists(originalDb,
                    DatabaseHelper.TABLE_FIELDS, DatabaseHelper.COLUMN_CREATED_AT)) return false;

            List<Integer> filteredFieldIds = new ArrayList<>();
            try (Cursor c = originalDb.query("SELECT " + DatabaseHelper.COLUMN_ID
                    + " FROM " + DatabaseHelper.TABLE_FIELDS + " WHERE "
                    + DatabaseHelper.COLUMN_CREATED_AT + " BETWEEN ? AND ?",
                    new Object[]{startDate, endDate})) {
                while (c.moveToNext()) filteredFieldIds.add(c.getInt(0));
            }
            if (filteredFieldIds.isEmpty()) return true;

            String idsStr = TextUtils.join(",", filteredFieldIds);
            List<Integer> machineIds = new ArrayList<>();
            try (Cursor c = originalDb.query("SELECT DISTINCT " + DatabaseHelper.COLUMN_MACHINE_ID
                    + " FROM " + DatabaseHelper.TABLE_FIELDS + " WHERE "
                    + DatabaseHelper.COLUMN_ID + " IN (" + idsStr + ")")) {
                while (c.moveToNext()) machineIds.add(c.getInt(0));
            }

            Set<Integer> machineGroupIds = new HashSet<>();
            if (!machineIds.isEmpty()) {
                String machineIdsStr = TextUtils.join(",", machineIds);
                try (Cursor c = originalDb.query("SELECT DISTINCT " + DatabaseHelper.COLUMN_GROUP_ID
                        + " FROM " + DatabaseHelper.TABLE_MACHINES + " WHERE "
                        + DatabaseHelper.COLUMN_ID + " IN (" + machineIdsStr + ")")) {
                    while (c.moveToNext()) {
                        int gid = c.getInt(0);
                        if (gid != 0) machineGroupIds.add(gid);
                    }
                }
            }
            for (int gid : machineGroupIds) {
                try (Cursor c = originalDb.query("SELECT * FROM "
                        + DatabaseHelper.TABLE_MACHINE_GROUPS + " WHERE "
                        + DatabaseHelper.COLUMN_ID + "=?", new Object[]{gid})) {
                    if (c.moveToFirst()) copyRowToTable(filteredDb, c, DatabaseHelper.TABLE_MACHINE_GROUPS);
                }
            }
            for (int machineId : machineIds) {
                try (Cursor c = originalDb.query("SELECT * FROM "
                        + DatabaseHelper.TABLE_MACHINES + " WHERE "
                        + DatabaseHelper.COLUMN_ID + "=?", new Object[]{machineId})) {
                    if (c.moveToFirst()) copyRowToTable(filteredDb, c, DatabaseHelper.TABLE_MACHINES);
                }
            }
            if (!machineIds.isEmpty()) {
                String machineIdsStr = TextUtils.join(",", machineIds);
                try (Cursor c = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_GROUPS
                        + " WHERE " + DatabaseHelper.COLUMN_MACHINE_ID + " IN (" + machineIdsStr + ")")) {
                    while (c.moveToNext()) copyRowToTable(filteredDb, c, DatabaseHelper.TABLE_GROUPS);
                }
            }
            try (Cursor c = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_FIELDS
                    + " WHERE " + DatabaseHelper.COLUMN_ID + " IN (" + idsStr + ")")) {
                while (c.moveToNext()) copyRowToTable(filteredDb, c, DatabaseHelper.TABLE_FIELDS);
            }
            try (Cursor c = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_MEDIA
                    + " WHERE " + DatabaseHelper.COLUMN_FIELD_ID + " IN (" + idsStr + ")")) {
                while (c.moveToNext()) copyRowToTable(filteredDb, c, DatabaseHelper.TABLE_MEDIA);
            }
            try (Cursor c = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_FIELD_TEMPLATES)) {
                while (c.moveToNext()) copyRowToTable(filteredDb, c, DatabaseHelper.TABLE_FIELD_TEMPLATES);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error creating filtered database", e);
            return false;
        } finally {
            if (filteredDb != null) filteredDb.close();
        }
    }

    public static boolean createFilteredDatabaseByMachine(Context context, File destDbFile, int machineId) {
        SupportSQLiteDatabase originalDb = DatabaseHelper.getSupportDatabase(context);
        SQLiteDatabase filteredDb = null;
        try {
            if (destDbFile.exists()) destDbFile.delete();
            filteredDb = SQLiteDatabase.openDatabase(destDbFile.getAbsolutePath(),
                    null, SQLiteDatabase.CREATE_IF_NECESSARY);
            createAllTables(filteredDb);

            boolean machineFound = false;
            try (Cursor mc = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_MACHINES
                    + " WHERE " + DatabaseHelper.COLUMN_ID + "=?", new Object[]{machineId})) {
                if (mc.moveToFirst()) {
                    machineFound = true;
                    int gid = mc.getInt(mc.getColumnIndexOrThrow(DatabaseHelper.COLUMN_GROUP_ID));
                    if (gid != 0) {
                        try (Cursor mgc = originalDb.query("SELECT * FROM "
                                + DatabaseHelper.TABLE_MACHINE_GROUPS + " WHERE "
                                + DatabaseHelper.COLUMN_ID + "=?", new Object[]{gid})) {
                            if (mgc.moveToFirst()) copyRowToTable(filteredDb, mgc, DatabaseHelper.TABLE_MACHINE_GROUPS);
                        }
                    }
                    copyRowToTable(filteredDb, mc, DatabaseHelper.TABLE_MACHINES);
                }
            }
            if (!machineFound) return false;

            try (Cursor gc = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_GROUPS
                    + " WHERE " + DatabaseHelper.COLUMN_MACHINE_ID + "=?", new Object[]{machineId})) {
                while (gc.moveToNext()) copyRowToTable(filteredDb, gc, DatabaseHelper.TABLE_GROUPS);
            }
            try (Cursor fc = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_FIELDS
                    + " WHERE " + DatabaseHelper.COLUMN_MACHINE_ID + "=?", new Object[]{machineId})) {
                while (fc.moveToNext()) copyRowToTable(filteredDb, fc, DatabaseHelper.TABLE_FIELDS);
            }
            try (Cursor mediaCursor = filteredDb.rawQuery("SELECT " + DatabaseHelper.COLUMN_ID
                    + " FROM " + DatabaseHelper.TABLE_FIELDS, null)) {
                copyMediaForFields(filteredDb, originalDb, mediaCursor);
            }
            try (Cursor c = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_FIELD_TEMPLATES)) {
                while (c.moveToNext()) copyRowToTable(filteredDb, c, DatabaseHelper.TABLE_FIELD_TEMPLATES);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "createFilteredDatabaseByMachine failed", e);
            return false;
        } finally {
            if (filteredDb != null) filteredDb.close();
        }
    }

    public static boolean createFilteredDatabaseByGroup(Context context, File destDbFile, int groupId) {
        SupportSQLiteDatabase originalDb = DatabaseHelper.getSupportDatabase(context);
        SQLiteDatabase filteredDb = null;
        try {
            if (destDbFile.exists()) destDbFile.delete();
            filteredDb = SQLiteDatabase.openDatabase(destDbFile.getAbsolutePath(),
                    null, SQLiteDatabase.CREATE_IF_NECESSARY);
            createAllTables(filteredDb);

            int machineId = -1;
            try (Cursor gc = originalDb.query("SELECT " + DatabaseHelper.COLUMN_MACHINE_ID
                    + " FROM " + DatabaseHelper.TABLE_GROUPS + " WHERE "
                    + DatabaseHelper.COLUMN_ID + "=?", new Object[]{groupId})) {
                if (gc.moveToFirst()) machineId = gc.getInt(0);
            }
            if (machineId == -1) return false;

            try (Cursor mc = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_MACHINES
                    + " WHERE " + DatabaseHelper.COLUMN_ID + "=?", new Object[]{machineId})) {
                if (mc.moveToFirst()) {
                    int gid = mc.getInt(mc.getColumnIndexOrThrow(DatabaseHelper.COLUMN_GROUP_ID));
                    if (gid != 0) {
                        try (Cursor mgc = originalDb.query("SELECT * FROM "
                                + DatabaseHelper.TABLE_MACHINE_GROUPS + " WHERE "
                                + DatabaseHelper.COLUMN_ID + "=?", new Object[]{gid})) {
                            if (mgc.moveToFirst()) copyRowToTable(filteredDb, mgc, DatabaseHelper.TABLE_MACHINE_GROUPS);
                        }
                    }
                    copyRowToTable(filteredDb, mc, DatabaseHelper.TABLE_MACHINES);
                }
            }
            try (Cursor gc = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_GROUPS
                    + " WHERE " + DatabaseHelper.COLUMN_ID + "=?", new Object[]{groupId})) {
                if (gc.moveToFirst()) copyRowToTable(filteredDb, gc, DatabaseHelper.TABLE_GROUPS);
            }
            try (Cursor fc = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_FIELDS
                    + " WHERE " + DatabaseHelper.COLUMN_GROUP_ID + "=?", new Object[]{groupId})) {
                while (fc.moveToNext()) copyRowToTable(filteredDb, fc, DatabaseHelper.TABLE_FIELDS);
            }
            try (Cursor mediaCursor = filteredDb.rawQuery("SELECT " + DatabaseHelper.COLUMN_ID
                    + " FROM " + DatabaseHelper.TABLE_FIELDS, null)) {
                copyMediaForFields(filteredDb, originalDb, mediaCursor);
            }
            try (Cursor c = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_FIELD_TEMPLATES)) {
                while (c.moveToNext()) copyRowToTable(filteredDb, c, DatabaseHelper.TABLE_FIELD_TEMPLATES);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "createFilteredDatabaseByGroup failed", e);
            return false;
        } finally {
            if (filteredDb != null) filteredDb.close();
        }
    }

    public static boolean createFilteredDatabaseByField(Context context, File destDbFile, int fieldId) {
        SupportSQLiteDatabase originalDb = DatabaseHelper.getSupportDatabase(context);
        SQLiteDatabase filteredDb = null;
        try {
            if (destDbFile.exists()) destDbFile.delete();
            filteredDb = SQLiteDatabase.openDatabase(destDbFile.getAbsolutePath(),
                    null, SQLiteDatabase.CREATE_IF_NECESSARY);
            createAllTables(filteredDb);

            int machineId = -1;
            int groupId = 0;
            boolean fieldFound = false;
            try (Cursor fc = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_FIELDS
                    + " WHERE " + DatabaseHelper.COLUMN_ID + "=?", new Object[]{fieldId})) {
                if (fc.moveToFirst()) {
                    fieldFound = true;
                    machineId = fc.getInt(fc.getColumnIndexOrThrow(DatabaseHelper.COLUMN_MACHINE_ID));
                    groupId = fc.getInt(fc.getColumnIndexOrThrow(DatabaseHelper.COLUMN_GROUP_ID));
                    copyRowToTable(filteredDb, fc, DatabaseHelper.TABLE_FIELDS);
                }
            }
            if (!fieldFound || machineId == -1) return false;

            try (Cursor mc = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_MACHINES
                    + " WHERE " + DatabaseHelper.COLUMN_ID + "=?", new Object[]{machineId})) {
                if (mc.moveToFirst()) {
                    int gid = mc.getInt(mc.getColumnIndexOrThrow(DatabaseHelper.COLUMN_GROUP_ID));
                    if (gid != 0) {
                        try (Cursor mgc = originalDb.query("SELECT * FROM "
                                + DatabaseHelper.TABLE_MACHINE_GROUPS + " WHERE "
                                + DatabaseHelper.COLUMN_ID + "=?", new Object[]{gid})) {
                            if (mgc.moveToFirst()) copyRowToTable(filteredDb, mgc, DatabaseHelper.TABLE_MACHINE_GROUPS);
                        }
                    }
                    copyRowToTable(filteredDb, mc, DatabaseHelper.TABLE_MACHINES);
                }
            }
            if (groupId != 0) {
                try (Cursor gc = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_GROUPS
                        + " WHERE " + DatabaseHelper.COLUMN_ID + "=?", new Object[]{groupId})) {
                    if (gc.moveToFirst()) copyRowToTable(filteredDb, gc, DatabaseHelper.TABLE_GROUPS);
                }
            }
            try (Cursor mediaCursor = filteredDb.rawQuery("SELECT " + DatabaseHelper.COLUMN_ID
                    + " FROM " + DatabaseHelper.TABLE_FIELDS, null)) {
                copyMediaForFields(filteredDb, originalDb, mediaCursor);
            }
            try (Cursor c = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_FIELD_TEMPLATES)) {
                while (c.moveToNext()) copyRowToTable(filteredDb, c, DatabaseHelper.TABLE_FIELD_TEMPLATES);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "createFilteredDatabaseByField failed", e);
            return false;
        } finally {
            if (filteredDb != null) filteredDb.close();
        }
    }

    public static boolean createFilteredDatabaseByMachineGroup(Context context, File destDbFile, int machineGroupId) {
        SupportSQLiteDatabase originalDb = DatabaseHelper.getSupportDatabase(context);
        SQLiteDatabase filteredDb = null;
        try {
            if (destDbFile.exists()) destDbFile.delete();
            filteredDb = SQLiteDatabase.openDatabase(destDbFile.getAbsolutePath(),
                    null, SQLiteDatabase.CREATE_IF_NECESSARY);
            createAllTables(filteredDb);

            boolean groupFound = false;
            try (Cursor mgc = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_MACHINE_GROUPS
                    + " WHERE " + DatabaseHelper.COLUMN_ID + "=?", new Object[]{machineGroupId})) {
                if (mgc.moveToFirst()) {
                    groupFound = true;
                    copyRowToTable(filteredDb, mgc, DatabaseHelper.TABLE_MACHINE_GROUPS);
                }
            }
            if (!groupFound) return false;

            try (Cursor mc = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_MACHINES
                    + " WHERE " + DatabaseHelper.COLUMN_GROUP_ID + "=?", new Object[]{machineGroupId})) {
                while (mc.moveToNext()) copyRowToTable(filteredDb, mc, DatabaseHelper.TABLE_MACHINES);
            }
            copyAllForMachinesInTable(filteredDb, originalDb, DatabaseHelper.TABLE_MACHINES,
                    DatabaseHelper.COLUMN_GROUP_ID, machineGroupId);
            try (Cursor c = originalDb.query("SELECT * FROM " + DatabaseHelper.TABLE_FIELD_TEMPLATES)) {
                while (c.moveToNext()) copyRowToTable(filteredDb, c, DatabaseHelper.TABLE_FIELD_TEMPLATES);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "createFilteredDatabaseByMachineGroup failed", e);
            return false;
        } finally {
            if (filteredDb != null) filteredDb.close();
        }
    }

    public static List<String> getUsedMediaPaths(Context context, Long startDate, Long endDate) {
        List<String> paths = new ArrayList<>();
        SupportSQLiteDatabase db = DatabaseHelper.getSupportDatabase(context);
        if (!AppDatabase.columnExists(db,
                DatabaseHelper.TABLE_FIELDS, DatabaseHelper.COLUMN_CREATED_AT)) return paths;

        String where = DatabaseHelper.COLUMN_CREATED_AT + " BETWEEN ? AND ?";
        Object[] args = new Object[]{startDate, endDate};
        List<Integer> fieldIds = new ArrayList<>();
        try (Cursor c = db.query("SELECT " + DatabaseHelper.COLUMN_ID + " FROM "
                + DatabaseHelper.TABLE_FIELDS + " WHERE " + where, args)) {
            while (c.moveToNext()) fieldIds.add(c.getInt(0));
        }
        if (fieldIds.isEmpty()) return paths;

        String ids = TextUtils.join(",", fieldIds);
        try (Cursor c = db.query("SELECT " + DatabaseHelper.COLUMN_PATH + " FROM "
                + DatabaseHelper.TABLE_MEDIA + " WHERE "
                + DatabaseHelper.COLUMN_FIELD_ID + " IN (" + ids + ")")) {
            while (c.moveToNext()) {
                String path = c.getString(0);
                if (path != null && !path.isEmpty()) paths.add(path);
            }
        }
        return paths;
    }

    public static List<String> getMediaPathsForMachine(Context context, int machineId) {
        List<String> paths = new ArrayList<>();
        SupportSQLiteDatabase db = DatabaseHelper.getSupportDatabase(context);
        String query = "SELECT " + DatabaseHelper.COLUMN_PATH + " FROM "
                + DatabaseHelper.TABLE_MEDIA + " WHERE " + DatabaseHelper.COLUMN_FIELD_ID
                + " IN ( SELECT " + DatabaseHelper.COLUMN_ID + " FROM "
                + DatabaseHelper.TABLE_FIELDS + " WHERE "
                + DatabaseHelper.COLUMN_MACHINE_ID + " = ?)";
        try (Cursor c = db.query(query, new Object[]{machineId})) {
            while (c.moveToNext()) paths.add(c.getString(0));
        }
        return paths;
    }

    public static List<String> getMediaPathsForGroup(Context context, int groupId) {
        List<String> paths = new ArrayList<>();
        SupportSQLiteDatabase db = DatabaseHelper.getSupportDatabase(context);
        String query = "SELECT " + DatabaseHelper.COLUMN_PATH + " FROM "
                + DatabaseHelper.TABLE_MEDIA + " WHERE " + DatabaseHelper.COLUMN_FIELD_ID
                + " IN ( SELECT " + DatabaseHelper.COLUMN_ID + " FROM "
                + DatabaseHelper.TABLE_FIELDS + " WHERE "
                + DatabaseHelper.COLUMN_GROUP_ID + " = ?)";
        try (Cursor c = db.query(query, new Object[]{groupId})) {
            while (c.moveToNext()) paths.add(c.getString(0));
        }
        return paths;
    }

    public static List<String> getMediaPathsForField(Context context, int fieldId) {
        List<String> paths = new ArrayList<>();
        SupportSQLiteDatabase db = DatabaseHelper.getSupportDatabase(context);
        try (Cursor c = db.query("SELECT " + DatabaseHelper.COLUMN_PATH + " FROM "
                + DatabaseHelper.TABLE_MEDIA + " WHERE "
                + DatabaseHelper.COLUMN_FIELD_ID + "=?", new Object[]{fieldId})) {
            while (c.moveToNext()) paths.add(c.getString(0));
        }
        return paths;
    }

    public static List<String> getMediaPathsForMachineGroup(Context context, int machineGroupId) {
        List<String> paths = new ArrayList<>();
        SupportSQLiteDatabase db = DatabaseHelper.getSupportDatabase(context);
        String query = "SELECT " + DatabaseHelper.COLUMN_PATH + " FROM "
                + DatabaseHelper.TABLE_MEDIA + " WHERE " + DatabaseHelper.COLUMN_FIELD_ID
                + " IN ( SELECT " + DatabaseHelper.COLUMN_ID + " FROM "
                + DatabaseHelper.TABLE_FIELDS + " WHERE "
                + DatabaseHelper.COLUMN_MACHINE_ID + " IN ( SELECT "
                + DatabaseHelper.COLUMN_ID + " FROM " + DatabaseHelper.TABLE_MACHINES
                + " WHERE " + DatabaseHelper.COLUMN_GROUP_ID + " = ?))";
        try (Cursor c = db.query(query, new Object[]{machineGroupId})) {
            while (c.moveToNext()) paths.add(c.getString(0));
        }
        return paths;
    }

    public static void createAllTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + DatabaseHelper.TABLE_MACHINE_GROUPS + " (" +
                DatabaseHelper.COLUMN_ID + " INTEGER PRIMARY KEY, " +
                DatabaseHelper.COLUMN_NAME + " TEXT, " +
                DatabaseHelper.COLUMN_SORT_ORDER + " INTEGER)");
        db.execSQL("CREATE TABLE " + DatabaseHelper.TABLE_MACHINES + " (" +
                DatabaseHelper.COLUMN_ID + " INTEGER PRIMARY KEY, " +
                DatabaseHelper.COLUMN_NAME + " TEXT, " +
                DatabaseHelper.COLUMN_SORT_ORDER + " INTEGER, " +
                DatabaseHelper.COLUMN_GROUP_ID + " INTEGER)");
        db.execSQL("CREATE TABLE " + DatabaseHelper.TABLE_GROUPS + " (" +
                DatabaseHelper.COLUMN_ID + " INTEGER PRIMARY KEY, " +
                DatabaseHelper.COLUMN_MACHINE_ID + " INTEGER, " +
                DatabaseHelper.COLUMN_NAME + " TEXT, " +
                DatabaseHelper.COLUMN_SORT_ORDER + " INTEGER)");
        db.execSQL("CREATE TABLE " + DatabaseHelper.TABLE_FIELDS + " (" +
                DatabaseHelper.COLUMN_ID + " INTEGER PRIMARY KEY, " +
                DatabaseHelper.COLUMN_MACHINE_ID + " INTEGER, " +
                DatabaseHelper.COLUMN_TITLE + " TEXT, " +
                DatabaseHelper.COLUMN_TEXT + " TEXT, " +
                DatabaseHelper.COLUMN_CREATED_AT + " INTEGER, " +
                DatabaseHelper.COLUMN_UPDATED_AT + " INTEGER, " +
                DatabaseHelper.COLUMN_SORT_ORDER + " INTEGER, " +
                DatabaseHelper.COLUMN_GROUP_ID + " INTEGER, " +
                DatabaseHelper.COLUMN_IS_READ + " INTEGER, " +
                DatabaseHelper.COLUMN_CUSTOM_VALUES + " TEXT)");
        db.execSQL("CREATE TABLE " + DatabaseHelper.TABLE_MEDIA + " (" +
                DatabaseHelper.COLUMN_ID + " INTEGER PRIMARY KEY, " +
                DatabaseHelper.COLUMN_FIELD_ID + " INTEGER, " +
                DatabaseHelper.COLUMN_TYPE + " TEXT, " +
                DatabaseHelper.COLUMN_PATH + " TEXT)");
        db.execSQL("CREATE TABLE " + DatabaseHelper.TABLE_FIELD_TEMPLATES + " (" +
                DatabaseHelper.COLUMN_ID + " INTEGER PRIMARY KEY, " +
                DatabaseHelper.COLUMN_NAME + " TEXT, " +
                DatabaseHelper.COLUMN_TYPE + " TEXT, " +
                "options TEXT, " +
                "is_required INTEGER, " +
                "is_auto_fill INTEGER NOT NULL DEFAULT 0, " +
                DatabaseHelper.COLUMN_SORT_ORDER + " INTEGER)");
        db.execSQL("CREATE TABLE " + DatabaseHelper.TABLE_REPORT_DEFINITIONS + " (" +
                DatabaseHelper.COLUMN_ID + " INTEGER PRIMARY KEY, " +
                DatabaseHelper.COLUMN_NAME + " TEXT, " +
                DatabaseHelper.COLUMN_CONFIG_JSON + " TEXT, " +
                DatabaseHelper.COLUMN_CREATED_AT + " INTEGER, " +
                DatabaseHelper.COLUMN_UPDATED_AT + " INTEGER)");
    }

    public static void copyRowToTable(SQLiteDatabase destDb, Cursor cursor, String tableName) {
        android.content.ContentValues values = new android.content.ContentValues();
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            String colName = cursor.getColumnName(i);
            switch (cursor.getType(i)) {
                case Cursor.FIELD_TYPE_INTEGER: values.put(colName, cursor.getLong(i)); break;
                case Cursor.FIELD_TYPE_STRING: values.put(colName, cursor.getString(i)); break;
                case Cursor.FIELD_TYPE_FLOAT: values.put(colName, cursor.getFloat(i)); break;
                case Cursor.FIELD_TYPE_BLOB: values.put(colName, cursor.getBlob(i)); break;
                default: values.put(colName, cursor.getString(i));
            }
        }
        long id = destDb.insert(tableName, null, values);
        if (id == -1) Log.w(TAG, "copyRowToTable: insert failed for table=" + tableName);
    }

    public static void copyMediaForFields(SQLiteDatabase destDb, SupportSQLiteDatabase sourceDb, Cursor fieldIdsCursor) {
        if (fieldIdsCursor == null) return;
        List<Integer> fieldIds = new ArrayList<>();
        while (fieldIdsCursor.moveToNext()) fieldIds.add(fieldIdsCursor.getInt(0));
        if (fieldIds.isEmpty()) return;

        String ids = TextUtils.join(",", fieldIds);
        try (Cursor mc = sourceDb.query("SELECT * FROM " + DatabaseHelper.TABLE_MEDIA
                + " WHERE " + DatabaseHelper.COLUMN_FIELD_ID + " IN (" + ids + ")")) {
            while (mc.moveToNext()) copyRowToTable(destDb, mc, DatabaseHelper.TABLE_MEDIA);
        } catch (Exception e) {
            Log.e(TAG, "copyMediaForFields failed", e);
        }
    }

    public static void copyAllForMachinesInTable(SQLiteDatabase destDb, SupportSQLiteDatabase sourceDb,
                                                  String machinesTable, String filterColumn, int filterValue) {
        List<Integer> machineIds = new ArrayList<>();
        try (Cursor c = sourceDb.query("SELECT " + DatabaseHelper.COLUMN_ID + " FROM "
                + machinesTable + " WHERE " + filterColumn + " = ?", new Object[]{filterValue})) {
            while (c.moveToNext()) machineIds.add(c.getInt(0));
        }
        if (machineIds.isEmpty()) return;

        String ids = TextUtils.join(",", machineIds);
        try (Cursor c = sourceDb.query("SELECT * FROM " + DatabaseHelper.TABLE_GROUPS
                + " WHERE " + DatabaseHelper.COLUMN_MACHINE_ID + " IN (" + ids + ")")) {
            while (c.moveToNext()) copyRowToTable(destDb, c, DatabaseHelper.TABLE_GROUPS);
        }
        try (Cursor c = sourceDb.query("SELECT * FROM " + DatabaseHelper.TABLE_FIELDS
                + " WHERE " + DatabaseHelper.COLUMN_MACHINE_ID + " IN (" + ids + ")")) {
            while (c.moveToNext()) copyRowToTable(destDb, c, DatabaseHelper.TABLE_FIELDS);
        }
        try (Cursor mediaCursor = destDb.rawQuery("SELECT " + DatabaseHelper.COLUMN_ID
                + " FROM " + DatabaseHelper.TABLE_FIELDS, null)) {
            copyMediaForFields(destDb, sourceDb, mediaCursor);
        }
    }

    public static boolean tableExistsImport(SQLiteDatabase db, String tableName) {
        try (Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{tableName})) {
            return cursor.getCount() > 0;
        } catch (Exception e) { return false; }
    }

    public static boolean doesColumnExistImport(SQLiteDatabase db, String tableName, String columnName) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null)) {
            int nameIdx = cursor.getColumnIndex("name");
            if (nameIdx < 0) return false;
            while (cursor.moveToNext()) {
                if (columnName.equals(cursor.getString(nameIdx))) return true;
            }
        } catch (Exception e) { return false; }
        return false;
    }
}