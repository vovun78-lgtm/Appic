package com.example.appic.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.appic.utils.statistics.StatisticsResult;
import com.example.appic.utils.statistics.ViewType;

import java.util.Locale;

public class SimpleChartView extends View {

    private StatisticsResult result;
    private ViewType viewType = ViewType.BAR;
    private int primaryColor = Color.parseColor("#2196F3");
    private int textColor = Color.DKGRAY;
    private int dividerColor = Color.LTGRAY;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SimpleChartView(Context context) { super(context); }
    public SimpleChartView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); }
    public SimpleChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setPrimaryColor(int c) { this.primaryColor = c; invalidate(); }
    public void setTextColor(int c) { this.textColor = c; invalidate(); }
    public void setDividerColor(int c) { this.dividerColor = c; invalidate(); }

    public void setViewType(ViewType type) { this.viewType = type; invalidate(); }

    public void setResult(StatisticsResult r) { this.result = r; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // ИСПРАВЛЕНО (п.4): защита от null — если config.viewType не был установлен
        // (пустой config_json при открытии отчёта), switch(null) ронял приложение.
        if (viewType == null) viewType = ViewType.BAR;

        if (result == null || result.rows == null || result.rows.isEmpty()) {
            drawEmpty(canvas);
            return;
        }
        switch (viewType) {
            case BAR: drawBars(canvas); break;
            case PIE: drawPie(canvas); break;
            case LINE: drawLine(canvas); break;
            default: drawEmpty(canvas); break;
        }
    }

    private void drawEmpty(Canvas c) {
        paint.setColor(textColor);
        paint.setTextSize(px(14));
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText("Нет данных", getWidth() / 2f, getHeight() / 2f, paint);
    }

    private void drawBars(Canvas c) {
        int n = Math.min(result.rows.size(), 15);
        if (n == 0) return;
        float w = getWidth();
        float h = getHeight();
        float labelH = px(50);
        float barAreaH = h - labelH - px(20);

        double max = 0;
        for (int i = 0; i < n; i++) {
            if (result.rows.get(i).metrics.isEmpty()) continue;
            max = Math.max(max, result.rows.get(i).metrics.get(0));
        }
        if (max <= 0) max = 1;

        float barW = (w - px(16)) / n;
        paint.setTextSize(px(10));
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            double v = result.rows.get(i).metrics.isEmpty() ? 0 : result.rows.get(i).metrics.get(0);
            float barH = (float) (barAreaH * (v / max));
            float left = px(8) + i * barW + px(4);
            float right = left + barW - px(8);
            float top = px(20) + (barAreaH - barH);
            float bottom = px(20) + barAreaH;

            paint.setColor(primaryColor);
            c.drawRect(left, top, right, bottom, paint);

            paint.setColor(textColor);
            c.drawText(formatNumber(v), (left + right) / 2, top - px(4), paint);

            String label = result.rows.get(i).label;
            if (label != null && label.length() > 10) label = label.substring(0, 9) + "…";
            c.drawText(label != null ? label : "",
                    (left + right) / 2, bottom + px(14), paint);
        }
    }

    private void drawPie(Canvas c) {
        int n = Math.min(result.rows.size(), 8);
        double total = 0;
        for (int i = 0; i < n; i++) {
            if (!result.rows.get(i).metrics.isEmpty()) {
                total += result.rows.get(i).metrics.get(0);
            }
        }
        if (total <= 0) { drawEmpty(c); return; }

        float cx = getWidth() / 2f - px(40);
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.4f;

        int[] colors = {
                primaryColor,
                Color.parseColor("#FF9800"),
                Color.parseColor("#4CAF50"),
                Color.parseColor("#E91E63"),
                Color.parseColor("#9C27B0"),
                Color.parseColor("#00BCD4"),
                Color.parseColor("#795548"),
                Color.parseColor("#607D8B")
        };

        float startAngle = -90f;
        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        for (int i = 0; i < n; i++) {
            double v = result.rows.get(i).metrics.isEmpty() ? 0 : result.rows.get(i).metrics.get(0);
            float sweep = (float) (v / total * 360f);
            paint.setColor(colors[i % colors.length]);
            c.drawArc(oval, startAngle, sweep, true, paint);
            startAngle += sweep;
        }

        paint.setTextSize(px(10));
        paint.setTextAlign(Paint.Align.LEFT);
        float ly = cy - (n * px(16)) / 2f;
        for (int i = 0; i < n; i++) {
            double v = result.rows.get(i).metrics.isEmpty() ? 0 : result.rows.get(i).metrics.get(0);
            double pct = total > 0 ? v / total * 100 : 0;
            paint.setColor(colors[i % colors.length]);
            c.drawRect(getWidth() * 0.7f, ly, getWidth() * 0.7f + px(10), ly + px(10), paint);
            paint.setColor(textColor);
            String label = result.rows.get(i).label;
            if (label != null && label.length() > 12) label = label.substring(0, 11) + "…";
            c.drawText((label != null ? label : "") + " · " +
                            String.format(Locale.getDefault(), "%.0f", pct) + "%",
                    getWidth() * 0.7f + px(14), ly + px(9), paint);
            ly += px(16);
        }
    }

    private void drawLine(Canvas c) {
        int n = result.rows.size();
        if (n == 0) return;
        float w = getWidth();
        float h = getHeight();
        float pad = px(20);
        float bottom = h - px(50);

        double max = 0;
        for (int i = 0; i < n; i++) {
            if (!result.rows.get(i).metrics.isEmpty()) {
                max = Math.max(max, result.rows.get(i).metrics.get(0));
            }
        }
        if (max <= 0) max = 1;

        float dx = n > 1 ? (w - pad * 2) / (n - 1) : 0;
        Path path = new Path();
        for (int i = 0; i < n; i++) {
            double v = result.rows.get(i).metrics.isEmpty() ? 0 : result.rows.get(i).metrics.get(0);
            float x = pad + i * dx;
            float y = bottom - (float) ((bottom - pad) * (v / max));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(px(2));
        paint.setColor(primaryColor);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextSize(px(9));
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            double v = result.rows.get(i).metrics.isEmpty() ? 0 : result.rows.get(i).metrics.get(0);
            float x = pad + i * dx;
            float y = bottom - (float) ((bottom - pad) * (v / max));
            paint.setColor(primaryColor);
            c.drawCircle(x, y, px(2), paint);
            if (i % 2 == 0) {
                paint.setColor(textColor);
                String label = result.rows.get(i).label;
                if (label != null && label.length() > 7) label = label.substring(0, 6) + "…";
                c.drawText(label != null ? label : "", x, bottom + px(14), paint);
            }
        }
    }

    private String formatNumber(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.getDefault(), "%.1f", v);
    }

    private float px(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}