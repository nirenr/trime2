/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime;

import static com.osfans.trime.core.RimeKeyMap.RimeKey_VoidSymbol;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityViewCommand;

import com.androlua.LuaActivity;
import com.androlua.LuaDialog;
import com.androlua.LuaUtil;
import com.osfans.trime.candidate.CandidateView;
import com.osfans.trime.candidate.CandidatesManager;
import com.osfans.trime.core.CandidateItem;
import com.osfans.trime.core.Rime;
import com.osfans.trime.core.RimeMessage;
import com.osfans.trime.core.RimeProto;
import com.osfans.trime.dialog.KeyboardDialog;
import com.osfans.trime.dialog.OptionsDialog;
import com.osfans.trime.dialog.SchemaGroupDialog;
import com.osfans.trime.dialog.StyleDialog;
import com.osfans.trime.dialog.ThemeDialog;
import com.osfans.trime.enums.InlineModeType;
import com.osfans.trime.enums.WindowsPositionType;
import com.osfans.trime.keyboard.FloatKeyboard;
import com.osfans.trime.keyboard.ModifierState;
import com.osfans.trime.theme.ThemeManager;
import com.osfans.trime.util.Function;
import com.osfans.trime.util.HttpUtil;

import org.luaj.Globals;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.lib.ResourceFinder;
import org.luaj.lib.jse.JsePlatform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrimeService extends InputMethodService {
    // 1. 常量与静态变量
    private static final String TAG = "TrimeService";
    private static TrimeService sInstance;


    // 成员变量 - 逻辑处理与状态
    private Rime mRime;
    private final Handler mHandler = new Handler();
    private final Rime.Consumer<RimeMessage<?>> mMessageHandler = this::handleRimeMessage;
    private AlertDialog mOptionsDialog;
    private CharSequence lastCommittedText;
    private InlineModeType inlinePreedit = InlineModeType.INLINE_NONE;

    // 成员变量 - 标志位
    private boolean mShowExtractedCandidatesView = false;
    private boolean keyUpNeeded;
    private boolean enterAsLineBreak;
    private String mActionLabel;
    private boolean mTempAsciiMode;
    private boolean canCompose;
    private boolean reset_ascii_mode;
    private boolean mAsciiMode;
    private View mCustomView;
    private List<String> mClipboard;
    private ClipboardManager manager;
    private ClipboardManager.OnPrimaryClipChangedListener mOnPrimaryClipChangedListener;
    private int mClipboardSize = 300;
    private ArrayList<String> mPhrase;
    private RootInputView mRootInputView;
    private int orientation;
    private int uiMode;
    private String mLastInputClass;
    private Globals globals;
    private Speech mSpeech;
    private boolean mShowComposingText;
    private int mCurrSelEnd;
    private int mCurrSelStart;
    private int previousIdx;
    private int nextIdx;
    private RectF mPopupRectF=new RectF();

    // 3. 静态访问器与生命周期
    public static TrimeService getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        CandidatesManager.initStroke(this);
        sInstance = this;
        ThemeManager.setTheme(Config.getTheme());
        mRootInputView = new RootInputView(this);
        LuaUtil.rmDir(new File(Config.getDataDir()), "LOCK");
        mRime = new Rime(new Runnable() {
            @Override
            public void run() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // String soft_cursor_key = "soft_cursor";
                        // Rime.setRimeOption(soft_cursor_key, true); //軟光標
                        // mRootInputView.setSchema(Rime.getCurrentRimeSchema());
                        String id = Function.getPref(TrimeService.this).getString("select_schema_id", "");
                        if (!TextUtils.isEmpty(id))
                            Rime.selectRimeSchema(id);
                    }
                });
            }
        });
        mRime.startup();
        Rime.registerRimeMessageHandler(mMessageHandler);
        registerClipEvents();
        //String id = Function.getPref(this).getString("select_schema_id", "");
        //if(!TextUtils.isEmpty(id))
        //    mRootInputView.setSchema(id);

        ViewCompat.addAccessibilityAction(mRootInputView, "nextCandidate", new AccessibilityViewCommand() {
            @Override
            public boolean perform(@NonNull View view, @Nullable CommandArguments arguments) {
                if (!isComposing())
                    return false;
                mRootInputView.nextCandidate();
                return true;
            }
        });
        ViewCompat.addAccessibilityAction(mRootInputView, "prevCandidate", new AccessibilityViewCommand() {
            @Override
            public boolean perform(@NonNull View view, @Nullable CommandArguments arguments) {
                if (!isComposing())
                    return false;
                mRootInputView.prevCandidate();
                return true;
            }
        });
        ViewCompat.addAccessibilityAction(mRootInputView, "selectCandidate", new AccessibilityViewCommand() {
            @Override
            public boolean perform(@NonNull View view, @Nullable CommandArguments arguments) {
                if (!isComposing())
                    return false;
                selectCandidate(Rime.getHighlightRimeCandidate());
                return true;
            }
        });
        ViewCompat.addAccessibilityAction(mRootInputView, "delete", new AccessibilityViewCommand() {
            @Override
            public boolean perform(@NonNull View view, @Nullable CommandArguments arguments) {
                if (!isComposing())
                    return false;
                onKey(KeyEvent.KEYCODE_DEL, 0);
                return true;
            }
        });
    }

    @Override
    public void onDestroy() {
        unregisterClipEvents();
        mHandler.removeCallbacksAndMessages(null);
        sInstance = null;
        Rime.unregisterRimeMessageHandler(mMessageHandler);
        mRime.finalize();
        ThemeManager.callFunction("onDestroy");
        super.onDestroy();
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        showToolbarView(true);
        showClipboardView(false);
        showSymbolsView(false);
        showExtractedCandidatesView(false);
        showCustomView(null);
        String soft_cursor_key = "soft_cursor";
        Rime.setRimeOption(soft_cursor_key, true); //軟光標
        ThemeManager.callFunction("onWindowShown");
        mSpeech = new Speech(this);
    }

    @Override
    public void onFinishInput() {
        Log.w(TAG, "onFinishInput: " + Rime.isComposing());
        if (Rime.isComposing()) {
            onKey(KeyEvent.KEYCODE_ESCAPE, 0);
            mRime.clearComposition();
        }
        super.onFinishInput();
        ThemeManager.callFunction("onFinishInput");

    }

    @Override
    public void onWindowHidden() {
        Log.w(TAG, "onWindowHidden: " + Rime.isComposing());
        if (Rime.isComposing()) {
            onKey(KeyEvent.KEYCODE_ESCAPE, 0);
            mRime.clearComposition();
        }
        super.onWindowHidden();
        ThemeManager.callFunction("onWindowHidden");
        if (mSpeech != null)
            mSpeech.destroy();
        mSpeech = null;
    }

    @Override
    public View onCreateCandidatesView() {
        return super.onCreateCandidatesView();
    }

    @Override
    public View onCreateInputView() {
        return mRootInputView;
    }

    @Override
    public void onConfigureWindow(Window win, boolean isFullscreen, boolean isCandidatesOnly) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly);
        win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        win.setFormat(PixelFormat.RGBA_8888);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (orientation != newConfig.orientation) {
            // Clear composing text and candidates for orientation change.
            escape();
            orientation = newConfig.orientation;
            mRootInputView.setTheme(Config.getTheme());
        }
        if (uiMode != newConfig.uiMode) {
            uiMode = newConfig.uiMode;
            ThemeManager.setTheme(Config.getTheme());
            mRootInputView.setTheme(Config.getTheme());
        }
        ThemeManager.callFunction("onConfigurationChanged", newConfig);
    }

    public boolean isLandscape() {
        return orientation == Configuration.ORIENTATION_LANDSCAPE;
    }


    @Override
    public void setInputView(View view) {
        ViewParent parent = view.getParent();
        if (parent != null && parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        }
        //FrameLayout fr = new FrameLayout(this);
        //fr.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM));
        super.setInputView(view);
        FrameLayout mInputFrame = getWindow().findViewById(android.R.id.inputArea);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.height = getResources().getDisplayMetrics().heightPixels;
        lp.width = getResources().getDisplayMetrics().widthPixels;
        lp.gravity = Gravity.BOTTOM;
        view.setLayoutParams(lp);
        mInputFrame.updateViewLayout(view, lp);
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        outInsets.contentTopInsets = outInsets.visibleTopInsets;
        View mRoot = mRootInputView.getRoot();
        if (mRoot == null) return;
        int[] lc = getLocationInWindow(mRoot);
        outInsets.touchableRegion.setEmpty();
        if (Config.isFloatMode()) {
            outInsets.contentTopInsets = getHeight();
            outInsets.visibleTopInsets = getHeight();
            outInsets.touchableRegion.set(lc[0], lc[1], lc[0] + mRoot.getWidth(), lc[1] + mRoot.getHeight());
            View mPreedit = mRootInputView.getPreedit();
            if (mPreedit.getVisibility() == View.VISIBLE) {
                int[] plc = getLocationInWindow(mPreedit);
                outInsets.touchableRegion.union(new Rect(plc[0], plc[1], plc[0] + mPreedit.getWidth(), plc[1] + mPreedit.getHeight()));
            }
            View mCloud = mRootInputView.getCloud();
            if (mCloud.getVisibility() == View.VISIBLE) {
                int[] plc = getLocationInWindow(mCloud);
                outInsets.touchableRegion.union(new Rect(plc[0], plc[1], plc[0] + mPreedit.getWidth(), plc[1] + mPreedit.getHeight()));
            }
        } else {
            outInsets.contentTopInsets = lc[1];
            outInsets.visibleTopInsets = lc[1];
            outInsets.touchableRegion.set(0, lc[1], mRoot.getWidth(), lc[1] + mRoot.getHeight());
            View mPreedit = mRootInputView.getPreedit();
            if (mPreedit.getVisibility() == View.VISIBLE) {
                int[] plc = getLocationInWindow(mPreedit);
                outInsets.touchableRegion.union(new Rect(plc[0], plc[1], plc[0] + mPreedit.getWidth(), plc[1] + mPreedit.getHeight()));
            }
            View mCloud = mRootInputView.getCloud();
            if (mCloud.getVisibility() == View.VISIBLE) {
                int[] plc = getLocationInWindow(mCloud);
                outInsets.touchableRegion.union(new Rect(plc[0], plc[1], plc[0] + mPreedit.getWidth(), plc[1] + mPreedit.getHeight()));
            }
        }
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            // 核心代码：必须请求明确的监听模式
            // CURSOR_UPDATE_MONITOR: 持续监听光标和锚点位置变化
            // CURSOR_UPDATE_IMMEDIATE: 立即请求一次当前的位置
            ic.requestCursorUpdates(InputConnection.CURSOR_UPDATE_MONITOR);
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            // 退出时显式关闭监听，防止内存泄漏或状态错乱
            ic.requestCursorUpdates(0);
        }
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        // Function.printStackTrace("onStartInput");
        if (BuildConfig.DEBUG)
            android.util.Log.i(TAG, "onStartInput: " + attribute + ":" + restarting);
        super.onStartInput(attribute, restarting);
        mShowComposingText = ThemeManager.getStyle().getStyle("composition", ThemeManager.getStyle().getStyle("preedit")).getBoolean("show", true);
        inlinePreedit = ThemeManager.getInlinePreedit();
        // 获取 imeOptions 整数值
        int imeOptions = attribute.imeOptions;
        if ((imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            // 提取主要的回车动作ID
            // imeOptions & EditorInfo.IME_MASK_ACTION 会得到回车键的实际动作ID
            int actionId = imeOptions & EditorInfo.IME_MASK_ACTION;
            // 根据动作类型来更改你输入法界面的回车键显示
            if (!TextUtils.isEmpty(attribute.actionLabel)) {
                mActionLabel = attribute.actionLabel.toString();
            } else {
                switch (actionId) {
                    case EditorInfo.IME_ACTION_SEARCH:
                        // 将回车键显示为“搜索”图标或文字
                        mActionLabel = ThemeManager.getActionLabel("search", "搜索");
                        break;
                    case EditorInfo.IME_ACTION_SEND:
                        // 将回车键显示为“发送”
                        mActionLabel = ThemeManager.getActionLabel("send", "发送");
                        break;
                    case EditorInfo.IME_ACTION_NEXT:
                        // 将回车键显示为“发送”
                        mActionLabel = ThemeManager.getActionLabel("next", "下一个");
                        break;
                    case EditorInfo.IME_ACTION_PREVIOUS:
                        // 将回车键显示为“发送”
                        mActionLabel = ThemeManager.getActionLabel("previous", "上一个");
                        break;
                    case EditorInfo.IME_ACTION_GO:
                        // 将回车键显示为“发送”
                        mActionLabel = ThemeManager.getActionLabel("go", "前往");
                        break;
                    case EditorInfo.IME_ACTION_DONE:
                        // 将回车键显示为“发送”
                        mActionLabel = ThemeManager.getActionLabel("done", "完成");
                        break;
                    default:
                        // 默认回车键
                        mActionLabel = ThemeManager.getActionLabel("none", "Enter");
                        break;
                }
            }
        }
        canCompose = false;
        enterAsLineBreak = false;
        mTempAsciiMode = false;
        int inputType = attribute.inputType;
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        String keyboard = null;
        switch (inputClass) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_PHONE:
            case InputType.TYPE_CLASS_DATETIME:
                mTempAsciiMode = true;
                keyboard = "number";
                break;
            case InputType.TYPE_CLASS_TEXT:
                if (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                    // Make enter-key as line-breaks for messaging.
                    enterAsLineBreak = true;
                }
                if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                        || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                        || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
                    mTempAsciiMode = true;
                    keyboard = "ascii";
                    inputClass = 0;
                } else {
                    canCompose = true;
                    keyboard = Rime.getCurrentRimeSchema();//"default";
                }
                break;
            default: //0
                canCompose = (inputType > 0); //0x80000 FX重命名文本框
                //if (canCompose) break;
                //if (restarting)
                keyboard = Rime.getCurrentRimeSchema();
                break;
        }
        //Rime.get();
        if (reset_ascii_mode) mAsciiMode = false;
        // Select a keyboard based on the input type of the editing field.
        //mKeyboardSwitch.init(getMaxWidth()); //橫豎屏切換時重置鍵盤
        if (BuildConfig.DEBUG)
            android.util.Log.i(TAG, "onStartInput: " + keyboard);
        if (!TextUtils.isEmpty(keyboard) && !keyboard.equals(mLastInputClass))
            setKeyboard(keyboard);
        else
            updateRimeOption();
        mLastInputClass = keyboard;
        canCompose = canCompose && !Rime.getCurrentRimeSchema().isEmpty();
        //if (!onEvaluateInputViewShown()) setCandidatesViewShown(canCompose); //實體鍵盤進入文本框時顯示候選欄
        ThemeManager.callFunction("onStartInput", attribute, restarting);
    }

    public void sendEvent(String s) {
        onEvent(new Event(s));
    }

    public void sendEvent(LuaValue s) {
        onEvent(new Event(s));
    }

    // 5. 事件处理逻辑 (Event & Key Handling)
    public void onEvent(Event event) {
        if (BuildConfig.DEBUG) android.util.Log.w(TAG, "onEvent: " + event);
        if (BuildConfig.DEBUG) android.util.Log.w(TAG, "onEvent:1 " + event.getCode());
        if (BuildConfig.DEBUG) android.util.Log.w(TAG, "onEvent:2 " + event.getMask());
        String c = event.getCommit();
        if (BuildConfig.DEBUG) android.util.Log.w(TAG, "onEvent:3 " + c);
        if (!TextUtils.isEmpty(c)) {
            commitTextAndClearComposition(c);
            return;
        }

        String s = event.getText();
        if (BuildConfig.DEBUG) android.util.Log.w(TAG, "onEvent:4 " + s);
        if (!TextUtils.isEmpty(s)) {
            onText(s);
        } else if (event.getCode() > 0) {
            int code = event.getCode();
            switch (code) {
                case KeyEvent.KEYCODE_SWITCH_CHARSET:
                    commitText();
                    mRime.toggleRuntimeOption(event.getToggle());
                    break;
                case KeyEvent.KEYCODE_EISU:
                    setKeyboard(event.getSelect());
                    break;
                case KeyEvent.KEYCODE_LANGUAGE_SWITCH:
                    IBinder imeToken = getToken();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (event.getSelect().contentEquals(".next")) {
                        imm.switchToNextInputMethod(imeToken, false);
                    } else if (!TextUtils.isEmpty(event.getSelect())) {
                        imm.switchToLastInputMethod(imeToken);
                    } else {
                        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
                    }
                    break;
                case KeyEvent.KEYCODE_FUNCTION:
                    String cmd = event.getCommand();
                    String opt = event.getOption();
                    if (cmd.equals("filter")) {
                        if (opt.equals("char"))
                            CandidatesManager.toggleFilterChar();
                        else
                            CandidatesManager.filterStroke(opt, event.getLabel());
                        filterCandidate();
                    } else if (cmd.endsWith(".lua") && TextUtils.isEmpty(opt)) {
                        s = Function.handle(this, cmd, getActiveText(1), getActiveText(2), getActiveText(3), getActiveText(4));
                    } else if (cmd.equals("commit")) {
                        s = String.format(event.getOption(), getActiveText(1), getActiveText(2), getActiveText(3), getActiveText(4));
                    } else {
                        String arg = String.format(event.getOption(), getActiveText(1), getActiveText(2), getActiveText(3), getActiveText(4));
                        if ((cmd.equals("gpt") || cmd.equals("gpt2")) && (TextUtils.isEmpty(arg) || (event.getOption().contains("%") && event.getOption().equals(arg)))) {
                            Toast.makeText(this, "输入内容不能为空，请输入一些文字后重试", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Log.w(TAG, "onEvent: " + event.getOption());
                        Log.w(TAG, "onEvent: " + arg);

                        s = Function.handle(this, event.getCommand(), arg);
                    }
                    if (s != null) commitText(s);
                    break;
                case KeyEvent.KEYCODE_VOICE_ASSIST: //語音輸入
                    if (mSpeech != null)
                        mSpeech.start();
                    break;
                case KeyEvent.KEYCODE_SETTINGS:
                    switch (event.getOption()) {
                        case "theme":
                            if (!TextUtils.isEmpty(event.getSelect()))
                                setTheme(event.getSelect());
                            else
                                showThemeDialog();
                            break;
                        case "color":
                            if (!TextUtils.isEmpty(event.getSelect()))
                                setStyle(event.getSelect());
                            else
                                showColorDialog();
                            break;
                        case "schema":
                            if (!TextUtils.isEmpty(event.getSelect()))
                                mRime.selectSchema(event.getSelect());
                            else
                                showSchemaDialog();
                            break;
                        case "group":
                            if (!TextUtils.isEmpty(event.getSelect())) {
                                Config.setGroup(event.getSelect());
                                restart();
                            } else {
                                showSchemaGroupDialog();
                            }
                            break;
                        case "keyboard":
                            if (!TextUtils.isEmpty(event.getSelect())) {
                                Config.setKeyboard(event.getSelect());
                                setTheme(Config.getTheme());
                            } else {
                                showKeyboardDialog();
                            }
                            break;
                        default:
                            Function.showPrefDialog(this);
                            break;
                    }
                    break;
                case KeyEvent.KEYCODE_PROG_RED:
                    showColorDialog();
                    break;
                case KeyEvent.KEYCODE_MENU:
                    new OptionsDialog(this).show(getToken());
                    break;
                default:
                    onKey(event.getCode(), event.getMask());
                    break;
            }
        }
    }

    public void onUp(int keyCode) {
        if (BuildConfig.DEBUG)
            android.util.Log.i(TAG, "onUp: " + keyCode + ":" + mSpeech.getState());
        if (mSpeech != null) {
            if (mSpeech.getState() == Speech.STATE_BEGIN)
                mSpeech.stop();
            else if (mSpeech.getState() == Speech.STATE_READY || mSpeech.getState() == Speech.STATE_START)
                mSpeech.cancel();
        }
    }

    public void onKey(int keyCode, int mask) {
        if (BuildConfig.DEBUG) android.util.Log.w(TAG, "onKey: " + keyCode);
        if (handleKey(keyCode, mask)) return;
        if (keyCode >= Key.getSymbolStart()) {
            keyUpNeeded = false;
            commitText(Event.getDisplayLabel(keyCode));
            return;
        }
        keyUpNeeded = false;
        sendDownUpKeyEvents(keyCode, mask);
    }

    private boolean handleKey(int keyCode, int mask) {
        keyUpNeeded = false;
        //if(keyCode==KeyEvent.KEYCODE_DPAD_LEFT&&mRootInputView.prevCandidate())
        //    return true;
        //if(keyCode==KeyEvent.KEYCODE_DPAD_RIGHT&&mRootInputView.nextCandidate())
        //    return true;
        if (onRimeKey(Event.getRimeEvent(keyCode, mask))) {
            keyUpNeeded = true;
        } else if (handleAction(keyCode, mask) || handleOption(keyCode) || handleEnter(keyCode) || handleBack(keyCode)) {
            // Handled
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1 && Function.openCategory(this, keyCode)) {
            // Handled
        } else {
            keyUpNeeded = true;
            return false;
        }
        return true;
    }

    public void onText(CharSequence text) {
        String s = text.toString();
        if (!isAsciiPrintable(s.charAt(0))) {
            commitText();
        }
        if (s.length() == 1) {
            mRime.simulateKeySequence(s);
            return;
        }

        String t;
        Pattern p = Pattern.compile("^(\\{[^{}]+\\}).*$");
        Pattern pText = Pattern.compile("^((\\{Escape\\})?[^{}]+).*$");
        Matcher m;
        while (!s.isEmpty()) {
            m = pText.matcher(s);
            if (m.matches()) {
                t = m.group(1);
                if (!isAsciiPrintable(t.charAt(0))) {
                    commitText(t);
                } else {
                    mRime.simulateKeySequence(t);
                }
            } else {
                m = p.matcher(s);
                t = m.matches() ? m.group(1) : s.substring(0, 1);
                onEvent(new Event(t));
            }
            s = s.substring(t.length());
        }
        keyUpNeeded = false;
    }

    /**
     * 检查字符是否为 ASCII 可见字符 (码点在 32 到 126 之间)
     * 包含空格、数字、字母、标准标点符号。
     */
    public static boolean isAsciiPrintable(char ch) {
        return ch >= 32 && ch < 127;
    }

    // 6. 文本提交与 Rime 消息处理 (Text Commitment & Rime Logic)
    public void commitText(CharSequence text) {
        if (TextUtils.isEmpty(text)) return;
        lastCommittedText = text;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(text, 1);
        ThemeManager.callFunction("commitText", text);
    }

    public void commitTextAndClearComposition(CharSequence text) {
        commitText(text);
        mRime.clearComposition();
    }

    private boolean commitText() {
        if (isComposing()) {
            String text = mRime.getComposingText();
            if (!TextUtils.isEmpty(text)) {
                commitText(text);
            }
            mRime.clearComposition();
        }
        return false; // 原有逻辑返回 false
    }

    private boolean onRimeKey(int[] event) {
        if (event[0] == RimeKey_VoidSymbol)
            return false;
        boolean ret = mRime.processKey(event[0], event[1]);
        Log.w(TAG, "onRimeKey: " + ret);
        //commitText();
        return ret;
    }


    private boolean composeEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_MENU) return false; //不處理Menu鍵
        if (keyCode >= Key.getSymbolStart()) return false; //只處理安卓標準按鍵
        if (event.getRepeatCount() == 0 && KeyEvent.isModifierKey(keyCode)) {
            boolean ret =
                    onRimeKey(
                            Event.getRimeEvent(
                                    keyCode, event.getAction() == KeyEvent.ACTION_DOWN ? 0 : Rime.META_RELEASE_ON));
            if (isComposing()) setCandidatesViewShown(canCompose); //藍牙鍵盤打字時顯示候選欄
            return ret;
        }
        if (!canCompose || Rime.isVoidKeycode(keyCode)) return false;
        if (BuildConfig.DEBUG) android.util.Log.w(TAG, "onKeyDown:4 " + keyCode);
        return true;
    }

    public void onRelease(int keyCode) {
        if (BuildConfig.DEBUG) android.util.Log.i(TAG, "onRelease: " + keyCode);
        if (keyUpNeeded) {
            onRimeKey(Event.getRimeEvent(keyCode, Rime.META_RELEASE_ON));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //Log.info("onKeyDown=" + event);
        if (BuildConfig.DEBUG) android.util.Log.w(TAG, "onKeyDown: " + keyCode);
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            try {
                if (Rime.isComposing()) {
                    selectCandidate(Rime.getHighlightRimeCandidate());
                    return true;
                } else {
                    onKey(KeyEvent.KEYCODE_ENTER, 0);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (composeEvent(event) && onKeyEvent(event)) {
            if (BuildConfig.DEBUG) android.util.Log.w(TAG, "onKeyDown:2 " + keyCode);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        //Log.info("onKeyUp=" + event);
        if (composeEvent(event) && keyUpNeeded) {
            onRelease(keyCode);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * 處理實體鍵盤事件
     *
     * @param event {@link KeyEvent 按鍵事件}
     * @return 是否成功處理
     */
    private boolean onKeyEvent(KeyEvent event) {
        //Log.info("onKeyEvent=" + event);
        int keyCode = event.getKeyCode();

        boolean ret = true;
        keyUpNeeded = isComposing();

        if (!isComposing()) {
            if (keyCode == KeyEvent.KEYCODE_DEL
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_ESCAPE
                    || keyCode == KeyEvent.KEYCODE_BACK) {
                return false;
            }
        }/* else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mCandidatesViewShown || mInputViewShown)
                keyCode = KeyEvent.KEYCODE_ESCAPE; //返回鍵清屏
            else
                return false;
        }*/

        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.isCtrlPressed()
                && event.getRepeatCount() == 0
                && !KeyEvent.isModifierKey(keyCode)) {
            if (handleAction(keyCode, event.getMetaState())) return true;
        }

        int c = event.getUnicodeChar();
        String s = String.valueOf((char) c);
        int mask = 0;
        int i = Event.getClickCode(s);
        if (i > 0) {
            keyCode = i;
        } else { //空格、回車等
            mask = event.getMetaState();
        }
        ret = handleKey(keyCode, mask);
        if (BuildConfig.DEBUG) android.util.Log.w(TAG, "onKeyDown:3 " + keyCode + ret);
        if (isComposing()) setCandidatesViewShown(canCompose); //藍牙鍵盤打字時顯示候選欄
        return ret;
    }


    private int idx = 0;

    private void handleRimeMessage(RimeMessage<?> message) {
        Log.w("rime", "handleRimeMessage:1 " + message.getClass() + ":" + message.getData());
        if (message instanceof RimeMessage.CommitTextMessage) {
            commitText(((RimeMessage.CommitTextMessage) message).data.getText());
        } else if (message instanceof RimeMessage.SchemaMessage) {
            mRootInputView.setSchema(((RimeMessage.SchemaMessage) message).getData().getId());
        } else if (message instanceof RimeMessage.DeployMessage) {
            RimeMessage.DeployMessage msg = (RimeMessage.DeployMessage) message;
            if (msg.getData() == RimeMessage.DeployMessage.State.Success) {
                idx = 0;
                //setKeyboard(Rime.getCurrentRimeSchema());
                //Log.w(TAG, "handleRimeMessage:getCurrentRimeSchema " + Rime.getCurrentRimeSchema());
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (idx++ > 20)
                            return;
                        //Log.w(TAG, "handleRimeMessage:getCurrentRimeSchema " + Rime.getCurrentRimeSchema());
                        if (TextUtils.isEmpty(Rime.getCurrentRimeSchema())) {
                            mHandler.postDelayed(this, 10);
                        } else {
                            mRootInputView.setSchema(Rime.getCurrentRimeSchema());
                        }
                    }
                }, 10);
            }
        } else if (message instanceof RimeMessage.CompositionMessage) {
            updateComposing(((RimeMessage.CompositionMessage) message).getData());
        } else if (message instanceof RimeMessage.CandidateMenuMessage || message instanceof RimeMessage.CandidateListMessage) {
            updateCandidate();
        } else if (message instanceof RimeMessage.OptionMessage) {
            RimeMessage.OptionMessage msg = (RimeMessage.OptionMessage) message;
            if ("ascii_mode".equals(msg.getData().getOption())) {
                mRootInputView.setAsciiMode(msg.getData().isValue());
                updateRimeOption();
            } else if ("small_mode".equals(msg.getData().getOption())) {
                mRootInputView.setSmallMode(msg.getData().isValue());
            } else if ("float_mode".equals(msg.getData().getOption())) {
                mRootInputView.setFloatMode(msg.getData().isValue());
            } else {
                updateRimeOption();
            }
        } else if (message instanceof RimeMessage.StatusMessage) {
            RimeProto.Status status = ((RimeMessage.StatusMessage) message).getData();
            updateStatus(status);
        }
    }

    private boolean mComposing;
    // 1. 复用 Runnable，避免 GC 压力
    private final Runnable mStatusRunnable = new Runnable() {
        @Override
        public void run() {
            // 在执行时再次获取最新的状态，确保 UI 与数据同步
            boolean isComp = mComposing;
            showToolbarView(!isComp);
            mRootInputView.invalidateComposingKeys();
        }
    };

    private void updateStatus(RimeProto.Status status) {
        boolean isComposing = status.isComposing();

        // 2. 状态预判：如果状态没变，直接拦截，不往主线程 post 消息
        if (mComposing == isComposing) {
            return;
        }

        // 3. 更新状态变量（放在 post 之前）
        mComposing = isComposing;

        // 4. 防抖处理：撤回旧任务，确保队列里只有一个最新的状态切换任务
        mHandler.removeCallbacks(mStatusRunnable);

        // 5. 延迟/异步执行
        // 如果对实时性要求极高，用 post；如果怕连续抖动，用 postDelayed(mStatusRunnable, 10)
        mHandler.post(mStatusRunnable);
    }

    private void updateComposing(RimeProto.Context.Composition data) {
        if (mShowComposingText)
            setComposingText(data.getPreedit());

        if (inlinePreedit != InlineModeType.INLINE_NONE) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    String s = null;
                    switch (inlinePreedit) {
                        case INLINE_PREVIEW:
                            s = data.getCommitTextPreview();
                            break;
                        case INLINE_COMPOSITION:
                            s = data.getPreedit();
                            break;
                        case INLINE_INPUT:
                            s = Rime.getRimeRawInput();
                            break;
                    }
                    if (s == null) s = "";
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        CharSequence cs = ic.getSelectedText(0);
                        if (cs == null || !TextUtils.isEmpty(s)) ic.setComposingText(s, 1);
                    }
                }
            });
        }
    }


    public void updateComposing(String type) {
        String s = null;
        switch (type) {
            case "preview":
                s = mRime.getComposingText();
                break;
            case "composition":
                s = mRime.getCompositionCached().getPreedit();
                break;
            case "input":
                s = Rime.getRimeRawInput();
                break;
            default:
                return;
        }
        if (s == null) s = "";
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            CharSequence cs = ic.getSelectedText(0);
            if (cs == null || !TextUtils.isEmpty(s)) ic.setComposingText(s, 1);
        }
    }

    public void updateComposing() {
        if (inlinePreedit != InlineModeType.INLINE_NONE) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    String s = null;
                    switch (inlinePreedit) {
                        case INLINE_PREVIEW:
                            s = mRime.getComposingText();
                            break;
                        case INLINE_COMPOSITION:
                            s = mRime.getCompositionCached().getPreedit();
                            break;
                        case INLINE_INPUT:
                            s = Rime.getRimeRawInput();
                            break;
                    }
                    if (s == null) s = "";
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        CharSequence cs = ic.getSelectedText(0);
                        if (cs == null || !TextUtils.isEmpty(s)) ic.setComposingText(s, 1);
                    }
                }
            });
        }
    }


    @Override
    public void onUpdateCursorAnchorInfo(CursorAnchorInfo cursorAnchorInfo) {
        mRootInputView.onUpdateCursorAnchorInfo(cursorAnchorInfo);
    }


    @Override
    public void onUpdateSelection(
            int oldSelStart,
            int oldSelEnd,
            int newSelStart,
            int newSelEnd,
            int candidatesStart,
            int candidatesEnd) {
        super.onUpdateSelection(
                oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        if ((candidatesEnd != -1) && ((newSelStart != candidatesEnd) || (newSelEnd != candidatesEnd))) {
            //移動光標時，更新候選區
            if ((newSelEnd < candidatesEnd) && (newSelEnd >= candidatesStart)) {
                int n = newSelEnd - candidatesStart;
                mRime.setRimeCaretPos(n);
                updateComposing();
            }
        }
        if ((candidatesStart == -1 && candidatesEnd == -1) && (newSelStart == 0 && newSelEnd == 0)) {
            //上屏後，清除候選區
            escape();
        }
        mCurrSelEnd = newSelEnd;
        mCurrSelStart = newSelStart;
        previousIdx = 0;
        nextIdx = 0;
    }


    // 7. 视图状态管理与更新 (View State Management)

    public void setTheme(String theme) {
        if (Rime.isComposing()) {
            onKey(KeyEvent.KEYCODE_ESCAPE, 0);
            mRime.clearComposition();
        }
        ThemeManager.setTheme(theme);
        mRootInputView.setTheme(theme);
        //setInputView(onCreateInputView());
        //showToolbarView(true);
    }

    public void setStyle(String theme) {
        if (Rime.isComposing()) {
            onKey(KeyEvent.KEYCODE_ESCAPE, 0);
            mRime.clearComposition();
        }
        ThemeManager.setStyle(theme);
        mRootInputView.setStyle(theme);
        //setInputView(onCreateInputView());
    }

    public void showExtractedCandidatesView(boolean b) {
        mRootInputView.showExtractedCandidatesView(b);
        mShowExtractedCandidatesView = b;
        //updateCandidate();
    }

    public void showSymbolsView(boolean b) {
        mRootInputView.showSymbolsView(b);
    }

    public RootInputView getRootView() {
        return mRootInputView;
    }

    public InputView getInputView() {
        return mRootInputView.getInputView();
    }

    public View getKeyboardView() {
        return mRootInputView.getInputView().getKeyboardView();
    }

    public CandidateView getCandidateView() {
        return mRootInputView.getCandidateView();
    }

    public void setClipboardSize(int size) {
        mClipboardSize = size;
    }

    public void showCustomView(View keyboardView) {
        mRootInputView.showCustomView(keyboardView);
    }

    private void updateCandidate() {
        mRootInputView.updateCandidate();
    }

    private void filterCandidate() {
        mRootInputView.filterCandidate();
    }

    public void setComposingText(String s) {
        mRootInputView.setComposingText(s);
    }

    public void setCloudText(String s) {
        if (mRootInputView != null)
            mRootInputView.setCloudText(s);
    }

    public void setKeyboard(String id) {
        mRootInputView.setKeyboard(id);
    }

    public void setKeyboard(View id) {
        mRootInputView.showCustomView(id);
    }

    // 8. 输入法操作辅助 (Input Helpers)
    public void selectCandidate(int index) {
        mRime.selectCandidate(index);
    }

    public void selectPagedCandidate(int index) {
        mRime.selectPagedCandidate(index);
    }

    public void selectRimeSchema(String id) {
        mRime.selectSchema(id);
    }

    public void deploy() {
        mRime.deploy();
    }

    private boolean isComposing() {
        return Rime.isComposing();
    }

    public boolean isShifted() {
        return mRootInputView.isShifted();
    }

    public void setShifted(boolean shifted) {
        ModifierState.setShifted(shifted);
        mRootInputView.setShifted(shifted);
    }

    private void onPickCandidate(int index) {
    }

    // 1. 复用 Runnable，减少内存抖动
    private final Runnable mRimeOptionRunnable = new Runnable() {
        @Override
        public void run() {
            mRootInputView.invalidateAllKeys();
        }
    };

    private void updateRimeOption() {
        // 2. 移除旧任务（去重）
        mHandler.removeCallbacks(mRimeOptionRunnable);

        // 3. 延迟一小段时间执行（节流）
        // 10ms-16ms 是一个合理的区间，可以合并极短时间内的多次请求
        mHandler.postDelayed(mRimeOptionRunnable, 10);
    }

    // 9. 按键与编辑操作辅助 (Key & Edit Helpers)
    private boolean handleAction(int code, int mask) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        if (Event.hasModifier(mask, KeyEvent.META_CTRL_ON)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (code == KeyEvent.KEYCODE_V && Event.hasModifier(mask, KeyEvent.META_ALT_ON) && Event.hasModifier(mask, KeyEvent.META_SHIFT_ON))
                    return ic.performContextMenuAction(android.R.id.pasteAsPlainText);
                if (code == KeyEvent.KEYCODE_S && Event.hasModifier(mask, KeyEvent.META_ALT_ON)) {
                    if (ic.getSelectedText(0) == null)
                        ic.performContextMenuAction(android.R.id.selectAll);
                    return ic.performContextMenuAction(android.R.id.shareText);
                }
                if (code == KeyEvent.KEYCODE_Z) {
                    if (Event.hasModifier(mask, KeyEvent.META_SHIFT_ON))
                        return ic.performContextMenuAction(android.R.id.redo);

                    return ic.performContextMenuAction(android.R.id.undo);
                }
            }
            if (code == KeyEvent.KEYCODE_DEL && Event.hasModifier(mask, KeyEvent.META_SHIFT_ON)) {
                backToSentence();
                return true;
            }
            if (code == KeyEvent.KEYCODE_A)
                return ic.performContextMenuAction(android.R.id.selectAll);
            if (code == KeyEvent.KEYCODE_X) return ic.performContextMenuAction(android.R.id.cut);
            if (code == KeyEvent.KEYCODE_C) return ic.performContextMenuAction(android.R.id.copy);
            if (code == KeyEvent.KEYCODE_V) return ic.performContextMenuAction(android.R.id.paste);
        }
        return false;
    }

    public void backToSentence() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence text = ic.getTextBeforeCursor(1024, 0);
        if (TextUtils.isEmpty(text)) return;
        for (int i = text.length() - 1; i > 0; i--) {
            if (",.!?\n，。！？：:".indexOf(text.charAt(i)) != -1) {
                if (text.length() - i > 1) {
                    ic.deleteSurroundingText(text.length() - i - 1, 0);
                    return;
                }
            }
        }
        if (text.length() < 128) ic.deleteSurroundingText(text.length(), 0);
    }

    private void sendDownUpKeyEvents(int keyCode, int mask) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.clearMetaKeyStates(KeyEvent.META_FUNCTION_ON | KeyEvent.META_SHIFT_MASK | KeyEvent.META_ALT_MASK | KeyEvent.META_CTRL_MASK | KeyEvent.META_META_MASK | KeyEvent.META_SYM_ON);
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_EQUALS) {
            mask |= KeyEvent.META_NUM_LOCK_ON;
        }

        if (mRootInputView != null && mRootInputView.isShifted()) {
            if (keyCode == KeyEvent.KEYCODE_MOVE_HOME || keyCode == KeyEvent.KEYCODE_MOVE_END || keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_PAGE_DOWN || (keyCode >= KeyEvent.KEYCODE_DPAD_UP && keyCode <= KeyEvent.KEYCODE_DPAD_RIGHT))
                mask |= KeyEvent.META_SHIFT_ON;
        }
        if (Event.hasModifier(mask, KeyEvent.META_SHIFT_ON))
            sendKeyDown(ic, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
        if (Event.hasModifier(mask, KeyEvent.META_CTRL_ON))
            sendKeyDown(ic, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
        if (Event.hasModifier(mask, KeyEvent.META_ALT_ON))
            sendKeyDown(ic, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON);
        sendKeyDown(ic, keyCode, mask);
        sendKeyUp(ic, keyCode, mask);
        if (Event.hasModifier(mask, KeyEvent.META_ALT_ON))
            sendKeyUp(ic, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON);
        if (Event.hasModifier(mask, KeyEvent.META_CTRL_ON))
            sendKeyUp(ic, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
        if (Event.hasModifier(mask, KeyEvent.META_SHIFT_ON))
            sendKeyUp(ic, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
    }

    private void sendKeyDown(InputConnection ic, int key, int meta) {
        sendKey(ic, key, meta, KeyEvent.ACTION_DOWN);
    }

    private void sendKeyUp(InputConnection ic, int key, int meta) {
        sendKey(ic, key, meta, KeyEvent.ACTION_UP);
    }

    private void sendKey(InputConnection ic, int key, int meta, int action) {
        long now = System.currentTimeMillis();
        if (action == KeyEvent.ACTION_UP) now += 10;
        if (ic != null) ic.sendKeyEvent(new KeyEvent(now, now, action, key, 0, meta));
    }

    private boolean handleOption(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            new OptionsDialog(this).show(getToken());
            return true;
        }
        return false;
    }

    private boolean handleEnter(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (enterAsLineBreak) commitText("\n");
            else sendKeyChar('\n');
            return true;
        }
        return false;
    }

    private boolean handleBack(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            Function.printStackTrace("back");
            requestHideSelf(0);
            return true;
        }
        return false;
    }

    private void escape() {
        if (isComposing()) onKey(KeyEvent.KEYCODE_ESCAPE, 0);
    }

    // 10. 对话框与测量辅助 (Dialog & Dimension Helpers)
    public AlertDialog showDialog(AlertDialog dialog) {
        Window window = dialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Settings.canDrawOverlays(this))
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        else
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        lp.token = getToken();
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        dialog.show();
        window = dialog.getWindow();
        if (window != null) {
            lp = window.getAttributes();
            // 设置为屏幕宽度的 50%，避免撑满全屏
            DisplayMetrics dm = getResources().getDisplayMetrics();
            lp.width = (int) (dm.widthPixels * 0.5);
            window.setAttributes(lp);
        }
        return dialog;
    }

    public AlertDialog showWidthDialog(AlertDialog dialog) {
        Window window = dialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Settings.canDrawOverlays(this))
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        else
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        lp.token = getToken();
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        dialog.show();
        window = dialog.getWindow();
        if (window != null) {
            lp = window.getAttributes();
            // 设置为屏幕宽度的 80%，避免撑满全屏
            DisplayMetrics dm = getResources().getDisplayMetrics();
            window.setAttributes(lp);
        }
        return dialog;
    }

    public AlertDialog showListDialog(AlertDialog dialog) {
        Window window = dialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        IBinder token = PrefLauncher.getToken();
        if (token != null) {
            lp.token = token;
        } else {
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
            lp.token = getToken();
        }
        lp.gravity = Gravity.CENTER;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        try {
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dialog;
    }

    public void showSchemaGroupDialog() {
        new SchemaGroupDialog(this).show(getToken());
    }

    public void showSchemaDialog() {
        new OptionsDialog(this).show(getToken());
    }

    public void showColorDialog() {
        new StyleDialog(this).show(getToken());
    }

    public void showThemeDialog() {
        new ThemeDialog(this).show(getToken());
    }

    public void showKeyboardDialog() {
        new KeyboardDialog(this).show(getToken());
    }

    public IBinder getToken() {
        return mRootInputView.getRoot().getWindowToken();
    }

    public int getHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    public int getWidth() {
        if (Config.isSmallMode() || Config.isFloatMode())
            //if (Rime.getRimeOption("small_mode"))
            return Math.max(Math.min(Config.getSmallModeWidth(), (getMaxWidth())), (int) (getMaxWidth() * 0.2));
        return getMaxWidth();
    }

    public int[] getLocationInWindow(View view) {
        int[] ret = new int[2];
        view.getLocationInWindow(ret);
        return ret;
    }

    // 11. 文本获取辅助 (Text Extraction)
    private String getActiveText(int type) {
        if (type == 2) return Rime.getRimeRawInput();
        String s = mRime.getComposingText();
        if (TextUtils.isEmpty(s)) {
            InputConnection ic = getCurrentInputConnection();
            if (ic == null) return "";
            CharSequence cs = ic.getSelectedText(0);
            if (type == 1 && TextUtils.isEmpty(cs)) cs = lastCommittedText;
            if (TextUtils.isEmpty(cs)) cs = ic.getTextBeforeCursor(type == 4 ? 1024 : 1, 0);
            if (TextUtils.isEmpty(cs)) cs = ic.getTextAfterCursor(1024, 0);
            if (cs != null) s = cs.toString();
        }
        return s;
    }

    private String getAfterText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence cs = ic.getTextAfterCursor(10240, 0);
        return cs != null ? cs.toString() : "";
    }

    private String getBeforeText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence cs = ic.getTextBeforeCursor(10240, 0);
        return cs != null ? cs.toString() : "";
    }

    private String getBeforeChar() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence cs = ic.getTextBeforeCursor(1, 0);
        return cs != null ? cs.toString() : "";
    }

    public boolean isLongPressPopup() {
        return false;
    }

    public boolean isKeySwipeTap() {
        return false;
    }

    public String getActionLabel() {
        return mActionLabel;
    }

    public Object doFile(String path, String option) {
        if (globals == null) {
            globals = JsePlatform.standardGlobals();
            globals.finder = new ResourceFinder() {
                @Override
                public InputStream findResource(String filename) {
                    File f = new File(filename);
                    if (f.exists()) {
                        try {
                            return new FileInputStream(f);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                    f = new File(Config.getThemeDir(Config.getTheme()), filename);
                    if (f.exists()) {
                        try {
                            return new FileInputStream(f);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                    f = new File(Config.getScriptsDir(), filename);
                    if (f.exists()) {
                        try {
                            return new FileInputStream(f);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                    return null;
                }

                @Override
                public String findFile(String filename) {
                    File f = new File(filename);
                    if (f.exists()) {
                        return f.getAbsolutePath();
                    }
                    f = new File(Config.getScriptsDir(), filename);
                    if (f.exists()) {
                        return f.getAbsolutePath();
                    }
                    return filename;
                }
            };
        }
        LuaTable env = new LuaTable();
        env.setmetamethod("__index", globals);
        try {
            return globals.loadfile(path).jcall(option);
        } catch (Exception e) {
            sendMsg(e.toString());
        }
        return null;
    }

    public Object doFile(String path, Object... option) {
        if (globals == null) {
            globals = JsePlatform.standardGlobals();
            globals.finder = new ResourceFinder() {
                @Override
                public InputStream findResource(String filename) {
                    File f = new File(filename);
                    if (f.exists()) {
                        try {
                            return new FileInputStream(f);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                    f = new File(Config.getScriptsDir(), filename);
                    if (f.exists()) {
                        try {
                            return new FileInputStream(f);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                    return null;
                }

                @Override
                public String findFile(String filename) {
                    File f = new File(filename);
                    if (f.exists()) {
                        return f.getAbsolutePath();
                    }
                    f = new File(Config.getScriptsDir(), filename);
                    if (f.exists()) {
                        return f.getAbsolutePath();
                    }
                    return filename;
                }
            };
        }
        LuaTable env = new LuaTable();
        env.setmetamethod("__index", globals);
        try {
            return globals.loadfile(path).jcall(option);
        } catch (Exception e) {
            sendMsg(e.toString());
        }
        return null;
    }


    private void registerClipEvents() {
        loadClipboard();
        loadPhrase();
        manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager == null)
            return;
        mOnPrimaryClipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {
            private long lastProcessTime = 0L;    // 记录上一次处理的时间戳

            @Override
            public void onPrimaryClipChanged() {
                long currentTime = SystemClock.uptimeMillis();
                if (currentTime - lastProcessTime < 100) {
                    return;
                }
                try {
                    // 1. 先通过 Description 快速判断是否有文本内容，这比直接获取内容开销更小
                    if (manager.hasPrimaryClip()) {
                        ClipData clip = manager.getPrimaryClip(); // 只读取一次，缓存到局部变量

                        if (clip != null && clip.getItemCount() > 0) {
                            ClipData.Item item = clip.getItemAt(0);
                            CharSequence addedText = item.getText();

                            if (!TextUtils.isEmpty(addedText)) {
                                String text = addedText.toString();
                                addClipboard(text);
                                lastProcessTime = currentTime;
                            }
                        }
                    }
                } catch (SecurityException e) {
                    // 处理 Android 10+ 后台读取剪切板权限限制
                    Log.e("Clipboard", "No permission to access clipboard: " + e.getMessage());
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        };
        manager.addPrimaryClipChangedListener(mOnPrimaryClipChangedListener);
    }

    private void unregisterClipEvents() {
        if (manager == null)
            return;
        manager.removePrimaryClipChangedListener(mOnPrimaryClipChangedListener);
    }


    public void loadPhrase() {
        mPhrase = JsonUtil.load(new File(Config.getDataDir(), "phrase.json"));
    }

    public void addPhrase(String text) {
        if (mPhrase.contains(text))
            mPhrase.remove(text);
        mPhrase.add(0, text);
        for (int size = mPhrase.size() - 1; size >= 120; size--) {
            mPhrase.remove(size);
        }
        JsonUtil.save(new File(Config.getDataDir(), "phrase.json"), mPhrase);
    }

    public void removePhrase(int i) {
        mPhrase.remove(i);
        JsonUtil.save(new File(Config.getDataDir(), "phrase.json"), mPhrase);
    }

    public List<String> getPhrase() {
        return mPhrase;
    }

    private void loadClipboard() {
        mClipboard = JsonUtil.load(new File(Config.getDataDir(), "clipboard.json"));
    }

    public void addClipboard(String text) {
        if (mClipboard.contains(text))
            mClipboard.remove(text);
        mClipboard.add(0, text);
        for (int size = mClipboard.size() - 1; size >= mClipboardSize; size--) {
            mClipboard.remove(size);
        }
        JsonUtil.save(new File(Config.getDataDir(), "clipboard.json"), mClipboard);
    }

    public void removeClipboard(int i) {
        mClipboard.remove(i);
        JsonUtil.save(new File(Config.getDataDir(), "clipboard.json"), mClipboard);
    }

    public List<String> getClipboard() {
        return mClipboard;
    }

    public void showClipboardView(boolean b) {
        mRootInputView.showClipboardView(b);
    }

    private void showToolbarView(boolean b) {
        mRootInputView.showToolbarView(b);
    }

    public void restart() {
        mRime.restart();
    }

    private AlertDialog mDlg;

    public void sendMsg(final String text) {
        //Function.printStackTrace("sendMsg " + text);
        LuaActivity.logs.add(text);
        Log.w(TAG, "sendMsg: " + text);
        //sendMsgAux(text);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void sendMsgAux(final String text) {
        Log.w(TAG, "sendMsgAux: " + text);
        //if(!isInputViewShown()&&PrefLauncher.getToken()==null){
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        //return;
        //}
       /*if (mDlg == null) {
            mDlg=showListDialog(new AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setAdapter( new ArrayListAdapter<>(this,new String[]{text}),null)
                    .setNegativeButton("取消",null)
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            mDlg=null;
                        }
                    })
                    .create());
        } else {
            ArrayListAdapter<String> adapter = (ArrayListAdapter<String>) mDlg.getListView().getAdapter();
            adapter.add(text);
        }*/
    }

    public LuaDialog createDialog(String title, String[] items) {
        LuaDialog dlg = new LuaDialog(this);
        dlg.setTitle(title);
        dlg.setItems(items);
        dlg.setNegativeButton(getString(R.string.cancel), null);
        return dlg;
    }

    public void setCandidates(ArrayList<String> list) {
        if (list == null) {
            list = new ArrayList<>();
        }
        ArrayList<CandidateItem> items = new ArrayList<CandidateItem>(list.size());
        for (String s : list) {
            items.add(new CandidateItem(s));
        }
        mRootInputView.setCandidates(items);
        mComposing = !items.isEmpty();
    }

    public void addCompositions(ArrayList<String> list) {
        mRootInputView.addCompositions(list);
    }

    public void setComposition(String list) {
        mRootInputView.setComposition(list);
    }

    public void setCompositionFromHtml(String list) {
        mRootInputView.setComposition(Html.fromHtml(list));
    }

    public void addCloud(String s) {

    }

    public void addCloud(String index, String comment) {

    }

    public Rime getRime() {
        return mRime;
    }


    public Handler getHandler() {
        return mHandler;
    }
}
