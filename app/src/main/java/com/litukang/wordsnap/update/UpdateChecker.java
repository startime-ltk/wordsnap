package com.litukang.wordsnap.update;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * App 内「检查更新」：去统一的安装包仓库问一句「最新版是多少」。
 *
 * 数据来源（按先后顺序试，谁的先答就用谁）：
 *   1. 仓库根目录的 versions.json —— 发布脚本每次发版都会刷新，格式最稳；
 *   2. wordsnap-latest 这个 Release 的说明文字里的「当前指向：**x.y**」—— 老发版的兜底。
 *
 * 只读，不上传任何东西，更不会自己偷偷下载安装；只是告诉用户有没有新版。
 */
public final class UpdateChecker {

    /** 统一安装包仓库 */
    public static final String OWNER_REPO = "startime-ltk/apk";
    /** 固定直链：永远指向最新版 */
    public static final String APK_LATEST = "https://github.com/" + OWNER_REPO
            + "/releases/download/wordsnap-latest/WordSnap-latest.apk";
    /** 校园网 / 国内网络的加速前缀（原链下不动时用它） */
    public static final String MIRROR = "https://ghfast.top/";

    private static final String API = "https://api.github.com/repos/" + OWNER_REPO;
    private static final int TIMEOUT_MS = 8000;
    private static final String UA = "WordSnap-Android";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Pattern IN_BODY =
            Pattern.compile("当前指向：\\s*\\*\\*([0-9][0-9.]*)\\*\\*");

    private UpdateChecker() {
    }

    /** 主线程回调：latest 为 null 表示这次没查到（note 里是人话原因）。 */
    public interface Callback {
        void onResult(String latest, String note);
    }

    /** 加速直链（优先给用户点这个） */
    public static String fastUrl() {
        return MIRROR + APK_LATEST;
    }

    public static void check(final String currentVersion, final Callback cb) {
        new Thread(() -> {
            String latest = null;
            String note = null;
            try {
                latest = fromVersionsJson();
            } catch (Exception ignored) {
                // 没这个文件或没网，走下面兜底
            }
            if (TextUtils.isEmpty(latest)) {
                try {
                    latest = fromLatestRelease();
                } catch (Exception e) {
                    note = "连不上更新服务器，检查下网络再试";
                }
            }
            final String fLatest = latest;
            final String fNote = note;
            MAIN.post(() -> {
                if (TextUtils.isEmpty(fLatest)) {
                    cb.onResult(null, fNote == null ? "没读到版本信息，稍后再试" : fNote);
                } else if (isNewer(fLatest, currentVersion)) {
                    cb.onResult(fLatest, "发现新版本 v" + fLatest
                            + "（当前 v" + currentVersion + "），可以覆盖安装");
                } else {
                    cb.onResult(fLatest, "已经是最新版 v" + currentVersion);
                }
            });
        }).start();
    }

    /** versions.json 形如 {"wordsnap": {"version": "1.3", ...}} */
    private static String fromVersionsJson() throws Exception {
        String txt = httpGet(API + "/contents/versions.json", "application/vnd.github.raw");
        JSONObject me = new JSONObject(txt).optJSONObject("wordsnap");
        if (me == null) {
            return null;
        }
        String v = me.optString("version", "");
        return v.isEmpty() ? null : v;
    }

    private static String fromLatestRelease() throws Exception {
        String txt = httpGet(API + "/releases/tags/wordsnap-latest",
                "application/vnd.github+json");
        Matcher m = IN_BODY.matcher(new JSONObject(txt).optString("body", ""));
        return m.find() ? m.group(1) : null;
    }

    private static String httpGet(String url, String accept) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", accept);
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + code);
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bo.write(buf, 0, n);
                }
                return new String(bo.toByteArray(), "UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    /** 1.10 > 1.9 > 1.2，按点分段比大小 */
    public static boolean isNewer(String latest, String current) {
        int[] a = parts(latest);
        int[] b = parts(current);
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int[] parts(String v) {
        if (v == null) {
            return new int[0];
        }
        String[] ps = v.trim().split("\\.");
        int[] r = new int[ps.length];
        for (int i = 0; i < ps.length; i++) {
            try {
                r[i] = Integer.parseInt(ps[i].replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                r[i] = 0;
            }
        }
        return r;
    }
}
