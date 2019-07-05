package com.facedetector;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

import com.facedetector.customview.DragScaleView;

public class PreviewActivity extends AppCompatActivity {

    DragScaleView dragScaleView;
    String mPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        initView();

        Intent intent = getIntent();
        mPath = intent.getStringExtra("path");
        //打开图片
        openImage(mPath);
    }

    private void initView(){
        dragScaleView = (DragScaleView) findViewById(R.id.dragScaleView);
    }

    private void openImage(String path){
        dragScaleView.setImageResource(path);

    }

}
