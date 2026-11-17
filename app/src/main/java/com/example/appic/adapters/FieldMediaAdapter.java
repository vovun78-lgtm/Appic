package com.example.appic.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.appic.R;
import com.example.appic.database.models.Media;

import java.io.File;
import java.util.List;

public class FieldMediaAdapter extends RecyclerView.Adapter<FieldMediaAdapter.ViewHolder> {

    private static final String KEY_PREVIEW_SIZE = "media_preview_size";
    private static final int DEFAULT_SIZE_DP = 100;

    private List<Media> mediaList;
    private OnMediaClickListener listener;

    public interface OnMediaClickListener {
        void onMediaClick(Media media, int position);
        void onDeleteClick(Media media, int position);
    }

    public FieldMediaAdapter(List<Media> mediaList, OnMediaClickListener listener) {
        this.mediaList = mediaList;
        this.listener = listener;
    }

    private int getPreviewSizeDp(Context ctx) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
            String raw = prefs.getString(KEY_PREVIEW_SIZE, String.valueOf(DEFAULT_SIZE_DP));
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return DEFAULT_SIZE_DP;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media_grid, parent, false);
        // ИСПРАВЛЕНО: используем настройку media_preview_size вместо захардкоженного 100dp
        int sizeDp = getPreviewSizeDp(parent.getContext());
        int size = (int) (parent.getContext().getResources().getDisplayMetrics().density * sizeDp);
        int m = (int) (parent.getContext().getResources().getDisplayMetrics().density * 4);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.setMargins(m, m, m, m);
        view.setLayoutParams(lp);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Media media = mediaList.get(position);
        boolean isVideo = "video".equals(media.getType());
        File file = new File(media.getPath());

        holder.videoIcon.setVisibility(isVideo ? View.VISIBLE : View.GONE);

        if (file.exists()) {
            if (isVideo) {
                Glide.with(holder.imageView.getContext())
                        .asBitmap()
                        .load(file)
                        .apply(new RequestOptions()
                                .centerCrop()
                                .placeholder(android.R.drawable.ic_menu_camera)
                                .error(android.R.drawable.ic_media_play))
                        .into(holder.imageView);
            } else {
                Glide.with(holder.imageView.getContext())
                        .load(file)
                        .apply(new RequestOptions()
                                .centerCrop()
                                .placeholder(R.drawable.ic_launcher)
                                .error(android.R.drawable.ic_menu_report_image))
                        .into(holder.imageView);
            }
        } else {
            holder.imageView.setImageResource(android.R.drawable.ic_menu_report_image);
            holder.videoIcon.setVisibility(View.GONE);
        }

        holder.imageView.setOnClickListener(v -> {
            if (listener != null) listener.onMediaClick(media, position);
        });
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(media, position);
        });
        holder.dragHandle.setVisibility(View.GONE);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        Glide.with(holder.imageView.getContext()).clear(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return mediaList.size();
    }

    public void updateData(List<Media> newList) {
        this.mediaList = newList;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ImageView deleteButton;
        ImageView videoIcon;
        ImageView dragHandle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_thumbnail);
            deleteButton = itemView.findViewById(R.id.iv_delete);
            videoIcon = itemView.findViewById(R.id.iv_video_icon);
            dragHandle = itemView.findViewById(R.id.iv_drag_handle);
        }
    }
}