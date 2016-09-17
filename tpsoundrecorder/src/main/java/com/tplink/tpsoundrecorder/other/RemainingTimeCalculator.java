/************************************************************
    Copyright (C), 1996-2015, TP-LINK TECHNOLOGIES CO., LTD.

    File name: RemainingTimeCalculator.java

    Description:

    Author: ZhangYi

    History:
    ----------------------------------
    Version: 1.0, 2016年1月17日, ZhangYi   create file.
 ************************************************************/

package com.tplink.tpsoundrecorder.other;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import com.tplink.tpsoundrecorder.util.AndroidUtil;

import java.io.File;

/**
 * Calculates remaining recording time based on available disk space and
 * optionally a maximum recording file size. The reason why this is not trivial
 * is that the file grows in blocks every few seconds or so, while we want a
 * smooth countdown.
 */

public class RemainingTimeCalculator {
    public static final int UNKNOWN_LIMIT = 0;

    public static final int FILE_SIZE_LIMIT = 1;

    public static final int DISK_SPACE_LIMIT = 2;

    // which of the two limits we will hit (or have fit) first
    private int mCurrentLowerLimit = UNKNOWN_LIMIT;

    private File mSDCardDirectory;

    private File mPhoneCardDirectory;

    // State for tracking file size of recording.
    private File mRecordingFile;

    private long mMaxBytes;

    // Rate at which the file grows
    private int mBytesPerSecond;

    private int mPath = 0;

    // time at which number of free blocks last changed
    private long mBlocksChangedTime;

    // number of available blocks at that time
    private long mLastBlocks;

    // time at which the size of the file has last changed
    private long mFileSizeChangedTime;

    // size of the file at that time
    private long mLastFileSize;

    public RemainingTimeCalculator(Context context) {
        String sdPath = AndroidUtil.getSDPath(context);

        if (sdPath != null) {
            mSDCardDirectory = new File(sdPath);
        }
        mPhoneCardDirectory = Environment.getExternalStorageDirectory();
    }

    /**
     * If called, the calculator will return the minimum of two estimates: how
     * long until we run out of disk space and how long until the file reaches
     * the specified size.
     *
     * @param file the file to watch
     * @param maxBytes the limit
     */

    public void setFileSizeLimit(File file, long maxBytes) {
        mRecordingFile = file;
        mMaxBytes = maxBytes;
    }

    /**
     * Resets the interpolation.
     */
    public void reset() {
        mCurrentLowerLimit = UNKNOWN_LIMIT;
        mBlocksChangedTime = -1;
        mFileSizeChangedTime = -1;
    }

    /**
     * Returns how long (in seconds) we can continue recording.
     */
    public long timeRemaining() {
        // Calculate how long we can record based on free disk space

        long blocks;
        StatFs fs;
        if (mPath == 1 && mSDCardDirectory != null) {
            fs = new StatFs(mSDCardDirectory.getAbsolutePath());
            blocks = fs.getAvailableBlocks();
        } else {
            fs = new StatFs(mPhoneCardDirectory.getAbsolutePath());
            blocks = fs.getAvailableBlocks() - fs.getBlockCount() * 5 / 100;
        }
        long blockSize = fs.getBlockSize();
        long now = System.currentTimeMillis();

        if (mBlocksChangedTime == -1 || blocks != mLastBlocks) {
            mBlocksChangedTime = now;
            mLastBlocks = blocks;
        }

        /*
         * The calculation below always leaves one free block, since free space
         * in the block we're currently writing to is not added. This last block
         * might get nibbled when we close and flush the file, but we won't run
         * out of disk.
         */

        // at mBlocksChangedTime we had this much time
        long result = mLastBlocks * blockSize / mBytesPerSecond;
        // so now we have this much time
        result -= (now - mBlocksChangedTime) / 1000;

        if (mRecordingFile == null) {
            mCurrentLowerLimit = DISK_SPACE_LIMIT;
            return result;
        }

        // If we have a recording file set, we calculate a second estimate
        // based on how long it will take us to reach mMaxBytes.

        mRecordingFile = new File(mRecordingFile.getAbsolutePath());
        long fileSize = mRecordingFile.length();
        if (mFileSizeChangedTime == -1 || fileSize != mLastFileSize) {
            mFileSizeChangedTime = now;
            mLastFileSize = fileSize;
        }

        long result2 = (mMaxBytes - fileSize) / mBytesPerSecond;
        result2 -= (now - mFileSizeChangedTime) / 1000;
        result2 -= 1; // just for safety

        mCurrentLowerLimit = result < result2 ? DISK_SPACE_LIMIT : FILE_SIZE_LIMIT;

        return Math.min(result, result2);
    }

    /**
     * Indicates which limit we will hit (or have hit) first, by returning one
     * of FILE_SIZE_LIMIT or DISK_SPACE_LIMIT or UNKNOWN_LIMIT. We need this to
     * display the correct message to the user when we hit one of the limits.
     */
    public int currentLowerLimit() {
        return mCurrentLowerLimit;
    }

    /**
     * Is there any point of trying to start recording?
     */
    public boolean diskSpaceAvailable() {
        boolean result;
        if (mPath == 1 && mSDCardDirectory != null) {
            StatFs fs = new StatFs(mSDCardDirectory.getAbsolutePath());
            result = fs.getAvailableBlocks() > 1;
        } else {
            StatFs fs = new StatFs(mPhoneCardDirectory.getAbsolutePath());
            result = fs.getAvailableBlocks() > fs.getBlockCount() * 5 / 100;
        }
        // keep one free block
        return result;
    }

    /**
     * Sets the bit rate used in the interpolation.
     *
     * @param bitRate the bit rate to set in bits/sec.
     */
    public void setBitRate(int bitRate) {
        mBytesPerSecond = bitRate / 8;
    }

    public void setStoragePath(int path) {
        mPath = path;
    }
}
