package com.example.appic.utils;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.appic.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MediaHelper {

    private static final String TAG = "MediaHelper";

    private static final String KEY_PHOTO_PATH = "media_helper_photo_path";
    private static final String KEY_VIDEO_PATH = "media_helper_video_path";
    private static final String KEY_PENDING_ACTION = "media_helper_pending_action";

    private enum PendingAction { NONE, PHOTO, VIDEO }

    private final WeakReference<Activity> activityRef;
    private final File mediaDir;
    private final String prefix;
    private final OnMediaCapturedListener listener;

    private File tempPhotoFile;
    private File tempVideoFile;
    private PendingAction pendingAction = PendingAction.NONE;

    private ActivityResultLauncher<Uri> cameraPhotoLauncher;
    private ActivityResultLauncher<Intent> cameraVideoLauncher;
    private ActivityResultLauncher<Void> galleryLauncher;
    private ActivityResultLauncher<String[]> documentsLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    private static final ThreadLocal<SimpleDateFormat> timestampSdf = ThreadLocal.withInitial(
            () -> new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
    );

    public interface OnMediaCapturedListener {
        void onMediaCaptured(File file, boolean isVideo);
        void onMediaSelected(Uri uri, String mimeType);
        void onError(String message);
    }

    public MediaHelper(Activity activity, File mediaDir, String prefix,
                       OnMediaCapturedListener listener) {
        this.activityRef = new WeakReference<>(activity);
        this.mediaDir = mediaDir;
        this.prefix = prefix;
        this.listener = listener;
        if (!mediaDir.exists()) mediaDir.mkdirs();
    }

    public void setLaunchers(ActivityResultLauncher<Uri> photo,
                             ActivityResultLauncher<Intent> video,
                             ActivityResultLauncher<Void> gallery,
                             ActivityResultLauncher<String[]> documents,
                             ActivityResultLauncher<String> cameraPermission) {
        this.cameraPhotoLauncher = photo;
        this.cameraVideoLauncher = video;
        this.galleryLauncher = gallery;
        this.documentsLauncher = documents;
        this.cameraPermissionLauncher = cameraPermission;
    }

    public void saveState(Bundle outState) {
        if (outState == null) return;
        if (tempPhotoFile != null) outState.putString(KEY_PHOTO_PATH, tempPhotoFile.getAbsolutePath());
        if (tempVideoFile != null) outState.putString(KEY_VIDEO_PATH, tempVideoFile.getAbsolutePath());
        outState.putString(KEY_PENDING_ACTION, pendingAction.name());
    }

    public void restoreState(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        String photoPath = savedInstanceState.getString(KEY_PHOTO_PATH);
        if (photoPath != null) tempPhotoFile = new File(photoPath);
        String videoPath = savedInstanceState.getString(KEY_VIDEO_PATH);
        if (videoPath != null) tempVideoFile = new File(videoPath);
        String pending = savedInstanceState.getString(KEY_PENDING_ACTION);
        if (pending != null) {
            try { pendingAction = PendingAction.valueOf(pending); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    public void cleanupOrphanedTempFiles() {
        if (tempPhotoFile != null && tempPhotoFile.exists()) tempPhotoFile.delete();
        if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
        tempPhotoFile = null;
        tempVideoFile = null;
    }

    /**
     * ИСПРАВЛЕНО (№7): Material-диалог через DialogHelper.showItems.
     * Убрана «Отмена» из списка — теперь это стандартная negative-кнопка Material.
     */
    public void showMediaSourceDialog() {
        Activity activity = activityRef.get();
        if (activity == null || activity.isFinishing()) return;
        String[] options = {
                activity.getString(R.string.take_photo),
                activity.getString(R.string.record_video),
                activity.getString(R.string.pick_photo_video),
                activity.getString(R.string.choose_document)
        };
        DialogHelper.showItems(activity,
                activity.getString(R.string.media_source_dialog_title),
                options,
                index -> {
                    if (index == 0) takePhotoFromCamera();
                    else if (index == 1) takeVideoFromCamera();
                    else if (index == 2) chooseMixedFromGallery();
                    else if (index == 3) chooseDocument();
                });
    }

    public void onCameraPhotoResult(boolean success) {
        Activity activity = activityRef.get();
        if (!success) {
            if (tempPhotoFile != null && tempPhotoFile.exists()) tempPhotoFile.delete();
            tempPhotoFile = null;
            return;
        }
        if (tempPhotoFile != null && tempPhotoFile.exists() && tempPhotoFile.length() > 0) {
            if (listener != null) listener.onMediaCaptured(tempPhotoFile, false);
            tempPhotoFile = null;
        } else {
            if (tempPhotoFile != null && tempPhotoFile.exists()) tempPhotoFile.delete();
            tempPhotoFile = null;
            if (listener != null && activity != null) {
                listener.onError(activity.getString(R.string.error_photo_not_received));
            }
        }
    }

    public void onCameraVideoResult(boolean success) {
        Activity activity = activityRef.get();
        if (!success) {
            if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
            tempVideoFile = null;
            return;
        }
        if (tempVideoFile != null && tempVideoFile.exists() && tempVideoFile.length() > 0) {
            if (listener != null) listener.onMediaCaptured(tempVideoFile, true);
            tempVideoFile = null;
        } else {
            if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
            tempVideoFile = null;
            if (listener != null && activity != null) {
                listener.onError(activity.getString(R.string.error_video_not_received));
            }
        }
    }

    public void onGalleryResult(List<Uri> uris) {
        if (listener == null || uris == null) return;
        ContentResolver resolver = getContentResolver();
        if (resolver == null) return;
        for (Uri uri : uris) {
            if (uri == null) continue;
            try {
                String mime = resolver.getType(uri);
                listener.onMediaSelected(uri, mime);
            } catch (Exception e) {
                Log.e(TAG, "onGalleryResult: failed for uri=" + uri, e);
            }
        }
    }

    public void onDocumentsResult(List<Uri> uris) {
        onGalleryResult(uris);
    }

    public void onCameraPermissionResult(boolean granted) {
        Activity activity = activityRef.get();
        PendingAction action = pendingAction;
        pendingAction = PendingAction.NONE;

        if (!granted) {
            if (listener != null && activity != null) {
                listener.onError(activity.getString(R.string.error_camera_permission));
            }
            return;
        }
        if (action == PendingAction.PHOTO) doTakePhoto();
        else if (action == PendingAction.VIDEO) doTakeVideo();
    }

    private boolean hasCameraPermission() {
        Activity activity = activityRef.get();
        if (activity == null) return false;
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermissionFor(PendingAction action) {
        Activity activity = activityRef.get();
        pendingAction = action;
        if (cameraPermissionLauncher == null) {
            if (listener != null && activity != null) {
                listener.onError(activity.getString(R.string.error_camera_unavailable));
            }
            return;
        }
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void takePhotoFromCamera() {
        if (!hasCameraPermission()) {
            requestCameraPermissionFor(PendingAction.PHOTO);
            return;
        }
        doTakePhoto();
    }

    private void doTakePhoto() {
        Activity activity = activityRef.get();
        if (activity == null || cameraPhotoLauncher == null) return;
        try {
            String ts = timestampSdf.get().format(new Date());
            tempPhotoFile = new File(mediaDir, prefix + "CAM_" + ts + ".jpg");
            if (!tempPhotoFile.getParentFile().exists()) tempPhotoFile.getParentFile().mkdirs();
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", tempPhotoFile);
            cameraPhotoLauncher.launch(uri);
        } catch (Exception e) {
            Log.e(TAG, "doTakePhoto error", e);
            if (listener != null) listener.onError(activity.getString(R.string.error_camera));
        }
    }

    private void takeVideoFromCamera() {
        if (!hasCameraPermission()) {
            requestCameraPermissionFor(PendingAction.VIDEO);
            return;
        }
        doTakeVideo();
    }

    private void doTakeVideo() {
        Activity activity = activityRef.get();
        if (activity == null || cameraVideoLauncher == null) return;
        try {
            String ts = timestampSdf.get().format(new Date());
            tempVideoFile = new File(mediaDir, prefix + "VIDEO_" + ts + ".mp4");
            if (!tempVideoFile.getParentFile().exists()) tempVideoFile.getParentFile().mkdirs();
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", tempVideoFile);
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraVideoLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "doTakeVideo error", e);
            if (listener != null) listener.onError(activity.getString(R.string.error_camera));
        }
    }

    private void chooseMixedFromGallery() {
        if (galleryLauncher == null) return;
        galleryLauncher.launch(null);
    }

    private void chooseDocument() {
        if (documentsLauncher == null) return;
        documentsLauncher.launch(new String[]{
                "application/pdf", "text/plain",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        });
    }

    private ContentResolver getContentResolver() {
        Activity activity = activityRef.get();
        if (activity == null) return null;
        return activity.getContentResolver();
    }

    public static void copyUriToFile(ContentResolver resolver, Uri uri, File dest) throws IOException {
        try (InputStream in = resolver.openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IOException("InputStream is null");
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
    }

    public static boolean isVideoFile(File file) {
        String n = file.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".3gp") || n.endsWith(".mov") || n.endsWith(".avi");
    }

    public static boolean isDocumentFile(File file) {
        if (file == null) return false;
        String n = file.getName().toLowerCase(Locale.ROOT);
        return !n.endsWith(".jpg") && !n.endsWith(".jpeg") && !n.endsWith(".png")
                && !n.endsWith(".gif") && !n.endsWith(".webp") && !isVideoFile(file);
    }

    public static SimpleDateFormat getTimestampSdf() {
        return timestampSdf.get();
    }
}