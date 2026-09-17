package com.litukang.wordsnap.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.litukang.wordsnap.solver.Answer;
import com.litukang.wordsnap.util.Ui;

/**
 * 答题结果悬浮卡：贴在屏幕顶部，显示推荐答案和置信度。
 * 没有悬浮窗权限时返回 false，调用方要自己退化成通知。
 */
public final class QuizOverlay {

    private static final long AUTO_HIDE_MS = 20000;

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static WindowManager wm;
    private static View card;
    private static final Runnable HIDE = QuizOverlay::hide;

    private QuizOverlay() {
    }

    public static synchronized boolean show(Context c, Answer a) {
        if (c == null || a == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(c)) {
            return false;
        }
        hide();

        Context app = c.getApplicationContext();
        LinearLayout root = new LinearLayout(app);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.card(app, Color.parseColor("#FFFFFF"), Color.parseColor("#1565C0")));
        int pad = Ui.dp(app, 12);
        root.setPadding(pad, pad, pad, pad);

        TextView head = Ui.text(app, "识屏答题", 12f, Ui.TEXT_SUB, false);
        root.addView(head);

        String main;
        int color;
        if (a.hasAnswer()) {
            main = a.bestLabel() + ". " + a.bestText();
            color = a.confidence >= 0.35 ? Ui.GREEN : Color.parseColor("#B26A00");
        } else {
            main = "没答出来";
            color = Ui.RED;
        }
        TextView answer = Ui.text(app, main, 19f, color, true);
        answer.setPadding(0, Ui.dp(app, 4), 0, 0);
        root.addView(answer);

        TextView stem = Ui.text(app, "题干：" + a.question.stem, 12f, Ui.TEXT_SUB, false);
        stem.setPadding(0, Ui.dp(app, 4), 0, 0);
        root.addView(stem);

        TextView meta = Ui.text(app,
                "置信度 " + String.format(java.util.Locale.US, "%.2f", a.confidence)
                        + "  ·  耗时 " + a.costMs + " ms  ·  点一下关闭",
                11f, Ui.TEXT_SUB, false);
        meta.setPadding(0, Ui.dp(app, 4), 0, 0);
        root.addView(meta);

        root.setOnClickListener(v -> hide());

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = Ui.dp(app, 56);

        try {
            wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            wm.addView(root, lp);
            card = root;
        } catch (Exception e) {
            return false;
        }
        H.removeCallbacks(HIDE);
        H.postDelayed(HIDE, AUTO_HIDE_MS);
        return true;
    }

    public static synchronized void hide() {
        H.removeCallbacks(HIDE);
        if (card != null && wm != null) {
            try {
                wm.removeView(card);
            } catch (Exception ignored) {
            }
        }
        card = null;
        wm = null;
    }
}
