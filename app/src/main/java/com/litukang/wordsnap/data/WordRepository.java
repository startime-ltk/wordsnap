package com.litukang.wordsnap.data;

import android.content.Context;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 本地词库：单词 -> 中文释义。
 *
 * 数据来源优先级：
 *   1) 用户导入的文件（内部存储 cet4.csv，通过"导入词库"写入）
 *   2) APK 内置 assets/cet4.csv（样例，约 120 词，用于验证链路）
 *
 * 文件行格式（逗号或制表符分隔均可，UTF-8）：
 *   company,n. 公司
 *   adequate,adj. 足够的
 */
public class WordRepository {

    public static final String DICT_FILE = "cet4.csv";

    public static class Entry {
        public final String word;
        public final String meaning;

        public Entry(String word, String meaning) {
            this.word = word;
            this.meaning = meaning;
        }
    }

    public interface LoadCallback {
        void onLoaded(int count, String source);
    }

    private static volatile WordRepository instance;

    private final HashMap<String, String> map = new HashMap<>();
    private final ArrayList<Entry> list = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean ready = false;
    private volatile String source = "未加载";

    private WordRepository() {
    }

    public static WordRepository get(Context c) {
        if (instance == null) {
            synchronized (WordRepository.class) {
                if (instance == null) {
                    instance = new WordRepository();
                }
            }
        }
        return instance;
    }

    public boolean isReady() {
        return ready;
    }

    public int size() {
        return list.size();
    }

    public String source() {
        return source;
    }

    public void loadAsync(Context c, LoadCallback cb) {
        Context app = c.getApplicationContext();
        io.execute(() -> {
            int n = load(app);
            if (cb != null) {
                cb.onLoaded(n, source);
            }
        });
    }

    private synchronized int load(Context c) {
        map.clear();
        list.clear();
        ready = false;

        File imported = new File(c.getFilesDir(), DICT_FILE);
        String src = "内置样例";
        InputStream in = null;
        try {
            if (imported.exists() && imported.length() > 0) {
                in = new FileInputStream(imported);
                src = "已导入";
            } else {
                in = c.getAssets().open(DICT_FILE);
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) {
                    first = false;
                    if (!line.isEmpty() && line.charAt(0) == '﻿') {
                        line = line.substring(1); // 去 UTF-8 BOM
                    }
                }
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int comma = line.indexOf(',');
                int tab = line.indexOf('\t');
                int split = -1;
                if (comma >= 0 && tab >= 0) {
                    split = Math.min(comma, tab);
                } else {
                    split = Math.max(comma, tab);
                }
                if (split <= 0) {
                    continue;
                }
                String w = normalize(line.substring(0, split));
                String m = line.substring(split + 1).trim();
                if (w.length() < 2 || m.isEmpty()) {
                    continue;
                }
                if (!map.containsKey(w)) {
                    map.put(w, m);
                    list.add(new Entry(w, m));
                }
            }
        } catch (IOException e) {
            source = "读取失败：" + e.getMessage();
            return 0;
        } finally {
            closeQuietly(in);
        }
        source = src;
        ready = !list.isEmpty();
        return list.size();
    }

    /** 只保留小写英文字母 */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.US).replaceAll("[^a-z]", "");
    }

    /** 查词；查不到会尝试还原常见词形（复数/过去式/进行时等） */
    public String lookup(String raw) {
        String w = normalize(raw);
        if (w.length() < 2) {
            return null;
        }
        String m = map.get(w);
        if (m != null) {
            return m;
        }
        for (String candidate : variants(w)) {
            m = map.get(candidate);
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    /** 返回用于展示的原词（若词库里有，用词库里的拼写） */
    public String canonicalOf(String raw) {
        String w = normalize(raw);
        if (map.containsKey(w)) {
            return w;
        }
        for (String candidate : variants(w)) {
            if (map.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** 词形还原候选（复数/过去式/进行时/比较级/副词等），答题引擎的模糊匹配要用 */
    public static List<String> variants(String w) {
        List<String> out = new ArrayList<>();
        add(out, w);
        if (w.endsWith("ies") && w.length() > 4) {
            add(out, w.substring(0, w.length() - 3) + "y");
        }
        if (w.endsWith("ves") && w.length() > 4) {
            add(out, w.substring(0, w.length() - 3) + "f");
            add(out, w.substring(0, w.length() - 3) + "fe");
        }
        if (w.endsWith("es") && w.length() > 3) {
            add(out, w.substring(0, w.length() - 2));
            add(out, w.substring(0, w.length() - 1));
        }
        if (w.endsWith("s") && !w.endsWith("ss") && w.length() > 3) {
            add(out, w.substring(0, w.length() - 1));
        }
        if (w.endsWith("ed") && w.length() > 4) {
            String base = w.substring(0, w.length() - 2);
            add(out, base);
            add(out, base + "e");
            add(out, stripDoubled(base));
        }
        if (w.endsWith("ing") && w.length() > 5) {
            String base = w.substring(0, w.length() - 3);
            add(out, base);
            add(out, base + "e");
            add(out, stripDoubled(base));
        }
        if (w.endsWith("er") && w.length() > 4) {
            add(out, w.substring(0, w.length() - 2));
        }
        if (w.endsWith("est") && w.length() > 5) {
            add(out, w.substring(0, w.length() - 3));
        }
        if (w.endsWith("ly") && w.length() > 4) {
            add(out, w.substring(0, w.length() - 2));
        }
        return out;
    }

    private static String stripDoubled(String s) {
        int n = s.length();
        if (n >= 3 && s.charAt(n - 1) == s.charAt(n - 2)) {
            return s.substring(0, n - 1);
        }
        return s;
    }

    private static void add(List<String> out, String s) {
        if (s != null && s.length() >= 2 && !out.contains(s)) {
            out.add(s);
        }
    }

    /** 随机取 n 个不同词条（excludeWord 可为 null） */
    public List<Entry> randomDistinct(int n, Random r, String excludeWord) {
        ArrayList<Entry> out = new ArrayList<>();
        if (list.isEmpty()) {
            return out;
        }
        Set<String> used = new HashSet<>();
        if (excludeWord != null) {
            used.add(normalize(excludeWord));
        }
        int guard = 0;
        while (out.size() < n && guard++ < 200) {
            Entry e = list.get(r.nextInt(list.size()));
            if (used.add(e.word)) {
                out.add(e);
            }
        }
        return out;
    }

    public Entry random(Random r) {
        if (list.isEmpty()) {
            return null;
        }
        return list.get(r.nextInt(list.size()));
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
            }
        }
    }
}
