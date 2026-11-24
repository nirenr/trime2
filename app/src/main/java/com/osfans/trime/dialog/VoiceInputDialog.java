/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.dialog;


import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.speech.RecognitionService;

import com.androlua.LuaApplication;
import com.osfans.trime.theme.ThemeManager;
import com.osfans.trime.util.Function;

import java.util.ArrayList;
import java.util.List;

public class VoiceInputDialog {
    private final AlertDialog mDig;

    public VoiceInputDialog(Context context) {
        ArrayList<String> list = new ArrayList<>();
        list.add("默认");
        list.add("vivo");
        if(ThemeManager.getAsrClient()!=null){
            list.add("AI大模型");
            list.add("ai");
        }
        try {
            reloadInstalledRecognitionService(context.getPackageManager(), list);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String[] mEngines = new String[list.size()];
        list.toArray(mEngines);
        String name = Function.getPref(context).getString("recognition_service", "vivo");

        int idx=0;
        for (int i = 0; i < mEngines.length; i++) {
            if(mEngines[i].equals(name)){
                idx=i;
                break;
            }
        }

        mDig=new AlertDialog.Builder(context)
                .setTitle("语音识别引擎")
                .setSingleChoiceItems(mEngines, idx, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Function.saveString(LuaApplication.getInstance(), "recognition_service", mEngines[which]);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel,null)
                .create();
    }

    public static String reloadInstalledRecognitionService(PackageManager pm, List<String> results) {
        final Intent intent = new Intent(RecognitionService.SERVICE_INTERFACE);
        final List<ResolveInfo> resolveInfos =
                pm.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY);

        String systemTtsEngine = null;

        for (ResolveInfo resolveInfo : resolveInfos) {
            final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            final ApplicationInfo appInfo = serviceInfo.applicationInfo;
            final String packageName = serviceInfo.packageName;
            final CharSequence title = resolveInfo.loadLabel(pm);

            final boolean isSystemApp = ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);

            results.add(title + "/" + packageName + "/" + serviceInfo.name);

            if (isSystemApp) {
                systemTtsEngine = packageName;
            }
        }

        return systemTtsEngine;
    }

    public void show() {
        mDig.show();
    }
}
