package com.example.appic.activities;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.appic.R;
import com.example.appic.adapters.DropdownAdapter;
import com.example.appic.database.DatabaseClient;
import com.example.appic.database.models.FieldTemplate;
import com.example.appic.fragments.TemplateEditDialogFragment;
import com.example.appic.utils.DialogHelper;
import com.example.appic.utils.TermsHelper;
import com.example.appic.utils.TextCapitalizer;
import com.example.appic.utils.ThemeHelper;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OnboardingActivity extends AppCompatActivity {

    private static final String TAG = "Onboarding";
    private static final int TOTAL_STEPS = 3;
    private static final String KEY_CURRENT_STEP = "current_step";
    private static final String KEY_TEMPLATES_JSON = "templates_json";
    private static final String KEY_ORIGINAL_TEMPLATES_JSON = "original_templates_json";

    private static final String KEY_STATE_APP_NAME = "state_app_name";
    private static final String KEY_STATE_MACHINE_SINGLE = "state_machine_single";
    private static final String KEY_STATE_MACHINE_PLURAL = "state_machine_plural";
    private static final String KEY_STATE_FIELD_SINGLE = "state_field_single";
    private static final String KEY_STATE_FIELD_PLURAL = "state_field_plural";
    private static final String KEY_STATE_TITLE_TYPE_UI = "state_title_type_ui";
    private static final String KEY_STATE_TITLE_OPTIONS = "state_title_options";
    private static final String KEY_STATE_TITLE_AUTOFILL = "state_title_autofill";
    private static final String KEY_STATE_CONTENT_TYPE_UI = "state_content_type_ui";
    private static final String KEY_STATE_CONTENT_OPTIONS = "state_content_options";
    private static final String KEY_STATE_CONTENT_AUTOFILL = "state_content_autofill";

    private static final String KEY_IS_SAVING = "is_saving_state";

    private int currentStep = 0;
    private boolean isEditMode;
    private boolean isSaving = false;

    private ViewFlipper viewFlipper;
    private ProgressBar stepProgress;
    private TextView tvStepCount;
    private Button btnBack, btnNext;

    private TextInputEditText etAppName;
    private EditText etMachineSingle, etMachinePlural, etFieldSingle, etFieldPlural;
    private MaterialAutoCompleteTextView spinnerTitleType, spinnerContentType;
    private TextInputLayout tilTitleOptions, tilContentOptions;
    private TextInputEditText etTitleOptions, etContentOptions;
    private MaterialCheckBox cbTitleAutoFill, cbContentAutoFill;
    private RecyclerView recyclerTemplates;
    private Button btnAddTemplate;

    private List<FieldTemplate> templates = new ArrayList<>();
    private List<FieldTemplate> originalTemplates = new ArrayList<>();
    private TemplateAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private DatabaseClient dbClient;

    private static final String[] DEFAULT_VALUES = {
            "Объект", "Объекты", "Запись", "Записи"
    };

    private static final String[] TYPE_OPTIONS = {
            "Текст", "Число", "Дата", "Дата и время", "Флажок (Да/Нет)", "Выбор из списка",
            "Множественный выбор", "Ссылка (URL)", "Email", "Телефон", "Рейтинг", "Прогресс"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeHelper.getThemeResId(this));
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null
                && savedInstanceState.getBoolean(KEY_IS_SAVING, false)
                && TermsHelper.isOnboardingDone(this)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        setContentView(R.layout.activity_onboarding);

        dbClient = DatabaseClient.getInstance(this);
        isEditMode = TermsHelper.isOnboardingDone(this);

        viewFlipper = findViewById(R.id.view_flipper);
        stepProgress = findViewById(R.id.step_progress);
        tvStepCount = findViewById(R.id.tv_step_count);
        btnBack = findViewById(R.id.btn_back);
        btnNext = findViewById(R.id.btn_next);

        etAppName = findViewById(R.id.et_app_name);
        etMachineSingle = findViewById(R.id.et_machine_single);
        etMachinePlural = findViewById(R.id.et_machine_plural);
        etFieldSingle = findViewById(R.id.et_field_single);
        etFieldPlural = findViewById(R.id.et_field_plural);

        spinnerTitleType = findViewById(R.id.spinner_title_type);
        spinnerContentType = findViewById(R.id.spinner_content_type);
        tilTitleOptions = findViewById(R.id.til_title_options);
        tilContentOptions = findViewById(R.id.til_content_options);
        etTitleOptions = findViewById(R.id.et_title_options);
        etContentOptions = findViewById(R.id.et_content_options);
        cbTitleAutoFill = findViewById(R.id.cb_title_auto_fill);
        cbContentAutoFill = findViewById(R.id.cb_content_auto_fill);

        recyclerTemplates = findViewById(R.id.recycler_templates);
        btnAddTemplate = findViewById(R.id.btn_add_template);

        TextCapitalizer.apply(etAppName);
        TextCapitalizer.apply(etMachineSingle);
        TextCapitalizer.apply(etMachinePlural);
        TextCapitalizer.apply(etFieldSingle);
        TextCapitalizer.apply(etFieldPlural);
        TextCapitalizer.apply(etTitleOptions);
        TextCapitalizer.apply(etContentOptions);

        setupTypeSpinners();
        setupRecyclerView();

        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_TEMPLATES_JSON)) {
            restoreFromBundle(savedInstanceState);
        } else if (isEditMode) {
            loadFromDb();
        } else {
            setDefaults();
        }

        if (savedInstanceState != null) {
            int savedStep = savedInstanceState.getInt(KEY_CURRENT_STEP, 0);
            if (savedStep > 0 && savedStep < TOTAL_STEPS) {
                for (int i = 0; i < savedStep; i++) viewFlipper.showNext();
                currentStep = savedStep;
            }
        }

        btnAddTemplate.setOnClickListener(v -> {
            if (templates.size() >= 10) {
                Toast.makeText(this, R.string.onboarding_max_fields, Toast.LENGTH_SHORT).show();
                return;
            }
            showTemplateDialog(null);
        });

        btnNext.setOnClickListener(v -> goNext());
        btnBack.setOnClickListener(v -> goBack());

        getSupportFragmentManager().setFragmentResultListener(
                TemplateEditDialogFragment.REQUEST_KEY, this, this::onTemplateEditResult);

        // ИСПРАВЛЕНО (3.1.2): раньше системный «Назад» на шагах 2–3 просто
        // закрывал Activity, и весь введённый текст терялся. Теперь перехватываем
        // back: на шаге > 0 — goBack(), на шаге 0 — подтверждение выхода.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isSaving) return;
                if (currentStep > 0) {
                    goBack();
                    return;
                }
                if (isEditMode) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    return;
                }
                DialogHelper.showConfirm(OnboardingActivity.this,
                        getString(R.string.unsaved_changes_title),
                        getString(R.string.onboarding_exit_confirm),
                        () -> {
                            setEnabled(false);
                            getOnBackPressedDispatcher().onBackPressed();
                        });
            }
        });

        setupTextWatchers();
        updateStepUI();
        applyInputSizes();
    }

    private void restoreFromBundle(Bundle savedInstanceState) {
        try {
            Type listType = new TypeToken<List<FieldTemplate>>(){}.getType();
            Gson gson = new Gson();
            List<FieldTemplate> restored = gson.fromJson(
                    savedInstanceState.getString(KEY_TEMPLATES_JSON), listType);
            if (restored != null) {
                templates.clear();
                templates.addAll(restored);
            }
            List<FieldTemplate> restoredOrig = gson.fromJson(
                    savedInstanceState.getString(KEY_ORIGINAL_TEMPLATES_JSON), listType);
            if (restoredOrig != null) {
                originalTemplates.clear();
                originalTemplates.addAll(restoredOrig);
            }

            String appName = savedInstanceState.getString(KEY_STATE_APP_NAME);
            etAppName.setText(appName != null ? appName : getString(R.string.app_name));

            String machineSingle = savedInstanceState.getString(KEY_STATE_MACHINE_SINGLE);
            String machinePlural = savedInstanceState.getString(KEY_STATE_MACHINE_PLURAL);
            String fieldSingle = savedInstanceState.getString(KEY_STATE_FIELD_SINGLE);
            String fieldPlural = savedInstanceState.getString(KEY_STATE_FIELD_PLURAL);
            etMachineSingle.setText(machineSingle != null ? machineSingle : "");
            etMachinePlural.setText(machinePlural != null ? machinePlural : "");
            etFieldSingle.setText(fieldSingle != null ? fieldSingle : "");
            etFieldPlural.setText(fieldPlural != null ? fieldPlural : "");

            String titleTypeUi = savedInstanceState.getString(KEY_STATE_TITLE_TYPE_UI);
            String titleOptionsText = savedInstanceState.getString(KEY_STATE_TITLE_OPTIONS);
            boolean titleAutoFill = savedInstanceState.getBoolean(KEY_STATE_TITLE_AUTOFILL, false);
            if (titleTypeUi != null) {
                spinnerTitleType.setText(titleTypeUi, false);
                updateMainFieldVisibility(titleTypeUi, tilTitleOptions, cbTitleAutoFill);
            }
            if (titleOptionsText != null) etTitleOptions.setText(titleOptionsText);
            cbTitleAutoFill.setChecked(titleAutoFill);

            String contentTypeUi = savedInstanceState.getString(KEY_STATE_CONTENT_TYPE_UI);
            String contentOptionsText = savedInstanceState.getString(KEY_STATE_CONTENT_OPTIONS);
            boolean contentAutoFill = savedInstanceState.getBoolean(KEY_STATE_CONTENT_AUTOFILL, false);
            if (contentTypeUi != null) {
                spinnerContentType.setText(contentTypeUi, false);
                updateMainFieldVisibility(contentTypeUi, tilContentOptions, cbContentAutoFill);
            }
            if (contentOptionsText != null) etContentOptions.setText(contentOptionsText);
            cbContentAutoFill.setChecked(contentAutoFill);

            adapter.notifyDataSetChanged();
        } catch (Exception e) {
            Log.e(TAG, "restoreFromBundle failed", e);
        }
    }

    private void loadFromDb() {
        etAppName.setText(TermsHelper.getAppName(this));
        etMachineSingle.setText(TermsHelper.getMachineSingle(this));
        etMachinePlural.setText(TermsHelper.getMachinePlural(this));
        etFieldSingle.setText(TermsHelper.getFieldSingle(this));
        etFieldPlural.setText(TermsHelper.getFieldPlural(this));

        setupMainFieldType(spinnerTitleType, TermsHelper.getTitleType(this),
                TermsHelper.getTitleOptions(this), tilTitleOptions, etTitleOptions,
                cbTitleAutoFill, TermsHelper.isTitleAutoFill(this));
        setupMainFieldType(spinnerContentType, TermsHelper.getContentType(this),
                TermsHelper.getContentOptions(this), tilContentOptions, etContentOptions,
                cbContentAutoFill, TermsHelper.isContentAutoFill(this));

        new Thread(() -> {
            try {
                List<FieldTemplate> loaded = dbClient.fieldTemplateDao().getAll();
                originalTemplates.addAll(loaded);
                templates.addAll(loaded);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    adapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load templates", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, R.string.onboarding_templates_load_error,
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void setDefaults() {
        etAppName.setText(getString(R.string.app_name));
        etMachineSingle.setText(DEFAULT_VALUES[0]);
        etMachinePlural.setText(DEFAULT_VALUES[1]);
        etFieldSingle.setText(DEFAULT_VALUES[2]);
        etFieldPlural.setText(DEFAULT_VALUES[3]);

        spinnerTitleType.setText("Текст", false);
        spinnerContentType.setText("Текст", false);
        cbTitleAutoFill.setChecked(false);
        cbContentAutoFill.setChecked(false);
    }

    private void setupRecyclerView() {
        adapter = new TemplateAdapter();
        recyclerTemplates.setLayoutManager(new LinearLayoutManager(this));
        recyclerTemplates.setNestedScrollingEnabled(false);
        recyclerTemplates.setAdapter(adapter);

        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean isLongPressDragEnabled() { return false; }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAbsoluteAdapterPosition();
                int toPos = target.getAbsoluteAdapterPosition();
                if (fromPos == RecyclerView.NO_POSITION
                        || toPos == RecyclerView.NO_POSITION) return false;
                Collections.swap(templates, fromPos, toPos);
                adapter.notifyItemMoved(fromPos, toPos);
                return true;
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                for (int i = 0; i < templates.size(); i++) {
                    templates.get(i).setSortOrder(i);
                }
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerTemplates);
    }

    private void setupTypeSpinners() {
        spinnerTitleType.setAdapter(new DropdownAdapter(this, TYPE_OPTIONS));
        spinnerContentType.setAdapter(new DropdownAdapter(this, TYPE_OPTIONS));

        spinnerTitleType.setOnItemClickListener((parent, view, position, id) ->
                updateMainFieldVisibility(TYPE_OPTIONS[position], tilTitleOptions, cbTitleAutoFill));
        spinnerContentType.setOnItemClickListener((parent, view, position, id) ->
                updateMainFieldVisibility(TYPE_OPTIONS[position], tilContentOptions, cbContentAutoFill));
    }

    private void updateMainFieldVisibility(String selectedTypeUi,
                                            TextInputLayout tilOptions,
                                            MaterialCheckBox cbAutoFill) {
        boolean isDate = "Дата".equals(selectedTypeUi) || "Дата и время".equals(selectedTypeUi);
        cbAutoFill.setVisibility(isDate ? View.VISIBLE : View.GONE);
        if (!isDate) cbAutoFill.setChecked(false);

        boolean isList = "Выбор из списка".equals(selectedTypeUi)
                || "Множественный выбор".equals(selectedTypeUi);
        tilOptions.setVisibility(isList ? View.VISIBLE : View.GONE);
    }

    private void setupMainFieldType(MaterialAutoCompleteTextView spinner, String type,
                                     String optionsJson, TextInputLayout tilOptions,
                                     TextInputEditText etOptions,
                                     MaterialCheckBox cbAutoFill, boolean autoFill) {
        String uiType = getTypeString(type);
        spinner.setText(uiType, false);
        cbAutoFill.setChecked(autoFill);

        boolean isDate = "DATE".equals(type) || "DATETIME".equals(type);
        cbAutoFill.setVisibility(isDate ? View.VISIBLE : View.GONE);

        boolean isList = "DROPDOWN".equals(type) || "MULTISELECT".equals(type);
        tilOptions.setVisibility(isList ? View.VISIBLE : View.GONE);
        if (isList && optionsJson != null) {
            try {
                List<String> opts = new Gson().fromJson(optionsJson, List.class);
                if (opts != null) etOptions.setText(TextUtils.join(", ", opts));
            } catch (Exception ignored) {}
        }
    }

    private String getTypeFromSpinner(MaterialAutoCompleteTextView spinner) {
        return getTypeFromUi(spinner.getText().toString());
    }

    private void setupTextWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateNextButtonState(); }
        };
        etAppName.addTextChangedListener(watcher);
        etMachineSingle.addTextChangedListener(watcher);
        etMachinePlural.addTextChangedListener(watcher);
        etFieldSingle.addTextChangedListener(watcher);
        etFieldPlural.addTextChangedListener(watcher);
    }

    private void updateNextButtonState() {
        boolean enabled = true;
        switch (currentStep) {
            case 0:
                enabled = !TextUtils.isEmpty(etMachineSingle.getText())
                        && !TextUtils.isEmpty(etMachinePlural.getText());
                break;
            case 1:
                enabled = !TextUtils.isEmpty(etFieldSingle.getText())
                        && !TextUtils.isEmpty(etFieldPlural.getText());
                break;
            case 2:
                enabled = true;
                break;
        }
        btnNext.setEnabled(enabled && !isSaving);
        btnNext.setAlpha(enabled ? 1f : 0.4f);
    }

    private void goNext() {
        if (!btnNext.isEnabled()) return;
        if (currentStep == TOTAL_STEPS - 1) {
            saveAll();
            return;
        }
        viewFlipper.setInAnimation(this, R.anim.slide_in_right);
        viewFlipper.setOutAnimation(this, R.anim.slide_out_left);
        currentStep++;
        viewFlipper.showNext();
        updateStepUI();
    }

    private void goBack() {
        if (currentStep == 0) return;
        viewFlipper.setInAnimation(this, R.anim.slide_in_left);
        viewFlipper.setOutAnimation(this, R.anim.slide_out_right);
        currentStep--;
        viewFlipper.showPrevious();
        updateStepUI();
    }

    private void updateStepUI() {
        tvStepCount.setText("Шаг " + (currentStep + 1) + " из " + TOTAL_STEPS);
        stepProgress.setProgress(currentStep + 1);
        btnBack.setVisibility(currentStep == 0 ? View.INVISIBLE : View.VISIBLE);
        btnNext.setText(currentStep == TOTAL_STEPS - 1
                ? (isEditMode ? getString(R.string.save) : "Начать работу")
                : "Далее");
        updateNextButtonState();
    }

    private void applyInputSizes() {
        ThemeHelper.applyToInputView(etAppName);
        ThemeHelper.applyToInputView(etMachineSingle);
        ThemeHelper.applyToInputView(etMachinePlural);
        ThemeHelper.applyToInputView(etFieldSingle);
        ThemeHelper.applyToInputView(etFieldPlural);
        ThemeHelper.applyToInputView(spinnerTitleType);
        ThemeHelper.applyToInputView(spinnerContentType);
        ThemeHelper.applyToInputView(etTitleOptions);
        ThemeHelper.applyToInputView(etContentOptions);
        ThemeHelper.applyToInputView(cbTitleAutoFill);
        ThemeHelper.applyToInputView(cbContentAutoFill);

        if (recyclerTemplates != null) {
            for (int i = 0; i < recyclerTemplates.getChildCount(); i++) {
                View child = recyclerTemplates.getChildAt(i);
                if (child == null) continue;
                View tvName = child.findViewById(R.id.tv_field_name);
                View tvType = child.findViewById(R.id.tv_field_type);
                View tvRequired = child.findViewById(R.id.tv_field_required);
                if (tvName instanceof TextView) ThemeHelper.applyToTextViewPrimary((TextView) tvName);
                if (tvType instanceof TextView) ThemeHelper.applyToTextViewSecondary((TextView) tvType);
                if (tvRequired instanceof TextView) ThemeHelper.applyToTextViewSecondary((TextView) tvRequired);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyInputSizes();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CURRENT_STEP, currentStep);
        Gson gson = new Gson();
        try {
            outState.putString(KEY_TEMPLATES_JSON, gson.toJson(templates));
            outState.putString(KEY_ORIGINAL_TEMPLATES_JSON, gson.toJson(originalTemplates));
        } catch (Exception e) {
            Log.w(TAG, "saveInstanceState: failed to serialize templates", e);
        }

        outState.putString(KEY_STATE_APP_NAME, etAppName.getText().toString());
        outState.putString(KEY_STATE_MACHINE_SINGLE, etMachineSingle.getText().toString());
        outState.putString(KEY_STATE_MACHINE_PLURAL, etMachinePlural.getText().toString());
        outState.putString(KEY_STATE_FIELD_SINGLE, etFieldSingle.getText().toString());
        outState.putString(KEY_STATE_FIELD_PLURAL, etFieldPlural.getText().toString());

        outState.putString(KEY_STATE_TITLE_TYPE_UI, spinnerTitleType.getText().toString());
        outState.putString(KEY_STATE_TITLE_OPTIONS, etTitleOptions.getText().toString());
        outState.putBoolean(KEY_STATE_TITLE_AUTOFILL, cbTitleAutoFill.isChecked());

        outState.putString(KEY_STATE_CONTENT_TYPE_UI, spinnerContentType.getText().toString());
        outState.putString(KEY_STATE_CONTENT_OPTIONS, etContentOptions.getText().toString());
        outState.putBoolean(KEY_STATE_CONTENT_AUTOFILL, cbContentAutoFill.isChecked());

        outState.putBoolean(KEY_IS_SAVING, isSaving);
    }

    private void saveAll() {
        if (isSaving) return;
        isSaving = true;
        btnNext.setEnabled(false);

        String newTitleType = getTypeFromSpinner(spinnerTitleType);
        String newContentType = getTypeFromSpinner(spinnerContentType);

        boolean titleTypeChanged = isEditMode && !TermsHelper.getTitleType(this).equals(newTitleType);
        boolean contentTypeChanged = isEditMode && !TermsHelper.getContentType(this).equals(newContentType);

        if (titleTypeChanged || contentTypeChanged) {
            new Thread(() -> {
                try {
                    boolean hasTitleData = titleTypeChanged && hasDataInColumn("title");
                    boolean hasContentData = contentTypeChanged && hasDataInColumn("text");

                    if (hasTitleData || hasContentData) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                isSaving = false; btnNext.setEnabled(true); return;
                            }
                            showSystemFieldTypeChangeWarning(hasTitleData, hasContentData,
                                    newTitleType, newContentType);
                        });
                    } else {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                isSaving = false; btnNext.setEnabled(true); return;
                            }
                            performSaveAll(newTitleType, newContentType);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "saveAll: type check failed", e);
                    runOnUiThread(() -> {
                        isSaving = false;
                        btnNext.setEnabled(true);
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(this, R.string.onboarding_data_check_error,
                                Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        } else {
            performSaveAll(newTitleType, newContentType);
        }
    }

    private void showSystemFieldTypeChangeWarning(boolean hasTitleData, boolean hasContentData,
                                                   String newTitleType, String newContentType) {
        StringBuilder message = new StringBuilder();
        message.append(getString(R.string.onboarding_system_change_base)).append("\n\n");
        if (hasTitleData) {
            message.append(getString(R.string.onboarding_system_change_field,
                    TermsHelper.getFieldSingle(this))).append("\n");
        }
        if (hasContentData) {
            message.append(getString(R.string.onboarding_system_change_content)).append("\n");
        }
        message.append("\n").append(getString(R.string.onboarding_system_change_confirm));

        DialogHelper.showConfirm(this,
                getString(R.string.onboarding_type_change_title),
                message.toString(),
                () -> new Thread(() -> {
                    try {
                        if (hasTitleData) clearDataInColumn("title");
                        if (hasContentData) clearDataInColumn("text");
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                isSaving = false; btnNext.setEnabled(true); return;
                            }
                            performSaveAll(newTitleType, newContentType);
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to clear column data", e);
                        runOnUiThread(() -> {
                            isSaving = false;
                            btnNext.setEnabled(true);
                            if (isFinishing() || isDestroyed()) return;
                            Toast.makeText(this, R.string.onboarding_data_clear_error,
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                }).start(),
                () -> {
                    isSaving = false;
                    btnNext.setEnabled(true);
                },
                () -> {
                    isSaving = false;
                    btnNext.setEnabled(true);
                });
    }

    private void performSaveAll(String newTitleType, String newContentType) {
        // ИСПРАВЛЕНО (3.1.3): раньше appName всегда перезаписывался на
        // R.string.app_name («Аппик»), и пользователь не мог задать своё имя.
        // Теперь берём значение из et_app_name (с дефолтом на R.string.app_name).
        String appName = etAppName.getText().toString().trim();
        if (TextUtils.isEmpty(appName)) appName = getString(R.string.app_name);

        String mSingle = TextCapitalizer.capitalizeSentences(etMachineSingle.getText().toString().trim());
        String mPlural = TextCapitalizer.capitalizeSentences(etMachinePlural.getText().toString().trim());
        String fSingle = TextCapitalizer.capitalizeSentences(etFieldSingle.getText().toString().trim());
        String fPlural = TextCapitalizer.capitalizeSentences(etFieldPlural.getText().toString().trim());

        if (TextUtils.isEmpty(mSingle)) mSingle = "Объект";
        if (TextUtils.isEmpty(mPlural)) mPlural = "Объекты";
        if (TextUtils.isEmpty(fSingle)) fSingle = "Запись";
        if (TextUtils.isEmpty(fPlural)) fPlural = "Записи";

        String titleOptionsJson = "[]";
        String titleUiType = spinnerTitleType.getText().toString();
        if ("Выбор из списка".equals(titleUiType) || "Множественный выбор".equals(titleUiType)) {
            List<String> opts = new ArrayList<>();
            for (String s : etTitleOptions.getText().toString().split(",")) {
                if (!s.trim().isEmpty()) opts.add(s.trim());
            }
            titleOptionsJson = new Gson().toJson(opts);
        }

        String contentOptionsJson = "[]";
        String contentUiType = spinnerContentType.getText().toString();
        if ("Выбор из списка".equals(contentUiType) || "Множественный выбор".equals(contentUiType)) {
            List<String> opts = new ArrayList<>();
            for (String s : etContentOptions.getText().toString().split(",")) {
                if (!s.trim().isEmpty()) opts.add(s.trim());
            }
            contentOptionsJson = new Gson().toJson(opts);
        }

        final String fAppName = appName;
        final String fMSingle = mSingle;
        final String fMPlural = mPlural;
        final String fFSingle = fSingle;
        final String fFPlural = fPlural;
        final String fTitleType = newTitleType;
        final String fTitleOptions = titleOptionsJson;
        final String fContentType = newContentType;
        final String fContentOptions = contentOptionsJson;
        final boolean fTitleAutoFill = cbTitleAutoFill.isChecked();
        final boolean fContentAutoFill = cbContentAutoFill.isChecked();

        final List<FieldTemplate> finalTemplates = new ArrayList<>(templates);
        new Thread(() -> {
            try {
                SupportSQLiteDatabase db = dbClient.database()
                        .getOpenHelper().getWritableDatabase();
                db.beginTransaction();
                try {
                    for (FieldTemplate orig : originalTemplates) {
                        boolean found = false;
                        for (FieldTemplate newT : finalTemplates) {
                            if (newT.getId() == orig.getId()) { found = true; break; }
                        }
                        if (!found) {
                            clearDataForTemplateWithinDb(db, orig.getId());
                            db.execSQL("DELETE FROM field_templates WHERE id = ?",
                                    new Object[]{orig.getId()});
                        }
                    }

                    for (int i = 0; i < finalTemplates.size(); i++) {
                        FieldTemplate t = finalTemplates.get(i);
                        t.setSortOrder(i);
                        ContentValues values = new ContentValues();
                        values.put("name", t.getName());
                        values.put("type", t.getType());
                        values.put("options", t.getOptions());
                        values.put("is_required", t.isRequired() ? 1 : 0);
                        values.put("is_auto_fill", t.isAutoFill() ? 1 : 0);
                        values.put("sort_order", t.getSortOrder());

                        if (t.getId() != 0) {
                            db.update("field_templates", SQLiteDatabase.CONFLICT_NONE,
                                    values, "id = ?", new Object[]{t.getId()});
                        } else {
                            long newId = db.insert("field_templates",
                                    SQLiteDatabase.CONFLICT_NONE, values);
                            if (newId != -1) t.setId((int) newId);
                        }
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }

                TermsHelper.saveTerms(OnboardingActivity.this, fAppName, fMSingle, fMPlural,
                        fFSingle, fFPlural, fTitleType, fTitleOptions, fTitleAutoFill,
                        fContentType, fContentOptions, fContentAutoFill);

                runOnUiThread(() -> {
                    isSaving = false;
                    btnNext.setEnabled(true);
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, R.string.onboarding_saved, Toast.LENGTH_SHORT).show();
                    if (!isEditMode) startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "performSaveAll failed", e);
                runOnUiThread(() -> {
                    isSaving = false;
                    btnNext.setEnabled(true);
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, getString(R.string.onboarding_save_error,
                                    e.getMessage() == null ? "неизвестно" : e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private boolean hasDataInColumn(String columnName) {
        try {
            String query = "SELECT COUNT(*) FROM fields WHERE " + columnName
                    + " IS NOT NULL AND TRIM(" + columnName + ") != ''";
            SupportSQLiteDatabase db = dbClient.database().getOpenHelper().getWritableDatabase();
            try (Cursor c = db.query(query)) {
                if (c != null && c.moveToFirst()) return c.getInt(0) > 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking data in " + columnName, e);
        }
        return false;
    }

    private void clearDataInColumn(String columnName) {
        try {
            SupportSQLiteDatabase db = dbClient.database().getOpenHelper().getWritableDatabase();
            db.beginTransaction();
            try {
                ContentValues values = new ContentValues();
                values.putNull(columnName);
                db.update("fields", SQLiteDatabase.CONFLICT_NONE, values, null, null);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing data in " + columnName, e);
        }
    }

    private void clearDataForTemplateWithinDb(SupportSQLiteDatabase db, int templateId) {
        String key = String.valueOf(templateId);
        try (Cursor c = db.query(
                "SELECT id, custom_values FROM fields WHERE custom_values LIKE ?",
                new Object[]{"%\"" + key + "\":%"})) {
            List<Integer> ids = new ArrayList<>();
            List<String> jsons = new ArrayList<>();
            while (c.moveToNext()) {
                ids.add(c.getInt(0));
                jsons.add(c.getString(1));
            }
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            for (int i = 0; i < ids.size(); i++) {
                try {
                    Map<String, String> map = gson.fromJson(jsons.get(i), type);
                    if (map != null && map.containsKey(key)) {
                        map.remove(key);
                        ContentValues values = new ContentValues();
                        values.put("custom_values", gson.toJson(map));
                        db.update("fields", SQLiteDatabase.CONFLICT_NONE, values,
                                "id = ?", new Object[]{ids.get(i)});
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing data for template " + templateId, e);
        }
    }

    private void showTemplateDialog(@Nullable final FieldTemplate editTemplate) {
        TemplateEditDialogFragment fragment = TemplateEditDialogFragment.newInstance(editTemplate);
        fragment.show(getSupportFragmentManager(), TemplateEditDialogFragment.TAG);
    }

    private void onTemplateEditResult(@NonNull String requestKey, @NonNull Bundle result) {
        String name = result.getString(TemplateEditDialogFragment.KEY_NAME, "");
        if (name == null) name = "";
        name = name.trim();
        String type = result.getString(TemplateEditDialogFragment.KEY_TYPE, "TEXT");
        if (type == null) type = "TEXT";
        String optionsJson = result.getString(TemplateEditDialogFragment.KEY_OPTIONS, "[]");
        if (optionsJson == null) optionsJson = "[]";
        boolean req = result.getBoolean(TemplateEditDialogFragment.KEY_REQUIRED, false);
        boolean autoFill = result.getBoolean(TemplateEditDialogFragment.KEY_AUTOFILL, false);
        boolean isEdit = result.getBoolean(TemplateEditDialogFragment.KEY_IS_EDIT, false);
        int editId = result.getInt(TemplateEditDialogFragment.KEY_EDIT_ID, 0);

        if (name.isEmpty()) {
            Toast.makeText(this, R.string.onboarding_enter_field_name, Toast.LENGTH_SHORT).show();
            return;
        }

        if (isEdit) {
            FieldTemplate editTemplate = null;
            for (FieldTemplate t : templates) {
                if (t.getId() == editId) { editTemplate = t; break; }
            }
            if (editTemplate == null) return;

            final String finalName = name;
            final String finalType = type;
            final String finalOptionsJson = optionsJson;
            final boolean finalReq = req;
            final boolean finalAutoFill = autoFill;
            final FieldTemplate finalEdit = editTemplate;

            if (!editTemplate.getType().equals(type)) {
                new Thread(() -> {
                    try {
                        boolean hasData = hasDataForTemplate(editId);
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (hasData) {
                                showTypeChangeWarning(finalEdit, finalName, finalType,
                                        finalOptionsJson, finalReq, finalAutoFill);
                            } else {
                                applyTemplateChanges(finalEdit, finalName, finalType,
                                        finalOptionsJson, finalReq, finalAutoFill);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Type check failed", e);
                    }
                }).start();
            } else {
                applyTemplateChanges(editTemplate, name, type, optionsJson, req, autoFill);
            }
        } else {
            templates.add(new FieldTemplate(0, name, type, optionsJson,
                    req, templates.size(), autoFill));
            adapter.notifyItemInserted(templates.size() - 1);
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

    private void showTypeChangeWarning(FieldTemplate template, String newName, String newType,
                                        String newOptions, boolean newReq, boolean newAutoFill) {
        DialogHelper.showConfirm(this,
                getString(R.string.onboarding_type_change_title),
                getString(R.string.onboarding_type_change_message, template.getName()),
                () -> new Thread(() -> {
                    try {
                        clearDataForTemplate(template.getId());
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            applyTemplateChanges(template, newName, newType,
                                    newOptions, newReq, newAutoFill);
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "clearDataForTemplate failed", e);
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            Toast.makeText(this, R.string.onboarding_clear_old_error,
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                }).start());
    }

    private void applyTemplateChanges(FieldTemplate template, String newName, String newType,
                                       String newOptions, boolean newReq, boolean newAutoFill) {
        template.setName(newName);
        template.setType(newType);
        template.setOptions(newOptions);
        template.setRequired(newReq);
        template.setAutoFill(newAutoFill);
        adapter.notifyDataSetChanged();
    }

    private boolean hasDataForTemplate(int templateId) {
        try {
            String searchArg = "%\"" + templateId + "\":%";
            try (Cursor c = dbClient.database().query(
                    "SELECT id FROM fields WHERE custom_values LIKE ?", new Object[]{searchArg})) {
                return c != null && c.getCount() > 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void clearDataForTemplate(int templateId) {
        try {
            SupportSQLiteDatabase db = dbClient.database().getOpenHelper().getWritableDatabase();
            String key = String.valueOf(templateId);
            try (Cursor c = db.query(
                    "SELECT id, custom_values FROM fields WHERE custom_values LIKE ?",
                    new Object[]{"%\"" + key + "\":%"})) {
                List<Integer> ids = new ArrayList<>();
                List<String> jsons = new ArrayList<>();
                while (c.moveToNext()) {
                    ids.add(c.getInt(0));
                    jsons.add(c.getString(1));
                }
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                db.beginTransaction();
                try {
                    for (int i = 0; i < ids.size(); i++) {
                        Map<String, String> map = gson.fromJson(jsons.get(i), type);
                        if (map != null && map.containsKey(key)) {
                            map.remove(key);
                            ContentValues values = new ContentValues();
                            values.put("custom_values", gson.toJson(map));
                            db.update("fields", SQLiteDatabase.CONFLICT_NONE, values,
                                    "id=?", new Object[]{ids.get(i)});
                        }
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing data", e);
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

    private class TemplateAdapter extends RecyclerView.Adapter<TemplateAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_template_edit, parent, false));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            FieldTemplate t = templates.get(position);
            holder.tvName.setText(t.getName());
            holder.tvType.setText("Тип: " + getTypeString(t.getType()));
            holder.tvRequired.setText("Обязательное: " + (t.isRequired() ? "Да" : "Нет"));

            ThemeHelper.applyToTextViewPrimary(holder.tvName);
            ThemeHelper.applyToTextViewSecondary(holder.tvType);
            ThemeHelper.applyToTextViewSecondary(holder.tvRequired);

            holder.itemView.setOnClickListener(v -> showTemplateDialog(t));
            holder.btnDelete.setOnClickListener(v -> {
                int pos = holder.getAbsoluteAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                FieldTemplate toDelete = templates.get(pos);
                if (toDelete.getId() != 0) {
                    new Thread(() -> {
                        boolean hasData = hasDataForTemplate(toDelete.getId());
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (hasData) {
                                DialogHelper.showConfirm(OnboardingActivity.this,
                                        getString(R.string.onboarding_type_change_title),
                                        "В поле \"" + toDelete.getName()
                                                + "\" уже есть сохранённые данные. При удалении поля они будут удалены. Продолжить?",
                                        () -> {
                                            int p2 = templates.indexOf(toDelete);
                                            if (p2 >= 0) {
                                                templates.remove(p2);
                                                notifyItemRemoved(p2);
                                                notifyItemRangeChanged(p2, templates.size());
                                            }
                                        });
                            } else {
                                int p2 = templates.indexOf(toDelete);
                                if (p2 >= 0) {
                                    templates.remove(p2);
                                    notifyItemRemoved(p2);
                                    notifyItemRangeChanged(p2, templates.size());
                                }
                            }
                        });
                    }).start();
                } else {
                    templates.remove(pos);
                    notifyItemRemoved(pos);
                    notifyItemRangeChanged(pos, templates.size());
                }
            });

            holder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && itemTouchHelper != null) {
                    int pos = holder.getAbsoluteAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) itemTouchHelper.startDrag(holder);
                }
                return false;
            });
        }

        @Override
        public int getItemCount() { return templates.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvType, tvRequired;
            ImageButton btnDelete;
            android.widget.ImageView dragHandle;

            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_field_name);
                tvType = v.findViewById(R.id.tv_field_type);
                tvRequired = v.findViewById(R.id.tv_field_required);
                btnDelete = v.findViewById(R.id.btn_delete);
                dragHandle = v.findViewById(R.id.iv_drag_handle);
            }
        }
    }
}