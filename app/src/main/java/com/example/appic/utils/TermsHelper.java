package com.example.appic.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class TermsHelper {

    private static final String KEY_APP_NAME = "term_app_name";
    private static final String KEY_TERM_MACHINE_SINGLE = "term_machine_single";
    private static final String KEY_TERM_MACHINE_PLURAL = "term_machine_plural";
    private static final String KEY_TERM_FIELD_SINGLE = "term_field_single";
    private static final String KEY_TERM_FIELD_PLURAL = "term_field_plural";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";

    // Ключи для типов основных полей
    private static final String KEY_TITLE_TYPE = "term_title_type";
    private static final String KEY_TITLE_OPTIONS = "term_title_options";
    private static final String KEY_TITLE_AUTO_FILL = "term_title_auto_fill";
    
    private static final String KEY_CONTENT_TYPE = "term_content_type";
    private static final String KEY_CONTENT_OPTIONS = "term_content_options";
    private static final String KEY_CONTENT_AUTO_FILL = "term_content_auto_fill";

    // Значения по умолчанию
    private static final String DEFAULT_APP_NAME = "Приложение";
    private static final String DEFAULT_MACHINE_SINGLE = "Объект";
    private static final String DEFAULT_MACHINE_PLURAL = "Объекты";
    private static final String DEFAULT_FIELD_SINGLE = "Запись";
    private static final String DEFAULT_FIELD_PLURAL = "Записи";

    private static SharedPreferences getPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean isOnboardingDone(Context context) {
        return getPrefs(context).getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public static void setOnboardingDone(Context context) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();
    }

    public static String getAppName(Context context) {
        return getPrefs(context).getString(KEY_APP_NAME, DEFAULT_APP_NAME);
    }

    public static String getMachineSingle(Context context) {
        return getPrefs(context).getString(KEY_TERM_MACHINE_SINGLE, DEFAULT_MACHINE_SINGLE);
    }

    public static String getMachinePlural(Context context) {
        return getPrefs(context).getString(KEY_TERM_MACHINE_PLURAL, DEFAULT_MACHINE_PLURAL);
    }

    public static String getFieldSingle(Context context) {
        return getPrefs(context).getString(KEY_TERM_FIELD_SINGLE, DEFAULT_FIELD_SINGLE);
    }

    public static String getFieldPlural(Context context) {
        return getPrefs(context).getString(KEY_TERM_FIELD_PLURAL, DEFAULT_FIELD_PLURAL);
    }

    // Геттеры и сеттеры для типов и автозаполнения
    public static String getTitleType(Context context) {
        return getPrefs(context).getString(KEY_TITLE_TYPE, "TEXT");
    }

    public static String getTitleOptions(Context context) {
        return getPrefs(context).getString(KEY_TITLE_OPTIONS, "[]");
    }

    public static boolean isTitleAutoFill(Context context) {
        return getPrefs(context).getBoolean(KEY_TITLE_AUTO_FILL, false);
    }

    public static String getContentType(Context context) {
        return getPrefs(context).getString(KEY_CONTENT_TYPE, "TEXT");
    }

    public static String getContentOptions(Context context) {
        return getPrefs(context).getString(KEY_CONTENT_OPTIONS, "[]");
    }

    public static boolean isContentAutoFill(Context context) {
        return getPrefs(context).getBoolean(KEY_CONTENT_AUTO_FILL, false);
    }

    // Основной метод сохранения
    public static void saveTerms(Context context, String appName, String machineSingle, String machinePlural,
                                 String fieldSingle, String fieldPlural,
                                 String titleType, String titleOptions, boolean titleAutoFill,
                                 String contentType, String contentOptions, boolean contentAutoFill) {
        getPrefs(context).edit()
                .putString(KEY_APP_NAME, appName)
                .putString(KEY_TERM_MACHINE_SINGLE, machineSingle)
                .putString(KEY_TERM_MACHINE_PLURAL, machinePlural)
                .putString(KEY_TERM_FIELD_SINGLE, fieldSingle)
                .putString(KEY_TERM_FIELD_PLURAL, fieldPlural)
                .putString(KEY_TITLE_TYPE, titleType)
                .putString(KEY_TITLE_OPTIONS, titleOptions)
                .putBoolean(KEY_TITLE_AUTO_FILL, titleAutoFill)
                .putString(KEY_CONTENT_TYPE, contentType)
                .putString(KEY_CONTENT_OPTIONS, contentOptions)
                .putBoolean(KEY_CONTENT_AUTO_FILL, contentAutoFill)
                .putBoolean(KEY_ONBOARDING_DONE, true)
                .apply();
    }
}