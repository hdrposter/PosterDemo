package com.facedetector.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.facedetector.FaceDetectorActivity;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class DeepLabTest {

    byte[] img;
    DeepLab deepLab;
    String TAG="DeepLab";
    String mPath= Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"Poster_Camera"+File.separator;

    @Before
    public void before(){
        try{
            FileInputStream fs=new FileInputStream(mPath+"IMG_20190707_1551190.jpg");
            Bitmap bm= BitmapFactory.decodeStream(fs);
            int bytes=bm.getByteCount();
            ByteBuffer buffer=ByteBuffer.allocate(bytes);
            img=buffer.array();
            deepLab=new DeepLab(new FaceDetectorActivity(),5);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Test
    public void setImageData() {
    }

    @Test
    public void getTVSegment() {
    }

    @Test
    public void runInference() {
        try{
            FileInputStream fs=new FileInputStream(mPath+"IMG_20190707_1551190.jpg");
            Bitmap bm= BitmapFactory.decodeStream(fs);
            int bytes=bm.getByteCount();
            ByteBuffer buffer=ByteBuffer.allocate(bytes);
            img=buffer.array();
            deepLab=new DeepLab(new FaceDetectorActivity(),5);
        }catch (Exception e){
            e.printStackTrace();
        }
        deepLab.setImageData(img);
        deepLab.runInference();
        Boolean[][] seg=deepLab.getTVSegment();
    }
}