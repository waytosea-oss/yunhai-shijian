package io.github.waytosea.yunhaishijian;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

/** 单击诗签后展开的多帧气象判读与字体设置页。 */
public final class PoemAnalysisPanelView extends View {
    interface FontSelectionListener { void onFontSelected(int style); }

    private final float density;
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint poemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint controlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint controlTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap sealBitmap;
    private final RectF xingButton = new RectF();
    private final RectF kaiButton = new RectF();
    private final RectF closeButton = new RectF();

    private FontSelectionListener fontSelectionListener;
    private int selectedFont = PoemOverlayView.FONT_XING;
    private String title = "云图诗鉴";
    private String date = "";
    private String phenomenon = "等待气象分析";
    private String trend = "";
    private String localImpact = "";
    private String summary = "";
    private String worldEcho = "";
    private String confidence = "";
    private String evidence = "连续多帧云图";
    private final String[] lines = {"云图正在判", "读完成后更", "新今日诗句", "请稍候片刻"};

    public PoemAnalysisPanelView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;

        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(sp(30));
        titlePaint.setTypeface(Typeface.create("sans", Typeface.BOLD));

        subtitlePaint.setColor(0xFF9CA5A8);
        subtitlePaint.setTextSize(sp(13));

        labelPaint.setColor(0xFFE0A05C);
        labelPaint.setTextSize(sp(12));
        labelPaint.setTypeface(Typeface.create("sans", Typeface.BOLD));

        bodyPaint.setColor(0xFFF3F1EB);
        bodyPaint.setTextSize(sp(18));

        poemPaint.setColor(0xFFF2E7D3);
        poemPaint.setTextSize(sp(27));
        poemPaint.setTextAlign(Paint.Align.CENTER);

        dividerPaint.setColor(0x4CFFFFFF);
        dividerPaint.setStrokeWidth(dp(1));

        controlTextPaint.setTextSize(sp(14));
        controlTextPaint.setTextAlign(Paint.Align.CENTER);

        sealBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.poem_seal);

        setClickable(true);
        setVisibility(GONE);
    }

    void setFontSelectionListener(FontSelectionListener listener) {
        fontSelectionListener = listener;
    }

    void setSelectedFont(int style) {
        selectedFont = style == PoemOverlayView.FONT_KAI
                ? PoemOverlayView.FONT_KAI : PoemOverlayView.FONT_XING;
        poemPaint.setTypeface(loadTypeface(selectedFont));
        invalidate();
    }

    void setPoemJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            title = root.optString("title", "云图诗鉴");
            date = root.optString("poemDateShanghai", "").replace('-', '.');
            JSONArray poemLines = root.getJSONArray("lines");
            for (int index = 0; index < lines.length; index++) {
                lines[index] = poemLines.getString(index);
            }

            JSONObject analysis = root.optJSONObject("analysis");
            if (analysis != null) {
                phenomenon = analysis.optString("phenomenon", phenomenon);
                trend = analysis.optString("trend", trend);
                localImpact = analysis.optString("localImpact", localImpact);
                summary = analysis.optString("summary", summary);
                worldEcho = analysis.optString("worldEcho", worldEcho);
                confidence = analysis.optString("confidence", confidence);
            }

            JSONObject sourceEvidence = root.optJSONObject("evidence");
            if (sourceEvidence != null) {
                JSONArray frameTimes = sourceEvidence.optJSONArray("frameTimes");
                JSONObject typhoon = sourceEvidence.optJSONObject("typhoon");
                StringBuilder text = new StringBuilder();
                if (frameTimes != null && frameTimes.length() > 0) {
                    text.append("连续").append(frameTimes.length()).append("帧云图");
                    text.append(" · ").append(frameTimes.optString(0));
                    text.append("至").append(frameTimes.optString(frameTimes.length() - 1));
                }
                if (typhoon != null) {
                    text.append(" · ").append(typhoon.optString("source", "权威台风快讯"));
                }
                if (text.length() > 0) {
                    evidence = text.toString();
                }
            }
            invalidate();
        } catch (Exception ignored) {
            // Keep the most recent valid analysis on malformed updates.
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xF0111517);
        float width = getWidth();
        float height = getHeight();
        float left = dp(58);
        float analysisWidth = Math.min(dp(600), width * 0.59f);
        float dividerX = width * 0.69f;

        canvas.drawText("云图气象判读", left, dp(58), titlePaint);
        String subtitle = date + "  ·  AI 多帧视觉分析";
        canvas.drawText(subtitle, left, dp(84), subtitlePaint);

        drawSection(canvas, "主要天气现象  ·  置信度 " + confidence,
                phenomenon, left, dp(125), analysisWidth, 2);
        drawSection(canvas, "移动与发展趋势",
                trend, left, dp(210), analysisWidth, 2);
        drawSection(canvas, "对上海与长江口的影响",
                localImpact, left, dp(305), analysisWidth, 2);
        drawSection(canvas, "多帧判读依据",
                summary, left, dp(400), analysisWidth, 2);
        drawSection(canvas, "国际风云映照",
                worldEcho, left, dp(495), analysisWidth, 2);
        canvas.drawText(evidence, left, height - dp(24), subtitlePaint);

        canvas.drawLine(dividerX, dp(45), dividerX, height - dp(36), dividerPaint);
        drawPoem(canvas, dividerX, width);
        drawFontControls(canvas, dividerX, width, height);
        drawClose(canvas, width);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP) {
            return true;
        }
        float x = event.getX();
        float y = event.getY();
        if (xingButton.contains(x, y)) {
            selectFont(PoemOverlayView.FONT_XING);
            return true;
        }
        if (kaiButton.contains(x, y)) {
            selectFont(PoemOverlayView.FONT_KAI);
            return true;
        }
        if (closeButton.contains(x, y)) {
            setVisibility(GONE);
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void selectFont(int style) {
        setSelectedFont(style);
        if (fontSelectionListener != null) {
            fontSelectionListener.onFontSelected(style);
        }
    }

    private void drawSection(Canvas canvas, String label, String value, float x, float y,
                             float maxWidth, int maxLines) {
        canvas.drawText(label, x, y, labelPaint);
        drawWrappedText(canvas, value, x, y + dp(29), maxWidth, bodyPaint, dp(27), maxLines);
    }

    private void drawPoem(Canvas canvas, float dividerX, float width) {
        float areaCenter = (dividerX + width) / 2f;
        titlePaint.setTextSize(sp(20));
        titlePaint.setTypeface(loadTypeface(selectedFont));
        titlePaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(title, areaCenter, dp(60), titlePaint);
        titlePaint.setTextSize(sp(30));
        titlePaint.setTypeface(Typeface.create("sans", Typeface.BOLD));
        titlePaint.setTextAlign(Paint.Align.LEFT);

        float step = dp(25);
        float lineGap = dp(8);
        float rightX = areaCenter + dp(31);
        float leftX = areaCenter - dp(31);
        float firstTop = dp(88);
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
    }

    private void drawVerticalLine(Canvas canvas, String line, float x, float top) {
        Paint.FontMetrics metrics = poemPaint.getFontMetrics();
        float step = dp(25);
        for (int index = 0; index < line.length(); index++) {
            float centerY = top + step * index + step / 2f;
            float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(String.valueOf(line.charAt(index)), x, baseline, poemPaint);
        }
    }

    private void drawSeal(Canvas canvas, float centerX, float top, float size) {
        if (sealBitmap == null) {
            return;
        }
        float left = centerX - size / 2f;
        canvas.drawBitmap(sealBitmap, null, new RectF(left, top, left + size, top + size), null);
    }

    private void drawFontControls(Canvas canvas, float dividerX, float width, float height) {
        float left = dividerX + dp(24);
        float right = width - dp(24);
        float gap = dp(8);
        float buttonWidth = (right - left - gap) / 2f;
        float top = height - dp(66);
        float bottom = height - dp(24);
        xingButton.set(left, top, left + buttonWidth, bottom);
        kaiButton.set(left + buttonWidth + gap, top, right, bottom);

        canvas.drawText("诗签字体", left, top - dp(10), subtitlePaint);
        drawFontButton(canvas, xingButton, "志莽行书", PoemOverlayView.FONT_XING);
        drawFontButton(canvas, kaiButton, "马善政楷书", PoemOverlayView.FONT_KAI);
    }

    private void drawFontButton(Canvas canvas, RectF rect, String label, int style) {
        boolean selected = selectedFont == style;
        controlPaint.setColor(selected ? 0xFFE6D4B7 : 0x332E3639);
        canvas.drawRoundRect(rect, dp(4), dp(4), controlPaint);
        controlTextPaint.setColor(selected ? 0xFF1D211F : 0xFFE8ECEB);
        controlTextPaint.setTypeface(loadTypeface(style));
        Paint.FontMetrics metrics = controlTextPaint.getFontMetrics();
        float baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(label, rect.centerX(), baseline, controlTextPaint);
    }

    private void drawClose(Canvas canvas, float width) {
        float centerX = width - dp(35);
        float centerY = dp(34);
        closeButton.set(centerX - dp(22), centerY - dp(22),
                centerX + dp(22), centerY + dp(22));
        dividerPaint.setStrokeWidth(dp(2));
        canvas.drawLine(centerX - dp(7), centerY - dp(7),
                centerX + dp(7), centerY + dp(7), dividerPaint);
        canvas.drawLine(centerX + dp(7), centerY - dp(7),
                centerX - dp(7), centerY + dp(7), dividerPaint);
        dividerPaint.setStrokeWidth(dp(1));
    }

    private float drawWrappedText(Canvas canvas, String value, float x, float y,
                                  float maxWidth, Paint paint, float lineHeight, int maxLines) {
        String text = value == null || value.isEmpty() ? "暂无" : value;
        StringBuilder line = new StringBuilder();
        int lineCount = 0;
        for (int index = 0; index < text.length() && lineCount < maxLines; index++) {
            char character = text.charAt(index);
            String candidate = line.toString() + character;
            if (paint.measureText(candidate) > maxWidth && line.length() > 0) {
                canvas.drawText(line.toString(), x, y + lineHeight * lineCount, paint);
                lineCount += 1;
                line.setLength(0);
            }
            if (lineCount < maxLines) {
                line.append(character);
            }
        }
        if (line.length() > 0 && lineCount < maxLines) {
            canvas.drawText(line.toString(), x, y + lineHeight * lineCount, paint);
            lineCount += 1;
        }
        return y + lineHeight * lineCount;
    }

    private Typeface loadTypeface(int style) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return getResources().getFont(style == PoemOverlayView.FONT_KAI
                    ? R.font.ma_shan_zheng_regular
                    : R.font.zhi_mang_xing_regular);
        }
        return Typeface.create("serif", Typeface.NORMAL);
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
