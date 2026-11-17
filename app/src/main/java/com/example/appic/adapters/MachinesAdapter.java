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
import com.example.appic.database.models.Machine;
import com.example.appic.utils.ThemeHelper;
import com.google.android.material.card.MaterialCardView;

import java.util.Collections;
import java.util.List;

public class MachinesAdapter extends RecyclerView.Adapter<MachinesAdapter.ViewHolder> {
    private List<Machine> machines;
    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;
    private OnStartDragListener dragStartListener;

    public interface OnItemClickListener {
        void onItemClick(Machine machine);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(Machine machine, int position);
    }

    public interface OnStartDragListener {
        void onStartDrag(ViewHolder viewHolder);
    }

    public MachinesAdapter(List<Machine> machines,
                           OnItemClickListener clickListener,
                           OnItemLongClickListener longClickListener,
                           OnStartDragListener dragStartListener) {
        this.machines = machines;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        this.dragStartListener = dragStartListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_machine, parent, false);
        ViewHolder holder = new ViewHolder(v);
        if (holder.tvName != null) {
            holder.tvName.setTag("primary");
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Machine machine = machines.get(position);
        holder.tvName.setText(machine.getName());

        ThemeHelper.applyToTextViewPrimary(holder.tvName);
        if (holder.itemView instanceof MaterialCardView) {
            ThemeHelper.applyToMaterialCardView((MaterialCardView) holder.itemView, false);
        }
        if (holder.cardContent != null) {
            ThemeHelper.applyCardPadding(holder.cardContent);
        }

        if (holder.ivUnread != null) {
            holder.ivUnread.setVisibility(machine.hasUnread() ? View.VISIBLE : View.GONE);
        }

        holder.itemView.setOnClickListener(v -> clickListener.onItemClick(machine));
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(machine, position);
                return true;
            }
            return false;
        });

        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && dragStartListener != null) {
                dragStartListener.onStartDrag(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return machines.size();
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(machines, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(machines, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        ImageView dragHandle;
        ImageView ivUnread;
        LinearLayout cardContent;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_machine_name);
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