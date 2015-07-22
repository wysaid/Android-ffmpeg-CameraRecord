package org.wysaid.glfunctions;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;

import com.googlecode.javacv.cpp.opencv_core;

import org.wysaid.camera.CameraInstance;
import org.wysaid.myutils.ImageUtil;
import org.wysaid.recorder.MyRecorderWrapper;

import java.nio.IntBuffer;
import java.util.LinkedList;

import java.util.Queue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by wangyang on 15/7/17.
 */
public class MyGLSurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    public static final String LOG_TAG = Common.LOG_TAG;

    public int viewWidth;
    public int viewHeight;

    private FrameRenderer myRenderer;

    private SurfaceTexture mSurfaceTexture;
    private int mTextureID;

    private MyRecorderWrapper mVideoRecorder;

    private static final int MAX_CACHED_FRAMES = 5;

    private opencv_core.IplImage[] mIplImageCaches;
    private LinkedList<opencv_core.IplImage> mImageList;

    private boolean mShouldRecord = false;

    class CachedFrame {
        opencv_core.IplImage image;
        long frameTimeMillis;
        long frameNanoTime;

        CachedFrame(opencv_core.IplImage img, long timeMill, long timeNano) {
            image = img;
            frameTimeMillis = timeMill;
            frameNanoTime = timeNano;
        }
    }

    private Queue<CachedFrame> mFrameQueue;

    private CachedFrame makeFrame(opencv_core.IplImage img) {
        return new CachedFrame(img, System.currentTimeMillis(), System.nanoTime());
    }

    private void pushCachedFrame(opencv_core.IplImage img) {
        synchronized (mFrameQueue) {
            mFrameQueue.offer(makeFrame(img));
        }
    }

    private CachedFrame getCachedFrame() {
        synchronized (mFrameQueue) {
            return mFrameQueue.poll();
        }
    }

    //回收使用过的缓存帧
    private void recycleCachedFrame(CachedFrame frame) {
        synchronized (mImageList) {
            mImageList.offer(frame.image);
        }
    }

    //获取空闲的缓存帧
    private opencv_core.IplImage getImageCache() {
        synchronized (mImageList) {
            return mImageList.poll();
        }
    }


    class RecordingRunnable implements Runnable {

        @Override
        public void run() {
            while(mShouldRecord)
            {
                CachedFrame frame = getCachedFrame();

                if(frame == null)
                {
                    synchronized (mRecordingRunnable) {
                        try {
                            this.wait();
                        } catch (InterruptedException e) {
                            Log.e(LOG_TAG, "Recording runnable wait() : " + e.getMessage());
                        }

                        if(!mShouldRecord) {
                            return;
                        }
                    }
                    continue;
                }

                if(mVideoRecorder != null && mVideoRecorder.isRecording()) {
                    mVideoRecorder.writeFrame(frame.image);
                }
                recycleCachedFrame(frame);
            }
        }
    }

    RecordingRunnable mRecordingRunnable = new RecordingRunnable();
    Thread mRecordingThread;

    public class Viewport {
        public int x, y;
        public int width, height;
    }

    public Viewport drawViewport;

    public class ClearColor {
        public float r, g, b, a;
    }

    public ClearColor clearColor;

    public void setClearColor(float r, float g, float b, float a) {
        clearColor.r = r;
        clearColor.g = g;
        clearColor.b = b;
        clearColor.a = a;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                GLES20.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a);
            }
        });
    }

    public float waveMotion = 0.0f;

    private CameraInstance cameraInstance() {
        return CameraInstance.getInstance();
    }

    public void startRecording() {
        mIplImageCaches = new opencv_core.IplImage[MAX_CACHED_FRAMES];
        mImageList = new LinkedList<>();
        mFrameQueue = new LinkedList<>();
        for(int i = 0; i != MAX_CACHED_FRAMES; ++i)
        {
            mIplImageCaches[i] = opencv_core.IplImage.create(640, 480, opencv_core.IPL_DEPTH_8U, 4);
            mImageList.add(mIplImageCaches[i]);
        }

        mShouldRecord = true;
        mVideoRecorder = new MyRecorderWrapper();
        mVideoRecorder.startRecording();
        mRecordingThread = new Thread(mRecordingRunnable);
        mRecordingThread.start();
    }

    public void endRecording() {
        Log.i(LOG_TAG, "notify quit...");
        synchronized (mRecordingRunnable)
        {
            mShouldRecord = false;
            try {
                mRecordingRunnable.notifyAll();
            } catch (Exception e) {
            }
        }

        Log.i(LOG_TAG, "joining thread...");
        try {
            mRecordingThread.join();
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Join recording thread err: " + e.getMessage());
        }

        mRecordingRunnable = null;
        mRecordingThread = null;

        Log.i(LOG_TAG, "saving recoring...");
        mVideoRecorder.saveRecording();

        for(opencv_core.IplImage img : mIplImageCaches) {
            img.release();
        }
        mIplImageCaches = null;
        mImageList.clear();
        mImageList = null;
        mFrameQueue.clear();
        mFrameQueue = null;

        Log.i(LOG_TAG, "recording OK");
    }

    public MyGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.i(LOG_TAG, "MyGLSurfaceView Construct...");

        setEGLContextClientVersion(2);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        clearColor = new ClearColor();
//        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.i(LOG_TAG, "onSurfaceCreated...");

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_STENCIL_TEST);

        mTextureID = genSurfaceTextureID();
        mSurfaceTexture = new SurfaceTexture(mTextureID);
        mSurfaceTexture.setOnFrameAvailableListener(this);


        myRenderer = new FrameRenderer();
        myRenderer.setRotation((float) Math.PI / 2.0f);
        requestRender();

        cameraInstance().tryOpenCamera(new CameraInstance.CameraOpenCallback() {
            @Override
            public void cameraReady() {
                Log.i(LOG_TAG, "tryOpenCamera OK...");
            }
        });
    }

    private void calcViewport() {
        float camHeight = (float)cameraInstance().previewWidth();
        float camWidth = (float)cameraInstance().previewHeight();

        drawViewport = new Viewport();

        float scale = Math.min(viewWidth / camWidth, viewHeight / camHeight);
        drawViewport.width = (int)(camWidth * scale);
        drawViewport.height = (int)(camHeight * scale);
        drawViewport.x = (viewWidth - drawViewport.width) / 2;
        drawViewport.y = (viewHeight - drawViewport.height) / 2;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.i(LOG_TAG, String.format("onSurfaceChanged: %d x %d", width, height));

        GLES20.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a);

        viewWidth = width;
        viewHeight = height;

        if(!cameraInstance().isPreviewing()) {
            cameraInstance().startPreview(mSurfaceTexture);
        }

        calcViewport();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        myRenderer.release();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glViewport(drawViewport.x, drawViewport.y, drawViewport.width, drawViewport.height);
//        myRenderer.renderTexture(mTextureID);
        myRenderer.renderTextureExternalOES(mTextureID);

        if(mShouldRecord && mVideoRecorder != null && mVideoRecorder.isRecording()) {
            opencv_core.IplImage imgCache = getImageCache();
            if(imgCache != null)
            {
                GLES20.glReadPixels(drawViewport.x, drawViewport.y, 640, 480, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, imgCache.getByteBuffer());
                pushCachedFrame(imgCache);

                synchronized (mRecordingRunnable)
                {
                    try {
                        mRecordingRunnable.notifyAll();
                    }catch (Exception e) {
                        Log.e(LOG_TAG, "Notify failed: " + e.getMessage());
                    }
                }

            }
            else {
                Log.d(LOG_TAG, "Frame loss...");
            }
        }

        if(mSurfaceTexture != null)
            mSurfaceTexture.updateTexImage();

//        Log.i(LOG_TAG, "onDrawFrame...");

        waveMotion += 0.4f;
        if(waveMotion > 1e3f) {
            waveMotion -= 1e3f;
        }
        myRenderer.setWaveMotion(waveMotion);
    }

    private IntBuffer mBuffer;

    public synchronized void takeShot() {
//        cameraInstance().stopPreview();

        if(mBuffer == null || mBuffer.limit() != drawViewport.width * drawViewport.height)
        {
            mBuffer = IntBuffer.allocate(drawViewport.width * drawViewport.height);
        }

        queueEvent(new Runnable() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "Taking shot...");
                GLES20.glReadPixels(drawViewport.x, drawViewport.y, drawViewport.width, drawViewport.height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mBuffer);

                Bitmap bmp = Bitmap.createBitmap(mBuffer.array(), drawViewport.width, drawViewport.height, Bitmap.Config.ARGB_8888);
                ImageUtil.saveBitmap(bmp);
            }
        });

//        cameraInstance().startPreview(mSurfaceTexture);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(LOG_TAG, "onResume...");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(LOG_TAG, "onPause...");
        cameraInstance().stopCamera();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//        Log.i(LOG_TAG, "onFrameAvailable...");
        requestRender();
    }

    private int genSurfaceTextureID() {
        int[] texID = new int[1];
        GLES20.glGenTextures(1, texID, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texID[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        return texID[0];
    }
}
