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
import android.util.Range;
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
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCaptureSession;
    private CameraCharacteristics mCharacteristics;
    private Size mPreviewSize;
    private Size mCaptureSize;
    private int mWidth;
    private int mHeight;
    private int currPic=0;

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
    private int fileTail = 0;

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "*********Image Available!*******");
            String fileName = mPath + "IMG_";
            String timeStamp = (new SimpleDateFormat("yyyyMMdd_HHmmss")).format(new Date());
            fileName = fileName + timeStamp + fileTail + ".jpg";
            fileTail += 1;
            fileTail = fileTail % 10;
            mFile = new File(fileName);
            //File saveFile = new File(fileName);
            Log.d(TAG, "Save file:" + fileName);
            //ImageSaver saver = new ImageSaver(reader.acquireNextImage(), saveFile);
            //save
            //saver.run();
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));

        /* try {
                File imageFile=new File(fileName);
                if (!imageFile.exists()) {
                    imageFile.createNewFile();
                }
                if (imageFile.exists()){
                    Log.d(TAG, "saveFaceImages: "+fileName+"创建成功");
                }
                BufferedSink bs=Okio.buffer(Okio.sink(imageFile));

                ByteBuffer buffer = reader.acquireNextImage().getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                bs.write(bytes);

                Log.d(TAG, "onClick: 保存路径："+fileName);
                bs.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
*/
        }

    };


    private int mState = STATE_PREVIEW;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private boolean mFlashSupported;
    private int mSensorOrientation;

    private CameraCaptureSession.CaptureCallback mCaptureCallback2
            = new CameraCaptureSession.CaptureCallback() {


        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {

            switch (mState) {
                case STATE_PREVIEW:
                    break;
                case STATE_WAITING_LOCK:
                    Log.d(TAG, "Taking photo");
                    captureStillPicture();
                    //等待对焦
                    /*Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_LOCKED ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                                aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) {
                            mState = STATE_PICTURE_TAKEN;
                            //对焦完成
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }*/
                    break;

            }
        }

    };
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Log.d(TAG,"STATE_WAITING_LOCK");
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_LOCKED ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                                aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) {
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

                    Log.d(TAG,"STATE_WAITING_PRECAPTURE");
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

                    Log.d(TAG,"aeState:"+aeState);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_LOCKED ||
                            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                            aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Log.d(TAG,"STATE_WAITING_NON_PRECAPTURE");
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
            //Log.d(TAG,"mCaptureCallback->onCaptureCompleted");
            process(result);
        }

    };

    private void createCameraPreviewSession() {
        //setupImageReader();
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            Surface surface = new Surface(texture);

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            //创建一个CameraCaptureSession来进行相机预览
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
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
                                        mCaptureCallback, null);
                                Log.e(TAG, " 开启相机预览并添加事件");
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, " onConfigureFailed 开启预览失败");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void lockFocus() {
        try {
            Log.d(TAG,"LOCKFCOUS");
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
          /*  mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
           double compensationSteps = mCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).doubleValue();
            Range<Integer> range = mCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            int maxRange = range.getUpper();
            int minRange = range.getLower();
            int exposureCompensation = (int) (4.0 / compensationSteps);
            Log.d(TAG, "exposure step:" + compensationSteps + " range:" + range.toString() + "range max:" + maxRange);
            Log.d(TAG, "Exposure compensation:" + exposureCompensation);

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensation);*/

            // Tell #mCaptureCallback to wait for the lock.


            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void runPrecaptureSequence() {
        Log.d(TAG,"runPrecaptureSequence");
        try {
            // This is how to tell the camera to trigger.
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
            //        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureStillPicture() {
        Log.d(TAG,"CaptureStillPicture");
        try {
            if (null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);

            //setAutoFlash(captureBuilder);
            List<CaptureRequest> captureRequests = new ArrayList<>();

            //降噪
            captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);

            // Orientation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(direction));
            Log.d(TAG,"CurrPic:"+currPic);
            if(currPic==0){
                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,new Long(2000000));
                Log.d(TAG,"Set exposure time 2");
            }else if(currPic ==1){
                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,new Long(100000000));
                Log.d(TAG,"Set exposure time 2");
            }
            captureRequests.add(captureBuilder.build());

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
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


            Log.d(TAG, "CaptureRequests:" + captureRequests.toString());
            mCaptureSession.captureBurst(captureRequests, CaptureCallback, null);
            //mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mState = STATE_PREVIEW;
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

            //setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.

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
                Log.d(TAG, "Save fail");
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

    private ArrayList<byte[]> images; //按下快门后捕捉的照片
    private ArrayList<Direction> imageDirections;

    private OrientationEventListener mOrientationListener; //方向监听
    private Direction direction = Direction.Up; //实时相机方向

    //四个方向
    private enum Direction {
        Up, Down, Left, Right
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
        images = new ArrayList<>();
        imageDirections = new ArrayList<>();

        mPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "Poster_Camera" + File.separator;
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
                if (orientation >= 0 && orientation < 45 || orientation >= 315) {
                    direction = Direction.Up;
                } else if (orientation >= 45 && orientation < 135) {
                    direction = Direction.Right;
                } else if (orientation >= 135 && orientation < 225) {
                    direction = Direction.Down;
                } else if (orientation >= 225 && orientation < 315) {
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

    private void setupCamera() {
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            final String[] ids = mCameraManager.getCameraIdList();
            int numberOfCameras = ids.length;

            for (String id : ids) {
                final CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);

                final int facing_orientation = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (facing_orientation == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                Log.d(TAG, "mWidth:" + mWidth + " mHeight:" + mHeight);

                //根据TextureView的尺寸设置预览尺寸
                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), mWidth, mHeight);
                //获取相机支持的最大拍照尺寸
                mCaptureSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new Comparator<Size>() {
                    @Override
                    public int compare(Size lhs, Size rhs) {
                        return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getHeight() * rhs.getWidth());
                    }
                });

                Log.d(TAG, "Preview size:" + mPreviewSize.getHeight() + " " + mPreviewSize.getWidth());
                Log.d(TAG, "Capture size:" + mCaptureSize.getHeight() + " " + mCaptureSize.getWidth());
                setTextureViewLayout();

                mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, null);

                mCameraId = id;
                break;
            }


        } catch (Exception e) {
            Log.e(TAG, "Error during camera initialize");
        }
    }

    private void setTextureViewLayout() {
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mTextureView.getLayoutParams();

        double ratio = (double) mWidth / mPreviewSize.getHeight();

        lp.width = mWidth;
        lp.height = (int) (mPreviewSize.getWidth() * ratio);

        mTextureView.setLayoutParams(lp);
    }

    private void openCamera() {
        setupCamera();
        try {

            //检查权限
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            //打开相机
            mCameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            mCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
        } catch (CameraAccessException e) {
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
       // stopBackgroundThread();
        super.onPause();
    }

    //旋转角度
    private int getOrientation(Direction direction) {
        switch (direction) {
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

    //选择sizeMap中大于并且最接近width和height的size
    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return sizeMap[0];
    }



    /* old Camera API*/


    private void initViews() {
        mTextureView = (TextureView) this.findViewById(R.id.textureView);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        previewView = (ImageView) this.findViewById(R.id.previewView);
        loadView = (ProgressBar) this.findViewById(R.id.progressBar2);
        imageView = (ImageView) this.findViewById(R.id.imageView);

        facesView = new DrawFacesView(this);
        focusView = new FocusCircleView(this);
        actionView = new ActionView(this);
        addContentView(focusView, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        addContentView(actionView, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        imageButton = (ImageButton) this.findViewById(R.id.imageButton);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //saveFaceImages();
                if (actionView != null) {
                    actionView.show();
                }
                takePhoto();//拍照
            }
        });
        mTextureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float x = event.getX();
                float y = event.getY();
                setFocusArea(x, y);
                if (focusView != null) {
                    focusView.setVisibility(View.VISIBLE);
                    focusView.myViewScaleAnimation(focusView);
                    focusView.setPoint(x, y);
                }
                return true;
            }
        });
    }

    //设置特定的对焦、曝光区域
    private void setFocusArea(float x, float y) {
        try {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void logTime(String info) {
        Log.d(TAG, info + new Date().getTime());
    }

    /****
     * 计算对应的聚焦和曝光区域
     * @param x
     * @param y 聚焦中心坐标
     * @param v 区域缩放系数
     */
    private Rect calculateTapArea(float x, float y, float v) {
        int FOCUS_AREA_SIZE = 300;
        int areaSize = Float.valueOf(FOCUS_AREA_SIZE * v).intValue();

        int centerx = (int) x * 2000 / mTextureView.getWidth() - 1000;
        int centery = (int) y * 2000 / mTextureView.getHeight() - 1000;
        Log.d(TAG, "calculateTapArea: x: " + x + ", y: " + y + ", areaSize: " + areaSize);
        int left = centerx - areaSize;//rect.left*2000/surfaceView.getWidth()-1000;
        int top = centery - areaSize;//rect.top*2000/surfaceView.getHeight()-1000;
        int right = centerx + areaSize;//rect.right*2000/surfaceView.getWidth()-1000;
        int bottom = centery + areaSize;//rect.bottom*2000/surfaceView.getHeight()-1000;
        left = left < -1000 ? -1000 : left;
        right = right > 1000 ? 1000 : right;
        top = top < -1000 ? -1000 : top;
        bottom = bottom > 1000 ? 1000 : bottom;
        return new Rect(left, top, right, bottom);
    }

    private void saveFaceImages() {
        //mCamera.stopPreview();
        String fileName = mPath;
        Log.d(TAG, "onClick: 存储文件夹：" + mPath);
        File dir = new File(mPath);
        if (!dir.exists()) {
            dir.mkdir();
            //Log.d(TAG, "onClick: 已创建文件夹");
        }
        //Log.d(TAG, "onClick: 文件夹已创建："+dir.exists());
        for (int i = 0; i < images.size(); i++) {
            fileName = fileName + "IMG_";
            String timeStamp = (new SimpleDateFormat("yyyyMMdd_HHmmss")).format(new Date());
            fileName = fileName + timeStamp + i + ".jpg";
            Log.d(TAG, "onClick: 文件名：" + fileName);
            Log.d(TAG, "onClick: 保存第 " + (i + 1) + "/" + images.size() + " 张图片");
            try {
                File imageFile = new File(fileName);
                if (!imageFile.exists()) {
                    imageFile.createNewFile();
                }
                if (imageFile.exists()) {
                    Log.d(TAG, "saveFaceImages: " + fileName + "创建成功");
                }
                BufferedSink bs = Okio.buffer(Okio.sink(imageFile));
                //调整方向后存储照片

                bs.write(rotateImage(images.get(i), imageDirections.get(i)));

                Log.d(TAG, "onClick: 保存路径：" + fileName);
                bs.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            fileName = mPath;
        }
        //清空缓存照片
        images = new ArrayList<>();
        imageDirections = new ArrayList<>();
    }

    //旋转图片 返回byte[]
    private byte[] rotateImage(byte[] img, Direction direction) {

        Bitmap newImg = rotateImageBitmap(img, direction);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        newImg.compress(Bitmap.CompressFormat.JPEG, 100, baos);

        newImg = null;

        return baos.toByteArray();
    }


    //旋转照片 返回bitmap
    private Bitmap rotateImageBitmap(byte[] img, Direction direction) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(img, 0, img.length);
        if (bitmap == null)
            return null;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Matrix mtx = new Matrix();

        switch (direction) {
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
    private void takePhoto() {
        lockFocus();

        loadView.setVisibility(View.VISIBLE);

    }

    /*拍照结束*/
    private void finishTakePhoto() {
        loadView.setVisibility(View.INVISIBLE);
        /*if(currPic==0){
            takePhoto();
            currPic+=1;
        }else {
            loadView.setVisibility(View.INVISIBLE);
            currPic=0;
        }*/
    }

    //设置拍照瞬间预览
    private void setPreview(Bitmap img) {
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

}

