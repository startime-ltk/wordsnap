package com.litukang.wordsnap;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.litukang.wordsnap.capture.ScreenCaptureService;
import com.litukang.wordsnap.data.WordBookStore;
import com.litukang.wordsnap.data.WordRepository;
import com.litukang.wordsnap.ui.SolverActivity;
import com.litukang.wordsnap.ui.TrainingActivity;
import com.litukang.wordsnap.ui.WordBookActivity;
import com.litukang.wordsnap.update.UpdateChecker;
import com.litukang.wordsnap.util.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQ_CAPTURE = 1001;
    private static final int REQ_IMPORT = 1002;
    private static final int REQ_OVERLAY = 1003;
    private static final int REQ_NOTIFY = 1004;

    private WordRepository repo;
    private WordBookStore book;
    private MediaProjectionManager mpm;
    private TextView statusView;
    private TextView statView;
    private TextView bookView;
    private TextView versionView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        repo = WordRepository.get(this);
        book = new WordBookStore(this);
        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        setContentView(buildUi());
        askNotificationPermission();
        refresh();
        if (repo.size() == 0) {
            statusView.setText("词库：加载中…");
            repo.loadAsync(this, (count, src) -> runOnUiThread(this::refresh));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20));

        TextView title = Ui.text(this, "词拍 WordSnap", 22f, Ui.TEXT_MAIN, true);
        root.addView(title);

        TextView sub = Ui.text(this, "截屏 → 端侧 OCR → 本地词库查义，全程离线",
                14f, Ui.TEXT_SUB, false);
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 6));
        root.addView(sub);

        versionView = Ui.text(this, "当前版本 v" + versionName(), 13f, Ui.TEXT_SUB, false);
        versionView.setPadding(0, 0, 0, Ui.dp(this, 12));
        root.addView(versionView);

        statusView = Ui.text(this, "词库：未加载", 15f, Ui.BLUE, true);
        root.addView(statusView);

        statView = Ui.text(this, "", 13f, Ui.TEXT_SUB, false);
        statView.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        root.addView(statView);

        bookView = Ui.text(this, "", 13f, Ui.TEXT_SUB, false);
        bookView.setPadding(0, 0, 0, Ui.dp(this, 10));
        root.addView(bookView);

        root.addView(Ui.button(this, "开始截屏识词", v -> startCapture()));
        root.addView(Ui.button(this, "识屏答题引擎（算法实验台）", v ->
                startActivity(new Intent(this, SolverActivity.class))));
        root.addView(Ui.button(this, "计时训练（7 题 / 每题 10 秒）", v ->
                startActivity(new Intent(this, TrainingActivity.class))));
        root.addView(Ui.button(this, "生词本", v ->
                startActivity(new Intent(this, WordBookActivity.class))));
        root.addView(Ui.button(this, "导入完整四级词库（CSV）", v -> pickCsv()));

        Button reload = Ui.button(this, "重新加载词库", v -> {
            statusView.setText("词库：加载中…");
            repo.loadAsync(this, (count, src) -> runOnUiThread(this::refresh));
        });
        root.addView(reload);
        root.addView(Ui.button(this, "检查更新", v -> checkUpdate()));

        TextView tips = Ui.text(this,
                "说明：\n"
                        + "· 截屏只在你点按钮时发生一次，用来查词和记生词，不会代替你在任何对战或考试里作答。\n"
                        + "· 想在对战里变快，用「计时训练」练反应，这才是真本事。\n"
                        + "· 首次使用请允许：截屏授权 + 悬浮窗（悬浮窗用于显示结果，不给也能用通知查看）。\n"
                        + "· vivo/OriginOS 需要在 i 管家里把本 App 加进后台高耗电白名单，否则服务容易被杀。",
                13f, Ui.TEXT_SUB, false);
        tips.setPadding(0, Ui.dp(this, 16), 0, 0);
        tips.setLineSpacing(Ui.dp(this, 2), 1.1f);
        root.addView(tips);

        sv.addView(root);
        return sv;
    }

    private void refresh() {
        if (statusView == null) {
            return;
        }
        if (repo.size() == 0) {
            statusView.setText("词库：未加载");
        } else {
            statusView.setText("词库：" + repo.size() + " 条（" + repo.source() + "）");
        }
        statView.setText(book.summary());
        int n = book.count();
        bookView.setText(n == 0 ? "生词本：空" : "生词本：" + n + " 词");
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** 去安装包仓库问一句最新版是多少；有新版就弹窗给下载链接（不自动下载） */
    private void checkUpdate() {
        Toast.makeText(this, "正在检查更新…", Toast.LENGTH_SHORT).show();
        UpdateChecker.check(versionName(), (latest, note) -> {
            if (latest == null) {
                Toast.makeText(this, note, Toast.LENGTH_LONG).show();
                return;
            }
            versionView.setText("当前版本 v" + versionName() + "　·　最新版本 v" + latest);
            if (!UpdateChecker.isNewer(latest, versionName())) {
                Toast.makeText(this, "已经是最新版 v" + versionName(), Toast.LENGTH_SHORT).show();
                return;
            }
            final String link = UpdateChecker.fastUrl();
            new AlertDialog.Builder(this)
                    .setTitle("发现新版本 v" + latest)
                    .setMessage("新包和现在这个用同一张签名证书，可以直接覆盖安装，不用卸载。\n\n"
                            + "加速链接走国内镜像，校园网下更快。")
                    .setPositiveButton("加速下载", (d, w) -> openUrl(link))
                    .setNeutralButton("原链", (d, w) -> openUrl(UpdateChecker.APK_LATEST))
                    .setNegativeButton("稍后", null)
                    .show();
        });
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "没装浏览器，链接：" + url, Toast.LENGTH_LONG).show();
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
            }
        }
    }

    private void startCapture() {
        if (repo.size() == 0) {
            Toast.makeText(this, "词库还没准备好，稍等或先导入", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许悬浮窗权限（否则结果只能走通知）", Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
            return;
        }
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    private void pickCsv() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "选择词库 CSV"), REQ_IMPORT);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                ScreenCaptureService.start(this, resultCode, data);
            }
        } else if (requestCode == REQ_IMPORT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importCsv(data.getData());
            }
        } else if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
            }
        }
    }

    private void importCsv(Uri uri) {
        statusView.setText("词库：导入中…");
        new Thread(() -> {
            boolean ok = false;
            String err = null;
            File target = new File(getFilesDir(), WordRepository.DICT_FILE);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(target)) {
                if (in == null) {
                    throw new java.io.IOException("无法读取所选文件");
                }
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                ok = true;
            } catch (Exception e) {
                err = e.getMessage();
                // 写了一半的文件会让词库加载读到残缺数据，必须删掉，回落到内置词库
                try {
                    target.delete();
                } catch (Exception ignored) {
                }
            }
            final boolean finalOk = ok;
            final String finalErr = err;
            runOnUiThread(() -> {
                if (!finalOk) {
                    Toast.makeText(this, "导入失败：" + finalErr, Toast.LENGTH_LONG).show();
                }
                repo.loadAsync(this, (count, src) -> runOnUiThread(() -> {
                    refresh();
                    Toast.makeText(this, finalOk ? "已加载 " + count + " 条（" + src + "）" : "加载失败",
                            Toast.LENGTH_SHORT).show();
                }));
            });
        }).start();
    }

}
