/*
 * Copyright (C) 2016, TP-LINK TECHNOLOGIES CO., LTD.
 *
 * SystemPropertiesInvokeUtil.java
 * 
 * Description
 *  
 * Author nongzhanfei
 * 
 * Ver 1.0, 9/10/16, nongzhanfei, Create file
 */
package com.tplink.tpsoundrecorder.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SystemPropertiesInvokeUtil {

    //get("ro.qc.sdk.audio.ssr", "false");
    private static String getString(String str1,String str2) {
        String result = "";
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getDeclaredMethod("get", String.class, String.class);
            result = (String) get.invoke(systemProperties, str1, str2);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return result;
    }

    //SystemProperties.getBoolean("debug.soundrecorder.enable", false)
    public static boolean getBoolean(String str1,boolean flag) {
        boolean result = false;
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getDeclaredMethod("getBoolean", Boolean.class);
            result = (boolean) get.invoke(systemProperties, false);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return result;
    }
}
