package com.example.appic.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModelProvider;

import com.example.appic.R;
import com.example.appic.utils.ExportImportHelper;
import com.example.appic.utils.MaterialProgressDialog;
import com.example.appic.utils.ThemeHelper;
import com.example.appic.viewmodels.ExportViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

public class ExportDateRangeActivity extends BaseActivity {

    private static final String KEY_START = "start_date";
    private static final String KEY_END = "end_date";
    private static final long NO_DATE = -1L;
    private static final long MS_PER_DAY = 86_400_000L;
    private static final long SHARE_FILE_LIFETIME_MS = 5L * 60 * 1000;

    private TextView tvSelectedRange;
    private Button btnExportSave, btnExportShare;
    private MaterialButton btnSelectDates;

    private Long startDate = null;
    private Long endDate = null;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MaterialProgressDialog progressDialog;

    private ExportViewModel exportViewModel;

    private final ActivityResultLauncher<Intent> saveLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && startDate != null && endDate != null) {
                        exportViewModel.exportFiltered(uri, startDate, endDate);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export_date_range);

        if (savedInstanceState != null) {
            long s = savedInstanceState.getLong(KEY_START, NO_DATE);
            long e = savedInstanceState.getLong(KEY_END, NO_DATE);
            if (s != NO_DATE) startDate = s;
            if (e != NO_DATE) endDate = e;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applyToToolbar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.export_date_title);
        }

        tvSelectedRange = findViewById(R.id.tv_selected_range);
        btnSelectDates = findViewById(R.id.btn_select_dates);
        btnExportSave = findViewById(R.id.btn_export_save);
        btnExportShare = findViewById(R.id.btn_export_share);

        exportViewModel = new ViewModelProvider(this).get(ExportViewModel.class);
        observeExportState();
        updateDateDisplay();

        btnSelectDates.setOnClickListener(v -> showDateRangePicker());
        btnExportSave.setOnClickListener(v -> {
            if (exportViewModel.isOperationInProgress()) {
                Toast.makeText(this, R.string.status_operation_in_progress_short, Toast.LENGTH_SHORT).show();
                return;
            }
            if (validateDates()) showSaveFileNameDialog();
        });
        btnExportShare.setOnClickListener(v -> {
            if (exportViewModel.isOperationInProgress()) {
                Toast.makeText(this, R.string.status_operation_in_progress_short, Toast.LENGTH_SHORT).show();
                return;
            }
            if (validateDates()) showShareFileNameDialog();
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_START, startDate != null ? startDate : NO_DATE);
        outState.putLong(KEY_END, endDate != null ? endDate : NO_DATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        dismissProgressDialog();
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void observeExportState() {
        exportViewModel.getState().observe(this, s -> {
            if (s == null) return;
            if (s.inProgress) showProgressDialog(s.message);
            else dismissProgressDialog();
        });

        exportViewModel.getResultEvent().observe(this, event -> {
            if (event == null) return;
            ExportViewModel.ExportResult result = event.getContentIfNotHandled();
            if (result == null) return;
            dismissProgressDialog();

            switch (result.operation) {
                case EXPORT_FILTERED:
                    Toast.makeText(this, result.success
                            ? R.string.archive_export_completed : R.string.archive_export_error,
                            Toast.LENGTH_SHORT).show();
                    if (result.success) finish();
                    break;
                case SHARE_FILTERED:
                    if (result.success && result.sharedUri != null) shareArchive(result.sharedUri);
                    else Toast.makeText(this, R.string.status_archive_error, Toast.LENGTH_SHORT).show();
                    break;
                default: break;
            }
        });
    }

    private void showDateRangePicker() {
        MaterialDatePicker.Builder<Pair<Long, Long>> builder =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText(R.string.export_date_range_title);

        if (startDate != null && endDate != null) {
            builder.setSelection(new Pair<>(
                    localDayStartToUtcMillis(startDate),
                    localDayStartToUtcMillis(endDate)));
        }

        MaterialDatePicker<Pair<Long, Long>> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            Long s = selection.first;
            Long e = selection.second;
            if (s != null) startDate = utcDayStartToLocalMillis(s);
            if (e != null) endDate = utcDayEndToLocalMillis(e);
            updateDateDisplay();
        });
        picker.show(getSupportFragmentManager(), "dateRangePicker");
    }

    private long utcDayStartToLocalMillis(long utcMidnightMillis) {
        LocalDate day = LocalDate.ofEpochDay(utcMidnightMillis / MS_PER_DAY);
        return day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private long utcDayEndToLocalMillis(long utcMidnightMillis) {
        LocalDate day = LocalDate.ofEpochDay(utcMidnightMillis / MS_PER_DAY);
        return day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1L;
    }

    private long localDayStartToUtcMillis(long localMillis) {
        LocalDate day = LocalDate.ofInstant(new Date(localMillis).toInstant(), ZoneId.systemDefault());
        return day.toEpochDay() * MS_PER_DAY;
    }

    private void updateDateDisplay() {
        if (startDate != null && endDate != null) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault());
            String startStr = LocalDate.ofInstant(new Date(startDate).toInstant(), ZoneId.systemDefault()).format(fmt);
            String endStr = LocalDate.ofInstant(new Date(endDate).toInstant(), ZoneId.systemDefault()).format(fmt);
            if (startStr.equals(endStr)) tvSelectedRange.setText(getString(R.string.export_date_single, startStr));
            else tvSelectedRange.setText(getString(R.string.export_date_period, startStr, endStr));
        } else {
            tvSelectedRange.setText(R.string.export_date_not_selected);
        }
    }

    private boolean validateDates() {
        if (startDate == null || endDate == null) {
            Toast.makeText(this, R.string.export_date_not_chosen_toast, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * Создаёт поле ввода в обёртке. Возвращает FrameLayout —
     * именно его можно передавать в setView(). Сам TextInputLayout уже
     * имеет родителя (wrapper), повторно вставлять его нельзя.
     */
    private FrameLayout buildInput(String hint, String initialValue) {
        TextInputEditText et = new TextInputEditText(this);
        et.setSingleLine(true);
        if (initialValue != null) et.setText(initialValue);
        ThemeHelper.applyToEditText(et);
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(hint);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.addView(et);
        FrameLayout wrapper = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        wrapper.setPadding(pad, 0, pad, 0);
        wrapper.addView(til);
        wrapper.setTag(et);
        return wrapper;
    }

    private void showSaveFileNameDialog() {
        String defaultName = getString(R.string.archive_default_name_date, System.currentTimeMillis());
        FrameLayout wrapper = buildInput(getString(R.string.archive_name_for_save_title), defaultName);
        TextInputEditText et = (TextInputEditText) wrapper.getTag();

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.archive_name_for_save_title)
                .setView(wrapper)
                .setPositiveButton(R.string.archive_save_btn, (dialog, which) -> {
                    String fileName = et != null ? et.getText().toString().trim() : "";
                    if (fileName.isEmpty()) fileName = defaultName;
                    if (!fileName.endsWith(".zip")) fileName += ".zip";

                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/zip");
                    intent.putExtra(Intent.EXTRA_TITLE, fileName);
                    saveLauncher.launch(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showShareFileNameDialog() {
        String defaultName = getString(R.string.archive_default_name_date, System.currentTimeMillis());
        FrameLayout wrapper = buildInput(getString(R.string.archive_name_for_share_title), defaultName);
        TextInputEditText et = (TextInputEditText) wrapper.getTag();

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.archive_name_for_share_title)
                .setView(wrapper)
                .setPositiveButton(R.string.archive_share_btn, (dialog, which) -> {
                    String fileName = et != null ? et.getText().toString().trim() : "";
                    if (fileName.isEmpty()) fileName = defaultName;
                    if (!fileName.endsWith(".zip")) fileName += ".zip";
                    exportViewModel.shareFiltered(startDate, endDate, fileName);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void shareArchive(Uri uri) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/zip");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.archive_send_chooser_title)));

        final Uri toCleanup = uri;
        mainHandler.postDelayed(() -> ExportImportHelper.cleanupTempShareFile(toCleanup), SHARE_FILE_LIFETIME_MS);
    }

    private void showProgressDialog(String message) {
        if (isFinishing() || isDestroyed()) return;
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        progressDialog = new MaterialProgressDialog(this);
        progressDialog.setMessage(message);
        progressDialog.setCancelable(false);
        try { progressDialog.show(); } catch (Exception ignored) {}
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try { progressDialog.dismiss(); } catch (Exception ignored) {}
        }
    }
}