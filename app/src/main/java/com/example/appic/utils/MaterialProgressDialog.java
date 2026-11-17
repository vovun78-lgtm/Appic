package com.example.appic.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.appic.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MaterialProgressDialog {

    public static final int STYLE_SPINNER = 0;
    public static final int STYLE_HORIZONTAL = 1;

    private final Context context;
    private AlertDialog dialog;
    private ProgressBar progressBar;
    private TextView messageView;

    private String message;
    private int style = STYLE_SPINNER;
    private int max = 100;
    private int progress = 0;
    private boolean cancelable = false;

    public MaterialProgressDialog(Context context) { this.context = context; }

    public void setMessage(String m) {
        this.message = m;
        if (messageView != null) {
            messageView.setText(m);
            messageView.setVisibility(m == null || m.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }
    public void setCancelable(boolean c) {
        this.cancelable = c;
        if (dialog != null) dialog.setCancelable(c);
    }
    public void setProgressStyle(int s) { this.style = s; }
    public void setMax(int m) {
        this.max = m;
        if (progressBar != null) progressBar.setMax(m);
    }
    public void setProgress(int p) {
        this.progress = p;
        if (progressBar != null && style == STYLE_HORIZONTAL) progressBar.setProgress(p);
    }

    public void show() {
        if (dialog != null && dialog.isShowing()) return;
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null);
        messageView = view.findViewById(R.id.progress_message);
        progressBar = view.findViewById(R.id.progress_bar);

        if (style == STYLE_HORIZONTAL) {
            progressBar.setIndeterminate(false);
            progressBar.setMax(max);
            progressBar.setProgress(progress);
        } else {
            progressBar.setIndeterminate(true);
        }
        if (message != null && !message.isEmpty()) {
            messageView.setText(message);
            messageView.setVisibility(View.VISIBLE);
        } else {
            messageView.setVisibility(View.GONE);
        }

        dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(cancelable)
                .create();
        try { dialog.show(); } catch (Exception ignored) {}
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            try { dialog.dismiss(); } catch (Exception ignored) {}
        }
        dialog = null;
    }

    public boolean isShowing() { return dialog != null && dialog.isShowing(); }
}