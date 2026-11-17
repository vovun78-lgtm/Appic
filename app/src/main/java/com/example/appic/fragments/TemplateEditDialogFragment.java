package com.example.appic.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.appic.R;
import com.example.appic.adapters.DropdownAdapter;
import com.example.appic.database.models.FieldTemplate;
import com.example.appic.utils.TextCapitalizer;
import com.example.appic.utils.ThemeHelper;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class TemplateEditDialogFragment extends DialogFragment {

    public static final String TAG = "TemplateEditDialogFragment";
    public static final String REQUEST_KEY = "template_edit_result";

    public static final String KEY_NAME = "result_name";
    public static final String KEY_TYPE = "result_type";
    public static final String KEY_OPTIONS = "result_options";
    public static final String KEY_REQUIRED = "result_required";
    public static final String KEY_AUTOFILL = "result_autofill";
    public static final String KEY_IS_EDIT = "result_is_edit";
    public static final String KEY_EDIT_ID = "result_edit_id";

    private static final String ARG_IS_EDIT = "arg_is_edit";
    private static final String ARG_EDIT_ID = "arg_edit_id";
    private static final String ARG_EDIT_NAME = "arg_edit_name";
    private static final String ARG_EDIT_TYPE = "arg_edit_type";
    private static final String ARG_EDIT_OPTIONS = "arg_edit_options";
    private static final String ARG_EDIT_REQUIRED = "arg_edit_required";
    private static final String ARG_EDIT_AUTOFILL = "arg_edit_autofill";

    private static final String STATE_NAME = "state_name";
    private static final String STATE_TYPE_UI = "state_type_ui";
    private static final String STATE_OPTIONS = "state_options";
    private static final String STATE_REQUIRED = "state_required";
    private static final String STATE_AUTOFILL = "state_autofill";

    private static final String[] TYPE_OPTIONS = {
            "Текст", "Число", "Дата", "Дата и время", "Флажок (Да/Нет)", "Выбор из списка",
            "Множественный выбор", "Ссылка (URL)", "Email", "Телефон", "Рейтинг", "Прогресс"
    };

    private TextInputEditText etName;
    private TextInputLayout tilOptions;
    private TextInputEditText etOptions;
    private MaterialAutoCompleteTextView spinnerType;
    private MaterialCheckBox cbRequired;
    private MaterialCheckBox cbAutoFill;

    public static TemplateEditDialogFragment newInstance(@Nullable FieldTemplate template) {
        TemplateEditDialogFragment f = new TemplateEditDialogFragment();
        Bundle args = new Bundle();
        if (template != null) {
            args.putBoolean(ARG_IS_EDIT, true);
            args.putInt(ARG_EDIT_ID, template.getId());
            args.putString(ARG_EDIT_NAME, template.getName());
            args.putString(ARG_EDIT_TYPE, template.getType());
            args.putString(ARG_EDIT_OPTIONS, template.getOptions());
            args.putBoolean(ARG_EDIT_REQUIRED, template.isRequired());
            args.putBoolean(ARG_EDIT_AUTOFILL, template.isAutoFill());
        } else {
            args.putBoolean(ARG_IS_EDIT, false);
        }
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        boolean isEdit = args.getBoolean(ARG_IS_EDIT, false);

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_template, null);
        etName = view.findViewById(R.id.et_template_name);
        tilOptions = view.findViewById(R.id.til_options);
        etOptions = view.findViewById(R.id.et_options);
        spinnerType = view.findViewById(R.id.spinner_type);
        cbRequired = view.findViewById(R.id.cb_required);
        cbAutoFill = view.findViewById(R.id.cb_auto_fill);

        TextCapitalizer.apply(etName);
        TextCapitalizer.apply(etOptions);

        ThemeHelper.applyToInputView(etName);
        ThemeHelper.applyToInputView(etOptions);
        ThemeHelper.applyToInputView(spinnerType);
        ThemeHelper.applyToInputView(cbRequired);
        ThemeHelper.applyToInputView(cbAutoFill);

        spinnerType.setAdapter(new DropdownAdapter(requireContext(), TYPE_OPTIONS));

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else if (isEdit) {
            String name = args.getString(ARG_EDIT_NAME, "");
            String type = args.getString(ARG_EDIT_TYPE, "TEXT");
            String optionsJson = args.getString(ARG_EDIT_OPTIONS, "[]");
            boolean required = args.getBoolean(ARG_EDIT_REQUIRED, false);
            boolean autoFill = args.getBoolean(ARG_EDIT_AUTOFILL, false);

            etName.setText(name);
            cbRequired.setChecked(required);

            String typeUi = getTypeString(type);
            if ("DROPDOWN".equals(type) || "MULTISELECT".equals(type)) {
                tilOptions.setVisibility(View.VISIBLE);
                try {
                    List<String> opts = new Gson().fromJson(optionsJson, List.class);
                    if (opts != null) etOptions.setText(TextUtils.join(", ", opts));
                } catch (Exception ignored) {}
            }
            spinnerType.setText(typeUi, false);
            cbAutoFill.setChecked(autoFill);
            boolean showAutoFill = "Дата".equals(typeUi) || "Дата и время".equals(typeUi);
            cbAutoFill.setVisibility(showAutoFill ? View.VISIBLE : View.GONE);
        } else {
            spinnerType.setText("Текст", false);
            cbAutoFill.setChecked(false);
            cbAutoFill.setVisibility(View.GONE);
        }

        spinnerType.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) hideKeyboard(v);
            return false;
        });

        spinnerType.setOnItemClickListener((parent, v, position, id) -> {
            String selected = TYPE_OPTIONS[position];
            tilOptions.setVisibility(("Выбор из списка".equals(selected)
                    || "Множественный выбор".equals(selected)) ? View.VISIBLE : View.GONE);
            boolean showAutoFill = "Дата".equals(selected) || "Дата и время".equals(selected);
            cbAutoFill.setVisibility(showAutoFill ? View.VISIBLE : View.GONE);
            if (!showAutoFill) cbAutoFill.setChecked(false);
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(isEdit
                        ? R.string.dialog_edit_template_title
                        : R.string.dialog_new_template_title)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(isEdit ? R.string.save : R.string.dialog_add_btn, null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            ((AlertDialog) d).getButton(DialogInterface.BUTTON_POSITIVE)
                    .setOnClickListener(v -> onPositiveClicked());
        });
        return dialog;
    }

    private void restoreState(@NonNull Bundle s) {
        String name = s.getString(STATE_NAME);
        if (name != null) etName.setText(name);
        String typeUi = s.getString(STATE_TYPE_UI);
        if (typeUi != null) {
            spinnerType.setText(typeUi, false);
            tilOptions.setVisibility(("Выбор из списка".equals(typeUi)
                    || "Множественный выбор".equals(typeUi)) ? View.VISIBLE : View.GONE);
            boolean showAutoFill = "Дата".equals(typeUi) || "Дата и время".equals(typeUi);
            cbAutoFill.setVisibility(showAutoFill ? View.VISIBLE : View.GONE);
        }
        String options = s.getString(STATE_OPTIONS);
        if (options != null) etOptions.setText(options);
        cbRequired.setChecked(s.getBoolean(STATE_REQUIRED, false));
        cbAutoFill.setChecked(s.getBoolean(STATE_AUTOFILL, false));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (etName != null) outState.putString(STATE_NAME, etName.getText() != null ? etName.getText().toString() : "");
        if (spinnerType != null) outState.putString(STATE_TYPE_UI, spinnerType.getText().toString());
        if (etOptions != null) outState.putString(STATE_OPTIONS, etOptions.getText() != null ? etOptions.getText().toString() : "");
        if (cbRequired != null) outState.putBoolean(STATE_REQUIRED, cbRequired.isChecked());
        if (cbAutoFill != null) outState.putBoolean(STATE_AUTOFILL, cbAutoFill.isChecked());
    }

    private void onPositiveClicked() {
        String name = etName != null && etName.getText() != null ? etName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), R.string.onboarding_enter_field_name, Toast.LENGTH_SHORT).show();
            return;
        }

        String typeUi = spinnerType.getText().toString();
        String type = getTypeFromUi(typeUi);
        String optionsJson = "[]";
        if ("DROPDOWN".equals(type) || "MULTISELECT".equals(type)) {
            List<String> optionsList = new ArrayList<>();
            String raw = etOptions.getText() != null ? etOptions.getText().toString() : "";
            for (String s : raw.split(",")) {
                if (!s.trim().isEmpty()) optionsList.add(s.trim());
            }
            optionsJson = new Gson().toJson(optionsList);
        }
        boolean required = cbRequired.isChecked();
        boolean autoFill = cbAutoFill.isChecked();

        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        boolean isEdit = args.getBoolean(ARG_IS_EDIT, false);
        int editId = args.getInt(ARG_EDIT_ID, 0);

        Bundle result = new Bundle();
        result.putString(KEY_NAME, name);
        result.putString(KEY_TYPE, type);
        result.putString(KEY_OPTIONS, optionsJson);
        result.putBoolean(KEY_REQUIRED, required);
        result.putBoolean(KEY_AUTOFILL, autoFill);
        result.putBoolean(KEY_IS_EDIT, isEdit);
        result.putInt(KEY_EDIT_ID, editId);

        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        dismiss();
    }

    private void hideKeyboard(View view) {
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private String getTypeFromUi(String uiType) {
        switch (uiType) {
            case "Число": return "NUMBER";
            case "Дата": return "DATE";
            case "Дата и время": return "DATETIME";
            case "Флажок (Да/Нет)": return "CHECKBOX";
            case "Выбор из списка": return "DROPDOWN";
            case "Множественный выбор": return "MULTISELECT";
            case "Ссылка (URL)": return "URL";
            case "Email": return "EMAIL";
            case "Телефон": return "PHONE";
            case "Рейтинг": return "RATING";
            case "Прогресс": return "PROGRESS";
            default: return "TEXT";
        }
    }

    private String getTypeString(String type) {
        switch (type) {
            case "TEXT": return "Текст";
            case "NUMBER": return "Число";
            case "DATE": return "Дата";
            case "DATETIME": return "Дата и время";
            case "CHECKBOX": return "Флажок (Да/Нет)";
            case "DROPDOWN": return "Выбор из списка";
            case "MULTISELECT": return "Множественный выбор";
            case "URL": return "Ссылка (URL)";
            case "EMAIL": return "Email";
            case "PHONE": return "Телефон";
            case "RATING": return "Рейтинг";
            case "PROGRESS": return "Прогресс";
            default: return "Текст";
        }
    }
}