package com.litukang.wordsnap.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.litukang.wordsnap.capture.QuizCaptureService;
import com.litukang.wordsnap.data.AllowList;
import com.litukang.wordsnap.data.WordRepository;
import com.litukang.wordsnap.solver.Answer;
import com.litukang.wordsnap.solver.AnswerEngine;
import com.litukang.wordsnap.solver.Question;
import com.litukang.wordsnap.solver.QuestionParser;
import com.litukang.wordsnap.util.Ui;

/**
 * 答题算法实验台。
 *
 * 不用真机截屏也能玩：直接粘贴题目文本（或点内置示例），
 * 就能看到四个策略各自给每个选项打了多少分、加权融合后选了谁、置信度多少。
 * 想看实时效果就用「截屏答题」，走的是完整链路：截屏 → OCR → 解析 → 融合。
 */
public class SolverActivity extends Activity {

    private static final int REQ_CAPTURE = 2101;
    private static final int REQ_OVERLAY = 2102;

    private static final String SAMPLE_EN = "abandon\nA. 公司\nB. 放弃，抛弃\nC. 能力\nD. 缺席";
    private static final String SAMPLE_CN = "充足的，足够的\nA. adequate\nB. abandon\nC. ability\nD. absent";
    private static final String SAMPLE_HARD = "benevolent\nA. 仁慈的，慈善的\nB. 年轻的\nC. 猛烈的\nD. 稀疏的";

    private WordRepository repo;
    private MediaProjectionManager mpm;
    private EditText input;
    private TextView out;
    private EditText pkgInput;
    private CheckBox autoSwitch;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        repo = WordRepository.get(this);
        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        setContentView(buildUi());
        if (repo.size() == 0) {
            repo.loadAsync(this, (count, src) -> runOnUiThread(() ->
                    out.setText("词库已加载：" + count + " 条（" + src + "），可以开始测了")));
        }
    }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        root.setPadding(p, p, p, p);

        root.addView(Ui.text(this, "识屏答题引擎 · 算法实验台", 21f, Ui.TEXT_MAIN, true));
        TextView sub = Ui.text(this,
                "粘贴题目 → 看四个策略怎么打分 → 加权融合出答案。全程离线。",
                13f, Ui.TEXT_SUB, false);
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
        root.addView(sub);

        input = new EditText(this);
        input.setHint("把题目粘到这里，一行一句，例如：abandon / A. 公司 / B. 放弃，抛弃");
        input.setHintTextColor(Ui.TEXT_SUB);
        input.setTextSize(14f);
        input.setTypeface(Typeface.DEFAULT);
        input.setSingleLine(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(5);
        input.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        input.setBackground(Ui.card(this, Ui.CARD_BG, Ui.CARD_STROKE));
        root.addView(input);

        LinearLayout samples = Ui.row(this, 0);
        samples.setPadding(0, Ui.dp(this, 8), 0, 0);
        samples.addView(smallButton("示例·英→中", v -> {
            input.setText(SAMPLE_EN);
            solve();
        }));
        samples.addView(smallButton("示例·中→英", v -> {
            input.setText(SAMPLE_CN);
            solve();
        }));
        samples.addView(smallButton("示例·超纲词", v -> {
            input.setText(SAMPLE_HARD);
            solve();
        }));
        root.addView(samples);

        root.addView(Ui.button(this, "解析并作答", v -> solve()));
        root.addView(Ui.button(this, "截屏答题（实时走一遍完整链路）", v -> startLive()));

        out = Ui.text(this, "结果会显示在这里：每个策略对每个选项的打分，以及加权后的最终选择。",
                13f, Ui.TEXT_MAIN, false);
        out.setTypeface(Typeface.MONOSPACE);
        out.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        out.setBackground(Ui.card(this, Ui.CARD_BG, Ui.CARD_STROKE));
        LinearLayout.LayoutParams olp = Ui.lpMatch();
        olp.setMargins(0, Ui.dp(this, 12), 0, 0);
        out.setLayoutParams(olp);
        root.addView(out);

        root.addView(sectionTitle("自动作答（可选，默认关闭）"));
        TextView hint = Ui.text(this,
                "要真正让它在别的 App 里替你点选，需要三个条件同时满足：\n"
                        + "① 系统设置 → 无障碍 → 打开「词拍」；\n"
                        + "② 下面这个总开关打开；\n"
                        + "③ 填上目标 App 的包名（不填＝任何界面都不会被点）。\n"
                        + "包名形如 com.xxx.app，多个用逗号隔开。",
                12f, Ui.TEXT_SUB, false);
        hint.setPadding(0, 0, 0, Ui.dp(this, 8));
        root.addView(hint);

        autoSwitch = new CheckBox(this);
        autoSwitch.setText("总开关");
        autoSwitch.setTextSize(15f);
        autoSwitch.setChecked(AllowList.isEnabled(this));
        root.addView(autoSwitch);

        pkgInput = new EditText(this);
        pkgInput.setHint("目标包名，逗号分隔");
        pkgInput.setHintTextColor(Ui.TEXT_SUB);
        pkgInput.setTextSize(14f);
        pkgInput.setSingleLine(true);
        pkgInput.setText(AllowList.asText(this));
        pkgInput.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        pkgInput.setBackground(Ui.card(this, Ui.CARD_BG, Ui.CARD_STROKE));
        root.addView(pkgInput);

        root.addView(Ui.button(this, "保存自动作答设置", v -> {
            AllowList.setEnabled(this, autoSwitch.isChecked());
            AllowList.setPackages(this, pkgInput.getText().toString());
            String msg = autoSwitch.isChecked()
                    ? "已开启，目标：" + (AllowList.asText(this).isEmpty() ? "（空，不会点任何界面）"
                    : AllowList.asText(this))
                    : "已关闭";
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }));
        root.addView(Ui.button(this, "打开系统无障碍设置", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "打不开设置页，请手动在设置里找「无障碍」", Toast.LENGTH_SHORT).show();
            }
        }));

        TextView warn = Ui.text(this,
                "提醒：别把它开在有真人对战的模式里 —— 对面是真人在跟你比手速，"
                        + "机器抢答会毁掉对方的体验，也违反平台规则、会封号。\n"
                        + "用在自己的练习、题库、自学内容上是没问题的。",
                12f, Ui.RED, false);
        warn.setPadding(0, Ui.dp(this, 14), 0, 0);
        root.addView(warn);

        sv.addView(root);
        return sv;
    }

    private TextView sectionTitle(String s) {
        TextView t = Ui.text(this, s, 17f, Ui.BLUE, true);
        LinearLayout.LayoutParams lp = Ui.lpMatch();
        lp.setMargins(0, Ui.dp(this, 22), 0, Ui.dp(this, 6));
        t.setLayoutParams(lp);
        return t;
    }

    private Button smallButton(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12f);
        b.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        LinearLayout.LayoutParams lp = Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(0, 0, Ui.dp(this, 6), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private void solve() {
        String raw = input.getText() == null ? "" : input.getText().toString();
        if (raw.trim().isEmpty()) {
            Toast.makeText(this, "先粘一段题目，或点上面的示例", Toast.LENGTH_SHORT).show();
            return;
        }
        if (repo.size() == 0) {
            out.setText("词库还在加载，稍等两秒再点一次");
            return;
        }
        Question q = QuestionParser.parse(raw);
        Answer a = AnswerEngine.solve(q, repo);
        out.setText(a.explain());
    }

    private void startLive() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "建议先允许悬浮窗，否则结果只能走通知栏", Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
            return;
        }
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                QuizCaptureService.start(this, resultCode, data);
                Toast.makeText(this, "已授权，切到题目界面后结果会弹在顶部", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
            }
        }
    }
}
