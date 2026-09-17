package com.litukang.wordsnap.solver;

/** 一次作答的完整结果，含每个策略对每个选项的打分，方便你对照着调算法。 */
public class Answer {

    public final Question question;
    public final String[] strategyNames;  // 每个策略的名字
    public final double[][] raw;          // raw[策略][选项] 原始打分 0..1
    public final double[] weights;        // 每个策略的权重
    public final double[] scores;         // 加权融合后每个选项的最终分
    public final int best;                // 推荐选项下标，-1 表示没答出来
    public final double confidence;       // 0..1
    public final long costMs;

    public Answer(Question question, String[] strategyNames, double[][] raw,
                  double[] weights, double[] scores, int best, double confidence, long costMs) {
        this.question = question;
        this.strategyNames = strategyNames;
        this.raw = raw;
        this.weights = weights;
        this.scores = scores;
        this.best = best;
        this.confidence = confidence;
        this.costMs = costMs;
    }

    public boolean hasAnswer() {
        return question != null && best >= 0 && best < question.size();
    }

    public String bestLabel() {
        return hasAnswer() ? question.optionLabel(best) : "?";
    }

    public String bestText() {
        return hasAnswer() ? question.optionText(best) : "";
    }

    private static String pct(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    /** 生成可读的打分明细，直接显示在 UI 上 */
    public String explain() {
        if (question == null) {
            return "没有可解析的题目";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("题干：").append(question.stem).append('\n');
        sb.append("选项：").append(question.size()).append(" 个\n\n");

        sb.append("── 各策略打分 ──\n");
        for (int k = 0; k < strategyNames.length; k++) {
            sb.append(strategyNames[k]).append("（权重 ").append(pct(weights[k])).append("）\n");
            for (int i = 0; i < question.size(); i++) {
                sb.append("   ").append(question.optionLabel(i)).append(" ")
                        .append(question.optionText(i))
                        .append("  →  ").append(pct(raw[k][i])).append('\n');
            }
        }

        sb.append("\n── 加权融合 ──\n");
        for (int i = 0; i < question.size(); i++) {
            sb.append(i == best ? "▶ " : "  ")
                    .append(question.optionLabel(i)).append(". ")
                    .append(question.optionText(i))
                    .append("  =  ").append(pct(scores[i])).append('\n');
        }

        sb.append('\n');
        if (hasAnswer()) {
            sb.append("推荐：").append(bestLabel()).append(". ").append(bestText()).append('\n');
            sb.append("置信度：").append(pct(confidence));
            if (confidence < 0.35) {
                sb.append("（偏低，建议自己看一眼）");
            }
        } else {
            sb.append("没答出来：所有策略得分都接近 0，通常是题干没被正确解析。");
        }
        sb.append("\n耗时：").append(costMs).append(" ms");
        return sb.toString();
    }
}
