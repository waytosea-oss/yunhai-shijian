package io.github.waytosea.yunhaishijian;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CloudFrameController {
    private static final long REFRESH_MS = 60L * 60L * 1000L;
    private static final long RETRY_MS = 5L * 60L * 1000L;

    private final Context context;
    private final ImageView imageView;
    private final String host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchCurrent();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private boolean stopped;

    CloudFrameController(Context context, ImageView imageView, String host) {
        this.context = context.getApplicationContext();
        this.imageView = imageView;
        this.host = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        this.imageView.setBackgroundColor(0xff000000);
        this.imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        this.imageView.setAdjustViewBounds(false);
    }

    void start() {
        stopped = false;
        loadCached();
        fetchCurrent();
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, REFRESH_MS);
    }

    void stop() {
        stopped = true;
        handler.removeCallbacks(refreshRunnable);
    }

    void destroy() {
        stop();
        executor.shutdownNow();
    }

    private void fetchCurrent() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] bytes = downloadBytes(imageUrl());
                    saveBytes(cacheFile(), bytes);
                    final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!stopped) {
                                    imageView.setImageBitmap(bitmap);
                                }
                            }
                        });
                    }
                } catch (IOException ignored) {
                    loadCached();
                    scheduleRetry();
                }
            }
        });
    }

    private void scheduleRetry() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!stopped) {
                    fetchCurrent();
                }
            }
        }, RETRY_MS);
    }

    private void loadCached() {
        final File file = cacheFile();
        if (!file.exists()) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap == null) {
                    return;
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!stopped) {
                            imageView.setImageBitmap(bitmap);
                        }
                    }
                });
            }
        });
    }

    private String imageUrl() {
        return host + "/image.jpg?product=fy4b-true-color&region=global&reload=" + System.currentTimeMillis();
    }

    private File cacheFile() {
        return new File(context.getFilesDir(), "national.jpg");
    }

    private static byte[] downloadBytes(String urlString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(20000);
        connection.setUseCaches(false);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            InputStream input = connection.getInputStream();
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            } finally {
                input.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void saveBytes(File file, byte[] bytes) throws IOException {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
    }

    static void applyImmersive(View view) {
        view.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
