package com.example.appic.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.appic.R;
import com.example.appic.utils.ThemeHelper;

import java.util.List;

/**
 * ArrayAdapter для MaterialAutoCompleteTextView / выпадающих списков,
 * который явно применяет цвет текста из темы (?attr/textPrimaryColor)
 * к выпадающим элементам. Иначе на тёмной теме / ярких цветовых схемах
 * стандартный android.R.layout.simple_list_item_1 может дать невидимый текст.
 */
public class DropdownAdapter extends ArrayAdapter<String> {

    private final Context context;

    public DropdownAdapter(Context context, List<String> items) {
        super(context, android.R.layout.simple_list_item_1, items);
        this.context = context;
    }

    public DropdownAdapter(Context context, String[] items) {
        super(context, android.R.layout.simple_list_item_1, items);
        this.context = context;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        applyTheme(view);
        return view;
    }

    @NonNull
    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = super.getDropDownView(position, convertView, parent);
        applyTheme(view);
        return view;
    }

    private void applyTheme(View view) {
        if (view instanceof TextView) {
            int color = ThemeHelper.getThemeColor(context, R.attr.textPrimaryColor);
            if (color != 0) {
                ((TextView) view).setTextColor(color);
            }
        }
    }
}