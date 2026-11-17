package com.example.appic.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class PostRenderer {

    public static final float MIN_ZOOM = 0.3f;
    public static final float MAX_ZOOM = 3.0f;

    public enum Format {
        SQUARE(1080, 1080),
        STORY(1080, 1920),
        ARTICLE(1200, 630),
        PORTRAIT(1080, 1350);

        public final int width;
        public final int height;

        Format(int w, int h) {
            this.width = w;
            this.height = h;
        }
    }

    public enum Template {
        MINIMAL,
        FRAME,
        LOGO_CORNER,
        COLLAGE_2,
        COLLAGE_4,
        ARTICLE_HORIZONTAL
    }

    public enum AccentColor {
        NEUTRAL(0xFF000000, 0xFFFFFFFF),
        BLUE(0xFF1976D2, 0xFFFFFFFF),
        ORANGE(0xFFF57C00, 0xFFFFFFFF),
        GREEN(0xFF388E3C, 0xFFFFFFFF),
        PINK(0xFFC2185B, 0xFFFFFFFF),
        PURPLE(0xFF6A1B9A, 0xFFFFFFFF),
        TEAL(0xFF00796B, 0xFFFFFFFF),
        RED(0xFFC62828, 0xFFFFFFFF);

        public final int bgColor;
        public final int textColor;

        AccentColor(int bg, int text) {
            this.bgColor = bg;
            this.textColor = text;
        }
    }

    public enum TextPosition {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    public enum PhotoLayout {
        AUTO,
        SINGLE,
        CUSTOM
    }

    public static final class PhotoPlacement {
        public String path;
        public int gridCol = 0;
        public int gridRow = 0;
        public int gridColSpan = 1;
        public int gridRowSpan = 1;
        public float zoom = 1.0f;
        public float offsetX = 0.0f;
        public float offsetY = 0.0f;

        public PhotoPlacement() {}

        public PhotoPlacement(String path, int col, int row, int colSpan, int rowSpan) {
            this.path = path;
            this.gridCol = col;
            this.gridRow = row;
            this.gridColSpan = colSpan;
            this.gridRowSpan = rowSpan;
        }
    }

    public static final class Config {
        public Format format = Format.SQUARE;
        public Template template = Template.MINIMAL;
        public AccentColor accent = AccentColor.NEUTRAL;
        public TextPosition textPosition = TextPosition.BOTTOM_LEFT;
        public PhotoLayout photoLayout = PhotoLayout.AUTO;
        public String title = "";
        public String text = "";
        public String hashtags = "";
        public List<String> photoPaths = new ArrayList<>();
        public List<PhotoPlacement> placements = new ArrayList<>();
        public String watermark = "Appic";
        public boolean showWatermark = true;
        public float photoZoom = 1.0f;
        public float photoOffsetX = 0.0f;
        public float photoOffsetY = 0.0f;
    }

    private PostRenderer() {}

    public static Bitmap render(Context context, Config cfg, int targetWidth) {
        if (targetWidth <= 0) targetWidth = 1080;
        int w = targetWidth;
        int h = (int) ((float) w * cfg.format.height / cfg.format.width);
        if (h <= 0) h = 1;

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        switch (cfg.template) {
            case FRAME:
                drawFrame(canvas, cfg, w, h);
                break;
            case LOGO_CORNER:
                drawLogoCorner(canvas, cfg, w, h);
                break;
            case COLLAGE_2:
                drawCollage(canvas, cfg, w, h, 2);
                break;
            case COLLAGE_4:
                drawCollage(canvas, cfg, w, h, 4);
                break;
            case ARTICLE_HORIZONTAL:
                drawArticleHorizontal(canvas, cfg, w, h);
                break;
            case MINIMAL:
            default:
                drawMinimal(canvas, cfg, w, h);
                break;
        }

        if (cfg.showWatermark) {
            drawWatermark(canvas, cfg, w, h);
        }

        return bitmap;
    }

    public static List<PhotoPlacement> autoLayoutPhotos(List<String> paths, Format format) {
        List<PhotoPlacement> result = new ArrayList<>();
        if (paths == null || paths.isEmpty()) return result;

        int n = Math.min(paths.size(), 9);

        if (n == 1) {
            result.add(new PhotoPlacement(paths.get(0), 0, 0, 3, 3));
        } else if (n == 2) {
            boolean portrait = format.height >= format.width;
            if (portrait) {
                result.add(new PhotoPlacement(paths.get(0), 0, 0, 3, 2));
                result.add(new PhotoPlacement(paths.get(1), 0, 2, 3, 1));
            } else {
                result.add(new PhotoPlacement(paths.get(0), 0, 0, 2, 3));
                result.add(new PhotoPlacement(paths.get(1), 2, 0, 1, 3));
            }
        } else if (n == 3) {
            result.add(new PhotoPlacement(paths.get(0), 0, 0, 3, 2));
            result.add(new PhotoPlacement(paths.get(1), 0, 2, 2, 1));
            result.add(new PhotoPlacement(paths.get(2), 2, 2, 1, 1));
        } else if (n == 4) {
            result.add(new PhotoPlacement(paths.get(0), 0, 0, 2, 2));
            result.add(new PhotoPlacement(paths.get(1), 2, 0, 1, 2));
            result.add(new PhotoPlacement(paths.get(2), 0, 2, 2, 1));
            result.add(new PhotoPlacement(paths.get(3), 2, 2, 1, 1));
        } else if (n <= 6) {
            for (int i = 0; i < n; i++) {
                int col = i % 3;
                int row = i / 3;
                result.add(new PhotoPlacement(paths.get(i), col, row, 1, 1));
            }
        } else {
            for (int i = 0; i < n; i++) {
                int col = i % 3;
                int row = i / 3;
                result.add(new PhotoPlacement(paths.get(i), col, row, 1, 1));
            }
        }
        return result;
    }

    private static void drawPhotoArea(Canvas canvas, Config cfg, RectF area) {
        List<PhotoPlacement> placements;
        if (cfg.photoLayout == PhotoLayout.CUSTOM && !cfg.placements.isEmpty()) {
            placements = cfg.placements;
        } else if (cfg.photoLayout == PhotoLayout.SINGLE
                || cfg.photoPaths == null || cfg.photoPaths.size() <= 1) {
            if (cfg.photoPaths == null || cfg.photoPaths.isEmpty()) return;
            Bitmap bmp = loadBitmap(cfg.photoPaths.get(0), 2048);
            if (bmp != null) {
                drawCroppedBitmap(canvas, bmp, area,
                        cfg.photoZoom, cfg.photoOffsetX, cfg.photoOffsetY);
                bmp.recycle();
            }
            return;
        } else {
            placements = autoLayoutPhotos(cfg.photoPaths, cfg.format);
        }

        for (PhotoPlacement p : placements) {
            if (p.path == null || p.path.isEmpty()) continue;
            float x = area.left + p.gridCol / 3f * area.width();
            float y = area.top + p.gridRow / 3f * area.height();
            float pw = Math.max(1f, p.gridColSpan / 3f * area.width());
            float ph = Math.max(1f, p.gridRowSpan / 3f * area.height());
            Bitmap bmp = loadBitmap(p.path, 1600);
            if (bmp != null) {
                drawCroppedBitmap(canvas, bmp,
                        new RectF(x, y, x + pw, y + ph),
                        p.zoom, p.offsetX, p.offsetY);
                bmp.recycle();
            }
        }
    }

    private static void drawMinimal(Canvas canvas, Config cfg, int w, int h) {
        boolean hasPhoto = cfg.photoPaths != null && !cfg.photoPaths.isEmpty();
        boolean lightText;

        if (hasPhoto) {
            canvas.drawColor(Color.BLACK);
            drawPhotoArea(canvas, cfg, new RectF(0, 0, w, h));
            drawDirectionalGradient(canvas, w, h, cfg.textPosition);
            lightText = true;
        } else {
            canvas.drawColor(cfg.accent == AccentColor.NEUTRAL ? Color.WHITE : cfg.accent.bgColor);
            lightText = cfg.accent != AccentColor.NEUTRAL;
        }

        drawTextBlock(canvas, cfg,
                new RectF(w * 0.07f, h * 0.07f, w * 0.93f, h * 0.93f),
                lightText);
    }

    private static void drawFrame(Canvas canvas, Config cfg, int w, int h) {
        boolean hasPhoto = cfg.photoPaths != null && !cfg.photoPaths.isEmpty();
        int outerPad = (int) (w * 0.04f);
        int frameThickness = (int) (w * 0.02f);
        int accentColor = cfg.accent == AccentColor.NEUTRAL ? 0xFF212121 : cfg.accent.bgColor;

        canvas.drawColor(accentColor);

        RectF imageRect = new RectF(
                outerPad + frameThickness, outerPad + frameThickness,
                w - outerPad - frameThickness, h - outerPad - frameThickness);

        if (hasPhoto) {
            drawPhotoArea(canvas, cfg, imageRect);
        } else {
            Paint bg = new Paint();
            bg.setColor(Color.WHITE);
            canvas.drawRect(imageRect, bg);
        }

        drawDirectionalGradient(canvas, w, h, cfg.textPosition);

        drawTextBlock(canvas, cfg,
                new RectF(outerPad * 3, outerPad * 3, w - outerPad * 3, h - outerPad * 3),
                hasPhoto);
    }

    private static void drawLogoCorner(Canvas canvas, Config cfg, int w, int h) {
        boolean hasPhoto = cfg.photoPaths != null && !cfg.photoPaths.isEmpty();
        boolean lightText;

        if (hasPhoto) {
            canvas.drawColor(Color.BLACK);
            drawPhotoArea(canvas, cfg, new RectF(0, 0, w, h));
            drawDirectionalGradient(canvas, w, h, cfg.textPosition);
            lightText = true;
        } else {
            canvas.drawColor(cfg.accent == AccentColor.NEUTRAL ? Color.WHITE : cfg.accent.bgColor);
            lightText = cfg.accent != AccentColor.NEUTRAL;
        }

        drawTextBlock(canvas, cfg,
                new RectF(w * 0.07f, h * 0.07f, w * 0.93f, h * 0.93f),
                lightText);

        int logoSize = (int) (w * 0.14f);
        int margin = (int) (w * 0.04f);
        Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        circle.setColor(cfg.accent == AccentColor.NEUTRAL ? 0xCC000000 : cfg.accent.bgColor);
        canvas.drawCircle(w - margin - logoSize / 2f, margin + logoSize / 2f,
                logoSize / 2f, circle);

        TextPaint initials = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        initials.setColor(Color.WHITE);
        initials.setTextAlign(Paint.Align.CENTER);
        initials.setTextSize(logoSize * 0.4f);
        initials.setTypeface(Typeface.DEFAULT_BOLD);
        String letter = firstLetter(cfg.watermark);
        float cx = w - margin - logoSize / 2f;
        float cy = margin + logoSize / 2f - (initials.descent() + initials.ascent()) / 2f;
        canvas.drawText(letter, cx, cy, initials);
    }

    private static void drawCollage(Canvas canvas, Config cfg, int w, int h, int count) {
        canvas.drawColor(cfg.accent == AccentColor.NEUTRAL ? 0xFF000000 : cfg.accent.bgColor);

        // Защита от пустого списка фото — иначе cfg.photoPaths.get(0) бросит
        // IndexOutOfBoundsException, и приложение упадёт при выборе шаблона
        // COLLAGE_2 / COLLAGE_4 без единого фото.
        if (cfg.photoPaths == null || cfg.photoPaths.isEmpty()) {
            return;
        }

        List<PhotoPlacement> placements;
        if (cfg.photoLayout == PhotoLayout.CUSTOM && !cfg.placements.isEmpty()) {
            placements = cfg.placements;
        } else {
            placements = new ArrayList<>();
            int n = Math.min(count, cfg.photoPaths.size());
            if (count == 2) {
                placements.add(new PhotoPlacement(cfg.photoPaths.get(0), 0, 0, 2, 3));
                if (n > 1) placements.add(new PhotoPlacement(cfg.photoPaths.get(1), 2, 0, 1, 3));
            } else {
                int[][] cells = {{0, 0}, {2, 0}, {0, 2}, {2, 2}};
                int[][] spans = {{2, 2}, {1, 2}, {2, 1}, {1, 1}};
                for (int i = 0; i < Math.min(4, n); i++) {
                    placements.add(new PhotoPlacement(cfg.photoPaths.get(i),
                            cells[i][0], cells[i][1], spans[i][0], spans[i][1]));
                }
            }
        }

        for (PhotoPlacement p : placements) {
            if (p.path == null) continue;
            float x = p.gridCol / 3f * w;
            float y = p.gridRow / 3f * h;
            float pw = p.gridColSpan / 3f * w;
            float ph = p.gridRowSpan / 3f * h;
            Bitmap bmp = loadBitmap(p.path, 1600);
            if (bmp != null) {
                drawCroppedBitmap(canvas, bmp, new RectF(x, y, x + pw, y + ph),
                        p.zoom, p.offsetX, p.offsetY);
                bmp.recycle();
            }
        }

        drawDirectionalGradient(canvas, w, h, cfg.textPosition);
        drawTextBlock(canvas, cfg,
                new RectF(w * 0.06f, h * 0.06f, w * 0.94f, h * 0.94f),
                true);
    }

    private static void drawArticleHorizontal(Canvas canvas, Config cfg, int w, int h) {
        boolean hasPhoto = cfg.photoPaths != null && !cfg.photoPaths.isEmpty();
        int imageW = (int) (w * 0.42f);

        canvas.drawColor(Color.WHITE);

        RectF imageRect = new RectF(0, 0, imageW, h);
        if (hasPhoto) {
            drawPhotoArea(canvas, cfg, imageRect);
        } else {
            Paint bg = new Paint();
            bg.setColor(cfg.accent == AccentColor.NEUTRAL ? 0xFFEEEEEE : cfg.accent.bgColor);
            canvas.drawRect(imageRect, bg);
        }

        Paint accentBar = new Paint();
        accentBar.setColor(cfg.accent == AccentColor.NEUTRAL ? 0xFF212121 : cfg.accent.bgColor);
        int barW = (int) (w * 0.012f);
        canvas.drawRect(imageW, 0, imageW + barW, h, accentBar);

        RectF textRect = new RectF(
                imageW + barW + w * 0.06f,
                h * 0.08f,
                w - w * 0.06f,
                h * 0.92f);

        drawTextBlock(canvas, cfg, textRect, false);
    }

    private static void drawTextBlock(Canvas canvas, Config cfg, RectF bounds, boolean lightText) {
        String title = safeTitle(cfg);
        String body = safeBody(cfg);
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(body)) return;

        int w = (int) bounds.width();
        if (w <= 0) return;

        Layout.Alignment align = alignmentFor(cfg.textPosition);

        TextPaint tpTitle = makeTextPaint(cfg, w, lightText);
        tpTitle.setTextSize(w * 0.085f);
        tpTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tpTitle.setTextAlign(Paint.Align.LEFT);

        TextPaint tpBody = makeTextPaint(cfg, w, lightText);
        tpBody.setTextSize(w * 0.048f);
        tpBody.setTextAlign(Paint.Align.LEFT);

        StaticLayout titleLayout = null;
        StaticLayout bodyLayout = null;

        if (!TextUtils.isEmpty(title)) {
            titleLayout = StaticLayout.Builder
                    .obtain(title, 0, title.length(), tpTitle, w)
                    .setMaxLines(4)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setLineSpacing(0, 1.05f)
                    .setAlignment(align)
                    .build();
        }
        if (!TextUtils.isEmpty(body)) {
            bodyLayout = StaticLayout.Builder
                    .obtain(body, 0, body.length(), tpBody, w)
                    .setMaxLines(12)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setLineSpacing(0, 1.15f)
                    .setAlignment(align)
                    .build();
        }

        int gap = (int) (w * 0.04f);
        int totalH = 0;
        if (titleLayout != null) totalH += titleLayout.getHeight();
        if (titleLayout != null && bodyLayout != null) totalH += gap;
        if (bodyLayout != null) totalH += bodyLayout.getHeight();

        float startY;
        switch (cfg.textPosition) {
            case TOP_LEFT: case TOP_CENTER: case TOP_RIGHT:
                startY = bounds.top;
                break;
            case MIDDLE_LEFT: case MIDDLE_CENTER: case MIDDLE_RIGHT:
                startY = bounds.top + (bounds.height() - totalH) / 2f;
                break;
            default:
                startY = bounds.bottom - totalH;
                break;
        }

        float y = startY;
        if (titleLayout != null) {
            canvas.save();
            canvas.translate(bounds.left, y);
            titleLayout.draw(canvas);
            canvas.restore();
            y += titleLayout.getHeight();
            if (bodyLayout != null) y += gap;
        }
        if (bodyLayout != null) {
            canvas.save();
            canvas.translate(bounds.left, y);
            bodyLayout.draw(canvas);
            canvas.restore();
        }
    }

    private static float verticalFactor(TextPosition p) {
        switch (p) {
            case TOP_LEFT: case TOP_CENTER: case TOP_RIGHT: return 0f;
            case MIDDLE_LEFT: case MIDDLE_CENTER: case MIDDLE_RIGHT: return 0.5f;
            default: return 1f;
        }
    }

    private static Layout.Alignment alignmentFor(TextPosition p) {
        switch (p) {
            case TOP_LEFT: case MIDDLE_LEFT: case BOTTOM_LEFT:
                return Layout.Alignment.ALIGN_NORMAL;
            case TOP_CENTER: case MIDDLE_CENTER: case BOTTOM_CENTER:
                return Layout.Alignment.ALIGN_CENTER;
            default:
                return Layout.Alignment.ALIGN_OPPOSITE;
        }
    }

    private static TextPaint makeTextPaint(Config cfg, int canvasWidth, boolean lightText) {
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setSubpixelText(true);
        if (lightText) {
            tp.setColor(Color.WHITE);
        } else {
            if (cfg.accent == AccentColor.NEUTRAL) tp.setColor(Color.BLACK);
            else tp.setColor(cfg.accent.textColor);
        }
        return tp;
    }

    private static void drawDirectionalGradient(Canvas canvas, int w, int h, TextPosition p) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float vf = verticalFactor(p);

        int[] colors;
        float[] positions;

        if (vf <= 0f) {
            colors = new int[]{0xE6000000, 0xB3000000, 0x40000000, 0x00000000};
            positions = new float[]{0f, 0.25f, 0.5f, 0.75f};
        } else if (vf >= 1f) {
            colors = new int[]{0x00000000, 0x40000000, 0xB3000000, 0xE6000000};
            positions = new float[]{0.25f, 0.5f, 0.75f, 1f};
        } else {
            colors = new int[]{0x00000000, 0x66000000, 0x66000000, 0x00000000};
            positions = new float[]{0f, 0.3f, 0.7f, 1f};
        }

        LinearGradient lg = new LinearGradient(0, 0, 0, h, colors, positions,
                Shader.TileMode.CLAMP);
        paint.setShader(lg);
        canvas.drawRect(0, 0, w, h, paint);
    }

    private static void drawWatermark(Canvas canvas, Config cfg, int w, int h) {
        String text = cfg.watermark == null ? "" : cfg.watermark.trim();
        if (text.isEmpty()) return;

        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0x99FFFFFF);
        tp.setTextSize(w * 0.032f);
        tp.setTextAlign(Paint.Align.RIGHT);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0x66000000);
        float textW = tp.measureText(text);
        float pad = w * 0.012f;
        float margin = w * 0.02f;
        float rectL = w - margin - textW - pad * 2;
        float rectT = h - margin - tp.getTextSize() - pad * 2;
        float rectR = w - margin;
        float rectB = h - margin;
        RectF rect = new RectF(rectL, rectT, rectR, rectB);
        canvas.drawRoundRect(rect, pad, pad, bg);
        canvas.drawText(text, rectR - pad, rectB - pad - tp.descent(), tp);
    }

    private static String safeTitle(Config cfg) {
        return cfg.title == null ? "" : cfg.title.trim();
    }

    private static String safeBody(Config cfg) {
        StringBuilder sb = new StringBuilder();
        if (cfg.text != null && !cfg.text.trim().isEmpty()) sb.append(cfg.text.trim());
        if (cfg.hashtags != null && !cfg.hashtags.trim().isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(cfg.hashtags.trim());
        }
        return sb.toString();
    }

    private static String firstLetter(String s) {
        if (s == null || s.trim().isEmpty()) return "A";
        return s.trim().substring(0, 1).toUpperCase();
    }

    private static void drawCroppedBitmap(Canvas canvas, Bitmap bmp, RectF bounds,
                                           float zoom, float offsetX, float offsetY) {
        if (bmp == null || bmp.isRecycled() || bounds.width() <= 0 || bounds.height() <= 0) return;
        float bw = bmp.getWidth();
        float bh = bmp.getHeight();
        float baseScale = Math.max(bounds.width() / bw, bounds.height() / bh);
        float scale = baseScale * clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        float drawW = bw * scale;
        float drawH = bh * scale;

        float extraX = drawW - bounds.width();
        float extraY = drawH - bounds.height();

        float dx = (bounds.width() - drawW) / 2f + clamp(offsetX, -1f, 1f) * (extraX / 2f);
        float dy = (bounds.height() - drawH) / 2f + clamp(offsetY, -1f, 1f) * (extraY / 2f);

        canvas.save();
        canvas.clipRect(bounds);
        canvas.drawBitmap(bmp, null,
                new RectF(bounds.left + dx, bounds.top + dy,
                        bounds.left + dx + drawW, bounds.top + dy + drawH),
                null);
        canvas.restore();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Bitmap loadBitmap(String path, int maxDimension) {
        if (path == null || path.isEmpty()) return null;
        File file = new File(path);
        if (!file.exists()) return null;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;

        int sample = 1;
        while (opts.outWidth / (sample * 2) >= maxDimension
                || opts.outHeight / (sample * 2) >= maxDimension) {
            sample *= 2;
        }
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sample;
        Bitmap bmp;
        try {
            bmp = BitmapFactory.decodeFile(path, opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
        if (bmp == null) return null;
        return rotateByExif(file, bmp);
    }

    private static Bitmap rotateByExif(File file, Bitmap bmp) {
        int orientation;
        try (InputStream in = new java.io.FileInputStream(file)) {
            ExifInterface exif = new ExifInterface(in);
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            return bmp;
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.postScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: matrix.postRotate(90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: matrix.postRotate(270); matrix.postScale(-1, 1); break;
            default: return bmp;
        }
        try {
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
            if (rotated != bmp) bmp.recycle();
            return rotated;
        } catch (OutOfMemoryError e) {
            return bmp;
        }
    }

    public static File saveBitmapToCache(Context context, Bitmap bitmap) throws IOException {
        File dir = new File(context.getCacheDir(), "share_posts");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create share_posts dir");
        }
        String name = "post_" + System.currentTimeMillis() + ".png";
        File file = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            boolean ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            if (!ok) throw new IOException("Bitmap compress failed");
        }
        return file;
    }
}