package com.litukang.wordsnap.solver;

import com.litukang.wordsnap.data.WordRepository;

/**
 * 策略一「词典直查」—— 主力。
 *
 * 思路：题干是英文单词 → 去本地词库查出中文释义 → 拿这个释义跟每个选项比字面重合度。
 * 额外规则：如果词库释义和某个选项存在"互相包含"关系（比如释义"v. 丢弃；放弃，抛弃"
 * 与选项"放弃，抛弃"），直接给 0.85 保底分，因为这种包含关系比纯字符重合更可靠。
 */
public class DictMatchStrategy extends Strategy {

    public DictMatchStrategy() {
        super("词典直查", "题干单词 → 词库释义 → 与选项比字面重合度（Jaccard + 包含保底）", 0.45);
    }

    @Override
    public double[] score(Question q, WordRepository repo) {
        int n = q.size();
        double[] s = zeros(n);
        if (repo == null || q.stem.isEmpty()) {
            return s;
        }
        String meaning = repo.lookup(q.stem);
        if (meaning == null) {
            return s; // 词库里没有这个题干，本策略弃权（全 0）
        }
        recognized = true;
        String cm = Algo.chinese(meaning);
        for (int i = 0; i < n; i++) {
            String opt = q.optionText(i);
            double v = Algo.similar(meaning, opt);
            String co = Algo.chinese(opt);
            if (!cm.isEmpty() && !co.isEmpty() && (cm.contains(co) || co.contains(cm))) {
                v = Math.max(v, 0.85);
            }
            s[i] = Algo.clamp01(v);
        }
        return s;
    }
}
