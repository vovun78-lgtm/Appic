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
import com.example.appic.database.models.MachineGroup;
import com.example.appic.utils.ThemeHelper;
import com.google.android.material.card.MaterialCardView;

import java.util.Collections;
import java.util.List;

/**
 * Адаптер главного экрана.
 * Три типа элементов:
 *   - MachineGroup    — заголовок-карточка группы ;
 *   - UngroupedHeader — виртуальный разделитель перед станциками без группы;
 *   - Machine         — карточка  (без группы).
 *
 * ВАЖНО: {@link #items} и {@code MainActivity.items} — одна и та же ссылка.
 * Это намеренно: drag-and-drop в MainActivity вызывает {@link #onItemMove},
 * который делает swap в этом списке, а {@code saveOrders()} в MainActivity
 * читает порядок из того же списка. Если сделать копию — D&D сломается.
 */
public class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_MACHINE_GROUP = 0;
    public static final int TYPE_HEADER = 1;
    public static final int TYPE_MACHINE = 2;

    /** Маркер-разделитель перед станциками без группы. */
    public static class UngroupedHeader {}

    private List<Object> items;
    private OnGroupClick groupClick;
    private OnGroupLongClick groupLongClick;
    private OnMachineClick machineClick;
    private OnMachineLongClick machineLongClick;
    private OnStartDragListener dragStartListener;

    public interface OnGroupClick {
        void onGroupClick(MachineGroup group);
    }

    public interface OnGroupLongClick {
        void onGroupLongClick(MachineGroup group);
    }

    public interface OnMachineClick {
        void onMachineClick(Machine machine);
    }

    public interface OnMachineLongClick {
        void onMachineLongClick(Machine machine);
    }

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public HomeAdapter(List<Object> items,
                       OnGroupClick groupClick,
                       OnGroupLongClick groupLongClick,
                       OnMachineClick machineClick,
                       OnMachineLongClick machineLongClick,
                       OnStartDragListener dragStartListener) {
        this.items = items;
        this.groupClick = groupClick;
        this.groupLongClick = groupLongClick;
        this.machineClick = machineClick;
        this.machineLongClick = machineLongClick;
        this.dragStartListener = dragStartListener;
    }

    @Override
    public int getItemViewType(int position) {
        Object obj = items.get(position);
        if (obj instanceof MachineGroup) return TYPE_MACHINE_GROUP;
        if (obj instanceof UngroupedHeader) return TYPE_HEADER;
        return TYPE_MACHINE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_MACHINE_GROUP) {
            View v = inflater.inflate(R.layout.item_machine_group, parent, false);
            return new GroupViewHolder(v);
        } else if (viewType == TYPE_HEADER) {
            View v = inflater.inflate(R.layout.item_section_divider, parent, false);
            return new DividerViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_machine, parent, false);
            return new MachineViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object obj = items.get(position);

        if (holder instanceof GroupViewHolder && obj instanceof MachineGroup) {
            MachineGroup group = (MachineGroup) obj;
            GroupViewHolder gh = (GroupViewHolder) holder;
            gh.tvName.setText(group.getName());
            ThemeHelper.applyToTextViewPrimary(gh.tvName);
            if (gh.itemView instanceof MaterialCardView) {
                ThemeHelper.applyToMaterialCardView((MaterialCardView) gh.itemView, true);
            }
            if (gh.cardContent != null) ThemeHelper.applyCardPadding(gh.cardContent);

            gh.itemView.setOnClickListener(v -> {
                if (groupClick != null) groupClick.onGroupClick(group);
            });
            gh.itemView.setOnLongClickListener(v -> {
                if (groupLongClick != null) groupLongClick.onGroupLongClick(group);
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
            // Разделитель — только визуальный элемент, ничего не делаем

        } else if (holder instanceof MachineViewHolder && obj instanceof Machine) {
            Machine machine = (Machine) obj;
            MachineViewHolder mh = (MachineViewHolder) holder;
            mh.tvName.setText(machine.getName());
            ThemeHelper.applyToTextViewPrimary(mh.tvName);
            if (mh.itemView instanceof MaterialCardView) {
                ThemeHelper.applyToMaterialCardView((MaterialCardView) mh.itemView, false);
            }
            if (mh.cardContent != null) ThemeHelper.applyCardPadding(mh.cardContent);

            if (mh.ivUnread != null) {
                mh.ivUnread.setVisibility(machine.hasUnread() ? View.VISIBLE : View.GONE);
            }

            mh.itemView.setOnClickListener(v -> {
                if (machineClick != null) machineClick.onMachineClick(machine);
            });
            mh.itemView.setOnLongClickListener(v -> {
                if (machineLongClick != null) machineLongClick.onMachineLongClick(machine);
                return true;
            });

            if (mh.dragHandle != null) {
                mh.dragHandle.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN && dragStartListener != null) {
                        dragStartListener.onStartDrag(mh);
                    }
                    return false;
                });
            }
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

    public static class GroupViewHolder extends RecyclerView.ViewHolder {
        public TextView tvName;
        public ImageView dragHandle;
        public LinearLayout cardContent;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_group_name);
            dragHandle = itemView.findViewById(R.id.iv_drag_handle);
            if (itemView instanceof MaterialCardView) {
                ViewGroup vg = (ViewGroup) itemView;
                if (vg.getChildCount() > 0 && vg.getChildAt(0) instanceof LinearLayout) {
                    cardContent = (LinearLayout) vg.getChildAt(0);
                }
            }
        }
    }

    public static class DividerViewHolder extends RecyclerView.ViewHolder {
        public DividerViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    public static class MachineViewHolder extends RecyclerView.ViewHolder {
        public TextView tvName;
        public ImageView dragHandle;
        public ImageView ivUnread;
        public LinearLayout cardContent;

        public MachineViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_machine_name);
            dragHandle = itemView.findViewById(R.id.iv_drag_handle);
            ivUnread = itemView.findViewById(R.id.iv_unread_indicator);
            if (itemView instanceof MaterialCardView) {
                ViewGroup vg = (ViewGroup) itemView;
                if (vg.getChildCount() > 0 && vg.getChildAt(0) instanceof LinearLayout) {
                    cardContent = (LinearLayout) vg.getChildAt(0);
                }
            }
        }
    }
}