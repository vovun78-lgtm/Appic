package com.example.appic.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Base64;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ArticleGenerator {

    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;
    private static final int MIN_PHOTO_HEIGHT = 80;
    private static final int PHOTO_GAP = 15;
    private static final int TEXT_GAP_BEFORE_PHOTOS = 20;
    private static final int HEADING_GAP = 20;

    private ArticleGenerator() {}

    public static void generateHtml(String title, String text,
                                    List<String> photoPaths, OutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"ru\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(escapeHtml(title == null ? "" : title)).append("</title>\n");
        sb.append("<style>\n");
        sb.append("*{box-sizing:border-box;}\n");
        sb.append("body{margin:0;padding:40px 20px;background:#fff;color:#212121;");
        sb.append("font-family:Georgia,'Times New Roman',serif;line-height:1.65;}\n");
        sb.append(".container{max-width:820px;margin:0 auto;text-align:center;}\n");
        sb.append("h1{font-size:34px;line-height:1.25;margin:0 0 28px;font-weight:700;");
        sb.append("text-align:center;color:#111;}\n");
        sb.append(".text{font-size:17px;line-height:1.7;margin:0 0 36px;");
        sb.append("text-align:center;white-space:pre-wrap;word-wrap:break-word;color:#333;}\n");
        sb.append(".photo{display:block;max-width:100%;height:auto;margin:0 auto 20px;");
        sb.append("border-radius:6px;box-shadow:0 4px 16px rgba(0,0,0,0.08);}\n");
        sb.append("@media(max-width:600px){body{padding:20px 12px;}h1{font-size:26px;}");
        sb.append(".text{font-size:15px;}}\n");
        sb.append("</style>\n</head>\n<body>\n<div class=\"container\">\n");

        if (title != null && !title.trim().isEmpty()) {
            sb.append("<h1>").append(escapeHtml(title.trim())).append("</h1>\n");
        }
        if (text != null && !text.trim().isEmpty()) {
            sb.append("<div class=\"text\">").append(escapeHtml(text.trim())).append("</div>\n");
        }
        if (photoPaths != null) {
            for (String path : photoPaths) {
                String base64 = encodePhotoAsBase64(path, 1200);
                if (base64 != null) {
                    sb.append("<img class=\"photo\" src=\"data:image/jpeg;base64,")
                            .append(base64).append("\" alt=\"\">\n");
                }
            }
        }
        sb.append("</div>\n</body>\n</html>");

        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public static void generatePdf(String title, String text,
                                   List<String> photoPaths, OutputStream out) throws IOException {
        PdfDocument doc = new PdfDocument();
        try {
            int contentW = PAGE_WIDTH - 2 * MARGIN;

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH, PAGE_HEIGHT, 1).create();
            PdfDocument.Page page = doc.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            int y = MARGIN;

            if (title != null && !title.trim().isEmpty()) {
                TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                titlePaint.setTextSize(22);
                titlePaint.setColor(Color.BLACK);
                StaticLayout titleLayout = StaticLayout.Builder
                        .obtain(title.trim(), 0, title.trim().length(), titlePaint, contentW)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0, 1.15f)
                        .setMaxLines(4)
                        .build();
                canvas.save();
                canvas.translate(MARGIN, y);
                titleLayout.draw(canvas);
                canvas.restore();
                y += titleLayout.getHeight() + HEADING_GAP;
            }

            if (text != null && !text.trim().isEmpty()) {
                TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setTextSize(12);
                textPaint.setColor(0xFF333333);
                String trimmedText = text.trim();
                StaticLayout textLayout = StaticLayout.Builder
                        .obtain(trimmedText, 0, trimmedText.length(), textPaint, contentW)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0, 1.4f)
                        .build();

                int lineCount = textLayout.getLineCount();
                int startLine = 0;

                while (startLine < lineCount) {
                    int availH = PAGE_HEIGHT - MARGIN - y;
                    int linesFit = 0;
                    for (int i = startLine; i < lineCount; i++) {
                        int offset = textLayout.getLineBottom(i) - textLayout.getLineTop(startLine);
                        if (offset > availH) break;
                        linesFit++;
                    }

                    if (linesFit == 0) {
                        doc.finishPage(page);
                        page = doc.startPage(new PdfDocument.PageInfo.Builder(
                                PAGE_WIDTH, PAGE_HEIGHT, doc.getPages().size() + 1).create());
                        canvas = page.getCanvas();
                        y = MARGIN;
                        continue;
                    }

                    int topY = textLayout.getLineTop(startLine);
                    int botY = textLayout.getLineBottom(startLine + linesFit - 1);

                    canvas.save();
                    canvas.translate(MARGIN, y - topY);
                    canvas.clipRect(0, topY, contentW, botY);
                    textLayout.draw(canvas);
                    canvas.restore();

                    startLine += linesFit;
                    y += (botY - topY);

                    if (startLine < lineCount) {
                        doc.finishPage(page);
                        page = doc.startPage(new PdfDocument.PageInfo.Builder(
                                PAGE_WIDTH, PAGE_HEIGHT, doc.getPages().size() + 1).create());
                        canvas = page.getCanvas();
                        y = MARGIN;
                    }
                }
                y += TEXT_GAP_BEFORE_PHOTOS;
            }

            if (photoPaths != null) {
                for (String path : photoPaths) {
                    Bitmap bmp = loadScaledBitmap(path, 2000);
                    if (bmp == null) continue;

                    float aspect = (float) bmp.getHeight() / bmp.getWidth();

                    int idealW = contentW;
                    int idealH = (int) (idealW * aspect);

                    int maxH = PAGE_HEIGHT - 2 * MARGIN;
                    if (idealH > maxH) {
                        idealH = maxH;
                        idealW = (int) (idealH / aspect);
                    }

                    int availH = PAGE_HEIGHT - MARGIN - y;
                    int minH = Math.min(idealH, MIN_PHOTO_HEIGHT);

                    int drawW;
                    int drawH;

                    if (availH >= minH) {
                        drawH = Math.min(idealH, availH);
                        drawW = (int) (drawH / aspect);
                        if (drawW > contentW) {
                            drawW = contentW;
                            drawH = (int) (drawW * aspect);
                        }
                    } else {
                        doc.finishPage(page);
                        page = doc.startPage(new PdfDocument.PageInfo.Builder(
                                PAGE_WIDTH, PAGE_HEIGHT, doc.getPages().size() + 1).create());
                        canvas = page.getCanvas();
                        y = MARGIN;
                        drawW = idealW;
                        drawH = idealH;
                    }

                    float left = (PAGE_WIDTH - drawW) / 2f;
                    RectF dest = new RectF(left, y, left + drawW, y + drawH);
                    canvas.drawBitmap(bmp, null, dest, null);
                    bmp.recycle();
                    y += drawH + PHOTO_GAP;
                }
            }

            doc.finishPage(page);
            doc.writeTo(out);
            out.flush();
        } finally {
            doc.close();
        }
    }

    private static String encodePhotoAsBase64(String path, int maxDimension) {
        Bitmap bmp = loadScaledBitmap(path, maxDimension);
        if (bmp == null) return null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        bmp.recycle();
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    private static Bitmap loadScaledBitmap(String path, int maxDimension) {
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
        try (InputStream in = new FileInputStream(file)) {
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
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270); matrix.postScale(-1, 1); break;
            default: return bmp;
        }
        try {
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0,
                    bmp.getWidth(), bmp.getHeight(), matrix, true);
            if (rotated != bmp) bmp.recycle();
            return rotated;
        } catch (OutOfMemoryError e) {
            return bmp;
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                case '\n': sb.append("<br>"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}