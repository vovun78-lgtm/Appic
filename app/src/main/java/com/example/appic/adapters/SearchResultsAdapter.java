package com.example.appic.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.R;
import com.example.appic.utils.TermsHelper;
import com.example.appic.utils.ThemeHelper;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.ViewHolder> {

    public static final int TYPE_MACHINE = 1;
    public static final int TYPE_GROUP = 2;
    public static final int TYPE_FIELD = 3;

    public static class SearchResult {
        public int type;
        public int machineId;
        public int groupId;
        public int fieldId;
        public String title;
        public String subtitle;

        public SearchResult(int type, int machineId, int groupId, int fieldId, String title, String subtitle) {
            this.type = type;
            this.machineId = machineId;
            this.groupId = groupId;
            this.fieldId = fieldId;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private List<SearchResult> results;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(SearchResult result);
    }

    public SearchResultsAdapter(List<SearchResult> results, OnItemClickListener listener) {
        this.results = results;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SearchResult result = results.get(position);
        Context ctx = holder.itemView.getContext();
        String typeStr;
        if (result.type == TYPE_MACHINE) {
            typeStr = TermsHelper.getMachineSingle(ctx);
        } else if (result.type == TYPE_GROUP) {
            // ИСПРАВЛЕНО (п.7): вместо хардкода "Группа" — строка из ресурсов
            typeStr = ctx.getString(R.string.type_group);
        } else {
            typeStr = TermsHelper.getFieldSingle(ctx);
        }
        holder.tvType.setText(typeStr);
        holder.tvTitle.setText(result.title);
        if (result.subtitle != null && !result.subtitle.isEmpty()) {
            holder.tvSubtitle.setVisibility(View.VISIBLE);
            holder.tvSubtitle.setText(result.subtitle);
        } else {
            holder.tvSubtitle.setVisibility(View.GONE);
        }
        holder.itemView.setOnClickListener(v -> listener.onItemClick(result));

        if (holder.itemView instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) holder.itemView;
            ThemeHelper.applyToMaterialCardView(card, false);
        }
        if (holder.cardContent != null) {
            ThemeHelper.applyCardPadding(holder.cardContent);
        }
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    public void updateResults(List<SearchResult> newResults) {
        this.results = newResults;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvType, tvTitle, tvSubtitle;
        LinearLayout cardContent;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tv_type);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
            if (itemView instanceof MaterialCardView && itemView instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) itemView;
                if (group.getChildCount() > 0 && group.getChildAt(0) instanceof LinearLayout) {
                    cardContent = (LinearLayout) group.getChildAt(0);
                }
            }
        }
    }
}