package com.example.appic.activities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.R;
import com.example.appic.adapters.ArticlePhotosAdapter;
import com.example.appic.utils.ArticleGenerator;
import com.example.appic.utils.MaterialProgressDialog;
import com.example.appic.utils.ThemeHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CreateArticleActivity extends BaseActivity {

    private static final String TAG = "CreateArticle";

    private static final String KEY_TITLE = "title";
    private static final String KEY_TEXT = "text";
    private static final String KEY_PHOTO_PATHS = "photo_paths";

    private static final String STATE_STEP = "state_step";
    private static final String STATE_SELECTED = "state_selected";
    private static final String STATE_TEMPLATE = "state_template";

    private static final int STEP_PHOTOS = 0;
    private static final int STEP_TEMPLATE = 1;

    private static final int TEMPLATE_NONE = 0;
    private static final int TEMPLATE_HTML = 1;
    private static final int TEMPLATE_PDF = 2;

    private static final int ACTION_NONE = 0;
    private static final int ACTION_SHARE = 1;
    private static final int ACTION_SAVE = 2;

    private static final long SHARE_FILE_LIFETIME_MS = 5L * 60 * 1000;

    private String title;
    private String text;
    private final List<String> allPhotos = new ArrayList<>();
    private final Set<String> selectedPhotos = new LinkedHashSet<>();

    private int currentStep = STEP_PHOTOS;
    private int selectedTemplate = TEMPLATE_NONE;
    private int pendingAction = ACTION_NONE;
    private String pendingFileName = "";

    private ViewFlipper viewFlipper;
    private ProgressBar stepProgress;
    private TextView tvStepCount;
    private Button btnBack;
    private Button btnNext;
    private RecyclerView recyclerPhotos;
    private TextView tvEmptyPhotos;
    private ArticlePhotosAdapter adapter;
    private MaterialCardView cardHtml;
    private MaterialCardView cardPdf;

    private boolean isCreating = false;
    private MaterialProgressDialog progressDialog;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> saveFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) generateAndSaveToUri(uri);
                }
            });

    public static Intent newIntent(Context ctx, String title, String text, List<String> photoPaths) {
        Intent i = new Intent(ctx, CreateArticleActivity.class);
        i.putExtra(KEY_TITLE, title);
        i.putExtra(KEY_TEXT, text);
        i.putStringArrayListExtra(KEY_PHOTO_PATHS,
                photoPaths != null ? new ArrayList<>(photoPaths) : new ArrayList<>());
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_article);

        title = getIntent().getStringExtra(KEY_TITLE);
        if (title == null) title = "";
        text = getIntent().getStringExtra(KEY_TEXT);
        if (text == null) text = "";

        ArrayList<String> paths = getIntent().getStringArrayListExtra(KEY_PHOTO_PATHS);
        if (paths != null) {
            for (String p : paths) {
                if (p != null && !p.isEmpty()) allPhotos.add(p);
            }
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applyToToolbar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.create_article_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        viewFlipper = findViewById(R.id.view_flipper);
        stepProgress = findViewById(R.id.step_progress);
        tvStepCount = findViewById(R.id.tv_step_count);
        btnBack = findViewById(R.id.btn_back);
        btnNext = findViewById(R.id.btn_next);
        recyclerPhotos = findViewById(R.id.recycler_photos);
        tvEmptyPhotos = findViewById(R.id.tv_empty_photos);
        cardHtml = findViewById(R.id.card_template_html);
        cardPdf = findViewById(R.id.card_template_pdf);

        recyclerPhotos.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new ArticlePhotosAdapter(allPhotos, selectedPhotos, count -> updateStepUi());
        recyclerPhotos.setAdapter(adapter);

        if (allPhotos.isEmpty()) {
            tvEmptyPhotos.setVisibility(View.VISIBLE);
        } else {
            for (String p : allPhotos) selectedPhotos.add(p);
            adapter.notifyDataSetChanged();
        }

        cardHtml.setOnClickListener(v -> selectTemplate(TEMPLATE_HTML));
        cardPdf.setOnClickListener(v -> selectTemplate(TEMPLATE_PDF));

        btnBack.setOnClickListener(v -> goBack());
        btnNext.setOnClickListener(v -> onNextClicked());

        if (savedInstanceState != null) {
            currentStep = savedInstanceState.getInt(STATE_STEP, STEP_PHOTOS);
            selectedTemplate = savedInstanceState.getInt(STATE_TEMPLATE, TEMPLATE_NONE);
            ArrayList<String> selected = savedInstanceState.getStringArrayList(STATE_SELECTED);
            if (selected != null) {
                selectedPhotos.clear();
                for (String p : selected) {
                    if (allPhotos.contains(p)) selectedPhotos.add(p);
                }
            }
            if (currentStep == STEP_TEMPLATE) viewFlipper.setDisplayedChild(STEP_TEMPLATE);
            adapter.notifyDataSetChanged();
        }

        if (allPhotos.isEmpty() && currentStep == STEP_PHOTOS) {
            currentStep = STEP_TEMPLATE;
            viewFlipper.setDisplayedChild(STEP_TEMPLATE);
        }

        updateTemplateSelectionUi();
        updateStepUi();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isCreating) return;
                if (currentStep == STEP_TEMPLATE && !allPhotos.isEmpty()) {
                    goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STEP, currentStep);
        outState.putInt(STATE_TEMPLATE, selectedTemplate);
        outState.putStringArrayList(STATE_SELECTED, new ArrayList<>(selectedPhotos));
    }

    private void selectTemplate(int template) {
        selectedTemplate = template;
        updateTemplateSelectionUi();
        updateStepUi();
    }

    private void updateTemplateSelectionUi() {
        int primary = ThemeHelper.getThemeColor(this, androidx.appcompat.R.attr.colorPrimary);
        int transparent = 0x00000000;
        cardHtml.setStrokeColor(selectedTemplate == TEMPLATE_HTML ? primary : transparent);
        cardPdf.setStrokeColor(selectedTemplate == TEMPLATE_PDF ? primary : transparent);
    }

    private void goBack() {
        if (currentStep == STEP_TEMPLATE && !allPhotos.isEmpty()) {
            currentStep = STEP_PHOTOS;
            viewFlipper.setDisplayedChild(STEP_PHOTOS);
            updateStepUi();
        } else {
            finish();
        }
    }

    private void onNextClicked() {
        if (currentStep == STEP_PHOTOS) {
            currentStep = STEP_TEMPLATE;
            viewFlipper.setDisplayedChild(STEP_TEMPLATE);
            updateStepUi();
        } else {
            if (selectedTemplate == TEMPLATE_NONE) {
                Toast.makeText(this, R.string.create_article_select_template,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            showActionDialog();
        }
    }

    private void updateStepUi() {
        stepProgress.setProgress(currentStep + 1);
        if (currentStep == STEP_PHOTOS) {
            tvStepCount.setText(getString(R.string.create_article_step_photos_counter,
                    selectedPhotos.size(), allPhotos.size()));
            btnBack.setVisibility(View.INVISIBLE);
            btnNext.setText(R.string.next);
            btnNext.setEnabled(true);
        } else {
            tvStepCount.setText(R.string.create_article_step_template);
            btnBack.setVisibility(View.VISIBLE);
            btnNext.setText(R.string.create_article_create);
            btnNext.setEnabled(selectedTemplate != TEMPLATE_NONE);
        }
    }

    private void showActionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.create_article_action_title)
                .setItems(new String[]{
                        getString(R.string.create_article_action_share),
                        getString(R.string.create_article_action_save)
                }, (d, which) -> {
                    pendingAction = (which == 0) ? ACTION_SHARE : ACTION_SAVE;
                    showFileNameDialog();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showFileNameDialog() {
        final String ext = selectedTemplate == TEMPLATE_HTML ? "html" : "pdf";

        String defaultName = title != null ? title.trim() : "";
        defaultName = defaultName.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (defaultName.isEmpty()) defaultName = "article";
        if (defaultName.length() > 60) defaultName = defaultName.substring(0, 60);

        TextInputEditText et = new TextInputEditText(this);
        et.setSingleLine(true);
        et.setText(defaultName);
        ThemeHelper.applyToEditText(et);
        if (et.getText() != null) et.setSelection(et.getText().length());

        TextInputLayout til = new TextInputLayout(this);
        til.setHint(getString(R.string.create_article_filename_hint));
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.addView(et);

        FrameLayout wrapper = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        wrapper.setPadding(pad, 0, pad, 0);
        wrapper.addView(til);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.create_article_filename_title)
                .setView(wrapper)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String name = et.getText() != null ? et.getText().toString().trim() : "";
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.create_article_empty_filename,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                    if (!name.toLowerCase().endsWith("." + ext)) {
                        name = name + "." + ext;
                    }
                    pendingFileName = name;
                    if (pendingAction == ACTION_SAVE) {
                        launchSavePicker();
                    } else if (pendingAction == ACTION_SHARE) {
                        generateAndShare();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void launchSavePicker() {
        String mime = selectedTemplate == TEMPLATE_HTML ? "text/html" : "application/pdf";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, pendingFileName);
        saveFileLauncher.launch(intent);
    }

    private void generateAndSaveToUri(Uri uri) {
        if (isCreating) return;
        isCreating = true;
        showProgress();

        final String fTitle = title;
        final String fText = text;
        final List<String> fPhotos = getOrderedSelection();
        final int fTemplate = selectedTemplate;

        executor.execute(() -> {
            boolean success = false;
            String error = null;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("OutputStream null");
                if (fTemplate == TEMPLATE_HTML) {
                    ArticleGenerator.generateHtml(fTitle, fText, fPhotos, out);
                } else {
                    ArticleGenerator.generatePdf(fTitle, fText, fPhotos, out);
                }
                success = true;
            } catch (Exception e) {
                Log.e(TAG, "generation failed", e);
                error = e.getMessage();
            }

            final boolean ok = success;
            final String errMsg = error;
            mainHandler.post(() -> {
                isCreating = false;
                dismissProgress();
                if (isFinishing() || isDestroyed()) return;
                if (ok) {
                    Toast.makeText(this, R.string.create_article_saved, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this,
                            getString(R.string.create_article_save_error) +
                                    (errMsg != null ? ": " + errMsg : ""),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void generateAndShare() {
        if (isCreating) return;
        isCreating = true;
        showProgress();

        final String fTitle = title;
        final String fText = text;
        final List<String> fPhotos = getOrderedSelection();
        final int fTemplate = selectedTemplate;
        final String fName = pendingFileName;

        executor.execute(() -> {
            Uri shareUri = null;
            File tempDir = null;
            try {
                tempDir = new File(getCacheDir(),
                        "article_share/" + UUID.randomUUID().toString());
                if (!tempDir.exists() && !tempDir.mkdirs()) {
                    throw new IOException("Cannot create temp dir");
                }
                File outFile = new File(tempDir, fName);

                try (OutputStream out = new FileOutputStream(outFile)) {
                    if (fTemplate == TEMPLATE_HTML) {
                        ArticleGenerator.generateHtml(fTitle, fText, fPhotos, out);
                    } else {
                        ArticleGenerator.generatePdf(fTitle, fText, fPhotos, out);
                    }
                }

                shareUri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", outFile);
            } catch (Exception e) {
                Log.e(TAG, "share generation failed", e);
            }

            final Uri finalUri = shareUri;
            final File finalTempDir = tempDir;
            final int finalTemplate = fTemplate;
            mainHandler.post(() -> {
                isCreating = false;
                dismissProgress();
                if (isFinishing() || isDestroyed()) {
                    if (finalTempDir != null) deleteRecursive(finalTempDir);
                    return;
                }
                if (finalUri == null) {
                    Toast.makeText(this, R.string.create_article_save_error,
                            Toast.LENGTH_LONG).show();
                    if (finalTempDir != null) deleteRecursive(finalTempDir);
                    return;
                }

                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType(finalTemplate == TEMPLATE_HTML ? "text/html" : "application/pdf");
                share.putExtra(Intent.EXTRA_STREAM, finalUri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share,
                        getString(R.string.create_article_share_chooser)));

                if (finalTempDir != null) {
                    mainHandler.postDelayed(() -> deleteRecursive(finalTempDir),
                            SHARE_FILE_LIFETIME_MS);
                }
            });
        });
    }

    private static void deleteRecursive(File f) {
        if (f == null) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        f.delete();
    }

    private List<String> getOrderedSelection() {
        List<String> result = new ArrayList<>();
        for (String p : allPhotos) {
            if (selectedPhotos.contains(p)) result.add(p);
        }
        return result;
    }

    private void showProgress() {
        if (isFinishing() || isDestroyed()) return;
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        progressDialog = new MaterialProgressDialog(this);
        progressDialog.setMessage(getString(R.string.create_article_progress));
        progressDialog.setCancelable(false);
        try { progressDialog.show(); } catch (Exception ignored) {}
    }

    private void dismissProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try { progressDialog.dismiss(); } catch (Exception ignored) {}
        }
        progressDialog = null;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (isCreating) return true;
            if (currentStep == STEP_TEMPLATE && !allPhotos.isEmpty()) {
                goBack();
            } else {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) ThemeHelper.applyToToolbar(toolbar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        dismissProgress();
    }
}