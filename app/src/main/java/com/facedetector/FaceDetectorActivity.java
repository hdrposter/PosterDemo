package com.facedetector;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.File;

import okio.BufferedSink;
import okio.Okio;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.facedetector.customview.ActionView;
import com.facedetector.customview.DrawFacesView;
import com.facedetector.customview.FocusCircleView;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;

/**
 * <pre>
 *      author : zouweilin
 *      e-mail : zwl9517@hotmail.com
 *      time   : 2017/07/17
 *      version:
 *      desc   : 由于使用的是camera1，在P以上的版本可能无法使用
 * </pre>
 */
public class FaceDetectorActivity extends AppCompatActivity {

    private static final String TAG = FaceDetectorActivity.class.getSimpleName();
    private static final int REQUEST_CAMERA_CODE = 0x100;
    private SurfaceView surfaceView;
    private ImageView previewView;
    private ProgressBar loadView;
    private Camera mCamera;
    private SurfaceHolder mHolder;
    private DrawFacesView facesView;
    private FocusCircleView focusView;
    private ActionView actionView;
    private ImageButton imageButton;
    private int exposure;
    private String iso;
    private String mPath;
    private boolean isTakingPic;

    private ArrayList<byte[]> images; //按下快门后捕捉的照片

    private OrientationEventListener mOrientationListener; //方向监听
    private Direction direction = Direction.Up; //实时相机方向
    //四个方向
    private enum Direction{
        Up,Down,Left,Right
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, FaceDetectorActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        setContentView(R.layout.activity_face);
        images=new ArrayList<>();

        isTakingPic=false;
        mPath=Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"Poster_Camera"+File.separator;
        initViews();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_CODE);
                }
                return;
            }
            openSurfaceView();
        }

        //开启方向监听
        mOrientationListener = new OrientationEventListener(this,
                SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if(orientation>=0 && orientation <45 || orientation>=315){
                    direction = Direction.Up;
                }else if(orientation>=45 && orientation <135){
                    direction = Direction.Right;
                }else if(orientation>=135 && orientation <225){
                    direction = Direction.Down;
                }else if(orientation >=225 && orientation < 315){
                    direction = Direction.Left;
                }
                //Log.v(TAG,"Orientation changed to " + orientation+" Direction changed to "+direction);
            }
        };

        if (mOrientationListener.canDetectOrientation()) {
            Log.v(TAG, "Can detect orientation");
            mOrientationListener.enable();
        } else {
            Log.v(TAG, "Cannot detect orientation");
            mOrientationListener.disable();
        }
    }


    /**
     * 把摄像头的图像显示到SurfaceView
     */
    private void openSurfaceView() {
        mHolder = surfaceView.getHolder();
        mHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (mCamera == null) {
                    mCamera = Camera.open();
                    try {
                        mCamera.setFaceDetectionListener(new FaceDetectorListener());
                        mCamera.setPreviewDisplay(holder);
                        //startFaceDetection();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (mHolder.getSurface() == null) {
                    // preview surface does not exist
                    Log.e(TAG, "mHolder.getSurface() == null");
                    return;
                }

                try {
                    mCamera.stopPreview();

                } catch (Exception e) {
                    // ignore: tried to stop a non-existent preview
                    Log.e(TAG, "Error stopping camera preview: " + e.getMessage());
                }

                try {
                    mCamera.setPreviewDisplay(mHolder);
                    int measuredWidth = surfaceView.getMeasuredWidth();
                    int measuredHeight = surfaceView.getMeasuredHeight();
                    setCameraParms(mCamera, measuredWidth, measuredHeight);
                    mCamera.startPreview();

                    //startFaceDetection(); // re-start face detection feature

                } catch (Exception e) {
                    // ignore: tried to stop a non-existent preview
                    Log.d(TAG, "Error starting camera preview: " + e.getMessage());
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                // mCamera.setPreviewCallback(null);// 防止 Method called after release()
                mCamera.stopPreview();
                // mCamera.setPreviewCallback(null);
                mCamera.release();
                mCamera = null;
            }
        });
    }

    private void initViews() {
        surfaceView = (SurfaceView)this.findViewById(R.id.surfaceView);
        previewView = (ImageView) this.findViewById(R.id.previewView);
        loadView = (ProgressBar) this.findViewById(R.id.progressBar2);
        facesView = new DrawFacesView(this);
        focusView=new FocusCircleView(this);
        actionView = new ActionView(this);
        addContentView(focusView,new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.MATCH_PARENT));
        //addContentView(facesView, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        addContentView(actionView, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        imageButton=(ImageButton)this.findViewById(R.id.imageButton);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //saveFaceImages();
                if(actionView!=null){
                    actionView.show();
                }
                takePhoto();//拍照
            }
        });
        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float x=event.getX();
                float y=event.getY();
                setFocusArea(x,y);
                if (focusView!=null){
                    focusView.myViewScaleAnimation(focusView);
                    focusView.setPoint(x,y);
                }
                return true;
            }
        });
    }

    //设置特定的对焦、曝光区域
    private void setFocusArea(float x,float y){
        Rect focusRect=calculateTapArea(x,y,0.33333f);
        //Log.d(TAG, "onTouch: left: "+focusRect.left+", right: "+focusRect.right+", top: "+focusRect.top+", bottom: "+focusRect.bottom+", centerX: "+focusRect.centerX()+", centerY: "+focusRect.centerY());
        Rect meteringRect=calculateTapArea(x,y,1f);
        List<Camera.Area>mFocusList=new ArrayList<>();
        mFocusList.add(new Camera.Area(focusRect,1000));
        List<Camera.Area>mMeteringList=new ArrayList<>();
        mMeteringList.add(new Camera.Area(meteringRect,1000));
        final Camera.Parameters parameters=mCamera.getParameters();
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        Log.d(TAG, "setFocusArea: "+parameters.getSupportedFocusModes().toString());
        //mCamera.cancelAutoFocus();
        if (parameters.getMaxNumFocusAreas()>0){
            parameters.setFocusAreas(mFocusList);
        }
        if (parameters.getMaxNumMeteringAreas()>0){
            parameters.setMeteringAreas(mMeteringList);
        }

        try {
            mCamera.setParameters(parameters);
        }catch (RuntimeException e) {

            Log.d(TAG, "failed to set parameters");
            e.printStackTrace();
        }
        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if (success){
                    //takeExposurePic(parameters.getMinExposureCompensation());

                    Log.d(TAG, "onAutoFocus: auto focus success!");
                }
            }
        });
    }

    /**
     * 拍摄多张不同曝光度照片
     */
    private void takeExposurePic(final int exposure) {
        final Camera.Parameters parameters=mCamera.getParameters();
        final int maxExposure=parameters.getMaxExposureCompensation();
        final int minExposure=parameters.getMinExposureCompensation();
        Log.d(TAG, "takeExposurePic: max exposure: "+maxExposure+", min exposure: "+ minExposure+", current exposure: "+exposure);
        parameters.setExposureCompensation(exposure);
        mCamera.setParameters(parameters);
        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                if (data.length>0){
                    images.add(data);//捕捉当前照片

                    //当前第一张，作为预览
                    if(exposure ==minExposure){
                        //Bitmap bm = rotateImageBitmap(data,direction);

                        //setPreview(bm);
                    }
                    //Log.d(TAG, "onPictureTaken: 已添加 "+images.size()+" 张脸部图片");
                    /*while (images.size()>10){
                        Log.d(TAG, "onPictureTaken: 多于5张了，移除之前的");
                        images.remove(images.size()-1);
                    }*/
                }
                mCamera.startPreview();

                int nextExposure=exposure+(maxExposure-minExposure)/2;
                if (nextExposure<=maxExposure){
                    takeExposurePic(nextExposure);
                }else{//拍摄结束
                    parameters.setExposureCompensation(0); //恢复曝光补偿
                    mCamera.setParameters(parameters);
                    //关闭预览
                    Log.d(TAG,"关闭loading");
                    loadView.setVisibility(View.INVISIBLE);
                    //previewView.setVisibility(View.INVISIBLE);

                    //保存图片
                    saveFaceImages();
                }

            }
        });
    }

    /****
     * 计算对应的聚焦和曝光区域
     * @param x
     * @param y 聚焦中心坐标
     * @param v 区域缩放系数
     */
    private Rect calculateTapArea(float x, float y, float v) {
        int FOCUS_AREA_SIZE=300;
        int areaSize=Float.valueOf(FOCUS_AREA_SIZE*v).intValue();
      /*  Rect rect=new Rect((int)x-areaSize,(int)y-areaSize,(int)x+areaSize,(int)y+areaSize);
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        Canvas canvas=new Canvas();
        canvas.drawRect(rect,paint);
        surfaceView.draw(canvas);*/
        int centerx=(int)x*2000/surfaceView.getWidth()-1000;
        int centery=(int)y*2000/surfaceView.getHeight()-1000;
        Log.d(TAG, "calculateTapArea: x: "+x+", y: "+y+", areaSize: "+areaSize);
        int left=centerx-areaSize;//rect.left*2000/surfaceView.getWidth()-1000;
        int top=centery-areaSize;//rect.top*2000/surfaceView.getHeight()-1000;
        int right=centerx+areaSize;//rect.right*2000/surfaceView.getWidth()-1000;
        int bottom=centery+areaSize;//rect.bottom*2000/surfaceView.getHeight()-1000;
        left=left<-1000?-1000:left;
        right=right>1000?1000:right;
        top=top<-1000?-1000:top;
        bottom=bottom>1000?1000:bottom;
        return new Rect(left,top,right,bottom);
    }

    private void saveFaceImages() {
        //mCamera.stopPreview();
        String fileName=mPath;
        Log.d(TAG, "onClick: 存储文件夹："+mPath);
        File dir=new File(mPath);
        if (!dir.exists()){
            dir.mkdir();
            //Log.d(TAG, "onClick: 已创建文件夹");
        }
        //Log.d(TAG, "onClick: 文件夹已创建："+dir.exists());
        for (int i=0;i<images.size();i++){
            fileName=fileName+"IMG_";
            String timeStamp=(new SimpleDateFormat("yyyyMMdd_HHmmss")).format(new Date());
            fileName=fileName+timeStamp+i+".jpg";
            Log.d(TAG, "onClick: 文件名："+fileName);
            Log.d(TAG, "onClick: 保存第 "+(i+1)+"/"+images.size()+" 张图片");
            try {
                File imageFile=new File(fileName);
                if (!imageFile.exists()) {
                    imageFile.createNewFile();
                }
                if (imageFile.exists()){
                    Log.d(TAG, "saveFaceImages: "+fileName+"创建成功");
                }
                BufferedSink bs=Okio.buffer(Okio.sink(imageFile));
                //调整方向后存储照片

                bs.write(rotateImage(images.get(i),direction));

                Log.d(TAG, "onClick: 保存路径："+fileName);
                bs.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            fileName=mPath;
        }
        //清空缓存照片
        images = new ArrayList<>();
    }

    //旋转图片 返回byte[]
    private byte[] rotateImage(byte[] img, Direction direction){

        Bitmap newImg = rotateImageBitmap(img,direction);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        newImg.compress(Bitmap.CompressFormat.JPEG, 100, baos);

        newImg = null;

        return baos.toByteArray();
    }

    //旋转照片 返回bitmap
    private Bitmap rotateImageBitmap(byte[] img,Direction direction){
        Bitmap bitmap = BitmapFactory.decodeByteArray(img,0,img.length);
        if (bitmap == null)
            return null;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Matrix mtx = new Matrix();

        switch (direction){
            case Up:
                mtx.postRotate(90);
                break;
            case Left:
                mtx.postRotate(0);
                break;
            case Right:
                mtx.postRotate(180);
                break;
            case Down:
                mtx.postRotate(-90);
                break;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
    }

    /*正式拍照*/
    private void takePhoto(){

        final Camera.Parameters parameters=mCamera.getParameters();
        final int minExpose = parameters.getMinExposureCompensation();
        final int maxExpose = parameters.getMaxExposureCompensation();
        final int medianExpose = 0;

        loadView.setVisibility(View.VISIBLE);
        takeExposurePic(minExpose);

    }

    //设置拍照瞬间预览
    private void setPreview(Bitmap img){
        previewView.setVisibility(View.VISIBLE);
        previewView.setImageBitmap(img);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                recreate();
            } else {
                finish();
            }
        }
    }

    /**
     * 脸部检测接口
     */
    private class FaceDetectorListener implements Camera.FaceDetectionListener {
        @Override
        public void onFaceDetection(Camera.Face[] faces, Camera camera) {
            if (faces.length > 0) {
                Camera.Face face = faces[0];
                Rect rect = face.rect;
                float x=(rect.centerX()+1000)*surfaceView.getWidth()/2000;
                float y=(rect.centerY()+1000)*surfaceView.getHeight()/2000;
                setFocusArea(x,y);
                Log.d("FaceDetection", "confidence：" + face.score + "face detected: " + faces.length +
                        " Face 1 Location X: " + rect.centerX() +
                        "Y: " + rect.centerY() + "   " + rect.left + " " + rect.top + " " + rect.right + " " + rect.bottom);
                Matrix matrix = updateFaceRect();
                facesView.updateFaces(matrix, faces);
                if (!isTakingPic){
                    doTakePic();
                    isTakingPic=true;
                }
                //thread.quitSafely();
            } else {
                // 只会执行一次
                Log.e("tag", "【FaceDetectorListener】类的方法：【onFaceDetection】: " + "没有脸部");
                facesView.removeRect();
            }
        }
    }

    //FaceDetector capture
    private void doTakePic() {
        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                if (data.length>0){
                   images.add(data);
                    Log.d(TAG, "onPictureTaken: 已添加 "+images.size()+" 张脸部图片");
                    while (images.size()>5){
                        Log.d(TAG, "onPictureTaken: 多于5张了，移除之前的");
                        images.remove(images.size()-1);
                    }
                }
                mCamera.startPreview();
            }
        });
    }

    /**
     * 因为对摄像头进行了旋转，所以同时也旋转画板矩阵
     * 详细请查看{@link Camera.Face#rect}
     * @return
     */
    private Matrix updateFaceRect() {
        Matrix matrix = new Matrix();
        Camera.CameraInfo info = new Camera.CameraInfo();
        // Need mirror for front camera.
        boolean mirror = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
        matrix.setScale(mirror ? -1 : 1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        matrix.postRotate(90);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        matrix.postScale(surfaceView.getWidth() / 2000f, surfaceView.getHeight() / 2000f);
        matrix.postTranslate(surfaceView.getWidth() / 2f, surfaceView.getHeight() / 2f);
        return matrix;
    }

    public void startFaceDetection() {
        // Try starting Face Detection
        Camera.Parameters params = mCamera.getParameters();
        // start face detection only *after* preview has started
        if (params.getMaxNumDetectedFaces() > 0) {
            // mCamera supports face detection, so can start it:
            try {
                mCamera.startFaceDetection();
            } catch (Exception e) {
                e.printStackTrace();
                // Invoked this method throw exception on some devices but also can detect.
            }
        } else {
            Toast.makeText(this, "Device not support face detection", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 在摄像头启动前设置参数
     *
     * @param camera
     * @param width
     * @param height
     */
    private void setCameraParms(Camera camera, int width, int height) {
        // 获取摄像头支持的pictureSize列表
        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> pictureSizeList = parameters.getSupportedPictureSizes();
        // 从列表中选择合适的分辨率
        Camera.Size pictureSize = getProperSize(pictureSizeList, (float) height / width);
        if (null == pictureSize) {
            pictureSize = parameters.getPictureSize();
        }
        // 根据选出的PictureSize重新设置SurfaceView大小
        float w = pictureSize.width;
        float h = pictureSize.height;
        parameters.setPictureSize(pictureSize.width, pictureSize.height);

        //surfaceView.setLayoutParams(new RelativeLayout.LayoutParams((int) (height * (h / w)), height));

        // 获取摄像头支持的PreviewSize列表
        List<Camera.Size> previewSizeList = parameters.getSupportedPreviewSizes();
        Camera.Size preSize = getProperSize(previewSizeList, (float) height / width);
        if (null != preSize) {
            parameters.setPreviewSize(preSize.width, preSize.height);
        }
        parameters.setJpegQuality(100);
        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            // 连续对焦
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        camera.cancelAutoFocus();
        camera.setDisplayOrientation(90);
        camera.setParameters(parameters);
    }

    private Camera.Size getProperSize(List<Camera.Size> pictureSizes, float screenRatio) {
        Camera.Size result = null;
        for (Camera.Size size : pictureSizes) {
            float currenRatio = ((float) size.width) / size.height;
            if (currenRatio - screenRatio == 0) {
                result = size;
                break;
            }
        }
        if (null == result) {
            for (Camera.Size size : pictureSizes) {
                float curRatio = ((float) size.width) / size.height;
                if (curRatio == 4f / 3) {
                    result = size;
                    break;
                }
            }
        }
        return result;
    }

}
