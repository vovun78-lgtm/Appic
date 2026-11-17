package com.example.appic.utils;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.appic.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Единая точка создания диалогов в приложении.
 * Использует MaterialAlertDialogBuilder → подхватывает Appic_MaterialAlertDialog
 * из themes.xml (закругления 16dp, кнопки colorPrimary активной темы).
 */
public final class DialogHelper {

    private DialogHelper() {}

    public interface OnYes { void run(); }
    public interface OnItem { void onClick(int index); }
    public interface OnInput { void onInput(@NonNull String text); }

    // ==================== СООБЩЕНИЕ ====================

    public static void showMessage(Context context, String title, String message) {
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    // ==================== ПОДТВЕРЖДЕНИЕ ====================

    public static void showConfirm(Context context, String title, String message,
                                   @Nullable OnYes onYes) {
        showConfirm(context, title, message, onYes, null, null);
    }

    public static void showConfirm(Context context, String title, String message,
                                   @Nullable OnYes onYes, @Nullable Runnable onNo) {
        showConfirm(context, title, message, onYes, onNo, null);
    }

    /**
     * Полная версия:
     *   onYes     — на «Да»,
     *   onNo      — на «Нет»,
     *   onDismiss — ТОЛЬКО если диалог закрыт не кнопкой (системный «Назад»,
     *               тап вне диалога и т.п.). Если пользователь нажал «Да» или
     *               «Нет» — onDismiss НЕ вызывается.
     *
     * Раньше onDismiss срабатывал на любое закрытие — это приводило к тому,
     * что после «Да» в OnboardingActivity.showSystemFieldTypeChangeWarning
     * флаг isSaving сбрасывался, и пользователь мог запустить второе
     * сохранение параллельно первому.
     */
    public static void showConfirm(Context context, String title, String message,
                                   @Nullable OnYes onYes, @Nullable Runnable onNo,
                                   @Nullable Runnable onDismiss) {
        if (context == null) return;

        final boolean[] handled = {false};

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.yes, (d, w) -> {
                    handled[0] = true;
                    if (onYes != null) onYes.run();
                })
                .setNegativeButton(R.string.no, (d, w) -> {
                    handled[0] = true;
                    if (onNo != null) onNo.run();
                });

        if (onDismiss != null) {
            b.setOnDismissListener(d -> {
                if (!handled[0]) onDismiss.run();
            });
        }

        b.show();
    }

    // ==================== СПИСОК ДЕЙСТВИЙ ====================

    public static void showItems(Context context, String title, String[] items,
                                 @Nullable OnItem onItem) {
        if (context == null || items == null) return;
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setItems(items, (d, which) -> {
                    if (onItem != null) onItem.onClick(which);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ==================== ВВОД ====================

    public static void showInput(Context context, String title, String hint,
                                 @Nullable String initialValue,
                                 @Nullable OnInput onResult) {
        showInputInternal(context, title, hint, initialValue,
                InputType.TYPE_CLASS_TEXT, false, onResult);
    }

    public static void showInputNumber(Context context, String title, String hint,
                                       @Nullable String initialValue,
                                       @Nullable OnInput onResult) {
        showInputInternal(context, title, hint, initialValue,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
                false, onResult);
    }

    public static void showInputMultiline(Context context, String title, String hint,
                                          @Nullable String initialValue,
                                          @Nullable OnInput onResult) {
        showInputInternal(context, title, hint, initialValue,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                true, onResult);
    }

    private static void showInputInternal(Context context, String title, String hint,
                                          @Nullable String initialValue, int inputType,
                                          boolean multiline,
                                          @Nullable OnInput onResult) {
        if (context == null) return;

        TextInputEditText et = new TextInputEditText(context);
        et.setInputType(inputType);
        if (multiline) {
            et.setMinLines(3);
            et.setGravity(Gravity.TOP | Gravity.START);
        } else {
            et.setSingleLine(true);
        }
        if (initialValue != null) et.setText(initialValue);
        ThemeHelper.applyToEditText(et);

        TextInputLayout til = new TextInputLayout(context);
        if (hint != null) til.setHint(hint);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.addView(et);

        FrameLayout wrapper = new FrameLayout(context);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        wrapper.setPadding(pad, 0, pad, 0);
        wrapper.addView(til);

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(wrapper)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    if (onResult != null) {
                        CharSequence text = et.getText();
                        onResult.onInput(text != null ? text.toString() : "");
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ==================== СПЕЦИАЛЬНЫЕ ====================

    /**
     * Диалог с кастомным View (для сложных форм).
     * Используется редко — например, в OnboardingActivity для формы
     * «Новое поле»/«Редактировать поле», где нужен кастомный layout.
     */
    public static MaterialAlertDialogBuilder custom(Context context, String title,
                                                    android.view.View view) {
        return new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(view);
    }
}