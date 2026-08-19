package io.github.waytosea.yunhaishijian;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

public final class MainActivity extends Activity {
    private CloudFrameController controller;
    private ImageView imageView;
    private BalanceOverlayView balanceOverlay;
    private PoemOverlayView poemOverlay;
    private PoemAnalysisPanelView poemAnalysisPanel;
    private BalancePanelView balancePanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        CloudFrameController.applyImmersive(imageView);
        controller.start();
        balanceOverlay.start();
        poemOverlay.start();
    }

    @Override
    protected void onPause() {
        balanceOverlay.stop();
        poemOverlay.stop();
        controller.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        balanceOverlay.destroy();
        poemOverlay.destroy();
        controller.destroy();
        super.onDestroy();
    }
}
