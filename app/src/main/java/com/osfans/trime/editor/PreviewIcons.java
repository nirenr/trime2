package com.osfans.trime.editor;

import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.LruCache;
import com.osfans.trime.Config;
import com.osfans.trime.theme.KeyStyle;
import java.io.File;

/** Preview-local image resources; no live KeyStyle cache or global style mutation. */
final class PreviewIcons {
    private final LruCache<String, Bitmap> bitmaps = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private final LruCache<String,Boolean> monochrome = new LruCache<>(256);
    static File find(File styleDir, String name, String folder) {
        if (name == null || name.isEmpty()) return null;
        File theme = new File(Config.getThemeDir(Config.getTheme()));
        if (styleDir != null && styleDir.getParentFile() != null && styleDir.getParentFile().getParentFile() != null)
            theme = styleDir.getParentFile().getParentFile();
        File[] dirs = {styleDir, new File(theme, folder), new File(Config.getDataDir(), folder)};
        for (File dir : dirs) if (dir != null) {
            File f = new File(dir, name);
            if (f.isFile()) return f;
        }
        return null;
    }
    CharSequence span(Resources resources, File styleDir, String text, int color, int pixels) {
        if (text == null || text.isEmpty()) return text;
        File file = find(styleDir, text.endsWith(".png") ? text : text + ".png", "images");
        if (file == null) return text;
        try {
            String key = file.getAbsolutePath() + ":" + file.lastModified() + ":" + file.length();
            Bitmap bitmap = bitmaps.get(key);
            if (bitmap == null) {
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap == null) return text;
                bitmaps.put(key, bitmap);
            }
            BitmapDrawable drawable = new BitmapDrawable(resources, bitmap);
            Boolean pure = monochrome.get(key);
            if (pure == null) { pure = KeyStyle.getSingleColorIfPure(bitmap) != null; monochrome.put(key, pure); }
            if (pure) {
                drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            } else if (color != 0 && (color & 0x00ffffff) != 0) {
                float r=Color.red(color)/255f,g=Color.green(color)/255f,b=Color.blue(color)/255f,a=Color.alpha(color)/255f;
                drawable.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                    .213f*r,.715f*r,.072f*r,0,0,
                    .213f*g,.715f*g,.072f*g,0,0,
                    .213f*b,.715f*b,.072f*b,0,0,
                    0,0,0,a,0
                })));
            }
            drawable.setBounds(0,0,pixels,pixels);
            SpannableString result = new SpannableString(text);
            result.setSpan(new ImageSpan(drawable, DynamicDrawableSpan.ALIGN_CENTER),0,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return result;
        } catch (Exception ignored) { return text; }
    }
}
