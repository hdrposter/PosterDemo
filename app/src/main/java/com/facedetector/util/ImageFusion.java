package com.facedetector.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.provider.ContactsContract;
import android.text.BoringLayout;
import android.util.Log;

import com.facedetector.MainActivity;

import org.opencv.core.Mat;
import org.opencv.core.Algorithm;
import org.opencv.core.Core;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfInt;
import org.opencv.core.CvType;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;
import org.opencv.utils.Converters;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ImageFusion {
    private Mat originImg;
    private String mPath;
    private Mat restructImg;
    private Boolean[][] seg;
    private String TAG="ImageFusion: ";
    private Mat originMatrix;
    private int IMG_WIDTH;
    private int IMG_HEIGHT;
    private Size IMG_SIZE;
    private Bitmap fusionImg;

    public ImageFusion(byte[] originImg,byte[] restructImg,Boolean[][] seg){
//
//        String s="";
//        for (int i=0;i<seg.length;i++){
//            for (int j=0;j<seg[i].length;j++){
//                if (seg[i][j]){
//                    s=s+"1 ";
//                }
//                else {
//                    s=s+"0 ";
//                }
//            }
//            s=s+"\n";
//        }
//        Log.i(TAG, "runInference: \n"+s);

        this.originImg=new Mat();
        this.restructImg=new Mat();
        this.seg=seg;
        originMatrix= Mat.zeros(new Size(IMG_WIDTH,IMG_HEIGHT),CvType.CV_8UC3);

        Bitmap origin= BitmapFactory.decodeByteArray(originImg,0,originImg.length);
        IMG_WIDTH=origin.getWidth();
        IMG_HEIGHT=origin.getHeight();
        this.fusionImg=Bitmap.createBitmap(IMG_WIDTH,IMG_HEIGHT,Bitmap.Config.ARGB_8888);
        mPath= Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"Poster_Camera"+File.separator;
        this.IMG_SIZE=new Size(IMG_WIDTH,IMG_HEIGHT);
        Utils.bitmapToMat(origin,this.originImg);
        Bitmap restruct=BitmapFactory.decodeByteArray(restructImg,0,restructImg.length);
        Utils.bitmapToMat(restruct,this.restructImg);
    }

    public Bitmap fuseImg(){
        Log.i(TAG, "fuseImg: get into fusion!");
        List<Mat> weightMatrix=new ArrayList<>();
        weightMatrix=weighted_matrix();
        Mat resultImg=new Mat();
        resultImg=restructResult(originImg,restructImg,weightMatrix);
        Utils.matToBitmap(resultImg,fusionImg);
        saveImg(fusionImg);
        return fusionImg;
    }

    private void saveImg(Bitmap bm) {
        String fileName=mPath;
        Log.d(TAG, "onClick: 存储文件夹："+mPath);
        File dir=new File(mPath);
        if (!dir.exists()){
            dir.mkdir();
            //Log.d(TAG, "onClick: 已创建文件夹");
        }
        Log.d(TAG, "savePPT: 文件夹已创建："+dir.exists());
        fileName=fileName+"fused_";
        String timeStamp=(new SimpleDateFormat("yyyyMMdd_HHmmss")).format(new Date());
        fileName=fileName+timeStamp+".jpg";
        File f=new File(fileName);
        try {
            FileOutputStream outputStream=new FileOutputStream(f);
            bm.compress(Bitmap.CompressFormat.JPEG,100,outputStream);
            outputStream.flush();
            outputStream.close();
            Log.i(TAG, "saveImg: save img success! ");
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private Mat restructResult(Mat originImg, Mat restructImg, List<Mat> weightMatrix) {
        List<Mat> rgb=new ArrayList<>();
        Core.split(originImg,rgb);
        if (rgb.size()==3){
            for (int i=0;i<rgb.size();i++) {
                Core.multiply(rgb.get(i), weightMatrix.get(0), rgb.get(i));
            }
            Core.merge(rgb,originImg);
        }
        Core.split(restructImg,rgb);
        if (rgb.size()==4){
            for (int i=0;i<rgb.size();i++) {
                Core.multiply(rgb.get(i), weightMatrix.get(1), rgb.get(i));
            }
            Core.merge(rgb,restructImg);
        }
        Mat resultImg=new Mat();
        Core.add(originImg,restructImg,resultImg);
        resultImg.convertTo(resultImg,CvType.CV_8UC4);
        Bitmap bm=Bitmap.createBitmap(IMG_WIDTH,IMG_HEIGHT, Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(resultImg,bm);
        bm.toString();
        return resultImg;
    }

    private List<Mat> weighted_matrix(){
        Size segSize=new Size(seg.length,seg[0].length);
        List<Point> segPointList=new ArrayList<>();
        for (int i=0;i<seg.length;i++){
            for (int j=0;j<seg[i].length;j++){
                if (seg[i][j]){
                    Point point=new Point(i,j);
                    segPointList.add(point);
                }
            }
        }
        MatOfInt hull = new MatOfInt();
        if (segPointList.size()>0) {
            Log.i(TAG, "weighted_matrix: segPointList size: " + segPointList.size());
            MatOfPoint segPoints = new MatOfPoint();
            segPoints.fromList(segPointList);
            Log.i(TAG, "weighted_matrix: size: " + segPoints.size().toString());
            Imgproc.convexHull(segPoints, hull);
        }else {
            Log.i(TAG, "weighted_matrix: no TV");
            return null;
        }
        Size length=hull.size();
        List<Point> cornerPoint=new ArrayList<>();
        for (int i=0;i<hull.rows();i++){
            double[] index=hull.get(i,0);
            Point point=segPointList.get((int)index[0]);
            double x=point.x;
            double y=point.y;
//            x=x*IMG_HEIGHT/segSize.height;
//            y=y*IMG_WIDTH/segSize.width;
            cornerPoint.add(new Point(y,x));
            Log.i(TAG, "weighted_matrix: index: "+index);
        }
        Log.i(TAG, "weighted_matrix: length: "+length.toString());
        MatOfPoint cornerPointsMat=new MatOfPoint();
        cornerPointsMat.fromList(cornerPoint);
        originMatrix=Mat.zeros(new Size(257,257),CvType.CV_8U);

        Imgproc.fillConvexPoly(originMatrix,cornerPointsMat,new Scalar(255,255,255));
        Imgproc.resize(originMatrix,originMatrix,new Size(IMG_WIDTH,IMG_HEIGHT));
        Imgproc.GaussianBlur(originMatrix,originMatrix,new Size(101,71),30,30,4);
        Core.divide(255.0,originMatrix,originMatrix,CvType.CV_8U);
        Mat restructMatrix=new Mat();
        Core.absdiff(originMatrix,new Scalar(1),restructMatrix);
        List<Mat> matList=new ArrayList<>();
        matList.add(originMatrix);
        matList.add(restructMatrix);
        return matList;
//        for (int i=0;i<length;i++){
//            int x=hull
//        }
    }

    public void setSeg(Boolean[][] seg){
        this.seg=seg;
        return;
    }

    public void setOriginImg(Mat originImg) {
        this.originImg = originImg;
    }

    public void setRestructImg(Mat restructImg) {
        this.restructImg = restructImg;
    }

    public Bitmap getFusionImg() {
        return fusionImg;
    }
}
