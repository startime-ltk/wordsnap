package com.litukang.wordsnap.solver;

import android.os.SystemClock;

import com.litukang.wordsnap.data.WordRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 融合器：把多个策略的打分合起来，挑一个最终答案。
 *
 * 融合方式很简单 —— 加权平均：
 *     final[i] = Σ_k (raw[k][i] × weight[k]) / Σ_k weight[k]
 *
 * 置信度 = 最高分有多高 × 跟第二名拉开多少：
 *     conf = 0.6 × best + 0.4 × (best - second)
 * 两个因子缺一不可：分数高但跟第二名叫板，说明不可靠；拉开很大但都很低，说明题没解析对。
 */
public final class AnswerEngine {

    private AnswerEngine() {
    }

    public static List<Strategy> defaultStrategies() {
        List<Strategy> list = new ArrayList<>();
        list.add(new DictMatchStrategy());
        list.add(new ReverseLookupStrategy());
        list.add(new StemFuzzyStrategy());
        list.add(new EliminationStrategy());
        return list;
    }

    public static Answer solve(Question q, WordRepository repo) {
        long t0 = SystemClock.elapsedRealtime();
        if (q == null) {
            q = new Question("", null, "");
        }
        List<Strategy> ss = defaultStrategies();
        int n = q.size();

        double[][] raw = new double[ss.size()][Math.max(n, 0)];
        double[] weights = new double[ss.size()];
        String[] names = new String[ss.size()];

        for (int k = 0; k < ss.size(); k++) {
            Strategy s = ss.get(k);
            names[k] = s.name;
            weights[k] = s.weight;
            raw[k] = safe(s.score(q, repo), n);
        }

        double wsum = 0;
        for (double w : weights) {
            wsum += w;
        }
        if (wsum <= 0) {
            wsum = 1;
        }

        double[] fin = new double[Math.max(n, 0)];
        for (int k = 0; k < ss.size(); k++) {
            for (int i = 0; i < n; i++) {
                fin[i] += raw[k][i] * weights[k] / wsum;
            }
        }

        int best = -1;
        double bestV = 0;
        for (int i = 0; i < n; i++) {
            if (fin[i] > bestV) {
                bestV = fin[i];
                best = i;
            }
        }
        double second = 0;
        for (int i = 0; i < n; i++) {
            if (i != best && fin[i] > second) {
                second = fin[i];
            }
        }

        // 关键的安全阀：四个策略里一个都没在词库里找到实证，
        // 说明题干大概率没被识别对（OCR 认错字 / 超纲词），这时候的分是"猜"出来的，
        // 置信度直接打对折，让它跨不过自动作答的 0.35 门槛。
        boolean anyRecognized = false;
        for (Strategy s : ss) {
            if (s.recognized) {
                anyRecognized = true;
                break;
            }
        }

        double conf = 0;
        if (best >= 0) {
            conf = 0.6 * bestV + 0.4 * (bestV - second);
            if (!anyRecognized) {
                conf *= 0.5;
            }
            conf = Algo.clamp01(conf);
        }

        return new Answer(q, names, raw, weights, fin, best, conf,
                SystemClock.elapsedRealtime() - t0);
    }

    /** 兜底：任何策略返回 null / 长度不对 / NaN，都不让整个引擎崩掉 */
    private static double[] safe(double[] src, int n) {
        double[] out = new double[Math.max(n, 0)];
        if (src == null) {
            return out;
        }
        int m = Math.min(src.length, n);
        for (int i = 0; i < m; i++) {
            out[i] = Algo.clamp01(src[i]);
        }
        return out;
    }
}
