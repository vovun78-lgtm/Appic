package com.example.appic.activities;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.R;
import com.example.appic.adapters.HomeAdapter;
import com.example.appic.database.models.Machine;
import com.example.appic.database.models.MachineGroup;
import com.example.appic.database.repository.MachineGroupRepository;
import com.example.appic.database.repository.MachineRepository;
import com.example.appic.utils.ExportImportHelper;
import com.example.appic.utils.MaterialProgressDialog;
import com.example.appic.utils.TermsHelper;
import com.example.appic.utils.TextCapitalizer;
import com.example.appic.utils.ThemeHelper;
import com.example.appic.viewmodels.ExportViewModel;
import com.example.appic.viewmodels.ImportViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MainActivity extends BaseActivity implements HomeAdapter.OnStartDragListener {

    private static final String TAG = "MainActivity";
    private static final String KEY_EXPORTING_MACHINE_ID = "exporting_machine_id";
    private static final String KEY_EXPORTING_MACHINE_GROUP_ID = "exporting_machine_group_id";
    private static final long SHARE_FILE_LIFETIME_MS = 5L * 60 * 1000;

    private RecyclerView recycler;
    private FloatingActionButton btnAdd;
    private FloatingActionButton btnAddGroup;
    private HomeAdapter adapter;
    private MachineRepository machineRepository;
    private MachineGroupRepository machineGroupRepository;
    private List<Object> items = new ArrayList<>();
    private ItemTouchHelper itemTouchHelper;

    private volatile boolean isDragging = false;
    private volatile boolean refreshScheduled = false;

    private float lastDraggedCenterX = 0;
    private float lastDraggedCenterY = 0;
    private boolean hasDraggedPosition = false;

    private final ExecutorService lightExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MaterialProgressDialog progressDialog;

    private ExportViewModel exportViewModel;
    private ImportViewModel importViewModel;
    private MaterialProgressDialog importProgressDialog;

    private int exportingMachineId = -1;
    private int exportingMachineGroupId = -1;

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

    private final ActivityResultLauncher<Intent> machineGroupSaveLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && exportingMachineGroupId != -1) {
                        int id = exportingMachineGroupId;
                        exportingMachineGroupId = -1;
                        exportViewModel.exportMachineGroupToUri(uri, id);
                        return;
                    }
                }
                exportingMachineGroupId = -1;
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
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            exportingMachineId = savedInstanceState.getInt(KEY_EXPORTING_MACHINE_ID, -1);
            exportingMachineGroupId = savedInstanceState.getInt(KEY_EXPORTING_MACHINE_GROUP_ID, -1);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        updateToolbarTitle();
        ThemeHelper.applyToToolbar(toolbar);

        machineRepository = new MachineRepository(this);
        machineGroupRepository = new MachineGroupRepository(this);

        recycler = findViewById(R.id.recycler_machines);
        btnAdd = findViewById(R.id.btn_add_machine);
        btnAddGroup = findViewById(R.id.btn_add_machine_group);

        adapter = new HomeAdapter(
                items,
                group -> startActivity(MachineGroupActivity.newIntent(this, group.getId(), group.getName())),
                this::showMachineGroupOptionsDialog,
                machine -> startActivity(MachineDetailsActivity.newIntent(this, machine.getId())),
                this::showMachineOptionsDialog,
                this);
        recycler.setAdapter(adapter);

        setupDragAndDrop();
        btnAdd.setOnClickListener(v -> startActivity(AddMachineActivity.newIntent(this, -1, "")));
        btnAddGroup.setOnClickListener(v -> showCreateMachineGroupDialog());

        exportViewModel = new ViewModelProvider(this).get(ExportViewModel.class);
        importViewModel = new ViewModelProvider(this).get(ImportViewModel.class);
        observeExportState();
        observeImportState();
    }

    private void updateToolbarTitle() {
        if (getSupportActionBar() == null) return;
        String appName = TermsHelper.getAppName(this);
        String machinePlural = TermsHelper.getMachinePlural(this);

        if (machinePlural == null || machinePlural.trim().isEmpty()) {
            getSupportActionBar().setTitle(appName);
            return;
        }

        String full = appName + " · " + machinePlural;
        SpannableString ss = new SpannableString(full);
        ss.setSpan(new StyleSpan(Typeface.BOLD), 0, appName.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        getSupportActionBar().setTitle(ss);
    }

    private void setupDragAndDrop() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean isLongPressDragEnabled() { return false; }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAbsoluteAdapterPosition();
                int toPos = target.getAbsoluteAdapterPosition();
                if (fromPos == toPos || fromPos == RecyclerView.NO_POSITION
                        || toPos == RecyclerView.NO_POSITION) return false;
                int fromType = viewHolder.getItemViewType();
                int toType = target.getItemViewType();
                if (fromType == HomeAdapter.TYPE_MACHINE_GROUP && toType == HomeAdapter.TYPE_MACHINE_GROUP) {
                    adapter.onItemMove(fromPos, toPos); return true;
                }
                if (fromType == HomeAdapter.TYPE_MACHINE && toType == HomeAdapter.TYPE_MACHINE) {
                    adapter.onItemMove(fromPos, toPos); return true;
                }
                return false;
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                    View draggedView = viewHolder.itemView;
                    lastDraggedCenterX = draggedView.getX() + draggedView.getWidth() / 2f;
                    lastDraggedCenterY = draggedView.getY() + draggedView.getHeight() / 2f;
                    hasDraggedPosition = true;
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                viewHolder.itemView.animate().cancel();
                viewHolder.itemView.setTranslationX(0);
                viewHolder.itemView.setTranslationY(0);
                int currentPos = viewHolder.getAbsoluteAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION
                        && recyclerView.getLayoutManager() != null) {
                    recyclerView.getLayoutManager().scrollToPosition(currentPos);
                }
                isDragging = false;
                saveOrders();
                if (hasDraggedPosition && viewHolder instanceof HomeAdapter.MachineViewHolder) {
                    View targetView = recyclerView.findChildViewUnder(lastDraggedCenterX, lastDraggedCenterY);
                    if (targetView != null && targetView != viewHolder.itemView) {
                        RecyclerView.ViewHolder targetHolder = recyclerView.getChildViewHolder(targetView);
                        if (targetHolder instanceof HomeAdapter.GroupViewHolder) {
                            int targetPos = targetHolder.getAbsoluteAdapterPosition();
                            if (targetPos != RecyclerView.NO_POSITION && targetPos < items.size()) {
                                Object obj = items.get(targetPos);
                                if (obj instanceof MachineGroup) {
                                    MachineGroup targetGroup = (MachineGroup) obj;
                                    int draggedPos = viewHolder.getAbsoluteAdapterPosition();
                                    if (draggedPos != RecyclerView.NO_POSITION
                                            && draggedPos < items.size()
                                            && items.get(draggedPos) instanceof Machine) {
                                        Machine draggedMachine = (Machine) items.get(draggedPos);
                                        hasDraggedPosition = false;
                                        showMoveToGroupDialog(draggedMachine, targetGroup);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
                hasDraggedPosition = false;
                loadItems();
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    isDragging = true;
                    hasDraggedPosition = false;
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    isDragging = false;
                }
            }
        };
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recycler);
    }

    private void scheduleRefresh() {
        if (refreshScheduled) return;
        refreshScheduled = true;
        mainHandler.postDelayed(() -> { refreshScheduled = false; loadItems(); }, 300);
    }

    private void saveOrders() {
        final List<MachineGroup> groupSnapshot = new ArrayList<>();
        final List<Machine> ungroupedSnapshot = new ArrayList<>();
        for (Object obj : items) {
            if (obj instanceof MachineGroup) groupSnapshot.add((MachineGroup) obj);
            else if (obj instanceof Machine) ungroupedSnapshot.add((Machine) obj);
        }
        try {
            lightExecutor.execute(() -> {
                if (!groupSnapshot.isEmpty()) machineGroupRepository.updateOrder(groupSnapshot);
                if (!ungroupedSnapshot.isEmpty()) machineRepository.updateOrder(ungroupedSnapshot);
            });
        } catch (RejectedExecutionException e) { Log.w(TAG, "saveOrders rejected", e); }
    }

    @Override public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_EXPORTING_MACHINE_ID, exportingMachineId);
        outState.putInt(KEY_EXPORTING_MACHINE_GROUP_ID, exportingMachineGroupId);
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
                case EXPORT_MACHINE_GROUP_TO_URI:
                    Toast.makeText(this, result.success
                            ? R.string.archive_export_completed : R.string.archive_export_error,
                            Toast.LENGTH_SHORT).show();
                    break;
                case SHARE_MACHINE:
                case SHARE_MACHINE_GROUP:
                    if (result.success && result.sharedUri != null) shareArchive(result.sharedUri);
                    else Toast.makeText(this, R.string.status_archive_error, Toast.LENGTH_SHORT).show();
                    break;
                default: break;
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
                loadItems();
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

    private void loadItems() {
        if (machineGroupRepository == null || machineRepository == null) return;
        try {
            lightExecutor.execute(() -> {
                List<Object> newItems = new ArrayList<>();
                List<MachineGroup> groups = machineGroupRepository.getAll();
                List<Machine> ungrouped = machineRepository.getUngrouped();
                newItems.addAll(groups);
                if (!groups.isEmpty() && !ungrouped.isEmpty()) {
                    newItems.add(new HomeAdapter.UngroupedHeader());
                }
                newItems.addAll(ungrouped);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (isDragging) { scheduleRefresh(); return; }
                    items.clear();
                    items.addAll(newItems);
                    adapter.notifyDataSetChanged();
                });
            });
        } catch (RejectedExecutionException e) { Log.w(TAG, "loadItems rejected", e); }
    }

    private void showMoveToGroupDialog(Machine machine, MachineGroup group) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_move_to_group_title)
                .setMessage(getString(R.string.dialog_move_machine_to_group_message,
                        machine.getName(), group.getName()))
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    try {
                        lightExecutor.execute(() -> {
                            machineRepository.moveToGroup(machine.getId(), group.getId());
                            mainHandler.post(this::loadItems);
                        });
                    } catch (RejectedExecutionException e) { Log.w(TAG, "moveToGroup rejected", e); }
                })
                .setNegativeButton(R.string.no, (dialog, which) -> loadItems())
                .show();
    }

    private void showMachineGroupOptionsDialog(MachineGroup group) {
        String[] options = {
                getString(R.string.edit), getString(R.string.delete),
                getString(R.string.share), getString(R.string.save)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_action_group_machines_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showEditMachineGroupDialog(group);
                    else if (which == 1) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.delete_confirm)
                                .setMessage(getString(R.string.dialog_delete_group_message, group.getName()))
                                .setPositiveButton(R.string.yes, (d, w) -> {
                                    try {
                                        lightExecutor.execute(() -> {
                                            try { machineGroupRepository.delete(group.getId()); }
                                            catch (Exception e) { Log.e(TAG, "delete group failed", e); }
                                            mainHandler.post(this::loadItems);
                                        });
                                    } catch (RejectedExecutionException e) { Log.w(TAG, "delete rejected", e); }
                                })
                                .setNegativeButton(R.string.no, null)
                                .show();
                    } else if (which == 2) {
                        if (exportViewModel.isOperationInProgress()) {
                            Toast.makeText(this, R.string.status_operation_in_progress_short, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        exportViewModel.shareMachineGroup(group.getId(), group.getName());
                    } else if (which == 3) {
                        if (exportViewModel.isOperationInProgress()) {
                            Toast.makeText(this, R.string.status_operation_in_progress_short, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        exportingMachineGroupId = group.getId();
                        ExportImportHelper.exportMachineGroupToUri(
                                this, machineGroupSaveLauncher, group.getId(), group.getName());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Создаёт поле ввода, обёрнутое во FrameLayout с отступами.
     * Возвращает именно FrameLayout — потому что именно его можно безопасно
     * передавать в MaterialAlertDialogBuilder.setView(). Сам TextInputLayout
     * уже имеет родителя (wrapper), и повторно вставлять его в диалог нельзя —
     * это приводит к IllegalStateException "child already has a parent".
     * TextInputEditText достаётся через wrapper.getTag().
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

    private void showEditMachineGroupDialog(MachineGroup group) {
        FrameLayout wrapper = buildInput(getString(R.string.dialog_edit_group_title));
        TextInputEditText et = (TextInputEditText) wrapper.getTag();
        if (et != null) {
            et.setText(group.getName());
            TextCapitalizer.apply(et);
            ThemeHelper.applyToEditText(et);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_edit_group_title)
                .setView(wrapper)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    if (et == null) return;
                    String newName = TextCapitalizer.capitalizeSentences(et.getText().toString().trim());
                    if (!newName.isEmpty()) {
                        group.setName(newName);
                        try {
                            lightExecutor.execute(() -> {
                                machineGroupRepository.update(group);
                                mainHandler.post(this::loadItems);
                            });
                        } catch (RejectedExecutionException e) { Log.w(TAG, "update group rejected", e); }
                    } else {
                        Toast.makeText(this, R.string.dialog_group_name_empty, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMachineOptionsDialog(Machine machine) {
        String machineSingle = TermsHelper.getMachineSingle(this).toLowerCase();
        String[] options = {
                getString(R.string.edit), getString(R.string.delete),
                getString(R.string.share), getString(R.string.save),
                getString(R.string.action_move_to_group)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_action_machine_title) + " " + machineSingle)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(AddMachineActivity.newIntent(this, machine.getId(), machine.getName()));
                    } else if (which == 1) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.delete_confirm)
                                .setMessage(getString(R.string.dialog_delete_machine_message, machine.getName()))
                                .setPositiveButton(R.string.yes, (d, w) -> {
                                    final int id = machine.getId();
                                    try {
                                        lightExecutor.execute(() -> {
                                            try { machineRepository.delete(id); }
                                            catch (Exception e) { Log.e(TAG, "delete machine failed", e); }
                                            mainHandler.post(MainActivity.this::loadItems);
                                        });
                                    } catch (RejectedExecutionException e) { Log.w(TAG, "delete rejected", e); }
                                })
                                .setNegativeButton(R.string.no, null)
                                .show();
                    } else if (which == 2) {
                        if (exportViewModel.isOperationInProgress()) {
                            Toast.makeText(this, R.string.status_operation_in_progress_short, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        exportViewModel.shareMachine(machine.getId(), machine.getName());
                    } else if (which == 3) {
                        if (exportViewModel.isOperationInProgress()) {
                            Toast.makeText(this, R.string.status_operation_in_progress_short, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        exportingMachineId = machine.getId();
                        ExportImportHelper.exportMachineToUri(
                                this, machineSaveLauncher, machine.getId(), machine.getName());
                    } else if (which == 4) {
                        showMoveMachineToGroupDialog(machine);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMoveMachineToGroupDialog(Machine machine) {
        try {
            lightExecutor.execute(() -> {
                List<MachineGroup> groups = machineGroupRepository.getAll();
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (groups.isEmpty()) {
                        Toast.makeText(this, R.string.dialog_move_no_groups, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] names = new String[groups.size()];
                    for (int i = 0; i < groups.size(); i++) names[i] = groups.get(i).getName();
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.dialog_move_to_group_title)
                            .setItems(names, (d, idx) -> {
                                MachineGroup target = groups.get(idx);
                                try {
                                    lightExecutor.execute(() -> {
                                        machineRepository.moveToGroup(machine.getId(), target.getId());
                                        mainHandler.post(MainActivity.this::loadItems);
                                    });
                                } catch (RejectedExecutionException e) { Log.w(TAG, "move rejected", e); }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
            });
        } catch (RejectedExecutionException e) { Log.w(TAG, "load groups rejected", e); }
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

    @Override
    protected void onResume() {
        super.onResume();
        updateToolbarTitle();
        loadItems();
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) ThemeHelper.applyToToolbar(toolbar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        lightExecutor.shutdownNow();
        dismissProgressDialog();
        dismissImportProgressDialog();
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
        if (id == R.id.action_settings) return super.onOptionsItemSelected(item);

        if (id == R.id.action_export || id == R.id.action_export_date_range
                || id == R.id.action_import_data) {
            if (isAnyOperationInProgress()) {
                Toast.makeText(this, R.string.status_operation_in_progress, Toast.LENGTH_SHORT).show();
                return true;
            }
        }

        if (id == R.id.action_export) { showExportFileNameDialog(); return true; }
        else if (id == R.id.action_export_date_range) {
            startActivity(new Intent(this, ExportDateRangeActivity.class)); return true;
        } else if (id == R.id.action_import_data) { showImportWarningDialog(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private boolean isAnyOperationInProgress() {
        return exportViewModel.isOperationInProgress() || importViewModel.isImportInProgress();
    }

    private void showCreateMachineGroupDialog() {
        FrameLayout wrapper = buildInput(getString(R.string.dialog_new_group_title));
        TextInputEditText et = (TextInputEditText) wrapper.getTag();
        if (et != null) {
            TextCapitalizer.apply(et);
            ThemeHelper.applyToEditText(et);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_new_group_title)
                .setView(wrapper)
                .setPositiveButton(R.string.create, (dialog, which) -> {
                    if (et == null) return;
                    String name = TextCapitalizer.capitalizeSentences(et.getText().toString().trim());
                    if (!name.isEmpty()) {
                        try {
                            lightExecutor.execute(() -> {
                                machineGroupRepository.insertWithOrder(new MachineGroup(0, name, 0));
                                mainHandler.post(this::loadItems);
                            });
                        } catch (RejectedExecutionException e) { Log.w(TAG, "create group rejected", e); }
                    } else {
                        Toast.makeText(this, R.string.dialog_enter_name, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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