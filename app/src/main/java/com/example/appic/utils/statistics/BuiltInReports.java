package com.example.appic.utils.statistics;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.appic.database.DatabaseHelper;
import com.example.appic.database.models.FieldTemplate;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class BuiltInReports {

    private static final String TAG = "BuiltInReports";

    public static class BuiltIn {
        public String id;
        public String title;
        public String description;
        public StatisticsConfig config;

        public BuiltIn(String id, String title, String description, StatisticsConfig config) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.config = config;
        }
    }

    private BuiltInReports() {}

    public static List<BuiltIn> build(Context context) {
        List<BuiltIn> list = new ArrayList<>();

        list.add(new BuiltIn("by_month", "Записи по месяцам",
                "Динамика записей по месяцам",
                byMonthConfig()));

        list.add(new BuiltIn("top_machines", "Топ объектов по записям",
                "Объекты с наибольшим количеством записей",
                topMachinesConfig()));

        list.add(new BuiltIn("by_group", "Записи по группам",
                "Распределение записей по группам",
                byGroupConfig()));

        List<FieldTemplate> templates = loadTemplates(context);
        for (FieldTemplate t : templates) {
            if ("DROPDOWN".equals(t.getType())) {
                list.add(new BuiltIn("field_" + t.getId(),
                        "Распределение по «" + t.getName() + "»",
                        "Количество записей по значениям поля",
                        byFieldConfig(t.getId())));
            }
            if ("RATING".equals(t.getType())) {
                list.add(new BuiltIn("avg_rating_" + t.getId(),
                        "Средний «" + t.getName() + "» по объектам",
                        "Средняя оценка по каждому объекту",
                        avgByMachineConfig(t.getId())));
            }
            if ("PROGRESS".equals(t.getType())) {
                list.add(new BuiltIn("avg_progress_" + t.getId(),
                        "Средний «" + t.getName() + "» по объектам",
                        "Средний прогресс по каждому объекту",
                        avgByMachineConfig(t.getId())));
            }
            if ("NUMBER".equals(t.getType())) {
                list.add(new BuiltIn("sum_field_" + t.getId(),
                        "Сумма «" + t.getName() + "» по объектам",
                        "Суммарное значение по каждому объекту",
                        sumByMachineConfig(t.getId())));
            }
        }

        return list;
    }

    private static StatisticsConfig byMonthConfig() {
        StatisticsConfig c = new StatisticsConfig();
        c.name = "Записи по месяцам";
        c.groupBy = GroupByType.MONTH;
        c.metrics.add(new MetricDef(MetricType.COUNT));
        c.viewType = ViewType.LINE;
        c.sortDesc = false;
        c.limit = 60;
        return c;
    }

    private static StatisticsConfig topMachinesConfig() {
        StatisticsConfig c = new StatisticsConfig();
        c.name = "Топ объектов";
        c.groupBy = GroupByType.MACHINE;
        c.metrics.add(new MetricDef(MetricType.COUNT));
        c.viewType = ViewType.BAR;
        c.sortDesc = true;
        c.limit = 10;
        return c;
    }

    private static StatisticsConfig byGroupConfig() {
        StatisticsConfig c = new StatisticsConfig();
        c.name = "Записи по группам";
        c.groupBy = GroupByType.GROUP;
        c.metrics.add(new MetricDef(MetricType.COUNT));
        c.viewType = ViewType.PIE;
        c.sortDesc = true;
        c.limit = 20;
        return c;
    }

    private static StatisticsConfig byFieldConfig(int templateId) {
        StatisticsConfig c = new StatisticsConfig();
        c.groupBy = GroupByType.FIELD;
        c.groupByTemplateId = templateId;
        c.metrics.add(new MetricDef(MetricType.COUNT));
        c.viewType = ViewType.PIE;
        c.sortDesc = true;
        c.limit = 20;
        return c;
    }

    private static StatisticsConfig avgByMachineConfig(int templateId) {
        StatisticsConfig c = new StatisticsConfig();
        c.groupBy = GroupByType.MACHINE;
        c.metrics.add(new MetricDef(MetricType.COUNT));
        c.metrics.add(new MetricDef(MetricType.AVG, "template:" + templateId));
        c.viewType = ViewType.TABLE;
        c.sortDesc = true;
        c.limit = 50;
        return c;
    }

    private static StatisticsConfig sumByMachineConfig(int templateId) {
        StatisticsConfig c = new StatisticsConfig();
        c.groupBy = GroupByType.MACHINE;
        c.metrics.add(new MetricDef(MetricType.COUNT));
        c.metrics.add(new MetricDef(MetricType.SUM, "template:" + templateId));
        c.viewType = ViewType.TABLE;
        c.sortDesc = true;
        c.limit = 50;
        return c;
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
        } catch (Exception e) {
            Log.e(TAG, "loadTemplates failed", e);
        }
        return list;
    }

    /** Дефолтный период "За всё время" (без ограничений). */
    public static Long[] periodAllTime() {
        return new Long[]{null, null};
    }

    public static Long[] periodThisMonth() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        c.add(Calendar.MONTH, 1);
        long end = c.getTimeInMillis() - 1;
        return new Long[]{start, end};
    }

    public static Long[] periodLastMonth() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, -1);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        c.add(Calendar.MONTH, 1);
        long end = c.getTimeInMillis() - 1;
        return new Long[]{start, end};
    }

    public static Long[] periodThisYear() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_YEAR, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        c.add(Calendar.YEAR, 1);
        long end = c.getTimeInMillis() - 1;
        return new Long[]{start, end};
    }

    public static Long[] periodLast30Days() {
        long end = System.currentTimeMillis();
        long start = end - 30L * 24 * 60 * 60 * 1000;
        return new Long[]{start, end};
    }
}