package com.litukang.wordsnap.solver;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 答题引擎的"度量工具箱"：怎么算两段文字像不像。
 *
 * 这里刻意用了三种不同思路的相似度，因为题目里的文字分两类：
 *   - 中文释义（"放弃，抛弃"）→ 看字面重合，用 Jaccard（二元字集合交并比）
 *   - 英文单词（"abandon"）  → 看拼写差异，用编辑距离（Levenshtein）
 * 混排时两者加权。整个引擎的"像不像"判断都从这里出发。
 */
public final class Algo {

    private Algo() {
    }

    /** 是否包含中文字符（CJK 统一表意文字区间） */
    public static boolean hasChinese(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    /** 只保留小写英文字母（去掉词性标注、标点、空格） */
    public static String letters(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.US).replaceAll("[^a-z]", "");
    }

    /** 只保留中文字符（去掉 "n." "adj." 这类词性标注） */
    public static String chinese(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static double clamp01(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0;
        }
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /**
     * 编辑距离 Levenshtein：把 a 改成 b 最少要几步（增/删/改各算 1 步）。
     * 用滚动数组把空间压到 O(min(n,m))，时间复杂度 O(n*m)。
     */
    public static int editDistance(String a, String b) {
        if (a == null) {
            a = "";
        }
        if (b == null) {
            b = "";
        }
        if (a.equals(b)) {
            return 0;
        }
        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    /** 归一化编辑相似度：0..1，1 表示完全相同 */
    public static double editSimilarity(String a, String b) {
        if (a == null || b == null) {
            return 0;
        }
        int max = Math.max(a.length(), b.length());
        if (max == 0) {
            return 0;
        }
        return 1.0 - (double) editDistance(a, b) / max;
    }

    /** 相邻两字符组成的集合（"放弃抛弃" → {放弃, 弃抛, 抛弃}） */
    private static Set<String> bigrams(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) {
            return out;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return out;
        }
        if (t.length() == 1) {
            out.add(t);
            return out;
        }
        for (int i = 0; i + 1 < t.length(); i++) {
            out.add(t.substring(i, i + 2));
        }
        return out;
    }

    /** Jaccard 相似度 = |交集| / |并集|，对短中文释义很有效 */
    public static double jaccard(String a, String b) {
        Set<String> sa = bigrams(a);
        Set<String> sb = bigrams(b);
        if (sa.isEmpty() || sb.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(sa);
        inter.retainAll(sb);
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        return union.isEmpty() ? 0 : (double) inter.size() / union.size();
    }

    /**
     * 通用相似度：中文比字面重合，英文比拼写，中英都有时 7:3 加权。
     * 这是所有策略共用的"像不像"底座。
     */
    public static double similar(String a, String b) {
        if (a == null || b == null) {
            return 0;
        }
        String ca = chinese(a);
        String cb = chinese(b);
        String la = letters(a);
        String lb = letters(b);
        boolean hasCn = ca.length() >= 2 && cb.length() >= 2;
        boolean hasEn = la.length() >= 2 && lb.length() >= 2;
        double cn = hasCn ? jaccard(ca, cb) : 0;
        double en = hasEn ? editSimilarity(la, lb) : 0;
        if (hasCn && hasEn) {
            return 0.7 * cn + 0.3 * en;
        }
        return Math.max(cn, en);
    }
}
