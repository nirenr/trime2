package com.osfans.trime.ui;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/** Edge-to-edge for native screens with their own in-content toolbar. */
public final class EdgeToEdge {
    private EdgeToEdge() {}

    public static void apply(Activity activity, View root) {
        Window window = activity.getWindow();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT < 30) return; // Keep legacy decor/resize behavior.
        window.setDecorFitsSystemWindows(false);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setStatusBarContrastEnforced(false);
        window.setNavigationBarContrastEnforced(true); // Keep three-button navigation legible.
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(attrs);
        updateAppearance(activity);
        final int left = root.getPaddingLeft(), top = root.getPaddingTop();
        final int right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom);
            // This root owns all these insets; children must not apply them twice.
            return WindowInsets.CONSUMED;
        });
        if (root.isAttachedToWindow()) root.requestApplyInsets();
        else root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                v.removeOnAttachStateChangeListener(this);
                v.requestApplyInsets();
            }
            @Override public void onViewDetachedFromWindow(View v) {}
        });
    }

    public static void updateAppearance(Activity activity) {
        if (Build.VERSION.SDK_INT < 30) return;
        boolean light = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsController controller = activity.getWindow().getInsetsController();
        if (controller != null) {
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(light ? mask : 0, mask);
        }
    }
}
