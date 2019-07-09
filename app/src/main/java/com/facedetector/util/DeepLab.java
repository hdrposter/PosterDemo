package com.facedetector.util;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.support.v4.graphics.BitmapCompat;
import android.util.Log;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DeepLab {
    private final String TAG = "DeepLab";

    private TensorFlowInferenceInterface inferenceInterface;
    //    private int img_w = 257;
//    private int img_h = 257;
    private byte[] byteValues;
    private int[] intValues;
    private int[] outValues;

    /**
     * Settings
     **/
    private final String MODEL_PATH = "deeplabv3.pb";
    private final String INPUT_NODE = "ImageTensor";
    private final String OUTPUT_NODE = "SemanticPredictions";
    private final boolean USE_GPU = false;
    private final int IMG_C = 3;
    private final int IMG_H = 513;
    private final int IMG_W = 513;
    private final int BYTES_PER_LONG = 8; // Long.SIZE / Byte.SIZE;
    private final int BYTES_PER_CHANNEL = 4; // Float.SIZE / Byte.SIZE;
    private final int CATEGORY_N = 21;
    private final int CATEGORY_TV = 20;
    private final float IMG_MEAN = 127.5f;
    private final float IMG_STD = 127.5f;

    public DeepLab(AssetManager assetManager) {
        long startTime = System.currentTimeMillis();
        inferenceInterface = new TensorFlowInferenceInterface(assetManager, MODEL_PATH);
        long endTime = System.currentTimeMillis();
        Log.v(TAG, String.format("load model into inference interface: %dms", endTime - startTime));
        byteValues = new byte[IMG_H * IMG_W * 3];
        intValues = new int[IMG_H * IMG_W];
        outValues = new int[IMG_H * IMG_W];
    }

    private void preprocess(byte[] data) {
        long startTime = System.currentTimeMillis();
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        bitmap = Bitmap.createScaledBitmap(bitmap, IMG_W, IMG_H, false);
        bitmap.getPixels(intValues, 0, IMG_W, 0, 0, IMG_W, IMG_H);
        for (int i = 0; i < intValues.length; i++) {
            final int val = intValues[i];
            byteValues[i * 3] = (byte) ((val >> 16) & 0xFF);
            byteValues[i * 3 + 1] = (byte) ((val >> 8) & 0xFF);
            byteValues[i * 3 + 2] = (byte) (val & 0xFF);
        }

        inferenceInterface.feed(INPUT_NODE, byteValues, 1, IMG_H, IMG_W, IMG_C);
        long endTime = System.currentTimeMillis();
        Log.v(TAG, String.format("pre-processing: %dms", endTime - startTime));
    }

    private Boolean[][] transOutput() {
        long startTime = System.currentTimeMillis();
        Boolean[][] isTV = new Boolean[IMG_W][IMG_H];
//        Bitmap bitmap = Bitmap.createBitmap(IMG_W, IMG_H, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < IMG_H; y++) {
            for (int x = 0; x < IMG_W; x++) {
//                bitmap.setPixel(x, y, outValues[y * IMG_W + x] == CATEGORY_TV ? Color.YELLOW : Color.BLACK);
                isTV[x][y] = outValues[y * IMG_W + x] == CATEGORY_TV;
            }
        }
        long endTime = System.currentTimeMillis();
        Log.v(TAG, String.format("translate output: %dms", endTime - startTime));
        return isTV;
    }

    private void inference() {
        long startTime = System.currentTimeMillis();
        inferenceInterface.run(new String[]{OUTPUT_NODE}, true);
        inferenceInterface.fetch(OUTPUT_NODE, outValues);
        long endTime = System.currentTimeMillis();
        Log.v(TAG, String.format("inference: %dms", endTime - startTime));
    }

    public Boolean[][] getTVSegment(byte[] data) {
        preprocess(data);
        inference();
        return transOutput();
    }

}