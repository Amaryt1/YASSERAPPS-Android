package iq.yasserapps.studio;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
/** Presentation only. All workflow callbacks remain in MainActivity. */
final class LabUi {
 static final int GREEN=0xff63ff91,WHITE=0xfff1faf5,MUTED=0xffa9c0b5,BG=0xff04110e;
 final Context c;
 LabUi(Context c){this.c=c;}
 int d(float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
 TextView text(CharSequence s,float size,int color,boolean bold){TextView v=new TextView(c);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
 LinearLayout col(){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
 LinearLayout row(){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);v.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);return v;}
 void add(LinearLayout parent,View v,int height,int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height<0?height:d(height));p.topMargin=d(margin);parent.addView(v,p);}
 Drawable panel(int type){return new Panel(type);}
 Drawable ripple(int type){return new RippleDrawable(ColorStateList.valueOf(0x3363ff91),panel(type),null);}
 Button action(String title,String kind,int style,Runnable cb){
  Button b=new Button(c);b.setAllCaps(false);b.setText(title);b.setTextSize(style==3?13:14);b.setTextColor(WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);b.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
  b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(d(12),d(7),d(9),d(7));b.setCompoundDrawablePadding(d(9));b.setBackground(ripple(style));
  b.setCompoundDrawables(new Glyph("chevron",20,false),null,new Glyph(kind,style==3?33:40,true),null);
  b.setOnClickListener(v->cb.run());return b;
 }
 void heading(LinearLayout root,String label,String kind,Runnable toggle){
  LinearLayout bar=row();View line=new View(c);line.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0x0044ff81,0xff56e48a}));bar.addView(line,new LinearLayout.LayoutParams(0,d(1),1));
  TextView t=text(label,16,WHITE,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.setMargins(d(12),0,d(7),0);bar.addView(t,tp);
  ImageView icon=new ImageView(c);icon.setImageDrawable(new Glyph(kind,24,false));bar.addView(icon,new LinearLayout.LayoutParams(d(24),d(24)));if(toggle!=null){bar.setOnClickListener(v->toggle.run());bar.setContentDescription(label+"، فتح وإغلاق");bar.setFocusable(true);}
  add(root,bar,32,10);
 }
 LinearLayout section(){LinearLayout l=col();l.setPadding(d(10),d(10),d(10),d(10));l.setBackground(panel(0));return l;}
 void header(LinearLayout root,String version,Runnable tools,Runnable settings){
  android.widget.FrameLayout frame=new FrameLayout(c);frame.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
  LinearLayout center=col();center.setGravity(Gravity.CENTER);
  ImageView logo=new ImageView(c);logo.setImageDrawable(new Glyph("logo",73,false));center.addView(logo,new LinearLayout.LayoutParams(d(73),d(73)));
  TextView name=text("",27,WHITE,true);android.text.SpannableString s=new android.text.SpannableString("YASSER Lab");s.setSpan(new android.text.style.ForegroundColorSpan(GREEN),7,10,0);name.setText(s);name.setLetterSpacing(0.015f);center.addView(name);
  TextView sub=text(version,13,MUTED,false);sub.setGravity(Gravity.CENTER);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.topMargin=d(4);center.addView(sub,sp);
  frame.addView(center,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
  for(int i=0;i<2;i++){final Runnable callback=i==0?tools:settings;ImageButton b=new ImageButton(c);b.setBackground(ripple(0));b.setImageDrawable(new Glyph(i==0?"dots":"gear",22,false));b.setScaleType(ImageView.ScaleType.CENTER);b.setContentDescription(i==0?"الأدوات المتقدمة":"إعدادات الهوية");b.setOnClickListener(v->callback.run());FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(d(36),d(36),(i==0?Gravity.LEFT:Gravity.RIGHT)|Gravity.TOP);lp.topMargin=d(18);frame.addView(b,lp);}
  add(root,frame,130,0);
 }
 void step(LinearLayout group,Button b,String number,String detail,boolean primary){
  LinearLayout r=row();LinearLayout body=col();body.setBackground(panel(primary?2:1));b.setBackground(ripple(primary?2:1));b.setCompoundDrawables(null,null,b.getCompoundDrawables()[2],null);add(body,b,detail==null?48:36,0);
  if(detail!=null){TextView t=text(detail,11,MUTED,false);t.setGravity(Gravity.RIGHT);t.setPadding(d(8),0,d(12),d(7));add(body,t,-2,0);}
  r.addView(body,new LinearLayout.LayoutParams(0,-2,1));TextView n=text(number,13,primary?BG:WHITE,true);n.setGravity(Gravity.CENTER);n.setBackground(panel(primary?4:0));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(d(25),d(25));np.leftMargin=d(8);r.addView(n,np);add(group,r,-2,6);
 }
 LinearLayout statusCard(TextView status,TextView summary){
  LinearLayout r=row();r.setPadding(d(12),d(12),d(12),d(12));r.setBackground(panel(5));ImageView folder=new ImageView(c);folder.setImageDrawable(new Glyph("folder",64,false));r.addView(folder,new LinearLayout.LayoutParams(d(74),d(74)));View div=new View(c);div.setBackgroundColor(0x5563ff91);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(d(1),d(58));dl.setMargins(d(6),0,d(12),0);r.addView(div,dl);
  LinearLayout info=col();status.setTextColor(WHITE);status.setTextSize(16);status.setTypeface(Typeface.DEFAULT,Typeface.BOLD);status.setGravity(Gravity.RIGHT);summary.setTextColor(MUTED);summary.setTextSize(12);summary.setGravity(Gravity.RIGHT);add(info,status,-2,0);add(info,summary,-2,7);r.addView(info,new LinearLayout.LayoutParams(0,-2,1));return r;
 }
 Drawable background(){return new Drawable(){
  final Paint p=new Paint(3);
  public void draw(Canvas canvas){Rect b=getBounds();float w=b.width(),h=b.height();canvas.save();canvas.translate(b.left,b.top);p.setShader(new LinearGradient(0,0,w,h,new int[]{0xff04120e,0xff050d0c,0xff071a12},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,p);
   p.setShader(new RadialGradient(w*.8f,0,Math.max(w,1)*.95f,new int[]{0xff12472a,0x00031a0e},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,p);p.setShader(null);
   Path wave=new Path();wave.moveTo(0,w*.35f);wave.cubicTo(w*.26f,-w*.05f,w*.73f,w*.28f,w*.86f,-w*.13f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(1));p.setColor(0x8855ff87);canvas.drawPath(wave,p);p.setStyle(Paint.Style.FILL);p.setColor(0x193cff82);for(int i=0;i<12;i++)for(int j=0;j<8;j++)if(i+j<13)canvas.drawCircle(d(5+i*7),d(9+j*7),d(.65f),p);canvas.restore();}
  public void setAlpha(int a){} public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 };}
 final class Panel extends Drawable{
  final int type;final Paint p=new Paint(3);Panel(int t){type=t;}
  public void draw(Canvas canvas){Rect b=getBounds();if(b.isEmpty())return;RectF r=new RectF(b);r.inset(d(.7f),d(.7f));float radius=d(type==4?30:12);
   int top=0xff213733,bottom=0xff101c1b,stroke=0xff46635a;
   if(type==0){top=0xff091b16;bottom=0xff071210;stroke=0xff233f35;}
   if(type==2){top=0xff0c742d;bottom=0xff08431d;stroke=0xff68ff96;}
   if(type==3){top=0xff21332f;bottom=0xff111c1a;stroke=0xff3d5850;}
   if(type==4){top=0xff79ff9a;bottom=0xff31cf68;stroke=GREEN;}
   if(type==5){top=0xff103e25;bottom=0xff061d14;stroke=0xff4dba70;}
   p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(0,r.top,0,r.bottom,new int[]{top,bottom},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(r,radius,radius,p);p.setShader(null);
   p.setStyle(Paint.Style.STROKE);p.setColor(stroke);p.setStrokeWidth(d(1));canvas.drawRoundRect(r,radius,radius,p);p.setStyle(Paint.Style.FILL);
   if(type==1||type==2||type==5){p.setShader(new LinearGradient(r.left,0,r.right,0,new int[]{0x0063ff91,0xcc63ff91,0x0063ff91},null,Shader.TileMode.CLAMP));canvas.drawRect(r.left+d(12),r.bottom-d(1),r.right-d(12),r.bottom,p);p.setShader(null);}
  }
  public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 final class Glyph extends Drawable{
  final String kind;final int size;final boolean tile;final Paint p=new Paint(3);
  Glyph(String k,int s,boolean t){kind=k;size=d(s);tile=t;setBounds(0,0,size,size);}
  public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
  void line(Canvas c,float... coords){Path path=new Path();path.moveTo(coords[0],coords[1]);for(int i=2;i<coords.length;i+=2)path.lineTo(coords[i],coords[i+1]);c.drawPath(path,p);}
  void rect(Canvas c,float x,float y,float w,float h,float r){c.drawRoundRect(x,y,x+w,y+h,r,r,p);}
  void fill(int color){p.setStyle(Paint.Style.FILL);p.setColor(color);}
  void stroke(int color,float width){p.setStyle(Paint.Style.STROKE);p.setColor(color);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
  public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/48f,b.height()/48f);if(tile){fill(0xff173a2a);rect(c,1,1,46,46,10);stroke(0xff3b6e50,0.7f);rect(c,1,1,46,46,10);}
   stroke(GREEN,2.7f);
   switch(kind){
    case "logo": {Path h=new Path();h.moveTo(24,2);h.lineTo(43,12);h.lineTo(44,34);h.lineTo(24,46);h.lineTo(5,35);h.lineTo(4,13);h.close();fill(0xff0b3920);c.drawPath(h,p);stroke(GREEN,1.1f);c.drawPath(h,p);Path y=new Path();y.moveTo(11,11);y.lineTo(19,11);y.lineTo(25,22);y.lineTo(32,11);y.lineTo(40,11);y.lineTo(28,29);y.lineTo(28,39);y.lineTo(21,39);y.lineTo(21,29);y.close();p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(12,10,35,39,new int[]{0xffbaffca,0xff24db62},null,Shader.TileMode.CLAMP));c.drawPath(y,p);p.setShader(null);break;}
    case "apk":line(c,13,7,27,7,35,15,35,39,13,39,13,7);line(c,27,7,27,15,35,15);fill(GREEN);rect(c,9,25,30,13,3);fill(BG);p.setTextSize(10);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);c.drawText("APK",24,35,p);break;
    case "brush":line(c,18,28,35,9,40,8,39,14,22,32);c.drawOval(8,28,23,39,p);break;
    case "preview":case "swap":rect(c,8,10,32,27,4);line(c,8,18,40,18);fill(GREEN);for(int i=0;i<3;i++)c.drawCircle(13+i*5,14,1.2f,p);if(kind.equals("swap")){stroke(GREEN,2.5f);line(c,15,26,32,26,28,22);line(c,32,32,15,32,19,36);}break;
    case "gear":for(int i=0;i<8;i++){c.save();c.rotate(i*45,24,24);c.drawLine(24,7,24,12,p);c.restore();}c.drawCircle(24,24,13,p);c.drawCircle(24,24,5,p);break;
    case "rocket":{Path a=new Path();a.moveTo(13,32);a.cubicTo(12,17,28,7,40,8);a.cubicTo(41,22,30,36,17,35);a.close();fill(GREEN);c.drawPath(a,p);fill(BG);c.drawCircle(29,19,4,p);fill(GREEN);line(c,12,30,7,39,18,36);break;}
    case "folder":line(c,5,14,5,37,42,37,42,15,23,15,18,10,9,10,5,14);line(c,9,37,13,21,43,21,39,37);fill(GREEN);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(12);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("APK",25,32,p);break;
    case "cert":c.drawCircle(24,21,12,p);line(c,18,21,22,25,30,16);line(c,16,31,13,42,22,37);line(c,32,31,35,42,26,37);break;
    case "bars":fill(GREEN);rect(c,10,27,7,12,2);rect(c,21,19,7,20,2);rect(c,32,10,7,29,2);break;
    case "search":c.drawCircle(20,20,11,p);line(c,28,28,39,39);break;
    case "layers":line(c,5,17,24,7,43,17,24,27,5,17);line(c,5,24,24,34,43,24);line(c,5,31,24,41,43,31);break;
    case "tools":line(c,10,9,38,37);line(c,9,38,34,13,40,8);c.drawCircle(10,9,5,p);c.drawCircle(38,37,4,p);break;
    case "dots":fill(WHITE);for(int i=0;i<3;i++)c.drawCircle(24,13+i*11,2.5f,p);break;
    default:stroke(WHITE,2.8f);line(c,18,13,29,24,18,35);
   }c.restore();
  }
  public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
