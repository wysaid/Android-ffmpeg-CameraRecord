package org.wysaid.glfunctions;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;

import org.wysaid.camera.CameraInstance;
import org.wysaid.myutils.ImageUtil;

import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
//import javax.microedition.khronos.egl.EGL10;
//import javax.microedition.khronos.egl.EGLContext;
//import javax.microedition.khronos.egl.EGLDisplay;
//import javax.microedition.khronos.egl.EGLSurface;
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

    public class Viewport {
        public int x, y;
        public int width, height;
    }

    public Viewport drawViewport;

    public float waveMotion = 0.0f;

    private boolean mShouldTakeshot = false;

    public synchronized void takeShot() {
        mShouldTakeshot = true;
    }

//    public class EGLEnv {
//        EGLDisplay display;
//        EGLSurface surfaceRead;
//        EGLSurface surfaceWrite;
//        EGLContext context;
//    }
//
//    public EGLEnv eglEnv;
//    public EGL10 egl;

    private CameraInstance cameraInstance() {
        return CameraInstance.getInstance();
    }

    public MyGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.i(LOG_TAG, "MyGLSurfaceView Construct...");

        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
//        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.i(LOG_TAG, "onSurfaceCreated...");

        GLES20.glClearColor(1.0f, 1.0f, 0.3f, 1.0f);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

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

        viewWidth = width;
        viewHeight = height;

        if(!cameraInstance().isPreviewing()) {
            cameraInstance().startPreview(mSurfaceTexture);
        }

        calcViewport();
//        try {
//            egl = (EGL10) EGLContext.getEGL();
//            eglEnv.display = egl.eglGetCurrentDisplay();
//            eglEnv.surfaceRead = egl.eglGetCurrentSurface(egl.EGL_READ);
//            eglEnv.surfaceWrite = egl.eglGetCurrentSurface(egl.EGL_DRAW);
//            eglEnv.context = egl.eglGetCurrentContext();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
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

        if(mShouldTakeshot) {
            _takeShot();
            mShouldTakeshot = false;
        }

        if(mSurfaceTexture != null)
            mSurfaceTexture.updateTexImage();

//        Log.i(LOG_TAG, "onDrawFrame...");

        waveMotion += 0.4f;
        if(waveMotion > 1e6f) {
            waveMotion -= 1e6f;
        }
        myRenderer.setWaveMotion(waveMotion);
    }

    private void _takeShot() {
        cameraInstance().stopPreview();

        IntBuffer buffer = IntBuffer.allocate(drawViewport.width * drawViewport.height);

//        if(egl != null && eglEnv.display != null)
//        {
//            egl.eglMakeCurrent(eglEnv.display, eglEnv.surfaceWrite, eglEnv.surfaceRead, eglEnv.context);
//        }

        GLES20.glReadPixels(drawViewport.x, drawViewport.y, drawViewport.width, drawViewport.height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);

        Bitmap bmp = Bitmap.createBitmap(buffer.array(), drawViewport.width, drawViewport.height, Bitmap.Config.ARGB_8888);

        ImageUtil.saveBitmap(bmp);

        cameraInstance().startPreview(mSurfaceTexture);
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
