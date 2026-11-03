package com.local.nfcuid;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsRouteActivity extends AppCompatActivity {

    private static final String[] KEY_LABELS = {
            "AID 路由", "ISODEP 路由", "MIFARE 路由",
            "FELICA 路由", "SYSCODE 路由", "OFFHOST 路由"
    };
    private static final String[] KEY_NAMES = {
            "DEFAULT_AID_ROUTE", "DEFAULT_ISODEP_ROUTE", "DEFAULT_MIFARE_CLT_ROUTE",
            "DEFAULT_FELICA_CLT_ROUTE", "DEFAULT_SYS_CODE_ROUTE", "DEFAULT_OFFHOST_ROUTE"
    };
    private static final String[] ROUTE_LABELS = {
            "host (0x00)", "eSE (0x01)", "UICC1 (0x02)", "UICC2 (0x03)"
    };
    private static final String[] ROUTE_SHORT = {"host", "eSE", "UICC1", "UICC2"};
    private static final String SCRIPT = "/data/adb/nfc-uid/set.sh";

    private TextView summary;
    private TextView uidValue;
    private final TextView[] routeValues = new TextView[6];
    private final int[] routeIndex = new int[]{0, 0, 0, 0, 0, 0};
    private String uid = "";
    private android.widget.PopupWindow popupRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);

        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("UID 与路由");
        title.setTextSize(32);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(20), dp(20), dp(20), dp(12));
        root.addView(title);

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(pad, 0, pad, pad);

        MaterialCardView summaryCard = card();
        summaryCard.setCardBackgroundColor(
                themeColor(com.google.android.material.R.attr.colorPrimaryContainer));
        LinearLayout sumInner = new LinearLayout(this);
        sumInner.setOrientation(LinearLayout.VERTICAL);
        sumInner.setPadding(dp(20), dp(16), dp(20), dp(16));
        summary = new TextView(this);
        summary.setTextSize(15);
        summary.setTextColor(
                themeColor(com.google.android.material.R.attr.colorOnPrimaryContainer));
        summary.setText("读取中…");
        sumInner.addView(summary);
        summaryCard.addView(sumInner);
        ll.addView(summaryCard);

        MaterialCardView uidCard = card();
        LinearLayout uidInner = new LinearLayout(this);
        uidInner.setOrientation(LinearLayout.VERTICAL);
        uidInner.setPadding(0, dp(4), 0, dp(4));
        uidValue = new TextView(this);
        uidInner.addView(makeRow("UID", uidValue, v -> showUidDialog(), false));
        uidCard.addView(uidInner);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = dp(16);
        ll.addView(uidCard, ulp);

        MaterialCardView card = card();
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(0, dp(4), 0, dp(4));
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            TextView value = new TextView(this);
            routeValues[i] = value;
            inner.addView(makeRow(KEY_LABELS[i], value, v -> showRoutePicker(idx, v)));
            if (i < 5) inner.addView(divider());
        }
        card.addView(inner);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(16);
        ll.addView(card, clp);

        MaterialButton apply = new MaterialButton(this);
        apply.setText("应用");
        apply.setOnClickListener(v -> applySettings());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(24);
        ll.addView(apply, ap);

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
        loadCurrent();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private int themeColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private MaterialCardView card() {
        MaterialCardView c = new MaterialCardView(this);
        c.setCardElevation(0f);
        c.setRadius(dp(32));
        c.setClipToOutline(true);
        c.setCardBackgroundColor(
                themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh));
        c.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private Drawable ripple() {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorControlHighlight, tv, true);
        return new RippleDrawable(ColorStateList.valueOf(tv.data),
                new GradientDrawable(),
                null);
    }

    private View divider() {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f)));
        lp.leftMargin = dp(20);
        lp.rightMargin = dp(20);
        v.setLayoutParams(lp);
        v.setBackgroundColor(
                themeColor(com.google.android.material.R.attr.colorOutlineVariant));
        return v;
    }

    private View makeRow(String label, TextView valueView, View.OnClickListener onClick) {
        return makeRow(label, valueView, onClick, true);
    }

    private View makeRow(String label, TextView valueView,
                         View.OnClickListener onClick, boolean showArrow) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(15), dp(16), dp(15));
        row.setBackground(ripple());

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(16);
        labelView.setTextColor(
                themeColor(com.google.android.material.R.attr.colorOnSurface));
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(labelView);

        valueView.setTextSize(15);
        valueView.setTextColor(
                themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        row.addView(valueView);

        if (showArrow) {
            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextSize(20);
            arrow.setPadding(dp(8), 0, 0, 0);
            arrow.setTextColor(
                    themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            row.addView(arrow);
        }

        row.setOnClickListener(onClick);
        return row;
    }

    private void showUidDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint("8 位十六进制");
        input.setText(uid);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("UID")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String v = input.getText().toString()
                            .replace(":", "").replace(" ", "").trim().toUpperCase();
                    if (!v.matches("[0-9A-F]{8}")) {
                        Toast.makeText(this, "需要 8 位十六进制", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    uid = v;
                    uidValue.setText(v);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRoutePicker(int idx, View anchor) {
        LinearLayout content = (LinearLayout) getLayoutInflater()
                .inflate(R.layout.popup_route, null, false);

        int popupW = dp(240);
        for (int i = 0; i < ROUTE_LABELS.length; i++) {
            final int position = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(20), dp(14), dp(16), dp(14));

            TextView text = new TextView(this);
            text.setText(ROUTE_LABELS[i]);
            text.setTextSize(15);
            boolean selected = i == routeIndex[idx];
            text.setTextColor(selected
                    ? themeColor(com.google.android.material.R.attr.colorPrimary)
                    : themeColor(com.google.android.material.R.attr.colorOnSurface));
            text.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(text);

            ImageView check = new ImageView(this);
            check.setImageResource(R.drawable.ic_check);
            check.setColorFilter(themeColor(
                    com.google.android.material.R.attr.colorPrimary));
            check.setVisibility(selected ? View.VISIBLE : View.GONE);
            row.addView(check, new LinearLayout.LayoutParams(dp(20), dp(20)));

            row.setOnClickListener(v -> {
                routeIndex[idx] = position;
                routeValues[idx].setText(ROUTE_LABELS[position]);
                updateSummary();
                if (popupRef != null) popupRef.dismiss();
            });
            content.addView(row);
        }

        android.widget.PopupWindow popup = new android.widget.PopupWindow(
                content, popupW, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(getDrawable(R.drawable.popup_bg));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(8));
        popupRef = popup;

        popup.showAsDropDown(anchor, Math.max(0, anchor.getWidth() - popupW),
                -anchor.getHeight());
    }

    private void loadCurrent() {
        new Thread(() -> {
            String conf = su("cat /vendor/etc/libnfc-nxp.conf");
            String foundUid = null;
            int[] routes = new int[]{-1, -1, -1, -1, -1, -1};

            Matcher m = Pattern.compile(
                    "33,\\s*04,\\s*([0-9A-Fa-f]{2}),\\s*([0-9A-Fa-f]{2}),\\s*([0-9A-Fa-f]{2}),\\s*([0-9A-Fa-f]{2})")
                    .matcher(conf);
            if (m.find()) {
                foundUid = (m.group(1) + m.group(2) + m.group(3) + m.group(4)).toUpperCase();
            }
            for (int i = 0; i < 6; i++) {
                Matcher r = Pattern.compile(
                        "^" + KEY_NAMES[i] + "=0x0([0-3])", Pattern.MULTILINE).matcher(conf);
                if (r.find()) routes[i] = Integer.parseInt(r.group(1));
            }

            final String fuid = foundUid;
            final int[] froutes = routes;
            runOnUiThread(() -> {
                if (fuid != null) {
                    uid = fuid;
                    uidValue.setText(fuid);
                }
                for (int i = 0; i < 6; i++) {
                    if (froutes[i] >= 0 && froutes[i] < ROUTE_LABELS.length) {
                        routeIndex[i] = froutes[i];
                        routeValues[i].setText(ROUTE_LABELS[froutes[i]]);
                    }
                }
                updateSummary();
            });
        }).start();
    }

    private void updateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("UID  ").append(uid.isEmpty() ? "未知" : uid).append('\n');
        sb.append("A:").append(shortRoute(routeIndex[0]))
                .append("  I:").append(shortRoute(routeIndex[1]))
                .append("  M:").append(shortRoute(routeIndex[2])).append('\n');
        sb.append("F:").append(shortRoute(routeIndex[3]))
                .append("  S:").append(shortRoute(routeIndex[4]))
                .append("  O:").append(shortRoute(routeIndex[5]));
        summary.setText(sb.toString());
    }

    private static String shortRoute(int r) {
        return (r >= 0 && r < ROUTE_SHORT.length) ? ROUTE_SHORT[r] : "?";
    }

    private void applySettings() {
        if (!uid.matches("[0-9A-F]{8}")) {
            Toast.makeText(this, "UID 格式错误：需要 8 位十六进制", Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder sb = new StringBuilder(SCRIPT + " route " + uid);
        for (int i = 0; i < 6; i++) {
            sb.append(' ').append(routeIndex[i]);
        }
        final String cmd = sb.toString();
        Toast.makeText(this, "应用中，约 15 秒…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String out = su(cmd);
            runOnUiThread(() -> Toast.makeText(this, out.trim(), Toast.LENGTH_LONG).show());
        }).start();
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
