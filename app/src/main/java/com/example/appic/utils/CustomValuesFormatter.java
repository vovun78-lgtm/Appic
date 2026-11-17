package com.example.appic.utils;

import com.example.appic.database.models.FieldTemplate;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Форматирует JSON custom_values в человекочитаемое превью
 * для карточек записей (item_field.xml → tv_custom_preview).
 *
 * Используется в FieldsAdapter и MachineDetailsAdapter, чтобы не дублировать логику.
 */
public final class CustomValuesFormatter {

    private static final int MAX_FIELDS = 2;

    private CustomValuesFormatter() {}

    /**
     * @return строка вида "Рейтинг: ★★★☆☆ · Прогресс: 75%"
     *         или null, если нечего показывать.
     */
    public static String format(String json, List<FieldTemplate> templates) {
        if (json == null || json.trim().isEmpty() || json.equals("{}")) return null;
        if (templates == null || templates.isEmpty()) return null;

        try {
            Type mapType = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> values = new Gson().fromJson(json, mapType);
            if (values == null || values.isEmpty()) return null;

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (FieldTemplate template : templates) {
                String key = String.valueOf(template.getId());
                String value = values.get(key);
                if (value == null || value.trim().isEmpty() || value.equals("false")) continue;

                if (count > 0) sb.append(" · ");

                String displayValue = value;
                if ("RATING".equals(template.getType())) {
                    try {
                        float rating = Float.parseFloat(value);
                        if (rating <= 0f) continue;
                        StringBuilder stars = new StringBuilder();
                        for (int i = 1; i <= 5; i++) {
                            stars.append(i <= rating ? "★" : "☆");
                        }
                        displayValue = stars.toString();
                    } catch (Exception ignored) {}
                } else if ("PROGRESS".equals(template.getType())) {
                    try {
                        int progress = (int) Float.parseFloat(value);
                        if (progress <= 0) continue;
                        displayValue = progress + "%";
                    } catch (Exception ignored) {}
                }

                sb.append(template.getName()).append(": ").append(displayValue);
                count++;
                if (count >= MAX_FIELDS) break;
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}