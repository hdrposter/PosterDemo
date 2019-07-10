package com.facedetector;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
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


import com.facedetector.util.DeepLab;
import com.facedetector.util.ImageFusion;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

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
    public void onResume(){
        super.onResume();
        if (!OpenCVLoader.initDebug()){
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0,this,mLoadCallbake);
        }else {
            mLoadCallbake.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private BaseLoaderCallback mLoadCallbake =new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            super.onManagerConnected(status);
            switch (status){
                case LoaderCallbackInterface.SUCCESS:
                    Log.d("", "onManagerConnected: opencv-3.4.1 load success!");
                    break;
                default:{
                    super.onManagerConnected(status);
                }
            }
        }
    };

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
        findViewById(R.id.albumbtn).setOnClickListener(this);
        findViewById(R.id.btn1).setOnClickListener(this);
        findViewById(R.id.slidesbtn).setOnClickListener(this);
//        findViewById(R.id.button).setOnClickListener(this);
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
            case R.id.albumbtn:
                String path =  Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"Poster_Camera"+File.separator;
                openAlbum(path);
                break;
            case R.id.slidesbtn:
                System.out.println("slides btn clicked!");
                path =  Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"Poster_Camera"+File.separator+"PPT"+File.separator;
                openAlbum(path);
                break;
//            case R.id.button:
//                test();
        }
    }

//    private void test() {
//        String dir=Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"Poster_Camera"+File.separator;
//        String fn=dir+"IMG_20190702_2048521.jpg";
//        try {
//            FileInputStream fs=new FileInputStream(fn);
//            Bitmap bm=BitmapFactory.decodeStream(fs);
//            ByteArrayOutputStream bs=new ByteArrayOutputStream();
//            bm.compress(Bitmap.CompressFormat.JPEG,100,bs);
//            byte[] origin=bs.toByteArray();
//
//            fs.close();
//            bm.recycle();
//            bs.close();
//
//            fn=dir+"IMG_20190702_2048532.jpg";
//            fs=new FileInputStream(fn);
//            bm=BitmapFactory.decodeStream(fs);
//            bs=new ByteArrayOutputStream();
//            bm.compress(Bitmap.CompressFormat.JPEG,100,bs);
//            byte[] restruct=bs.toByteArray();
//
//            AssetManager assetManager=this.getAssets();
//            DeepLab deepLab=new DeepLab(assetManager);
//            Boolean[][] seg=deepLab.getTVSegment(origin);
//            ImageFusion imageFusion=new ImageFusion(origin,restruct,seg);
//            imageFusion.fuseImg();
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//    }

    private void openAlbum(String path) {
        Intent intent = new Intent(this,AlbumActivity.class);

        intent.putExtra("path",path);
        startActivity(intent);
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