package org.wysaid.filtervideocamera;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import org.wysaid.view.CameraGLSurfaceView;
import org.wysaid.view.CameraGLSurfaceView.FilterButtons;


public class CameraDemoActivity extends Activity {

    private Button mTakePicBtn;
    private Button mRecordBtn;
    private CameraGLSurfaceView mCameraSurfaceView;
    private SeekBar mSeekBar;

    public final static String LOG_TAG = CameraGLSurfaceView.LOG_TAG;

    public static CameraDemoActivity mCurrentInstance = null;
    public static CameraDemoActivity getInstance() {
        return mCurrentInstance;
    }

    public static final String FilterNames[] = {
            "波纹",
            "浮雕",
            "查找边缘",
            "LerpBlur"
    };

    public static final FilterButtons[] FilterTypes = {
            FilterButtons.Filter_Origin,
            FilterButtons.Filter_Wave,
            FilterButtons.Filter_Emboss,
            FilterButtons.Filter_Edge,
            FilterButtons.Filter_BlurLerp
    };

    public class MyButtons extends Button {

        public FilterButtons filterType;

        public MyButtons(Context context) {
            super(context);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_demo);

        mTakePicBtn = (Button)findViewById(R.id.takeShotBtn);
        mRecordBtn = (Button)findViewById(R.id.recordBtn);
        mCameraSurfaceView = (CameraGLSurfaceView)findViewById(R.id.myGLSurfaceView);
        mSeekBar = (SeekBar)findViewById(R.id.seekBar);

        mTakePicBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(LOG_TAG, "Taking Picture...");
                mCameraSurfaceView.post(new Runnable() {
                    @Override
                    public void run() {
                        mCameraSurfaceView.takeShot();
                    }
                });
            }
        });

        mRecordBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mCameraSurfaceView.isRecording()) {
                    Log.i(LOG_TAG, "End recording...");
                    mCameraSurfaceView.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                    mCameraSurfaceView.endRecording();
                    Log.i(LOG_TAG, "End recording OK");
                    Toast.makeText(CameraDemoActivity.this, "End Recording...", Toast.LENGTH_LONG).show();
                } else {
                    Log.i(LOG_TAG, "Start recording...");
                    mCameraSurfaceView.setClearColor(1.0f, 0.0f, 0.0f, 0.6f);
                    mCameraSurfaceView.startRecording(null);
                    Toast.makeText(CameraDemoActivity.this, "Start Recording...", Toast.LENGTH_LONG).show();
                }
            }
        });

          LinearLayout layout = (LinearLayout) findViewById(R.id.menuLinearLayout);

        for(int i = 0; i != FilterTypes.length; ++i) {
            MyButtons button = new MyButtons(this);
            button.filterType = FilterTypes[i];
            button.setText(FilterNames[i]);
            button.setOnClickListener(mFilterSwitchListener);
            layout.addView(button);
        }

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    mCameraSurfaceView.setIntensity(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

        Button switchButton = (Button)findViewById(R.id.switchBtn);
        switchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraSurfaceView.switchCamera();
            }
        });

        mCurrentInstance = this;
    }

    private View.OnClickListener mFilterSwitchListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            MyButtons btn = (MyButtons)v;
            mCameraSurfaceView.setFrameRenderer(btn.filterType);
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        System.exit(0);
    }

    @Override
    public void onPause() {
        super.onPause();
        System.exit(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_camera_demo, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
