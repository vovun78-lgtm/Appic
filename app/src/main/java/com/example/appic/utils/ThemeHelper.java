package com.example.appic.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.example.appic.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

public class ThemeHelper {

    private static final String TAG = "ThemeHelper";

    public static final String KEY_TEXT_SIZE_TITLE = "text_size_title";
    public static final String KEY_TEXT_SIZE_BODY = "text_size_body";
    public static final String KEY_TEXT_SIZE_INPUT = "text_size_input";
    public static final String KEY_TOOLBAR_TEXT_SIZE = "toolbar_text_size";
    public static final String KEY_CARD_MARGIN = "card_margin";
    public static final String KEY_CARD_PADDING = "card_padding";
    public static final String KEY_CARD_CORNER_RADIUS = "card_corner_radius";
    public static final String KEY_CARD_ELEVATION = "card_elevation";

    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_COLOR_THEME = "color_theme";

    public static final int DARK_MODE_LIGHT = 0;
    public static final int DARK_MODE_DARK = 1;
    public static final int DARK_MODE_SYSTEM = 2;

    private static final String DEFAULT_TEXT_SIZE_TITLE = "18";
    private static final String DEFAULT_TEXT_SIZE_BODY = "14";
    private static final String DEFAULT_TEXT_SIZE_INPUT = "16";
    private static final String DEFAULT_TOOLBAR_TEXT_SIZE = "20";
    private static final String DEFAULT_CARD_MARGIN = "8";
    private static final String DEFAULT_CARD_PADDING = "12";
    private static final String DEFAULT_CARD_CORNER_RADIUS = "8";
    private static final String DEFAULT_CARD_ELEVATION = "2";

    private static SharedPreferences getPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static String getStringSafe(Context context, String key, String defaultValue) {
        try {
            Object raw = getPrefs(context).getAll().get(key);
            if (raw == null) return defaultValue;
            if (raw instanceof String) return (String) raw;
            return String.valueOf(raw);
        } catch (Exception e) {
            Log.w(TAG, "getStringSafe failed for key=" + key, e);
            return defaultValue;
        }
    }

    public static void migrateLegacyPrefs(Context context) {
        // No-op: приложение новое, миграция не требуется.
        // Метод оставлен для обратной совместимости вызовов из SettingsFragment.
    }

    // ======================== ЦВЕТОВЫЕ ТЕМЫ ========================
    public static String getColorTheme(Context context) {
        return getPrefs(context).getString(KEY_COLOR_THEME, "default");
    }

    public static void setColorTheme(Context context, String theme) {
        getPrefs(context).edit().putString(KEY_COLOR_THEME, theme).apply();
    }

    public static int getThemeResId(Context context) {
        String theme = getColorTheme(context);
        switch (theme) {
            case "orange": return R.style.AppTheme_Orange;
            case "space": return R.style.AppTheme_Space;
            case "summer": return R.style.AppTheme_Summer;
            case "autumn": return R.style.AppTheme_Autumn;
            case "festival": return R.style.AppTheme_Festival;
            default: return R.style.AppTheme;
        }
    }

    // ======================== ЧТЕНИЕ ЦВЕТОВ ИЗ ТЕМЫ ========================
    public static int getThemeColor(Context context, int attrResId) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attrResId, typedValue, true)) {
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return typedValue.data;
            }
            if (typedValue.resourceId != 0) {
                return ContextCompat.getColor(context, typedValue.resourceId);
            }
        }
        return 0;
    }

    // ======================== ГЕТТЕРЫ РАЗМЕРОВ ========================
    public static float getTextSizeTitleSp(Context context) {
        return parseFloat(getStringSafe(context, KEY_TEXT_SIZE_TITLE, DEFAULT_TEXT_SIZE_TITLE), 18f);
    }

    public static float getTextSizeBodySp(Context context) {
        return parseFloat(getStringSafe(context, KEY_TEXT_SIZE_BODY, DEFAULT_TEXT_SIZE_BODY), 14f);
    }

    public static float getTextSizeInputSp(Context context) {
        return parseFloat(getStringSafe(context, KEY_TEXT_SIZE_INPUT, DEFAULT_TEXT_SIZE_INPUT), 16f);
    }

    public static float getToolbarTextSizeSp(Context context) {
        return parseFloat(getStringSafe(context, KEY_TOOLBAR_TEXT_SIZE, DEFAULT_TOOLBAR_TEXT_SIZE), 20f);
    }

    public static int getCardMarginPx(Context context) {
        return dpToPx(context, parseInt(getStringSafe(context, KEY_CARD_MARGIN, DEFAULT_CARD_MARGIN), 8));
    }

    public static int getCardPaddingPx(Context context) {
        return dpToPx(context, parseInt(getStringSafe(context, KEY_CARD_PADDING, DEFAULT_CARD_PADDING), 12));
    }

    public static int getCardCornerRadiusPx(Context context) {
        return dpToPx(context, parseInt(getStringSafe(context, KEY_CARD_CORNER_RADIUS, DEFAULT_CARD_CORNER_RADIUS), 8));
    }

    public static int getCardElevationPx(Context context) {
        return dpToPx(context, parseInt(getStringSafe(context, KEY_CARD_ELEVATION, DEFAULT_CARD_ELEVATION), 2));
    }

    // ======================== РЕЖИМ ТЕМЫ ========================
    public static int getDarkMode(Context context) {
        String raw = getStringSafe(context, KEY_DARK_MODE, String.valueOf(DARK_MODE_LIGHT));
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return DARK_MODE_LIGHT;
        }
    }

    public static boolean isDarkMode(Context context) {
        int mode = getDarkMode(context);
        if (mode == DARK_MODE_SYSTEM) {
            int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return nightMode == Configuration.UI_MODE_NIGHT_YES;
        }
        return mode == DARK_MODE_DARK;
    }

    public static void setDarkMode(Context context, int mode) {
        // ИСПРАВЛЕНО: apply() вместо commit() — не блокируем UI-поток диском
        getPrefs(context).edit().putString(KEY_DARK_MODE, String.valueOf(mode)).apply();
        switch (mode) {
            case DARK_MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case DARK_MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case DARK_MODE_SYSTEM:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public static void resetToDefaults(Context context) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putString(KEY_TEXT_SIZE_TITLE, DEFAULT_TEXT_SIZE_TITLE);
        editor.putString(KEY_TEXT_SIZE_BODY, DEFAULT_TEXT_SIZE_BODY);
        editor.putString(KEY_TEXT_SIZE_INPUT, DEFAULT_TEXT_SIZE_INPUT);
        editor.putString(KEY_TOOLBAR_TEXT_SIZE, DEFAULT_TOOLBAR_TEXT_SIZE);
        editor.putString(KEY_CARD_MARGIN, DEFAULT_CARD_MARGIN);
        editor.putString(KEY_CARD_PADDING, DEFAULT_CARD_PADDING);
        editor.putString(KEY_CARD_CORNER_RADIUS, DEFAULT_CARD_CORNER_RADIUS);
        editor.putString(KEY_CARD_ELEVATION, DEFAULT_CARD_ELEVATION);
        editor.putString(KEY_COLOR_THEME, "default");
        editor.apply();

        setDarkMode(context, DARK_MODE_LIGHT);
    }

    // ======================== ПРИМЕНЕНИЕ К VIEW ========================
    public static void applyToTextViewPrimary(TextView textView) {
        if (textView == null) return;
        Context ctx = textView.getContext();
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, getTextSizeTitleSp(ctx));
        textView.setTextColor(getThemeColor(ctx, R.attr.textPrimaryColor));
    }

    public static void applyToTextViewSecondary(TextView textView) {
        if (textView == null) return;
        Context ctx = textView.getContext();
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, getTextSizeBodySp(ctx));
        textView.setTextColor(getThemeColor(ctx, R.attr.textSecondaryColor));
    }

    public static void applyToEditText(EditText editText) {
        if (editText == null) return;
        Context context = editText.getContext();
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, getTextSizeInputSp(context));
        editText.setTextColor(getThemeColor(context, R.attr.inputTextColor));
    }

    public static void applyToInputView(View view) {
        if (view == null) return;
        Context ctx = view.getContext();
        float sp = getTextSizeInputSp(ctx);

        if (view instanceof TextInputLayout) {
            TextInputLayout til = (TextInputLayout) view;
            EditText et = til.getEditText();
            if (et != null) {
                et.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            }
            TextView helper = til.findViewById(com.google.android.material.R.id.textinput_helper_text);
            if (helper != null) {
                helper.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(10f, sp - 4f));
            }
            TextView error = til.findViewById(com.google.android.material.R.id.textinput_error);
            if (error != null) {
                error.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(10f, sp - 4f));
            }
        } else if (view instanceof TextView) {
            ((TextView) view).setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        }
    }

    public static void applyToMaterialCardView(MaterialCardView cardView, boolean isGroup) {
        if (cardView == null) return;
        Context ctx = cardView.getContext();
        cardView.setRadius(getCardCornerRadiusPx(ctx));
        cardView.setCardElevation(getCardElevationPx(ctx));
        if (isGroup) {
            cardView.setCardBackgroundColor(getThemeColor(ctx, R.attr.colorGroupBackground));
        } else {
            cardView.setCardBackgroundColor(getThemeColor(ctx, R.attr.cardBackground));
        }
        if (cardView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) cardView.getLayoutParams();
            int margin = getCardMarginPx(ctx);
            params.setMargins(margin, margin, margin, margin);
            cardView.setLayoutParams(params);
        }
    }

    public static void applyCardPadding(View contentView) {
        if (contentView == null) return;
        int padding = getCardPaddingPx(contentView.getContext());
        contentView.setPadding(padding, padding, padding, padding);
    }

    public static void applyToToolbar(Toolbar toolbar) {
        if (toolbar == null) return;
        Context ctx = toolbar.getContext();
        float textSize = getToolbarTextSizeSp(ctx);
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
                break;
            }
        }
    }

    // ======================== УТИЛИТЫ ========================
    private static int parseInt(String value, int def) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return def;
        }
    }

    private static float parseFloat(String value, float def) {
        try {
            return Float.parseFloat(value);
        } catch (Exception e) {
            return def;
        }
    }

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }
}