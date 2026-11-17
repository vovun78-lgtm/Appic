package com.example.appic.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.appic.R;
import com.example.appic.adapters.SearchResultsAdapter;
import com.example.appic.database.models.FieldTemplate;
import com.example.appic.utils.TermsHelper;
import com.example.appic.utils.TextCapitalizer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DatabaseHelper {

    private static final String TAG = "DatabaseHelper";

    public static final String DATABASE_NAME = "appic.db";
    public static final String TABLE_MACHINES = "machines";
    public static final String TABLE_MACHINE_GROUPS = "machine_groups";
    public static final String TABLE_FIELDS = "fields";
    public static final String TABLE_MEDIA = "media";
    public static final String TABLE_GROUPS = "groups";
    public static final String TABLE_FIELD_TEMPLATES = "field_templates";
    public static final String TABLE_REPORT_DEFINITIONS = "report_definitions";

    public static final String COLUMN_ID = "id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_MACHINE_ID = "machine_id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_TEXT = "text";
    public static final String COLUMN_CUSTOM_VALUES = "custom_values";
    public static final String COLUMN_FIELD_ID = "field_id";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_PATH = "path";
    public static final String COLUMN_SORT_ORDER = "sort_order";
    public static final String COLUMN_CREATED_AT = "created_at";
    public static final String COLUMN_UPDATED_AT = "updated_at";
    public static final String COLUMN_GROUP_ID = "group_id";
    public static final String COLUMN_IS_READ = "is_read";
    public static final String COLUMN_CONFIG_JSON = "config_json";

    private DatabaseHelper() {}

    public static SupportSQLiteDatabase getSupportDatabase(Context context) {
        return AppDatabase.getInstance(context).getOpenHelper().getWritableDatabase();
    }

    /**
     * Глобальный поиск по объектам, группам и записям.
     *
     * Фильтрация выполняется в SQLite (LIKE + json_extract), чтобы
     * не грузить всю базу в память на каждую букву в поиске.
     *
     * ИСПРАВЛЕНО (D): превью текста обрезается через safeTruncate,
     * чтобы не рвать суррогатные пары (эмодзи).
     *
     * ИСПРАВЛЕНО (п.1): запрос к таблице fields (с json_extract) обёрнут в
     * try/catch (SQLiteException) — если json1-расширение недоступно на
     * старых версиях Android или custom_values содержит повреждённый JSON,
     * метод не падает, а возвращает уже собранные результаты (машины и группы).
     */
    public static List<SearchResultsAdapter.SearchResult> globalSearch(Context context, String query) {
        List<SearchResultsAdapter.SearchResult> results = new ArrayList<>();
        if (query == null) return results;
        String trimmed = query.trim();
        if (trimmed.isEmpty()) return results;

        String likeArg = "%" + trimmed + "%";
        String lowerQuery = trimmed.toLowerCase(Locale.ROOT);

        SupportSQLiteDatabase db = getSupportDatabase(context);

        // -------- Шаблоны --------
        Map<String, FieldTemplate> templateMap = new HashMap<>();
        try (Cursor c = db.query("SELECT * FROM " + TABLE_FIELD_TEMPLATES)) {
            int idIdx = c.getColumnIndexOrThrow(COLUMN_ID);
            int nameIdx = c.getColumnIndexOrThrow(COLUMN_NAME);
            int typeIdx = c.getColumnIndexOrThrow(COLUMN_TYPE);
            int optIdx = c.getColumnIndexOrThrow("options");
            int reqIdx = c.getColumnIndexOrThrow("is_required");
            int sortIdx = c.getColumnIndexOrThrow(COLUMN_SORT_ORDER);
            int autoFillIdx = c.getColumnIndex("is_auto_fill");

            while (c.moveToNext()) {
                FieldTemplate t = new FieldTemplate(
                        c.getInt(idIdx),
                        c.getString(nameIdx),
                        c.getString(typeIdx),
                        c.getString(optIdx),
                        c.getInt(reqIdx) == 1,
                        c.getInt(sortIdx),
                        autoFillIdx != -1 && c.getInt(autoFillIdx) == 1
                );
                templateMap.put(String.valueOf(t.getId()), t);
            }
        }

        // -------- Машины --------
        try (Cursor c = db.query(
                "SELECT " + COLUMN_ID + ", " + COLUMN_NAME
                        + " FROM " + TABLE_MACHINES
                        + " WHERE " + COLUMN_NAME + " LIKE ?"
                        + " ORDER BY " + COLUMN_SORT_ORDER + " ASC",
                new Object[]{likeArg})) {
            int idIdx = c.getColumnIndexOrThrow(COLUMN_ID);
            int nameIdx = c.getColumnIndexOrThrow(COLUMN_NAME);
            while (c.moveToNext()) {
                int id = c.getInt(idIdx);
                String name = c.getString(nameIdx);
                results.add(new SearchResultsAdapter.SearchResult(
                        SearchResultsAdapter.TYPE_MACHINE, id, 0, 0, name,
                        TermsHelper.getMachineSingle(context)));
            }
        }

        // -------- Группы --------
        // ИСПРАВЛЕНО (п.7): строки "Группа" берутся из ресурсов.
        final String groupTypeStr = context.getString(R.string.type_group);
        try (Cursor c = db.query(
                "SELECT g." + COLUMN_ID + ", g." + COLUMN_MACHINE_ID + ", g." + COLUMN_NAME
                        + ", m." + COLUMN_NAME
                        + " FROM " + TABLE_GROUPS + " g"
                        + " LEFT JOIN " + TABLE_MACHINES + " m ON m." + COLUMN_ID + " = g." + COLUMN_MACHINE_ID
                        + " WHERE g." + COLUMN_NAME + " LIKE ?"
                        + " ORDER BY g." + COLUMN_SORT_ORDER + " ASC",
                new Object[]{likeArg})) {
            while (c.moveToNext()) {
                int groupId = c.getInt(0);
                int machineId = c.getInt(1);
                String groupName = c.getString(2);
                String machineName = c.isNull(3) ? "" : c.getString(3);
                String subtitle = machineName.isEmpty()
                        ? groupTypeStr
                        : groupTypeStr + " · " + machineName;
                results.add(new SearchResultsAdapter.SearchResult(
                        SearchResultsAdapter.TYPE_GROUP, machineId, groupId, 0, groupName, subtitle));
            }
        }

        // -------- Записи --------
        StringBuilder sql = new StringBuilder();
        List<Object> args = new ArrayList<>();
        sql.append("SELECT f.").append(COLUMN_ID)
                .append(", f.").append(COLUMN_MACHINE_ID)
                .append(", f.").append(COLUMN_GROUP_ID)
                .append(", f.").append(COLUMN_TITLE)
                .append(", f.").append(COLUMN_TEXT)
                .append(", f.").append(COLUMN_CUSTOM_VALUES)
                .append(", m.").append(COLUMN_NAME)
                .append(", g.").append(COLUMN_NAME);
        sql.append(" FROM ").append(TABLE_FIELDS).append(" f");
        sql.append(" LEFT JOIN ").append(TABLE_MACHINES).append(" m ON m.").append(COLUMN_ID).append(" = f.").append(COLUMN_MACHINE_ID);
        sql.append(" LEFT JOIN ").append(TABLE_GROUPS).append(" g ON g.").append(COLUMN_ID).append(" = f.").append(COLUMN_GROUP_ID);
        sql.append(" WHERE (f.").append(COLUMN_TITLE).append(" LIKE ?")
                .append(" OR f.").append(COLUMN_TEXT).append(" LIKE ?")
                .append(" OR f.").append(COLUMN_CUSTOM_VALUES).append(" LIKE ?");
        args.add(likeArg);
        args.add(likeArg);
        args.add(likeArg);

        for (FieldTemplate t : templateMap.values()) {
            String type = t.getType();
            if ("TEXT".equals(type) || "URL".equals(type) || "EMAIL".equals(type)
                    || "PHONE".equals(type) || "DROPDOWN".equals(type) || "MULTISELECT".equals(type)) {
                sql.append(" OR json_extract(f.").append(COLUMN_CUSTOM_VALUES)
                        .append(", '$.\"").append(t.getId()).append("\"') LIKE ?");
                args.add(likeArg);
            }
        }
        sql.append(") ORDER BY f.").append(COLUMN_SORT_ORDER).append(" ASC");

        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, String>>(){}.getType();

        // ИСПРАВЛЕНО (п.1): json_extract может отсутствовать (Android < 8.0 / кастомные сборки)
        // или упасть на повреждённом JSON в custom_values. Ловим SQLiteException и возвращаем
        // уже собранные результаты (машины + группы), чтобы поиск не крашил приложение.
        try (Cursor c = db.query(sql.toString(), args.toArray())) {
            while (c.moveToNext()) {
                int fieldId = c.getInt(0);
                int machineId = c.getInt(1);
                int groupId = c.getInt(2);
                String title = c.getString(3);
                String text = c.getString(4);
                String customValues = c.getString(5);
                String machineName = c.isNull(6) ? "" : c.getString(6);
                String groupName = c.isNull(7) ? "" : c.getString(7);

                String customMatchPreview = null;
                if (customValues != null && !customValues.trim().isEmpty()
                        && !customValues.equals("{}")) {
                    try {
                        Map<String, String> values = gson.fromJson(customValues, mapType);
                        if (values != null) {
                            for (Map.Entry<String, String> entry : values.entrySet()) {
                                String templateIdStr = entry.getKey();
                                String val = entry.getValue();
                                if (val == null || val.trim().isEmpty() || val.equals("false")) continue;

                                FieldTemplate template = templateMap.get(templateIdStr);
                                if (template == null) continue;

                                if (val.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                                    customMatchPreview = template.getName() + ": " + val;
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                StringBuilder subtitle = new StringBuilder();
                if (!machineName.isEmpty()) subtitle.append(machineName);
                if (!groupName.isEmpty()) {
                    if (subtitle.length() > 0) subtitle.append(" · ");
                    subtitle.append(groupName);
                }
                if (text != null && !text.isEmpty() && subtitle.length() < 40) {
                    if (subtitle.length() > 0) subtitle.append(" · ");
                    // ИСПРАВЛЕНО (D): safeTruncate не рвёт эмодзи на границе
                    subtitle.append(TextCapitalizer.safeTruncate(text, 60));
                }
                if (customMatchPreview != null) {
                    if (subtitle.length() > 0) subtitle.append(" · ");
                    subtitle.append(customMatchPreview);
                }

                results.add(new SearchResultsAdapter.SearchResult(
                        SearchResultsAdapter.TYPE_FIELD, machineId, groupId, fieldId,
                        title != null ? title : "", subtitle.toString()));
            }
        } catch (SQLiteException e) {
            Log.w(TAG, "globalSearch: fields query failed (json_extract missing or bad JSON), "
                    + "returning partial results", e);
            // результаты по машинам и группам уже собраны — отдаём их
        }
        return results;
    }
}