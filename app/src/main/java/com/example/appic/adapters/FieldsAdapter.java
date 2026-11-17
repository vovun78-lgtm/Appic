package com.example.appic.adapters;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.R;
import com.example.appic.database.models.Field;
import com.example.appic.database.models.FieldTemplate;
import com.example.appic.utils.CustomValuesFormatter;
import com.example.appic.utils.ThemeHelper;
import com.google.android.material.card.MaterialCardView;

import java.util.Collections;
import java.util.List;

public class FieldsAdapter extends RecyclerView.Adapter<FieldsAdapter.ViewHolder> {
    private List<Field> fields;
    private List<FieldTemplate> templates;
    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;
    private OnStartDragListener dragStartListener;

    public interface OnItemClickListener {
        void onItemClick(Field field);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(Field field);
    }

    public interface OnStartDragListener {
        void onStartDrag(ViewHolder viewHolder);
    }

    public FieldsAdapter(List<Field> fields,
                         List<FieldTemplate> templates,
                         OnItemClickListener clickListener,
                         OnItemLongClickListener longClickListener,
                         OnStartDragListener dragStartListener) {
        this.fields = fields;
        this.templates = templates;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        this.dragStartListener = dragStartListener;
    }

    /** Обновляет список шаблонов БЕЗ вызова notifyDataSetChanged —
     *  чтобы вызывающий код мог обновить и fields, и сделать один notify. */
    public void updateTemplates(List<FieldTemplate> newTemplates) {
        this.templates = newTemplates;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_field, parent, false);
        ViewHolder holder = new ViewHolder(v);
        if (holder.tvTitle != null) holder.tvTitle.setTag("primary");
        if (holder.tvText != null) holder.tvText.setTag("secondary");
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Field field = fields.get(position);
        holder.tvTitle.setText(field.getTitle());
        holder.tvText.setText(field.getText());

        String preview = CustomValuesFormatter.format(field.getCustomValues(), templates);
        if (preview != null && !preview.isEmpty()) {
            holder.tvCustomPreview.setVisibility(View.VISIBLE);
            holder.tvCustomPreview.setText(preview);
        } else {
            holder.tvCustomPreview.setVisibility(View.GONE);
        }

        ThemeHelper.applyToTextViewPrimary(holder.tvTitle);
        ThemeHelper.applyToTextViewSecondary(holder.tvText);
        if (holder.itemView instanceof MaterialCardView) {
            ThemeHelper.applyToMaterialCardView((MaterialCardView) holder.itemView, false);
        }
        if (holder.cardContent != null) {
            ThemeHelper.applyCardPadding(holder.cardContent);
        }

        if (holder.ivUnread != null) {
            holder.ivUnread.setVisibility(field.isRead() ? View.GONE : View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> clickListener.onItemClick(field));
        holder.itemView.setOnLongClickListener(v -> longClickListener.onItemLongClick(field));

        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && dragStartListener != null) {
                dragStartListener.onStartDrag(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return fields.size();
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(fields, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(fields, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvText, tvCustomPreview;
        ImageView dragHandle;
        ImageView ivUnread;
        LinearLayout cardContent;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_field_title);
            tvText = itemView.findViewById(R.id.tv_field_text);
            tvCustomPreview = itemView.findViewById(R.id.tv_custom_preview);
            dragHandle = itemView.findViewById(R.id.iv_drag_handle);
            ivUnread = itemView.findViewById(R.id.iv_unread_indicator);
            if (itemView instanceof MaterialCardView) {
                ViewGroup group = (ViewGroup) itemView;
                if (group.getChildCount() > 0 && group.getChildAt(0) instanceof LinearLayout) {
                    cardContent = (LinearLayout) group.getChildAt(0);
                }
            }
        }
    }
}