/*
 * Copyright (C) 2016, TP-LINK TECHNOLOGIES CO., LTD.
 *
 * Recorder.java
 *
 * Description
 *
 * Author nongzhanfei
 *
 * Ver 1.0, 9/5/16, nongzhanfei, Create file
 */
package com.tplink.tpsoundrecorder.view;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

public class WaveHelper {
    private WaveView mWaveView;
    private ObjectAnimator mAnivisi;
    private ObjectAnimator mAnigone;
    private ObjectAnimator mWaveShiftAnim;

    public WaveHelper(WaveView waveView) {
        mWaveView = waveView;
        initAnimation();
    }

    public void start() {
        mWaveView.setShowWave(true);
        if (!mWaveShiftAnim.isRunning()) {
            mWaveShiftAnim.start();
        }
        AniVisi();
    }

    private void initAnimation() {
        mWaveShiftAnim = ObjectAnimator.ofFloat(
                mWaveView, "waveShiftRatio", 0f, 1.0f);
        mWaveShiftAnim.setRepeatCount(ValueAnimator.INFINITE);
        mWaveShiftAnim.setRepeatMode(ValueAnimator.RESTART);
        mWaveShiftAnim.setDuration(550);
        mWaveShiftAnim.setInterpolator(new LinearInterpolator());
        mWaveShiftAnim.start();
    }

    public void cancel() {
        if (mWaveShiftAnim != null) {
            AniGone();
        }
    }

    public boolean getIsAniRunning() {
        return mWaveShiftAnim.isRunning();
    }

    public void AniGone() {
        //取消放大的
        if (mAnivisi != null && mAnivisi.isRunning()) {
            mAnivisi.cancel();
            mAnivisi.end();
        }

        //还未启动隐藏的就启动
        if (mAnigone == null || !mAnigone.isRunning()) {
            PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat("alpha", 1f, 0f);
            PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat("scaleX", 1f, 0);
            PropertyValuesHolder pvhZ = PropertyValuesHolder.ofFloat("scaleY", 1f, 0);
            mAnigone = ObjectAnimator.ofPropertyValuesHolder(mWaveView, pvhX, pvhY, pvhZ).setDuration(1000);
            mAnigone.start();


            mAnigone.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {

                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    //动画结束
//                    mWaveShiftAnim.cancel();
//                    mWaveShiftAnim.end();
                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
        }
    }

    public void AniVisi() {
        //取消缩小的
        if (mAnigone != null && mAnigone.isRunning()) {
            mAnigone.cancel();
            mAnigone.end();
        }
        //还未启动可见就启动
        if (mAnivisi == null || !mAnivisi.isRunning()) {
            PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat("alpha", 0f, 1f);
            PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat("scaleX", 0f, 1f);
            PropertyValuesHolder pvhZ = PropertyValuesHolder.ofFloat("scaleY", 0f, 1f);
            mAnivisi = ObjectAnimator.ofPropertyValuesHolder(mWaveView, pvhX, pvhY, pvhZ).setDuration(1000);
            mAnivisi.start();
        }
    }
}
