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

package com.tplink.tpsoundrecorder.activity;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.tplink.tpsoundrecorder.R;
import com.tplink.tpsoundrecorder.other.SystemProperties;
import com.tplink.tpsoundrecorder.recorder.Recorder;
import com.tplink.tpsoundrecorder.service.SoundRecorderService;
import com.tplink.tpsoundrecorder.util.AndroidUtil;
import com.tplink.tpsoundrecorder.util.SystemPropertiesInvokeUtil;
import com.tplink.tpsoundrecorder.view.WaveHelper;
import com.tplink.tpsoundrecorder.view.WaveView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SoundRecorder extends Activity
        implements Button.OnClickListener, Recorder.OnStateChangedListener {
    /**
     * 开始录制时，旋转动画一个周期旋转的度数
     */
    private static final int ROTATE_DEGREES = 360;

    static final String TAG = "SoundRecorder";

    private static final String EXIT_AFTER_RECORD = "exit_after_record";

    static final int SETTING_TYPE_STORAGE_LOCATION = 0;

    static final int SETTING_TYPE_FILE_TYPE = 1;

    private AudioManager mAudioManager;

    private boolean mRecorderStop;

    private boolean mRecorderProcessed;

    private boolean mDataExist;

    private boolean mWAVSupport = true;

    private String timeCurremtMs;

    /**
     * 如果通过其他应用（如在彩信中添加录音附件）调用录音机，则录音结束后，会结束录音机应用
     */
    private boolean mExitAfterRecord;

    String mRequestedType = SoundRecorderService.AUDIO_AMR; //  "audio/amr"

    boolean mSampleInterrupted;

    static boolean bSSRSupported;

    String mErrorUiMessage = null; // Some error messages are displayed in the
    // UI,
    // not a dialog. This happens when a
    // recording
    // is interrupted for some reason.

    String mTimerFormat;

    final Handler mHandler = new Handler();

    Runnable mUpdateTimer = new Runnable() {
        public void run() {
            float time_ms = SoundRecorderService.mRecorder.sampleLength_ms();
            timeCurremtMs = "" + time_ms;
            char s = timeCurremtMs.charAt(timeCurremtMs.indexOf(".") + 1);
            timeCurremtMs = "" + s;
            updateTimerView();
        }
    };

    AlertDialog mSaveDialog;    //是否悬着保存

    ProgressBar mStateProgressBar;  //试听进度条

    TextView mPlayCurrentTimeTv;    //试听里变动的时间

    TextView mPlayFullTimeTv;       //试听里右侧的全部时间

    Toolbar mToolbar;   //上方的Toobar

    ImageView mMoreIv;  //Toobar里的菜单键

    ImageButton mRecordButton;

    ImageButton mStopButton;

    ImageButton mFlagButton;

    TextView mStateMessage;

    TextView mTimerView;

    TextView mFlagView;

    ObjectAnimator mRotateAnimator;

    View mRotateView;

    WaveView mWaveView;

    WaveHelper mWaveHelper;

    private BroadcastReceiver mSDCardMountEventReceiver = null;

    private int mFileType = 0;

    private int mPath = 0;

    private SharedPreferences mSharedPreferences;

    private Editor mPrefsStoragePathEditor;

    private int mRecorderState = Recorder.INVALID_STATE;

    /**
     * 使用返回键退出应用，则该值为true，在onDestroy中会用该值进行判断。 如果是back键导致的finish，则录音继续；
     * 如果是使用最近任务关闭该应用，则停止录音，并自动保存；
     */
    private boolean mIsBackFinish;

    /**
     * The Constant REQUEST_CODE_ASK_PERMISSIONS.
     */
    private static final int REQUEST_CODE_ASK_PERMISSIONS = 123;

    private static final String country_code = SystemProperties.get("persist.radio.countrycode");

    private boolean isSupportCountry;

    @Override
    public void onCreate(Bundle icycle) {
        super.onCreate(icycle);
        Toast.makeText(this, "onCreate", Toast.LENGTH_SHORT).show();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        mSharedPreferences = getSharedPreferences("storage_Path", Context.MODE_PRIVATE);
        mPrefsStoragePathEditor = mSharedPreferences.edit();

        Intent i = getIntent();
        long maxFileSize = -1;
        if (i != null) {
            String s = i.getType();
            if (SoundRecorderService.AUDIO_AMR.equals(s)) {
                mRequestedType = s;
                mWAVSupport = false;
            } else if (s != null) {
                // we only support amr and 3gpp formats right now
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            final String EXTRA_MAX_BYTES = MediaStore.Audio.Media.EXTRA_MAX_BYTES;
            maxFileSize = i.getLongExtra(EXTRA_MAX_BYTES, -1);

            mExitAfterRecord = i.getBooleanExtra(EXIT_AFTER_RECORD, false);
        }

        mPath = mSharedPreferences.getInt("path", mPath);
        if (!mExitAfterRecord) {
            // Don't reload cached encoding type,if it's assigned by external
            // intent.
            mRequestedType = mSharedPreferences.getString("requestedType",
                    getResources().getString(R.string.def_save_mimetype));
        }
        mFileType = mSharedPreferences.getInt("fileType",
                getResources().getInteger(R.integer.def_save_type));
        if (!mWAVSupport && mRequestedType == SoundRecorderService.AUDIO_WAVE_2CH_LPCM) {
            mRequestedType = SoundRecorderService.AUDIO_AMR;
            mFileType = 0;
        }
        //还要设置偏移，否则状态栏和内容重叠
        View view = View.inflate(this, R.layout.main, null);
        RelativeLayout rl = (RelativeLayout) view.findViewById(R.id.rl_main);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        LinearLayout content = new LinearLayout(this);
        content.addView(rl, lp);
        setContentView(content);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        initResourceRefs();
        mRecorderStop = false;
        mRecorderProcessed = false;
        mDataExist = false;

        Bundle bundle = new Bundle();
        bundle.putLong(SoundRecorderService.EXTRA_MAX_FILE_SIZE, maxFileSize);
        bundle.putInt(SoundRecorderService.EXTRA_ACTION_NAME, SoundRecorderService.ACTION_INIT);
        sendCommandToService(bundle);

        checkPermission();

        /**
         检查是否支持通话录音，根据国家码
         check if support call recording with country code
         *
         */
        isSupportCountry = AndroidUtil.isSupportCallRecording(country_code);
    }

    /**
     * Check permission.
     * 检查权限-录音，写入外部卡，如过被禁了就提示获取
     *
     * @return true, if successful
     */
    private boolean checkPermission() {
        /**
         * return 0——PERMISSION_GRANTED，1——PERMISSION_DENIED
         **/
        if ((checkSelfPermission(
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED)
                || (checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED)) {

            if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                    && !shouldShowRequestPermissionRationale(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                //
                requestPermissions(new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO
                }, REQUEST_CODE_ASK_PERMISSIONS);
            } else {
                /** 没有被用户永久禁止弹出,则弹出窗口请求权限 */
                requestPermissions(new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO
                }, REQUEST_CODE_ASK_PERMISSIONS);
            }
        } else {
            /** 具有权限 */
            return true;
        }
        return false;
    }

    private void sendCommandToService(Bundle bundle) {
        Intent intent = new Intent(this, SoundRecorderService.class);
        intent.putExtras(bundle);
        startService(intent);
    }

    private void sendCommandToService(int actionCommand) {
        Intent intent = new Intent(this, SoundRecorderService.class);
        intent.putExtra(SoundRecorderService.EXTRA_ACTION_NAME, actionCommand);
        startService(intent);
    }

    /**
     * Service启动后，实例化成功Recorder收到一个广播
     * 1.录音已经准备好
     * 2.录音已经保存完成-保存失败，保存成功
     */
    private BroadcastReceiver mRecorderBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SoundRecorderService.ACTION_RECORDER_INIT_FINISH)) {
                onRecorderPrepareFinished();
            } else if (intent.getAction()
                    .equals(SoundRecorderService.ACTION_RECORDER_SAVE_FINISH)) {
                Uri uri = intent.getParcelableExtra(SoundRecorderService.EXTRA_RECORDING_URI);

                if (uri == null) {
                    Toast.makeText(SoundRecorder.this, R.string.toast_save_failed,
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SoundRecorder.this, R.string.toast_save_successfully,
                            Toast.LENGTH_SHORT).show();
                }
                //如果是在后台，返回result，并并关闭当前窗口
                if (mExitAfterRecord && uri != null) {
                    SoundRecorder.this.setResult(RESULT_OK, new Intent().setData(uri));
                    SoundRecorder.this.finish();
                }
            }
        }
    };

    protected void onRecorderPrepareFinished() {
        if (mWaveView != null) {
            //view获取播放对象，即可根据播放的属性进行对应的调整
            mWaveView.setRecorder(SoundRecorderService.mRecorder);
        }
        //监听播放器的改变状态。
        SoundRecorderService.mRecorder.setOnStateChangedListener(SoundRecorder.this);
        //组测外部卡的状态
        registerExternalStorageListener();
//        String ssrRet = SystemProperties.get("ro.qc.sdk.audio.ssr", "false");
//        if (ssrRet.contains("true")) {
//            Log.d(TAG, "Surround sound recording is supported");
//            bSSRSupported = true;
//        } else {
//            Log.d(TAG, "Surround sound recording is not supported");
//            bSSRSupported = false;
//        }
        /**-----------------------------------------------------------*/
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getDeclaredMethod("get", String.class, String.class);
            String ssrRet = (String) get.invoke(systemProperties, "ro.qc.sdk.audio.ssr", "false");

            if (ssrRet.contains("true")) {
                Log.d(TAG, "Surround sound recording is supported");
                bSSRSupported = true;
            } else {
                Log.d(TAG, "Surround sound recording is not supported");
                bSSRSupported = false;
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        /**-----------------------------------------------------------*/

        Log.i(TAG, "mRecorder.state = " + mRecorderState);

        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        setContentView(R.layout.main);
        initResourceRefs();
        updateUi();
    }

    /*
     * Whenever the UI is re-created (due f.ex. to orientation change) we have
     * to reinitialize references to the views.
     */
    private void initResourceRefs() {
        /**
         * 隐藏标题栏
         */
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbar.setTitle("");
        mToolbar.setBackgroundColor(Color.TRANSPARENT);
        setActionBar(mToolbar);

        // 因为toolbar右边按钮的颜色无法改变，所以只能在它的上面覆盖一个显示我们需要图片的控件
        mMoreIv = (ImageView) findViewById(R.id.iv_more);

        mRecordButton = (ImageButton) findViewById(R.id.ib_record);
        mStopButton = (ImageButton) findViewById(R.id.ib_stop);
        mFlagButton = (ImageButton) findViewById(R.id.bt_flag_time);

        mStateMessage = (TextView) findViewById(R.id.tv_state);
        mTimerView = (TextView) findViewById(R.id.tv_timer);
        mFlagView = (TextView) findViewById(R.id.tv_flag);

        mRotateView = findViewById(R.id.iv_animation_bg);
        mRotateView.setAlpha(0);

        mWaveView = (WaveView) findViewById(R.id.vmv);
        mWaveHelper = new WaveHelper(mWaveView);

        mRecordButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mFlagButton.setOnClickListener(this);

        mTimerFormat = getResources().getString(R.string.timer_format);
    }

    private void startRotateAnimation() {
        mRotateView.animate().alpha(1).start();//无到有
        mRotateView.setRotation(0);
        mRotateAnimator = ObjectAnimator.ofFloat(mRotateView, "rotation", ROTATE_DEGREES);
        mRotateAnimator.setDuration(2000);
        mRotateAnimator.setRepeatCount(-1);
        mRotateAnimator.setInterpolator(new LinearInterpolator());
        mRotateAnimator.start();
    }

    private void stopRotateAnimation() {
        mRotateView.animate().alpha(0).setDuration(1000)//有到无
                //当前角度+360,相当于：转到哪，停止时都要再转一圈再消失。
                .rotation(mRotateView.getRotation() + ROTATE_DEGREES).withEndAction(new Runnable() {
            @Override
            public void run() {
                mRotateView.setRotation(0);
            }
        }).start();
    }

    private void startRecordingAnimator() {
        mWaveHelper.start();
        mWaveView.startAnimator();
        startRotateAnimation();
    }

    private void stopRecordingAnimator() {
        mWaveHelper.cancel();
        mWaveView.stopAnimator();
        stopRotateAnimation();
    }

    /*
     * Handle the buttons.
     */
    public void onClick(View button) {
        if (!button.isEnabled())
            return;

        switch (button.getId()) {
            case R.id.ib_record:
                //暂停状态，就恢复
                if (mRecorderState == Recorder.PAUSE_STATE) {
                    sendCommandToService(SoundRecorderService.ACTION_RESUME_RECORDING);
                    return;
                    //录制状态，就暂停
                } else if (mRecorderState == Recorder.RECORDING_STATE) {
                    sendCommandToService(SoundRecorderService.ACTION_PAUSE_RECORDING);
                    return;
                    //未启动状态，就进行录制
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putInt(SoundRecorderService.EXTRA_ACTION_NAME,
                            SoundRecorderService.ACTION_START_RECORDING);
                    bundle.putInt(SoundRecorderService.EXTRA_PATH_TYPE, mPath);
                    bundle.putString(SoundRecorderService.EXTRA_REQUEST_TYPE, mRequestedType);
                    sendCommandToService(bundle);

                    mRecorderStop = false;
                    mRecorderProcessed = false;
                }
                invalidateOptionsMenu();//刷新菜单（相当于：再次调用onCreateOptionsMenu方法）
                break;
            case R.id.ib_stop:
                // 如果录音时间大于1秒，就正常的停止和保存并通知
                if (SoundRecorderService.mRecorder.sampleLength() >= 1) {
                    sendCommandToService(SoundRecorderService.ACTION_STOP_RECORDING);
                    mRecorderStop = true;
                    String timeStr = String.format(mTimerFormat, 0, 0, 0);
                    mTimerView.setText(timeStr + ".0");
                    showSaveDialog();
                    invalidateOptionsMenu();
                    // 如果录音时间短于1秒，则弹出提示不可少于1秒。
                } else {
                    Toast.makeText(this,
                            R.string.toast_record_time_too_short, Toast.LENGTH_SHORT).show();
                }
                mFlagView.setVisibility(View.GONE);

                break;
            case R.id.bt_flag_time:
                if (mRecorderState == Recorder.RECORDING_STATE) {
                    mFlagView.setVisibility(View.VISIBLE);
                }
                //正在录制才能点击标记
                if (mRecorderState == Recorder.RECORDING_STATE) {
                    ObjectAnimator translationY = ObjectAnimator.ofFloat(mFlagView, "translationY", mFlagView.getHeight() * 2, 0);
                    ObjectAnimator alpha = ObjectAnimator.ofFloat(mFlagView, "alpha", 0.0f, 1.0f);
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(translationY, alpha);
                    animatorSet.start();
                    mFlagView.setText("- " +
                            SoundRecorderService.mRecorder.sampleLength() / 60 / 60 + ":" +
                            SoundRecorderService.mRecorder.sampleLength() / 60 % 60 + ":" +
                            SoundRecorderService.mRecorder.sampleLength() % 60 + "." +
                            timeCurremtMs + " -");
                }
                break;
        }
    }

    @SuppressLint("InflateParams")
    private void showSaveDialog() {
        View saveDialogContentView = LayoutInflater.from(this).inflate(R.layout.dialog_save, null);
        mSaveDialog = new Builder(this).setTitle(R.string.save_title)
                .setView(saveDialogContentView)
                .setPositiveButton(R.string.save_btn, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        mSampleInterrupted = false;
                        mRecorderProcessed = true;
                        sendCommandToService(SoundRecorderService.ACTION_SAVE_RECORDING);
                    }
                }).setNegativeButton(R.string.delete_btn, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        showDeleteConfirmDialog();
                    }
                }).setCancelable(false).show();

        ImageButton playButton = (ImageButton) saveDialogContentView.findViewById(R.id.ib_play);
        mStateProgressBar = (ProgressBar) saveDialogContentView.findViewById(R.id.pb);
        mPlayCurrentTimeTv = (TextView) saveDialogContentView
                .findViewById(R.id.tv_play_current_time);
        mPlayFullTimeTv = (TextView) saveDialogContentView.findViewById(R.id.tv_play_full_time);

        // 显示音频的时间区间
        mPlayCurrentTimeTv.setText(String.format(mTimerFormat, 0, 0, 0));
        mPlayFullTimeTv.setText(
                String.format(mTimerFormat, SoundRecorderService.mRecorder.sampleLength() / 60 / 60,
                        SoundRecorderService.mRecorder.sampleLength() / 60 % 60,
                        SoundRecorderService.mRecorder.sampleLength() % 60));

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SoundRecorderService.mRecorder.sampleFile() == null
                        || !SoundRecorderService.mRecorder.sampleFile().exists()) {
                    Toast.makeText(SoundRecorder.this, R.string.toast_paly_failed,
                            Toast.LENGTH_SHORT).show();
                    mSaveDialog.dismiss();
                }

                sendCommandToService(SoundRecorderService.ACTION_PLAY_RECORDING);
            }
        });
    }

    protected void showDeleteConfirmDialog() {
        new Builder(SoundRecorder.this, R.style.AlertDialogTheme)
                .setTitle(R.string.action_delete).setMessage(R.string.delete_confirm_message)
                .setPositiveButton(R.string.delete_btn, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        mSampleInterrupted = false;
                        mRecorderProcessed = true;
                        sendCommandToService(SoundRecorderService.ACTION_DELETE_RECORDING);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        showSaveDialog();
                    }
                }).setCancelable(false).show();
    }

    private void openOptionDialog(final int optionType) {
        final int[] typeInfo = new int[]{
                R.string.format_setting_amr_info, R.string.format_setting_wav_info
        };
        final int[] storageLocationResIds = new int[]{
                R.string.storage_setting_local_item, R.string.storage_setting_sdcard_item
        };

        String[] storageLocationStrs = null;
        if (AndroidUtil.getSDState(SoundRecorder.this) != null
                && AndroidUtil.getSDState(SoundRecorder.this).equals(Environment.MEDIA_MOUNTED)) {
            storageLocationStrs = new String[]{
                    getString(R.string.storage_setting_local_item),
                    getString(R.string.storage_setting_sdcard_item)
            };
        } else {
            storageLocationStrs = new String[]{
                    getString(R.string.storage_setting_local_item)
            };
        }

        final ArrayAdapter<Integer> fileTypeAdapter = new ArrayAdapter<Integer>(this,
                R.layout.item_file_type) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(SoundRecorder.this)
                            .inflate(R.layout.item_file_type, parent, false);
                }

                ((TextView) convertView.findViewById(R.id.tv_type_name)).setText(getItem(position));
                ((TextView) convertView.findViewById(R.id.tv_type_info)).setText(typeInfo[position]);
                ((RadioButton) convertView.findViewById(R.id.rb)).setChecked(position == mFileType);

                return convertView;
            }
        };

        final OnClickListener clickListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                int resId = 0;
                if (optionType == SETTING_TYPE_FILE_TYPE) {
                    resId = fileTypeAdapter.getItem(which);
                } else if (optionType == SETTING_TYPE_STORAGE_LOCATION) {
                    resId = storageLocationResIds[which];
                }
                switch (resId) {
                    /*
                     * 暂不支持 arm 和 3gpp 格式 case R.string.format_setting_amr_item:
                     * mRequestedType = AUDIO_AMR; mFileType = 0;
                     * mPrefsStoragePathEditor.putString("requestedType",
                     * mRequestedType);
                     * mPrefsStoragePathEditor.putInt("fileType", mFileType);
                     * mPrefsStoragePathEditor.commit(); break; case
                     * R.string.format_setting_3gpp_item: mRequestedType =
                     * AUDIO_3GPP; mFileType = 1;
                     * mPrefsStoragePathEditor.putString("requestedType",
                     * mRequestedType);
                     * mPrefsStoragePathEditor.putInt("fileType", mFileType);
                     * mPrefsStoragePathEditor.commit(); // Keep 40KB size in
                     * the Recording file for Mpeg4Writer // to write Moov. if
                     * ((mMaxFileSize != -1) && (mMaxFileSize > 40 * 1024))
                     * mMaxFileSize = mMaxFileSize - 40 * 1024; break;
                     */
                    case R.string.format_setting_amr_item:
                        mRequestedType = SoundRecorderService.AUDIO_AMR;
                        mFileType = 0;
                        mPrefsStoragePathEditor.putString("requestedType", mRequestedType);
                        mPrefsStoragePathEditor.putInt("fileType", mFileType);
                        mPrefsStoragePathEditor.commit();
                        break;
                    case R.string.format_setting_wav_item:
                        mRequestedType = SoundRecorderService.AUDIO_WAVE_2CH_LPCM;
                        mFileType = 1;
                        mPrefsStoragePathEditor.putString("requestedType", mRequestedType);
                        mPrefsStoragePathEditor.putInt("fileType", mFileType);
                        mPrefsStoragePathEditor.commit();
                        break;
                    case R.string.storage_setting_sdcard_item:
                        setStorePathToSD();
                        break;
                    case R.string.storage_setting_local_item:
                        setStorePathToLocal();
                        break;

                    default: {
                        Log.e(TAG, "Unexpected resource: "
                                + getResources().getResourceEntryName(resId));
                    }
                }
            }
        };

        AlertDialog ad = null;
        if (optionType == SETTING_TYPE_STORAGE_LOCATION) {
            ad = new Builder(this).setTitle(R.string.storage_setting)
                    .setSingleChoiceItems(storageLocationStrs, mPath, clickListener)
                    .setNegativeButton(android.R.string.cancel,
                            new OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                    .create();
        } else if (optionType == SETTING_TYPE_FILE_TYPE) {
            fileTypeAdapter.add(R.string.format_setting_amr_item);
            if (mWAVSupport) {
                fileTypeAdapter.add(R.string.format_setting_wav_item);
            }
            ad = new Builder(this).setTitle(R.string.format_setting)
                    .setSingleChoiceItems(fileTypeAdapter, mFileType, clickListener)
                    .setNegativeButton(android.R.string.cancel,
                            new OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                    .create();
        }
        ad.setCanceledOnTouchOutside(true);
        ad.show();
    }

    protected void setStorePathToLocal() {
        mPath = SoundRecorderService.PATH_TYPE_LOCAL;
        mPrefsStoragePathEditor.putInt("path", mPath);
        mPrefsStoragePathEditor.commit();
    }

    protected void setStorePathToSD() {
        if (AndroidUtil.getSDState(SoundRecorder.this).equals(Environment.MEDIA_MOUNTED)) {
            mPath = SoundRecorderService.PATH_TYPE_SD;
            mPrefsStoragePathEditor.putInt("path", mPath);
            mPrefsStoragePathEditor.commit();
        } else {
            Log.e(TAG, "please insert sd card");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO Auto-generated method stub
        if (mRecorderState == Recorder.IDLE_STATE) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.main_menu, menu);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        switch (item.getItemId()) {
            case R.id.menu_item_my_records:
                // 出货国家中可能会有部分国家不加入通话录音功能.
                Intent intent = null;
                if (isSupportCountry) {
                    // 有录音和通话录音的版本
                    intent = new Intent(SoundRecorder.this, SoundRecordActivity.class);
                } else {
                    // 无录音和通话录音Tab的版本
                    intent = new Intent(SoundRecorder.this, SoundListActivity.class);
                }
                SoundRecorder.this.startActivity(intent);

                break;
            case R.id.menu_item_filetype:
                if (mRecorderState == Recorder.IDLE_STATE) {
                    openOptionDialog(SETTING_TYPE_FILE_TYPE);
                }
                break;
            case R.id.menu_item_storage:
                if (mRecorderState == Recorder.IDLE_STATE) {
                    openOptionDialog(SETTING_TYPE_STORAGE_LOCATION);
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /*
     * Handle the "back" hardware key.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            switch (mRecorderState) {
                case Recorder.PAUSE_STATE:
                case Recorder.RECORDING_STATE:
                    if (mExitAfterRecord) {
                        sendCommandToService(SoundRecorderService.ACTION_STOP_RECORDING);
                        mRecorderStop = true;
                        String timeStr = String.format(mTimerFormat, 0, 0, 0);
                        mTimerView.setText(timeStr);
                        showSaveDialog();
                        invalidateOptionsMenu();
                    } else {
                        mIsBackFinish = true;
                        finish();
                    }
                    break;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter filter = new IntentFilter();
        filter.addAction(SoundRecorderService.ACTION_RECORDER_INIT_FINISH);
        filter.addAction(SoundRecorderService.ACTION_RECORDER_SAVE_FINISH);
        registerReceiver(mRecorderBroadcastReceiver, filter);

        // 通知service已经不是后台运行
        Bundle bundle = new Bundle();
        bundle.putInt(SoundRecorderService.EXTRA_ACTION_NAME,
                SoundRecorderService.ACTION_UPDATE_BACKGROUND_STATE);
        bundle.putBoolean(SoundRecorderService.EXTRA_BACKGROUND_FLAG, false);
        sendCommandToService(bundle);

        mIsBackFinish = false;
    }

    @Override
    public void onStop() {
        if (mRecorderBroadcastReceiver != null) {
            unregisterReceiver(mRecorderBroadcastReceiver);
        }

        // 通知service后台运行
        if (mRecorderState == Recorder.RECORDING_STATE || mRecorderState == Recorder.PAUSE_STATE) {
            Bundle bundle = new Bundle();
            bundle.putInt(SoundRecorderService.EXTRA_ACTION_NAME,
                    SoundRecorderService.ACTION_UPDATE_BACKGROUND_STATE);
            bundle.putBoolean(SoundRecorderService.EXTRA_BACKGROUND_FLAG, true);
            sendCommandToService(bundle);
        }

        super.onStop();
    }

    @Override
    protected void onPause() {
        mSampleInterrupted = mRecorderState == Recorder.RECORDING_STATE;
        super.onPause();
    }

    /*
     * Called on destroy to unregister the SD card mount event receiver.
     */
    @Override
    public void onDestroy() {
        if (mSDCardMountEventReceiver != null) {
            unregisterReceiver(mSDCardMountEventReceiver);
            mSDCardMountEventReceiver = null;
        }

        // 没有在录音，activity退出的时候，将service结束并结束此进程
        if (mRecorderState == Recorder.IDLE_STATE || mRecorderState == Recorder.INVALID_STATE) {
            stopService(new Intent(this, SoundRecorderService.class));
            android.os.Process.killProcess(android.os.Process.myPid());
        }

        // 被除了使用back键以外的方式将activity销毁
        if (!mIsBackFinish) {
            sendCommandToService(SoundRecorderService.ACTION_SAVE_RECORDING);
        }

        super.onDestroy();
    }

    /*
     * Registers an intent to listen for ACTION_MEDIA_EJECT/ACTION_MEDIA_MOUNTED
     * notifications.
     */
    private void registerExternalStorageListener() {
        if (mSDCardMountEventReceiver == null) {
            mSDCardMountEventReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        // SD卡拔出后，自动将保存位置改为内部存储
                        setStorePathToLocal();
                        sendCommandToService(SoundRecorderService.ACTION_DELETE_RECORDING);
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        mSampleInterrupted = false;
                        updateUi();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mSDCardMountEventReceiver, iFilter);
        }
    }

    /**
     * Update the big MM:SS timer. If we are in playback, also update the
     * progress bar.
     */
    private void updateTimerView() {
        Resources res = getResources();

        boolean ongoing = mRecorderState == Recorder.RECORDING_STATE
                || mRecorderState == Recorder.PLAYING_STATE;

        long time = ongoing ? SoundRecorderService.mRecorder.progress()
                : SoundRecorderService.mRecorder.sampleLength();
        String timeStr = String.format(mTimerFormat, time / 60 / 60, time / 60 % 60, time % 60);

        if (mRecorderState == Recorder.PLAYING_STATE && mStateProgressBar != null) {
            mStateProgressBar
                    .setProgress((int) (100 * time / SoundRecorderService.mRecorder.sampleLength()));
            mPlayCurrentTimeTv.setText(
                    String.format(mTimerFormat, time / 60 / 60, time / 60 % 60, time % 60));
            mPlayFullTimeTv.setText(String.format(mTimerFormat,
                    SoundRecorderService.mRecorder.sampleLength() / 60 / 60,
                    SoundRecorderService.mRecorder.sampleLength() / 60 % 60,
                    SoundRecorderService.mRecorder.sampleLength() % 60));
        } else if (mRecorderState == Recorder.RECORDING_STATE
                || mRecorderState == Recorder.PAUSE_STATE) {
            if (timeCurremtMs != null) {
                mTimerView.setText(timeStr + "." + timeCurremtMs);
            }
        }

        if (ongoing)
            mHandler.postDelayed(mUpdateTimer, 10);
    }

    /**
     * Shows/hides the appropriate child views for the new state.
     */
    private void updateUi() {
        Resources res = getResources();

        switch (mRecorderState) {
            case Recorder.IDLE_STATE:
                stopRecordingAnimator();

                if (SoundRecorderService.mRecorder.sampleLength() == 0) {
                    mRecordButton.setImageResource(R.drawable.ic_start_recording);
                    mRecordButton.setEnabled(true);
                    mRecordButton.setFocusable(false);
                    mStopButton.setEnabled(false);
                    mStopButton.setFocusable(false);
                    mMoreIv.setVisibility(View.VISIBLE);

                    mStateMessage.setVisibility(View.INVISIBLE);
                    String timeStr = String.format(mTimerFormat, 0, 0, 0);
                    mTimerView.setText(timeStr + ".0");
                    if (true == bSSRSupported) {
                        // mStateMessage2.setText(res.getString(R.string.press_record_ssr));
                    } else {
//                        if (SystemProperties.getBoolean("debug.soundrecorder.enable", false)) {
                        if (SystemPropertiesInvokeUtil.getBoolean("debug.soundrecorder.enable", false)) {
                            // mStateMessage2.setText(res.getString(R.string.press_record));
                        } else {
                            // mStateMessage2.setText(res.getString(R.string.press_record2));
                        }
                    }

                } else {
                    mRecordButton.setImageResource(R.drawable.ic_start_recording);
                    mRecordButton.setEnabled(true);
                    mRecordButton.setFocusable(true);
                    mStopButton.setEnabled(false);
                    mStopButton.setFocusable(false);

                    mStateMessage.setVisibility(View.VISIBLE);
                    mStateMessage.setText(res.getString(R.string.recording_stopped));
                    // 音频录制停止或者暂停后才能获得音频的时长，所以显示保存对话框后，还需要更新一下UI，显示音频的时长
                    if (mSaveDialog != null && mSaveDialog.isShowing()) {
                        mPlayFullTimeTv.setText(String.format(mTimerFormat,
                                SoundRecorderService.mRecorder.sampleLength() / 60 / 60,
                                SoundRecorderService.mRecorder.sampleLength() / 60 % 60,
                                SoundRecorderService.mRecorder.sampleLength() % 60));
                    }
                }

                if (mSampleInterrupted) {
                    // TODO: Set decent message and icon resources
                    mStateMessage.setVisibility(View.VISIBLE);
                    mStateMessage.setText(res.getString(R.string.recording_stopped));
                }

                break;
            case Recorder.RECORDING_STATE:
                startRecordingAnimator();

                mRecordButton.setImageResource(R.drawable.ic_pause_recording);
                mRecordButton.setEnabled(true);
                mRecordButton.setFocusable(true);
                mStopButton.setEnabled(true);
                mStopButton.setFocusable(true);

                mMoreIv.setVisibility(View.INVISIBLE);
                mStateMessage.setVisibility(View.VISIBLE);
                mStateMessage.setText(res.getString(R.string.recording));

                break;

            case Recorder.PLAYING_STATE:
                mRecordButton.setEnabled(true);
                mRecordButton.setFocusable(true);
                mStopButton.setEnabled(true);
                mStopButton.setFocusable(true);

                break;
            case Recorder.PAUSE_STATE:
                stopRecordingAnimator();

                mRecordButton.setImageResource(R.drawable.ic_start_recording);
                mRecordButton.setEnabled(true);
                mRecordButton.setFocusable(true);
                mStopButton.setEnabled(true);
                mStopButton.setFocusable(true);

                mMoreIv.setVisibility(View.INVISIBLE);
                mStateMessage.setVisibility(View.VISIBLE);
                mStateMessage.setText(res.getString(R.string.recording_paused));

                break;
        }

        updateTimerView();
        invalidateOptionsMenu();
    }

    /*
     * Called when Recorder changed it's state.
     */
    public void onStateChanged(int state) {
        mRecorderState = state;

        Log.i(TAG, "state = " + state);

        if (state == Recorder.PLAYING_STATE) {
            mSampleInterrupted = false;
            mErrorUiMessage = null;
        }

        if (state == Recorder.RECORDING_STATE) {
            mSampleInterrupted = false;
            mErrorUiMessage = null;
        }

        updateUi();
    }

    /*
     * Called when MediaPlayer encounters an error.
     */
    public void onError(int error) {
        Resources res = getResources();
        boolean isExit = false;

        String message = null;
        switch (error) {
            case Recorder.SDCARD_ACCESS_ERROR:
                Log.e(TAG, "Can\'t access USB storage");
                break;
            case Recorder.IN_CALL_RECORD_ERROR:
                // TODO: update error message to reflect that the recording
                // could not be
                // performed during a call.
                Log.e(TAG, "In call record error.");
                isExit = true;
                break;
            case Recorder.INTERNAL_ERROR:
                Log.e(TAG, "Internal application error");
                // 一般是因为其他应用正在占用麦克风资源
                Toast.makeText(this, R.string.toast_record_failed, Toast.LENGTH_SHORT).show();
                isExit = true;
                break;
            case Recorder.UNSUPPORTED_FORMAT:
                Log.e(TAG, "Unsupported format");
                isExit = true;
                break;
        }
        if (message != null) {
            Toast.makeText(this, R.string.toast_record_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
