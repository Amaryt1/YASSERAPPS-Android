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
    private static final int BG = Color.BLACK;
    private static final int PANEL = Color.rgb(18, 20, 22);
    private static final int PANEL2 = Color.rgb(28, 30, 29);
    private static final int GREEN = Color.rgb(105, 255, 0);
    private static final int WHITE = Color.WHITE;
    private static final int SUBTEXT = Color.rgb(182, 188, 184);
    private static final String CHANNEL = "https://t.me/YasserAppsIQ";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService images = Executors.newFixedThreadPool(3);
    private final android.util.LruCache<String, Bitmap> thumbnails =
        new android.util.LruCache<String, Bitmap>(12 * 1024 * 1024) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
    private final List<Catalog.App> apps = new ArrayList<>();
    private DownloadCoordinator downloadCoordinator;
    private boolean verifyingInstall = false;
    private boolean checkedStoreUpdateThisLaunch = false;
    private Catalog.App pendingLegacyDownload;
    private static final int REQUEST_LEGACY_STORAGE = 4281;
    private static final int REQUEST_UPDATE_NOTICES = 4282;
    private final String FAVORITES_KEY = "favorites_store_v1";
    // A download started in the foreground automatically opens Android's
    // installation confirmation only after the APK passes verification.
    // Background downloads use a branded notification instead of launching UI.
    private final java.util.Set<Long> armedDownloads = new java.util.HashSet<>();
    private final java.util.Set<Long> handledDownloads = new java.util.HashSet<>();
    private final Map<Long, long[]> speedSamples = new HashMap<>();
    private boolean activityVisible = false;
    private DownloadCoordinator.Entry pendingInstallAfterPermission;
    private static final int REQUEST_DOWNLOAD_NOTICES = 4283;
    private final Runnable completionWatch = new Runnable() {
        @Override public void run() {
            if (!activityVisible || isFinishing()) return;
            for (DownloadCoordinator.Entry entry : downloadCoordinator.all()) {
                if (entry.active()) {
                    if (!handledDownloads.contains(entry.id)) armedDownloads.add(entry.id);
                } else if (armedDownloads.remove(entry.id) && handledDownloads.add(entry.id)) {
                    if (entry.complete()) {
                        DownloadBranding.complete(MainActivity.this, entry);
                        if ("downloads".equals(page)) showDownloads();
                        showCompletionBanner(entry);
                        main.postDelayed(() -> {
                            if (activityVisible && !isFinishing()) installFromHistory(entry);
                        }, 1200);
                    } else if (entry.failed()) {
                        DownloadBranding.failed(MainActivity.this, entry);
                        if ("downloads".equals(page)) showDownloads();
                    }
                }
            }
            main.postDelayed(this, 1500);
        }
    };
    private final Runnable downloadsTick = new Runnable() {
        @Override public void run() {
            if ("downloads".equals(page) && !isFinishing()) showDownloads();
        }
    };
    private FrameLayout screen;
    private String page = "home";
    private String category = "all";
    private static final int CATALOG_PAGE_SIZE = 40;
    private String listTitle = "";
    private String listKind = "all";
    private boolean loading = true;
    private String warning;
    private Catalog.App detailApp;
    private long lastPollAt;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        downloadCoordinator = new DownloadCoordinator(this);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        showHome();
        if ("com.yasserapps.store.OPEN_DOWNLOADS".equals(getIntent().getAction())) {
            openDownloadNotification(getIntent());
        }
        refresh();
        StoreUpdates.schedule(this);
        checkStoreUpdate("com.yasserapps.store.SHOW_UPDATE".equals(getIntent().getAction()));
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if ("com.yasserapps.store.SHOW_UPDATE".equals(intent.getAction())) checkStoreUpdate(true);
        else if ("com.yasserapps.store.OPEN_DOWNLOADS".equals(intent.getAction()))
            openDownloadNotification(intent);
    }

    private void openDownloadNotification(Intent intent) {
        showDownloads();
        long id = intent.getLongExtra("download_id", -1L);
        if (id > 0L) main.postDelayed(() -> {
            DownloadCoordinator.Entry entry = downloadCoordinator.find(id);
            if (entry != null && entry.complete() && !isFinishing()) {
                installFromHistory(entry);
            }
        }, 500);
    }

    private void checkStoreUpdate(boolean interactive) {
        StoreUpdates.check(this, (update, error) -> main.post(() -> {
            if (isFinishing()) return;
            if (update != null) {
                new android.app.AlertDialog.Builder(this)
                    .setTitle("تحديث YASSERAPPS " + update.version)
                    .setMessage(update.notes + "\n\nيتم التحميل بعد موافقتك، والتثبيت بمثبت أندرويد الرسمي.")
                    .setPositiveButton("تنزيل التحديث", (dialog, which) -> download(update.toApp()))
                    .setNegativeButton("لاحقًا", null)
                    .show();
            } else if (interactive) {
                Toast.makeText(this, error == null ?
                    "متجر YASSERAPPS محدث حاليًا أو لم يُنشر إصدار جديد" :
                    "تعذّر فحص تحديث المتجر: " + error, Toast.LENGTH_LONG).show();
            }
        }));
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
        // Android 15 enables edge-to-edge by default for target SDK 35.
        // Respect system status/navigation bars so the logo and dock never overlap them.
        if (Build.VERSION.SDK_INT >= 35) {
            screen.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                android.graphics.Insets bars = windowInsets.getInsets(
                    android.view.WindowInsets.Type.statusBars() |
                    android.view.WindowInsets.Type.navigationBars()
                );
                if (view.getPaddingTop() != bars.top || view.getPaddingBottom() != bars.bottom) {
                    view.setPadding(0, bars.top, 0, bars.bottom);
                }
                return windowInsets;
            });
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        FrameLayout.LayoutParams s = new FrameLayout.LayoutParams(-1, -1);
        s.bottomMargin = footer ? dp(88) : 0;
        screen.addView(scroll, s);
        if (footer) addFooter(screen);
        setContentView(screen);
        if (Build.VERSION.SDK_INT >= 35) screen.requestApplyInsets();
    }

    private void addFooter(FrameLayout parent) {
        LinearLayout dock = linear(false);
        dock.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.rgb(33, 36, 37), Color.rgb(12, 15, 14)}
        );
        background.setCornerRadius(dp(21));
        background.setStroke(dp(1), Color.rgb(45, 58, 43));
        dock.setBackground(background);
        dock.setElevation(dp(7));
        dock.setPadding(dp(4), dp(5), dp(4), dp(5));
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-1, dp(72), Gravity.BOTTOM);
        position.setMargins(dp(10), dp(2), dp(10), dp(10));
        parent.addView(dock, position);
        nav(dock, "home", "الرئيسية", "home", this::showHome);
        nav(dock, "search", "البحث", "search", this::showSearch);
        nav(dock, "download", "التحميلات", "downloads", this::showDownloads);
        nav(dock, "settings", "الإعدادات", "settings", this::showSettings);
    }

    private void nav(LinearLayout row, String icon, String label, String key, Runnable callback) {
        LinearLayout button = linear(true);
        button.setGravity(Gravity.CENTER);
        boolean active = page.equals(key);
        button.setBackground(shape(
            active ? Color.rgb(20, 58, 8) : Color.TRANSPARENT, 19,
            active ? Color.rgb(67, 135, 18) : 0
        ));
        StoreGlyph glyph = new StoreGlyph(this, icon, active ? GREEN : Color.rgb(210, 216, 209));
        button.addView(glyph, lp(27, 27));
        gap(button, 4);
        TextView caption = text(label, 11.5f, active ? GREEN : WHITE, active);
        caption.setGravity(Gravity.CENTER);
        button.addView(caption);
        button.setOnClickListener(v -> callback.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.setMargins(dp(2), 0, dp(2), 0);
        row.addView(button, params);
    }

    private void header(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout telegram = new FrameLayout(this);
        StoreGlyph bell = new StoreGlyph(this, "bell", WHITE);
        FrameLayout.LayoutParams b = new FrameLayout.LayoutParams(dp(27), dp(27), Gravity.CENTER);
        telegram.addView(bell, b);
        View dot = new View(this);
        dot.setBackground(shape(GREEN, 8, 0));
        FrameLayout.LayoutParams dotPos = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.TOP | Gravity.RIGHT);
        dotPos.rightMargin = dp(3);
        dotPos.topMargin = dp(5);
        telegram.addView(dot, dotPos);
        telegram.setContentDescription("قناة YASSERAPPS");
        telegram.setOnClickListener(v -> open(CHANNEL));
        row.addView(telegram, lp(38, 54));

        padx(row, 9);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.yasser_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(logo, lp(59, 59));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        words.setGravity(Gravity.CENTER_VERTICAL);
        words.setPadding(dp(8), 0, dp(1), 0);
        String brand = "YASSERAPPS";
        android.text.SpannableString styled = new android.text.SpannableString(brand);
        styled.setSpan(new android.text.style.ForegroundColorSpan(GREEN), 6,
            brand.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView name = text(brand, 21, WHITE, true);
        name.setTextDirection(View.TEXT_DIRECTION_LTR);
        name.setSingleLine(true);
        name.setText(styled);
        name.setLetterSpacing(-0.025f);
        words.addView(name);
        TextView subtitle = text("متجر التطبيقات والألعاب للأندرويد", 10.5f, SUBTEXT, false);
        subtitle.setTextDirection(View.TEXT_DIRECTION_RTL);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(subtitle);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));

        StoreGlyph search = new StoreGlyph(this, "search", WHITE);
        search.setContentDescription("البحث عن التطبيقات والألعاب");
        search.setOnClickListener(v -> showSearch());
        row.addView(search, lp(34, 43));

        root.addView(row);
        gap(root, 19);
    }

    private void searchBox(LinearLayout root) {
        LinearLayout row = linear(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(6), dp(17), dp(6));
        row.setBackground(shape(PANEL2, 22, Color.rgb(42, 60, 44)));
        row.addView(text("⌕", 26, GREEN, true), lp(34, 43));
        TextView hint = text("بحث", 14, SUBTEXT, false);
        row.addView(hint, new LinearLayout.LayoutParams(0, dp(43), 1));
        row.setOnClickListener(v -> showSearch());
        root.addView(row, lp(-1, 54));
        gap(root, 13);
    }

    private void tabs(LinearLayout root) {
        LinearLayout row = linear(false);
        // RTL: "All" comes first on the right, followed by Apps, Games, Updates.
        for (String type : new String[]{"all", "app", "game", "updates"}) {
            boolean active = category.equals(type);
            String label = type.equals("all") ? "الكل" :
                type.equals("app") ? "التطبيقات" :
                type.equals("game") ? "الألعاب" : "التحديثات";
            String glyphName = type.equals("all") ? "grid" :
                type.equals("app") ? "grid" :
                type.equals("game") ? "game" : "refresh";
            LinearLayout button = linear(false);
            button.setGravity(Gravity.CENTER);
            button.setBackground(shape(active ? GREEN : Color.rgb(16, 18, 19), 13,
                active ? GREEN : Color.rgb(65, 67, 68)));
            StoreGlyph icon = new StoreGlyph(this, glyphName, active ? Color.BLACK : WHITE);
            button.addView(icon, lp(17, 17));
            padx(button, 3);
            TextView caption = text(label, 11, active ? Color.BLACK : WHITE, true);
            caption.setSingleLine(true);
            caption.setGravity(Gravity.CENTER);
            button.addView(caption);
            button.setOnClickListener(v -> {
                category = type;
                if ("updates".equals(type)) showUpdates();
                else showHome();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(47), 1);
            params.setMargins(dp(2), 0, dp(2), 0);
            row.addView(button, params);
        }
        root.addView(row);
        gap(root, 13);
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
            if ("all".equals(kind)) list.add(app);
            else if ("updates".equals(kind)) {
                if (installedAndNewer(app)) list.add(app);
            } else if ("game".equals(kind) && app.isGame()) list.add(app);
            else if ("app".equals(kind) && !app.isGame()) list.add(app);
        }
        return list;
    }

    public void showHome() {
        page = "home";
        if ("updates".equals(category)) category = "all";
        LinearLayout content = verticalContent();
        header(content);
        tabs(content);
        if ("all".equals(category)) {
            heroCard(content);
            gap(content, 14);
            section(content, "أحدث التطبيقات", matching("app"), false);
            gap(content, 17);
            section(content, "أحدث الألعاب", matching("game"), true);
        } else if ("app".equals(category)) {
            catalogList(content, "التطبيقات", matching("app"));
        } else if ("game".equals(category)) {
            catalogList(content, "الألعاب", matching("game"));
        }
        render(content, true);
    }

    private void heroCard(LinearLayout root) {
        Catalog.App featured = null;
        int featuredCount = 0;
        for (Catalog.App candidate : apps) {
            if (candidate.featured) {
                featuredCount++;
                if (featured == null) featured = candidate;
            }
        }
        final Catalog.App selected = featured;
        FrameLayout banner = new FrameLayout(this);
        banner.setBackground(shape(Color.rgb(7,14,8), 18, Color.rgb(65,105,47)));
        banner.setClipToOutline(true);
        banner.addView(new BrandArt(this), new FrameLayout.LayoutParams(-1,-1));

        String artwork = selected == null ? "" :
            !selected.bannerUrl.isEmpty() ? selected.bannerUrl :
            !selected.screenshots.isEmpty() ? selected.screenshots.get(0) : "";
        if (!artwork.isEmpty()) {
            ImageView art = new ImageView(this);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image(art, artwork);
            banner.addView(art,new FrameLayout.LayoutParams(-1,-1));
        }
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(
            GradientDrawable.Orientation.RIGHT_LEFT,
            new int[]{0xE6000600,0xB509100B,0x53060B08}
        ));
        banner.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout inner = linear(false);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(18),dp(14),dp(18),dp(16));
        LinearLayout title = linear(true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        if (selected == null) {
            title.addView(text("YASSERAPPS",24,GREEN,true));
            gap(title,7);
            title.addView(text("متجرك للتطبيقات والألعاب",16,WHITE,true));
            gap(title,6);
            TextView sub=text("أحدث الإضافات والتحديثات في مكان واحد",12,SUBTEXT,false);
            sub.setMaxLines(2);
            title.addView(sub);
        } else {
            TextView brand=text("مميز في YASSERAPPS",12,GREEN,true);
            title.addView(brand);
            gap(title,6);
            TextView name=text(selected.name,26,WHITE,true);
            name.setMaxLines(2);
            title.addView(name);
            gap(title,7);
            TextView sub=text(selected.summary,12,WHITE,false);
            sub.setMaxLines(2);
            title.addView(sub);
            gap(title,12);
            title.addView(text("عرض التفاصيل ‹",14,GREEN,true));
        }
        inner.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        // Featured recommendations must show the promoted app's own imagery,
        // never the YASSERAPPS logo or unrelated game artwork.
        if (selected != null && !selected.screenshots.isEmpty()) {
            FrameLayout shotFrame = new FrameLayout(this);
            shotFrame.setBackground(shape(Color.rgb(14, 20, 16), 13, GREEN));
            shotFrame.setClipToOutline(true);
            ImageView realScreenshot = new ImageView(this);
            realScreenshot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image(realScreenshot, selected.screenshots.get(
                selected.screenshots.size() > 1 ? 1 : 0));
            FrameLayout.LayoutParams innerShot = new FrameLayout.LayoutParams(-1, -1);
            innerShot.setMargins(dp(2), dp(2), dp(2), dp(2));
            shotFrame.addView(realScreenshot, innerShot);
            inner.addView(shotFrame, lp(74, 128));
        } else if (selected == null) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.yasser_logo);
            inner.addView(logo, lp(88, 88));
        }
        banner.addView(inner,new FrameLayout.LayoutParams(-1,-1));
        banner.setOnClickListener(v -> {
            if (selected != null) showDetail(selected);
            else showSearch();
        });
        root.addView(banner,lp(-1,191));

        if(featuredCount>1){
            LinearLayout indicators=linear(false);
            indicators.setGravity(Gravity.CENTER);
            gap(root,8);
            for (int i=0;i<Math.min(7,featuredCount);i++) {
                View dot=new View(this);
                dot.setBackground(shape(i==0?GREEN:Color.rgb(76,80,77),8,0));
                LinearLayout.LayoutParams dotParams=lp(i==0?18:7,7);
                dotParams.setMargins(dp(4),0,dp(4),0);
                indicators.addView(dot,dotParams);
            }
            root.addView(indicators);
        }
    }

    private void section(LinearLayout root, String name, List<Catalog.App> entries, boolean compact) {
        LinearLayout title = linear(false);
        title.setGravity(Gravity.CENTER_VERTICAL);
        StoreGlyph badge=new StoreGlyph(this,
            name.contains("الألعاب")?"game":"grid", GREEN);
        title.addView(badge,lp(24,27));
        padx(title,7);
        TextView heading=text(name + " (" + entries.size() + ")",19,WHITE,true);
        title.addView(heading,new LinearLayout.LayoutParams(0,dp(38),1));
        TextView all=text("عرض الكل  ‹",13,WHITE,false);
        all.setGravity(Gravity.CENTER_VERTICAL);
        all.setOnClickListener(v -> {
            category=name.contains("الألعاب")?"game":"app";
            showList(name,category);
        });
        title.addView(all);
        root.addView(title);
        gap(root,7);
        if(entries.isEmpty()){
            String message=loading ? "جاري تحميل محتوى المتجر..." :
                name.contains("الألعاب") ? "لا توجد ألعاب منشورة حاليًا." : "لا توجد تطبيقات منشورة حاليًا.";
            if(warning!=null) message=warning;
            panelText(root,message);
            return;
        }
        // Keep the familiar home preview, with additional rows available in place.
        appendCatalogPages(root, entries, compact ? 3 : 10);
        android.util.Log.i("YASSERAPPS_CATALOG", "UI_HOME_SECTION kind=" +
            (compact ? "game" : "app") + " total=" + entries.size());
    }

    private void catalogList(LinearLayout root, String title, List<Catalog.App> entries) {
        TextView heading = text(title + " (" + entries.size() + ")", 19, WHITE, true);
        root.addView(heading);
        gap(root, 9);
        if (entries.isEmpty()) {
            panelText(root, loading ? "جاري تحميل محتوى المتجر..." :
                (warning != null ? warning : "لا يوجد محتوى منشور حاليًا."));
            return;
        }
        appendCatalogPages(root, entries, CATALOG_PAGE_SIZE);
        android.util.Log.i("YASSERAPPS_CATALOG", "UI_LIST total=" + entries.size() +
            " rendered=" + Math.min(entries.size(), CATALOG_PAGE_SIZE));
    }

    private void appendCatalogPages(LinearLayout root, List<Catalog.App> entries, int initialSize) {
        LinearLayout rows = linear(true);
        root.addView(rows, lp(-1, -2));
        final int[] cursor = {0};
        TextView more = action("عرض المزيد", false, () -> {});
        Runnable append = () -> {
            int end = Math.min(entries.size(), cursor[0] + (cursor[0] == 0 ? initialSize : CATALOG_PAGE_SIZE));
            for (int i = cursor[0]; i < end; i++) {
                card(rows, entries.get(i));
                gap(rows, 9);
            }
            cursor[0] = end;
            more.setText("عرض المزيد (" + (entries.size() - end) + ")");
            more.setVisibility(end < entries.size() ? View.VISIBLE : View.GONE);
        };
        root.addView(more, lp(-1, 50));
        more.setOnClickListener(v -> append.run());
        append.run();
    }

    private void showList(String title, String kind) {
        page = "catalog_list";
        listTitle = title;
        listKind = kind;
        LinearLayout c = verticalContent();
        backBar(c, title, this::showHome);
        catalogList(c, title, matching(kind));
        render(c, true);
    }

    private void card(LinearLayout root, Catalog.App app) {
        LinearLayout row=linear(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9),dp(10),dp(11),dp(10));
        GradientDrawable background=new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.rgb(24,26,27),Color.rgb(15,17,18)}
        );
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1),Color.rgb(53,59,55));
        row.setBackground(background);

        FrameLayout iconHolder=new FrameLayout(this);
        iconHolder.setBackground(shape(PANEL2,15,Color.rgb(50,63,47)));
        iconHolder.setClipToOutline(true);
        ImageView icon=new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setImageResource(R.drawable.yasser_logo);
        image(icon,app.iconUrl);
        iconHolder.addView(icon,new FrameLayout.LayoutParams(-1,-1));
        row.addView(iconHolder,lp(73,73));
        padx(row,12);

        LinearLayout contents=linear(true);
        contents.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text(app.name,16.5f,WHITE,true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        contents.addView(name);
        gap(contents,3);
        TextView sub=text(app.summary.isEmpty()?"تطبيق للأندرويد":app.summary,12,SUBTEXT,false);
        sub.setMaxLines(2);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        contents.addView(sub);
        gap(contents,8);
        LinearLayout meta=linear(false);
        TextView version=text("الإصدار "+app.version,10.5f,SUBTEXT,false);
        version.setPadding(dp(8),dp(4),dp(8),dp(4));
        version.setBackground(shape(Color.rgb(43,45,48),9,0));
        meta.addView(version);
        padx(meta,5);
        TextView bytes=text(size(app.sizeBytes),10.5f,SUBTEXT,false);
        bytes.setPadding(dp(8),dp(4),dp(8),dp(4));
        bytes.setBackground(shape(Color.rgb(43,45,48),9,0));
        meta.addView(bytes);
        contents.addView(meta);
        row.addView(contents,new LinearLayout.LayoutParams(0,-2,1));
        padx(row,10);

        View dividingLine=new View(this);
        dividingLine.setBackgroundColor(Color.rgb(55,57,55));
        row.addView(dividingLine,lp(1,60));
        padx(row,10);
        LinearLayout installButton=linear(false);
        installButton.setGravity(Gravity.CENTER);
        installButton.setBackground(shape(GREEN,12,0));
        TextView install=text(installedAndNewer(app)?"تحديث":"تثبيت",14,Color.BLACK,true);
        installButton.addView(install);
        padx(installButton,4);
        installButton.addView(new StoreGlyph(this,"download",Color.BLACK),lp(20,20));
        installButton.setOnClickListener(v -> showDetail(app));
        row.addView(installButton,lp(89,45));

        row.setOnClickListener(v -> showDetail(app));
        root.addView(row,lp(-1,-2));
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
        field.setHint("بحث");
        field.setPadding(dp(16), 0, dp(16), 0);
        field.setBackground(shape(PANEL2, 20, Color.rgb(53, 67, 52)));
        field.setTextDirection(View.TEXT_DIRECTION_RTL);
        c.addView(field, lp(-1, 55));
        gap(c, 13);
        LinearLayout results = linear(true);
        c.addView(results, lp(-1, -2));
        Runnable update = () -> {
            results.removeAllViews();
            String query = field.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (query.isEmpty()) {
                return;
            }
            ArrayList<Catalog.App> matches = new ArrayList<>();
            for (Catalog.App app : apps) {
                if (app.name.toLowerCase(Locale.ROOT).contains(query) ||
                    app.summary.toLowerCase(Locale.ROOT).contains(query) ||
                    app.packageName.toLowerCase(Locale.ROOT).contains(query)) matches.add(app);
            }
            if (matches.isEmpty()) panelText(results, "لا توجد نتائج مطابقة لبحثك.");
            else catalogList(results, "نتائج البحث", matches);
        };
        activeSearchRefresh = update;
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
        backBar(c, "تحديثات التطبيقات", () -> { category = "all"; showHome(); });
        TextView check = action("فحص تحديثات المتجر الآن", true, () -> {
            Toast.makeText(this, "جاري جلب أحدث إصدارات المتجر", Toast.LENGTH_SHORT).show();
            refresh();
        });
        c.addView(check, lp(-1, 49));
        gap(c, 10);
        c.addView(text(
            "يفحص المتجر إصدارات التطبيقات المنشورة عندنا والمتاحة للرؤية على جهازك، ولا يثبت أي تحديث دون موافقتك.",
            12, SUBTEXT, false
        ));
        gap(c, 17);
        List<Catalog.App> updates = matching("updates");
        if (updates.isEmpty()) panelText(c, loading ?
            "جاري التحقق من الإصدارات..." :
            "لا توجد تحديثات متاحة حاليًا. قد لا يتمكن النظام من عرض بعض التطبيقات المثبتة.");
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
        LinearLayout content = verticalContent();

        LinearLayout top=linear(false);
        top.setGravity(Gravity.CENTER_VERTICAL);
        StoreGlyph back=new StoreGlyph(this,"arrow",WHITE);
        back.setContentDescription("العودة إلى المتجر");
        back.setOnClickListener(v -> showHome());
        top.addView(back,lp(32,40));
        padx(top,11);
        ImageView brandIcon=new ImageView(this);
        brandIcon.setImageResource(R.drawable.yasser_logo);
        top.addView(brandIcon,lp(39,39));
        padx(top,6);
        TextView brand=text("YASSERAPPS",21,GREEN,true);
        brand.setTextDirection(View.TEXT_DIRECTION_LTR);
        brand.setSingleLine(true);
        top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        StoreGlyph shareIcon=new StoreGlyph(this,"share",WHITE);
        shareIcon.setContentDescription("مشاركة التطبيق");
        shareIcon.setOnClickListener(v -> shareApp(app));
        top.addView(shareIcon,lp(29,40));
        content.addView(top);
        gap(content,24);

        LinearLayout intro=linear(false);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout iconFrame=new FrameLayout(this);
        iconFrame.setClipToOutline(true);
        iconFrame.setBackground(shape(Color.rgb(19,22,20),19,GREEN));
        ImageView appIcon=new ImageView(this);
        appIcon.setImageResource(R.drawable.yasser_logo);
        appIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image(appIcon,app.iconUrl);
        FrameLayout.LayoutParams iconPos=new FrameLayout.LayoutParams(-1,-1);
        iconPos.setMargins(dp(2),dp(2),dp(2),dp(2));
        iconFrame.addView(appIcon,iconPos);
        intro.addView(iconFrame,lp(118,118));
        padx(intro,13);
        LinearLayout texts=linear(true);
        TextView name=text(app.name,27,WHITE,true);
        name.setMaxLines(2);
        texts.addView(name);
        gap(texts,6);
        TextView summary=text(app.summary,13,SUBTEXT,false);
        summary.setMaxLines(2);
        texts.addView(summary);
        gap(texts,11);
        TextView platform=text(app.isGame()?"🎮 لعبة للأندرويد":"تطبيق للأندرويد",12,GREEN,true);
        texts.addView(platform);
        intro.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        content.addView(intro);
        gap(content,18);
        if (!app.sourceName.isEmpty()) detailSection(content, "المصدر", app.sourceName + (app.license.isEmpty() ? "" : " • " + app.license));


        LinearLayout specs=linear(false);
        TextView version=text("الإصدار "+app.version,12,WHITE,true);
        version.setPadding(dp(11),dp(8),dp(11),dp(8));
        version.setBackground(shape(PANEL2,17,0));
        specs.addView(version);
        padx(specs,8);
        TextView bytes=text(size(app.sizeBytes),12,WHITE,true);
        bytes.setPadding(dp(11),dp(8),dp(11),dp(8));
        bytes.setBackground(shape(PANEL2,17,0));
        specs.addView(bytes);
        content.addView(specs);
        gap(content,22);

        LinearLayout buttons=linear(false);
        LinearLayout mainButton=linear(false);
        mainButton.setGravity(Gravity.CENTER);
        mainButton.setBackground(shape(GREEN,19,0));
        mainButton.addView(new StoreGlyph(this,"download",Color.BLACK),lp(25,25));
        padx(mainButton,10);
        DownloadCoordinator.Entry previous = downloadCoordinator.latestFor(app);
        String installLabel = previous != null && previous.complete() ?
            "تثبيت المحمّل" : installedAndNewer(app) ? "تحديث" : "تثبيت";
        TextView action=text(installLabel,19,Color.BLACK,true);
        mainButton.addView(action);
        mainButton.setOnClickListener(v -> {
            DownloadCoordinator.Entry existing = downloadCoordinator.latestFor(app);
            if (existing != null && existing.complete()) installFromHistory(existing);
            else download(app);
        });
        buttons.addView(mainButton,new LinearLayout.LayoutParams(0,dp(58),2));
        padx(buttons,9);
        LinearLayout altButton=linear(false);
        altButton.setGravity(Gravity.CENTER);
        altButton.setBackground(shape(Color.rgb(5,12,5),19,GREEN));
        altButton.addView(new StoreGlyph(this,"share",GREEN),lp(22,22));
        padx(altButton,7);
        altButton.addView(text("مشاركة",14,GREEN,true));
        altButton.setOnClickListener(v -> shareApp(app));
        buttons.addView(altButton,new LinearLayout.LayoutParams(0,dp(58),1));
        content.addView(buttons);
        gap(content,10);
        TextView favorite = action(isFavorite(app) ? "★ إزالة من المفضلة" : "☆ إضافة إلى المفضلة",
            false, () -> {
                toggleFavorite(app);
                showDetail(app);
            });
        content.addView(favorite, lp(-1, 44));
        gap(content,18);

        if(!app.screenshots.isEmpty()) {
            content.addView(text("صور التطبيق",20,WHITE,true));
            gap(content,12);
            android.widget.HorizontalScrollView scroller=
                new android.widget.HorizontalScrollView(this);
            scroller.setHorizontalScrollBarEnabled(false);
            LinearLayout gallery=linear(false);
            for(String url : app.screenshots){
                FrameLayout photoFrame=new FrameLayout(this);
                photoFrame.setBackground(shape(PANEL,18,GREEN));
                photoFrame.setClipToOutline(true);
                ImageView screenshot=new ImageView(this);
                screenshot.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image(screenshot,url);
                FrameLayout.LayoutParams imagePos=new FrameLayout.LayoutParams(-1,-1);
                imagePos.setMargins(dp(1),dp(1),dp(1),dp(1));
                photoFrame.addView(screenshot,imagePos);
                LinearLayout.LayoutParams p=lp(260,170);
                p.setMargins(dp(5),0,dp(5),0);
                gallery.addView(photoFrame,p);
            }
            scroller.addView(gallery);
            content.addView(scroller,lp(-1,173));
            gap(content,23);
        }
        detailSection(content,"الوصف",app.description.isEmpty()?app.summary:app.description);
        gap(content,16);
        detailSection(content,"سجل التحديثات  •  "+app.version,
            app.changelog.isEmpty()?"لا يوجد سجل تحديثات لهذا الإصدار.":app.changelog);
        gap(content,16);
        TextView note=text("سيطلب أندرويد موافقتك قبل تثبيت أي تطبيق.",12,SUBTEXT,false);
        content.addView(note);
        render(content,false);
    }

    private void shareApp(Catalog.App app) {
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,app.name+"\n"+app.apkUrl);
        startActivity(Intent.createChooser(send,"مشاركة التطبيق"));
    }

    private int catalogRefreshGeneration;
    private boolean refreshInFlight;
    private boolean notifyRefresh;
    private long lastCatalogCheck;
    private Runnable activeSearchRefresh;
    private static final long CATALOG_REFRESH_INTERVAL = 15L * 60L * 1000L;
    private final Runnable catalogTicker = new Runnable() {
        @Override public void run() {
            if (!activityVisible || isFinishing()) return;
            if (!refreshInFlight && android.os.SystemClock.elapsedRealtime() - lastCatalogCheck >= CATALOG_REFRESH_INTERVAL) refresh(false);
            main.postDelayed(this, 60000);
        }
    };

    private void refresh() {
        refresh(false);
    }

    private void refresh(boolean showResult) {
        notifyRefresh |= showResult;
        if (refreshInFlight) return;
        refreshInFlight = true;
        lastCatalogCheck = android.os.SystemClock.elapsedRealtime();
        final int generation = ++catalogRefreshGeneration;
        loading = true;
        Catalog.load(this, result -> main.post(() -> {
            if (generation != catalogRefreshGeneration || isFinishing() || isDestroyed()) return;
            loading = !result.finalResult;
            if (result.finalResult) refreshInFlight = false;
            apps.clear();
            apps.addAll(result.apps);
            warning = result.warning;
            if ("home".equals(page)) showHome();
            else if ("catalog_list".equals(page)) showList(listTitle, listKind);
            else if ("updates".equals(page)) showUpdates();
            else if ("downloads".equals(page)) showDownloads();
            else if ("favorites".equals(page)) showFavorites();
            else if ("search".equals(page) && activeSearchRefresh != null) activeSearchRefresh.run();
            else if ("detail".equals(page) && detailApp != null) {
                for (Catalog.App current : apps) if (current.packageName.equals(detailApp.packageName)) {
                    if (current.versionCode != detailApp.versionCode || !current.apkUrl.equals(detailApp.apkUrl)) showDetail(current);
                    break;
                }
            }
            if (notifyRefresh && result.finalResult) {
                notifyRefresh = false;
                String message = result.warning != null ? result.warning :
                    "تم تحديث المتجر: " + apps.size() + " تطبيقًا ولعبة";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                if ("settings".equals(page)) {
                    category = "all";
                    showHome();
                }
            }
        }));
    }

    private Bitmap decodeLimitedImage(InputStream input) throws Exception {
        try (java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int size = 0, read;
            while ((read = input.read(buffer)) != -1) {
                size += read;
                if (size > 5_000_000) throw new IllegalArgumentException("Image too large");
                output.write(buffer, 0, read);
            }
            byte[] bytes = output.toByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw new IllegalArgumentException("Invalid image");
            }
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inPreferredConfig = Bitmap.Config.RGB_565;
            decode.inSampleSize = 1;
            while (bounds.outWidth / decode.inSampleSize > 1200 ||
                   bounds.outHeight / decode.inSampleSize > 1200) {
                decode.inSampleSize *= 2;
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decode);
        }
    }

    private void image(ImageView target, String address) {
        if (address == null || !address.startsWith("https://")) return;
        Bitmap cached = thumbnails.get(address);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }
        images.execute(() -> {
            Bitmap bitmap = null;
            // Official catalog artwork bundled into this signed APK has priority,
            // so featured game screenshots and icons appear even when offline.
            try {
                java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(address.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder filename = new StringBuilder("catalog_media/");
                for (byte item : hash) {
                    filename.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
                }
                filename.append(".img");
                try (InputStream input = getAssets().open(filename.toString())) {
                    bitmap = decodeLimitedImage(input);
                }
            } catch (Exception ignored) { }

            if (bitmap == null) {
                java.net.HttpURLConnection connection = null;
                try {
                    connection = (java.net.HttpURLConnection) new URL(address).openConnection();
                    connection.setConnectTimeout(6000);
                    connection.setReadTimeout(9000);
                    connection.setInstanceFollowRedirects(true);
                    if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                        throw new IllegalArgumentException("Images must use HTTPS");
                    }
                    try (InputStream input = connection.getInputStream()) {
                        bitmap = decodeLimitedImage(input);
                    }
                } catch (Exception ignored) { }
                finally {
                    if (connection != null) connection.disconnect();
                }
            }
            final Bitmap ready = bitmap;
            if (ready != null && !ready.isRecycled()) {
                thumbnails.put(address, ready);
                main.post(() -> {
                    if (!isDestroyed()) target.setImageBitmap(ready);
                });
            }
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

    private void showCompletionBanner(DownloadCoordinator.Entry entry) {
        if (screen == null || isFinishing()) return;
        FrameLayout host = screen;
        LinearLayout banner = linear(false);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setBackground(shape(Color.rgb(18, 58, 13), 18, GREEN));
        banner.setPadding(dp(13), dp(9), dp(13), dp(9));
        banner.setElevation(dp(16));

        ImageView graphic = new ImageView(this);
        graphic.setImageResource(R.drawable.yasser_logo);
        graphic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        for (Catalog.App item : apps) {
            if (entry.packageName.equals(item.packageName)) {
                image(graphic, item.iconUrl);
                break;
            }
        }
        banner.addView(graphic, lp(53, 53));
        padx(banner, 11);

        LinearLayout content = linear(true);
        content.addView(text("✓ اكتمل تنزيل " + entry.name, 15, WHITE, true));
        gap(content, 3);
        content.addView(text("من YASSERAPPS • جاري تجهيز شاشة التثبيت", 12,
            Color.rgb(222, 244, 215), false));
        banner.addView(content, new LinearLayout.LayoutParams(0, -2, 1));

        FrameLayout.LayoutParams atTop = new FrameLayout.LayoutParams(
            -1, dp(84), Gravity.TOP);
        atTop.setMargins(dp(13), dp(16), dp(13), 0);
        host.addView(banner, atTop);
        banner.setAlpha(0f);
        banner.animate().alpha(1f).setDuration(210).start();
        main.postDelayed(() -> {
            banner.animate().alpha(0f).setDuration(260).withEndAction(() -> {
                if (banner.getParent() == host) host.removeView(banner);
            }).start();
        }, 2750);
    }

    private void requestBrandedDownloadNotices() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        android.content.SharedPreferences pref = getSharedPreferences("yasserapps_notifications", MODE_PRIVATE);
        if (!pref.getBoolean("prompted_downloads_once", false)) {
            pref.edit().putBoolean("prompted_downloads_once", true).apply();
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_DOWNLOAD_NOTICES);
        }
    }

    private void download(Catalog.App app) {
        if (Build.VERSION.SDK_INT <= 28 &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingLegacyDownload = app;
            requestPermissions(
                new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_LEGACY_STORAGE
            );
            return;
        }
        try {
            DownloadCoordinator.Entry entry = downloadCoordinator.start(app);
            if (entry.complete()) {
                Toast.makeText(this, "هذا الإصدار موجود بالتنزيلات وجاهز للتثبيت", Toast.LENGTH_LONG).show();
            } else if (entry.active()) {
                Toast.makeText(this, "جاري التنزيل من YASSERAPPS • عند اكتماله تنفتح شاشة التثبيت", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "راجع حالة التحميل في التنزيلات", Toast.LENGTH_SHORT).show();
            }
            if (entry.active()) {
                armedDownloads.add(entry.id);
                main.removeCallbacks(completionWatch);
                if (activityVisible) main.postDelayed(completionWatch, 650);
            }
            showDownloads();
            requestBrandedDownloadNotices();
        } catch (Exception ex) {
            Toast.makeText(this, "تعذّر بدء التحميل: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void installFromHistory(DownloadCoordinator.Entry entry) {
        if (verifyingInstall) return;
        verifyingInstall = true;
        // SHA-256 and package metadata still get checked privately; no extra
        // "check and install" step is required from the user.
        downloadCoordinator.verify(entry, (success, error, uri) -> {
            verifyingInstall = false;
            if (!success) {
                Toast.makeText(this, error == null ? "تعذّر فحص APK" : error, Toast.LENGTH_LONG).show();
                return;
            }
            if (!DownloadCoordinator.allowedToInstall(this)) {
                pendingInstallAfterPermission = entry;
                Toast.makeText(this, "فعّل إذن التثبيت لـ YASSERAPPS، وبعد الرجوع تنفتح شاشة أندرويد", Toast.LENGTH_LONG).show();
                try { startActivity(DownloadCoordinator.permissionIntent(this)); }
                catch (Exception e) {
                    Toast.makeText(this, "تعذّر فتح إعدادات صلاحية التثبيت", Toast.LENGTH_LONG).show();
                }
                return;
            }
            if (SplitPackageInstaller.isBundle(entry.url)) {
                SplitPackageInstaller.install(this,
                    downloadCoordinator.fileFor(entry), entry.packageName, entry.versionCode,
                    (ok, message) -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
                return;
            }
            try {
                Intent installer = new Intent(Intent.ACTION_VIEW);
                installer.setDataAndType(uri, "application/vnd.android.package-archive");
                installer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(installer);
            } catch (Exception ex) {
                Toast.makeText(this, "تعذّر فتح مثبت أندرويد: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showDownloads() {
        main.removeCallbacks(downloadsTick);
        page = "downloads";
        LinearLayout content = verticalContent();
        header(content);
        content.addView(text("إدارة التنزيلات", 23, WHITE, true));
        gap(content, 13);
        FrameLayout brandPanel = new FrameLayout(this);
        brandPanel.setBackground(shape(Color.rgb(8, 23, 10), 17, Color.rgb(62, 120, 36)));
        brandPanel.setClipToOutline(true);
        brandPanel.addView(new BrandArt(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout ribbon = linear(false);
        ribbon.setGravity(Gravity.CENTER_VERTICAL);
        ribbon.setPadding(dp(14), dp(10), dp(14), dp(10));
        ImageView emblem = new ImageView(this);
        emblem.setImageResource(R.drawable.yasser_logo);
        ribbon.addView(emblem, lp(58, 58));
        padx(ribbon, 12);
        LinearLayout copy = linear(true);
        copy.addView(text("YASSERAPPS DOWNLOADS", 18, GREEN, true));
        gap(copy, 3);
        copy.addView(text("سرعة • تقدم مباشر • إدارة وتحميل آمن", 12, WHITE, false));
        ribbon.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        brandPanel.addView(ribbon, new FrameLayout.LayoutParams(-1, -1));
        content.addView(brandPanel, lp(-1, 94));
        gap(content, 9);
        content.addView(text("تستمر التنزيلات عند إغلاق المتجر، ويكتمل التثبيت بموافقتك.", 12, SUBTEXT, false));
        gap(content, 17);
        List<DownloadCoordinator.Entry> records = downloadCoordinator.all();
        if (records.isEmpty()) {
            panelText(content, "لا توجد تنزيلات حاليًا.\nعند بدء تنزيل تطبيق أو لعبة، سيظهر تقدم التنزيل هنا.");
        } else for (DownloadCoordinator.Entry entry : records) {
            LinearLayout card = linear(true);
            card.setPadding(dp(13), dp(13), dp(13), dp(14));
            card.setBackground(shape(PANEL, 17, Color.rgb(51, 66, 47)));
            LinearLayout headingRow = linear(false);
            headingRow.setGravity(Gravity.CENTER_VERTICAL);
            ImageView appGraphic = new ImageView(this);
            appGraphic.setImageResource(R.drawable.yasser_logo);
            appGraphic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            for (Catalog.App item : apps) {
                if (item.packageName.equals(entry.packageName)) {
                    image(appGraphic, item.iconUrl);
                    break;
                }
            }
            headingRow.addView(appGraphic, lp(53, 53));
            padx(headingRow, 10);
            LinearLayout labels = linear(true);
            TextView title = text(entry.name, 17, WHITE, true);
            title.setMaxLines(2);
            labels.addView(title);
            gap(labels, 3);
            labels.addView(text("YASSERAPPS • " + entry.version, 11, GREEN, true));
            headingRow.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(headingRow);
            gap(card, 9);
            String stateText = entry.message();
            int color = entry.failed() ? Color.rgb(255, 161, 141) : GREEN;
            card.addView(text(stateText, 12, color, false));
            gap(card, 9);
            if (entry.active()) {
                android.widget.ProgressBar progress = new android.widget.ProgressBar(
                    this, null, android.R.attr.progressBarStyleHorizontal);
                progress.setMax(100);
                if (entry.percent() < 0) {
                    progress.setIndeterminate(true);
                } else {
                    progress.setIndeterminate(false);
                    progress.setProgress(entry.percent());
                }
                if (Build.VERSION.SDK_INT >= 21) progress.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(GREEN));
                card.addView(progress, lp(-1, 6));
                gap(card, 6);
                String done = size(entry.bytesDone);
                String total = entry.bytesTotal > 0 ? size(entry.bytesTotal) : "الحجم غير معروف";
                String percent = entry.percent() < 0 ? "" : " • " + entry.percent() + "%";
                card.addView(text(done + " / " + total + percent, 12, SUBTEXT, false));
                long now = System.currentTimeMillis();
                long[] last = speedSamples.get(entry.id);
                long speed = 0;
                if (last != null) {
                    long elapsed = now - last[0];
                    speed = elapsed > 700 ? Math.max(0,
                        (entry.bytesDone - last[1]) * 1000L / Math.max(1L, elapsed)) : last[2];
                }
                speedSamples.put(entry.id, new long[]{now, entry.bytesDone, speed});
                if (speed > 0) {
                    long remaining = entry.bytesTotal > entry.bytesDone ?
                        (entry.bytesTotal - entry.bytesDone) / speed : -1;
                    String eta = remaining < 0 ? "" : remaining < 60 ?
                        " • المتبقي " + remaining + " ث" :
                        " • المتبقي " + (remaining / 60 + 1) + " د";
                    String speedText = speed >= 1024 * 1024 ?
                    String.format(Locale.ROOT, "%.1f MB", speed / 1048576.0) :
                    speed >= 1024 ?
                    String.format(Locale.ROOT, "%.0f KB", speed / 1024.0) :
                    speed + " B";
                TextView rate = text("السرعة " + speedText + "/ث" + eta,
                        12, GREEN, false);
                    card.addView(rate);
                }
            }
            gap(card, 10);
            LinearLayout actions = linear(false);
            if (entry.complete()) {
                TextView install = action("تثبيت الآن", true, () -> installFromHistory(entry));
                actions.addView(install, new LinearLayout.LayoutParams(0, dp(44), 1));
                padx(actions, 8);
            } else if (entry.failed()) {
                TextView retry = action("إعادة المحاولة", true, () -> {
                    Catalog.App candidate = null;
                    for (Catalog.App item : apps) if (entry.packageName.equals(item.packageName)) {
                        candidate = item; break;
                    }
                    downloadCoordinator.remove(entry.id);
                    DownloadBranding.clear(this, entry.id);
                    if (candidate != null) download(candidate);
                    else {
                        Toast.makeText(this, "التطبيق مو موجود بالكتالوج الحالي", Toast.LENGTH_LONG).show();
                        showDownloads();
                    }
                });
                actions.addView(retry, new LinearLayout.LayoutParams(0, dp(44), 1));
                padx(actions, 8);
            }
            TextView delete = action(entry.active() ? "إلغاء" : "حذف", false, () -> {
                new android.app.AlertDialog.Builder(this)
                    .setTitle(entry.active() ? "إلغاء التحميل؟" : "حذف ملف التنزيل؟")
                    .setMessage("راح ينحذف التحميل وسجله من جهازك. ما يتأثر التطبيق المثبّت.")
                    .setPositiveButton("تأكيد", (dialog, which) -> {
                        downloadCoordinator.remove(entry.id);
                        DownloadBranding.clear(this, entry.id);
                        speedSamples.remove(entry.id);
                        armedDownloads.remove(entry.id);
                        showDownloads();
                    })
                    .setNegativeButton("رجوع", null)
                    .show();
            });
            actions.addView(delete, new LinearLayout.LayoutParams(0, dp(44), 1));
            card.addView(actions);
            content.addView(card, lp(-1, -2));
            gap(content, 11);
        }
        render(content, true);
        main.postDelayed(downloadsTick, 2400);
    }

    private boolean isFavorite(Catalog.App app) {
        return getSharedPreferences(FAVORITES_KEY, MODE_PRIVATE)
            .getStringSet("ids", java.util.Collections.emptySet()).contains(app.id);
    }

    private void toggleFavorite(Catalog.App app) {
        android.content.SharedPreferences pref = getSharedPreferences(FAVORITES_KEY, MODE_PRIVATE);
        java.util.HashSet<String> ids = new java.util.HashSet<>(
            pref.getStringSet("ids", java.util.Collections.emptySet()));
        if (!ids.add(app.id)) ids.remove(app.id);
        pref.edit().putStringSet("ids", ids).apply();
    }

    private void showFavorites() {
        page = "favorites";
        LinearLayout c = verticalContent();
        backBar(c, "المفضلة", this::showSettings);
        int count = 0;
        for (Catalog.App item : apps) {
            if (isFavorite(item)) {
                card(c, item);
                gap(c, 11);
                count++;
            }
        }
        if (count == 0) panelText(c, "ما عندك تطبيقات مفضّلة. افتح صفحة أي تطبيق واضغط إضافة إلى المفضلة.");
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
        TextView favorites = action("★ تطبيقاتي المفضّلة", false, this::showFavorites);
        c.addView(favorites, lp(-1, 54));
        gap(c, 11);
        TextView wifi = action(downloadCoordinator.wifiOnly() ?
            "التنزيل عبر Wi-Fi فقط: مفعّل" : "التنزيل عبر Wi-Fi فقط: غير مفعّل", false, () -> {
                downloadCoordinator.setWifiOnly(!downloadCoordinator.wifiOnly());
                showSettings();
            });
        c.addView(wifi, lp(-1, 54));
        gap(c, 11);
        TextView notifications = action("تفعيل إشعارات تحديث المتجر", false, () -> {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_UPDATE_NOTICES);
            } else {
                Toast.makeText(this, "إشعارات المتجر مفعّلة أو مدعومة تلقائيًا", Toast.LENGTH_SHORT).show();
            }
        });
        c.addView(notifications, lp(-1, 54));
        gap(c, 11);
        TextView storeUpdate = action("فحص تحديث متجر YASSERAPPS", true,
            () -> checkStoreUpdate(true));
        c.addView(storeUpdate, lp(-1, 54));
        gap(c, 11);
        TextView updates = action("فحص تحديثات التطبيقات", false, this::showUpdates);
        c.addView(updates, lp(-1, 54));
        gap(c, 11);
        TextView refresh = action("تحديث قائمة التطبيقات والألعاب", true, () -> {
            Toast.makeText(this, "جاري تحديث المتجر", Toast.LENGTH_SHORT).show();
            refresh(true);
        });
        c.addView(refresh, lp(-1, 54));
        gap(c, 19);
        detailSection(c, "حول المتجر", "YASSERAPPS 10.3.0\n"
            + "وجهتك للتطبيقات والألعاب في مكان واحد. استكشف أحدث الإصدارات، "
            + "وتابع تنزيلاتك وتحديثاتك بسهولة، ضمن تجربة عصرية تجمع البساطة والتنظيم والأمان.");
        gap(c, 12);

        render(c, true);
    }

    @Override public void onBackPressed() {
        if ("home".equals(page)) super.onBackPressed();
        else { category = "all"; showHome(); }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_DOWNLOAD_NOTICES) {
            boolean allowed = results.length > 0 &&
                results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (allowed) Toast.makeText(this, "تم تفعيل إشعارات YASSERAPPS", Toast.LENGTH_SHORT).show();
            return;
        }
        if (requestCode == REQUEST_UPDATE_NOTICES) {
            boolean yes = results.length > 0 &&
                results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            Toast.makeText(this, yes ? "تم تفعيل إشعارات تحديث المتجر" :
                "الإشعارات غير مفعّلة؛ تقدر تفحص التحديثات يدويًا", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQUEST_LEGACY_STORAGE) {
            Catalog.App pending = pendingLegacyDownload;
            pendingLegacyDownload = null;
            if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (pending != null) download(pending);
            } else {
                Toast.makeText(this,
                    "لا يمكن تنزيل ملفات APK على إصدار أندرويد هذا بدون إذن التخزين",
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        activityVisible = true;
        main.removeCallbacks(catalogTicker);
        catalogTicker.run();
        if (downloadCoordinator != null) {
            if (pendingInstallAfterPermission != null &&
                DownloadCoordinator.allowedToInstall(this)) {
                DownloadCoordinator.Entry ready = pendingInstallAfterPermission;
                pendingInstallAfterPermission = null;
                main.postDelayed(() -> installFromHistory(ready), 250);
            }
            for (DownloadCoordinator.Entry item : downloadCoordinator.all()) {
                if (item.active() && !handledDownloads.contains(item.id)) armedDownloads.add(item.id);
            }
            main.removeCallbacks(completionWatch);
            main.postDelayed(completionWatch, 900);
            if ("downloads".equals(page)) showDownloads();
            else if ("updates".equals(page)) showUpdates();
        }
    }

    @Override protected void onPause() {
        activityVisible = false;
        main.removeCallbacks(catalogTicker);
        main.removeCallbacks(completionWatch);
        main.removeCallbacks(downloadsTick);
        super.onPause();
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(catalogTicker);
        activeSearchRefresh = null;
        main.removeCallbacks(completionWatch);
        main.removeCallbacks(downloadsTick);
        images.shutdownNow();
        super.onDestroy();
    }
}
