package com.litukang.wordsnap.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.litukang.wordsnap.data.WordBookStore;
import com.litukang.wordsnap.ocr.WordScanner;
import com.litukang.wordsnap.util.Ui;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 无悬浮窗权限时的兜底结果页（从通知点进来）。 */
public class ResultActivity extends Activity {

    private static final String EXTRA_HITS = "hits";
    private static final String EXTRA_COST = "cost";
    private static final String EXTRA_PREVIEW = "preview";

    public static Intent buildIntent(Context c, ArrayList<WordScanner.Hit> hits, long costMs, String preview) {
        Intent i = new Intent(c, ResultActivity.class);
        i.putExtra(EXTRA_HITS, (Serializable) hits);
        i.putExtra(EXTRA_COST, costMs);
        i.putExtra(EXTRA_PREVIEW, preview);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        long cost = getIntent().getLongExtra(EXTRA_COST, 0L);
        String preview = getIntent().getStringExtra(EXTRA_PREVIEW);
        Object raw = getIntent().getSerializableExtra(EXTRA_HITS);
        final List<WordScanner.Hit> hits =
                (raw instanceof List) ? (List<WordScanner.Hit>) raw : new ArrayList<>();

        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(0x66000000);
        outer.setPadding(Ui.dp(this, 20), Ui.dp(this, 40), Ui.dp(this, 20), Ui.dp(this, 40));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(this, Ui.CARD_BG, Ui.CARD_STROKE));
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER;
        card.setLayoutParams(cp);

        card.addView(Ui.text(this, "识别完成 · " + cost + " ms", 14f, Ui.TEXT_SUB, true));

        ScrollView sv = new ScrollView(this);
        LinearLayout.LayoutParams sp = Ui.lpMatch();
        sp.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        sv.setLayoutParams(sp);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        for (WordScanner.Hit h : hits) {
            TextView tv = Ui.text(this, h.word + "  —  " + h.meaning, 16f, Ui.TEXT_MAIN, false);
            tv.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
            box.addView(tv);
        }
        if (hits.isEmpty()) {
            TextView tv = Ui.text(this, preview == null || preview.isEmpty()
                    ? "屏幕上没识别到文本" : preview, 15f, Ui.TEXT_MAIN, false);
            box.addView(tv);
        }
        sv.addView(box);
        card.addView(sv);

        card.addView(Ui.button(this, "全部加入生词本", v -> {
            WordBookStore store = new WordBookStore(this);
            for (WordScanner.Hit h : hits) {
                store.add(h.word);
            }
            Toast.makeText(this, "已加入 " + hits.size() + " 个词", Toast.LENGTH_SHORT).show();
            finish();
        }));
        card.addView(Ui.button(this, "关闭", v -> finish()));

        outer.addView(card);
        setContentView(outer);
    }
}
