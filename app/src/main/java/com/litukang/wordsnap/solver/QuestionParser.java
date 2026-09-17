package com.litukang.wordsnap.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 OCR 出来的一坨文本切成「题干 + 选项」。
 *
 * 两条路，按顺序尝试：
 *   1) 有标号：认 "A. xxx" / "A、xxx" / "A) xxx" / "A：xxx" / "① xxx"
 *      注意分隔符是必选的 —— 否则 "abandon" 会被误判成 "A. bandon"。
 *   2) 没标号：按语言特征猜。题干和选项通常一个是英文、一个是中文，
 *      找出唯一那个"落单"的英文行（或中文行）当题干，其余当选项。
 */
public final class QuestionParser {

    private QuestionParser() {
    }

    private static final Pattern LABELED =
            Pattern.compile("^\\s*([A-Z])\\s*[.、)\\]:：]\\s*(.+?)\\s*$");

    private static final Pattern CIRCLED =
            Pattern.compile("^\\s*([①-⑧])\\s*(.+?)\\s*$");

    private static final String[] LABELS = {"A", "B", "C", "D", "E", "F", "G", "H"};

    public static Question parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new Question("", null, raw);
        }
        List<String> lines = new ArrayList<>();
        for (String l : raw.split("\\r?\\n")) {
            String t = l.trim();
            if (!t.isEmpty()) {
                lines.add(t);
            }
        }
        if (lines.isEmpty()) {
            return new Question("", null, raw);
        }
        Question byLabel = parseLabeled(lines, raw);
        if (byLabel != null && byLabel.valid()) {
            return byLabel;
        }
        return parseHeuristic(lines, raw);
    }

    /** 从一堆 OCR 行里解析（实时截屏走这条） */
    public static Question parseLines(List<String> lines) {
        return parse(lines == null ? "" : String.join("\n", lines));
    }

    private static Question parseLabeled(List<String> lines, String raw) {
        List<Question.Option> opts = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (String line : lines) {
            if (opts.size() >= LABELS.length) {
                others.add(line);
                continue;
            }
            Matcher m = LABELED.matcher(line);
            if (m.matches()) {
                opts.add(new Question.Option(LABELS[opts.size()], m.group(2).trim()));
                continue;
            }
            Matcher c = CIRCLED.matcher(line);
            if (c.matches()) {
                opts.add(new Question.Option(LABELS[opts.size()], c.group(2).trim()));
                continue;
            }
            others.add(line);
        }
        if (opts.size() < 2) {
            return null;
        }
        String stem = pickStem(others, opts);
        if (stem.isEmpty()) {
            stem = "(题干未识别)";
        }
        return new Question(stem, opts, raw);
    }

    /**
     * 从非选项行里挑题干：选项以中文为主时，题干应该是英文行，反之亦然。
     * 选不出来就退回"全部非选项行拼起来"。
     */
    private static String pickStem(List<String> others, List<Question.Option> opts) {
        int cnOptions = 0;
        for (Question.Option o : opts) {
            if (Algo.hasChinese(o.text)) {
                cnOptions++;
            }
        }
        boolean optionsAreChinese = cnOptions * 2 >= opts.size();

        List<String> preferred = new ArrayList<>();
        for (String s : others) {
            boolean cn = Algo.hasChinese(s);
            if (optionsAreChinese ? !cn : cn) {
                preferred.add(s);
            }
        }
        List<String> pool = preferred.isEmpty() ? others : preferred;
        if (pool.isEmpty()) {
            return "";
        }
        // 题干通常是最短的那行（就一个单词 / 一句释义），多余的是 UI 文字
        return shortest(pool);
    }

    private static String shortest(List<String> pool) {
        String best = pool.get(0);
        for (String s : pool) {
            if (s.length() < best.length()) {
                best = s;
            }
        }
        return best;
    }

    private static Question parseHeuristic(List<String> lines, String raw) {
        List<String> en = new ArrayList<>();
        List<String> zh = new ArrayList<>();
        for (String l : lines) {
            if (Algo.hasChinese(l)) {
                zh.add(l);
            } else {
                en.add(l);
            }
        }

        String stem;
        List<String> optLines;
        if (!en.isEmpty() && zh.size() >= 2) {
            // 典型：题干一个英文单词，选项四个中文释义
            stem = shortest(en);
            optLines = zh;
        } else if (zh.size() == 1 && en.size() >= 2) {
            // 反过来：题干中文释义，选项四个英文单词
            stem = zh.get(0);
            optLines = en;
        } else if (en.size() >= 3) {
            stem = en.get(0);
            optLines = new ArrayList<>(en.subList(1, en.size()));
        } else {
            stem = lines.get(0);
            optLines = new ArrayList<>(lines.subList(1, lines.size()));
        }

        List<Question.Option> opts = new ArrayList<>();
        for (int i = 0; i < optLines.size() && i < LABELS.length; i++) {
            opts.add(new Question.Option(LABELS[i], optLines.get(i).trim()));
        }
        return new Question(stem, opts, raw);
    }
}
