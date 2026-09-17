#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
答题引擎的离线仿真校验（不需要安卓机）。

把 Java 侧 solver/ 里的算法原样用 Python 复刻一遍，直接读 app/src/main/assets/cet4.csv，
随机抽词造四选一题目，统计每个策略 + 融合后的准确率。

用法：
    python tools/sim_check.py [题目数量] [随机种子]
例：
    python tools/sim_check.py 500 1
"""
import random
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DICT_PATH = ROOT / "app" / "src" / "main" / "assets" / "cet4.csv"


# ---------- Algo.java ----------

def has_chinese(s):
    return any('\u4e00' <= c <= '\u9fff' for c in s)


def letters(s):
    return re.sub(r'[^a-z]', '', (s or '').lower())


def chinese(s):
    return ''.join(c for c in (s or '') if '\u4e00' <= c <= '\u9fff')


def clamp01(v):
    if v != v or v in (float('inf'), float('-inf')):
        return 0.0
    return 0.0 if v < 0 else (1.0 if v > 1 else v)


def edit_distance(a, b):
    if a == b:
        return 0
    if not a:
        return len(b)
    if not b:
        return len(a)
    prev = list(range(len(b) + 1))
    cur = [0] * (len(b) + 1)
    for i in range(1, len(a) + 1):
        cur[0] = i
        for j in range(1, len(b) + 1):
            cost = 0 if a[i - 1] == b[j - 1] else 1
            cur[j] = min(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        prev, cur = cur, prev
    return prev[len(b)]


def edit_similarity(a, b):
    m = max(len(a or ''), len(b or ''))
    return 0.0 if m == 0 else 1.0 - edit_distance(a or '', b or '') / m


def bigrams(s):
    t = (s or '').strip()
    if not t:
        return set()
    if len(t) == 1:
        return {t}
    return {t[i:i + 2] for i in range(len(t) - 1)}


def jaccard(a, b):
    sa, sb = bigrams(a), bigrams(b)
    if not sa or not sb:
        return 0.0
    return len(sa & sb) / len(sa | sb)


def similar(a, b):
    ca, cb = chinese(a), chinese(b)
    la, lb = letters(a), letters(b)
    has_cn = len(ca) >= 2 and len(cb) >= 2
    has_en = len(la) >= 2 and len(lb) >= 2
    cn = jaccard(ca, cb) if has_cn else 0.0
    en = edit_similarity(la, lb) if has_en else 0.0
    if has_cn and has_en:
        return 0.7 * cn + 0.3 * en
    return max(cn, en)


# ---------- WordRepository.variants ----------

def strip_doubled(s):
    return s[:-1] if len(s) >= 3 and s[-1] == s[-2] else s


def variants(w):
    out = []

    def add(x):
        if x and len(x) >= 2 and x not in out:
            out.append(x)

    add(w)
    if w.endswith('ies') and len(w) > 4:
        add(w[:-3] + 'y')
    if w.endswith('ves') and len(w) > 4:
        add(w[:-3] + 'f')
        add(w[:-3] + 'fe')
    if w.endswith('es') and len(w) > 3:
        add(w[:-2])
        add(w[:-1])
    if w.endswith('s') and not w.endswith('ss') and len(w) > 3:
        add(w[:-1])
    if w.endswith('ed') and len(w) > 4:
        base = w[:-2]
        add(base)
        add(base + 'e')
        add(strip_doubled(base))
    if w.endswith('ing') and len(w) > 5:
        base = w[:-3]
        add(base)
        add(base + 'e')
        add(strip_doubled(base))
    if w.endswith('er') and len(w) > 4:
        add(w[:-2])
    if w.endswith('est') and len(w) > 5:
        add(w[:-3])
    if w.endswith('ly') and len(w) > 4:
        add(w[:-2])
    return out


class Repo:
    def __init__(self, path):
        self.map = {}
        with open(path, encoding='utf-8') as f:
            for line in f:
                line = line.strip().lstrip('\ufeff')
                if not line or line.startswith('#'):
                    continue
                sep = min([i for i in (line.find(','), line.find('\t')) if i >= 0], default=-1)
                if sep <= 0:
                    continue
                w = letters(line[:sep])
                m = line[sep + 1:].strip()
                if len(w) >= 2 and m:
                    self.map.setdefault(w, m)
        self.list = list(self.map.items())

    def lookup(self, raw):
        w = letters(raw)
        if len(w) < 2:
            return None
        if w in self.map:
            return self.map[w]
        for c in variants(w):
            if c in self.map:
                return self.map[c]
        return None


# ---------- QuestionParser（带标号的分支，够仿真用） ----------

def parse_labeled(raw):
    opts, others = [], []
    labels = list("ABCDEFGH")
    for line in (l.strip() for l in raw.split('\n')):
        if not line:
            continue
        m = re.match(r'^\s*([A-Z])\s*[.、)\]:：]\s*(.+?)\s*$', line)
        if m and len(opts) < len(labels):
            opts.append((labels[len(opts)], m.group(2).strip()))
            continue
        others.append(line)
    if len(opts) < 2:
        return None
    cn_opts = sum(1 for _, t in opts if has_chinese(t))
    opts_are_cn = cn_opts * 2 >= len(opts)
    preferred = [s for s in others if has_chinese(s) != opts_are_cn]
    pool = preferred or others
    stem = min(pool, key=len) if pool else ''
    return stem, opts


# ---------- 四个策略 ----------

def s_dict(q, repo):
    stem, opts = q
    n = len(opts)
    out = [0.0] * n
    meaning = repo.lookup(stem)
    if not meaning:
        return out, False
    cm = chinese(meaning)
    for i, (_, t) in enumerate(opts):
        v = similar(meaning, t)
        co = chinese(t)
        if cm and co and (cm in co or co in cm):
            v = max(v, 0.85)
        out[i] = clamp01(v)
    return out, True


def s_reverse(q, repo):
    stem, opts = q
    n = len(opts)
    out = [0.0] * n
    rec = False
    stem_zh = chinese(stem)
    for i, (_, t) in enumerate(opts):
        v = 0.0
        m = repo.lookup(t)
        if m and stem_zh:
            v = max(v, similar(m, stem))
            rec = True
        if has_chinese(t) and stem_zh:
            v = max(v, similar(t, stem))
        out[i] = clamp01(v)
    return out, rec


def s_stem(q, repo):
    stem, opts = q
    n = len(opts)
    out = [0.0] * n
    w = letters(stem)
    if len(w) < 2:
        return out
    meaning = None
    for c in variants(w):
        meaning = repo.lookup(c)
        if meaning:
            break
    if not meaning:
        for i, (_, t) in enumerate(opts):
            out[i] = clamp01(edit_similarity(w, letters(t)) * 0.6)
        return out, False
    cm = chinese(meaning)
    for i, (_, t) in enumerate(opts):
        v = similar(meaning, t)
        co = chinese(t)
        if cm and co and (cm in co or co in cm):
            v = max(v, 0.8)
        out[i] = clamp01(v)
    return out, True


def s_elim(q, repo):
    _, opts = q
    n = len(opts)
    out = [0.0] * n
    if n < 3:
        return out, False
    aff = []
    for i in range(n):
        sims = [similar(opts[i][1], opts[j][1]) for j in range(n) if j != i]
        aff.append(sum(sims) / len(sims))
    lo, hi = min(aff), max(aff)
    if hi - lo < 1e-6:
        return out, False
    for i in range(n):
        out[i] = clamp01((aff[i] - lo) / (hi - lo))
    return out, False


STRATEGIES = [
    ("词典直查", 0.45, s_dict),
    ("选项反查", 0.25, s_reverse),
    ("词形模糊", 0.20, s_stem),
    ("离群排除", 0.10, s_elim),
]


CONF_MIN = 0.35  # 与 AutoAnswerService 里自动作答的门槛保持一致


def solve(q, repo):
    n = len(q[1])
    scored = [fn(q, repo) for _, _, fn in STRATEGIES]
    raw = [clamp_array(s, n) for s, _ in scored]
    wsum = sum(w for _, w, _ in STRATEGIES)
    fin = [0.0] * n
    for k, (_, w, _) in enumerate(STRATEGIES):
        for i in range(n):
            fin[i] += raw[k][i] * w / wsum

    best = max(range(n), key=lambda i: fin[i]) if n else -1
    if n and fin[best] <= 0:
        best = -1
    second = max([fin[i] for i in range(n) if i != best], default=0.0)

    conf = 0.0
    if best >= 0:
        conf = 0.6 * fin[best] + 0.4 * (fin[best] - second)
        if not any(rec for _, rec in scored):
            conf *= 0.5  # 没有任何词典实证 → 打折（Java 侧同逻辑）
        conf = clamp01(conf)
        if conf < CONF_MIN:
            best = -1  # 低于门槛就不答，跟自动作答模块行为一致
    return best, conf, raw, fin


def clamp_array(a, n):
    out = [0.0] * n
    for i in range(min(len(a), n)):
        out[i] = clamp01(a[i])
    return out


# ---------- 跑评测 ----------

def ocr_noise(w, rng):
    """模拟 OCR 把单词认错：随机删一个字母 / 换一个字母 / 插一个字母"""
    if len(w) < 3:
        return w
    i = rng.randrange(len(w))
    op = rng.choice(["del", "sub", "ins"])
    if op == "del":
        return w[:i] + w[i + 1:]
    if op == "sub":
        return w[:i] + rng.choice("abcdefghijklmnopqrstuvwxyz") + w[i + 1:]
    return w[:i] + rng.choice("abcdefghijklmnopqrstuvwxyz") + w[i:]


def first_sense(m):
    """真实题目里的选项往往只给一个义项，这里取第一个分号前的部分并去掉词性标注"""
    s = re.split(r'[；;]', m)[0].strip()
    return re.sub(r'^[a-z]+\.\s*', '', s).strip() or m


def pick_distractors(repo, word, meaning, rng, hard, pool_size=300):
    """hard=True 时，干扰项取释义最相近的词（接近真实出题难度）；
    hard=False 时纯随机（太简单，只能验证链路通不通）。"""
    if not hard:
        out = []
        guard = 0
        while len(out) < 3 and guard < 80:
            guard += 1
            w2, m2 = rng.choice(repo.list)
            if w2 == word or m2 == meaning or m2 in out:
                continue
            out.append(m2)
        return out
    pool = [rng.choice(repo.list) for _ in range(min(pool_size, len(repo.list)))]
    scored = []
    for w2, m2 in pool:
        if w2 == word or m2 == meaning:
            continue
        scored.append((jaccard(chinese(meaning), chinese(m2)), m2))
    scored.sort(reverse=True)
    out, seen = [], set()
    for _, m2 in scored:
        if m2 in seen:
            continue
        seen.add(m2)
        out.append(m2)
        if len(out) == 3:
            break
    return out


def main():
    total = int(sys.argv[1]) if len(sys.argv) > 1 else 500
    seed = int(sys.argv[2]) if len(sys.argv) > 2 else 1
    hard = (sys.argv[3] == "hard") if len(sys.argv) > 3 else False
    noise = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0
    short_opt = (sys.argv[5] == "short") if len(sys.argv) > 5 else False
    rng = random.Random(seed)

    repo = Repo(DICT_PATH)
    lines = []
    lines.append(f"词库：{len(repo.map)} 条")
    lines.append(f"模式：{'困难（干扰项=释义最相近的词）' if hard else '简单（干扰项=随机词）'}")
    lines.append("")

    hit = [0] * len(STRATEGIES)
    fused_hit = 0
    fused_answered = 0
    answered_hit = 0
    wrong_total = 0
    noisy_total = 0
    noisy_answered = 0
    noisy_hit = 0

    for _ in range(total):
        word, meaning = rng.choice(repo.list)
        distractors = pick_distractors(repo, word, meaning, rng, hard)
        if len(distractors) < 3:
            continue

        options = [meaning] + distractors
        if short_opt:
            options = [first_sense(o) for o in options]
        idx = list(range(4))
        rng.shuffle(idx)
        shuffled = [options[i] for i in idx]
        gold = idx.index(0)

        # 按概率把题干单词"认错"，模拟 OCR 出错
        corrupted = rng.random() < noise
        shown_stem = ocr_noise(word, rng) if corrupted else word
        if corrupted:
            noisy_total += 1

        raw_text = shown_stem + "\n" + "\n".join(
            f"{'ABCD'[i]}. {t}" for i, t in enumerate(shuffled))
        q = parse_labeled(raw_text)
        if not q:
            continue

        best, conf, raw_scores, fin = solve(q, repo)
        for k in range(len(STRATEGIES)):
            if best_of(raw_scores[k]) == gold:
                hit[k] += 1
        if best == gold:
            fused_hit += 1
        if best >= 0:
            fused_answered += 1
            if best == gold:
                answered_hit += 1
            else:
                wrong_total += 1
            if corrupted:
                noisy_answered += 1
                if best == gold:
                    noisy_hit += 1

    lines.append(f"样本 {total} 题")
    lines.append("")
    lines.append("单个策略准确率：")
    for k, (name, w, _) in enumerate(STRATEGIES):
        lines.append(f"  {name}（权重 {w}）: {hit[k] / total * 100:5.1f}%")
    lines.append("")
    lines.append("融合后：")
    lines.append(f"  总体准确率        : {fused_hit / total * 100:5.1f}%")
    if fused_answered:
        lines.append(f"  敢答的部分里答对  : {answered_hit / fused_answered * 100:5.1f}%"
                     f"（{fused_answered}/{total} 题敢答）")
    lines.append(f"  放弃作答          : {total - fused_answered}/{total} 题"
                 f"（宁可不答也不乱点）")
    lines.append(f"  答错              : {wrong_total}/{total} 题")
    if noisy_total:
        lines.append("")
        lines.append(f"OCR 出错的那 {noisy_total} 题：")
        lines.append(f"  其中敢答 {noisy_answered} 题，答对 {noisy_hit} 题")
    report = "\n".join(lines)
    print(report)
    (ROOT / "tools" / "sim_result.txt").write_text(report + "\n", encoding="utf-8")


def best_of(arr):
    b, bv = -1, 0.0
    for i, v in enumerate(arr):
        if v > bv:
            b, bv = i, v
    return b


if __name__ == "__main__":
    main()
