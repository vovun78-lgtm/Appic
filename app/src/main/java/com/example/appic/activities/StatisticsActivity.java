package com.example.appic.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.R;
import com.example.appic.adapters.MyReportsAdapter;
import com.example.appic.adapters.StatisticsCardAdapter;
import com.example.appic.database.DatabaseClient;
import com.example.appic.database.models.Machine;
import com.example.appic.database.models.MachineGroup;
import com.example.appic.database.models.ReportDefinition;
import com.example.appic.database.repository.MachineGroupRepository;
import com.example.appic.database.repository.MachineRepository;
import com.example.appic.utils.ThemeHelper;
import com.example.appic.utils.statistics.BuiltInReports;
import com.example.appic.utils.statistics.ScopeType;
import com.example.appic.utils.statistics.StatisticsConfig;
import com.example.appic.utils.statistics.StatisticsEngine;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatisticsActivity extends BaseActivity {

    private static final String TAG = "StatisticsActivity";
    private static final String KEY_SCOPE_TYPE = "scope_type";
    private static final String KEY_SCOPE_IDS = "scope_ids";
    private static final String KEY_START = "start_date";
    private static final String KEY_END = "end_date";
    private static final String KEY_PERIOD_POS = "period_position";
    private static final String KEY_SCOPE_LABEL = "scope_label";
    private static final String KEY_PERIOD_LABEL = "period_label";
    private static final long MS_PER_DAY = 86_400_000L;

    private MaterialAutoCompleteTextView spinnerScope;
    private MaterialAutoCompleteTextView spinnerPeriod;
    private LinearLayout kpiContainer;
    private RecyclerView recyclerBuiltIn, recyclerMyReports;
    private TextView tvEmptyBuiltIn, tvEmptyMyReports;
    private FloatingActionButton btnCreate;

    private MachineRepository machineRepository;
    private MachineGroupRepository machineGroupRepository;
    private DatabaseClient dbClient;

    private List<Machine> allMachines = new ArrayList<>();
    private List<MachineGroup> allGroups = new ArrayList<>();

    private ScopeType scopeType = ScopeType.ALL;
    private List<Integer> scopeIds = new ArrayList<>();
    private Long startDate = null;
    private Long endDate = null;
    private int periodPosition = 0;

    private final String[] PERIOD_LABELS = {
            "За всё время", "Этот месяц", "Прошлый месяц",
            "Последние 30 дней", "Этот год", "Свой диапазон…"
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private StatisticsCardAdapter builtInAdapter;
    private MyReportsAdapter myReportsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applyToToolbar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.stats_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        machineRepository = new MachineRepository(this);
        machineGroupRepository = new MachineGroupRepository(this);
        dbClient = DatabaseClient.getInstance(this);

        spinnerScope = findViewById(R.id.spinner_scope);
        spinnerPeriod = findViewById(R.id.spinner_period);
        kpiContainer = findViewById(R.id.kpi_container);
        recyclerBuiltIn = findViewById(R.id.recycler_builtin);
        recyclerMyReports = findViewById(R.id.recycler_my_reports);
        tvEmptyBuiltIn = findViewById(R.id.tv_empty_builtin);
        tvEmptyMyReports = findViewById(R.id.tv_empty_my_reports);
        btnCreate = findViewById(R.id.btn_create_report);

        recyclerBuiltIn.setLayoutManager(new LinearLayoutManager(this));
        recyclerMyReports.setLayoutManager(new LinearLayoutManager(this));

        setupSpinners();

        if (savedInstanceState != null) {
            scopeType = ScopeType.values()[savedInstanceState.getInt(KEY_SCOPE_TYPE, 0)];
            ArrayList<Integer> ids = savedInstanceState.getIntegerArrayList(KEY_SCOPE_IDS);
            if (ids != null) scopeIds = ids;
            if (savedInstanceState.containsKey(KEY_START)) startDate = savedInstanceState.getLong(KEY_START);
            if (savedInstanceState.containsKey(KEY_END)) endDate = savedInstanceState.getLong(KEY_END);
            periodPosition = savedInstanceState.getInt(KEY_PERIOD_POS, 0);
            String scopeLabel = savedInstanceState.getString(KEY_SCOPE_LABEL);
            if (scopeLabel != null) spinnerScope.setText(scopeLabel, false);
            String periodLabel = savedInstanceState.getString(KEY_PERIOD_LABEL);
            if (periodLabel != null) spinnerPeriod.setText(periodLabel, false);
        }

        btnCreate.setOnClickListener(v -> {
            Intent i = new Intent(this, ReportBuilderActivity.class);
            startActivity(i);
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SCOPE_TYPE, scopeType.ordinal());
        outState.putIntegerArrayList(KEY_SCOPE_IDS, new ArrayList<>(scopeIds));
        if (startDate != null) outState.putLong(KEY_START, startDate);
        if (endDate != null) outState.putLong(KEY_END, endDate);
        outState.putInt(KEY_PERIOD_POS, periodPosition);
        outState.putString(KEY_SCOPE_LABEL, spinnerScope.getText().toString());
        outState.putString(KEY_PERIOD_LABEL, spinnerPeriod.getText().toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void setupSpinners() {
        spinnerScope.setSimpleItems(new String[]{"Все объекты", "Группа объектов", "Один объект"});
        spinnerScope.setText("Все объекты", false);
        spinnerScope.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                scopeType = ScopeType.ALL;
                scopeIds.clear();
                spinnerScope.setText("Все объекты", false);
                refreshAll();
            } else if (position == 1) {
                pickMachineGroup();
            } else {
                pickMachine();
            }
        });

        spinnerPeriod.setSimpleItems(PERIOD_LABELS);
        spinnerPeriod.setText(PERIOD_LABELS[0], false);
        spinnerPeriod.setOnItemClickListener((parent, view, position, id) -> {
            periodPosition = position;
            applyPeriod();
        });
    }

    private void pickMachineGroup() {
        if (allGroups.isEmpty()) {
            Toast.makeText(this, R.string.stats_no_groups, Toast.LENGTH_SHORT).show();
            revertScopeToAll();
            return;
        }
        String[] names = new String[allGroups.size()];
        for (int i = 0; i < allGroups.size(); i++) names[i] = allGroups.get(i).getName();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.stats_pick_group)
                .setItems(names, (d, which) -> {
                    scopeType = ScopeType.MACHINE_GROUP;
                    scopeIds.clear();
                    scopeIds.add(allGroups.get(which).getId());
                    spinnerScope.setText("Группа: " + allGroups.get(which).getName(), false);
                    refreshAll();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> revertScopeToAll())
                .setOnCancelListener(d -> revertScopeToAll())
                .show();
    }

    private void pickMachine() {
        if (allMachines.isEmpty()) {
            Toast.makeText(this, R.string.stats_no_machines, Toast.LENGTH_SHORT).show();
            revertScopeToAll();
            return;
        }
        String[] names = new String[allMachines.size()];
        for (int i = 0; i < allMachines.size(); i++) names[i] = allMachines.get(i).getName();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.stats_pick_machine)
                .setItems(names, (d, which) -> {
                    scopeType = ScopeType.MACHINE;
                    scopeIds.clear();
                    scopeIds.add(allMachines.get(which).getId());
                    spinnerScope.setText(allMachines.get(which).getName(), false);
                    refreshAll();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> revertScopeToAll())
                .setOnCancelListener(d -> revertScopeToAll())
                .show();
    }

    private void revertScopeToAll() {
        scopeType = ScopeType.ALL;
        scopeIds.clear();
        spinnerScope.setText("Все объекты", false);
        refreshAll();
    }

    private void applyPeriod() {
        switch (periodPosition) {
            case 0: { Long[] p = BuiltInReports.periodAllTime(); startDate = p[0]; endDate = p[1]; break; }
            case 1: { Long[] p = BuiltInReports.periodThisMonth(); startDate = p[0]; endDate = p[1]; break; }
            case 2: { Long[] p = BuiltInReports.periodLastMonth(); startDate = p[0]; endDate = p[1]; break; }
            case 3: { Long[] p = BuiltInReports.periodLast30Days(); startDate = p[0]; endDate = p[1]; break; }
            case 4: { Long[] p = BuiltInReports.periodThisYear(); startDate = p[0]; endDate = p[1]; break; }
            case 5: pickCustomRange(); return;
        }
        refreshAll();
    }

    private long utcDayStartToLocalMillis(long utcMidnight) {
        LocalDate d = LocalDate.ofEpochDay(utcMidnight / MS_PER_DAY);
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private long utcDayEndToLocalMillis(long utcMidnight) {
        LocalDate d = LocalDate.ofEpochDay(utcMidnight / MS_PER_DAY);
        return d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1L;
    }

    private void pickCustomRange() {
        com.google.android.material.datepicker.MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker =
                com.google.android.material.datepicker.MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText(R.string.stats_pick_range)
                        .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null && selection.first != null && selection.second != null) {
                startDate = utcDayStartToLocalMillis(selection.first);
                endDate = utcDayEndToLocalMillis(selection.second);
                java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd.MM.yy",
                        java.util.Locale.getDefault());
                spinnerPeriod.setText(fmt.format(new Date(startDate)) + " – " +
                        fmt.format(new Date(endDate)), false);
                refreshAll();
            } else {
                periodPosition = 0;
                spinnerPeriod.setText(PERIOD_LABELS[0], false);
                startDate = null;
                endDate = null;
                refreshAll();
            }
        });
        picker.addOnCancelListener(d -> {
            periodPosition = 0;
            spinnerPeriod.setText(PERIOD_LABELS[0], false);
            startDate = null;
            endDate = null;
            refreshAll();
        });
        picker.show(getSupportFragmentManager(), "range");
    }

    private StatisticsConfig baseConfig() {
        StatisticsConfig c = new StatisticsConfig();
        c.scopeType = scopeType;
        c.scopeIds = new ArrayList<>(scopeIds);
        c.startDate = startDate;
        c.endDate = endDate;
        return c;
    }

    private void loadData() {
        executor.execute(() -> {
            try {
                allMachines = machineRepository.getAll();
                allGroups = machineGroupRepository.getAll();
                final List<BuiltInReports.BuiltIn> builtIn = BuiltInReports.build(this);
                final List<ReportDefinition> myReports = dbClient.reportDefinitionDao().getAll();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setupBuiltIn(builtIn);
                    setupMyReports(myReports);
                    refreshAll();
                });
            } catch (Exception e) {
                Log.e(TAG, "loadData failed", e);
            }
        });
    }

    private void setupBuiltIn(List<BuiltInReports.BuiltIn> list) {
        builtInAdapter = new StatisticsCardAdapter(list, this::openBuiltIn);
        recyclerBuiltIn.setAdapter(builtInAdapter);
        tvEmptyBuiltIn.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerBuiltIn.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setupMyReports(List<ReportDefinition> list) {
        myReportsAdapter = new MyReportsAdapter(list,
                this::openSaved,
                this::showSavedOptions);
        recyclerMyReports.setAdapter(myReportsAdapter);
        tvEmptyMyReports.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerMyReports.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void refreshAll() {
        refreshKpi();
    }

    private void refreshKpi() {
        executor.execute(() -> {
            try {
                StatisticsConfig c = baseConfig();
                int count = StatisticsEngine.countFields(this, c);

                int machinesCount;
                int groupsCount;

                switch (scopeType) {
                    case MACHINE_GROUP: {
                        machinesCount = 0;
                        if (!scopeIds.isEmpty()) {
                            for (Machine m : allMachines) {
                                if (scopeIds.contains(m.getGroupId())) {
                                    machinesCount++;
                                }
                            }
                        }
                        groupsCount = 0;
                        for (MachineGroup g : allGroups) {
                            if (scopeIds.contains(g.getId())) {
                                groupsCount++;
                            }
                        }
                        break;
                    }
                    case MACHINE: {
                        machinesCount = 0;
                        for (Machine m : allMachines) {
                            if (scopeIds.contains(m.getId())) {
                                machinesCount++;
                            }
                        }
                        groupsCount = 0;
                        break;
                    }
                    case ALL:
                    default: {
                        machinesCount = allMachines.size();
                        groupsCount = allGroups.size();
                        break;
                    }
                }

                final int finalMachines = machinesCount;
                final int finalGroups = groupsCount;
                final int finalCount = count;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    kpiContainer.removeAllViews();
                    addKpiCard(finalMachines, "Объектов");
                    addKpiCard(finalGroups, "Групп");
                    addKpiCard(finalCount, "Записей");
                });
            } catch (Exception e) {
                Log.e(TAG, "refreshKpi failed", e);
            }
        });
    }

    private void addKpiCard(long value, String label) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        float d = getResources().getDisplayMetrics().density;
        lp.setMargins((int) (4 * d), (int) (4 * d), (int) (4 * d), (int) (4 * d));
        card.setLayoutParams(lp);
        card.setRadius((int) (8 * d));
        card.setCardElevation((int) (2 * d));

        // ИСПРАВЛЕНО (3.2.1): раньше фон KPI-карточки не переопределялся и
        // брался дефолтный из Material — на тёмной теме карточки оставались
        // светлыми. Теперь явно применяем ?attr/cardBackground.
        int cardBgColor = ThemeHelper.getThemeColor(this, R.attr.cardBackground);
        if (cardBgColor != 0) {
            card.setCardBackgroundColor(cardBgColor);
        }

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * d);
        inner.setPadding(pad, pad, pad, pad);

        TextView tvValue = new TextView(this);
        tvValue.setText(String.valueOf(value));
        tvValue.setTextSize(22);
        tvValue.setTextColor(ThemeHelper.getThemeColor(this, R.attr.textPrimaryColor));
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(ThemeHelper.getThemeColor(this, R.attr.textSecondaryColor));

        inner.addView(tvValue);
        inner.addView(tvLabel);
        card.addView(inner);
        kpiContainer.addView(card);
    }

    private void openBuiltIn(BuiltInReports.BuiltIn b) {
        StatisticsConfig c = b.config.copy();
        c.scopeType = scopeType;
        c.scopeIds = new ArrayList<>(scopeIds);
        c.startDate = startDate;
        c.endDate = endDate;
        c.name = b.title;
        Intent i = ReportViewActivity.newIntent(this, c.toJson(), b.title, false, -1);
        startActivity(i);
    }

    private void openSaved(ReportDefinition d) {
        StatisticsConfig c = StatisticsConfig.fromJson(d.getConfigJson());
        Intent i = ReportViewActivity.newIntent(this, c.toJson(), d.getName(), true, d.getId());
        startActivity(i);
    }

    private void showSavedOptions(ReportDefinition d) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(d.getName())
                .setItems(new String[]{
                        getString(R.string.stats_open),
                        getString(R.string.stats_edit),
                        getString(R.string.stats_duplicate),
                        getString(R.string.delete)
                }, (dialog, which) -> {
                    if (which == 0) openSaved(d);
                    else if (which == 1) editSaved(d);
                    else if (which == 2) duplicateSaved(d);
                    else if (which == 3) confirmDelete(d);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void editSaved(ReportDefinition d) {
        Intent i = new Intent(this, ReportBuilderActivity.class);
        i.putExtra("edit_id", d.getId());
        i.putExtra("config_json", d.getConfigJson());
        startActivity(i);
    }

    private void duplicateSaved(ReportDefinition d) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                ReportDefinition copy = new ReportDefinition(0, "Копия — " + d.getName(),
                        d.getConfigJson(), now, now);
                dbClient.reportDefinitionDao().insert(copy);
                runOnUiThread(this::loadData);
            } catch (Exception e) {
                Log.e(TAG, "duplicate failed", e);
            }
        });
    }

    private void confirmDelete(ReportDefinition d) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirm)
                .setMessage("Удалить отчёт \"" + d.getName() + "\"?")
                .setPositiveButton(R.string.yes, (dlg, w) -> {
                    executor.execute(() -> {
                        try {
                            dbClient.reportDefinitionDao().deleteById(d.getId());
                            runOnUiThread(this::loadData);
                        } catch (Exception e) {
                            Log.e(TAG, "delete failed", e);
                        }
                    });
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdownNow();
    }
}