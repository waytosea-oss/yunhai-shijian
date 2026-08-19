package io.github.waytosea.yunhaishijian;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 每日云图五言诗签。可拖动，位置与最近一次诗句会跨屏保会话保存。 */
public final class PoemOverlayView extends View {
    private static final long POLL_MS = 60L * 60L * 1000L;
    private static final String PREFS = "cloud_poem_overlay";
    private static final String KEY_JSON = "last_poem_json";
    private static final String KEY_X = "position_x";
    private static final String KEY_Y = "position_y";
    private static final String KEY_FONT = "font_style";
    public static final int FONT_XING = 0;
    public static final int FONT_KAI = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SharedPreferences preferences;
    private final String poemUrl;
    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint poemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sealPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap sealBitmap;
    private final int touchSlop;
    private final float density;
    interface DataListener { void onData(String json); }
    private DataListener dataListener;
    private int fontStyle;

    private final String[] lines = new String[4];
    private boolean stopped = true;
    private boolean dragging;
    private float downRawX;
    private float downRawY;
    private float downViewX;
    private float downViewY;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            fetchPoem();
            handler.postDelayed(this, POLL_MS);
        }
    };

    public PoemOverlayView(Context context, String host) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        poemUrl = host + "/cloud-poem.json?product=fy4b-true-color";
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        panelPaint.setColor(0xB8F3EBDD);

        fontStyle = preferences.getInt(KEY_FONT, FONT_XING);
        Typeface poemTypeface = loadPoemTypeface(fontStyle);
        poemPaint.setTypeface(poemTypeface);
        poemPaint.setTextSize(sp(25));
        poemPaint.setColor(0xFF1E1B18);
        poemPaint.setTextAlign(Paint.Align.CENTER);

        sealBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.poem_seal);
        sealPaint.setColor(0xFFD05242);

        setClickable(true);
        setVisibility(INVISIBLE);
        loadCachedPoem();
        post(new Runnable() {
            @Override
            public void run() {
                restorePosition();
            }
        });
    }

    public void start() {
        stopped = false;
        handler.removeCallbacks(pollRunnable);
        handler.post(pollRunnable);
    }

    public void stop() {
        stopped = true;
        handler.removeCallbacks(pollRunnable);
    }

    public void destroy() {
        stop();
        executor.shutdownNow();
    }

    void setDataListener(DataListener listener) {
        dataListener = listener;
        String cached = preferences.getString(KEY_JSON, "");
        if (listener != null && !cached.isEmpty()) {
            listener.onData(cached);
        }
    }

    public int getFontStyle() {
        return fontStyle;
    }

    public void setFontStyle(int style) {
        fontStyle = style == FONT_KAI ? FONT_KAI : FONT_XING;
        poemPaint.setTypeface(loadPoemTypeface(fontStyle));
        preferences.edit().putInt(KEY_FONT, fontStyle).apply();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize((int) dp(128), widthMeasureSpec),
                resolveSize((int) dp(352), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        RectF panel = new RectF(dp(1), dp(1), width - dp(1), height - dp(1));
        canvas.drawRoundRect(panel, dp(4), dp(4), panelPaint);

        float firstTop = dp(18);
        float step = dp(25.5f);
        float lineGap = dp(6);
        float rightX = width - dp(42);
        float leftX = dp(42);
        float rightSecondTop = firstTop + step * 5 + lineGap;
        float rightThirdTop = rightSecondTop + step * 5 + lineGap;
        float leftTop = firstTop;
        float leftFourthTop = leftTop + step * 3 + lineGap;
        float stampTop = leftFourthTop + step * 5 + step;

        drawVerticalLine(canvas, lines[0], rightX, firstTop);
        drawVerticalLine(canvas, lines[1], rightX, rightSecondTop);
        drawVerticalLine(canvas, lines[2].substring(0, 2), rightX, rightThirdTop);
        drawVerticalLine(canvas, lines[2].substring(2), leftX, leftTop);
        drawVerticalLine(canvas, lines[3], leftX, leftFourthTop);
        drawSeal(canvas, leftX, stampTop, step * 1.7f);

        float gripY = dp(12);
        sealPaint.setColor(0x7042382E);
        canvas.drawCircle(dp(8), gripY - dp(3), dp(1.2f), sealPaint);
        canvas.drawCircle(dp(8), gripY, dp(1.2f), sealPaint);
        canvas.drawCircle(dp(8), gripY + dp(3), dp(1.2f), sealPaint);
        sealPaint.setColor(0xFFD05242);
    }

    private void drawVerticalLine(Canvas canvas, String line, float centerX, float top) {
        Paint.FontMetrics metrics = poemPaint.getFontMetrics();
        float step = dp(25.5f);
        for (int index = 0; index < line.length(); index++) {
            float centerY = top + step * index + step / 2f;
            float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(String.valueOf(line.charAt(index)), centerX, baseline, poemPaint);
        }
    }

    private void drawSeal(Canvas canvas, float centerX, float top, float size) {
        if (sealBitmap == null) {
            return;
        }
        float left = centerX - size / 2f;
        canvas.drawBitmap(sealBitmap, null, new RectF(left, top, left + size, top + size), null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downViewX = getX();
                downViewY = getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getRawX() - downRawX;
                float deltaY = event.getRawY() - downRawY;
                if (!dragging && Math.hypot(deltaX, deltaY) > touchSlop) {
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    moveWithinParent(downViewX + deltaX, downViewY + deltaY);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    savePosition();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    performClick();
                }
                dragging = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void fetchPoem() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(poemUrl).openConnection();
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(450000);
                    connection.setUseCaches(false);
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) {
                        return;
                    }
                    InputStream input = connection.getInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    input.close();
                    final String json = output.toString("UTF-8");
                    final JSONObject root = new JSONObject(json);
                    if (!isValidPoem(root)) {
                        return;
                    }
                    preferences.edit().putString(KEY_JSON, json).apply();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!stopped) {
                                applyPoem(root);
                            }
                        }
                    });
                } catch (Exception ignored) {
                    // Keep the last valid daily poem when the Mac or network is unavailable.
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        });
    }

    private void loadCachedPoem() {
        String cached = preferences.getString(KEY_JSON, "");
        if (cached.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(cached);
            if (isValidPoem(root)) {
                applyPoem(root);
            }
        } catch (Exception ignored) {
            // Ignore an invalid cache and wait for the next server response.
        }
    }

    private static boolean isValidPoem(JSONObject root) {
        try {
            JSONArray poemLines = root.getJSONArray("lines");
            if (poemLines.length() != 4) {
                return false;
            }
            for (int index = 0; index < poemLines.length(); index++) {
                if (poemLines.getString(index).length() != 5) {
                    return false;
                }
            }
            String poemTitle = root.optString("title", "");
            return poemTitle.length() >= 2 && poemTitle.length() <= 6;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void applyPoem(JSONObject root) {
        try {
            JSONArray poemLines = root.getJSONArray("lines");
            for (int index = 0; index < lines.length; index++) {
                lines[index] = poemLines.getString(index);
            }
            setVisibility(VISIBLE);
            if (dataListener != null) {
                dataListener.onData(root.toString());
            }
            invalidate();
        } catch (Exception ignored) {
            // Validation happens before this method.
        }
    }

    private void restorePosition() {
        ViewGroup parent = parentView();
        if (parent == null || parent.getWidth() == 0 || parent.getHeight() == 0) {
            return;
        }
        float maxX = Math.max(0, parent.getWidth() - getWidth());
        float maxY = Math.max(0, parent.getHeight() - getHeight());
        float savedX = preferences.getFloat(KEY_X, -1f);
        float savedY = preferences.getFloat(KEY_Y, -1f);
        float x = savedX >= 0 ? savedX * maxX : maxX - dp(16);
        float y = savedY >= 0 ? savedY * maxY : dp(16);
        moveWithinParent(x, y);
    }

    private void moveWithinParent(float x, float y) {
        ViewGroup parent = parentView();
        if (parent == null) {
            return;
        }
        float maxX = Math.max(0, parent.getWidth() - getWidth());
        float maxY = Math.max(0, parent.getHeight() - getHeight());
        setX(Math.max(0, Math.min(maxX, x)));
        setY(Math.max(0, Math.min(maxY, y)));
    }

    private void savePosition() {
        ViewGroup parent = parentView();
        if (parent == null) {
            return;
        }
        float maxX = Math.max(1, parent.getWidth() - getWidth());
        float maxY = Math.max(1, parent.getHeight() - getHeight());
        preferences.edit()
                .putFloat(KEY_X, getX() / maxX)
                .putFloat(KEY_Y, getY() / maxY)
                .apply();
    }

    private ViewGroup parentView() {
        return getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private Typeface loadPoemTypeface(int style) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return getResources().getFont(style == FONT_KAI
                    ? R.font.ma_shan_zheng_regular
                    : R.font.zhi_mang_xing_regular);
        }
        return Typeface.create("serif", Typeface.NORMAL);
    }
}
