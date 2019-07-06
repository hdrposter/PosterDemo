package com.facedetector.util;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class DeepLab {
    private final String TAG = "DeepLab";
    private GpuDelegate gpuDelegate = null;
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();

    /**
     * Settings
     **/
    private final String MODEL_PATH = "deeplabv3_257_mv_gpu.tflite";
    private final int IMG_H = 257;
    private final int IMG_W = 257;
    private final int IMG_C = 3;
    private final int BYTES_PER_CHANNEL = 4; // Float.SIZE / Byte.SIZE;
    private final int CATEGORY_N = 21;
    private final int CATEGORY_TV = 20;
    private final float IMG_MEAN = 127.5f;
    private final float IMG_STD = 127.5f;

    /**
     * A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs.
     */
    private ByteBuffer imgData = null;

    /**
     * Preallocated buffers for storing image data in.
     */
    private final int[] intValues = new int[IMG_H * IMG_W];

    private float[][][][] segment = new float[1][IMG_W][IMG_H][CATEGORY_N];

    /**
     * An instance of the driver class to run model inference with Tensorflow Lite.
     */
    private Interpreter tflite;

    /**
     * The loaded TensorFlow Lite model.
     */
    private MappedByteBuffer tfliteModel;

    public DeepLab(Activity activity, int numThreads) throws IOException {
        long startTime = System.currentTimeMillis();
        tfliteModel = loadModelFile(activity);
        tfliteOptions.setNumThreads(numThreads);
        tflite = new Interpreter(tfliteModel, tfliteOptions);
        imgData = ByteBuffer.allocateDirect(IMG_H * IMG_W * IMG_C * BYTES_PER_CHANNEL);
        imgData.order(ByteOrder.nativeOrder());
        long endTime = System.currentTimeMillis();
        Log.v(TAG, String.format("Created DeepLab Model of TFLite: %dms", endTime - startTime));
    }

    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        Log.v(TAG, "Loading Model from " + MODEL_PATH);
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void setImageData(byte[] data) {
        long startTime = System.currentTimeMillis();
        Bitmap bitmap = (BitmapFactory.decodeByteArray(data, 0, data.length));
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, IMG_W, IMG_H, false);
        bitmap.getPixels(intValues,
                0, scaledBitmap.getWidth(), 0, 0,
                scaledBitmap.getWidth(), scaledBitmap.getHeight());
        imgData.rewind();
        int pixel = 0;
        for (int i = 0; i < IMG_W; i++) {
            for (int j = 0; j < IMG_H; j++) {
                final int val = intValues[pixel++];
                imgData.putFloat((((val >> 16) & 0xFF) - IMG_MEAN) / IMG_STD);
                imgData.putFloat((((val >> 8) & 0xFF) - IMG_MEAN) / IMG_STD);
                imgData.putFloat(((val & 0xFF) - IMG_MEAN) / IMG_STD);
            }
        }
        long endTime = System.currentTimeMillis();
        Log.v(TAG, String.format("image converted into input: %dms", endTime - startTime));
    }

    public Boolean[][] getTVSegment() {
        long startTime = System.currentTimeMillis();
        Boolean[][] isTV = new Boolean[IMG_W][IMG_H];
        for (int i = 0; i < IMG_W; i++) {
            for (int j = 0; j < IMG_H; j++) {
                float maxProb = 0;
                int maxK = 0;
                for (int k = 0; k < CATEGORY_N; k++) {
                    if (segment[0][i][j][k] > maxProb) {
                        maxProb = segment[0][i][j][k];
                        maxK = k;
                    }
                }
                isTV[i][j] = (maxK == CATEGORY_TV);
            }
        }
        long endTime = System.currentTimeMillis();
        Log.v(TAG, String.format("output translated: %dms", endTime - startTime));
        return isTV;
    }

    public void runInference() {
        long startTime = System.currentTimeMillis();
        tflite.run(imgData, segment);
        long endTime = System.currentTimeMillis();
        Log.v(TAG, String.format("Inference Latency: %dms", endTime - startTime));
    }
}