package com.example.appic.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.appic.R;

import java.io.File;
import java.util.List;
import java.util.Set;

public class ArticlePhotosAdapter extends RecyclerView.Adapter<ArticlePhotosAdapter.VH> {

    public interface OnSelectionChanged {
        void onChanged(int count);
    }

    private final List<String> photos;
    private final Set<String> selected;
    private final OnSelectionChanged listener;

    public ArticlePhotosAdapter(List<String> photos, Set<String> selected,
                                OnSelectionChanged listener) {
        this.photos = photos;
        this.selected = selected;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_article_photo, parent, false);
        int size = parent.getResources().getDisplayMetrics().widthPixels / 3;
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(size, size);
        v.setLayoutParams(lp);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        String path = photos.get(position);
        boolean isSelected = selected.contains(path);

        Glide.with(h.image.getContext())
                .load(new File(path))
                .centerCrop()
                .into(h.image);

        h.checkIcon.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        h.image.setAlpha(isSelected ? 1.0f : 0.45f);

        h.itemView.setOnClickListener(v -> {
            if (selected.contains(path)) {
                selected.remove(path);
            } else {
                selected.add(path);
            }
            int pos = h.getAbsoluteAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
            if (listener != null) listener.onChanged(selected.size());
        });
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView image;
        ImageView checkIcon;

        VH(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.photo_image);
            checkIcon = v.findViewById(R.id.check_icon);
        }
    }
}