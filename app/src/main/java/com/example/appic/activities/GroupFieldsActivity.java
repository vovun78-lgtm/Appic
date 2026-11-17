package com.example.appic.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.R;
import com.example.appic.adapters.FieldsAdapter;
import com.example.appic.database.DatabaseClient;
import com.example.appic.database.models.Field;
import com.example.appic.database.models.FieldTemplate;
import com.example.appic.database.models.Group;
import com.example.appic.database.repository.FieldRepository;
import com.example.appic.database.repository.GroupRepository;
import com.example.appic.utils.ExportImportHelper;
import com.example.appic.utils.MaterialProgressDialog;
import com.example.appic.utils.ThemeHelper;
import com.example.appic.viewmodels.ExportViewModel;
import com.example.appic.viewmodels.ImportViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class GroupFieldsActivity extends BaseActivity implements FieldsAdapter.OnStartDragListener {

    private static final String TAG = "GroupFieldsActivity";
    private static final String KEY_EXPORTING_FIELD_ID = "exporting_field_id";
    private static final long SHARE_FILE_LIFETIME_MS = 5L * 60 * 1000;

    private int machineId;
    private int groupId;
    private String groupName;
    private RecyclerView recycler;
    private FloatingActionButton btnAdd;
    private FieldRepository fieldRepository;
    private GroupRepository groupRepository;
    private DatabaseClient dbClient;
    private List<Field> fields = new ArrayList<>();
    private List<FieldTemplate> templates = new ArrayList<>();
    private FieldsAdapter adapter;
    private ItemTouchHelper itemTouchHelper;

    private volatile boolean isDragging = false;
    private volatile boolean refreshScheduled = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService lightExecutor = Executors.newSingleThreadExecutor();
    private MaterialProgressDialog progressDialog;

    private ExportViewModel exportViewModel;
    private ImportViewModel importViewModel;
    private MaterialProgressDialog importProgressDialog;

    private int exportingFieldId = -1;

    private final ActivityResultLauncher<Intent> fieldExportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && exportingFieldId != -1) {
                        int id = exportingFieldId;
                        exportingFieldId = -1;
                        exportViewModel.exportFieldToUri(uri, id);
                        return;
                    }
                }
                exportingFieldId = -1;
            });

    private final ActivityResultLauncher<Intent> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) exportViewModel.exportAll(uri);
                }
            });

    private final ActivityResultLauncher<Intent> importLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) importViewModel.startImport(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_fields);

        if (savedInstanceState != null) {
            exportingFieldId = savedInstanceState.getInt(KEY_EXPORTING_FIELD_ID, -1);
        }

        machineId = getIntent().getIntExtra("machine_id", -1);
        groupId = getIntent().getIntExtra("group_id", -1);
        groupName = getIntent().getStringExtra("group_name");

        if (machineId == -1 || groupId == -1) {
            Toast.makeText(this, R.string.error_machine_or_group_not_specified,
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applyToToolbar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(groupName);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        fieldRepository = new FieldRepository(this);
        groupRepository = new GroupRepository(this);
        dbClient = DatabaseClient.getInstance(this);

        recycler = findViewById(R.id.recycler_group_fields);
        btnAdd = findViewById(R.id.btn_add_field);

        adapter = new FieldsAdapter(
                fields,
                templates,
                field -> startActivity(AddEditFieldActivity.newIntent(
                        GroupFieldsActivity.this, field.getId(), machineId, groupId)),
                field -> { showFieldOptionsDialog(field); return true; },
                this
        );
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

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
                if (fromPos < toPos) {
                    for (int i = fromPos; i < toPos; i++) Collections.swap(fields, i, i + 1);
                } else {
                    for (int i = fromPos; i > toPos; i--) Collections.swap(fields, i, i - 1);
                }
                adapter.notifyItemMoved(fromPos, toPos);
                return true;
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    isDragging = true;
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    isDragging = false;
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                isDragging = false;
                saveFieldsOrder();
                loadFields();
            }
        };
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recycler);

        exportViewModel = new ViewModelProvider(this).get(ExportViewModel.class);
        importViewModel = new ViewModelProvider(this).get(ImportViewModel.class);
        observeExportState();
        observeImportState();

        btnAdd.setOnClickListener(v -> startActivity(
                AddEditFieldActivity.newIntent(GroupFieldsActivity.this, -1, machineId, groupId)));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_EXPORTING_FIELD_ID, exportingFieldId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isDragging = false;
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
                case EXPORT_ALL:
                case EXPORT_FIELD_TO_URI:
                    Toast.makeText(this, result.success
                                    ? R.string.archive_export_completed : R.string.archive_export_error,
                            Toast.LENGTH_SHORT).show();
                    break;
                case SHARE_FIELD:
                    if (result.success && result.sharedUri != null) shareArchive(result.sharedUri);
                    else Toast.makeText(this, R.string.status_archive_error, Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        });
    }

    private void observeImportState() {
        importViewModel.getState().observe(this, state -> {
            if (state == null) return;
            if (state.inProgress) showOrUpdateImportProgressDialog(state.progress);
            else dismissImportProgressDialog();
        });

        importViewModel.getFinishedEvent().observe(this, event -> {
            if (event == null) return;
            Boolean success = event.getContentIfNotHandled();
            if (success == null) return;
            dismissImportProgressDialog();
            if (success) {
                Toast.makeText(this, R.string.status_import_success, Toast.LENGTH_LONG).show();
                loadFields();
            } else {
                Toast.makeText(this, R.string.status_import_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void scheduleRefresh() {
        if (refreshScheduled) return;
        refreshScheduled = true;
        mainHandler.postDelayed(() -> {
            refreshScheduled = false;
            loadFields();
        }, 300);
    }

    private void loadFields() {
        try {
            lightExecutor.execute(() -> {
                List<Field> newFields = fieldRepository.getByGroupId(groupId);
                List<FieldTemplate> newTemplates = dbClient.fieldTemplateDao().getAll();
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (isDragging) { scheduleRefresh(); return; }
                    templates.clear();
                    templates.addAll(newTemplates);
                    fields.clear();
                    fields.addAll(newFields);
                    adapter.updateTemplates(templates);
                    adapter.notifyDataSetChanged();
                });
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "loadFields rejected", e);
        }
    }

    private void saveFieldsOrder() {
        final List<Field> snapshot = new ArrayList<>(fields);
        try {
            lightExecutor.execute(() -> fieldRepository.updateOrder(snapshot, groupId));
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "saveOrder rejected", e);
        }
    }

    private void showFieldOptionsDialog(Field field) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_action_field_title)
                .setItems(new String[]{
                        getString(R.string.edit),
                        getString(R.string.delete),
                        getString(R.string.share),
                        getString(R.string.save),
                        getString(R.string.action_move_to_group)
                }, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(AddEditFieldActivity.newIntent(
                                GroupFieldsActivity.this, field.getId(), machineId, groupId));
                    } else if (which == 1) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.delete_confirm)
                                .setMessage(getString(R.string.dialog_delete_field_message, field.getTitle()))
                                .setPositiveButton(R.string.yes, (d, w) -> {
                                    try {
                                        lightExecutor.execute(() -> {
                                            fieldRepository.delete(field.getId());
                                            mainHandler.post(this::loadFields);
                                        });
                                    } catch (RejectedExecutionException e) {
                                        Log.w(TAG, "delete rejected", e);
                                    }
                                })
                                .setNegativeButton(R.string.no, null)
                                .show();
                    } else if (which == 2) {
                        if (exportViewModel.isOperationInProgress()) {
                            Toast.makeText(this, R.string.status_operation_in_progress_short,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        exportViewModel.shareField(field.getId(), field.getTitle());
                    } else if (which == 3) {
                        if (exportViewModel.isOperationInProgress()) {
                            Toast.makeText(this, R.string.status_operation_in_progress_short,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        exportingFieldId = field.getId();
                        ExportImportHelper.exportFieldToUri(
                                this, fieldExportLauncher, field.getId(), field.getTitle());
                    } else if (which == 4) {
                        showMoveFieldToGroupDialog(field);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMoveFieldToGroupDialog(Field field) {
        try {
            lightExecutor.execute(() -> {
                List<Group> allGroups = groupRepository.getByMachineId(machineId);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    List<Group> candidates = new ArrayList<>();
                    for (Group g : allGroups) {
                        if (g.getId() != field.getGroupId()) candidates.add(g);
                    }

                    final boolean hasUngrouped = field.getGroupId() != 0;
                    final int groupOffset = hasUngrouped ? 1 : 0;
                    int totalItems = candidates.size() + groupOffset;

                    if (totalItems == 0) {
                        Toast.makeText(this, R.string.dialog_move_no_groups,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String[] names = new String[totalItems];
                    if (hasUngrouped) {
                        names[0] = getString(R.string.ungrouped_machines);
                    }
                    for (int i = 0; i < candidates.size(); i++) {
                        names[i + groupOffset] = candidates.get(i).getName();
                    }

                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.dialog_move_to_group_title)
                            .setItems(names, (d, idx) -> {
                                int targetGroupId;
                                if (hasUngrouped && idx == 0) {
                                    targetGroupId = 0;
                                } else {
                                    targetGroupId = candidates.get(idx - groupOffset).getId();
                                }
                                if (targetGroupId == field.getGroupId()) return;
                                try {
                                    lightExecutor.execute(() -> {
                                        fieldRepository.moveToGroup(field.getId(), targetGroupId);
                                        mainHandler.post(this::loadFields);
                                    });
                                } catch (RejectedExecutionException e) {
                                    Log.w(TAG, "move field rejected", e);
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "load groups rejected", e);
        }
    }

    private void shareArchive(Uri uri) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/zip");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent,
                getString(R.string.archive_send_chooser_title)));
        final Uri toCleanup = uri;
        mainHandler.postDelayed(
                () -> ExportImportHelper.cleanupTempShareFile(toCleanup),
                SHARE_FILE_LIFETIME_MS);
    }

    @Override
    public void onStartDrag(FieldsAdapter.ViewHolder viewHolder) {
        if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFields();
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) ThemeHelper.applyToToolbar(toolbar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        dismissProgressDialog();
        dismissImportProgressDialog();
        lightExecutor.shutdownNow();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) { finish(); return true; }
        if (id == R.id.action_settings) return super.onOptionsItemSelected(item);

        if (id == R.id.action_export || id == R.id.action_export_date_range
                || id == R.id.action_import_data) {
            if (isAnyOperationInProgress()) {
                Toast.makeText(this, R.string.status_operation_in_progress, Toast.LENGTH_SHORT).show();
                return true;
            }
        }

        if (id == R.id.action_export) {
            showExportFileNameDialog();
            return true;
        } else if (id == R.id.action_export_date_range) {
            startActivity(new Intent(this, ExportDateRangeActivity.class));
            return true;
        } else if (id == R.id.action_import_data) {
            showImportWarningDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isAnyOperationInProgress() {
        return exportViewModel.isOperationInProgress() || importViewModel.isImportInProgress();
    }

    /**
     * Создаёт поле ввода в обёртке. Возвращает FrameLayout —
     * именно его можно передавать в setView(). Сам TextInputLayout уже
     * имеет родителя (wrapper), повторно вставлять его нельзя.
     */
    private FrameLayout buildInput(String hint) {
        TextInputEditText et = new TextInputEditText(this);
        et.setSingleLine(true);
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

    private void showExportFileNameDialog() {
        FrameLayout wrapper = buildInput(getString(R.string.archive_name_dialog_title));
        TextInputEditText et = (TextInputEditText) wrapper.getTag();
        if (et != null) {
            et.setText(getString(R.string.archive_default_name));
            ThemeHelper.applyToEditText(et);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.archive_name_dialog_title)
                .setView(wrapper)
                .setPositiveButton(R.string.archive_export_btn, (dialog, which) -> {
                    String fileName = et != null ? et.getText().toString().trim() : "";
                    if (fileName.isEmpty()) fileName = getString(R.string.archive_default_name);
                    if (!fileName.endsWith(".zip")) fileName += ".zip";
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/zip");
                    intent.putExtra(Intent.EXTRA_TITLE, fileName);
                    exportLauncher.launch(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showImportWarningDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_import_title)
                .setMessage(R.string.dialog_import_message)
                .setPositiveButton(R.string.dialog_import_continue, (d, w) ->
                        ExportImportHelper.importData(this, importLauncher))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showOrUpdateImportProgressDialog(int percent) {
        if (importProgressDialog == null || !importProgressDialog.isShowing()) {
            if (isFinishing() || isDestroyed()) return;
            importProgressDialog = new MaterialProgressDialog(this);
            importProgressDialog.setProgressStyle(MaterialProgressDialog.STYLE_HORIZONTAL);
            importProgressDialog.setMessage(getString(R.string.progress_importing));
            importProgressDialog.setCancelable(false);
            importProgressDialog.setMax(100);
            try { importProgressDialog.show(); } catch (Exception ignored) {}
        }
        importProgressDialog.setProgress(percent);
        importProgressDialog.setMessage(getString(R.string.progress_importing_percent, percent));
    }

    private void dismissImportProgressDialog() {
        if (importProgressDialog != null && importProgressDialog.isShowing()) {
            try { importProgressDialog.dismiss(); } catch (Exception ignored) {}
        }
        importProgressDialog = null;
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

    public static Intent newIntent(android.content.Context context,
                                   int machineId, int groupId, String groupName) {
        Intent i = new Intent(context, GroupFieldsActivity.class);
        i.putExtra("machine_id", machineId);
        i.putExtra("group_id", groupId);
        i.putExtra("group_name", groupName);
        return i;
    }
}