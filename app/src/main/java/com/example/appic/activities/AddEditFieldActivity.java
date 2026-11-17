package com.example.appic.activities;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.R;
import com.example.appic.activities.addeditfield.DynamicFieldController;
import com.example.appic.adapters.DocumentsAdapter;
import com.example.appic.adapters.FieldMediaAdapter;
import com.example.appic.database.DatabaseClient;
import com.example.appic.database.models.Field;
import com.example.appic.database.models.FieldTemplate;
import com.example.appic.database.models.Group;
import com.example.appic.database.models.Machine;
import com.example.appic.database.models.MachineGroup;
import com.example.appic.database.models.Media;
import com.example.appic.database.repository.FieldRepository;
import com.example.appic.utils.FileUtils;
import com.example.appic.utils.GetMixedMediaContract;
import com.example.appic.utils.MaterialProgressDialog;
import com.example.appic.utils.MediaHelper;
import com.example.appic.utils.TermsHelper;
import com.example.appic.utils.ThemeHelper;
import com.example.appic.viewmodels.AddEditFieldViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AddEditFieldActivity extends BaseActivity implements MediaHelper.OnMediaCapturedListener {

    private static final String TAG = "AddEditField";
    private static final String KEY_FIELD_ID = "field_id";
    private static final String KEY_MEDIA_IDS = "media_ids";
    private static final String KEY_MEDIA_PATHS = "media_paths";
    private static final String KEY_MEDIA_TYPES = "media_types";
    private static final String KEY_CUSTOM_VALUES = "custom_values_state";
    private static final String KEY_TITLE_VAL = "title_val_state";
    private static final String KEY_CONTENT_VAL = "content_val_state";
    private static final String KEY_DATA_LOADED = "data_loaded_state";
    private static final String KEY_INITIAL_TITLE = "initial_title_state";
    private static final String KEY_INITIAL_CONTENT = "initial_content_state";
    private static final String KEY_INITIAL_CUSTOM = "initial_custom_state";
    private static final String KEY_INITIAL_MEDIA = "initial_media_state";

    private volatile int fieldId = -1;
    private int machineId;
    private int groupId = 0;

    private LinearLayout systemFieldsContainer;
    private LinearLayout dynamicFieldsContainer;
    private RecyclerView recyclerMedia;
    private RecyclerView recyclerDocuments;
    private TextView tvDocumentsLabel;
    private FloatingActionButton btnAddMedia;
    private FloatingActionButton btnCreateArticle;
    private Button btnSave;
    private ScrollView scrollContent;
    private ProgressBar loadingProgress;

    private FieldRepository fieldRepository;
    private DatabaseClient dbClient;
    private List<Media> mediaList;
    private List<Media> photoVideoList;
    private List<Media> documentsList;
    private FieldMediaAdapter mediaAdapter;
    private DocumentsAdapter documentsAdapter;
    private MediaHelper mediaHelper;

    private DynamicFieldController fieldController;

    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MaterialProgressDialog progressDialog;
    private final AtomicBoolean isActivityValid = new AtomicBoolean(true);
    private final AtomicInteger pendingMediaCount = new AtomicInteger(0);

    private volatile boolean saved = false;
    private volatile boolean dataLoaded = false;
    private volatile boolean isSaving = false;

    private String initialTitle = "";
    private String initialContent = "";
    private String initialCustomValues = "";
    private List<String> initialMediaSignatures = new ArrayList<>();
    private boolean initialCaptured = false;

    private final ActivityResultLauncher<Uri> cameraPhotoLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> { if (mediaHelper != null) mediaHelper.onCameraPhotoResult(success); });

    private final ActivityResultLauncher<Intent> cameraVideoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                boolean success = result.getResultCode() == RESULT_OK;
                if (mediaHelper != null) mediaHelper.onCameraVideoResult(success);
            });

    private final ActivityResultLauncher<Void> galleryLauncher = registerForActivityResult(
            new GetMixedMediaContract(),
            uris -> { if (mediaHelper != null) mediaHelper.onGalleryResult(uris); });

    private final ActivityResultLauncher<String[]> documentsLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            uris -> { if (mediaHelper != null) mediaHelper.onDocumentsResult(uris); });

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> { if (mediaHelper != null) mediaHelper.onCameraPermissionResult(granted); });

    private AddEditFieldViewModel saveViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_field);
        isActivityValid.set(true);
        executor = Executors.newSingleThreadExecutor();
        dbClient = DatabaseClient.getInstance(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        btnSave = findViewById(R.id.btn_save);
        btnAddMedia = findViewById(R.id.btn_add_media);
        btnCreateArticle = findViewById(R.id.btn_create_article);
        systemFieldsContainer = findViewById(R.id.system_fields_container);
        dynamicFieldsContainer = findViewById(R.id.dynamic_fields_container);
        scrollContent = findViewById(R.id.scroll_content);
        loadingProgress = findViewById(R.id.loading_progress);

        scrollContent.setVisibility(View.GONE);
        loadingProgress.setVisibility(View.VISIBLE);

        if (savedInstanceState != null) {
            fieldId = savedInstanceState.getInt(KEY_FIELD_ID, -1);
        } else {
            fieldId = getIntent().getIntExtra("field_id", -1);
        }
        machineId = getIntent().getIntExtra("machine_id", -1);
        groupId = getIntent().getIntExtra("group_id", 0);

        if (machineId == -1) {
            Toast.makeText(this, R.string.error_machine_not_specified, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (getSupportActionBar() != null) {
            if (fieldId == -1) {
                getSupportActionBar().setTitle(R.string.title_add_field);
            } else {
                getSupportActionBar().setTitle(R.string.title_edit_field);
            }
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        configureToolbarTitleForScrolling(toolbar);
        ThemeHelper.applyToToolbar(toolbar);

        fieldRepository = new FieldRepository(this);

        if (fieldId != -1) {
            final int idToMark = fieldId;
            try { executor.execute(() -> fieldRepository.markAsRead(idToMark)); }
            catch (RejectedExecutionException ignored) {}
        }

        fieldController = new DynamicFieldController(this,
                systemFieldsContainer, dynamicFieldsContainer,
                TermsHelper.getTitleType(this), TermsHelper.getContentType(this));

        recyclerMedia = findViewById(R.id.recycler_media);
        recyclerDocuments = findViewById(R.id.recycler_documents);
        tvDocumentsLabel = findViewById(R.id.tv_documents_label);

        recyclerMedia.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerDocuments.setLayoutManager(new LinearLayoutManager(this));

        mediaList = new ArrayList<>();
        photoVideoList = new ArrayList<>();
        documentsList = new ArrayList<>();

        mediaAdapter = new FieldMediaAdapter(photoVideoList, new FieldMediaAdapter.OnMediaClickListener() {
            @Override public void onMediaClick(Media media, int position) {
                if (isSaving) return;
                openGallery(position);
            }
            @Override public void onDeleteClick(Media media, int position) {
                if (isSaving) return;
                confirmDeleteMedia(media, true);
            }
        });
        recyclerMedia.setAdapter(mediaAdapter);

        documentsAdapter = new DocumentsAdapter(documentsList, new DocumentsAdapter.OnDocumentClickListener() {
            @Override public void onDocumentClick(Media document) {
                if (isSaving) return;
                openDocument(document.getPath());
            }
            @Override public void onDeleteClick(Media document) {
                if (isSaving) return;
                confirmDeleteMedia(document, false);
            }
        });
        recyclerDocuments.setAdapter(documentsAdapter);

        loadTemplatesAndData(savedInstanceState);

        File mediaDir = getExternalFilesDir("media");
        if (mediaDir == null) mediaDir = new File(getFilesDir(), "media");
        mediaHelper = new MediaHelper(this, mediaDir, "FIELD_", this);
        mediaHelper.setLaunchers(cameraPhotoLauncher, cameraVideoLauncher,
                galleryLauncher, documentsLauncher, cameraPermissionLauncher);
        mediaHelper.restoreState(savedInstanceState);

        btnAddMedia.setOnClickListener(v -> {
            if (isSaving) return;
            mediaHelper.showMediaSourceDialog();
        });

        btnCreateArticle.setOnClickListener(v -> {
            if (isSaving) return;
            if (pendingMediaCount.get() > 0) {
                Toast.makeText(this, R.string.status_wait_media_processing, Toast.LENGTH_SHORT).show();
                return;
            }
            openCreateArticle();
        });

        btnSave.setOnClickListener(v -> {
            if (isSaving) return;
            if (pendingMediaCount.get() > 0) {
                Toast.makeText(this, R.string.status_wait_media_processing, Toast.LENGTH_SHORT).show();
                return;
            }
            saveField();
        });

        saveViewModel = new ViewModelProvider(this).get(AddEditFieldViewModel.class);

        saveViewModel.getState().observe(this, s -> {
            if (s == null) return;
            if (s.inProgress) {
                isSaving = true;
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
                showProgress(getString(R.string.progress_saving));
                btnSave.setEnabled(false);
                btnAddMedia.setEnabled(false);
                btnCreateArticle.setEnabled(false);
            } else if (s.finished) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                dismissProgress();
                btnSave.setEnabled(true);
                btnAddMedia.setEnabled(true);
                btnCreateArticle.setEnabled(true);
                isSaving = false;
                if (s.success) {
                    fieldId = s.fieldId;
                    if (s.newMediaIdsByIndex != null && mediaList != null) {
                        for (Map.Entry<Integer, Integer> e : s.newMediaIdsByIndex.entrySet()) {
                            int idx = e.getKey();
                            if (idx >= 0 && idx < mediaList.size()) {
                                Media m = mediaList.get(idx);
                                m.setId(e.getValue());
                                m.setFieldId(s.fieldId);
                            }
                        }
                    }
                    saved = true;
                    setResult(RESULT_OK);
                    finish();
                }
            }
        });

        saveViewModel.getErrorEvent().observe(this, event -> {
            if (event == null) return;
            String msg = event.getContentIfNotHandled();
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                attemptExit();
            }
        });
    }

    private void attemptExit() {
        if (isSaving) return;
        if (pendingMediaCount.get() > 0) {
            Toast.makeText(this, R.string.status_wait_media_processing, Toast.LENGTH_SHORT).show();
            return;
        }
        if (hasUnsavedChanges()) {
            showUnsavedChangesDialog();
            return;
        }
        finish();
    }

    private boolean hasUnsavedChanges() {
        if (saved) return false;
        if (!initialCaptured || fieldController == null) return false;

        String currentTitle = fieldController.getTitleValue();
        String currentContent = fieldController.getContentValue();
        String currentCustom = fieldController.gatherCustomValues();

        if (!TextUtils.equals(initialTitle, currentTitle)) return true;
        if (!TextUtils.equals(initialContent, currentContent)) return true;
        if (!TextUtils.equals(initialCustomValues, currentCustom)) return true;
        if (!sameMedia(initialMediaSignatures, buildMediaSignatures())) return true;

        return false;
    }

    private boolean sameMedia(List<String> a, List<String> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!TextUtils.equals(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private List<String> buildMediaSignatures() {
        List<String> result = new ArrayList<>();
        if (mediaList == null) return result;
        for (Media m : mediaList) {
            result.add(m.getId() + "|" + m.getType() + "|" + m.getPath());
        }
        return result;
    }

    private void showUnsavedChangesDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unsaved_changes_title)
                .setMessage(R.string.unsaved_changes_message)
                .setPositiveButton(R.string.save, (d, w) -> {
                    if (!fieldController.validate(msg ->
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show())) {
                        return;
                    }
                    saveField();
                })
                .setNegativeButton(R.string.unsaved_changes_discard, (d, w) -> {
                    saved = false;
                    finish();
                })
                .setNeutralButton(R.string.cancel, null)
                .show();
    }

    private void captureInitialSnapshot() {
        if (fieldController == null) return;
        initialTitle = fieldController.getTitleValue();
        initialContent = fieldController.getContentValue();
        initialCustomValues = fieldController.gatherCustomValues();
        initialMediaSignatures = buildMediaSignatures();
        initialCaptured = true;
    }

    private void openCreateArticle() {
        List<String> photoPaths = new ArrayList<>();
        for (Media m : photoVideoList) {
            if ("image".equals(m.getType())) {
                photoPaths.add(m.getPath());
            }
        }
        String titleVal = fieldController != null ? fieldController.getTitleValue() : "";
        String contentVal = fieldController != null ? fieldController.getContentValue() : "";
        startActivity(CreateArticleActivity.newIntent(this, titleVal, contentVal, photoPaths));
    }

    private void configureToolbarTitleForScrolling(Toolbar toolbar) {
        if (toolbar == null) return;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                tv.setSingleLine(true);
                tv.setHorizontallyScrolling(true);
                tv.setEllipsize(null);
                tv.setMovementMethod(ScrollingMovementMethod.getInstance());
                tv.setFocusable(true);
                tv.setFocusableInTouchMode(true);
                return;
            }
        }
    }

    private void setPathTitleAndScrollToEnd(String path) {
        if (getSupportActionBar() == null) return;
        getSupportActionBar().setTitle(path);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar == null) return;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView) {
                final TextView tv = (TextView) child;
                tv.post(() -> {
                    if (tv.getLayout() == null) return;
                    float lineWidth = tv.getLayout().getLineWidth(0);
                    int availableWidth = tv.getWidth()
                            - tv.getCompoundPaddingLeft()
                            - tv.getCompoundPaddingRight();
                    int scrollX = (int) Math.max(0f, lineWidth - availableWidth);
                    tv.scrollTo(scrollX, 0);
                });
                return;
            }
        }
    }

    private void loadTemplatesAndData(Bundle savedInstanceState) {
        try {
            executor.execute(() -> {
                try {
                    List<FieldTemplate> loadedTemplates = dbClient.fieldTemplateDao().getAll();
                    Field field = fieldId != -1 ? fieldRepository.getById(fieldId) : null;
                    List<Media> loadedMedia = fieldId != -1
                            ? fieldRepository.getMediaByFieldId(fieldId) : new ArrayList<>();

                    Machine machine = null;
                    MachineGroup machineGroup = null;
                    Group group = null;
                    if (fieldId != -1) {
                        try {
                            machine = dbClient.machineDao().getById(machineId);
                            if (machine != null && machine.getGroupId() != 0) {
                                machineGroup = dbClient.machineGroupDao().getById(machine.getGroupId());
                            }
                            if (groupId != 0) {
                                group = dbClient.groupDao().getById(groupId);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to load context for toolbar title", e);
                        }
                    }

                    final Machine fMachine = machine;
                    final MachineGroup fMachineGroup = machineGroup;
                    final Group fGroup = group;

                    mainHandler.post(() -> {
                        if (isFinishing() || isDestroyed() || !isActivityValid.get()) return;
                        try {
                            fieldController.setTemplates(loadedTemplates);
                            boolean isNewField = fieldId == -1;

                            String titleHint = TermsHelper.getFieldSingle(this);
                            fieldController.build(
                                    titleHint, "Содержимое",
                                    TermsHelper.getTitleOptions(this),
                                    TermsHelper.getContentOptions(this),
                                    TermsHelper.isTitleAutoFill(this),
                                    TermsHelper.isContentAutoFill(this),
                                    isNewField);

                            boolean restoreFromBundle = savedInstanceState != null
                                    && savedInstanceState.getBoolean(KEY_DATA_LOADED, false);

                            String titleForPath = null;
                            if (restoreFromBundle) {
                                ArrayList<Integer> ids = savedInstanceState.getIntegerArrayList(KEY_MEDIA_IDS);
                                ArrayList<String> paths = savedInstanceState.getStringArrayList(KEY_MEDIA_PATHS);
                                ArrayList<String> types = savedInstanceState.getStringArrayList(KEY_MEDIA_TYPES);
                                if (ids != null && paths != null && types != null) {
                                    for (int i = 0; i < ids.size(); i++) {
                                        Media m = new Media(ids.get(i), fieldId, types.get(i), paths.get(i));
                                        mediaList.add(m);
                                        addToSeparateList(m);
                                    }
                                    updateAdapters();
                                }
                                String savedCustom = savedInstanceState.getString(KEY_CUSTOM_VALUES);
                                if (savedCustom != null) fieldController.populateDynamicValues(savedCustom);
                                String savedTitle = savedInstanceState.getString(KEY_TITLE_VAL);
                                String savedContent = savedInstanceState.getString(KEY_CONTENT_VAL);
                                fieldController.populateSystemValues(savedTitle, savedContent);
                                titleForPath = savedTitle;

                                String initTitle = savedInstanceState.getString(KEY_INITIAL_TITLE);
                                String initContent = savedInstanceState.getString(KEY_INITIAL_CONTENT);
                                String initCustom = savedInstanceState.getString(KEY_INITIAL_CUSTOM);
                                ArrayList<String> initMedia = savedInstanceState.getStringArrayList(KEY_INITIAL_MEDIA);
                                if (initTitle != null && initCustom != null) {
                                    initialTitle = initTitle;
                                    initialContent = initContent != null ? initContent : "";
                                    initialCustomValues = initCustom;
                                    initialMediaSignatures = initMedia != null ? initMedia : new ArrayList<>();
                                    initialCaptured = true;
                                }
                            } else if (field != null) {
                                fieldController.populateSystemValues(field.getTitle(), field.getText());
                                fieldController.populateDynamicValues(field.getCustomValues());
                                mediaList.addAll(loadedMedia);
                                for (Media m : mediaList) addToSeparateList(m);
                                updateAdapters();
                                titleForPath = field.getTitle();
                            }

                            if (fieldId != -1 && getSupportActionBar() != null) {
                                String path = buildFieldPath(fMachineGroup, fMachine, fGroup, titleForPath);
                                setPathTitleAndScrollToEnd(path);
                            }

                            dataLoaded = true;
                            loadingProgress.setVisibility(View.GONE);
                            scrollContent.setVisibility(View.VISIBLE);

                            if (!initialCaptured) {
                                captureInitialSnapshot();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to build UI from loaded data", e);
                            loadingProgress.setVisibility(View.GONE);
                            Toast.makeText(this, "Ошибка отображения данных", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Initial load failed", e);
                    mainHandler.post(() -> {
                        if (isFinishing() || isDestroyed() || !isActivityValid.get()) return;
                        loadingProgress.setVisibility(View.GONE);
                        Toast.makeText(this, "Не удалось загрузить данные: " +
                                (e.getMessage() == null ? "неизвестно" : e.getMessage()),
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            if (loadingProgress != null) loadingProgress.setVisibility(View.GONE);
        }
    }

    private String buildFieldPath(MachineGroup machineGroup, Machine machine,
                                   Group group, String title) {
        List<String> parts = new ArrayList<>();
        if (machineGroup != null && !TextUtils.isEmpty(machineGroup.getName())
                && !machineGroup.getName().trim().isEmpty()) {
            parts.add(machineGroup.getName().trim());
        }
        if (machine != null && !TextUtils.isEmpty(machine.getName())
                && !machine.getName().trim().isEmpty()) {
            parts.add(machine.getName().trim());
        }
        if (group != null && !TextUtils.isEmpty(group.getName())
                && !group.getName().trim().isEmpty()) {
            parts.add(group.getName().trim());
        }
        if (!TextUtils.isEmpty(title) && !title.trim().isEmpty()) {
            parts.add(title.trim());
        }
        if (parts.isEmpty()) {
            return getString(R.string.title_edit_field);
        }
        return TextUtils.join(" · ", parts);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) ThemeHelper.applyToToolbar(toolbar);
        if (fieldController != null) fieldController.refreshInputSizes();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            attemptExit();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void addToSeparateList(Media media) {
        if ("document".equals(media.getType())) documentsList.add(media);
        else photoVideoList.add(media);
    }

    private void updateAdapters() {
        mediaAdapter.updateData(photoVideoList);
        documentsAdapter.updateData(documentsList);
        tvDocumentsLabel.setVisibility(documentsList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_FIELD_ID, fieldId);
        outState.putBoolean(KEY_DATA_LOADED, dataLoaded);

        if (dataLoaded && fieldController != null) {
            outState.putString(KEY_CUSTOM_VALUES, fieldController.gatherCustomValues());
            outState.putString(KEY_TITLE_VAL, fieldController.getTitleValue());
            outState.putString(KEY_CONTENT_VAL, fieldController.getContentValue());

            ArrayList<Integer> ids = new ArrayList<>();
            ArrayList<String> paths = new ArrayList<>();
            ArrayList<String> types = new ArrayList<>();
            if (mediaList != null) {
                for (Media m : mediaList) {
                    ids.add(m.getId());
                    paths.add(m.getPath());
                    types.add(m.getType());
                }
            }
            outState.putIntegerArrayList(KEY_MEDIA_IDS, ids);
            outState.putStringArrayList(KEY_MEDIA_PATHS, paths);
            outState.putStringArrayList(KEY_MEDIA_TYPES, types);

            outState.putString(KEY_INITIAL_TITLE, initialTitle);
            outState.putString(KEY_INITIAL_CONTENT, initialContent);
            outState.putString(KEY_INITIAL_CUSTOM, initialCustomValues);
            outState.putStringArrayList(KEY_INITIAL_MEDIA,
                    new ArrayList<>(initialMediaSignatures));
        }
        if (mediaHelper != null) mediaHelper.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        boolean saveInProgress = saveViewModel != null
                && saveViewModel.getState().getValue() != null
                && saveViewModel.getState().getValue().inProgress;

        boolean shouldCleanup = isFinishing() && !saved && !saveInProgress && mediaList != null;
        if (shouldCleanup) {
            for (Media m : mediaList) {
                if (m.getId() == 0) FileUtils.deleteFile(m.getPath());
            }
            if (mediaHelper != null) mediaHelper.cleanupOrphanedTempFiles();
        }
        isSaving = false;
        isActivityValid.set(false);
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        dismissProgress();
    }

    private void saveField() {
        if (isSaving) return;
        if (!fieldController.validate(msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show())) return;

        String titleVal = fieldController.getTitleValue();
        String contentVal = fieldController.getContentValue();
        String customValuesJson = fieldController.gatherCustomValues();

        isSaving = true;
        btnAddMedia.setEnabled(false);
        btnCreateArticle.setEnabled(false);

        final List<Media> snapshot = new ArrayList<>(mediaList);
        saveViewModel.save(fieldId, machineId, groupId, titleVal, contentVal, customValuesJson, snapshot);
    }

    private void showProgress(String msg) {
        if (isFinishing() || isDestroyed()) return;
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        progressDialog = new MaterialProgressDialog(this);
        progressDialog.setMessage(msg);
        progressDialog.setCancelable(false);
        try { progressDialog.show(); } catch (Exception ignored) {}
    }

    private void dismissProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try { progressDialog.dismiss(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onMediaCaptured(File file, boolean isVideo) {
        if (!isActivityValid.get()) return;
        if (isSaving) {
            FileUtils.deleteFile(file.getAbsolutePath());
            return;
        }
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (isSaving) { FileUtils.deleteFile(file.getAbsolutePath()); return; }
            addMediaToList(isVideo ? "video" : "image", file.getAbsolutePath());
            Toast.makeText(this, isVideo ? R.string.media_video_added : R.string.media_photo_added,
                    Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onMediaSelected(Uri uri, String mimeType) {
        if (!isActivityValid.get()) return;
        if (isSaving) return;
        pendingMediaCount.incrementAndGet();
        if (!isProgressShowing()) showProgress(getString(R.string.progress_processing_media));

        final String initialMimeType = mimeType;
        try {
            executor.execute(() -> {
                try {
                    String resolvedMime = initialMimeType;
                    if (resolvedMime == null) {
                        String name = getOriginalFileName(uri);
                        if (name != null) {
                            int dot = name.lastIndexOf('.');
                            if (dot > 0 && dot < name.length() - 1) {
                                resolvedMime = MimeTypeMap.getSingleton()
                                        .getMimeTypeFromExtension(name.substring(dot + 1));
                            }
                        }
                    }
                    final boolean isVideo = resolvedMime != null && resolvedMime.startsWith("video/");
                    final boolean isImage = resolvedMime != null && resolvedMime.startsWith("image/");

                    File destDir = getExternalFilesDir("media");
                    if (destDir == null) destDir = new File(getFilesDir(), "media");
                    if (!destDir.exists()) destDir.mkdirs();

                    File destFile;
                    String type;
                    if (isVideo || isImage) {
                        String ts = MediaHelper.getTimestampSdf().format(new java.util.Date());
                        String ext = getFileExtensionFromUri(uri, resolvedMime, true, isVideo);
                        destFile = new File(destDir, "FIELD_" + ts + "_" + System.currentTimeMillis() + ext);
                        MediaHelper.copyUriToFile(getContentResolver(), uri, destFile);
                        type = isVideo ? "video" : "image";
                    } else {
                        String name = getOriginalFileName(uri);
                        if (TextUtils.isEmpty(name)) name = "document_" + System.currentTimeMillis();
                        destFile = getUniqueFile(destDir, sanitizeFileName(name));
                        MediaHelper.copyUriToFile(getContentResolver(), uri, destFile);
                        type = "document";
                    }

                    final File finalFile = destFile;
                    final String finalType = type;
                    mainHandler.post(() -> {
                        if (isActivityValid.get() && !isFinishing() && !isDestroyed()) {
                            if (isSaving) {
                                FileUtils.deleteFile(finalFile.getAbsolutePath());
                                return;
                            }
                            addMediaToList(finalType, finalFile.getAbsolutePath());
                        } else {
                            FileUtils.deleteFile(finalFile.getAbsolutePath());
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error saving media", e);
                    mainHandler.post(() -> {
                        if (isActivityValid.get() && !isFinishing() && !isDestroyed()) {
                            Toast.makeText(this, R.string.error_copy_photo, Toast.LENGTH_SHORT).show();
                        }
                    });
                } finally {
                    if (pendingMediaCount.decrementAndGet() <= 0) {
                        mainHandler.post(() -> {
                            if (isActivityValid.get() && !isFinishing() && !isDestroyed()) dismissProgress();
                        });
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            pendingMediaCount.decrementAndGet();
            dismissProgress();
        }
    }

    private boolean isProgressShowing() { return progressDialog != null && progressDialog.isShowing(); }

    @Override
    public void onError(String message) {
        if (isActivityValid.get() && !isFinishing() && !isDestroyed()) {
            runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
        }
    }

    private String getOriginalFileName(Uri uri) {
        String fileName = null;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx != -1) fileName = cursor.getString(idx);
            }
        } catch (Exception e) { Log.e(TAG, "Error getting file name", e); }
        finally { if (cursor != null) cursor.close(); }
        return fileName;
    }

    private String sanitizeFileName(String name) {
        return name == null ? "document" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private File getUniqueFile(File dir, String originalName) {
        File file = new File(dir, originalName);
        if (!file.exists()) return file;
        String base = originalName, ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) { base = originalName.substring(0, dot); ext = originalName.substring(dot); }
        int counter = 1;
        while (file.exists()) {
            file = new File(dir, base + " (" + counter + ")" + ext);
            counter++;
        }
        return file;
    }

    private String getFileExtensionFromUri(Uri uri, String mimeType, boolean isMedia, boolean isVideo) {
        String name = getOriginalFileName(uri);
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot > 0 && dot < name.length() - 1) {
                String ext = name.substring(dot);
                if (ext.length() <= 6) return ext;
            }
        }
        if (mimeType != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (ext != null && !ext.isEmpty()) return "." + ext;
        }
        return isMedia ? (isVideo ? ".mp4" : ".jpg") : "";
    }

    private void addMediaToList(String type, String path) {
        Media media = new Media(0, fieldId, type, path);
        mediaList.add(media);
        if ("document".equals(type)) {
            documentsList.add(media);
            documentsAdapter.notifyItemInserted(documentsList.size() - 1);
            tvDocumentsLabel.setVisibility(View.VISIBLE);
        } else {
            photoVideoList.add(media);
            mediaAdapter.notifyItemInserted(photoVideoList.size() - 1);
        }
    }

    private void confirmDeleteMedia(Media media, boolean isPhotoVideo) {
        if (isSaving) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirm)
                .setMessage(R.string.media_delete_confirm)
                .setPositiveButton(R.string.yes, (d, w) -> {
                    try {
                        executor.execute(() -> {
                            fieldRepository.deleteMedia(media);
                            mainHandler.post(() -> {
                                if (!isActivityValid.get() || isFinishing() || isDestroyed()) return;
                                mediaList.remove(media);
                                if (isPhotoVideo) {
                                    int index = photoVideoList.indexOf(media);
                                    if (index >= 0) {
                                        photoVideoList.remove(index);
                                        mediaAdapter.notifyItemRemoved(index);
                                    }
                                } else {
                                    int index = documentsList.indexOf(media);
                                    if (index >= 0) {
                                        documentsList.remove(index);
                                        documentsAdapter.notifyItemRemoved(index);
                                        if (documentsList.isEmpty())
                                            tvDocumentsLabel.setVisibility(View.GONE);
                                    }
                                }
                            });
                        });
                    } catch (RejectedExecutionException ignored) {}
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void openGallery(int startPos) {
        ArrayList<String> paths = new ArrayList<>();
        ArrayList<String> types = new ArrayList<>();
        for (Media m : photoVideoList) { paths.add(m.getPath()); types.add(m.getType()); }
        if (paths.isEmpty()) return;
        Intent intent = new Intent(this, MediaGalleryActivity.class);
        intent.putStringArrayListExtra(MediaGalleryActivity.EXTRA_MEDIA_PATHS, paths);
        intent.putStringArrayListExtra(MediaGalleryActivity.EXTRA_MEDIA_TYPES, types);
        intent.putExtra(MediaGalleryActivity.EXTRA_START_POSITION, startPos);
        startActivity(intent);
    }

    private void openDocument(String path) {
        File file = new File(path);
        if (!file.exists()) {
            Toast.makeText(this, R.string.media_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, getMimeType(file.getName()));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Открыть документ"));
        } catch (Exception e) {
            Toast.makeText(this, R.string.media_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            String mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(fileName.substring(dot + 1));
            if (mime != null) return mime;
        }
        return "*/*";
    }

    public static Intent newIntent(android.content.Context context, int fieldId, int machineId, int groupId) {
        Intent i = new Intent(context, AddEditFieldActivity.class);
        i.putExtra("field_id", fieldId);
        i.putExtra("machine_id", machineId);
        i.putExtra("group_id", groupId);
        return i;
    }

    public static Intent newIntent(android.content.Context context, int fieldId, int machineId) {
        return newIntent(context, fieldId, machineId, 0);
    }
}