/*
 * Copyriht (C) 2016, TP-LINK TECHNOLOGIES CO., LTD
 *
 * SoundRecordActivity.java
 *
 * Description manage fragments to show record list
 *
 * Author WangDasen
 *
 * Ver 1.0, 2016年6月1日, WangDasen, Create file
 */

package com.tplink.tpsoundrecorder.activity;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;

import com.tplink.tpsoundrecorder.R;
import com.tplink.tpsoundrecorder.adapter.TabPageAdapter;
import com.tplink.tpsoundrecorder.fragment.CallRecordingFragment;
import com.tplink.tpsoundrecorder.fragment.RecordingFragment;
import com.tplink.tpsoundrecorder.interfaces.onSelectionModeListener;
import com.tplink.tpsoundrecorder.view.PagerSlidingTabStrip;
import com.tplink.tpsoundrecorder.view.SoundRecorderViewPager;

/**
 * manage fragments to show record list
 */
public class SoundRecordActivity extends AppCompatActivity implements onSelectionModeListener {
    /** The m pager sliding tab strip. */
    private PagerSlidingTabStrip mPagerSlidingTabStrip;

    /** The m tabViewPager view. */
    private SoundRecorderViewPager mTabViewPager;

    private Toolbar mToolbar;

    private TabPageAdapter mAdapter;

    private final int tabTextColor = 0xDE000000;

    private boolean isSelectionMode = false;

    private Fragment mCurrentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.sound_record_layout);
        mToolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        mTabViewPager = (SoundRecorderViewPager)findViewById(R.id.viewpager);
        mAdapter = new TabPageAdapter(this);
        mTabViewPager.setAdapter(mAdapter);

        // 关联ViewPager
        mPagerSlidingTabStrip = (PagerSlidingTabStrip)findViewById(R.id.tabs);
        mPagerSlidingTabStrip.setTextColor(tabTextColor);
        mPagerSlidingTabStrip.setViewPager(mTabViewPager);
    }
    @Override
    public void onSelectionModeStart(Fragment currentFragment) {
        isSelectionMode = true;
        mCurrentFragment = currentFragment;
        for (int i = 0; i < mPagerSlidingTabStrip.getTabLayout().getChildCount(); i++) {
            mPagerSlidingTabStrip.getTabLayout().getChildAt(i).setClickable(false);
        }

        mTabViewPager.setScrollEnabled(false);
    }

    @Override
    public void onSelectionModeFinished() {
        isSelectionMode = false;
        for (int i = 0; i < mPagerSlidingTabStrip.getTabLayout().getChildCount(); i++) {
            mPagerSlidingTabStrip.getTabLayout().getChildAt(i).setClickable(true);
        }

        mTabViewPager.setScrollEnabled(true);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // fragment中列表选择模式下，才消费KEYCODE_BACK
        if (isSelectionMode && event.getAction() == KeyEvent.ACTION_UP
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // fragment中列表选择模式，处理当前fragment返回操作
            if (mCurrentFragment != null && mCurrentFragment instanceof RecordingFragment) {
                ((RecordingFragment)mCurrentFragment).handleBack();
            } else if (mCurrentFragment != null
                    && mCurrentFragment instanceof CallRecordingFragment) {
                ((CallRecordingFragment)mCurrentFragment).handleBack();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
