package com.osfans.trime.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.osfans.trime.R;

/** One accessible click target, with optional current configuration subtitle. */
public final class HomeCardView extends LinearLayout {
    private final TextView title, subtitle;
    private final String label;
    public HomeCardView(Context context, HomeAppearance a, int icon, int color, String label, boolean vertical, String value, View.OnClickListener action) {
        super(context);
        this.label = label;
        setOrientation(vertical ? VERTICAL : HORIZONTAL);
        setGravity(vertical ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        setPadding(a.dp(12),a.dp(12),a.dp(10),a.dp(12));
        setMinimumHeight(a.dp(vertical ? 96 : 64));
        a.clickable(this,a.surface,16,a.outline,action);
        ImageView image = icon(context,icon,color);
        addView(image,new LayoutParams(a.dp(24),a.dp(24)));
        LinearLayout content = new LinearLayout(context); content.setOrientation(VERTICAL);
        LayoutParams cp = vertical ? new LayoutParams(-1,-2) : new LayoutParams(0,-2,1);
        if(vertical) cp.topMargin=a.dp(8); else cp.setMarginStart(a.dp(10));
        addView(content,cp);
        LinearLayout line = new LinearLayout(context); line.setGravity(Gravity.CENTER_VERTICAL);
        title=a.text(label,14,a.ink,true);
        line.addView(title,new LayoutParams(0,-2,1));
        ImageView arrow = icon(context,R.drawable.home_chevron,a.muted);
        arrow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.addView(arrow,new LayoutParams(a.dp(16),a.dp(16)));
        content.addView(line,new LayoutParams(-1,-2));
        subtitle = a.text("",12,a.muted,false); subtitle.setSingleLine(true); subtitle.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(subtitle,new LayoutParams(-1,-2));
        title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        subtitle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setValue(value);
    }
    public void setValue(String value) {
        subtitle.setText(value == null ? "" : value);
        subtitle.setVisibility(value == null || value.isEmpty() ? GONE : VISIBLE);
        setContentDescription(label + (value == null || value.isEmpty() ? "" : "，" + value));
    }
    public static ImageView icon(Context context,int res,int color) {
        ImageView v=new ImageView(context); v.setImageResource(res); v.setColorFilter(color);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER); v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); return v;
    }
}
