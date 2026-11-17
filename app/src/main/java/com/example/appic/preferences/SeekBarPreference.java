package com.example.appic.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.example.appic.R;
import com.example.appic.utils.ThemeHelper;

public class SeekBarPreference extends Preference implements SeekBar.OnSeekBarChangeListener {

    private int minValue = 0, maxValue = 100, currentValue = 0;
    private TextView valueTextView;
    private SeekBar seekBar;
    private boolean isUpdating = false;

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public SeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setLayoutResource(R.layout.preference_seekbar);
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.SeekBarPreference);
            try {
                minValue = a.getInt(R.styleable.SeekBarPreference_minValue, 0);
                maxValue = a.getInt(R.styleable.SeekBarPreference_maxValue, 100);
            } finally {
                a.recycle();
            }
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        seekBar = (SeekBar) holder.findViewById(R.id.seekbar);
        valueTextView = (TextView) holder.findViewById(R.id.value_text);

        if (valueTextView != null) {
            valueTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,
                    ThemeHelper.getTextSizeBodySp(getContext()));
        }

        if (seekBar != null) {
            seekBar.setMax(maxValue - minValue);
            seekBar.setProgress(currentValue - minValue);
            seekBar.setOnSeekBarChangeListener(this);
        }
        updateValueDisplaySilent(currentValue);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        String saved = null;
        try {
            saved = getPersistedString(null);
        } catch (ClassCastException e) {
            // Значение сохранено как int в старой версии — миграция
            try {
                int oldValue = getPersistedInt(minValue);
                saved = String.valueOf(oldValue);
                persistString(saved);
            } catch (Exception ignored) {
                saved = null;
            }
        }
        if (saved != null) {
            try { currentValue = Integer.parseInt(saved); } catch (NumberFormatException ignored) {}
        } else if (defaultValue != null) {
            try { currentValue = Integer.parseInt(defaultValue.toString()); } catch (NumberFormatException ignored) {}
        } else {
            currentValue = minValue;
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (isUpdating) return;
        currentValue = minValue + progress;
        updateValueDisplaySilent(currentValue);
        if (fromUser) {
            persistString(String.valueOf(currentValue));
            new Handler(Looper.getMainLooper()).post(() -> {
                isUpdating = true;
                setSummary(currentValue + " dp");
                isUpdating = false;
            });
            callChangeListener(currentValue);
        }
    }

    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    @Override public void onStopTrackingTouch(SeekBar seekBar) {}

    private void updateValueDisplaySilent(int value) {
        if (valueTextView != null) valueTextView.setText(value + " dp");
    }

    public void setCurrentValue(int value) {
        currentValue = value;
        persistString(String.valueOf(value));
    }
}