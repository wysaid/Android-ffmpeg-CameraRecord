package org.wysaid.recorder;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;

import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import com.googlecode.javacv.cpp.opencv_core;

import org.wysaid.camera.CameraInstance;
import org.wysaid.myUtils.Common;

import java.io.File;
import java.nio.Buffer;
import java.nio.ShortBuffer;

import static com.googlecode.javacv.cpp.avutil.AV_PIX_FMT_RGBA;

/**
 * Created by wangyang on 15/7/27.
 */

public class MyRecorderWrapper {
    public final static String LOG_TAG = Common.LOG_TAG;

    //视频文件存放地址
    private String mVideoPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "rec_video.mp4";

    //视频文件对象
    private File mFileVideoPath = null;
    //视频文件在系统中存放的uri
    private Uri mUriVideoPath = null;

    //判断是否需要录制 (手指按下继续， 抬起暂停)
    private boolean mShouldRecording = false;

    //判断是否开始录制
    private boolean mIsRecordingStarted = false;

    //class for video&audio recording...
    private volatile MyRecorder mFrameRecorder;

    //判断当前是否为前置摄像头
    private boolean mIsFrontCamera = false;

    //当前录制的质量 （质量跟视频的清晰度和大小有关)
    private int mResolution = CONSTANTS.RESOLUTION_MEDIUM_VALUE;

    //预览视频宽高
    private int mPreviewWidth = 640, mPreviewHeight = 480;

    //音频采样率
    private int mAudioSampleRate = 44100;

    //录制音频的线程
    private Thread mAudioThread;
    private AudioRecordingRunnable mAudioRecordRunnable;

    //启动/停止 音频录制
    private boolean mRunAudioThread = true;

    //开始录制时的时间
    private long mStartRecordingTime;
    //录制总时间
    private long mTotalRecordingTime;
    //录制最长时间
    private int mRecordingTimeMax = 15000;
    //录制最短时间
    private int mRecordingTimeMin = 5000;

    //音频时间戳
    private volatile long mAudioTimestamp = 0L;
    private long mLastAudioTimestamp = 0L;
    private volatile long mAudioTimeRecorded;


    //音频同步标志
    private final int[] mAudioSyncLock = new int[0];
    //视频同步标志
    private final int[] mVideoSyncLock = new int [0];

    //每一帧间隔时间
    private int mFrameTime;

    //当前帧编号
    private int mFrameNumber = 0;

    //视频时间戳
    private long mVideoTimestamp = 0L;

    //是否保存过视频文件
    private boolean mIsRecordingSaved = false;
    private boolean isFinalizing = false;

    static {
        System.loadLibrary("checkneon");
    }

    public native static int checkNeonFromJNI();
    private boolean bInitSuccess = false;

    public boolean isRecording() {
        return mIsRecordingStarted;
    }

    public MyRecorderWrapper(Context context) {
        initVideoRecorder(context);
    }

    public MyRecorderWrapper(String filePath) {
        initVideoRecorder(filePath);
    }

    // 录制音频的线程
    class AudioRecordingRunnable implements Runnable {
        int bufferSize;
        short[] audioData;
        int bufferRet;
        //系统自带的音频类
        final AudioRecord audioRecord;
        volatile boolean isInitialized;
        int count = 0;

        private AudioRecordingRunnable() {
            bufferSize = AudioRecord.getMinBufferSize(mAudioSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, mAudioSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            audioData = new short[bufferSize];
        }

        //shortBuffer 包含音频的数据和起始位置
        private void record(ShortBuffer shortBuffer) {
            try {
//                synchronized (mAudioSyncLock) {
                if(mFrameRecorder != null) {
                    count += shortBuffer.limit();
//                        mFrameRecorder.record(0, shortBuffer);
                    //TODO 读写分离 - 待优化
                    mFrameRecorder.record(0, new Buffer[]{shortBuffer});
//                    }
                }
            }catch (Exception e) {
                Log.e(LOG_TAG, "record audio error...");
            }
        }

        //更新音频时间戳
        private void updateTimestamp() {
            if(mFrameRecorder != null) {
                int i = VideoUtil.getTimeStampInNsFromSampleCounted(count);
                if(mAudioTimestamp != i) {
                    mAudioTimestamp = i;
                    mAudioTimeRecorded = System.nanoTime();
                }
            }
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            this.isInitialized = false;

            if(audioRecord != null) {
                //等待音频初始化
                while(this.audioRecord.getState() == 0) {
                    try {
                        Thread.sleep(100L);
                    }catch (InterruptedException e) {
                        //nothing to do
                    }
                }

                this.isInitialized = true;
                this.audioRecord.startRecording();

                while (((mRunAudioThread) || (mVideoTimestamp > mAudioTimestamp)) && (mAudioTimestamp < (1000 * mRecordingTimeMax)))
                {
                    updateTimestamp();
                    bufferRet = this.audioRecord.read(audioData, 0, audioData.length);
                    if ((bufferRet > 0) && (mShouldRecording || (mVideoTimestamp > mAudioTimestamp)))
                        record(ShortBuffer.wrap(audioData, 0, bufferRet));
                }
                this.audioRecord.stop();
                this.audioRecord.release();
            }
        }
    }

    private void initVideoRecorder(String videoPath) {
        mVideoPath = videoPath;
        RecorderParameters param = VideoUtil.getRecorderParameter(mResolution);
        mAudioSampleRate = param.getAudioSamplingRate();

        mFrameTime = 1000000 / CameraInstance.DEFAULT_PREVIEW_RATE;

        mFileVideoPath = new File(mVideoPath);
        mFrameRecorder = new MyRecorder(mVideoPath, 640, 480, 1);
        mFrameRecorder.setFormat(param.getVideoOutputFormat());
        mFrameRecorder.setSampleRate(param.getAudioSamplingRate());
        mFrameRecorder.setFrameRate(param.getVideoFrameRate());
        mFrameRecorder.setVideoCodec(param.getVideoCodec());
        mFrameRecorder.setVideoQuality(param.getVideoQuality());
        mFrameRecorder.setAudioQuality(param.getVideoQuality());
        mFrameRecorder.setAudioCodec(param.getAudioCodec());
        mFrameRecorder.setVideoBitrate(param.getVideoBitrate());
        mFrameRecorder.setAudioBitrate(param.getAudioBitrate());

        mAudioRecordRunnable = new AudioRecordingRunnable();
        mAudioThread = new Thread(mAudioRecordRunnable);
    }

    private void initVideoRecorder(Context context) {
        mVideoPath = VideoUtil.createFinalPath(context);
        RecorderParameters param = VideoUtil.getRecorderParameter(mResolution);
        mAudioSampleRate = param.getAudioSamplingRate();

        mFrameTime = 1000000 / CameraInstance.DEFAULT_PREVIEW_RATE;

        mFileVideoPath = new File(mVideoPath);
        mFrameRecorder = new MyRecorder(mVideoPath, 640, 480, 1);
        mFrameRecorder.setFormat(param.getVideoOutputFormat());
        mFrameRecorder.setSampleRate(param.getAudioSamplingRate());
        mFrameRecorder.setFrameRate(param.getVideoFrameRate());
        mFrameRecorder.setVideoCodec(param.getVideoCodec());
        mFrameRecorder.setVideoQuality(param.getVideoQuality());
        mFrameRecorder.setAudioQuality(param.getVideoQuality());
        mFrameRecorder.setAudioCodec(param.getAudioCodec());
        mFrameRecorder.setVideoBitrate(param.getVideoBitrate());
        mFrameRecorder.setAudioBitrate(param.getAudioBitrate());

        mAudioRecordRunnable = new AudioRecordingRunnable();
        mAudioThread = new Thread(mAudioRecordRunnable);
    }

    //开始录制
    public void startRecording() {
        try {
            mFrameRecorder.start();
            mAudioThread.start();
        } catch(Exception e) {
            Log.e(LOG_TAG, "start record failed!\n");
            e.printStackTrace();
            return;
        }

        mFrameNumber = 0;
        mIsRecordingStarted = true;
        mStartRecordingTime = System.currentTimeMillis();

        mShouldRecording = true;
        mTotalRecordingTime = 0;

    }

    //结束录制(不保存)
    public void endRecording(boolean success) {
        releaseRes();
        if(mFileVideoPath != null && mFileVideoPath.exists() && !success)
            mFileVideoPath.delete();
    }

    //压入一帧图像数据
    public void writeFrame(opencv_core.IplImage iplImage) {
        //计算时间戳
//        long frameTimeStamp;
//        if(mAudioTimestamp == 0L && mStartRecordingTime > 0L)
//            frameTimeStamp = 1000L * (System.currentTimeMillis() -mStartRecordingTime);
//        else if (mLastAudioTimestamp == mAudioTimestamp)
//            frameTimeStamp = mAudioTimestamp + mStartRecordingTime;
//        else
//        {
//            long l2 = (System.nanoTime() - mAudioTimeRecorded) / 1000L;
//            frameTimeStamp = l2 + mAudioTimestamp;
//            mLastAudioTimestamp = mAudioTimestamp;
//        }

        synchronized (mFrameRecorder) {
            ++mFrameNumber;
            Log.i(LOG_TAG, String.format("当前帧编号: %d", mFrameNumber));

            try {
//                mFrameRecorder.setTimestamp(mFrameNumber * mFrameTime * 2);
                mFrameRecorder.setFrameNumber(mFrameNumber);
                mFrameRecorder.record(iplImage, AV_PIX_FMT_RGBA);

            } catch (Exception e) {
                Log.e(LOG_TAG, "录制错误: " + e.getMessage());
            }
        }
    }

    //注册录制的视频文件
    private void registerVideo(Context context) {
        Uri videoiTable = Uri.parse(CONSTANTS.VIDEO_CONTENT_URI);
        VideoUtil.videoContentValues.put(MediaStore.Video.Media.SIZE, new File(mVideoPath).length());

        try {
            mUriVideoPath = context.getContentResolver().insert(videoiTable, VideoUtil.videoContentValues);
        } catch (Throwable e) {
            mUriVideoPath = null;
            mVideoPath = null;
            e.printStackTrace();
        }
        VideoUtil.videoContentValues = null;
    }

    //保存录制的视频文件
    public void saveRecording(Context context) {
        mIsRecordingStarted = false;
        if(mIsRecordingStarted) {
            mRunAudioThread = false;
            try {
                mAudioThread.join(1000L);
            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "join audio thread failed...");
            }
            mAudioThread = null;
            if(!mIsRecordingSaved) {
                mIsRecordingSaved = true;
                registerVideo(context);
                mFrameRecorder = null;
            } else {
//                videoTh
            }
        }
        releaseRes();
    }

    private void releaseRes() {
        mIsRecordingSaved = true;

        try {
            if(mFrameRecorder != null) {
                mFrameRecorder.stop();
                mFrameRecorder.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        mFrameRecorder = null;

    }

    //停止录制
//    public class AsyncStopRecording

}
