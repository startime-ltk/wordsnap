package com.litukang.wordsnap.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 纯代码构建 UI 的小工具，避免写一堆 XML 布局。 */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return (int) (c.getResources().getDisplayMetrics().density * v + 0.5f);
    }

    public static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams lp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    public static LinearLayout.LayoutParams lpMatch() {
        return lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    public static Button button(Context c, String label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16f);
        b.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        LinearLayout.LayoutParams p = lpMatch();
        p.setMargins(0, Ui.dp(c, 8), 0, 0);
        b.setLayoutParams(p);
        if (l != null) {
            b.setOnClickListener(l);
        }
        return b;
    }

    /** 圆角卡片背景 */
    public static GradientDrawable card(Context c, int bgColor, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(bgColor);
        d.setCornerRadius(Ui.dp(c, 12));
        d.setStroke(Math.max(1, Ui.dp(c, 1)), strokeColor);
        return d;
    }

    /** 带内边距的水平容器 */
    public static LinearLayout row(Context c, int padDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(Ui.dp(c, padDp), Ui.dp(c, padDp), Ui.dp(c, padDp), Ui.dp(c, padDp));
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static final int TEXT_MAIN = Color.parseColor("#1F2328");
    public static final int TEXT_SUB = Color.parseColor("#6B7280");
    public static final int GREEN = Color.parseColor("#1B8A4B");
    public static final int RED = Color.parseColor("#C0392B");
    public static final int BLUE = Color.parseColor("#1565C0");
    public static final int CARD_BG = Color.parseColor("#FFFFFF");
    public static final int CARD_STROKE = Color.parseColor("#DDE1E6");
}
