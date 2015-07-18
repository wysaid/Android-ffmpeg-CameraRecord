package org.wysaid.camera;

import android.hardware.Camera;
import android.util.Log;

import org.wysaid.glfunctions.Common;

/**
 * Created by wangyang on 15/7/18.
 */

// Camera 仅适用单例
public class CameraInterface {
    public static final String LOG_TAG = Common.LOG_TAG;

    private Camera mCameraDevice;
    private Camera.Parameters mParams;

    private boolean mIsPreviewing = false;
    private float mPreviewrate = -1.0f;

    private static CameraInterface mThisInstance;

    private CameraInterface() {}

    public static synchronized CameraInterface getInstance() {
        if(mThisInstance == null) {
            mThisInstance = new CameraInterface();
        }
        return mThisInstance;
    }

    public interface CameraOpenCallback {
        public void cameraReady();
    }

    public void tryOpenCamera(CameraOpenCallback callback) {
        Log.i(LOG_TAG, "try open camera...");
        if(mCameraDevice == null) {
            mCameraDevice = Camera.open();
            Log.i(LOG_TAG, "Camera open OK");
            if(callback != null) {
                callback.cameraReady();
            }
        }
        else {
            Log.i(LOG_TAG, "Camera open failed!");

        }
    }

    public void stopCamera() {
        if(mCameraDevice != null) {
            mCameraDevice.setPreviewCallback(null);
            mCameraDevice.stopPreview();
            mIsPreviewing = false;
            mPreviewrate = -1.0f;
            mCameraDevice.release();
            mCameraDevice = null;
        }
    }

    public void startPreview() {

    }
}
