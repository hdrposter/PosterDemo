package com.facedetector.util;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.RotatedRect;
import org.opencv.utils.Converters;
import org.opencv.core.Algorithm;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PPTCorrector {
    private Mat originImg;
    private Bitmap correctedPPT;
    private String mPath;
    private static String TAG="PPTCorrector: ";
    public PPTCorrector(byte[] img){
        Bitmap imgBitmap= BitmapFactory.decodeByteArray(img,0,img.length);
        originImg=new Mat(imgBitmap.getHeight(),imgBitmap.getWidth(), CvType.CV_8UC4);
        Utils.bitmapToMat(imgBitmap,originImg);
        correctedPPT=null;
        mPath= Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"Poster_Camera"+File.separator;
    }
    public Mat correction(Mat img){
        Log.d(TAG, "correction: get into correction");
        Mat gray=new Mat();
        Imgproc.cvtColor(img,gray,Imgproc.COLOR_BGR2GRAY);
        Mat blurred=new Mat();
        Imgproc.GaussianBlur(gray,blurred,new Size(5,5),0);
        Mat dilate=new Mat();
        Imgproc.dilate(blurred,dilate,Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(3,3)));
        Mat edged=new Mat();
        Imgproc.Canny(dilate,edged,30.0,120.0,3,false);
        Mat edged_copy=new Mat();
        edged.copyTo(edged_copy);
        List<MatOfPoint> cnts=new ArrayList<>();
        Mat hierachy=new Mat();
        Imgproc.findContours(edged_copy,cnts,hierachy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
        double maxArea=Imgproc.boundingRect(cnts.get(0)).area();
        int index=0;
        MatOfPoint2f approxCurve = new MatOfPoint2f();
        MatOfPoint2f matOfPoint2f=new MatOfPoint2f();
        double peri;
        Point[] tempPoints,finalPoint={};
        for (int i=0;i<cnts.size();i++){
            matOfPoint2f = new MatOfPoint2f(cnts.get(i).toArray());
            peri=Imgproc.arcLength(matOfPoint2f,true);
            Imgproc.approxPolyDP(matOfPoint2f, approxCurve, 0.02*peri, true);
            tempPoints = approxCurve.toArray();
            if (tempPoints.length==4){
                double tempArea= Imgproc.boundingRect(cnts.get(i)).area();
                if (tempArea>maxArea){
                    maxArea=tempArea;
                    finalPoint=tempPoints;
                    index=i;
                }
            }
        }
        Mat pptCorrectedMt=new Mat();
        pptCorrectedMt=perspective_transform(img,finalPoint);
        correctedPPT=Bitmap.createBitmap(pptCorrectedMt.cols(),pptCorrectedMt.rows(),Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(pptCorrectedMt,correctedPPT);
        savePPT(correctedPPT);
//        for (int i=0;i<finalPoint.length;i++) {
//            Log.d(TAG, "correction: points: x: " + finalPoint[i].x + " y: " + finalPoint[i].y + ",points number: " + finalPoint.length);
//        }
        return hierachy;
    }

    private Mat perspective_transform(Mat img,Point[] points) {
        ArrayList<Double> length=new ArrayList<>();
        Mat imgCp=new Mat();
        img.copyTo(imgCp);
        for (int i=0;i<points.length;i++){
            double distance=0;
            if (i==points.length-1){
                distance=getDistance(points[i],points[0]);
                length.add(distance);
                Log.d(TAG, "perspective_transform: distance between "+i+" and 0 : "+distance);
                break;
            }
            distance=getDistance(points[i],points[i+1]);
            length.add(distance);
            Log.d(TAG, "perspective_transform: distance between "+i+" and "+i+1+" : "+distance);
        }
        int width=(int)Math.max(length.get(1),length.get(3));
        int height=(int)Math.max(length.get(0),length.get(2));
        Point lu=new Point(0,0);
        Point ld=new Point(0, height);
        Point rd=new Point(width,height);
        Point ru=new Point(width,0);
        Point[] correct={lu,ld,rd,ru};

        List<Point> srcPoint= Arrays.asList(points[0],points[1],points[2],points[3]);
        List<Point> dstPoint=Arrays.asList(correct[0],correct[1],correct[2],correct[3]);
        Mat src=Converters.vector_Point_to_Mat(srcPoint,CvType.CV_32F);
        Mat dst=Converters.vector_Point_to_Mat(dstPoint,CvType.CV_32F);

        Mat transform_mtx=Imgproc.getPerspectiveTransform(src,dst);
        Mat pptCorrected=new Mat();
        Imgproc.warpPerspective(img,pptCorrected,transform_mtx,new Size(width,height));
        return pptCorrected;
    }

    private double getDistance(Point a,Point b){
        double square=Math.pow((a.x-b.x),2)+Math.pow((a.y-b.y),2);
        double distance=Math.sqrt(square);
        return distance;
    }

    private void savePPT(Bitmap bm){
        String fileName=mPath;
        Log.d(TAG, "onClick: 存储文件夹："+mPath);
        File dir=new File(mPath);
        if (!dir.exists()){
            dir.mkdir();
            //Log.d(TAG, "onClick: 已创建文件夹");
        }
        Log.d(TAG, "savePPT: 文件夹已创建："+dir.exists());
        fileName=fileName+"ppt.jpg";
        File f=new File(fileName);
        try {
            FileOutputStream outputStream=new FileOutputStream(f);
            bm.compress(Bitmap.CompressFormat.JPEG,100,outputStream);
            outputStream.flush();
            outputStream.close();
            Log.i(TAG, "savePPT: save ppt success! ");
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public Mat getOriginImg(){
        return  originImg;
    }
}
