package com.litukang.wordsnap.solver;

import com.litukang.wordsnap.data.WordRepository;

/**
 * 策略三「词形模糊」—— 抗 OCR 错字和词形变化。
 *
 * 思路：题干可能是 "abandons" / "abandoned" / "abandoning"，
 * 直接查词库查不到。先做词形还原（复数、过去式、进行时、比较级、副词），
 * 拿到词库里真实存在的那个形式的释义，再去跟选项比。
 * 全都查不到时（超纲词 / OCR 认错了），退化成"选项与题干拼写接近程度"的弱信号。
 */
public class StemFuzzyStrategy extends Strategy {

    public StemFuzzyStrategy() {
        super("词形模糊", "题干先做词形还原再查词库，抗屈折变化和 OCR 错字", 0.20);
    }

    @Override
    public double[] score(Question q, WordRepository repo) {
        int n = q.size();
        double[] s = zeros(n);
        if (repo == null) {
            return s;
        }
        String w = Algo.letters(q.stem);
        if (w.length() < 2) {
            return s;
        }

        String meaning = null;
        for (String cand : WordRepository.variants(w)) {
            meaning = repo.lookup(cand);
            if (meaning != null) {
                break;
            }
        }

        if (meaning == null) {
            // 词库没有：只能给一个很弱的"拼写接近"信号
            recognized = false;
            for (int i = 0; i < n; i++) {
                String optEn = Algo.letters(q.optionText(i));
                s[i] = Algo.clamp01(Algo.editSimilarity(w, optEn) * 0.6);
            }
            return s;
        }
        recognized = true;

        String cm = Algo.chinese(meaning);
        for (int i = 0; i < n; i++) {
            String opt = q.optionText(i);
            double v = Algo.similar(meaning, opt);
            String co = Algo.chinese(opt);
            if (!cm.isEmpty() && !co.isEmpty() && (cm.contains(co) || co.contains(cm))) {
                v = Math.max(v, 0.8);
            }
            s[i] = Algo.clamp01(v);
        }
        return s;
    }
}
