package com.yasserapps.store;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Editorial recommendations, not fabricated global download rankings. */
final class FeaturedApps {
    private static final String[] RECOMMENDED = {
        "org.schabi.newpipe", "org.mozilla.fennec_fdroid", "org.videolan.vlc",
        "com.aurora.store", "org.fdroid.fdroid", "net.osmand.plus",
        "com.nextcloud.client", "org.localsend.localsend_app", "com.termux",
        "com.fsck.k9", "net.thunderbird.android", "com.shatteredpixel.shatteredpixeldungeon",
        "com.github.catfriend1.syncthingandroid", "com.simplemobiletools.gallery.pro",
        "org.joinmastodon.android", "org.fossify.gallery", "org.fossify.filemanager",
        "com.junkfood.seal", "com.github.libretube", "com.kunzisoft.keepass.libre",
        "org.libreoffice.androidlibreoffice", "org.wikipedia", "com.ichi2.anki",
        "org.tasks", "org.kde.kdeconnect_tp", "org.openstreetmap.josm",
        "io.github.muntashirakon.AppManager", "com.duckduckgo.mobile.android"
    };
    static List<Catalog.App> choose(List<Catalog.App> apps) {
        Map<String,Integer> order=new HashMap<>();
        for(int i=0;i<RECOMMENDED.length;i++) order.put(RECOMMENDED[i].toLowerCase(Locale.ROOT),i);
        ArrayList<Catalog.App> candidates=new ArrayList<>();
        for(Catalog.App a:apps) if(a.iconUrl!=null&&!a.iconUrl.isEmpty()) candidates.add(a);
        candidates.sort(Comparator
            .comparingInt((Catalog.App a)->order.getOrDefault(a.packageName.toLowerCase(Locale.ROOT),Integer.MAX_VALUE))
            .thenComparingInt(a->(!a.bannerUrl.isEmpty()||!a.screenshots.isEmpty())?0:1)
            .thenComparingInt(a->a.summary.isEmpty()?1:0)
            .thenComparing(Comparator.comparingLong((Catalog.App a)->a.updatedAt).reversed())
            .thenComparing(a->a.packageName));
        return new ArrayList<>(candidates.subList(0,Math.min(10,candidates.size())));
    }
    /** Remove technical references from display copy, never invent descriptions. */
    static String displayCopy(String raw) {
        if(raw==null) return "";
        String s=raw.replaceAll("(?i)\\[([^\\]\\r\\n]+)\\]\\(https?://[^\\s)]+\\)", "$1");
        StringBuilder result=new StringBuilder();
        for(String line:s.split("\\r?\\n",-1)) {
            String t=line.trim();
            if(t.matches("(?i)^(?:source(?: code)?|repository|github|gitlab|issue tracker|bug reports?|build instructions|compile instructions|license|رابط المصدر|المصدر|الكود المصدري|رخصة)\\s*[:：-].*")) continue;
            t=t.replaceAll("(?i)\\b(?:https?://|www\\.)[^\\s<>]+","")
                .replaceAll("(?i)\\b(?:github\\.com|gitlab\\.com|codeberg\\.org)/[^\\s<>]+","")
                .replaceAll("(?i)\\b(?:git clone|git checkout)\\s+[^\\s]+","")
                .replaceAll("[ \\t]+"," ").trim();
            if(t.matches("^[\\p{Punct}\\s]+$")) continue;
            if(t.isEmpty()) {
                if(result.length()>0&&!result.toString().endsWith("\n\n")) result.append('\n');
            } else {
                if(result.length()>0&&result.charAt(result.length()-1)!='\n') result.append('\n');
                result.append(t).append('\n');
            }
        }
        return result.toString().trim();
    }
    static String description(Catalog.App a) {
        String d=displayCopy(a.description);
        return d.isEmpty()?displayCopy(a.summary):d;
    }
}
