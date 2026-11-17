package com.example.appic.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.appic.R;
import com.example.appic.adapters.DropdownAdapter;
import com.example.appic.database.DatabaseClient;
import com.example.appic.database.DatabaseHelper;
import com.example.appic.database.models.FieldTemplate;
import com.example.appic.database.models.Machine;
import com.example.appic.database.models.MachineGroup;
import com.example.appic.database.models.ReportDefinition;
import com.example.appic.database.repository.MachineGroupRepository;
import com.example.appic.database.repository.MachineRepository;
import com.example.appic.utils.MaterialProgressDialog;
import com.example.appic.utils.ThemeHelper;
import com.example.appic.utils.statistics.BuiltInReports;
import com.example.appic.utils.statistics.FilterCondition;
import com.example.appic.utils.statistics.FilterOp;
import com.example.appic.utils.statistics.GroupByType;
import com.example.appic.utils.statistics.MetricDef;
import com.example.appic.utils.statistics.MetricType;
import com.example.appic.utils.statistics.ScopeType;
import com.example.appic.utils.statistics.StatisticsConfig;
import com.example.appic.utils.statistics.StatisticsEngine;
import com.example.appic.utils.statistics.StatisticsResult;
import com.example.appic.utils.statistics.ViewType;
import com.example.appic.views.SimpleChartView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportBuilderActivity extends BaseActivity {

    private static final String TAG = "ReportBuilderActivity";
    private static final int TOTAL_STEPS = 4;
    private static final String KEY_CONFIG = "config_state";
    private static final String KEY_STEP = "step_state";
    private static final String KEY_PERIOD_KIND = "period_kind_state";
    private static final long MS_PER_DAY = 86_400_000L;

    private static final String PREFS_NAME = "report_save_prefs";
    private static final String KEY_LAST_SAVED_HASH = "last_saved_hash";
    private static final String KEY_LAST_SAVED_TIME = "last_saved_time";
    private static final long DUPLICATE_GUARD_WINDOW_MS = 60_000L;

    private static final int PERIOD_ALL = 0;
    private static final int PERIOD_THIS_MONTH = 1;
    private static final int PERIOD_LAST_30 = 2;
    private static final int PERIOD_THIS_YEAR = 3;
    private static final int PERIOD_RANGE = 4;

    private int currentStep = 0;
    private int periodKind = PERIOD_ALL;
    private boolean isRestoring = false;
    private boolean isSaving = false;

    private ViewFlipper viewFlipper;
    private ProgressBar stepProgress;
    private TextView tvStepCount;
    private Button btnBack, btnNext;

    private StatisticsConfig config = new StatisticsConfig();

    private RadioGroup rgScope;
    private MaterialAutoCompleteTextView spinnerScopePick;
    private RadioGroup rgPeriod;
    private Button btnPickRange;
    private TextView tvRangeLabel;

    private LinearLayout filtersContainer;
    private Button btnAddFilter;

    private MaterialAutoCompleteTextView spinnerGroupBy;
    private LinearLayout metricsContainer;
    private Button btnAddMetric;

    private RadioGroup rgView;
    private SimpleChartView previewChart;
    private TextView tvPreviewInfo;

    private List<Machine> allMachines = new ArrayList<>();
    private List<MachineGroup> allGroups = new ArrayList<>();
    private List<FieldTemplate> templates = new ArrayList<>();

    private int editId = -1;
    private String editConfigJson;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private MaterialProgressDialog savingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_builder);

        editId = getIntent().getIntExtra("edit_id", -1);
        editConfigJson = getIntent().getStringExtra("config_json");
        if (editConfigJson != null) config = StatisticsConfig.fromJson(editConfigJson);

        if (savedInstanceState != null) {
            String savedConfig = savedInstanceState.getString(KEY_CONFIG);
            if (savedConfig != null) config = StatisticsConfig.fromJson(savedConfig);
            currentStep = savedInstanceState.getInt(KEY_STEP, 0);
            periodKind = savedInstanceState.getInt(KEY_PERIOD_KIND, PERIOD_ALL);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applyToToolbar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.builder_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        viewFlipper = findViewById(R.id.view_flipper);
        stepProgress = findViewById(R.id.step_progress);
        tvStepCount = findViewById(R.id.tv_step_count);
        btnBack = findViewById(R.id.btn_back);
        btnNext = findViewById(R.id.btn_next);

        rgScope = findViewById(R.id.rg_scope);
        spinnerScopePick = findViewById(R.id.spinner_scope_pick);
        rgPeriod = findViewById(R.id.rg_period);
        btnPickRange = findViewById(R.id.btn_pick_range);
        tvRangeLabel = findViewById(R.id.tv_range_label);

        filtersContainer = findViewById(R.id.filters_container);
        btnAddFilter = findViewById(R.id.btn_add_filter);

        spinnerGroupBy = findViewById(R.id.spinner_group_by);
        metricsContainer = findViewById(R.id.metrics_container);
        btnAddMetric = findViewById(R.id.btn_add_metric);

        rgView = findViewById(R.id.rg_view);
        previewChart = findViewById(R.id.preview_chart);
        tvPreviewInfo = findViewById(R.id.tv_preview_info);

        executor.execute(() -> {
            try {
                allMachines = new MachineRepository(this).getAll();
                allGroups = new MachineGroupRepository(this).getAll();
                templates = loadTemplates();
                mainHandler.post(this::initUi);
            } catch (Exception e) {
                Log.e(TAG, "load failed", e);
            }
        });

        btnBack.setOnClickListener(v -> goBack());
        btnNext.setOnClickListener(v -> goNext());
        btnAddFilter.setOnClickListener(v -> addFilterDialog());
        btnAddMetric.setOnClickListener(v -> addMetricDialog());
        btnPickRange.setOnClickListener(v -> pickRange());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isSaving) return;
                goBack();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_CONFIG, config.toJson());
        outState.putInt(KEY_STEP, currentStep);
        outState.putInt(KEY_PERIOD_KIND, periodKind);
    }

    private List<FieldTemplate> loadTemplates() {
        List<FieldTemplate> list = new ArrayList<>();
        try {
            SupportSQLiteDatabase db = DatabaseHelper.getSupportDatabase(this);
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
                            c.getInt(idIdx), c.getString(nameIdx),
                            c.getString(typeIdx), c.getString(optIdx),
                            c.getInt(reqIdx) == 1, c.getInt(sortIdx),
                            autoIdx != -1 && c.getInt(autoIdx) == 1));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void initUi() {
        setupScopeStep();
        setupFilterStep();
        setupGroupMetricStep();
        setupViewStep();
        if (currentStep > 0) {
            for (int i = 0; i < currentStep; i++) viewFlipper.showNext();
        }
        updateStepUi();
    }

    private void setupScopeStep() {
        isRestoring = true;
        try {
            rgScope.setOnCheckedChangeListener((g, id) -> {
                if (isRestoring) return;
                if (id == R.id.rb_scope_group) {
                    config.scopeType = ScopeType.MACHINE_GROUP;
                    spinnerScopePick.setVisibility(View.VISIBLE);
                    showGroupPicker();
                } else if (id == R.id.rb_scope_machine) {
                    config.scopeType = ScopeType.MACHINE;
                    spinnerScopePick.setVisibility(View.VISIBLE);
                    showMachinePicker();
                } else {
                    config.scopeType = ScopeType.ALL;
                    config.scopeIds.clear();
                    spinnerScopePick.setVisibility(View.GONE);
                }
            });

            rgPeriod.setOnCheckedChangeListener((g, id) -> {
                if (isRestoring) return;
                if (id == R.id.rb_period_range) {
                    periodKind = PERIOD_RANGE;
                    btnPickRange.setVisibility(View.VISIBLE);
                    if (config.startDate != null && config.endDate != null) {
                        updateRangeLabel();
                    }
                } else {
                    btnPickRange.setVisibility(View.GONE);
                    tvRangeLabel.setText("");
                    if (id == R.id.rb_period_all) {
                        periodKind = PERIOD_ALL;
                        config.startDate = null; config.endDate = null;
                    } else if (id == R.id.rb_period_this_month) {
                        periodKind = PERIOD_THIS_MONTH;
                        Long[] p = BuiltInReports.periodThisMonth();
                        config.startDate = p[0]; config.endDate = p[1];
                    } else if (id == R.id.rb_period_this_year) {
                        periodKind = PERIOD_THIS_YEAR;
                        Long[] p = BuiltInReports.periodThisYear();
                        config.startDate = p[0]; config.endDate = p[1];
                    } else if (id == R.id.rb_period_last_30) {
                        periodKind = PERIOD_LAST_30;
                        Long[] p = BuiltInReports.periodLast30Days();
                        config.startDate = p[0]; config.endDate = p[1];
                    }
                }
            });

            if (config.scopeType == ScopeType.MACHINE_GROUP) {
                rgScope.check(R.id.rb_scope_group);
                spinnerScopePick.setVisibility(View.VISIBLE);
                if (!config.scopeIds.isEmpty()) {
                    for (MachineGroup g : allGroups) {
                        if (g.getId() == config.scopeIds.get(0)) {
                            spinnerScopePick.setText(g.getName(), false);
                            break;
                        }
                    }
                }
            } else if (config.scopeType == ScopeType.MACHINE) {
                rgScope.check(R.id.rb_scope_machine);
                spinnerScopePick.setVisibility(View.VISIBLE);
                if (!config.scopeIds.isEmpty()) {
                    for (Machine m : allMachines) {
                        if (m.getId() == config.scopeIds.get(0)) {
                            spinnerScopePick.setText(m.getName(), false);
                            break;
                        }
                    }
                }
            } else {
                rgScope.check(R.id.rb_scope_all);
            }

            restorePeriod();
        } finally {
            isRestoring = false;
        }
    }

    private void restorePeriod() {
        switch (periodKind) {
            case PERIOD_THIS_MONTH:
                rgPeriod.check(R.id.rb_period_this_month);
                break;
            case PERIOD_LAST_30:
                rgPeriod.check(R.id.rb_period_last_30);
                break;
            case PERIOD_THIS_YEAR:
                rgPeriod.check(R.id.rb_period_this_year);
                break;
            case PERIOD_RANGE:
                rgPeriod.check(R.id.rb_period_range);
                btnPickRange.setVisibility(View.VISIBLE);
                if (config.startDate != null && config.endDate != null) {
                    updateRangeLabel();
                }
                break;
            case PERIOD_ALL:
            default:
                rgPeriod.check(R.id.rb_period_all);
                break;
        }
    }

    private void showGroupPicker() {
        if (allGroups.isEmpty()) return;
        String[] names = new String[allGroups.size()];
        for (int i = 0; i < allGroups.size(); i++) names[i] = allGroups.get(i).getName();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.stats_pick_group)
                .setItems(names, (d, which) -> {
                    config.scopeIds.clear();
                    config.scopeIds.add(allGroups.get(which).getId());
                    spinnerScopePick.setText(allGroups.get(which).getName(), false);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMachinePicker() {
        if (allMachines.isEmpty()) return;
        String[] names = new String[allMachines.size()];
        for (int i = 0; i < allMachines.size(); i++) names[i] = allMachines.get(i).getName();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.stats_pick_machine)
                .setItems(names, (d, which) -> {
                    config.scopeIds.clear();
                    config.scopeIds.add(allMachines.get(which).getId());
                    spinnerScopePick.setText(allMachines.get(which).getName(), false);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateRangeLabel() {
        if (config.startDate == null || config.endDate == null) {
            tvRangeLabel.setText("");
            return;
        }
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd.MM.yy",
                Locale.getDefault());
        tvRangeLabel.setText(fmt.format(new Date(config.startDate)) + " – " +
                fmt.format(new Date(config.endDate)));
    }

    private long utcDayStartToLocalMillis(long utcMidnight) {
        LocalDate d = LocalDate.ofEpochDay(utcMidnight / MS_PER_DAY);
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private long utcDayEndToLocalMillis(long utcMidnight) {
        LocalDate d = LocalDate.ofEpochDay(utcMidnight / MS_PER_DAY);
        return d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1L;
    }

    private long localDayStartToUtcMillis(long localMillis) {
        LocalDate d = LocalDate.ofInstant(new Date(localMillis).toInstant(), ZoneId.systemDefault());
        return d.toEpochDay() * MS_PER_DAY;
    }

    private void pickRange() {
        com.google.android.material.datepicker.MaterialDatePicker.Builder<androidx.core.util.Pair<Long, Long>> builder =
                com.google.android.material.datepicker.MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText(R.string.stats_pick_range);
        if (config.startDate != null && config.endDate != null) {
            builder.setSelection(new androidx.core.util.Pair<>(
                    localDayStartToUtcMillis(config.startDate),
                    localDayStartToUtcMillis(config.endDate)));
        }
        com.google.android.material.datepicker.MaterialDatePicker<androidx.core.util.Pair<Long, Long>> p = builder.build();
        p.addOnPositiveButtonClickListener(sel -> {
            if (sel != null && sel.first != null && sel.second != null) {
                config.startDate = utcDayStartToLocalMillis(sel.first);
                config.endDate = utcDayEndToLocalMillis(sel.second);
                periodKind = PERIOD_RANGE;
                updateRangeLabel();
            }
        });
        p.show(getSupportFragmentManager(), "range");
    }

    private void setupFilterStep() {
        rebuildFilters();
    }

    private void rebuildFilters() {
        filtersContainer.removeAllViews();
        for (int i = 0; i < config.filters.size(); i++) {
            final int idx = i;
            FilterCondition fc = config.filters.get(i);
            View v = LayoutInflater.from(this).inflate(R.layout.item_filter, filtersContainer, false);
            TextView tv = v.findViewById(R.id.tv_filter_text);
            ImageButton btnDelete = v.findViewById(R.id.btn_delete_filter);
            tv.setText(describeFilter(fc));
            btnDelete.setOnClickListener(b -> {
                config.filters.remove(idx);
                rebuildFilters();
            });
            filtersContainer.addView(v);
        }
    }

    private String describeFilter(FilterCondition fc) {
        String fieldName = fieldDisplayName(fc.fieldKey);
        String opName = opDisplayName(fc.op);
        String value = "";
        if (fc.stringValue != null) value = " " + fc.stringValue;
        else if (fc.numberValue != null) value = " " + fc.numberValue;
        else if (fc.dateStart != null && fc.dateEnd != null) {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd.MM.yy",
                    Locale.getDefault());
            value = " " + fmt.format(new Date(fc.dateStart)) + " – " +
                    fmt.format(new Date(fc.dateEnd));
        } else if (fc.boolValue != null) value = fc.boolValue ? " Да" : " Нет";
        else if (fc.listValues != null && !fc.listValues.isEmpty()) value = " " + fc.listValues;
        return fieldName + " " + opName + value;
    }

    private String fieldDisplayName(String key) {
        if (key == null) return "—";
        if ("createdAt".equals(key)) return "Дата создания";
        if ("updatedAt".equals(key)) return "Дата изменения";
        if ("isRead".equals(key)) return "Прочитано";
        if ("hasMedia".equals(key)) return "Есть медиа";
        if (key.startsWith("template:")) {
            try {
                int id = Integer.parseInt(key.substring("template:".length()));
                for (FieldTemplate t : templates) if (t.getId() == id) return t.getName();
            } catch (Exception ignored) {}
        }
        return key;
    }

    private String opDisplayName(FilterOp op) {
        if (op == null) return "";
        switch (op) {
            case EQUALS: return "=";
            case NOT_EQUALS: return "≠";
            case CONTAINS: return "содержит";
            case NOT_EMPTY: return "не пусто";
            case EMPTY: return "пусто";
            case GREATER: return ">";
            case GREATER_EQ: return "≥";
            case LESS: return "<";
            case LESS_EQ: return "≤";
            case BETWEEN: return "между";
            case CHECKED: return "установлен";
            case UNCHECKED: return "не установлен";
            case IN: return "в списке";
        }
        return op.name();
    }

    private void addFilterDialog() {
        List<String> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        keys.add("createdAt"); labels.add("Дата создания");
        keys.add("updatedAt"); labels.add("Дата изменения");
        keys.add("isRead"); labels.add("Прочитано");
        keys.add("hasMedia"); labels.add("Есть медиа");
        for (FieldTemplate t : templates) {
            keys.add("template:" + t.getId());
            labels.add(t.getName() + " (" + t.getType() + ")");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.builder_pick_field)
                .setItems(labels.toArray(new String[0]), (d, which) ->
                        pickOperatorAndValue(keys.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickOperatorAndValue(String key) {
        List<FilterOp> ops = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        if ("createdAt".equals(key) || "updatedAt".equals(key)) {
            ops.add(FilterOp.BETWEEN); labels.add("между");
        } else if ("isRead".equals(key) || "hasMedia".equals(key)) {
            ops.add(FilterOp.EQUALS); labels.add("равно");
        } else {
            FieldTemplate t = findTemplate(key);
            String type = t != null ? t.getType() : "TEXT";
            switch (type) {
                case "NUMBER":
                case "RATING":
                case "PROGRESS":
                    ops.add(FilterOp.GREATER_EQ); labels.add("≥");
                    ops.add(FilterOp.GREATER); labels.add(">");
                    ops.add(FilterOp.LESS_EQ); labels.add("≤");
                    ops.add(FilterOp.LESS); labels.add("<");
                    ops.add(FilterOp.EQUALS); labels.add("=");
                    break;
                case "CHECKBOX":
                    ops.add(FilterOp.CHECKED); labels.add("установлен");
                    ops.add(FilterOp.UNCHECKED); labels.add("не установлен");
                    break;
                case "DROPDOWN":
                case "MULTISELECT":
                    ops.add(FilterOp.EQUALS); labels.add("=");
                    ops.add(FilterOp.NOT_EQUALS); labels.add("≠");
                    break;
                case "DATE":
                case "DATETIME":
                    ops.add(FilterOp.BETWEEN); labels.add("между");
                    break;
                default:
                    ops.add(FilterOp.CONTAINS); labels.add("содержит");
                    ops.add(FilterOp.EQUALS); labels.add("=");
                    ops.add(FilterOp.NOT_EMPTY); labels.add("не пусто");
                    ops.add(FilterOp.EMPTY); labels.add("пусто");
                    break;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.builder_pick_op)
                .setItems(labels.toArray(new String[0]), (d, which) ->
                        askFilterValue(key, ops.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private FieldTemplate findTemplate(String key) {
        if (key == null || !key.startsWith("template:")) return null;
        try {
            int id = Integer.parseInt(key.substring("template:".length()));
            for (FieldTemplate t : templates) if (t.getId() == id) return t;
        } catch (Exception ignored) {}
        return null;
    }

    private FieldTemplate findTemplateById(int id) {
        for (FieldTemplate t : templates) if (t.getId() == id) return t;
        return null;
    }

    private View wrapInput(TextInputEditText input, String hint) {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(hint);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.addView(input);
        FrameLayout wrapper = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        wrapper.setPadding(pad, 0, pad, 0);
        wrapper.addView(til);
        return wrapper;
    }

    private void askFilterValue(String key, FilterOp op) {
        if (op == FilterOp.CHECKED || op == FilterOp.UNCHECKED) {
            FilterCondition fc = new FilterCondition(key, op);
            fc.boolValue = (op == FilterOp.CHECKED);
            config.filters.add(fc);
            rebuildFilters();
            return;
        }
        if (op == FilterOp.NOT_EMPTY || op == FilterOp.EMPTY) {
            FilterCondition fc = new FilterCondition(key, op);
            config.filters.add(fc);
            rebuildFilters();
            return;
        }

        FieldTemplate t = findTemplate(key);
        String type = t != null ? t.getType()
                : (("createdAt".equals(key) || "updatedAt".equals(key)) ? "DATE" : "TEXT");

        if ("isRead".equals(key) || "hasMedia".equals(key)) {
            FilterCondition fc = new FilterCondition(key, op);
            fc.boolValue = true;
            config.filters.add(fc);
            rebuildFilters();
            return;
        }

        if ("DATE".equals(type) || "DATETIME".equals(type)) {
            com.google.android.material.datepicker.MaterialDatePicker<Long> p =
                    com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                            .setTitleText("Начало").build();
            p.addOnPositiveButtonClickListener(start -> {
                if (start == null) return;
                com.google.android.material.datepicker.MaterialDatePicker<Long> p2 =
                        com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                                .setTitleText("Конец").build();
                p2.addOnPositiveButtonClickListener(end -> {
                    if (end == null) return;
                    FilterCondition fc = new FilterCondition(key, op);
                    fc.dateStart = utcDayStartToLocalMillis(start);
                    fc.dateEnd = utcDayEndToLocalMillis(end);
                    config.filters.add(fc);
                    rebuildFilters();
                });
                p2.show(getSupportFragmentManager(), "end");
            });
            p.show(getSupportFragmentManager(), "start");
            return;
        }

        TextInputEditText et = new TextInputEditText(this);
        if ("NUMBER".equals(type) || "RATING".equals(type) || "PROGRESS".equals(type)) {
            et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }
        ThemeHelper.applyToEditText(et);
        View wrapped = wrapInput(et, "Значение");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Значение")
                .setView(wrapped)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String s = et.getText().toString().trim();
                    FilterCondition fc = new FilterCondition(key, op);
                    if ("NUMBER".equals(type) || "RATING".equals(type) || "PROGRESS".equals(type)) {
                        try { fc.numberValue = Double.parseDouble(s); }
                        catch (Exception e) {
                            Toast.makeText(this, "Введите число", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else {
                        fc.stringValue = s;
                    }
                    config.filters.add(fc);
                    rebuildFilters();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setupGroupMetricStep() {
        // ИСПРАВЛЕНО (3.1.5): добавлен пункт «По значению поля», который
        // соответствует GroupByType.FIELD. Ранее движок поддерживал FIELD,
        // но пользователь не мог его выбрать в конструкторе.
        String[] opts = {"Без группировки", "Объект", "Группа", "Месяц", "Год", "По значению поля"};
        spinnerGroupBy.setAdapter(new DropdownAdapter(this, opts));
        restoreGroupBySpinner();
        spinnerGroupBy.setOnItemClickListener((p, v, pos, id) -> {
            switch (pos) {
                case 0:
                    config.groupBy = GroupByType.NONE;
                    config.groupByTemplateId = null;
                    break;
                case 1:
                    config.groupBy = GroupByType.MACHINE;
                    config.groupByTemplateId = null;
                    break;
                case 2:
                    config.groupBy = GroupByType.GROUP;
                    config.groupByTemplateId = null;
                    break;
                case 3:
                    config.groupBy = GroupByType.MONTH;
                    config.groupByTemplateId = null;
                    break;
                case 4:
                    config.groupBy = GroupByType.YEAR;
                    config.groupByTemplateId = null;
                    break;
                case 5:
                    pickFieldForGrouping();
                    break;
            }
        });

        if (config.metrics.isEmpty()) config.metrics.add(new MetricDef(MetricType.COUNT));
        rebuildMetrics();
    }

    /**
     * ИСПРАВЛЕНО (3.1.5): выбор поля для GroupByType.FIELD.
     * Группировка по значению поля имеет смысл для DROPDOWN/MULTISELECT/CHECKBOX,
     * но для гибкости разрешаем любые шаблоны (движок всё равно применит
     * json_extract по выбранному id).
     */
    private void pickFieldForGrouping() {
        if (templates.isEmpty()) {
            Toast.makeText(this, R.string.builder_no_fields_for_group, Toast.LENGTH_SHORT).show();
            restoreGroupBySpinner();
            return;
        }
        String[] labels = new String[templates.size()];
        for (int i = 0; i < templates.size(); i++) {
            FieldTemplate t = templates.get(i);
            labels[i] = t.getName() + " (" + t.getType() + ")";
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.builder_pick_field)
                .setItems(labels, (d, which) -> {
                    FieldTemplate t = templates.get(which);
                    config.groupBy = GroupByType.FIELD;
                    config.groupByTemplateId = t.getId();
                    spinnerGroupBy.setText("По полю: " + t.getName(), false);
                })
                .setNegativeButton(R.string.cancel, (d, w) -> restoreGroupBySpinner())
                .setOnCancelListener(d -> restoreGroupBySpinner())
                .show();
    }

    private void restoreGroupBySpinner() {
        GroupByType gb = config.groupBy == null ? GroupByType.NONE : config.groupBy;
        switch (gb) {
            case NONE: spinnerGroupBy.setText("Без группировки", false); break;
            case MACHINE: spinnerGroupBy.setText("Объект", false); break;
            case GROUP: spinnerGroupBy.setText("Группа", false); break;
            case MONTH: spinnerGroupBy.setText("Месяц", false); break;
            case YEAR: spinnerGroupBy.setText("Год", false); break;
            case FIELD:
                // ИСПРАВЛЕНО (3.1.5): корректное отображение FIELD-группировки,
                // в т.ч. при импорте отчёта или редактировании сохранённого.
                if (config.groupByTemplateId != null) {
                    FieldTemplate t = findTemplateById(config.groupByTemplateId);
                    if (t != null) {
                        spinnerGroupBy.setText("По полю: " + t.getName(), false);
                    } else {
                        spinnerGroupBy.setText("По значению поля", false);
                    }
                } else {
                    spinnerGroupBy.setText("По значению поля", false);
                }
                break;
            default: spinnerGroupBy.setText("Объект", false); break;
        }
    }

    private void rebuildMetrics() {
        metricsContainer.removeAllViews();
        for (int i = 0; i < config.metrics.size(); i++) {
            final int idx = i;
            MetricDef m = config.metrics.get(i);
            View v = LayoutInflater.from(this).inflate(R.layout.item_metric, metricsContainer, false);
            TextView tv = v.findViewById(R.id.tv_metric_text);
            ImageButton btnDelete = v.findViewById(R.id.btn_delete_metric);
            tv.setText(describeMetric(m));
            btnDelete.setOnClickListener(b -> {
                if (config.metrics.size() <= 1) return;
                config.metrics.remove(idx);
                rebuildMetrics();
            });
            metricsContainer.addView(v);
        }
    }

    private String describeMetric(MetricDef m) {
        String t;
        switch (m.type) {
            case COUNT: return "Количество записей";
            case SUM: t = "Сумма"; break;
            case AVG: t = "Среднее"; break;
            case MIN: t = "Минимум"; break;
            case MAX: t = "Максимум"; break;
            default: t = "—";
        }
        return t + " · " + fieldDisplayName(m.fieldKey);
    }

    private void addMetricDialog() {
        String[] types = {"Количество", "Сумма", "Среднее", "Минимум", "Максимум"};
        MetricType[] mt = {MetricType.COUNT, MetricType.SUM, MetricType.AVG, MetricType.MIN, MetricType.MAX};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.builder_pick_metric)
                .setItems(types, (d, which) -> {
                    MetricType type = mt[which];
                    if (type == MetricType.COUNT) {
                        config.metrics.add(new MetricDef(MetricType.COUNT));
                        rebuildMetrics();
                        return;
                    }
                    pickNumericField(type);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickNumericField(MetricType type) {
        List<String> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (FieldTemplate t : templates) {
            if ("NUMBER".equals(t.getType()) || "RATING".equals(t.getType())
                    || "PROGRESS".equals(t.getType())) {
                keys.add("template:" + t.getId());
                labels.add(t.getName() + " (" + t.getType() + ")");
            }
        }
        if (keys.isEmpty()) {
            Toast.makeText(this, R.string.builder_no_numeric_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.builder_pick_field)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    config.metrics.add(new MetricDef(type, keys.get(which)));
                    rebuildMetrics();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setupViewStep() {
        isRestoring = true;
        try {
            rgView.setOnCheckedChangeListener((g, id) -> {
                if (isRestoring) return;
                if (id == R.id.rb_view_table) config.viewType = ViewType.TABLE;
                else if (id == R.id.rb_view_bar) config.viewType = ViewType.BAR;
                else if (id == R.id.rb_view_pie) config.viewType = ViewType.PIE;
                else if (id == R.id.rb_view_line) config.viewType = ViewType.LINE;
                buildPreview();
            });

            if (config.viewType == ViewType.BAR) rgView.check(R.id.rb_view_bar);
            else if (config.viewType == ViewType.PIE) rgView.check(R.id.rb_view_pie);
            else if (config.viewType == ViewType.LINE) rgView.check(R.id.rb_view_line);
            else rgView.check(R.id.rb_view_table);
        } finally {
            isRestoring = false;
        }
    }

    private void goNext() {
        if (isSaving) return;
        if (currentStep == TOTAL_STEPS - 1) {
            doSave();
            return;
        }
        currentStep++;
        viewFlipper.showNext();
        updateStepUi();
        if (currentStep == TOTAL_STEPS - 1) buildPreview();
    }

    private void goBack() {
        if (currentStep == 0) { finish(); return; }
        currentStep--;
        viewFlipper.showPrevious();
        updateStepUi();
    }

    private void updateStepUi() {
        tvStepCount.setText("Шаг " + (currentStep + 1) + " из " + TOTAL_STEPS);
        stepProgress.setProgress(currentStep + 1);
        btnBack.setText(currentStep == 0 ? R.string.cancel : R.string.builder_back);
        btnNext.setText(currentStep == TOTAL_STEPS - 1
                ? R.string.builder_save : R.string.builder_next);
    }

    private void buildPreview() {
        // ИСПРАВЛЕНО (3.1.1): используем Material-атрибут colorPrimary,
        // а не системный android.R.attr.colorPrimary. Ранее resolveAttribute
        // не находил значение в AppTheme (в themes.xml задан именно
        // Material-атрибут), возвращал 0, и графики рисовались прозрачным.
        previewChart.setPrimaryColor(ThemeHelper.getThemeColor(
                this, com.google.android.material.R.attr.colorPrimary));
        previewChart.setTextColor(ThemeHelper.getThemeColor(this, R.attr.textPrimaryColor));
        previewChart.setDividerColor(ThemeHelper.getThemeColor(this, R.attr.dividerColor));
        previewChart.setViewType(config.viewType);

        final StatisticsConfig snapshot = config.copy();

        executor.execute(() -> {
            StatisticsResult r = StatisticsEngine.execute(this, snapshot);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (r.errorMessage != null) {
                    tvPreviewInfo.setText(r.errorMessage);
                    previewChart.setResult(null);
                } else {
                    tvPreviewInfo.setText("Строк: " + r.rows.size()
                            + " · " + (r.metricLabels.isEmpty() ? "" : r.metricLabels.get(0)));
                    previewChart.setResult(r);
                }
            });
        });
    }

    private void doSave() {
        if (isSaving) return;
        TextInputEditText et = new TextInputEditText(this);
        et.setSingleLine(true);
        if (editId != -1 && editConfigJson != null) {
            StatisticsConfig old = StatisticsConfig.fromJson(editConfigJson);
            if (old.name != null) et.setText(old.name);
        }
        ThemeHelper.applyToEditText(et);
        View wrapped = wrapInput(et, getString(R.string.builder_report_name));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.builder_save_title)
                .setView(wrapped)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        name = "Отчёт от " + new java.text.SimpleDateFormat("dd.MM.yy",
                                Locale.getDefault()).format(new Date());
                    }
                    config.name = name;
                    persist(name);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void persist(String name) {
        isSaving = true;
        showSavingDialog();

        final String configJson = config.toJson();
        final int configHash = configJson.hashCode();
        final int currentEditId = editId;

        executor.execute(() -> {
            boolean success = false;
            try {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                long lastTime = prefs.getLong(KEY_LAST_SAVED_TIME, 0);
                int lastHash = prefs.getInt(KEY_LAST_SAVED_HASH, 0);
                long now = System.currentTimeMillis();

                if (currentEditId == -1
                        && lastHash == configHash
                        && (now - lastTime) < DUPLICATE_GUARD_WINDOW_MS) {
                    success = true;
                } else {
                    DatabaseClient db = DatabaseClient.getInstance(this);
                    if (currentEditId != -1) {
                        ReportDefinition existing = db.reportDefinitionDao().getById(currentEditId);
                        if (existing != null) {
                            existing.setName(name);
                            existing.setConfigJson(configJson);
                            existing.setUpdatedAt(now);
                            db.reportDefinitionDao().update(existing);
                        }
                    } else {
                        db.reportDefinitionDao().insert(
                                new ReportDefinition(0, name, configJson, now, now));
                    }
                    prefs.edit()
                            .putInt(KEY_LAST_SAVED_HASH, configHash)
                            .putLong(KEY_LAST_SAVED_TIME, now)
                            .apply();
                    success = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "persist failed", e);
            }

            final boolean finalSuccess = success;
            mainHandler.post(() -> {
                dismissSavingDialog();
                isSaving = false;
                if (isFinishing() || isDestroyed()) return;
                if (finalSuccess) {
                    Toast.makeText(this, R.string.builder_saved, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, R.string.builder_save_failed, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void showSavingDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (savingDialog != null && savingDialog.isShowing()) savingDialog.dismiss();
        savingDialog = new MaterialProgressDialog(this);
        savingDialog.setMessage(getString(R.string.progress_saving));
        savingDialog.setCancelable(false);
        try { savingDialog.show(); } catch (Exception ignored) {}
    }

    private void dismissSavingDialog() {
        if (savingDialog != null && savingDialog.isShowing()) {
            try { savingDialog.dismiss(); } catch (Exception ignored) {}
        }
        savingDialog = null;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (isSaving) return true;
            goBack();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissSavingDialog();
        if (!isSaving) {
            executor.shutdownNow();
        } else {
            executor.shutdown();
        }
    }
}