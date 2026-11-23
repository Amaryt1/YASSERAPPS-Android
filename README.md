# YASSERAPPS — متجر التطبيقات والألعاب للأندرويد

**المتجر الرسمي:** [@YasserAppsIQ](https://t.me/YasserAppsIQ)

تطبيق Android مستقل بواجهة عربية سوداء وخضراء. يعرض التطبيقات والألعاب التي يُضيفها مسؤول المتجر فقط، بدون شاشة مصادر أو بحث في مستودعات برمجية.

## أجزاء المستودع
- `independent/`: كود تطبيق Android مستقل بتقنيات Java وAndroid SDK.
- `catalog/apps.json`: قاعدة بيانات المتجر العامة وتحديث الإصدارات والروابط والصور.
- `scripts/validate_catalog.py`: فحص تلقائي لصحة التطبيقات قبل نشرها.
- `.github/workflows/build-independent.yml`: إنتاج APK من الكود المستقل.
- `.github/workflows/test-independent.yml`: تجربة فتح التطبيق على محاكي أندرويد.

## إضافة تطبيق
حرّر `catalog/apps.json` وأضف تطبيقًا فعليًا برابط HTTPS لصيغة APK أو حزمة APK مقسّمة مدعومة، وصورة ووصف وإصدار وبصمة SHA-256. راجع `catalog/README.md` للتفاصيل. أي تحديث للقائمة يظهر للمستخدمين عند إعادة جلب البيانات بدون إصدار APK جديد.

**الكتالوج يحتوي NewPipe وShattered Pixel Dungeon** من الإصدارات الرسمية مفتوحة المصدر، مع بصمات SHA-256 متحقق منها. لا نعرض ألعابًا أو برامج غير منشورة.

## البناء
في GitHub افتح **Actions** → **Build Independent YASSERAPPS APK** ثم حمّل Artifact باسم **YASSERAPPS-Independent-APK**.

النسخ الناتجة من CI هي **Debug** للتجارب؛ قبل النشر التجاري يجب توقيع إصدار Release بمفتاح ثابت والاحتفاظ به بأمان.

## أمان وحقوق التطبيقات
انشر فقط ملفات APK التي تملك حق توزيعها، وافحص توقيعاتها وسلامتها. روابط التحميل تستخدم HTTPS، مع بصمة SHA-256 إلزامية. يدعم APK وحزم APKS/APKM/XAPK المحتوية APK splits بدون OBB، وكل تثبيت بموافقة المستخدم. نرفض تثبيت XAPK الذي يتطلب نسخ OBB لمجلد لعبة أخرى.

## YASSERAPPS V9
- لوحة الإدارة: [admin/index.html](admin/index.html) ثم [Manage YASSERAPPS catalog](.github/workflows/manage-catalog.yml).
- فحص ملفات APK من المصدر والتحقق من SHA-256 وبيانات Android: [Verify official YASSERAPPS APK catalog](.github/workflows/verify-catalog-apks.yml).
- إشعارات تحديث المتجر وتحديثاته الرسمية: [catalog/store-release.json](catalog/store-release.json).
- مشروع التوقيع الرسمي عبر أسرار GitHub: [Publish signed YASSERAPPS APK](.github/workflows/release-independent.yml).
- التعليمات والتحذيرات وخطوات مفتاح التوقيع: [دليل V9](docs/YASSERAPPS_V9_DEPLOYMENT.md).

**تنبيه:** ملف التحديث الرسمي غير مفعّل حتى تضبط شهادة توقيع ثابتة من عندك. نسخ CI التجريبية لا تمثل إصدارًا رسميًا.
