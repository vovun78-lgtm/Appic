package com.example.appic.utils;

import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.example.appic.R;

public final class TextCapitalizer {

    private TextCapitalizer() {}

    public static void apply(EditText editText) {
        if (editText == null) return;
        if (Boolean.TRUE.equals(editText.getTag(R.id.tag_text_capitalizer_applied))) return;

        int inputType = editText.getInputType();
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        int klass = inputType & InputType.TYPE_MASK_CLASS;

        boolean isText = (klass == InputType.TYPE_CLASS_TEXT);
        boolean skip = (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
                || (variation == InputType.TYPE_TEXT_VARIATION_URI)
                || (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD)
                || (variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                || (variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
                || (variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);

        if (isText && !skip) {
            int newType = inputType;
            if ((newType & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) == 0
                    && (newType & InputType.TYPE_TEXT_FLAG_CAP_WORDS) == 0
                    && (newType & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) == 0) {
                newType |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
            }
            if (newType != inputType) editText.setInputType(newType);
        }

        editText.addTextChangedListener(new TextWatcher() {
            private boolean selfChange = false;

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (selfChange) return;
                if (s == null || s.length() == 0) return;

                java.util.List<Integer> positions = new java.util.ArrayList<>();
                boolean capitalizeNext = true;
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (capitalizeNext && Character.isLowerCase(c)) {
                        positions.add(i);
                        capitalizeNext = false;
                    } else if (!Character.isWhitespace(c)) {
                        capitalizeNext = false;
                    }
                    if (c == '.' || c == '!' || c == '?' || c == '\n') {
                        capitalizeNext = true;
                    }
                }
                if (positions.isEmpty()) return;

                selfChange = true;
                try {
                    int selStart = Selection.getSelectionStart(s);
                    int selEnd = Selection.getSelectionEnd(s);
                    for (int i = positions.size() - 1; i >= 0; i--) {
                        int pos = positions.get(i);
                        s.replace(pos, pos + 1,
                                String.valueOf(Character.toUpperCase(s.charAt(pos))));
                    }
                    if (selStart >= 0 && selEnd >= 0) {
                        Selection.setSelection(s,
                                Math.min(selStart, s.length()),
                                Math.min(selEnd, s.length()));
                    }
                } finally {
                    selfChange = false;
                }
            }
        });

        editText.setTag(R.id.tag_text_capitalizer_applied, Boolean.TRUE);
    }

    public static void applyToAll(View view) {
        if (view == null) return;
        if (view instanceof EditText) { apply((EditText) view); return; }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyToAll(group.getChildAt(i));
        }
    }

    /** Капитализирует первую букву текста. */
    public static String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        char first = text.charAt(0);
        return Character.isLowerCase(first)
                ? Character.toUpperCase(first) + text.substring(1)
                : text;
    }

    /** Капитализирует первую букву текста и после . ! ? \n (fallback при сохранении). */
    public static String capitalizeSentences(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text);
        boolean capitalizeNext = true;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (capitalizeNext && Character.isLowerCase(c)) {
                sb.setCharAt(i, Character.toUpperCase(c));
                capitalizeNext = false;
            } else if (!Character.isWhitespace(c)) {
                capitalizeNext = false;
            }
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                capitalizeNext = true;
            }
        }
        return sb.toString();
    }

    /**
     * Безопасная обрезка строки с многоточием.
     * Не разрывает суррогатную пару (эмодзи, редкие иероглифы),
     * иначе в конце получится «квадратик» или «вопросик».
     *
     * @param text      исходный текст (может быть null)
     * @param maxLength максимальная длина в символах Java (char). Если текст
     *                  длиннее — обрезается до maxLength с добавлением «…».
     * @return обрезанный текст, «…», если текст длиннее, или null/оригинал.
     */
    public static String safeTruncate(String text, int maxLength) {
        if (text == null) return null;
        if (maxLength <= 0) return text;
        if (text.length() <= maxLength) return text;

        int cut = maxLength;
        // Суррогатная пара: high surrogate + low surrogate = 2 char.
        // Если последний символ — high surrogate, сдвигаем границу на 1 назад,
        // чтобы не разорвать пару.
        if (cut > 0 && Character.isHighSurrogate(text.charAt(cut - 1))) {
            cut--;
        }
        return text.substring(0, cut) + "…";
    }
}