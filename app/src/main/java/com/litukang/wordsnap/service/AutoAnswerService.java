package com.litukang.wordsnap.service;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.litukang.wordsnap.data.AllowList;
import com.litukang.wordsnap.data.WordRepository;
import com.litukang.wordsnap.solver.Answer;
import com.litukang.wordsnap.solver.AnswerEngine;
import com.litukang.wordsnap.solver.Question;
import com.litukang.wordsnap.solver.QuestionParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 自动作答（可选模块，默认关闭）。
 *
 * 三重门槛，缺一不可，所以装完不动它的话它就是个摆设：
 *   1) 系统「无障碍服务」开关 —— 必须由你在设置里手动打开；
 *   2) App 内的总开关 —— AllowList.enabled，默认 false；
 *   3) 目标包名白名单 —— 默认空，不填包名任何界面都不会被点。
 *
 * 另外还有两个保护：
 *   - 置信度低于 CONF_MIN 不点（宁可不答，也不乱点）；
 *   - 连续 400 ms 内不重复触发，且同一个答案 3 秒内不重复点。
 *
 * 这个服务拿到的是"屏幕上的文字"（跟读屏软件同一套接口），
 * 不注入、不 hook、不改内存，只做和自动化测试一样的事：找到控件 → 点一下。
 */
public class AutoAnswerService extends AccessibilityService {

    private static final long MIN_INTERVAL_MS = 400;
    private static final long SAME_ANSWER_GUARD_MS = 3000;
    private static final double CONF_MIN = 0.35;

    private long lastRun = 0;
    private String lastClicked = "";
    private long lastClickedAt = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }
        runOnce();
    }

    private void runOnce() {
        if (!AllowList.isEnabled(this)) {
            return;
        }
        String pkg = currentPackage();
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        if (pkg.equals(getPackageName())) {
            return; // 不自己点自己
        }
        if (!AllowList.contains(this, pkg)) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastRun < MIN_INTERVAL_MS) {
            return;
        }
        lastRun = now;

        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
        } catch (Exception ignored) {
        }
        if (root == null) {
            return;
        }

        List<String> lines = new ArrayList<>();
        collectText(root, lines, 0);
        if (lines.size() < 3) {
            return;
        }

        WordRepository repo = WordRepository.get(this);
        if (repo.size() == 0) {
            return; // 词库没好就别乱猜
        }

        Question q = QuestionParser.parseLines(lines);
        if (!q.valid()) {
            return;
        }
        Answer a = AnswerEngine.solve(q, repo);
        if (!a.hasAnswer() || a.confidence < CONF_MIN) {
            return;
        }

        String target = a.bestText();
        if (target.isEmpty()) {
            return;
        }
        if (target.equals(lastClicked)
                && SystemClock.elapsedRealtime() - lastClickedAt < SAME_ANSWER_GUARD_MS) {
            return;
        }

        AccessibilityNodeInfo node = findNode(root, target);
        if (node == null) {
            return;
        }
        AccessibilityNodeInfo clickable = clickableAncestor(node);
        if (clickable == null) {
            return;
        }
        boolean ok = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (ok) {
            lastClicked = target;
            lastClickedAt = SystemClock.elapsedRealtime();
        }
    }

    private String currentPackage() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                CharSequence cs = root.getPackageName();
                return cs == null ? null : cs.toString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void collectText(AccessibilityNodeInfo node, List<String> out, int depth) {
        if (node == null || depth > 24 || out.size() > 80) {
            return;
        }
        CharSequence t = node.getText();
        if (t != null) {
            String s = t.toString().trim();
            if (!s.isEmpty() && !out.contains(s)) {
                out.add(s);
            }
        }
        CharSequence d = node.getContentDescription();
        if (d != null) {
            String s = d.toString().trim();
            if (!s.isEmpty() && !out.contains(s)) {
                out.add(s);
            }
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            collectText(node.getChild(i), out, depth + 1);
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US).replaceAll("\\s+", "");
    }

    /** 找文本能跟选项对上的控件（先精确后包含，容忍 OCR/视图上的空格差异） */
    private static AccessibilityNodeInfo findNode(AccessibilityNodeInfo root, String text) {
        String want = norm(text);
        if (want.isEmpty()) {
            return null;
        }
        AccessibilityNodeInfo hit = search(root, want, 0, true);
        if (hit == null) {
            hit = search(root, want, 0, false);
        }
        return hit;
    }

    private static AccessibilityNodeInfo search(AccessibilityNodeInfo node, String want,
                                                int depth, boolean exact) {
        if (node == null || depth > 24) {
            return null;
        }
        CharSequence t = node.getText();
        if (t != null) {
            String s = norm(t.toString());
            if (!s.isEmpty()) {
                if (exact ? s.equals(want) : (s.contains(want) || want.contains(s))) {
                    return node;
                }
            }
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo r = search(node.getChild(i), want, depth + 1, exact);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    /** 文字节点本身常常不可点，往上找到最近的可点击祖先 */
    private static AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        for (int i = 0; cur != null && i < 8; i++) {
            if (cur.isClickable() && cur.isEnabled()) {
                return cur;
            }
            AccessibilityNodeInfo p = cur.getParent();
            if (p == cur) {
                break;
            }
            cur = p;
        }
        return null;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 仍走 XML 配置，这里只是确保服务起来后状态是可预期的
        }
    }
}
