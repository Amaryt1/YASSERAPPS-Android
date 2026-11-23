package com.yasserapps.store;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * YASSERAPPS is an original Android storefront built for the owner's curated
 * apps.json catalog. No third-party store-client framework is used.
 */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(5, 8, 6);
    private static final int PANEL = Color.rgb(21, 25, 22);
    private static final int PANEL2 = Color.rgb(30, 34, 31);
    private static final int GREEN = Color.rgb(193, 255, 16);
    private static final int WHITE = Color.WHITE;
    private static final int SUBTEXT = Color.rgb(182, 188, 184);
    private static final String CHANNEL = "https://t.me/YasserAppsIQ";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService images = Executors.newFixedThreadPool(3);
    private final List<Catalog.App> apps = new ArrayList<>();
    private final Map<Long, Catalog.App> downloads = new HashMap<>();
    private final Map<Long, File> destinations = new HashMap<>();
    private FrameLayout screen;
    private String page = "home";
    private String category = "app";
    private boolean loading = true;
    private String warning;
    private Catalog.App detailApp;
    private long lastPollAt;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        showHome();
        refresh();
    }

    private int dp(float v) { return Math.round(getResources().getDisplayMetrics().density * v); }

    private GradientDrawable shape(int color, int radius, int outline) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (outline != 0) d.setStroke(dp(1), outline);
        return d;
    }

    private GradientDrawable neonGradient() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.rgb(11, 34, 13), Color.rgb(12, 17, 12), Color.rgb(3, 10, 5)});
        d.setCornerRadius(dp(19));
        d.setStroke(dp(1), Color.rgb(55, 112, 29));
        return d;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(true);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout linear(boolean vertical) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height));
    }

    private void gap(LinearLayout parent, int size) {
        View v = new View(this);
        parent.addView(v, lp(1, size));
    }

    private void padx(LinearLayout row, int size) {
        View v = new View(this);
        row.addView(v, lp(size, 1));
    }

    private TextView action(String label, boolean active, Runnable click) {
        TextView t = text(label, 14, active ? Color.BLACK : WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(shape(active ? GREEN : PANEL, 16, active ? GREEN : Color.rgb(59, 65, 59)));
        t.setPadding(dp(11), dp(12), dp(11), dp(12));
        t.setOnClickListener(v -> click.run());
        return t;
    }

    private LinearLayout verticalContent() {
        LinearLayout content = linear(true);
        content.setPadding(dp(16), dp(13), dp(16), dp(18));
        return content;
    }

    private void render(LinearLayout content, boolean footer) {
        screen = new FrameLayout(this);
        screen.setBackgroundColor(BG);
        screen.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        FrameLayout.LayoutParams s = new FrameLayout.LayoutParams(-1, -1);
        s.bottomMargin = footer ? dp(88) : 0;
        screen.addView(scroll, s);
        if (footer) addFooter(screen);
        setContentView(screen);
    }

    private void addFooter(FrameLayout parent) {
        LinearLayout dock = linear(false);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(shape(Color.rgb(21, 26, 23), 20, Color.rgb(44, 66, 38)));
        dock.setPadding(dp(3), dp(4), dp(3), dp(4));
        FrameLayout.LayoutParams pos = new FrameLayout.LayoutParams(-1, dp(70), Gravity.BOTTOM);
        pos.setMargins(dp(11), dp(2), dp(11), dp(11));
        parent.addView(dock, pos);
        nav(dock, "⌂", "الرئيسية", "home", this::showHome);
        nav(dock, "⌕", "البحث", "search", this::showSearch);
        nav(dock, "↓", "التنزيلات", "downloads", this::showDownloads);
        nav(dock, "⚙", "الإعدادات", "settings", this::showSettings);
    }

    private void nav(LinearLayout row, String icon, String label, String key, Runnable callback) {
        LinearLayout b = linear(true);
        b.setGravity(Gravity.CENTER);
        boolean active = page.equals(key);
        b.setBackground(shape(active ? Color.rgb(25, 70, 13) : PANEL, 18, active ? Color.rgb(67, 140, 17) : 0));
        TextView ico = text(icon, 25, active ? GREEN : SUBTEXT, true);
        ico.setGravity(Gravity.CENTER);
        TextView caption = text(label, 12, active ? GREEN : WHITE, active);
        caption.setGravity(Gravity.CENTER);
        b.addView(ico);
        b.addView(caption);
        b.setOnClickListener(v -> callback.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        p.setMargins(dp(2), 0, dp(2), 0);
        row.addView(b, p);
    }

    private void header(LinearLayout root) {
        LinearLayout row = linear(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.yasser_logo);
        row.addView(logo, lp(58, 58));
        padx(row, 9);
        LinearLayout words = linear(true);
        LinearLayout name = linear(false);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.addView(text("YASSER", 23, WHITE, true));
        name.addView(text("APPS", 23, GREEN, true));
        words.addView(name);
        TextView caption = text("متجر التطبيقات والألعاب للأندرويد", 11, WHITE, false);
        words.addView(caption);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        TextView telegram = text("➤", 23, GREEN, true);
        telegram.setGravity(Gravity.CENTER);
        telegram.setContentDescription("قناة YASSERAPPS");
        telegram.setOnClickListener(v -> open(CHANNEL));
        row.addView(telegram, lp(40, 46));
        root.addView(row);
        gap(root, 12);
    }

    private void searchBox(LinearLayout root) {
        LinearLayout row = linear(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(6), dp(17), dp(6));
        row.setBackground(shape(PANEL2, 22, Color.rgb(42, 60, 44)));
        row.addView(text("⌕", 26, GREEN, true), lp(34, 43));
        TextView hint = text("ابحث عن تطبيق أو لعبة...", 14, SUBTEXT, false);
        row.addView(hint, new LinearLayout.LayoutParams(0, dp(43), 1));
        row.setOnClickListener(v -> showSearch());
        root.addView(row, lp(-1, 54));
        gap(root, 13);
    }

    private void tabs(LinearLayout root) {
        LinearLayout row = linear(false);
        for (String type : new String[]{"app", "game", "updates"}) {
            String label = type.equals("app") ? "التطبيقات" : type.equals("game") ? "الألعاب" : "التحديثات";
            TextView tab = action(label, category.equals(type), () -> {
                category = type;
                if (type.equals("updates")) showUpdates();
                else showHome();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(49), 1);
            p.setMargins(dp(3), 0, dp(3), 0);
            row.addView(tab, p);
        }
        root.addView(row);
        gap(root, 15);
    }

    private boolean installedAndNewer(Catalog.App app) {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(app.packageName, 0);
            long current = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            return app.versionCode > current;
        } catch (Exception ignored) { return false; }
    }

    private List<Catalog.App> matching(String kind) {
        ArrayList<Catalog.App> list = new ArrayList<>();
        for (Catalog.App app : apps) {
            if ("updates".equals(kind)) {
                if (installedAndNewer(app)) list.add(app);
            } else if (("game".equals(kind)) == app.isGame()) list.add(app);
        }
        return list;
    }

    public void showHome() {
        page = "home";
        if ("updates".equals(category)) category = "app";
        LinearLayout content = verticalContent();
        header(content);
        searchBox(content);
        tabs(content);
        if ("app".equals(category)) {
            Catalog.App featured = null;
            for (Catalog.App a : apps) if (a.featured) { featured = a; break; }
            if (featured != null) {
                final Catalog.App highlighted = featured;
                FrameLayout hero = new FrameLayout(this);
                hero.setBackground(neonGradient());
                hero.setClipToOutline(true);
                ImageView banner = new ImageView(this);
                banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (!featured.bannerUrl.isEmpty()) image(banner, featured.bannerUrl);
                hero.addView(banner, new FrameLayout.LayoutParams(-1, -1));
                LinearLayout overlay = linear(true);
                overlay.setGravity(Gravity.CENTER_VERTICAL);
                overlay.setPadding(dp(16), dp(13), dp(16), dp(12));
                overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,
                    new int[]{0xF20A1309, 0xAA0A160B, 0x300D170C}));
                overlay.addView(text("مميز على YASSERAPPS", 13, GREEN, true));
                gap(overlay, 8);
                overlay.addView(text(featured.name, 25, WHITE, true));
                gap(overlay, 7);
                overlay.addView(text(featured.summary, 12, WHITE, false));
                gap(overlay, 8);
                overlay.addView(text("عرض التفاصيل ‹", 13, GREEN, true));
                hero.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
                hero.setOnClickListener(v -> showDetail(highlighted));
                content.addView(hero, lp(-1, 178));
            } else {
                LinearLayout intro = linear(false);
                intro.setGravity(Gravity.CENTER_VERTICAL);
                intro.setPadding(dp(16), dp(17), dp(16), dp(17));
                intro.setBackground(neonGradient());
                ImageView logo = new ImageView(this);
                logo.setImageResource(R.drawable.yasser_logo);
                intro.addView(logo, lp(70, 70));
                padx(intro, 12);
                LinearLayout box = linear(true);
                box.addView(text("YASSERAPPS", 24, GREEN, true));
                box.addView(text("أحدث التطبيقات والألعاب في مكان واحد", 13, WHITE, false));
                intro.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
                content.addView(intro, lp(-1, 160));
            }
            gap(content, 17);
            section(content, "أحدث التطبيقات", matching("app"), false);
            gap(content, 18);
            section(content, "أحدث الألعاب", matching("game"), true);
        } else {
            section(content, "أحدث الألعاب", matching("game"), false);
        }
        render(content, true);
    }

    private void section(LinearLayout root, String name, List<Catalog.App> entries, boolean compact) {
        LinearLayout title = linear(false);
        title.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(name, 21, WHITE, true);
        title.addView(heading, new LinearLayout.LayoutParams(0, dp(39), 1));
        TextView all = text("عرض الكل ‹", 13, GREEN, true);
        all.setGravity(Gravity.CENTER_VERTICAL);
        all.setOnClickListener(v -> {
            category = name.contains("الألعاب") ? "game" : "app";
            showList(name, category);
        });
        title.addView(all);
        root.addView(title);
        gap(root, 7);
        if (entries.isEmpty()) {
            String m = loading ? "جاري تحميل التطبيقات..." :
                ("game".equals(category) ? "ماكو ألعاب منشورة حاليًا." : "ماكو تطبيقات منشورة حاليًا.");
            if (warning != null) m = warning;
            panelText(root, m);
            return;
        }
        int length = compact ? Math.min(3, entries.size()) : Math.min(10, entries.size());
        for (int i = 0; i < length; i++) {
            card(root, entries.get(i));
            gap(root, 10);
        }
    }

    private void showList(String title, String kind) {
        page = "home";
        LinearLayout c = verticalContent();
        backBar(c, title, this::showHome);
        for (Catalog.App a : matching(kind)) {
            card(c, a);
            gap(c, 9);
        }
        render(c, true);
    }

    private void card(LinearLayout root, Catalog.App app) {
        LinearLayout row = linear(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setBackground(shape(PANEL, 17, Color.rgb(51, 59, 52)));
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setImageResource(R.drawable.yasser_logo);
        icon.setBackground(shape(PANEL2, 14, 0));
        image(icon, app.iconUrl);
        row.addView(icon, lp(68, 68));
        padx(row, 10);
        LinearLayout content = linear(true);
        TextView title = text(app.name, 16, WHITE, true);
        title.setMaxLines(1);
        content.addView(title);
        TextView sub = text(app.summary.isEmpty() ? "تطبيق للأندرويد" : app.summary, 12, SUBTEXT, false);
        sub.setMaxLines(2);
        content.addView(sub);
        gap(content, 6);
        content.addView(text("الإصدار " + app.version + "   •   " + size(app.sizeBytes), 11, SUBTEXT, false));
        row.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
        padx(row, 9);
        TextView install = action(installedAndNewer(app) ? "تحديث ↓" : "تثبيت ↓", true, () -> showDetail(app));
        row.addView(install, lp(81, 45));
        row.setOnClickListener(v -> showDetail(app));
        root.addView(row, lp(-1, -2));
    }

    private String size(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes >= 1073741824L) return String.format(Locale.ROOT, "%.1f GB", bytes / 1073741824.0);
        return String.format(Locale.ROOT, "%.0f MB", bytes / 1048576.0);
    }

    private void backBar(LinearLayout root, String label, Runnable back) {
        LinearLayout row = linear(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView arrow = text("‹", 36, GREEN, true);
        arrow.setGravity(Gravity.CENTER);
        arrow.setOnClickListener(v -> back.run());
        row.addView(arrow, lp(40, 50));
        TextView title = text(label, 23, WHITE, true);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1));
        root.addView(row);
        gap(root, 18);
    }

    private void panelText(LinearLayout root, String message) {
        TextView view = text(message, 13, SUBTEXT, false);
        view.setPadding(dp(16), dp(18), dp(16), dp(18));
        view.setBackground(shape(PANEL, 18, Color.rgb(49, 56, 49)));
        root.addView(view, lp(-1, -2));
    }

    private void showSearch() {
        page = "search";
        LinearLayout c = verticalContent();
        backBar(c, "البحث", this::showHome);
        EditText field = new EditText(this);
        field.setTextSize(16);
        field.setSingleLine(true);
        field.setTextColor(WHITE);
        field.setHintTextColor(SUBTEXT);
        field.setHint("ابحث عن تطبيق أو لعبة...");
        field.setPadding(dp(16), 0, dp(16), 0);
        field.setBackground(shape(PANEL2, 20, Color.rgb(53, 67, 52)));
        field.setTextDirection(View.TEXT_DIRECTION_RTL);
        c.addView(field, lp(-1, 55));
        gap(c, 19);
        LinearLayout results = linear(true);
        c.addView(results, lp(-1, -2));
        Runnable update = () -> {
            results.removeAllViews();
            String query = field.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (query.isEmpty()) {
                panelText(results, "ابحث داخل تطبيقات وألعاب YASSERAPPS المتوفرة فقط.");
                return;
            }
            int matches = 0;
            for (Catalog.App app : apps) {
                if (app.name.toLowerCase(Locale.ROOT).contains(query) ||
                    app.summary.toLowerCase(Locale.ROOT).contains(query) ||
                    app.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                    card(results, app);
                    gap(results, 10);
                    matches++;
                }
            }
            if (matches == 0) panelText(results, "ماكو نتائج داخل المتجر لهذا البحث.");
        };
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        update.run();
        render(c, true);
    }

    private void showUpdates() {
        page = "updates";
        category = "updates";
        LinearLayout c = verticalContent();
        backBar(c, "التحديثات", () -> { category = "app"; showHome(); });
        List<Catalog.App> updates = matching("updates");
        if (updates.isEmpty()) panelText(c, "ماكو تحديثات متوفرة للتطبيقات المثبتة المعروفة بالمتجر حاليًا.");
        for (Catalog.App app : updates) {
            card(c, app);
            gap(c, 10);
        }
        render(c, true);
    }

    private void detailSection(LinearLayout c, String title, String message) {
        LinearLayout panel = linear(true);
        panel.setPadding(dp(15), dp(13), dp(15), dp(14));
        panel.setBackground(shape(PANEL, 19, Color.rgb(47, 53, 47)));
        panel.addView(text(title, 18, GREEN, true));
        gap(panel, 9);
        panel.addView(text(message.isEmpty() ? "لا توجد معلومات إضافية." : message, 14, WHITE, false));
        c.addView(panel, lp(-1, -2));
    }

    private void showDetail(Catalog.App app) {
        detailApp = app;
        page = "detail";
        LinearLayout c = verticalContent();
        backBar(c, "YASSERAPPS", this::showHome);
        LinearLayout header = linear(false);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setImageResource(R.drawable.yasser_logo);
        image(icon, app.iconUrl);
        header.addView(icon, lp(107, 107));
        padx(header, 14);
        LinearLayout labels = linear(true);
        labels.addView(text(app.name, 27, WHITE, true));
        gap(labels, 7);
        labels.addView(text("إصدار " + app.version + "   •   " + size(app.sizeBytes), 13, SUBTEXT, false));
        gap(labels, 6);
        labels.addView(text(app.isGame() ? "لعبة أندرويد" : "تطبيق أندرويد", 13, GREEN, true));
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(header);
        gap(c, 20);

        TextView button = action(installedAndNewer(app) ? "تحديث التطبيق  ↓" : "تثبيت التطبيق  ↓",
            true, () -> download(app));
        button.setTextSize(18);
        c.addView(button, lp(-1, 56));
        gap(c, 9);
        TextView share = action("مشاركة التطبيق", false, () -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, app.name + "\n" + app.apkUrl);
            startActivity(Intent.createChooser(send, "مشاركة التطبيق"));
        });
        c.addView(share, lp(-1, 48));
        gap(c, 19);

        if (!app.screenshots.isEmpty()) {
            c.addView(text("صور التطبيق", 21, WHITE, true));
            gap(c, 10);
            android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(this);
            scroller.setHorizontalScrollBarEnabled(false);
            LinearLayout gallery = linear(false);
            for (String url : app.screenshots) {
                ImageView shot = new ImageView(this);
                shot.setScaleType(ImageView.ScaleType.CENTER_CROP);
                shot.setBackground(shape(PANEL, 13, Color.rgb(50, 70, 45)));
                image(shot, url);
                LinearLayout.LayoutParams p = lp(228, 152);
                p.setMargins(dp(5), 0, dp(5), 0);
                gallery.addView(shot, p);
            }
            scroller.addView(gallery);
            c.addView(scroller, lp(-1, 160));
            gap(c, 18);
        }
        detailSection(c, "الوصف", app.description.isEmpty() ? app.summary : app.description);
        gap(c, 15);
        detailSection(c, "سجل التحديثات", app.changelog.isEmpty() ? "الإصدار " + app.version : app.changelog);
        gap(c, 18);
        c.addView(text("التثبيت يتم بموافقة المستخدم عبر نظام أندرويد.", 12, SUBTEXT, false));
        render(c, false);
    }

    private void refresh() {
        loading = true;
        Catalog.load(this, result -> main.post(() -> {
            loading = false;
            apps.clear();
            apps.addAll(result.apps);
            warning = result.warning;
            if ("home".equals(page)) showHome();
            else if ("updates".equals(page)) showUpdates();
            else if ("downloads".equals(page)) showDownloads();
        }));
    }

    private void image(ImageView target, String address) {
        if (address == null || !address.startsWith("https://")) return;
        images.execute(() -> {
            Bitmap bitmap = null;
            try {
                URL url = new URL(address);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(9000);
                try (InputStream input = conn.getInputStream()) {
                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inSampleSize = 2;
                    bitmap = BitmapFactory.decodeStream(input, null, opt);
                } finally { conn.disconnect(); }
            } catch (Exception ignored) {}
            final Bitmap result = bitmap;
            if (result != null) main.post(() -> target.setImageBitmap(result));
        });
    }

    private void open(String link) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "تعذّر فتح الرابط", Toast.LENGTH_SHORT).show();
        }
    }

    private void download(Catalog.App app) {
        try {
            Uri uri = Uri.parse(app.apkUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                uri.getHost() == null || !uri.getPath().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                Toast.makeText(this, "رابط APK غير صالح", Toast.LENGTH_LONG).show();
                return;
            }
            File downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadFolder == null) throw new IllegalStateException("Download folder unavailable");
            String filename = app.packageName.replaceAll("[^a-zA-Z0-9_.-]", "_") + "_" +
                app.versionCode + ".apk";
            File output = new File(downloadFolder, filename);
            DownloadManager.Request req = new DownloadManager.Request(uri);
            req.setTitle(app.name);
            req.setDescription("YASSERAPPS");
            req.setMimeType("application/vnd.android.package-archive");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, filename);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            long id = dm.enqueue(req);
            downloads.put(id, app);
            destinations.put(id, output);
            Toast.makeText(this, "بدأ تحميل " + app.name, Toast.LENGTH_SHORT).show();
            poll(id, 0);
            showDownloads();
        } catch (Exception ex) {
            Toast.makeText(this, "تعذّر بدء التحميل: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void poll(long id, int attempts) {
        if (attempts >= 900) return;
        main.postDelayed(() -> {
            if (!downloads.containsKey(id)) return;
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
            try (android.database.Cursor cur = dm.query(query)) {
                if (cur != null && cur.moveToFirst()) {
                    int status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        Catalog.App app = downloads.get(id);
                        if (app != null) {
                            if ("downloads".equals(page)) showDownloads();
                            verifyAndInstall(id, app, destinations.get(id));
                        }
                        downloads.remove(id);
                        return;
                    }
                    if (status == DownloadManager.STATUS_FAILED) {
                        Toast.makeText(this, "فشل تحميل التطبيق", Toast.LENGTH_LONG).show();
                        downloads.remove(id);
                        if ("downloads".equals(page)) showDownloads();
                        return;
                    }
                }
            } catch (Exception ignored) { }
            poll(id, attempts + 1);
        }, 1300);
    }

    private void verifyAndInstall(long id, Catalog.App app, File file) {
        if (file == null || !file.isFile()) {
            Toast.makeText(this, "تعذّر العثور على الملف", Toast.LENGTH_LONG).show();
            return;
        }
        String expected = app.sha256 == null ? "" : app.sha256;
        if (expected.isEmpty()) {
            installDownloaded(id, app);
            return;
        }
        images.execute(() -> {
            boolean valid = false;
            try (FileInputStream stream = new FileInputStream(file)) {
                MessageDigest dig = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[32768];
                int n;
                while ((n = stream.read(buffer)) > 0) dig.update(buffer, 0, n);
                StringBuilder actual = new StringBuilder();
                for (byte b : dig.digest()) actual.append(String.format(Locale.ROOT, "%02x", b & 255));
                valid = actual.toString().equalsIgnoreCase(expected);
            } catch (Exception ignored) { }
            final boolean result = valid;
            main.post(() -> {
                if (result) installDownloaded(id, app);
                else Toast.makeText(this, "فشل فحص سلامة APK، تم منع التثبيت", Toast.LENGTH_LONG).show();
            });
        });
    }

    private void installDownloaded(long id, Catalog.App app) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this, "فعّل السماح بالتثبيت من YASSERAPPS ثم ارجع إلى التنزيلات", Toast.LENGTH_LONG).show();
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
                startActivity(i);
                return;
            }
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            Uri content = dm.getUriForDownloadedFile(id);
            if (content == null) throw new IllegalStateException("Download URI unavailable");
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(content, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, "تعذّر فتح المثبت: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showDownloads() {
        page = "downloads";
        LinearLayout c = verticalContent();
        header(c);
        c.addView(text("التنزيلات", 23, WHITE, true));
        gap(c, 15);
        if (downloads.isEmpty()) {
            panelText(c, "ماكو تنزيلات نشطة. التطبيقات اللي تختارها تظهر هنا أثناء التحميل.");
        } else for (Map.Entry<Long, Catalog.App> entry : new HashMap<>(downloads).entrySet()) {
            long id = entry.getKey();
            Catalog.App app = entry.getValue();
            LinearLayout box = linear(true);
            box.setBackground(shape(PANEL, 17, Color.rgb(48, 65, 48)));
            box.setPadding(dp(16), dp(13), dp(16), dp(13));
            box.addView(text(app.name, 18, WHITE, true));
            box.addView(text("جاري التحميل، راقب شريط الإشعارات…", 13, GREEN, false));
            box.setOnClickListener(v -> {
                File file = destinations.get(id);
                if (file != null && file.exists()) installDownloaded(id, app);
            });
            c.addView(box, lp(-1, -2));
            gap(c, 12);
        }
        render(c, true);
    }

    private void showSettings() {
        page = "settings";
        LinearLayout c = verticalContent();
        header(c);
        c.addView(text("الإعدادات", 24, WHITE, true));
        gap(c, 19);
        TextView channel = action("قناة YASSERAPPS على تيليجرام", false, () -> open(CHANNEL));
        c.addView(channel, lp(-1, 54));
        gap(c, 11);
        TextView refresh = action("تحديث قائمة التطبيقات", true, () -> {
            Toast.makeText(this, "جاري تحديث المتجر", Toast.LENGTH_SHORT).show();
            refresh();
        });
        c.addView(refresh, lp(-1, 54));
        gap(c, 19);
        detailSection(c, "حول المتجر", "YASSERAPPS 6.0.0\nمتجر أندرويد مستقل، يعرض فقط التطبيقات والألعاب المنشورة في كتالوج المتجر.");
        gap(c, 12);
        c.addView(text("واجهة عربية • الأسود والأخضر • بدون طلب توكن من المستخدم", 12, SUBTEXT, false));
        render(c, true);
    }

    @Override public void onBackPressed() {
        if ("home".equals(page)) super.onBackPressed();
        else { category = "app"; showHome(); }
    }

    @Override protected void onDestroy() {
        images.shutdownNow();
        super.onDestroy();
    }
}
