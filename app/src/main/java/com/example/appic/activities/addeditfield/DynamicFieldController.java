package com.example.appic.activities.addeditfield;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import com.example.appic.R;
import com.example.appic.adapters.DropdownAdapter;
import com.example.appic.database.models.FieldTemplate;
import com.example.appic.utils.TextCapitalizer;
import com.example.appic.utils.ThemeHelper;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DynamicFieldController {

    private static final String TAG = "DynamicFieldController";

    public interface ValidationListener {
        void onValidationError(String message);
    }

    private final Context context;
    private final LinearLayout systemContainer;
    private final LinearLayout dynamicContainer;
    private final String titleType;
    private final String contentType;

    private final List<FieldTemplate> templates = new ArrayList<>();
    private final Map<String, View> dynamicViews = new HashMap<>();
    private final Map<View, MultiselectState> multiselectStates = new HashMap<>();

    private View titleView;
    private View contentView;

    private static class MultiselectState {
        final String[] options;
        final boolean[] checked;
        MultiselectState(String[] options) {
            this.options = options;
            this.checked = new boolean[options.length];
        }
    }

    public DynamicFieldController(Context context,
                                  LinearLayout systemContainer,
                                  LinearLayout dynamicContainer,
                                  String titleType,
                                  String contentType) {
        this.context = context;
        this.systemContainer = systemContainer;
        this.dynamicContainer = dynamicContainer;
        this.titleType = titleType;
        this.contentType = contentType;
    }

    public void setTemplates(List<FieldTemplate> list) {
        templates.clear();
        templates.addAll(list);
    }

    public List<FieldTemplate> getTemplates() { return templates; }

    public View getTitleView() { return titleView; }
    public View getContentView() { return contentView; }

    public void build(String titleHint, String contentHint,
                      String titleOptions, String contentOptions,
                      boolean titleAutoFill, boolean contentAutoFill,
                      boolean isNewField) {
        buildSystem(titleHint, contentHint, titleOptions, contentOptions, titleAutoFill, contentAutoFill, isNewField);
        buildDynamic(isNewField);
    }

    private void buildSystem(String titleHint, String contentHint,
                             String titleOptions, String contentOptions,
                             boolean titleAutoFill, boolean contentAutoFill,
                             boolean isNewField) {
        systemContainer.removeAllViews();
        multiselectStates.clear();

        titleView = createInputView(titleType, titleHint, titleOptions, titleAutoFill, isNewField);
        if (titleView != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            titleView.setLayoutParams(lp);
            systemContainer.addView(titleView);
        }

        contentView = createInputView(contentType, contentHint, contentOptions, contentAutoFill, isNewField);
        if (contentView != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            contentView.setLayoutParams(lp);
            systemContainer.addView(contentView);
        }
    }

    private void buildDynamic(boolean isNewField) {
        dynamicContainer.removeAllViews();
        dynamicViews.clear();

        if (templates.isEmpty()) return;

        for (FieldTemplate template : templates) {
            String type = template.getType();
            String label = template.getName()
                    + (template.isRequired() && !"CHECKBOX".equals(type) ? " *" : "");

            View fieldView = createInputView(type, label, template.getOptions(), template.isAutoFill(), isNewField);
            if (fieldView != null) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(12);
                fieldView.setLayoutParams(lp);
                dynamicContainer.addView(fieldView);
                dynamicViews.put(String.valueOf(template.getId()), fieldView);
            }
        }
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    private View createInputView(String type, String label, String optionsJson,
                                 boolean isAutoFill, boolean isNewField) {
        if ("CHECKBOX".equals(type)) {
            TextInputLayout til = (TextInputLayout) LayoutInflater.from(context)
                    .inflate(R.layout.dynamic_checkbox_field, dynamicContainer, false);
            MaterialCheckBox cb = til.findViewById(R.id.checkbox_input);
            if (cb != null) {
                cb.setText(label);
                ThemeHelper.applyToInputView(cb);
            }
            return til;

        } else if ("DROPDOWN".equals(type)) {
            TextInputLayout til = (TextInputLayout) LayoutInflater.from(context)
                    .inflate(R.layout.dynamic_dropdown_field, dynamicContainer, false);
            til.setHint(label);
            MaterialAutoCompleteTextView autoComplete = til.findViewById(R.id.dropdown_input);
            if (autoComplete != null) {
                try {
                    Type listType = new TypeToken<List<String>>(){}.getType();
                    List<String> options = new Gson().fromJson(optionsJson, listType);
                    if (options != null && !options.isEmpty()) {
                        autoComplete.setAdapter(new DropdownAdapter(context, options));
                    }
                } catch (Exception e) { Log.e(TAG, "Error parsing dropdown options", e); }
            }
            ThemeHelper.applyToInputView(til);
            return til;

        } else if ("MULTISELECT".equals(type)) {
            TextInputLayout til = (TextInputLayout) LayoutInflater.from(context)
                    .inflate(R.layout.dynamic_multiselect_field, dynamicContainer, false);
            til.setHint(label);
            MaterialAutoCompleteTextView autoComplete = til.findViewById(R.id.multiselect_input);
            if (autoComplete != null) {
                try {
                    Type listType = new TypeToken<List<String>>(){}.getType();
                    List<String> options = new Gson().fromJson(optionsJson, listType);
                    if (options != null && !options.isEmpty()) {
                        String[] optionsArray = options.toArray(new String[0]);
                        MultiselectState state = new MultiselectState(optionsArray);
                        multiselectStates.put(autoComplete, state);

                        autoComplete.setOnClickListener(v -> {
                            new MaterialAlertDialogBuilder(context)
                                    .setTitle(label)
                                    .setMultiChoiceItems(state.options, state.checked,
                                            (dialog, which, isChecked) -> {
                                                state.checked[which] = isChecked;
                                            })
                                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                                        List<String> selected = new ArrayList<>();
                                        for (int i = 0; i < state.options.length; i++) {
                                            if (state.checked[i]) selected.add(state.options[i]);
                                        }
                                        autoComplete.setText(TextUtils.join(", ", selected), false);
                                    })
                                    .setNegativeButton(R.string.cancel, null)
                                    .show();
                        });
                    }
                } catch (Exception e) { Log.e(TAG, "Error parsing multiselect options", e); }
            }
            ThemeHelper.applyToInputView(til);
            return til;

        } else if ("RATING".equals(type)) {
            TextInputLayout til = (TextInputLayout) LayoutInflater.from(context)
                    .inflate(R.layout.dynamic_rating_field, dynamicContainer, false);
            TextView labelView = til.findViewById(R.id.rating_label);
            if (labelView != null) labelView.setText(label);

            RatingBar ratingBar = til.findViewById(R.id.rating_bar);
            ImageButton clearBtn = til.findViewById(R.id.btn_clear_rating);
            if (ratingBar != null && clearBtn != null) {
                ratingBar.setOnRatingBarChangeListener((bar, rating, fromUser) ->
                        clearBtn.setVisibility(rating > 0 ? View.VISIBLE : View.GONE));
                clearBtn.setOnClickListener(v -> {
                    ratingBar.setRating(0);
                    clearBtn.setVisibility(View.GONE);
                });
            }
            return til;

        } else if ("PROGRESS".equals(type)) {
            TextInputLayout til = (TextInputLayout) LayoutInflater.from(context)
                    .inflate(R.layout.dynamic_progress_field, dynamicContainer, false);
            TextView labelView = til.findViewById(R.id.progress_label);
            if (labelView != null) labelView.setText(label);
            return til;

        } else {
            TextInputLayout til = (TextInputLayout) LayoutInflater.from(context)
                    .inflate(R.layout.dynamic_text_field, dynamicContainer, false);
            til.setHint(label);
            TextInputEditText et = til.findViewById(R.id.text_input);
            if (et != null) {
                if ("NUMBER".equals(type)) {
                    et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                } else if ("DATE".equals(type)) {
                    et.setFocusable(false);
                    et.setClickable(true);
                    et.setOnClickListener(v -> showDatePicker(et, til));
                    if (isAutoFill && isNewField) {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                        et.setText(sdf.format(new Date()));
                    }
                } else if ("DATETIME".equals(type)) {
                    et.setFocusable(false);
                    et.setClickable(true);
                    et.setOnClickListener(v -> showDateTimePicker(et, til));
                    if (isAutoFill && isNewField) {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                        et.setText(sdf.format(new Date()));
                    }
                } else if ("URL".equals(type)) {
                    et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                } else if ("EMAIL".equals(type)) {
                    et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                } else if ("PHONE".equals(type)) {
                    et.setInputType(InputType.TYPE_CLASS_PHONE);
                } else if ("TEXT".equals(type)) {
                    TextCapitalizer.apply(et);
                }
            }
            ThemeHelper.applyToInputView(til);
            return til;
        }
    }

    private void showDatePicker(TextInputEditText et, TextInputLayout til) {
        Calendar cal = Calendar.getInstance();
        try {
            String current = et.getText().toString();
            if (!TextUtils.isEmpty(current)) {
                Date parsed = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(current);
                if (parsed != null) cal.setTime(parsed);
            }
        } catch (Exception ignored) {}

        new DatePickerDialog(context, (view, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day);
            et.setText(new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    .format(selected.getTime()));
            til.setHintEnabled(false);
            til.setHintEnabled(true);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showDateTimePicker(TextInputEditText et, TextInputLayout til) {
        Calendar cal = Calendar.getInstance();
        try {
            String current = et.getText().toString();
            if (!TextUtils.isEmpty(current)) {
                Date parsed = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).parse(current);
                if (parsed != null) cal.setTime(parsed);
            }
        } catch (Exception ignored) {}

        final Calendar finalCal = cal;
        new DatePickerDialog(context, (view, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day);
            new TimePickerDialog(context, (tv, hour, minute) -> {
                selected.set(Calendar.HOUR_OF_DAY, hour);
                selected.set(Calendar.MINUTE, minute);
                et.setText(new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        .format(selected.getTime()));
                til.setHintEnabled(false);
                til.setHintEnabled(true);
            }, finalCal.get(Calendar.HOUR_OF_DAY), finalCal.get(Calendar.MINUTE), true).show();
        }, finalCal.get(Calendar.YEAR), finalCal.get(Calendar.MONTH), finalCal.get(Calendar.DAY_OF_MONTH)).show();
    }

    public String extractFromView(View view) {
        if (view instanceof TextInputLayout) {
            TextInputLayout til = (TextInputLayout) view;
            EditText inner = til.getEditText();
            if (inner instanceof MaterialAutoCompleteTextView) {
                return ((MaterialAutoCompleteTextView) inner).getText().toString();
            } else if (inner != null) {
                return inner.getText().toString();
            } else {
                MaterialCheckBox cb = til.findViewById(R.id.checkbox_input);
                if (cb != null) return String.valueOf(cb.isChecked());
                RatingBar ratingBar = til.findViewById(R.id.rating_bar);
                if (ratingBar != null) return String.valueOf(ratingBar.getRating());
                Slider slider = til.findViewById(R.id.progress_slider);
                if (slider != null) return String.valueOf((int) slider.getValue());
            }
        }
        return "";
    }

    public void setValueToView(View view, String value) {
        if (value == null || view == null) return;
        if (view instanceof TextInputLayout) {
            TextInputLayout til = (TextInputLayout) view;
            EditText inner = til.getEditText();
            if (inner instanceof MaterialAutoCompleteTextView) {
                try { ((MaterialAutoCompleteTextView) inner).setText(value, false); } catch (Exception ignored) {}
                MultiselectState state = multiselectStates.get(inner);
                if (state != null) {
                    Arrays.fill(state.checked, false);
                    if (!TextUtils.isEmpty(value)) {
                        for (String s : value.split(",")) {
                            String trimmed = s.trim();
                            if (trimmed.isEmpty()) continue;
                            for (int i = 0; i < state.options.length; i++) {
                                if (state.options[i].equals(trimmed)) {
                                    state.checked[i] = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            } else if (inner != null) {
                try { inner.setText(value); } catch (Exception ignored) {}
            } else {
                MaterialCheckBox cb = til.findViewById(R.id.checkbox_input);
                if (cb != null) { cb.setChecked("true".equalsIgnoreCase(value)); return; }
                RatingBar rb = til.findViewById(R.id.rating_bar);
                if (rb != null) {
                    try { rb.setRating(Float.parseFloat(value)); } catch (Exception ignored) {}
                    ImageButton clearBtn = til.findViewById(R.id.btn_clear_rating);
                    if (clearBtn != null) {
                        clearBtn.setVisibility(rb.getRating() > 0 ? View.VISIBLE : View.GONE);
                    }
                    return;
                }
                Slider sl = til.findViewById(R.id.progress_slider);
                if (sl != null) { try { sl.setValue(Float.parseFloat(value)); } catch (Exception ignored) {} }
            }
        }
    }

    public void populateSystemValues(String titleValue, String contentValue) {
        if (titleValue != null) setValueToView(titleView, titleValue);
        if (contentValue != null) setValueToView(contentView, contentValue);
    }

    public void populateDynamicValues(String json) {
        if (TextUtils.isEmpty(json)) return;
        try {
            Type mapType = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> values = new Gson().fromJson(json, mapType);
            if (values == null) return;
            for (FieldTemplate template : templates) {
                String key = String.valueOf(template.getId());
                String value = values.get(key);
                if (value == null) continue;
                setValueToView(dynamicViews.get(key), value);
            }
        } catch (Exception e) { Log.e(TAG, "Error parsing custom values", e); }
    }

    public String getTitleValue() { return extractFromView(titleView); }
    public String getContentValue() { return extractFromView(contentView); }
    public String getTitleType() { return titleType; }
    public String getContentType() { return contentType; }

    public String gatherCustomValues() {
        Map<String, String> values = new HashMap<>();
        for (FieldTemplate template : templates) {
            String key = String.valueOf(template.getId());
            values.put(key, extractFromView(dynamicViews.get(key)));
        }
        return new Gson().toJson(values);
    }

    public boolean validate(ValidationListener listener) {
        String titleVal = getTitleValue();
        if (!"CHECKBOX".equals(titleType) && TextUtils.isEmpty(titleVal)) {
            listener.onValidationError(context.getString(R.string.validation_fill_title,
                    com.example.appic.utils.TermsHelper.getFieldSingle(context)));
            return false;
        }

        for (FieldTemplate template : templates) {
            if (!template.isRequired()) continue;
            String type = template.getType();
            String key = String.valueOf(template.getId());
            String val = extractFromView(dynamicViews.get(key));

            if ("CHECKBOX".equals(type)) {
                if (!"true".equalsIgnoreCase(val)) {
                    listener.onValidationError(context.getString(R.string.validation_check_box, template.getName()));
                    return false;
                }
                continue;
            }
            if ("RATING".equals(type)) {
                float f; try { f = Float.parseFloat(val); } catch (Exception e) { f = 0f; }
                if (f <= 0f) {
                    listener.onValidationError(context.getString(R.string.validation_set_rating, template.getName()));
                    return false;
                }
                continue;
            }
            if ("PROGRESS".equals(type)) {
                int p; try { p = Integer.parseInt(val); } catch (Exception e) { p = 0; }
                if (p <= 0) {
                    listener.onValidationError(context.getString(R.string.validation_set_progress, template.getName()));
                    return false;
                }
                continue;
            }
            if (TextUtils.isEmpty(val)) {
                listener.onValidationError(context.getString(R.string.validation_fill_required, template.getName()));
                return false;
            }
        }
        return true;
    }

    public void refreshInputSizes() {
        refreshContainer(systemContainer);
        refreshContainer(dynamicContainer);
    }

    /**
     * ИСПРАВЛЕНО (п.6): для RATING и PROGRESS-полей ThemeHelper.applyToInputView(child)
     * не достаёт до TextView-метки (getEditText() == null, метка — отдельный TextView
     * внутри LinearLayout, а не `textinput_helper_text`). В результате размер шрифта
     * метки не обновлялся при смене настройки. Теперь обновляем явно.
     */
    private void refreshContainer(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextInputLayout) {
                ThemeHelper.applyToInputView(child);

                MaterialCheckBox cb = child.findViewById(R.id.checkbox_input);
                if (cb != null) ThemeHelper.applyToInputView(cb);

                // ИСПРАВЛЕНО (п.6): метки RATING / PROGRESS
                TextView ratingLabel = child.findViewById(R.id.rating_label);
                if (ratingLabel != null) {
                    ThemeHelper.applyToTextViewSecondary(ratingLabel);
                }
                TextView progressLabel = child.findViewById(R.id.progress_label);
                if (progressLabel != null) {
                    ThemeHelper.applyToTextViewSecondary(progressLabel);
                }
            }
        }
    }
}