package com.facedetector.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.facedetector.FaceDetectorActivity;

import org.junit.Before;
import org.junit.Test;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.Assert.*;

public class DeepLabTest {

    Boolean[][] seg;
    ImageFusion imageFusion;
    String TAG="imageFusion";
    Mat origin;
    Mat restruct;
    int width;
    int height;
    String mPath= Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"Poster_Camera"+File.separator+"seg.txt";

    @Before
    public void before(){

    }

    @Test
    public void setImageData() {

    }

    @Test
    public void getTVSegment() {
    }

    @Test
    public void runInference() {
        
    }
}