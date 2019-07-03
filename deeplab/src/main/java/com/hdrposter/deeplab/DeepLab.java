package com.hdrposter.deeplab;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class DeepLab {
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

    /**
     * A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs.
     */
    protected ByteBuffer imgData = null;

    /**
     * An instance of the driver class to run model inference with Tensorflow Lite.
     */
    protected Interpreter tflite;

    /**
     * The loaded TensorFlow Lite model.
     */
    private MappedByteBuffer tfliteModel;

    public DeepLab(Activity activity, int numThreads) throws IOException {
        tfliteModel = loadModelFile(activity);
        tfliteOptions.setNumThreads(numThreads);
        tflite = new Interpreter(tfliteModel, tfliteOptions);
        imgData = ByteBuffer.allocateDirect(IMG_H * IMG_W * IMG_C * BYTES_PER_CHANNEL);
    }

    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

}
