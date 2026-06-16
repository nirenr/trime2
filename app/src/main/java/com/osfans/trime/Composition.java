/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.osfans.trime;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.text.Html;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.androlua.LuaBitmapDrawable;
import com.osfans.trime.candidate.CandidatesManager;
import com.osfans.trime.core.CandidateItem;
import com.osfans.trime.core.Rime;
import com.osfans.trime.core.RimeProto;
import com.osfans.trime.enums.WindowsPositionType;
import com.osfans.trime.theme.KeyStyle;
import com.osfans.trime.theme.Style;
import com.osfans.trime.theme.ThemeManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * 編碼區，顯示已輸入的按鍵編碼，可使用方向鍵或觸屏移動光標位置
 */
@SuppressLint("AppCompatCustomView")
public class Composition extends TextView {
    private int key_text_size, text_size, label_text_size, candidate_text_size, comment_text_size;
    private int key_text_color, text_color, label_color, candidate_text_color, comment_text_color;
    private int hilited_text_color, hilited_candidate_text_color, hilited_comment_text_color;
    private int back_color, hilited_back_color, hilited_candidate_back_color;
    private Integer key_back_color;
    private Typeface tfText, tfLabel, tfCandidate, tfComment;
    private int composition_pos[] = new int[2];
    private int max_length, sticky_lines;
    private int max_entries = 5;
    private int cloud_max_entries = 0;
    private boolean candidate_use_cursor, show_comment=true;
    private int highlightIndex;
    private Style components;
    private SpannableStringBuilder ss;
    private int span = 0;
    private String movable;
    private int move_pos[] = new int[2];
    private boolean first_move = true;
    private float mDx, mDy;
    private int mCurrentX, mCurrentY;
    private int candidate_num;
    private boolean all_phrases;
    private int cloud_num = 1;
    private int cloud_line_length;
    private String cloudSep = " ";
    private int min_length;
    private int mAlpha;
    private Integer hilited_label_color;
    private boolean mSingle;
    private boolean end_top;
    private RimeProto.Context mRimeContext;
    private WindowsPositionType winPos=WindowsPositionType.FIXED;

    public Composition(Context context) {
        super(context);
        reset();
    }


    private final String TAG = "rime";


    public void addCloud(CandidateItem cloud) {
        if (cloud_num > cloud_max_entries && cloud_max_entries != 0)
            return;
        if (cloud_max_entries < 5 && cloud.getText().length() == 1)
            return;
        int start = ss.length();
        if (cloud_num == 1) {
            if (max_entries < 1 || min_length < 1)
                ss.append("\n").append(String.valueOf(cloud_num)).append(".");
            else
                ss.append("\n☁").append(String.valueOf(cloud_num)).append(".");
        } else if (max_length > 1 && cloud_line_length + cloud.getText().length() > max_length) {
            ss.append("\n").append(String.valueOf(cloud_num)).append(".");
            cloud_line_length = 0;
        } else {
            ss.append(cloudSep).append(String.valueOf(cloud_num)).append(".");
        }
        cloud_line_length += cloud.getText().length();
        int end = ss.length();
        ss.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL), start, end, span);
        ss.setSpan(new AbsoluteSizeSpan(candidate_text_size), start, end, span);
        start = ss.length();
        ss.append(cloud.getText());
        end = ss.length();
        ss.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL), start, end, span);
        ss.setSpan(
                new CloudSpan(
                        cloud,
                        tfLabel,
                        hilited_candidate_text_color,
                        hilited_candidate_back_color,
                        label_color),
                start,
                end,
                span);
        ss.setSpan(new AbsoluteSizeSpan(candidate_text_size), start, end, span);
    }

    public void setCloud(CandidateItem cloud) {
        if (cloud_max_entries == 0) {
            return;
        }
        cloud_num = 1;
        cloud_line_length = 0;
        if (ss == null)
            ss = new SpannableStringBuilder();
        if (ss.toString().contains(cloud.getText()))
            return;
        addCloud(cloud);
        cloud_num++;
        if (ss.length() > max_length) {
            setSingleLine(false); //設置單行
        }/*else {
            measure(0,0);
            if(getMeasuredWidth()/2>getMaxWidth())
                setSingleLine(false);
        }*/
        setText(ss);
    }

    public void addCloud(String cloud) {
        if (cloud_num > cloud_max_entries && cloud_max_entries != 0)
            return;
        if (cloud_max_entries < 5 && cloud.length() == 1)
            return;
        int start = ss.length();
        if (cloud_num == 1) {
            if (max_entries < 1 || min_length < 1)
                ss.append("\n").append(String.valueOf(cloud_num)).append(".");
            else
                ss.append("\n☁").append(String.valueOf(cloud_num)).append(".");
        } else if (max_length > 1 && cloud_line_length + cloud.length() > max_length) {
            ss.append("\n").append(String.valueOf(cloud_num)).append(".");
            cloud_line_length = 0;
        } else {
            ss.append(cloudSep).append(String.valueOf(cloud_num)).append(".");
        }
        cloud_line_length += cloud.length();
        int end = ss.length();
        ss.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL), start, end, span);
        ss.setSpan(new AbsoluteSizeSpan(candidate_text_size), start, end, span);
        start = ss.length();
        ss.append(cloud);
        end = ss.length();
        ss.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL), start, end, span);
        ss.setSpan(
                new CloudSpan(
                        cloud,
                        tfLabel,
                        hilited_candidate_text_color,
                        hilited_candidate_back_color,
                        label_color),
                start,
                end,
                span);
        ss.setSpan(new AbsoluteSizeSpan(candidate_text_size), start, end, span);
    }

    public void setCloud(String cloud) {
        if (cloud_max_entries == 0) {
            return;
        }
        cloud_num = 1;
        cloud_line_length = 0;
        if (ss == null)
            ss = new SpannableStringBuilder();
        if (ss.toString().contains(cloud))
            return;
        addCloud(cloud);
        cloud_num++;
        if (ss.length() > max_length) {
            setSingleLine(false); //設置單行
        }/*else {
            measure(0,0);
            if(getMeasuredWidth()/2>getMaxWidth())
                setSingleLine(false);
        }*/
        setText(ss);
    }

    public void setCloud(ArrayList<String> cloud) {
        if (cloud_max_entries == 0) {
            TrimeService.getInstance().setCandidates(cloud);
            return;
        }
        if (cloud_max_entries == 1) {
            return;
        }
        String text = ss.toString();
        cloud_num = 1;
        cloud_line_length = 0;
        for (String s : cloud) {
            if (text.contains(s))
                continue;
            addCloud(s);
            cloud_num++;
            if (cloud_max_entries > 0 && cloud_num > 5)
                break;
        }
        if (ss.length() > max_length)
            setSingleLine(false); //設置單行
        setText(ss);
    }

    private void addComposition(String cloud1) {
        CharSequence cloud=cloud1;
        if(cloud1.startsWith("<html>")){
            cloud = Html.fromHtml(cloud1);
        }
        int start = ss.length();
        if (cloud_num == 1) {
            ss.append("\n").append(String.valueOf(cloud_num)).append(".");
        } else if (max_length > 1 && cloud_line_length + cloud.length() > max_length) {
            ss.append("\n").append(String.valueOf(cloud_num)).append(".");
            cloud_line_length = 0;
        } else {
            ss.append(cloudSep).append(String.valueOf(cloud_num)).append(".");
        }
        cloud_line_length += cloud.length();
        int end = ss.length();
        ss.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL), start, end, span);
        ss.setSpan(new AbsoluteSizeSpan(candidate_text_size), start, end, span);
        start = ss.length();
        ss.append(cloud);
        end = ss.length();
        ss.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL), start, end, span);
        ss.setSpan(
                new CloudSpan2(
                        cloud,
                        tfLabel,
                        hilited_candidate_text_color,
                        hilited_candidate_back_color,
                        label_color),
                start,
                end,
                span);
        ss.setSpan(new AbsoluteSizeSpan(candidate_text_size), start, end, span);
    }

    public void addCompositions(ArrayList<String> list) {
        if (TextUtils.isEmpty(mRimeContext.getComposition().getPreedit()) || ss == null) {
            ss = new SpannableStringBuilder();
            int len = components.getLength();
            for (int i = 0; i < len; i++) {
                Style m = components.getStyle(i);
                if (m.hasKey("move")) appendMove(m);
            }
        }

        cloud_num = 1;
        for (String s : list) {
            addComposition(s);
            cloud_num++;
        }
        if (ss.length() > max_length)
            setSingleLine(false); //設置單行
        setText(ss);
    }

    public void setCompositionSingleLine(boolean single) {
        mSingle = single;
    }

    public void setCompositionEndTop(boolean b) {
        end_top = b;
    }

    public WindowsPositionType getWindowsPosition() {
        return winPos;
    }

    private class CloudSpan2 extends ClickableSpan {
        CharSequence index;
        Typeface tf;
        int hi_text, hi_back, text;

        public CloudSpan2(CharSequence i, Typeface _tf, int _hi_text, int _hi_back, int _text) {
            super();
            index = i;
            tf = _tf;
            hi_text = _hi_text;
            hi_back = _hi_back;
            text = _text;
        }

        @Override
        public void onClick(View tv) {
            TrimeService.getInstance().commitTextAndClearComposition(index);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setUnderlineText(false);
            ds.setTypeface(tf);
            ds.setColor(text);
        }
    }

    private class CloudSpan extends ClickableSpan {
        String comment;
        String index;
        Typeface tf;
        int hi_text, hi_back, text;

        public CloudSpan(CandidateItem i, Typeface _tf, int _hi_text, int _hi_back, int _text) {
            super();
            index = i.getText();
            comment = i.getComment();
            tf = _tf;
            hi_text = _hi_text;
            hi_back = _hi_back;
            text = _text;
        }

        public CloudSpan(String i, Typeface _tf, int _hi_text, int _hi_back, int _text) {
            super();
            index = i;
            tf = _tf;
            hi_text = _hi_text;
            hi_back = _hi_back;
            text = _text;
        }

        @Override
        public void onClick(View tv) {
            TrimeService.getInstance().commitTextAndClearComposition(index);
            TrimeService.getInstance().addCloud(index);
            if(!TextUtils.isEmpty(comment))
                TrimeService.getInstance().addCloud(index,comment);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setUnderlineText(false);
            ds.setTypeface(tf);
            ds.setColor(text);
        }
    }

    private class CompositionSpan extends UnderlineSpan {
        public CompositionSpan() {
            super();
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setTypeface(tfText);
            ds.setColor(text_color);
            ds.bgColor = back_color;
        }
    }

    private class CandidateSpan extends ClickableSpan {
        int index;
        Typeface tf;
        int hi_text, hi_back, text;

        public CandidateSpan(int i, Typeface _tf, int _hi_text, int _hi_back, int _text) {
            super();
            index = i;
            tf = _tf;
            hi_text = _hi_text;
            hi_back = _hi_back;
            text = _text;
        }

        @Override
        public void onClick(View tv) {
            TrimeService.getInstance().selectPagedCandidate(index);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setUnderlineText(false);
            ds.setTypeface(tf);
            if (index == highlightIndex) {
                ds.setColor(hi_text);
                ds.bgColor = hi_back;
            } else {
                ds.setColor(text);
            }
        }
    }

    private class EventSpan extends ClickableSpan {
        Event event;

        public EventSpan(Event e) {
            super();
            event = e;
        }

        @Override
        public void onClick(View tv) {
            TrimeService.getInstance().onEvent(event);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setUnderlineText(false);
            ds.setColor(key_text_color);
            if (key_back_color != null) ds.bgColor = key_back_color;
        }
    }

    @TargetApi(21)
    public class LetterSpacingSpan extends UnderlineSpan {
        private float letterSpacing;

        /**
         * @param letterSpacing 字符間距
         */
        public LetterSpacingSpan(float letterSpacing) {
            this.letterSpacing = letterSpacing;
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setLetterSpacing(letterSpacing);
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_UP) {
            int n = getOffsetForPosition(event.getX(), event.getY());
            if (composition_pos[0] <= n && n <= composition_pos[1]) {
                String s =
                        getText().toString().substring(n, composition_pos[1]).replace(" ", "").replace("‸", "");
                n = Rime.getRimeRawInput().length() - s.length(); //從右側定位
                TrimeService.getInstance().getRime().moveCursorPos(n);
                TrimeService.getInstance().updateComposing();
                return true;
            }
        } else if (!movable.contentEquals("false")
                && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_DOWN)) {
            int n = getOffsetForPosition(event.getX(), event.getY());
            if (move_pos[0] <= n && n <= move_pos[1]) {
                if (action == MotionEvent.ACTION_DOWN) {
                    if (first_move || movable.contentEquals("once")) {
                        first_move = false;
                        int location[] = TrimeService.getInstance().getLocationInWindow(this);
                        mCurrentX = location[0];
                        mCurrentY = location[1];
                    }
                    mDx = mCurrentX - event.getRawX();
                    mDy = mCurrentY - event.getRawY();
                } else { //MotionEvent.ACTION_MOVE
                    mCurrentX = (int) (event.getRawX() + mDx);
                    mCurrentY = (int) (event.getRawY() + mDy);
                    setTranslationX(mCurrentX);
                    setTranslationY(mCurrentY);
                }
                return true;
            }
        }

        return super.onTouchEvent(event);
    }

    public void setShowComment(boolean value) {
        show_comment = value;
    }

    public void setMaxEntries(int i) {
        max_entries = i;
    }

    public void reset() {
        // 1. 获取所有相关的 KeyStyle 定义 (逻辑分层)
        Style theme = ThemeManager.getStyle();
        KeyStyle style = theme.getKeyStyle("composition");
        winPos=WindowsPositionType.fromString(style.getString("position"));
        KeyStyle mPressedStyle = style.getKeyStyle("pressed", style);

        KeyStyle mCandidateStyle = theme.getKeyStyle("candidate");
        KeyStyle mCandidatePressedStyle = mCandidateStyle.getKeyStyle("pressed", mCandidateStyle);

        KeyStyle mCommentStyle = mCandidateStyle.getKeyStyle("comment", mCandidateStyle);
        KeyStyle mCommentPressedStyle = mCommentStyle.getKeyStyle("pressed", mCommentStyle);

        KeyStyle hintKeyStyle = style.getKeyStyle("key", ThemeManager.getStyle().getKeyStyle()).getHintKeyStyle();
        KeyStyle keyStyle = style.getKeyStyle("key", ThemeManager.getStyle().getKeyStyle());

        // 2. 基础逻辑参数设置
        components = style.getStyle("window");
        max_entries = style.getInt("max_entries", max_entries);
        cloud_max_entries = style.getInt("cloud_max_entries", cloud_max_entries);
        min_length = style.getInt("min_length");
        max_length = style.getInt("max_length", 5);
        sticky_lines = style.getInt("sticky_lines");
        all_phrases = style.getBoolean("all_phrases");
        candidate_use_cursor = style.getBoolean("use_cursor", true);
        movable = style.getString("movable", "false");

        // 3. 尺寸与布局设置 (Size, Margin, Padding, Spacing)
        setMinWidth(style.getSize("min_width", 10));
        setMinHeight(style.getSize("min_height", 10));
        // 注意：setMaxWidth 被调用了两次，此处保留逻辑，先取限制值再取比例值
        setMaxWidth(Math.min(style.getSize("max_width", 10000), TrimeService.getInstance().getMaxWidth()));
        //setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * style.getFloat("width", 0.8f)));
        setMaxHeight(style.getSize("max_height", 1000));

        float line_spacing_multiplier = style.getFloat("line_spacing_multiplier", 1f);
        if (line_spacing_multiplier == 0f) line_spacing_multiplier = 1f;
        setLineSpacing(style.getFloat("line_spacing", 1f), line_spacing_multiplier);

        Style padding = style.getStyle("padding");
        setPadding(padding.getSize("left", 0), padding.getSize("top", 0), padding.getSize("right", 0), padding.getSize("bottom", 0));

        // 4. 文字尺寸设置 (Text Size)
        text_size = ThemeManager.dp2px(style.getTextSize(18));
        candidate_text_size = ThemeManager.dp2px(mCandidateStyle.getTextSize(18));
        comment_text_size = ThemeManager.dp2px(mCommentStyle.getTextSize(12));
        label_text_size = ThemeManager.dp2px(hintKeyStyle.getTextSize(12));
        key_text_size = ThemeManager.dp2px(keyStyle.getTextSize(18));

        // 5. 颜色属性设置 (Colors)
        // 普通颜色
        text_color = style.getTextColor();
        candidate_text_color = mCandidateStyle.getTextColor();
        comment_text_color = mCommentStyle.getTextColor();
        label_color = hintKeyStyle.getTextColor();
        key_text_color = keyStyle.getTextColor();

        // 选中/高亮颜色
        hilited_text_color = mPressedStyle.getTextColor();
        hilited_candidate_text_color = mCandidatePressedStyle.getTextColor();
        hilited_comment_text_color = mCommentPressedStyle.getTextColor();
        hilited_label_color = style.getKeyStyle("key", ThemeManager.getStyle().getKeyStyle()).getPressedStyle().getHintKeyStyle().getTextColor();

        // 背景颜色
        back_color = getColor(style, "background");
        key_back_color = keyStyle.getBackgroundColor();
        hilited_back_color = style.getPressedStyle().getBackgroundColor();
        hilited_candidate_back_color = getColor(mCandidatePressedStyle, "background");

        // 6. 字体设置 (Fonts)
        tfText = style.getFont();
        tfCandidate = mCandidateStyle.getFont();
        tfComment = mCommentStyle.getFont();
        tfLabel = hintKeyStyle.getFont();

        // 7. 初始状态
        setText("");
    }

    private int getColor(Style style, String s) {
        Integer clr = style.getColor(s);
        if (clr == null) {
            Drawable cd = style.getDrawable(s);
            if (cd instanceof BitmapDrawable) {
                Bitmap bmp = ((BitmapDrawable) cd).getBitmap();
                clr = bmp.getPixel(bmp.getWidth() / 2, bmp.getHeight() / 2);
            } else {
                clr = 0;
            }
        }
        return clr;
    }

    private Object getAlign(Style m) {
        Layout.Alignment i = Layout.Alignment.ALIGN_NORMAL;
        if (m.hasKey("align")) {
            String align = m.getString("align");
            switch (align) {
                case "left":
                case "normal":
                    i = Layout.Alignment.ALIGN_NORMAL;
                    break;
                case "right":
                case "opposite":
                    i = Layout.Alignment.ALIGN_OPPOSITE;
                    break;
                case "center":
                    i = Layout.Alignment.ALIGN_CENTER;
                    break;
            }
        }
        return new AlignmentSpan.Standard(i);
    }

    private void appendComposition(Style m) {
        if(end_top&&ss.length()>2)
            ss.append("\n");
        RimeProto.Context.Composition r = mRimeContext.getComposition();
        String s = r.getPreedit();
        int start, end;
        String sep = m.getString("start");
        if (!TextUtils.isEmpty(sep)) {
            start = ss.length();
            ss.append(sep);
            end = ss.length();
            ss.setSpan(getAlign(m), start, end, span);
        }
        start = ss.length();
        if(s.startsWith("<html>")) {
            ss.append(Html.fromHtml(s));
            end = ss.length();
        }
        else {
            ss.append(s);
            end = ss.length();
            ss.setSpan(getAlign(m), start, end, span);
        }
       composition_pos[0] = start;
        composition_pos[1] = end;
        ss.setSpan(new CompositionSpan(), start, end, span);
        ss.setSpan(new AbsoluteSizeSpan(text_size), start, end, span);
        if ( m.hasKey("letter_spacing")) {
            float size = m.getFloat("letter_spacing",0);
            if (size != 0f)
                ss.setSpan(new LetterSpacingSpan(size), start, end, span);
        }
        start = composition_pos[0] + r.getSelStart();
        end = composition_pos[0] + r.getSelEnd();
        ss.setSpan(new ForegroundColorSpan(hilited_text_color), start, end, span);
        ss.setSpan(new BackgroundColorSpan(hilited_back_color), start, end, span);
        sep = m.getString("end");
        if (!TextUtils.isEmpty(sep)) ss.append(sep);
    }

    private int appendCandidates(Style m, int length) {
        int start, end;
        int start_num = 0;
        RimeProto.Candidate[] candidates = mRimeContext.getMenu().getCandidates();
        if (BuildConfig.DEBUG) Log.w(TAG, "appendCandidates: "+ Arrays.toString(candidates));
        if (candidates == null || candidates.length == 0) return start_num;
        ArrayList tmp = new ArrayList();
        String sep = m.getString("start");
        highlightIndex = candidate_use_cursor ? mRimeContext.getMenu().getHighlightedCandidateIndex() : -1;
        String label_format = m.getString("label");
        String candidate_format = m.getString("candidate");
        String comment_format = m.getString("comment");
        String line = m.getString("sep");
        cloudSep = line;
        if (length < 1)
            return 0;
        int last_cand_length = 0;
        int line_length = 0;
        String[] labels = mRimeContext.getMenu().getSelectLabels();
        if (!end_top) {
            int i = -1;
            candidate_num = 0;
            int n = -1;
            for (RimeProto.Candidate o : candidates) {
                n++;
                String cand = o.getText();
                if (TextUtils.isEmpty(cand)) cand = "";
                i++;
                if (candidate_num >= max_entries && max_entries > -1) {
                    if (start_num == 0 && candidate_num == i)
                        start_num = candidate_num;
                    break;
                }
                if (cand.length() < length) {
                    if (start_num == 0 && candidate_num == i)
                        start_num = candidate_num;
                    if (all_phrases)
                        continue;
                    else
                        break;
                }
                cand = String.format(candidate_format, cand);
                String line_sep;
                if (candidate_num == 0) {
                    line_sep = sep;
                } /*else if (n % 5 == 0) {
                    line_sep = "\n";
                    line_length = 0;
                }*/ else if ((sticky_lines > 0 && sticky_lines >= i)
                        || (max_length > 0 && line_length + cand.length() > max_length)) {
                    line_sep = "\n";
                    line_length = 0;
                } else {
                    line_sep = line;
                }
                if (!TextUtils.isEmpty(line_sep)) {
                    start = ss.length();
                    ss.append(line_sep);
                    end = ss.length();
                    ss.setSpan(getAlign(m), start, end, span);
                }
                if (!TextUtils.isEmpty(label_format) && labels != null&&labels.length>i) {
                    String label = String.format(label_format, labels[i]);
                    start = ss.length();
                    ss.append(label);
                    end = ss.length();
                    ss.setSpan(
                            new CandidateSpan(
                                    i,
                                    tfLabel,
                                    hilited_label_color,
                                    hilited_candidate_back_color,
                                    label_color),
                            start,
                            end,
                            span);
                    ss.setSpan(new AbsoluteSizeSpan(label_text_size), start, end, span);
                }
                start = ss.length();
                ss.append(cand);
                end = ss.length();
                line_length += cand.length();
                ss.setSpan(getAlign(m), start, end, span);
                ss.setSpan(
                        new CandidateSpan(
                                i,
                                tfCandidate,
                                hilited_candidate_text_color,
                                hilited_candidate_back_color,
                                candidate_text_color),
                        start,
                        end,
                        span);
                ss.setSpan(new AbsoluteSizeSpan(candidate_text_size), start, end, span);
                String comment = o.getComment();
                if (!Config.is_hide_comment() && !TextUtils.isEmpty(comment_format) && !TextUtils.isEmpty(comment)) {
                    comment = String.format(comment_format, comment);
                    start = ss.length();
                    if(comment.contains("<html>")){
                        ss.append(Html.fromHtml(comment, new Html.ImageGetter() {
                            @Override
                            public Drawable getDrawable(String source) {
                                return new LuaBitmapDrawable(TrimeService.getInstance(),source);
                            }
                        },null));
                        end = ss.length();
                    }else {
                        ss.append(comment);
                        end = ss.length();
                        ss.setSpan(getAlign(m), start, end, span);
                        ss.setSpan(
                                new CandidateSpan(
                                        i,
                                        tfComment,
                                        hilited_comment_text_color,
                                        hilited_candidate_back_color,
                                        comment_text_color),
                                start,
                                end,
                                span);
                        ss.setSpan(new AbsoluteSizeSpan(comment_text_size), start, end, span);
                    }
                    //line_length += comment.length();
                }
                candidate_num++;
            }

            if (start_num == 0 && candidate_num == i + 1) start_num = candidate_num;
        } else {
            candidate_num = 0;
            int n = -1;
            int i = -1;
            int max = max_entries;
            if (max == -1)
                max = candidates.length;
            if (max > candidates.length)
                max = candidates.length;
            for (i = max - 1; i >= 0; i--) {
                RimeProto.Candidate o = candidates[i];
                n++;
                String cand = o.getText();
                if (TextUtils.isEmpty(cand)) cand = "";
                /*if (candidate_num >= max_entries && max_entries > -1) {
                    if (start_num == 0 && candidate_num == i)
                        start_num = candidate_num;
                    break;
                }
                if (cand.length() < length) {
                    if (start_num == 0 && candidate_num == i)
                        start_num = candidate_num;
                    continue;
                }*/
                cand = String.format(candidate_format, cand);
                String line_sep;
                if (candidate_num == 0) {
                    line_sep = sep;
                } else {
                    line_sep = "\n";
                    line_length = 0;
                }
                if (!TextUtils.isEmpty(line_sep)) {
                    start = ss.length();
                    ss.append(line_sep);
                    end = ss.length();
                    ss.setSpan(getAlign(m), start, end, span);
                }
                if (!TextUtils.isEmpty(label_format) && labels != null) {
                    String label = String.format(label_format, labels[i]);
                    start = ss.length();
                    ss.append(label);
                    end = ss.length();
                    ss.setSpan(
                            new CandidateSpan(
                                    i,
                                    tfLabel,
                                    hilited_label_color,
                                    hilited_candidate_back_color,
                                    label_color),
                            start,
                            end,
                            span);
                    ss.setSpan(new AbsoluteSizeSpan(label_text_size), start, end, span);
                }
                start = ss.length();
                ss.append(cand);
                end = ss.length();
                line_length += cand.length();
                ss.setSpan(getAlign(m), start, end, span);
                ss.setSpan(
                        new CandidateSpan(
                                i,
                                tfCandidate,
                                hilited_candidate_text_color,
                                hilited_candidate_back_color,
                                candidate_text_color),
                        start,
                        end,
                        span);
                ss.setSpan(new AbsoluteSizeSpan(candidate_text_size), start, end, span);
                String comment = o.getComment();
                if (!Config.is_hide_comment() && !TextUtils.isEmpty(comment_format) && !TextUtils.isEmpty(comment)) {
                    comment = String.format(comment_format, comment);
                    start = ss.length();
                    if(comment.contains("<html>")){
                        ss.append(Html.fromHtml(comment, new Html.ImageGetter() {
                            @Override
                            public Drawable getDrawable(String source) {
                                return new LuaBitmapDrawable(TrimeService.getInstance(),source);
                            }
                        },null));
                        end = ss.length();
                    } else {
                        ss.append(comment);
                        end = ss.length();
                        ss.setSpan(getAlign(m), start, end, span);
                        ss.setSpan(
                                new CandidateSpan(
                                        i,
                                        tfComment,
                                        hilited_comment_text_color,
                                        hilited_candidate_back_color,
                                        comment_text_color),
                                start,
                                end,
                                span);
                        ss.setSpan(new AbsoluteSizeSpan(comment_text_size), start, end, span);
                    }

                    //line_length += comment.length();
                }
                candidate_num++;
            }

            start_num = max;
        }

        sep = m.getString("end");
        if (!TextUtils.isEmpty(sep)) ss.append(sep);
        return start_num;
    }

    private void appendButton(Style m) {
        if (m.hasKey("when")) {
            String when = m.getString("when");
            if (when.contentEquals("paging") && !Rime.isPaging()) return;
            if (when.contentEquals("has_menu") && !Rime.hasMenu()) return;
        }
        String label;
        Event e = new Event(m.getString("click"));
        if (m.hasKey("label")) label = m.getString("label");
        else label = e.getLabel();
        int start, end;
        String sep = null;
        if (m.hasKey("start")) sep = m.getString("start");
        if (!TextUtils.isEmpty(sep)) {
            start = ss.length();
            ss.append(sep);
            end = ss.length();
            ss.setSpan(getAlign(m), start, end, span);
        }
        start = ss.length();
        ss.append(label);
        end = ss.length();
        ss.setSpan(getAlign(m), start, end, span);
        ss.setSpan(new EventSpan(e), start, end, span);
        ss.setSpan(new AbsoluteSizeSpan(key_text_size), start, end, span);
        sep = m.getString("end");
        if (!TextUtils.isEmpty(sep)) ss.append(sep);
    }

    private void appendMove(Style m) {
        String s = m.getString("move");
        int start, end;
        String sep = m.getString("start");
        if (!TextUtils.isEmpty(sep)) {
            start = ss.length();
            ss.append(sep);
            end = ss.length();
            ss.setSpan(getAlign(m), start, end, span);
        }
        start = ss.length();
        ss.append(s);
        end = ss.length();
        ss.setSpan(getAlign(m), start, end, span);
        move_pos[0] = start;
        move_pos[1] = end;
        ss.setSpan(new AbsoluteSizeSpan(key_text_size), start, end, span);
        ss.setSpan(new ForegroundColorSpan(key_text_color), start, end, span);
        sep = m.getString("end");
        if (!TextUtils.isEmpty(sep)) ss.append(sep);
    }

    public int setWindow(int length) {
        if (getVisibility() != View.VISIBLE) return 0;
        mRimeContext=Rime.getRimeContext();
        RimeProto.Context.Composition r = mRimeContext.getComposition();
        if (r == null) return 0;
        String s = r.getPreedit();
        if (TextUtils.isEmpty(s)) return 0;
        setSingleLine(true); //設置單行
        ss = new SpannableStringBuilder();
        int start_num = 0;
        if(!end_top) {
            int len = components.getLength();
            for (int i = 0; i < len; i++) {
                Style m = components.getStyle(i);
                if (m.hasKey("composition")) appendComposition(m);
                else if (m.hasKey("candidate")) start_num = appendCandidates(m, length);
                else if (m.hasKey("click")) appendButton(m);
                else if (m.hasKey("move")) appendMove(m);
            }
        }else {
            int len = components.getLength();
            for (int i = len - 1; i >= 0; i--) {
                Style m=components.getStyle(i);
                if (m.hasKey("composition")) appendComposition(m);
                else if (m.hasKey("candidate")) start_num = appendCandidates(m, length);
                else if (m.hasKey("click")) appendButton(m);
                else if (m.hasKey("move")) appendMove(m);
            }
        }

        setText(ss);
        if (mSingle) {
            setSingleLine();
            scrollTo(0, 0);
            setMaxWidth(TrimeService.getInstance().getWidth() * 10);
        } else {
            if (ss.length() > 8) {
                measure(0, 0);
                if (getMeasuredWidth() >= getMaxWidth())
                    setSingleLine(false);
                //measure(0, 0);
                //setMinWidth(getMeasuredWidth());
                //setMinHeight(getMeasuredHeight());
            }
            /*if(BuildConfig.DEBUG){
                Log.i(TAG, "setWindow:s "+ss);
                Log.i(TAG, "setWindow:w "+getWidth());
                Log.i(TAG, "setWindow:m "+getMaxWidth());
                Log.i(TAG, "setWindow:w2 "+getMeasuredWidth());
            }*/
             if (start_num > 0)
                setSingleLine(false);
        }
        setMovementMethod(LinkMovementMethod.getInstance());
        return start_num;
    }


}
