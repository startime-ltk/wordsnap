package com.litukang.wordsnap.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 生词本 + 训练成绩，存 SharedPreferences，轻量、无需数据库。 */
public class WordBookStore {

    private static final String PREFS = "wordsnap";
    private static final String KEY_BOOK = "wordbook";
    private static final String KEY_BEST_AVG = "best_avg_ms";
    private static final String KEY_LAST = "last_session";
    private static final String KEY_ROUNDS = "rounds";

    private final SharedPreferences prefs;

    public WordBookStore(Context c) {
        prefs = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 条目格式：单词|时间戳 */
    public void add(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        String key = WordRepository.normalize(word);
        if (key.isEmpty()) {
            return;
        }
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_BOOK, new HashSet<>()));
        set.removeIf(item -> item.startsWith(key + "|"));
        set.add(key + "|" + System.currentTimeMillis());
        prefs.edit().putStringSet(KEY_BOOK, set).apply();
    }

    public void remove(String word) {
        String key = WordRepository.normalize(word);
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_BOOK, new HashSet<>()));
        set.removeIf(item -> item.startsWith(key + "|"));
        prefs.edit().putStringSet(KEY_BOOK, set).apply();
    }

    public void clear() {
        prefs.edit().putStringSet(KEY_BOOK, new HashSet<>()).apply();
    }

    /** 最近加入的排前面 */
    public List<String> words() {
        List<String[]> items = new ArrayList<>();
        for (String item : prefs.getStringSet(KEY_BOOK, new HashSet<>())) {
            int p = item.indexOf('|');
            if (p > 0) {
                items.add(new String[]{item.substring(0, p), item.substring(p + 1)});
            }
        }
        items.sort((a, b) -> Long.compare(parseLong(b[1]), parseLong(a[1])));
        List<String> out = new ArrayList<>();
        for (String[] it : items) {
            out.add(it[0]);
        }
        return out;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public int count() {
        return prefs.getStringSet(KEY_BOOK, new HashSet<>()).size();
    }

    public void saveSession(int correct, int total, long avgMs) {
        long best = prefs.getLong(KEY_BEST_AVG, 0L);
        SharedPreferences.Editor e = prefs.edit();
        if (best == 0L || avgMs < best) {
            e.putLong(KEY_BEST_AVG, avgMs);
        }
        e.putString(KEY_LAST, correct + "/" + total + " 正确，平均 " + fmt(avgMs) + " 秒");
        e.putInt(KEY_ROUNDS, prefs.getInt(KEY_ROUNDS, 0) + 1);
        e.apply();
    }

    public String summary() {
        int rounds = prefs.getInt(KEY_ROUNDS, 0);
        if (rounds == 0) {
            return "还没有训练记录";
        }
        long best = prefs.getLong(KEY_BEST_AVG, 0L);
        String last = prefs.getString(KEY_LAST, "");
        return "已练 " + rounds + " 局 · 最佳平均 " + fmt(best) + " 秒 · 上局 " + last;
    }

    public static String fmt(long ms) {
        return String.format(java.util.Locale.CHINA, "%.2f", ms / 1000.0);
    }
}
