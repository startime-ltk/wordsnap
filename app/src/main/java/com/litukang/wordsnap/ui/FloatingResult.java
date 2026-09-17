package com.litukang.wordsnap.ui;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.litukang.wordsnap.data.WordBookStore;
import com.litukang.wordsnap.ocr.WordScanner;
import com.litukang.wordsnap.util.Ui;

import java.util.List;

/**
 * 悬浮窗结果卡片：识别完成后贴在屏幕顶部，几秒后自动消失。
 * 只展示信息，不拦截、不模拟任何点击。
 */
public final class FloatingResult {

    private static final long AUTO_HIDE_MS = 20000;
    private static View currentView;
    private static WindowManager windowManager;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Runnable hideTask = FloatingResult::hide;

    private FloatingResult() {
    }

    public static void show(Context context, List<WordScanner.Hit> hits, long costMs) {
        main.removeCallbacks(hideTask);
        main.post(() -> {
            hide();
            Context app = context.getApplicationContext();
            windowManager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) {
                return;
            }
            View v = buildView(app, hits, costMs);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            p.y = Ui.dp(app, 16);
            p.x = 0;
            p.windowAnimations = android.R.style.Animation_Dialog;
            try {
                windowManager.addView(v, p);
                currentView = v;
                main.postDelayed(hideTask, AUTO_HIDE_MS);
            } catch (Exception e) {
                Toast.makeText(app, "悬浮窗显示失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void hide() {
        if (currentView != null && windowManager != null) {
            try {
                windowManager.removeView(currentView);
            } catch (Exception ignored) {
            }
        }
        currentView = null;
    }

    private static View buildView(Context c, List<WordScanner.Hit> hits, long costMs) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.card(c, Ui.CARD_BG, Ui.CARD_STROKE));
        root.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        root.setElevation(Ui.dp(c, 6));

        TextView title = Ui.text(c, "识别完成 · " + costMs + " ms · " + hits.size() + " 词",
                14f, Ui.TEXT_SUB, true);
        root.addView(title);

        if (hits.isEmpty()) {
            TextView empty = Ui.text(c, "屏幕上没匹配到词库里的单词", 15f, Ui.TEXT_MAIN, false);
            empty.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
            root.addView(empty);
        } else {
            ScrollView sv = new ScrollView(c);
            LinearLayout.LayoutParams sp = Ui.lpMatch();
            sp.height = Ui.dp(c, 220);
            sp.setMargins(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
            sv.setLayoutParams(sp);

            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            for (WordScanner.Hit h : hits) {
                TextView tv = Ui.text(c, h.word + "  —  " + h.meaning, 16f, Ui.TEXT_MAIN, false);
                tv.setPadding(0, Ui.dp(c, 5), 0, Ui.dp(c, 5));
                box.addView(tv);
            }
            sv.addView(box);
            root.addView(sv);
        }

        LinearLayout actions = Ui.row(c, 0);
        Button add = Ui.button(c, "全部加入生词本", v -> {
            WordBookStore store = new WordBookStore(c);
            for (WordScanner.Hit h : hits) {
                store.add(h.word);
            }
            Toast.makeText(c, "已加入 " + hits.size() + " 个词", Toast.LENGTH_SHORT).show();
            hide();
        });
        LinearLayout.LayoutParams ap = Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        add.setLayoutParams(ap);

        Button close = Ui.button(c, "关闭", v -> hide());
        LinearLayout.LayoutParams cp = Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMargins(Ui.dp(c, 8), 0, 0, 0);
        close.setLayoutParams(cp);

        actions.addView(add);
        actions.addView(close);
        root.addView(actions);
        return root;
    }
}
