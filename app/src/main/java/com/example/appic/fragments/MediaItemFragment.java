package com.example.appic.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.example.appic.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MediaItemFragment extends Fragment {

    private static final String TAG = "MediaItemFragment";
    private static final String ARG_MEDIA_PATH = "media_path";
    private static final String ARG_MEDIA_TYPE = "media_type";

    private static final long CONTROLS_AUTO_HIDE_MS = 3000L;
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 500L;

    private String currentPath;
    private SubsamplingScaleImageView photoView;
    private VideoView videoView;

    private volatile boolean isCurrentPage = false;
    private boolean isVideo = false;

    private View controlsOverlay;
    private ImageButton btnPlayPause;
    private SeekBar seekVideo;
    private TextView tvCurrentTime;
    private TextView tvDuration;
    private MaterialButton btnRewind10;
    private MaterialButton btnForward10;
    private ImageButton btnVolume;
    private MaterialButton btnSpeed;
    private LinearLayout volumePanel;
    private SeekBar seekVolume;

    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private float currentSpeed = 1.0f;
    private boolean controlsVisible = false;
    private boolean customControlsAvailable = false;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService copyExecutor = Executors.newSingleThreadExecutor();

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (videoView != null && videoView.isPlaying()) {
                updateSeekBar();
                progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
            }
        }
    };

    private final Runnable hideControlsRunnable = this::hideControls;

    private final ActivityResultLauncher<Intent> saveImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri targetUri = result.getData().getData();
                    if (targetUri != null) copyFileToUri(targetUri);
                }
            });

    public static MediaItemFragment newInstance(String path, String type) {
        MediaItemFragment f = new MediaItemFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MEDIA_PATH, path);
        args.putString(ARG_MEDIA_TYPE, type);
        f.setArguments(args);
        return f;
    }

    public String getMediaPath() {
        return currentPath;
    }

    public void setCurrentPage(boolean current) {
        isCurrentPage = current;
        if (current) {
            startVideo();
            if (isVideo && customControlsAvailable) showControls();
        } else {
            pauseVideo();
            if (customControlsAvailable) hideControls();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_media_item, container, false);
        String path = getArguments() != null ? getArguments().getString(ARG_MEDIA_PATH) : null;
        String type = getArguments() != null ? getArguments().getString(ARG_MEDIA_TYPE) : null;
        currentPath = path;
        if (path == null) return view;

        String lower = path.toLowerCase(Locale.ROOT);
        isVideo = "video".equals(type) || lower.endsWith(".mp4") || lower.endsWith(".3gp")
                || lower.endsWith(".mov") || lower.endsWith(".avi");

        photoView = view.findViewById(R.id.photo_view);
        videoView = view.findViewById(R.id.video_view);

        if (isVideo) {
            if (photoView != null) photoView.setVisibility(View.GONE);
            if (videoView != null) videoView.setVisibility(View.VISIBLE);
            setupVideoView();
            setupVideoControls(view);
        } else {
            if (videoView != null) videoView.setVisibility(View.GONE);
            if (photoView != null) {
                photoView.setVisibility(View.VISIBLE);
                setupPhoto();
            }
        }
        return view;
    }

    private void setupPhoto() {
        if (photoView == null) return;
        File imgFile = new File(currentPath);
        if (!imgFile.exists()) return;
        try {
            int orientation = getExifOrientation(imgFile);
            photoView.setOrientation(orientation);

            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", imgFile);
            photoView.setImage(ImageSource.uri(uri));
            photoView.setMaxScale(10.0f);
            photoView.setDoubleTapZoomScale(5.0f);
            photoView.setOnLongClickListener(v -> {
                showCopyDialog();
                return true;
            });
        } catch (Exception e) {
            Log.e(TAG, "setupPhoto failed", e);
        }
    }

    private int getExifOrientation(File file) {
        try (InputStream in = new FileInputStream(file)) {
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException e) {
            Log.w(TAG, "getExifOrientation failed for " + file.getName(), e);
            return 0;
        }
    }

    private void showCopyDialog() {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Копировать фото")
                .setMessage("Сохранить изображение в выбранную папку?")
                .setPositiveButton("Да", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    File srcFile = new File(currentPath);
                    intent.putExtra(Intent.EXTRA_TITLE, srcFile.getName());
                    saveImageLauncher.launch(intent);
                })
                .setNegativeButton("Нет", null)
                .show();
    }

    private void copyFileToUri(Uri targetUri) {
        if (!isAdded()) return;
        final File srcFile = new File(currentPath);
        if (!srcFile.exists()) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Исходный файл не найден", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        final Context appCtx = requireContext().getApplicationContext();
        try {
            copyExecutor.execute(() -> {
                boolean ok = false;
                Exception error = null;
                try (InputStream in = new FileInputStream(srcFile);
                     OutputStream out = appCtx.getContentResolver().openOutputStream(targetUri)) {
                    if (out == null) throw new IOException("OutputStream is null");
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    ok = true;
                } catch (Exception e) {
                    error = e;
                }
                if (!ok && error != null) {
                    Log.w(TAG, "copyFileToUri failed for " + srcFile.getName(), error);
                }
                final boolean finalOk = ok;
                progressHandler.post(() -> {
                    if (!isAdded() || getContext() == null) return;
                    Toast.makeText(getContext(),
                            finalOk ? "Фото сохранено" : "Ошибка копирования",
                            Toast.LENGTH_SHORT).show();
                });
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "copyFileToUri rejected: executor shutdown", e);
            // ИСПРАВЛЕНО (п.3): getContext() может быть null, если фрагмент уже откреплён.
            if (getContext() != null) {
                Toast.makeText(getContext(), "Не удалось сохранить: экран закрыт",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupVideoView() {
        if (videoView == null) return;
        File videoFile = new File(currentPath);
        if (!videoFile.exists()) return;

        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", videoFile);
            videoView.setVideoURI(uri);
            videoView.setOnPreparedListener(mp -> {
                mediaPlayer = mp;
                mp.setLooping(false);
                applySpeed();
                updateSeekBar();
                if (isCurrentPage) {
                    try { videoView.start(); } catch (Exception ignored) {}
                    if (customControlsAvailable) showControls();
                }
            });
            videoView.setOnCompletionListener(mp -> {
                stopProgressUpdates();
                updatePlayPauseIcon();
                if (customControlsAvailable) showControls();
            });
            videoView.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "VideoView error: what=" + what + " extra=" + extra);
                return false;
            });
            videoView.setOnClickListener(v -> {
                if (customControlsAvailable) toggleControls();
            });
        } catch (Exception e) {
            Log.e(TAG, "setupVideoView failed", e);
        }
    }

    private void setupVideoControls(View root) {
        controlsOverlay = root.findViewById(R.id.video_controls_overlay);
        if (controlsOverlay == null) {
            Log.w(TAG, "video_controls_overlay not found — работаем как обычный VideoView.");
            customControlsAvailable = false;
            return;
        }

        btnPlayPause = root.findViewById(R.id.btn_play_pause);
        seekVideo = root.findViewById(R.id.seek_video);
        tvCurrentTime = root.findViewById(R.id.tv_current_time);
        tvDuration = root.findViewById(R.id.tv_duration);
        btnRewind10 = root.findViewById(R.id.btn_rewind_10);
        btnForward10 = root.findViewById(R.id.btn_forward_10);
        btnVolume = root.findViewById(R.id.btn_volume);
        btnSpeed = root.findViewById(R.id.btn_speed);
        volumePanel = root.findViewById(R.id.volume_panel);
        seekVolume = root.findViewById(R.id.seek_volume);

        if (btnPlayPause == null || seekVideo == null
                || tvCurrentTime == null || tvDuration == null
                || btnRewind10 == null || btnForward10 == null
                || btnVolume == null || btnSpeed == null
                || volumePanel == null || seekVolume == null) {
            Log.w(TAG, "Не все Video View найдены — кастомные контролы отключены.");
            customControlsAvailable = false;
            return;
        }

        customControlsAvailable = true;

        controlsOverlay.setOnClickListener(v -> hideControls());
        btnPlayPause.setOnClickListener(v -> {
            togglePlayPause();
            resetAutoHide();
        });
        btnRewind10.setOnClickListener(v -> {
            seekBy(-10_000);
            resetAutoHide();
        });
        btnForward10.setOnClickListener(v -> {
            seekBy(10_000);
            resetAutoHide();
        });
        btnSpeed.setOnClickListener(v -> {
            showSpeedMenu();
            resetAutoHide();
        });

        seekVideo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) tvCurrentTime.setText(formatTime(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                progressHandler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                if (videoView != null) {
                    videoView.seekTo(sb.getProgress());
                    updateSeekBar();
                }
                resetAutoHide();
            }
        });

        setupVolumeControl();
    }

    private void setupVolumeControl() {
        if (getContext() == null) return;
        audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null || seekVolume == null || btnVolume == null) return;

        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        seekVolume.setMax(maxVolume);
        seekVolume.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));

        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && audioManager != null) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                progressHandler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                resetAutoHide();
            }
        });

        btnVolume.setOnClickListener(v -> {
            if (volumePanel == null) return;
            if (volumePanel.getVisibility() == View.VISIBLE) {
                volumePanel.setVisibility(View.GONE);
            } else {
                if (audioManager != null) {
                    seekVolume.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
                }
                volumePanel.setVisibility(View.VISIBLE);
            }
            resetAutoHide();
        });
    }

    private void togglePlayPause() {
        if (videoView == null) return;
        if (videoView.isPlaying()) {
            videoView.pause();
            stopProgressUpdates();
        } else {
            videoView.start();
            startProgressUpdates();
        }
        updatePlayPauseIcon();
    }

    private void updatePlayPauseIcon() {
        if (btnPlayPause == null) return;
        boolean playing = videoView != null && videoView.isPlaying();
        btnPlayPause.setImageResource(playing
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play);
    }

    private void seekBy(int deltaMs) {
        if (videoView == null) return;
        int duration = videoView.getDuration();
        if (duration <= 0) return;
        int newPos = videoView.getCurrentPosition() + deltaMs;
        if (newPos < 0) newPos = 0;
        if (newPos > duration) newPos = duration;
        videoView.seekTo(newPos);
        updateSeekBar();
    }

    private void applySpeed() {
        if (mediaPlayer == null) return;
        try {
            PlaybackParams params = new PlaybackParams();
            params.setSpeed(currentSpeed);
            params.setPitch(1.0f);
            mediaPlayer.setPlaybackParams(params);
        } catch (Exception e) {
            Log.w(TAG, "applySpeed failed", e);
        }
    }

    private void showSpeedMenu() {
        if (!isAdded()) return;
        final float[] speeds = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        final String[] labels = {"0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"};
        int selected = 2;
        for (int i = 0; i < speeds.length; i++) {
            if (Math.abs(speeds[i] - currentSpeed) < 0.01f) {
                selected = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Скорость воспроизведения")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    currentSpeed = speeds[which];
                    if (btnSpeed != null) btnSpeed.setText(labels[which]);
                    applySpeed();
                    dialog.dismiss();
                })
                .show();
    }

    private void updateSeekBar() {
        if (videoView == null || seekVideo == null || tvCurrentTime == null || tvDuration == null) return;
        int pos = videoView.getCurrentPosition();
        int dur = videoView.getDuration();
        if (dur > 0) {
            seekVideo.setMax(dur);
            seekVideo.setProgress(pos);
            tvCurrentTime.setText(formatTime(pos));
            tvDuration.setText(formatTime(dur));
        }
    }

    private String formatTime(int ms) {
        if (ms < 0) ms = 0;
        int totalSec = ms / 1000;
        int h = totalSec / 3600;
        int m = (totalSec % 3600) / 60;
        int s = totalSec % 60;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.getDefault(), "%d:%02d", m, s);
    }

    private void startProgressUpdates() {
        progressHandler.removeCallbacks(progressUpdater);
        progressHandler.post(progressUpdater);
    }

    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(progressUpdater);
    }

    private void showControls() {
        if (controlsOverlay == null || !customControlsAvailable) return;
        controlsOverlay.setVisibility(View.VISIBLE);
        controlsVisible = true;
        updatePlayPauseIcon();
        resetAutoHide();
    }

    private void hideControls() {
        if (controlsOverlay == null) return;
        controlsOverlay.setVisibility(View.GONE);
        controlsVisible = false;
        if (volumePanel != null) volumePanel.setVisibility(View.GONE);
        progressHandler.removeCallbacks(hideControlsRunnable);
    }

    private void toggleControls() {
        if (!customControlsAvailable) return;
        if (controlsVisible) hideControls();
        else showControls();
    }

    private void resetAutoHide() {
        if (!controlsVisible) return;
        progressHandler.removeCallbacks(hideControlsRunnable);
        progressHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS);
    }

    public void startVideo() {
        if (videoView == null) return;
        if (mediaPlayer != null && !videoView.isPlaying()) {
            try { videoView.start(); } catch (Exception ignored) {}
            updatePlayPauseIcon();
            startProgressUpdates();
        }
    }

    public void pauseVideo() {
        if (videoView != null) {
            try { videoView.pause(); } catch (Exception ignored) {}
            updatePlayPauseIcon();
            stopProgressUpdates();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        pauseVideo();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isCurrentPage = false;
        stopProgressUpdates();
        progressHandler.removeCallbacksAndMessages(null);
        if (videoView != null) {
            try { videoView.stopPlayback(); } catch (Exception ignored) {}
            try { videoView.setVideoURI(null); } catch (Exception ignored) {}
            videoView = null;
        }
        mediaPlayer = null;
        photoView = null;
        controlsOverlay = null;
        btnPlayPause = null;
        seekVideo = null;
        tvCurrentTime = null;
        tvDuration = null;
        btnRewind10 = null;
        btnForward10 = null;
        btnVolume = null;
        btnSpeed = null;
        volumePanel = null;
        seekVolume = null;
        customControlsAvailable = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        copyExecutor.shutdown();
    }
}