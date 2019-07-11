package com.facedetector;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.content.res.AssetManager;
import android.os.Environment;

import java.io.ByteArrayOutputStream;
import java.io.File;

import okio.BufferedSink;
import okio.Okio;

import java.io.FileInputStream;
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
import android.os.SystemClock;
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
import android.widget.Toast;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.facedetector.customview.ActionView;
import com.facedetector.customview.FocusCircleView;
import com.facedetector.util.DeepLab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.facedetector.util.ImageFusion;
import com.facedetector.util.PPTCorrector;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.LoaderCallbackInterface;

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

    private DeepLab deeplab;

    //UI相关
    private SurfaceView surfaceView;
    private Context context;
    private ImageView previewView; //拍照瞬间遮罩视图
    private ProgressBar loadView; //Loading
    private ImageView imageView;//拍照左下角缩略图
    private ImageView pptView;//ppt缩略图
    private FocusCircleView focusView; //对焦框
    private ActionView actionView; //拍照动画
    private ImageButton imageButton; //相机快门

    private Camera mCamera;
    private SurfaceHolder mHolder;
    boolean processing;
    private String mPath;
    private String previewPath;
    private String pptPath;
    private int currPic; //当前拍摄

    private ArrayList<byte[]> images; //按下快门后捕捉的照片
    private ArrayList<Direction> imageDirections;

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
        imageDirections = new ArrayList<>();
        mPath=Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"Poster_Camera"+File.separator;
        context=this.getApplicationContext();
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

        // 载入 DeepLab
        deeplab = new DeepLab(getAssets());
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
                    Log.d(TAG, "onManagerConnected: opencv-3.4.1 load success!");
                    break;
                default:{
                    super.onManagerConnected(status);
                }
            }
        }
    };

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
                        //mCamera.setFaceDetectionListener(new FaceDetectorListener());
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
        imageView = (ImageView) this.findViewById(R.id.imageView);
        pptView=(ImageView)this.findViewById(R.id.imageView2);

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
                    focusView.setVisibility(View.VISIBLE);
                    focusView.myViewScaleAnimation(focusView);
                    focusView.setPoint(x,y);
                }
                return true;
            }
        });



        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,"touch");
                if (previewPath!=null){
                   Intent intent = new Intent(FaceDetectorActivity.this, PreviewActivity.class);
                    intent.putExtra("path",previewPath);
                    startActivity(intent);
                }

            }
        });

        pptView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pptPath!=null){
                    Intent intent = new Intent(FaceDetectorActivity.this, PreviewActivity.class);
                    intent.putExtra("path",pptPath);
                    startActivity(intent);
                }
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
    private void takeExposurePic() {
        final Camera.Parameters parameters=mCamera.getParameters();
        final int maxExposure=parameters.getMaxExposureCompensation();
        final int minExposure=parameters.getMinExposureCompensation();
        Log.d(TAG, "takeExposurePic: max exposure: "+maxExposure+", min exposure: "+ minExposure);
        if(currPic==0) {
            parameters.setExposureCompensation(0);
            Log.d(TAG, "currExposure:0");
        }else if(currPic==1){
            parameters.setExposureCompensation(minExposure);
            Log.d(TAG, "currExposure:"+minExposure);
        }else{
            parameters.setExposureCompensation(maxExposure);
            Log.d(TAG, "currExposure:"+maxExposure);
        }

        mCamera.setParameters(parameters);

        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                if (data.length>0){
                    images.add(data);//捕捉当前照片
                    imageDirections.add(direction);
                    //当前第一张，作为预览
                    if(currPic==0){
                        Bitmap bm = rotateImageBitmap(data,Direction.Up);
//                        setPreview(bm);
                    }

                }
                mCamera.startPreview();

                currPic += 1;

                if (currPic<=2){
                    takeExposurePic();
                }else{//拍摄结束
                    parameters.setExposureCompensation(0); //恢复曝光补偿
                    mCamera.setParameters(parameters);
                    //关闭预览
                    loadView.setVisibility(View.INVISIBLE);
                    previewView.setVisibility(View.INVISIBLE);

                    //保存图片
                    SavePictureTask saveTask = new SavePictureTask();
                    saveTask.execute();
                }

            }
        });
    }


    private void logTime(String info){
        Log.d(TAG,info+new Date().getTime());
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
        logTime("setParameter:");
        SystemClock.sleep(1000);
        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                logTime("pictureTaken:");
                if (data.length>0){
                    images.add(data);//捕捉当前照片
                    imageDirections.add(direction);
                    //当前第一张，作为预览
                    if(exposure ==minExposure){
                        Bitmap bm = rotateImageBitmap(data,Direction.Up);
//                        setPreview(bm);
                    }
                    //Log.d(TAG, "onPictureTaken: 已添加 "+images.size()+" 张脸部图片");
                    /*while (images.size()>10){
                        Log.d(TAG, "onPictureTaken: 多于5张了，移除之前的");
                        images.remove(images.size()-1);
                    }*/
                }
                mCamera.startPreview();

                //int nextExposure=exposure+(maxExposure-minExposure)/2;

                if (exposure<maxExposure){
                    takeExposurePic(maxExposure);
                }else{//拍摄结束
                    parameters.setExposureCompensation(0); //恢复曝光补偿
                    mCamera.setParameters(parameters);
                    Toast.makeText(context,"拍摄完成，处理中，请稍等...",Toast.LENGTH_LONG).show();
                    List<String>imgs=picturehandler();
                    try {
                        FileInputStream fs=new FileInputStream(imgs.get(0));
                        Bitmap pptbmp=BitmapFactory.decodeStream(fs);
                        fs.close();
                        fs=new FileInputStream(imgs.get(1));
                        Bitmap fusedbmp=BitmapFactory.decodeStream(fs);
                        Bitmap[] bm={fusedbmp,pptbmp};
                        setPreview(bm);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //关闭预览
                    loadView.setVisibility(View.INVISIBLE);
                    previewView.setVisibility(View.INVISIBLE);


                    //保存图片
                    //SavePictureTask saveTask = new SavePictureTask();
                    //saveTask.execute();
                    //saveFaceImages();
                }

            }
        });
    }

    public List<String> picturehandler() {
        List<String> imgs=new ArrayList<>();

        PPTCorrector corrector=new PPTCorrector(images.get(0));
        String ppt=corrector.correction(corrector.getOriginImg());
        pptPath=ppt;
        imgs.add(ppt);

        Toast.makeText(this.getApplicationContext(),"PPT",Toast.LENGTH_LONG).show();
        try {
            AssetManager assetManager=this.getAssets();
            DeepLab deepLab=new DeepLab(assetManager);
            Boolean[][] seg=deepLab.getTVSegment(images.get(0));
            ImageFusion imageFusion=new ImageFusion(images.get(0),images.get(1),seg);
            String result=imageFusion.fuseImg();
            previewPath=result;
            imgs.add(result);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return imgs;
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

                bs.write(rotateImage(images.get(i),imageDirections.get(i)));
                previewPath = fileName;

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
        imageDirections = new ArrayList<>();
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

        loadView.setVisibility(View.VISIBLE);

        currPic = 0;
        takeExposurePic(0);
        //takeExposurePic();
    }

    //设置拍照瞬间预览
    private void setPreview(Bitmap[] img){
        focusView.setVisibility(View.INVISIBLE);
//        previewView.setVisibility(View.VISIBLE);
//        previewView.setImageBitmap(img[0]);
        imageView.setImageBitmap(img[0]);
        pptView.setImageBitmap(img[1]);
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
     * 在摄像头启动前设置参数
     *
     * @param camera
     * @param width
     * @param height
     */
    private void setCameraParms(Camera camera, int width, int height) {

        Camera.Parameters parameters = camera.getParameters();

        // 获取摄像头支持的PreviewSize列表
       List<Camera.Size> previewSizeList = parameters.getSupportedPreviewSizes();
        Camera.Size preSize = getProperSize(previewSizeList, (float) height / width);
        Log.d(TAG,"preview size:"+preSize.width+" "+preSize.height);
        if (null != preSize) {
            Log.d(TAG,"preview size:"+preSize.width+" "+preSize.height);
            parameters.setPreviewSize(preSize.width, preSize.height);
        }

        List<Camera.Size> pictureSizeList = parameters.getSupportedPictureSizes();
        float previewRatio = (float)preSize.width / preSize.height;

        Camera.Size pictureSize = getProperSize(pictureSizeList, previewRatio,true);
        if (null == pictureSize) {
            pictureSize = parameters.getPictureSize();
        }
        // 根据选出的PictureSize重新设置SurfaceView大小
        float w = pictureSize.width;
        float h = pictureSize.height;
        Log.d(TAG,"picture size:"+pictureSize.width+" "+pictureSize.height);
        parameters.setPictureSize(pictureSize.width, pictureSize.height);

        parameters.setJpegQuality(100);
        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            // 连续对焦
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        camera.cancelAutoFocus();
        camera.setDisplayOrientation(90);
        camera.setParameters(parameters);

        RelativeLayout.LayoutParams Params = (RelativeLayout.LayoutParams) surfaceView.getLayoutParams();
        Params.width = width;
        Params.height = (int)(previewRatio *width);

        surfaceView.setLayoutParams(Params);
        previewView.setLayoutParams(Params);

    }

    private Camera.Size getProperSize(List<Camera.Size> pictureSizes, float screenRatio){
        return getProperSize(pictureSizes,screenRatio,false);
    }

    private Camera.Size getProperSize(List<Camera.Size> pictureSizes, float screenRatio,boolean equal) {
        Camera.Size result = null;
        float minDiff = 1000;
        float maxWidth = 0;
        for (Camera.Size size : pictureSizes) {
            //Log.d(TAG, "size:" + size.width + " " + size.height);
            float currenRatio = ((float) size.width) / size.height;
            //Log.d(TAG, "currenRatio:" + currenRatio + " screenRatio:" + screenRatio + " maxWdith:" + maxWidth);

            if (equal) {
                if (currenRatio - screenRatio ==0 && size.width > maxWidth) {
                    result = size;
                    maxWidth = size.width;
                    minDiff = Math.abs(currenRatio - screenRatio);
                    //Log.d(TAG, "minDiff:" + minDiff);

                }
            } else {
                if (Math.abs(currenRatio - screenRatio) <= minDiff && size.width > maxWidth) {
                    result = size;
                    maxWidth = size.width;
                    minDiff = Math.abs(currenRatio - screenRatio);
                    //Log.d(TAG, "minDiff:" + minDiff);

                }
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

    private class SavePictureTask extends AsyncTask<byte[],String,String>{
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }
        @Override
        protected String doInBackground(byte[]... params) {
            Log.d("=======","background");
            saveFaceImages();
            return null;
        }
    }

    private class DeepLabInferenceTask extends AsyncTask<byte[], Void, Boolean[][]> {

        @Override
        protected Boolean[][] doInBackground(byte[]... bytes) {
            Log.v(TAG, "call DeepLab function");
            return deeplab.getTVSegment(bytes[0]);
        }

        @Override
        protected void onPostExecute(Boolean[][] isTV) {
            // TODO 处理 isTV 分割结果
            int x = 0;
            for (Boolean[] booleans : isTV) {
                for (Boolean b : booleans) {
                    if (b)
                        x++;
                }
            }
        }
    }

    private class pptCorrectionTask extends AsyncTask<byte[],byte[], List<String>>{
        @Override
        protected void onPreExecute(){
            super.onPreExecute();
        }

        @Override
        protected List<String > doInBackground(byte[]... bytes) {

            return picturehandler();
        }

        @Override
        protected void onPostExecute(List<String> imgs) {
            try {
                FileInputStream fs=new FileInputStream(imgs.get(0));
                Bitmap pptbmp=BitmapFactory.decodeStream(fs);
                fs.close();
                fs=new FileInputStream(imgs.get(1));
                Bitmap fusedbmp=BitmapFactory.decodeStream(fs);
                Bitmap[] bm={fusedbmp,pptbmp};
                setPreview(bm);
            } catch (Exception e) {
                e.printStackTrace();
            }
            processing=false;
        }
    }
}
