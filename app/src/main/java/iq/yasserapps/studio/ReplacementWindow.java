package iq.yasserapps.studio;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
/** Native optional channel window. An application must opt in and call this renderer. */
public final class ReplacementWindow {
 private ReplacementWindow(){}
 public static String embeddedProfile(){return "__YASSERAPPS_BRAND_TEMPLATE__";}
 public static void launch(Activity a){
  if(a==null||a.isFinishing()||a.isDestroyed())return;
  try{JSONObject n=new JSONObject(embeddedProfile());SharedPreferences pref=a.getSharedPreferences("yasserapps-channel-window",0);String key="hide-"+embeddedProfile().hashCode();if(pref.getBoolean(key,false))return;show(a,n,false,()->pref.edit().putBoolean(key,true).apply());}catch(Exception e){android.util.Log.w("YasserChannel","Optional channel window failed",e);}
 }
 static int dp(Context c,int x){return Math.round(x*c.getResources().getDisplayMetrics().density);}
 static GradientDrawable shape(int fill,int edge,int radius,Context c){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(c,radius));if(edge!=0)g.setStroke(dp(c,1),edge);return g;}
 static TextView text(Context c,String s,int size,int color,boolean bold){TextView v=new TextView(c);v.setText(s);v.setTextColor(color);v.setTextSize(size);v.setGravity(Gravity.CENTER);v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
 static void add(LinearLayout l,View v,int width,int height,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,height);p.topMargin=dp(l.getContext(),top);p.gravity=Gravity.CENTER_HORIZONTAL;l.addView(v,p);}
 static Button button(Activity a,String label,int fill,int edge,int fg){Button b=new Button(a);b.setAllCaps(false);b.setText(label);b.setTextColor(fg);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setPadding(dp(a,10),dp(a,8),dp(a,10),dp(a,8));b.setBackground(shape(fill,edge,12,a));return b;}
 public static void show(Activity a,JSONObject profile,boolean preview,Runnable optOut)throws Exception{
  JSONObject n=new JSONObject(profile.toString());int bg=Color.parseColor(n.getString("background")),accent=Color.parseColor(n.getString("accent"));boolean light=Color.luminance(bg)>.45f;int fg=light?Color.BLACK:Color.WHITE,muted=light?0xff414a43:0xffb7c2b9,edge=light?0xffc4cec6:0xff343d35;
  LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER_HORIZONTAL);card.setPadding(dp(a,22),dp(a,22),dp(a,22),dp(a,16));card.setBackground(shape(bg,accent,22,a));
  View logo;String encoded=n.optString("logoPng");if(!encoded.isEmpty()){byte[] data=java.util.Base64.getDecoder().decode(encoded);Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length);if(bitmap==null)throw new java.io.IOException("Invalid logo");ImageView image=new ImageView(a);image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.FIT_CENTER);logo=image;}else logo=new YLogo(a,accent,bg);add(card,logo,dp(a,84),dp(a,84),0);
  add(card,text(a,n.getString("brand"),24,accent,true),-1,-2,12);
  add(card,text(a,n.getString("title"),19,fg,true),-1,-2,8);
  TextView body=text(a,n.getString("body"),15,muted,false);body.setLineSpacing(dp(a,3),1);add(card,body,-1,-2,8);
  Button join=button(a,n.getString("buttonText"),accent,0,Color.luminance(accent)>.45f?Color.BLACK:Color.WHITE);add(card,join,-1,dp(a,52),20);
  Button next=button(a,"متابعة إلى التطبيق",bg,edge,fg);add(card,next,-1,dp(a,50),10);
  CheckBox hide=new CheckBox(a);hide.setText("عدم الإظهار مجدداً");hide.setTextColor(muted);hide.setTextSize(14);hide.setButtonTintList(ColorStateList.valueOf(accent));hide.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);add(card,hide,-2,-2,9);
  View line=new View(a);line.setBackgroundColor(edge);add(card,line,-1,dp(a,1),12);
  add(card,text(a,n.getString("brand")+" • إشعار قناة مستقل",12,accent,true),-1,-2,10);
  if(preview)add(card,text(a,"معاينة التصميم — لا يُحفظ خيار الإخفاء هنا",11,muted,false),-1,-2,6);
  ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);scroll.addView(card);Dialog d=new Dialog(a);d.requestWindowFeature(Window.FEATURE_NO_TITLE);d.setContentView(scroll);d.setCancelable(true);d.setCanceledOnTouchOutside(false);
  Runnable finish=()->{if(!preview&&hide.isChecked()&&optOut!=null)optOut.run();d.dismiss();};next.setOnClickListener(v->finish.run());d.setOnCancelListener(v->{if(!preview&&hide.isChecked()&&optOut!=null)optOut.run();});
  join.setOnClickListener(v->{try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(n.optString("channelUrl"))));finish.run();}catch(ActivityNotFoundException ex){Toast.makeText(a,"تعذر فتح القناة",Toast.LENGTH_SHORT).show();}});
  d.show();Window window=d.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(Math.min(dp(a,380),a.getResources().getDisplayMetrics().widthPixels-dp(a,32)),WindowManager.LayoutParams.WRAP_CONTENT);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.65f);}
 }
 static final class YLogo extends View{
  final int color,bg;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  YLogo(Context c,int color,int bg){super(c);this.color=color;this.bg=bg;}
  @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),s=Math.min(w,h);RectF r=new RectF((w-s)/2+2,(h-s)/2+2,(w+s)/2-2,(h+s)/2-2);p.setColor(bg);p.setStyle(Paint.Style.FILL);c.drawRoundRect(r,s*.23f,s*.23f,p);p.setStyle(Paint.Style.STROKE);p.setColor(color);p.setStrokeWidth(dp(getContext(),2));c.drawRoundRect(r,s*.23f,s*.23f,p);p.setStrokeWidth(s*.10f);p.setStrokeCap(Paint.Cap.ROUND);float x=w/2,y=h/2;c.drawLine(x-s*.24f,y-s*.24f,x,y,p);c.drawLine(x+s*.24f,y-s*.24f,x,y,p);c.drawLine(x,y,x,y+s*.26f,p);}
 }
}