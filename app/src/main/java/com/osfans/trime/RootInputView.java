/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.osfans.trime.candidate.CandidateView;
import com.osfans.trime.candidate.CandidatesManager;
import com.osfans.trime.candidate.ExpandedCandidateView;
import com.osfans.trime.core.CandidateItem;
import com.osfans.trime.core.Rime;
import com.osfans.trime.keyboard.ClipboardKeyboardView;
import com.osfans.trime.keyboard.FloatKeyboard;
import com.osfans.trime.keyboard.KeyView;
import com.osfans.trime.keyboard.SymbolsKeyboardView;
import com.osfans.trime.theme.Style;
import com.osfans.trime.theme.ThemeManager;
import com.osfans.trime.util.Function;

import java.util.ArrayList;

public class RootInputView extends FrameLayout {

    // 2. 成员变量 - UI 根布局与框架
    private LinearLayout mRoot;
    private LinearLayout mInputViewRoot;
    private FrameLayout mLeftLayout;
    private FrameLayout mRightLayout;
    private KeyView mLeftButton;
    private KeyView mRightButton;

    // 成员变量 - 核心组件视图
    private CandidateView mCandidateView;
    private InputView mInputView;
    private ExpandedCandidateView mExpandedCandidateView;
    private SymbolsKeyboardView mSymbolsKeyboardView;
    private Composition mPreedit;
    private TextView mCloud;
    private FrameLayout mCenterLayout;
    private View mCustomView;
    private boolean mShowExtractedCandidatesView;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ClipboardKeyboardView mClipboardKeyboardView;
    private int mStartIdx = 0;
    private int mCompositionMinLength;

    public RootInputView(@NonNull Context context) {
        super(context);
        initView(context);
        //setFitsSystemWindows(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } catch (Exception e) {
            e.printStackTrace();
            //setMeasuredDimension(TrimeService.getInstance().getWidth(),TrimeService.getInstance().getHeight());
        }
    }

    @SuppressLint({"ClickableViewAccessibility", "AppCompatCustomView"})
    private void initView(@NonNull Context context) {
        setClipChildren(false);
        setClipToPadding(false);
        mHasComposition = ThemeManager.getStyle().hasKey("composition");
        mCompositionMinLength = ThemeManager.getStyle().getStyle("composition").getInt("min_length");

        mShowExtractedCandidatesView = false;
        mExpandedCandidateView = null;
        mClipboardKeyboardView = null;
        mCustomView = null;
        mSymbolsKeyboardView = null;
        TrimeService trime = TrimeService.getInstance();

        mRoot = new LinearLayout(context);

        mRoot.setClipChildren(false);
        mRoot.setClipToPadding(false);
        mRoot.setOrientation(LinearLayout.HORIZONTAL);
        if (Config.isSmallMode()) {
            mLeftLayout = new FrameLayout(context);
            mLeftLayout.setVisibility(View.GONE);
            mRightLayout = new FrameLayout(context);
            mRightLayout.setVisibility(View.GONE);

            mLeftButton = new KeyView(context, ThemeManager.getStyle().getStyle("toolbar").getKeyStyle());
            mLeftButton.setText("◀");
            mLeftButton.setContentDescription("显示在左侧");
            mLeftLayout.addView(mLeftButton, new FrameLayout.LayoutParams(ThemeManager.getCandidateHeight(), ThemeManager.getCandidateHeight(), Gravity.CENTER | Gravity.RIGHT));
            mLeftButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mLeftLayout.setVisibility(GONE);
                    mRightLayout.setVisibility(VISIBLE);
                    Config.setSmallModeGravity(Gravity.LEFT);
                }
            });
            mLeftButton.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    setSelected(true);
                    return true;
                }
            });
            mLeftButton.setOnTouchListener(new OnTouchListener() {
                private float mWidth;
                private int mLastW;
                private float mLastX;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            mLastX = event.getRawX();
                            mLastW = mCenterLayout.getWidth();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (isSelected()) {
                                float x = event.getRawX();
                                mWidth = (mLastW - (x - mLastX));
                                mCenterLayout.setScaleX(mWidth / mLastW);
                                mCenterLayout.setTranslationX(-(mWidth - mLastW) / 2);
                                mLeftButton.setTranslationX(-(mWidth - mLastW));
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (isSelected()) {
                                Config.setSmallModeWidth((int) mWidth);
                                setSelected(false);
                                setTheme(Config.getTheme());
                            }
                            break;
                    }
                    return false;
                }
            });

            mRightButton = new KeyView(context, ThemeManager.getStyle().getStyle("toolbar").getKeyStyle());
            mRightButton.setText("▶");
            mRightButton.setContentDescription("显示在右侧");
            mRightLayout.addView(mRightButton, new FrameLayout.LayoutParams(ThemeManager.getCandidateHeight(), ThemeManager.getCandidateHeight(), Gravity.CENTER | Gravity.LEFT));
            mRightButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mRightLayout.setVisibility(GONE);
                    mLeftLayout.setVisibility(VISIBLE);
                    Config.setSmallModeGravity(Gravity.RIGHT);
                }
            });

            mRightButton.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    setSelected(true);
                    return true;
                }
            });
            mRightButton.setOnTouchListener(new OnTouchListener() {
                private float mWidth;
                private int mLastW;
                private float mLastX;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            mLastX = event.getRawX();
                            mLastW = mCenterLayout.getWidth();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (isSelected()) {
                                float x = event.getRawX();
                                mWidth = (mLastW + (x - mLastX));
                                mCenterLayout.setScaleX(mWidth / mLastW);
                                mCenterLayout.setTranslationX((mWidth - mLastW) / 2);
                                mRightButton.setTranslationX((mWidth - mLastW));
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (isSelected()) {
                                Config.setSmallModeWidth((int) mWidth);
                                setSelected(false);
                                setTheme(Config.getTheme());
                            }
                            break;
                    }
                    return false;
                }
            });
        }


        mCenterLayout = new FrameLayout(context);
        mCenterLayout.setClipChildren(false);
        mCenterLayout.setClipToPadding(false);
        if (Config.isSmallMode())
            mRoot.addView(mLeftLayout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ThemeManager.getContentHeight(), 1));
        mRoot.addView(mCenterLayout, new LinearLayout.LayoutParams(trime.getWidth(), ThemeManager.getContentHeight()));
        if (Config.isSmallMode())
            mRoot.addView(mRightLayout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ThemeManager.getContentHeight(), 1));

        mInputViewRoot = new LinearLayout(context);
        mInputViewRoot.setClipChildren(false);
        mInputViewRoot.setClipToPadding(false);
        mInputViewRoot.setOrientation(LinearLayout.VERTICAL);
        mCenterLayout.addView(mInputViewRoot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mCandidateView = new CandidateView(context);
        mInputView = new InputView(context);
        mInputViewRoot.addView(mCandidateView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ThemeManager.getCandidateHeight()));
        mInputViewRoot.addView(mInputView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ThemeManager.getKeyboardHeight()));
        Style color = ThemeManager.getStyle();
        mRoot.setBackground(color.getBackground(0xffdddddd));

        mPreedit = new Composition(context) {
            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                if (isSelected())
                    return;
                if (Config.isFloatMode()) {
                    float targetY = mRoot.getY() - mPreedit.getHeight();
                    mPreedit.setTranslationY(targetY);
                    mPreedit.setTranslationX(mRoot.getX());
                } else {
                    float targetY = mRoot.getTop() - mPreedit.getHeight();
                    mPreedit.setTranslationY(targetY);
                    mPreedit.setTranslationX(mCenterLayout.getX());
                }
            }
        };
        Style preeditColor = ThemeManager.getStyle().getStyle("preedit");
        mPreedit.setTextColor(preeditColor.getTextColor(0xffaaaaaa));
        mPreedit.setBackground(preeditColor.getBackground(0xff888888));
        mPreedit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, preeditColor.getTextSize(18));
        int pd = ThemeManager.dp2px(4);
        mPreedit.setPadding(pd, pd, pd, pd);
        mPreedit.setVisibility(View.INVISIBLE);
        //mPreedit.setText(" ");

        mCloud = new TextView(context);
        mCloud.setTextColor(preeditColor.getTextColor(0xffaaaaaa));
        mCloud.setBackground(preeditColor.getBackground(0xff888888));
        mCloud.setTextSize(TypedValue.COMPLEX_UNIT_DIP, preeditColor.getTextSize(18));
        mCloud.setPadding(pd, pd, pd, pd);
        mCloud.setVisibility(View.INVISIBLE);
        mCloud.setText(" ");
        addView(mRoot, new FrameLayout.LayoutParams(Config.isFloatMode() ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT, ThemeManager.getHeight(), Gravity.BOTTOM));
        addView(mPreedit, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        addView(mCloud, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT));
        showToolbarView(true);

        mRoot.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                // 将系统栏占用的空间转化为 ListView 的 Padding
                /*v.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom()
                );*/
                if (!Config.isFloatMode() && mRoot != null) {
                    ViewGroup.LayoutParams lp = mRoot.getLayoutParams();
                    lp.height = ThemeManager.getHeight() + insets.getSystemWindowInsetBottom();
                    mRoot.setLayoutParams(lp);
                }
                if (insets.getSystemWindowInsetBottom() > 1) {
                    Window win = trime.getWindow().getWindow();
                    if (win != null) {
                        //win.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                        //win.setDecorFitsSystemWindows(false);

                        win.setNavigationBarColor(ThemeManager.getStyle().getBackgroundColor(0));
                    }
                } else {
                    Window win = trime.getWindow().getWindow();
                    if (win != null) {
                        //win.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                        //win.setDecorFitsSystemWindows(false);
                        win.setNavigationBarColor(ThemeManager.getStyle().getBackgroundColor(0));
                    }
                }
                return insets.consumeSystemWindowInsets();
            }
        });
        mRoot.requestApplyInsets();
        Window win = trime.getWindow().getWindow();
        if (win != null) {
            //win.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            //win.setDecorFitsSystemWindows(false);
            win.setNavigationBarColor(ThemeManager.getStyle().getBackgroundColor(0));
        }
        if (Config.isSmallMode()) {
            if (Config.getSmallModeGravity() == Gravity.LEFT) {
                mLeftLayout.setVisibility(GONE);
                mRightLayout.setVisibility(VISIBLE);
            } else {
                mLeftLayout.setVisibility(VISIBLE);
                mRightLayout.setVisibility(GONE);
            }
        } else {
            if (mLeftLayout != null)
                mLeftLayout.setVisibility(GONE);
            if (mRightLayout != null)
                mRightLayout.setVisibility(GONE);
        }
        if (Config.isFloatMode()) {
            KeyView hide = mCandidateView.getToolbar().getHide();
            hide.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    setSelected(true);
                    if (!Config.isFloatMode()) {
                        mPreedit.setVisibility(View.VISIBLE);
                        mPreedit.setText("默认高度");
                        mPreedit.setY(getHeight() - ThemeManager.getRawContentHeight() - mPreedit.getHeight());
                    }
                    return true;
                }
            });
            hide.setOnTouchListener(new OnTouchListener() {
                private float mWidth;
                private int mLastW;
                private int mLastH;
                private float mX;
                private float mY;
                private float mLastX;
                private float mLastY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            mLastX = event.getRawX();
                            mLastY = event.getRawY();
                            mLastH = mInputView.getHeight();
                            mLastW = mCenterLayout.getWidth();
                            mX = mRoot.getTranslationX();
                            mY = mRoot.getTranslationY();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (isSelected()) {
                                if (Config.isSmallMode()) {
                                    float y = event.getRawY();
                                    float mHeight = (mLastH + (mLastY - y));
                                    mInputView.setScaleY(mHeight / mLastH);
                                    mInputView.setTranslationY((mLastH - mHeight) / 2);
                                    mCandidateView.setTranslationY((mLastH - mHeight));
                                    float x = event.getRawX();
                                    mWidth = (mLastW + (x - mLastX));
                                    mCenterLayout.setScaleX(mWidth / mLastW);
                                    mCenterLayout.setTranslationX((mWidth - mLastW) / 2);
                                    mRightButton.setTranslationX((mWidth - mLastW));
                                } else {
                                    mRoot.setTranslationX(mX + event.getRawX() - mLastX);
                                    mRoot.setTranslationY(mY + event.getRawY() - mLastY);
                                }
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (isSelected()) {
                                if (Config.isSmallMode()) {
                                    Config.setKeyboardHeightScale(mInputView.getScaleY());
                                    Config.setSmallModeWidth((int) mWidth);
                                } else {
                                    Config.setFloatModeX((int) mRoot.getTranslationX());
                                    Config.setFloatModeY((int) mRoot.getTranslationY());
                                }
                                setSelected(false);
                                setTheme(Config.getTheme());
                            }
                            break;
                    }
                    return false;
                }
            });
            mRoot.setX(Config.getFloatModeX());
            if (mRoot.getX() < 0) {
                mRoot.setX(0);
            }
            if (mRoot.getX() + trime.getWidth() > trime.getMaxWidth()) {
                mRoot.setX(trime.getMaxWidth() - trime.getWidth());
            }

            mRoot.setY(Config.getFloatModeY());
            if (mRoot.getY() < ThemeManager.getHeight() - trime.getHeight() + ThemeManager.getCandidateHeight()) {
                mRoot.setY(ThemeManager.getHeight() - trime.getHeight() + ThemeManager.getCandidateHeight());
            }
            if (mRoot.getY() > 0) {
                mRoot.setY(0);
            }
        } else {
            KeyView hide = mCandidateView.getToolbar().getHide();
            hide.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    setSelected(true);
                    if (!Config.isFloatMode()) {
                        mPreedit.setVisibility(View.VISIBLE);
                        mPreedit.setText("默认高度");
                        mPreedit.setY(getHeight() - ThemeManager.getRawContentHeight() - mPreedit.getHeight());
                    }
                    return true;
                }
            });
            hide.setOnTouchListener(new OnTouchListener() {
                private float mLastX;
                private float mWidth;
                private int mLastW;
                private int mLastH;
                private float mLastY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            mLastX = event.getRawX();
                            mLastY = event.getRawY();
                            mLastH = mInputView.getHeight();
                            mLastW = mCenterLayout.getWidth();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (isSelected()) {
                                float y = event.getRawY();
                                float mHeight = (mLastH + (mLastY - y));
                                mInputView.setScaleY(mHeight / mLastH);
                                mInputView.setTranslationY((mLastH - mHeight) / 2);
                                mCandidateView.setTranslationY((mLastH - mHeight));
                                if (Config.isSmallMode()) {
                                    float x = event.getRawX();
                                    mWidth = (mLastW + (x - mLastX));
                                    mCenterLayout.setScaleX(mWidth / mLastW);
                                    mCenterLayout.setTranslationX((mWidth - mLastW) / 2);
                                    mRightButton.setTranslationX((mWidth - mLastW));
                                }
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (isSelected()) {
                                if (Config.isSmallMode()) {
                                    Config.setSmallModeWidth((int) mWidth);
                                }
                                Config.setKeyboardHeightScale(mInputView.getScaleY());
                                setSelected(false);
                                setTheme(Config.getTheme());
                            }
                            break;
                    }
                    return false;
                }
            });
        }

        if(Rime.getRimeOption("_hide_candidate"))
            mCandidateView.setVisibility(GONE);
        else
            mCandidateView.setVisibility(VISIBLE);
        Config.set_hide_comment(Rime.getRimeOption("_hide_comment"));
        Config.set_hide_key_hint(Rime.getRimeOption("_hide_key_hint"));

    }

    public View getRoot() {
        return mRoot;
    }

    public View getPreedit() {
        return mPreedit;
    }

    public TextView getCloud() {
        return mCloud;
    }

    public void invalidateComposingKeys() {
        if (mInputView != null)
            mInputView.invalidateComposingKeys();
    }

    public void initView() {
        removeAllViews();
        initView(getContext());
    }

    public void showCustomView(View keyboardView) {
        if (mCustomView != null) {
            mCenterLayout.removeView(mCustomView);
        }
        mCustomView = keyboardView;
        if (keyboardView == null) {
            mInputViewRoot.setVisibility(View.VISIBLE);
            return;
        }
        mCenterLayout.addView(mCustomView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mInputViewRoot.setVisibility(View.GONE);
    }

    public void showExtractedCandidatesView(boolean b) {
        showCustomView(null);
        if (mExpandedCandidateView == null) {
            if (!b)
                return;
            mExpandedCandidateView = new ExpandedCandidateView(getContext());
            mExpandedCandidateView.setVisibility(View.GONE);
            mCenterLayout.addView(mExpandedCandidateView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        mExpandedCandidateView.setVisibility(b ? View.VISIBLE : View.GONE);
        mInputViewRoot.setVisibility(!b ? View.VISIBLE : View.GONE);
        mShowExtractedCandidatesView = b;
        if (b) {
            mExpandedCandidateView.setData(mCandidateView.getData());
            mExpandedCandidateView.setIdx(mCandidateView.getIdx());
        } else {
            if (mExpandedCandidateView != null) {
                mCandidateView.setData(mExpandedCandidateView.getData());
                mCandidateView.setIdx(mExpandedCandidateView.getIdx());
            }
        }
    }

    public void showSymbolsView(boolean b) {
        showCustomView(null);
        if (mSymbolsKeyboardView == null) {
            if (!b)
                return;
            mSymbolsKeyboardView = new SymbolsKeyboardView(getContext());
            mSymbolsKeyboardView.setVisibility(View.GONE);
            mCenterLayout.addView(mSymbolsKeyboardView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        mInputViewRoot.setVisibility(!b ? View.VISIBLE : View.GONE);
        mSymbolsKeyboardView.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    private final Runnable mUpdateCandidateRunnable = () -> {
        if (mShowExtractedCandidatesView) mExpandedCandidateView.show();
        else mCandidateView.show(mStartIdx);
    };

    public void updateCandidate() {
        // 必须有延迟（如 10-20ms），才能在 Handler 队列中起到去重效果
        mHandler.removeCallbacks(mUpdateCandidateRunnable);
        mHandler.postDelayed(mUpdateCandidateRunnable, 10);
    }

    private final Runnable mFilterCandidateRunnable = () -> {
        if (mShowExtractedCandidatesView) mExpandedCandidateView.update();
        else mCandidateView.update();
    };

    public void filterCandidate() {
        // 必须有延迟（如 10-20ms），才能在 Handler 队列中起到去重效果
        mHandler.removeCallbacks(mFilterCandidateRunnable);
        mHandler.postDelayed(mFilterCandidateRunnable, 10);
    }

    // 1. 定义一个成员变量保存上一次的内容，用于比对去重
    private String mLastComposingText = "";

    private boolean mHasComposition;
    // 2. 复用 Runnable，避免频繁 GC 产生内存抖动
    private final Runnable mComposingRunnable = new Runnable() {
        @Override
        public void run() {
            String s = mLastComposingText;

            if (TextUtils.isEmpty(s)) {
                mPreedit.setVisibility(View.INVISIBLE);
            } else {
                mPreedit.setVisibility(View.VISIBLE);

                // 核心优化：使用 setTranslationY 代替 LayoutParams
                // 这只会触发重绘（Repaint），不会触发重布局（Relayout），性能提升巨大
                 /*if(Config.isFloatMode()) {
                    float targetY = mRoot.getY() - mPreedit.getHeight();
                    mPreedit.setTranslationY(targetY);
                    mPreedit.setTranslationX(mRoot.getX());
                } else {
                     float targetY = mRoot.getTop() - mPreedit.getHeight();
                     mPreedit.setTranslationY(targetY);
                     mPreedit.setTranslationX(mCenterLayout.getX());
                 }*/
            }
            mStartIdx = 0;
            if (mHasComposition)
                mStartIdx = mPreedit.setWindow(mCompositionMinLength);
            else
                mPreedit.setText(s);
        }
    };

    public void setComposingText(String s) {
        // 3. 在非 UI 线程预判：如果内容没变，直接拦截，不向主线程发消息
        if (s == null) s = "";
        //if (s.equals(mLastComposingText)) return;

        mLastComposingText = s;

        // 4. 防抖处理：移除旧任务，确保主线程只处理最后一次更新
        mHandler.removeCallbacks(mComposingRunnable);
        mHandler.post(mComposingRunnable);
    }

    // 1. 成员变量复用，减少 GC 压力
    private String mLastCloudText = "";

    private final Runnable mCloudRunnable = new Runnable() {
        @Override
        public void run() {
            // 这里的 s 直接取最新的 mLastCloudText
            String s = mLastCloudText;
            mCloud.setText(s);

            if (TextUtils.isEmpty(s)) {
                mCloud.setVisibility(View.INVISIBLE);
            } else {
                mCloud.setVisibility(View.VISIBLE);

                // 2. 性能核心：使用 TranslationY 避开 RequestLayout
                // 这样只会触发重绘（Invalidate），不会导致主线程计算整棵布局树
                if (Config.isFloatMode()) {
                    float targetY = mRoot.getTop() - mRoot.getY() - mCloud.getHeight();
                    mCloud.setTranslationY(targetY);
                    mCloud.setTranslationX(-(TrimeService.getInstance().getMaxWidth() - mRoot.getX() - mRoot.getWidth()));
                } else {
                    float targetY = mRoot.getTop() - mCloud.getHeight();
                    mCloud.setTranslationY(targetY);
                    mCloud.setTranslationX(-(TrimeService.getInstance().getMaxWidth() - mCenterLayout.getX() - mCenterLayout.getWidth()));
                }
            }
        }
    };

    public void setCloudText(String s) {
        // 3. 内容一致性拦截
        if (s == null) s = "";
        if (s.equals(mLastCloudText)) return;

        mLastCloudText = s;

        // 4. 防抖：撤回尚未执行的任务，确保消息队列始终只有一个最新任务
        mHandler.removeCallbacks(mCloudRunnable);
        mHandler.post(mCloudRunnable);
    }

    public void setKeyboard(String id) {
        mHandler.post(() -> {
            if (mCustomView != null) {
                mCenterLayout.removeView(mCustomView);
                mCustomView = null;
            }
            switch (id) {
                //case "symbols_ext":
                //    showSymbolsView(true);
                //    return;
                case "candidate":
                    showExtractedCandidatesView(true);
                    return;
                case "clipboard":
                    showClipboardView(true);
                    return;
                case "phrase":
                    showClipboardView(true);
                    mClipboardKeyboardView.showPhrase();
                    return;
                default:
                    showSymbolsView(false);
                    mInputView.setKeyboard(id);
                    return;
            }
        });
    }

    public boolean isShifted() {
        return mInputView.isShifted();
    }

    public void setShifted(boolean shifted) {
        mInputView.setShifted(shifted);
    }

    public void invalidateAllKeys() {
         Config.set_hide_comment(Rime.getRimeOption("_hide_comment"));
        Config.set_hide_key_hint(Rime.getRimeOption("_hide_key_hint"));
        if (mInputView != null) mInputView.invalidateAllKeys();
        if (mCandidateView != null){
            if(Rime.getRimeOption("_hide_candidate"))
                mCandidateView.setVisibility(GONE);
            else
                mCandidateView.setVisibility(VISIBLE);
            mCandidateView.invalidateAllKeys();
            mRoot.requestApplyInsets();
        }
    }

    public void showClipboardView(boolean b) {
        showCustomView(null);
        if (mClipboardKeyboardView == null) {
            if (!b)
                return;
            mClipboardKeyboardView = new ClipboardKeyboardView(getContext());
            mClipboardKeyboardView.setVisibility(View.GONE);
            mCenterLayout.addView(mClipboardKeyboardView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        mClipboardKeyboardView.setVisibility(b ? View.VISIBLE : View.GONE);
        mInputViewRoot.setVisibility(!b ? View.VISIBLE : View.GONE);
        if (!b)
            return;
        mClipboardKeyboardView.show();

    }

    public void showToolbarView(boolean b) {
        mCandidateView.showToolbarView(b);
    }

    public void setAsciiMode(boolean asciiMode) {
        mInputView.setAsciiMode(asciiMode);
    }

    public void setSchema(String id) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Function.saveString(getContext(), "select_schema_id", id);
                String soft_cursor_key = "soft_cursor";
                Rime.setRimeOption(soft_cursor_key, true); //軟光標
                setTheme(Config.getTheme());
                //mInputView.setKeyboard(id);
                //mCandidateView.setSchema(id);
            }
        });
    }

    public boolean prevCandidate() {
        return mCandidateView.prevCandidate();
    }

    public boolean nextCandidate() {
        return mCandidateView.nextCandidate();
    }

    public void setCandidates(final ArrayList<CandidateItem> items) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mCandidateView.setData(items);
                showToolbarView(items.isEmpty());
            }
        });
    }

    public void setSmallMode(boolean value) {
        Config.setSmallMode(value);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                setTheme(Config.getTheme());
            }
        });
    }

    public void setFloatMode(boolean value) {
        Config.setFloatMode(value);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                setTheme(Config.getTheme());
            }
        });
    }

    public void setComposition(CharSequence c) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mPreedit.setText(c);
            }
        });
    }

    public void addCompositions(ArrayList<String> list) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mPreedit.addCompositions(list);
            }
        });
    }

    public InputView getInputView() {
        return mInputView;
    }

    public CandidateView getCandidateView() {
        return mCandidateView;
    }


}
