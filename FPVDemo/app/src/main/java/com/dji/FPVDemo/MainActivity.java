package com.dji.FPVDemo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.SparseArray;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.util.Timer;
import java.util.TimerTask;

import dji.common.product.Model;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

public class MainActivity extends Activity implements SurfaceTextureListener {

    private static final String TAG = MainActivity.class.getName();
    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;

    // Codec for video live view
    protected DJICodecManager mCodecManager = null;
    protected TextureView mVideoSurface = null;
    ImageView myImageView;
    private TextView mTxtNoOfFacesDetected;
    private Bitmap myBitmap;
    private Paint myRectPaint;
    TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            if (mVideoSurface != null) {
                myBitmap = mVideoSurface.getBitmap();
                processBitmap();
            }
        }
    };
    private Timer timer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
        timer = new Timer();

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if (mCodecManager != null) {
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }
            }
        };

    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initPreviewer();
        timer.schedule(timerTask, 0, 1000);
        if (mVideoSurface == null) {
            Log.e(TAG, "mVideoSurface is null");
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        uninitPreviewer();
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view) {
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        uninitPreviewer();
        super.onDestroy();
    }

    private void initUI() {
        // init mVideoSurface
        mVideoSurface = (TextureView) findViewById(R.id.video_previewer_surface);
        myImageView = (ImageView) findViewById(R.id.imgview);
        mTxtNoOfFacesDetected = (TextView) findViewById(R.id.txt_no_of_faces_count);

        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        myRectPaint = new Paint();
        myRectPaint.setStrokeWidth(5);
        myRectPaint.setColor(Color.RED);
        myRectPaint.setStyle(Paint.Style.STROKE);
    }

    private void initPreviewer() {

        BaseProduct product = FPVDemoApplication.getProductInstance();

        if (product == null || !product.isConnected()) {
            showToast(getString(R.string.disconnected));
        } else {
            if (null != mVideoSurface) {
                mVideoSurface.setSurfaceTextureListener(this);
            }
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(mReceivedVideoDataCallBack);
            }
        }
    }

    private void uninitPreviewer() {
        Camera camera = FPVDemoApplication.getCameraInstance();
        if (camera != null) {
            // Reset the callback
            VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(null);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG, "onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public Point getScreenSize(Context context) {
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        if (manager != null) {
            manager.getDefaultDisplay().getRealSize(size);
        }
        return size;
    }

    private void processBitmap() {
        if (myBitmap != null) {
            final Bitmap bitmap = Bitmap.createBitmap(
                    getScreenSize(this).x, // Width
                    getScreenSize(this).y, // Height
                    Bitmap.Config.ARGB_8888 // Config
            );
            Canvas canvas = new Canvas(bitmap);


            FaceDetector faceDetector = new
                    FaceDetector.Builder(getApplicationContext()).setTrackingEnabled(false)
                    .build();
            if (!faceDetector.isOperational()) {
                new AlertDialog.Builder(getApplicationContext()).setMessage("Could not set up the face detector!").show();
                return;
            }
            Frame frame = new Frame.Builder().setBitmap(myBitmap).build();
            final SparseArray<Face> faces = faceDetector.detect(frame);
            if (faces.size() == 0) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTxtNoOfFacesDetected.setText("" + 0);
                        myImageView.setImageBitmap(null);
                    }
                });
            } else if (faces.size() > 0) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTxtNoOfFacesDetected.setText(String.valueOf(faces.size()));
                    }
                });
            }
            for (int i = 0; i < faces.size(); i++) {

                Face thisFace = faces.valueAt(i);
                float x1 = thisFace.getPosition().x;
                float y1 = thisFace.getPosition().y;
                float x2 = x1 + thisFace.getWidth();
                float y2 = y1 + thisFace.getHeight();
                canvas.drawRoundRect(new RectF(x1, y1, x2, y2), 2, 2, myRectPaint);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        myImageView.setImageBitmap(bitmap);
                    }

                });


            }

        }

//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                myImageView.setImageDrawable(new BitmapDrawable(getResources(), tempBitmap));
//            }
//        });
//        myImageView.setImageDrawable(new BitmapDrawable(getResources(), tempBitmap));
//        textureView.draw(tempCanvas);]\
    }

}
