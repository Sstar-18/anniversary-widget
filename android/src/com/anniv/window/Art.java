package com.anniv.window;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Typeface;

/** 小组件里的图形：圆环 / 点阵 / 背景插画，全部本地绘制成位图（RemoteViews 只能放静态图）。 */
public class Art {

    /** 24 节气进度圆环 */
    public static Bitmap ring(int size, int progress, int track, int arc, int text) {
        Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float stroke = Math.max(2.2f, size * 0.055f);
        RectF oval = new RectF(stroke / 2f + 1, stroke / 2f + 1, size - stroke / 2f - 1, size - stroke / 2f - 1);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke * 0.55f);
        p.setColor(track);
        c.drawArc(oval, -90, 360, false, p);

        p.setStrokeWidth(stroke);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(arc);
        c.drawArc(oval, -90, 360f * progress / 24f, false, p);

        float ang = (float) Math.toRadians(-90 + 360f * progress / 24f);
        float r = (size - stroke) / 2f;
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(size / 2f + r * (float) Math.cos(ang), size / 2f + r * (float) Math.sin(ang),
                stroke * 0.55f, p);

        p.setColor(text);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        p.setTextSize(size * 0.30f);
        c.drawText(String.valueOf(progress), size / 2f, size / 2f + size * 0.105f, p);
        p.setTextSize(size * 0.115f);
        c.drawText("/ 24", size / 2f, size / 2f + size * 0.235f, p);
        return bm;
    }

    /** 二十四节气点阵：一行 24 格，兼作分隔线 */
    public static Bitmap dots(int w, int h, int progress, int on, int off) {
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float gap = 2.2f;
        float cw = (w - gap * 23) / 24f;
        float rr = Math.min(cw, h) * 0.42f;
        for (int i = 0; i < 24; i++) {
            float l = i * (cw + gap);
            p.setColor(i < progress ? on : off);
            c.drawRoundRect(new RectF(l, 0, l + cw, h), rr, rr, p);
        }
        return bm;
    }

    // ---------- 背景插画 ----------

    private static Path heart(float s) {
        Path p = new Path();
        p.moveTo(0, 0.30f * s);
        p.cubicTo(0, -0.06f * s, -0.46f * s, -0.06f * s, -0.46f * s, 0.32f * s);
        p.cubicTo(-0.46f * s, 0.66f * s, 0, 0.90f * s, 0, 1.08f * s);
        p.cubicTo(0, 0.90f * s, 0.46f * s, 0.66f * s, 0.46f * s, 0.32f * s);
        p.cubicTo(0.46f * s, -0.06f * s, 0, -0.06f * s, 0, 0.30f * s);
        return p;
    }

    private static Path sparkle(float s) {
        Path p = new Path();
        p.moveTo(0, -s);
        p.quadTo(0.12f * s, -0.12f * s, s, 0);
        p.quadTo(0.12f * s, 0.12f * s, 0, s);
        p.quadTo(-0.12f * s, 0.12f * s, -s, 0);
        p.quadTo(-0.12f * s, -0.12f * s, 0, -s);
        return p;
    }

    private static void heartAt(Canvas c, Paint p, float x, float y, float s, int color, int alpha) {
        p.setColor(color);
        p.setAlpha(alpha);
        Path path = heart(s);
        c.save();
        c.translate(x, y);
        c.drawPath(path, p);
        c.restore();
    }

    private static void sparkleAt(Canvas c, Paint p, float x, float y, float s, int color, int alpha) {
        p.setColor(color);
        p.setAlpha(alpha);
        Path path = sparkle(s);
        c.save();
        c.translate(x, y);
        c.drawPath(path, p);
        c.restore();
    }

    /**
     * 卡片背景：圆角底 + 渐变 + 可可爱爱的点缀（爱心、星光、云朵）。
     * 低透明度、贴边放置，不压正文。
     */
    public static Bitmap background(int style, int w, int h) {
        Bitmap bm = Bitmap.createBitmap(Math.max(w, 8), Math.max(h, 8), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float r = 18f * w / 300f;
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
        c.clipPath(clip);

        int top, bottom, ink;
        if (style == 3) {          // 夜读
            top = 0xFF232019; bottom = 0xFF171513; ink = 0xFFC8A46A;
        } else if (style == 1) {   // 粉色
            top = 0xFFFDEDF1; bottom = 0xFFF9DDE5; ink = 0xFFE0708F;
        } else {                   // 纸笺 / 点阵
            top = 0xFFFDF9F0; bottom = 0xFFF8F0E2; ink = 0xFFA9443C;
        }
        LinearGradient lg = new LinearGradient(0, 0, w * 0.7f, h, top, bottom, Shader.TileMode.CLAMP);
        p.setShader(lg);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // 边缘描边
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, w / 600f));
        p.setColor(style == 3 ? 0x55C8A46A : 0x22A5762F);
        c.drawRoundRect(new RectF(1, 1, w - 1, h - 1), r, r, p);
        p.setStyle(Paint.Style.FILL);

        float u = h / 124f;   // 以设计稿高度换算

        if (style == 1) {      // 粉色：爱心 + 星光
            heartAt(c, p, w * 0.055f, h * 0.10f, 13 * u, ink, 70);
            heartAt(c, p, w * 0.14f, h * 0.72f, 8 * u, ink, 45);
            heartAt(c, p, w * 0.93f, h * 0.80f, 16 * u, ink, 55);
            sparkleAt(c, p, w * 0.86f, h * 0.16f, 7 * u, ink, 60);
            sparkleAt(c, p, w * 0.66f, h * 0.90f, 5 * u, ink, 45);
            p.setColor(0x22E0708F);
            c.drawCircle(w * 0.80f, h * 0.55f, 22 * u, p);
            c.drawCircle(w * 0.20f, h * 0.30f, 14 * u, p);
        } else if (style == 3) { // 夜读：月亮 + 星
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.2f * u);
            p.setColor(0x77F2E9DA);
            c.drawArc(new RectF(w * 0.86f, h * 0.08f, w * 0.97f, h * 0.34f), 40, 260, false, p);
            p.setStyle(Paint.Style.FILL);
            int[][] st = {{8, 22, 2}, {18, 58, 1}, {62, 12, 1}, {74, 84, 2}, {92, 66, 1}, {40, 92, 1}};
            for (int[] s : st) sparkleAt(c, p, w * s[0] / 100f, h * s[1] / 100f, s[2] * u, 0xFFF2E9DA, 90);
        } else {               // 纸笺 / 点阵：小枝 + 点
            heartAt(c, p, w * 0.90f, h * 0.74f, 9 * u, ink, 34);
            heartAt(c, p, w * 0.08f, h * 0.84f, 7 * u, ink, 30);
            sparkleAt(c, p, w * 0.52f, h * 0.88f, 4 * u, ink, 30);
            p.setColor(0x18A5762F);
            c.drawCircle(w * 0.96f, h * 0.30f, 26 * u, p);
        }
        return bm;
    }

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }



    /** 天气小图标：0 晴 / 1 云 / 2 雨 / 3 雪 / 4 雷 / 5 雾，线性描边。 */
    public static Bitmap weather(int kind, int sizePx, int color) {
        int S = Math.max(12, sizePx);
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        float u = S / 24f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.3f, u * 1.5f));
        p.setColor(color);

        if (kind == 0) {
            cv.drawCircle(12 * u, 12 * u, 5.2f * u, p);
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45);
                cv.drawLine(12 * u + (float) Math.cos(a) * 7.6f * u, 12 * u + (float) Math.sin(a) * 7.6f * u,
                        12 * u + (float) Math.cos(a) * 10.2f * u, 12 * u + (float) Math.sin(a) * 10.2f * u, p);
            }
            return bmp;
        }

        if (kind == 5) {
            for (int i = 0; i < 4; i++)
                cv.drawLine(4 * u, (7 + i * 4) * u, (i % 2 == 0 ? 20 : 17) * u, (7 + i * 4) * u, p);
            return bmp;
        }

        // 云底
        Path cloud = new Path();
        cv.drawCircle(9.0f * u, 11.0f * u, 4.6f * u, p);
        cv.drawCircle(14.6f * u, 9.6f * u, 3.4f * u, p);
        cv.drawCircle(19.0f * u, 12.4f * u, 2.9f * u, p);
        cv.drawLine(4.6f * u, 13.4f * u, 18.5f * u, 13.4f * u, p);

        if (kind == 1) {
            cv.drawLine(4.6f * u, 13.4f * u, 18.5f * u, 13.4f * u, p);
        } else if (kind == 2) {
            for (int i = 0; i < 3; i++)
                cv.drawLine((6 + i * 4.2f) * u, 16.6f * u, (4.6f + i * 4.2f) * u, 21.6f * u, p);
        } else if (kind == 3) {
            for (int i = 0; i < 2; i++) {
                float cx0 = (8 + i * 7) * u, cy0 = 19f * u, r0 = 2.1f * u;
                cv.drawLine(cx0, cy0 - r0, cx0, cy0 + r0, p);
                cv.drawLine(cx0 - r0 * 0.87f, cy0 - r0 * 0.5f, cx0 + r0 * 0.87f, cy0 + r0 * 0.5f, p);
                cv.drawLine(cx0 - r0 * 0.87f, cy0 + r0 * 0.5f, cx0 + r0 * 0.87f, cy0 - r0 * 0.5f, p);
            }
        } else if (kind == 4) {
            Path bolt = new Path();
            bolt.moveTo(13.6f * u, 15.2f * u);
            bolt.lineTo(9.6f * u, 21.4f * u);
            bolt.lineTo(12.6f * u, 21.4f * u);
            bolt.lineTo(11.4f * u, 24.0f * u);
            cv.drawPath(bolt, p);
        }
        return bmp;
    }

    /**
     * 卡通天气小牌：牌子本身长成天气形状（太阳圆盘 / 云朵），温度与城市·天气写在里面，
     * 外面挂几笔卡通小笔画。
     * kind: 0 晴 / 1 云 / 2 雨 / 3 雪 / 4 雷 / 5 雾
     */
    public static Bitmap weatherPanel(int kind, String temp, String desc, int wPx, int hPx,
                                      int strokeColor, int textColor, int subColor) {
        int W = Math.max(64, wPx), H = Math.max(40, hPx);
        float u0 = W / 84f;                  // 84dp 设计基准
        int DH = Math.round(60f * u0);        // 设计画布高度（60dp）
        Bitmap bmp = Bitmap.createBitmap(W, DH, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);

        float u = u0;                        // 84dp 设计基准
        float top = 11f * u;
        float bot = 47f * u;
        float rc = 8f * u;
        float lw = Math.max(1.5f, 1.7f * u);  // 卡通粗描边

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(lw);
        line.setColor(strokeColor);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x5AFFFFFF);

        if (kind == 0) {
            // 晴：圆角胖盘 + 一圈短光芒
            float cx = W / 2f, cy = (top + bot) / 2f;
            float hw = W / 2f - 4f * u, hh = (bot - top) / 2f;
            cv.drawRoundRect(new RectF(cx - hw, cy - hh, cx + hw, cy + hh), hh, hh, fill);
            for (int i = 0; i < 12; i++) {
                double ang = Math.toRadians(i * 30 + 15);
                float x1 = cx + (float) Math.cos(ang) * (hw + 2.2f * u);
                float y1 = cy + (float) Math.sin(ang) * (hh + 2.0f * u);
                float x2 = cx + (float) Math.cos(ang) * (hw + 5.2f * u);
                float y2 = cy + (float) Math.sin(ang) * (hh + 5.0f * u);
                cv.drawLine(x1, y1, x2, y2, line);
            }
            cv.drawRoundRect(new RectF(cx - hw, cy - hh, cx + hw, cy + hh), hh, hh, line);
        } else {
            // 云朵：上沿三团半圆 + 圆角身子
            float c1 = 17f * u, c2 = 38f * u, c3 = 54f * u;
            float r1 = 10f * u, r2 = 8.4f * u, r3 = 6f * u;
            Path body = new Path();
            body.addRoundRect(new RectF(0.5f, top, W - 0.5f, bot), rc, rc, Path.Direction.CW);
            cv.drawPath(body, fill);
            cv.drawArc(new RectF(c1 - r1, top - r1, c1 + r1, top + r1), 180, 180, false, line);
            cv.drawArc(new RectF(c2 - r2, top - r2, c2 + r2, top + r2), 180, 180, false, line);
            cv.drawArc(new RectF(c3 - r3, top - r3, c3 + r3, top + r3), 180, 180, false, line);
            float[] xs = {rc, c1 - r1, c1 + r1, c2 - r2, c2 + r2, c3 - r3, c3 + r3, W - rc};
            for (int i = 0; i < xs.length; i += 2) {
                if (xs[i + 1] > xs[i]) cv.drawLine(xs[i], top, xs[i + 1], top, line);
            }
            cv.drawArc(new RectF(0, top, rc * 2, top + rc * 2), 180, 90, false, line);
            cv.drawArc(new RectF(W - rc * 2, top, W, top + rc * 2), 270, 90, false, line);
            cv.drawLine(0, top + rc, 0, bot - rc, line);
            cv.drawLine(W, top + rc, W, bot - rc, line);
            cv.drawArc(new RectF(0, bot - rc * 2, rc * 2, bot), 90, 90, false, line);
            cv.drawArc(new RectF(W - rc * 2, bot - rc * 2, W, bot), 0, 90, false, line);
            cv.drawLine(rc, bot, W - rc, bot, line);
        }

        // 云底外挂的小笔画
        if (kind == 2) {
            for (int i = 0; i < 3; i++) {
                float dx = (32 + i * 10) * u;
                Path drop = new Path();
                drop.moveTo(dx, bot + 0.8f * u);
                drop.lineTo(dx - 1.8f * u, bot + 3.4f * u);
                drop.lineTo(dx, bot + 5.2f * u);
                drop.lineTo(dx + 1.8f * u, bot + 3.4f * u);
                drop.close();
                cv.drawPath(drop, line);
            }
        } else if (kind == 3) {
            for (int i = 0; i < 2; i++) {
                float px = (34 + i * 14) * u, py = bot + 3.8f * u, s0 = 2f * u;
                cv.drawLine(px, py - s0, px, py + s0, line);
                cv.drawLine(px - s0 * 0.87f, py - s0 * 0.5f, px + s0 * 0.87f, py + s0 * 0.5f, line);
                cv.drawLine(px - s0 * 0.87f, py + s0 * 0.5f, px + s0 * 0.87f, py - s0 * 0.5f, line);
            }
        } else if (kind == 4) {
            Path bolt = new Path();
            bolt.moveTo(41f * u, bot + 0.6f * u);
            bolt.lineTo(36.2f * u, bot + 4.8f * u);
            bolt.lineTo(39.4f * u, bot + 4.8f * u);
            bolt.lineTo(37.4f * u, bot + 8.6f * u);
            cv.drawPath(bolt, line);
        } else if (kind == 5) {
            for (int i = 0; i < 3; i++)
                cv.drawLine((12 + i * 13) * u, (bot + 2.6f + i * 3f) * u,
                        (27 + i * 13) * u, (bot + 2.6f + i * 3f) * u, line);
        }

        // 温度（越界不缩得太小）
        Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
        big.setColor(textColor);
        big.setTypeface(Typeface.create("serif", Typeface.BOLD));
        float ts = 22f * u;
        big.setTextSize(ts);
        while (big.measureText(temp) > (W - 16f * u) && ts > 9 * u) { ts -= 0.5f * u; big.setTextSize(ts); }
        cv.drawText(temp, (W - big.measureText(temp)) / 2f, 32f * u, big);

        // 城市 · 天气
        Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        small.setColor(subColor);
        small.setTextSize(Math.max(7.6f, 8.4f * u));
        String dd = desc;
        while (small.measureText(dd) > (W - 14f * u) && dd.length() > 1) dd = dd.substring(0, dd.length() - 1);
        cv.drawText(dd.isEmpty() ? "—" : dd, (W - small.measureText(dd)) / 2f, 44.5f * u, small);

        if (DH == H) return bmp;
        // 目标高度与设计高度不同：整体压进目标画布（保持宽度不变，纵向轻压缩）
        Bitmap fit = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas fc = new Canvas(fit);
        fc.drawBitmap(bmp, null, new RectF(0, 0, W, H), null);
        return fit;
    }

    /** 相框：圆角粉框 + 小爱心装饰，照片居中裁切；没照片时画虚线占位 */
    public static Bitmap photoFrame(Bitmap photo, int wPx, int hPx, int strokeColor, int bgColor, int textColor) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, wPx), Math.max(1, hPx), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float u = wPx / 108f;
        float r = 10f * u;

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(bgColor);
        RectF outer = new RectF(0, 0, wPx, hPx);
        cv.drawRoundRect(outer, r, r, fill);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(strokeColor);
        line.setStrokeWidth(Math.max(1.4f, 2.2f * u));
        RectF r1 = new RectF(line.getStrokeWidth() / 2f, line.getStrokeWidth() / 2f,
                wPx - line.getStrokeWidth() / 2f, hPx - line.getStrokeWidth() / 2f);
        cv.drawRoundRect(r1, r, r, line);

        line.setStrokeWidth(1f * u);
        line.setColor((strokeColor & 0x00FFFFFF) | 0x33000000);
        RectF r2 = new RectF(4f * u, 4f * u, wPx - 4f * u, hPx - 4f * u);
        cv.drawRoundRect(r2, 7.6f * u, 7.6f * u, line);

        RectF inner = new RectF(7.4f * u, 7.4f * u, wPx - 7.4f * u, hPx - 7.4f * u);
        float ir = 6.4f * u;

        if (photo == null) {
            Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
            dash.setStyle(Paint.Style.STROKE);
            dash.setColor((strokeColor & 0x00FFFFFF) | 0x55000000);
            dash.setStrokeWidth(1.1f * u);
            dash.setPathEffect(new android.graphics.DashPathEffect(new float[]{4f * u, 3f * u}, 0));
            Path dp = new Path();
            dp.addRoundRect(inner, ir, ir, Path.Direction.CW);
            cv.drawPath(dp, dash);

            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(textColor);
            t.setTextSize(Math.max(8.5f, 10f * u));
            t.setTextAlign(Paint.Align.CENTER);
            cv.drawText("＋", wPx / 2f, hPx * 0.44f, t);
            t.setTextSize(Math.max(7.5f, 8.6f * u));
            cv.drawText("放一张照片", wPx / 2f, hPx * 0.44f + 13f * u, t);
        } else {
            Paint cover = new Paint(Paint.ANTI_ALIAS_FLAG);
            cover.setColor(bgColor);
            cv.drawRoundRect(inner, ir, ir, cover);

            // 居中裁切铺满
            RectF dst = new RectF(inner);
            int sw = photo.getWidth(), sh = photo.getHeight();
            float scale = Math.max(dst.width() / sw, dst.height() / sh);
            float cw = dst.width() / scale, ch = dst.height() / scale;
            Rect src = new Rect((int) ((sw - cw) / 2f), (int) ((sh - ch) / 2f),
                    (int) ((sw + cw) / 2f), (int) ((sh + ch) / 2f));

            Path clip = new Path();
            clip.addRoundRect(inner, ir, ir, Path.Direction.CW);
            cv.save();
            cv.clipPath(clip);
            cv.drawBitmap(photo, src, dst, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            cv.restore();

            line.setStrokeWidth(1f * u);
            line.setColor((strokeColor & 0x00FFFFFF) | 0x40000000);
            cv.drawRoundRect(inner, ir, ir, line);
        }

        // 右上角两颗小爱心、左下一点星光
        Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
        hp.setColor(strokeColor);
        float hx = wPx - 13f * u, hy = 13f * u;
        heartPath(cv, hp, hx, hy, 3.4f * u);
        hp.setColor((strokeColor & 0x00FFFFFF) | 0x66000000);
        heartPath(cv, hp, hx + 6.2f * u, hy + 1.2f * u, 2.4f * u);

        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setColor((strokeColor & 0x00FFFFFF) | 0x88000000);
        sparkle(cv, sp, 13f * u, hPx - 12f * u, 3.2f * u);
        return b;
    }

    private static void heartPath(Canvas cv, Paint p, float cx, float cy, float r) {
        Path path = new Path();
        path.moveTo(cx, cy + r * 0.92f);
        path.cubicTo(cx - r * 1.45f, cy - r * 0.15f, cx - r * 0.62f, cy - r * 1.05f, cx, cy - r * 0.34f);
        path.cubicTo(cx + r * 0.62f, cy - r * 1.05f, cx + r * 1.45f, cy - r * 0.15f, cx, cy + r * 0.92f);
        cv.drawPath(path, p);
    }

    private static void sparkle(Canvas cv, Paint p, float cx, float cy, float r) {
        Path path = new Path();
        path.moveTo(cx, cy - r);
        path.quadTo(cx + r * 0.18f, cy - r * 0.18f, cx + r, cy);
        path.quadTo(cx + r * 0.18f, cy + r * 0.18f, cx, cy + r);
        path.quadTo(cx - r * 0.18f, cy + r * 0.18f, cx - r, cy);
        path.quadTo(cx - r * 0.18f, cy - r * 0.18f, cx, cy - r);
        cv.drawPath(path, p);
    }

    /** 一排小爱心：三颗、大小错落，粉色系 */
    public static Bitmap hearts(int wPx, int hPx, int color) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, wPx), Math.max(1, hPx), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float u = wPx / 22f;
        int light = mix(color, 0xFFFFFFFF, 0.55f);
        float[] cx = {4f * u, 11f * u, 18f * u};
        float[] rr = {3.2f, 4.2f, 3.2f};
        float cy = hPx * 0.52f;
        for (int i = 0; i < 3; i++) {
            float x = cx[i], r = rr[i] * u;
            Path path = new Path();
            path.moveTo(x, cy + r * 0.92f);
            path.cubicTo(x - r * 1.45f, cy - r * 0.15f, x - r * 0.62f, cy - r * 1.05f, x, cy - r * 0.34f);
            path.cubicTo(x + r * 0.62f, cy - r * 1.05f, x + r * 1.45f, cy - r * 0.15f, x, cy + r * 0.92f);
            p.setShader(null);
            p.setColor(color);
            cv.drawPath(path, p);
            p.setColor(light);
            cv.drawCircle(x - r * 0.52f, cy - r * 0.28f, r * 0.26f, p);
        }
        return b;
    }

    /** 便签顶部的三个装订环（带高光、阴影与孔洞的立体环） */
    /** 三个立体环，分散在整条上边沿：左上、中上、右上 */
    public static Bitmap ringsSpread(int wPx, int hPx, float density, int strokeColor) {
        int W = Math.max(120, wPx), H = Math.max(16, hPx);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);

        float u = density;                   // dp 单位
        float r = 8f * u;                    // 环外径
        float inset = 24f * u;               // 左右环心到边沿
        float cy = H * 0.56f;

        int light = mix(strokeColor, 0xFFFFFFFF, 0.58f);
        int dark = mix(strokeColor, 0xFF2A1418, 0.42f);

        for (int i = 0; i < 3; i++) {
            float cx = (i == 0) ? inset : (i == 1 ? W / 2f : W - inset);

            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setStyle(Paint.Style.FILL);
            sh.setColor(0x30000000);
            cv.drawCircle(cx + 0.7f * u, cy + 1.5f * u, r + 0.5f * u, sh);

            Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
            body.setStyle(Paint.Style.FILL);
            body.setShader(new RadarGradientShim(cx - r * 0.35f, cy - r * 0.45f, r * 1.2f,
                    new int[]{light, strokeColor, dark}).build());
            cv.drawCircle(cx, cy, r, body);
            body.setShader(null);

            Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
            hole.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            cv.drawCircle(cx, cy, r - 3f * u, hole);

            Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
            inner.setStyle(Paint.Style.STROKE);
            inner.setStrokeWidth(1f * u);
            inner.setColor(0x402A1418);
            cv.drawCircle(cx, cy, r - 2.7f * u, inner);

            Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
            gloss.setStyle(Paint.Style.STROKE);
            gloss.setStrokeWidth(1.1f * u);
            gloss.setColor(0x55FFFFFF);
            cv.drawArc(new RectF(cx - r + 1.4f * u, cy - r + 1.4f * u,
                    cx + r - 1.4f * u, cy + r - 1.4f * u), 170, 70, false, gloss);
        }
        return bmp;
    }

    public static Bitmap rings(int wPx, int hPx, int strokeColor) {
        int W = Math.max(60, wPx), H = Math.max(16, hPx);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);

        float u = W / 76f;                  // 76dp 设计基准
        float r = 8f * u;                   // 环外径
        float gap = 26f * u;                // 环心间距
        float cy = H * 0.56f;

        int light = mix(strokeColor, 0xFFFFFFFF, 0.58f);
        int dark  = mix(strokeColor, 0xFF2A1418, 0.42f);
        int shadow = 0x30000000;

        for (int i = 0; i < 3; i++) {
            float cx = W / 2f + (i - 1) * gap;

            // 落在纸上的一层淡影
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setStyle(Paint.Style.FILL);
            sh.setColor(shadow);
            cv.drawCircle(cx + 0.7f * u, cy + 1.5f * u, r + 0.5f * u, sh);

            // 环体：斜上方受光，右下走暗
            Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
            body.setStyle(Paint.Style.FILL);
            body.setShader(new RadarGradientShim(cx - r * 0.35f, cy - r * 0.45f, r * 1.2f,
                    new int[]{light, strokeColor, dark}).build());
            cv.drawCircle(cx, cy, r, body);
            body.setShader(null);

            // 挖孔
            Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
            hole.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            cv.drawCircle(cx, cy, r - 3f * u, hole);

            // 孔口一圈内阴影，做出厚度
            Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
            inner.setStyle(Paint.Style.STROKE);
            inner.setStrokeWidth(1f * u);
            inner.setColor(0x402A1418);
            cv.drawCircle(cx, cy, r - 2.7f * u, inner);

            // 左上一点高光
            Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
            gloss.setStyle(Paint.Style.STROKE);
            gloss.setStrokeWidth(1.1f * u);
            gloss.setColor(0x55FFFFFF);
            cv.drawArc(new RectF(cx - r + 1.4f * u, cy - r + 1.4f * u,
                    cx + r - 1.4f * u, cy + r - 1.4f * u), 170, 70, false, gloss);
        }
        return bmp;
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        int r = (int) (ar + (br - ar) * t), g = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t);
        return 0xff000000 | (r << 16) | (g << 8) | bl;
    }

    /** 小垫片：只为构造 RadialGradient 时少写点字 */
    private static class RadarGradientShim {
        float x, y, rad; int[] colors;
        RadarGradientShim(float x, float y, float rad, int[] colors) { this.x = x; this.y = y; this.rad = rad; this.colors = colors; }
        Shader build() {
            return new RadialGradient(x, y, rad, colors, new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP);
        }
    }
}
