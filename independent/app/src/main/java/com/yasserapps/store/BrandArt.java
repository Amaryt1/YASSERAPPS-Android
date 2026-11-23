package com.yasserapps.store;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Original abstract black/lime artwork for the empty-store and promotional hero. */
public final class BrandArt extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    public BrandArt(Context context) { super(context); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(), h=getHeight();
        if(w<=0 || h<=0) return;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0,0,w,h,
            new int[]{0xFF071307,0xFF101812,0xFF080D08},null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,p);
        p.setShader(null);
        p.setColor(0x2C69FF00);
        Path beam = new Path();
        beam.moveTo(w*.12f,-10); beam.lineTo(w*.27f,-10);
        beam.lineTo(w*.82f,h+10); beam.lineTo(w*.58f,h+10); beam.close();
        canvas.drawPath(beam,p);
        p.setColor(0x1A79FF05);
        Path beam2 = new Path();
        beam2.moveTo(w*.69f,-10);beam2.lineTo(w*.81f,-10);
        beam2.lineTo(w*.43f,h+10);beam2.lineTo(w*.28f,h+10);beam2.close();
        canvas.drawPath(beam2,p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1,w/480f));
        for(int i=0;i<5;i++){
            p.setColor((0x10+i*3)<<24 | 0x69FF00);
            canvas.drawCircle(w*.84f,h*.45f,w*(.18f+i*.075f),p);
        }
        p.setColor(0x5F6BFF00);
        p.setStrokeWidth(Math.max(2,w/220f));
        canvas.drawLine(0,h*.91f,w*.26f,h*.53f,p);
        canvas.drawLine(w*.76f,0,w,h*.32f,p);
        p.setShader(null);
    }
}
