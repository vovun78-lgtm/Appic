package com.example.appic.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.R;
import com.example.appic.adapters.SearchResultsAdapter;
import com.example.appic.database.DatabaseHelper;
import com.example.appic.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class GlobalSearchActivity extends BaseActivity {

    private static final String TAG = "GlobalSearchActivity";

    private RecyclerView recyclerView;
    private SearchResultsAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private SearchView searchView;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private static final long SEARCH_DEBOUNCE_MS = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_global_search);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applyToToolbar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.global_search);
        }

        recyclerView = findViewById(R.id.recycler_search_results);
        progressBar = findViewById(R.id.progress_bar);
        emptyView = findViewById(R.id.empty_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchResultsAdapter(new ArrayList<>(), this::onResultClicked);
        recyclerView.setAdapter(adapter);

        searchView = findViewById(R.id.search_view);
        searchView.setQueryHint(getString(R.string.search_hint));

        // === ЗАМЕНА ИКОНКИ ЛУПЫ В РАЗВЁРНУТОМ СОСТОЯНИИ ===
        // Внутри SearchView есть ImageView с системным ID search_mag_icon,
        // который отвечает за иконку в уже открытом поле ввода.
        ImageView searchMagIcon = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
        if (searchMagIcon != null) {
            searchMagIcon.setImageResource(R.drawable.ic_search);
        }
        // =================================================

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (pendingSearch != null) {
                    uiHandler.removeCallbacks(pendingSearch);
                    pendingSearch = null;
                }
                performSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (pendingSearch != null) {
                    uiHandler.removeCallbacks(pendingSearch);
                }
                pendingSearch = () -> performSearch(newText);
                uiHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
                return true;
            }
        });

        searchView.requestFocus();
        uiHandler.postDelayed(() -> {
            if (searchView != null && searchView.isAttachedToWindow()) {
                searchView.setIconified(false);
                searchView.requestFocusFromTouch();
            }
        }, 200);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (adapter != null) adapter.notifyDataSetChanged();
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) ThemeHelper.applyToToolbar(toolbar);
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.updateResults(new ArrayList<>());
            emptyView.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        try {
            executor.execute(() -> {
                List<SearchResultsAdapter.SearchResult> results =
                        DatabaseHelper.globalSearch(this, query.trim());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    if (results.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        emptyView.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        adapter.updateResults(results);
                    }
                });
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "search rejected: executor shutdown", e);
            progressBar.setVisibility(View.GONE);
        }
    }

    private void onResultClicked(SearchResultsAdapter.SearchResult result) {
        Intent intent = null;
        switch (result.type) {
            case SearchResultsAdapter.TYPE_MACHINE:
                intent = MachineDetailsActivity.newIntent(this, result.machineId);
                break;
            case SearchResultsAdapter.TYPE_GROUP:
                intent = GroupFieldsActivity.newIntent(this, result.machineId, result.groupId, result.title);
                break;
            case SearchResultsAdapter.TYPE_FIELD:
                intent = AddEditFieldActivity.newIntent(this, result.fieldId, result.machineId, result.groupId);
                break;
        }
        if (intent != null) {
            startActivity(intent);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }
}