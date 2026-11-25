package com.yasserapps.store;

import android.util.LruCache;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.common.model.DownloadConditions;
import java.util.Locale;
import java.util.function.Consumer;

/** Translate genuine descriptions on device, never invent app functionality. */
final class ArabicCopy {
    private static final String MISSING="لا يوجد وصف متاح لهذا التطبيق.";
    private static final String FAILED="تعذّر توفير الوصف بالعربية حالياً. حاول فتح التفاصيل لاحقاً.";
    private static final LruCache<String,String> CACHE=new LruCache<>(160);
    private static boolean arabic(String s) {
        int ar=0,other=0;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(Character.isLetter(c)){
                if(Character.UnicodeBlock.of(c)==Character.UnicodeBlock.ARABIC)ar++;
                else other++;
            }
        }
        return ar>0&&ar>=other;
    }
    static String clean(String raw){
        String s=FeaturedApps.displayCopy(raw);
        String lower=s.toLowerCase(Locale.ROOT);
        if(lower.contains("appeared first on apkmirror")||lower.contains("get apkmirror premium")||
           lower.contains("apkmirror •")||lower.contains("appeared first on apk mirror")||
           lower.contains("get apkmirror premium")||lower.contains("ad-free, dark theme, faster downloads"))return "";
        return s;
    }
    private static String original(Catalog.App a){
        String value=clean(a.description);
        if(value.isEmpty())value=clean(a.summary);
        return value.length()>4500?value.substring(0,4500):value;
    }
    static String preview(Catalog.App a){
        String raw=original(a),translated=CACHE.get(raw);
        if(translated!=null)return translated;
        if(arabic(raw))return raw;
        return a.isGame()?"لعبة للأندرويد":"تطبيق للأندرويد";
    }
    static String initial(Catalog.App a){
        String raw=original(a);
        if(raw.isEmpty())return MISSING;
        if(arabic(raw))return raw;
        String cached=CACHE.get(raw);
        return cached==null?"جاري تجهيز الوصف بالعربية…":cached;
    }
    static void translate(Catalog.App a,Consumer<String> done){
        String raw=original(a);
        if(raw.isEmpty()){done.accept(MISSING);return;}
        if(arabic(raw)){done.accept(raw);return;}
        String cached=CACHE.get(raw);
        if(cached!=null){done.accept(cached);return;}
        final LanguageIdentifier identifier=LanguageIdentification.getClient();
        identifier.identifyLanguage(raw).addOnCompleteListener(task -> {
            identifier.close();
            if(!task.isSuccessful()){done.accept(FAILED);return;}
            String language=TranslateLanguage.fromLanguageTag(task.getResult());
            if(language==null){done.accept(FAILED);return;}
            if(TranslateLanguage.ARABIC.equals(language)){CACHE.put(raw,raw);done.accept(raw);return;}
            final Translator translator=Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(language).setTargetLanguage(TranslateLanguage.ARABIC).build());
            translator.downloadModelIfNeeded(new DownloadConditions.Builder().requireWifi().build())
                .onSuccessTask(v -> translator.translate(raw))
                .addOnCompleteListener(result -> {
                    translator.close();
                    if(!result.isSuccessful()||result.getResult()==null||result.getResult().trim().isEmpty()){done.accept(FAILED);return;}
                    String translated=result.getResult();
                    CACHE.put(raw,translated);
                    done.accept(translated);
                });
        });
    }
}
