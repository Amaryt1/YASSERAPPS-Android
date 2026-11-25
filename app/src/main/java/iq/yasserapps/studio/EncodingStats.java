package iq.yasserapps.studio;
import java.util.Base64;
import java.nio.*;
import java.nio.charset.*;
/** Counts standard encodings without exposing decoded content or attempting decryption. */
final class EncodingStats {
 long base64Text,escapedText,checked;
 static boolean printable(byte[] b){
  if(b.length<8)return false;
  try{
   String s=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();
   int n=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t')return false;if(Character.isLetterOrDigit(c))n++;}
   return n>=4;
  }catch(CharacterCodingException e){return false;}
 }
 void observe(String s){
  if(s==null||s.length()<12||s.length()>2048)return;
  if(s.contains("\\u")||s.contains("\\x")){escapedText++;return;}
  if(s.length()%4!=0)return;
  boolean marker=false;
  for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='+'||c=='/'||c=='=')marker=true;else if(!(c>='A'&&c<='Z'||c>='a'&&c<='z'||c>='0'&&c<='9'))return;}
  if(!marker)return;
  checked++;
  try{if(printable(Base64.getDecoder().decode(s)))base64Text++;}catch(IllegalArgumentException ignored){}
 }
 String report(){return "مؤشرات الترميز القياسي (تقديرية)\nمرشحات Base64 المفحوصة: "+checked+"\nBase64 محتمل لنص مقروء: "+base64Text+"\nنصوص تحمل علامات escape: "+escapedText+"\nلا تعرض الأداة المحتوى المفكوك في هذا التقرير ولا تحاول فك التشفير.";}
}
