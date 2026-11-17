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
import com.example.appic.database.models.Group;
import com.example.appic.utils.ThemeHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MachineDetailsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_GROUP = 0;
    public static final int TYPE_FIELD = 1;
    public static final int TYPE_DIVIDER = 2;

    public static class SectionDivider {}

    private List<Object> items;
    private List<FieldTemplate> templates;
    private OnGroupClickListener groupClickListener;
    private OnFieldClickListener fieldClickListener;
    private OnFieldLongClickListener fieldLongClickListener;
    private OnStartDragListener dragStartListener;

    public interface OnGroupClickListener {
        void onGroupClick(Group group);
        void onGroupLongClick(Group group);
    }

    public interface OnFieldClickListener {
        void onFieldClick(Field field);
    }

    public interface OnFieldLongClickListener {
        boolean onFieldLongClick(Field field);
    }

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public MachineDetailsAdapter(List<Object> items, List<FieldTemplate> templates,
                                 OnGroupClickListener groupClickListener,
                                 OnFieldClickListener fieldClickListener,
                                 OnFieldLongClickListener fieldLongClickListener,
                                 OnStartDragListener dragStartListener) {
        this.items = items;
        this.templates = templates;
        this.groupClickListener = groupClickListener;
        this.fieldClickListener = fieldClickListener;
        this.fieldLongClickListener = fieldLongClickListener;
        this.dragStartListener = dragStartListener;
    }

    @Override
    public int getItemViewType(int position) {
        Object obj = items.get(position);
        if (obj instanceof Group) return TYPE_GROUP;
        if (obj instanceof SectionDivider) return TYPE_DIVIDER;
        return TYPE_FIELD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_GROUP) {
            View v = inflater.inflate(R.layout.item_group, parent, false);
            GroupViewHolder holder = new GroupViewHolder(v);
            if (holder.tvGroupName != null) holder.tvGroupName.setTag("primary");
            return holder;
        } else if (viewType == TYPE_DIVIDER) {
            View v = inflater.inflate(R.layout.item_section_divider, parent, false);
            return new DividerViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_field, parent, false);
            FieldViewHolder holder = new FieldViewHolder(v);
            if (holder.tvTitle != null) holder.tvTitle.setTag("primary");
            if (holder.tvText != null) holder.tvText.setTag("secondary");
            return holder;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof GroupViewHolder) {
            Group group = (Group) items.get(position);
            GroupViewHolder gh = (GroupViewHolder) holder;
            gh.tvGroupName.setText(group.getName());

            ThemeHelper.applyToTextViewPrimary(gh.tvGroupName);
            if (gh.itemView instanceof MaterialCardView) {
                ThemeHelper.applyToMaterialCardView((MaterialCardView) gh.itemView, true);
            }
            if (gh.cardContent != null) ThemeHelper.applyCardPadding(gh.cardContent);

            if (gh.ivUnread != null) {
                gh.ivUnread.setVisibility(group.hasUnread() ? View.VISIBLE : View.GONE);
            }

            gh.itemView.setOnClickListener(v -> groupClickListener.onGroupClick(group));
            gh.itemView.setOnLongClickListener(v -> {
                groupClickListener.onGroupLongClick(group);
                return true;
            });

            if (gh.dragHandle != null) {
                gh.dragHandle.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN && dragStartListener != null) {
                        dragStartListener.onStartDrag(gh);
                    }
                    return false;
                });
            }

        } else if (holder instanceof DividerViewHolder) {
            // Разделитель

        } else if (holder instanceof FieldViewHolder) {
            Field field = (Field) items.get(position);
            FieldViewHolder fh = (FieldViewHolder) holder;
            fh.tvTitle.setText(field.getTitle());
            fh.tvText.setText(field.getText());

            String preview = formatCustomValues(field.getCustomValues());
            if (preview != null && !preview.isEmpty()) {
                fh.tvCustomPreview.setVisibility(View.VISIBLE);
                fh.tvCustomPreview.setText(preview);
            } else {
                fh.tvCustomPreview.setVisibility(View.GONE);
            }

            ThemeHelper.applyToTextViewPrimary(fh.tvTitle);
            ThemeHelper.applyToTextViewSecondary(fh.tvText);
            if (fh.itemView instanceof MaterialCardView) {
                ThemeHelper.applyToMaterialCardView((MaterialCardView) fh.itemView, false);
            }
            if (fh.cardContent != null) ThemeHelper.applyCardPadding(fh.cardContent);

            if (fh.ivUnread != null) {
                fh.ivUnread.setVisibility(field.isRead() ? View.GONE : View.VISIBLE);
            }

            fh.itemView.setOnClickListener(v -> fieldClickListener.onFieldClick(field));
            fh.itemView.setOnLongClickListener(v -> fieldLongClickListener.onFieldLongClick(field));
            fh.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN && dragStartListener != null) {
                    dragStartListener.onStartDrag(holder);
                }
                return false;
            });
        }
    }

    private String formatCustomValues(String json) {
        if (json == null || json.trim().isEmpty() || json.equals("{}")) return null;
        try {
            Type mapType = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> values = new Gson().fromJson(json, mapType);
            if (values == null || values.isEmpty()) return null;

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (FieldTemplate template : templates) {
                String key = String.valueOf(template.getId());
                String value = values.get(key);
                if (value == null || value.trim().isEmpty() || value.equals("false")) continue;

                if (count > 0) sb.append(" · ");
                
                String displayValue = value;
                if ("RATING".equals(template.getType())) {
                    try {
                        float rating = Float.parseFloat(value);
                        StringBuilder stars = new StringBuilder();
                        for (int i = 1; i <= 5; i++) {
                            if (i <= rating) stars.append("★");
                            else stars.append("☆");
                        }
                        displayValue = stars.toString();
                    } catch (Exception ignored) {}
                } else if ("PROGRESS".equals(template.getType())) {
                    try {
                        int progress = (int) Float.parseFloat(value);
                        displayValue = progress + "%";
                    } catch (Exception ignored) {}
                } else if ("MULTISELECT".equals(template.getType())) {
                    displayValue = value;
                }
                
                sb.append(template.getName()).append(": ").append(displayValue);
                count++;
                if (count >= 2) break;
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(items, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(items, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    public void updateItems(List<Object> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public void updateTemplates(List<FieldTemplate> newTemplates) {
        this.templates = newTemplates;
        notifyDataSetChanged();
    }

    public static class GroupViewHolder extends RecyclerView.ViewHolder {
        public TextView tvGroupName;
        public ImageView ivUnread;
        public ImageView dragHandle;
        public LinearLayout cardContent;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            tvGroupName = itemView.findViewById(R.id.tv_group_name);
            ivUnread = itemView.findViewById(R.id.iv_unread_indicator);
            dragHandle = itemView.findViewById(R.id.iv_drag_handle);
            if (itemView instanceof MaterialCardView) {
                ViewGroup group = (ViewGroup) itemView;
                if (group.getChildCount() > 0 && group.getChildAt(0) instanceof LinearLayout) {
                    cardContent = (LinearLayout) group.getChildAt(0);
                }
            }
        }
    }

    public static class FieldViewHolder extends RecyclerView.ViewHolder {
        public TextView tvTitle, tvText, tvCustomPreview;
        public ImageView dragHandle;
        public ImageView ivUnread;
        public LinearLayout cardContent;

        public FieldViewHolder(@NonNull View itemView) {
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

    public static class DividerViewHolder extends RecyclerView.ViewHolder {
        public DividerViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}