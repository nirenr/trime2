package com.myopicmobile.textwarrior.android;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListPopupWindow;
import android.widget.TextView;
import android.widget.PopupWindow;
import com.osfans.trime.editor.RimeYamlCompletion;
import com.myopicmobile.textwarrior.common.ColorScheme;
import java.util.concurrent.*;

/** Separate per-editor completion popup; no changes to the Lua language registry. */
final class YamlCompletionPanel {
    interface Snapshot { String get(); }
    private final FreeScrollingTextField editor;
    private final Snapshot snapshot;
    private final ListPopupWindow popup;
    private ExecutorService worker;
    private Future<?> pending;
    private Runnable debounce;
    private long generation;
    private boolean applying;
    YamlCompletionPanel(FreeScrollingTextField editor,Snapshot snapshot){
        this.editor=editor;this.snapshot=snapshot;
        popup=new ListPopupWindow(editor.getContext());
        popup.setAnchorView(editor);popup.setModal(false);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        popup.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private int dp(int n){return Math.round(n*editor.getResources().getDisplayMetrics().density);}
    void refresh(){
        cancel();if(applying||!editor.isYamlMode()||!editor.hasFocus()||editor.isSelectText()||editor.getWindowToken()==null)return;
        final long request=generation;
        debounce=()->{
            if(request!=generation||!editor.hasFocus()||editor.isSelectText())return;
            if(editor.getLength()>300000)return;
            final String text=snapshot.get();final int caret=editor.getCaretPosition();
            if(worker==null||worker.isShutdown())worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Rime-YAML-completion");t.setDaemon(true);return t;});
            pending=worker.submit(()->{
                RimeYamlCompletion.Result result=RimeYamlCompletion.complete(text,caret);
                if(Thread.currentThread().isInterrupted())return;
                editor.post(()->{
                    if(request!=generation||!valid(text,caret)||result.items.isEmpty())return;
                    show(text,caret,result);
                });
            });
        };editor.postDelayed(debounce,100);
    }
    private boolean valid(String text,int caret){return editor.isYamlMode()&&editor.hasFocus()&&!editor.isSelectText()&&editor.getWindowToken()!=null&&editor.getCaretPosition()==caret&&text.equals(snapshot.get());}
    private void show(String text,int caret,RimeYamlCompletion.Result result){
        final int foreground=editor.getColorScheme().getColor(ColorScheme.Colorable.FOREGROUND);
        final int background=editor.getColorScheme().getColor(ColorScheme.Colorable.BACKGROUND);
        GradientDrawable shape=new GradientDrawable();shape.setColor(background);shape.setCornerRadius(dp(8));shape.setStroke(dp(1),foreground);popup.setBackgroundDrawable(shape);
        ArrayAdapter<RimeYamlCompletion.Item> adapter=new ArrayAdapter<RimeYamlCompletion.Item>(editor.getContext(),android.R.layout.simple_list_item_1,result.items){
            @Override public View getView(int position,View view,ViewGroup parent){TextView row=(TextView)super.getView(position,view,parent);row.setTextColor(foreground);row.setTextSize(14);row.setPadding(dp(12),dp(7),dp(12),dp(7));return row;}
        };
        popup.setAdapter(adapter);popup.setWidth(Math.min(editor.getWidth(),dp(320)));popup.setHeight(Math.min(dp(240),dp(52)*result.items.size()+dp(4)));
        popup.setOnItemClickListener((parent,view,position,id)->{
            if(!valid(text,caret)){cancel();return;}
            RimeYamlCompletion.Item item=result.items.get(position);cancel();applying=true;
            try{editor.replaceText(result.start,result.end-result.start,item.text);}finally{applying=false;}
        });
        int height=Math.min(dp(240),dp(52)*result.items.size()+dp(4));
        int y=editor.getCaretY()+editor.rowHeight()/2-editor.getScrollY();
        int top=Math.max(0,Math.min(y,editor.getHeight()-height));
        popup.setVerticalOffset(top-editor.getHeight());
        popup.show();
    }
    void cancel(){++generation;if(debounce!=null){editor.removeCallbacks(debounce);debounce=null;}if(pending!=null){pending.cancel(true);pending=null;}popup.dismiss();}
    void close(){cancel();if(worker!=null){worker.shutdownNow();worker=null;}}
}
