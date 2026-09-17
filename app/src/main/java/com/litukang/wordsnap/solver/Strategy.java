package com.litukang.wordsnap.solver;

import com.litukang.wordsnap.data.WordRepository;

/**
 * 一种"怎么选答案"的算法。
 *
 * 每个策略独立地给所有选项打分（0..1，越大越像正确答案），互不知道对方存在。
 * 最后由 AnswerEngine 按权重加权融合 —— 这样你可以单独关掉某一个，
 * 看看它到底贡献了多少，也方便换掉其中任何一个。
 */
public abstract class Strategy {

    public final String name;
    public final String desc;
    public final double weight;

    /**
     * 本策略这次有没有真的找到词典证据。
     * 融合器会看这个：四个策略全都没找到证据（多半是 OCR 把单词认错了），
     * 就把置信度打折，避免"瞎猜还显得很自信"。
     */
    public boolean recognized = false;

    protected Strategy(String name, String desc, double weight) {
        this.name = name;
        this.desc = desc;
        this.weight = weight;
    }

    /** 返回长度 == 选项个数的打分数组，取值 0..1 */
    public abstract double[] score(Question q, WordRepository repo);

    protected static double[] zeros(int n) {
        return new double[Math.max(n, 0)];
    }
}
