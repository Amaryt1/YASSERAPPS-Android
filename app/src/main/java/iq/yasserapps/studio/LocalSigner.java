package iq.yasserapps.studio;
import android.security.keystore.KeyGenParameterSpec;import android.security.keystore.KeyProperties;import com.android.apksig.ApkSigner;import com.android.apksig.ApkVerifier;import java.io.*;import java.math.BigInteger;import java.security.*;import java.security.cert.X509Certificate;import java.util.*;import javax.security.auth.x500.X500Principal;
public final class LocalSigner {
 static final String ALIAS="yasserapps-local-test-v1";
 public static synchronized void sign(File in,File out)throws Exception{sign(in,in,out);}
 public static synchronized String sign(File original,File in,File out)throws Exception{
 if(in.getCanonicalFile().equals(out.getCanonicalFile())||original.getCanonicalFile().equals(out.getCanonicalFile()))throw new IOException("Output must differ from input");
 Map<String,String> prepared=SigningAudit.snapshot(in);Map<String,String> source=original.equals(in)?prepared:SigningAudit.snapshot(original);
 KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
 if(!ks.containsAlias(ALIAS)){KeyPairGenerator g=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");long now=System.currentTimeMillis();g.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512,KeyProperties.DIGEST_SHA1).setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,KeyProperties.SIGNATURE_PADDING_RSA_PSS).setCertificateSubject(new X500Principal("CN=YASSERAPPS Local Test")).setCertificateSerialNumber(BigInteger.valueOf(now)).setCertificateNotBefore(new Date(now-86400000L)).setCertificateNotAfter(new Date(now+20L*365*86400000L)).build());g.generateKeyPair();}
 PrivateKey key=(PrivateKey)ks.getKey(ALIAS,null);X509Certificate cert=(X509Certificate)ks.getCertificate(ALIAS);if(key==null||cert==null)throw new IOException("No signing identity");boolean ok=false;try{ApkSigner.SignerConfig cfg=new ApkSigner.SignerConfig.Builder("YASSER",key,Collections.singletonList(cert)).build();new ApkSigner.Builder(Collections.singletonList(cfg)).setInputApk(in).setOutputApk(out).setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).setV4SigningEnabled(false).setOtherSignersSignaturesPreserved(false).build().sign();ApkVerifier.Result r=new ApkVerifier.Builder(out).build().verify();if(!r.isVerified()||!r.isVerifiedUsingV2Scheme())throw new IOException("Verification failed: "+r.getErrors());
 if(r.getSignerCertificates().size()!=1||!MessageDigest.isEqual(cert.getEncoded(),r.getSignerCertificates().get(0).getEncoded()))throw new IOException("Signer certificate mismatch");
 Map<String,String> signed=SigningAudit.snapshot(out);SigningAudit.requireSame(prepared,signed);
 String report="تقرير سلامة التوقيع المحلي\nالتوقيع صحيح: نعم\nV1: "+r.isVerifiedUsingV1Scheme()+" / V2: "+r.isVerifiedUsingV2Scheme()+" / V3: "+r.isVerifiedUsingV3Scheme()
 +"\nSHA-256 للشهادة: "+SigningAudit.fingerprint(cert)+"\nتطابق شهادة المفتاح المحفوظ: نعم\nتطابق محتوى الملف قبل وبعد التوقيع: نعم\n\nمقارنة مع الملف الأصلي (دون بيانات التوقيع):\n"+SigningAudit.difference(source,signed)
 +"\nتحذيرات التحقق: "+r.getWarnings().size()+"\nهذا فحص سلامة رقمي وليس فحص برمجيات ضارة أو ضماناً لقبول Play Protect.";
 ok=true;return report;}finally{if(!ok)out.delete();}
 }
}