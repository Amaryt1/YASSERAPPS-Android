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
    // Latest actual upstream release/update timestamps, not editorial rankings.
    static List<Catalog.App> choose(List<Catalog.App> apps) {
        Map<String,Catalog.App> unique=new HashMap<>();
        for(Catalog.App a:apps) {
            if((a.discoveryOnly && !"Aptoide".equals(a.sourceName)) || (a.iconUrl.isEmpty()&&a.bannerUrl.isEmpty()&&a.screenshots.isEmpty())) continue;
            String key=OwnerCatalog.SOURCE.equals(a.sourceName)?a.id:a.packageName;
            Catalog.App old=unique.get(key);
            if(old==null||a.updatedAt>old.updatedAt||(a.updatedAt==old.updatedAt&&a.versionCode>old.versionCode)) unique.put(key,a);
        }
        ArrayList<Catalog.App> candidates=new ArrayList<>(unique.values());
        candidates.sort(Comparator.comparing((Catalog.App a)->OwnerCatalog.SOURCE.equals(a.sourceName)&&a.featured).reversed().thenComparing(Popularity.ORDER));
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
