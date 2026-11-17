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
import com.example.appic.adapters.MachinesAdapter;
import com.example.appic.database.models.Machine;
import com.example.appic.database.models.MachineGroup;
import com.example.appic.database.repository.MachineGroupRepository;
import com.example.appic.database.repository.MachineRepository;
import com.example.appic.utils.ExportImportHelper;
import com.example.appic.utils.MaterialProgressDialog;
import com.example.appic.utils.TextCapitalizer;
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

public class MachineGroupActivity extends BaseActivity implements MachinesAdapter.OnStartDragListener {

    private static final String TAG = "MachineGroupActivity";
    private static final long SHARE_FILE_LIFETIME_MS = 5L * 60 * 1000;
    private static final String KEY_GROUP_NAME_SAVED = "machine_group_name_saved";
    private static final String KEY_EXPORTING_MACHINE_ID = "exporting_machine_id";

    private int groupId;
    private String groupName;
    private RecyclerView recycler;
    private FloatingActionButton btnAdd;
    private MachinesAdapter adapter;
    private MachineRepository machineRepository;
    private MachineGroupRepository machineGroupRepository;
    private List<Machine> machines = new ArrayList<>();
    private ItemTouchHelper itemTouchHelper;

    private final ExecutorService lightExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ExportViewModel exportViewModel;
    private ImportViewModel importViewModel;
    private MaterialProgressDialog progressDialog;
    private MaterialProgressDialog importProgressDialog;

    private int exportingMachineId = -1;

    private final ActivityResultLauncher<Intent> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) exportViewModel.exportAll(uri);
                }
            });

    private final ActivityResultLauncher<Intent> machineSaveLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && exportingMachineId != -1) {
                        int id = exportingMachineId;
                        exportingMachineId = -1;
                        exportViewModel.exportMachineToUri(uri, id);
                        return;
                    }
                }
                exportingMachineId = -1;
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
        setContentView(R.layout.activity_machine_group);

        groupId = getIntent().getIntExtra("machine_group_id", -1);
        groupName = getIntent().getStringExtra("machine_group_name");

        if (savedInstanceState != null) {
            String savedName = savedInstanceState.getString(KEY_GROUP_NAME_SAVED);
            if (savedName != null) groupName = savedName;
            exportingMachineId = savedInstanceState.getInt(KEY_EXPORTING_MACHINE_ID, -1);
        }

        if (groupId == -1) {
            Toast.makeText(this, R.string.error_group_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applyToToolbar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(groupName != null ? groupName : getString(R.string.type_group));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        toolbar.post(() -> {
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                if (toolbar.getChildAt(i) instanceof android.widget.TextView) {
                    toolbar.getChildAt(i).setOnLongClickListener(v -> {
                        showGroupOptionsDialog();
                        return true;
                    });
                    break;
                }
            }
        });

        machineRepository = new MachineRepository(this);
        machineGroupRepository = new MachineGroupRepository(this);

        recycler = findViewById(R.id.recycler_group_machines);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        btnAdd = findViewById(R.id.btn_add_machine);

        adapter = new MachinesAdapter(machines,
                machine -> startActivity(MachineDetailsActivity.newIntent(this, machine.getId())),
                (machine, position) -> showMachineOptionsDialog(machine),
                this);
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
                    for (int i = fromPos; i < toPos; i++) Collections.swap(machines, i, i + 1);
                } else {
                    for (int i = fromPos; i > toPos; i--) Collections.swap(machines, i, i - 1);
                }
                adapter.notifyItemMoved(fromPos, toPos);
                return true;
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                saveOrder();
            }
        };
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recycler);

        btnAdd.setOnClickListener(v ->
                startActivity(AddMachineActivity.newIntentInGroup(this, groupId)));

        exportViewModel = new ViewModelProvider(this).get(ExportViewModel.class);
        importViewModel = new ViewModelProvider(this).get(ImportViewModel.class);
        observeExportState();
        observeImportState();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_GROUP_NAME_SAVED, groupName);
        outState.putInt(KEY_EXPORTING_MACHINE_ID, exportingMachineId);
    }

    private void showGroupOptionsDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_action_group_title)
                .setItems(new String[]{
                        getString(R.string.rename),
                        getString(R.string.delete)
                }, (dialog, which) -> {
                    if (which == 0) {
                        showRenameDialog();
                    } else {
                        confirmDeleteGroup();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void saveOrder() {
        final List<Machine> snapshot = new ArrayList<>(machines);
        try {
            lightExecutor.execute(() -> machineRepository.updateOrder(snapshot));
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "saveOrder rejected", e);
        }
    }

    @Override
    public void onStartDrag(MachinesAdapter.ViewHolder viewHolder) {
        if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
    }

    private void loadMachines() {
        try {
            lightExecutor.execute(() -> {
                List<Machine> list = machineRepository.getByGroupId(groupId);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    machines.clear();
                    machines.addAll(list);
                    adapter.notifyDataSetChanged();
                });
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "loadMachines rejected", e);
        }
    }

    private void showMachineOptionsDialog(Machine machine) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_action_machine_title)
                .setItems(new String[]{
                        getString(R.string.edit),
                        getString(R.string.delete),
                        getString(R.string.share),
                        getString(R.string.save),
                        getString(R.string.action_move_to_group)
                }, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(AddMachineActivity.newIntent(this, machine.getId(), machine.getName()));
                    } else if (which == 1) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.delete_confirm)
                                .setMessage(getString(R.string.dialog_delete_machine_message, machine.getName()))
                                .setPositiveButton(R.string.yes, (d, w) -> {
                                    try {
                                        lightExecutor.execute(() -> {
                                            machineRepository.delete(machine.getId());
                                            mainHandler.post(this::loadMachines);
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
                        exportViewModel.shareMachine(machine.getId(), machine.getName());
                    } else if (which == 3) {
                        if (exportViewModel.isOperationInProgress()) {
                            Toast.makeText(this, R.string.status_operation_in_progress_short,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        exportingMachineId = machine.getId();
                        ExportImportHelper.exportMachineToUri(
                                this, machineSaveLauncher, machine.getId(), machine.getName());
                    } else if (which == 4) {
                        showMoveToOtherGroupDialog(machine);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMoveToOtherGroupDialog(Machine machine) {
        try {
            lightExecutor.execute(() -> {
                List<MachineGroup> allGroups = machineGroupRepository.getAll();
                List<MachineGroup> otherGroups = new ArrayList<>();
                for (MachineGroup g : allGroups) {
                    if (g.getId() != groupId) otherGroups.add(g);
                }
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    String[] names = new String[otherGroups.size() + 1];
                    names[0] = getString(R.string.ungrouped_machines);
                    for (int i = 0; i < otherGroups.size(); i++) {
                        names[i + 1] = otherGroups.get(i).getName();
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.dialog_move_machine_title)
                            .setItems(names, (d, idx) -> {
                                int targetGroupId = idx == 0 ? 0 : otherGroups.get(idx - 1).getId();
                                try {
                                    lightExecutor.execute(() -> {
                                        machineRepository.moveToGroup(machine.getId(), targetGroupId);
                                        mainHandler.post(this::loadMachines);
                                    });
                                } catch (RejectedExecutionException e) {
                                    Log.w(TAG, "move rejected", e);
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
    protected void onResume() {
        super.onResume();
        loadMachines();
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) ThemeHelper.applyToToolbar(toolbar);
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
                case EXPORT_MACHINE_TO_URI:
                    Toast.makeText(this, result.success
                                    ? R.string.archive_export_completed : R.string.archive_export_error,
                            Toast.LENGTH_SHORT).show();
                    break;
                case SHARE_MACHINE:
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
                loadMachines();
            } else {
                Toast.makeText(this, R.string.status_import_error, Toast.LENGTH_LONG).show();
            }
        });
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

    private void showRenameDialog() {
        FrameLayout wrapper = buildInput(getString(R.string.dialog_rename_group_title));
        TextInputEditText et = (TextInputEditText) wrapper.getTag();
        if (et != null) {
            et.setText(groupName);
            TextCapitalizer.apply(et);
            ThemeHelper.applyToEditText(et);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_rename_group_title)
                .setView(wrapper)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    if (et == null) return;
                    String newName = TextCapitalizer.capitalizeSentences(et.getText().toString().trim());
                    if (!newName.isEmpty()) {
                        try {
                            lightExecutor.execute(() -> {
                                MachineGroup g = machineGroupRepository.getById(groupId);
                                if (g == null) return;
                                g.setName(newName);
                                machineGroupRepository.update(g);
                                mainHandler.post(() -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    groupName = newName;
                                    if (getSupportActionBar() != null) getSupportActionBar().setTitle(newName);
                                });
                            });
                        } catch (RejectedExecutionException e) {
                            Log.w(TAG, "rename rejected", e);
                        }
                    } else {
                        Toast.makeText(this, R.string.dialog_group_name_empty, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDeleteGroup() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirm)
                .setMessage(getString(R.string.dialog_delete_group_message, groupName))
                .setPositiveButton(R.string.yes, (d, w) -> {
                    try {
                        lightExecutor.execute(() -> {
                            machineGroupRepository.delete(groupId);
                            mainHandler.post(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                setResult(RESULT_OK);
                                finish();
                            });
                        });
                    } catch (RejectedExecutionException e) {
                        Log.w(TAG, "delete rejected", e);
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        lightExecutor.shutdownNow();
        dismissProgressDialog();
        dismissImportProgressDialog();
    }

    public static Intent newIntent(android.content.Context context, int groupId, String groupName) {
        Intent i = new Intent(context, MachineGroupActivity.class);
        i.putExtra("machine_group_id", groupId);
        i.putExtra("machine_group_name", groupName);
        return i;
    }
}