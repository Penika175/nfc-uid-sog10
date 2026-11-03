package com.local.nfcuid;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "cards";
    private static final String KEY = "list";
    private static final String SCRIPT = "/data/adb/nfc-uid/set.sh";
    private static final int REQ_NOTIF = 100;
    private static final int REQ_PICK = 200;

    private static final String[] KEY_NAMES = {
            "DEFAULT_AID_ROUTE", "DEFAULT_ISODEP_ROUTE", "DEFAULT_MIFARE_CLT_ROUTE",
            "DEFAULT_FELICA_CLT_ROUTE", "DEFAULT_SYS_CODE_ROUTE", "DEFAULT_OFFHOST_ROUTE"
    };
    private static final String[] ROUTE_SHORT = {"host", "eSE", "UICC1", "UICC2"};

    private NfcAdapter nfcAdapter;
    private TextView status;
    private TextView cardName;
    private LinearLayout tagRow;
    private LinearLayout cardList;
    private LinearLayout sheetList;
    private com.google.android.material.card.MaterialCardView statusCard;
    private FrameLayout statusFrame;
    private ImageView statusCover;
    private BottomSheetBehavior<LinearLayout> sheetBehavior;
    private String pendingCoverUid;



    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Card> items = new ArrayList<>();
    private String currentUid;
    private int[] currentRoutes = new int[]{-1, -1, -1, -1, -1, -1};

    private EditText activeInput;
    private EditText activeNameInput;

    static class Card {
        String name;
        String uid;

        Card(String name, String uid) {
            this.name = name;
            this.uid = uid;
        }

        String entry() {
            return name + "  " + uid;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        cardName = findViewById(R.id.cardName);
        tagRow = findViewById(R.id.tagRow);
        cardList = findViewById(R.id.cardList);
        sheetList = findViewById(R.id.sheetList);
        statusCard = findViewById(R.id.statusCard);
        statusFrame = findViewById(R.id.statusFrame);
        statusCover = findViewById(R.id.statusCover);
        setupStatusCard();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        LinearLayout sheet = findViewById(R.id.sheet);
        sheetBehavior = BottomSheetBehavior.from(sheet);
        sheetBehavior.setHideable(false);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        final android.view.View sheetScroll = findViewById(R.id.sheetScroll);
        findViewById(R.id.sheetTitle).setOnClickListener(v -> {
            int st = sheetBehavior.getState();
            if (st == BottomSheetBehavior.STATE_EXPANDED) {
                sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            } else {
                sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                sheetScroll.post(() ->
                        ((androidx.core.widget.NestedScrollView) sheetScroll).scrollTo(0, 0));
            }
        });

        findViewById(R.id.btn_manual).setOnClickListener(v -> showManualInput(null, null, true));
        findViewById(R.id.btn_advanced).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        NotificationHelper.ensureChannels(this);
        askNotificationPermission();
        loadCards();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCurrent();
        enableDispatch();
        updateNotifHint();
    }

    @Override
    protected void onPause() {
        super.onPause();
        disableDispatch();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private int themeColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    // ---------- dispatch ----------

    private void enableDispatch() {
        if (nfcAdapter == null || !nfcAdapter.isEnabled()) return;
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent, flags);

        IntentFilter[] filters = {
                new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        };
        String[][] techLists = {
                {NfcA.class.getName()},
                {NfcB.class.getName()},
                {NfcF.class.getName()},
                {NfcV.class.getName()},
                {IsoDep.class.getName()},
                {MifareClassic.class.getName()},
                {MifareUltralight.class.getName()},
                {Ndef.class.getName()},
                {NdefFormatable.class.getName()}
        };
        try {
            nfcAdapter.enableForegroundDispatch(this, pending, filters, techLists);
        } catch (Exception ignored) {
        }
    }

    private void disableDispatch() {
        if (nfcAdapter == null) return;
        try {
            nfcAdapter.disableForegroundDispatch(this);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;

        String action = intent.getStringExtra("action");
        String extraUid = intent.getStringExtra("uid");
        if (extraUid != null) {
            Card c = findCard(extraUid);
            String name = c == null ? null : c.name;
            if ("switch".equals(action)) {
                applyUid(extraUid);
            } else if ("save".equals(action)) {
                showManualInput(extraUid, name, false);
            } else {
                showManualInput(extraUid, name, true);
            }
            return;
        }

        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) return;
        onCardRead(bytesToHex(tag.getId()));
    }

    private void onCardRead(String uid) {
        if (activeInput != null) {
            activeInput.setText(uid);
            Card c = findCard(uid);
            if (c != null && activeNameInput != null && activeNameInput.getText().length() == 0) {
                activeNameInput.setText(c.name);
            }
            return;
        }
        Card c = findCard(uid);
        if (c != null) {
            if (Prefs.notifySavedCard(this) && !Prefs.isMuted(this, uid)) {
                NotificationHelper.notifySavedCard(this, uid, c.name);
            }
        } else if (Prefs.notifyNewCard(this)) {
            NotificationHelper.notifyNewCard(this, uid);
        }
    }

    // ---------- notifications ----------

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private void updateNotifHint() {
        View hint = findViewById(R.id.notifHint);
        boolean enabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        hint.setVisibility(enabled ? View.GONE : View.VISIBLE);
        hint.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            try {
                startActivity(i);
            } catch (Exception ignored) {
            }
        });
    }

    // ---------- ui ----------

    private TextView pill(String text, int bg, int fg) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(fg);
        tv.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(14));
        d.setColor(bg);
        tv.setBackground(d);
        return tv;
    }

    private void renderTags() {
        tagRow.removeAllViews();
        int bg = themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest);
        int fg = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant);
        int mifare = currentRoutes[2];
        int felica = currentRoutes[3];
        if (mifare >= 0) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            tagRow.addView(pill("MIFARE → " + shortRoute(mifare), bg, fg), lp);
        }
        if (felica >= 0) {
            tagRow.addView(pill("FELICA → " + shortRoute(felica), bg, fg));
        }
    }

    private static String shortRoute(int r) {
        return (r >= 0 && r < ROUTE_SHORT.length) ? ROUTE_SHORT[r] : "?";
    }

    private void refreshCurrent() {
        new Thread(() -> {
            String conf = su("grep -E '33, 04,|^DEFAULT_.*_ROUTE=0x' /vendor/etc/libnfc-nxp.conf");
            String uid = null;
            int[] routes = new int[]{-1, -1, -1, -1, -1, -1};

            Matcher m = Pattern.compile(
                    "33,\\s*04,\\s*([0-9A-Fa-f]{2}),\\s*([0-9A-Fa-f]{2}),\\s*([0-9A-Fa-f]{2}),\\s*([0-9A-Fa-f]{2})")
                    .matcher(conf);
            if (m.find()) {
                uid = (m.group(1) + m.group(2) + m.group(3) + m.group(4)).toUpperCase();
            }
            for (int i = 0; i < 6; i++) {
                Matcher r = Pattern.compile(
                        "^" + KEY_NAMES[i] + "=0x0([0-3])", Pattern.MULTILINE).matcher(conf);
                if (r.find()) routes[i] = Integer.parseInt(r.group(1));
            }

            final String fuid = uid;
            final int[] froutes = routes;
            runOnUiThread(() -> {
                currentUid = fuid;
                currentRoutes = froutes;
                status.setText(fuid == null ? "--------" : fuid);
                renderTags();
                renderCardList();
            });
        }).start();
    }

    private void renderCardList() {
        cardList.removeAllViews();
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有保存的卡");
            empty.setTextSize(14);
            empty.setPadding(dp(20), dp(8), dp(20), dp(16));
            empty.setTextColor(themeColor(android.R.attr.textColorSecondary));
            cardList.addView(empty);
        }
        for (int i = 0; i < items.size(); i++) {
            Card c = items.get(i);
            boolean active = c.uid.equals(currentUid);
            View row = LayoutInflater.from(this).inflate(R.layout.item_card_row, cardList, false);
            TextView name = row.findViewById(R.id.card_name);
            TextView uidView = row.findViewById(R.id.card_uid);
            ImageView check = row.findViewById(R.id.card_check);
            String suffix = Prefs.isMuted(this, c.uid) ? "  🔕" : "";
            name.setText(c.name + suffix);
            uidView.setText(c.uid);
            check.setVisibility(active ? View.VISIBLE : View.GONE);
            if (active) {
                uidView.setTextColor(themeColor(com.google.android.material.R.attr.colorPrimary));
            }
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(
                    com.google.android.material.R.attr.colorControlHighlight, tv, true);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(tv.data), null,
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));
            row.setOnClickListener(v -> applyUid(c.uid));
            row.setOnLongClickListener(v -> {
                showCardMenu(c);
                return true;
            });
            cardList.addView(row);
            if (i < items.size() - 1) {
                View div = new View(this);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f)));
                dlp.leftMargin = dp(20);
                div.setLayoutParams(dlp);
                div.setBackgroundColor(
                        themeColor(com.google.android.material.R.attr.colorOutlineVariant));
                cardList.addView(div);
            }
        }
        Card current = findCard(currentUid);
        status.setText(current != null ? current.name
                : (currentUid == null ? "--------" : currentUid));
        cardName.setVisibility(View.GONE);
        refreshStatusCard();
        renderSheet();
    }

    private void setupStatusCard() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int cardW = screenW - dp(32);
        int cardH = Math.round(cardW / 1.585f);
        ViewGroup.LayoutParams lp = statusCard.getLayoutParams();
        lp.height = cardH;
        statusCard.setLayoutParams(lp);
    }

    private void refreshStatusCard() {
        File f = currentUid == null ? null : coverFile(currentUid);
        if (f != null && f.exists()) {
            statusCover.setVisibility(View.VISIBLE);
            statusCover.setImageURI(Uri.fromFile(f));
            statusFrame.setBackground(null);
        } else {
            statusCover.setVisibility(View.GONE);
            GradientDrawable g = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{
                            themeColor(com.google.android.material.R.attr.colorPrimary),
                            themeColor(com.google.android.material.R.attr.colorTertiary)});
            g.setCornerRadius(dp(32));
            statusFrame.setBackground(g);
        }
    }

    // ---------- bottom sheet ----------

    private void renderSheet() {
        if (sheetList == null) return;
        sheetList.removeAllViews();
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有保存的卡");
            empty.setTextSize(14);
            empty.setPadding(dp(8), dp(8), dp(8), dp(24));
            empty.setTextColor(themeColor(android.R.attr.textColorSecondary));
            sheetList.addView(empty);
            return;
        }
        for (Card c : items) {
            View v = LayoutInflater.from(this).inflate(R.layout.item_sheet_card, sheetList, false);
            ImageView cover = v.findViewById(R.id.card_cover);
            TextView initial = v.findViewById(R.id.card_initial);
            TextView title = v.findViewById(R.id.card_title);
            TextView sub = v.findViewById(R.id.card_sub);
            ImageView check = v.findViewById(R.id.card_check);

            File f = coverFile(c.uid);
            if (f.exists()) {
                cover.setVisibility(View.VISIBLE);
                initial.setVisibility(View.GONE);
                cover.setImageURI(Uri.fromFile(f));
            } else {
                cover.setVisibility(View.GONE);
                initial.setVisibility(View.VISIBLE);
                initial.setText(firstChar(c.name));
                GradientDrawable g = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{
                                themeColor(com.google.android.material.R.attr.colorPrimary),
                                themeColor(com.google.android.material.R.attr.colorTertiary)});
                g.setCornerRadius(dp(20));
                v.setBackground(g);
            }
            title.setText(c.name);
            sub.setText(c.uid);
            check.setVisibility(
                    c.uid.equalsIgnoreCase(currentUid) ? View.VISIBLE : View.GONE);

            int screenW = getResources().getDisplayMetrics().widthPixels;
            int cardW = screenW - dp(32);
            int cardH = Math.round(cardW / 1.585f);
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.width = cardW;
            lp.height = cardH;
            v.setLayoutParams(lp);

            v.setOnClickListener(x -> applyUid(c.uid));
            v.setOnLongClickListener(x -> {
                showCoverMenu(c);
                return true;
            });
            sheetList.addView(v);
        }
    }

    private static String firstChar(String s) {
        if (s == null || s.isEmpty()) return "?";
        return s.substring(0, 1).toUpperCase();
    }

    private File coverFile(String uid) {
        File dir = new File(getFilesDir(), "covers");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, uid + ".jpg");
    }

    private void saveCover(String uid, Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return;
            android.graphics.Bitmap src = android.graphics.BitmapFactory.decodeStream(in);
            if (src == null) {
                Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show();
                return;
            }
            int w = src.getWidth();
            int h = src.getHeight();
            final float target = 1.585f;
            int cw;
            int ch;
            if (w / (float) h > target) {
                ch = h;
                cw = Math.round(h * target);
            } else {
                cw = w;
                ch = Math.round(w / target);
            }
            int x = Math.max(0, (w - cw) / 2);
            int y = Math.max(0, (h - ch) / 2);
            android.graphics.Bitmap cropped =
                    android.graphics.Bitmap.createBitmap(src, x, y, cw, ch);
            try (FileOutputStream out = new FileOutputStream(coverFile(uid))) {
                cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out);
            }
            if (cropped != src) cropped.recycle();
            src.recycle();
            Toast.makeText(this, "封面已更新", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存封面失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCoverMenu(Card c) {
        String[] opts = {"设置封面", "移除封面", "删除卡片"};
        showActionSheet(c.name, opts, which -> {
            if (which == 0) {
                pendingCoverUid = c.uid;
                Intent pick = new Intent(Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                pick.setType("image/*");
                try {
                    startActivityForResult(pick, REQ_PICK);
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开图片选择器",
                            Toast.LENGTH_SHORT).show();
                }
            } else if (which == 1) {
                coverFile(c.uid).delete();
                renderSheet();
                refreshStatusCard();
            } else {
                confirmDelete(c);
            }
        });
    }

    private void showCardMenu(Card c) {
        List<String> options = new ArrayList<>();
        options.add("切换到此卡");
        if (Prefs.isMuted(this, c.uid)) options.add("恢复通知");
        else options.add("不再通知此卡");
        options.add("删除");
        showActionSheet(c.name, options.toArray(new String[0]), which -> {
            String opt = options.get(which);
            if (opt.startsWith("切换")) {
                applyUid(c.uid);
            } else if (opt.startsWith("恢复")) {
                Prefs.unmute(this, c.uid);
                renderCardList();
            } else if (opt.startsWith("不再")) {
                Prefs.mute(this, c.uid);
                renderCardList();
            } else {
                confirmDelete(c);
            }
        });
    }

    private interface SheetPick {
        void onPick(int which);
    }

    /** Material 3 Expressive styled action sheet. */
    private void showActionSheet(String title, String[] labels, SheetPick pick) {
        com.google.android.material.bottomsheet.BottomSheetDialog d =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(22));

        View handle = new View(this);
        GradientDrawable hg = new GradientDrawable();
        hg.setCornerRadius(dp(2));
        hg.setColor(themeColor(com.google.android.material.R.attr.colorOutlineVariant));
        handle.setBackground(hg);
        LinearLayout.LayoutParams hlp =
                new LinearLayout.LayoutParams(dp(36), dp(4));
        hlp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = dp(4);
        hlp.bottomMargin = dp(10);
        box.addView(handle, hlp);

        if (title != null && !title.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(title);
            tv.setTextSize(17);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setPadding(dp(18), dp(6), dp(18), dp(14));
            tv.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
            box.addView(tv);
        }

        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(16), dp(18), dp(16));

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(dp(20));
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(
                    com.google.android.material.R.attr.colorControlHighlight, tv, true);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(tv.data),
                    rowBg, null));

            TextView t = new TextView(this);
            t.setText(labels[i]);
            t.setTextSize(16);
            t.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
            t.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(t);

            boolean danger = labels[i].contains("删除");
            if (danger) {
                t.setTextColor(themeColor(com.google.android.material.R.attr.colorError));
            }

            row.setOnClickListener(v -> {
                d.dismiss();
                pick.onPick(which);
            });
            box.addView(row);
        }

        d.setContentView(box);
        d.show();
    }

    private void confirmDelete(Card c) {
        new AlertDialog.Builder(this)
                .setTitle("删除")
                .setMessage("删除 " + c.name + " ?")
                .setPositiveButton("删除", (d, w) -> removeCard(c))
                .setNegativeButton("取消", null)
                .show();
    }

    private Card findCard(String uid) {
        for (Card c : items) {
            if (c.uid.equalsIgnoreCase(uid)) return c;
        }
        return null;
    }

    // ---------- manual input ----------

    private void showManualInput(String presetUid, String presetName, boolean andUse) {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(20), dp(8), dp(20), 0);

        EditText uidInput = new EditText(this);
        uidInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        uidInput.setHint("卡号（8 位十六进制）");
        uidInput.setText(presetUid != null ? presetUid : (currentUid != null ? currentUid : ""));
        ll.addView(uidInput);

        EditText nameInput = new EditText(this);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setHint("名称（可留空）");
        if (presetName != null) nameInput.setText(presetName);
        ll.addView(nameInput);

        activeInput = uidInput;
        activeNameInput = nameInput;

        new AlertDialog.Builder(this)
                .setTitle("手动输入卡号")
                .setMessage("贴一张卡可自动填入")
                .setView(ll)
                .setPositiveButton(andUse ? "保存并应用" : "保存", (d, w) -> {
                    String uid = normalizeUid(uidInput.getText().toString());
                    if (uid == null) {
                        Toast.makeText(this, "格式错误：需要 8 位十六进制", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) name = uid;
                    saveCard(name, uid);
                    if (andUse) applyUid(uid);
                })
                .setNegativeButton("取消", null)
                .setOnDismissListener(d -> {
                    activeInput = null;
                    activeNameInput = null;
                })
                .show();
    }

    private static String normalizeUid(String raw) {
        if (raw == null) return null;
        String s = raw.replace(":", "").replace(" ", "").replace("-", "").trim().toUpperCase();
        return s.matches("[0-9A-F]{8}") ? s : null;
    }

    // ---------- data ----------

    private void loadCards() {
        items.clear();
        Set<String> saved = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(KEY, Collections.emptySet());
        for (String s : saved) {
            int sep = s.indexOf("  ");
            if (sep > 0) items.add(new Card(s.substring(0, sep), s.substring(sep + 2)));
            else items.add(new Card(s, s));
        }
        Collections.sort(items, (a, b) -> a.name.compareToIgnoreCase(b.name));
        renderCardList();
    }

    private void saveCard(String name, String uid) {
        Set<String> saved = new HashSet<>(
                getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet(KEY, Collections.emptySet()));
        saved.removeIf(s -> s.endsWith("  " + uid));
        saved.add(name + "  " + uid);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(KEY, saved).apply();
        loadCards();
        Toast.makeText(this, "已保存 " + name, Toast.LENGTH_SHORT).show();
    }

    private void removeCard(Card c) {
        Set<String> saved = new HashSet<>(
                getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet(KEY, Collections.emptySet()));
        saved.remove(c.entry());
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(KEY, saved).apply();
        loadCards();
    }

    private void applyUid(String uid) {
        showLoading("正在切换到 " + uid);
        new Thread(() -> {
            String out = su(SCRIPT + " " + uid);
            main.post(() -> {
                hideLoading();
                Toast.makeText(this, out.trim(), Toast.LENGTH_SHORT).show();
                refreshCurrent();
            });
        }).start();
    }

    private Dialog loadingDialog;

    private void showLoading(String text) {
        if (loadingDialog != null || isFinishing()) return;
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER);
        int p = dp(28);
        box.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(32));
        bg.setColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh));
        box.setBackground(bg);

        android.widget.ProgressBar spinner = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        int size = dp(72);
        box.addView(spinner, new LinearLayout.LayoutParams(size, size));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(14);
        box.addView(tv, tlp);

        d.setContentView(box);
        Window win = d.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.dimAmount = 0.45f;
            win.setAttributes(lp);
        }
        d.setCancelable(false);
        d.show();
        loadingDialog = d;
    }

    private void hideLoading() {
        if (loadingDialog != null) {
            try {
                loadingDialog.dismiss();
            } catch (Exception ignored) {
            }
            loadingDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        hideLoading();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null && pendingCoverUid != null) {
                saveCover(pendingCoverUid, uri);
                renderSheet();
            }
            pendingCoverUid = null;
        }
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

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }
}
