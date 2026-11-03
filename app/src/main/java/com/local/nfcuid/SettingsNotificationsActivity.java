package com.local.nfcuid;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Set;

public class SettingsNotificationsActivity extends AppCompatActivity {

    private TextView permStatus;
    private LinearLayout mutedList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);

        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("通知设置");
        title.setTextSize(32);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(20), dp(20), dp(20), dp(12));
        root.addView(title);

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(pad, 0, pad, pad);

        MaterialCardView card = card();
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(0, 0, 0, 0);

        inner.addView(switchRow("新卡通知", "贴到未保存的卡时通知",
                Prefs.notifyNewCard(this), c -> Prefs.setNotifyNewCard(this, c)));
        inner.addView(divider());
        inner.addView(switchRow("已存卡通知", "贴到已保存的卡时通知",
                Prefs.notifySavedCard(this), c -> Prefs.setNotifySavedCard(this, c)));
        inner.addView(divider());
        inner.addView(permRow());

        card.addView(inner);
        ll.addView(card);

        TextView mutedTitle = new TextView(this);
        mutedTitle.setText("已静默的卡");
        mutedTitle.setTextSize(13);
        mutedTitle.setPadding(dp(8), dp(24), dp(8), dp(8));
        ll.addView(mutedTitle);

        MaterialCardView mutedCard = card();
        mutedList = new LinearLayout(this);
        mutedList.setOrientation(LinearLayout.VERTICAL);
        mutedList.setPadding(0, dp(8), 0, dp(8));
        mutedCard.addView(mutedList);
        ll.addView(mutedCard);

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
        renderMuted();
        updatePermStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermStatus();
    }

    private void renderMuted() {
        mutedList.removeAllViews();
        Set<String> muted = Prefs.muted(this);
        if (muted.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("没有静默的卡");
            empty.setTextSize(14);
            empty.setPadding(dp(20), dp(14), dp(20), dp(14));
            mutedList.addView(empty);
            return;
        }
        int i = 0;
        for (String uid : muted) {
            if (i++ > 0) mutedList.addView(divider());
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(20), dp(14), dp(20), dp(14));
            row.setBackgroundResource(R.drawable.ripple_row);

            TextView t = new TextView(this);
            t.setText(uid);
            t.setTextSize(15);
            t.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(t);

            TextView action = new TextView(this);
            action.setText("恢复");
            action.setTextSize(14);
            row.addView(action);

            row.setOnClickListener(v -> {
                Prefs.unmute(this, uid);
                renderMuted();
            });
            mutedList.addView(row);
        }
    }

    private void updatePermStatus() {
        boolean enabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        permStatus.setText(enabled ? "已开启" : "未开启，点击去系统设置");
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private int themeColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private Drawable ripple() {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorControlHighlight, tv, true);
        return new RippleDrawable(ColorStateList.valueOf(tv.data), null,
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE));
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

    private View divider() {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f)));
        lp.leftMargin = dp(60);
        v.setLayoutParams(lp);
        v.setBackgroundColor(
                themeColor(com.google.android.material.R.attr.colorOutlineVariant));
        return v;
    }

    private View switchRow(String titleText, String subtitle, boolean checked,
                           java.util.function.Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(10), dp(16), dp(10));
        row.setBackgroundResource(R.drawable.ripple_row);

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

        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, isChecked) -> onChange.accept(isChecked));
        row.addView(sw);
        return row;
    }

    private View permRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(10), dp(16), dp(10));
        row.setBackgroundResource(R.drawable.ripple_row);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText("通知权限");
        t.setTextSize(16);
        col.addView(t);

        permStatus = new TextView(this);
        permStatus.setTextSize(12);
        permStatus.setPadding(0, dp(2), 0, 0);
        col.addView(permStatus);

        row.addView(col);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(22);
        row.addView(arrow);

        row.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            try {
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show();
            }
        });
        return row;
    }
}
