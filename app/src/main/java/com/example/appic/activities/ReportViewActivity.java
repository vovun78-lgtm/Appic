package com.example.appic.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.R;
import com.example.appic.adapters.ReportTableAdapter;
import com.example.appic.utils.ThemeHelper;
import com.example.appic.utils.statistics.StatisticsConfig;
import com.example.appic.utils.statistics.StatisticsEngine;
import com.example.appic.utils.statistics.StatisticsResult;
import com.example.appic.utils.statistics.ViewType;
import com.example.appic.views.SimpleChartView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportViewActivity extends BaseActivity {

    private StatisticsConfig config;
    private boolean isSaved;
    private int editId = -1;

    private SimpleChartView chartView;
    private RecyclerView recyclerTable;
    private TextView tvEmpty;
    private ReportTableAdapter tableAdapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static Intent newIntent(Context context, String configJson, String title,
                                   boolean isSaved, int editId) {
        Intent i = new Intent(context, ReportViewActivity.class);
        i.putExtra("config_json", configJson);
        i.putExtra("title", title);
        i.putExtra("is_saved", isSaved);
        i.putExtra("edit_id", editId);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_view);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applyToToolbar(toolbar);

        String title = getIntent().getStringExtra("title");
        String json = getIntent().getStringExtra("config_json");
        isSaved = getIntent().getBooleanExtra("is_saved", false);
        editId = getIntent().getIntExtra("edit_id", -1);
        config = StatisticsConfig.fromJson(json);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title != null ? title : "Отчёт");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        chartView = findViewById(R.id.chart_view);
        recyclerTable = findViewById(R.id.recycler_table);
        tvEmpty = findViewById(R.id.tv_empty);

        chartView.setPrimaryColor(ThemeHelper.getThemeColor(this, android.R.attr.colorPrimary));
        chartView.setTextColor(ThemeHelper.getThemeColor(this, R.attr.textPrimaryColor));
        chartView.setDividerColor(ThemeHelper.getThemeColor(this, R.attr.dividerColor));

        tableAdapter = new ReportTableAdapter();
        recyclerTable.setLayoutManager(new LinearLayoutManager(this));
        recyclerTable.setAdapter(tableAdapter);

        runQuery();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_report_view, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) { finish(); return true; }
        if (id == R.id.action_edit_report && isSaved && editId != -1) {
            Intent i = new Intent(this, ReportBuilderActivity.class);
            i.putExtra("edit_id", editId);
            i.putExtra("config_json", config.toJson());
            startActivity(i);
            finish();
            return true;
        }
        if (id == R.id.action_refresh) { runQuery(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void runQuery() {
        tvEmpty.setVisibility(View.GONE);
        executor.execute(() -> {
            StatisticsResult r = StatisticsEngine.execute(this, config);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (r.errorMessage != null) {
                    tvEmpty.setText(r.errorMessage);
                    tvEmpty.setVisibility(View.VISIBLE);
                    chartView.setVisibility(View.GONE);
                    recyclerTable.setVisibility(View.GONE);
                    return;
                }
                if (r.isEmpty()) {
                    tvEmpty.setText(R.string.stats_empty_result);
                    tvEmpty.setVisibility(View.VISIBLE);
                    chartView.setVisibility(View.GONE);
                    recyclerTable.setVisibility(View.GONE);
                    return;
                }

                ViewType vt = config.viewType;
                if (vt == ViewType.BAR || vt == ViewType.PIE || vt == ViewType.LINE) {
                    chartView.setViewType(vt);
                    chartView.setResult(r);
                    chartView.setVisibility(View.VISIBLE);
                } else {
                    chartView.setVisibility(View.GONE);
                }

                if (vt == ViewType.TABLE || vt == ViewType.BAR
                        || vt == ViewType.PIE || vt == ViewType.LINE) {
                    tableAdapter.setResult(r);
                    recyclerTable.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}