package com.litukang.wordsnap.capture;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.graphics.Rect;

import com.litukang.wordsnap.data.WordRepository;
import com.litukang.wordsnap.ocr.WordScanner;
import com.litukang.wordsnap.ui.FloatingResult;
import com.litukang.wordsnap.ui.ResultActivity;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 截屏识别服务：用户授权 MediaProjection 后，抓一帧 -> 端侧 OCR -> 本地查词 -> 展示。
 * 注意：本服务只"读屏"和"展示信息"，不做任何模拟点击。
 */
public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "code";
    public static final String EXTRA_DATA = "data";
    private static final String CHANNEL_ID = "wordsnap_capture";
    private static final int NOTIFY_ID = 1001;
    private static final int MAX_IMAGES = 2;

    private MediaProjectionManager mpm;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread handlerThread;
    private Handler handler;
    private WordScanner scanner;
    private volatile boolean consumed = false;

    public static void start(Context c, int resultCode, Intent data) {
        Intent i = new Intent(c, ScreenCaptureService.class);
        i.putExtra(EXTRA_RESULT_CODE, resultCode);
        i.putExtra(EXTRA_DATA, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            c.startForegroundService(i);
        } else {
            c.startService(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        scanner = new WordScanner();
        handlerThread = new HandlerThread("capture");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildNotification("正在准备截屏识别", "授权后仅读取一次屏幕").build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFY_ID, n);
        }

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        if (data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            projection = mpm.getMediaProjection(code, data);
        } catch (SecurityException e) {
            notifyError("授权已失效，请重新授权截屏");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (projection == null) {
            notifyError("无法获取截屏授权");
            stopSelf();
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                cleanup();
            }
        }, handler);

        startCapture();
        return START_NOT_STICKY;
    }

    private void startCapture() {
        // 服务可能被复用（连续截屏时 onStartCommand 会走同一实例），必须重置，否则第二帧被丢弃
        consumed = false;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Rect bounds;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bounds = wm.getCurrentWindowMetrics().getBounds();
        } else {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            bounds = new Rect(0, 0, dm.widthPixels, dm.heightPixels);
        }
        final int width = bounds.width();
        final int height = bounds.height();
        int dpi = getResources().getConfiguration().densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, MAX_IMAGES);
        imageReader.setOnImageAvailableListener(reader -> {
            if (consumed) {
                return;
            }
            consumed = true;
            Bitmap bmp = null;
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) {
                    return;
                }
                bmp = toBitmap(image);
            } catch (Exception e) {
                notifyError("截屏失败：" + e.getMessage());
            } finally {
                if (image != null) {
                    image.close();
                }
            }
            if (bmp != null) {
                // 原图用于回收，缩放图用于 OCR：vivo 这类 1.5K/2K 屏全屏跑 OCR 很慢
                final Bitmap full = bmp;
                final Bitmap forOcr = downscale(bmp, 1600);
                scanner.scan(forOcr, WordRepository.get(this), new WordScanner.Callback() {
                    @Override
                    public void onResult(List<WordScanner.Hit> hits, long costMs, String preview) {
                        showResult(hits, costMs, preview);
                        releaseBitmaps(full, forOcr);
                        finishAndStop();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        notifyError("识别失败：" + e.getMessage());
                        releaseBitmaps(full, forOcr);
                        finishAndStop();
                    }
                });
            } else {
                finishAndStop();
            }
        }, handler);

        try {
            virtualDisplay = projection.createVirtualDisplay("wordsnap",
                    width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, handler);
        } catch (Exception e) {
            notifyError("创建虚拟屏幕失败：" + e.getMessage());
            stopSelf();
        }
    }

    private static void releaseBitmaps(Bitmap full, Bitmap ocr) {
        if (ocr != null && ocr != full) {
            ocr.recycle();
        }
        if (full != null) {
            full.recycle();
        }
    }

    /** 把长边缩到 maxEdge 以内，OCR 速度能快一倍以上，对 16sp 以上的文字识别率影响很小 */
    private static Bitmap downscale(Bitmap src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        int max = Math.max(w, h);
        if (max <= maxEdge) {
            return src;
        }
        float ratio = maxEdge * 1.0f / max;
        int nw = Math.max(1, Math.round(w * ratio));
        int nh = Math.max(1, Math.round(h * ratio));
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private static Bitmap toBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();
        int w = image.getWidth() + (pixelStride > 0 ? rowPadding / pixelStride : 0);
        Bitmap bmp = Bitmap.createBitmap(w, image.getHeight(), Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buffer);
        if (w == image.getWidth()) {
            return bmp;
        }
        Bitmap cropped = Bitmap.createBitmap(bmp, 0, 0, image.getWidth(), image.getHeight());
        bmp.recycle();
        return cropped;
    }

    private void showResult(List<WordScanner.Hit> hits, long costMs, String preview) {
        if (Settings.canDrawOverlays(this)) {
            FloatingResult.show(this, hits, costMs);
            return;
        }
        // 没有悬浮窗权限时，退化为通知（点通知查看结果）
        String title;
        String text;
        if (hits.isEmpty()) {
            title = "没查到词";
            text = preview.isEmpty() ? "屏幕上没识别到文本" : preview;
        } else {
            title = hits.get(0).word + "  " + hits.get(0).meaning;
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < Math.min(hits.size(), 4); i++) {
                sb.append(hits.get(i).word).append(" ").append(hits.get(i).meaning).append("\n");
            }
            text = sb.toString().trim();
            if (text.isEmpty()) {
                text = "耗时 " + costMs + " ms";
            }
        }
        Intent i = ResultActivity.buildIntent(this, new ArrayList<>(hits), costMs, preview);
        PendingIntent pi = PendingIntent.getActivity(this, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = buildNotification(title, text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFY_ID + 1, n);
        }
    }

    private Notification.Builder buildNotification(String title, String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "截屏识词",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(ch);
            }
        }
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(false);
    }

    private void notifyError(String msg) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFY_ID + 2, buildNotification("词拍", msg).build());
        }
    }

    private void finishAndStop() {
        cleanup();
        stopSelf();
    }

    private void cleanup() {
        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
            }
        } catch (Exception ignored) {
        }
        virtualDisplay = null;
        try {
            if (imageReader != null) {
                imageReader.close();
            }
        } catch (Exception ignored) {
        }
        imageReader = null;
        try {
            if (projection != null) {
                projection.stop();
            }
        } catch (Exception ignored) {
        }
        projection = null;
    }

    @Override
    public void onDestroy() {
        cleanup();
        if (scanner != null) {
            scanner.close();
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        super.onDestroy();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}
