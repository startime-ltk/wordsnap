package com.litukang.wordsnap.solver;

import com.litukang.wordsnap.data.WordRepository;

/**
 * 策略四「离群排除」—— 完全不看词库，纯靠选项之间的关系。
 *
 * 出题规律：四个选项里通常三个语义接近（都是干扰项），正确答案跟它们不太一样；
 * 反过来也有反规律：干扰项常常是"离群"的那个。所以这个策略权重给得很低（0.10），
 * 只在前面几个策略打平、或者词库完全没命中时，才提供一点区分度。
 *
 * 算法：算每个选项与其余选项的平均相似度，归一化到 0..1。"合群"的分高。
 */
public class EliminationStrategy extends Strategy {

    public EliminationStrategy() {
        super("离群排除", "不看词库，只算每个选项与其余选项的合群程度（排除法的量化版）", 0.10);
    }

    @Override
    public double[] score(Question q, WordRepository repo) {
        int n = q.size();
        double[] s = zeros(n);
        if (n < 3) {
            return s;
        }
        double[] affinity = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            int cnt = 0;
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                sum += Algo.similar(q.optionText(i), q.optionText(j));
                cnt++;
            }
            affinity[i] = cnt == 0 ? 0 : sum / cnt;
        }
        double max = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;
        for (double v : affinity) {
            if (v > max) {
                max = v;
            }
            if (v < min) {
                min = v;
            }
        }
        double span = max - min;
        if (span < 1e-6) {
            return s; // 选项彼此都差不多，这个策略没有信息量，弃权
        }
        for (int i = 0; i < n; i++) {
            s[i] = Algo.clamp01((affinity[i] - min) / span);
        }
        return s;
    }
}
