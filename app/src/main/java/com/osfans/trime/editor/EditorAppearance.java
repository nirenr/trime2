package com.osfans.trime.editor;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.TextView;

/** Local visual tokens; independent of the configuration being edited. */
final class EditorAppearance {
    final int page, surface, ink, muted, outline, accent, onAccent, soft;
    private final Context context;
    EditorAppearance(Context context) {
        this.context = context;
        boolean dark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        page = dark ? 0xff141820 : 0xfff2f5fa;
        surface = dark ? 0xff202630 : Color.WHITE;
        ink = dark ? 0xffe6eaf2 : 0xff202938;
        muted = dark ? 0xffaab5c6 : 0xff536278;
        outline = dark ? 0xff394353 : 0xffdce3ed;
        accent = dark ? 0xffa9c7ff : 0xff315da8;
        onAccent = dark ? 0xff142d55 : Color.WHITE;
        soft = dark ? 0xff293c59 : 0xffe5edfc;
    }
    int dp(float v) { return (int)(context.getResources().getDisplayMetrics().density*v+.5f); }
    GradientDrawable shape(int fill, float radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }
    void clickable(View v, int fill, float radius, int stroke) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x257b92bd), shape(fill,radius,stroke), shape(Color.WHITE,radius,0)));
    }
    TextView label(String value, int size, int color, boolean strong) {
        TextView t = new TextView(context); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if(strong) t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); return t;
    }
}
