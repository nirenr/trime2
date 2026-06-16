/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.core;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.androlua.LuaApplication;
import com.osfans.trime.BuildConfig;
import com.osfans.trime.R;
import com.osfans.trime.TrimeApplication;
//import com.osfans.trime.core.isStorageAvailable;
import com.osfans.trime.data.opencc.OpenCCDictManager;
import com.osfans.trime.util.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;




public class Rime implements RimeApi, RimeLifecycleOwner {
    public static final int META_SHIFT_ON = RimeKeyEvent.getModifierByName("Shift");
    public static final int META_CTRL_ON = RimeKeyEvent.getModifierByName("Control");
    public static final int META_ALT_ON = RimeKeyEvent.getModifierByName("Alt");
    public static final int META_RELEASE_ON = RimeKeyEvent.getModifierByName("Release");
    private static boolean isAsciiMode;
    private final RimeLifecycleImpl lifecycleImpl = new RimeLifecycleImpl();

    // WARNING: Kotlin Flows cannot be directly mapped to standard Java, this is a conceptual conversion.
    private static final SharedFlowImpl<RimeMessage> messageFlow_ = new SharedFlowImpl<>(15);
    //TODO 获取 LuaApplication.getInstance().getApplicationContext();
    private final TrimeApplication appContext = LuaApplication.getInstance();
    private static boolean isComposing = false;
    private static boolean hasMenu = false;
    private static boolean paging = false;

    public static boolean isComposing() {
        return isComposing;
    }

    public static void toggleOption(String toggle) {
        setRimeOption(toggle, !getRimeOption(toggle));
        if(toggle.equals("ascii_mode"))
           isAsciiMode =getRimeOption(toggle);
    }

    public static boolean isAsciiMode() {
        return isAsciiMode;
    }

    public static boolean isVoidKeycode(int keycode) {
        int XK_VoidSymbol = 0xffffff;
        return keycode <= 0 || keycode == XK_VoidSymbol;
    }

    public boolean onText(CharSequence text) {
        if (text == null || text.length() == 0) return false;
        return Boolean.TRUE.equals(withRimeContext(() -> {
            boolean it = simulateRimeKeySequence(text.toString().replace("{}", "{braceleft}{braceright}"));
            if (it) Rime.this.emitResponse();
            return it;
        }));
    }

    // 对应 Kotlin 的 private set
    private void setComposing(boolean composing) {
        isComposing = composing;
    }

    public static boolean hasMenu() {
        return hasMenu;
    }

    // 对应 Kotlin 的 private set
    private void setHasMenu(boolean hasMenu) {
        this.hasMenu = hasMenu;
    }

    public static boolean isPaging() {
        return paging;
    }

    // 对应 Kotlin 的 private set
    private void setPaging(boolean paging) {
        this.paging = paging;
    }

    @Override
    public SharedFlowImpl<?> getMessage() {
        return messageFlow_;
    }

    @Override
    public RimeLifecycle getLifecycle() {
        return lifecycleImpl;
    }

    @Override
    public Executor getLifecycleExecutor() {
        return RimeLifecycleOwner.super.getLifecycleExecutor();
    }

    @Override
    public RimeLifecycle.State getState() {
        return lifecycleImpl.getCurrentState();
    }

    @Override
    public boolean isReady() {
        return lifecycleImpl.getCurrentState() == RimeLifecycle.State.READY;
    }

    @Override
    public RimeSchema getSchemaCached() {
        return schemaCached;
    }

    @Override
    public RimeProto.Status getStatusCached() {
        return statusCached;
    }

    @Override
    public RimeProto.Context.Composition getCompositionCached() {
        return compositionCached;
    }

    @Override
    public RimeProto.Context.Menu getMenuCached() {
        return menuCached;
    }


    private RimeSchema schemaCached;
    private RimeProto.Status statusCached = new RimeProto.Status();
    private RimeProto.Context.Composition compositionCached = new RimeProto.Context.Composition();
    private RimeProto.Context.Menu menuCached = new RimeProto.Context.Menu();

    // Simulating delegated property: AppPrefs.defaultInstance().general.asciiSwitchTips
    private boolean getShowAsciiSwitchTips() {
        // Assume AppPrefs.defaultInstance().general.getAsciiSwitchTips() is implemented in Java
        //return AppPrefs.defaultInstance().general.getAsciiSwitchTips();
        return false;
    }

    private String lastAsciiTipsText = "";
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private AtomicReference<ScheduledFuture<?>> asciiSwitchTipsJob = new AtomicReference<>(null);

    // Executor for Rime context operations (simulating CoroutineDispatcher)
    private final RimeDispatcher dispatcher;

    public Rime(Runnable runnable) {
        if (lifecycleImpl.getCurrentState() != RimeLifecycle.State.STOPPED) {
            throw new IllegalStateException("Rime has already been created!");
        }

        this.dispatcher = new RimeDispatcher(new RimeDispatcher.RimeController() {
            @Override
            public void nativeStartup() {
                Log.w("rime", "nativeStartup: " );
                startRime(false);
                runnable.run();
                lifecycleImpl.emitState(RimeLifecycle.State.READY);
            }

            @Override
            public void nativeFinalize() {
                Log.w("rime", "nativeFinalize: " );
                exitRime();
            }
        });
    }

    // Helper method to run code on the Rime dispatcher (Simulating withContext)
    private <T> T withRimeContext(Callable<T> block) {
        // In a real Android Java environment, you would use an Executor or a specific Handler/Thread
        // here to synchronize access to Rime JNI calls. For this conversion, we assume
        // RimeDispatcher handles execution.
        try {
            return dispatcher.submit(block);
        } catch (Exception e) {
            //Timber.e(e, "Rime context execution failed.");
            return null; // Handle error appropriately
        }
    }

    // --- RimeApi Implementation (Public Methods) ---

    @Override
    public boolean isEmpty() {
        return Boolean.TRUE.equals(withRimeContext(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return getCurrentRimeSchema().equals(".default");
            }
        })); // 無方案
    }

    @Override
    public void deploy() {
        withRimeContext((Callable<String>) () -> {
            Rime.exitRime();
            Rime.startRime(true);
            return null;
        });
    }
    public void restart() {
        withRimeContext((Callable<String>) () -> {
            Rime.exitRime();
            Rime.startRime(false);
            return null;
        });
    }

    @Override
    public boolean syncUserData() {
        return withRimeContext(Rime::syncRimeUserData);
    }

    @Override
    public boolean processKey(int value, long modifiers, boolean isVirtual) {
        return Boolean.TRUE.equals(withRimeContext(() -> Rime.this.processKeyInner(value, (int) modifiers, isVirtual)));
    }

    @Override
    public boolean processKey(int value) {
        return RimeApi.super.processKey(value);
    }

    @Override
    public boolean processKey(int value, long modifiers) {
        return RimeApi.super.processKey(value, modifiers);
    }

    @Override
    public boolean processKey(KeyValue value, KeyModifiers modifiers) {
        return RimeApi.super.processKey(value, modifiers);
    }

    @Override
    public boolean processKey(KeyValue value, KeyModifiers modifiers, boolean isVirtual) {
        return Boolean.TRUE.equals(withRimeContext(() -> processKeyInner(value.getValue(), modifiers.toInt(), isVirtual)));
    }

    @Override
    public boolean simulateKeySequence(String sequence) {
        boolean composing = isComposing();
        if(BuildConfig.DEBUG)Log.w("rime", "simulateKeySequence:old1 "+composing);
        return Boolean.TRUE.equals(withRimeContext(() -> {
            //Timber.d("simulateKeySequence: " + sequence);
            String old = compositionCached.getCommitTextPreview();
            if(BuildConfig.DEBUG)Log.w("rime", "simulateKeySequence:old2 "+old );
            boolean success = simulateRimeKeySequence(sequence);
            if (success) {
                RimeProto.Commit commit = getRimeCommit();
                String input = getRimeRawInput();
                if(BuildConfig.DEBUG)Log.w("rime", "simulateKeySequence:1 "+commit);
                if(BuildConfig.DEBUG)Log.w("rime", "simulateKeySequence:2 "+input );
                if (commit.getText() != null && !commit.getText().isEmpty()) {
                    emitResponse(commit);
                    if(BuildConfig.DEBUG)Log.w("rime", "simulateKeySequence:3 "+isComposing() );
                    if(composing&&!isComposing()&&commit.getText().equals(old))
                        emitResponse(new RimeProto.Commit(sequence));
                    return true;
                } else if(!input.isEmpty()){
                   if(BuildConfig.DEBUG)Log.w("rime", "simulateKeySequence:4 "+input);
                    emitResponse(commit);
                    return true;
                } else {
                    //if(!TextUtils.isEmpty(old)) {
                    //    emitResponse(new RimeProto.Commit(old));
                    //    clearRimeComposition();
                    //}
                    if(BuildConfig.DEBUG)Log.w("rime", "simulateKeySequence:5 "+sequence);
                    emitResponse(new RimeProto.Commit(sequence));
                    return false;
                }
            } else {
                return false;
            }
        }));//.also(result -> //Timber.d("simulateKeySequence " + (result ? "success" : "failed")));
    }

    @Override
    public boolean selectCandidate(int idx) {
        return Boolean.TRUE.equals(withRimeContext(() -> {
            boolean it = selectRimeCandidate(idx);
            if (it) Rime.this.emitResponse();
            return it;
        }));
    }

    @Override
    public boolean forgetCandidate(int idx) {
        return Boolean.TRUE.equals(withRimeContext(() -> {
            boolean it = forgetRimeCandidate(idx);
            if (it) Rime.this.emitResponse();
            return it;
        }));
    }

    @Override
    public boolean selectPagedCandidate(int idx) {
        return Boolean.TRUE.equals(withRimeContext(() -> {
            boolean it = selectRimeCandidateOnCurrentPage(idx);
            if (it) Rime.this.emitResponse();
            return it;
        }));
    }

    @Override
    public boolean deletedPagedCandidate(int idx) {
        return Boolean.TRUE.equals(withRimeContext(() -> {
            boolean it = deleteRimeCandidateOnCurrentPage(idx);
            if (it) Rime.this.emitResponse();
            return it;
        }));
    }

    @Override
    public boolean changeCandidatePage(boolean backward) {
        return Boolean.TRUE.equals(withRimeContext(() -> {
            boolean it = changeRimeCandidatePage(backward);
            if (it) Rime.this.emitResponse();
            return it;
        }));
    }

    @Override
    public void moveCursorPos(int position) {
         withRimeContext((Callable<String>) () -> {
             setRimeCaretPos(position);
             Rime.this.emitResponse();
             return null;
         });
    }

    @Override
    public SchemaItem[] availableSchemata() {
        return withRimeContext(Rime::getAvailableRimeSchemaList);
    }

    @Override
    public SchemaItem[] enabledSchemata() {
        return withRimeContext(Rime::getSelectedRimeSchemaList);
    }

    @Override
    public boolean setEnabledSchemata(String[] schemaIds) {
        return Boolean.TRUE.equals(withRimeContext(() -> selectRimeSchemas(schemaIds)));
    }

    @Override
    public SchemaItem[] selectedSchemata() {
        return withRimeContext(Rime::getRimeSchemaList);
    }

    @Override
    public String selectedSchemaId() {
        return withRimeContext(Rime::getCurrentRimeSchema);
    }

    @Override
    public boolean selectSchema(String schemaId) {
        return Boolean.TRUE.equals(withRimeContext(() -> selectRimeSchema(schemaId)));
    }

    @Override
    public RimeSchema currentSchema() {
        return withRimeContext(() -> new RimeSchema(getCurrentRimeSchema()));
    }

    @Override
    public boolean commitComposition() {
        return Boolean.TRUE.equals(withRimeContext(() -> {
            boolean it = commitRimeComposition();
            if (it) Rime.this.emitResponse();
            return it;
        }));
    }

    @Override
    public void clearComposition() {
        withRimeContext((Callable<String>) () -> {
            clearRimeComposition();
            Rime.this.emitResponse();
            return null;
        });
    }

    @Override
    public void setRuntimeOption(String option, boolean value) {
        withRimeContext((Callable<String>) () -> {
            setRimeOption(option, value);
            return null;
        });
    }

    public void toggleRuntimeOption(String option) {
        withRimeContext((Callable<String>) () -> {
            toggleOption(option);
            return null;
        });
    }

    @Override
    public boolean getRuntimeOption(String option) {
        return Boolean.TRUE.equals(withRimeContext(() -> getRimeOption(option)));
    }

    @Override
    public CandidateItem[] getCandidates(int startIndex, int limit) {
        return withRimeContext(() -> getRimeCandidates(startIndex, limit));
    }

    // --- Private Helper Methods ---

    @SuppressLint("StringFormatIn//Timber")
    public static void startRime(boolean fullCheck) {
        DataManager.sync();
        String sharedDataDir = DataManager.getSharedDataDir().getAbsolutePath();
        String userDataDir = DataManager.getUserDataDir().getAbsolutePath();
        //Timber.d(
        //        String.format(Locale.CHINA,
        //                "Starting rime with: sharedDataDir: %s userDataDir: %s fullCheck: %b",
        //                sharedDataDir,
        //                userDataDir,
        //                fullCheck
        //        )
        //);
        startupRime(sharedDataDir, userDataDir, BuildConfig.BUILD_VERSION_NAME, fullCheck);
    }

    private boolean processKeyInner(int value, int modifiers, boolean isVirtual) {
        //lastAsciiTipsText = getAsciiTipsText();
        boolean handled = processRimeKey(value, modifiers);
        emitResponse();
        if (!handled) {
            handleRimeMessage(
                    9, // RimeMessage.MessageType.Key,
                    new Object[]{value, modifiers, isVirtual}
            );
        }
        return handled;
    }

    private String getAsciiTipsText() {
        RimeProto.Status status = getRimeStatus();
        if (status.isAsciiMode()) {
            return "En";
        } else if (status.getSchemaName() != null &&
                !status.getSchemaName().startsWith(".")) {
            // Java equivalent of take(2)
            return status.getSchemaName();
            //return status.getSchemaName().length() >= 2 ? status.getSchemaName().substring(0, 2) : status.getSchemaName();
        } else {
            return "";
        }
    }

    public void emitResponse() {
        emitResponse(Rime.getRimeCommit());
    }

    private void emitResponse(RimeProto.Commit commit) {
        handleRimeMessage(4, new Object[]{commit});

        RimeProto.Context context = getRimeContext();
        handleRimeMessage(5, new Object[]{context.getComposition()});

        //if (context.getComposition().getLength() <= 0 && !lastAsciiTipsText.equals(getAsciiTipsText())) {
        //    showAsciiSwitchTips();
        //}

        if (getRimeOption("paging_mode")) {
            handleRimeMessage(6, new Object[]{context.getMenu()});
        } else {
            CandidateItem[] candidates = getRimeCandidates(0, 1);
            handleRimeMessage(
                    8,
                    new Object[]{candidates.length, candidates}
            );
        }
        handleRimeMessage(7, new Object[]{getRimeStatus()});
    }

    public static void handleRimeMessage(int type, Object[] params) {
        // 1. 调用静态工厂方法创建消息
        RimeMessage<?> message = RimeMessage.nativeCreate(type, params);

        // 2. 日志记录 (//Timber 在 Java 中推荐使用占位符或字符串拼接)
        //Timber.d("Handling %s", message);

        // 3. 遍历并执行所有处理器
        // 假设 rimeMessageHandlers 是一个 List<Consumer<RimeMessage<?>>>
        // 或者类似的函数式接口集合
        for (Consumer<RimeMessage<?>> handler : rimeMessageHandlers) {
            handler.accept(message);
        }

        // 4. 发送到 Flow (MutableSharedFlow 的 tryEmit 在 Java 中可以直接调用)
        messageFlow_.tryEmit(message);
    }

    public void handleRimeMessage(RimeMessage<?> message) {
        if (message instanceof RimeMessage.SchemaMessage) {
            RimeMessage.SchemaMessage msg = (RimeMessage.SchemaMessage) message;
            this.statusCached = getRimeStatus();
            isComposing=statusCached.isComposing();
            isAsciiMode =statusCached.isAsciiMode();
            this.schemaCached = new RimeSchema(msg.getData().getId());
         } else if (message instanceof RimeMessage.OptionMessage) {
            RimeMessage.OptionMessage msg = (RimeMessage.OptionMessage) message;
            RimeProto.Status status = getRimeStatus();
            isComposing=status.isComposing();
            this.statusCached = status;
            updateSchemaCached(status);

            if ("ascii_mode".equals(msg.getData().getOption())) {
                isAsciiMode =status.isAsciiMode();
                showAsciiSwitchTips();
            }

        } else if (message instanceof RimeMessage.DeployMessage) {
            RimeMessage.DeployMessage msg = (RimeMessage.DeployMessage) message;
            Context context = LuaApplication.getInstance();
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            int notificationId = 1001; // 固定 ID 以便更新同一条通知
            long startTime = SystemClock.elapsedRealtime(); // 记录起始点

            if (msg.getData() == RimeMessage.DeployMessage.State.Start) {
                OpenCCDictManager.buildOpenCCDict();

                // 发送通知，并实时显示耗时
                Notification notification = new NotificationCompat.Builder(context, "rime_deploy_channel")
                        .setContentTitle("正在部署")
                        .setContentText("请稍候...")
                        .setSmallIcon(R.drawable.icon) // 替换为你的图标
                        .setOngoing(true) // 设置为正在进行，防止被划掉
                        .setUsesChronometer(true) // 核心：开启计时器
                        .setWhen(System.currentTimeMillis())
                        .build();
                notificationManager.notify(notificationId, notification);

            } else if (msg.getData() == RimeMessage.DeployMessage.State.Success) {
                // 计算总耗时（秒）
                long duration = (SystemClock.elapsedRealtime() - startTime) / 1000;

                // 显示完成通知，并显示总耗时
                Notification notification = new NotificationCompat.Builder(context, "rime_deploy_channel")
                        .setContentTitle("部署成功")
                        .setContentText("部署已完成，总耗时：" + duration + "秒")
                        .setSmallIcon(R.drawable.icon) // 替换为你的图标
                        .setOngoing(false) // 允许划掉
                        .setUsesChronometer(false) // 停止计时
                        .setTimeoutAfter(duration<5?1000:30000)
                        .build();
                notificationManager.notify(notificationId, notification);
            } else if (msg.getData() == RimeMessage.DeployMessage.State.Failure) {
                long duration = (SystemClock.elapsedRealtime() - startTime) / 1000;

                // 显示失败通知，并显示总耗时
                Notification notification = new NotificationCompat.Builder(context, "rime_deploy_channel")
                        .setContentTitle("部署失败")
                        .setContentText("部署过程中出现错误，已停止。耗时：" + duration + "秒")
                        .setSmallIcon(R.drawable.icon) // 替换为你的图标
                        .setOngoing(false)
                        .build();
                notificationManager.notify(notificationId, notification);
            }

        } else if (message instanceof RimeMessage.CompositionMessage) {
            RimeProto.Context.Composition data = ((RimeMessage.CompositionMessage) message).getData();
            isComposing=statusCached.isComposing();
            this.compositionCached = data;
         } else if (message instanceof RimeMessage.CandidateMenuMessage) {
            RimeProto.Context.Menu menu = ((RimeMessage.CandidateMenuMessage) message).getData();
            paging = menu.getPageNumber() != 0;
            hasMenu = menu.getCandidates() != null && menu.getCandidates().length!=0;

        } else if (message instanceof RimeMessage.CandidateListMessage) {
            RimeMessage.CandidateListMessage.Data list = ((RimeMessage.CandidateListMessage) message).getData();
            hasMenu = list.getCandidates() != null && list.getCandidates().length!=0;

        } else if (message instanceof RimeMessage.StatusMessage) {
            RimeProto.Status status = ((RimeMessage.StatusMessage) message).getData();
            this.statusCached = status;
            isComposing=statusCached.isComposing();
            isAsciiMode =statusCached.isAsciiMode();
            updateSchemaCached(status);
        }
    }

    private void updateSchemaCached(RimeProto.Status status) {
        try {
            String schemaId = status.getSchemaId();
            String schemaName = status.getSchemaName();
            // Engine response update won't send SchemaMessage, but usually update RimeStatus
            if (schemaCached==null||!schemaId.equals(schemaCached.getSchemaId())) {
                schemaCached = new RimeSchema(schemaId);
                // notify downstream consumers that schema has changed
                RimeMessage.SchemaMessage message = new RimeMessage.SchemaMessage(
                        new SchemaItem(schemaId, schemaName)
                );
                messageFlow_.tryEmit(message);
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private void showAsciiSwitchTips() {
        //Function.printStackTrace("showAsciiSwitchTips");
        if (!getShowAsciiSwitchTips()) return;
        String tipsText = getAsciiTipsText();
        if (tipsText.isEmpty()) return;

        RimeProto.Context.Composition tips = new RimeProto.Context.Composition(tipsText);
        handleRimeMessage(5, new Object[]{tips});

        // Cancel previous job if running
        ScheduledFuture<?> prevJob = asciiSwitchTipsJob.getAndSet(null);
        if (prevJob != null) {
            prevJob.cancel(true);
        }

        // Schedule the new job (1000L delay)
        Runnable job = () -> {
            RimeProto.Context ctx = getRimeContext();
            handleRimeMessage(5, new Object[]{ctx.getComposition()});
        };
        asciiSwitchTipsJob.set(scheduler.schedule(job, 1000L, TimeUnit.MILLISECONDS));
    }
    // 1. 在类中定义一个成员变量来保存监听器
    private final Rime.Consumer<RimeMessage<?>> mMessageHandler = this::handleRimeMessage;

    public void startup() {
        if (lifecycleImpl.getCurrentState() != RimeLifecycle.State.STOPPED) {
            //Timber.w("Skip starting rime: not at stopped state!");
            return;
        }
        if (appContext.isStorageAvailable()) {
            registerRimeMessageHandler(mMessageHandler);
            lifecycleImpl.emitState(RimeLifecycle.State.STARTING);
            dispatcher.start();
        }
    }

    public void finalize() {
        if (lifecycleImpl.getCurrentState() != RimeLifecycle.State.READY) {
            //Timber.w("Skip stopping rime: not at ready state!");
            return;
        }
        lifecycleImpl.emitState(RimeLifecycle.State.STOPPING);
        //Timber.i("Rime finalize()");
        List<Runnable> pendingJobs = dispatcher.stop();
        if (!pendingJobs.isEmpty()) {
            //Timber.w(pendingJobs.size() + " job(s) didn't get a chance to run!");
        }
        lifecycleImpl.emitState(RimeLifecycle.State.STOPPED);
        unregisterRimeMessageHandler(mMessageHandler);
        scheduler.shutdownNow();
    }


    // --- Companion Object / Static JNI Methods ---

    static {
        System.loadLibrary("rime_jni");
    }

    private static final List<Consumer<RimeMessage<?>>> rimeMessageHandlers = new ArrayList<>();

    // init
    public static native void startupRime(
            String sharedDir,
            String userDir,
            String versionName,
            boolean fullCheck
    );

    public static native void exitRime();

    public static native boolean deployRimeSchemaFile(String schemaFile);

    public static native boolean deployRimeConfigFile(
            String fileName,
            String versionKey
    );

    public static native boolean syncRimeUserData();

    // input
    public static native boolean processRimeKey(
            int keycode,
            int mask
    );

    public static native boolean commitRimeComposition();

    public static native void clearRimeComposition();

    // output
    public static native RimeProto.Commit getRimeCommit();

    public static native RimeProto.Context getRimeContext();

    public static native RimeProto.Status getRimeStatus();

    // runtime options
    public static native void setRimeOption(
            String option,
            boolean value
    );

    public static native boolean getRimeOption(String option);

    public static native SchemaItem[] getRimeSchemaList();

    public static native String getCurrentRimeSchema();

    public static native boolean selectRimeSchema(String schemaId);

    // testing
    public static native boolean simulateRimeKeySequence(String keySequence);

    public static native String getRimeRawInput();

    public static native int getRimeCaretPos();

    public static native void setRimeCaretPos(int caretPos);

    public static native boolean selectRimeCandidateOnCurrentPage(int index);

    public static native boolean deleteRimeCandidateOnCurrentPage(int index);

    public static native boolean selectRimeCandidate(int index);

    public static native boolean forgetRimeCandidate(int index);

    public static native boolean changeRimeCandidatePage(boolean backward);

    public static native boolean highlightRimeCandidate(int index);

    public static native int getHighlightRimeCandidate();

    public static native SchemaItem[] getAvailableRimeSchemaList();

    public static native SchemaItem[] getSelectedRimeSchemaList();

    public static native boolean selectRimeSchemas(String[] schemaIds);

    public static native CandidateItem[] getRimeCandidates(
            int startIndex,
            int limit
    );

    public String getComposingText() {
        RimeProto.Context.Composition cc = getCompositionCached();
        if(cc!=null)
           return cc.getCommitTextPreview();
        return "";
    }


    @FunctionalInterface
    public interface Consumer<T> {

        /**
         * Performs this operation on the given argument.
         *
         * @param t the input argument
         */
        void accept(T t);

        /**
         * Returns a composed {@code Consumer} that performs, in sequence, this
         * operation followed by the {@code after} operation. If performing either
         * operation throws an exception, it is relayed to the caller of the
         * composed operation.  If performing this operation throws an exception,
         * the {@code after} operation will not be performed.
         *
         * @param after the operation to perform after this operation
         * @return a composed {@code Consumer} that performs in sequence this
         * operation followed by the {@code after} operation
         * @throws NullPointerException if {@code after} is null
         */
        default Consumer<T> andThen(Consumer<? super T> after) {
            Objects.requireNonNull(after);
            return (T t) -> {
                accept(t);
                after.accept(t);
            };
        }
    }

    public static void registerRimeMessageHandler(Consumer<RimeMessage<?>> handler) {
        if (rimeMessageHandlers.contains(handler)) return;
        rimeMessageHandlers.add(handler);
    }

    public static void unregisterRimeMessageHandler(Consumer<RimeMessage<?>> handler) {
         rimeMessageHandlers.remove(handler);
    }

    public static void unregisterAllRimeMessageHandlers() {
        rimeMessageHandlers.clear();
    }
}
