/*
 * Copyriht (C) 2016, TP-LINK TECHNOLOGIES CO., LTD
 *
 * TabPageAdapter.java
 *
 * Description The Class TabPageAdapter.SoundRecordActivity上两个tab的adapter。
 *
 * Author WangDasen
 *
 * Ver 1.0, 2016年6月1日, WangDasen, Create file
 */
package com.tplink.tpsoundrecorder.adapter;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;

import com.tplink.tpsoundrecorder.R;
import com.tplink.tpsoundrecorder.activity.SoundRecordActivity;
import com.tplink.tpsoundrecorder.fragment.CallRecordingFragment;
import com.tplink.tpsoundrecorder.fragment.RecordingFragment;

public class TabPageAdapter extends FragmentPagerAdapter {

    private SoundRecordActivity mActivity;

    public TabPageAdapter(SoundRecordActivity activity) {
        super(activity.getSupportFragmentManager());
        this.mActivity = activity;
    }

    @Override
    public Fragment getItem(int position) {
        Fragment f = null;
        switch (position) {
            case 0:
                f = new RecordingFragment();
                ((RecordingFragment) f).setOnSelectionModeListener(mActivity);
                break;

            case 1:
                f = new CallRecordingFragment();
                ((CallRecordingFragment) f).setOnSelectionModeListener(mActivity);
                break;
        }
        return f;
    }

    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (position == 0) {
            return mActivity.getResources().getString(R.string.tab_recording); //录音
        } else {
            return mActivity.getResources().getString(R.string.tab_call_recording);//通话录音;
        }
    }
}
