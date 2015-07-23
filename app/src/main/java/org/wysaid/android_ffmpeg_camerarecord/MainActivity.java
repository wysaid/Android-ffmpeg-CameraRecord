package org.wysaid.android_ffmpeg_camerarecord;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import org.wysaid.glfunctions.MyGLSurfaceView;


public class MainActivity extends Activity {

    private Button mTakePicBtn;
    private Button mRecordBtn;
    private MyGLSurfaceView mGLSurfaceView;

    public final static String LOG_TAG = MyGLSurfaceView.LOG_TAG;

    public static MainActivity mCurrentInstance = null;
    public static MainActivity getInstance() {
        return mCurrentInstance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTakePicBtn = (Button)findViewById(R.id.takeShotBtn);
        mRecordBtn = (Button)findViewById(R.id.recordBtn);
        mGLSurfaceView = (MyGLSurfaceView)findViewById(R.id.myGLSurfaceView);

        mTakePicBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(LOG_TAG, "Taking Picture...");
                mGLSurfaceView.post(new Runnable() {
                    @Override
                    public void run() {
                        mGLSurfaceView.takeShot();
                    }
                });
            }
        });

        mRecordBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        Log.i(LOG_TAG, "Start recording...");
                        mGLSurfaceView.setClearColor(1.0f, 0.0f, 0.0f, 1.0f);
                        mGLSurfaceView.startRecording();
                        break;
                    case MotionEvent.ACTION_UP:
                        Log.i(LOG_TAG, "End recording...");
                        mGLSurfaceView.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                        mGLSurfaceView.endRecording();
                        Log.i(LOG_TAG, "End recording OK");
                        break;
                }
                return true;
            }
        });

        mCurrentInstance = this;
    }

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
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
