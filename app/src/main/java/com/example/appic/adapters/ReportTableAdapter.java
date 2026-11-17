package com.example.appic.adapters;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.utils.statistics.StatisticsResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReportTableAdapter extends RecyclerView.Adapter<ReportTableAdapter.VH> {

    private StatisticsResult result;
    private final List<String> header = new ArrayList<>();

    public void setResult(StatisticsResult r) {
        this.result = r;
        header.clear();
        if (r != null && r.columnLabels != null) header.addAll(r.columnLabels);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return new VH(row);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        LinearLayout row = (LinearLayout) h.itemView;
        row.removeAllViews();

        if (result == null) return;
        StatisticsResult r = result;
        StatisticsRowData data = new StatisticsRowData(r, position);

        int columns = Math.max(1, r.columnLabels.size());
        int dp = (int) (row.getResources().getDisplayMetrics().density * 8);
        int dpEnd = (int) (row.getResources().getDisplayMetrics().density * 4);

        // Для каждой колонки
        for (int col = 0; col < columns; col++) {
            TextView tv = new TextView(row.getContext());
            LinearLayout.LayoutParams lp;
            if (col == 0) {
                lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f);
            } else {
                lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            }
            lp.setMargins(dpEnd, dp, dpEnd, dp);
            tv.setLayoutParams(lp);
            tv.setGravity(col == 0 ? Gravity.START : Gravity.END);
            tv.setMaxLines(3);
            tv.setTextSize(13);

            if (col == 0) {
                tv.setText(data.label);
            } else {
                int idx = col - 1;
                double v = idx < data.values.size() ? data.values.get(idx) : 0;
                tv.setText(formatNumber(v));
            }
            row.addView(tv);
        }
    }

    @Override
    public int getItemCount() {
        return result != null && result.rows != null ? result.rows.size() : 0;
    }

    public List<String> getHeader() { return header; }

    private String formatNumber(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.getDefault(), "%.1f", v);
    }

    static class VH extends RecyclerView.ViewHolder {
        VH(@NonNull View itemView) { super(itemView); }
    }

    private static class StatisticsRowData {
        final String label;
        final List<Double> values;

        StatisticsRowData(StatisticsResult r, int position) {
            if (position < 0 || position >= r.rows.size()) {
                label = "";
                values = new ArrayList<>();
                return;
            }
            label = r.rows.get(position).label;
            values = r.rows.get(position).metrics;
        }
    }
}