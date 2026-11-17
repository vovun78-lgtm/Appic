package com.example.appic.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.R;
import com.example.appic.utils.statistics.BuiltInReports;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class StatisticsCardAdapter extends RecyclerView.Adapter<StatisticsCardAdapter.VH> {

    public interface OnCardClick { void onClick(BuiltInReports.BuiltIn builtIn); }

    private final List<BuiltInReports.BuiltIn> items;
    private final OnCardClick listener;

    public StatisticsCardAdapter(List<BuiltInReports.BuiltIn> items, OnCardClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_statistics_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        BuiltInReports.BuiltIn b = items.get(position);
        h.tvTitle.setText(b.title);
        h.tvDesc.setText(b.description);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(b);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDesc;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_card_title);
            tvDesc = itemView.findViewById(R.id.tv_card_desc);
        }
    }
}