package com.example.appic.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.R;
import com.example.appic.database.models.ReportDefinition;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MyReportsAdapter extends RecyclerView.Adapter<MyReportsAdapter.VH> {

    public interface OnReportClick { void onClick(ReportDefinition def); }
    public interface OnReportLongClick { void onLongClick(ReportDefinition def); }

    private final List<ReportDefinition> items;
    private final OnReportClick click;
    private final OnReportLongClick longClick;

    public MyReportsAdapter(List<ReportDefinition> items,
                            OnReportClick click, OnReportLongClick longClick) {
        this.items = items;
        this.click = click;
        this.longClick = longClick;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_report, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ReportDefinition d = items.get(position);
        h.tvName.setText(d.getName());
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yy", Locale.getDefault());
        h.tvMeta.setText("Сохранён " + sdf.format(new Date(d.getUpdatedAt())));
        h.itemView.setOnClickListener(v -> {
            if (click != null) click.onClick(d);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (longClick != null) longClick.onLongClick(d);
            return true;
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvMeta;
        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_my_report_name);
            tvMeta = itemView.findViewById(R.id.tv_my_report_meta);
        }
    }
}