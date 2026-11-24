package com.osfans.trime.editor;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

/** Saturation/value palette; hue and opacity are controlled by the editor. */
public final class ColorPaletteView extends View {
    public interface Listener { void onColorChanged(int color); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] hsv = {0, 0, 1};
    private int alpha = 255;
    private Listener listener;
    public ColorPaletteView(Context context) { super(context); setContentDescription("饱和度和明度调色板"); }
    public void setListener(Listener listener) { this.listener = listener; }
    public void setColor(int color) { Color.colorToHSV(color, hsv); alpha = Color.alpha(color); invalidate(); }
    public int getColor() { return Color.HSVToColor(alpha, hsv); }
    public void setHue(float hue) { hsv[0] = hue; changed(); }
    public void setOpacity(int opacity) { alpha = opacity; changed(); }
    private void changed() { invalidate(); if (listener != null) listener.onColorChanged(getColor()); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        int hue = Color.HSVToColor(new float[]{hsv[0], 1, 1});
        paint.setShader(new LinearGradient(0, 0, w, 0, Color.WHITE, hue, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new LinearGradient(0, 0, 0, h, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE);
        float x = hsv[1]*w, y = (1-hsv[2])*h;
        float r = 7*getResources().getDisplayMetrics().density;
        x = Math.max(r+2, Math.min(w-r-2,x)); y = Math.max(r+2, Math.min(h-r-2,y));
        paint.setStyle(Paint.Style.FILL); paint.setColor(getColor() | 0xff000000); canvas.drawCircle(x,y,r,paint); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4); paint.setColor(Color.BLACK); canvas.drawCircle(x,y,r,paint);
        paint.setStrokeWidth(2); paint.setColor(Color.WHITE); canvas.drawCircle(x,y,r,paint);
        paint.setStyle(Paint.Style.FILL);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: getParent().requestDisallowInterceptTouchEvent(true); break;
            case MotionEvent.ACTION_MOVE: break;
            case MotionEvent.ACTION_UP: getParent().requestDisallowInterceptTouchEvent(false); performClick(); break;
            case MotionEvent.ACTION_CANCEL: getParent().requestDisallowInterceptTouchEvent(false); return true;
            default: return true;
        }
        if (getWidth()>0 && getHeight()>0) {
            hsv[1] = Math.max(0, Math.min(1,event.getX()/getWidth()));
            hsv[2] = 1-Math.max(0, Math.min(1,event.getY()/getHeight())); changed();
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
