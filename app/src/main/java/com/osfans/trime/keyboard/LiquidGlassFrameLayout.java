package com.osfans.trime.keyboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LiquidGlassFrameLayout extends FrameLayout {
    private Paint glassPaint;
    private RuntimeShader liquidShader;

    private float cornerRadius = 48f; // 圆角半径
    private Path clipPath = new Path();
    private RectF rectF = new RectF();

    private Drawable fullBackgroundDrawable;
    private Bitmap cachedFullBitmap;

    private boolean isPressedState = false;
    private float touchX = 0f;
    private float touchY = 0f;

    public LiquidGlassFrameLayout(@NonNull Context context) {
        super(context);
        init();
    }

    public LiquidGlassFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LiquidGlassFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);

        glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String agslCode = "uniform shader bg_texture;\n" +
                    "    uniform float2 u_resolution;\n" +
                    "    uniform float2 u_touch;\n" +
                    "    uniform float u_pressed;\n" +
                    "\n" +
                    "    half4 main(float2 fragCoord) {\n" +
                    "        float2 uv = fragCoord / u_resolution;\n" +
                    "        \n" +
                    "        float2 waveCoord = fragCoord * 0.02;\n" +
                    "        float2 distortion = sin(waveCoord) * 3.0;\n" +
                    "        \n" +
                    "        float dist = distance(fragCoord, u_touch);\n" +
                    "        float ripple = u_pressed * sin(dist * 0.06 - 10.0) * 6.0 / (dist * 0.05 + 1.0);\n" +
                    "        \n" +
                    "        float2 totalOffset = distortion + float2(ripple);\n" +
                    "        half4 bgColor = bg_texture.eval(fragCoord + totalOffset);\n" +
                    "        \n" +
                    "        // 【调试开关】：如果 bg_texture 采样失败（或没传进来），按键会显示为半透明红色！\n" +
                    "        if (bgColor.a <= 0.01) {\n" +
                    "            return half4(1.0, 0.0, 0.0, 0.6); \n" +
                    "        }\n" +
                    "        \n" +
                    "        // 正常渲染逻辑\n" +
                    "        float topSpecular = pow(1.0 - uv.y, 2.0) * 0.5;\n" +
                    "        float edgeGlow = (pow(uv.x, 5.0) + pow(1.0 - uv.x, 5.0) + pow(uv.y, 5.0)) * 0.08;\n" +
                    "        float specularMix = topSpecular + edgeGlow;\n" +
                    "        \n" +
                    "        half3 finalRgb = bgColor.rgb + half3(specularMix + abs(ripple) * 0.15);\n" +
                    "        \n" +
                    "        return half4(finalRgb, 1.0);\n" +
                    "    }" ;

            try {
                liquidShader = new RuntimeShader(agslCode);
                liquidShader.setFloatUniform("u_pressed", 0.0f);
                liquidShader.setFloatUniform("u_touch", 0.0f, 0.0f);

                // 【关键修复】赋予一个默认的兜底纹理，防止着色器因纹理未绑定而崩溃白屏
                Bitmap dummyBmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                dummyBmp.eraseColor(0x33FFFFFF);
                BitmapShader dummyShader = new BitmapShader(dummyBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                liquidShader.setInputShader("bg_texture", dummyShader);

                glassPaint.setShader(liquidShader);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 设置键盘背景（自带防白屏与自动排队机制）
     */
    public void setKeyboardBackground(Drawable background) {
        this.fullBackgroundDrawable = background;
        if (background != null) {
            if (background instanceof BitmapDrawable) {
                cachedFullBitmap = ((BitmapDrawable) background).getBitmap();
            } else {
                int w = background.getIntrinsicWidth() > 0 ? background.getIntrinsicWidth() : 1080;
                int h = background.getIntrinsicHeight() > 0 ? background.getIntrinsicHeight() : 1920;
                cachedFullBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(cachedFullBitmap);
                background.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                background.draw(canvas);
            }
        }

        // 如果此时 View 还没有完成测量（宽高为0），则 Post 到队列中等测量完成后再裁剪绑定
        if (getWidth() > 0 && getHeight() > 0) {
            updateCroppedTexture();
        } else {
            post(this::updateCroppedTexture);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            updateCroppedTexture();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (liquidShader != null) {
            // 【核心致命漏掉点】必须把当前控件的宽高传给 AGSL 的 u_resolution！
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                liquidShader.setFloatUniform("u_resolution", (float) w, (float) h);
            }
        }
        updateCroppedTexture();
    }

    private void updateCroppedTexture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || liquidShader == null) return;
        if (cachedFullBitmap == null || getWidth() <= 0 || getHeight() <= 0) {
            android.util.Log.e("LiquidGlass", "[Aborted] bitmap null or size <= 0");
            return;
        }

        try {
            View referenceView = getRootView();
            android.view.ViewParent parent = getParent();
            while (parent instanceof View) {
                if (parent.getClass().getSimpleName().contains("KeyboardView")) {
                    referenceView = (View) parent;
                    break;
                }
                parent = ((View) parent).getParent();
            }

            int[] refLocation = new int[2];
            int[] myLocation = new int[2];

            if (referenceView != null) {
                referenceView.getLocationInWindow(refLocation);
            }
            getLocationInWindow(myLocation);

            int viewLeft = myLocation[0] - refLocation[0];
            int viewTop = myLocation[1] - refLocation[1];
            int w = getWidth();
            int h = getHeight();

            int refWidth = referenceView != null && referenceView.getWidth() > 0 ? referenceView.getWidth() : cachedFullBitmap.getWidth();
            int refHeight = referenceView != null && referenceView.getHeight() > 0 ? referenceView.getHeight() : cachedFullBitmap.getHeight();

            float scaleX = (float) cachedFullBitmap.getWidth() / refWidth;
            float scaleY = (float) cachedFullBitmap.getHeight() / refHeight;

            int srcX = (int) (viewLeft * scaleX);
            int srcY = (int) (viewTop * scaleY);
            int srcW = (int) (w * scaleX);
            int srcH = (int) (h * scaleY);

            srcX = Math.max(0, Math.min(srcX, cachedFullBitmap.getWidth() - 1));
            srcY = Math.max(0, Math.min(srcY, cachedFullBitmap.getHeight() - 1));
            srcW = Math.max(1, Math.min(srcW, cachedFullBitmap.getWidth() - srcX));
            srcH = Math.max(1, Math.min(srcH, cachedFullBitmap.getHeight() - srcY));

            android.util.Log.d("LiquidGlass", "Success crop: srcX=" + srcX + ", srcY=" + srcY + ", srcW=" + srcW + ", srcH=" + srcH);

            if (srcW > 0 && srcH > 0) {
                Bitmap cropped = Bitmap.createBitmap(cachedFullBitmap, srcX, srcY, srcW, srcH);
                BitmapShader bitmapShader = new BitmapShader(cropped, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                liquidShader.setInputShader("bg_texture", bitmapShader);
                invalidate();
            }
        } catch (Exception e) {
            android.util.Log.e("LiquidGlass", "Error in updateCroppedTexture", e);
            e.printStackTrace();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isPressedState = true;
                touchX = event.getX();
                touchY = event.getY();

                animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).start();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && liquidShader != null) {
                    liquidShader.setFloatUniform("u_touch", touchX, touchY);
                    liquidShader.setFloatUniform("u_pressed", 1.0f);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isPressedState = false;

                animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && liquidShader != null) {
                    liquidShader.setFloatUniform("u_pressed", 0.0f);
                    invalidate();
                }
                performClick();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.clipPath(clipPath);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && liquidShader != null) {
            canvas.drawRect(rectF, glassPaint);
        } else {
            // Android 13 以下版本的安全降级色
            Paint fallback = new Paint(Paint.ANTI_ALIAS_FLAG);
            fallback.setColor(0x33FFFFFF);
            canvas.drawRect(rectF, fallback);
        }

        canvas.restore();
        super.onDraw(canvas);
    }
}
