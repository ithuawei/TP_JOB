
package com.tplink.tpsoundrecorder.view;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

public class SoundRecorderViewPager extends ViewPager {
    private static final String TAG = "SoundRecorderViewPager";

    private boolean mScrollEnabled = true;

    public SoundRecorderViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub
    }

    public SoundRecorderViewPager(Context context) {
        super(context);
    }

    public void setScrollEnabled(boolean enabled) {
        mScrollEnabled = enabled;
    }

    @Override
    public void scrollTo(int x, int y) {
        super.scrollTo(x, y);
    }

    @Override
    public boolean onTouchEvent(MotionEvent arg0) {
        if (!mScrollEnabled) {
            Log.e(TAG, "forbid to onTouchEvent!");
            return false;
        } else {
            return super.onTouchEvent(arg0);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent arg0) {
        if (!mScrollEnabled) {
            Log.e(TAG, "forbid to onInterceptTouchEvent!");
            return false;
        } else {
            return super.onInterceptTouchEvent(arg0);
        }
    }

    @Override
    public void setCurrentItem(int item, boolean smoothScroll) {
        if (mScrollEnabled) {
            super.setCurrentItem(item, smoothScroll);
        } else {
            Log.e(TAG, "forbid to set current item smoothScroll!");
        }
    }
}
