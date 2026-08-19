package io.github.waytosea.yunhaishijian;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 算力码表叠加层：左上角两组同心双环（左 Codex 暖色 / 右 Claude 冷色），
 * 外环 7 天、内环 5 小时，中间数字取更紧张者。数据来自 Mac 上的 /balance.json，
 * 每 60 秒轮询；超过 15 分钟没有新数据整体置灰。
 */
public final class BalanceOverlayView extends View {
    private static final long POLL_MS = 60L * 1000L;
    private static final long STALE_MS = 15L * 60L * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String balanceUrl;

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 样式：0=双环 1=长条 2=徽章；尺寸：0=标准 1=迷你
    static final int STYLE_RINGS = 0, STYLE_BARS = 1, STYLE_BADGE = 2;
    static final int SIZE_STANDARD = 0, SIZE_MINI = 1;
    private static final String PREFS = "balance_widget";
    private int style = STYLE_RINGS;
    private int sizeMode = SIZE_STANDARD;

    private BalanceData data;
    interface DataListener { void onData(BalanceData data); }
    private DataListener dataListener;

    void setDataListener(DataListener listener) { this.dataListener = listener; }

    int getStyle() { return style; }
    int getSizeMode() { return sizeMode; }

    void setStyleAndSize(int style, int sizeMode) {
        this.style = style;
        this.sizeMode = sizeMode;
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putInt("style", style).putInt("size", sizeMode).apply();
        requestLayout();
        invalidate();
    }

    private void loadPrefs() {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        style = sp.getInt("style", STYLE_RINGS);
        sizeMode = sp.getInt("size", SIZE_STANDARD);
    }

    /** 每组双环边长 / 长条行宽 的基准尺寸(dp) */
    private float groupDp() { return sizeMode == SIZE_MINI ? 104f : 148f; }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            fetchBalance();
            handler.postDelayed(this, POLL_MS);
        }
    };

    public BalanceOverlayView(Context context, String host) {
        super(context);
        this.balanceUrl = host + "/balance.json";
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        panelPaint.setColor(0x8C000000);
        loadPrefs();
    }

    public void start() {
        handler.removeCallbacks(pollRunnable);
        handler.post(pollRunnable);
    }

    public void stop() {
        handler.removeCallbacks(pollRunnable);
    }

    public void destroy() {
        stop();
        executor.shutdownNow();
    }

    private void fetchBalance() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection connection =
                            (HttpURLConnection) new URL(balanceUrl).openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(8000);
                    connection.setUseCaches(false);
                    try {
                        InputStream input = connection.getInputStream();
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                        }
                        input.close();
                        final JSONObject root = new JSONObject(output.toString("UTF-8"));
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                applyData(root);
                            }
                        });
                    } finally {
                        connection.disconnect();
                    }
                } catch (Exception ignored) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            invalidate(); // 触发重绘以进入置灰判定
                        }
                    });
                }
            }
        });
    }

    private void applyData(JSONObject root) {
        data = BalanceData.parse(root);
        if (dataListener != null) {
            dataListener.onData(data);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float d = getResources().getDisplayMetrics().density;
        int width, height;
        if (style == STYLE_BADGE) {
            float h = (sizeMode == SIZE_MINI ? 58 : 74) * d;
            float w = (sizeMode == SIZE_MINI ? 150 : 190) * d;
            width = (int) Math.ceil(w); height = (int) Math.ceil(h);
        } else if (style == STYLE_BARS) {
            float pad = 12 * d;
            float rowW = (sizeMode == SIZE_MINI ? 190 : 240) * d;
            float rowH = (sizeMode == SIZE_MINI ? 38 : 48) * d;
            width = (int) Math.ceil(pad * 2 + rowW);
            height = (int) Math.ceil(pad * 2 + rowH * 2 + 6 * d);
        } else {
            float pad = 14 * d, group = groupDp() * d, gap = 20 * d, footer = group * 0.14f;
            width = (int) Math.ceil(pad * 2 + group * 2 + gap);
            height = (int) Math.ceil(pad * 2 + group + footer);
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean stale = data == null
                || System.currentTimeMillis() - data.fetchedAt > STALE_MS;
        float d = getResources().getDisplayMetrics().density;
        BalanceData.Tool codex = data != null ? data.codex : new BalanceData.Tool();
        BalanceData.Tool claude = data != null ? data.claude : new BalanceData.Tool();

        if (style == STYLE_BADGE) {
            drawBadge(canvas, d, stale, codex, claude);
        } else if (style == STYLE_BARS) {
            drawBars(canvas, d, stale, codex, claude);
        } else {
            drawRings(canvas, d, stale, codex, claude);
        }
    }

    private void drawRings(Canvas canvas, float d, boolean stale,
                           BalanceData.Tool codex, BalanceData.Tool claude) {
        float pad = 14 * d, group = groupDp() * d, gap = 20 * d, footer = group * 0.14f;
        textPaint.setTextAlign(Paint.Align.CENTER);
        boolean hasCodex = codex.hasBalance(), hasClaude = claude.hasBalance();
        int groups = (hasCodex ? 1 : 0) + (hasClaude ? 1 : 0);
        if (groups == 0) { groups = 2; hasCodex = hasClaude = true; }
        float panelW = pad * 2 + group * groups + (groups > 1 ? gap : 0);
        float panelH = pad * 2 + group + footer;
        canvas.drawRoundRect(new RectF(0, 0, panelW, panelH), 22 * d, 22 * d, panelPaint);
        float x = pad;
        if (hasCodex) {
            BalanceDrawing.drawGroup(canvas, ringPaint, textPaint, x, pad, group, stale,
                    codex, "Codex", false);
            x += group + gap;
        }
        if (hasClaude) {
            BalanceDrawing.drawGroup(canvas, ringPaint, textPaint, x, pad, group, stale,
                    claude, "Claude", true);
        }
    }

    private void drawBars(Canvas canvas, float d, boolean stale,
                          BalanceData.Tool codex, BalanceData.Tool claude) {
        boolean mini = sizeMode == SIZE_MINI;
        float pad = 12 * d;
        float rowW = (mini ? 190 : 240) * d;
        float rowH = (mini ? 38 : 48) * d;
        float rowGap = 6 * d;
        float panelW = pad * 2 + rowW;
        float panelH = pad * 2 + rowH * 2 + rowGap;
        canvas.drawRoundRect(new RectF(0, 0, panelW, panelH), 18 * d, 18 * d, panelPaint);
        BalanceDrawing.drawBarRow(canvas, ringPaint, textPaint, pad, pad, rowW, rowH, d, mini, stale,
                codex, "C", false);
        BalanceDrawing.drawBarRow(canvas, ringPaint, textPaint, pad, pad + rowH + rowGap, rowW, rowH, d, mini, stale,
                claude, "A", true);
    }

    private void drawBadge(Canvas canvas, float d, boolean stale,
                           BalanceData.Tool codex, BalanceData.Tool claude) {
        boolean mini = sizeMode == SIZE_MINI;
        float h = (mini ? 58 : 74) * d;
        float w = (mini ? 150 : 190) * d;
        canvas.drawRoundRect(new RectF(0, 0, w, h), 18 * d, 18 * d, panelPaint);
        float half = w / 2;
        BalanceDrawing.drawBadgeUnit(canvas, textPaint, 0, half, h, d, mini, stale,
                codex, "C", false);
        // 分隔竖线
        ringPaint.setStrokeWidth(1 * d);
        ringPaint.setColor(0x33FFFFFF);
        canvas.drawLine(half, h * 0.22f, half, h * 0.78f, ringPaint);
        BalanceDrawing.drawBadgeUnit(canvas, textPaint, half, half, h, d, mini, stale,
                claude, "A", true);
    }
}
