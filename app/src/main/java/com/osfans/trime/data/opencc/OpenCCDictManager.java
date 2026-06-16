// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.opencc;

import com.androlua.LuaApplication;
import com.osfans.trime.core.DataManager;
import com.osfans.trime.data.opencc.dict.Dictionary;
import com.osfans.trime.data.opencc.dict.OpenCCDictionary;
import com.osfans.trime.data.opencc.dict.TextDictionary;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OpenCCDictManager {

    public static final boolean MODE_BIN_TO_TXT = true;  // OCD(2) to TXT
    public static final boolean MODE_TXT_TO_BIN = false; // TXT to OCD2

    private static final File sharedDir;

    static {
        System.loadLibrary("rime_jni");
        sharedDir = new File(DataManager.getSharedDataDir(), "opencc");
        if (!sharedDir.exists()) {
            sharedDir.mkdirs();
        }
    }

    // 私有构造函数，防止实例化
    private OpenCCDictManager() {}

    private static File getUserDir() {
        File userDir = new File(DataManager.getUserDataDir(), "opencc");
        if (!userDir.exists()) {
            userDir.mkdirs();
        }
        return userDir;
    }

    public static List<Dictionary> sharedDictionaries() {
        File[] files = sharedDir.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<Dictionary> dictionaries = new ArrayList<>();
        for (File file : files) {
            Dictionary dict = Dictionary.newDictionary(file);
            if (dict != null) {
                dictionaries.add(dict);
            }
        }
        return dictionaries;
    }

    public static List<Dictionary> userDictionaries() {
        File[] files = getUserDir().listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<Dictionary> dictionaries = new ArrayList<>();
        for (File file : files) {
            Dictionary dict = Dictionary.newDictionary(file);
            if (dict != null) {
                dictionaries.add(dict);
            }
        }
        return dictionaries;
    }

    public static List<Dictionary> getAllDictionaries() {
        List<Dictionary> all = new ArrayList<>(sharedDictionaries());
        all.addAll(userDictionaries());
        return all;
    }

    public static OpenCCDictionary importFromFile(File file) {
        Dictionary raw = Dictionary.newDictionary(file);
        if (raw == null) {
            throw new IllegalArgumentException(file.getPath() + " is not a opencc/text dictionary");
        }

        // 获取不带后缀的文件名
        String nameWithoutExtension = file.getName();
        int dotIndex = nameWithoutExtension.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExtension = nameWithoutExtension.substring(0, dotIndex);
        }

        // convert to opencc format in dictionaries dir
        // preserve original file name
        File destFile = new File(getUserDir(), nameWithoutExtension + "." + Dictionary.Type.OCD2.getExt());
        OpenCCDictionary newDict = raw.toOpenCCDictionary(destFile);
        //Timber.d("Converted %s to %s", raw, newDict);
        return newDict;
    }

    /**
     * Convert internal text dict to opencc format
     */
    public static void buildOpenCCDict() {
        for (Dictionary d : getAllDictionaries()) {
            if (d instanceof TextDictionary) {
                long startTime = System.currentTimeMillis();
                try {
                    OpenCCDictionary r = ((TextDictionary) d).toOpenCCDictionary();
                    long duration = System.currentTimeMillis() - startTime;
                    //Timber.d("Took %d ms to convert to %s", duration, r);
                } catch (Exception e) {
                    //Timber.e(e, "Failed to convert %s", d);
                }
            }
        }
    }

    public static OpenCCDictionary importFromInputStream(InputStream stream, String name) throws IOException {
        File tempFile = new File(LuaApplication.getInstance().getCacheDir(), name);

        // 使用 Java 的 try-with-resources 自动关闭流
        try (OutputStream os = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

        try {
            return importFromFile(tempFile);
        } finally {
            tempFile.delete();
        }
    }

    public static String convertLine(String input, String configFileName) {
        if (configFileName == null || configFileName.isEmpty()) {
            return input;
        }

        File userFile = new File(getUserDir(), configFileName);
        if (userFile.exists()) {
            return openCCLineConv(input, userFile.getPath());
        }

        File sharedFile = new File(sharedDir, configFileName);
        if (sharedFile.exists()) {
            return openCCLineConv(input, sharedFile.getPath());
        }

        //Timber.w("Specified config %s doesn't exist, returning raw input ...", configFileName);
        return input;
    }

    public static native void openCCDictConv(String src, String dest, boolean mode);

    public static native String openCCLineConv(String input, String configFileName);
}
