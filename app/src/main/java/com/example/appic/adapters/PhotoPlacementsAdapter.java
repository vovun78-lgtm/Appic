package com.example.appic.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.appic.R;
import com.example.appic.utils.PostRenderer;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.File;
import java.util.List;

public class PhotoPlacementsAdapter extends RecyclerView.Adapter<PhotoPlacementsAdapter.VH> {

    public interface OnPlacementChanged {
        void onChanged();
    }

    private static final String[] POSITION_LABELS = {
            "СЛ", "СЦ", "СП",
            "ЦЛ", "ЦЦ", "ЦП",
            "НЛ", "НЦ", "НП"
    };

    private static final String[] SIZE_LABELS = {
            "1×1", "2×1", "3×1", "1×2", "2×2", "3×2", "1×3", "2×3", "3×3"
    };

    private static final int[][] SIZE_COLS = {
            {1, 1}, {2, 1}, {3, 1}, {1, 2}, {2, 2}, {3, 2}, {1, 3}, {2, 3}, {3, 3}
    };

    private final List<PostRenderer.PhotoPlacement> placements;
    private final OnPlacementChanged listener;

    public PhotoPlacementsAdapter(List<PostRenderer.PhotoPlacement> placements,
                                  OnPlacementChanged listener) {
        this.placements = placements;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo_placement, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PostRenderer.PhotoPlacement p = placements.get(position);

        h.tvIndex.setText(String.valueOf(position + 1));

        Glide.with(h.image.getContext())
                .load(new File(p.path))
                .centerCrop()
                .into(h.image);

        int posIdx = (p.gridRow * 3) + p.gridCol;
        if (posIdx < 0 || posIdx >= POSITION_LABELS.length) posIdx = 0;
        h.spinnerPosition.setText(POSITION_LABELS[posIdx], false);

        int sizeIdx = findSizeIndex(p.gridColSpan, p.gridRowSpan);
        h.spinnerSize.setText(SIZE_LABELS[sizeIdx], false);

        h.sliderZoom.setValue(Math.max(PostRenderer.MIN_ZOOM,
                Math.min(PostRenderer.MAX_ZOOM, p.zoom)));
        h.tvZoomValue.setText(String.format(java.util.Locale.US, "%.1f×", p.zoom));

        h.spinnerPosition.setOnItemClickListener((parent, view, pos, id) -> {
            p.gridCol = pos % 3;
            p.gridRow = pos / 3;
            if (p.gridCol + p.gridColSpan > 3) p.gridColSpan = 3 - p.gridCol;
            if (p.gridRow + p.gridRowSpan > 3) p.gridRowSpan = 3 - p.gridRow;
            if (listener != null) listener.onChanged();
        });

        h.spinnerSize.setOnItemClickListener((parent, view, pos, id) -> {
            p.gridColSpan = SIZE_COLS[pos][0];
            p.gridRowSpan = SIZE_COLS[pos][1];
            if (p.gridCol + p.gridColSpan > 3) p.gridCol = Math.max(0, 3 - p.gridColSpan);
            if (p.gridRow + p.gridRowSpan > 3) p.gridRow = Math.max(0, 3 - p.gridRowSpan);
            if (listener != null) listener.onChanged();
        });

        h.sliderZoom.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                p.zoom = value;
                h.tvZoomValue.setText(String.format(java.util.Locale.US, "%.1f×", value));
                if (listener != null) listener.onChanged();
            }
        });
    }

    private int findSizeIndex(int colSpan, int rowSpan) {
        for (int i = 0; i < SIZE_COLS.length; i++) {
            if (SIZE_COLS[i][0] == colSpan && SIZE_COLS[i][1] == rowSpan) return i;
        }
        return 0;
    }

    @Override
    public int getItemCount() {
        return placements.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView tvIndex;
        TextView tvZoomValue;
        MaterialAutoCompleteTextView spinnerPosition;
        MaterialAutoCompleteTextView spinnerSize;
        Slider sliderZoom;

        VH(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.placement_image);
            tvIndex = v.findViewById(R.id.placement_index);
            tvZoomValue = v.findViewById(R.id.placement_zoom_value);
            spinnerPosition = v.findViewById(R.id.placement_position);
            spinnerSize = v.findViewById(R.id.placement_size);
            sliderZoom = v.findViewById(R.id.placement_zoom);
        }
    }
}