package com.yasserapps.store;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Original, lightweight line icons drawn in the Android Canvas.
 * Dimensions and stroke are normalized to a 24x24 viewport.
 */
public final class StoreGlyph extends View {
    private final String kind;
    private int tint;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public StoreGlyph(Context context, String kind, int tint) {
        super(context);
        this.kind = kind;
        this.tint = tint;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setTint(int tint) {
        this.tint = tint;
        invalidate();
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2) {
        c.drawLine(x1, y1, x2, y2, paint);
    }
    private void path(Canvas c, float... p) {
        Path a = new Path();
        a.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) a.lineTo(p[i], p[i+1]);
        c.drawPath(a, paint);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        canvas.save();
        canvas.translate((getWidth()-size)/2f, (getHeight()-size)/2f);
        canvas.scale(size/24f, size/24f);
        paint.setColor(tint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.9f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        switch (kind) {
            case "home":
                path(canvas,3,10.5f,12,3.4f,21,10.5f);
                path(canvas,5.5f,9.8f,5.5f,20,18.5f,20,18.5f,9.8f);
                break;
            case "search":
                canvas.drawCircle(10.8f,10.8f,6.5f,paint);
                line(canvas,15.6f,15.6f,21,21);
                break;
            case "download":
                line(canvas,12,3.5f,12,16);
                path(canvas,6.8f,11.2f,12,16.4f,17.2f,11.2f);
                line(canvas,4.5f,20.4f,19.5f,20.4f);
                break;
            case "settings":
                canvas.drawCircle(12,12,3.5f,paint);
                for (int i=0;i<8;i++) {
                    double t=Math.PI*i/4;
                    float co=(float)Math.cos(t), si=(float)Math.sin(t);
                    line(canvas,12+7.1f*co,12+7.1f*si,12+10*co,12+10*si);
                }
                break;
            case "bell":
                path(canvas,5.5f,16.4f,6.9f,14.2f,6.9f,10,7.8f,6.8f,10,5.1f,12,4.6f,14,5.1f,16.2f,6.8f,17.1f,10,17.1f,14.2f,18.5f,16.4f,5.5f,16.4f);
                line(canvas,10.1f,19.5f,13.9f,19.5f);
                break;
            case "grid":
                for(int x=0;x<2;x++) for(int y=0;y<2;y++)
                    canvas.drawRoundRect(3+x*10,3+y*10,10+x*10,10+y*10,1,1,paint);
                break;
            case "game":
                Path p = new Path();
                p.moveTo(5.2f,9); p.cubicTo(5.8f,6.8f,7.5f,6.1f,10,7.4f);
                p.lineTo(14,7.4f); p.cubicTo(16.5f,6.1f,18.2f,6.8f,18.8f,9);
                p.lineTo(21,16.9f); p.cubicTo(21.8f,19.8f,19.5f,20.7f,17.5f,18.8f);
                p.lineTo(15.5f,16.8f); p.lineTo(8.5f,16.8f); p.lineTo(6.5f,18.8f);
                p.cubicTo(4.5f,20.7f,2.2f,19.8f,3,16.9f); p.close(); canvas.drawPath(p,paint);
                line(canvas,7.2f,12.2f,12,12.2f); line(canvas,9.6f,9.8f,9.6f,14.6f);
                canvas.drawCircle(16,11,0.65f,paint);canvas.drawCircle(18.4f,13,0.65f,paint);
                break;
            case "refresh":
                Path a = new Path();
                a.addArc(4,4,20,20,195,230); canvas.drawPath(a,paint);
                path(canvas,17.1f,4.9f,20,5,19.7f,8.1f);
                Path b = new Path();b.addArc(4,4,20,20,15,146);canvas.drawPath(b,paint);
                path(canvas,4.1f,15.8f,4,19.4f,7.4f,19.1f);
                break;
            case "arrow":
                path(canvas,16,5,9,12,16,19);
                break;
            case "share":
                canvas.drawCircle(18,5,2,paint); canvas.drawCircle(6,12,2,paint); canvas.drawCircle(18,19,2,paint);
                line(canvas,8,11,16,6);line(canvas,8,13,16,18);
                break;
            case "check":
                path(canvas,4,12,10,18,20,6);
                break;
            default: canvas.drawCircle(12,12,7,paint);
        }
        canvas.restore();
    }
}
