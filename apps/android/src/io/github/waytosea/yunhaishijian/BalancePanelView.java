package io.github.waytosea.yunhaishijian;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 点按小环后弹出的全屏详情面板：与 Mac 展开面板一致——
 * 每个工具一列：双环 + 今日/近7天/本月消耗看板 + 14天趋势柱状图 + 今日项目 Top。
 * 点按任意处关闭。
 */
public final class BalancePanelView extends View {
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundPaint = new Paint();
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private BalanceData data;
    private int selStyle = 0, selSize = 0;

    interface Listener {
        void onClose();
        void onStyleChange(int style, int size);
    }
    private Listener listener;
    void setListener(Listener l) { this.listener = l; }
    void setSelection(int style, int size) { this.selStyle = style; this.selSize = size; }

    // 选项卡命中区
    private static final class Chip { RectF r; int kind; int value; } // kind:0=样式 1=尺寸
    private final ArrayList<Chip> chips = new ArrayList<>();

    public BalancePanelView(Context context) {
        super(context);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setFakeBoldText(true);
        backgroundPaint.setColor(0xF2101014);
        cardPaint.setColor(0x14FFFFFF);
        setVisibility(GONE);
    }

    void applyData(BalanceData data) {
        this.data = data;
        if (getVisibility() == VISIBLE) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        float w = getWidth(), h = getHeight();
        float d = getResources().getDisplayMetrics().density;
        if (d <= 0) d = 1f;
        boolean stale = data == null
                || System.currentTimeMillis() - data.fetchedAt > 15L * 60L * 1000L;
        BalanceData.Tool codex = data != null ? data.codex : new BalanceData.Tool();
        BalanceData.Tool claude = data != null ? data.claude : new BalanceData.Tool();

        // 标题栏
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(0xFFEDEDED);
        textPaint.setTextSize(22 * d);
        canvas.drawText("算力码表", 24 * d, 40 * d, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setColor(0x99FFFFFF);
        textPaint.setTextSize(12 * d);
        String updated = data == null ? "--"
                : new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                        .format(new Date(data.fetchedAt));
        canvas.drawText("更新于 " + updated + "  ·  点按任意处返回", w - 24 * d, 38 * d, textPaint);

        // 两列
        float colGap = 24 * d;
        float colW = (w - 48 * d - colGap) / 2;
        float top = 58 * d;
        drawToolColumn(canvas, d, 24 * d, top, colW, h - top - 16 * d, stale, codex,
                "Codex", false);
        drawToolColumn(canvas, d, 24 * d + colW + colGap, top, colW, h - top - 16 * d, stale, claude,
                "Claude", true);

        drawChips(canvas, d, w, h);
    }

    private void drawChips(Canvas canvas, float d, float w, float h) {
        chips.clear();
        String[] styles = {"双环", "长条", "徽章"};
        String[] sizes = {"标准", "迷你"};
        float chipH = 30 * d, chipPad = 12 * d, gap = 6 * d, groupGap = 20 * d;
        float y = h - chipH - 10 * d;
        // 预估总宽居中
        textPaint.setTextSize(13 * d);
        textPaint.setTextAlign(Paint.Align.CENTER);
        float total = 0;
        for (String t : styles) total += textPaint.measureText(t) + chipPad * 2 + gap;
        total += groupGap;
        for (String t : sizes) total += textPaint.measureText(t) + chipPad * 2 + gap;
        float x = (w - total) / 2;

        for (int i = 0; i < styles.length; i++) x = drawChip(canvas, d, x, y, chipH, chipPad, styles[i], 0, i, selStyle == i) + gap;
        x += groupGap - gap;
        for (int i = 0; i < sizes.length; i++) x = drawChip(canvas, d, x, y, chipH, chipPad, sizes[i], 1, i, selSize == i) + gap;
    }

    private float drawChip(Canvas canvas, float d, float x, float y, float chipH, float chipPad,
                           String label, int kind, int value, boolean selected) {
        textPaint.setTextSize(13 * d);
        float tw = textPaint.measureText(label);
        float chipW = tw + chipPad * 2;
        RectF r = new RectF(x, y, x + chipW, y + chipH);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(selected ? 0x33FFFFFF : 0x14FFFFFF);
        canvas.drawRoundRect(r, chipH / 2, chipH / 2, bg);
        if (selected) {
            bg.setStyle(Paint.Style.STROKE);
            bg.setStrokeWidth(1.5f * d);
            bg.setColor(0xAAFFFFFF);
            canvas.drawRoundRect(r, chipH / 2, chipH / 2, bg);
        }
        textPaint.setColor(selected ? 0xFFFFFFFF : 0x99FFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, r.centerX(), r.centerY() + 5 * d, textPaint);
        Chip c = new Chip(); c.r = r; c.kind = kind; c.value = value; chips.add(c);
        return x + chipW;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float px = event.getX(), py = event.getY();
            for (Chip c : chips) {
                if (c.r != null && c.r.contains(px, py)) {
                    if (c.kind == 0) selStyle = c.value; else selSize = c.value;
                    if (listener != null) listener.onStyleChange(selStyle, selSize);
                    invalidate();
                    return true;
                }
            }
            if (listener != null) listener.onClose();
            return true;
        }
        return true;
    }

    private void drawToolColumn(Canvas canvas, float d, float left, float top, float colW, float colH,
                                boolean stale, BalanceData.Tool tool, String name, boolean cool) {
        int c5 = BalanceDrawing.windowColor(cool, 0);
        // 1) 双环（列宽的一部分）
        float ring = Math.min(colH * 0.42f, 170 * d);
        float ringLeft = left + (colW - ring) / 2;
        BalanceDrawing.drawGroup(canvas, ringPaint, textPaint, ringLeft, top, ring, stale,
                tool, name, cool);

        float y = top + ring + ring * 0.16f + 18 * d;

        // 2) 消耗看板：今日 / 近7天 / 本月 三张卡
        if (tool.hasTokens()) {
            float cardGap = 8 * d;
            float cardW = (colW - cardGap * 2) / 3;
            float cardH = 52 * d;
            String[] labels = {"今日", "近7天", "本月"};
            long[] vals = {tool.today, tool.week, tool.month};
            for (int i = 0; i < 3; i++) {
                float cx = left + i * (cardW + cardGap);
                RectF card = new RectF(cx, y, cx + cardW, y + cardH);
                canvas.drawRoundRect(card, 8 * d, 8 * d, cardPaint);
                textPaint.setTextAlign(Paint.Align.LEFT);
                textPaint.setColor(0x99FFFFFF);
                textPaint.setTextSize(11 * d);
                canvas.drawText(labels[i], cx + 8 * d, y + 18 * d, textPaint);
                textPaint.setColor(stale ? BalanceDrawing.GRAY : (i == 0 ? c5 : 0xFFEDEDED));
                textPaint.setTextSize(18 * d);
                canvas.drawText(BalanceDrawing.compactTokens(vals[i]), cx + 8 * d, y + 40 * d, textPaint);
            }
            y += cardH + 18 * d;

            // 3) 14 天趋势柱状图
            y = drawTrend(canvas, d, left, y, colW, tool.trend, c5, stale);

            // 4) 今日项目 Top
            if (!tool.projects.isEmpty()) {
                y += 8 * d;
                textPaint.setTextAlign(Paint.Align.LEFT);
                textPaint.setColor(0x99FFFFFF);
                textPaint.setTextSize(12 * d);
                canvas.drawText("今日项目 Top", left, y, textPaint);
                y += 18 * d;
                for (BalanceData.NamedValue nv : tool.projects) {
                    textPaint.setColor(0xFFDDDDDD);
                    textPaint.setTextSize(13 * d);
                    textPaint.setTextAlign(Paint.Align.LEFT);
                    String nm = nv.name.length() > 16 ? nv.name.substring(0, 16) + "…" : nv.name;
                    canvas.drawText(nm, left, y, textPaint);
                    textPaint.setColor(c5);
                    textPaint.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText(BalanceDrawing.compactTokens(nv.tokens), left + colW, y, textPaint);
                    y += 20 * d;
                }
            }
        } else {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(BalanceDrawing.GRAY);
            textPaint.setTextSize(13 * d);
            canvas.drawText("Token 统计待同步", left + colW / 2, y + 20 * d, textPaint);
        }
    }

    /** 14 天趋势柱状图，返回绘制后的 y */
    private float drawTrend(Canvas canvas, float d, float left, float y, float colW,
                            List<BalanceData.TrendPoint> trend, int color, boolean stale) {
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(0x99FFFFFF);
        textPaint.setTextSize(12 * d);
        canvas.drawText("最近 14 天", left, y, textPaint);
        y += 10 * d;

        float chartH = 78 * d;
        float chartTop = y;
        float chartBottom = y + chartH;
        if (trend == null || trend.isEmpty()) {
            return chartBottom + 8 * d;
        }
        long max = 1;
        for (BalanceData.TrendPoint p : trend) max = Math.max(max, p.tokens);

        int n = trend.size();
        float slot = colW / n;
        float barW = slot * 0.6f;
        barPaint.setColor(stale ? BalanceDrawing.GRAY : color);
        for (int i = 0; i < n; i++) {
            BalanceData.TrendPoint p = trend.get(i);
            float bh = (float) p.tokens / max * chartH;
            if (bh < 2 && p.tokens > 0) bh = 2;
            float bx = left + i * slot + (slot - barW) / 2;
            RectF bar = new RectF(bx, chartBottom - bh, bx + barW, chartBottom);
            canvas.drawRoundRect(bar, 2 * d, 2 * d, barPaint);
        }
        // 首/中/末日期标注
        textPaint.setColor(0x77FFFFFF);
        textPaint.setTextSize(9 * d);
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(trend.get(0).label, left, chartBottom + 14 * d, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(trend.get(n - 1).label, left + colW, chartBottom + 14 * d, textPaint);
        // 峰值标注
        textPaint.setColor(color);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(10 * d);
        canvas.drawText("峰值 " + BalanceDrawing.compactTokens(max),
                left + colW / 2, chartTop - 2 * d, textPaint);

        return chartBottom + 18 * d;
    }
}
