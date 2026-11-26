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
        return stripPromotions(s);
    }
    /** Remove promotional contact text, not descriptions of social/messaging features. */
    private static final java.util.regex.Pattern CONTACT = java.util.regex.Pattern.compile(
        "(?iu)(?:\\b(?:follow us|find us on|connect with us|contact us|reach us|write to us|email us|send us|give us feedback|share your feedback|we(?:'d| would) love to hear|customer support|support team|for (?:help|support|feedback|questions)|rate us|leave (?:us )?a review|stay (?:connected|updated)|visit our (?:website|page))\\b|تابعونا|تابعنا على|تواصل معنا|اتصل بنا|راسلنا|راسلونا|أرسل (?:لنا|إلينا)|فريق الدعم|دعم العملاء|خدمة العملاء|للدعم الفني|للاستفسارات|للملاحظات|شاركنا رأيك|قيّم التطبيق|قيم التطبيق|اترك لنا تقييم|زوروا موقعنا|تابعنا لأخبار)");
    private static final java.util.regex.Pattern SOCIAL_LABEL = java.util.regex.Pattern.compile(
        "(?iu)(?:^|\\s)(?:facebook|instagram|telegram|tiktok|twitter|youtube|discord|whatsapp|فيسبوك|انستغرام|إنستغرام|انستقرام|تيليجرام|تلغرام|تيك توك|تويتر|يوتيوب|ديسكورد|واتساب|feedback|support|الدعم|الملاحظات)\\s*[:：]");
    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("(?i)[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}");
    static String stripPromotions(String raw) {
        if(raw==null||raw.isEmpty())return "";
        StringBuilder out=new StringBuilder();
        java.util.Set<String> seen=new java.util.HashSet<>();
        for(String sentence:raw.split("(?<=[.!?؟؛])\\s+|\\r?\\n+")){
            String t=sentence.trim();
            if(t.isEmpty())continue;
            java.util.regex.Matcher contact=CONTACT.matcher(t),social=SOCIAL_LABEL.matcher(t),email=EMAIL.matcher(t);
            int cut=t.length();
            if(contact.find())cut=Math.min(cut,contact.start());
            if(social.find())cut=Math.min(cut,social.start());
            if(email.find())cut=Math.min(cut,email.start());
            if(cut<t.length()){
                // Keep a complete preceding sentence, never a dangling contact fragment.
                String prefix=t.substring(0,cut).trim();
                int end=Math.max(Math.max(prefix.lastIndexOf('.'),prefix.lastIndexOf('!')),Math.max(prefix.lastIndexOf('?'),prefix.lastIndexOf('؟')));
                t=end<0?"":prefix.substring(0,end+1).trim();
            }
            if(t.isEmpty())continue;
            String key=t.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$","");
            if(!seen.add(key))continue;
            if(out.length()>0)out.append('\n');
            out.append(t);
        }
        return out.toString().trim();
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
                    String translated=stripPromotions(result.getResult());
                    if(translated.isEmpty()){done.accept(MISSING);return;}
                    CACHE.put(raw,translated);
                    done.accept(translated);
                });
        });
    }
}
