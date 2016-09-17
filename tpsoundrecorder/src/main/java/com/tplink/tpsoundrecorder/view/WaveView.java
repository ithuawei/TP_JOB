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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import com.tplink.tpsoundrecorder.recorder.Recorder;

public class WaveView extends View {
    private static final float DEFAULT_AMPLITUDE_RATIO = 0.05f;
    private static final float DEFAULT_WATER_LEVEL_RATIO = 0.6f;
    private static final float DEFAULT_WAVE_LENGTH_RATIO = 1.0f;
    private static final float DEFAULT_WAVE_SHIFT_RATIO = 0.0f;

    private boolean mShowWave;
    private BitmapShader mWaveShader;
    private Matrix mShaderMatrix;
    private Paint mViewPaint;

    private float mDefaultAmplitude;
    private float mDefaultWaterLevel;
    private float mDefaultWaveLength;
    private double mDefaultAngularFrequency;

    private float mAmplitudeRatio = DEFAULT_AMPLITUDE_RATIO;
    private float mWaveLengthRatio = DEFAULT_WAVE_LENGTH_RATIO;
    private float mWaterLevelRatio = DEFAULT_WATER_LEVEL_RATIO;
    private float mWaveShiftRatio = DEFAULT_WAVE_SHIFT_RATIO;

    private int mBehindWaveColor = Color.parseColor("#772a8c7b");
    private int mFrontWaveColor = Color.parseColor("#771cbbec");

    public WaveView(Context context) {
        super(context);
        init();
    }

    public WaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mShaderMatrix = new Matrix();
        mViewPaint = new Paint();
        mViewPaint.setAntiAlias(true);
    }

    public void setWaveShiftRatio(float waveShiftRatio) {
        if (mWaveShiftRatio != waveShiftRatio) {
            mWaveShiftRatio = waveShiftRatio;
            invalidate();
        }
    }

    public float getWaterLevelRatio() {
        return mWaterLevelRatio;
    }

    public void setWaterLevelRatio(float waterLevelRatio) {
        if (mWaterLevelRatio != waterLevelRatio) {
            mWaterLevelRatio = waterLevelRatio;
            invalidate();
        }
    }

    public void setAmplitudeRatio(float amplitudeRatio) {
        if (mAmplitudeRatio != amplitudeRatio) {
            mAmplitudeRatio = amplitudeRatio;
            invalidate();
        }
    }

    public void setShowWave(boolean showWave) {
        mShowWave = showWave;
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        createShader();
    }

    private void createShader() {
        mDefaultAngularFrequency = 2.0f * Math.PI / DEFAULT_WAVE_LENGTH_RATIO / getWidth();
        mDefaultAmplitude = getHeight() * DEFAULT_AMPLITUDE_RATIO;
        mDefaultWaterLevel = getHeight() * DEFAULT_WATER_LEVEL_RATIO;
        mDefaultWaveLength = getWidth();

        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint wavePaint = new Paint();
        wavePaint.setStrokeWidth(2);
        wavePaint.setAntiAlias(true);

        final int endX = getWidth() + 1;
        final int endY = getHeight() + 1;

        float[] waveY = new float[endX];

        wavePaint.setColor(mBehindWaveColor);
        for (int beginX = 0; beginX < endX; beginX++) {
            double wx = beginX * mDefaultAngularFrequency;
            float beginY = (float) (mDefaultWaterLevel + mDefaultAmplitude * Math.sin(wx));
            canvas.drawLine(beginX, beginY, beginX, endY, wavePaint);

            waveY[beginX] = beginY;
        }

        wavePaint.setColor(mFrontWaveColor);
        final int wave2Shift = (int) (mDefaultWaveLength / 4);
        for (int beginX = 0; beginX < endX; beginX++) {
            canvas.drawLine(beginX, waveY[(beginX + wave2Shift) % endX], beginX, endY, wavePaint);
        }

        mWaveShader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
        mViewPaint.setShader(mWaveShader);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mShowWave && mWaveShader != null) {
            if (mViewPaint.getShader() == null) {
                mViewPaint.setShader(mWaveShader);
            }

            mShaderMatrix.setScale(
                    mWaveLengthRatio / DEFAULT_WAVE_LENGTH_RATIO,
                    mAmplitudeRatio / DEFAULT_AMPLITUDE_RATIO,
                    0,
                    mDefaultWaterLevel);

            mShaderMatrix.postTranslate(
                    mWaveShiftRatio * getWidth(),
                    (DEFAULT_WATER_LEVEL_RATIO - mWaterLevelRatio) * getHeight());

            mWaveShader.setLocalMatrix(mShaderMatrix);

            float radius = getWidth() / 2f;
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, mViewPaint);
        } else {
            mViewPaint.setShader(null);
        }
    }
    private static final float MAX_AMPLITUDE = 14400;

    private static final float MAX_SCALE = 1.5f;

    private static final int DURATION = 100;

    Recorder mRecorder;

    public void setRecorder(Recorder recorder) {
        mRecorder = recorder;
    }

    public void startAnimator() {
        float mCurrentAmplitude = mRecorder.getMaxAmplitude();

        float progress = mCurrentAmplitude / 20000 * 100;//32767

        float progre = (float) progress / 1000;
        if (progre < 0.001f) {
            progre = 0.001f;
        }
       float mAmplitude = progre;
      setAmplitudeRatio(mAmplitude);

        animate()/*.scaleX(scale).scaleY(scale)*/.setDuration(DURATION).withEndAction(new Runnable() {

            @Override
            public void run() {
                startAnimator();
            }
        }).start();
    }

    public void stopAnimator() {
        animate().cancel();
    }
}
