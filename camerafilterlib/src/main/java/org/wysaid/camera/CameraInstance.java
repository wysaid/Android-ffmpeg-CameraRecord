package org.wysaid.camera;

import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;

import org.wysaid.myUtils.Common;

import java.io.IOException;
import java.util.List;

/**
 * Created by wangyang on 15/7/27.
 */


// Camera 仅适用单例
public class CameraInstance {
    public static final String LOG_TAG = Common.LOG_TAG;

    private Camera mCameraDevice;
    private Camera.Parameters mParams;

    public static final int DEFAULT_PREVIEW_RATE = 30;


    private boolean mIsPreviewing = false;


    private int mCameraID = Camera.CameraInfo.CAMERA_FACING_BACK;
    private int mDefaultCameraID = -1;

    private static CameraInstance mThisInstance;
    private int mPreviewWidth;
    private int mPreviewHeight;

    private CameraInstance() {}

    public static synchronized CameraInstance getInstance() {
        if(mThisInstance == null) {
            mThisInstance = new CameraInstance();
        }
        return mThisInstance;
    }

    public boolean isPreviewing() { return mIsPreviewing; }

    public int previewWidth() { return mPreviewWidth; }
    public int previewHeight() { return mPreviewHeight; }

    public interface CameraOpenCallback {
        void cameraReady();
    }

    public boolean tryOpenCamera(CameraOpenCallback callback) {
        Log.i(LOG_TAG, "try open camera...");

        try
        {
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO)
            {
                int numberOfCameras = Camera.getNumberOfCameras();

                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                for (int i = 0; i < numberOfCameras; i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if (cameraInfo.facing == mCameraID) {
                        mDefaultCameraID = i;
                    }
                }
            }
            stopPreview();
            if(mCameraDevice != null)
                mCameraDevice.release();

            if(mDefaultCameraID >= 0)
                mCameraDevice = Camera.open(mDefaultCameraID);
            else
                mCameraDevice = Camera.open();
        }
        catch(Exception e)
        {
            Log.e(LOG_TAG, "Open Camera Failed!");
            e.printStackTrace();
            return false;
        }

        if(mCameraDevice != null) {
            if (callback != null) {
                callback.cameraReady();
            }
            Log.i(LOG_TAG, "Camera opened!");
            initCamera(DEFAULT_PREVIEW_RATE);
        }
        return true;
    }

    public void stopCamera() {
        if(mCameraDevice != null) {
            mCameraDevice.setPreviewCallback(null);
            mCameraDevice.stopPreview();
            mIsPreviewing = false;
            mCameraDevice.release();
            mCameraDevice = null;
        }
    }

    public void startPreview(SurfaceTexture texture) {
        Log.i(LOG_TAG, "Camera startPreview...");
        if(mIsPreviewing) {
            Log.i(LOG_TAG, "Err: camera is previewing...");
            stopPreview();
            return ;
        }

        if(mCameraDevice != null) {
            try {
                mCameraDevice.setPreviewTexture(texture);
            } catch (IOException e) {
                e.printStackTrace();
            }

            mCameraDevice.startPreview();
            mIsPreviewing = true;
        }
    }

    public void stopPreview() {
        Log.i(LOG_TAG, "Camera stopPreview...");
        if(mIsPreviewing && mCameraDevice != null) {
            mIsPreviewing = false;
            mCameraDevice.stopPreview();
        }
    }

    public void initCamera(int previewRate) {
        if(mCameraDevice == null) {
            Log.e(LOG_TAG, "initCamera: Camera is not opened!");
            return;
        }

        mParams = mCameraDevice.getParameters();
        List<Integer> supportedPictureFormats = mParams.getSupportedPictureFormats();

        for(int fmt : supportedPictureFormats) {
            Log.i(LOG_TAG, String.format("Picture Format: %x", fmt));
        }

        mParams.setPictureFormat(PixelFormat.JPEG);

        List<Camera.Size> picSizes = mParams.getSupportedPictureSizes();
        Camera.Size picSz = null;

        for(Camera.Size sz : picSizes) {
            Log.i(LOG_TAG, String.format("Supported picture size: %d x %d", sz.width, sz.height));
            if(picSz == null || (sz.width < 600 && sz.height < 600))
                picSz = sz;
        }

        List<Camera.Size> prevSizes = mParams.getSupportedPreviewSizes();
        Camera.Size prevSz = null;

        for(Camera.Size sz : prevSizes) {
            Log.i(LOG_TAG, String.format("Supported preview size: %d x %d", sz.width, sz.height));
            if(prevSz == null || (sz.width == 640 && sz.height == 480))
                prevSz = sz;
        }


        mParams.setPreviewSize(prevSz.width, prevSz.height);
        mParams.setPictureSize(picSz.width, picSz.height);
        mParams.setFocusMode(mParams.FOCUS_MODE_AUTO);
        mParams.setPreviewFrameRate(previewRate); //设置相机预览帧率

        mCameraDevice.setParameters(mParams);


        mParams = mCameraDevice.getParameters();

        Camera.Size szPic = mParams.getPictureSize();
        Camera.Size szPrev = mParams.getPreviewSize();

        mPreviewWidth = szPrev.width;
        mPreviewHeight = szPrev.height;

        Log.i(LOG_TAG, String.format("Camera Picture Size: %d x %d", szPic.width, szPic.height));
        Log.i(LOG_TAG, String.format("Camera Preview Size: %d x %d", szPrev.width, szPrev.height));
    }
}
