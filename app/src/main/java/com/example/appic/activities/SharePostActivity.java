package com.example.appic.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.appic.R;
import com.example.appic.adapters.DropdownAdapter;
import com.example.appic.adapters.PhotoPlacementsAdapter;
import com.example.appic.database.models.Field;
import com.example.appic.database.models.Media;
import com.example.appic.database.repository.FieldRepository;
import com.example.appic.utils.PostExporter;
import com.example.appic.utils.PostRenderer;
import com.example.appic.utils.ThemeHelper;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SharePostActivity extends BaseActivity {

    private static final String TAG = "SharePostActivity";

    private static final String KEY_FIELD_ID = "field_id";
    private static final String KEY_STATE_TITLE = "state_title";
    private static final String KEY_STATE_TEXT = "state_text";
    private static final String KEY_STATE_HASHTAGS = "state_hashtags";
    private static final String KEY_STATE_PHOTO = "state_photo";
    private static final String KEY_STATE_FORMAT = "state_format";
    private static final String KEY_STATE_TEMPLATE = "state_template";
    private static final String KEY_STATE_ACCENT = "state_accent";
    private static final String KEY_STATE_TEXT_POSITION = "state_text_position";
    private static final String KEY_STATE_PHOTO_LAYOUT = "state_photo_layout";
    private static final String KEY_STATE_PLACEMENTS = "state_placements";
    private static final String KEY_STATE_ZOOM = "state_zoom";
    private static final String KEY_STATE_OFFSET_X = "state_offset_x";
    private static final String KEY_STATE_OFFSET_Y = "state_offset_y";
    private static final String KEY_STATE_WATERMARK = "state_watermark";

    // ИСПРАВЛЕНО (3.2.3): через 5 минут после шаринга временный PNG удаляется,
    // чтобы кэш cache/share_posts/ не разрастался.
    private static final long SHARE_FILE_LIFETIME_MS = 5L * 60 * 1000;

    private static final String[] FORMAT_LABELS = {
            "Квадрат (1:1)", "Сторис (9:16)", "Статья (1.91:1)", "Портрет (4:5)"
    };

    private static final PostRenderer.Format[] FORMATS = {
            PostRenderer.Format.SQUARE,
            PostRenderer.Format.STORY,
            PostRenderer.Format.ARTICLE,
            PostRenderer.Format.PORTRAIT
    };

    private static final String[] TEMPLATE_LABELS = {
            "Минимал", "С рамкой", "С логотипом", "Коллаж 2", "Коллаж 4", "Статья (фото слева)"
    };

    private static final PostRenderer.Template[] TEMPLATES = {
            PostRenderer.Template.MINIMAL,
            PostRenderer.Template.FRAME,
            PostRenderer.Template.LOGO_CORNER,
            PostRenderer.Template.COLLAGE_2,
            PostRenderer.Template.COLLAGE_4,
            PostRenderer.Template.ARTICLE_HORIZONTAL
    };

    private static final String[] ACCENT_LABELS = {
            "Нейтральный", "Синий", "Оранжевый", "Зелёный",
            "Розовый", "Фиолетовый", "Бирюзовый", "Красный"
    };

    private static final PostRenderer.AccentColor[] ACCENTS = {
            PostRenderer.AccentColor.NEUTRAL,
            PostRenderer.AccentColor.BLUE,
            PostRenderer.AccentColor.ORANGE,
            PostRenderer.AccentColor.GREEN,
            PostRenderer.AccentColor.PINK,
            PostRenderer.AccentColor.PURPLE,
            PostRenderer.AccentColor.TEAL,
            PostRenderer.AccentColor.RED
    };

    private static final String[] TEXT_POSITION_LABELS = {
            "Сверху слева", "Сверху по центру", "Сверху справа",
            "По центру слева", "По центру", "По центру справа",
            "Снизу слева", "Снизу по центру", "Снизу справа"
    };

    private static final PostRenderer.TextPosition[] TEXT_POSITIONS = {
            PostRenderer.TextPosition.TOP_LEFT,
            PostRenderer.TextPosition.TOP_CENTER,
            PostRenderer.TextPosition.TOP_RIGHT,
            PostRenderer.TextPosition.MIDDLE_LEFT,
            PostRenderer.TextPosition.MIDDLE_CENTER,
            PostRenderer.TextPosition.MIDDLE_RIGHT,
            PostRenderer.TextPosition.BOTTOM_LEFT,
            PostRenderer.TextPosition.BOTTOM_CENTER,
            PostRenderer.TextPosition.BOTTOM_RIGHT
    };

    private static final String[] PHOTO_LAYOUT_LABELS = {
            "Автоматически", "Только активное", "Размещение вручную"
    };

    private static final PostRenderer.PhotoLayout[] PHOTO_LAYOUTS = {
            PostRenderer.PhotoLayout.AUTO,
            PostRenderer.PhotoLayout.SINGLE,
            PostRenderer.PhotoLayout.CUSTOM
    };

    private TextInputEditText etTitle;
    private TextInputEditText etText;
    private TextInputEditText etHashtags;
    private MaterialAutoCompleteTextView spinnerFormat;
    private MaterialAutoCompleteTextView spinnerTemplate;
    private MaterialAutoCompleteTextView spinnerAccent;
    private MaterialAutoCompleteTextView spinnerTextPosition;
    private MaterialAutoCompleteTextView spinnerPhotoLayout;
    private MaterialCheckBox cbWatermark;
    private ImageView previewImage;
    private ProgressBar previewProgress;
    private RecyclerView recyclerPhotos;
    private TextView tvPhotoCount;
    private PhotoAdapter photoAdapter;

    private FieldRepository fieldRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private int fieldId = -1;
    private PostRenderer.Format currentFormat = PostRenderer.Format.SQUARE;
    private PostRenderer.Template currentTemplate = PostRenderer.Template.MINIMAL;
    private PostRenderer.AccentColor currentAccent = PostRenderer.AccentColor.NEUTRAL;
    private PostRenderer.TextPosition currentTextPosition = PostRenderer.TextPosition.BOTTOM_LEFT;
    private PostRenderer.PhotoLayout currentPhotoLayout = PostRenderer.PhotoLayout.AUTO;
    private String currentPhotoPath = null;
    private List<Media> photoList = new ArrayList<>();
    private List<PostRenderer.PhotoPlacement> placements = new ArrayList<>();

    private float photoZoom = 1.0f;
    private float photoOffsetX = 0.0f;
    private float photoOffsetY = 0.0f;

    private boolean isRendering = false;
    private boolean previewRebuildScheduled = false;
    private int lastPreviewRequest = 0;

    private String pendingSaveFormat = "png";

    private final Runnable previewRebuildRunnable = () -> {
        previewRebuildScheduled = false;
        rebuildPreview();
    };

    private final ActivityResultLauncher<Intent> savePostLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) savePostToUri(uri);
                }
            });

    public static Intent newIntent(android.content.Context context, int fieldId) {
        Intent i = new Intent(context, SharePostActivity.class);
        i.putExtra(KEY_FIELD_ID, fieldId);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_post);

        fieldId = getIntent().getIntExtra(KEY_FIELD_ID, -1);
        if (fieldId == -1) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applyToToolbar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.share_post_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        etTitle = findViewById(R.id.et_title);
        etText = findViewById(R.id.et_text);
        etHashtags = findViewById(R.id.et_hashtags);
        spinnerFormat = findViewById(R.id.spinner_format);
        spinnerTemplate = findViewById(R.id.spinner_template);
        spinnerAccent = findViewById(R.id.spinner_accent);
        spinnerTextPosition = findViewById(R.id.spinner_text_position);
        spinnerPhotoLayout = findViewById(R.id.spinner_photo_layout);
        cbWatermark = findViewById(R.id.cb_watermark);
        previewImage = findViewById(R.id.preview_image);
        previewProgress = findViewById(R.id.preview_progress);
        recyclerPhotos = findViewById(R.id.recycler_photos);
        tvPhotoCount = findViewById(R.id.tv_photo_count);

        ThemeHelper.applyToInputView(etTitle);
        ThemeHelper.applyToInputView(etText);
        ThemeHelper.applyToInputView(etHashtags);
        ThemeHelper.applyToInputView(spinnerFormat);
        ThemeHelper.applyToInputView(spinnerTemplate);
        ThemeHelper.applyToInputView(spinnerAccent);
        ThemeHelper.applyToInputView(spinnerTextPosition);
        ThemeHelper.applyToInputView(spinnerPhotoLayout);

        spinnerFormat.setAdapter(new DropdownAdapter(this, FORMAT_LABELS));
        spinnerTemplate.setAdapter(new DropdownAdapter(this, TEMPLATE_LABELS));
        spinnerAccent.setAdapter(new DropdownAdapter(this, ACCENT_LABELS));
        spinnerTextPosition.setAdapter(new DropdownAdapter(this, TEXT_POSITION_LABELS));
        spinnerPhotoLayout.setAdapter(new DropdownAdapter(this, PHOTO_LAYOUT_LABELS));

        recyclerPhotos.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        photoAdapter = new PhotoAdapter();
        recyclerPhotos.setAdapter(photoAdapter);

        fieldRepository = new FieldRepository(this);

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            spinnerFormat.setText(FORMAT_LABELS[0], false);
            spinnerTemplate.setText(TEMPLATE_LABELS[0], false);
            spinnerAccent.setText(ACCENT_LABELS[0], false);
            spinnerTextPosition.setText(TEXT_POSITION_LABELS[6], false);
            spinnerPhotoLayout.setText(PHOTO_LAYOUT_LABELS[0], false);
        }

        setupListeners();
        loadField();
    }

    private void restoreState(Bundle s) {
        String title = s.getString(KEY_STATE_TITLE);
        String text = s.getString(KEY_STATE_TEXT);
        String hashtags = s.getString(KEY_STATE_HASHTAGS);
        currentPhotoPath = s.getString(KEY_STATE_PHOTO);

        int formatIdx = clampIndex(s.getInt(KEY_STATE_FORMAT, 0), FORMATS.length);
        int templateIdx = clampIndex(s.getInt(KEY_STATE_TEMPLATE, 0), TEMPLATES.length);
        int accentIdx = clampIndex(s.getInt(KEY_STATE_ACCENT, 0), ACCENTS.length);
        int posIdx = clampIndex(s.getInt(KEY_STATE_TEXT_POSITION, 6), TEXT_POSITIONS.length);
        int layoutIdx = clampIndex(s.getInt(KEY_STATE_PHOTO_LAYOUT, 0), PHOTO_LAYOUTS.length);

        currentFormat = FORMATS[formatIdx];
        currentTemplate = TEMPLATES[templateIdx];
        currentAccent = ACCENTS[accentIdx];
        currentTextPosition = TEXT_POSITIONS[posIdx];
        currentPhotoLayout = PHOTO_LAYOUTS[layoutIdx];

        photoZoom = s.getFloat(KEY_STATE_ZOOM, 1.0f);
        photoOffsetX = s.getFloat(KEY_STATE_OFFSET_X, 0.0f);
        photoOffsetY = s.getFloat(KEY_STATE_OFFSET_Y, 0.0f);

        String placementsJson = s.getString(KEY_STATE_PLACEMENTS);
        if (placementsJson != null && !placementsJson.isEmpty()) {
            try {
                Type type = new TypeToken<List<PostRenderer.PhotoPlacement>>(){}.getType();
                List<PostRenderer.PhotoPlacement> list = gson.fromJson(placementsJson, type);
                if (list != null) placements = list;
            } catch (Exception ignored) {}
        }

        if (title != null) etTitle.setText(title);
        if (text != null) etText.setText(text);
        if (hashtags != null) etHashtags.setText(hashtags);

        spinnerFormat.setText(FORMAT_LABELS[formatIdx], false);
        spinnerTemplate.setText(TEMPLATE_LABELS[templateIdx], false);
        spinnerAccent.setText(ACCENT_LABELS[accentIdx], false);
        spinnerTextPosition.setText(TEXT_POSITION_LABELS[posIdx], false);
        spinnerPhotoLayout.setText(PHOTO_LAYOUT_LABELS[layoutIdx], false);
        cbWatermark.setChecked(s.getBoolean(KEY_STATE_WATERMARK, true));
    }

    private int clampIndex(int value, int max) {
        if (value < 0) return 0;
        if (value >= max) return max - 1;
        return value;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_STATE_TITLE, textOf(etTitle));
        outState.putString(KEY_STATE_TEXT, textOf(etText));
        outState.putString(KEY_STATE_HASHTAGS, textOf(etHashtags));
        outState.putString(KEY_STATE_PHOTO, currentPhotoPath);
        outState.putInt(KEY_STATE_FORMAT, formatIndexOf(currentFormat));
        outState.putInt(KEY_STATE_TEMPLATE, templateIndexOf(currentTemplate));
        outState.putInt(KEY_STATE_ACCENT, accentIndexOf(currentAccent));
        outState.putInt(KEY_STATE_TEXT_POSITION, textPositionIndexOf(currentTextPosition));
        outState.putInt(KEY_STATE_PHOTO_LAYOUT, photoLayoutIndexOf(currentPhotoLayout));
        outState.putFloat(KEY_STATE_ZOOM, photoZoom);
        outState.putFloat(KEY_STATE_OFFSET_X, photoOffsetX);
        outState.putFloat(KEY_STATE_OFFSET_Y, photoOffsetY);
        outState.putBoolean(KEY_STATE_WATERMARK, cbWatermark.isChecked());

        try {
            outState.putString(KEY_STATE_PLACEMENTS, gson.toJson(placements));
        } catch (Exception ignored) {}
    }

    private String textOf(TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString();
    }

    private void setupListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { schedulePreviewRebuild(); }
        };
        etTitle.addTextChangedListener(watcher);
        etText.addTextChangedListener(watcher);
        etHashtags.addTextChangedListener(watcher);

        spinnerFormat.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < FORMATS.length) {
                currentFormat = FORMATS[position];
                regenerateAutoPlacements();
                schedulePreviewRebuild();
            }
        });

        spinnerTemplate.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < TEMPLATES.length) {
                currentTemplate = TEMPLATES[position];
                schedulePreviewRebuild();
            }
        });

        spinnerAccent.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < ACCENTS.length) {
                currentAccent = ACCENTS[position];
                schedulePreviewRebuild();
            }
        });

        spinnerTextPosition.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < TEXT_POSITIONS.length) {
                currentTextPosition = TEXT_POSITIONS[position];
                schedulePreviewRebuild();
            }
        });

        spinnerPhotoLayout.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < PHOTO_LAYOUTS.length) {
                currentPhotoLayout = PHOTO_LAYOUTS[position];
                if (currentPhotoLayout == PostRenderer.PhotoLayout.CUSTOM && placements.isEmpty()) {
                    regenerateAutoPlacements();
                }
                schedulePreviewRebuild();
            }
        });

        cbWatermark.setOnCheckedChangeListener((btn, checked) -> schedulePreviewRebuild());

        findViewById(R.id.btn_placements).setOnClickListener(v -> showPlacementsDialog());
        findViewById(R.id.btn_crop).setOnClickListener(v -> showCropDialog());
        findViewById(R.id.btn_share).setOnClickListener(v -> sharePost());
        findViewById(R.id.btn_save).setOnClickListener(v -> showSaveFormatDialog());
    }

    private void loadField() {
        executor.execute(() -> {
            try {
                Field field = fieldRepository.getById(fieldId);
                List<Media> allMedia = fieldRepository.getMediaByFieldId(fieldId);
                List<Media> photos = new ArrayList<>();
                if (allMedia != null) {
                    for (Media m : allMedia) {
                        if ("image".equals(m.getType())) photos.add(m);
                    }
                }
                final Field f = field;
                final List<Media> ph = photos;
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (f == null) {
                        Toast.makeText(this, R.string.share_post_load_error, Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    if (TextUtils.isEmpty(etTitle.getText())) etTitle.setText(f.getTitle() == null ? "" : f.getTitle());
                    if (TextUtils.isEmpty(etText.getText())) etText.setText(f.getText() == null ? "" : f.getText());

                    photoList.clear();
                    photoList.addAll(ph);
                    photoAdapter.notifyDataSetChanged();

                    if (currentPhotoPath == null && !ph.isEmpty()) {
                        currentPhotoPath = ph.get(0).getPath();
                        photoAdapter.notifyDataSetChanged();
                    }

                    regenerateAutoPlacements();
                    updatePhotoCountLabel();
                    schedulePreviewRebuild();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, R.string.share_post_load_error, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void updatePhotoCountLabel() {
        if (tvPhotoCount == null) return;
        int total = photoList.size();
        int used;
        if (currentPhotoLayout == PostRenderer.PhotoLayout.SINGLE) {
            used = currentPhotoPath == null ? 0 : 1;
        } else if (currentPhotoLayout == PostRenderer.PhotoLayout.CUSTOM) {
            used = Math.min(placements.size(), total);
        } else {
            used = Math.min(total, 9);
        }
        String text = getString(R.string.share_post_photo_count, used, total);
        tvPhotoCount.setText(text);
    }

    private void regenerateAutoPlacements() {
        List<String> paths = new ArrayList<>();
        for (Media m : photoList) paths.add(m.getPath());
        List<PostRenderer.PhotoPlacement> auto = PostRenderer.autoLayoutPhotos(paths, currentFormat);
        placements.clear();
        placements.addAll(auto);
        updatePhotoCountLabel();
    }

    private int formatIndexOf(PostRenderer.Format f) {
        for (int i = 0; i < FORMATS.length; i++) if (FORMATS[i] == f) return i;
        return 0;
    }

    private int templateIndexOf(PostRenderer.Template t) {
        for (int i = 0; i < TEMPLATES.length; i++) if (TEMPLATES[i] == t) return i;
        return 0;
    }

    private int accentIndexOf(PostRenderer.AccentColor a) {
        for (int i = 0; i < ACCENTS.length; i++) if (ACCENTS[i] == a) return i;
        return 0;
    }

    private int textPositionIndexOf(PostRenderer.TextPosition p) {
        for (int i = 0; i < TEXT_POSITIONS.length; i++) if (TEXT_POSITIONS[i] == p) return i;
        return 0;
    }

    private int photoLayoutIndexOf(PostRenderer.PhotoLayout l) {
        for (int i = 0; i < PHOTO_LAYOUTS.length; i++) if (PHOTO_LAYOUTS[i] == l) return i;
        return 0;
    }

    private PostRenderer.Config buildConfig() {
        PostRenderer.Config cfg = new PostRenderer.Config();
        cfg.format = currentFormat;
        cfg.template = currentTemplate;
        cfg.accent = currentAccent;
        cfg.textPosition = currentTextPosition;
        cfg.photoLayout = currentPhotoLayout;
        cfg.title = textOf(etTitle);
        cfg.text = textOf(etText);
        cfg.hashtags = textOf(etHashtags);
        cfg.showWatermark = cbWatermark.isChecked();
        cfg.watermark = getString(R.string.app_name);
        cfg.photoZoom = photoZoom;
        cfg.photoOffsetX = photoOffsetX;
        cfg.photoOffsetY = photoOffsetY;

        List<String> paths = new ArrayList<>();
        for (Media m : photoList) paths.add(m.getPath());
        if (currentPhotoPath != null) {
            int idx = -1;
            for (int i = 0; i < paths.size(); i++) {
                if (paths.get(i).equals(currentPhotoPath)) { idx = i; break; }
            }
            if (idx > 0) {
                String tmp = paths.get(0);
                paths.set(0, paths.get(idx));
                paths.set(idx, tmp);
            }
        }
        cfg.photoPaths = paths;

        if (currentPhotoLayout == PostRenderer.PhotoLayout.CUSTOM) {
            List<PostRenderer.PhotoPlacement> copy = new ArrayList<>();
            for (PostRenderer.PhotoPlacement p : placements) {
                PostRenderer.PhotoPlacement cp = new PostRenderer.PhotoPlacement();
                cp.path = p.path;
                cp.gridCol = p.gridCol;
                cp.gridRow = p.gridRow;
                cp.gridColSpan = p.gridColSpan;
                cp.gridRowSpan = p.gridRowSpan;
                cp.zoom = p.zoom;
                cp.offsetX = p.offsetX;
                cp.offsetY = p.offsetY;
                copy.add(cp);
            }
            cfg.placements = copy;
        } else if (currentPhotoLayout == PostRenderer.PhotoLayout.AUTO) {
            cfg.placements = PostRenderer.autoLayoutPhotos(paths, currentFormat);
        }

        return cfg;
    }

    private void schedulePreviewRebuild() {
        if (previewRebuildScheduled) return;
        previewRebuildScheduled = true;
        mainHandler.removeCallbacks(previewRebuildRunnable);
        mainHandler.postDelayed(previewRebuildRunnable, 250);
    }

    private void rebuildPreview() {
        if (isRendering) {
            schedulePreviewRebuild();
            return;
        }
        isRendering = true;
        lastPreviewRequest++;
        final int requestId = lastPreviewRequest;
        previewProgress.setVisibility(View.VISIBLE);

        final PostRenderer.Config cfg = buildConfig();
        int previewWidth = (int) (360 * getResources().getDisplayMetrics().density);

        executor.execute(() -> {
            final Bitmap bmp;
            try {
                bmp = PostRenderer.render(this, cfg, previewWidth);
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (requestId == lastPreviewRequest) {
                        isRendering = false;
                        previewProgress.setVisibility(View.GONE);
                    }
                });
                return;
            }
            mainHandler.post(() -> {
                if (requestId != lastPreviewRequest) {
                    if (bmp != null && !bmp.isRecycled()) bmp.recycle();
                    return;
                }
                isRendering = false;
                previewProgress.setVisibility(View.GONE);
                if (isFinishing() || isDestroyed()) {
                    if (bmp != null && !bmp.isRecycled()) bmp.recycle();
                    return;
                }
                if (bmp != null) previewImage.setImageBitmap(bmp);
            });
        });
    }

    private void showPlacementsDialog() {
        if (photoList.isEmpty()) {
            Toast.makeText(this, R.string.share_post_no_photos_in_record, Toast.LENGTH_SHORT).show();
            return;
        }
        if (placements.isEmpty()) regenerateAutoPlacements();

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_photo_placements, null);
        RecyclerView rv = view.findViewById(R.id.recycler_placements);
        rv.setLayoutManager(new LinearLayoutManager(this));

        final List<PostRenderer.PhotoPlacement> workingCopy = new ArrayList<>();
        for (PostRenderer.PhotoPlacement p : placements) {
            PostRenderer.PhotoPlacement cp = new PostRenderer.PhotoPlacement();
            cp.path = p.path;
            cp.gridCol = p.gridCol;
            cp.gridRow = p.gridRow;
            cp.gridColSpan = p.gridColSpan;
            cp.gridRowSpan = p.gridRowSpan;
            cp.zoom = p.zoom;
            cp.offsetX = p.offsetX;
            cp.offsetY = p.offsetY;
            workingCopy.add(cp);
        }

        PhotoPlacementsAdapter adapter = new PhotoPlacementsAdapter(workingCopy, () -> {});
        rv.setAdapter(adapter);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.share_post_placements_title)
                .setView(view)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    placements.clear();
                    placements.addAll(workingCopy);
                    currentPhotoLayout = PostRenderer.PhotoLayout.CUSTOM;
                    spinnerPhotoLayout.setText(PHOTO_LAYOUT_LABELS[2], false);
                    updatePhotoCountLabel();
                    schedulePreviewRebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.share_post_placements_auto, (d, w) -> {
                    currentPhotoLayout = PostRenderer.PhotoLayout.AUTO;
                    spinnerPhotoLayout.setText(PHOTO_LAYOUT_LABELS[0], false);
                    regenerateAutoPlacements();
                    schedulePreviewRebuild();
                })
                .show();
    }

    private void showCropDialog() {
        if (currentPhotoPath == null) {
            Toast.makeText(this, R.string.share_post_crop_no_photo, Toast.LENGTH_SHORT).show();
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_photo_crop, null);
        ImageView preview = view.findViewById(R.id.crop_preview);
        SeekBar seekZoom = view.findViewById(R.id.seek_zoom);
        SeekBar seekX = view.findViewById(R.id.seek_offset_x);
        SeekBar seekY = view.findViewById(R.id.seek_offset_y);
        TextView tvZoomValue = view.findViewById(R.id.crop_zoom_value);

        Glide.with(this).load(new File(currentPhotoPath)).centerCrop().into(preview);

        float range = PostRenderer.MAX_ZOOM - PostRenderer.MIN_ZOOM;
        int zoomProgress = (int) ((photoZoom - PostRenderer.MIN_ZOOM) / range * 200);
        seekZoom.setProgress(Math.max(0, Math.min(200, zoomProgress)));
        seekX.setProgress((int) ((photoOffsetX + 1f) * 100));
        seekY.setProgress((int) ((photoOffsetY + 1f) * 100));
        if (tvZoomValue != null) {
            tvZoomValue.setText(String.format(Locale.US, "%.1f×", photoZoom));
        }

        seekZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float z = PostRenderer.MIN_ZOOM + progress / 200f * range;
                if (tvZoomValue != null) {
                    tvZoomValue.setText(String.format(Locale.US, "%.1f×", z));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.share_post_crop_title)
                .setView(view)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    float newZoom = PostRenderer.MIN_ZOOM + seekZoom.getProgress() / 200f * range;
                    float newOffX = seekX.getProgress() / 100f - 1f;
                    float newOffY = seekY.getProgress() / 100f - 1f;

                    if (currentPhotoLayout == PostRenderer.PhotoLayout.CUSTOM) {
                        for (PostRenderer.PhotoPlacement p : placements) {
                            if (p.path.equals(currentPhotoPath)) {
                                p.zoom = newZoom;
                                p.offsetX = newOffX;
                                p.offsetY = newOffY;
                                break;
                            }
                        }
                    } else {
                        photoZoom = newZoom;
                        photoOffsetX = newOffX;
                        photoOffsetY = newOffY;
                    }
                    schedulePreviewRebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.share_post_crop_reset, (d, w) -> {
                    if (currentPhotoLayout == PostRenderer.PhotoLayout.CUSTOM) {
                        for (PostRenderer.PhotoPlacement p : placements) {
                            if (p.path.equals(currentPhotoPath)) {
                                p.zoom = 1.0f;
                                p.offsetX = 0.0f;
                                p.offsetY = 0.0f;
                                break;
                            }
                        }
                    } else {
                        photoZoom = 1.0f;
                        photoOffsetX = 0.0f;
                        photoOffsetY = 0.0f;
                    }
                    schedulePreviewRebuild();
                })
                .show();
    }

    private void sharePost() {
        final PostRenderer.Config cfg = buildConfig();
        previewProgress.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            boolean success = false;
            Uri uri = null;
            File file = null;
            try {
                Bitmap bmp = PostRenderer.render(this, cfg, cfg.format.width);
                file = PostRenderer.saveBitmapToCache(this, bmp);
                if (!bmp.isRecycled()) bmp.recycle();
                uri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", file);
                success = true;
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean finalSuccess = success;
            final Uri finalUri = uri;
            final File finalFile = file;
            mainHandler.post(() -> {
                previewProgress.setVisibility(View.GONE);
                if (isFinishing() || isDestroyed()) {
                    if (finalFile != null && finalFile.exists()) finalFile.delete();
                    return;
                }
                if (!finalSuccess || finalUri == null) {
                    Toast.makeText(this, R.string.share_post_save_error, Toast.LENGTH_SHORT).show();
                    if (finalFile != null && finalFile.exists()) finalFile.delete();
                    return;
                }
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("image/png");
                share.putExtra(Intent.EXTRA_STREAM, finalUri);
                share.putExtra(Intent.EXTRA_TEXT, buildShareText());
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share,
                        getString(R.string.share_post_share_chooser)));

                // ИСПРАВЛЕНО (3.2.3): удаляем временный PNG через 5 минут
                // после передачи в шаринг. Раньше файл оставался в
                // cache/share_posts/ навсегда, пока система не очистит кэш.
                if (finalFile != null) {
                    mainHandler.postDelayed(() -> {
                        if (finalFile.exists() && !finalFile.delete()) {
                            Log.w(TAG, "Failed to delete shared PNG: "
                                    + finalFile.getAbsolutePath());
                        }
                    }, SHARE_FILE_LIFETIME_MS);
                }
            });
        });
    }

    private String buildShareText() {
        StringBuilder sb = new StringBuilder();
        String title = textOf(etTitle).trim();
        String text = textOf(etText).trim();
        String hashtags = textOf(etHashtags).trim();

        if (!title.isEmpty()) sb.append(title);
        if (!text.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(text);
        }
        if (!hashtags.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(hashtags);
        }
        return sb.toString();
    }

    private void showSaveFormatDialog() {
        String[] items = {
                getString(R.string.share_post_save_format_png),
                getString(R.string.share_post_save_format_pdf),
                getString(R.string.share_post_save_format_html)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.share_post_save_format_title)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        pendingSaveFormat = "png";
                        launchSave("image/png", ".png");
                    } else if (which == 1) {
                        pendingSaveFormat = "pdf";
                        launchSave("application/pdf", ".pdf");
                    } else {
                        pendingSaveFormat = "html";
                        launchSave("text/html", ".html");
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void launchSave(String mime, String ext) {
        String title = textOf(etTitle).trim();
        if (title.isEmpty()) title = "post";
        String safeName = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safeName.length() > 40) safeName = safeName.substring(0, 40);
        if (safeName.trim().isEmpty()) safeName = "post";
        String fileName = safeName + "_" + System.currentTimeMillis() + ext;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        savePostLauncher.launch(intent);
    }

    private void savePostToUri(Uri uri) {
        final PostRenderer.Config cfg = buildConfig();
        final String format = pendingSaveFormat;
        previewProgress.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            boolean success = false;
            try {
                Bitmap bmp = PostRenderer.render(this, cfg, cfg.format.width);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("OutputStream null");
                    if ("png".equals(format)) {
                        success = PostExporter.savePng(bmp, out);
                    } else if ("pdf".equals(format)) {
                        success = PostExporter.savePdf(bmp, out);
                    } else {
                        success = PostExporter.saveHtml(this, cfg, bmp, out);
                    }
                }
                if (!bmp.isRecycled()) bmp.recycle();
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean finalSuccess = success;
            final String finalFormat = format;
            mainHandler.post(() -> {
                previewProgress.setVisibility(View.GONE);
                if (isFinishing() || isDestroyed()) return;
                if (finalSuccess) {
                    int msgRes;
                    if ("pdf".equals(finalFormat)) msgRes = R.string.share_post_saved_pdf;
                    else if ("html".equals(finalFormat)) msgRes = R.string.share_post_saved_html;
                    else msgRes = R.string.share_post_saved_png;
                    Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.share_post_save_error, Toast.LENGTH_SHORT).show();
                }
            });
        });
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
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.VH> {

        private static final int TYPE_NONE = 0;
        private static final int TYPE_PHOTO = 1;

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_NONE : TYPE_PHOTO;
        }

        @Override
        public int getItemCount() {
            return photoList.size() + 1;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_post_photo, parent, false);
            int size = (int) (96 * parent.getResources().getDisplayMetrics().density);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(size, size);
            lp.setMargins(4, 4, 4, 4);
            v.setLayoutParams(lp);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            if (getItemViewType(position) == TYPE_NONE) {
                h.image.setImageDrawable(null);
                h.image.setBackgroundColor(0xFFDDDDDD);
                h.label.setVisibility(View.VISIBLE);
                h.label.setText(R.string.share_post_no_photo);
                boolean selected = currentPhotoPath == null;
                h.itemView.setAlpha(selected ? 1f : 0.6f);
                h.itemView.setOnClickListener(v -> {
                    currentPhotoPath = null;
                    notifyDataSetChanged();
                    updatePhotoCountLabel();
                    schedulePreviewRebuild();
                });
            } else {
                Media m = photoList.get(position - 1);
                h.label.setVisibility(View.GONE);
                h.image.setBackgroundColor(0x00000000);
                Glide.with(h.image.getContext())
                        .load(new File(m.getPath()))
                        .centerCrop()
                        .into(h.image);
                boolean selected = m.getPath().equals(currentPhotoPath);
                h.itemView.setAlpha(selected ? 1f : 0.6f);
                h.itemView.setOnClickListener(v -> {
                    currentPhotoPath = m.getPath();
                    notifyDataSetChanged();
                    updatePhotoCountLabel();
                    schedulePreviewRebuild();
                });
            }
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView image;
            TextView label;

            VH(View v) {
                super(v);
                image = v.findViewById(R.id.post_photo_image);
                label = v.findViewById(R.id.post_photo_label);
            }
        }
    }
}