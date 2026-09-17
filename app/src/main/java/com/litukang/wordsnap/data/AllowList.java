package com.litukang.wordsnap.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 自动作答的目标白名单。
 *
 * 设计上刻意做成"空着就不工作"：默认情况下 enabled=false、白名单为空，
 * 无障碍服务拿到什么都不会点。要用就得自己打开开关、自己填包名。
 * 这样"在哪用"的决定权在你手上，而不是被预置进代码里。
 */
public final class AllowList {

    private static final String PREF = "auto_answer";
    private static final String KEY_ON = "enabled";
    private static final String KEY_PKGS = "packages";

    private AllowList() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(KEY_ON, false);
    }

    public static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_ON, on).apply();
    }

    /** 白名单里的包名（小写，已去空格），没有就返回空列表 */
    public static List<String> packages(Context c) {
        String raw = prefs(c).getString(KEY_PKGS, "");
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return out;
        }
        for (String p : raw.split("[,，\\s;；]+")) {
            String t = p.trim().toLowerCase(Locale.US);
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public static void setPackages(Context c, String csv) {
        prefs(c).edit().putString(KEY_PKGS, csv == null ? "" : csv.trim()).apply();
    }

    public static boolean contains(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        String target = pkg.toLowerCase(Locale.US);
        for (String p : packages(c)) {
            if (p.equals(target)) {
                return true;
            }
        }
        return false;
    }

    public static String asText(Context c) {
        return String.join(", ", packages(c));
    }
}
