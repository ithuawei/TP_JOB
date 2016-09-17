/*
 * Copyright (C) 2016, TP-LINK TECHNOLOGIES CO., LTD.
 *
 * Do.java
 *
 * Description
 *
 * Author nongzhanfei
 *
 * Ver 1.0, 9/12/16, NongZhanfei, Create file
 */
package com.tplink.tpsoundrecorder.interfaces;

import android.support.v4.app.Fragment;

/**
 * 歌曲勾选删除，选择模式：开始选择（Fragment）;完成选择
 */
public interface onSelectionModeListener {

    void onSelectionModeStart(Fragment currentFragment);

    void onSelectionModeFinished();
}
