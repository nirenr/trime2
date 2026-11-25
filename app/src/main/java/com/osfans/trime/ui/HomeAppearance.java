package com.osfans.trime.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

/** Visual tokens for the launcher, independent of the active keyboard theme. */
public final class HomeAppearance {
    public final int page, surface, ink, muted, outline, accent, soft, success, successSurface;
    public final boolean dark;
    private final Context context;
    public HomeAppearance(Context context, boolean dynamic) {
        this.context = context;
        dark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        page = dark ? 0xff171e28 : 0xffeaf0fa;
        surface = dark ? 0xff26313f : 0xfffafcff;
        ink = dark ? 0xfff0f4fa : 0xff182230;
        muted = dark ? 0xffb7c3d3 : 0xff526176;
        outline = dark ? 0xff425064 : 0xffd7e0ee;
        accent = dynamic && Build.VERSION.SDK_INT >= 31 ? systemColor(dark ? "system_accent1_200" : "system_accent1_700", dark ? 0xffa9c7ff : 0xff245bb1) : dark ? 0xffa9c7ff : 0xff245bb1;
        soft = dynamic && Build.VERSION.SDK_INT >= 31 ? systemColor(dark ? "system_accent1_800" : "system_accent1_100", dark ? 0xff34465c : 0xffdbe8fc) : dark ? 0xff34465c : 0xffdbe8fc;
        success = dark ? 0xff85d8ad : 0xff176844;
        successSurface = dark ? 0xff233e38 : 0xffdbefe5;
    }
    private int systemColor(String name, int fallback) {
        int id = context.getResources().getIdentifier(name, "color", "android");
        return id == 0 ? fallback : context.getColor(id);
    }
    public int dp(float v) { return (int)(v * context.getResources().getDisplayMetrics().density + .5f); }
    public GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }
    public void clickable(View v, int color, int radius, int stroke, View.OnClickListener action) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x28ffffff : 0x20245bb1), shape(color,radius,stroke), shape(Color.WHITE,radius,0)));
        v.setOnClickListener(action); v.setFocusable(true);
    }
    public TextView text(String text, int sp, int color, boolean medium) {
        TextView v = new TextView(context); v.setText(text); v.setTextSize(sp); v.setTextColor(color);
        if (medium) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }
}
