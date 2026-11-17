package com.example.appic.activities;

import android.content.Intent;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.R;
import com.example.appic.adapters.MachineDetailsAdapter;
import com.example.appic.database.DatabaseClient;
import com.example.appic.database.models.Field;
import com.example.appic.database.models.FieldTemplate;
import com.example.appic.database.models.Group;
import com.example.appic.database.models.Machine;
import com.example.appic.database.repository.FieldRepository;
import com.example.appic.database.repository.GroupRepository;
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

public class MachineDetailsActivity extends BaseActivity implements MachineDetailsAdapter.OnStartDragListener {

    private static final String TAG = "MachineDetailsActivity";
    private static final String KEY_EXPORTING_GROUP_ID = "exporting_group_id";
    private static final String KEY_EXPORTING_FIELD_ID = "exporting_field_id";
    private static final long SHARE_FILE_LIFETIME_MS = 5L * 60 * 1000;

    private int machineId;
    private String machineName;
    private RecyclerView recycler;
    private FloatingActionButton btnAdd;
    private FloatingActionButton btnAddGroup;
    private FieldRepository fieldRepository;
    private GroupRepository groupRepository;
    private MachineRepository machineRepository;
    private DatabaseClient dbClient;
    private List<Object> items = new ArrayList<>();
    private List<FieldTemplate> templates = new ArrayList<>();
    private MachineDetailsAdapter adapter;
    private ItemTouchHelper itemTouchHelper;

    private volatile boolean isDragging = false;
    private volatile boolean refreshScheduled = false;

    private float lastDraggedCenterX = 0;
    private float lastDraggedCenterY = 0;
    private boolean hasDraggedPosition = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService lightExecutor = Executors.newSingleThreadExecutor();
    private MaterialProgressDialog progressDialog;

    private ExportViewModel exportViewModel;
    private ImportViewModel importViewModel;
    private MaterialProgressDialog importProgressDialog;

    private int exportingGroupId = -1;
    private int exportingFieldId = -1;

    private final ActivityResultLauncher<Intent> groupExportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && exportingGroupId != -1) {
                        int id = exportingGroupId;
                        exportingGroupId = -1;
                        exportViewModel.exportGroupToUri(uri, id);
                        return;
                    }
                }
                exportingGroupId = -1;
            });

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
        setContentView(R.layout.activity_machine_details);

        if (savedInstanceState != null) {
            exportingGroupId = savedInstanceState.getInt(KEY_EXPORTING_GROUP_ID, -1);
            exportingFieldId = savedInstanceState.getInt(KEY_EXPORTING_FIELD_ID, -1);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applyToToolbar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        machineId = getIntent().getIntExtra("machine_id", -1);
        if (machineId == -1) {
            Toast.makeText(this, R.string.error_machine_not_specified, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fieldRepository = new FieldRepository(this);
        groupRepository = new GroupRepository(this);
        machineRepository = new MachineRepository(this);
        dbClient = DatabaseClient.getInstance(this);

        loadMachineTitle();

        recycler = findViewById(R.id.recycler_fields);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        btnAdd = findViewById(R.id.btn_add_field);
        btnAddGroup = findViewById(R.id.btn_add_group);

        adapter = new MachineDetailsAdapter(
                items,
                templates,
                new MachineDetailsAdapter.OnGroupClickListener() {
                    @Override
                    public void onGroupClick(Group group) {
                        startActivity(GroupFieldsActivity.newIntent(
                                MachineDetailsActivity.this, machineId, group.getId(), group.getName()));
                    }

                    @Override
                    public void onGroupLongClick(Group group) {
                        showGroupOptionsDialog(group);
                    }
                },
                field -> startActivity(AddEditFieldActivity.newIntent(
                        MachineDetailsActivity.this, field.getId(), machineId, field.getGroupId())),
                field -> { showFieldOptionsDialog(field); return true; },
                this
        );
        recycler.setAdapter(adapter);

        exportViewModel = new ViewModelProvider(this).get(ExportViewModel.class);
        importViewModel = new ViewModelProvider(this).get(ImportViewModel.class);
        observeExportState();
        observeImportState();

        setupDragAndDrop();
        btnAdd.setOnClickListener(v -> startActivity(
                AddEditFieldActivity.newIntent(MachineDetailsActivity.this, -1, machineId, 0)));
        btnAddGroup.setOnClickListener(v -> showCreateGroupDialog());
    }

    private void loadMachineTitle() {
        if (getSupportActionBar() == null) return;
        String fieldPlural = TermsHelper.getFieldPlural(this);

        getSupportActionBar().setTitle(fieldPlural);

        try {
            lightExecutor.execute(() -> {
                try {
                    Machine m = machineRepository.getById(machineId);
                    final String name = m != null ? m.getName() : null;
                    mainHandler.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (getSupportActionBar() == null) return;
                        if (name == null || name.trim().isEmpty()) {
                            getSupportActionBar().setTitle(fieldPlural);
                        } else {
                            machineName = name;
                            if (TextUtils.isEmpty(fieldPlural)) {
                                getSupportActionBar().setTitle(name);
                            } else {
                                getSupportActionBar().setTitle(name + " · " + fieldPlural);
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.w(TAG, "loadMachineTitle failed", e);
                }
            });
        } catch (RejectedExecutionException ignored) {}
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_EXPORTING_GROUP_ID, exportingGroupId);
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
                case EXPORT_GROUP_TO_URI:
                case EXPORT_FIELD_TO_URI:
                    Toast.makeText(this, result.success
                                    ? R.string.archive_export_completed : R.string.archive_export_error,
                            Toast.LENGTH_SHORT).show();
                    break;
                case SHARE_GROUP:
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
                loadItems();
            } else {
                Toast.makeText(this, R.string.status_import_error, Toast.LENGTH_LONG).show();
            }
        });
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

                if (fromType == MachineDetailsAdapter.TYPE_GROUP
                        && toType == MachineDetailsAdapter.TYPE_GROUP) {
                    adapter.onItemMove(fromPos, toPos);
                    return true;
                }
                if (fromType == MachineDetailsAdapter.TYPE_FIELD
                        && toType == MachineDetailsAdapter.TYPE_FIELD) {
                    adapter.onItemMove(fromPos, toPos);
                    return true;
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

                if (hasDraggedPosition && viewHolder instanceof MachineDetailsAdapter.FieldViewHolder) {
                    View targetView = recyclerView.findChildViewUnder(lastDraggedCenterX, lastDraggedCenterY);
                    if (targetView != null && targetView != viewHolder.itemView) {
                        RecyclerView.ViewHolder targetHolder = recyclerView.getChildViewHolder(targetView);
                        if (targetHolder instanceof MachineDetailsAdapter.GroupViewHolder) {
                            int targetPos = targetHolder.getAbsoluteAdapterPosition();
                            if (targetPos != RecyclerView.NO_POSITION && targetPos < items.size()) {
                                Object obj = items.get(targetPos);
                                if (obj instanceof Group) {
                                    Group targetGroup = (Group) obj;
                                    int draggedPos = viewHolder.getAbsoluteAdapterPosition();
                                    if (draggedPos != RecyclerView.NO_POSITION
                                            && draggedPos < items.size()
                                            && items.get(draggedPos) instanceof Field) {
                                        Field draggedField = (Field) items.get(draggedPos);
                                        hasDraggedPosition = false;
                                        showMoveToGroupDialog(draggedField, targetGroup);
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
        mainHandler.postDelayed(() -> {
            refreshScheduled = false;
            loadItems();
        }, 300);
    }

    private void showMoveToGroupDialog(Field field, Group group) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_move_to_group_title)
                .setMessage(getString(R.string.dialog_move_field_to_group_message,
                        field.getTitle(), group.getName()))
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    try {
                        lightExecutor.execute(() -> {
                            fieldRepository.moveToGroup(field.getId(), group.getId());
                            mainHandler.post(this::loadItems);
                        });
                    } catch (RejectedExecutionException e) {
                        Log.w(TAG, "move rejected", e);
                    }
                })
                .setNegativeButton(R.string.no, (dialog, which) -> loadItems())
                .show();
    }

    /**
     * ИСПРАВЛЕНО (3.2.2): добавлен пункт «Без группы» для полей, которые уже
     * находятся в группе. Ранее пользователь мог переместить запись только
     * в другую группу, но не мог вынести её из группы — приходилось заходить
     * внутрь группы и делать move там. Логика портирована из
     * GroupFieldsActivity.showMoveFieldToGroupDialog.
     */
    private void showMoveFieldToGroupDialog(Field field) {
        try {
            lightExecutor.execute(() -> {
                List<Group> groups = groupRepository.getByMachineId(machineId);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    List<Group> candidates = new ArrayList<>();
                    for (Group g : groups) {
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
                                        mainHandler.post(this::loadItems);
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

    private void loadItems() {
        try {
            lightExecutor.execute(() -> {
                List<Object> newItems = new ArrayList<>();
                List<Group> groups = groupRepository.getByMachineId(machineId);
                List<Field> ungroupedFields = fieldRepository.getByMachineId(machineId);
                List<FieldTemplate> loadedTemplates = dbClient.fieldTemplateDao().getAll();

                newItems.addAll(groups);
                if (!groups.isEmpty() && !ungroupedFields.isEmpty()) {
                    newItems.add(new MachineDetailsAdapter.SectionDivider());
                }
                newItems.addAll(ungroupedFields);

                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (isDragging) { scheduleRefresh(); return; }
                    templates.clear();
                    templates.addAll(loadedTemplates);
                    items.clear();
                    items.addAll(newItems);
                    adapter.updateTemplates(templates);
                    adapter.updateItems(items);
                });
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "loadItems rejected", e);
        }
    }

    private void saveOrders() {
        final List<Group> groupSnapshot = new ArrayList<>();
        final List<Field> fieldSnapshot = new ArrayList<>();
        for (Object obj : items) {
            if (obj instanceof Group) groupSnapshot.add((Group) obj);
            else if (obj instanceof Field) fieldSnapshot.add((Field) obj);
        }
        try {
            lightExecutor.execute(() -> {
                if (!groupSnapshot.isEmpty()) groupRepository.updateOrder(groupSnapshot);
                if (!fieldSnapshot.isEmpty()) fieldRepository.updateOrder(fieldSnapshot, 0);
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "saveOrders rejected", e);
        }
    }

    private void showGroupOptionsDialog(Group group) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_action_group_title)
                .setItems(new String[]{
                        getString(R.string.edit),
                        getString(R.string.delete),
                        getString(R.string.share),
                        getString(R.string.save)
                }, (dialog, which) -> {
                    if (which == 0) {
                        showEditGroupDialog(group);
                    } else if (which == 1) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.delete_confirm)
                                .setMessage(getString(R.string.dialog_delete_group_message, group.getName()))
                                .setPositiveButton(R.string.yes, (d, w) -> {
                                    try {
                                        lightExecutor.execute(() -> {
                                            groupRepository.delete(group.getId());
                                            mainHandler.post(this::loadItems);
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
                        exportViewModel.shareGroup(group.getId(), group.getName());
                    } else if (which == 3) {
                        if (exportViewModel.isOperationInProgress()) {
                            Toast.makeText(this, R.string.status_operation_in_progress_short,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        exportingGroupId = group.getId();
                        ExportImportHelper.exportGroupToUri(
                                this, groupExportLauncher, group.getId(), group.getName());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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

    private void showEditGroupDialog(final Group group) {
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
                                groupRepository.update(group);
                                mainHandler.post(this::loadItems);
                            });
                        } catch (RejectedExecutionException e) {
                            Log.w(TAG, "update rejected", e);
                        }
                    } else {
                        Toast.makeText(this, R.string.dialog_group_name_empty, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
                                MachineDetailsActivity.this, field.getId(), machineId, field.getGroupId()));
                    } else if (which == 1) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.delete_confirm)
                                .setMessage(getString(R.string.dialog_delete_field_message, field.getTitle()))
                                .setPositiveButton(R.string.yes, (d, w) -> {
                                    try {
                                        lightExecutor.execute(() -> {
                                            fieldRepository.delete(field.getId());
                                            mainHandler.post(this::loadItems);
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

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadItems();
        loadMachineTitle();
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

    private void showCreateGroupDialog() {
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
                                groupRepository.insertWithOrder(new Group(0, machineId, name, 0));
                                mainHandler.post(this::loadItems);
                            });
                        } catch (RejectedExecutionException e) {
                            Log.w(TAG, "create group rejected", e);
                        }
                    } else {
                        Toast.makeText(this, R.string.dialog_enter_name, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static Intent newIntent(android.content.Context context, int machineId) {
        Intent i = new Intent(context, MachineDetailsActivity.class);
        i.putExtra("machine_id", machineId);
        return i;
    }
}