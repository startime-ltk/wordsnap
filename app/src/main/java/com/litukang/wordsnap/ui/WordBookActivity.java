package com.litukang.wordsnap.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.litukang.wordsnap.data.WordBookStore;
import com.litukang.wordsnap.data.WordRepository;
import com.litukang.wordsnap.util.Ui;

import java.util.List;

/** 生词本：查得到释义就显示，支持单个删除和清空。 */
public class WordBookActivity extends Activity {

    private WordRepository repo;
    private WordBookStore book;
    private LinearLayout listBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        repo = WordRepository.get(this);
        book = new WordBookStore(this);
        setContentView(buildUi());
        render();
    }

    private ScrollView buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20));

        TextView title = Ui.text(this, "生词本", 22f, Ui.TEXT_MAIN, true);
        root.addView(title);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0, Ui.dp(this, 12), 0, 0);
        root.addView(listBox);

        root.addView(Ui.button(this, "清空生词本", v -> {
            book.clear();
            render();
        }));
        root.addView(Ui.button(this, "返回", v -> finish()));

        sv.addView(root);
        return sv;
    }

    private void render() {
        listBox.removeAllViews();
        List<String> words = book.words();
        if (words.isEmpty()) {
            TextView empty = Ui.text(this, "还没有生词。训练里答错或超时的词会自动进来。",
                    15f, Ui.TEXT_SUB, false);
            listBox.addView(empty);
            return;
        }
        for (String w : words) {
            String meaning = repo.lookup(w);
            LinearLayout row = Ui.row(this, 12);
            row.setBackground(Ui.card(this, Ui.CARD_BG, Ui.CARD_STROKE));
            LinearLayout.LayoutParams rp = Ui.lpMatch();
            rp.setMargins(0, 0, 0, Ui.dp(this, 8));
            row.setLayoutParams(rp);

            LinearLayout textBox = new LinearLayout(this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            textBox.setLayoutParams(Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            textBox.addView(Ui.text(this, w, 17f, Ui.TEXT_MAIN, true));
            textBox.addView(Ui.text(this, meaning == null ? "（词库里没有这个词的释义）" : meaning,
                    14f, Ui.TEXT_SUB, false));

            android.widget.Button del = new android.widget.Button(this);
            del.setText("删除");
            del.setAllCaps(false);
            del.setGravity(Gravity.CENTER);
            del.setOnClickListener(v -> {
                book.remove(w);
                render();
                Toast.makeText(this, "已删除 " + w, Toast.LENGTH_SHORT).show();
            });

            row.addView(textBox);
            row.addView(del);
            listBox.addView(row);
        }
    }
}
