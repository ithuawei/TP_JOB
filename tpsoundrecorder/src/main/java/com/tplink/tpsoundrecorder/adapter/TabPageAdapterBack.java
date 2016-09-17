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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;

import com.tplink.tpsoundrecorder.R;
import com.tplink.tpsoundrecorder.activity.SoundRecordActivity;
import com.tplink.tpsoundrecorder.fragment.CallRecordingFragment;
import com.tplink.tpsoundrecorder.fragment.RecordingFragment;

import java.util.LinkedHashMap;


/**
 * The Class TabPageAdapter.主界面上两个tab的adapter。
 */
public class TabPageAdapterBack extends FragmentPagerAdapter {

    /** The Constant TAB_NUM. */
    private static final int TAB_NUM = 2;

    /** The mTitle. */
    private String[] mTitle = new String[TAB_NUM];

    private static final int RECORDING_TAB_INDEX = 0;

    private static final int CALL_RECORDING_TAB_INDEX = 1;

    /** The m activity. */
    private SoundRecordActivity mActivity;

    /** The m fragment cache. */
    private LinkedHashMap<Integer, Fragment> mFragmentCache = new LinkedHashMap<>();

    /**
     * Instantiates a new my page adapter.
     *
     * @param activity the activity
     */
    public TabPageAdapterBack(SoundRecordActivity activity) {
        super(activity.getSupportFragmentManager());

        this.mActivity = activity;
        mTitle[RECORDING_TAB_INDEX] = mActivity.getResources().getString(R.string.tab_recording);
        mTitle[CALL_RECORDING_TAB_INDEX] = mActivity.getResources()
                .getString(R.string.tab_call_recording);
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentPagerAdapter#getItem(int)
     */
    @Override
    public Fragment getItem(int position) {
        Fragment f = null;
        switch (position) {
            case CALL_RECORDING_TAB_INDEX:
                f = mFragmentCache.containsKey(position) ? mFragmentCache.get(position)
                        : new CallRecordingFragment();
                ((CallRecordingFragment)f).setOnSelectionModeListener(mActivity);
                break;

            case RECORDING_TAB_INDEX:
                f = mFragmentCache.containsKey(position) ? mFragmentCache.get(position)
                        : new RecordingFragment();
                ((RecordingFragment)f).setOnSelectionModeListener(mActivity);
                break;

        }
        if (f == null) {
            return null;
        }
        if (f.getArguments() == null) {
            Bundle bundle = new Bundle();
            bundle.putInt("index", position);
            f.setArguments(bundle);
        }
        mFragmentCache.put(position, f);
        return f;
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.view.PagerAdapter#getCount()
     */
    @Override
    public int getCount() {
        //1:录音;2：通话录音
        return mTitle.length;
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.view.PagerAdapter#getPageTitle(int)
     */
    @Override
    public CharSequence getPageTitle(int position) {
        //1:录音;2：通话录音
        return mTitle[position];
    }

}
