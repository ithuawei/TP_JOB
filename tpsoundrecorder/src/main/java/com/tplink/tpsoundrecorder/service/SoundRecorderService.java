/************************************************************
 * Copyright (C), 1996-2015, TP-LINK TECHNOLOGIES CO., LTD.
 * <p/>
 * File name: SoundRecorderService.java
 * <p/>
 * Description:
 * <p/>
 * Author: ZhangYi
 * <p/>
 * History:
 * ----------------------------------
 * Version: 1.0, 2016年1月15日, ZhangYi   create file.
 ************************************************************/

package com.tplink.tpsoundrecorder.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.tplink.tpsoundrecorder.R;
import com.tplink.tpsoundrecorder.other.PlatformBuildHelper;
import com.tplink.tpsoundrecorder.other.RemainingTimeCalculator;
import com.tplink.tpsoundrecorder.recorder.Recorder;
import com.tplink.tpsoundrecorder.activity.SoundRecorder;
import com.tplink.tpsoundrecorder.util.AndroidUtil;

import java.io.File;

/**
 * @author Zhangyi
 */
public class SoundRecorderService extends Service {
    public static final String ACTION_RECORDER_INIT_FINISH = "action_recorder_init_finish";

    public static final String ACTION_RECORDER_SAVE_FINISH = "action_recorder_save_finish";

    public final static String EXTRA_ACTION_NAME = "extra_action_name";

    public final static String EXTRA_MAX_FILE_SIZE = "extra_max_file_size";

    public final static String EXTRA_REQUEST_TYPE = "extra_request_type";

    public final static String EXTRA_PATH_TYPE = "extra_path_type";

    public final static String EXTRA_RECORDING_URI = "extra_recording_uri";

    public final static String EXTRA_BACKGROUND_FLAG = "extra_background_flag";

    public final static int ACTION_INVALID = 0;

    public final static int ACTION_INIT = ACTION_INVALID + 1;

    public final static int ACTION_START_RECORDING = ACTION_INIT + 1;

    public final static int ACTION_PAUSE_RECORDING = ACTION_START_RECORDING + 1;

    public final static int ACTION_RESUME_RECORDING = ACTION_PAUSE_RECORDING + 1;

    public final static int ACTION_STOP_RECORDING = ACTION_RESUME_RECORDING + 1;

    public static final int ACTION_SAVE_RECORDING = ACTION_STOP_RECORDING + 1;

    public static final int ACTION_DELETE_RECORDING = ACTION_SAVE_RECORDING + 1;

    public static final int ACTION_PLAY_RECORDING = ACTION_DELETE_RECORDING + 1;

    public static final int ACTION_UPDATE_BACKGROUND_STATE = ACTION_PLAY_RECORDING + 1;

    public static final String FOLDER_NAME = "SoundRecorder";

    public static final String CALL_RECORD_FOLDER_NAME = "CallRecord";

    public final static int PATH_TYPE_LOCAL = 0;

    public final static int PATH_TYPE_SD = 1;

    public static final String AUDIO_AMR = "audio/amr";

    public static final String AUDIO_AAC_MP4 = "audio/aac_mp4";

    public static final String AUDIO_WAVE_6CH_LPCM = "audio/wave_6ch_lpcm";

    public static final String AUDIO_WAVE_2CH_LPCM = "audio/wave_2ch_lpcm";

    private static final int NOTIFICATION_ID = 0;

    private static final int BITRATE_AMR = 12800; // bits/sec

    private static final int BITRATE_3GPP = 128000;

    private static final int SAMPLERATE_MULTI_CH = 48000;

    private static final int SAMPLERATE_8000 = 8000;

    private static final String TAG = "SoundRecorderService";

    private static final int FOCUSCHANGE = 0;

    private static final int STOP_AND_SAVE_RECORD = 1;

    private static final int TIME_DELAY_REMAINING_TIME_CALCULATOR = 1000;

    public static Recorder mRecorder;

    private long mMaxFileSize;

    private RemainingTimeCalculator mRemainingTimeCalculator;

    private String mRequestType;

    private static final String ACTION_CAMERA_RECORDING = "com.tplink.camerarecording";

    /**
     * 是否是后台录制
     */
    private boolean mIsBackground = false;

    private Handler mHandler = new Handler();

    private BroadcastReceiver mPowerOffReceiver = null;

    private TelephonyManager mTelephonyManager;
    private PhoneStateListener mPhoneStateListener;

    @Override
    public void onCreate() {
        super.onCreate();
        mRecorder = new Recorder(this);
        mRemainingTimeCalculator = new RemainingTimeCalculator(this);

        // 关机时自动保存录音
        registerPowerOffListener();

        // 注册电话状态监听器
        registerTelephonyListener();

        // 注册相机开始录像广播监听，收到录像开始广播，则停止录音并保存录音文件，否则相机无法使用麦克风
        registerCameraRecordingBroadcast();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return super.onStartCommand(intent, flags, startId);
        }

        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey(EXTRA_ACTION_NAME)) {
            switch (bundle.getInt(EXTRA_ACTION_NAME, ACTION_INVALID)) {
                case ACTION_INIT: {
                    if (mRecorder == null) {
                        mRecorder = new Recorder(this);
                        mRemainingTimeCalculator = new RemainingTimeCalculator(this);
                    }

                    mMaxFileSize = bundle.getLong(EXTRA_MAX_FILE_SIZE, -1);

                    // 发出广播，通知Recorder已经初始化完成
                    sendBroadcast(new Intent(ACTION_RECORDER_INIT_FINISH));
                    break;
                }

                case ACTION_START_RECORDING: {
                    String requestType = bundle.getString(EXTRA_REQUEST_TYPE, AUDIO_AAC_MP4);
                    int pathType = bundle.getInt(EXTRA_PATH_TYPE, PATH_TYPE_LOCAL);
                    startRecording(requestType, pathType);
                    break;
                }

                case ACTION_PAUSE_RECORDING: {
                    mRecorder.pauseRecording();
                    break;
                }

                case ACTION_RESUME_RECORDING: {
                    stopAudioPlayback();
                    mRecorder.resumeRecording();
                    break;
                }

                case ACTION_STOP_RECORDING: {
                    mRecorder.stop();
                    break;
                }

                case ACTION_SAVE_RECORDING: {
                    saveSoundRecord();
                    break;
                }

                case ACTION_DELETE_RECORDING: {
                    // stopAudioPlayback();
                    mRecorder.delete();
                    break;
                }

                case ACTION_PLAY_RECORDING: {
                    stopAudioPlayback();
                    mRecorder.startPlayback();
                    break;
                }

                case ACTION_UPDATE_BACKGROUND_STATE: {
                    mIsBackground = bundle.getBoolean(EXTRA_BACKGROUND_FLAG, false);

                    if (mIsBackground) {
                        if (mRecorder.state() == Recorder.RECORDING_STATE) {
                            // 在通知栏发送通知，提示正在录音
                            sendNotification(getString(R.string.recording), true);
                        } else if (mRecorder.state() == Recorder.PAUSE_STATE) {
                            // 在通知栏发送通知，提示暂停录音
                            sendNotification(getString(R.string.recording_paused), true);
                        }
                    } else {
                        // 清除状态栏通知
                        NotificationManager notificationManager = (NotificationManager) getSystemService(
                                NOTIFICATION_SERVICE);
                        notificationManager.cancel(NOTIFICATION_ID);
                    }
                    break;
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    private Runnable mUpdateTimeRemainingRunnable = new Runnable() {

        @Override
        public void run() {
            updateTimeRemaining();
        }
    };

    /*
     * Make sure we're not recording music playing in the background, ask the
     * MediaPlaybackService to pause playback.
     */
    private void stopAudioPlayback() {
        // 获取声音焦点，若在播放音乐，则暂停
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
    }

    //对声音焦点的改变进行监听。
    private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            mRecorderHandler.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget();
            Log.i(TAG, "onAudioFocusChange:" + focusChange);
        }
    };

    /**
     * Called when we're in recording state. Find out how much longer we can go
     * on recording. If it's under 5 minutes, we display a count-down in the UI.
     * If we've run out of time, stop the recording.
     */
    //能否继续录制/剩余的监听
    private void updateTimeRemaining() {
        //还能录制多久的时间
        long t = mRemainingTimeCalculator.timeRemaining();
        //如果小于0,判断是内存满了还是/文件大小限制，并且停止录制。
        if (t <= 0) {
            int limit = mRemainingTimeCalculator.currentLowerLimit();
            switch (limit) {
                case RemainingTimeCalculator.DISK_SPACE_LIMIT:
                    Log.e(TAG, "storage is full");
                    break;
                case RemainingTimeCalculator.FILE_SIZE_LIMIT:
                    Log.e(TAG, "max length reached");
                    break;
            }

            mRecorder.stop();
            return;
        }
        //如果正在播放，那么继续这个方法，每秒1次。
        if (mRecorder.state() == Recorder.RECORDING_STATE) {
            mHandler.postDelayed(mUpdateTimeRemainingRunnable,
                    TIME_DELAY_REMAINING_TIME_CALCULATOR);
        }
    }

    @SuppressLint("HandlerLeak")
    private Handler mRecorderHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                //焦点改变，就根据焦点进行保存或者停止后台的声音。
                case FOCUSCHANGE:
                    switch (msg.arg1) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                            //如果正在录制，就保存录音。
                            if (mRecorder.state() == Recorder.RECORDING_STATE) {
                                saveSoundRecord();
                            } else if (mRecorder.state() == Recorder.PLAYING_STATE) {
                                //如果是正在播放就停止后台的声音。
                                mRecorder.stopPlayback();
                            }
                            break;
                    }
                    break;
                //保存音频
                case STOP_AND_SAVE_RECORD: {
                    saveSoundRecord();
                }
                break;
                default:
                    break;
            }
        }
    };

    /**
     * 开始录音
     */
    public void startRecording(String requestedType, int pathType) {
        mRemainingTimeCalculator.reset();
        mRemainingTimeCalculator.setStoragePath(pathType);
        if (pathType == PATH_TYPE_LOCAL) {
            mRecorder
                    .setStoragePath(AndroidUtil.getPhoneLocalStoragePath(this) + "/" + FOLDER_NAME);
        } else {
            mRecorder.setStoragePath(AndroidUtil.getSDPath(this) + "/" + FOLDER_NAME);
        }

        // 开始录音，关闭其他音频播放
        stopAudioPlayback();

        mRequestType = requestedType;
        if (AUDIO_AMR.equals(requestedType)) {
            mRemainingTimeCalculator.setBitRate(BITRATE_AMR);
            mRecorder.setChannels(1);
            mRecorder.setSamplingRate(SAMPLERATE_8000);
            mRecorder.startRecording(MediaRecorder.OutputFormat.AMR_WB, ".amr", this,
                    MediaRecorder.AudioSource.MIC, MediaRecorder.AudioEncoder.AMR_WB);
        } else if (AUDIO_AAC_MP4.equals(requestedType)) {
//            mRemainingTimeCalculator.setBitRate(BITRATE_3GPP);
//            mRecorder.setChannels(1);
//            mRecorder.setSamplingRate(SAMPLERATE_MULTI_CH);
//            mRecorder.startRecording(MediaRecorder.OutputFormat.MPEG_4, ".aac", this,
//                    MediaRecorder.AudioSource.MIC, MediaRecorder.AudioEncoder.HE_AAC);
            mRemainingTimeCalculator.setBitRate(BITRATE_AMR);
            mRecorder.setChannels(1);
            mRecorder.setSamplingRate(SAMPLERATE_8000);
            mRecorder.startRecording(MediaRecorder.OutputFormat.AMR_WB, ".amr", this,
                    MediaRecorder.AudioSource.MIC, MediaRecorder.AudioEncoder.AMR_WB);
        } else if (AUDIO_WAVE_6CH_LPCM.equals(requestedType)) {// WAVE LPCM
            // 6-channel
            // recording
            mRemainingTimeCalculator.setBitRate(BITRATE_3GPP);
            mRecorder.setChannels(6);
            mRecorder.setSamplingRate(SAMPLERATE_MULTI_CH);
            mRecorder.startRecording(PlatformBuildHelper.getOutputFormatWAV(), ".wav", this,
                    MediaRecorder.AudioSource.MIC, PlatformBuildHelper.getAudioEncoderPCM());
        } else if (AUDIO_WAVE_2CH_LPCM.equals(requestedType)) {
            mRemainingTimeCalculator.setBitRate(BITRATE_3GPP);
            // 改为单声道
            mRecorder.setChannels(1);
            mRecorder.setSamplingRate(SAMPLERATE_MULTI_CH);
            mRecorder.startRecording(PlatformBuildHelper.getOutputFormatWAV(), ".wav", this,
                    MediaRecorder.AudioSource.MIC, PlatformBuildHelper.getAudioEncoderPCM());
        } else {
            throw new IllegalArgumentException("Invalid output file type requested");
        }

        // 若不为-1,则会对音频文件大小的最大值进行限定
        if (mMaxFileSize != -1) {
            mRemainingTimeCalculator.setFileSizeLimit(mRecorder.sampleFile(), mMaxFileSize);
        }

        // 没有存储空间
        if (!mRemainingTimeCalculator.diskSpaceAvailable()) {
            Log.e(TAG, "storage is full");
        }
    }

    public Recorder getRecorder() {
        return mRecorder;
    }

    private boolean saveSoundRecord() {
        Uri uri = null;

        // 保存之前必须stop，否则无法保存成功
        if (mRecorder.state() != Recorder.IDLE_STATE) {
            mRecorder.stop();
        }

        if (mRecorder.sampleLength() <= 0) {
            mRecorder.delete();
            return false;
        }

        try {
            uri = this.addToMediaDB(mRecorder.sampleFile());
            // 通知录音已经保存
            Intent intent = new Intent(ACTION_RECORDER_SAVE_FINISH);
            intent.putExtra(EXTRA_RECORDING_URI, uri);
            sendBroadcast(intent);

        } catch (UnsupportedOperationException ex) { // Database manipulation
            // failure
            return false;
        } finally {
            if (uri == null) {
                return false;
            }
        }
        // reset mRecorder and restore UI.
        mRecorder.clear();
        mHandler.removeCallbacks(mUpdateTimeRemainingRunnable);

        // 提示已经停止并保存
        if (mIsBackground) {
            sendNotification(getString(R.string.notification_auto_save), false);

            // 不停止service以免造成界面无法正常刷新的问题
            // stopSelf();
        }

        return true;
    }

    /*
     * Adds file and returns content uri.
     */
    private Uri addToMediaDB(File file) {
        Resources res = getResources();
        ContentValues cv = new ContentValues();
        long current = System.currentTimeMillis();
        long modDate = file.lastModified();
        String title = mRecorder.getStartRecordingTime();
        long sampleLengthMillis = mRecorder.sampleLength() * 1000L;

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        final String[] ids = new String[]{
                MediaStore.Audio.Playlists._ID
        };
        final String where = MediaStore.Audio.Playlists.DATA + "=?";
        final String[] args = new String[]{
                file.getAbsolutePath()
        };
        Cursor cursor = query(uri, ids, where, args, null);

        if (cursor != null && cursor.getCount() > 0) {
            cursor.close();
            return null;
        }

        // Label the recorded audio file as MUSIC so that the file
        // will be displayed automatically
        cv.put(MediaStore.Audio.Media.IS_MUSIC, "1");

        cv.put(MediaStore.Audio.Media.TITLE, title);
        cv.put(MediaStore.Audio.Media.DATA, file.getAbsolutePath());
        cv.put(MediaStore.Audio.Media.DATE_ADDED, (int) (current / 1000));
        cv.put(MediaStore.Audio.Media.DATE_MODIFIED, (int) (modDate / 1000));
        cv.put(MediaStore.Audio.Media.DURATION, sampleLengthMillis);
        cv.put(MediaStore.Audio.Media.MIME_TYPE, mRequestType);
        cv.put(MediaStore.Audio.Media.ARTIST, res.getString(R.string.audio_db_artist_name));
        cv.put(MediaStore.Audio.Media.ALBUM, "Audio recordings");
        Log.d(TAG, "Inserting audio record: " + cv.toString());
        ContentResolver resolver = getContentResolver();
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Log.d(TAG, "ContentURI: " + base);
        Uri result;
        try {
            result = resolver.insert(base, cv);
        } catch (Exception exception) {
            result = null;
        }
        if (result == null) {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show();
            return null;
        }
        if (getPlaylistId(res) == -1) {
            createPlaylist(res, resolver);
        }
        int audioId = Integer.valueOf(result.getLastPathSegment());
        addToPlaylist(resolver, audioId, getPlaylistId(res));

        // Notify those applications such as Music listening to the
        // scanner events that a recorded audio file just created.
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, result));
        return result;
    }

    /*
     * A simple utility to do a query into the databases.
     */
    private Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                         String sortOrder) {
        try {
            ContentResolver resolver = getContentResolver();
            if (resolver == null) {
                return null;
            }
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
        } catch (UnsupportedOperationException ex) {
            return null;
        }
    }

    /*
     * Add the given audioId to the playlist with the given playlistId; and
     * maintain the play_order in the playlist.
     */
    private void addToPlaylist(ContentResolver resolver, int audioId, long playlistId) {
        String[] cols = new String[]{
                "count(*)"
        };
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        Cursor cur = resolver.query(uri, cols, null, null, null);
        //来到首位
        cur.moveToFirst();
        final int base = cur.getInt(0);
        cur.close();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, Integer.valueOf(base + audioId));
        values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, audioId);
        resolver.insert(uri, values);
    }

    /*
     * Obtain the id for the default play list from the audio_playlists table.
     */
    private int getPlaylistId(Resources res) {

        Cursor cursor = query(
                MediaStore.Audio.Playlists.getContentUri("external"),   //uri
                new String[]{MediaStore.Audio.Playlists._ID},           //projection
                MediaStore.Audio.Playlists.NAME + "=?",                 //selection
                new String[]{"My recordings"},                          //selectionArgs
                null);                                                  //sortOrder
        if (cursor == null) {
            Log.v(TAG, "query returns null");
        }
        int id = -1;
        if (cursor != null) {
            //来到第一位
            cursor.moveToFirst();
            //判断当前指针是否已经到文件尾
            if (!cursor.isAfterLast()) {
                id = cursor.getInt(0);
            }
        }
        cursor.close();
        return id;
    }

    /*
     * Create a playlist with the given default playlist name, if no such
     * playlist exists.
     */
    private Uri createPlaylist(Resources res, ContentResolver resolver) {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Audio.Playlists.NAME, "My recordings");
        Uri uri = resolver.insert(MediaStore.Audio.Playlists.getContentUri("external"), cv);
        return uri;
    }

    /**
     * 服务销毁：
     * 1.录制器清空
     * 2.移除录制剩余时间提醒
     * 3.移除电池电量监听
     * 4.移除电话通话状态的监听
     * 5.取消相机状态的广播监听
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mRecorder.clear();
        mHandler.removeCallbacks(mUpdateTimeRemainingRunnable);

        if (mPowerOffReceiver != null) {
            unregisterReceiver(mPowerOffReceiver);
            mPowerOffReceiver = null;
        }
        // Stop listening for phone state changes.
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);//None

        unregisterCameraRecordingBroadcast();
    }

    /**
     * 直接最近任务关闭，会调用onTaskRemoved方法。
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // TODO Auto-generated method stub
        super.onTaskRemoved(rootIntent);
        mIsBackground = true;
        saveSoundRecord();
    }

    /**
     * Registers an intent to listen for ACTION_SHUTDOWN notifications.
     * 关机广播的监听-保存文件
     */
    private void registerPowerOffListener() {
        //如果广播对象还是空就创建广播，广播如果接收到关机动作，就保存文件。
        if (mPowerOffReceiver == null) {
            mPowerOffReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_SHUTDOWN)) {
                        saveSoundRecord();
                    }
                }
            };
            //注册
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_SHUTDOWN);
            registerReceiver(mPowerOffReceiver, iFilter);
        }
    }

    /**
     * 对电话通话进行监听-电话响铃（来电），抬起电话（接听）
     */
    private void registerTelephonyListener() {
        //电话管理+监听
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mPhoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String ignored) {

                switch (state) {
                    case TelephonyManager.CALL_STATE_RINGING:   //来电
                    case TelephonyManager.CALL_STATE_OFFHOOK:   //接听
                        // 若录音正在进行则录音自动保存
                        if (mRecorder.state() == Recorder.RECORDING_STATE) {
                            saveSoundRecord();
                        }
                        // MediaPlayer正在“试听”录音，则暂停
                        if (mRecorder.state() == Recorder.PLAYING_STATE) {
                            mRecorder.stopPlayback();
                        }
                        break;
                }
            }
        };
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);//状态
    }

    /**
     * 改变通知栏信息显示方法：录音中/暂停中/已经保存
     *
     * @param contentText
     * @param isOnGoing
     */
    private void sendNotification(String contentText, boolean isOnGoing) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(
                NOTIFICATION_SERVICE);
        Notification.Builder builder = new Notification.Builder(this);

        // 设置通知栏标题
        builder.setContentTitle(getString(R.string.app_name))
                // 设置通知栏显示内容
                .setContentText(contentText)
                // 通知首次出现在通知栏，带上升动画效果的
                .setTicker(contentText)
                // 通知产生的时间，会在通知信息里显示，一般是系统获取到的时间
                .setWhen(System.currentTimeMillis())
                // 设置该通知优先级
                .setPriority(Notification.PRIORITY_DEFAULT)
                // ture 表示后台任务
                .setOngoing(isOnGoing)
                // 点击跳转
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, SoundRecorder.class), Notification.FLAG_AUTO_CANCEL))
                // 设置通知小ICON
                .setSmallIcon(R.drawable.ic_notification);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * 使用相机的广播,收到个指令，发送给Handlder处理------------------------------
     */
    //注册使用相机广播
    private void registerCameraRecordingBroadcast() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_CAMERA_RECORDING);
        registerReceiver(myBroadcastReceiver, intentFilter);
    }

    //注销相机广播
    private void unregisterCameraRecordingBroadcast() {
        if (myBroadcastReceiver != null) {
            unregisterReceiver(myBroadcastReceiver);
        }
    }

    //定义广播，获取收到的后台保存的广播
    private BroadcastReceiver myBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // save record file
            mRecorderHandler.obtainMessage(STOP_AND_SAVE_RECORD).sendToTarget();
        }
    };
}
