package com.androlua;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.RadioGroup.LayoutParams;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.myopicmobile.textwarrior.android.FreeScrollingTextField;
import com.myopicmobile.textwarrior.android.OnSelectionChangedListener;
import com.myopicmobile.textwarrior.android.TouchNavigationMethod;
import com.myopicmobile.textwarrior.android.TrackpadNavigationMethod;
import com.myopicmobile.textwarrior.android.YoyoNavigationMethod;
import com.myopicmobile.textwarrior.common.ColorScheme;
import com.myopicmobile.textwarrior.common.ColorSchemeDark;
import com.myopicmobile.textwarrior.common.ColorSchemeLight;
import com.myopicmobile.textwarrior.common.Document;
import com.myopicmobile.textwarrior.common.DocumentProvider;
import com.myopicmobile.textwarrior.common.LanguageLua;
import com.myopicmobile.textwarrior.common.Lexer;
import com.myopicmobile.textwarrior.common.LinearSearchStrategy;

import com.osfans.trime.BuildConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_COPY;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CUT;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT;

public class LuaEditor extends FreeScrollingTextField {

    private final TouchNavigationMethod mDefNavigationMethod;
    private final YoyoNavigationMethod mSelNavigationMethod;
    private Document _inputtingDoc;

    private boolean _isWordWrap;
    private int luaTabSpaces;
    private final java.util.EnumMap<ColorScheme.Colorable,Integer> luaColors = new java.util.EnumMap<>(ColorScheme.Colorable.class);

    private Context mContext;

    private String _lastSelectedFile;

    private int _index;
    private LinearSearchStrategy finder;
    private int idx;
    private String mKeyword;

    @SuppressLint("StaticFieldLeak")
    public LuaEditor(final Context context) {
        super(context);
        mContext = context;
        setTypeface(Typeface.MONOSPACE);
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float size = TypedValue.applyDimension(2, BASE_TEXT_SIZE_PIXELS, dm);
        setTextSize((int) size);
        setShowLineNumbers(true);
        setHighlightCurrentRow(true);
        setWordWrap(false);
        setAutoIndentWidth(2);
        Lexer.setLanguage(LanguageLua.getInstance());
        if (isAccessibilityEnabled())
            mDefNavigationMethod=new TrackpadNavigationMethod(this);
        else
            mDefNavigationMethod=new YoyoNavigationMethod(this);
        setNavigationMethod(mDefNavigationMethod);
        mSelNavigationMethod=new YoyoNavigationMethod(this);

        TypedArray array = mContext.getTheme().obtainStyledAttributes(new int[]{
                android.R.attr.colorBackground,
                android.R.attr.textColorPrimary,
                android.R.attr.textColorHighlight,
        });
        int backgroundColor = array.getColor(0, 0xFF00FF);
        int textColor = array.getColor(1, 0xFF00FF);
        int textColorHighlight = array.getColor(2, 0xFF00FF);
        array.recycle();
        setTextColor(textColor);
        setTextHighlightColor(textColorHighlight);
    }


    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // TODO: Implement this method
        super.onLayout(changed, left, top, right, bottom);
        if (_index != 0 && right > 0) {
            moveCaret(_index);
            _index = 0;
        }
    }

    public void setDark(boolean isDark) {
        if (isDark)
            setColorScheme(new ColorSchemeDark());
        else
            setColorScheme(new ColorSchemeLight());
        TypedArray array = mContext.getTheme().obtainStyledAttributes(new int[]{
                android.R.attr.colorBackground,
                android.R.attr.textColorPrimary,
                android.R.attr.textColorHighlight,
        });
        int backgroundColor = array.getColor(0, 0xFF00FF);
        int textColor = array.getColor(1, 0xFF00FF);
        int textColorHighlight = array.getColor(2, 0xFF00FF);
        array.recycle();
        setTextColor(textColor);
        setTextHighlightColor(textColorHighlight);
        //setBackground(new ColorDrawable(backgroundColor));
        if (isYamlMode()) { rememberLuaColors(); applyYamlColors(); }
    }

    /** Enable YAML syntax highlighting without changing other LuaEditor instances. */
    @Override public void setYamlMode(boolean enable) {
        if (enable == isYamlMode()) return;
        if (enable) {
            luaTabSpaces = _tabLength;
            rememberLuaColors();
        }
        super.setYamlMode(enable);
        if (enable) {
            setTabSpaces(2);
            applyYamlColors();
        } else {
            setTabSpaces(luaTabSpaces);
            for (java.util.Map.Entry<ColorScheme.Colorable,Integer> color : luaColors.entrySet())
                getColorScheme().setColor(color.getKey(), color.getValue());
            luaColors.clear();
            invalidate();
        }
    }

    private void rememberLuaColors() {
        for (ColorScheme.Colorable color : new ColorScheme.Colorable[]{
                ColorScheme.Colorable.NAME, ColorScheme.Colorable.STRING,
                ColorScheme.Colorable.LITERAL, ColorScheme.Colorable.COMMENT,
                ColorScheme.Colorable.GLOBAL, ColorScheme.Colorable.SECONDARY,
                ColorScheme.Colorable.NON_PRINTING_GLYPH})
            luaColors.put(color, getColorScheme().getColor(color));
    }

    private void applyYamlColors() {
        boolean dark = getColorScheme().isDark();
        setBasewordColor(dark ? 0xff9cdcfe : 0xff174a96);
        setStringColor(dark ? 0xffce9178 : 0xffa13a24);
        setUserwordColor(dark ? 0xffb5cea8 : 0xff17683d);
        setCommentColor(dark ? 0xff86a67a : 0xff637b53);
        setGlobalColor(dark ? 0xffc586c0 : 0xff8051a0);
        getColorScheme().setColor(ColorScheme.Colorable.SECONDARY, dark ? 0xffd4d4d4 : 0xff526176);
        setLineColor(dark ? 0xff7d8997 : 0xff98a3b0);
        invalidate();
    }

    public void addNames(String[] names) {
        LanguageLua lang = (LanguageLua) Lexer.getLanguage();
        String[] old = lang.getNames();
        String[] news = new String[old.length + names.length];
        System.arraycopy(old, 0, news, 0, old.length);
        System.arraycopy(names, 0, news, old.length, names.length);
        lang.setNames(news);
        Lexer.setLanguage(lang);
        respan();
        invalidate();

    }

    public void addPackage(String pkg, String[] names) {
        LanguageLua lang = (LanguageLua) Lexer.getLanguage();
        lang.addBasePackage(pkg, names);
        Lexer.setLanguage(lang);
        respan();
        invalidate();
    }

    public void removePackage(String pkg) {
        LanguageLua lang = (LanguageLua) Lexer.getLanguage();
        lang.removeBasePackage(pkg);
        Lexer.setLanguage(lang);
        respan();
        invalidate();
    }

    public void setPanelBackgroundColor(int color) {
        // TODO: Implement this method
        _autoCompletePanel.setBackgroundColor(color);
    }

    public void setPanelBackground(Drawable color) {
        // TODO: Implement this method
        _autoCompletePanel.setBackground(color);
    }

    public void setPanelTextColor(int color) {
        // TODO: Implement this method
        _autoCompletePanel.setTextColor(color);
    }

    public void setKeywordColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.KEYWORD, color);
    }

    public void setUserwordColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.LITERAL, color);
    }

    public void setBasewordColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.NAME, color);
    }

    public void setStringColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.STRING, color);
    }

    public void setCommentColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.COMMENT, color);
    }

    public void setBackgroundColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.BACKGROUND, color);
        super.setBackgroundColor(color);
    }

    public void setTextColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.FOREGROUND, color);
    }

    public void setGlobalColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.GLOBAL, color);
    }

    public void setLocalColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.LOCAL, color);
    }

    public void setUpvalColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.UPVAL, color);
    }

    public void setTextHighlightColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.SELECTION_BACKGROUND, color);
    }

    public void setLineColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.NON_PRINTING_GLYPH, color);
    }

    public String getSelectedText() {
        // TODO: Implement this method
        return _hDoc.subSequence(getSelectionStart(), getSelectionEnd() - getSelectionStart()).toString();
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        final int filteredMetaState = event.getMetaState() & ~KeyEvent.META_CTRL_MASK;
        if (KeyEvent.metaStateHasNoModifiers(filteredMetaState)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_A:
                    selectAll();
                    return true;
                case KeyEvent.KEYCODE_X:
                    cut();
                    return true;
                case KeyEvent.KEYCODE_C:
                    copy();
                    return true;
                case KeyEvent.KEYCODE_V:
                    paste();
                    return true;
                case KeyEvent.KEYCODE_L:
                    format();
                    return true;
                case KeyEvent.KEYCODE_S:
                    search();
                    return true;
                case KeyEvent.KEYCODE_G:
                    gotoLine();
                    return true;
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }

    public void gotoLine() {
        // TODO: Implement this method
        startGotoMode();
    }

    public void search() {
        // TODO: Implement this method
        startFindMode();
    }

    public void startGotoMode() {
        // TODO: Implement this method
        startActionMode(new ActionMode.Callback() {

            private int idx;

            private EditText edit;

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // TODO: Implement this method
                mode.setTitle("转到");
                mode.setSubtitle(null);

                edit = new EditText(mContext) {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (s.length() > 0) {
                            idx = 0;
                            _gotoLine();
                        }
                    }

                };

                edit.setSingleLine(true);
                edit.setInputType(2);
                edit.setImeOptions(2);
                edit.setOnEditorActionListener(new OnEditorActionListener() {

                    @Override
                    public boolean onEditorAction(TextView p1, int p2, KeyEvent p3) {
                        // TODO: Implement this method
                        _gotoLine();
                        return true;
                    }
                });
                edit.setLayoutParams(new LayoutParams(getWidth() / 3, -1));
                menu.add(0, 1, 0, "").setActionView(edit);
                menu.add(0, 2, 0, mContext.getString(android.R.string.ok));
                edit.requestFocus();
                return true;
            }

            private void _gotoLine() {
                String s = edit.getText().toString();
                if (s.isEmpty())
                    return;

                int l = Integer.valueOf(s);
                if (l > _hDoc.getRowCount()) {
                    l = _hDoc.getRowCount();
                }
                gotoLine(l);
                // TODO: Implement this method
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                // TODO: Implement this method
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                // TODO: Implement this method
                switch (item.getItemId()) {
                    case 1:
                        break;
                    case 2:
                        _gotoLine();
                        break;

                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode p1) {
                // TODO: Implement this method
            }
        });

    }

    public void startFindMode() {
        // TODO: Implement this method
        startActionMode(new ActionMode.Callback() {

            private LinearSearchStrategy finder;

            private int idx;

            private EditText edit;

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // TODO: Implement this method
                mode.setTitle("搜索");
                mode.setSubtitle(null);

                edit = new EditText(mContext) {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (s.length() > 0) {
                            idx = 0;
                            findNext();
                        }
                    }
                };
                edit.setSingleLine(true);
                edit.setImeOptions(3);
                edit.setOnEditorActionListener(new OnEditorActionListener() {

                    @Override
                    public boolean onEditorAction(TextView p1, int p2, KeyEvent p3) {
                        // TODO: Implement this method
                        findNext();
                        return true;
                    }
                });
                edit.setLayoutParams(new LayoutParams(getWidth() / 3, -1));
                menu.add(0, 1, 0, "").setActionView(edit);
                menu.add(0, 2, 0, mContext.getString(android.R.string.search_go));
                edit.requestFocus();
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                // TODO: Implement this method
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                // TODO: Implement this method
                switch (item.getItemId()) {
                    case 1:
                        break;
                    case 2:
                        findNext();
                        break;

                }
                return false;
            }

            private void findNext() {
                // TODO: Implement this method
                finder = new LinearSearchStrategy();
                String kw = edit.getText().toString();
                if (kw.isEmpty()) {
                    selectText(false);
                    return;
                }
                idx = finder.find(getText(), kw, idx, getText().length(), false, false);
                if (idx == -1) {
                    selectText(false);
                    Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show();
                    idx = 0;
                    return;
                }
                setSelection(idx, edit.getText().length());
                idx += edit.getText().length();
                moveCaret(idx);
            }

            @Override
            public void onDestroyActionMode(ActionMode p1) {
                // TODO: Implement this method
            }
        });

    }

    @Override
    public void setWordWrap(boolean enable) {
        // TODO: Implement this method
        _isWordWrap = enable;
        super.setWordWrap(enable);
    }

    public DocumentProvider getText() {
        return createDocumentProvider();
    }

    public void setText(CharSequence c) {
        //TextBuffer text=new TextBuffer();
        Document doc = new Document(this);
        doc.setWordWrap(_isWordWrap);
        doc.setText(c);
        setDocumentProvider(new DocumentProvider(doc));
        //doc.analyzeWordWrap();
    }

    public void insert(int idx, String text) {
        selectText(false);
        moveCaret(idx);
        paste(text);
        //_hDoc.insert(idx,text);
    }

    public void setText(CharSequence c, boolean isRep) {
        replaceText(0, getLength() - 1, c.toString());
    }

    public void setSelection(int index) {
        selectText(false);
        if (!hasLayout())
            moveCaret(index);
        else
            _index = index;
    }

    public void gotoLine(int line) {
        if (line > _hDoc.getRowCount()) {
            line = _hDoc.getRowCount();
        }
        int i = getText().getLineOffset(line - 1);
        setSelection(i);
    }

    public void undo() {
        DocumentProvider doc = createDocumentProvider();
        int newPosition = doc.undo();

        if (newPosition >= 0) {
            setEdited(true);
            respan();
            selectText(false);
            moveCaret(newPosition);
            invalidate();
        }

    }

    public void redo() {
        DocumentProvider doc = createDocumentProvider();
        int newPosition = doc.redo();

        if (newPosition >= 0) {
            setEdited(true);

            respan();
            selectText(false);
            moveCaret(newPosition);
            invalidate();
        }
    }

    public String getFilePath() {
        return _lastSelectedFile;
    }

    public void open(String filename) throws IOException {
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        boolean yaml = lower.endsWith(".yaml") || lower.endsWith(".yml");
        if (!new File(filename).exists()) {
            try {
                new File(filename).createNewFile();
            } catch (Exception e) {
                if (BuildConfig.DEBUG)
                    e.printStackTrace();
            }
            _lastSelectedFile = filename;
            setYamlMode(yaml);
            setText("");
            return;
        }

        StringBuilder buf = new StringBuilder();
        if (yaml) {
            // Preserve line endings, trailing newline and indentation verbatim.
            try (java.io.Reader reader = new java.io.InputStreamReader(new java.io.FileInputStream(filename), java.nio.charset.StandardCharsets.UTF_8)) {
                char[] chunk = new char[8192]; int count;
                while ((count = reader.read(chunk)) != -1) buf.append(chunk, 0, count);
            }
        } else {
            try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
                String line;
                while ((line = reader.readLine()) != null) buf.append(line).append("\n");
            }
            if (buf.length() > 1) buf.setLength(buf.length() - 1);
        }
        _lastSelectedFile = filename;
        setYamlMode(yaml);
        setText(buf);
        /*
        File inputFile = new File(filename);
        _inputtingDoc = new Document(this);
        _inputtingDoc.setWordWrap(this.isWordWrap());
        ReadTask _taskRead = new ReadTask(this, inputFile);
        _taskRead.start();*/
    }

    public boolean save() throws IOException {
        return save(_lastSelectedFile);
    }

    public boolean save(String filename) throws IOException {
        if (filename == null)
            return true;
        File outputFile = new File(filename);

        if (outputFile.exists()) {
            if (!outputFile.canWrite()) {
                return false;
            }
        } else {
            if (!outputFile.getParentFile().exists())
                if (!outputFile.getParentFile().mkdirs())
                    return false;
        }

        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
        writer.write(getText().toString());
        writer.close();
        return true;
    }

    public boolean findNext() {
        return findNext(mKeyword);
    }

    public boolean findNext(String keyword) {
        if (!keyword.equals(mKeyword)) {
            mKeyword = keyword;
            idx = 0;
        }
        // TODO: Implement this method
        finder = new LinearSearchStrategy();
        String kw = mKeyword;
        if (kw.isEmpty()) {
            selectText(false);
            return false;
        }
        idx = finder.find(getText(), kw, idx, getText().length(), false, false);
        if (idx == -1) {
            selectText(false);
            Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show();
            idx = 0;
            return false;
        }
        setSelection(idx, mKeyword.length());
        idx += mKeyword.length();
        moveCaret(idx);
        return true;
    }

    public boolean findBack() {
        return findBack(mKeyword);
    }

    public boolean findBack(String keyword) {
        if (!keyword.equals(mKeyword)) {
            mKeyword = keyword;
            idx = 0;
        }
        // TODO: Implement this method
        finder = new LinearSearchStrategy();
        String kw = mKeyword;
        if (kw.isEmpty()) {
            selectText(false);
            return false;
        }
        idx = finder.findBackwards(getText(), kw, idx - keyword.length() - 1, 0, false, false);
        if (idx == -1) {
            selectText(false);
            Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show();
            idx = getLength();
            return false;
        }
        setSelection(idx, mKeyword.length());
        idx += mKeyword.length();
        moveCaret(idx);
        return true;
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {

        switch (action) {
            case AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
                switch (arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT)) {
                    case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE:
                        moveCaretDown();
                        break;
                    case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER:
                        moveCaretRight();
                        break;
                }
                return true;
            case ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
                switch (arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT)) {
                    case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE:
                        moveCaretUp();
                        break;
                    case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER:
                        moveCaretLeft();
                        break;
                }
                return true;
            case ACTION_SET_SELECTION:
                int start = arguments.getInt(ACTION_ARGUMENT_SELECTION_START_INT, 0);
                int end = arguments.getInt(ACTION_ARGUMENT_SELECTION_END_INT, 0);
                boolean sel = arguments.getBoolean(ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, false);
                if (sel)
                    setSelectionRange(start, end-start);
                else
                    setSelection(start, end);
                return true;
            case ACTION_SET_TEXT:
                selectText(false);
                if(arguments==null)
                    setText("",true);
                else
                    setText(arguments.getCharSequence(ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE),true);
                return true;
            case ACTION_PASTE:
                paste();
                return true;
            case ACTION_COPY:
                copy();
                return true;
            case ACTION_CUT:
                cut();
                return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }


}
