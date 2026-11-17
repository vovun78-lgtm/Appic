package com.example.appic.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class PostExporter {

    private PostExporter() {}

    public static boolean savePng(Bitmap bmp, OutputStream out) throws IOException {
        return bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
    }

    public static boolean savePdf(Bitmap bmp, OutputStream out) throws IOException {
        PdfDocument document = new PdfDocument();
        try {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                    bmp.getWidth(), bmp.getHeight(), 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            canvas.drawBitmap(bmp, 0, 0, null);
            document.finishPage(page);
            document.writeTo(out);
            return true;
        } finally {
            document.close();
        }
    }

    public static boolean saveHtml(Context context, PostRenderer.Config cfg,
                                   Bitmap bmp, OutputStream out) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean compressed = bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
        if (!compressed) throw new IOException("Bitmap compress failed");
        String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        String title = escapeHtml(cfg.title);
        String text = escapeHtml(cfg.text);
        String hashtags = escapeHtml(cfg.hashtags);
        String appName = escapeHtml(context.getString(
                context.getApplicationInfo().labelRes));

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"ru\">\n");
        html.append("<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>").append(title.isEmpty() ? appName : title).append("</title>\n");
        html.append("<style>\n");
        html.append("*{box-sizing:border-box;}\n");
        html.append("body{margin:0;padding:0;background:#f5f5f5;color:#212121;");
        html.append("font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Oxygen,Ubuntu,sans-serif;");
        html.append("line-height:1.6;}\n");
        html.append("article{max-width:820px;margin:0 auto;padding:32px 20px 60px;background:#fff;");
        html.append("min-height:100vh;box-shadow:0 0 40px rgba(0,0,0,0.05);}\n");
        html.append("h1{font-size:38px;line-height:1.2;margin:0 0 24px;font-weight:800;letter-spacing:-0.02em;}\n");
        html.append(".cover{display:block;width:100%;height:auto;border-radius:12px;");
        html.append("box-shadow:0 8px 32px rgba(0,0,0,0.12);margin:0 0 32px;}\n");
        html.append(".content{font-size:17px;line-height:1.7;color:#333;white-space:pre-wrap;");
        html.append("word-wrap:break-word;}\n");
        html.append(".hashtags{margin-top:32px;padding-top:20px;border-top:1px solid #eee;");
        html.append("color:#1976d2;font-size:15px;white-space:pre-wrap;}\n");
        html.append(".footer{margin-top:40px;color:#999;font-size:13px;text-align:center;}\n");
        html.append("@media(max-width:600px){article{padding:20px 16px;}h1{font-size:28px;}}\n");
        html.append("</style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("<article>\n");
        if (!title.isEmpty()) {
            html.append("<h1>").append(title).append("</h1>\n");
        }
        html.append("<img class=\"cover\" src=\"data:image/png;base64,").append(base64).append("\" alt=\"\">\n");
        if (!text.isEmpty()) {
            html.append("<div class=\"content\">").append(text).append("</div>\n");
        }
        if (!hashtags.isEmpty()) {
            html.append("<div class=\"hashtags\">").append(hashtags).append("</div>\n");
        }
        html.append("<div class=\"footer\">").append(appName).append("</div>\n");
        html.append("</article>\n");
        html.append("</body>\n");
        html.append("</html>");

        out.write(html.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
        return true;
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