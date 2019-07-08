package com.facedetector;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import com.facedetector.util.DeepLab;
import com.facedetector.util.ImageFusion;

/**
 * 检测图片中脸部的数量，同时检测至少27张脸
 * 检测视频中脸部的数量，同时检测至少10张脸，根据手机性能来决定
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int PICK_IMAGE_ALBUM = 0x12;
    private static final int REQUEST_PERMISSION = 0x1000;
    private ImageView iv;
    private HandlerThread handlerThread;
    private FaceHandler faceHandler;
    private View btnGallery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (faceHandler != null) {
            faceHandler.removeCallbacksAndMessages(null);
        }
        if (handlerThread != null) {
            handlerThread.quit();
        }
    }

    private void initViews() {
        btnGallery = findViewById(R.id.btn_de_face_image);
        btnGallery.setOnClickListener(this);
        findViewById(R.id.btn1).setOnClickListener(this);
        iv = findViewById(R.id.iv);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == PICK_IMAGE_ALBUM) {
            Uri uri = data.getData();
            if (handlerThread == null) {
                handlerThread = new HandlerThread("face");
                handlerThread.start();
                faceHandler = new FaceHandler(handlerThread.getLooper(), this);
            }
            faceHandler.detect(uri);
            btnGallery.setEnabled(false);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn1:
                FaceDetectorActivity.start(this);
                break;
            case R.id.btn_de_face_image:
                openAlbum();
                break;
        }
    }

    private void openAlbum() {
        String TAG="IMAGEFUSION: ";
        String dir= Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"Poster_Camera"+File.separator;
        String fn=dir+"IMG_20190702_2048521.jpg";
        try {
            byte[] origin,restruct;
            FileInputStream fs=new FileInputStream(fn);
            Bitmap bm= BitmapFactory.decodeStream(fs);
            int bytes=bm.getByteCount();
            ByteBuffer buffer=ByteBuffer.allocate(bytes);
            origin=buffer.array();
            fs.close();
            fn=dir+"IMG_20190702_2048532.jpg";
            File file=new File(fn);
            if (file.exists()){
                Log.i(TAG, "openAlbum: "+fn+" exists!");
            }
            fs=new FileInputStream(fn);
            bm= BitmapFactory.decodeStream(fs);
            bytes=bm.getByteCount();
            buffer=ByteBuffer.allocate(bytes);
            restruct=buffer.array();
            fs.close();
            DeepLab deepLab=new DeepLab(this,8);
            Log.i(TAG, "openAlbum: origin length: "+restruct.length);
            deepLab.setImageData(restruct);
            deepLab.runInference();
            Boolean[][] seg=deepLab.getTVSegment();
            ImageFusion imageFusion=new ImageFusion(origin,restruct,seg);
            imageFusion.fuseImg();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showResult(final Bitmap bitmap) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                iv.setImageBitmap(bitmap);
                btnGallery.setEnabled(true);
            }
        });
    }

    private void readGalleryError() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Something wrong happened when read gallery.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void beginDetect() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            }
        });
    }


    private static class FaceHandler extends Handler {
        private WeakReference<MainActivity> viewWeakReference;

        private WeakReference<Uri> uriWeakReference;

        FaceHandler(Looper looper, MainActivity activity) {
            super(looper);
            this.viewWeakReference = new WeakReference<>(activity);
        }

        void detect(Uri uri) {
            this.uriWeakReference = new WeakReference<>(uri);
            sendEmptyMessage(0);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            MainActivity activity = viewWeakReference.get();
            Uri uri = uriWeakReference.get();
            if (activity == null || uri == null) {
                return;
            }
            // get image path from gallery
            String imagePath = getPath(uri, activity.getContentResolver());
            if (imagePath == null) {
                activity.readGalleryError();
                return;
            }
            activity.beginDetect();
            Log.e("tag", " imagePath :" + imagePath);
            // begin detect
        }

        private String getPath(Uri uri, ContentResolver provider) {
            String[] projection = {MediaStore.Images.Media.DATA};
            Cursor cursor = provider.query(uri, projection, null, null, null);
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            int columnIndex = cursor.getColumnIndex(projection[0]);
            String imagePath = cursor.getString(columnIndex);
            cursor.close();
            return imagePath;
        }

    }

}