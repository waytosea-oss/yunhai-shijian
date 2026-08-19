package io.github.waytosea.yunhaishijian;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** 双环组绘制工具：叠加层与详情面板共用，所有尺寸按 size 等比缩放 */
final class BalanceDrawing {
    static final int CODEX_C5 = 0xFFED93B1;   // 粉
    static final int CODEX_C7 = 0xFFEFB25C;   // 琥珀
    static final int CLAUDE_C5 = 0xFF85B7EB;  // 蓝
    static final int CLAUDE_C7 = 0xFFAFA9EC;  // 紫
    static final int GRAY = 0xFF6E6E76;

    private static final int[] WARM = {0xFFED93B1, 0xFFEFB25C, 0xFFF0A183};
    private static final int[] COLD = {0xFF85B7EB, 0xFFAFA9EC, 0xFF7FCFC3, 0xFFC79FE0};

    /** 窗口序号取色：色=窗口身份（Codex 暖族 / Claude 冷族） */
    static int windowColor(boolean cool, int index) {
        int[] family = cool ? COLD : WARM;
        return family[index % family.length];
    }

    private BalanceDrawing() {}

    /** 画一组「主环+卫星」：主环=瓶颈窗口，环心 [%/label/倒计时]，环下卫星胶囊行。窗口数任意。 */
    static void drawGroup(Canvas canvas, Paint ringPaint, Paint textPaint,
                          float left, float top, float size, boolean stale,
                          BalanceData.Tool tool, String name, boolean cool) {
        java.util.List<BalanceData.Window> windows = tool.resolvedWindows();
        int bottleneckIdx = tool.bottleneckIndex();
        BalanceData.Window bottleneck = windows.isEmpty() ? null : windows.get(Math.min(bottleneckIdx, windows.size() - 1));
        int heroColor = stale ? GRAY : windowColor(cool, bottleneckIdx);

        float cx = left + size / 2, cy = top + size * 0.44f;
        float ringR = size * 0.38f;

        // 工具名（环上方）
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(stale ? GRAY : 0x8CFFFFFF);
        textPaint.setTextSize(size * 0.085f);
        canvas.drawText(name, cx, top + size * 0.02f, textPaint);

        drawRing(canvas, ringPaint, cx, cy, ringR, size * 0.085f, heroColor,
                bottleneck == null || bottleneck.pct < 0 ? 0 : bottleneck.pct / 100.0);

        // 环心三行
        textPaint.setColor(stale ? GRAY : 0xEBFFFFFF);
        textPaint.setTextSize(size * 0.20f);
        canvas.drawText(bottleneck == null ? "--" : percentText(bottleneck.pct, stale), cx, cy + size * 0.02f, textPaint);
        textPaint.setColor(heroColor);
        textPaint.setTextSize(size * 0.085f);
        canvas.drawText(bottleneck == null ? "" : bottleneck.label, cx, cy + size * 0.12f, textPaint);
        textPaint.setColor(stale ? GRAY : 0x73FFFFFF);
        textPaint.setTextSize(size * 0.075f);
        canvas.drawText(bottleneck == null || stale ? "--"
                : countdownText(bottleneck.reset, bottleneck.hourScale), cx, cy + size * 0.21f, textPaint);

        // 卫星胶囊行（环下）
        int n = Math.max(1, windows.size());
        float capW = Math.min(size * 0.18f, (size * 0.9f - (n - 1) * size * 0.05f) / n);
        float capH = size * 0.045f;
        float gap = size * 0.05f;
        float totalW = n * capW + (n - 1) * gap;
        float sx = cx - totalW / 2;
        float sy = top + size * 0.92f;
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < windows.size(); i++) {
            BalanceData.Window w = windows.get(i);
            int tint = stale ? GRAY : windowColor(cool, i);
            RectF track = new RectF(sx, sy, sx + capW, sy + capH);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor((tint & 0x00FFFFFF) | 0x30000000);
            canvas.drawRoundRect(track, capH / 2, capH / 2, fill);
            float ratio = (float) Math.max(0, Math.min(1, w.pct / 100.0));
            if (ratio > 0.02) {
                fill.setColor(tint);
                canvas.drawRoundRect(new RectF(sx, sy, sx + capW * ratio, sy + capH), capH / 2, capH / 2, fill);
            }
            if (i == bottleneckIdx) {
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(1.2f);
                fill.setColor(0x80FFFFFF);
                canvas.drawRoundRect(new RectF(sx - 1, sy - 1, sx + capW + 1, sy + capH + 1), capH / 2 + 1, capH / 2 + 1, fill);
            }
            sx += capW + gap;
        }
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private static void drawRing(Canvas canvas, Paint ringPaint, float cx, float cy,
                                 float radius, float stroke, int color, double fraction) {
        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        ringPaint.setStrokeWidth(stroke);
        ringPaint.setColor((color & 0x00FFFFFF) | 0x3C000000);
        canvas.drawArc(oval, 0, 360, false, ringPaint);
        ringPaint.setColor(color);
        float sweep = (float) (Math.max(0, Math.min(1, fraction)) * 360.0);
        if (sweep > 2) {
            canvas.drawArc(oval, -90, sweep, false, ringPaint);
        }
    }

    static String percentText(double value, boolean stale) {
        if (stale || value < 0) {
            return "--";
        }
        return Math.round(value) + "%";
    }

    /** 与 Mac 端 resetCountdownShort 一致：5小时窗口→X时Y分，7天窗口→X天Y时 */
    static String countdownText(long resetEpochSeconds, boolean hoursMode) {
        if (resetEpochSeconds <= 0) {
            return "--";
        }
        long seconds = resetEpochSeconds - System.currentTimeMillis() / 1000L;
        if (seconds <= 0) {
            return "--";
        }
        long minutes = seconds / 60, hours = minutes / 60, days = hours / 24;
        if (hoursMode) {
            if (hours <= 0) {
                return Math.max(1, minutes) + "分";
            }
            return hours + "时" + (minutes % 60) + "分";
        }
        if (days <= 0) {
            return hours + "时";
        }
        return days + "天" + (hours % 24) + "时";
    }

    /** 与 Mac 端 compactNumber 一致的 万/亿 表示 */
    static String compactTokens(long value) {
        if (value < 0) {
            return "--";
        }
        if (value >= 100_000_000L) {
            return compactUnit(value / 100_000_000.0, "亿");
        }
        if (value >= 10_000L) {
            return compactUnit(value / 10_000.0, "万");
        }
        return String.valueOf(value);
    }

    /** 长条样式的一行：字母章 + [条上方: 窗口标签·剩余时间] + 进度条(更紧张窗口) + 百分比 */
    static void drawBarRow(Canvas canvas, Paint ringPaint, Paint textPaint,
                           float left, float top, float rowW, float rowH, float d,
                           boolean mini, boolean stale,
                           BalanceData.Tool tool, String letter, boolean cool) {
        BalanceData.Window bn = tool.tightest();
        double pct = bn == null ? -1 : bn.pct;
        int color = stale ? GRAY : windowColor(cool, tool.bottleneckIndex());
        boolean isPrimary = bn != null && bn.hourScale;
        // 条的中线放在行的下部，给上方标签留位置
        float barMidY = top + rowH * 0.68f;

        // 字母章（对齐条的中线）
        float chip = rowH * 0.46f;
        RectF chipRect = new RectF(left, barMidY - chip / 2, left + chip, barMidY + chip / 2);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        canvas.drawRoundRect(chipRect, 5 * d, 5 * d, fill);
        textPaint.setColor(0xFF101014);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(chip * 0.62f);
        canvas.drawText(letter, chipRect.centerX(), barMidY + chip * 0.22f, textPaint);

        // 百分比数字（右对齐，对齐条的中线）
        float numW = (mini ? 46 : 58) * d;
        String pctText = (stale || pct < 0) ? "--" : Math.round(pct) + "%";
        textPaint.setColor(color);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize((mini ? 15 : 19) * d);
        canvas.drawText(pctText, left + rowW, barMidY + (mini ? 5 : 7) * d, textPaint);

        // 进度条（字母章右侧 到 数字左侧）
        float barLeft = chipRect.right + 8 * d;
        float barRight = left + rowW - numW;
        float barH = 7 * d;
        RectF track = new RectF(barLeft, barMidY - barH / 2, barRight, barMidY + barH / 2);
        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor((color & 0x00FFFFFF) | 0x33000000);
        canvas.drawRoundRect(track, barH / 2, barH / 2, bar);
        if (!stale && pct > 0) {
            float ratio = (float) Math.max(0, Math.min(1, pct / 100.0));
            RectF fillBar = new RectF(barLeft, barMidY - barH / 2,
                    barLeft + (barRight - barLeft) * ratio, barMidY + barH / 2);
            bar.setColor(color);
            canvas.drawRoundRect(fillBar, barH / 2, barH / 2, bar);
        }

        // 条上方：剩余刷新时间（格式自带维度："1时49分"=5时窗口，"6天15时"=7天窗口）
        long reset = bn == null ? 0 : bn.reset;
        String label = stale ? "--" : countdownText(reset, isPrimary);
        textPaint.setColor(stale ? GRAY : ((color & 0x00FFFFFF) | 0xC8000000));
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize((mini ? 10 : 12) * d);
        canvas.drawText(label, barLeft, top + rowH * 0.26f, textPaint);
    }

    private static String compactUnit(double value, String unit) {
        if (value >= 100 || Math.floor(value) == value) {
            return Math.round(value) + unit;
        }
        return String.format("%.1f%s", value, unit);
    }

    /** 徽章样式的一半：上行倒计时 + 下行[字母色块+瓶颈大数字+卫星点列]。窗口数任意。 */
    static void drawBadgeUnit(Canvas canvas, Paint textPaint, float left, float halfW, float h,
                              float d, boolean mini, boolean stale,
                              BalanceData.Tool tool, String letter, boolean cool) {
        java.util.List<BalanceData.Window> windows = tool.resolvedWindows();
        int bottleneckIdx = tool.bottleneckIndex();
        BalanceData.Window bn = tool.tightest();
        double pct = bn == null ? -1 : bn.pct;
        int color = stale ? GRAY : windowColor(cool, bottleneckIdx);
        float cx = left + halfW / 2;
        float lowY = h * 0.66f;

        long reset = bn == null ? 0 : bn.reset;
        String label = stale || bn == null ? "--" : countdownText(reset, bn.hourScale);
        textPaint.setColor(stale ? GRAY : ((color & 0x00FFFFFF) | 0xE0000000));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize((mini ? 12 : 15) * d);
        canvas.drawText(label, cx, h * 0.30f, textPaint);

        float chip = (mini ? 12 : 15) * d;
        String pctText = (stale || pct < 0) ? "--" : Math.round(pct) + "%";
        textPaint.setTextSize((mini ? 18 : 24) * d);
        float numW = textPaint.measureText(pctText);
        float dotColW = windows.size() > 1 ? 8 * d : 0;
        float groupW = chip + 7 * d + numW + dotColW;
        float gx = cx - groupW / 2;
        RectF chipRect = new RectF(gx, lowY - chip / 2, gx + chip, lowY + chip / 2);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        canvas.drawRoundRect(chipRect, 4 * d, 4 * d, fill);
        textPaint.setColor(0xFF101014);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(chip * 0.66f);
        canvas.drawText(letter, chipRect.centerX(), lowY + chip * 0.24f, textPaint);

        textPaint.setColor(stale ? GRAY : 0xFFEDEDED);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize((mini ? 18 : 24) * d);
        canvas.drawText(pctText, chipRect.right + 7 * d, lowY + (mini ? 6 : 8) * d, textPaint);

        // 卫星点列（竖排，越耗越实；瓶颈点带白描边）
        if (windows.size() > 1) {
            float dotX = chipRect.right + 7 * d + numW + 4 * d;
            float dotR = 2.2f * d;
            float step = 6 * d;
            float totalH = (windows.size() - 1) * step;
            float dy = lowY + totalH / 2;
            for (int i = 0; i < windows.size(); i++) {
                BalanceData.Window w = windows.get(i);
                int tint = stale ? GRAY : windowColor(cool, i);
                float used = (float) (1 - Math.max(0, Math.min(1, w.pct / 100.0)));
                int alpha = (int) (76 + 179 * used);
                fill.setStyle(Paint.Style.FILL);
                fill.setColor((tint & 0x00FFFFFF) | (alpha << 24));
                canvas.drawCircle(dotX, dy, dotR, fill);
                if (i == bottleneckIdx) {
                    fill.setStyle(Paint.Style.STROKE);
                    fill.setStrokeWidth(0.8f * d);
                    fill.setColor(0x99FFFFFF);
                    canvas.drawCircle(dotX, dy, dotR + 0.8f * d, fill);
                }
                dy -= step;
            }
        }
    }
}
