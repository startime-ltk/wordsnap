package com.litukang.wordsnap.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.litukang.wordsnap.data.WordBookStore;
import com.litukang.wordsnap.data.WordRepository;
import com.litukang.wordsnap.util.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 计时训练：本地模拟对战节奏（7 题 / 每题 10 秒），练的是你自己的反应速度。
 * 这是对战外挂的合规替代：同样的节奏，但答案是自己点出来的。
 */
public class TrainingActivity extends Activity {

    private static final int TOTAL = 7;
    private static final long LIMIT_MS = 10_000L;
    private static final int OPTION_COUNT = 4;

    private WordRepository repo;
    private WordBookStore book;
    private final Random rnd = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<WordRepository.Entry> questions = new ArrayList<>();
    private String[] options = new String[OPTION_COUNT];
    private int correctIndex = 0;

    private int index = 0;
    private int correctCount = 0;
    private long totalCost = 0L;
    private long fastest = Long.MAX_VALUE;
    private long questionStart = 0L;
    private boolean locked = false;
    private boolean finished = false;

    private TextView progressView;
    private TextView timeView;
    private TextView wordView;
    private TextView feedbackView;
    private LinearLayout optionsBox;
    private LinearLayout panel;
    private final Button[] optionButtons = new Button[OPTION_COUNT];

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (locked || finished) {
                return;
            }
            long left = LIMIT_MS - (SystemClock.elapsedRealtime() - questionStart);
            if (left <= 0) {
                onTimeout();
                return;
            }
            timeView.setText("剩余 " + String.format(java.util.Locale.CHINA, "%.1f", left / 1000.0) + " 秒");
            handler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        repo = WordRepository.get(this);
        book = new WordBookStore(this);
        setContentView(buildUi());
        if (repo.size() == 0) {
            // 冷启动直接进训练时词库可能还没加载完，等加载完再开局，避免"词库为空"误报
            wordView.setText("词库加载中…");
            repo.loadAsync(this, (count, src) -> runOnUiThread(() -> {
                if (count < 10) {
                    Toast.makeText(this, "词库异常，请回主界面重新加载", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    newRound();
                }
            }));
        } else {
            newRound();
        }
    }

    private android.view.View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20));

        progressView = Ui.text(this, "", 15f, Ui.TEXT_SUB, true);
        root.addView(progressView);

        timeView = Ui.text(this, "", 14f, Ui.TEXT_SUB, false);
        root.addView(timeView);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.card(this, Ui.CARD_BG, Ui.CARD_STROKE));
        panel.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18));
        LinearLayout.LayoutParams pp = Ui.lpMatch();
        pp.setMargins(0, Ui.dp(this, 12), 0, 0);
        panel.setLayoutParams(pp);

        wordView = Ui.text(this, "", 30f, Ui.TEXT_MAIN, true);
        wordView.setGravity(Gravity.CENTER);
        wordView.setMaxLines(1);
        // 长单词自动缩小，避免顶出屏幕
        wordView.setAutoSizeTextTypeUniformWithConfiguration(14, 34, 2,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        panel.addView(wordView);

        feedbackView = Ui.text(this, "", 14f, Ui.TEXT_SUB, false);
        feedbackView.setGravity(Gravity.CENTER);
        feedbackView.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        panel.addView(feedbackView);

        optionsBox = new LinearLayout(this);
        optionsBox.setOrientation(LinearLayout.VERTICAL);
        optionsBox.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < OPTION_COUNT; i++) {
            Button btn = Ui.button(this, "", v -> onAnswer((Integer) v.getTag()));
            btn.setTag(i);
            optionButtons[i] = btn;
            optionsBox.addView(btn);
        }
        panel.addView(optionsBox);

        root.addView(panel);
        return root;
    }

    private void newRound() {
        questions = repo.randomDistinct(TOTAL, rnd, null);
        index = 0;
        correctCount = 0;
        totalCost = 0L;
        fastest = Long.MAX_VALUE;
        finished = false;
        if (questions.size() < 2) {
            Toast.makeText(this, "词库为空，无法训练", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        showQuestion();
    }

    private void showQuestion() {
        if (index >= questions.size()) {
            showResult();
            return;
        }
        locked = false;
        WordRepository.Entry e = questions.get(index);
        progressView.setText("第 " + (index + 1) + " / " + questions.size() + " 题");
        wordView.setText(e.word);
        feedbackView.setText("");
        feedbackView.setTextColor(Ui.TEXT_SUB);

        options[0] = e.meaning;
        // 干扰项必须与正确释义不同，且彼此不同，否则"正确答案"会出现两处
        int i = 1;
        int guard = 0;
        while (i < OPTION_COUNT && guard++ < 60) {
            WordRepository.Entry d = repo.random(rnd);
            if (d == null) {
                break;
            }
            if (d.meaning.equals(e.meaning) || d.word.equals(e.word)) {
                continue;
            }
            boolean dup = false;
            for (int k = 0; k < i; k++) {
                if (options[k].equals(d.meaning)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                options[i++] = d.meaning;
            }
        }
        while (i < OPTION_COUNT) {
            options[i++] = "—";
        }
        // 打乱
        for (int k = options.length - 1; k > 0; k--) {
            int j = rnd.nextInt(k + 1);
            String t = options[k];
            options[k] = options[j];
            options[j] = t;
        }
        for (int k = 0; k < OPTION_COUNT; k++) {
            if (e.meaning.equals(options[k])) {
                correctIndex = k;
            }
            optionButtons[k].setText(options[k]);
            optionButtons[k].setEnabled(true);
        }

        questionStart = SystemClock.elapsedRealtime();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void onAnswer(int which) {
        if (locked || finished) {
            return;
        }
        locked = true;
        handler.removeCallbacks(tick);
        long cost = SystemClock.elapsedRealtime() - questionStart;
        totalCost += cost;
        fastest = Math.min(fastest, cost);

        WordRepository.Entry e = questions.get(index);
        boolean right = which == correctIndex;
        for (Button b : optionButtons) {
            b.setEnabled(false);
        }
        if (right) {
            correctCount++;
            feedbackView.setTextColor(Ui.GREEN);
            feedbackView.setText("✓ 正确  " + cost + " ms");
        } else {
            feedbackView.setTextColor(Ui.RED);
            feedbackView.setText("✗ 正确答案：" + e.meaning + "（" + cost + " ms）");
            book.add(e.word);
        }
        handler.postDelayed(this::nextQuestion, right ? 550 : 1100);
    }

    private void onTimeout() {
        if (locked || finished) {
            return;
        }
        locked = true;
        handler.removeCallbacks(tick);
        totalCost += LIMIT_MS;
        WordRepository.Entry e = questions.get(index);
        feedbackView.setTextColor(Ui.RED);
        feedbackView.setText("⏱ 超时，正确答案：" + e.meaning);
        book.add(e.word);
        for (Button b : optionButtons) {
            b.setEnabled(false);
        }
        handler.postDelayed(this::nextQuestion, 1100);
    }

    private void nextQuestion() {
        index++;
        showQuestion();
    }

    private void showResult() {
        finished = true;
        handler.removeCallbacks(tick);
        int total = questions.size();
        long avg = totalCost / Math.max(1, total);
        book.saveSession(correctCount, total, avg);

        progressView.setText("本局结束");
        timeView.setText("");
        wordView.setText(correctCount + " / " + total);
        feedbackView.setTextColor(Ui.BLUE);
        feedbackView.setText("正确率 " + (correctCount * 100 / total) + "% · 平均 "
                + WordBookStore.fmt(avg) + " 秒 · 最快 "
                + (fastest == Long.MAX_VALUE ? "—" : WordBookStore.fmt(fastest) + " 秒"));

        optionsBox.removeAllViews();
        optionsBox.addView(Ui.button(this, "再来一局", v -> {
            panel.removeAllViews();
            rebuildQuizViews();
            newRound();
        }));
        optionsBox.addView(Ui.button(this, "返回", v -> finish()));
    }

    /** 成绩页复用了 panel，重新练时把题目区视图加回去 */
    private void rebuildQuizViews() {
        panel.addView(wordView);
        panel.addView(feedbackView);
        panel.addView(optionsBox);
        for (Button b : optionButtons) {
            b.setVisibility(android.view.View.VISIBLE);
        }
        optionsBox.removeAllViews();
        for (Button b : optionButtons) {
            optionsBox.addView(b);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }
}
