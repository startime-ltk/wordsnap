package com.litukang.wordsnap.solver;

import com.litukang.wordsnap.data.WordRepository;

/**
 * 策略二「选项反查」—— 专门处理"题干给中文、选项给英文"的反向题型。
 *
 * 思路：既然题干查不到，就把每个选项当成单词去查词库，
 * 谁的释义最贴近题干，谁就最可能是答案。
 * 选项本身是中文时，直接跟题干比字面重合。
 */
public class ReverseLookupStrategy extends Strategy {

    public ReverseLookupStrategy() {
        super("选项反查", "把每个选项当单词查词库，谁的释义最贴近题干谁就是答案", 0.25);
    }

    @Override
    public double[] score(Question q, WordRepository repo) {
        int n = q.size();
        double[] s = zeros(n);
        if (repo == null) {
            return s;
        }
        String stemZh = Algo.chinese(q.stem);
        for (int i = 0; i < n; i++) {
            String opt = q.optionText(i);
            double v = 0;
            String m = repo.lookup(opt);
            if (m != null && !stemZh.isEmpty()) {
                v = Math.max(v, Algo.similar(m, q.stem));
                recognized = true;
            }
            if (Algo.hasChinese(opt) && !stemZh.isEmpty()) {
                v = Math.max(v, Algo.similar(opt, q.stem));
            }
            s[i] = Algo.clamp01(v);
        }
        return s;
    }
}
