package io.github.waytosea.yunhaishijian;

import android.service.dreams.DreamService;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

public final class CloudDreamService extends DreamService {
    private CloudFrameController controller;
    private ImageView imageView;
    private BalanceOverlayView balanceOverlay;
    private PoemOverlayView poemOverlay;
    private PoemAnalysisPanelView poemAnalysisPanel;
    private BalancePanelView balancePanel;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        imageView = new ImageView(this);
        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.addView(imageView);
        String host = getString(R.string.server_url);
        balanceOverlay = new BalanceOverlayView(this, host);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        float density = getResources().getDisplayMetrics().density;
        overlayParams.leftMargin = (int) (16 * density);
        overlayParams.topMargin = (int) (16 * density);
        rootLayout.addView(balanceOverlay, overlayParams);

        poemOverlay = new PoemOverlayView(this, host);
        rootLayout.addView(poemOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        poemAnalysisPanel = new PoemAnalysisPanelView(this);
        rootLayout.addView(poemAnalysisPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        poemOverlay.setDataListener(new PoemOverlayView.DataListener() {
            @Override
            public void onData(String json) {
                poemAnalysisPanel.setPoemJson(json);
            }
        });
        poemOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                poemAnalysisPanel.setSelectedFont(poemOverlay.getFontStyle());
                poemAnalysisPanel.setVisibility(View.VISIBLE);
                poemAnalysisPanel.invalidate();
            }
        });
        poemAnalysisPanel.setFontSelectionListener(new PoemAnalysisPanelView.FontSelectionListener() {
            @Override
            public void onFontSelected(int style) {
                poemOverlay.setFontStyle(style);
            }
        });

        balancePanel = new BalancePanelView(this);
        rootLayout.addView(balancePanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        balanceOverlay.setDataListener(new BalanceOverlayView.DataListener() {
            @Override
            public void onData(BalanceData data) {
                balancePanel.applyData(data);
            }
        });
        balanceOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                balancePanel.setSelection(balanceOverlay.getStyle(), balanceOverlay.getSizeMode());
                balanceOverlay.setVisibility(View.INVISIBLE);
                balancePanel.setVisibility(View.VISIBLE);
                balancePanel.invalidate();
            }
        });
        balancePanel.setListener(new BalancePanelView.Listener() {
            @Override
            public void onClose() {
                balancePanel.setVisibility(View.GONE);
                balanceOverlay.setVisibility(View.VISIBLE);
            }
            @Override
            public void onStyleChange(int style, int size) {
                balanceOverlay.setStyleAndSize(style, size);
            }
        });
        setContentView(rootLayout);

        controller = new CloudFrameController(this, imageView, host);
        CloudFrameController.applyImmersive(imageView);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (controller != null) {
            controller.start();
        }
        if (balanceOverlay != null) {
            balanceOverlay.start();
        }
        if (poemOverlay != null) {
            poemOverlay.start();
        }
    }

    @Override
    public void onDreamingStopped() {
        if (balanceOverlay != null) {
            balanceOverlay.stop();
        }
        if (poemOverlay != null) {
            poemOverlay.stop();
        }
        if (controller != null) {
            controller.stop();
        }
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (balanceOverlay != null) {
            balanceOverlay.destroy();
        }
        if (poemOverlay != null) {
            poemOverlay.destroy();
        }
        if (controller != null) {
            controller.destroy();
        }
        super.onDetachedFromWindow();
    }
}
