/*
 * Copyright (C), 2011-2016, TP-LINK TECHNOLOGIES CO., LTD.
 *
 * PlatformBuildHelper.java
 *
 * Description:管理不同平台反射MediaRecorder相关对象
 *
 * Author Wang Dasen
 *
 * Ver 1.0, 2016-7-26, Wang Dasen, Create file
 */

package com.tplink.tpsoundrecorder.other;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PlatformBuildHelper {

    public static final String TAG = "PlatformBuildHelper";

    private static int platform;

    public static final int PLATFORM_DEFAULT = 0;

    public static final int PLATFORM_MSM8909 = 1;

    public static final int PLATFORM_MT6755 = 2;

    private static final String BOARD_PLATFORM_MSM8909 = "msm8909";

    private static final String BOARD_PLATFORM_MT6755 = "mt6755";

    private static int OutputFormat_WAV = -1;

    private static int AudioEncoder_PCM = -1;

    private static Class<?> clazz;

    private static Method mMethod;

    public static void initPlatform() {
        platform = PLATFORM_DEFAULT;
        // check platform
        String platformStr = SystemProperties.get("ro.board.platform");
        if (platformStr.equals(BOARD_PLATFORM_MSM8909)) {
            platform = PLATFORM_MSM8909;
        } else if (platformStr.equals(BOARD_PLATFORM_MT6755)) {
            platform = PLATFORM_MT6755;
        }

        if (platform == PLATFORM_MT6755) {
            try {
                // in MTK mt6755 platform, pause(MediaRecorder recorder) define
                // in
                // MediaRecorderEx, in vendor/mediatek
                clazz = Class.forName("com.mediatek.media.MediaRecorderEx");
                mMethod = clazz.getDeclaredMethod("pause", android.media.MediaRecorder.class);
                if (clazz != null) {
                    Field formatfield = android.media.MediaRecorder.OutputFormat.class
                            .getDeclaredField("OUTPUT_FORMAT_WAV");
                    Field encoderField = android.media.MediaRecorder.AudioEncoder.class
                            .getDeclaredField("PCM");
                    OutputFormat_WAV = formatfield.getInt(formatfield);
                    AudioEncoder_PCM = encoderField.getInt(encoderField);
                }

            } catch (ExceptionInInitializerError e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                Log.e(TAG,
                        "get class com.mediatek.media.MediaRecorderEx, " +
                                "report ClassNotFoundException means that app did " +
                                "not run in MTK mt6755 platform");
                e.printStackTrace();
            } catch (LinkageError e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        } else if (platform == PLATFORM_MSM8909) {
            try {
                // in qcom platform, pause() define in
                // android.media.MediaRecorder
                clazz = android.media.MediaRecorder.class;
                mMethod = clazz.getDeclaredMethod("pause");

                Field formatfield = android.media.MediaRecorder.OutputFormat.class
                        .getDeclaredField("WAVE");
                Field encoderField = android.media.MediaRecorder.AudioEncoder.class
                        .getDeclaredField("LPCM");
                OutputFormat_WAV = formatfield.getInt(formatfield);
                AudioEncoder_PCM = encoderField.getInt(encoderField);
            } catch (ExceptionInInitializerError e) {
                e.printStackTrace();
            } catch (LinkageError e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                Log.e(TAG,
                        "get method pause(), report NoSuchMethodException means that app did not run in qcom msm8960 platform");

                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }

        }

        Log.d(TAG,
                "initPlatform() platformStr: " + platformStr + "; clazz: " + clazz + "; mMethod: "
                        + mMethod + ";platform: " + platform + "; OutputFormat_WAV:"
                        + OutputFormat_WAV + "; AudioEncoder_PCM: " + AudioEncoder_PCM);

    }

    public static int getPlatform() {
        return platform;
    }

    public static Class<?> getClazz() {
        return clazz;
    }

    public static Method getMethod() {
        return mMethod;
    }

    public static int getOutputFormatWAV() {
        return OutputFormat_WAV;
    }

    public static int getAudioEncoderPCM() {
        return AudioEncoder_PCM;
    }

}
