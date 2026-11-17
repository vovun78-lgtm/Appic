package com.example.appic.utils.statistics;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.appic.database.DatabaseHelper;
import com.example.appic.database.models.FieldTemplate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatisticsEngine {

    private static final String TAG = "StatisticsEngine";

    public static StatisticsResult execute(Context context, StatisticsConfig config) {
        StatisticsResult result = new StatisticsResult();
        try {
            SupportSQLiteDatabase db = DatabaseHelper.getSupportDatabase(context);

            List<Integer> machineIds = resolveScopeMachines(db, config);

            if (machineIds.isEmpty()) {
                result.errorMessage = "Нет объектов в выбранной области";
                return result;
            }

            StringBuilder sql = new StringBuilder();
            List<Object> args = new ArrayList<>();

            StringBuilder where = new StringBuilder();
            where.append("f.machine_id IN (");
            for (int i = 0; i < machineIds.size(); i++) {
                if (i > 0) where.append(",");
                where.append("?");
                args.add(machineIds.get(i));
            }
            where.append(")");

            if (config.startDate != null) {
                where.append(" AND f.created_at >= ?");
                args.add(config.startDate);
            }
            if (config.endDate != null) {
                where.append(" AND f.created_at <= ?");
                args.add(config.endDate);
            }

            if (config.filters != null && !config.filters.isEmpty()) {
                for (FilterCondition fc : config.filters) {
                    String sub = buildFilterClause(fc, args, context);
                    if (sub != null && !sub.isEmpty()) {
                        where.append(" AND (").append(sub).append(")");
                    }
                }
            }

            String groupExpr = buildGroupExpr(config);
            result.groupByLabel = buildGroupLabel(config);

            StringBuilder selectMetrics = new StringBuilder();
            for (int i = 0; i < config.metrics.size(); i++) {
                MetricDef m = config.metrics.get(i);
                String expr = buildMetricExpr(m);
                selectMetrics.append(", ").append(expr).append(" AS m").append(i);
                result.metricLabels.add(buildMetricLabel(m, context));
            }
            if (config.metrics.isEmpty()) {
                selectMetrics.append(", COUNT(*) AS m0");
                result.metricLabels.add("Количество");
            }

            sql.append("SELECT ").append(groupExpr).append(" AS grp").append(selectMetrics);
            sql.append(" FROM fields f");
            sql.append(" LEFT JOIN machines m ON m.id = f.machine_id");
            sql.append(" LEFT JOIN groups g ON g.id = f.group_id");
            sql.append(" WHERE ").append(where);
            sql.append(" GROUP BY grp");
            sql.append(config.sortDesc ? " ORDER BY m0 DESC" : " ORDER BY m0 ASC");
            if (config.limit > 0) sql.append(" LIMIT ").append(config.limit);

            Log.d(TAG, "SQL: " + sql);
            Log.d(TAG, "ARGS: " + args);

            try (Cursor c = db.query(sql.toString(), args.toArray())) {
                result.columnLabels.add(result.groupByLabel);
                result.columnLabels.addAll(result.metricLabels);

                int colGrp = c.getColumnIndex("grp");
                while (c.moveToNext()) {
                    String grp = c.isNull(colGrp) ? "—" : c.getString(colGrp);
                    List<Double> vals = new ArrayList<>();
                    for (int i = 0; i < config.metrics.size(); i++) {
                        String name = "m" + i;
                        int idx = c.getColumnIndex(name);
                        if (idx >= 0) vals.add(c.isNull(idx) ? 0.0 : c.getDouble(idx));
                    }
                    if (config.metrics.isEmpty()) {
                        int idx = c.getColumnIndex("m0");
                        if (idx >= 0) vals.add(c.isNull(idx) ? 0.0 : c.getDouble(idx));
                    }
                    result.rows.add(new StatisticsRow(grp != null ? grp : "—", vals));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "execute failed", e);
            result.errorMessage = "Ошибка выполнения: " + e.getMessage();
        }
        return result;
    }

    /**
     * ИСПРАВЛЕНО (3.2.5): теперь countFields учитывает config.filters так же,
     * как execute(). Раньше фильтры игнорировались, и если бы кто-то использовал
     * countFields для отчёта с фильтрами — получил бы неверное число записей.
     * Для StatisticsActivity это не критично (там фильтров нет), но метод
     * должен быть консистентным с execute().
     */
    public static int countFields(Context context, StatisticsConfig config) {
        try {
            SupportSQLiteDatabase db = DatabaseHelper.getSupportDatabase(context);
            List<Integer> machineIds = resolveScopeMachines(db, config);
            if (machineIds.isEmpty()) return 0;

            StringBuilder where = new StringBuilder();
            List<Object> args = new ArrayList<>();

            where.append("f.machine_id IN (");
            for (int i = 0; i < machineIds.size(); i++) {
                if (i > 0) where.append(",");
                where.append("?");
                args.add(machineIds.get(i));
            }
            where.append(")");

            if (config.startDate != null) {
                where.append(" AND f.created_at >= ?");
                args.add(config.startDate);
            }
            if (config.endDate != null) {
                where.append(" AND f.created_at <= ?");
                args.add(config.endDate);
            }

            if (config.filters != null && !config.filters.isEmpty()) {
                for (FilterCondition fc : config.filters) {
                    String sub = buildFilterClause(fc, args, context);
                    if (sub != null && !sub.isEmpty()) {
                        where.append(" AND (").append(sub).append(")");
                    }
                }
            }

            try (Cursor c = db.query("SELECT COUNT(*) FROM fields f WHERE " + where,
                    args.toArray())) {
                if (c.moveToFirst()) return c.getInt(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "countFields failed", e);
        }
        return 0;
    }

    private static List<Integer> resolveScopeMachines(SupportSQLiteDatabase db,
                                                       StatisticsConfig config) {
        List<Integer> list = new ArrayList<>();
        try {
            if (config.scopeType == null || config.scopeType == ScopeType.ALL) {
                try (Cursor c = db.query("SELECT id FROM machines")) {
                    while (c.moveToNext()) list.add(c.getInt(0));
                }
            } else if (config.scopeType == ScopeType.MACHINE) {
                if (config.scopeIds != null) {
                    for (Integer id : config.scopeIds) if (id != null) list.add(id);
                }
            } else if (config.scopeType == ScopeType.MACHINE_GROUP) {
                if (config.scopeIds != null && !config.scopeIds.isEmpty()) {
                    StringBuilder in = new StringBuilder();
                    for (int i = 0; i < config.scopeIds.size(); i++) {
                        if (i > 0) in.append(",");
                        in.append(config.scopeIds.get(i));
                    }
                    try (Cursor c = db.query("SELECT id FROM machines WHERE group_id IN (" + in + ")")) {
                        while (c.moveToNext()) list.add(c.getInt(0));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "resolveScope failed", e);
        }
        return list;
    }

    private static String buildGroupExpr(StatisticsConfig config) {
        if (config.groupBy == null) return "'Всего'";
        switch (config.groupBy) {
            case NONE: return "'Всего'";
            case MACHINE: return "m.name";
            case GROUP: return "COALESCE(g.name, '(без группы)')";
            case MONTH: return "strftime('%Y-%m', f.created_at/1000, 'unixepoch')";
            case YEAR: return "strftime('%Y', f.created_at/1000, 'unixepoch')";
            case FIELD:
                if (config.groupByTemplateId == null) return "'Всего'";
                return "json_extract(f.custom_values, '$.\"" + config.groupByTemplateId + "\"')";
        }
        return "'Всего'";
    }

    private static String buildGroupLabel(StatisticsConfig config) {
        if (config.groupBy == null) return "Значение";
        switch (config.groupBy) {
            case NONE: return "Всего";
            case MACHINE: return "Объект";
            case GROUP: return "Группа";
            case MONTH: return "Месяц";
            case YEAR: return "Год";
            case FIELD: return "Значение";
        }
        return "Значение";
    }

    private static String buildMetricExpr(MetricDef metric) {
        if (metric.type == MetricType.COUNT) return "COUNT(*)";
        String fieldExpr = fieldExpr(metric.fieldKey);
        if (fieldExpr == null) return "COUNT(*)";
        switch (metric.type) {
            case SUM: return "SUM(CAST(" + fieldExpr + " AS REAL))";
            case AVG: return "AVG(CAST(" + fieldExpr + " AS REAL))";
            case MIN: return "MIN(CAST(" + fieldExpr + " AS REAL))";
            case MAX: return "MAX(CAST(" + fieldExpr + " AS REAL))";
            default: return "COUNT(*)";
        }
    }

    private static String buildMetricLabel(MetricDef metric, Context context) {
        String base;
        switch (metric.type) {
            case COUNT: base = "Количество"; break;
            case SUM: base = "Сумма"; break;
            case AVG: base = "Среднее"; break;
            case MIN: base = "Мин."; break;
            case MAX: base = "Макс."; break;
            default: base = "Значение";
        }
        if (metric.type != MetricType.COUNT && metric.fieldKey != null) {
            String fieldName = fieldName(metric.fieldKey, context);
            if (fieldName != null) return base + " " + fieldName;
        }
        return base;
    }

    private static String fieldName(String fieldKey, Context context) {
        if (fieldKey == null) return null;
        if (fieldKey.startsWith("template:")) {
            try {
                int templateId = Integer.parseInt(fieldKey.substring("template:".length()));
                for (FieldTemplate t : loadTemplates(context)) {
                    if (t.getId() == templateId) return t.getName();
                }
            } catch (Exception ignored) {}
        } else if ("createdAt".equals(fieldKey)) return "дата";
        else if ("updatedAt".equals(fieldKey)) return "изм.";
        return null;
    }

    private static List<FieldTemplate> loadTemplates(Context context) {
        List<FieldTemplate> list = new ArrayList<>();
        try {
            SupportSQLiteDatabase db = DatabaseHelper.getSupportDatabase(context);
            try (Cursor c = db.query("SELECT * FROM field_templates")) {
                int idIdx = c.getColumnIndexOrThrow("id");
                int nameIdx = c.getColumnIndexOrThrow("name");
                int typeIdx = c.getColumnIndexOrThrow("type");
                int optIdx = c.getColumnIndexOrThrow("options");
                int reqIdx = c.getColumnIndexOrThrow("is_required");
                int sortIdx = c.getColumnIndexOrThrow("sort_order");
                int autoIdx = c.getColumnIndex("is_auto_fill");
                while (c.moveToNext()) {
                    list.add(new FieldTemplate(
                            c.getInt(idIdx),
                            c.getString(nameIdx),
                            c.getString(typeIdx),
                            c.getString(optIdx),
                            c.getInt(reqIdx) == 1,
                            c.getInt(sortIdx),
                            autoIdx != -1 && c.getInt(autoIdx) == 1));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static String fieldExpr(String fieldKey) {
        if (fieldKey == null) return null;
        if (fieldKey.startsWith("template:")) {
            String id = fieldKey.substring("template:".length());
            return "json_extract(f.custom_values, '$.\"" + id + "\"')";
        }
        if ("createdAt".equals(fieldKey)) return "f.created_at";
        if ("updatedAt".equals(fieldKey)) return "f.updated_at";
        return null;
    }

    private static String getTemplateType(Context context, String fieldKey) {
        if (fieldKey == null || !fieldKey.startsWith("template:")) return null;
        try {
            int templateId = Integer.parseInt(fieldKey.substring("template:".length()));
            SupportSQLiteDatabase db = DatabaseHelper.getSupportDatabase(context);
            try (Cursor c = db.query("SELECT type FROM field_templates WHERE id=?",
                    new Object[]{templateId})) {
                if (c.moveToFirst()) return c.getString(0);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String buildFilterClause(FilterCondition fc, List<Object> args, Context context) {
        if (fc == null || fc.fieldKey == null || fc.op == null) return null;

        if ("hasMedia".equals(fc.fieldKey)) {
            String op = "(SELECT COUNT(*) FROM media WHERE field_id = f.id) > 0";
            if (fc.boolValue != null && !fc.boolValue) op = "NOT " + op;
            return op;
        }
        if ("isRead".equals(fc.fieldKey)) {
            if (fc.boolValue == null) return null;
            return "f.is_read = " + (fc.boolValue ? "1" : "0");
        }

        String field = fieldExpr(fc.fieldKey);
        if (field == null) return null;

        switch (fc.op) {
            case EQUALS:
                if (fc.stringValue != null) {
                    args.add(fc.stringValue);
                    return field + " = ?";
                }
                if (fc.numberValue != null) {
                    args.add(fc.numberValue);
                    return "CAST(" + field + " AS REAL) = ?";
                }
                return null;
            case NOT_EQUALS:
                if (fc.stringValue != null) {
                    args.add(fc.stringValue);
                    return "(" + field + " IS NULL OR " + field + " <> ?)";
                }
                if (fc.numberValue != null) {
                    args.add(fc.numberValue);
                    return "CAST(" + field + " AS REAL) <> ?";
                }
                return null;
            case CONTAINS:
                if (fc.stringValue != null) {
                    args.add("%" + fc.stringValue + "%");
                    return field + " LIKE ?";
                }
                return null;
            case NOT_EMPTY:
                return "(" + field + " IS NOT NULL AND " + field + " <> '')";
            case EMPTY:
                return "(" + field + " IS NULL OR " + field + " = '')";
            case GREATER:
                if (fc.numberValue != null) {
                    args.add(fc.numberValue);
                    return "CAST(" + field + " AS REAL) > ?";
                }
                return null;
            case GREATER_EQ:
                if (fc.numberValue != null) {
                    args.add(fc.numberValue);
                    return "CAST(" + field + " AS REAL) >= ?";
                }
                return null;
            case LESS:
                if (fc.numberValue != null) {
                    args.add(fc.numberValue);
                    return "CAST(" + field + " AS REAL) < ?";
                }
                return null;
            case LESS_EQ:
                if (fc.numberValue != null) {
                    args.add(fc.numberValue);
                    return "CAST(" + field + " AS REAL) <= ?";
                }
                return null;
            case BETWEEN:
                return buildBetweenClause(fc, args, context, field);
            case CHECKED:
                return field + " = 'true'";
            case UNCHECKED:
                return "(" + field + " IS NULL OR " + field + " <> 'true')";
            case IN:
                if (fc.listValues != null && !fc.listValues.isEmpty()) {
                    StringBuilder in = new StringBuilder();
                    for (int i = 0; i < fc.listValues.size(); i++) {
                        if (i > 0) in.append(",");
                        in.append("?");
                        args.add(fc.listValues.get(i));
                    }
                    return field + " IN (" + in + ")";
                }
                return null;
        }
        return null;
    }

    private static String buildBetweenClause(FilterCondition fc, List<Object> args,
                                             Context context, String field) {
        if (fc.dateStart == null || fc.dateEnd == null) {
            if (fc.numberValue != null) {
                args.add(fc.numberValue);
                return "CAST(" + field + " AS REAL) = ?";
            }
            return null;
        }

        if ("createdAt".equals(fc.fieldKey) || "updatedAt".equals(fc.fieldKey)) {
            args.add(fc.dateStart);
            args.add(fc.dateEnd);
            return field + " BETWEEN ? AND ?";
        }

        String templateType = getTemplateType(context, fc.fieldKey);

        if ("DATE".equals(templateType)) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
            args.add(sdf.format(new Date(fc.dateStart)));
            args.add(sdf.format(new Date(fc.dateEnd)));
            return field + " BETWEEN ? AND ?";
        }
        if ("DATETIME".equals(templateType)) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US);
            args.add(sdf.format(new Date(fc.dateStart)));
            args.add(sdf.format(new Date(fc.dateEnd)));
            return field + " BETWEEN ? AND ?";
        }
        if (templateType != null) {
            args.add(fc.dateStart);
            args.add(fc.dateEnd);
            return "CAST(" + field + " AS REAL) BETWEEN ? AND ?";
        }

        SimpleDateFormat sdfDate = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
        args.add(fc.dateStart);
        args.add(fc.dateEnd);
        args.add(sdfDate.format(new Date(fc.dateStart)));
        args.add(sdfDate.format(new Date(fc.dateEnd)));
        return "(CAST(" + field + " AS REAL) BETWEEN ? AND ?) OR (" + field + " BETWEEN ? AND ?)";
    }
}