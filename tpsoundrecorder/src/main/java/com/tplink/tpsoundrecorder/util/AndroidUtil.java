/*
 * Copyright (C) 2016, TP-LINK TECHNOLOGIES CO., LTD.
 *
 * AndroidSDUtil.java
 * 
 * Description
 *  
 * Author nongzhanfei
 * 
 * Ver 1.0, 9/10/16, nongzhanfei, Create file
 */
package com.tplink.tpsoundrecorder.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class AndroidUtil {

    public static final String[] SUPPORT_COUNTRY = {
            "PL", "RU", "UA", "MX", "IR", "MY", "ID", "IN", "VN", "TH", "SG", "BD", "CO"
    };

    public static final String[] UNSUPPORT_COUNTRY = {
            "TR", "IT", "DE", "ES", "GR", "BR", "FR", "PT"
    };

    private static final String TP801 = "Y5L";
    private static final String TP803 = "Y5";

    public static String getSDPath(Context context) {
        StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        try {
            Method getVolumList = StorageManager.class.getMethod("getVolumeList", null);
            getVolumList.setAccessible(true);
            Object[] results = (Object[]) getVolumList.invoke(sm, null);
            if (results != null) {
                for (Object result : results) {
                    Method mRemoveable = result.getClass().getMethod("isRemovable", null);
                    Boolean isRemovable = (Boolean) mRemoveable.invoke(result, null);
                    if (isRemovable) {
                        Method getPath = result.getClass().getMethod("getPath", null);
                        String sdPath = (String) getPath.invoke(result, null);
                        return sdPath;
                    }
                }
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getSDState(Context context) {
        String sdPath = getSDPath(context);
        StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        try {
            if (sdPath != null) {
                Method getState = sm.getClass().getMethod("getVolumeState", String.class);
                String state = (String) getState.invoke(sm, sdPath);
                if (state == "unknown") {
                    state = null;
                }
                return state;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getPhoneLocalStoragePath(Context context) {
        return Environment.getExternalStorageDirectory().toString();
    }

    /**
     * check unsupport country only, set support in default
     *
     * @param country_code
     * @return
     */
    public static boolean isSupportCallRecording(String country_code) {
        boolean result = true;
        String name = Build.DEVICE;
        //如果是801和803就不支持
        if (Build.DEVICE.equals(TP801)||Build.DEVICE.equals(TP803)) {
            return false;
        }

        int size = UNSUPPORT_COUNTRY.length;
        for (int i = 0; i < size; i++) {
            if (country_code != null && country_code.equals(UNSUPPORT_COUNTRY[i])) {
                result = false;
                break;
            }
        }
        Log.d("AndroidUtil", "country code: " + country_code + "; supportable: " + result);

        return result;
    }
}
