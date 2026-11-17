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

public class TextSizeSeekBarPreference extends Preference implements SeekBar.OnSeekBarChangeListener {

    private int minValue = 8, maxValue = 30, currentValue = 14;
    private TextView previewTextView;
    private SeekBar seekBar;
    private boolean isUpdating = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public TextSizeSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public TextSizeSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setLayoutResource(R.layout.preference_textsize_seekbar);
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.SeekBarPreference);
            try {
                minValue = a.getInt(R.styleable.SeekBarPreference_minValue, 8);
                maxValue = a.getInt(R.styleable.SeekBarPreference_maxValue, 30);
            } finally {
                a.recycle();
            }
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        seekBar = (SeekBar) holder.findViewById(R.id.textsize_seekbar);
        previewTextView = (TextView) holder.findViewById(R.id.preview_text);

        if (seekBar != null) {
            seekBar.setMax(maxValue - minValue);
            seekBar.setProgress(currentValue - minValue);
            seekBar.setOnSeekBarChangeListener(this);
        }
        updateDisplaySilent(currentValue);
        // ВАЖНО: не вызываем здесь setSummary() — onBindViewHolder выполняется
        // внутри layout-прохода RecyclerView, а setSummary → notifyChanged →
        // notifyItemChanged → IllegalStateException.
        // Summary обновляется в onSetInitialValue и onProgressChanged.
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        String saved = null;
        try {
            saved = getPersistedString(null);
        } catch (ClassCastException e) {
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
        // Отложенный setSummary через handler.post, чтобы гарантированно
        // не попасть в layout-проход RecyclerView.
        final String summaryText = currentValue + " sp";
        handler.post(() -> {
            if (!summaryText.equals(getSummary())) {
                setSummary(summaryText);
            }
        });
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (isUpdating) return;
        currentValue = minValue + progress;
        updateDisplaySilent(currentValue);
        if (fromUser) {
            persistString(String.valueOf(currentValue));
            final String summaryText = currentValue + " sp";
            handler.post(() -> {
                isUpdating = true;
                if (!summaryText.equals(getSummary())) {
                    setSummary(summaryText);
                }
                isUpdating = false;
            });
            callChangeListener(currentValue);
        }
    }

    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    @Override public void onStopTrackingTouch(SeekBar seekBar) {}

    private void updateDisplaySilent(int value) {
        if (previewTextView != null) {
            previewTextView.setTextSize(value);
            previewTextView.setText("Aa");
        }
    }

    public void setCurrentValue(int value) {
        currentValue = value;
        persistString(String.valueOf(value));
        final String summaryText = value + " sp";
        handler.post(() -> {
            if (!summaryText.equals(getSummary())) {
                setSummary(summaryText);
            }
        });
    }
}