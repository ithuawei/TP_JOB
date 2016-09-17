/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tplink.tpsoundrecorder.recorder;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaRecorder;
import android.text.TextUtils;
import android.util.Log;

import com.tplink.tpsoundrecorder.R;
import com.tplink.tpsoundrecorder.other.PlatformBuildHelper;
import com.tplink.tpsoundrecorder.service.SoundRecorderService;
import com.tplink.tpsoundrecorder.util.AndroidUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Recorder implements OnCompletionListener, OnErrorListener {
    static final String TAG = "Recorder";

    static final String SAMPLE_PREFIX = "";

    public static final int INVALID_STATE = 0;

    public static final int IDLE_STATE = 0;

    public static final int RECORDING_STATE = 1;

    public static final int PLAYING_STATE = 2;

    public static final int PAUSE_STATE = 3;

    int mState = IDLE_STATE;

    public static final int SDCARD_ACCESS_ERROR = 1;

    public static final int INTERNAL_ERROR = 2;

    public static final int IN_CALL_RECORD_ERROR = 3;

    public static final int UNSUPPORTED_FORMAT = 4;

    public int mChannels = 0;

    public int mSamplingRate = 0;

    public String mStoragePath = null;

    public String mTime;

    //提供状态改变监听接口，向外提供改变状态方法/状态错误方法
    public interface OnStateChangedListener {

        void onStateChanged(int state);

        void onError(int error);
    }

    OnStateChangedListener mOnStateChangedListener = null;

    long mSampleStart = 0; // time at which latest record or play operation
    // started

    long mSampleLength = 0; // length of current sample

    File mSampleFile = null;

    MediaRecorder mRecorder = null;

    MediaPlayer mPlayer = null;

    public Recorder(Context context) {
        mStoragePath = AndroidUtil.getPhoneLocalStoragePath(context) + "/"
                + SoundRecorderService.FOLDER_NAME;
        //平台初始化
        PlatformBuildHelper.initPlatform();
    }

    public int getMaxAmplitude() {
        if (mState != RECORDING_STATE)
            return 0;
        return mRecorder.getMaxAmplitude();
    }

    public void setOnStateChangedListener(OnStateChangedListener listener) {
        mOnStateChangedListener = listener;
        // 设置监听，则立即发送状态修改通知，让监听者马上收到录音的状态
        listener.onStateChanged(mState);
    }

    public void setChannels(int nChannelsCount) {
        mChannels = nChannelsCount;
    }

    public void setSamplingRate(int samplingRate) {
        mSamplingRate = samplingRate;
    }

    public int state() {
        return mState;
    }

    public int progress() {
        if (mState == RECORDING_STATE) {
            return (int) ((mSampleLength + (System.currentTimeMillis() - mSampleStart)) / 1000);
        } else if (mState == PLAYING_STATE) {
            return (int) ((System.currentTimeMillis() - mSampleStart) / 1000);
        }
        return 0;
    }

    /**
     * mSampleLength是在录音暂停或者停止时才更新的，现在需要在录音时就能获取，所以增加该条件分支
     */
    public int sampleLength() {
        if (mState == RECORDING_STATE) {
            return (int) ((mSampleLength + (System.currentTimeMillis() - mSampleStart)) / 1000);
        }

        return (int) (mSampleLength / 1000);
    }

    //毫秒显示,float
    public float sampleLength_ms() {
        if (mState == RECORDING_STATE) {
            return (float) (mSampleLength + (System.currentTimeMillis() - mSampleStart)) / 1000;
        }
        return (float) mSampleLength / 1000;
    }

    public File sampleFile() {
        return mSampleFile;
    }

    public String getStartRecordingTime() {
        return mTime;
    }

    /**
     * 重置录音机状态，如果已经录制过了就删除。
     * Resets the recorder state. If a sample was recorded, the file is deleted.
     */
    public void delete() {
        stop();

        if (mSampleFile != null)
            mSampleFile.delete();

        mSampleFile = null;
        mSampleLength = 0;

        signalStateChanged(IDLE_STATE);
    }

    /**重置录音机状态，如果已经录制过了，该文件将被留在磁盘上，并将被重新使用为一个新的记录
     * Resets the recorder state. If a sample was recorded, the file is left on
     * disk and will be reused for a new recording.
     */
    public void clear() {
        stop();

        mSampleFile = null;
        mSampleLength = 0;

        signalStateChanged(IDLE_STATE);
    }

    public void startRecording(int outputfileformat, String extension, Context context,
                               int audiosourcetype, int codectype) {
        stop();

        if (mSampleFile != null) {
            delete();
        }

        if (mSampleFile == null) {
            File sampleDir = new File(mStoragePath);
            if (!sampleDir.exists()) {
                sampleDir.mkdirs();
            }
            if (!sampleDir.canWrite()) // Workaround for broken sdcard support
                // on the device.
                sampleDir = new File("/sdcard/sdcard");

            try {
                if (!"".equals(context.getResources().getString(R.string.def_save_name_prefix))) {
                    String prefix = context.getResources().getString(R.string.def_save_name_prefix)
                            + '-';
                    mSampleFile = createTempFile(context, prefix, extension, sampleDir);
                } else {
                    //获取当前日期
                    String dateFormat = context.getResources().getString(R.string.def_date_format);
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat);
                    String time = simpleDateFormat.format(new Date(System.currentTimeMillis()));
                    if (!TextUtils.isEmpty(time)) {
                        time = time.replaceAll("[\\\\*|\":<>/?]", "_").replaceAll(" ",
                                "\\\\" + " ");
                    }
                    mTime = time;
                    if (extension == null) {
                        extension = ".tmp";
                    }

                    //拼接：   ""+2010-01-02-12-03-06+".arr"
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(SAMPLE_PREFIX).append(time).append(extension);
                    String name = stringBuilder.toString();

                    //(SoundRecorder,2010-01-02-12-04-39.amr)
                    mSampleFile = new File(sampleDir, name);

                    if (!mSampleFile.createNewFile()) {
                        mSampleFile = File.createTempFile(SAMPLE_PREFIX, extension, sampleDir);
                    }
                }
            } catch (IOException e) {
                setError(SDCARD_ACCESS_ERROR);
                return;
            }
        }

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(audiosourcetype);//录制声音来源：MediaRecorder.AudioSource.MIC = 1;
        // set channel for surround sound recording.
        if (mChannels > 0) {
            mRecorder.setAudioChannels(mChannels);//设置录制的音频通道数:1
        }
        if (mSamplingRate > 0) {
            mRecorder.setAudioSamplingRate(mSamplingRate);//采样率
        }

        mRecorder.setOutputFormat(outputfileformat);//输出格式

        try {
            /**
             * MediaRecorder.AudioEncoder.AMR_NB = 1;
             * MediaRecorder.AudioEncoder.AMR_WB = 2;
             */
            mRecorder.setAudioEncoder(codectype);//设置所录制的声音的“编码”格式  2
        } catch (RuntimeException exception) {
            setError(UNSUPPORTED_FORMAT);
            mRecorder.reset();
            mRecorder.release();
            if (mSampleFile != null)
                mSampleFile.delete();
            mSampleFile = null;
            mSampleLength = 0;
            mRecorder = null;
            return;
        }
        /**
         *  /storage/emulated/0/SoundRecorder/2010-01-02-12-25-59.amr
         */
        mRecorder.setOutputFile(mSampleFile.getAbsolutePath());

        // Handle IOException
        try {
            mRecorder.prepare();
        } catch (IOException exception) {
            setError(INTERNAL_ERROR);
            mRecorder.reset();
            mRecorder.release();
            if (mSampleFile != null)
                mSampleFile.delete();
            mSampleFile = null;
            mSampleLength = 0;
            mRecorder = null;
            return;
        }
        // Handle RuntimeException if the recording couldn't start
        try {
            mRecorder.start();
        } catch (RuntimeException exception) {
            //通过系统服务获得声音管理器
            AudioManager audioMngr = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            boolean isInCall = ((audioMngr.getMode() == AudioManager.MODE_IN_CALL)//接通电话模式
                    || (audioMngr.getMode() == AudioManager.MODE_IN_COMMUNICATION));//通话模式
            if (isInCall) {
                setError(IN_CALL_RECORD_ERROR);
            } else {
                setError(INTERNAL_ERROR);
            }
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
            return;
        }
        mSampleStart = System.currentTimeMillis();
        setState(RECORDING_STATE);
    }

    //暂停录制
    public void pauseRecording() {
        if (mRecorder == null) {
            return;
        }
        try {

            int platform = PlatformBuildHelper.getPlatform();
            if (platform == PlatformBuildHelper.PLATFORM_MT6755) {
                if (PlatformBuildHelper.getMethod() != null
                        && PlatformBuildHelper.getClazz() != null) {
                    try {
                        PlatformBuildHelper.getMethod().invoke(PlatformBuildHelper.getClazz(),
                                mRecorder);
                    } catch (NullPointerException | IllegalAccessException
                            | IllegalArgumentException | InvocationTargetException e) {
                        e.printStackTrace();
                        setError(INTERNAL_ERROR);
                        Log.e(TAG, "PLATFORM_MTK excute pause(mRecorder) failed");
                    }
                }

            } else if (platform == PlatformBuildHelper.PLATFORM_MSM8909) {
                if (PlatformBuildHelper.getMethod() != null
                        && PlatformBuildHelper.getClazz() != null) {
                    try {
                        PlatformBuildHelper.getMethod().invoke(mRecorder);
                    } catch (NullPointerException | IllegalAccessException
                            | IllegalArgumentException | InvocationTargetException e) {
                        e.printStackTrace();
                        setError(INTERNAL_ERROR);
                        Log.e(TAG, "PLATFORM_QCOM excute pause() failed");
                    }
                }
            } else {
                setError(INTERNAL_ERROR);
                Log.e(TAG, "Pause Failed, platform does not support pause, platform: " + platform);
            }
        } catch (RuntimeException exception) {
            setError(INTERNAL_ERROR);
            Log.e(TAG, "Pause Failed");
            exception.printStackTrace();
        }
        mSampleLength = mSampleLength + (System.currentTimeMillis() - mSampleStart);
        setState(PAUSE_STATE);
    }

    //恢复录制
    public void resumeRecording() {
        if (mRecorder == null) {
            return;
        }
        try {
            mRecorder.start();
        } catch (RuntimeException exception) {
            setError(INTERNAL_ERROR);
            Log.e(TAG, "Resume Failed");
            exception.printStackTrace();
        }
        mSampleStart = System.currentTimeMillis();
        setState(RECORDING_STATE);
    }

    //结束录制
    public void stopRecording() {
        if (mRecorder == null)
            return;
        try {
            mRecorder.stop();
        } catch (RuntimeException exception) {
            setError(INTERNAL_ERROR);
            Log.e(TAG, "Stop Failed");
        }
        mRecorder.reset();
        mRecorder.release();
        mRecorder = null;
        mChannels = 0;
        mSamplingRate = 0;
        if (mState == RECORDING_STATE) {
            mSampleLength = mSampleLength + (System.currentTimeMillis() - mSampleStart);
        }
        setState(IDLE_STATE);
    }

    //在后台播放
    public void startPlayback() {
        stop();

        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mSampleFile.getAbsolutePath());
            mPlayer.setOnCompletionListener(this);
            mPlayer.setOnErrorListener(this);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            setError(INTERNAL_ERROR);
            mPlayer = null;
            return;
        } catch (IOException e) {
            setError(SDCARD_ACCESS_ERROR);
            mPlayer = null;
            return;
        }

        mSampleStart = System.currentTimeMillis();
        setState(PLAYING_STATE);
    }

    //停止试听
    public void stopPlayback() {
        if (mPlayer == null) // we were not in playback
            return;

        mPlayer.stop();
        mPlayer.release();
        mPlayer = null;
        setState(IDLE_STATE);
    }

    //停止录制和播放
    public void stop() {
        stopRecording();
        stopPlayback();
    }

    public boolean onError(MediaPlayer mp, int what, int extra) {
        stop();
        setError(SDCARD_ACCESS_ERROR);
        return true;
    }

    public void onCompletion(MediaPlayer mp) {
        stop();
    }

    private void setState(int state) {
        if (state == mState)
            return;

        mState = state;
        signalStateChanged(mState);
    }

    private void signalStateChanged(int state) {
        if (mOnStateChangedListener != null)
            mOnStateChangedListener.onStateChanged(state);
    }

    private void setError(int error) {
        if (mOnStateChangedListener != null)
            mOnStateChangedListener.onError(error);
    }

    public void setStoragePath(String path) {
        mStoragePath = path;
    }

    public File createTempFile(Context context, String prefix, String suffix, File directory)
            throws IOException {
        // Force a prefix null check first
        if (prefix.length() < 3) {
            throw new IllegalArgumentException("prefix must be at least 3 characters");
        }
        if (suffix == null) {
            suffix = ".tmp";
        }
        File tmpDirFile = directory;
        if (tmpDirFile == null) {
            String tmpDir = System.getProperty("java.io.tmpdir", ".");
            tmpDirFile = new File(tmpDir);
        }

        String nameFormat = context.getResources().getString(R.string.def_save_name_format);
        SimpleDateFormat df = new SimpleDateFormat(nameFormat);
        String currentTime = df.format(System.currentTimeMillis());
        if (!TextUtils.isEmpty(currentTime)) {
            currentTime = currentTime.replaceAll("[\\\\*|\":<>/?]", "_").replaceAll(" ",
                    "\\\\" + " ");
        }
        mTime = currentTime;

        File result;
        do {
            result = new File(tmpDirFile, prefix + currentTime + suffix);
        } while (!result.createNewFile());
        return result;
    }
}
