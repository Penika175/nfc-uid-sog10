package com.local.nfcuid;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SettingsActivity extends AppCompatActivity {

    private static final String SCRIPT = "/data/adb/nfc-uid/set.sh";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);

        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(32);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(20), dp(20), dp(20), dp(12));
        root.addView(title);

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(pad, 0, pad, pad);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(0f);
        card.setRadius(dp(32));
        card.setClipToOutline(true);
        card.setCardBackgroundColor(
                themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(0, 0, 0, 0);

        inner.addView(menuRow(R.drawable.ic_bell, "通知设置", "新卡、已存卡、通知权限",
                v -> startActivity(new Intent(this, SettingsNotificationsActivity.class))));
        inner.addView(divider());
        inner.addView(menuRow(R.drawable.ic_tag, "UID 与路由", currentSummary(),
                v -> startActivity(new Intent(this, SettingsRouteActivity.class))));
        inner.addView(divider());
        inner.addView(menuRow(R.drawable.ic_refresh, "恢复默认配置", "还原到原厂 NFC 配置",
                v -> confirmReset()));
        inner.addView(divider());
        inner.addView(menuRow(R.drawable.ic_nfc, "关于应用", "版本信息与说明",
                v -> startActivity(new Intent(this, AboutActivity.class))));

        card.addView(inner);
        ll.addView(card);

        TextView hint = new TextView(this);
        hint.setText("UID 与路由决定卡的模拟方式；恢复默认配置会清除全部修改。");
        hint.setTextSize(12);
        hint.setPadding(dp(8), dp(12), dp(8), 0);
        ll.addView(hint);

        ScrollView sv = new ScrollView(this);
        sv.setClipToPadding(false);
        sv.addView(ll);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, 0);
            return insets;
        });

        setContentView(root);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private int themeColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private View divider() {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f)));
        lp.leftMargin = dp(60);
        v.setLayoutParams(lp);
        v.setBackgroundColor(themeColor(com.google.android.material.R.attr.colorOutlineVariant));
        return v;
    }

    private Drawable ripple() {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorControlHighlight, tv, true);
        return new RippleDrawable(ColorStateList.valueOf(tv.data), null,
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE));
    }

    private View menuRow(int iconRes, String titleText, String subtitle,
                         View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(9), dp(16), dp(9));
        row.setBackgroundResource(R.drawable.ripple_row);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(24), dp(24));
        ilp.rightMargin = dp(16);
        row.addView(icon, ilp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(titleText);
        t.setTextSize(16);
        col.addView(t);

        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextSize(12);
        s.setPadding(0, dp(2), 0, 0);
        col.addView(s);

        row.addView(col);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(22);
        row.addView(arrow);

        row.setOnClickListener(onClick);
        return row;
    }

    private String currentSummary() {
        String out = su("grep -m1 '33, 04,' /vendor/etc/libnfc-nxp.conf");
        for (String line : out.split("\n")) {
            if (line.contains("33,")) {
                String[] parts = line.replace(",", " ").trim().split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < parts.length && sb.length() < 8; i++) {
                    sb.append(parts[i].toUpperCase());
                }
                if (sb.length() == 8) return "当前 " + sb;
            }
        }
        return "UID、六条路由";
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("恢复默认配置")
                .setMessage("将恢复到原厂配置，UID 也会变回 01020304。\n\n确定？")
                .setPositiveButton("确定", (d, w) -> {
                    Toast.makeText(this, "恢复中，约 15 秒…", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        String out = su(SCRIPT + " reset");
                        runOnUiThread(() ->
                                Toast.makeText(this, out.trim(), Toast.LENGTH_LONG).show());
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String su(String cmd) {
        StringBuilder sb = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            p.waitFor();
        } catch (Exception e) {
            sb.append("失败: ").append(e.getMessage());
        }
        return sb.toString();
    }
}
