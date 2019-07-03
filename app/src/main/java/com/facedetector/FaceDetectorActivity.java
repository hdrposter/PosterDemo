package com.facedetector;

import android.Manifest;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Environment;

import java.io.ByteArrayOutputStream;
import java.io.File;

import okio.BufferedSink;
import okio.Okio;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.Semaphore;

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

    //UI相关
    private SurfaceView surfaceView;
    private ImageView previewView; //拍照瞬间遮罩视图
    private ProgressBar loadView; //Loading
    private ImageView imageView; //拍照左下角缩略图
    private DrawFacesView facesView;
    private FocusCircleView focusView; //对焦框
    private ActionView actionView; //拍照动画
    private ImageButton imageButton; //相机快门

    // camera2
    private CameraManager mCameraManager;
    private String mCameraId;
    //private AutoFitTextureView mTextureView;
    private TextureView mTextureView;
    //private SurfaceView mTextureView;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCaptureSession;
    private Size mPreviewSize;
    private int mWidth;
    private int mHeight;

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            //openCamera(width, height);
            mWidth = width;
            mHeight = height;
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            //configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                //获取CameraDevice
                mCameraDevice = cameraDevice;
                createCameraPreviewSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                //关闭CameraDevice
                cameraDevice.close();
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int error) {
                //关闭CameraDevice
                cameraDevice.close();
            }
        };

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;
    private File mFile;

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            String fileName=mPath+"IMG_";
            String timeStamp=(new SimpleDateFormat("yyyyMMdd_HHmmss")).format(new Date());
            fileName=fileName+timeStamp+".jpg";
            mFile = new File(fileName);

            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }

    };

    private void setupImageReader() {
        //前三个参数分别是需要的尺寸和格式，最后一个参数代表每次最多获取几帧数据，本例的2代表ImageReader中最多可以获取两帧图像流
        mImageReader = ImageReader.newInstance(mWidth, mHeight,
                ImageFormat.JPEG, 2);
        //监听ImageReader的事件，当有图像流数据可用时会回调onImageAvailable方法，它的参数就是预览帧数据，可以对这帧数据进行处理
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);
    }


    private int mState = STATE_PREVIEW;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private boolean mFlashSupported;
    private int mSensorOrientation;

    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            //对焦完成
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    private void createCameraPreviewSession() {
        //setupImageReader();
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            //texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

           Surface surface = new Surface(texture);

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            //创建一个CameraCaptureSession来进行相机预览
            mCameraDevice.createCaptureSession(Arrays.asList(surface,mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // 相机已经关闭
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                //setAutoFlash(mPreviewRequestBuilder);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                                Log.e(TAG," 开启相机预览并添加事件");
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG," onConfigureFailed 开启预览失败");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureStillPicture() {
        try {
            if ( null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(direction));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {

                    unlockFocus();
                    finishTakePhoto();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }



    private Camera mCamera;
    private SurfaceHolder mHolder;
    private int exposure;
    private String iso;
    private String mPath;
    private boolean isTakingPic;
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



    /* Camera 2 Begin */

    private void setupCamera(){
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try{
            final String[] ids = mCameraManager.getCameraIdList();
            int numberOfCameras = ids.length;

            for(String id: ids){
                final CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);

                final int facing_orientation = characteristics.get(CameraCharacteristics.LENS_FACING);

                if(facing_orientation == CameraCharacteristics.LENS_FACING_FRONT){
                    continue;
                }

                //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                Log.d(TAG,"mWidth:"+mWidth+" mHeight:"+mHeight);
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());
                Log.d(TAG,"Width:"+largest.getWidth()+" Height:"+largest.getHeight());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, null);

                //noinspection ConstantConditions
//                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
//
//                Point displaySize = new Point();
//                getWindowManager().getDefaultDisplay().getSize(displaySize);
//                int rotatedPreviewWidth = mWidth;
//                int rotatedPreviewHeight = mHeight;
//                int maxPreviewWidth = displaySize.x;
//                int maxPreviewHeight = displaySize.y;
//
//                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
//                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
//                }
//
//                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
//                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
//                }
//
//                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
//                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
//                        maxPreviewHeight, largest);
//                RelativeLayout.LayoutParams Params = (RelativeLayout.LayoutParams) surfaceView.getLayoutParams();
//                Params.width = mPreviewSize.getWidth();
//                Params.height = mPreviewSize.getHeight();
//                Log.d(TAG,"preview size width:"+Params.width+" height:"+Params.height);
//                //(int)(previewRatio *width);
//
//                mTextureView.setLayoutParams(Params);

                mCameraId = id;
                break;
            }



        }catch(Exception e){
            Log.e(TAG,"Error during camera initialize");
        }
    }

    private void openCamera(){
        setupCamera();
        try {

            //检查权限
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            //打开相机
            mCameraManager.openCamera(mCameraId,mStateCallback,mBackgroundHandler);
        }catch(CameraAccessException e){
            e.printStackTrace();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        //closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    //旋转角度
    private int getOrientation(Direction direction){
        switch (direction){
            case Up:
                return 90;
            case Left:
                return 0;
            case Right:
                return 180;
            case Down:
                return 270;
        }
        return 0;
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }


    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }



    /* old Camera API*/

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
        mTextureView = (TextureView)this.findViewById(R.id.textureView);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        previewView = (ImageView) this.findViewById(R.id.previewView);
        loadView = (ProgressBar) this.findViewById(R.id.progressBar2);
        imageView = (ImageView) this.findViewById(R.id.imageView);

        facesView = new DrawFacesView(this);
        focusView=new FocusCircleView(this);
        actionView = new ActionView(this);
        addContentView(focusView,new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.MATCH_PARENT));
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
       mTextureView.setOnTouchListener(new View.OnTouchListener() {
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
    }

    //设置特定的对焦、曝光区域
    private void setFocusArea(float x,float y){
        try{
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);

        }catch (CameraAccessException e){
            e.printStackTrace();
        }
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

                        setPreview(bm);
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
                    //关闭预览
                    loadView.setVisibility(View.INVISIBLE);
                    previewView.setVisibility(View.INVISIBLE);

                    //保存图片
                    SavePictureTask saveTask = new SavePictureTask();
                    saveTask.execute();
                    //saveFaceImages();
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

        int centerx=(int)x*2000/mTextureView.getWidth()-1000;
        int centery=(int)y*2000/mTextureView.getHeight()-1000;
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
        lockFocus();

        loadView.setVisibility(View.VISIBLE);

    }
    /*拍照结束*/
    private void finishTakePhoto(){
        loadView.setVisibility(View.INVISIBLE);
    }

    //设置拍照瞬间预览
    private void setPreview(Bitmap img){
        focusView.setVisibility(View.INVISIBLE);
        previewView.setVisibility(View.VISIBLE);
        previewView.setImageBitmap(img);
        imageView.setImageBitmap(img);
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
}
